package com.jiezhirbmkmod.network.msg;

import com.jiezhirbmkmod.block.TileRbmkMonitorScreen;
import io.netty.buffer.ByteBuf;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/**
 * v18.2 设置中心（蹲下连续右键两下）的显示设置包（C→S）。
 *
 * <p>把一项显示设置写到目标屏上并持久化：亮度（0）、主题（1）、
 * 曲线对比（2）、对数坐标（3）。设置是屏幕自身属性，跟着屏存档走，
 * 谁都可以调——不涉权限，和拨电源同级。</p>
 */
public class MessageScreenDisplaySetting
implements IMessage {

    /** 设置项键 */
    public static final int KEY_BRIGHTNESS = 0;
    public static final int KEY_THEME = 1;
    public static final int KEY_COMPARE = 2;
    public static final int KEY_LOG_SCALE = 3;

    private BlockPos pos = BlockPos.ORIGIN;
    private int key;
    private int value;

    public MessageScreenDisplaySetting() {
    }

    public MessageScreenDisplaySetting(BlockPos pos, int key, int value) {
        this.pos = pos;
        this.key = key;
        this.value = value;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.pos = new BlockPos(buf.readInt(), buf.readInt(), buf.readInt());
        this.key = buf.readInt();
        this.value = buf.readInt();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(this.pos.getX());
        buf.writeInt(this.pos.getY());
        buf.writeInt(this.pos.getZ());
        buf.writeInt(this.key);
        buf.writeInt(this.value);
    }

    public static class Handler
    implements IMessageHandler<MessageScreenDisplaySetting, IMessage> {
        @Override
        public IMessage onMessage(MessageScreenDisplaySetting msg, MessageContext ctx) {
            final BlockPos p = msg.pos;
            final int k = msg.key;
            final int v = msg.value;
            if (ctx.side.isServer() && p != null && ctx.getServerHandler() != null
                    && ctx.getServerHandler().player != null) {
                final net.minecraft.entity.player.EntityPlayerMP player = ctx.getServerHandler().player;
                player.getServerWorld().addScheduledTask(new Runnable() {
                    @Override
                    public void run() {
                        World w = player.world;
                        if (w == null || !w.isBlockLoaded(p)) {
                            return;
                        }
                        // 防伪造包：人不在屏边上（16 格）就当没看见
                        if (player.getDistanceSq((double) p.getX() + 0.5D,
                                (double) p.getY() + 0.5D, (double) p.getZ() + 0.5D) > 256.0D) {
                            return;
                        }
                        TileEntity te = w.getTileEntity(p);
                        if (te instanceof TileRbmkMonitorScreen) {
                            ((TileRbmkMonitorScreen) te).applyDisplaySetting(k, v);
                        }
                    }
                });
            }
            return null;
        }
    }
}
