package com.jiezhirbmkmod.dfc;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;

/**
 * v17.9 兼容探针：{@code /rbmkcompat}（任何玩家可用，纯只读）。
 *
 * <p>出问题时的第一句话永远是"你装的什么版本"。本命令把排查一条链上
 * 要问的所有问题一次打全：本端版本与网络协议、运行环境、安全开关
 * 状态、审计/吊销文件、光影与渲染管线状态（客户端）、潜在同类冲突
 * mod、以及最近一次网络握手的对端版本与裁决 —— 新旧 jar 互连被 FML
 * 拒绝时，{@link ProtocolGuard} 已把精确原因落了盘，这里翻译成人话。</p>
 *
 * <p>只读命令：不写配置、不动世界，权限等级 0。所有输出走
 * {@code translateToLocalFormatted}（服务端语言），与
 * /rbmkrevoke、/rbmktrace 同款。</p>
 */
public class CompatCommand
extends CommandBase {

    /** 同类冲突关键词：命中即提示"可能是同类/相关 mod，注意功能重叠" */
    private static final String[] CONFLICT_KEYWORDS =
            {"screen", "monitor", "display", "rbmk", "dashboard", "hud"};
    /** 排除掉自身与平台自带的容器，不参与冲突扫描 */
    private static final String[] IGNORED_MODS =
            {"jiezhirbmkmod", "mcp", "fml", "forge", "minecraft", "mercurius_updater"};

    @SubscribeEvent
    public void onServerStarting(FMLServerStartingEvent event) {
        event.registerServerCommand(this);
    }

    @Override
    public String getName() {
        return "rbmkcompat";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/rbmkcompat";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args)
            throws CommandException {
        if (args.length != 0) {
            throw new WrongUsageException("/rbmkcompat");
        }
        String pfx = TextFormatting.AQUA + "[jiezhirbmkmod] ";
        // 1) 本端身份：版本 + 协议
        send(sender, pfx, TextFormatting.AQUA, "msg.jiezhirbmkmod.compat.header",
                com.jiezhirbmkmod.Tags.VERSION,
                Integer.valueOf(com.jiezhirbmkmod.network.ModNetwork.PROTOCOL));
        // 2) 运行环境
        boolean dedicated = net.minecraftforge.fml.common.FMLCommonHandler.instance()
                .getSide() == Side.SERVER;
        send(sender, pfx, TextFormatting.GRAY, "msg.jiezhirbmkmod.compat.env",
                dedicated ? "Dedicated" : "Integrated/Client",
                Loader.MC_VERSION,
                String.valueOf(net.minecraftforge.common.ForgeVersion.getVersion()),
                System.getProperty("java.version", "?"));
        // 3) 网络包表（v17.9：聚合归属表包收编后共 23 个，id 0..22）
        send(sender, pfx, TextFormatting.GRAY, "msg.jiezhirbmkmod.compat.packets",
                Integer.valueOf(23), Integer.valueOf(22));
        // 4) 安全开关快照
        send(sender, pfx, TextFormatting.GRAY, "msg.jiezhirbmkmod.compat.security",
                RbmkConfig.enableHackerAttack ? "ON" : "OFF",
                RbmkConfig.allowNormalPlayerHack ? "ON" : "OFF",
                Integer.valueOf(BadgeRevocation.globalEpoch()));
        // 5) 数据文件位置
        send(sender, pfx, TextFormatting.GRAY, "msg.jiezhirbmkmod.compat.files");
        // 6) 客户端渲染状态（专用服务器上无意义，注明）
        send(sender, pfx, TextFormatting.GRAY, "msg.jiezhirbmkmod.compat.client",
                RbmkConfig.shaderPackActive ? "ON" : "OFF",
                RbmkConfig.sssAvailable ? "OK" : "DOWN",
                dedicated ? "(dedicated: n/a)" : "(client state)");
        // 7) OptiFine / 潜在同类冲突
        send(sender, pfx, optifinePresent() ? TextFormatting.YELLOW : TextFormatting.GRAY,
                "msg.jiezhirbmkmod.compat.optifine", optifinePresent() ? "YES" : "NO");
        List<String> conflicts = conflictScan();
        if (conflicts.isEmpty()) {
            send(sender, pfx, TextFormatting.GRAY, "msg.jiezhirbmkmod.compat.mods_none");
        } else {
            send(sender, pfx, TextFormatting.YELLOW, "msg.jiezhirbmkmod.compat.mods",
                    String.join(", ", conflicts));
        }
        // 8) 最近一次握手对端版本
        String remote = ProtocolGuard.describe();
        if (remote == null) {
            send(sender, pfx, TextFormatting.GRAY, "msg.jiezhirbmkmod.compat.remote_none");
        } else {
            boolean bad = remote.contains("REJECTED") || remote.contains("no RBMK");
            send(sender, pfx, bad ? TextFormatting.YELLOW : TextFormatting.GRAY,
                    "msg.jiezhirbmkmod.compat.remote", remote);
            send(sender, pfx, TextFormatting.GRAY, "msg.jiezhirbmkmod.compat.remote_hint",
                    com.jiezhirbmkmod.Tags.VERSION);
        }
    }

    /** OptiFine 探测：优先读运行时全局（光影包开着肯定在），再试特征类 */
    private static boolean optifinePresent() {
        if (RbmkConfig.shaderPackActive) {
            return true;
        }
        try {
            Class.forName("optifine.OptiFineTweaker");
            return true;
        }
        catch (Throwable try2) {
            try {
                Class.forName("optifine.OptiFineForgeTweaker");
                return true;
            }
            catch (Throwable ignored) {
                return false;
            }
        }
    }

    /** 扫描已加载 mod：名字/id 命中关键词即视为潜在同类/相关 mod */
    private static List<String> conflictScan() {
        List<String> out = new ArrayList<String>();
        for (ModContainer mc : Loader.instance().getActiveModList()) {
            String id = mc.getModId() == null ? "" : mc.getModId().toLowerCase(java.util.Locale.ROOT);
            String name = mc.getName() == null ? "" : mc.getName().toLowerCase(java.util.Locale.ROOT);
            if (isIgnored(id)) {
                continue;
            }
            for (String kw : CompatCommand.CONFLICT_KEYWORDS) {
                if (id.contains(kw) || name.contains(kw)) {
                    out.add(mc.getModId() + "@" + mc.getVersion());
                    break;
                }
            }
        }
        return out;
    }

    private static boolean isIgnored(String id) {
        for (String ig : CompatCommand.IGNORED_MODS) {
            if (ig.equals(id)) {
                return true;
            }
        }
        return false;
    }

    private static void send(ICommandSender sender, String prefix, TextFormatting color,
            String key, Object... args) {
        sender.sendMessage(new TextComponentString(prefix
                + TextFormatting.RESET + color
                + net.minecraft.util.text.translation.I18n.translateToLocalFormatted(key, args)));
    }
}
