package com.jiezhirbmkmod.block;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ITickable;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * v17.9 多堆芯聚合中控（rbmk_aggregate）：中控室大屏的"值班站长席"。
 *
 * <p>绑定至多 {@link #SLOTS} 块 RBMK 显示屏（与巡屏中控同一套扫描绑定，
 * 槽位多两个），按「绑定的控制台坐标」把屏归堆 —— 同一个控制台 =
 * 同一座堆芯；没绑控制台的屏自成一堆。GUI 由此算出多堆芯排行：
 * 平均柱温降序排名、总通量合计、最热堆芯高亮 —— "核电站中控"的
 * 招牌视图。</p>
 *
 * <p>实时数据<b>不经过中控转发</b>：与巡屏中控同理，GUI 直接读客户端
 * 世界里各屏自己的 Tile（屏的数据包本来就同步到客户端），中控只维护
 * 「屏 → 堆芯（控制台）」的归属表，归属表在服务端算好、开 GUI 前经
 * {@code MessageAggregateSync} 下发 —— 屏的 consolePos 字段不同步到
 * 客户端，归堆必须在服务端做。</p>
 *
 * <p>绑定动作在<b>服务端</b>：潜行 + 右键扫描半径 {@link #SCAN_RADIUS}
 * 内最近的、未绑定的屏（v18.0 起统一走 {@link ScreenScan#collect}：
 * 只扫已加载区块、绝不加载新地形、距离升序）。扫描是一次性的手动操作，
 * 不进 tick。绑定结果进 NBT 落盘。</p>
 */
public class TileRbmkAggregate
extends TileEntity
implements ITickable {

    /** 最多绑 6 块屏：宫格排行 2x3 一屏放下 */
    public static final int SLOTS = 6;
    /** 潜行右键扫描半径（方块距离），与巡屏中控一致 */
    public static final int SCAN_RADIUS = 24;

    private final BlockPos[] screens = new BlockPos[TileRbmkAggregate.SLOTS];

    // ---- 绑定 ----

    /** 潜行右键扫描：把半径内最近的未绑定屏填进空槽，返回本次新绑的块数 */
    public int scanBind(World world, BlockPos center) {
        if (world == null || world.isRemote) {
            return 0;
        }
        // 先剔除已失效的绑定，腾出空槽
        for (int i = 0; i < TileRbmkAggregate.SLOTS; ++i) {
            if (this.screens[i] != null
                    && !(world.getTileEntity(this.screens[i]) instanceof TileRbmkMonitorScreen)) {
                this.screens[i] = null;
            }
        }
        // v18.0：扫描统一走 ScreenScan（只扫已加载区块、距离升序），
        // 与巡屏中控/报警灯/trace 同源；不再遍历全服 TE 表
        List<TileRbmkMonitorScreen> found =
                ScreenScan.collect(world, center, TileRbmkAggregate.SCAN_RADIUS);
        int bound = 0;
        for (TileRbmkMonitorScreen s : found) {
            BlockPos p = s.getPos();
            boolean already = false;
            for (int i = 0; i < TileRbmkAggregate.SLOTS; ++i) {
                if (p.equals(this.screens[i])) {
                    already = true;
                    break;
                }
            }
            if (already) {
                continue;
            }
            int slot = -1;
            for (int i = 0; i < TileRbmkAggregate.SLOTS; ++i) {
                if (this.screens[i] == null) {
                    slot = i;
                    break;
                }
            }
            if (slot < 0) {
                break; // 槽满了
            }
            this.screens[slot] = p;
            ++bound;
        }
        if (bound > 0) {
            this.markDirty();
        }
        return bound;
    }

    /** 有效绑定清单（剔除指向已拆除/未加载屏的槽位），供下发与 GUI 用 */
    public List<BlockPos> validScreens() {
        List<BlockPos> out = new ArrayList<BlockPos>();
        for (int i = 0; i < TileRbmkAggregate.SLOTS; ++i) {
            BlockPos p = this.screens[i];
            if (p == null) {
                continue;
            }
            if (this.world == null
                    || !(this.world.getTileEntity(p) instanceof TileRbmkMonitorScreen)) {
                continue; // 拆掉的屏不上报；绑定位保留，重扫时自动腾挪
            }
            out.add(p);
        }
        return out;
    }

    /**
     * 归堆表：与 validScreens 同序，返回每块屏所属堆芯的控制台坐标；
     * 没绑控制台的屏返回它自己的坐标（自成一堆）。
     * 只在服务端调用 —— consolePos 是服务端字段。
     */
    public List<BlockPos> reactorKeys() {
        List<BlockPos> out = new ArrayList<BlockPos>();
        for (BlockPos p : this.validScreens()) {
            TileEntity te = this.world == null ? null : this.world.getTileEntity(p);
            BlockPos key = p;
            if (te instanceof TileRbmkMonitorScreen) {
                // effectiveConsole = 显式控制台，缺省落到绑定的数据源本身
                BlockPos key2 = ((TileRbmkMonitorScreen) te).effectiveConsole();
                if (key2 != null) {
                    key = key2;
                }
            }
            out.add(key);
        }
        return out;
    }

    // ---- NBT ----

    @Override
    public void readFromNBT(NBTTagCompound c) {
        super.readFromNBT(c);
        for (int i = 0; i < TileRbmkAggregate.SLOTS; ++i) {
            this.screens[i] = null;
        }
        int n = c.getInteger("BoundCount");
        for (int i = 0; i < Math.min(n, TileRbmkAggregate.SLOTS); ++i) {
            String base = "S" + i;
            if (c.hasKey(base + "X")) {
                this.screens[i] = new BlockPos(c.getInteger(base + "X"),
                        c.getInteger(base + "Y"), c.getInteger(base + "Z"));
            }
        }
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound c) {
        super.writeToNBT(c);
        int n = 0;
        for (int i = 0; i < TileRbmkAggregate.SLOTS; ++i) {
            if (this.screens[i] != null) {
                String base = "S" + i;
                c.setInteger(base + "X", this.screens[i].getX());
                c.setInteger(base + "Y", this.screens[i].getY());
                c.setInteger(base + "Z", this.screens[i].getZ());
                n = i + 1;
            }
        }
        c.setInteger("BoundCount", n);
        return c;
    }

    @Override
    public void update() {
        // 中控本体没有每刻逻辑：数据由各屏自己的同步链路走
    }
}
