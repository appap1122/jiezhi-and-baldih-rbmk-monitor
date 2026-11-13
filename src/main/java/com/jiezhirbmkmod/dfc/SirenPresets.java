package com.jiezhirbmkmod.dfc;

import java.util.Locale;

public final class SirenPresets {

    public static final int TWOTONE = 0;
    public static final int YELP = 1;
    public static final int AIRHORN = 2;
    public static final int WARN_BEEP = 3;
    public static final int MELTDOWN_WAIL = 4;
    public static final int COUNT = 5;

    private static final String[] SOUND_IDS = new String[]{
            "siren_twotone", "siren_yelp", "siren_airhorn", "alert_warn", "alert_crit"};

    private static final String[] LABELS = new String[]{
            "双音警报", "急促三音", "气笛长鸣", "预警两声", "熔毁长鸣"};

    private static final int[] LENGTH_TICKS = new int[]{38, 20, 36, 17, 118};

    private static final int[] DEFAULT_GAP = new int[]{6, 14, 10, 30, 20};

    public static final int MODE_LOOP = 0;   
    public static final int MODE_ONCE = 1;   
    public static final int MODE_PULSE = 2;  
    public static final int MODE_COUNT = 3;

    public static final int CAT_BLOCK = 0;   
    public static final int CAT_AMBIENT = 1; 
    public static final int CAT_MASTER = 2;  
    public static final int CAT_COUNT = 3;

    public static final float VOLUME_MIN = 0.1F, VOLUME_MAX = 8.0F;
    public static final float PITCH_MIN = 0.5F, PITCH_MAX = 2.0F;
    public static final int GAP_MIN = 0, GAP_MAX = 200;

    private SirenPresets() {
    }

    public static String soundId(int preset) {
        return SOUND_IDS[wrap(preset)];
    }

    public static String label(int preset) {
        return LABELS[wrap(preset)];
    }

    public static int length(int preset) {
        return LENGTH_TICKS[wrap(preset)];
    }

    public static int defaultGap(int preset) {
        return DEFAULT_GAP[wrap(preset)];
    }

    public static int repeatTicks(int preset, int gapTicks) {
        return length(preset) + clampGap(gapTicks);
    }

    public static String repeatSeconds(int preset, int gapTicks) {
        return String.format(Locale.ROOT, "%.1f", repeatTicks(preset, gapTicks) / 20.0);
    }

    public static String modeLabel(int mode) {
        switch (mode) {
            case MODE_ONCE:
                return "响一次";
            case MODE_PULSE:
                return "慢拍";
            default:
                return "循环";
        }
    }

    public static String catLabel(int cat) {
        switch (cat) {
            case CAT_AMBIENT:
                return "环境音";
            case CAT_MASTER:
                return "总音量";
            default:
                return "方块声";
        }
    }

    public static String approxRadius(float volume) {
        return "≈" + Math.round(volume * 16.0F) + " 格";
    }

    public static int wrap(int preset) {
        if (COUNT <= 0) {
            return 0;
        }
        return ((preset % COUNT) + COUNT) % COUNT;
    }

    public static int clampGap(int gap) {
        return gap < GAP_MIN ? GAP_MIN : (gap > GAP_MAX ? GAP_MAX : gap);
    }

    public static float clampVolume(float v) {
        if (Float.isNaN(v) || v < VOLUME_MIN) {
            return VOLUME_MIN;
        }
        return v > VOLUME_MAX ? VOLUME_MAX : v;
    }

    public static float clampPitch(float v) {
        if (Float.isNaN(v) || v < PITCH_MIN) {
            return PITCH_MIN;
        }
        return v > PITCH_MAX ? PITCH_MAX : v;
    }

    public static int clampMode(int m) {
        return m < 0 || m >= MODE_COUNT ? MODE_LOOP : m;
    }

    public static int clampCat(int c) {
        return c < 0 || c >= CAT_COUNT ? CAT_BLOCK : c;
    }

    public static int repeatTicksPreset(int preset, int gapTicks, int minTicks) {
        int r = repeatTicks(preset, gapTicks);
        return r < minTicks ? minTicks : r;
    }
}
