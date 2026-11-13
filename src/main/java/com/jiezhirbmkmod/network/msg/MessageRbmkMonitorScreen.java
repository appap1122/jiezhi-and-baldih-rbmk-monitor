package com.jiezhirbmkmod.network.msg;

import com.jiezhirbmkmod.block.TileRbmkMonitorScreen;
import com.jiezhirbmkmod.dfc.AlertPolicy;
import com.jiezhirbmkmod.dfc.RbmkSnapshot;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

public class MessageRbmkMonitorScreen
implements IMessage {
    private BlockPos pos;
    private boolean powered;
    private int tilt;
    private int refreshMs;
    private final RbmkSnapshot snapshot = new RbmkSnapshot();
    private List<String> events = new ArrayList<String>();
    private final AlertPolicy policy = new AlertPolicy();
    private int alertLevel;
    private String alertReason = "";
    private int rsPower;

    public MessageRbmkMonitorScreen() {
        this.pos = BlockPos.ORIGIN;
    }

    public MessageRbmkMonitorScreen(TileRbmkMonitorScreen tile) {
        this.pos = tile.getPos();
        this.powered = tile.isPowered();
        this.tilt = tile.getTilt();
        this.refreshMs = tile.getRefreshMs();
        if (tile.getData() != null) {
            this.snapshot.copyFrom(tile.getData());
        }
        this.events = new ArrayList<String>(tile.getEvents());
        this.policy.setFrom(tile.getPolicy());
        this.alertLevel = tile.getAlertLevel();
        this.alertReason = tile.getAlertReason();
        this.rsPower = tile.redstonePower();
    }

    public void fromBytes(ByteBuf buf) {
        this.pos = new BlockPos(buf.readInt(), buf.readInt(), buf.readInt());
        this.powered = buf.readBoolean();
        this.tilt = buf.readInt();
        this.refreshMs = buf.readInt();
        this.snapshot.readFrom(buf);
        this.events.clear();
        int n = buf.readInt();
        for (int i = 0; i < n; ++i) {
            int len = buf.readInt();
            StringBuilder sb = new StringBuilder(len);
            for (int j = 0; j < len; ++j) {
                sb.append(buf.readChar());
            }
            this.events.add(sb.toString());
        }
        this.policy.readFrom(buf);
        this.alertLevel = buf.readInt();
        this.alertReason = RbmkSnapshot.readShortString(buf);
        this.rsPower = buf.readInt();
    }

    public void toBytes(ByteBuf buf) {
        buf.writeInt(this.pos.getX());
        buf.writeInt(this.pos.getY());
        buf.writeInt(this.pos.getZ());
        buf.writeBoolean(this.powered);
        buf.writeInt(this.tilt);
        buf.writeInt(this.refreshMs);
        this.snapshot.writeTo(buf);
        buf.writeInt(this.events.size());
        for (String s : this.events) {
            buf.writeInt(s.length());
            for (int j = 0; j < s.length(); ++j) {
                buf.writeChar((int)s.charAt(j));
            }
        }
        this.policy.writeTo(buf);
        buf.writeInt(this.alertLevel);
        RbmkSnapshot.writeShortString(buf, this.alertReason);
        buf.writeInt(this.rsPower);
    }

    public static class Handler
    implements IMessageHandler<MessageRbmkMonitorScreen, IMessage> {
        public IMessage onMessage(final MessageRbmkMonitorScreen msg, MessageContext ctx) {
            Minecraft.getMinecraft().addScheduledTask(new Runnable(){

                @Override
                public void run() {
                    TileEntity te = Minecraft.getMinecraft().world.getTileEntity(msg.pos);
                    if (te instanceof TileRbmkMonitorScreen) {
                        TileRbmkMonitorScreen t = (TileRbmkMonitorScreen) te;
                        t.applyServerState(msg.powered, msg.tilt, msg.snapshot, msg.events);
                        t.applyAlertState(msg.policy, msg.alertLevel, msg.alertReason, msg.rsPower);
                    }
                }
            });
            return null;
        }
    }
}

