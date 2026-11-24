package com.jiezhirbmkmod.block;

import java.util.ArrayList;
import java.util.List;

import com.jiezhirbmkmod.block.TileRbmkMonitorScreen;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;

/**
 * v18.0 显示屏扫描中枢：绑屏（报警灯 / 巡屏中控 / 聚合中控）、
 * /rbmktrace 数据源、遥控器搜索 —— 以前是四处各写一遍"找附近的屏"，
 * 策略还各不相同（有的按区块、有的全表遍历 {@code loadedTileEntityList}）。
 * 现在统一到这一份：
 *
 * <ul>
 *   <li><b>只走已加载区块</b>（{@code getChunkProvider().getLoadedChunk}）：
 *       查询绝不加载新地形 —— v17.10 已把报警灯的同步加载修掉，这里把
 *       聚合中控/巡屏中控的同款隐患一起收编；</li>
 *   <li>按距离升序返回，"找最近"与"全收"共用同一份结果；</li>
 *   <li>含 {@link TileRbmkMonitorScreen} 全部子类（终端大屏/媒体屏等）。</li>
 * </ul>
 *
 * <p>调用方都是一次性手动操作（绑屏/查痕），不是每刻轮询，按区块扫
 * 3x3~5x5 的开销可忽略。</p>
 */
public final class ScreenScan {

    private ScreenScan() {
    }

    /**
     * 收集 {@code radius} 格内全部显示屏（只扫已加载区块），按与 center
     * 的距离升序排列。world/center 非法或 radius ≤ 0 返回空表。
     */
    public static List<TileRbmkMonitorScreen> collect(World world, BlockPos center, int radius) {
        List<TileRbmkMonitorScreen> out = new ArrayList<TileRbmkMonitorScreen>();
        if (world == null || center == null || radius <= 0) {
            return out;
        }
        int minX = center.getX() - radius;
        int maxX = center.getX() + radius;
        int minZ = center.getZ() - radius;
        int maxZ = center.getZ() + radius;
        int minY = center.getY() - radius;
        int maxY = center.getY() + radius;
        double maxSq = (double) radius * (double) radius;
        for (int cx = minX >> 4; cx <= maxX >> 4; ++cx) {
            for (int cz = minZ >> 4; cz <= maxZ >> 4; ++cz) {
                Chunk chunk;
                try {
                    chunk = world.getChunkProvider().getLoadedChunk(cx, cz);
                }
                catch (Throwable ignored) {
                    continue;
                }
                if (chunk == null) {
                    continue;
                }
                for (TileEntity te : chunk.getTileEntityMap().values()) {
                    if (!(te instanceof TileRbmkMonitorScreen)) {
                        continue;
                    }
                    BlockPos p = te.getPos();
                    if (p.getX() < minX || p.getX() > maxX || p.getZ() < minZ || p.getZ() > maxZ
                            || p.getY() < minY || p.getY() > maxY) {
                        continue;
                    }
                    if (p.distanceSq(center) > maxSq) {
                        continue;
                    }
                    out.add((TileRbmkMonitorScreen) te);
                }
            }
        }
        out.sort((a, b) -> Double.compare(
                a.getDistanceSq(center.getX() + 0.5D, center.getY() + 0.5D, center.getZ() + 0.5D),
                b.getDistanceSq(center.getX() + 0.5D, center.getY() + 0.5D, center.getZ() + 0.5D)));
        return out;
    }

    /** 最近的一块屏（没有返回 null）—— 报警灯绑定用 */
    public static BlockPos nearest(World world, BlockPos center, int radius) {
        List<TileRbmkMonitorScreen> all = ScreenScan.collect(world, center, radius);
        return all.isEmpty() ? null : all.get(0).getPos();
    }
}
