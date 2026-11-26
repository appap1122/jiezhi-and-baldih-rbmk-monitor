package com.jiezhirbmkmod.client;

import java.util.List;

import javax.annotation.Nullable;

import com.jiezhirbmkmod.block.TileRbmkMonitorScreen;

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
 * v18.2 黑匣子管理命令（仅客户端）：{@code /rbmkhistory}。
 *
 * <ul>
 *   <li><b>list</b>：列出本机全部黑匣子索引（键 / 采样数 / 最后保存）；</li>
 *   <li><b>merge x y z [dim]</b>：把指定屏的黑匣子<b>拼接到</b>当前注视的
 *       屏后面（中间垫采样断口），换屏/迁移存档后曲线无缝接续。环形
 *       容量装不下自动挤掉最老的样本。</li>
 * </ul>
 *
 * <p>纯客户端命令（ClientCommandHandler），数据是本机自采历史，专用
 * 服务器摸不到。</p>
 */
public class RbmkHistoryCommand
extends CommandBase {

    private static final double REACH = 32.0D;

    @Override
    public String getName() {
        return "rbmkhistory";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/rbmkhistory list | /rbmkhistory merge <x> <y> <z> [维度]";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    @Override
    public void execute(net.minecraft.server.MinecraftServer server, ICommandSender sender,
            String[] args) throws CommandException {
        if (!(sender.getCommandSenderEntity() instanceof EntityPlayer)) {
            throw new CommandException("rbmkexport.client_only");
        }
        EntityPlayer player = (EntityPlayer) sender.getCommandSenderEntity();
        if (args.length >= 1 && "list".equalsIgnoreCase(args[0])) {
            List<String> lines = HistoryStore.indexLines();
            if (lines.isEmpty()) {
                player.sendMessage(new TextComponentString(TextFormatting.YELLOW + "[jiezhirbmkmod] "
                        + "本机还没有任何黑匣子文件（rbmk_history/ 为空）。"));
                return;
            }
            player.sendMessage(new TextComponentString(TextFormatting.AQUA + "[jiezhirbmkmod] "
                    + "黑匣子索引（" + lines.size() + " 份）："));
            for (String line : lines) {
                String[] seg = line.split("\t");
                String key = seg.length > 0 ? seg[0] : line;
                String samples = seg.length > 1 ? seg[1] : "?";
                String time = "?";
                if (seg.length > 2) {
                    try {
                        time = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm")
                                .format(new java.util.Date(Long.parseLong(seg[2])));
                    }
                    catch (Throwable ignored) {
                        // 时间戳坏了就原样显示
                    }
                }
                player.sendMessage(new TextComponentString(TextFormatting.GRAY + "  "
                        + key + "  " + samples + " 采样  " + time));
            }
            return;
        }
        if (args.length >= 4 && "merge".equalsIgnoreCase(args[0])) {
            int sx;
            int sy;
            int sz;
            try {
                sx = Integer.parseInt(args[1]);
                sy = Integer.parseInt(args[2]);
                sz = Integer.parseInt(args[3]);
            }
            catch (NumberFormatException e) {
                throw new CommandException(getUsage(sender));
            }
            int srcDim = args.length >= 5
                    ? CommandBase.parseInt(args[4], -256, 255)
                    : (player.world != null ? player.world.provider.getDimension() : 0);
            // 目标 = 注视的屏
            TileRbmkMonitorScreen target = RbmkHistoryCommand.aimedScreen(player);
            if (target == null) {
                player.sendMessage(new TextComponentString(TextFormatting.RED + "[jiezhirbmkmod] "
                        + "请先对准<b>目标</b>屏（历史要接过去的那块）再执行 merge。"));
                return;
            }
            try {
                int moved = HistoryStore.mergeHistory(srcDim, new BlockPos(sx, sy, sz),
                        target.getWorld().provider.getDimension(), target.getPos());
                player.sendMessage(new TextComponentString(TextFormatting.GREEN + "[jiezhirbmkmod] "
                        + "已合并 " + moved + " 个采样点（中间垫了一个断口标记）→ "
                        + target.getPos().getX() + "," + target.getPos().getY() + ","
                        + target.getPos().getZ()));
            }
            catch (IllegalArgumentException e) {
                player.sendMessage(new TextComponentString(TextFormatting.RED + "[jiezhirbmkmod] "
                        + "合并失败：" + e.getMessage()));
            }
            return;
        }
        throw new CommandException(getUsage(sender));
    }

    /** 与 /rbmkexport 同款：注视射线找屏（32 格） */
    @Nullable
    private static TileRbmkMonitorScreen aimedScreen(EntityPlayer player) {
        World world = player.world;
        if (world == null) {
            return null;
        }
        RayTraceResult hit;
        try {
            hit = player.rayTrace(RbmkHistoryCommand.REACH, 1.0F);
        }
        catch (Throwable t) {
            return null;
        }
        if (hit == null || hit.typeOfHit != RayTraceResult.Type.BLOCK) {
            return null;
        }
        BlockPos pos = hit.getBlockPos();
        return world.getTileEntity(pos) instanceof TileRbmkMonitorScreen
                ? (TileRbmkMonitorScreen) world.getTileEntity(pos) : null;
    }
}
