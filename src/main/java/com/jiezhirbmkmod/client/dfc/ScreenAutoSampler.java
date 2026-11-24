package com.jiezhirbmkmod.client.dfc;

import com.jiezhirbmkmod.block.RealPower;
import com.jiezhirbmkmod.block.TileRbmkMonitorScreen;

/**
 * v18.0 修订 曲线历史自动记录器（仅客户端）。
 *
 * <p>历史意义：旧版采样挂在渲染路径上 —— 必须"有人看着屏 + 屏开着机 +
 * 停在曲线页 + 开机动画放完"四条全中，曲线历史才往前走。结果就是玩家
 * 放好屏、绑好数据源，导出/回放里却是"历史为空"，要人蹲在屏前翻到
 * 曲线页干等几十秒才开始积累。</p>
 *
 * <p>现在采样挂在客户端 tick 上：屏一放置 / 区块一加载，{@code update()}
 * 客户端分支就调 {@link #sample}，按 {@code GraphHistory.SAMPLE_MS} 节奏
 * 自动记录，跟有没有人看、翻到哪一页完全无关。渲染只负责画。</p>
 *
 * <p>断电不记录：真实版屏断电（或普通屏被切到待机）时按同样节奏推
 * 空格 —— 曲线上留下断口，表示"这段时间没有观测数据"。</p>
 *
 * <p>为什么不标 {@code @SideOnly(CLIENT)}：本类只从
 * {@code TileRbmkMonitorScreen.update()} 的 {@code world.isRemote} 分支被
 * 调用，专用服务器永远走不到那一行，类加载是惰性的 —— 标注反而会让
 * FML 在服务端把方法剥掉，多一层"万一被执行"的心智负担。类本体不引用
 * 任何客户端 API 的静态状态，加载无害。</p>
 */
public final class ScreenAutoSampler {

    private ScreenAutoSampler() {
    }

    /**
     * 每客户端 tick 调一次（内部按采样间隔节流）。给曲线/轮播屏等一切
     * 带 canvas 的屏用；终端屏的历史顺带积累，无副作用。
     */
    public static void sample(TileRbmkMonitorScreen te) {
        if (te == null || te.getWorld() == null || !te.getWorld().isRemote
                || te.getPos() == null) {
            return;
        }
        boolean canRecord = te.isRealScreen()
                ? te.getRealPowerState() == RealPower.ON
                : te.isPowered();
        com.jiezhirbmkmod.client.dfc.RbmkScreenCanvas c = te.canvas();
        if (c != null) {
            c.autoSample(te.getData(), canRecord);
        }
    }
}
