package com.jiezhirbmkmod.dfc;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
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
    /** v17.6: screen-space spotlight lighting for the alarm lights. */
    public static final boolean DEFAULT_ALARM_SSS = true;

    // ---- AI diagnostics (v17.0) -------------------------------------------
    public static final String DEFAULT_AI_URL = "https://api.openai.com/v1/chat/completions";
    /** local | ai | auto */
    public static String aiMode = "auto";
    public static String aiApiKey = "";
    public static String aiApiUrl = DEFAULT_AI_URL;
    private static File aiConfigFile;

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
    // v18.2 爆炸景观参数（对齐 HBM 式「喷泉碎片雨 + 弹坑阵 + 地表燃烧」）
    public static int explosionDebrisCount = 140;
    public static int explosionDebrisSeconds = 3;
    public static int explosionCraterCount = 32;
    public static int explosionCraterRadius = 22;
    public static int explosionFireCount = 28;
    public static int explosionVanishChance = 45;
    public static boolean enableAutoScram = true;
    public static int autoScramTemp = 1000;
    public static boolean enableHackerAttack = true;
    /**
     * v17.6: 黑客攻击玩法开关，<b>默认关</b>。开启只有一条路：申请人双击
     * 操作员工牌发起申请，全服主/管理员在各自的审批弹窗里投票，一票否决、
     * 全票通过才写回 true（{@link #setAllowNormalPlayerHack}）。关闭则只需
     * 任意一名管理员/房主同意。
     */
    public static boolean allowNormalPlayerHack = false;
    /**
     * v18.2 阈值自动化防护链开关，<b>默认关</b>。审批流程与黑客玩法开关
     * 完全一致（申请人双击操作员工牌发起，全票开 / 一名管理员关）。
     * 开启后终端 {@code watch} 命令可用：阈值监视规则 + 自动动作
     * （SCRAM / 报警 / 审计），由 {@link WatchChain} 每 20 tick 扫描执行。
     */
    public static boolean allowThresholdWatch = false;
    public static int defaultWarnC = DEFAULT_WARN_C;
    public static int defaultCritC = DEFAULT_CRIT_C;
    public static int defaultRateWarnD = DEFAULT_RATE_WARN_D;
    public static int defaultFluxWarnF = DEFAULT_FLUX_WARN_F;
    /**
     * ★ <b>自 17.6 起停用，保留此键只为老配置文件不报缺失。</b>
     *
     * <p>以前它是「自动」档的解析依据（true = 自动灯走 FBO，false = 走几何）。
     * FBO 是可能失灵的路径 —— 着色器编不出来、光影包让路都会让
     * {@code sssAvailable} 翻成 false，拿它当默认就意味着"自动"档的
     * 实际表现跟着运行时状态变：同是自动档，换个显卡、开个光影，
     * 灯与灯就不一样了。「自动」档的解析最终定稿为按环境选最优
     * （{@link TileAlarmLight#autoPath}：没光影选 FBO、编挂退几何、
     * 有光影选贴图）—— 无论解析到哪条，读的都是全局状态，所有自动灯
     * 同一时刻同一个答案。本字段不再被任何代码读取。</p>
     */
    public static boolean alarmSssLighting = DEFAULT_ALARM_SSS;

    /**
     * 本局 SSS 管线到底能不能用（着色器编出来了、没被 JVM 参数掐掉）。
     *
     * <p>★ 这个字段是"灯全黑"的最后一道保险：{@code spotMode = 强制 SSS} 的灯若遇到
     * 显卡不支持 GLSL 120 / 链接失败 / {@code -Djiezhirbmkmod.debugDepth=false}，
     * 屏幕空间那一侧什么都不画，几何那一侧又因为它"要 SSS"而跳过 —— 两不管 ⇒ 灯亮着但没光。
     * 由 {@code DepthDebugPass} 在注册成功时置 true、编译失败时置 false，
     * 灯这边只在客户端读，服务端不参与渲染所以不受影响。</p>
     */
    public static boolean sssAvailable = true;

    /**
     * 光影包此刻是否生效（OptiFine shader pack 开着）。
     *
     * <p>★ 「自动」档选最优渲染模式的依据之一。写在 common 包的配置类里而不是
     * 让方块实体直接反射 OptiFine，是因为 {@code TileAlarmLight} 是 common 类，
     * 专用服务器也会加载 —— 反射 client 类只该发生在客户端。维护者是
     * {@code AlarmLightVolume#onRenderWorldLast}：每帧一次、先于任何灯的渲染
     * 分派（探测本身带缓存，游戏内开关光影包约 1.6 秒内更新到这里）。
     * 面板文案读它会有同等的微小滞后，无碍。</p>
     */
    public static boolean shaderPackActive = false;

    private static boolean loaded = false;

    private RbmkConfig() {
    }

    public static void load(File configDir) {
        if (loaded) {
            return;
        }
        loaded = true;
        configDirectory = configDir;
        // read .ai_config first: it must survive even if the Forge cfg is broken
        readAiConfig(configDir);
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
                            "新放置显示屏的出厂刷新率（毫秒）。已有屏幕各自保留出厂值，「默认」档回到该值，不受此项改动影响"),
                    50, 1000, DEFAULT_REFRESH_MS);

            alarmSssLighting = cfg.getBoolean("sssLighting", "alarm", DEFAULT_ALARM_SSS,
                    "★ 已停用。以前决定「自动」档报警灯走 FBO 还是几何；现在「自动」"
                    + "档按环境自动选最优（没光影选 FBO、编挂退几何、有光影选贴图，"
                    + "见 TileAlarmLight#autoPath），本项不再参与任何判断。"
                    + "保留此键只为老配置文件不报缺失，改成任何值都不再有效果");

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

            // v18.2 爆炸景观参数
            explosionDebrisCount = clampInt(
                    cfg.getInt("debrisCount", "explosion", 140, 0, 400,
                            "核爆后喷泉式抛出的碎片方块总数（分秒陆续喷出，落地部分消失、部分成方块）"),
                    0, 400, 140);
            explosionDebrisSeconds = clampInt(
                    cfg.getInt("debrisSeconds", "explosion", 3, 1, 15,
                            "碎片喷泉持续多少秒（每 tick 均匀喷出）"),
                    1, 15, 3);
            explosionCraterCount = clampInt(
                    cfg.getInt("craterCount", "explosion", 32, 0, 120,
                            "主爆坑之外散布的小弹坑数量（弹坑阵）"),
                    0, 120, 32);
            explosionCraterRadius = clampInt(
                    cfg.getInt("craterRadius", "explosion", 22, 5, 48,
                            "弹坑阵散布的最大半径（格）"),
                    5, 48, 22);
            explosionFireCount = clampInt(
                    cfg.getInt("fireCount", "explosion", 28, 0, 120,
                            "地表随机燃烧点数量（落在实体方块上）"),
                    0, 120, 28);
            explosionVanishChance = clampInt(
                    cfg.getInt("vanishChance", "explosion", 45, 0, 100,
                            "碎片落地后直接消失（不落成方块）的概率百分比"),
                    0, 100, 45);

            enableAutoScram = cfg.getBoolean("enableAutoScram", "safety", true,
                    "是否开启自动紧急停堆（Auto-SCRAM）：堆芯温度超过 autoScramTemp 时自动把全部控制棒插到底");

            autoScramTemp = clampInt(
                    cfg.getInt("autoScramTemp", "safety", 1000, 100, 5000,
                            "自动停堆的临界温度（°C，按屏幕读取的平均堆芯温度判定）"),
                    100, 5000, 1000);

            enableHackerAttack = cfg.getBoolean("enableHackerAttack", "security", true,
                    "是否允许黑客攻击玩法：关闭后终端 hack 指令会被服务器拒绝");

            allowNormalPlayerHack = cfg.getBoolean("allowNormalPlayerHack", "security", false,
                    "黑客攻击玩法开关（默认关）：开启需全服主/管理员审批通过"
                    + "（玩家双击操作员工牌发起），关闭只需一名管理员/房主同意；"
                    + "false 时仅 OP / 持签发工牌者可发起网络攻击");

            if (cfg.hasChanged()) {
                cfg.save();
            }
        }
        catch (Throwable ignored) {
        }
    }

    // ---- AI diagnostics config (v17.0): hidden .ai_config ----------------

    private static boolean readAiConfig(File configDir) {
        try {
            if (configDir == null) {
                return false;
            }
            aiConfigFile = new File(configDir, ".ai_config");
            if (!aiConfigFile.isFile()) {
                return false;
            }
            List<String> lines = Files.readAllLines(aiConfigFile.toPath(), StandardCharsets.UTF_8);
            for (int i = 0; i < lines.size(); ++i) {
                String line = lines.get(i).trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                int eq = line.indexOf('=');
                if (eq <= 0) {
                    continue;
                }
                String key = line.substring(0, eq).trim().toLowerCase(java.util.Locale.ROOT);
                String value = line.substring(eq + 1).trim();
                if ("mode".equals(key)) {
                    aiMode = normalizeAiMode(value);
                } else if ("key".equals(key)) {
                    aiApiKey = value;
                } else if ("url".equals(key) && !value.isEmpty()) {
                    aiApiUrl = value;
                }
            }
            return true;
        }
        catch (Throwable ignored) {
            return false;
        }
    }

    /** persist mode / key / url to <configDir>/.ai_config (plain text, hidden) */
    public static void saveAiConfig() {
        if (aiConfigFile == null) {
            return;
        }
        try {
            File parent = aiConfigFile.getParentFile();
            if (parent != null && !parent.isDirectory()) {
                parent.mkdirs();
            }
            StringBuilder sb = new StringBuilder();
            sb.append("# RBMK-Monitor AI diagnostics config\n");
            sb.append("mode=").append(aiMode).append('\n');
            sb.append("key=").append(aiApiKey).append('\n');
            sb.append("url=").append(aiApiUrl).append('\n');
            Files.write(aiConfigFile.toPath(), sb.toString().getBytes(StandardCharsets.UTF_8));
            try {
                Files.setAttribute(aiConfigFile.toPath(), "dos:hidden", Boolean.TRUE);
            }
            catch (Throwable ignored2) {
                // non-Windows file systems: the dot prefix is the hidden marker
            }
        }
        catch (Throwable ignored) {
        }
    }

    public static boolean hasAiKey() {
        return aiApiKey != null && !aiApiKey.trim().isEmpty();
    }

    public static String aiModeName() {
        return aiMode == null ? "auto" : aiMode;
    }

    public static void setAiMode(String mode) {
        aiMode = normalizeAiMode(mode);
        saveAiConfig();
    }

    public static void setAiKey(String key) {
        aiApiKey = key == null ? "" : key.trim();
        saveAiConfig();
    }

    public static void setAiUrl(String url) {
        String u = url == null ? "" : url.trim();
        aiApiUrl = u.isEmpty() ? DEFAULT_AI_URL : u;
        saveAiConfig();
    }

    public static String aiConfigPath() {
        return aiConfigFile == null ? "(未初始化)" : aiConfigFile.getAbsolutePath();
    }

    private static String normalizeAiMode(String mode) {
        if (mode == null) {
            return "auto";
        }
        String m = mode.trim().toLowerCase(java.util.Locale.ROOT);
        return "local".equals(m) || "ai".equals(m) ? m : "auto";
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

    // ---- v17.6: 黑客攻击玩法开关联动（审批通过/否决后写回配置文件）----

    /** load() 时记录的配置目录，写回开关时用 */
    private static File configDirectory;

    /**
     * 审批结果落地：更新内存开关并写回 {@code jiezhirbmkmod.cfg} 的
     * {@code security.allowNormalPlayerHack}，重启后保持。
     */
    public static void setAllowNormalPlayerHack(boolean value) {
        allowNormalPlayerHack = value;
        File dir = configDirectory;
        if (dir == null) {
            return;
        }
        try {
            Configuration cfg = new Configuration(new File(dir, "jiezhirbmkmod.cfg"));
            cfg.load();
            cfg.get("security", "allowNormalPlayerHack", value,
                    "黑客攻击玩法开关（默认关）：开启需全服主/管理员审批通过"
                    + "（玩家双击操作员工牌发起），关闭只需一名管理员/房主同意；"
                    + "false 时仅 OP / 持签发工牌者可发起网络攻击").set(value);
            if (cfg.hasChanged()) {
                cfg.save();
            }
        }
        catch (Throwable ignored) {
            // 写回失败只影响重启后的持久化，本次会话开关仍然生效
        }
    }

    /**
     * v18.2 防护链审批结果落地：更新内存开关并写回
     * {@code jiezhirbmkmod.cfg} 的 {@code security.allowThresholdWatch}。
     */
    public static void setAllowThresholdWatch(boolean value) {
        allowThresholdWatch = value;
        File dir = configDirectory;
        if (dir == null) {
            return;
        }
        try {
            Configuration cfg = new Configuration(new File(dir, "jiezhirbmkmod.cfg"));
            cfg.load();
            cfg.get("security", "allowThresholdWatch", value,
                    "阈值自动化防护链开关（默认关）：开启流程与黑客玩法一致"
                            + "（双击操作员工牌申请，全票通过才开）；开启后终端 watch "
                            + "命令可用：阈值监视 + 自动 SCRAM/报警/审计").set(value);
            if (cfg.hasChanged()) {
                cfg.save();
            }
        }
        catch (Throwable ignored) {
            // 写回失败只影响重启后的持久化，本次会话开关仍然生效
        }
    }
}
