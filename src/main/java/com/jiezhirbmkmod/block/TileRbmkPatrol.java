package com.jiezhirbmkmod.block;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ITickable;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * v17.7 巡屏中控（rbmk_patrol）：一块 1x1 的"值班长席位"。
 *
 * <p>绑定最多 4 块 RBMK 显示屏（含真实屏/自定义/终端等所有屏变体），
 * GUI 里一格一块看总览；开自动轮播后客户端 GUI 每 5 秒切换选中，
 * 任何一块屏的告警等级升高时立刻跳到那块 —— 大电站十几块屏不用再
 * 跑着看。</p>
 *
 * <p>绑定动作在<b>服务端</b>：潜行 + 右键中控扫描半径 {@link #SCAN_RADIUS}
 * 内最近的、未绑定的屏（v18.0 起统一走 {@link ScreenScan#collect}：
 * 只扫已加载区块、绝不加载新地形、按距离排序取前 4 填空槽）。扫描是手动
 * 触发的一次性操作，不是每刻轮询。绑定结果进 NBT 落盘；GUI 打开时由服务端把清单经
 * {@code MessagePatrolSync} 下发（方块实体字段不同步到客户端，
 * 坐标也塞不进 16 位的窗口属性，专用包是唯一正路）。</p>
 *
 * <p>实时数据<b>不经过中控转发</b>：GUI 直接读客户端世界里那几块屏
 * 自己的 Tile（屏的数据包本来就同步到客户端），中控只是一个目录，
 * 断电的屏显示"离线"，被拆的屏在客户端静默剔除。</p>
 */
public class TileRbmkPatrol
extends TileEntity
implements ITickable {

    /** 最多绑 4 块屏：GUI 一屏四行，再多就该造第二台中控了 */
    public static final int SLOTS = 4;
    /** 潜行右键扫描半径（方块距离） */
    public static final int SCAN_RADIUS = 24;

    private final BlockPos[] screens = new BlockPos[TileRbmkPatrol.SLOTS];

    // ---- 绑定 ----

    /** 潜行右键扫描：把半径内最近的未绑定屏填进空槽，返回本次新绑的块数 */
    public int scanBind(World world, BlockPos center) {
        if (world == null || world.isRemote) {
            return 0;
        }
        // 先剔除已失效的绑定，腾出空槽
        for (int i = 0; i < TileRbmkPatrol.SLOTS; ++i) {
            if (this.screens[i] != null
                    && !(world.getTileEntity(this.screens[i]) instanceof TileRbmkMonitorScreen)) {
                this.screens[i] = null;
            }
        }
        // v18.0：扫描统一走 ScreenScan（只扫已加载区块、距离升序）——
        // 原来遍历 world.loadedTileEntityList 是全服 TE 表，人口多的服
        // 一次扫描要把几千个 TE 摸一遍；按区块扫 3x3 顶多几百个。
        List<TileRbmkMonitorScreen> found =
                ScreenScan.collect(world, center, TileRbmkPatrol.SCAN_RADIUS);
        int bound = 0;
        for (TileRbmkMonitorScreen s : found) {
            BlockPos p = s.getPos();
            boolean already = false;
            for (int i = 0; i < TileRbmkPatrol.SLOTS; ++i) {
                if (p.equals(this.screens[i])) {
                    already = true;
                    break;
                }
            }
            if (already) {
                continue;
            }
            int slot = -1;
            for (int i = 0; i < TileRbmkPatrol.SLOTS; ++i) {
                if (this.screens[i] == null) {
                    slot = i;
                    break;
                }
            }
            if (slot < 0) {
                break; // 4 个槽都满了
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
        for (int i = 0; i < TileRbmkPatrol.SLOTS; ++i) {
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

    // ---- NBT ----

    @Override
    public void readFromNBT(NBTTagCompound c) {
        super.readFromNBT(c);
        for (int i = 0; i < TileRbmkPatrol.SLOTS; ++i) {
            this.screens[i] = null;
        }
        int n = c.getInteger("BoundCount");
        for (int i = 0; i < Math.min(n, TileRbmkPatrol.SLOTS); ++i) {
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
        for (int i = 0; i < TileRbmkPatrol.SLOTS; ++i) {
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
