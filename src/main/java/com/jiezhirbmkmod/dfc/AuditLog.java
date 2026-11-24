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

    /** 单文件上限（字节），超过即轮转为 .old */
    private static final long MAX_BYTES = 4L * 1024L * 1024L;
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
                    File old = new File(AuditLog.file.getParentFile(), "rbmk_audit.log.old");
                    if (old.exists() && !old.delete()) {
                        // 轮不动就先继续写，宁可超限也不丢这一条
                    }
                    if (!AuditLog.file.renameTo(old)) {
                        // 同上：改名失败不致命
                    }
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
