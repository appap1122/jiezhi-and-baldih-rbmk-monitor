package com.jiezhirbmkmod.network.msg;


import com.jiezhirbmkmod.network.NetGuard;
import java.util.ArrayList;
import java.util.List;

import com.jiezhirbmkmod.client.gui.GuiRbmkPatrol;

import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/**
 * v17.7 巡屏绑定清单同步（S→C）。方块实体的绑定字段不同步到客户端，
 * 打开 GUI 前由服务端把有效绑定（至多 4 个坐标）下发；客户端 GUI 收包
 * 后存进静态暂存区，下一帧就能画。
 */
public class MessagePatrolSync
implements IMessage {

    private List<long[]> screens = new ArrayList<long[]>();

    public MessagePatrolSync() {
    }

    public MessagePatrolSync(List<net.minecraft.util.math.BlockPos> list) {
        if (list == null) {
            return;
        }
        for (int i = 0; i < list.size() && i < 4; ++i) {
            net.minecraft.util.math.BlockPos p = list.get(i);
            if (p != null) {
                this.screens.add(new long[]{p.getX(), p.getY(), p.getZ()});
            }
        }
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        // 严谨钳位：负数条数会让 ArrayList 的 initialCapacity 抛
        // IllegalArgumentException（netty 线程里直接踢线）—— 17.9 起同款加固
        int n = Math.max(0, Math.min(4, buf.readByte()));
        this.screens = new ArrayList<long[]>(n);
        for (int i = 0; i < n; ++i) {
            this.screens.add(new long[]{buf.readInt(), buf.readInt(), buf.readInt()});
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeByte(this.screens.size());
        for (int i = 0; i < this.screens.size(); ++i) {
            long[] p = this.screens.get(i);
            buf.writeInt((int) p[0]);
            buf.writeInt((int) p[1]);
            buf.writeInt((int) p[2]);
        }
    }

    public static class Handler
    implements IMessageHandler<MessagePatrolSync, IMessage> {
        @Override
        public IMessage onMessage(MessagePatrolSync msg, MessageContext ctx) {
            try {
            net.minecraft.client.Minecraft.getMinecraft().addScheduledTask(() -> {
                GuiRbmkPatrol.acceptBound(msg.screens);
            });
            return null;
        
        } catch (Throwable t) {
            NetGuard.reportHandler("Handler", t);
            return null;
        }
        }
    }
}
