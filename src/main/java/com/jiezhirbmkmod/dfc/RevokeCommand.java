package com.jiezhirbmkmod.dfc;

import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * v17.7 工牌吊销命令：{@code /rbmkrevoke <玩家|all>}。
 *
 * <p>权限等级 2（服主/管理员/单人房主）。语义是"吊销"而不是"收卡"：
 * 卡是实体，散落在谁的背包里管理员管不着 —— 吊销让卡<b>当场失效</b>，
 * 管理员再按需重签。按人吊销只灭发给该名字的卡；全服吊销
 * （{@code all}）连旧版无持有人记录的卡一并灭掉。</p>
 *
 * <p>与 {@link AuditCommand} 同款注册方式（Forge 事件总线订阅
 * FMLServerStartingEvent），数据层在 {@link BadgeRevocation}。</p>
 */
public class RevokeCommand
extends CommandBase {

    @SubscribeEvent
    public void onServerStarting(FMLServerStartingEvent event) {
        event.registerServerCommand(this);
    }

    @Override
    public String getName() {
        return "rbmkrevoke";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/rbmkrevoke <玩家|all>";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args)
            throws CommandException {
        if (args.length != 1 || args[0].isEmpty()) {
            throw new WrongUsageException("/rbmkrevoke <玩家|all>");
        }
        String arg = args[0];
        boolean all = "all".equalsIgnoreCase(arg) || "*".equals(arg);
        int epoch;
        String target;
        if (all) {
            epoch = BadgeRevocation.revokeAll();
            target = "all";
        } else {
            // 在线玩家用真实大小写归一，离线玩家按输入原样记录
            EntityPlayerMP online = server.getPlayerList().getPlayerByUsername(arg);
            target = online != null ? online.getName() : arg;
            epoch = BadgeRevocation.revokePlayer(target);
        }
        com.jiezhirbmkmod.dfc.AuditLog.log(sender.getName(), "BADGE_REVOKE",
                "target=" + target + " epoch=" + epoch);
        sender.sendMessage(new TextComponentString(TextFormatting.GOLD
                + "[jiezhirbmkmod] "
                + net.minecraft.util.text.translation.I18n.translateToLocalFormatted(
                        all ? "msg.jiezhirbmkmod.revoke.all" : "msg.jiezhirbmkmod.revoke.done",
                        target, Integer.valueOf(epoch))));
        // 目标在线就当面通知：权限没了要让人知道，而不是让他下次点按钮时才发现
        if (!all) {
            EntityPlayerMP online = server.getPlayerList().getPlayerByUsername(target);
            if (online != null) {
                online.sendStatusMessage(new TextComponentString(TextFormatting.RED
                        + "[jiezhirbmkmod] "
                        + net.minecraft.util.text.translation.I18n.translateToLocalFormatted(
                                "msg.jiezhirbmkmod.revoke.notify")), false);
            }
        }
    }

    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender,
            String[] args, @Nullable BlockPos pos) {
        if (args.length == 1) {
            java.util.List<String> out = new java.util.ArrayList<String>();
            out.add("all");
            out.addAll(java.util.Arrays.asList(server.getPlayerList().getOnlinePlayerNames()));
            return CommandBase.getListOfStringsMatchingLastWord(args, out);
        }
        return java.util.Collections.<String>emptyList();
    }
}
