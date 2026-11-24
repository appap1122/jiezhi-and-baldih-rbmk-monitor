package com.jiezhirbmkmod.network.msg;

import java.util.ArrayList;
import java.util.List;

import com.jiezhirbmkmod.client.gui.GuiRbmkAggregate;

import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/**
 * v17.9 多堆芯聚合归属表同步（S→C，discriminator 22）。
 *
 * <p>与 {@link MessagePatrolSync} 同款思路：方块实体的绑定字段不同步到
 * 客户端，打开 GUI 前由服务端把「屏坐标 → 所属堆芯（控制台）坐标」
 * 成对下发（两份清单同序，至多 {@code TileRbmkAggregate.SLOTS} 对）；
 * 实时数据仍由客户端 GUI 读各屏自己的 Tile，本包只管归堆。</p>
 *
 * <p>线上格式：1 字节条数 N，随后 N 组 6 个 int（屏 x/y/z + 堆芯 x/y/z）。
 * 堆芯坐标不存在时与屏坐标相同（自成一堆的哨兵语义，客户端无需特判）。</p>
 */
public class MessageAggregateSync
implements IMessage {

    /** 每组 long[]{sx,sy,sz,rx,ry,rz} */
    private List<long[]> pairs = new ArrayList<long[]>();

    public MessageAggregateSync() {
    }

    public MessageAggregateSync(List<net.minecraft.util.math.BlockPos> screens,
            List<net.minecraft.util.math.BlockPos> reactors) {
        int n = Math.min(screens == null ? 0 : screens.size(),
                reactors == null ? 0 : reactors.size());
        for (int i = 0; i < n; ++i) {
            net.minecraft.util.math.BlockPos s = screens.get(i);
            net.minecraft.util.math.BlockPos r = reactors.get(i);
            if (s != null && r != null) {
                this.pairs.add(new long[]{s.getX(), s.getY(), s.getZ(),
                        r.getX(), r.getY(), r.getZ()});
            }
        }
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        // 严谨钳位：伪造/异版包给出负数或超大条数时不能让它炸 netty 线程
        int n = Math.max(0, Math.min(com.jiezhirbmkmod.block.TileRbmkAggregate.SLOTS, buf.readByte()));
        this.pairs = new ArrayList<long[]>(n);
        for (int i = 0; i < n; ++i) {
            this.pairs.add(new long[]{buf.readInt(), buf.readInt(), buf.readInt(),
                    buf.readInt(), buf.readInt(), buf.readInt()});
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeByte(this.pairs.size());
        for (int i = 0; i < this.pairs.size(); ++i) {
            long[] p = this.pairs.get(i);
            buf.writeInt((int) p[0]);
            buf.writeInt((int) p[1]);
            buf.writeInt((int) p[2]);
            buf.writeInt((int) p[3]);
            buf.writeInt((int) p[4]);
            buf.writeInt((int) p[5]);
        }
    }

    public static class Handler
    implements IMessageHandler<MessageAggregateSync, IMessage> {
        @Override
        public IMessage onMessage(MessageAggregateSync msg, MessageContext ctx) {
            net.minecraft.client.Minecraft.getMinecraft().addScheduledTask(() -> {
                GuiRbmkAggregate.acceptPairs(msg.pairs);
            });
            return null;
        }
    }
}
