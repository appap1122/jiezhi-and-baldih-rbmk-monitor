package com.jiezhirbmkmod.dfc;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.Date;
import java.util.Deque;

import net.minecraftforge.fml.common.FMLCommonHandler;

/**
 * v17.7 权限体系收尾 · 审计日志（AuditLog）。
 *
 * <p>权限体系的三根柱子（工牌、签发器、黑客开关）都有了，缺一条
 * 事后可回溯的审计链：谁签发了卡、卡在谁的哪块屏上用过一次、黑客
 * 开关何时被谁改了、谁的屏被谁攻击了。全部写入服务端存档目录下的
 * {@code rbmk_audit.log}，纯追加、带轮转，崩服不丢（每条即写即刷）。</p>
 *
 * <p>设计要点：</p>
 * <ul>
 *   <li><b>服务端专用</b>：所有调用点都在服务端线程或 addScheduledTask
 *       里；方法内部用同一把锁，文件单写者；</li>
 *   <li><b>轮转</b>：超过 {@link #MAX_BYTES} 改名为 {@code .old} 重开，
 *       永远只保留一代旧文件 —— 审计是追责用的，不是归档用的；</li>
 *   <li><b>绝不抛出</b>：磁盘满 / 目录只读这类故障只允许降级为控制台
 *       警告一次，绝不能把一次正常的发卡操作变成服务端报错；</li>
 *   <li><b>内存尾迹</b>：最近 {@link #TAIL} 条留在内存，供
 *       {@code /rbmkaudit} 直接打印，不用读文件。</li>
 * </ul>
 *
 * <p>行格式：{@code [yyyy-MM-dd HH:mm:ss] [玩家] 动作 | 详情}，逗号分隔
 * 字段不加引号 —— 这不是给程序解析的 CSV，是给人 grep 的流水。</p>
 */
public final class AuditLog {

    /** 单文件上限（字节），超过即轮转 */
    private static final long MAX_BYTES = 4L * 1024L * 1024L;
    /** v18.2 保留的旧代数（rbmk_audit.1.log ~ .3.log，比旧版单代 .old 更耐查） */
    private static final int GENERATIONS = 3;
    /** 内存尾迹条数 */
    private static final int TAIL = 200;

    private static final Object LOCK = new Object();
    private static File file;
    private static boolean warned = false;
    private static final Deque<String> tail = new ArrayDeque<String>();

    private AuditLog() {
    }

    /** 写一条审计：玩家可为空（系统事件），动作用大写词，详情任意。 */
    public static void log(String player, String action, String detail) {
        String ts = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
        String line = "[" + ts + "] [" + (player == null || player.isEmpty() ? "SYSTEM" : player)
                + "] " + action + (detail == null || detail.isEmpty() ? "" : " | " + detail);
        synchronized (AuditLog.LOCK) {
            tail.addLast(line);
            while (tail.size() > TAIL) {
                tail.removeFirst();
            }
            try {
                if (AuditLog.file == null) {
                    File dir = FMLCommonHandler.instance().getMinecraftServerInstance() == null
                            ? null
                            : FMLCommonHandler.instance().getMinecraftServerInstance().getDataDirectory();
                    if (dir == null) {
                        return;
                    }
                    AuditLog.file = new File(dir, "rbmk_audit.log");
                }
                if (AuditLog.file.length() > MAX_BYTES) {
                    AuditLog.rotate();
                }
                try (FileWriter w = new FileWriter(AuditLog.file, true)) {
                    w.write(line);
                    w.write('\n');
                }
                AuditLog.warned = false;
            } catch (Throwable t) {
                if (!AuditLog.warned) {
                    AuditLog.warned = true;
                    System.out.println("[jiezhirbmkmod] audit log unavailable: " + t);
                }
            }
        }
    }

    /**
     * v18.2 玩家动作审计：除名字外追加 {@code uuid=<带连字符全串>}，
     * 改名/伪造昵称不再能洗白身份。player 为 null 时与 {@link #log}
     * 等价（SYSTEM 行）。
     */
    public static void logPlayer(net.minecraft.entity.player.EntityPlayerMP player,
            String action, String detail) {
        if (player == null) {
            AuditLog.log(null, action, detail);
            return;
        }
        String uuid;
        try {
            uuid = player.getUniqueID() == null ? "?" : player.getUniqueID().toString();
        }
        catch (Throwable t) {
            uuid = "?";
        }
        AuditLog.log(player.getName(), action,
                (detail == null || detail.isEmpty() ? "" : detail + " ") + "uuid=" + uuid);
    }

    /**
     * v18.2 多代轮转：{@code .3←.2←.1←当前}，最老一代直接丢弃。
     * 某一代改名失败不影响后面代次 —— 宁可少留一代也不丢当前写入。
     */
    private static void rotate() {
        File dir = AuditLog.file.getParentFile();
        String base = AuditLog.file.getName(); // rbmk_audit.log
        // 先删最老一代，再逐代后移
        File oldest = new File(dir, base.replace(".log", "") + "." + GENERATIONS + ".log");
        if (oldest.exists()) {
            oldest.delete();
        }
        for (int g = GENERATIONS - 1; g >= 1; --g) {
            File from = new File(dir, base.replace(".log", "") + "." + g + ".log");
            File to = new File(dir, base.replace(".log", "") + "." + (g + 1) + ".log");
            if (from.exists()) {
                from.renameTo(to);
            }
        }
        File firstGen = new File(dir, base.replace(".log", "") + ".1.log");
        AuditLog.file.renameTo(firstGen);
        // 兼容旧版 .old：趁轮转顺手归入 .1（已有 .1 时丢弃）
        File legacy = new File(dir, base.replace(".log", "") + ".log.old");
        if (legacy.exists() && !firstGen.exists()) {
            legacy.renameTo(firstGen);
        } else if (legacy.exists()) {
            legacy.delete();
        }
    }

    /** 最近 n 条（命令用），最多 TAIL 条；新的在后。 */
    public static String[] tail(int n) {
        synchronized (AuditLog.LOCK) {
            int count = Math.max(0, Math.min(n, tail.size()));
            String[] out = new String[count];
            int i = count - 1;
            java.util.Iterator<String> it = tail.descendingIterator();
            while (i >= 0 && it.hasNext()) {
                out[i--] = it.next();
            }
            return out;
        }
    }
}
