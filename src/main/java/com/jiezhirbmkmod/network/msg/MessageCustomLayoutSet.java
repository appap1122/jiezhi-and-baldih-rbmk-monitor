package com.jiezhirbmkmod.network.msg;


import com.jiezhirbmkmod.network.NetGuard;
import com.jiezhirbmkmod.block.TileCustomScreen;
import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/**
 * v18.2 自定义页布局保存包（C→S，id 24）。
 *
 * <p>布局编辑器的「保存」按钮把整份布局 NBT（Base64 压缩串）发到服务端，
 * 服务端整体替换 {@link TileCustomScreen#getLayout} 并持久化 + 同步。
 * 布局串校验失败（伪造包/坏数据）静默丢弃 —— 空布局走旧版固定排布，
 * 不会让屏变砖。</p>
 */
public class MessageCustomLayoutSet
implements IMessage {

    private BlockPos pos = BlockPos.ORIGIN;
    /** Base64(压缩 NBT)；空串 = 清空布局回旧版固定排布 */
    private String data = "";

    public MessageCustomLayoutSet() {
    }

    public MessageCustomLayoutSet(BlockPos pos, String data) {
        this.pos = pos;
        this.data = data == null ? "" : data;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.pos = new BlockPos(buf.readInt(), buf.readInt(), buf.readInt());
        this.data = ByteBufUtils.readUTF8String(buf);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(this.pos.getX());
        buf.writeInt(this.pos.getY());
        buf.writeInt(this.pos.getZ());
        ByteBufUtils.writeUTF8String(buf, this.data);
    }

    public static class Handler
    implements IMessageHandler<MessageCustomLayoutSet, IMessage> {
        @Override
        public IMessage onMessage(final MessageCustomLayoutSet msg, MessageContext ctx) {
            try {
            if (ctx.side.isServer() && ctx.getServerHandler() != null
                    && ctx.getServerHandler().player != null) {
                final net.minecraft.entity.player.EntityPlayerMP player = ctx.getServerHandler().player;
                player.getServerWorld().addScheduledTask(new Runnable() {
                    @Override
                    public void run() {
                        BlockPos p = msg.pos;
                        World w = player.world;
                        if (w == null || !w.isBlockLoaded(p)) {
                            return;
                        }
                        // 防伪造包：人不在屏边（16 格）就当没看见
                        if (player.getDistanceSq((double) p.getX() + 0.5D,
                                (double) p.getY() + 0.5D, (double) p.getZ() + 0.5D) > 256.0D) {
                            return;
                        }
                        TileEntity te = w.getTileEntity(p);
                        if (!(te instanceof TileCustomScreen)) {
                            return;
                        }
                        TileCustomScreen screen = (TileCustomScreen) te;
                        com.jiezhirbmkmod.dfc.CustomWidgetLayout layout =
                                new com.jiezhirbmkmod.dfc.CustomWidgetLayout();
                        if (msg.data != null && !msg.data.isEmpty()) {
                            if (!layout.importString(msg.data)) {
                                return; // 坏数据整体丢弃，屏保持原布局
                            }
                        }
                        screen.setLayout(layout);
                    }
                });
            }
            return null;
        
        } catch (Throwable t) {
            NetGuard.reportHandler("Handler", t);
            return null;
        }
        }
    }
}
