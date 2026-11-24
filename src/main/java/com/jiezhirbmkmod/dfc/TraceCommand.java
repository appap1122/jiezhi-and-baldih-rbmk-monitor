package com.jiezhirbmkmod.dfc;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import com.jiezhirbmkmod.block.TileRbmkMonitorScreen;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * v17.9 黑客玩法二期 · 反向追踪：{@code /rbmktrace [玩家]}（权限等级 2）。
 *
 * <p>一期只让屏"红闪报假数"，管理员面对一块被黑的屏只能靠
 * {@code rbmk_audit.log} 事后翻流水。二期让屏幕自己变成证据：每次
 * 黑入都把入侵者名字与时间盖进屏幕的 NBT（{@code Hacker/HackTime/
 * HackFixed}，修复也不抹），本命令把痕迹读出来：</p>
 * <ul>
 *   <li><b>无参数</b>：扫描身边 64 格（已加载区块）内有入侵痕迹的屏，
 *       列出坐标、当前状态（被黑中/已修复）、入侵者与多久前的事；</li>
 *   <li><b>带玩家名</b>：在本维度全部已加载区块里搜该玩家留下痕迹的
 *       屏 —— 定位"他到底黑过哪几块屏"。</li>
 * </ul>
 *
 * <p>只读命令：不改任何状态；查询本身写一条 TRACE 审计痕（谁在何时
 * 查了谁）。与 {@link RevokeCommand} 同款注册方式。</p>
 */
public class TraceCommand
extends CommandBase {

    /** 无参数模式：以命令发出者为中心的扫描半径 */
    public static final int TRACE_RADIUS = 64;

    @SubscribeEvent
    public void onServerStarting(FMLServerStartingEvent event) {
        event.registerServerCommand(this);
    }

    @Override
    public String getName() {
        return "rbmktrace";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/rbmktrace [玩家]";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args)
            throws CommandException {
        if (args.length > 1) {
            throw new WrongUsageException("/rbmktrace [玩家]");
        }
        if (!(sender.getCommandSenderEntity() instanceof EntityPlayerMP)) {
            // 控制台/命令方块：无"身边"可言，直接按玩家名搜或拒绝
            if (args.length == 0) {
                throw new WrongUsageException("/rbmktrace <玩家>");
            }
            traceByName(server, sender, args[0]);
            return;
        }
        EntityPlayerMP player = (EntityPlayerMP) sender.getCommandSenderEntity();
        if (args.length == 0) {
            traceAround(player, sender);
        } else {
            traceByName(server, sender, args[0]);
        }
    }

    /** 模式一：扫描身边半径内全部带痕迹的屏 */
    private void traceAround(EntityPlayerMP player, ICommandSender sender) {
        World world = player.world;
        BlockPos center = player.getPosition();
        List<TileRbmkMonitorScreen> found =
                TileRbmkMonitorScreen.collectScreens(world, center, TraceCommand.TRACE_RADIUS);
        List<TileRbmkMonitorScreen> traces = new ArrayList<TileRbmkMonitorScreen>();
        for (TileRbmkMonitorScreen s : found) {
            if (s.hasHackerTrace()) {
                traces.add(s);
            }
        }
        traces.sort((a, b) -> Long.compare(b.getHackTimeTicks(), a.getHackTimeTicks()));
        send(sender, TextFormatting.AQUA, "msg.jiezhirbmkmod.trace.header",
                Integer.valueOf(TraceCommand.TRACE_RADIUS),
                Integer.valueOf(world.provider.getDimension()));
        if (traces.isEmpty()) {
            send(sender, TextFormatting.GRAY, "msg.jiezhirbmkmod.trace.none",
                    Integer.valueOf(TraceCommand.TRACE_RADIUS));
        }
        for (TileRbmkMonitorScreen s : traces) {
            send(sender, s.isHacked ? TextFormatting.RED : TextFormatting.YELLOW,
                    s.isHacked ? "msg.jiezhirbmkmod.trace.line_living"
                               : "msg.jiezhirbmkmod.trace.line_fixed",
                    Integer.valueOf(s.getPos().getX()), Integer.valueOf(s.getPos().getY()),
                    Integer.valueOf(s.getPos().getZ()), s.getHackerName(),
                    TraceCommand.duration(world.getTotalWorldTime() - s.getHackTimeTicks()));
        }
        send(sender, TextFormatting.AQUA, "msg.jiezhirbmkmod.trace.count",
                Integer.valueOf(traces.size()));
        AuditLog.log(sender.getName(), "TRACE",
                "scan radius=" + TraceCommand.TRACE_RADIUS
                        + " @ " + center.getX() + "," + center.getY() + "," + center.getZ()
                        + " found=" + traces.size());
    }

    /** 模式二：按入侵者名字在本维度已加载区块里定位他黑过的屏 */
    private void traceByName(MinecraftServer server, ICommandSender sender, String name) {
        // 在线玩家用真实大小写归一，离线玩家按输入原样查
        EntityPlayerMP online = server.getPlayerList().getPlayerByUsername(name);
        String target = online != null ? online.getName() : name;
        World world = sender.getEntityWorld() != null
                ? sender.getEntityWorld() : server.getEntityWorld();
        List<TileRbmkMonitorScreen> traces = new ArrayList<TileRbmkMonitorScreen>();
        for (TileEntity te : world.loadedTileEntityList) {
            if (te instanceof TileRbmkMonitorScreen
                    && ((TileRbmkMonitorScreen) te).hasHackerTrace()
                    && ((TileRbmkMonitorScreen) te).getHackerName().equalsIgnoreCase(target)) {
                traces.add((TileRbmkMonitorScreen) te);
            }
        }
        traces.sort((a, b) -> Long.compare(b.getHackTimeTicks(), a.getHackTimeTicks()));
        send(sender, TextFormatting.AQUA, "msg.jiezhirbmkmod.trace.player_header",
                target, Integer.valueOf(world.provider.getDimension()));
        if (traces.isEmpty()) {
            send(sender, TextFormatting.GRAY, "msg.jiezhirbmkmod.trace.player_none", target);
        }
        for (TileRbmkMonitorScreen s : traces) {
            send(sender, s.isHacked ? TextFormatting.RED : TextFormatting.YELLOW,
                    s.isHacked ? "msg.jiezhirbmkmod.trace.line_living"
                               : "msg.jiezhirbmkmod.trace.line_fixed",
                    Integer.valueOf(s.getPos().getX()), Integer.valueOf(s.getPos().getY()),
                    Integer.valueOf(s.getPos().getZ()), s.getHackerName(),
                    TraceCommand.duration(world.getTotalWorldTime() - s.getHackTimeTicks()));
        }
        send(sender, TextFormatting.AQUA, "msg.jiezhirbmkmod.trace.count",
                Integer.valueOf(traces.size()));
        AuditLog.log(sender.getName(), "TRACE", "scan player=" + target
                + " dim=" + world.provider.getDimension() + " found=" + traces.size());
    }

    /** tick 差转人话：<1分 显示秒，<1时 显示分，其余显示时 */
    private static String duration(long ticks) {
        if (ticks < 0L) {
            ticks = 0L;
        }
        long sec = ticks / 20L;
        if (sec < 60L) {
            return sec + "s";
        }
        long min = sec / 60L;
        if (min < 60L) {
            return min + "m" + (sec % 60L) + "s";
        }
        return (min / 60L) + "h" + (min % 60L) + "m";
    }

    private static void send(ICommandSender sender, TextFormatting color, String key, Object... args) {
        sender.sendMessage(new TextComponentString(color + "[jiezhirbmkmod] "
                + net.minecraft.util.text.translation.I18n.translateToLocalFormatted(key, args)));
    }

    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender,
            String[] args, @Nullable BlockPos pos) {
        if (args.length == 1) {
            return CommandBase.getListOfStringsMatchingLastWord(args,
                    server.getPlayerList().getOnlinePlayerNames());
        }
        return java.util.Collections.<String>emptyList();
    }
}
