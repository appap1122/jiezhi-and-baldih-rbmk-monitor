package com.jiezhirbmkmod.network.msg;

import com.jiezhirbmkmod.dfc.RbmkBridge;
import com.jiezhirbmkmod.dfc.RbmkSnapshot;
import com.jiezhirbmkmod.item.ItemRbmkPda;
import com.jiezhirbmkmod.network.ModNetwork;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.relauncher.Side;

/**
 * v18.3 PDA round trip: snapshot request / bind request / bind reply.
 *
 * <p>Client -> server: one of {@link #ACTION_SNAPSHOT}, {@link #ACTION_BIND},
 * {@link #ACTION_UNBIND}. The server re-reads the PDA from the player's hands
 * and validates distance, dimension and the console tile itself; client fields
 * are never trusted.</p>
 *
 * <p>Server -> client: {@code reply=true} plus an optional {@link RbmkSnapshot}
 * and the bound console coordinates. This is deliberately the only new packet
 * of the feature: terminal input still goes through
 * {@link MessageTerminalCommand}.</p>
 */
public class MessageRbmkPda implements IMessage {

    public static final int ACTION_SNAPSHOT = 0;
    public static final int ACTION_BIND = 1;
    public static final int ACTION_UNBIND = 2;

    /** max distance to the bound console for snapshot/command access (128 m) */
    public static final double MAX_DISTANCE_SQ = 16384.0D;

    private boolean reply;
    private int action;
    private boolean ok;
    private String text = "";
    private int x;
    private int y;
    private int z;
    private int dim;
    private boolean hasSnapshot;
    private RbmkSnapshot snapshot = new RbmkSnapshot();

    public MessageRbmkPda() {
    }

    private MessageRbmkPda(boolean reply, int action) {
        this.reply = reply;
        this.action = action;
    }

    public static MessageRbmkPda request(int action) {
        return new MessageRbmkPda(false, action);
    }

    private static MessageRbmkPda reply(int action, boolean ok, String text) {
        MessageRbmkPda m = new MessageRbmkPda(true, action);
        m.ok = ok;
        m.text = text == null ? "" : text;
        return m;
    }

    // ---- getters (client GUI) --------------------------------------------

    public boolean isOk() {
        return this.ok;
    }

    public String getText() {
        return this.text;
    }

    public int getAction() {
        return this.action;
    }

    public int getDim() {
        return this.dim;
    }

    public int getX() {
        return this.x;
    }

    public int getY() {
        return this.y;
    }

    public int getZ() {
        return this.z;
    }

    public boolean hasSnapshot() {
        return this.hasSnapshot;
    }

    public RbmkSnapshot getSnapshot() {
        return this.snapshot;
    }

    // ---- codec -----------------------------------------------------------

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeBoolean(this.reply);
        buf.writeByte(this.action);
        buf.writeBoolean(this.ok);
        RbmkSnapshot.writeShortString(buf, this.text);
        buf.writeInt(this.x);
        buf.writeInt(this.y);
        buf.writeInt(this.z);
        buf.writeInt(this.dim);
        buf.writeBoolean(this.hasSnapshot);
        if (this.hasSnapshot) {
            this.snapshot.writeTo(buf);
        }
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.reply = buf.readBoolean();
        this.action = buf.readByte();
        this.ok = buf.readBoolean();
        this.text = RbmkSnapshot.readShortString(buf);
        this.x = buf.readInt();
        this.y = buf.readInt();
        this.z = buf.readInt();
        this.dim = buf.readInt();
        this.hasSnapshot = buf.readBoolean();
        this.snapshot = new RbmkSnapshot();
        if (this.hasSnapshot) {
            this.snapshot.readFrom(buf);
        }
    }

    // ---- handler ---------------------------------------------------------

    public static class Handler implements IMessageHandler<MessageRbmkPda, IMessage> {

        @Override
        public IMessage onMessage(final MessageRbmkPda msg, MessageContext ctx) {
            if (ctx.side == Side.SERVER) {
                final EntityPlayerMP player = ctx.getServerHandler().player;
                player.getServerWorld().addScheduledTask(() -> Handler.handleServer(player, msg));
                return null;
            }
            final MessageRbmkPda reply = msg;
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getMinecraft();
            mc.addScheduledTask(() -> {
                if (mc.currentScreen instanceof com.jiezhirbmkmod.client.gui.GuiRbmkPda) {
                    ((com.jiezhirbmkmod.client.gui.GuiRbmkPda) mc.currentScreen).onPdaPacket(reply);
                }
            });
            return null;
        }

        private static void handleServer(EntityPlayerMP player, MessageRbmkPda msg) {
            ItemStack held = ItemRbmkPda.findHeld(player);
            if (held.isEmpty()) {
                ModNetwork.CHANNEL.sendTo(MessageRbmkPda.reply(msg.action, false, "PDA 不在手上。"), player);
                return;
            }
            World world = player.getServerWorld();
            if (msg.action == MessageRbmkPda.ACTION_BIND) {
                BlockPos target = ItemRbmkPda.findBindTarget(player, world);
                if (target == null) {
                    ModNetwork.CHANNEL.sendTo(MessageRbmkPda.reply(msg.action, false,
                            "视线 32 格内没有 RBMK 控制台，靠近控制台再试。"), player);
                    return;
                }
                ItemRbmkPda.writeBound(held, target, world.provider.getDimension());
                MessageRbmkPda r = MessageRbmkPda.reply(msg.action, true,
                        "已绑定控制台 @ " + target.getX() + " " + target.getY() + " " + target.getZ() + "。");
                r.x = target.getX();
                r.y = target.getY();
                r.z = target.getZ();
                r.dim = world.provider.getDimension();
                ModNetwork.CHANNEL.sendTo(r, player);
                return;
            }
            if (msg.action == MessageRbmkPda.ACTION_UNBIND) {
                ItemRbmkPda.clearBound(held);
                ModNetwork.CHANNEL.sendTo(MessageRbmkPda.reply(msg.action, true, "已解除绑定。"), player);
                return;
            }
            Handler.sendSnapshot(player, held, msg.action);
        }

        private static void sendSnapshot(EntityPlayerMP player, ItemStack held, int action) {
            BlockPos pos = ItemRbmkPda.readBoundPos(held);
            if (pos == null) {
                ModNetwork.CHANNEL.sendTo(MessageRbmkPda.reply(action, false,
                        "尚未绑定控制台，先点「绑定控制台」。"), player);
                return;
            }
            World world = player.getServerWorld();
            if (ItemRbmkPda.readBoundDim(held) != world.provider.getDimension()) {
                ModNetwork.CHANNEL.sendTo(MessageRbmkPda.reply(action, false,
                        "绑定的控制台在另一个维度。"), player);
                return;
            }
            if (player.getDistanceSq((double) pos.getX() + 0.5D, (double) pos.getY() + 0.5D,
                    (double) pos.getZ() + 0.5D) > MessageRbmkPda.MAX_DISTANCE_SQ) {
                ModNetwork.CHANNEL.sendTo(MessageRbmkPda.reply(action, false,
                        "距离绑定的控制台超过 128 格。"), player);
                return;
            }
            TileEntity console = world.getTileEntity(pos);
            if (!RbmkBridge.isConsole(console)) {
                ModNetwork.CHANNEL.sendTo(MessageRbmkPda.reply(action, false,
                        "绑定的控制台已不存在，请重新绑定。"), player);
                return;
            }
            RbmkSnapshot data = new RbmkSnapshot();
            if (!RbmkBridge.read(console, data)) {
                ModNetwork.CHANNEL.sendTo(MessageRbmkPda.reply(action, false, "读取反应堆数据失败。"), player);
                return;
            }
            MessageRbmkPda r = MessageRbmkPda.reply(action, true, "");
            r.hasSnapshot = true;
            r.snapshot = data;
            r.x = pos.getX();
            r.y = pos.getY();
            r.z = pos.getZ();
            r.dim = world.provider.getDimension();
            ModNetwork.CHANNEL.sendTo(r, player);
        }
    }
}
