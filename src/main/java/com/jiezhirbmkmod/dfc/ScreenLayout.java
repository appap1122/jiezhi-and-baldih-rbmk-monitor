package com.jiezhirbmkmod.dfc;

import com.jiezhirbmkmod.dfc.RbmkSnapshot;
import java.util.ArrayList;
import java.util.List;

public final class ScreenLayout {
    public static final int W = 256;
    public static final int H = 192;
    public static final int PHASE_BOOT = 0;
    public static final int PHASE_CONSOLE = 1;
    public static final int PHASE_MAIN = 2;
    private static final long T_BOOT = 700L;
    private static final long T_CONSOLE = 600L;
    public static final long ALERT_BLINK_MS = 500L;
    public static final int REFRESH_TICKS = 4;
    public static final int REFRESH_MS = 200;
    public static final int LOG_CAPACITY = 12;
    public static final long FAULT_MS = 4000L;
    /** brand shown on the boot / self-test screens - change it here only */
    public static final String OS_NAME = "RBMK MONITOR";
    public static final String OS_TITLE = "RBMK \u76d1\u63a7\u7cfb\u7edf 2.0";
    public static final String[] BOOT_LOG = new String[]{"\u5185\u6838 2.0 \u00b7 build 1750", "\u81ea\u68c0\uff1a\u663e\u793a\u9a71\u52a8 OK", "\u81ea\u68c0\uff1a\u53cd\u5c04\u6865 OK", "\u81ea\u68c0\uff1aRBMK \u6570\u636e\u901a\u9053 OK", "\u7b49\u5f85\u6570\u636e\u6e90\u2026"};

    private ScreenLayout() {
    }

    public static int phaseAt(long sinceOnMs) {
        if (sinceOnMs < 700L) {
            return 0;
        }
        if (sinceOnMs < 1300L) {
            return 1;
        }
        return 2;
    }

    public static double temperatureRate(double prevC, double curC, int intervalTicks) {
        double perTick = (curC - prevC) / (double)Math.max(1, intervalTicks);
        return perTick * 20.0;
    }

    public static int alertLevel(RbmkSnapshot d) {
        if (d == null || !d.linked || d.offline) {
            return 0;
        }
        if (d.meltdown || d.meltRisk >= 0.85) {
            return 2;
        }
        if (d.meltRisk >= 0.7) {
            return 1;
        }
        return 0;
    }

    public static boolean alertActive(RbmkSnapshot d) {
        return ScreenLayout.alertLevel(d) > 0;
    }

    public static boolean blinkOn(long elapsedMs) {
        return elapsedMs / 500L % 2L == 0L;
    }

    public static List<String> pendingEvents(RbmkSnapshot prev, RbmkSnapshot cur) {
        boolean prevHot;
        boolean prevCold;
        boolean was;
        ArrayList<String> out = new ArrayList<String>();
        if (cur == null) {
            return out;
        }
        boolean bl = was = prev == null || !prev.linked;
        if (!was && !cur.linked) {
            out.add("\u6e90\u5df2\u65ad\u5f00");
        }
        if (cur.linked && cur.offline) {
            out.add("\u79bb\u7ebf\uff1a\u626b\u63cf\u533a\u65e0\u67f1\u5b50\uff08\u63a7\u5236\u53f0\u672a\u94fe\u63a5\uff1f\uff09");
        } else if (cur.linked && !was && !cur.offline) {
            out.add("RBMK \u6570\u636e\u6e90\u5df2\u8fde\u63a5");
        }
        if (was && cur.linked && !cur.offline) {
            out.add("\u6570\u636e\u6e90\u6062\u590d");
        }
        boolean bl2 = prevCold = prev != null && prev.linked && !prev.offline && prev.cold;
        if (cur.linked && !cur.offline && cur.cold && !prevCold) {
            out.add("\u51b7\u5806\uff08\u5e73\u5747\u67f1\u6e29 < 100\u00b0C\uff09");
        }
        boolean bl3 = prevHot = prev != null && prev.linked && !prev.offline && prev.meltRisk >= 0.7;
        if (cur.linked && !cur.offline && cur.meltRisk >= 0.7 && !prevHot) {
            out.add("\u5806\u82af\u8d85\u6e29\uff01\u6700\u70ed\u67f1 " + Math.round(cur.maxHeat) + "\u00b0C / " + Math.round(cur.maxHeatLimit) + "\u00b0C");
        }
        if (cur.meltdown && (prev == null || !prev.meltdown)) {
            out.add("\u7194\u6bc1\u544a\u8b66\uff01\uff01");
        }
        if (cur.fuelCount > 0) {
            boolean prevFuel;
            boolean bl4 = prevFuel = prev != null && prev.fuelCount > 0 && prev.avgEnrichment >= 0.15;
            if (cur.avgEnrichment < 0.15 && prevFuel) {
                out.add("\u71c3\u6599\u5373\u5c06\u8017\u5c3d\uff08\u5e73\u5747\u5bcc\u96c6\u5ea6 " + Math.round(cur.avgEnrichment * 100.0) + "%\uff09");
            }
        }
        if (cur.fuelCount > 0) {
            boolean prevXen;
            boolean bl5 = prevXen = prev != null && prev.fuelCount > 0 && prev.avgXenon <= 0.6;
            if (cur.avgXenon > 0.6 && prevXen) {
                out.add("\u6c19\u6bd2\u5316\u4e25\u91cd\uff08" + Math.round(cur.avgXenon * 100.0) + "%\uff09");
            }
        }
        return out;
    }
}

