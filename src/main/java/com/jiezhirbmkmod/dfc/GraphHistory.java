package com.jiezhirbmkmod.dfc;

public final class GraphHistory {

    public static final int CAPACITY = com.jiezhirbmkmod.dfc.RbmkConfig.historyCapacitySafe();

    public static final long SAMPLE_MS = com.jiezhirbmkmod.dfc.RbmkConfig.sampleMs;

    public static final int CHANNELS = 6;

    public static final double HEAT_BASE_MAX = 1000.0D;

    public static final double FLUX_BASE_MAX = 100.0D;

    public static final double WATER_BASE_MAX = 10000.0D;

    public static final double STEAM_BASE_MAX = 10000.0D;

    public static final double ENRICH_BASE_MAX = 1.0D;

    public static final double XENON_BASE_MAX = 1.0D;

    public static final int DIVISIONS = 4;

    public static final int WINDOW_SECONDS = (int) (CAPACITY * SAMPLE_MS / 1000L);

    private final double[][] channels = new double[CHANNELS][CAPACITY];

    /** v17.7 每个采样点的告警等级（0=正常 1=预警 2=严重），供曲线上画告警标记线 */
    private final byte[] alerts = new byte[CAPACITY];

    private int count;
    private int head;

    public GraphHistory() {
        this.clear();
    }

    public void clear() {
        for (int c = 0; c < CHANNELS; c++) {
            for (int i = 0; i < CAPACITY; i++) {
                this.channels[c][i] = Double.NaN;
            }
        }
        for (int i = 0; i < CAPACITY; i++) {
            this.alerts[i] = 0;
        }
        this.count = 0;
        this.head = 0;
    }

    public void push(double avgHeat, double flux, double water, double steam, double enrichment, double xenon) {
        this.push(avgHeat, flux, water, steam, enrichment, xenon, (byte) 0);
    }

    /** v17.7 带告警等级的采样：等级随数据同格落盘，标记线才能对齐曲线 */
    public void push(double avgHeat, double flux, double water, double steam, double enrichment, double xenon, byte alertLevel) {
        if (!GraphHistory.isFinite(avgHeat) || !GraphHistory.isFinite(flux)
                || !GraphHistory.isFinite(water) || !GraphHistory.isFinite(steam)
                || !GraphHistory.isFinite(enrichment) || !GraphHistory.isFinite(xenon)) {
            this.pushGap();
            return;
        }
        this.channels[0][this.head] = Math.max(0.0D, avgHeat);
        this.channels[1][this.head] = Math.max(0.0D, flux);
        this.channels[2][this.head] = Math.max(0.0D, water);
        this.channels[3][this.head] = Math.max(0.0D, steam);
        this.channels[4][this.head] = Math.max(0.0D, Math.min(1.0D, enrichment));
        this.channels[5][this.head] = Math.max(0.0D, Math.min(1.0D, xenon));
        this.alerts[this.head] = alertLevel;
        this.advance();
    }

    public void pushGap() {
        for (int c = 0; c < CHANNELS; c++) {
            this.channels[c][this.head] = Double.NaN;
        }
        this.alerts[this.head] = 0;
        this.advance();
    }

    private void advance() {
        this.head = (this.head + 1) % CAPACITY;
        if (this.count < CAPACITY) {
            this.count++;
        }
    }

    public int size() {
        return this.count;
    }

    private int slot(int i) {
        int start = (this.head - this.count + CAPACITY * 2) % CAPACITY;
        return (start + i) % CAPACITY;
    }

    public double valueAt(int channel, int i) {
        return this.channels[channel][this.slot(i)];
    }

    public boolean gapAt(int i) {
        return Double.isNaN(this.channels[0][this.slot(i)]);
    }

    /** v17.7 采样点 i 的告警等级（0/1/2），断线格为 0 */
    public byte alertAt(int i) {
        return this.alerts[this.slot(i)];
    }

    public double maxOf(int channel) {
        double m = 0.0D;
        double[] src = this.channels[channel];
        for (int i = 0; i < CAPACITY; i++) {
            double v = src[i];
            if (!Double.isNaN(v) && v > m) {
                m = v;
            }
        }
        return m;
    }

    public double axisMax(int channel) {
        double base;
        switch (channel) {
            case 1: {
                base = FLUX_BASE_MAX;
                break;
            }
            case 2: {
                base = WATER_BASE_MAX;
                break;
            }
            case 3: {
                base = STEAM_BASE_MAX;
                break;
            }
            case 4: {
                base = ENRICH_BASE_MAX;
                break;
            }
            case 5: {
                base = XENON_BASE_MAX;
                break;
            }
            default: {
                base = HEAT_BASE_MAX;
            }
        }
        return GraphHistory.niceCeil(Math.max(base, this.maxOf(channel)));
    }

    public boolean hasData() {
        for (int i = 0; i < CAPACITY; i++) {
            if (!Double.isNaN(this.channels[0][i])) {
                return true;
            }
        }
        return false;
    }

    private static final double[] MANTISSA = {1.0D, 1.5D, 2.0D, 2.5D, 3.0D, 4.0D, 5.0D, 6.0D, 8.0D, 10.0D};

    public static double niceCeil(double v) {
        if (!GraphHistory.isFinite(v) || v <= 0.0D) {
            return 1.0D;
        }
        double exp = Math.pow(10.0D, Math.floor(Math.log10(v)));
        double m = v / exp;
        for (int i = 0; i < GraphHistory.MANTISSA.length; i++) {
            if (m <= GraphHistory.MANTISSA[i] + 1e-9D) {
                return GraphHistory.MANTISSA[i] * exp;
            }
        }
        return 10.0D * exp;
    }

    public static double gridValue(double axisMax, int k) {
        return axisMax * (double) (GraphHistory.DIVISIONS - k) / (double) GraphHistory.DIVISIONS;
    }

    private static boolean isFinite(double v) {
        return !Double.isNaN(v) && !Double.isInfinite(v);
    }
}
