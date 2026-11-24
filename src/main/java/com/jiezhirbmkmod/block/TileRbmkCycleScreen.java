package com.jiezhirbmkmod.block;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ITickable;

/**
 * v18.0 画面轮播显示屏：在「总览 → 热图 → 曲线」三个页面间按
 * {@code cycleInterval} 自动轮播的监控屏。
 *
 * <p>继承 {@link TileRbmkMonitorScreen}：绑定/刷新/报警/真实版供电
 * 一整套照旧工作。轮播只额外做两件事——服务端按间隔推进
 * {@code currentPage} 并随常规刷新同步；客户端同步渲染页号
 * （{@link #pageForRender()} 把页号翻译成渲染器的 1=总览 / 2=热图 /
 * 4=曲线）。真实版方块（{@link BlockRbmkCycleScreenReal}）经
 * {@code IRealScreen} 标记自动获得主控供电判定：红石不足 10 级且
 * HE/FE 双空时 {@code realPowerState} 被强制 OFF，渲染器对真实屏
 * 直接不画 —— 即需求里的「未通电强制黑屏」。</p>
 */
public class TileRbmkCycleScreen extends TileRbmkMonitorScreen implements ITickable {

    /** 轮播页数：0=总览, 1=热图, 2=曲线 */
    public static final int PAGE_COUNT = 3;

    /** 轮播间隔（tick），默认 60 = 3 秒；合法范围 10..1200 */
    private int cycleInterval = 60;
    /** 当前页（0..PAGE_COUNT-1），随存档保存并同步客户端 */
    private int currentPage = 0;
    /** 服务端轮播计时器 */
    private int cycleTimer = 0;

    /** 轮播间隔（tick），10..1200 之外钳位。 */
    public int getCycleInterval() {
        return this.cycleInterval;
    }

    public void setCycleInterval(int ticks) {
        this.cycleInterval = Math.max(10, Math.min(1200, ticks));
        this.markDirty();
    }

    /** 当前页号 0..2（0=总览，1=热图，2=曲线）。 */
    public int getCurrentPage() {
        return this.currentPage;
    }

    public void setCurrentPage(int page) {
        this.currentPage = ((page % PAGE_COUNT) + PAGE_COUNT) % PAGE_COUNT;
    }

    /** 渲染器用：把轮播页号翻译成 RbmkScreenCanvas 的页面代码。 */
    public int pageForRender() {
        switch (this.currentPage) {
            case 1:
                return 2;   // 热图（网格页）
            case 2:
                return 4;   // 曲线
            case 0:
            default:
                return 1;   // 总览
        }
    }

    @Override
    public void update() {
        // 父类 tick：刷新/供电/警笛全套照旧（客户端侧父类自会早退）
        super.update();
        if (this.world == null) {
            return;
        }
        if (this.world.isRemote) {
            return; // 页号以服务端同步为准，客户端不做本地推演
        }
        if (++this.cycleTimer >= this.cycleInterval) {
            this.cycleTimer = 0;
            this.setCurrentPage(this.currentPage + 1);
            // 页号写进 NBT（markDirty），随父类周期刷新包下发给客户端
            this.markDirty();
        }
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound c) {
        super.writeToNBT(c);
        c.setInteger("CycleInterval", this.cycleInterval);
        c.setInteger("CurrentPage", this.currentPage);
        return c;
    }

    @Override
    public void readFromNBT(NBTTagCompound c) {
        super.readFromNBT(c);
        // 旧存档/坏值一律钳位回默认，绝不让伪造 NBT 把页号推成怪值
        this.cycleInterval = Math.max(10, Math.min(1200, c.getInteger("CycleInterval")));
        if (!c.hasKey("CycleInterval")) {
            this.cycleInterval = 60;
        }
        this.setCurrentPage(c.hasKey("CurrentPage") ? c.getInteger("CurrentPage") : 0);
        this.cycleTimer = 0;
    }
}
