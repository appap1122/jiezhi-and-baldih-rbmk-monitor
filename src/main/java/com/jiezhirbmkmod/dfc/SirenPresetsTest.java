package com.jiezhirbmkmod.dfc;

public final class SirenPresetsTest {

    private static int passed = 0;
    private static int failed = 0;

    private SirenPresetsTest() {
    }

    public static void main(String[] args) {
        tableConsistency();
        repeatPeriod();
        pitchScaling();
        clamps();
        labels();
        System.out.println();
        System.out.println("PASS " + passed + " / FAIL " + failed);
        if (failed > 0) {
            System.exit(1);
        }
    }

    private static void check(String name, boolean ok) {
        if (ok) {
            ++passed;
            System.out.println("  ok   " + name);
        } else {
            ++failed;
            System.out.println("  FAIL " + name);
        }
    }

    private static void tableConsistency() {
        check("音色数量与 sounds.json/ModSounds 的表长一致", SirenPresets.COUNT == 5);
        boolean ids = true;
        for (int i = 0; i < SirenPresets.COUNT; ++i) {
            ids &= SirenPresets.soundId(i) != null && SirenPresets.soundId(i).length() > 0;
            ids &= SirenPresets.length(i) > 0;
            ids &= SirenPresets.label(i) != null && SirenPresets.label(i).length() > 2;
        }
        check("每一项都有注册名、正长度、可读名称", ids);
        check("越界下标绕回表内（不会因为旧存档写了 99 而越界）",
                SirenPresets.wrap(99) >= 0 && SirenPresets.wrap(99) < SirenPresets.COUNT
                        && SirenPresets.wrap(-1) == SirenPresets.COUNT - 1
                        && SirenPresets.wrap(SirenPresets.COUNT) == 0);
    }

    private static void repeatPeriod() {
        int p = SirenPresets.TWOTONE;
        check("周期 = 样本长度 + 停顿", SirenPresets.repeatTicks(p, 20) == SirenPresets.length(p) + 20);
        check("停顿为 0 也不会低于样本长度", SirenPresets.repeatTicks(p, 0) == SirenPresets.length(p));
        check("停顿越界被夹住", SirenPresets.repeatTicks(p, 99999) == SirenPresets.length(p) + SirenPresets.GAP_MAX);
        int r = SirenPresets.repeatTicksPreset(p, 0, 40);
        check("低于下限时抬到下限", r == 40);
        r = SirenPresets.repeatTicksPreset(p, 120, 14);
        check("高于下限时原样保留", r == SirenPresets.length(p) + 120);
        String secs = SirenPresets.repeatSeconds(SirenPresets.MELTDOWN_WAIL, 20);
        check("秒数展示按 /20 折算（118+20 拍 = 6.9 秒）", "6.9".equals(secs));
    }

    private static void pitchScaling() {
        int p = SirenPresets.AIRHORN;
        int base = SirenPresets.repeatTicksPreset(p, 10, 14);
        int hi = Math.max(14, (int) Math.round((double) base / 1.5D));
        check("1.5 倍音调下周期按比例缩短（不抢拍也不拖拍）", hi < base && hi >= 14);
        int lo = Math.max(14, (int) Math.round((double) base / 0.5D));
        check("0.5 倍音调下周期拉长", lo > base);
    }

    private static void clamps() {
        check("NaN 音量落到下限而不是炸包",
                SirenPresets.clampVolume(Float.NaN) == SirenPresets.VOLUME_MIN);
        check("NaN 音调落到下限",
                SirenPresets.clampPitch(Float.NaN) == SirenPresets.PITCH_MIN);
        check("超界音量被夹住", SirenPresets.clampVolume(9999.0F) == SirenPresets.VOLUME_MAX);
        check("超界音调被夹住", SirenPresets.clampPitch(-3.0F) == SirenPresets.PITCH_MIN);
        check("非法模式/声道回落到默认",
                SirenPresets.clampMode(77) == SirenPresets.MODE_LOOP
                        && SirenPresets.clampCat(-5) == SirenPresets.CAT_BLOCK);
        check("合法值原样通过",
                SirenPresets.clampMode(SirenPresets.MODE_ONCE) == SirenPresets.MODE_ONCE
                        && SirenPresets.clampCat(SirenPresets.CAT_AMBIENT) == SirenPresets.CAT_AMBIENT);
    }

    private static void labels() {
        check("模式名称齐全", SirenPresets.modeLabel(0) != null && SirenPresets.modeLabel(1) != null
                && SirenPresets.modeLabel(2) != null && SirenPresets.modeLabel(9) != null);
        check("声道名称齐全", SirenPresets.catLabel(0) != null && SirenPresets.catLabel(1) != null
                && SirenPresets.catLabel(2) != null);
        check("音量给的是格数提示", SirenPresets.approxRadius(1.0F).contains("16"));
    }
}
