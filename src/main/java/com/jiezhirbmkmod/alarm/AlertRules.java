package com.jiezhirbmkmod.alarm;

import com.jiezhirbmkmod.dfc.RbmkConfig;
import com.jiezhirbmkmod.dfc.RbmkSnapshot;

/**
 * 「显示屏数据 → 警示灯颜色」的唯一真源（纯函数，无 Minecraft 渲染依赖）。
 *
 * <h3>为什么单独抽出来</h3>
 * 这条映射有五个来源（熔毁 / 柱温 / 温升 / 中子通量 / 熔毁风险）和四级输出，
 * 如果写进方块实体的 {@code update()} 里，就只能进游戏一格一格配色去试。
 * 抽成纯函数之后，离线自测可以直接 {@code evaluate(snapshot)} 断言每一级，
 * 而且改阈值时只动这一个文件。
 *
 * <h3>四级与配色（沿用原版 jiezhimod 5.6 的语义）</h3>
 * <table>
 *   <tr><th>等级</th><th>颜色</th><th>典型触发</th><th>转速</th></tr>
 *   <tr><td>0 正常</td><td>（熄灭）</td><td>—</td><td>0</td></tr>
 *   <tr><td>1 警告</td><td>黄</td><td>柱温到预警线、温升过快、中子通量偏高</td><td>10</td></tr>
 *   <tr><td>2 危急</td><td>橙</td><td>柱温冲进预警线与严重线之间、熔毁风险 ≥70%</td><td>30</td></tr>
 *   <tr><td>3 熔毁</td><td>红</td><td>已熔毁、柱温越过严重线 / 柱温上限、风险 ≥85%</td><td>90</td></tr>
 * </table>
 *
 * <p>同级冲突时**按检查顺序取先出现的**（越靠前越"根因"）：熔毁 &gt; 柱温 &gt;
 * 温升 &gt; 通量 &gt; 风险。</p>
 *
 * <p>★ v17.7 适配说明：原版吃的是 DFC 的 {@code DfcSnapshot}（熔点 / 引爆器 / 激光部件），
 * 本模组没有那套数据，这里换成 RBMK 的 {@link RbmkSnapshot}。
 * 阈值不另立一套，直接复用屏幕上那份 {@code AlertPolicy} 的 warnC / critC，
 * 保证"屏幕变黄的那一刻灯也变黄"，不会出现两个界面各说各话。</p>
 */
public final class AlertRules {

    // ==================== 等级 ====================
    public static final int OK = 0;
    public static final int WARN = 1;
    public static final int HIGH = 2;
    public static final int CRIT = 3;

    // ==================== 配色（RGB，直接进 AlarmConfig.color） ====================
    /** 正常：绿。灯在这种状态下是**熄灭**的，这个颜色只给 GUI / 预览用 */
    public static final int COLOR_OK = 0x2ECC71;
    public static final int COLOR_WARN = 0xFFD23F;
    public static final int COLOR_HIGH = 0xFF8A00;
    public static final int COLOR_CRIT = 0xFF1A1A;

    // ==================== 转速（rpm）：越严重转得越快 ====================
    public static final int RPM_OK = 0;
    public static final int RPM_WARN = 10;
    public static final int RPM_HIGH = 30;
    public static final int RPM_CRIT = 90;

    private static final AlertSignal OK_SIGNAL = new AlertSignal(OK, "ok", "");

    private AlertRules() {
    }

    // ==================== 对外 ====================

    /** 用模组默认阈值判定（单盏灯、没有屏幕策略时走这条） */
    public static AlertSignal evaluate(RbmkSnapshot d) {
        return evaluate(d, RbmkConfig.defaultWarnC, RbmkConfig.defaultCritC,
                RbmkConfig.defaultRateWarnD, RbmkConfig.defaultFluxWarnF);
    }

    /**
     * 判定一份快照的告警等级。
     *
     * <p>屏幕没连数据源（{@code !linked}）或数据源离线时不报警 —— 那只是"还没绑核心"
     * 或"扫不到柱子"，不是故障；否则刚放下的灯就会一直红着。</p>
     *
     * @param warnC 预警柱温（°C），≤0 时回落默认值
     * @param critC 严重柱温（°C），≤0 时回落默认值
     * @param rateD 温升阈值（0.1°C/s），≤0 时回落默认值
     * @param fluxF 中子通量阈值（F），≤0 时回落默认值
     */
    public static AlertSignal evaluate(RbmkSnapshot d, int warnC, int critC, int rateD, int fluxF) {
        if (d == null || !d.linked || d.offline) {
            return OK_SIGNAL;
        }
        int warn = warnC > 0 ? warnC : RbmkConfig.defaultWarnC;
        int crit = critC > warn ? critC : Math.max(warn + 1, RbmkConfig.defaultCritC);
        int rate = rateD > 0 ? rateD : RbmkConfig.defaultRateWarnD;
        int flux = fluxF > 0 ? fluxF : RbmkConfig.defaultFluxWarnF;

        // ---- 3 级：熔毁 / 越线 ----
        if (d.meltdown) {
            return new AlertSignal(CRIT, "meltdown", heat(d.maxHeat));
        }
        if (d.maxHeatLimit > 0.0D && d.maxHeat >= d.maxHeatLimit) {
            return new AlertSignal(CRIT, "overheat", heat(d.maxHeat) + " / " + heat(d.maxHeatLimit));
        }
        if (d.maxHeat >= crit) {
            return new AlertSignal(CRIT, "melt", heat(d.maxHeat) + " / " + heat(crit));
        }
        if (d.meltRisk >= 0.85D) {
            return new AlertSignal(CRIT, "risk", percent(d.meltRisk));
        }

        // ---- 2 级：冲进"预警线 ~ 严重线"的上半段 ----
        //   只有柱温会落到这一档。熔毁风险不参与细分：屏幕那边 risk≥0.70 就是黄的，
        //   灯这边如果给橙色，同一个反应堆会出现"屏幕黄、灯橙"的分裂。
        double highC = warn + (crit - warn) * 0.6D;
        if (d.maxHeat >= highC) {
            return new AlertSignal(HIGH, "hot", heat(d.maxHeat) + " / " + heat((int) Math.round(highC)));
        }

        // ---- 1 级：预警（与屏幕 AlertPolicy 同一组条件） ----
        if (d.maxHeat >= warn) {
            return new AlertSignal(WARN, "warm", heat(d.maxHeat) + " / " + heat(warn));
        }
        if (d.tempDelta * 10.0D >= (double) rate) {
            return new AlertSignal(WARN, "rate", rate(d.tempDelta));
        }
        if (d.flux >= flux) {
            return new AlertSignal(WARN, "flux", d.flux + " F");
        }
        if (d.meltRisk >= 0.70D) {
            return new AlertSignal(WARN, "risk", percent(d.meltRisk));
        }
        return OK_SIGNAL;
    }

    public static int colorFor(int level) {
        switch (level) {
            case CRIT:
                return COLOR_CRIT;
            case HIGH:
                return COLOR_HIGH;
            case WARN:
                return COLOR_WARN;
            default:
                return COLOR_OK;
        }
    }

    public static int rpmFor(int level) {
        switch (level) {
            case CRIT:
                return RPM_CRIT;
            case HIGH:
                return RPM_HIGH;
            case WARN:
                return RPM_WARN;
            default:
                return RPM_OK;
        }
    }

    /** 正常时灯是灭的（真实警报器待机不亮），告警才点亮 */
    public static boolean litFor(int level) {
        return level > OK;
    }

    // ==================== 明细格式化（只给聊天栏 / 日志看） ====================

    private static String heat(double c) {
        return Math.round(c) + "°C";
    }

    private static String percent(double v) {
        return Math.round(v * 100.0D) + "%";
    }

    private static String rate(double deltaPerTick) {
        return String.format(java.util.Locale.ROOT, "%.1f°C/s", deltaPerTick * 10.0D);
    }
}
