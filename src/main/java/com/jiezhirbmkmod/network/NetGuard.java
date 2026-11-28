package com.jiezhirbmkmod.network;

import com.jiezhirbmkmod.RbmkMonitorMod;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;

/**
 * v18.4 网络收发的统一安全出口（NetGuard）。
 *
 * <p>背景：{@code RbmkMonitorMod#onNetworkCheck} 从 v18.4 起改为
 * 「对端没装本模组就放行、装了但版本不符才拒绝」（见该方法的注释）。
 * 放行 vanilla 服 / 只装了别的模组的客户端之后，本模组会真的和
 * 「对方压根没有这个 channel」的对端建立连接，此时任何一次
 * {@code ModNetwork.CHANNEL.sendXxx(...)} 都可能抛出异常（channel 未注册、
 * 对端连接已断开、包体过大等）。异常一旦冒泡到 tick / 事件回调里，
 * 轻则功能静默失效，重则直接崩服崩客户端。</p>
 *
 * <p>所以把所有发包收敛到这一层：空值检查 + {@code try-catch Throwable}，
 * 失败只记一条 warn 日志（同一条消息只告警一次，避免刷屏），
 * <b>绝不把异常抛回调用方</b>。收包侧配合
 * {@link #reportHandler(String, Throwable)} 使用。</p>
 *
 * <p>这一层不改变任何线上格式，也不改变包的注册顺序 —— 只是把
 * {@code CHANNEL.sendXxx} 包了一层，可以随时对照旧代码排查。</p>
 */
public final class NetGuard {

    /** 已经告警过的 key（尽力而为，不需要线程安全，只用于去重刷屏） */
    private static final java.util.Set<String> WARNED = java.util.Collections.newSetFromMap(
            new java.util.concurrent.ConcurrentHashMap<String, Boolean>());

    private NetGuard() {
    }

    // ---------------------------------------------------------------- 发包

    /** 客户端 → 服务端。对端没有本模组时静默丢弃。 */
    public static void sendToServer(IMessage msg) {
        if (msg == null) {
            return;
        }
        try {
            ModNetwork.CHANNEL.sendToServer(msg);
        }
        catch (Throwable t) {
            report("sendToServer", msg, t);
        }
    }

    /** 服务端 → 指定玩家。player 为 null 时静默返回。 */
    public static void sendTo(IMessage msg, EntityPlayerMP player) {
        if (msg == null || player == null) {
            return;
        }
        try {
            ModNetwork.CHANNEL.sendTo(msg, player);
        }
        catch (Throwable t) {
            report("sendTo", msg, t);
        }
    }

    /** 服务端 → 所有在线玩家。 */
    public static void sendToAll(IMessage msg) {
        if (msg == null) {
            return;
        }
        try {
            ModNetwork.CHANNEL.sendToAll(msg);
        }
        catch (Throwable t) {
            report("sendToAll", msg, t);
        }
    }

    /** 服务端 → 指定维度。 */
    public static void sendToDimension(IMessage msg, int dimensionId) {
        if (msg == null) {
            return;
        }
        try {
            ModNetwork.CHANNEL.sendToDimension(msg, dimensionId);
        }
        catch (Throwable t) {
            report("sendToDimension", msg, t);
        }
    }

    /** 服务端 → 指定坐标点半径内的玩家。point 为 null 时静默返回。 */
    public static void sendToAllAround(IMessage msg, NetworkRegistry.TargetPoint point) {
        if (msg == null || point == null) {
            return;
        }
        try {
            ModNetwork.CHANNEL.sendToAllAround(msg, point);
        }
        catch (Throwable t) {
            report("sendToAllAround", msg, t);
        }
    }

    // ---------------------------------------------------------------- 收包

    /**
     * 收包侧的兜底日志：{@code IMessageHandler#onMessage} 里 catch 到
     * 任何 Throwable 后调用。不抛异常，不改变包的返回语义（调用方仍
     * 返回 null，即"不回复"）。
     *
     * @param where 出错的处理器标识，例如 {@code "MessageRbmkPda.Handler"}
     * @param t     捕获到的异常
     */
    public static void reportHandler(String where, Throwable t) {
        String key = "H:" + where;
        String text = "[jiezhirbmkmod] packet handler " + where
                + " failed, ignored to keep the connection alive: " + t;
        if (WARNED.add(key)) {
            log(text, t);
        }
        else {
            log(text, null);
        }
    }

    // ---------------------------------------------------------------- 内部

    private static void report(String where, IMessage msg, Throwable t) {
        String type = msg == null ? "?" : msg.getClass().getSimpleName();
        String text = "[jiezhirbmkmod] packet " + where + " failed (" + type
                + "), dropped so the caller keeps running: " + t;
        if (WARNED.add(where + ":" + type)) {
            log(text, t);
        }
        else {
            log(text, null);
        }
    }

    private static void log(String text, Throwable t) {
        try {
            if (RbmkMonitorMod.logger != null) {
                if (t != null) {
                    RbmkMonitorMod.logger.warn(text, t);
                }
                else {
                    RbmkMonitorMod.logger.warn(text);
                }
            }
            else {
                System.out.println(text);
            }
        }
        catch (Throwable ignored) {
            // 日志本身不可用时也不许影响网络流程
        }
    }
}
