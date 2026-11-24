package com.jiezhirbmkmod.network.msg;

import com.jiezhirbmkmod.block.TileRbmkMonitorScreen;
import com.jiezhirbmkmod.dfc.AlertPolicy;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

public class MessageRbmkScreenPolicy
implements IMessage {
    private BlockPos pos;
    private final AlertPolicy policy = new AlertPolicy();

    public MessageRbmkScreenPolicy() {
        this.pos = BlockPos.ORIGIN;
    }

    public MessageRbmkScreenPolicy(BlockPos pos, AlertPolicy policy) {
        this.pos = pos;
        this.policy.setFrom(policy);
    }

    public void fromBytes(ByteBuf buf) {
        this.pos = new BlockPos(buf.readInt(), buf.readInt(), buf.readInt());
        this.policy.readFrom(buf);
    }

    public void toBytes(ByteBuf buf) {
        buf.writeInt(this.pos.getX());
        buf.writeInt(this.pos.getY());
        buf.writeInt(this.pos.getZ());
        this.policy.writeTo(buf);
    }

    public static class Handler
    implements IMessageHandler<MessageRbmkScreenPolicy, IMessage> {
        public IMessage onMessage(MessageRbmkScreenPolicy msg, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> {
                TileEntity te = player.world.getTileEntity(msg.pos);
                if (!(te instanceof TileRbmkMonitorScreen)) {
                    return;
                }
                TileRbmkMonitorScreen screen = (TileRbmkMonitorScreen) te;
                if (player.getDistanceSq((double) msg.pos.getX() + 0.5,
                        (double) msg.pos.getY() + 0.5,
                        (double) msg.pos.getZ() + 0.5) > 4096.0) {
                    return;
                }
                screen.applyPolicy(msg.policy);
                player.sendStatusMessage((ITextComponent) new TextComponentString(
                        TextFormatting.AQUA + "[jiezhirbmkmod] " + TextFormatting.RESET
                                + TextFormatting.GREEN + "已保存：汽笛"
                                + (screen.getPolicy().siren ? "开" : "关") + "，红石 "
                                + AlertPolicy.modeName(screen.getPolicy().rsMode) + " "
                                + AlertPolicy.valueText(screen.getPolicy().rsMode, screen.getPolicy().rsValue)
                                + (screen.getPolicy().rsInvert ? TextFormatting.YELLOW + "（已反相）" : "")), true);
            });
            return null;
        }
    }
}
