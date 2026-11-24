package com.jiezhirbmkmod.dfc;

import io.netty.buffer.ByteBuf;
import java.util.Locale;
import net.minecraft.nbt.NBTTagCompound;
public final class AlertPolicy {

    public static final int LEVEL_NONE = 0;
    public static final int LEVEL_WARN = 1;
    public static final int LEVEL_CRIT = 2;

    public static final int RS_STRENGTH = 15;

    public static final int RS_OFF = 0;
    public static final int RS_ALERT = 1;
    public static final int RS_MAX_HEAT = 2;
    public static final int RS_AVG_HEAT = 3;
    public static final int RS_FLUX = 4;
    public static final int RS_HEAT_RATE = 5;
    public static final int RS_MELTDOWN = 6;
    public static final int RS_LOST = 7;
    public static final int RS_WATCH = 8;
    public static final int RS_MODE_COUNT = 9;

    public static final int WARN_MIN = 50, WARN_MAX = 3600;
    public static final int CRIT_MIN = 100, CRIT_MAX = 3600;
    public static final int RATE_MIN = 0, RATE_MAX = 4000;   
    public static final int FLUX_MIN = 0, FLUX_MAX = 100000; 
    public static final int RSVAL_MIN = 0, RSVAL_MAX = 100000;

    public static final int DEF_WARN_C = RbmkConfig.defaultWarnC;
    public static final int DEF_CRIT_C = RbmkConfig.defaultCritC;
    public static final int DEF_RATE_D = RbmkConfig.defaultRateWarnD;   // 30.0 °C/s
    public static final int DEF_FLUX_F = RbmkConfig.defaultFluxWarnF;
    public static final int DEF_RS_MODE = RS_OFF;
    public static final int DEF_RS_VALUE = RbmkConfig.defaultCritC;
    public static final boolean DEF_SIREN = RbmkConfig.sirenEnabled;

    public int warnC = DEF_WARN_C;
    public int critC = DEF_CRIT_C;
    public int rateWarnD = DEF_RATE_D;
    public int fluxWarn = DEF_FLUX_F;
    public boolean siren = DEF_SIREN;
    public int rsMode = DEF_RS_MODE;
    public int rsValue = DEF_RS_VALUE;
    public boolean rsInvert = false;

    public void setFrom(AlertPolicy src) {
        if (src == null) {
            return;
        }
        this.warnC = src.warnC;
        this.critC = src.critC;
        this.rateWarnD = src.rateWarnD;
        this.fluxWarn = src.fluxWarn;
        this.siren = src.siren;
        this.rsMode = src.rsMode;
        this.rsValue = src.rsValue;
        this.rsInvert = src.rsInvert;
    }

    public int level(RbmkSnapshot d) {
        int lv = ScreenLayout.alertLevel(d);
        if (d == null || !d.linked || d.offline) {
            return lv;
        }
        if (d.meltdown || d.maxHeat >= (double) this.critC) {
            lv = Math.max(lv, LEVEL_CRIT);
        } else if (d.maxHeat >= (double) this.warnC
                || (this.rateWarnD > 0 && d.tempDelta * 10.0 >= (double) this.rateWarnD)
                || (this.fluxWarn > 0 && (double) d.flux >= (double) this.fluxWarn)) {
            lv = Math.max(lv, LEVEL_WARN);
        }
        return Math.min(LEVEL_CRIT, lv);
    }

    public String reason(RbmkSnapshot d) {
        if (d == null || this.level(d) == LEVEL_NONE) {
            return "";
        }
        if (d.meltdown) {
            return "熔毁！堆芯已越过熔毁线";
        }
        if (d.maxHeat >= (double) this.critC) {
            return "最高柱温 " + Math.round(d.maxHeat) + "°C / 严重 " + this.critC + "°C";
        }
        if (d.maxHeat >= (double) this.warnC) {
            return "最高柱温 " + Math.round(d.maxHeat) + "°C / 预警 " + this.warnC + "°C";
        }
        if (this.rateWarnD > 0 && d.tempDelta * 10.0 >= (double) this.rateWarnD) {
            return "温升 " + one1(d.tempDelta) + "°C/s / 阈值 " + one1(this.rateWarnD / 10.0) + "°C/s";
        }
        if (this.fluxWarn > 0 && (double) d.flux >= (double) this.fluxWarn) {
            return "中子通量 " + d.flux + "F / 阈值 " + this.fluxWarn + "F";
        }
        return "熔毁风险 " + DfcFormat.percent(d.meltRisk) + " 最热柱 " + Math.round(d.maxHeat) + "°C";
    }

    private static String one1(double v) {
        return String.format(Locale.ROOT, "%.1f", v);
    }

    public boolean condition(RbmkSnapshot d, int alertLevel, DfcWatch firstWatch) {
        switch (this.rsMode) {
            case RS_ALERT:
                return alertLevel >= LEVEL_CRIT;
            case RS_MAX_HEAT:
                return d != null && d.linked && !d.offline && d.maxHeat >= (double) this.rsValue;
            case RS_AVG_HEAT:
                return d != null && d.linked && !d.offline && d.avgHeat >= (double) this.rsValue;
            case RS_FLUX:
                return d != null && d.linked && !d.offline && (double) d.flux >= (double) this.rsValue;
            case RS_HEAT_RATE:
                return d != null && d.linked && !d.offline && d.tempDelta * 10.0 >= (double) this.rsValue;
            case RS_MELTDOWN:
                return d != null && d.linked && !d.offline && d.meltdown;
            case RS_LOST:
                return d == null || !d.linked || d.offline;
            case RS_WATCH:
                return firstWatch != null && firstWatch.alive
                        && firstWatch.ratio * 100.0 >= (double) this.rsValue;
            default:
                return false;
        }
    }

    public static int latchedOutput(int current, boolean condition, int[] streak, int confirmCycles) {
        int want = condition ? RS_STRENGTH : 0;
        if (want == current) {
            streak[0] = 0;
            return current;
        }
        ++streak[0];
        if (streak[0] < Math.max(1, confirmCycles)) {
            return current;   
        }
        streak[0] = 0;
        return want;
    }

    public static boolean hasThreshold(int mode) {
        return mode != RS_OFF && mode != RS_ALERT && mode != RS_MELTDOWN && mode != RS_LOST;
    }

    public static String modeName(int mode) {
        switch (mode) {
            case RS_ALERT:
                return "告警（严重）";
            case RS_MAX_HEAT:
                return "最高柱温";
            case RS_AVG_HEAT:
                return "平均柱温";
            case RS_FLUX:
                return "中子通量";
            case RS_HEAT_RATE:
                return "温升速率";
            case RS_MELTDOWN:
                return "熔毁";
            case RS_LOST:
                return "信号丢失";
            case RS_WATCH:
                return "自定义槽1";
            default:
                return "关闭";
        }
    }

    public static String valueText(int mode, int value) {
        switch (mode) {
            case RS_MAX_HEAT:
            case RS_AVG_HEAT:
                return value + " °C";
            case RS_FLUX:
                return value + " F";
            case RS_HEAT_RATE:
                return one1(value / 10.0) + " °C/s";
            case RS_MELTDOWN:
            case RS_ALERT:
                return "—";
            case RS_WATCH:
                return value + " %";
            default:
                return "—";
        }
    }

    public static int step(int mode) {
        switch (mode) {
            case RS_ALERT:
            case RS_MELTDOWN:
                return 0;
            case RS_MAX_HEAT:
            case RS_AVG_HEAT:
                return 10;
            case RS_FLUX:
                return 1;
            case RS_HEAT_RATE:
                return 50;
            case RS_WATCH:
                return 5;
            default:
                return 10;
        }
    }

    public static int min(int mode) {
        return RSVAL_MIN;
    }

    public static int max(int mode) {
        switch (mode) {
            case RS_MAX_HEAT:
            case RS_AVG_HEAT:
                return CRIT_MAX;
            case RS_HEAT_RATE:
                return RATE_MAX;
            case RS_WATCH:
                return 100;
            default:
                return FLUX_MAX;
        }
    }

    public void clampRsValue() {
        this.rsValue = clamp(this.rsValue, min(this.rsMode), max(this.rsMode));
    }

    public void clampAll() {
        this.warnC = clamp(this.warnC, WARN_MIN, WARN_MAX);
        this.critC = clamp(this.critC, CRIT_MIN, CRIT_MAX);
        if (this.critC < this.warnC) {
            this.critC = this.warnC; 
        }
        this.rateWarnD = clamp(this.rateWarnD, RATE_MIN, RATE_MAX);
        this.fluxWarn = clamp(this.fluxWarn, FLUX_MIN, FLUX_MAX);
        this.rsMode = clamp(this.rsMode, RS_OFF, RS_MODE_COUNT - 1);
        this.clampRsValue();
    }

    private static int clamp(int v, int lo, int hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    public int serializedBytes() {
        return 6 * 4 + 2;
    }

    public void readNBT(NBTTagCompound c) {
        this.warnC = c.hasKey("WarnC") ? c.getInteger("WarnC") : DEF_WARN_C;
        this.critC = c.hasKey("CritC") ? c.getInteger("CritC") : DEF_CRIT_C;
        this.rateWarnD = c.hasKey("RateD") ? c.getInteger("RateD") : DEF_RATE_D;
        this.fluxWarn = c.hasKey("FluxW") ? c.getInteger("FluxW") : DEF_FLUX_F;
        this.siren = c.hasKey("Siren") ? c.getBoolean("Siren") : DEF_SIREN;
        this.rsMode = c.hasKey("RsMode") ? c.getInteger("RsMode") : DEF_RS_MODE;
        this.rsValue = c.hasKey("RsValue") ? c.getInteger("RsValue") : DEF_RS_VALUE;
        this.rsInvert = c.getBoolean("RsInvert");
        this.clampAll();
    }

    public void writeNBT(NBTTagCompound c) {
        c.setInteger("WarnC", this.warnC);
        c.setInteger("CritC", this.critC);
        c.setInteger("RateD", this.rateWarnD);
        c.setInteger("FluxW", this.fluxWarn);
        c.setBoolean("Siren", this.siren);
        c.setInteger("RsMode", this.rsMode);
        c.setInteger("RsValue", this.rsValue);
        c.setBoolean("RsInvert", this.rsInvert);
    }

    public void writeTo(ByteBuf buf) {
        buf.writeInt(this.warnC);
        buf.writeInt(this.critC);
        buf.writeInt(this.rateWarnD);
        buf.writeInt(this.fluxWarn);
        buf.writeBoolean(this.siren);
        buf.writeInt(this.rsMode);
        buf.writeInt(this.rsValue);
        buf.writeBoolean(this.rsInvert);
    }

    public void readFrom(ByteBuf buf) {
        this.warnC = buf.readInt();
        this.critC = buf.readInt();
        this.rateWarnD = buf.readInt();
        this.fluxWarn = buf.readInt();
        this.siren = buf.readBoolean();
        this.rsMode = buf.readInt();
        this.rsValue = buf.readInt();
        this.rsInvert = buf.readBoolean();
        this.clampAll();
    }
}
