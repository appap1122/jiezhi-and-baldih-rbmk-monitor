package com.jiezhirbmkmod.dfc;

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
 * v17.6 黑客攻击玩法开关审批（HackAppManager）。
 *
 * <p>开关 {@link RbmkConfig#allowNormalPlayerHack} 默认关。开启需要<b>全票
 * 通过</b>：申请人双击操作员工牌发起申请后，所有在线的服主/管理员（原版
 * 权限等级 2 或单人房主，不分正版/离线）的界面同时弹出「玩家xxx试图开启
 * 黑客攻击玩法」与同意/不同意两个按钮 —— 任何一人不同意即失败，全票同意
 * 才写回开关并落盘。关闭则相反：<b>任意一名</b>管理员/房主同意即生效。</p>
 *
 * <p>受理人集合就是"在线的 {@link ItemRbmkOpCard#isTrueOp} 玩家"，三种
 * 世界形态自动归一：</p>
 * <ul>
 *   <li>单人世界（未开 LAN）：集合 = 房主本人 = 申请人，弹窗发给自己；</li>
 *   <li>LAN 且有其他玩家：看做服务器，集合 = 房主 + 所有被 OP 的管理员；</li>
 *   <li>LAN 但只有世界创建者一人：看做单人，弹窗发回创建者自己。</li>
 * </ul>
 *
 * <p>生命周期：60 秒无人投完整票按超时失败；申请人下线取消（不发回信）；
 * 受理人全部下线按失败处理。同一时间只受理一份申请。</p>
 */
public class HackAppManager {

    /** 申请类型：true = 申请关闭（一名管理员同意即生效），false = 申请开启（全票通过） */
    private static boolean closeType;
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
            notifyReviewers(server, TextFormatting.GRAY,
                    net.minecraft.util.text.translation.I18n.translateToLocalFormatted(
                            "msg.jiezhirbmkmod.hackapp.applicant_offline", applicantName));
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
            finish(server, false, net.minecraft.util.text.translation.I18n.translateToLocalFormatted(
                        "msg.jiezhirbmkmod.hackapp.reviewers_offline"));
            return;
        }
        if (tickCounter >= deadline) {
            finish(server, false, net.minecraft.util.text.translation.I18n.translateToLocalFormatted(
                        "msg.jiezhirbmkmod.hackapp.timeout"));
        }
    }

    // ---- 发起申请（客户端「发送申请」按钮 → MessageHackAppSend）----

    public static void handleSend(EntityPlayerMP sender, boolean close) {
        MinecraftServer server = sender.getServer();
        if (server == null) {
            return;
        }
        String prefix = TextFormatting.AQUA + "[jiezhirbmkmod] " + TextFormatting.RESET;
        if (active) {
            sender.sendStatusMessage(new TextComponentString(TextFormatting.RED + "[jiezhirbmkmod] "
                    + net.minecraft.util.text.translation.I18n.translateToLocalFormatted(
                            "msg.jiezhirbmkmod.hackapp.pending")), false);
            return;
        }
        if (!close && RbmkConfig.allowNormalPlayerHack) {
            sender.sendStatusMessage(new TextComponentString(TextFormatting.YELLOW + "[jiezhirbmkmod] "
                    + net.minecraft.util.text.translation.I18n.translateToLocalFormatted(
                            "msg.jiezhirbmkmod.hackapp.already_on")), false);
            return;
        }
        if (close && !RbmkConfig.allowNormalPlayerHack) {
            sender.sendStatusMessage(new TextComponentString(TextFormatting.YELLOW + "[jiezhirbmkmod] "
                    + net.minecraft.util.text.translation.I18n.translateToLocalFormatted(
                            "msg.jiezhirbmkmod.hackapp.already_off")), false);
            return;
        }
        if (close && !ItemRbmkOpCard.isTrueOp(sender)) {
            sender.sendStatusMessage(new TextComponentString(TextFormatting.RED + "[jiezhirbmkmod] "
                    + net.minecraft.util.text.translation.I18n.translateToLocalFormatted(
                            "msg.jiezhirbmkmod.hackapp.close_denied")), false);
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
                    + net.minecraft.util.text.translation.I18n.translateToLocalFormatted(
                            "msg.jiezhirbmkmod.hackapp.no_reviewer")), false);
            return;
        }
        closeType = close;
        applicantName = sender.getName();
        applicantId = sender.getUniqueID();
        ++appId;
        agreed.clear();
        active = true;
        deadline = tickCounter + TIMEOUT_TICKS;
        // v17.7: 审计 —— 申请受理即落盘
        AuditLog.log(sender.getName(), "HACK_REQUEST",
                (close ? "close" : "open") + " -> reviewers=" + required.size() + " id=" + appId);
        for (EntityPlayerMP p : server.getPlayerList().getPlayers()) {
            if (required.contains(p.getUniqueID())) {
                ModNetwork.CHANNEL.sendTo(new MessageHackAppReview(
                        applicantName, closeType, appId), p);
            }
        }
        sender.sendStatusMessage(new TextComponentString(TextFormatting.AQUA + "[jiezhirbmkmod] "
                + net.minecraft.util.text.translation.I18n.translateToLocalFormatted(
                        "msg.jiezhirbmkmod.hackapp.submitted", Integer.valueOf(required.size()))
                + net.minecraft.util.text.translation.I18n.translateToLocalFormatted(close
                        ? "msg.jiezhirbmkmod.hackapp.submitted_close"
                        : "msg.jiezhirbmkmod.hackapp.submitted_open")), false);
    }

    // ---- 投票（审批弹窗 → MessageHackAppVote）----

    public static void handleVote(EntityPlayerMP voter, int id, boolean agree) {
        MinecraftServer server = voter.getServer();
        if (server == null) {
            return;
        }
        if (!active || id != appId) {
            voter.sendStatusMessage(new TextComponentString(TextFormatting.YELLOW + "[jiezhirbmkmod] "
                    + net.minecraft.util.text.translation.I18n.translateToLocalFormatted(
                            "msg.jiezhirbmkmod.hackapp.stale")), false);
            return;
        }
        if (!ItemRbmkOpCard.isTrueOp(voter) || !required.contains(voter.getUniqueID())) {
            return; // 非受理人：静默丢弃（GUI 是包触发的，伪造包进不了名单）
        }
        if (!agree) {
            // 一票否决（开启/关闭申请都适用）：立即失败
            AuditLog.log(voter.getName(), "HACK_VETO",
                    "id=" + id + " applicant=" + applicantName); // v17.7 审计
            finish(server, false, net.minecraft.util.text.translation.I18n.translateToLocalFormatted(
                            "msg.jiezhirbmkmod.hackapp.veto", voter.getName()));
            return;
        }
        agreed.add(voter.getUniqueID());
        AuditLog.log(voter.getName(), "HACK_VOTE",
                "agree id=" + id + (closeType ? " close" : " open")); // v17.7 审计
        if (closeType) {
            // 关闭申请：任意一名管理员/房主同意即生效
            finish(server, true, net.minecraft.util.text.translation.I18n.translateToLocalFormatted(
                                "msg.jiezhirbmkmod.hackapp.closed_by", voter.getName()));
            return;
        }
        if (agreed.containsAll(required)) {
            // 开启申请：全票通过
            finish(server, true, net.minecraft.util.text.translation.I18n.translateToLocalFormatted(
                            "msg.jiezhirbmkmod.hackapp.unanimous"));
        }
    }

    // ---- 结果 ----

    private static void finish(MinecraftServer server, boolean accepted, String why) {
        // v17.7: 审计 —— 结果（含超时/否决/掉线失败）全部落盘
        AuditLog.log(null, "HACK_RESULT",
                "applicant=" + applicantName + " type=" + (closeType ? "close" : "open")
                        + " accepted=" + accepted + (why == null ? "" : " | " + why));
        if (accepted) {
            RbmkConfig.setAllowNormalPlayerHack(!closeType);
            // 全服公告：开关变动是服务器级事件
            server.getPlayerList().sendMessage(new TextComponentString(
                TextFormatting.AQUA + "[jiezhirbmkmod] " + TextFormatting.GREEN
                        + net.minecraft.util.text.translation.I18n.translateToLocalFormatted(
                                closeType ? "msg.jiezhirbmkmod.hackapp.announce_off"
                                          : "msg.jiezhirbmkmod.hackapp.announce_on",
                                applicantName, why)));
        }
        EntityPlayerMP applicant = findPlayer(server, applicantId);
        if (applicant != null) {
            // 回信弹窗：点「知道了」关闭
            ModNetwork.CHANNEL.sendTo(new MessageHackAppResult(closeType, accepted), applicant);
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

    private static void reset() {
        active = false;
        closeType = false;
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
