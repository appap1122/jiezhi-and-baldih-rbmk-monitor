package com.jiezhirbmkmod.network.msg;

import com.jiezhirbmkmod.block.BlockRbmkSiren;
import com.jiezhirbmkmod.block.TileRbmkSiren;
import io.netty.buffer.ByteBuf;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.EnumDyeColor;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.relauncher.Side;

public class MessageSirenTune implements IMessage {

    private BlockPos pos = BlockPos.ORIGIN;
    private int colorMeta;
    private int preset;
    private float volume;
    private float pitch;
    private int gap;
    private int mode;
    private int category;
    private boolean flash;

    public MessageSirenTune() {
    }

    public MessageSirenTune(BlockPos pos, int colorMeta, int preset, float volume, float pitch,
            int gap, int mode, int category, boolean flash) {
        this.pos = pos;
        this.colorMeta = colorMeta;
        this.preset = preset;
        this.volume = volume;
        this.pitch = pitch;
        this.gap = gap;
        this.mode = mode;
        this.category = category;
        this.flash = flash;
    }

    public MessageSirenTune(World world, BlockPos pos, TileRbmkSiren tile) {
        this(pos, colorOf(world, pos), tile.getPreset(), tile.getVolume(), tile.getPitch(),
                tile.getGapTicks(), tile.getMode(), tile.getCategory(), tile.isFlashLight());
    }

    private static int colorOf(World world, BlockPos pos) {
        IBlockState s = world == null ? null : world.getBlockState(pos);
        return s != null && s.getBlock() instanceof BlockRbmkSiren
                ? ((EnumDyeColor) s.getValue(BlockRbmkSiren.COLOR)).ordinal() : 14;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.pos = new BlockPos(buf.readInt(), buf.readInt(), buf.readInt());
        this.colorMeta = buf.readInt();
        this.preset = buf.readInt();
        this.volume = buf.readFloat();
        this.pitch = buf.readFloat();
        this.gap = buf.readInt();
        this.mode = buf.readInt();
        this.category = buf.readInt();
        this.flash = buf.readBoolean();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(this.pos.getX());
        buf.writeInt(this.pos.getY());
        buf.writeInt(this.pos.getZ());
        buf.writeInt(this.colorMeta);
        buf.writeInt(this.preset);
        buf.writeFloat(this.volume);
        buf.writeFloat(this.pitch);
        buf.writeInt(this.gap);
        buf.writeInt(this.mode);
        buf.writeInt(this.category);
        buf.writeBoolean(this.flash);
    }

    public static class Handler implements IMessageHandler<MessageSirenTune, IMessage> {
        @Override
        public IMessage onMessage(MessageSirenTune msg, MessageContext ctx) {
            if (ctx.side == Side.SERVER) {
                final EntityPlayerMP player = ctx.getServerHandler().player;
                player.getServerWorld().addScheduledTask(() -> applyServer(player.getServerWorld(), msg, player));
                return null;
            }
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getMinecraft();
            mc.addScheduledTask(() -> applyClient(mc.world, msg));
            return null;
        }

        private static void applyServer(World world, MessageSirenTune msg, EntityPlayerMP player) {
            if (player.getDistanceSq((double) msg.pos.getX() + 0.5D, (double) msg.pos.getY() + 0.5D,
                    (double) msg.pos.getZ() + 0.5D) > 4096.0D) {
                return;
            }
            TileEntity te = world.getTileEntity(msg.pos);
            if (!(te instanceof TileRbmkSiren)) {
                return;
            }
            IBlockState old = world.getBlockState(msg.pos);
            if (old.getBlock() instanceof BlockRbmkSiren) {
                EnumDyeColor want = BlockRbmkSiren.dye(msg.colorMeta);
                if ((EnumDyeColor) old.getValue(BlockRbmkSiren.COLOR) != want) {
                    world.setBlockState(msg.pos, old.withProperty(BlockRbmkSiren.COLOR, want), 3);
                }
            }
            TileRbmkSiren tile = (TileRbmkSiren) te;
            tile.applyConfig(msg.preset, msg.volume, msg.pitch, msg.gap, msg.mode, msg.category, msg.flash);
            world.checkLight(msg.pos);
            com.jiezhirbmkmod.network.ModNetwork.CHANNEL.sendToAllAround(
                    (IMessage) new MessageSirenTune(world, msg.pos, tile),
                    new NetworkRegistry.TargetPoint(world.provider.getDimension(),
                            (double) msg.pos.getX() + 0.5D, (double) msg.pos.getY() + 0.5D,
                            (double) msg.pos.getZ() + 0.5D, 96.0D));
        }

        private static void applyClient(World world, MessageSirenTune msg) {
            if (world == null) {
                return;
            }
            TileEntity te = world.getTileEntity(msg.pos);
            if (te instanceof TileRbmkSiren) {
                ((TileRbmkSiren) te).applyConfigLocal(msg.preset, msg.volume, msg.pitch, msg.gap,
                        msg.mode, msg.category, msg.flash);
            }
        }
    }

}
