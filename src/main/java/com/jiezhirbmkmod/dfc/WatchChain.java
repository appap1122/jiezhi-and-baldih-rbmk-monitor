package com.jiezhirbmkmod.dfc;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.jiezhirbmkmod.RbmkMonitorMod;
import com.jiezhirbmkmod.Tags;
import com.jiezhirbmkmod.block.TileRbmkSiren;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * v18.2 阈值自动化防护链（WatchChain）。
 *
 * <p>开关 {@link RbmkConfig#allowThresholdWatch} 默认关，审批流程与黑客
 * 玩法开关完全一致（{@link HackAppManager} 泛化 kind）。开启后终端可用：</p>
 * <pre>
 *   watch heat &gt; 900 do scram    # 平均柱温超 900 自动 AZ-5
 *   watch peak &gt; 1100 do alarm   # 峰值柱温超 1100 全员报警
 *   watch flux &lt; 50 do audit     # 通量骤降记审计
 *   watch list / watch clear / watch del N
 * </pre>
 *
 * <p>规则按「绑定的 RBMK 控制台」为键（dim:x:y:z），落盘
 * {@code rbmk_watch.json}（存档目录），重启不丢。每 20 tick（1 秒）
 * 扫描一轮：直接读控制台快照（与终端 status 同一条 {@link RbmkBridge}
 * 通道），命中且过冷却（默认 10 秒）才执行动作，绝不在一秒内连发 SCRAM。</p>
 *
 * <p>动作三件套：</p>
 * <ul>
 *   <li><b>scram</b>：AZ-5 —— 全部手动控制棒插到底（复用
 *       {@link RbmkControl}），写审计 + 通知在线管理员；</li>
 *   <li><b>alarm</b>：全员（管理员优先）聊天告警 + 周边警报灯汽笛
 *       ({@link TileRbmkSiren#testBlare}) + 写审计；</li>
 *   <li><b>audit</b>：只写审计（事后可查）。</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = Tags.MODID)
public final class WatchChain {

    // ---- 规则模型 ----

    public static final String ACTION_SCRAM = "scram";
    public static final String ACTION_ALARM = "alarm";
    public static final String ACTION_AUDIT = "audit";
    public static final String[] ACTIONS = {ACTION_SCRAM, ACTION_ALARM, ACTION_AUDIT};

    /** 规则（Gson 直接反序列化的结构，字段名即 JSON 键） */
    public static final class Rule {
        public String key;      // dim:x:y:z（RBMK 控制台）
        public String metric;   // heat/peak/flux/water/steam/enrich/xenon
        public String op;       // ">" 或 "<"
        public double threshold;
        public String action;   // scram/alarm/audit
        public String createdBy;
        public long createdAt;

        /** 命中判定的可读形式（watch list 用） */
        public String describe() {
            return metric + " " + op + " " + trimNum(threshold) + " do " + action;
        }
    }

    private static final long SCAN_INTERVAL_TICKS = 20L;
    /** 同一条规则两次动作之间的冷却（tick），防止一秒内连发 */
    private static final long ACTION_COOLDOWN_TICKS = 200L;
    private static final int MAX_RULES = 64;

    private static final Object LOCK = new Object();
    private static final List<Rule> RULES = new ArrayList<Rule>();
    private static final List<Long> LAST_FIRE = new ArrayList<Long>();
    private static File storeFile;
    private static long tickCounter = 0L;
    private static boolean loaded = false;

    private WatchChain() {
    }

    // ---- 存取（Gson 落盘）----

    private static File store() {
        if (WatchChain.storeFile == null) {
            MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
            if (server == null) {
                return null;
            }
            WatchChain.storeFile = new File(server.getDataDirectory(), "rbmk_watch.json");
        }
        return WatchChain.storeFile;
    }

    private static void loadIfNeeded() {
        if (WatchChain.loaded) {
            return;
        }
        WatchChain.loaded = true;
        File f = store();
        if (f == null || !f.exists()) {
            return;
        }
        try (FileReader r = new FileReader(f)) {
            Type t = new TypeToken<List<Rule>>() { }.getType();
            List<Rule> from = new Gson().fromJson(r, t);
            if (from != null) {
                RULES.clear();
                LAST_FIRE.clear();
                for (Rule rule : from) {
                    if (rule != null && rule.key != null && rule.metric != null) {
                        RULES.add(rule);
                        LAST_FIRE.add(0L);
                    }
                }
            }
        }
        catch (Throwable t) {
            RbmkMonitorMod.warn("watch chain load failed: {}", t.toString());
        }
    }

    private static void save() {
        File f = store();
        if (f == null) {
            return;
        }
        try (java.io.Writer w = new java.io.OutputStreamWriter(
                new java.io.FileOutputStream(f), StandardCharsets.UTF_8)) {
            new Gson().toJson(RULES, w);
        }
        catch (Throwable t) {
            RbmkMonitorMod.warn("watch chain save failed: {}", t.toString());
        }
    }

    // ---- 终端命令入口 ----

    /** metric 是否合法 */
    public static boolean isMetric(String m) {
        return metricIndex(m) >= 0;
    }

    private static int metricIndex(String m) {
        if (m == null) return -1;
        switch (m.toLowerCase()) {
            case "heat": return 0;
            case "peak": return 1;
            case "flux": return 2;
            case "water": return 3;
            case "steam": return 4;
            case "enrich": case "enrichment": return 5;
            case "xenon": return 6;
            default: return -1;
        }
    }

    public static boolean isAction(String a) {
        if (a == null) return false;
        for (String s : ACTIONS) {
            if (s.equalsIgnoreCase(a)) return true;
        }
        return false;
    }

    /** 添加规则；返回错误信息（null = 成功） */
    public static String addRule(World world, BlockPos consolePos, String metric, String op,
            double threshold, String action, String player) {
        if (metricIndex(metric) < 0) {
            return "unknown metric '" + metric + "' (heat/peak/flux/water/steam/enrich/xenon)";
        }
        if (!">".equals(op) && !"<".equals(op)) {
            return "unknown operator '" + op + "' (want > or <)";
        }
        if (!isAction(action)) {
            return "unknown action '" + action + "' (scram/alarm/audit)";
        }
        if (!Double.isFinite(threshold)) {
            return "threshold must be a number";
        }
        synchronized (WatchChain.LOCK) {
            WatchChain.loadIfNeeded();
            if (RULES.size() >= MAX_RULES) {
                return "rule table full (" + MAX_RULES + ")";
            }
            Rule rule = new Rule();
            rule.key = world.provider.getDimension() + ":" + consolePos.getX() + ":"
                    + consolePos.getY() + ":" + consolePos.getZ();
            rule.metric = metric.toLowerCase();
            rule.op = op;
            rule.threshold = threshold;
            rule.action = action.toLowerCase();
            rule.createdBy = player == null ? "terminal" : player;
            rule.createdAt = System.currentTimeMillis();
            RULES.add(rule);
            LAST_FIRE.add(0L);
            save();
        }
        AuditLog.log(player, "WATCH_ADD", metric + " " + op + " " + trimNum(threshold)
                + " do " + action + " @" + consolePos);
        return null;
    }

    /** 删除第 idx 条（1 起始）；返回被删规则的描述，null = 序号不存在 */
    public static String removeRule(int idx) {
        synchronized (WatchChain.LOCK) {
            WatchChain.loadIfNeeded();
            if (idx < 1 || idx > RULES.size()) {
                return null;
            }
            Rule rule = RULES.remove(idx - 1);
            LAST_FIRE.remove(idx - 1);
            save();
            AuditLog.log(null, "WATCH_DEL", rule.describe());
            return rule.describe();
        }
    }

    /** 清空全部规则；返回删除条数 */
    public static int clearRules() {
        synchronized (WatchChain.LOCK) {
            WatchChain.loadIfNeeded();
            int n = RULES.size();
            RULES.clear();
            LAST_FIRE.clear();
            if (n > 0) {
                save();
                AuditLog.log(null, "WATCH_CLEAR", n + " rules");
            }
            return n;
        }
    }

    /** 快照（watch list 用），不供修改 */
    public static List<Rule> snapshotRules() {
        synchronized (WatchChain.LOCK) {
            WatchChain.loadIfNeeded();
            return new ArrayList<Rule>(RULES);
        }
    }

    // ---- 每 tick 扫描 ----

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (++tickCounter % SCAN_INTERVAL_TICKS != 0L) {
            return;
        }
        if (!RbmkConfig.allowThresholdWatch) {
            return;
        }
        List<Rule> rules = snapshotRules();
        if (rules.isEmpty()) {
            return;
        }
        MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
        if (server == null) {
            return;
        }
        for (int i = 0; i < rules.size(); ++i) {
            Rule rule = rules.get(i);
            World world = worldFor(server, rule.key);
            if (world == null) {
                continue;
            }
            TileEntity console = consoleFor(world, rule.key);
            if (console == null || !RbmkBridge.isConsole(console)) {
                continue;
            }
            RbmkSnapshot s = RbmkSnapshot.empty();
            if (!RbmkBridge.read(console, s) || !s.linked || s.offline || s.cold || s.meltdown) {
                continue;
            }
            double value = metricValue(rule.metric, s);
            boolean hit = ">".equals(rule.op) ? value > rule.threshold
                    : value < rule.threshold;
            if (!hit) {
                continue;
            }
            long now = tickCounter;
            synchronized (WatchChain.LOCK) {
                if (i < LAST_FIRE.size()
                        && now - LAST_FIRE.get(i) < ACTION_COOLDOWN_TICKS) {
                    continue;
                }
                if (i < LAST_FIRE.size()) {
                    LAST_FIRE.set(i, now);
                }
            }
            fire(world, console, rule, value);
        }
    }

    private static World worldFor(MinecraftServer server, String key) {
        try {
            int dim = Integer.parseInt(key.substring(0, key.indexOf(':')));
            return DimensionManager.getWorld(dim);
        }
        catch (Throwable t) {
            return null;
        }
    }

    private static TileEntity consoleFor(World world, String key) {
        String[] seg = key.split(":");
        if (seg.length != 4) {
            return null;
        }
        try {
            return world.getTileEntity(new BlockPos(Integer.parseInt(seg[1]),
                    Integer.parseInt(seg[2]), Integer.parseInt(seg[3])));
        }
        catch (Throwable t) {
            return null;
        }
    }

    private static double metricValue(String metric, RbmkSnapshot s) {
        switch (metricIndex(metric)) {
            case 1: return s.maxHeat;
            case 2: return s.flux;
            case 3: return s.waterTotal;
            case 4: return s.steamTotal;
            case 5: return s.avgEnrichment;
            case 6: return s.avgXenon;
            default: return s.avgHeat;
        }
    }

    // ---- 动作 ----

    private static void fire(World world, TileEntity console, Rule rule, double value) {
        BlockPos center;
        try {
            center = RbmkControl.reactorCenter(world, console);
        }
        catch (Throwable t) {
            center = console.getPos();
        }
        String where = "dim" + world.provider.getDimension() + " @" + console.getPos();
        String detail = rule.describe() + " (now " + trimNum(value) + ") " + where;
        if (ACTION_SCRAM.equals(rule.action)) {
            int set = 0;
            try {
                List<TileEntity> rods = RbmkControl.collectRods(world, center, 7);
                for (TileEntity rod : rods) {
                    if (RbmkControl.setRodLevel(rod, 0.0D)) {
                        ++set;
                    }
                }
            }
            catch (Throwable t) {
                RbmkMonitorMod.warn("watch scram failed: {}", t.toString());
            }
            AuditLog.log(null, "WATCH_SCRAM", detail + " rods=" + set);
            announce(world, TextFormatting.RED, "[防护链] 阈值命中自动 SCRAM（" + rule.describe()
                    + "，当前 " + trimNum(value) + "），已插棒 " + set + " 根。" + where);
        } else if (ACTION_ALARM.equals(rule.action)) {
            AuditLog.log(null, "WATCH_ALARM", detail);
            announce(world, TextFormatting.YELLOW, "[防护链] 阈值命中报警（" + rule.describe()
                    + "，当前 " + trimNum(value) + "）。" + where);
            // 周边汽笛试鸣一声（不锁 on，避免规则反复命中把汽笛焊死在开）
            try {
                List<TileRbmkSiren> sirens = sirensNear(world, console.getPos(), 24);
                for (TileRbmkSiren siren : sirens) {
                    siren.testBlare();
                }
            }
            catch (Throwable ignored) {
                // 汽笛只是装饰动作，失败不影响报警
            }
        } else {
            AuditLog.log(null, "WATCH_HIT", detail);
        }
    }

    /** 扫描以 center 为中心的已加载汽笛方块实体（方块实体不是实体，必须走区块映射） */
    private static List<TileRbmkSiren> sirensNear(World world, BlockPos center, int radius) {
        List<TileRbmkSiren> out = new ArrayList<TileRbmkSiren>();
        int minY = Math.max(0, center.getY() - radius);
        int maxY = Math.min(255, center.getY() + radius);
        for (int cx = (center.getX() - radius) >> 4; cx <= (center.getX() + radius) >> 4; ++cx) {
            for (int cz = (center.getZ() - radius) >> 4; cz <= (center.getZ() + radius) >> 4; ++cz) {
                net.minecraft.world.chunk.Chunk chunk;
                try {
                    chunk = world.getChunkProvider().getLoadedChunk(cx, cz);
                }
                catch (Throwable ignored) {
                    continue;
                }
                if (chunk == null) {
                    continue;
                }
                for (TileEntity te : chunk.getTileEntityMap().values()) {
                    if (!(te instanceof TileRbmkSiren)) {
                        continue;
                    }
                    BlockPos p = te.getPos();
                    if (p.getY() < minY || p.getY() > maxY) {
                        continue;
                    }
                    if (p.distanceSq(center) <= (double) radius * (double) radius) {
                        out.add((TileRbmkSiren) te);
                    }
                }
            }
        }
        return out;
    }

    /** 全员聊天广播（管理员额外高亮） */
    private static void announce(World world, TextFormatting color, String text) {
        MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
        if (server == null) {
            return;
        }
        for (EntityPlayerMP p : server.getPlayerList().getPlayers()) {
            boolean op = com.jiezhirbmkmod.item.ItemRbmkOpCard.isTrueOp(p);
            p.sendStatusMessage(new TextComponentString(color + "[jiezhirbmkmod] " + text), false);
            if (op) {
                p.sendStatusMessage(new TextComponentString(TextFormatting.AQUA
                        + "[jiezhirbmkmod] ⚠ 防护链动作已执行，请尽快到场确认。"), false);
            }
        }
    }

    private static String trimNum(double v) {
        if (Math.abs(v) >= 100.0D) {
            return String.valueOf(Math.round(v));
        }
        return String.format(java.util.Locale.ROOT, "%.2f", Double.valueOf(v));
    }
}
