package com.jiezhirbmkmod.dfc;

import io.netty.buffer.Unpooled;
import net.minecraft.nbt.NBTTagCompound;

public final class AlertPolicyTest {

    private static int passed = 0;
    private static int failed = 0;

    private AlertPolicyTest() {
    }

    public static void main(String[] args) {
        levelFromThresholds();
        offlineIsSafe();
        meltdownIsCritical();
        clampKeepsCritAboveWarn();
        redstoneModes();
        codecRoundTrip();
        copyFromCopiesEverything();
        nbtRoundTrip();
        reasonMatchesCause();
        debounceBlocksOscillation();
        System.out.println();
        System.out.println("PASS " + passed + " / FAIL " + failed);
        if (failed > 0) {
            System.exit(1);
        }
    }

    // ---- helpers ----

    private static void check(String name, boolean ok) {
        if (ok) {
            ++passed;
            System.out.println("  ok   " + name);
        } else {
            ++failed;
            System.out.println("  FAIL " + name);
        }
    }

    private static RbmkSnapshot data(double maxHeat, double limit, double rate, int flux, boolean meltdown) {
        RbmkSnapshot d = new RbmkSnapshot();
        d.linked = true;
        d.offline = false;
        d.maxHeat = maxHeat;
        d.maxHeatLimit = limit;
        d.avgHeat = maxHeat * 0.9;
        d.tempDelta = rate;
        d.flux = flux;
        d.meltdown = meltdown;
        d.meltRisk = limit > 0.0 ? maxHeat / limit : 0.0;
        return d;
    }

    private static AlertPolicy policy(int warn, int crit, int rateD, int flux) {
        AlertPolicy p = new AlertPolicy();
        p.warnC = warn;
        p.critC = crit;
        p.rateWarnD = rateD;
        p.fluxWarn = flux;
        return p;
    }

    private static void levelFromThresholds() {
        AlertPolicy p = policy(500, 800, 300, 0);
        check("低于预警 = 安全",
                p.level(data(300.0, 1200.0, 0.0, 10, false)) == AlertPolicy.LEVEL_NONE);
        check("到达预警阈值 = 预警",
                p.level(data(500.0, 1200.0, 0.0, 10, false)) == AlertPolicy.LEVEL_WARN);
        check("到达严重阈值 = 严重",
                p.level(data(800.0, 1200.0, 0.0, 10, false)) == AlertPolicy.LEVEL_CRIT);
        check("温升速率达标 = 预警",
                p.level(data(200.0, 1200.0, 40.0, 10, false)) == AlertPolicy.LEVEL_WARN);
        check("速率通道关掉(0)就不看速率",
                policy(500, 800, 0, 0).level(data(200.0, 1200.0, 999.0, 10, false)) == AlertPolicy.LEVEL_NONE);
        AlertPolicy fluxP = policy(500, 800, 0, 100);
        check("通量达标 = 预警", fluxP.level(data(100.0, 1200.0, 0.0, 100, false)) == AlertPolicy.LEVEL_WARN);
        check("通量通道关掉(0)就不看通量",
                policy(500, 800, 0, 0).level(data(100.0, 1200.0, 0.0, 99999, false)) == AlertPolicy.LEVEL_NONE);
    }

    private static void offlineIsSafe() {
        AlertPolicy p = policy(500, 800, 300, 100);
        RbmkSnapshot none = new RbmkSnapshot();
        none.reset();
        check("未绑定 = 安全（不闪不叫）", p.level(none) == AlertPolicy.LEVEL_NONE);
        RbmkSnapshot off = new RbmkSnapshot();
        off.linked = true;
        off.offline = true;
        off.maxHeat = 2000.0;
        off.flux = 99999;
        check("离线 = 安全（不闪不叫）", p.level(off) == AlertPolicy.LEVEL_NONE);
        check("空快照不炸", p.level(null) == AlertPolicy.LEVEL_NONE);
        check("无数据时原因是空串", p.reason(null).isEmpty());
    }

    private static void meltdownIsCritical() {
        AlertPolicy p = policy(3000, 3500, 0, 0);
        check("熔毁一律严重（阈值设得再高也不管用）",
                p.level(data(200.0, 1200.0, 0.0, 0, true)) == AlertPolicy.LEVEL_CRIT);
        check("熔毁风险 85% = 严重",
                policy(3000, 3500, 0, 0).level(data(1020.0, 1200.0, 0.0, 0, false)) == AlertPolicy.LEVEL_CRIT);
    }

    private static void clampKeepsCritAboveWarn() {
        AlertPolicy p = policy(900, 300, 99999, 999999);
        p.clampAll();
        check("严重阈值不低于预警阈值", p.critC >= p.warnC);
        check("速率阈值夹到上限", p.rateWarnD <= AlertPolicy.RATE_MAX);
        check("通量阈值夹到上限", p.fluxWarn <= AlertPolicy.FLUX_MAX);
        AlertPolicy m = new AlertPolicy();
        m.rsMode = 99;
        m.clampAll();
        check("非法模式夹回合法区间", m.rsMode >= 0 && m.rsMode < AlertPolicy.RS_MODE_COUNT);
    }

    private static void redstoneModes() {
        RbmkSnapshot hot = data(900.0, 1200.0, 0.0, 50, false);
        AlertPolicy p = policy(500, 800, 0, 0);
        p.rsMode = AlertPolicy.RS_MAX_HEAT;
        p.rsValue = 850;
        check("最高柱温模式：到达阈值 = 真", p.condition(hot, AlertPolicy.LEVEL_WARN, null));
        p.rsValue = 950;
        check("最高柱温模式：未到阈值 = 假", !p.condition(hot, AlertPolicy.LEVEL_WARN, null));
        p.rsMode = AlertPolicy.RS_ALERT;
        check("告警模式只在严重档成立", p.condition(hot, AlertPolicy.LEVEL_WARN, null) == false
                && p.condition(hot, AlertPolicy.LEVEL_CRIT, null));
        p.rsMode = AlertPolicy.RS_LOST;
        RbmkSnapshot none = new RbmkSnapshot();
        none.reset();
        check("无数据时\"信号丢失\"为真", p.condition(none, AlertPolicy.LEVEL_NONE, null));
        p.rsMode = AlertPolicy.RS_MAX_HEAT;
        check("无数据时温度类模式一律为假（不会因为 NaN/0 误触发）", !p.condition(none, AlertPolicy.LEVEL_NONE, null));
        p.rsMode = AlertPolicy.RS_LOST;
        check("有数据时\"信号丢失\"为假", !p.condition(hot, AlertPolicy.LEVEL_WARN, null));
        p.rsMode = AlertPolicy.RS_WATCH;
        p.rsValue = 80;
        DfcWatch full = new DfcWatch(0, 0, 0, 0, 2, 0);
        full.alive = true;
        full.ratio = 0.9;
        DfcWatch empty = new DfcWatch(0, 0, 0, 0, 2, 0);
        empty.alive = true;
        empty.ratio = 0.5;
        check("数据槽按占比判定", p.condition(hot, 0, full) && !p.condition(hot, 0, empty));
        check("没有数据槽时不成立", !p.condition(hot, 0, null));
        check("关闭 / 熔毁 / 信号丢失不需要阈值",
                !AlertPolicy.hasThreshold(AlertPolicy.RS_OFF)
                        && !AlertPolicy.hasThreshold(AlertPolicy.RS_ALERT)
                        && !AlertPolicy.hasThreshold(AlertPolicy.RS_MELTDOWN)
                        && !AlertPolicy.hasThreshold(AlertPolicy.RS_LOST)
                        && AlertPolicy.hasThreshold(AlertPolicy.RS_MAX_HEAT));
    }

    private static void codecRoundTrip() {
        AlertPolicy a = policy(640, 820, 250, 77);
        a.siren = true;
        a.rsMode = AlertPolicy.RS_FLUX;
        a.rsValue = 1234;
        a.rsInvert = true;
        io.netty.buffer.ByteBuf buf = Unpooled.buffer();
        a.writeTo(buf);
        int written = buf.writerIndex();
        AlertPolicy b = new AlertPolicy();
        b.readFrom(buf);
        check("网络包往返：预警/严重/速率/通量",
                b.warnC == 640 && b.critC == 820 && b.rateWarnD == 250 && b.fluxWarn == 77);
        check("网络包往返：开关与红石",
                b.siren && b.rsMode == AlertPolicy.RS_FLUX && b.rsValue == 1234 && b.rsInvert);
        check("编解码字节数与 serializedBytes() 一致（" + written + "）", written == a.serializedBytes());
    }

    private static void copyFromCopiesEverything() {
        AlertPolicy a = policy(720, 960, 180, 44);
        a.siren = true;
        a.rsMode = AlertPolicy.RS_FLUX;
        a.rsValue = 66;
        a.rsInvert = true;
        AlertPolicy b = new AlertPolicy();
        b.setFrom(a);
        check("整包复制不漏字段",
                b.warnC == 720 && b.critC == 960 && b.rateWarnD == 180 && b.fluxWarn == 44
                        && b.siren && b.rsMode == AlertPolicy.RS_FLUX && b.rsValue == 66 && b.rsInvert);
    }

    private static void nbtRoundTrip() {
        AlertPolicy a = policy(700, 900, 150, 9);
        a.rsMode = AlertPolicy.RS_AVG_HEAT;
        a.rsValue = 660;
        a.siren = false;
        NBTTagCompound c = new NBTTagCompound();
        a.writeNBT(c);
        AlertPolicy b = new AlertPolicy();
        b.readNBT(c);
        check("NBT 往返回来值不变",
                b.warnC == 700 && b.critC == 900 && b.rateWarnD == 150 && b.fluxWarn == 9
                        && b.rsMode == AlertPolicy.RS_AVG_HEAT && b.rsValue == 660 && !b.siren);
        AlertPolicy d = new AlertPolicy();
        d.readNBT(new NBTTagCompound());
        check("老屏幕（没有新键）读默认值：汽笛默认关", !d.siren);
        check("老屏幕默认预警/严重 = 配置默认",
                d.warnC == RbmkConfig.defaultWarnC && d.critC == RbmkConfig.defaultCritC);
        check("老屏幕默认不接红石", d.rsMode == AlertPolicy.RS_OFF);
    }

    private static void debounceBlocksOscillation() {
        int[] streak = new int[1];
        int out = 0;
        boolean[] jitter = new boolean[]{true, false, true, false, true, false, true, false, true};
        for (int i = 0; i < jitter.length; ++i) {
            out = AlertPolicy.latchedOutput(out, jitter[i], streak, 4);
        }
        check("阈值抖动 9 拍不产生任何翻转", out == 0);
        check("计数只统计\"与当前输出相反\"的连续拍（最后一拍成立→计数 1）", streak[0] == 1);
        out = AlertPolicy.latchedOutput(out, true, streak, 4);
        check("累计 2 拍不吸合", out == 0 && streak[0] == 2);
        out = AlertPolicy.latchedOutput(out, true, streak, 4);
        check("累计 3 拍仍不吸合", out == 0 && streak[0] == 3);
        out = AlertPolicy.latchedOutput(out, true, streak, 4);
        check("累计满 4 拍才吸合", out == AlertPolicy.RS_STRENGTH && streak[0] == 0);
        for (int i = 0; i < 3; ++i) {
            out = AlertPolicy.latchedOutput(out, false, streak, 4);
        }
        check("条件消失 3 拍还没松开", out == AlertPolicy.RS_STRENGTH);
        out = AlertPolicy.latchedOutput(out, false, streak, 4);
        check("第 4 拍松开", out == 0);
        int[] fresh = new int[1];
        int o = 0;
        for (int i = 0; i < 3; ++i) {
            o = AlertPolicy.latchedOutput(o, true, fresh, 4);
        }
        check("干净起振：3 拍不足", o == 0);
        o = AlertPolicy.latchedOutput(o, true, fresh, 4);
        check("干净起振：第 4 拍翻转", o == AlertPolicy.RS_STRENGTH);
        check("confirmCycles<=1 时不去抖（想要立刻响应就配 1）",
                AlertPolicy.latchedOutput(0, true, new int[1], 0) == AlertPolicy.RS_STRENGTH);
        check("去抖不改变\"已一致就不写\"的性质（返回原值，外层直接 return）",
                AlertPolicy.latchedOutput(0, false, new int[1], 4) == 0);
    }

    private static void reasonMatchesCause() {
        AlertPolicy p = policy(500, 800, 0, 0);
        String r = p.reason(data(860.0, 1200.0, 0.0, 10, false));
        check("严重原因里带实际读数与阈值", r.contains("860") && r.contains("800") && r.length() <= 26);
        check("等级为 0 时原因也清空", p.reason(data(100.0, 1200.0, 0.0, 0, false)).isEmpty());
        String rate = policy(500, 800, 300, 0).reason(data(100.0, 1200.0, 45.0, 0, false));
        check("速率原因带 °C/s", rate.contains("45.0") && rate.contains("30.0"));
    }
}
