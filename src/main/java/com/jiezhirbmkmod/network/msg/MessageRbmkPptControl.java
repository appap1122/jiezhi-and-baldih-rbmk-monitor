package com.jiezhirbmkmod.network.msg;


import com.jiezhirbmkmod.network.NetGuard;
import com.jiezhirbmkmod.block.TileRbmkPptScreen;
import com.jiezhirbmkmod.item.ItemRbmkPptRemote;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.relauncher.Side;

/**
 * v17.1 client -> server PPT control (key bindings and terminal commands).
 * action: 0 = next, 1 = prev, 2 = goto (value = page), 3 = load (value = total).
 * pos = controller position, or ORIGIN to resolve from the held PPT remote.
 */
public class MessageRbmkPptControl
implements IMessage {

    public static final int NEXT = 0;
    public static final int PREV = 1;
    public static final int GOTO = 2;
    public static final int LOAD = 3;
    public static final int PICTURE = 4;
    public static final int CLEAR = 5;

    private int action;
    private int value;
    private BlockPos pos = BlockPos.ORIGIN;
    private String text = "";

    public MessageRbmkPptControl() {
    }

    public MessageRbmkPptControl(int action, int value, BlockPos pos) {
        this(action, value, pos, "");
    }

    public MessageRbmkPptControl(int action, int value, BlockPos pos, String text) {
        this.action = action;
        this.value = value;
        this.pos = pos == null ? BlockPos.ORIGIN : pos;
        this.text = text == null ? "" : text;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeByte(this.action);
        buf.writeInt(this.value);
        buf.writeInt(this.pos.getX());
        buf.writeInt(this.pos.getY());
        buf.writeInt(this.pos.getZ());
        byte[] tb = this.text.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        buf.writeInt(tb.length);
        buf.writeBytes(tb);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.action = buf.readByte() & 255;
        this.value = buf.readInt();
        this.pos = new BlockPos(buf.readInt(), buf.readInt(), buf.readInt());
        int len = buf.readInt();
        if (len < 0 || len > 512) { len = 0; }
        byte[] tb = new byte[len];
        buf.readBytes(tb);
        this.text = new String(tb, java.nio.charset.StandardCharsets.UTF_8);
    }

    public static class Handler
    implements IMessageHandler<MessageRbmkPptControl, IMessage> {

        @Override
        public IMessage onMessage(final MessageRbmkPptControl msg, MessageContext ctx) {
            try {
            if (ctx.side != Side.SERVER || ctx.getServerHandler() == null
                    || ctx.getServerHandler().player == null) {
                return null;
            }
            final EntityPlayerMP player = ctx.getServerHandler().player;
            final MinecraftServer server = player.getServer();
            if (server == null) {
                return null;
            }
            server.addScheduledTask(new Runnable() {
                @Override
                public void run() {
                    TileRbmkPptScreen screen = Handler.resolve(player, msg.pos);
                    if (screen == null) {
                        return;
                    }
                    if (player.getDistanceSq((double) screen.getPos().getX() + 0.5D,
                            (double) screen.getPos().getY() + 0.5D,
                            (double) screen.getPos().getZ() + 0.5D) > 4096.0D) {
                        return;
                    }
                    switch (msg.action) {
                        case NEXT:
                            screen.nextSlide();
                            break;
                        case PREV:
                            screen.prevSlide();
                            break;
                        case GOTO:
                            screen.setSlide(msg.value);
                            break;
                        case LOAD:
                            screen.setTotalSlides(msg.value);
                            screen.setSlide(1);
                            break;
                        case PICTURE:
                            screen.setPicture(msg.text);
                            break;
                        case CLEAR:
                            screen.clearPicture();
                            break;
                        default:
                            break;
                    }
                }
            });
            return null;
        
        } catch (Throwable t) {
            NetGuard.reportHandler("Handler", t);
            return null;
        }
        }

        static TileRbmkPptScreen resolve(EntityPlayerMP player, BlockPos pos) {
            if (pos != null && !BlockPos.ORIGIN.equals(pos)) {
                TileEntity te = player.world.getTileEntity(pos);
                return te instanceof TileRbmkPptScreen ? (TileRbmkPptScreen) te : null;
            }
            ItemStack main = player.getHeldItem(EnumHand.MAIN_HAND);
            ItemStack off = player.getHeldItem(EnumHand.OFF_HAND);
            ItemStack[] hands = new ItemStack[]{main, off};
            for (int i = 0; i < hands.length; ++i) {
                ItemStack stack = hands[i];
                if (stack == null || !(stack.getItem() instanceof ItemRbmkPptRemote)) {
                    continue;
                }
                if (ItemRbmkPptRemote.getBoundPos(stack) == null) {
                    continue;
                }
                if (ItemRbmkPptRemote.getBoundDim(stack) != player.world.provider.getDimension()) {
                    continue;
                }
                TileEntity te = player.world.getTileEntity(ItemRbmkPptRemote.getBoundPos(stack));
                if (te instanceof TileRbmkPptScreen) {
                    return (TileRbmkPptScreen) te;
                }
            }
            return null;
        }
    }
}
