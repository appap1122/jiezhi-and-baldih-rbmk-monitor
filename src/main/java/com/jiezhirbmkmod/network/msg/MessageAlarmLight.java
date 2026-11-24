package com.jiezhirbmkmod.network.msg;

import com.jiezhirbmkmod.alarm.AlarmConfig;
import com.jiezhirbmkmod.block.TileAlarmLight;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.relauncher.Side;

/**
 * Client -> server：报警灯控制面板的一次提交。
 *
 * <h3>v17.8：从一个 int 扩成"整份面板状态"</h3>
 * 面板上现在有五组东西（颜色 / 转速 / 开关 / 光锥实现 / 触发方式）外加一个联动开关，
 * 只传颜色的话每加一个控件就要再开一个包。这里一次把面板上的值全带上，
 * 服务端按"能不能改"决定采纳哪些。
 *
 * <h3>为什么联动开关是独立的三态字节</h3>
 * {@code auto} 不是"联动的值"，而是"这次要不要动联动"：
 * <pre>
 *   AUTO_KEEP  只改颜色 / 转速 / 开关 / 光锥（面板上改滑条时的常规提交）
 *   AUTO_ON    开联动（服务端去搜最近的显示屏，搜不到会回一条提示）
 *   AUTO_OFF   关联动
 * </pre>
 * 用布尔量的话，"只改颜色"会被理解成"关掉联动"—— 一拖滑条联动就没了。
 */
public class MessageAlarmLight
implements IMessage {

    /** 不动联动状态 */
    public static final byte AUTO_KEEP = 0;
    /** 打开联动（自动绑定最近的显示屏） */
    public static final byte AUTO_ON = 1;
    /** 关闭联动 */
    public static final byte AUTO_OFF = 2;

    private BlockPos pos = BlockPos.ORIGIN;
    private int color;
    private int rpm;
    private boolean on;
    private int spotMode;
    private int trigger;
    private byte auto;

    public MessageAlarmLight() {
    }

    public MessageAlarmLight(BlockPos pos, int color, int rpm, boolean on, int spotMode,
                             int trigger, byte auto) {
        this.pos = pos == null ? BlockPos.ORIGIN : pos;
        this.color = color & 0xFFFFFF;
        this.rpm = AlarmConfig.clampRpm(rpm);
        this.on = on;
        this.spotMode = spotMode;
        this.trigger = trigger;
        this.auto = auto;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(this.pos.getX());
        buf.writeInt(this.pos.getY());
        buf.writeInt(this.pos.getZ());
        buf.writeInt(this.color);
        buf.writeInt(this.rpm);
        // 一个字节塞下开关与光锥模式（模式只有 0/1/2，两位够用）
        buf.writeByte((this.on ? 1 : 0) | ((this.spotMode & 3) << 1));
        // v17.9：触发方式 0..5（常亮 / 常灭档），3 位掩码（0..7）够用。
        // 旧端读包仍然 &3：跨版本混连时 4/5 被旧端读成 0（右键），不崩线。
        buf.writeByte(this.trigger & 7);
        buf.writeByte(this.auto);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.pos = new BlockPos(buf.readInt(), buf.readInt(), buf.readInt());
        this.color = buf.readInt() & 0xFFFFFF;
        this.rpm = AlarmConfig.clampRpm(buf.readInt());
        int flags = buf.readByte() & 0xFF;
        this.on = (flags & 1) != 0;
        this.spotMode = (flags >> 1) & 3;
        this.trigger = buf.readByte() & 7;
        this.auto = buf.readByte();
    }

    public static class Handler
    implements IMessageHandler<MessageAlarmLight, IMessage> {

        @Override
        public IMessage onMessage(final MessageAlarmLight msg, MessageContext ctx) {
            if (ctx.side != Side.SERVER || ctx.getServerHandler() == null
                    || ctx.getServerHandler().player == null) {
                return null;
            }
            final EntityPlayerMP player = ctx.getServerHandler().player;
            final MinecraftServer server = player.getServer();
            if (server == null) {
                return null;
            }
            try {
                server.addScheduledTask(() -> {
                    if (player.world == null) {
                        return;
                    }
                    if (player.getDistanceSq((double) msg.pos.getX() + 0.5D,
                            (double) msg.pos.getY() + 0.5D,
                            (double) msg.pos.getZ() + 0.5D) > 4096.0D) {
                        return;
                    }
                    TileEntity te = player.world.getTileEntity(msg.pos);
                    if (!(te instanceof TileAlarmLight)) {
                        return;
                    }
                    TileAlarmLight light = (TileAlarmLight) te;

                    // ---------- 1) 联动开关先处理 ----------
                    if (msg.auto == AUTO_ON) {
                        if (!light.setAutoEnabled(true)) {
                            player.sendStatusMessage(new TextComponentTranslation(
                                    "msg.jiezhirbmkmod.alarm_light.no_screen_near"), true);
                            // 联动没开成，这一包剩下的颜色 / 转速就不该落地：
                            // 玩家看到的是"点开联动失败"，却顺手把灯改成另一个颜色，很怪
                            return;
                        }
                    } else if (msg.auto == AUTO_OFF) {
                        light.setAutoEnabled(false);
                    }

                    // ---------- 2) 光锥实现：任何时候都能改 ----------
                    //    它跟告警等级无关，联动中的灯也需要能切（近处想看清楚就开 SSS）
                    light.setSpotMode(msg.spotMode);

                    // ---------- 3) 触发方式：任何时候都能存 ----------
                    //    联动中它不影响灯态（isEnabled 先看联动），但要存下来 ——
                    //    玩家解除联动之后就按这个继续走，不用再设一遍
                    light.setTriggerMode(msg.trigger);

                    // ---------- 4) 颜色 / 转速 / 开关：联动中一律不采纳 ----------
                    if (light.isAuto()) {
                        // 面板上这三组控件已经置灰，正常不会走到这里；真走到了（旧面板、
                        // 并发绑定）也不能写 —— poll 每 10 刻就会用告警等级把它们盖回去，
                        // 写了只是让玩家看到颜色闪一下。
                        return;
                    }
                    // v17.9 自动染色：AUTO_OFF 这包刚用快照恢复了玩家的手动染色，
                    // 而滑条里带的还是联动期间显示的告警色 —— 再落地一次就把恢复
                    // 盖掉了。关闭联动时以服务端恢复值为准，这一包的颜色不落地。
                    if (msg.auto == AUTO_OFF) {
                        return;
                    }
                    light.applyConfig(new AlarmConfig(msg.color, msg.rpm, msg.on));
                });
            }
            catch (Throwable ignored) {
            }
            return null;
        }
    }
}
