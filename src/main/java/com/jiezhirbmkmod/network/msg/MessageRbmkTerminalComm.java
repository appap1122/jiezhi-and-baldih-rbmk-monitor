package com.jiezhirbmkmod.network.msg;


import com.jiezhirbmkmod.network.NetGuard;
import com.jiezhirbmkmod.block.TileRbmkMediaScreen;
import com.jiezhirbmkmod.block.TileRbmkMonitorScreen;
import com.jiezhirbmkmod.network.ModNetwork;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.block.Block;
import net.minecraft.block.BlockRedstoneWire;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.relauncher.Side;

/**
 * Terminal side-channel used by the v15.0 'msg' / 'password' / 'auth'
 * commands.
 *
 * Client -> server only. The sender name carried in the packet is never
 * trusted: the server always uses the authenticated connection name, then
 * resolves the target screen and performs the NBT write itself.
 */
public class MessageRbmkTerminalComm
implements IMessage {

    /** 'msg <player> <text>': deliver a short text message to a screen near the target player */
    public static final int ACTION_MSG = 0;
    /** 'password <4 digits>': set the anti-grief security code on the screen */
    public static final int ACTION_SET_CODE = 1;
    /** 'auth <4 digits>': prove the code so that 'hack' may run */
    public static final int ACTION_AUTH = 2;
    /** 'redstone <x> <y> <z> <0-15>': force a remote redstone signal */
    public static final int ACTION_REDSTONE = 3;
    /** 'media play|pause|resume|stop|volume': 4x3 multimedia screen control */
    public static final int ACTION_MEDIA = 4;

    /** hard cap on a single message, keeps the terminal from being spammed */
    public static final int MAX_MESSAGE = 30;
    /** the recipient must have one of the sender's screens this close */
    public static final int MESSAGE_RADIUS = 32;
    /** how close the player must stand to the screen they configure */
    public static final double REACH_SQ = 4096.0D;
    /** hard limit for remote redstone coordinates */
    public static final int MAX_COORD = 30000000;
    /** anti-spam guard for remote redstone writes */
    private static final java.util.Map<String, Long> REDSTONE_COOLDOWN =
            new java.util.concurrent.ConcurrentHashMap<String, Long>();
    private static final long REDSTONE_COOLDOWN_MS = 250L;

    private int action;
    private BlockPos pos = BlockPos.ORIGIN;
    private String sender = "";
    private String target = "";
    private String payload = "";

    public MessageRbmkTerminalComm() {
    }

    public MessageRbmkTerminalComm(int action, BlockPos pos, String target, String payload) {
        this.action = action;
        this.pos = pos == null ? BlockPos.ORIGIN : pos;
        this.target = target == null ? "" : target;
        this.payload = payload == null ? "" : payload;
    }

    public void setSender(String sender) {
        this.sender = sender == null ? "" : sender;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(this.action);
        buf.writeInt(this.pos.getX());
        buf.writeInt(this.pos.getY());
        buf.writeInt(this.pos.getZ());
        ByteBufUtils.writeUTF8String(buf, MessageRbmkTerminalComm.cap(this.sender, 32));
        ByteBufUtils.writeUTF8String(buf, MessageRbmkTerminalComm.cap(this.target, 32));
        ByteBufUtils.writeUTF8String(buf, MessageRbmkTerminalComm.cap(this.payload, 64));
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.action = buf.readInt();
        this.pos = new BlockPos(buf.readInt(), buf.readInt(), buf.readInt());
        this.sender = ByteBufUtils.readUTF8String(buf);
        this.target = ByteBufUtils.readUTF8String(buf);
        this.payload = ByteBufUtils.readUTF8String(buf);
    }

    private static String cap(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max);
    }

    /** 4 ASCII digits or null. */
    private static String digits(String raw) {
        if (raw == null) {
            return null;
        }
        String s = raw.trim();
        if (s.length() != 4) {
            return null;
        }
        for (int i = 0; i < 4; ++i) {
            if (!Character.isDigit(s.charAt(i))) {
                return null;
            }
        }
        return s;
    }

    private static void reply(EntityPlayerMP player, int color, String text) {
        if (player == null) {
            return;
        }
        List<MessageTerminalCommand.CmdLine> lines = new ArrayList<MessageTerminalCommand.CmdLine>();
        lines.add(new MessageTerminalCommand.CmdLine(color, text));
        NetGuard.sendTo((IMessage) new MessageTerminalCommand(lines), player);
    }

    private static EntityPlayerMP findPlayer(MinecraftServer server, String name) {
        if (server == null || server.getPlayerList() == null || name == null || name.isEmpty()) {
            return null;
        }
        for (EntityPlayerMP p : server.getPlayerList().getPlayers()) {
            if (p != null && p.getName() != null && p.getName().equalsIgnoreCase(name)) {
                return p;
            }
        }
        return null;
    }

    /** the screen the player is configuring: must be a monitor tile within reach */
    private static TileRbmkMonitorScreen reachableScreen(EntityPlayerMP player, BlockPos pos) {
        if (player == null || player.world == null || pos == null || pos.getY() == Integer.MIN_VALUE) {
            return null;
        }
        if (player.getDistanceSq((double) pos.getX() + 0.5D, (double) pos.getY() + 0.5D,
                (double) pos.getZ() + 0.5D) > REACH_SQ) {
            return null;
        }
        net.minecraft.tileentity.TileEntity te = player.world.getTileEntity(pos);
        return te instanceof TileRbmkMonitorScreen ? (TileRbmkMonitorScreen) te : null;
    }

    private static void handle(EntityPlayerMP player, MessageRbmkTerminalComm msg) {
        String me = player.getName();
        switch (msg.action) {
            case ACTION_MSG:
                MessageRbmkTerminalComm.handleMessage(player, msg, me);
                break;
            case ACTION_SET_CODE:
                MessageRbmkTerminalComm.handleSetCode(player, msg, me);
                break;
            case ACTION_AUTH:
                MessageRbmkTerminalComm.handleAuth(player, msg, me);
                break;
            case ACTION_REDSTONE:
                MessageRbmkTerminalComm.handleRedstone(player, msg);
                break;
            case ACTION_MEDIA:
                MessageRbmkTerminalComm.handleMedia(player, msg);
                break;
            default:
                break;
        }
    }

    private static void handleMessage(EntityPlayerMP player, MessageRbmkTerminalComm msg, String me) {
        String targetName = msg.target == null ? "" : msg.target.trim();
        String text = msg.payload == null ? "" : msg.payload.trim();
        if (targetName.isEmpty() || text.isEmpty()) {
            MessageRbmkTerminalComm.reply(player, MessageTerminalCommand.C_ERR,
                    "  用法: msg <玩家ID> <内容>（单条最多 30 字符）");
            return;
        }
        if (text.length() > MAX_MESSAGE) {
            text = text.substring(0, MAX_MESSAGE);
        }
        EntityPlayerMP target = MessageRbmkTerminalComm.findPlayer(player.getServer(), targetName);
        if (target == null || target.world == null) {
            MessageRbmkTerminalComm.reply(player, MessageTerminalCommand.C_ERR,
                    "[MSG] 目标玩家 " + targetName + " 不在线。");
            return;
        }
        TileRbmkMonitorScreen screen = TileRbmkMonitorScreen.findScreenNear(
                target.world, target.getPosition(), MESSAGE_RADIUS, me);
        if (screen == null) {
            MessageRbmkTerminalComm.reply(player, MessageTerminalCommand.C_ERR,
                    "[MSG] " + target.getName() + " 附近 " + MESSAGE_RADIUS + " 格内没有你的 RBMK 屏幕。");
            return;
        }
        screen.receiveTerminalMessage(me, text);
        BlockPos p = screen.getPos();
        MessageRbmkTerminalComm.reply(player, MessageTerminalCommand.C_OK,
                "[MSG] 已送达 " + target.getName() + " 附近的屏幕 @ ("
                        + p.getX() + ", " + p.getY() + ", " + p.getZ() + ")：" + text);
        target.sendMessage((net.minecraft.util.text.ITextComponent) new TextComponentString(
                TextFormatting.AQUA + "[RBMK 通讯] " + me + ": " + TextFormatting.RESET + text));
    }

    private static void handleSetCode(EntityPlayerMP player, MessageRbmkTerminalComm msg, String me) {
        String code = MessageRbmkTerminalComm.digits(msg.payload);
        if (code == null) {
            MessageRbmkTerminalComm.reply(player, MessageTerminalCommand.C_ERR,
                    "  用法: password <4位数字>");
            return;
        }
        TileRbmkMonitorScreen screen = MessageRbmkTerminalComm.reachableScreen(player, msg.pos);
        if (screen == null) {
            MessageRbmkTerminalComm.reply(player, MessageTerminalCommand.C_ERR,
                    "  没有找到可用的 RBMK 屏幕（请站在屏幕 64 格内操作）。");
            return;
        }
        if (!screen.isOwner(me)) {
            MessageRbmkTerminalComm.reply(player, MessageTerminalCommand.C_ERR,
                    "[LOCK-DENY] 该屏幕已被 " + screen.getOwnerName() + " 锁定，无法修改安全码。");
            return;
        }
        if (screen.hasSecurityCode() && !TileRbmkMonitorScreen.isAuthed(screen, me)) {
            MessageRbmkTerminalComm.reply(player, MessageTerminalCommand.C_ERR,
                    "[AUTH-REQ] 该屏幕已设置安全码，请先输入 auth <4位数字>。");
            return;
        }
        screen.setOwnerName(me);
        screen.setSecurityCode(code);
        // v18.0 审计：修改安全码进 rbmk_audit.log（不记码值本身）
        com.jiezhirbmkmod.util.RbmkAuditLogger.logAt(me, "setSecurityCode",
                player.getServerWorld(), screen.getPos());
        TileRbmkMonitorScreen.markAuthed(screen, me);
        MessageRbmkTerminalComm.reply(player, MessageTerminalCommand.C_OK,
                "[LOCK-ON] 安全码已设置，屏幕已上锁（主人：" + me + "）。");
    }

    private static void handleAuth(EntityPlayerMP player, MessageRbmkTerminalComm msg, String me) {
        String code = MessageRbmkTerminalComm.digits(msg.payload);
        if (code == null) {
            MessageRbmkTerminalComm.reply(player, MessageTerminalCommand.C_ERR,
                    "  用法: auth <4位数字>");
            return;
        }
        TileRbmkMonitorScreen screen = MessageRbmkTerminalComm.reachableScreen(player, msg.pos);
        if (screen == null) {
            MessageRbmkTerminalComm.reply(player, MessageTerminalCommand.C_ERR,
                    "  没有找到可用的 RBMK 屏幕（请站在屏幕 64 格内操作）。");
            return;
        }
        if (!screen.hasSecurityCode()) {
            TileRbmkMonitorScreen.markAuthed(screen, me);
            MessageRbmkTerminalComm.reply(player, MessageTerminalCommand.C_OK,
                    "[AUTH-OK] 该屏幕未设置安全码，已直接放行。");
            return;
        }
        if (screen.checkSecurityCode(code)) {
            TileRbmkMonitorScreen.markAuthed(screen, me);
            MessageRbmkTerminalComm.reply(player, MessageTerminalCommand.C_OK,
                    "[AUTH-OK] 验证通过，可以执行 hack。");
        } else {
            MessageRbmkTerminalComm.reply(player, MessageTerminalCommand.C_ERR,
                    "[AUTH-FAIL] 安全码错误，验证失败。");
        }
    }

    /** payload of ACTION_REDSTONE: "x y z strength", null when malformed. */
    private static int[] parseTarget(String payload) {
        if (payload == null) {
            return null;
        }
        String[] p = payload.trim().split("\\s+");
        if (p.length != 4) {
            return null;
        }
        try {
            return new int[]{Integer.parseInt(p[0]), Integer.parseInt(p[1]),
                    Integer.parseInt(p[2]), Integer.parseInt(p[3])};
        }
        catch (NumberFormatException e) {
            return null;
        }
    }

    /** 'redstone <x> <y> <z> <0-15>': force a redstone signal at an arbitrary loaded position. */
    private static void handleRedstone(EntityPlayerMP player, MessageRbmkTerminalComm msg) {
        if (player.world == null) {
            return;
        }
        TileRbmkMonitorScreen screen = MessageRbmkTerminalComm.reachableScreen(player, msg.pos);
        if (screen == null) {
            MessageRbmkTerminalComm.reply(player, MessageTerminalCommand.C_ERR,
                    "  远程红石超控需要在 RBMK 屏幕 64 格内执行。");
            return;
        }
        int[] v = MessageRbmkTerminalComm.parseTarget(msg.payload);
        if (v == null) {
            MessageRbmkTerminalComm.reply(player, MessageTerminalCommand.C_ERR,
                    "  用法: redstone <X> <Y> <Z> <0-15>");
            return;
        }
        int x = v[0];
        int y = v[1];
        int z = v[2];
        int strength = v[3];
        if (Math.abs((long) x) > MAX_COORD || Math.abs((long) z) > MAX_COORD
                || y < 0 || y > 255 || strength < 0 || strength > 15) {
            MessageRbmkTerminalComm.reply(player, MessageTerminalCommand.C_ERR,
                    "  坐标或红石强度越界（X/Z ±3000万，Y 0-255，强度 0-15）。");
            return;
        }
        BlockPos target = new BlockPos(x, y, z);
        World world = player.world;
        // never touch unloaded chunks: that would load terrain and can stall the server
        if (!world.isBlockLoaded(target)) {
            MessageRbmkTerminalComm.reply(player, MessageTerminalCommand.C_ERR,
                    "[REDSTONE] 目标区块未加载，已取消（防止服务器卡死）。");
            return;
        }
        // anti-spam / anti-loop guard: one write per position per 250 ms
        String key = player.world.provider.getDimension() + ":" + x + ":" + y + ":" + z;
        long now = System.currentTimeMillis();
        Long last = REDSTONE_COOLDOWN.get(key);
        if (last != null && now - last < REDSTONE_COOLDOWN_MS) {
            MessageRbmkTerminalComm.reply(player, MessageTerminalCommand.C_WARN,
                    "[REDSTONE] 操作过于频繁，请稍后再试。");
            return;
        }
        if (REDSTONE_COOLDOWN.size() > 1024) {
            REDSTONE_COOLDOWN.clear();
        }
        REDSTONE_COOLDOWN.put(key, now);
        try {
            IBlockState state = world.getBlockState(target);
            Block block = state.getBlock();
            boolean ours = screen.isRemoteRedstoneAt(x, y, z);
            if (strength == 0) {
                if (ours && (block == Blocks.REDSTONE_BLOCK || block == Blocks.REDSTONE_WIRE)) {
                    world.setBlockToAir(target);
                }
                world.notifyNeighborsOfStateChange(target, world.getBlockState(target).getBlock(), false);
                screen.setRemoteRedstone(x, y, z, 0);
                MessageRbmkTerminalComm.reply(player, MessageTerminalCommand.C_OK,
                        "[REDSTONE] 已关闭 (" + x + ", " + y + ", " + z + ") 的红石输出。");
                return;
            }
            boolean replaceable = block.isAir(state, world, target)
                    || (ours && (block == Blocks.REDSTONE_BLOCK || block == Blocks.REDSTONE_WIRE));
            if (!replaceable) {
                MessageRbmkTerminalComm.reply(player, MessageTerminalCommand.C_WARN,
                        "[REDSTONE] 目标位置已被方块占用，无法铺设红石发射器。");
                return;
            }
            IBlockState output = strength >= 15
                    ? Blocks.REDSTONE_BLOCK.getDefaultState()
                    : Blocks.REDSTONE_WIRE.getDefaultState()
                            .withProperty(BlockRedstoneWire.POWER, Integer.valueOf(strength));
            // flag 2 = send to clients only; the single explicit notify below
            // performs the neighbour update once, avoiding any update cascade
            world.setBlockState(target, output, 2);
            world.notifyNeighborsOfStateChange(target, output.getBlock(), false);
            screen.setRemoteRedstone(x, y, z, strength);
            MessageRbmkTerminalComm.reply(player, MessageTerminalCommand.C_OK,
                    "[REDSTONE] 已在 (" + x + ", " + y + ", " + z + ") 输出强度 " + strength + " 的红石信号。");
        }
        catch (Throwable t) {
            MessageRbmkTerminalComm.reply(player, MessageTerminalCommand.C_ERR,
                    "[REDSTONE] 操作失败：" + t);
        }
    }

    /** 'media ...': drive the nearest 4x3 multimedia master tile */
    private static void handleMedia(EntityPlayerMP player, MessageRbmkTerminalComm msg) {
        if (player.world == null) {
            return;
        }
        TileRbmkMonitorScreen terminal = MessageRbmkTerminalComm.reachableScreen(player, msg.pos);
        if (terminal == null) {
            MessageRbmkTerminalComm.reply(player, MessageTerminalCommand.C_ERR,
                    "  多媒体指令需要在 RBMK 屏幕 64 格内执行。");
            return;
        }
        String payload = msg.payload == null ? "" : msg.payload.trim();
        String[] parts = payload.split("\\s+");
        String action = parts.length >= 1 ? parts[0].toLowerCase(java.util.Locale.ROOT) : "";
        String arg = parts.length >= 2 ? parts[1] : "";
        TileRbmkMediaScreen media = TileRbmkMediaScreen.findNear(player.world, terminal.getPos(), 24);
        if (media == null) {
            MessageRbmkTerminalComm.reply(player, MessageTerminalCommand.C_ERR,
                    "[MEDIA] 终端 24 格内没有 4x3 多媒体显示屏。");
            return;
        }
        if ("play".equals(action)) {
            // v18.2 PNG 幻灯片轮播：media play slides / media play slides@8
            //（@后是每张停留秒数，3~60）。幻灯片帧由客户端从本机
            // rbmk_media/slides/ 目录读取，服务端只记录模式名。
            if (arg.toLowerCase(java.util.Locale.ROOT).matches("slides(@([3-9]|[1-5][0-9]|60))?")) {
                media.setMediaState(true, arg.toLowerCase(java.util.Locale.ROOT),
                        media.getMediaVolume(), "");
                MessageRbmkTerminalComm.reply(player, MessageTerminalCommand.C_OK,
                        "[MEDIA] Slideshow started: rbmk_media/slides/*.png ("
                                + (arg.contains("@") ? arg.substring(arg.indexOf('@') + 1) : "8")
                                + "s per slide)");
                return;
            }
            if (!arg.matches("[A-Za-z0-9._-]{1,64}")) {
                MessageRbmkTerminalComm.reply(player, MessageTerminalCommand.C_ERR,
                        "  用法: media play <文件名>（字母数字 . _ -）| slides[@秒]");
                return;
            }
            media.setMediaState(true, arg, media.getMediaVolume(), "");
            MessageRbmkTerminalComm.reply(player, MessageTerminalCommand.C_OK, "[MEDIA] Playing: " + arg);
        } else if ("pause".equals(action)) {
            media.setMediaState(false, media.getMediaFile(), media.getMediaVolume(), "");
            MessageRbmkTerminalComm.reply(player, MessageTerminalCommand.C_WARN, "[MEDIA] Paused");
        } else if ("resume".equals(action)) {
            if (media.getMediaFile().isEmpty()) {
                MessageRbmkTerminalComm.reply(player, MessageTerminalCommand.C_ERR,
                        "[MEDIA] 没有可恢复的媒体文件。");
                return;
            }
            media.setMediaState(true, media.getMediaFile(), media.getMediaVolume(), "");
            MessageRbmkTerminalComm.reply(player, MessageTerminalCommand.C_OK,
                    "[MEDIA] Playing: " + media.getMediaFile());
        } else if ("stop".equals(action)) {
            media.setMediaState(false, "", media.getMediaVolume(), "");
            MessageRbmkTerminalComm.reply(player, MessageTerminalCommand.C_OK, "[MEDIA] Stopped");
        } else if ("volume".equals(action)) {
            int volume;
            try {
                volume = Integer.parseInt(arg);
            }
            catch (NumberFormatException e) {
                MessageRbmkTerminalComm.reply(player, MessageTerminalCommand.C_ERR,
                        "  用法: media volume <0-100>");
                return;
            }
            if (volume < 0 || volume > 100) {
                MessageRbmkTerminalComm.reply(player, MessageTerminalCommand.C_ERR,
                        "  音量必须在 0-100 之间。");
                return;
            }
            media.setMediaState(media.isMediaPlaying(), media.getMediaFile(), volume, "");
            MessageRbmkTerminalComm.reply(player, MessageTerminalCommand.C_OK,
                    "[MEDIA] Volume: " + volume);
        } else {
            MessageRbmkTerminalComm.reply(player, MessageTerminalCommand.C_ERR,
                    "  用法: media play <文件> | pause | resume | stop | volume <0-100>");
        }
    }

    public static class Handler
    implements IMessageHandler<MessageRbmkTerminalComm, IMessage> {

        public IMessage onMessage(final MessageRbmkTerminalComm msg, MessageContext ctx) {
            try {
            if (ctx.side != Side.SERVER || ctx.getServerHandler() == null
                    || ctx.getServerHandler().player == null) {
                return null;
            }
            final EntityPlayerMP player = ctx.getServerHandler().player;
            final MinecraftServer server = player.getServer();
            if (server == null) {
                return null;
            }
            try {
                // never touch worlds / tile entities from the netty thread
                server.addScheduledTask(() -> {
                    try {
                        MessageRbmkTerminalComm.handle(player, msg);
                    } catch (Throwable t) {
                        MessageRbmkTerminalComm.reply(player, MessageTerminalCommand.C_ERR,
                                "  [MSG] 内部错误: " + t);
                    }
                });
            } catch (Throwable ignored) {
                // a malformed packet must never break the network thread
            }
            return null;
        
        } catch (Throwable t) {
            NetGuard.reportHandler("Handler", t);
            return null;
        }
        }
    }
}
