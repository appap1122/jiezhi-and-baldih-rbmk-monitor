package com.jiezhirbmkmod.dfc;

/**
 * v17.9 网络握手记录中枢（ProtocolGuard）。
 *
 * <p>新旧 jar 互连被 FML 拒绝时，玩家屏幕上只有一句笼统的
 * "mod rejections"——谁拒的、为什么拒、拒的是哪个版本，全在 FML 日志
 * 深处。本类与 {@code RbmkMonitorMod} 上的
 * {@code @NetworkCheckHandler} 配合：握手时把对端的模组版本记下来，
 * 拒绝时把精确原因（对端版本 vs 本端版本）写进控制台日志与审计文件，
 * {@code /rbmkcompat} 再把它翻译成人话。</p>
 *
 * <p>重要约束：记录<b>绝不放宽也不收紧</b>校验语义 —— 接受/拒绝的判定
 * 在 {@code RbmkMonitorMod} 的握手回调里做（v18.0 起双侧对称：无论本端
 * 是哪一侧，都要求对端存在本模组且版本与本端一致，即 Forge 对未提供
 * {@code @NetworkCheckHandler} 的模组的默认语义），本类只做旁观记录。
 * 记录是"最近一次握手"的快照：多人同时连入时以后到的为准，时间戳
 * 供 {@code /rbmkcompat} 判断新鲜度。</p>
 */
public final class ProtocolGuard {

    private static volatile String remoteVersion = null;
    /** 对端是不是服务器（我们是客户端在连服） */
    private static volatile boolean remoteIsServer = false;
    /** 对端握手包里有没有本模组 */
    private static volatile boolean remotePresent = false;
    /** 按默认语义这次握手是否放行 */
    private static volatile boolean remoteAccepted = false;
    /** 最近一次握手的时间戳（System.currentTimeMillis），0 = 从未握手 */
    private static volatile long stamp = 0L;

    private ProtocolGuard() {
    }

    /**
     * 握手回调入口（由 {@code RbmkMonitorMod} 的
     * {@code @NetworkCheckHandler} 调用）。version 为对端模组版本，
     * 可为 null（对端没装本模组）。
     */
    public static void record(String version, boolean remoteIsServerSide, boolean accepted) {
        ProtocolGuard.remoteVersion = version;
        ProtocolGuard.remoteIsServer = remoteIsServerSide;
        ProtocolGuard.remotePresent = version != null;
        ProtocolGuard.remoteAccepted = accepted;
        ProtocolGuard.stamp = System.currentTimeMillis();
    }

    /**
     * 拒绝时的可读落盘：FML 的拒绝屏玩家看不懂，这里把精确原因写进
     * 控制台与审计链。只在服务端拒绝客户端时触发（客户端侧按默认
     * 语义不拒绝服务器，走不到这里）。
     */
    public static void logReject(String version, boolean remoteIsServerSide) {
        String msg = "Connection rejected by RBMK-Monitor compat check: remote "
                + (remoteIsServerSide ? "server" : "client") + " has "
                + com.jiezhirbmkmod.RbmkMonitorMod.MODID
                + (version == null ? " MISSING" : " v" + version)
                + ", local v" + com.jiezhirbmkmod.RbmkMonitorMod.VERSION
                + " — 让对方安装/更新到 v" + com.jiezhirbmkmod.RbmkMonitorMod.VERSION
                + " 后再连。";
        System.out.println("[jiezhirbmkmod] " + msg);
        try {
            AuditLog.log(null, "NET_REJECT",
                    (version == null ? "mod missing" : "remote v" + version)
                            + " local v" + com.jiezhirbmkmod.RbmkMonitorMod.VERSION);
        }
        catch (Throwable ignored) {
            // 审计不可用不影响握手流程
        }
        if (com.jiezhirbmkmod.RbmkMonitorMod.logger != null) {
            com.jiezhirbmkmod.RbmkMonitorMod.logger.warn(msg);
        }
    }

    /** 最近一次握手距现在的毫秒数；从未握手返回 -1 */
    public static long ageMs() {
        return ProtocolGuard.stamp == 0L ? -1L : System.currentTimeMillis() - ProtocolGuard.stamp;
    }

    /** /rbmkcompat 的一行人话摘要；无记录返回 null */
    public static String describe() {
        if (ProtocolGuard.stamp == 0L) {
            return null;
        }
        String who = ProtocolGuard.remoteIsServer ? "server" : "client";
        if (!ProtocolGuard.remotePresent) {
            return who + ": no RBMK-Monitor on remote";
        }
        String v = ProtocolGuard.remoteVersion;
        return who + ": v" + (v == null ? "?" : v)
                + (ProtocolGuard.remoteAccepted ? " (accepted)"
                : " (REJECTED — update remote to v"
                        + com.jiezhirbmkmod.RbmkMonitorMod.VERSION + ")");
    }
}
