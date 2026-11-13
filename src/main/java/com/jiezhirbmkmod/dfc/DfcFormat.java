package com.jiezhirbmkmod.dfc;

import java.util.Locale;

public final class DfcFormat {
    private static final String[] SI_PREFIX = new String[]{"", "k", "M", "G", "T", "P", "E"};
    public static final double STAB_FULL_SCALE = 10.0;
    public static final double SPIN_FULL_SCALE = 100.0;
    public static final double SPK_SCALE = 1000.0;
    public static final double CORE_ENERGY_BAR_SCALE = 100000.0;
    public static final double CORE_FAILSAFE = 2.5E8;

    private DfcFormat() {
    }

    public static String temperature(double celsius) {
        int idx;
        double v = celsius;
        for (idx = 0; Math.abs(v) >= 10000.0 && idx < SI_PREFIX.length - 1; ++idx) {
            v /= 1000.0;
        }
        return String.format(Locale.ROOT, "%.2f%s\u00b0C", v, SI_PREFIX[idx]);
    }

    public static String si(double value, String unit) {
        int idx;
        double v = value;
        for (idx = 0; Math.abs(v) >= 1000.0 && idx < SI_PREFIX.length - 1; ++idx) {
            v /= 1000.0;
        }
        return String.format(Locale.ROOT, "%.2f %s%s", v, SI_PREFIX[idx], unit);
    }

    public static String energy(double he) {
        return DfcFormat.si(he, "HE");
    }

    public static String amount(double milliBuckets) {
        return DfcFormat.si(milliBuckets, "mB");
    }

    public static String burst(double expellingEnergy) {
        return DfcFormat.si(expellingEnergy, "SPK") + "/s";
    }

    public static String rate(double perTick) {
        double perSecond = perTick * 20.0;
        double abs = Math.abs(perSecond);
        if (abs < 0.005) {
            return "0.00/s";
        }
        if (abs >= 1000.0) {
            String sign = perSecond >= 0.0 ? "+" : "";
            return sign + DfcFormat.si(perSecond, "/s");
        }
        return String.format(Locale.ROOT, "%+.2f/s", perSecond);
    }

    public static String percent(double ratio01) {
        double p = ratio01 * 100.0;
        if (p < 0.0) {
            p = 0.0;
        }
        if (p > 100.0) {
            p = 100.0;
        }
        return String.format(Locale.ROOT, "%.2f%%", p);
    }

    public static String percentRaw(double value100) {
        return DfcFormat.percent(value100 / 100.0);
    }

    public static double stabilizationRatio(double stabilization) {
        double r = stabilization / 10.0;
        if (r < 0.0) {
            return 0.0;
        }
        return r > 1.0 ? 1.0 : r;
    }

    public static String stabilization(double stabilization) {
        return DfcFormat.percent(DfcFormat.stabilizationRatio(stabilization));
    }

    public static int collapseLevel(double collapsing01) {
        if (collapsing01 >= 0.85) {
            return 2;
        }
        if (collapsing01 >= 0.5) {
            return 1;
        }
        return 0;
    }

    public static String levelLabel(int level) {
        switch (level) {
            case 2: {
                return "\u5371\u6025";
            }
            case 1: {
                return "\u8b66\u544a";
            }
        }
        return "\u6b63\u5e38";
    }

    public static String collapseLabel(int level) {
        return DfcFormat.levelLabel(level);
    }

    public static String duration(int ticks) {
        long total = (long)Math.max(0, ticks) / 20L;
        long h = total / 3600L;
        long m = total % 3600L / 60L;
        long s = total % 60L;
        return String.format(Locale.ROOT, "[%d:%02d:%02d]", h, m, s);
    }

    public static String countdown(double ticks) {
        if (ticks < 0.0) {
            return "--:--";
        }
        long total = (long)(ticks / 20.0);
        long m = total / 60L;
        long s = total % 60L;
        return String.format(Locale.ROOT, "%02d:%02d", m, s);
    }

    public static int heatLevel(double temperature, int meltingPoint) {
        if (meltingPoint <= 0) {
            return 0;
        }
        double ratio = temperature / (double)meltingPoint;
        if (ratio >= 1.0) {
            return 2;
        }
        if (ratio >= 0.85) {
            return 1;
        }
        return 0;
    }

    public static String heatLabel(int level) {
        return DfcFormat.levelLabel(level);
    }

    public static String stabLabel(int level) {
        return DfcFormat.levelLabel(level);
    }

    public static int stabLevel(double stabilization01) {
        if (stabilization01 < 0.9) {
            return 2;
        }
        if (stabilization01 < 0.99) {
            return 1;
        }
        return 0;
    }

    public static String spin(double rps) {
        return String.format(Locale.ROOT, "%.2f rps", rps);
    }

    public static double spinRatio(double rps) {
        double r = rps / 100.0;
        if (r < 0.0) {
            return 0.0;
        }
        return r > 1.0 ? 1.0 : r;
    }

    public static String spk(double raw) {
        return DfcFormat.si(raw * 1000.0, "SPK");
    }

    public static String spkRate(double raw) {
        return DfcFormat.si(raw * 1000.0, "SPK") + "/s";
    }

    public static String gainFactor(double potentialGain) {
        return String.format(Locale.ROOT, "%.2f", potentialGain);
    }

    public static double gainRatio(double potentialGain) {
        double r = (potentialGain - 1.0) / 9.0;
        if (r < 0.0) {
            return 0.0;
        }
        return r > 1.0 ? 1.0 : r;
    }
}

