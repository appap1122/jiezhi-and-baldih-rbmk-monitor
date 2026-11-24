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
    /** v17.4 real-screen mains state: 0 off, 1 standby, 2 on. */
    private int realPowerState;

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
        this.realPowerState = tile.getRealPowerState();
    }

    public void fromBytes(ByteBuf buf) {
        this.pos = new BlockPos(buf.readInt(), buf.readInt(), buf.readInt());
        this.powered = buf.readBoolean();
        // v17.8 瘦身：四个小范围整型从 int 降到 byte（省 12 字节/包）
        // tilt 0..3 / alertLevel 0..4 / rsPower 0..15 / realPowerState 0..2
        this.tilt = buf.readUnsignedByte();
        this.refreshMs = buf.readInt();
        this.snapshot.readFrom(buf);
        this.events.clear();
        int n = buf.readInt();
        for (int i = 0; i < n; ++i) {
            int len = buf.readInt();
            StringBuilder sb = new StringBuilder(len);
            for (int j = 0; j < len; j++) {
                sb.append(buf.readChar());
            }
            this.events.add(sb.toString());
        }
        this.policy.readFrom(buf);
        this.alertLevel = buf.readUnsignedByte();
        this.alertReason = RbmkSnapshot.readShortString(buf);
        this.rsPower = buf.readUnsignedByte();
        this.realPowerState = buf.readUnsignedByte();
    }

    public void toBytes(ByteBuf buf) {
        buf.writeInt(this.pos.getX());
        buf.writeInt(this.pos.getY());
        buf.writeInt(this.pos.getZ());
        buf.writeBoolean(this.powered);
        buf.writeByte(this.tilt);
        buf.writeInt(this.refreshMs);
        this.snapshot.writeTo(buf);
        buf.writeInt(this.events.size());
        for (String s : this.events) {
            buf.writeInt(s.length());
            for (int j = 0; j < s.length(); j++) {
                buf.writeChar((int)s.charAt(j));
            }
        }
        this.policy.writeTo(buf);
        buf.writeByte(this.alertLevel);
        RbmkSnapshot.writeShortString(buf, this.alertReason);
        buf.writeByte(this.rsPower);
        buf.writeByte(this.realPowerState);
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
                        // v17.4: the tuner item changes the server-side interval; the
                        // canvas has to follow, otherwise the picture keeps its old pace
                        t.setClientRefreshMs(msg.refreshMs);
                        t.applyServerState(msg.powered, msg.tilt, msg.snapshot, msg.events,
                                msg.realPowerState);
                        t.applyAlertState(msg.policy, msg.alertLevel, msg.alertReason, msg.rsPower);
                    }
                }
            });
            return null;
        }
    }
}

