package com.jiezhirbmkmod.dfc;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;

public final class RbmkConfig {

    public static final int DEFAULT_CONSOLE_SEARCH_RADIUS = 24;
    public static final long DEFAULT_SAMPLE_MS = 500L;
    public static final int DEFAULT_HISTORY_CAPACITY = 120;
    public static final double DEFAULT_COLD_THRESHOLD = 100.0D;
    public static final int DEFAULT_REFRESH_MS = 200;
    public static final float DEFAULT_VOLUME_WARN = 1.2F;
    public static final float DEFAULT_VOLUME_CRIT = 3.0F;
    public static final long DEFAULT_CRIT_REPEAT_SEC = 5L;
    public static final long DEFAULT_WARN_HYST_SEC = 40L;
    public static final int DEFAULT_RS_CONFIRM_CYCLES = 4;
    public static final boolean DEFAULT_SPAWN_FLYING_CAP = true;
    public static final boolean DEFAULT_ENABLE_STEAM_EXPLOSION = true;
    public static final int DEFAULT_STEAM_EXPLOSION_DELAY_SEC = 10;
    public static final int DEFAULT_WARN_C = 500;
    public static final int DEFAULT_CRIT_C = 800;
    public static final int DEFAULT_RATE_WARN_D = 300;  // 30.0 °C/s
    public static final int DEFAULT_FLUX_WARN_F = 0;    

    public static int consoleSearchRadius = DEFAULT_CONSOLE_SEARCH_RADIUS;
    public static long sampleMs = DEFAULT_SAMPLE_MS;
    public static int historyCapacity = DEFAULT_HISTORY_CAPACITY;
    public static double coldThreshold = DEFAULT_COLD_THRESHOLD;
    public static int defaultRefreshMs = DEFAULT_REFRESH_MS;
    public static float volumeWarn = DEFAULT_VOLUME_WARN;
    public static float volumeCrit = DEFAULT_VOLUME_CRIT;
    public static long critRepeatSec = DEFAULT_CRIT_REPEAT_SEC;
    public static long warnHysteresisSec = DEFAULT_WARN_HYST_SEC;
    public static boolean sirenEnabled = false;
    public static int rsConfirmCycles = DEFAULT_RS_CONFIRM_CYCLES;
    public static boolean spawnFlyingCap = DEFAULT_SPAWN_FLYING_CAP;
    public static boolean enableSteamExplosion = DEFAULT_ENABLE_STEAM_EXPLOSION;
    public static int steamExplosionDelaySec = DEFAULT_STEAM_EXPLOSION_DELAY_SEC;
    public static boolean enableAutoScram = true;
    public static int autoScramTemp = 1000;
    public static boolean enableHackerAttack = true;
    public static boolean allowNormalPlayerHack = true;
    public static int defaultWarnC = DEFAULT_WARN_C;
    public static int defaultCritC = DEFAULT_CRIT_C;
    public static int defaultRateWarnD = DEFAULT_RATE_WARN_D;
    public static int defaultFluxWarnF = DEFAULT_FLUX_WARN_F;

    private static boolean loaded = false;

    private RbmkConfig() {
    }

    public static void load(File configDir) {
        if (loaded) {
            return;
        }
        loaded = true;
        Configuration cfg = null;
        try {
            cfg = new Configuration(new File(configDir, "jiezhirbmkmod.cfg"));
            cfg.load();
        }
        catch (Throwable t) {
            return; 
        }
        try {
            consoleSearchRadius = clampInt(
                    cfg.getInt("consoleSearchRadius", "binding",
                            DEFAULT_CONSOLE_SEARCH_RADIUS, 4, 64,
                            "遥控器绑定 RBMK 部件时，自动搜索附近控制台的半径（格）"),
                    4, 64, DEFAULT_CONSOLE_SEARCH_RADIUS);

            sampleMs = clampLong(
                    cfg.get("graph", "sampleIntervalMs", DEFAULT_SAMPLE_MS,
                            "曲线屏采样间隔（毫秒），50 ~ 5000").getLong(DEFAULT_SAMPLE_MS),
                    50L, 5000L, DEFAULT_SAMPLE_MS);

            historyCapacity = clampInt(
                    cfg.getInt("historyCapacity", "graph",
                            DEFAULT_HISTORY_CAPACITY, 30, 600,
                            "曲线屏历史窗口容量（点数）。窗口秒数 = 容量 × 采样间隔 / 1000"),
                    30, 600, DEFAULT_HISTORY_CAPACITY);

            coldThreshold = clampDouble(
                    cfg.get("reactor", "coldThresholdC", DEFAULT_COLD_THRESHOLD,
                            "冷堆判定阈值：平均柱温低于此值且通量为 0 视为冷堆（不算故障）").getDouble(DEFAULT_COLD_THRESHOLD),
                    0.0D, 10000.0D, DEFAULT_COLD_THRESHOLD);

            defaultRefreshMs = clampInt(
                    cfg.getInt("defaultRefreshMs", "display",
                            DEFAULT_REFRESH_MS, 50, 1000,
                            "显示屏默认刷新率（毫秒）。开机后可调整的档位不变"),
                    50, 1000, DEFAULT_REFRESH_MS);

            volumeWarn = clampFloat(
                    cfg.getFloat("volumeWarn", "siren", DEFAULT_VOLUME_WARN, 0.1F, 4.0F,
                            "预警汽笛音量（本地 BLOCK 声道，可听半径≈16×音量格）") * 1.0F,
                    0.1F, 4.0F, DEFAULT_VOLUME_WARN);

            volumeCrit = clampFloat(
                    cfg.getFloat("volumeCrit", "siren", DEFAULT_VOLUME_CRIT, 0.1F, 16.0F,
                            "严重（熔毁级）汽笛音量。走 AMBIENT 声道（原版给环境音留了更大的传播范围），音量越大传得越远；想覆盖整个基地就调大") * 1.0F,
                    0.1F, 16.0F, DEFAULT_VOLUME_CRIT);

            critRepeatSec = clampLong(
                    cfg.get("siren", "critRepeatSec", DEFAULT_CRIT_REPEAT_SEC,
                            "持续严重告警时，汽笛每隔多少秒重叫一次（秒，1~60）").getLong(DEFAULT_CRIT_REPEAT_SEC),
                    1L, 60L, DEFAULT_CRIT_REPEAT_SEC);

            warnHysteresisSec = clampLong(
                    cfg.get("siren", "warnHysteresisSec", DEFAULT_WARN_HYST_SEC,
                            "预警至少间隔多少秒才允许重叫（防止阈值边缘来回抖动刷屏，秒）").getLong(DEFAULT_WARN_HYST_SEC),
                    0L, 600L, DEFAULT_WARN_HYST_SEC);

            sirenEnabled = cfg.getBoolean("autoOn", "siren", false,
                    "新放置的显示屏是否默认开启汽笛声音（每台屏仍可在设置界面单独关）");

            defaultWarnC = clampInt(
                    cfg.getInt("defaultWarnC", "siren", DEFAULT_WARN_C, 50, 3600,
                            "新显示屏的默认预警温度（°C）"),
                    50, 3600, DEFAULT_WARN_C);

            defaultCritC = clampInt(
                    cfg.getInt("defaultCritC", "siren", DEFAULT_CRIT_C, 100, 3600,
                            "新显示屏的默认严重温度（°C）"),
                    100, 3600, DEFAULT_CRIT_C);

            defaultRateWarnD = clampInt(
                    cfg.getInt("defaultRateWarn", "siren", DEFAULT_RATE_WARN_D, 0, 4000,
                            "新显示屏的默认温升速率阈值（0.1°C/s，0=不看速率）"),
                    0, 4000, DEFAULT_RATE_WARN_D);

            defaultFluxWarnF = clampInt(
                    cfg.getInt("defaultFluxWarn", "siren", DEFAULT_FLUX_WARN_F, 0, 100000,
                            "新显示屏的默认中子通量阈值（F，0=不看通量）"),
                    0, 100000, DEFAULT_FLUX_WARN_F);

            rsConfirmCycles = clampInt(
                    cfg.getInt("confirmCycles", "redstone", DEFAULT_RS_CONFIRM_CYCLES, 0, 200,
                            "红石输出去抖：条件要连续多少「刷新拍」才真的翻转（1 拍 = 屏幕刷新间隔，默认 200ms）。"
                            + "读数在阈值上下抖时，每次翻转要写主机+11 块配件共 12 个方块状态，这个值就是最坏代价的闸"),
                    0, 200, DEFAULT_RS_CONFIRM_CYCLES);

            spawnFlyingCap = cfg.getBoolean("spawnFlyingCap", "explosion", DEFAULT_SPAWN_FLYING_CAP,
                    "熔毁蒸汽爆炸时，是否掀飞反应堆顶盖（控制台 +3 格）并生成飞行方块实体");

            enableSteamExplosion = cfg.getBoolean("enableSteamExplosion", "explosion", DEFAULT_ENABLE_STEAM_EXPLOSION,
                    "熔毁时是否触发延迟核爆（真实爆炸，会破坏地形）");

            steamExplosionDelaySec = clampInt(
                    cfg.getInt("steamExplosionDelaySec", "explosion", DEFAULT_STEAM_EXPLOSION_DELAY_SEC, 1, 300,
                            "熔毁后延迟多少秒发生 20.0F 核爆（服务端定时，不阻塞主线程）"),
                    1, 300, DEFAULT_STEAM_EXPLOSION_DELAY_SEC);

            enableAutoScram = cfg.getBoolean("enableAutoScram", "safety", true,
                    "是否开启自动紧急停堆（Auto-SCRAM）：堆芯温度超过 autoScramTemp 时自动把全部控制棒插到底");

            autoScramTemp = clampInt(
                    cfg.getInt("autoScramTemp", "safety", 1000, 100, 5000,
                            "自动停堆的临界温度（°C，按屏幕读取的平均堆芯温度判定）"),
                    100, 5000, 1000);

            enableHackerAttack = cfg.getBoolean("enableHackerAttack", "security", true,
                    "是否允许黑客攻击玩法：关闭后终端 hack 指令会被服务器拒绝");

            allowNormalPlayerHack = cfg.getBoolean("allowNormalPlayerHack", "security", true,
                    "是否允许普通玩家使用 hack 指令；设为 false 时仅 OP（权限等级 2）可用");

            if (cfg.hasChanged()) {
                cfg.save();
            }
        }
        catch (Throwable ignored) {
        }
    }

    public static int historyCapacitySafe() {
        return Math.max(30, Math.min(600, historyCapacity));
    }

    public static int windowSeconds() {
        return (int) (historyCapacitySafe() * sampleMs / 1000L);
    }

    private static int clampInt(int v, int min, int max, int def) {
        return v >= min && v <= max ? v : def;
    }

    private static long clampLong(long v, long min, long max, long def) {
        return v >= min && v <= max ? v : def;
    }

    private static double clampDouble(double v, double min, double max, double def) {
        return v >= min && v <= max ? v : def;
    }

    private static float clampFloat(float v, float min, float max, float def) {
        return v >= min && v <= max ? v : def;
    }
}
