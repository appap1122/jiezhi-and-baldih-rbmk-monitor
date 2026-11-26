package com.jiezhirbmkmod.client;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.jiezhirbmkmod.dfc.GraphHistory;
import com.jiezhirbmkmod.dfc.RbmkConfig;

import net.minecraft.util.math.BlockPos;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * v18.0 曲线历史落盘中枢（仅客户端）：把每块屏的 {@link GraphHistory}
 * 从"内存态"升级成"黑匣子"—— 存档重载、游戏重启、区块卸载重载之后，
 * 曲线屏的历史照旧接续，导出/回放拿到的是跨 session 的完整记录。
 *
 * <h3>为什么落盘在客户端而不是存档里</h3>
 * 采样本来就在客户端（{@code RbmkScreenCanvas.autoSample}，v18.1 起由
 * 客户端 tick 驱动的自动记录，与曲线屏同一份自采数据），服务端根本没有这份序列。多人联机时客户端也写不到
 * 服务器存档目录，所以与 {@code rbmk_export} 同款：落到本机
 * {@code <游戏目录>/rbmk_history/}，按 维度_坐标 一屏一个文件。纯客户端：
 * 专用服务器永远摸不到这个类。
 *
 * <h3>生命周期</h3>
 * <ul>
 *   <li>屏的 canvas 首次创建（区块加载 / 世界进入）→ {@link #seed} 从内存
 *       注册表或磁盘文件回灌历史；</li>
 *   <li>每次采样 → {@link #touch} 打脏标记（只置标志位，不碰盘）；</li>
 *   <li>客户端 tick 每 300 刻（约 15 秒）→ 脏的批量落盘；世界卸载 →
 *       全量落盘；</li>
 *   <li>canvas 销毁（区块卸载 / 屏拆除）→ {@link #checkpoint} 立刻落盘并
 *       注销，内存注册表不随拆屏累积。</li>
 * </ul>
 *
 * <p>所有盘 IO 都包在 try/catch 里：磁盘只读、文件损坏都只影响"那块屏的
 * 历史接不上"，绝不让客户端主循环掉帧或崩溃。文件先写临时名再原子改名，
 * 写一半断电不会留下半个坏文件顶替好文件。</p>
 */
@SideOnly(Side.CLIENT)
public final class HistoryStore {

    /** 文件魔数 + 版本（结构变化才升） */
    private static final int MAGIC = 0x52424D48; // "RBMH"
    private static final int VERSION = 1;

    private static final int FLUSH_INTERVAL_TICKS = 300;

    /** 内存注册表：GraphHistory → 归属键（dim:x,y,z）。按实例跟踪，无 pos 反查需求 */
    private static final Map<GraphHistory, String> REG =
            new IdentityHashMap<GraphHistory, String>();
    /** 本 session 已被 seed 过的键 → 仍活着的实例（区块重载时从内存回灌，快于磁盘） */
    private static final Map<String, GraphHistory> LIVE =
            new HashMap<String, GraphHistory>();
    private static final Set<GraphHistory> DIRTY = new HashSet<GraphHistory>();
    private static int tickCounter = 0;

    private HistoryStore() {
    }

    // ==================== 调用点 ====================

    /**
     * canvas 首次创建时调用：从内存注册表或磁盘把历史回灌进新 history。
     * 键 = 维度 + 坐标 —— 同一块屏不管 TE 重建几次，接的都是同一份。
     */
    public static void seed(int dim, BlockPos pos, GraphHistory h) {
        if (h == null || pos == null) {
            return;
        }
        String key = key(dim, pos);
        try {
            GraphHistory existing = HistoryStore.LIVE.get(key);
            if (existing != null && existing != h) {
                copyInto(existing, h);
                HistoryStore.REG.remove(existing);
                HistoryStore.REG.put(h, key);
                HistoryStore.LIVE.put(key, h);
                // v18.0 自查修复：旧实例可能还挂着脏标记（采样后未到 flush
                // 点）——不摘掉的话 flushAll 摸到它时 REG 里已查无此实例，
                // 数据其实已搬进 h，但 h 自己没有脏标记：如果这之后一直没
                // 再采样（屏断线）就撞上世界卸载/退出，回灌的历史会丢。
                // 两边各归其位：旧实例出队、新实例入队。
                HistoryStore.DIRTY.remove(existing);
                HistoryStore.DIRTY.add(h);
                return;
            }
            if (existing == h) {
                return; // 同一实例重复 seed，不可能但防一手
            }
            File f = fileOf(key);
            if (f != null && f.isFile() && loadInto(f, h)) {
                // 磁盘回灌成功：同样补脏标记 —— 若一直没再采样就遇到
                // 世界卸载，flushAll 才有机会把这份接回来的历史写回去
                HistoryStore.DIRTY.add(h);
            }
            HistoryStore.REG.put(h, key);
            HistoryStore.LIVE.put(key, h);
        }
        catch (Throwable ignored) {
            // 注册表损坏不该影响曲线屏本身
        }
    }

    /** 每次采样后调用：只置脏标记，盘 IO 交给批量 flush */
    public static void touch(GraphHistory h) {
        if (h != null && HistoryStore.REG.containsKey(h)) {
            HistoryStore.DIRTY.add(h);
        }
    }

    /** canvas 销毁时调用：立刻落盘并注销，拆屏/卸区块不占内存 */
    public static void checkpoint(GraphHistory h) {
        if (h == null) {
            return;
        }
        try {
            String key = HistoryStore.REG.remove(h);
            HistoryStore.DIRTY.remove(h);
            if (key == null) {
                return; // 从没注册过（非屏幕类 canvas），不落盘
            }
            if (h.hasData()) {
                save(key, h);
            } else {
                // 清零后的屏删掉旧文件，避免"拆了重建还在放老曲线"
                File f = fileOf(key);
                if (f != null && f.isFile()) {
                    f.delete();
                }
            }
            HistoryStore.LIVE.remove(key, h);
        }
        catch (Throwable ignored) {
        }
    }

    /** 把所有脏的批量写盘（定时 flush 与世界卸载共用） */
    public static void flushAll() {
        if (HistoryStore.DIRTY.isEmpty()) {
            return;
        }
        for (GraphHistory h : new HashSet<GraphHistory>(HistoryStore.DIRTY)) {
            try {
                String key = HistoryStore.REG.get(h);
                if (key != null && h.hasData()) {
                    save(key, h);
                }
            }
            catch (Throwable ignored) {
            }
        }
        HistoryStore.DIRTY.clear();
    }

    // ==================== 事件挂点（RbmkClientProxy 注册本类） ====================

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (++HistoryStore.tickCounter >= FLUSH_INTERVAL_TICKS) {
            HistoryStore.tickCounter = 0;
            HistoryStore.flushAll();
        }
    }

    @SubscribeEvent
    public static void onWorldUnload(WorldEvent.Unload event) {
        if (event.getWorld() != null && event.getWorld().isRemote) {
            HistoryStore.flushAll();
        }
    }

    // ==================== 序列化 ====================

    /** 键 → 文件：{@code <游戏目录>/rbmk_history/dim<x>_<x>_<y>_<z>.dat} */
    private static File fileOf(String key) {
        try {
            File dir = dirOf();
            if (dir == null) {
                return null;
            }
            return new File(dir, key.replace(':', '_').replace(',', '_') + ".dat");
        }
        catch (Throwable t) {
            return null;
        }
    }

    /** v18.2：黑匣子目录（{@code <游戏目录>/rbmk_history/}） */
    static File dirOf() {
        try {
            File dir = new File(net.minecraft.client.Minecraft.getMinecraft().gameDir, "rbmk_history");
            if (!dir.isDirectory() && !dir.mkdirs()) {
                return null;
            }
            return dir;
        }
        catch (Throwable t) {
            return null;
        }
    }

    private static String key(int dim, BlockPos pos) {
        return "dim" + dim + ":" + pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    /**
     * 把 src 的样本灌进 dst（dst 先清零）。src 的环形序就是逻辑序
     * （老→新），逐格 {@code push} 即可原样重建，NaN 走 {@code pushGap}。
     */
    private static void copyInto(GraphHistory src, GraphHistory dst) {
        dst.clear();
        int n = src.size();
        for (int i = 0; i < n; ++i) {
            if (src.gapAt(i)) {
                dst.pushGap();
            } else {
                dst.push(src.valueAt(0, i), src.valueAt(1, i), src.valueAt(2, i),
                        src.valueAt(3, i), src.valueAt(4, i), src.valueAt(5, i),
                        src.alertAt(i));
            }
        }
    }

    private static boolean loadInto(File f, GraphHistory h) {
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(
                new java.io.FileInputStream(f), 8192))) {
            if (in.readInt() != MAGIC || in.readInt() != VERSION) {
                return false; // 异版文件：宁可不接，也不按错误结构乱读
            }
            long ms = in.readLong();
            if (ms != RbmkConfig.sampleMs) {
                // 采样间隔变了：时间轴对不上，同样放弃接续
                return false;
            }
            int n = in.readInt();
            if (n < 0 || n > 600) {
                return false;
            }
            h.clear();
            for (int i = 0; i < n; ++i) {
                double a = Double.longBitsToDouble(in.readLong());
                double b = Double.longBitsToDouble(in.readLong());
                double c = Double.longBitsToDouble(in.readLong());
                double d = Double.longBitsToDouble(in.readLong());
                double e = Double.longBitsToDouble(in.readLong());
                double g = Double.longBitsToDouble(in.readLong());
                byte alert = in.readByte();
                if (Double.isNaN(a)) {
                    h.pushGap();
                } else {
                    h.push(a, b, c, d, e, g, alert);
                }
            }
            return true;
        }
        catch (Throwable t) {
            // v18.0 自查修复：clear 之后逐条 push，读到一半断掉时 h 里
            // 会留半截数据 —— seed 照常注册这个半截实例，之后的新采样
            // 接在旧 session 的尾巴后面，画出来是一条错的时间轴。
            // 宁可从空白重新开始，也不接半截。
            try {
                h.clear();
            }
            catch (Throwable ignored) {
            }
            return false;
        }
    }

    /** 原子写：先写 .tmp 再改名，写一半断电不会顶替好文件 */
    private static void save(String key, GraphHistory h) {
        File f = fileOf(key);
        if (f == null) {
            return;
        }
        File tmp = new File(f.getParentFile(), f.getName() + ".tmp");
        try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(
                new java.io.FileOutputStream(tmp), 8192))) {
            out.writeInt(MAGIC);
            out.writeInt(VERSION);
            out.writeLong(RbmkConfig.sampleMs);
            int n = h.size();
            out.writeInt(n);
            for (int i = 0; i < n; ++i) {
                if (h.gapAt(i)) {
                    for (int c = 0; c < 6; ++c) {
                        out.writeLong(Double.doubleToRawLongBits(Double.NaN));
                    }
                    out.writeByte(0);
                } else {
                    for (int c = 0; c < 6; ++c) {
                        out.writeLong(Double.doubleToRawLongBits(h.valueAt(c, i)));
                    }
                    out.writeByte(h.alertAt(i));
                }
            }
        }
        catch (Throwable t) {
            tmp.delete();
            return;
        }
        try {
            Files.move(tmp.toPath(), f.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
        catch (Throwable t) {
            tmp.delete();
            return;
        }
        HistoryStore.indexUpdate(key, h.size());
    }

    // ==================== v18.2 黑匣子增强：索引 + 跨屏合并 ====================

    /** 索引文件名（与 .dat 同目录） */
    private static final String INDEX_NAME = "index.txt";

    /**
     * 索引行更新（save 成功后调用）：{@code key \t samples \t lastSavedMs}。
     * 索引是给人 grep 与 {@code /rbmkhistory list} 用的，坏了不影响黑匣子本体。
     */
    private static void indexUpdate(String key, int samples) {
        try {
            File dir = dirOf();
            File idx = dir == null ? null : new File(dir, INDEX_NAME);
            if (idx == null) {
                return;
            }
            java.util.LinkedHashMap<String, String> rows = new java.util.LinkedHashMap<String, String>();
            if (idx.isFile()) {
                try (java.io.BufferedReader r = new java.io.BufferedReader(new java.io.FileReader(idx))) {
                    String line;
                    while ((line = r.readLine()) != null) {
                        int tab = line.indexOf('\t');
                        if (tab > 0) {
                            rows.put(line.substring(0, tab), line.substring(tab + 1));
                        }
                    }
                }
                catch (Throwable ignored) {
                    // 索引坏了就当没有，重建
                }
            }
            rows.put(key, samples + "\t" + System.currentTimeMillis());
            try (java.io.PrintWriter w = new java.io.PrintWriter(
                    new java.io.OutputStreamWriter(new java.io.FileOutputStream(idx),
                            java.nio.charset.StandardCharsets.UTF_8))) {
                for (Map.Entry<String, String> e : rows.entrySet()) {
                    w.println(e.getKey() + "\t" + e.getValue());
                }
            }
        }
        catch (Throwable ignored) {
        }
    }

    /** 索引内容（{@code /rbmkhistory list} 用）：每行 [键 采样数 最后保存时间] */
    public static java.util.List<String> indexLines() {
        java.util.List<String> out = new java.util.ArrayList<String>();
        try {
            File dir = dirOf();
            File idx = dir == null ? null : new File(dir, INDEX_NAME);
            if (idx != null && idx.isFile()) {
                try (java.io.BufferedReader r = new java.io.BufferedReader(new java.io.FileReader(idx))) {
                    String line;
                    while ((line = r.readLine()) != null) {
                        out.add(line);
                    }
                }
            }
        }
        catch (Throwable ignored) {
        }
        return out;
    }

    /**
     * v18.2 跨屏历史合并（迁移存档/换屏场景）：把 from 屏的黑匣子文件
     * <b>拼接到</b> to 屏的历史后面，中间垫一个采样断口（与曲线页断口
     * 同一标记，视觉上一眼看出"这里换过屏"）。环形容量装不下时自动
     * 挤掉最老的样本。to 屏在区块里就写进活历史（立刻可见），不在就
     * 直接改盘上文件。
     *
     * @return 合并的采样数；null-错误串返回抛出 IllegalArgumentException
     */
    public static int mergeHistory(int srcDim, BlockPos srcPos, int dstDim, BlockPos dstPos) {
        if (srcPos == null || dstPos == null) {
            throw new IllegalArgumentException("coordinates required");
        }
        String srcKey = HistoryStore.key(srcDim, srcPos);
        String dstKey = HistoryStore.key(dstDim, dstPos);
        if (srcKey.equals(dstKey)) {
            throw new IllegalArgumentException("source and target are the same screen");
        }
        File srcFile = HistoryStore.fileOf(srcKey);
        if (srcFile == null || !srcFile.isFile()) {
            throw new IllegalArgumentException("no black box file for source screen " + srcKey);
        }
        GraphHistory src = new GraphHistory();
        if (!HistoryStore.loadInto(srcFile, src) || !src.hasData()) {
            throw new IllegalArgumentException("source black box is empty or unreadable (" + srcKey + ")");
        }
        int moved = src.size();
        try {
            // 目标优先走活历史（屏在区块里 → 合并立刻上屏）
            GraphHistory dst = HistoryStore.LIVE.get(dstKey);
            if (dst == null) {
                File dstFile = HistoryStore.fileOf(dstKey);
                dst = new GraphHistory();
                if (dstFile != null && dstFile.isFile()) {
                    HistoryStore.loadInto(dstFile, dst);
                }
                HistoryStore.REG.put(dst, dstKey);
                HistoryStore.LIVE.put(dstKey, dst);
            }
            // 断口 + 逐点拼接（环形容量自动挤掉最老）
            dst.pushGap();
            int n = src.size();
            for (int i = 0; i < n; ++i) {
                if (src.gapAt(i)) {
                    dst.pushGap();
                } else {
                    dst.push(src.valueAt(0, i), src.valueAt(1, i), src.valueAt(2, i),
                            src.valueAt(3, i), src.valueAt(4, i), src.valueAt(5, i),
                            src.alertAt(i));
                }
            }
            HistoryStore.DIRTY.add(dst);
            HistoryStore.flushAll();
        }
        catch (IllegalArgumentException e) {
            throw e;
        }
        catch (Throwable t) {
            throw new IllegalArgumentException("merge failed: " + t);
        }
        AuditStyleBridge.logMerge(srcKey, dstKey, moved);
        return moved;
    }

    /** 合并落审计：借助服务端 AuditLog 有同 JVM 静态写入通道（单人/LAN） */
    private static final class AuditStyleBridge {
        static void logMerge(String from, String to, int samples) {
            try {
                com.jiezhirbmkmod.dfc.AuditLog.log(null, "HISTORY_MERGE",
                        from + " -> " + to + " samples=" + samples);
            }
            catch (Throwable ignored) {
            }
        }
    }

    /** 诊断用：注册表规模（/rbmkcompat 未来可引用，当前仅供排障） */
    static int trackedCount() {
        return HistoryStore.REG.size();
    }
}
