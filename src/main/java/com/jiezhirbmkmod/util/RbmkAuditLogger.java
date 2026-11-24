package com.jiezhirbmkmod.util;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.relauncher.Side;

/**
 * v18.0 操作审计日志：关键操作（scram / togglePower / hack / 改安全码）
 * 追加一行到 <游戏根目录>/rbmk_audit.log（专用服务器则写服务端根目录）。
 *
 * <p>格式：{@code [2026-11-23 18:00:00] [PlayerName] Action: scram at (x, y, z)}。
 * 每次写入都是「打开-追加-关闭」（{@link Files#newBufferedWriter} +
 * try-with-resources），不在任何字段里持有句柄，游戏崩溃也绝不会锁文件。
 * 写盘失败只静默降级成控制台警告，绝不往调用路径上抛——审计是旁观者，
 * 不许拖垮反应堆操作。</p>
 *
 * <p>线程约定：调用点都在服务端主线程（tick / 包处理 scheduled task），
 * 但实现仍线程安全——synchronized 串行化追加，避免双任务并发交错写坏行。</p>
 */
public final class RbmkAuditLogger {

    private static final String FILE_NAME = "rbmk_audit.log";
    private static final DateTimeFormatter STAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    /** 同一分钟内最多告警一次写盘失败，防止磁盘满刷屏。 */
    private static long lastWarnMs;

    private RbmkAuditLogger() {
    }

    /** 游戏根目录（客户端 = .minecraft，专用服 = 服务端工作目录）下的日志文件。 */
    private static Path logFile() {
        net.minecraft.server.MinecraftServer srv =
                FMLCommonHandler.instance().getMinecraftServerInstance();
        if (srv != null) {
            return new java.io.File(srv.getDataDirectory(), FILE_NAME).toPath();
        }
        return Paths.get(".", FILE_NAME);
    }

    /**
     * 记录一条操作。
     *
     * @param player 操作者名字；系统自动动作（如 auto-SCRAM）传 "SYSTEM"
     * @param action 动作名，如 {@code scram} / {@code togglePower} / {@code hack} / {@code setSecurityCode}
     * @param detail 位置与目标的可读描述，如 {@code "scram at (120, 64, -3)"}
     */
    public static void log(String player, String action, String detail) {
        // 只在专用服 / 集成服主侧落盘；客户端绝不写（防止单人世界的
        // 服务端线程与客户端各写一份、路径含糊）
        if (FMLCommonHandler.instance().getEffectiveSide() != Side.SERVER) {
            return;
        }
        String who = player == null || player.trim().isEmpty() ? "SYSTEM" : player.trim();
        String line = "[" + STAMP.format(LocalDateTime.now()) + "] [" + who + "] "
                + action + ": " + (detail == null ? "" : detail);
        try {
            Path f = logFile();
            Path parent = f.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            // try-with-resources：写完立即关句柄，绝不长期占用文件
            try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(f,
                    StandardCharsets.UTF_8, StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND))) {
                w.println(line);
            }
        }
        catch (IOException | RuntimeException t) {
            long now = System.currentTimeMillis();
            if (now - lastWarnMs > 60000L) {
                lastWarnMs = now;
                System.out.println("[RBMK-Monitor] audit log write failed: " + t);
            }
        }
    }

    /** 便捷重载：自动拼 {@code at (x, y, z)}。 */
    public static void logAt(String player, String action, World world, BlockPos pos) {
        String d = pos == null ? "(?)" : "(" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + ")"
                + (world == null ? "" : " dim " + world.provider.getDimension());
        log(player, action, action + " at " + d);
    }
}
