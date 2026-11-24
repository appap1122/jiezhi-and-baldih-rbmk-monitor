package com.jiezhirbmkmod.dfc;

import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * v17.7 审计查询命令：{@code /rbmkaudit [n]}。
 *
 * <p>权限等级 2（服主/管理员/单人房主）专用 —— 审计流水里有发卡记录
 * 与黑客开关变动，普通玩家不该翻。默认打印最近 20 条，最多 200 条
 * （内存尾迹的容量上限，更早的请去存档目录翻 {@code rbmk_audit.log}）。</p>
 *
 * <p>{@link #onServerStarting} 挂在 Forge 事件总线上，由主类在 init 时
 * 注册 —— 与 HackAppManager 同款注册方式，不需要额外的启动钩子。</p>
 */
public class AuditCommand
extends CommandBase {

    @SubscribeEvent
    public void onServerStarting(FMLServerStartingEvent event) {
        event.registerServerCommand(this);
    }

    @Override
    public String getName() {
        return "rbmkaudit";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/rbmkaudit [关键词...] [条数]";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    /**
     * v17.7 增强：grep 式过滤。参数里的纯数字是条数（其余为关键词，
     * 最多 3 个，行内容须<b>同时</b>包含全部关键词）：
     * <ul>
     *   <li>{@code /rbmkaudit} —— 最近 20 条；</li>
     *   <li>{@code /rbmkaudit 50} —— 最近 50 条；</li>
     *   <li>{@code /rbmkaudit Steve} —— Steve 相关（玩家名或动作关键词都行）；</li>
     *   <li>{@code /rbmkaudit Steve REVOKE} —— Steve 的吊销记录；</li>
     *   <li>{@code /rbmkaudit BADGE_REVOKE Steve 10} —— 组合过滤，最多 10 条。</li>
     * </ul>
     * 关键词对整行做子串匹配（时间戳、玩家名、动作、明细全部参与匹配），
     * 不区分大小写 —— 与文本工具 grep 同一心智模型。
     */
    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args)
            throws CommandException {
        int n = 20;
        java.util.List<String> keywords = new java.util.ArrayList<String>();
        for (int i = 0; i < args.length; ++i) {
            if (args[i].matches("\\d+")) {
                n = CommandBase.parseInt(args[i], 1, 200);
            } else if (keywords.size() < 3) {
                keywords.add(args[i].toLowerCase(java.util.Locale.ROOT));
            }
        }
        // 过滤在内存尾迹全量上做（上限 200 条），命中后取最后 n 条展示
        String[] pool = AuditLog.tail(200);
        java.util.List<String> hits = new java.util.ArrayList<String>();
        for (int i = 0; i < pool.length; ++i) {
            String line = pool[i].toLowerCase(java.util.Locale.ROOT);
            boolean match = true;
            for (int k = 0; k < keywords.size(); ++k) {
                if (!line.contains(keywords.get(k))) {
                    match = false;
                    break;
                }
            }
            if (match) {
                hits.add(pool[i]);
            }
        }
        int from = Math.max(0, hits.size() - n);
        String[] lines = hits.subList(from, hits.size()).toArray(new String[0]);
        if (lines.length == 0) {
            sender.sendMessage(new TextComponentString(TextFormatting.YELLOW
                    + "[jiezhirbmkmod] " + (keywords.isEmpty()
                            ? "审计流水为空（暂无工牌/签发/黑客操作记录）"
                            : "没有匹配 " + keywords + " 的审计记录（全量见存档目录 rbmk_audit.log）")));
            return;
        }
        sender.sendMessage(new TextComponentString(TextFormatting.AQUA
                + "[jiezhirbmkmod] 审计流水（"
                + (keywords.isEmpty() ? "最近 " : "匹配命中 ")
                + lines.length + " 条，全量在存档目录 rbmk_audit.log）："));
        for (int i = 0; i < lines.length; ++i) {
            sender.sendMessage(new TextComponentString(TextFormatting.GRAY + lines[i]));
        }
    }

    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender,
            String[] args, @Nullable BlockPos pos) {
        if (args.length <= 1) {
            java.util.List<String> out = new java.util.ArrayList<String>();
            out.add("20");
            out.add("50");
            out.add("200");
            out.addAll(java.util.Arrays.asList(server.getPlayerList().getOnlinePlayerNames()));
            return CommandBase.getListOfStringsMatchingLastWord(args, out);
        }
        return java.util.Collections.<String>emptyList();
    }
}
