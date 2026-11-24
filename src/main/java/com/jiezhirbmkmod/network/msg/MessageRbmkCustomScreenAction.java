package com.jiezhirbmkmod.network.msg;

import com.jiezhirbmkmod.block.ContainerCustomScreen;
import com.jiezhirbmkmod.block.TileCustomScreen;
import com.jiezhirbmkmod.item.ItemRbmkRemote;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

public class MessageRbmkCustomScreenAction
implements IMessage {
    public static final int ACT_POWER = 0;
    public static final int ACT_DELETE = 1;
    public static final int ACT_CLEAR = 2;
    public static final int ACT_IMPORT = 3;
    private BlockPos pos;
    private int action;
    private int index;

    public MessageRbmkCustomScreenAction() {
        this.pos = BlockPos.ORIGIN;
    }

    public MessageRbmkCustomScreenAction(BlockPos pos, int action, int index) {
        this.pos = pos;
        this.action = action;
        this.index = index;
    }

    public void fromBytes(ByteBuf buf) {
        this.pos = new BlockPos(buf.readInt(), buf.readInt(), buf.readInt());
        this.action = buf.readInt();
        this.index = buf.readInt();
    }

    public void toBytes(ByteBuf buf) {
        buf.writeInt(this.pos.getX());
        buf.writeInt(this.pos.getY());
        buf.writeInt(this.pos.getZ());
        buf.writeInt(this.action);
        buf.writeInt(this.index);
    }

    public BlockPos pos() {
        return this.pos;
    }

    public int action() {
        return this.action;
    }

    public int index() {
        return this.index;
    }

    public static class Handler
    implements IMessageHandler<MessageRbmkCustomScreenAction, IMessage> {
        public IMessage onMessage(MessageRbmkCustomScreenAction msg, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> {
                TileEntity te = player.world.getTileEntity(msg.pos);
                if (!(te instanceof TileCustomScreen)) {
                    return;
                }
                TileCustomScreen screen = (TileCustomScreen)te;
                if (player.getDistanceSq((double)msg.pos.getX() + 0.5, (double)msg.pos.getY() + 0.5, (double)msg.pos.getZ() + 0.5) > 4096.0) {
                    return;
                }
                switch (msg.action) {
                    case 0: {
                        screen.togglePower((EntityPlayer)player);
                        return;
                    }
                    case 1: {
                        screen.removeSlot(msg.index);
                        break;
                    }
                    case 2: {
                        screen.clearSlots();
                        break;
                    }
                    case 3: {
                        Handler.importFromRemote(player, screen);
                        return;
                    }
                    default: {
                        return;
                    }
                }
                screen.refresh();
            });
            return null;
        }

        private static void importFromRemote(EntityPlayerMP player, TileCustomScreen screen) {
            if (!(player.openContainer instanceof ContainerCustomScreen)) {
                return;
            }
            ContainerCustomScreen c = (ContainerCustomScreen)player.openContainer;
            if (c.getTile() != screen) {
                return;
            }
            ItemStack remote = c.getRemoteStack();
            if (remote.isEmpty() || !(remote.getItem() instanceof ItemRbmkRemote)) {
                return;
            }
            int[] r = screen.importDevices(remote);
            screen.refresh();
            player.sendStatusMessage((ITextComponent)new TextComponentString(TextFormatting.AQUA + "[jiezhirbmkmod] " + TextFormatting.RESET + ItemRbmkRemote.summarize(r, screen.getSlots().size())), false);
        }
    }
}

