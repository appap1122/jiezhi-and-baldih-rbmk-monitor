package com.jiezhirbmkmod.network.msg;


import com.jiezhirbmkmod.network.NetGuard;
import com.jiezhirbmkmod.client.ClientJetState;
import io.netty.buffer.ByteBuf;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

public class MessageRbmkJetExhaust
implements IMessage {
    private UUID playerId;
    private boolean firing;
    private float heatRatio;

    public MessageRbmkJetExhaust() {
        this.playerId = new UUID(0L, 0L);
        this.heatRatio = 0.0f;
    }

    public MessageRbmkJetExhaust(EntityPlayer player, boolean firing, float heatRatio) {
        this.playerId = player.getUniqueID();
        this.firing = firing;
        this.heatRatio = heatRatio;
    }

    public void fromBytes(ByteBuf buf) {
        this.playerId = new UUID(buf.readLong(), buf.readLong());
        this.firing = buf.readBoolean();
        this.heatRatio = buf.readFloat();
    }

    public void toBytes(ByteBuf buf) {
        buf.writeLong(this.playerId.getMostSignificantBits());
        buf.writeLong(this.playerId.getLeastSignificantBits());
        buf.writeBoolean(this.firing);
        buf.writeFloat(this.heatRatio);
    }

    public UUID getPlayerId() {
        return this.playerId;
    }

    public boolean isFiring() {
        return this.firing;
    }

    public float getHeatRatio() {
        return this.heatRatio;
    }

    public static class Handler
    implements IMessageHandler<MessageRbmkJetExhaust, IMessage> {
        public IMessage onMessage(MessageRbmkJetExhaust message, MessageContext ctx) {
            try {
            Minecraft.getMinecraft().addScheduledTask(() -> {
                WorldClient w = Minecraft.getMinecraft().world;
                if (w == null) {
                    return;
                }
                ClientJetState.set(message.playerId, message.firing, message.heatRatio);
            });
            return null;
        
        } catch (Throwable t) {
            NetGuard.reportHandler("Handler", t);
            return null;
        }
        }
    }
}

