package com.jiezhirbmkmod.dfc;


import com.jiezhirbmkmod.network.NetGuard;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import com.jiezhirbmkmod.item.ItemRbmkOpCard;
import com.jiezhirbmkmod.network.ModNetwork;
import com.jiezhirbmkmod.network.msg.MessageHackAppResult;
import com.jiezhirbmkmod.network.msg.MessageHackAppReview;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * v17.6 敏感开关审批（HackAppManager）。v18.2 泛化为两类申请：
 *
 * <ul>
 *   <li><b>kind = {@link #KIND_HACK}</b>：黑客攻击玩法开关
 *       {@link RbmkConfig#allowNormalPlayerHack}（v17.6 起的原功能）；</li>
 *   <li><b>kind = {@link #KIND_WATCH}</b>：阈值自动化防护链开关
 *       {@link RbmkConfig#allowThresholdWatch} —— 通过后终端 watch 命令
 *       可用（阈值监视 + 自动 SCRAM/报警/审计，见 {@link WatchChain}）。</li>
 * </ul>
 *
 * <p>两类申请的审批流程<b>完全一致</b>：申请人双击操作员工牌发起申请后，
 * 所有在线的服主/管理员（原版权限等级 2 或单人房主）的界面同时弹出审批
 * 弹窗 —— 开启申请一票否决、全票通过；关闭申请任意一名管理员同意即生效。</p>
 *
 * <p>受理人集合就是"在线的 {@link ItemRbmkOpCard#isTrueOp} 玩家"，三种
 * 世界形态自动归一（单人 / LAN 有玩家 / LAN 空场）。生命周期：60 秒超时
 * 失败；申请人下线取消；受理人全部下线失败；同一时间只受理一份申请。</p>
 */
public class HackAppManager {

    /** 申请类别：0 = 黑客攻击玩法开关（v17.6），1 = 阈值防护链开关（v18.2） */
    public static final int KIND_HACK = 0;
    public static final int KIND_WATCH = 1;

    /** 申请类型：true = 申请关闭（一名管理员同意即生效），false = 申请开启（全票通过） */
    private static boolean closeType;
    /** v18.2 申请类别（KIND_HACK / KIND_WATCH） */
    private static int kind = KIND_HACK;
    private static boolean active = false;
    private static String applicantName = "";
    private static UUID applicantId = null;
    private static int appId = 0;
    /** 本次需要投票的服主/管理员集合 */
    private static final Set<UUID> required = new HashSet<UUID>();
    /** 已投同意票的受理人 */
    private static final Set<UUID> agreed = new HashSet<UUID>();
    /** 超时（tick 计数），60 秒无人投完整票按失败处理 */
    private static final long TIMEOUT_TICKS = 1200L;
    private static long tickCounter = 0L;
    private static long deadline = 0L;

    // ---- 事件 ----

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        ++tickCounter;
        if (!active) {
            return;
        }
        // 申请人掉线：申请作废，不发回信
        MinecraftServer server = net.minecraftforge.fml.common.FMLCommonHandler
                .instance().getMinecraftServerInstance();
        if (server == null) {
            reset();
            return;
        }
        EntityPlayerMP applicant = findPlayer(server, applicantId);
        if (applicant == null) {
            notifyReviewers(server, TextFormatting.GRAY, msg("applicant_offline"));
            reset();
            return;
        }
        // 受理人掉线：移出必需集合；全下线=无人可批，失败
        java.util.Iterator<UUID> it = required.iterator();
        while (it.hasNext()) {
            if (findPlayer(server, it.next()) == null) {
                it.remove();
            }
        }
        if (required.isEmpty()) {
            finish(server, false, msg("reviewers_offline"));
            return;
        }
        if (tickCounter >= deadline) {
            finish(server, false, msg("timeout"));
        }
    }

    // ---- 发起申请（客户端「发送申请」按钮 → MessageHackAppSend）----

    /** v17.6 兼容入口：黑客玩法开关申请 */
    public static void handleSend(EntityPlayerMP sender, boolean close) {
        handleSend(sender, KIND_HACK, close);
    }

    /** v18.2 泛化入口：kind = KIND_HACK / KIND_WATCH */
    public static void handleSend(EntityPlayerMP sender, int requestKind, boolean close) {
        MinecraftServer server = sender.getServer();
        if (server == null) {
            return;
        }
        if (requestKind != KIND_HACK && requestKind != KIND_WATCH) {
            return;
        }
        String prefix = TextFormatting.AQUA + "[jiezhirbmkmod] " + TextFormatting.RESET;
        if (active) {
            sender.sendStatusMessage(new TextComponentString(TextFormatting.RED + "[jiezhirbmkmod] "
                    + msg("pending")), false);
            return;
        }
        boolean on = requestKind == KIND_HACK
                ? RbmkConfig.allowNormalPlayerHack : RbmkConfig.allowThresholdWatch;
        if (!close && on) {
            sender.sendStatusMessage(new TextComponentString(TextFormatting.YELLOW + "[jiezhirbmkmod] "
                    + msg("already_on")), false);
            return;
        }
        if (close && !on) {
            sender.sendStatusMessage(new TextComponentString(TextFormatting.YELLOW + "[jiezhirbmkmod] "
                    + msg("already_off")), false);
            return;
        }
        if (close && !ItemRbmkOpCard.isTrueOp(sender)) {
            sender.sendStatusMessage(new TextComponentString(TextFormatting.RED + "[jiezhirbmkmod] "
                    + msg("close_denied")), false);
            return;
        }
        // 受理人 = 所有在线服主/管理员。单人世界与 LAN 空场时集合就是玩家自己，
        // LAN 有其他人时是房主 + 全部管理员 —— 三种形态无需分别处理。
        required.clear();
        for (EntityPlayerMP p : server.getPlayerList().getPlayers()) {
            if (ItemRbmkOpCard.isTrueOp(p)) {
                required.add(p.getUniqueID());
            }
        }
        if (required.isEmpty()) {
            sender.sendStatusMessage(new TextComponentString(TextFormatting.RED + "[jiezhirbmkmod] "
                    + msg("no_reviewer")), false);
            return;
        }
        kind = requestKind;
        closeType = close;
        applicantName = sender.getName();
        applicantId = sender.getUniqueID();
        ++appId;
        agreed.clear();
        active = true;
        deadline = tickCounter + TIMEOUT_TICKS;
        // 审计 —— 申请受理即落盘（动作名带类别，事后可分）
        AuditLog.log(sender.getName(), kind == KIND_WATCH ? "WATCH_REQUEST" : "HACK_REQUEST",
                (close ? "close" : "open") + " -> reviewers=" + required.size() + " id=" + appId);
        for (EntityPlayerMP p : server.getPlayerList().getPlayers()) {
            if (required.contains(p.getUniqueID())) {
                NetGuard.sendTo(new MessageHackAppReview(
                        applicantName, closeType, appId, kind), p);
            }
        }
        sender.sendStatusMessage(new TextComponentString(TextFormatting.AQUA + "[jiezhirbmkmod] "
                + msg("submitted", Integer.valueOf(required.size()))
                + msg(close ? "submitted_close" : "submitted_open")), false);
    }

    // ---- 投票（审批弹窗 → MessageHackAppVote）----

    public static void handleVote(EntityPlayerMP voter, int id, boolean agree) {
        MinecraftServer server = voter.getServer();
        if (server == null) {
            return;
        }
        if (!active || id != appId) {
            voter.sendStatusMessage(new TextComponentString(TextFormatting.YELLOW + "[jiezhirbmkmod] "
                    + msg("stale")), false);
            return;
        }
        if (!ItemRbmkOpCard.isTrueOp(voter) || !required.contains(voter.getUniqueID())) {
            return; // 非受理人：静默丢弃（GUI 是包触发的，伪造包进不了名单）
        }
        if (!agree) {
            // 一票否决（开启/关闭申请都适用）：立即失败
            AuditLog.log(voter.getName(),
                    kind == KIND_WATCH ? "WATCH_VETO" : "HACK_VETO",
                    "id=" + id + " applicant=" + applicantName);
            finish(server, false, msg("veto", voter.getName()));
            return;
        }
        agreed.add(voter.getUniqueID());
        AuditLog.log(voter.getName(),
                kind == KIND_WATCH ? "WATCH_VOTE" : "HACK_VOTE",
                "agree id=" + id + (closeType ? " close" : " open"));
        if (closeType) {
            // 关闭申请：任意一名管理员/房主同意即生效
            finish(server, true, msg("closed_by", voter.getName()));
            return;
        }
        if (agreed.containsAll(required)) {
            // 开启申请：全票通过
            finish(server, true, msg("unanimous"));
        }
    }

    // ---- 结果 ----

    private static void finish(MinecraftServer server, boolean accepted, String why) {
        // 审计 —— 结果（含超时/否决/掉线失败）全部落盘
        AuditLog.log(null, kind == KIND_WATCH ? "WATCH_RESULT" : "HACK_RESULT",
                "applicant=" + applicantName + " type=" + (closeType ? "close" : "open")
                        + " accepted=" + accepted + (why == null ? "" : " | " + why));
        if (accepted) {
            if (kind == KIND_WATCH) {
                RbmkConfig.setAllowThresholdWatch(!closeType);
            } else {
                RbmkConfig.setAllowNormalPlayerHack(!closeType);
            }
            // 全服公告：开关变动是服务器级事件
            server.getPlayerList().sendMessage(new TextComponentString(
                TextFormatting.AQUA + "[jiezhirbmkmod] " + TextFormatting.GREEN
                        + msg(closeType ? "announce_off" : "announce_on", applicantName, why)));
        }
        EntityPlayerMP applicant = findPlayer(server, applicantId);
        if (applicant != null) {
            // 回信弹窗：点「知道了」关闭
            NetGuard.sendTo(new MessageHackAppResult(closeType, accepted, kind), applicant);
        }
        if (!accepted) {
            notifyReviewers(server, TextFormatting.YELLOW, why);
        }
        reset();
    }

    private static void notifyReviewers(MinecraftServer server, TextFormatting color, String text) {
        for (EntityPlayerMP p : server.getPlayerList().getPlayers()) {
            if (required.contains(p.getUniqueID())) {
                p.sendStatusMessage(new TextComponentString(color + "[jiezhirbmkmod] " + text), false);
            }
        }
    }

    /**
     * v18.2 按 kind 取语言键：黑客玩法用 {@code msg.jiezhirbmkmod.hackapp.*}，
     * 防护链用 {@code msg.jiezhirbmkmod.watchapp.*}（文案对应替换，
     * 占位符一致）。
     */
    private static String msg(String key, Object... args) {
        String full = "msg.jiezhirbmkmod." + (kind == KIND_WATCH ? "watchapp." : "hackapp.") + key;
        return net.minecraft.util.text.translation.I18n.translateToLocalFormatted(full, args);
    }

    private static void reset() {
        active = false;
        closeType = false;
        kind = KIND_HACK;
        applicantName = "";
        applicantId = null;
        required.clear();
        agreed.clear();
    }

    private static EntityPlayerMP findPlayer(MinecraftServer server, UUID id) {
        if (id == null) {
            return null;
        }
        return server.getPlayerList().getPlayerByUUID(id);
    }
}
