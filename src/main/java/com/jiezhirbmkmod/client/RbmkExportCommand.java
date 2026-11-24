package com.jiezhirbmkmod.client;

import java.util.List;

import javax.annotation.Nullable;

import com.jiezhirbmkmod.block.TileRbmkMonitorScreen;
import com.jiezhirbmkmod.dfc.GraphHistory;

import net.minecraft.client.Minecraft;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;

/**
 * v17.9 曲线历史导出（仅客户端）：{@code /rbmkexport csv|png}。
 *
 * <p>对准一块屏（注视点射线命中）执行，把它的客户端自采历史
 * （{@link GraphHistory}，与曲线屏同一份数据）导出做事故复盘。
 * 文件格式与「蹲下连点两下屏 → 导出设置面板」完全一致 —— 两条入口
 * 共用 {@link RbmkExporter}，目录 {@code <游戏目录>/rbmk_export/} 由
 * 游戏运行时自动创建，文件名带屏坐标与时间戳永不覆盖。</p>
 *
 * <p>纯客户端：服务端看不到这次导出（审计不含），数据也只是客户端
 * 已经收到的那份。回放滚轮只是查看方式，导出的永远是完整采样窗。
 * 注册在 {@code ClientCommandHandler}（客户端聊天命令），本类只在
 * 客户端类加载，专用服务器永远摸不到。</p>
 */
public class RbmkExportCommand
extends CommandBase {

    /** 注视射线最大距离（格） */
    private static final double REACH = 32.0D;

    @Override
    public String getName() {
        return "rbmkexport";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/rbmkexport <csv|png> [秒]";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    @Override
    public void execute(net.minecraft.server.MinecraftServer server, ICommandSender sender,
            String[] args) throws CommandException {
        // 客户端聊天命令（ClientCommandHandler 注册）：连专用服务器时 server
        // 参数可能为 null —— 本方法刻意不碰它。sender 是本地玩家。
        if (!(sender.getCommandSenderEntity() instanceof EntityPlayer)) {
            throw new CommandException("rbmkexport.client_only");
        }
        if (args.length < 1 || args.length > 2
                || (!"csv".equalsIgnoreCase(args[0]) && !"png".equalsIgnoreCase(args[0]))) {
            throw new CommandException(getUsage(sender));
        }
        // v18.0 时间段：可选第二个参数 = 只导最近 N 秒（1..3600），缺省全量
        int seconds = 0;
        if (args.length == 2) {
            seconds = RbmkExportCommand.parseSeconds(args[1]);
        }
        EntityPlayer player = (EntityPlayer) sender.getCommandSenderEntity();
        TileRbmkMonitorScreen screen = RbmkExportCommand.aimedScreen(player);
        if (screen == null) {
            player.sendMessage(new TextComponentString(TextFormatting.RED + "[jiezhirbmkmod] "
                    + "导出目标：请对准一块显示屏（" + (int) RbmkExportCommand.REACH + " 格内）再执行。"));
            return;
        }
        GraphHistory h = screen.canvas().history();
        if (h == null || !h.hasData()) {
            player.sendMessage(new TextComponentString(TextFormatting.RED + "[jiezhirbmkmod] "
                    + "这块屏还没有采样数据（曲线屏需要通电运行一段时间）。"));
            return;
        }
        try {
            java.io.File dir = RbmkExporter.dir();
            if (dir == null) {
                player.sendMessage(new TextComponentString(TextFormatting.RED + "[jiezhirbmkmod] "
                        + "无法创建导出目录 " + new java.io.File(Minecraft.getMinecraft().gameDir,
                                RbmkExporter.DIR_NAME).getAbsolutePath()));
                return;
            }
            java.io.File out = RbmkExporter.export(screen.getPos(), h, "png".equalsIgnoreCase(args[0]), seconds);
            // v18.0 自查修复：时间段导出时实际行数是窗口内的 span，
            // 提示里报 h.size()（全窗）会跟文件内容对不上
            int span = h.size() - RbmkExporter.startIndexOf(h, seconds);
            player.sendMessage(new TextComponentString(TextFormatting.GREEN + "[jiezhirbmkmod] "
                    + "已导出 " + span + " 个采样点 → " + out.getAbsolutePath()));
        }
        catch (Throwable t) {
            player.sendMessage(new TextComponentString(TextFormatting.RED + "[jiezhirbmkmod] "
                    + "导出失败：" + t));
        }
    }

    /** 时间段参数解析：非法/越界抛 CommandException（客户端聊天栏直接显示） */
    private static int parseSeconds(String s) throws CommandException {
        try {
            int v = Integer.parseInt(s.trim());
            if (v <= 0 || v > 3600) {
                throw new NumberFormatException();
            }
            return v;
        }
        catch (NumberFormatException e) {
            throw new CommandException("rbmkexport.bad_seconds", s);
        }
    }

    /**
     * 注视射线：眼位沿视线找最近命中，命中块是可见面板（part 方块，
     * 无 TileEntity）时经 {@link com.jiezhirbmkmod.block.ScreenMultiblock
     * #findController} 解析到控制器屏 —— 与导出设置面板（双击）同一条
     * 解析路径，两条入口行为一致。
     */
    private static TileRbmkMonitorScreen aimedScreen(EntityPlayer player) {
        World w = player.world;
        if (w == null) {
            return null;
        }
        RayTraceResult hit = player.rayTrace(RbmkExportCommand.REACH, 1.0F);
        if (hit == null || hit.typeOfHit != RayTraceResult.Type.BLOCK) {
            return null;
        }
        BlockPos p = hit.getBlockPos();
        if (w.getTileEntity(p) instanceof TileRbmkMonitorScreen) {
            return (TileRbmkMonitorScreen) w.getTileEntity(p);
        }
        BlockPos ctrl = com.jiezhirbmkmod.block.ScreenMultiblock.findController(w, p);
        if (ctrl != null && w.getTileEntity(ctrl) instanceof TileRbmkMonitorScreen) {
            return (TileRbmkMonitorScreen) w.getTileEntity(ctrl);
        }
        return null;
    }

    @Override
    public List<String> getTabCompletions(net.minecraft.server.MinecraftServer server,
            ICommandSender sender, String[] args, @Nullable BlockPos pos) {
        if (args.length == 1) {
            return CommandBase.getListOfStringsMatchingLastWord(args, "csv", "png");
        }
        if (args.length == 2) {
            return CommandBase.getListOfStringsMatchingLastWord(args, "30", "60", "300");
        }
        return java.util.Collections.<String>emptyList();
    }
}
