package com.jiezhirbmkmod.network.msg;

import com.jiezhirbmkmod.block.TileRbmkSiren;
import com.jiezhirbmkmod.block.TileRbmkTermScreen;
import com.jiezhirbmkmod.block.TileRbmkTerminal;
import com.jiezhirbmkmod.dfc.DfcFormat;
import com.jiezhirbmkmod.dfc.RbmkBridge;
import com.jiezhirbmkmod.dfc.RbmkConfig;
import com.jiezhirbmkmod.dfc.RbmkControl;
import com.jiezhirbmkmod.dfc.RbmkSnapshot;
import com.jiezhirbmkmod.item.ItemRbmkLaptop;
import com.jiezhirbmkmod.network.ModNetwork;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.relauncher.Side;

/**
 * Terminal command round trip.
 *
 * Client -> server: one raw command line, typed into the terminal GUI opened
 * on a host block (floor console or the 4x3 wall display).
 * Server -> client: the rendered output lines with cmd-style colors.
 *
 * The host block owns the console binding; nothing is read from the client
 * except the command string itself - the server re-resolves the host tile,
 * checks the player is standing next to it, then resolves the bound console
 * and executes. Client input is never trusted.
 */
public class MessageTerminalCommand implements IMessage {

    public static final int C_OUT = 0xFFCCCCCC;
    public static final int C_OK = 0xFF16C60C;
    public static final int C_ERR = 0xFFE74856;
    public static final int C_WARN = 0xFFF9F1A5;
    public static final int C_DIM = 0xFF767676;
    public static final int C_PROMPT = 0xFF16C60C;

    public static final String[] HELP = new String[] {
            "help                     show this command list (Tab autocompletes)",
            "status                   reactor status: temps, flux, steam, rods",
            "rods                     list manual control rods",
            "rods <0-100>             set ALL manual rods to <0-100>% extracted",
            "rods <0-100> <color>     set only the rods of that color",
            "scram                    AZ-5: fully insert all control rods (emergency stop)",
            "bind                     link this terminal to the nearest RBMK console",
            "clear                    unlink this terminal from the reactor",
            "where                    show this terminal's position and its reactor link",
            "siren <on|off|test>      trigger, silence or test nearby RBMK sirens",
            "watch <m> <op> <v> do <a>   threshold guard rule: m=heat/peak/flux/water/steam/enrich/xenon, op=> or <, a=scram/alarm/audit",
            "watch list               show all guard rules",
            "watch del <n>            delete rule #n; watch clear deletes all",
            "history                  list previously typed commands (local)",
            "cls                      clear the screen (local)",
            "exit                     close the terminal (local)"
    };

    /** HBM manual rod colors, used for Tab completion of 'rods <0-100> <color>' */
    public static final String[] ROD_COLORS = new String[]{"RED", "YELLOW", "GREEN", "BLUE", "PURPLE"};
    /** search radius (blocks) of the 'siren' command around the terminal */
    public static final int SIREN_RADIUS = 16;
    public static final String[] SIREN_ACTIONS = new String[]{"on", "off", "test"};
    /** v18.2 watch 命令的 Tab 补全片段 */
    public static final String[] WATCH_METRICS = new String[]{"heat", "peak", "flux", "water", "steam", "enrich", "xenon"};
    public static final String[] WATCH_ACTIONS = new String[]{"scram", "alarm", "audit"};
    public static final String[] COMMANDS = new String[]{"help", "status", "rods", "scram", "bind", "clear",
            "cls", "exit", "az5", "unbind", "quit", "where", "siren", "history", "watch"};

    public static final class CmdLine {
        public final int color;
        public final String text;

        public CmdLine(int color, String text) {
            this.color = color;
            this.text = text == null ? "" : text;
        }
    }

    private boolean reply;
    /** true when the command comes from the handheld laptop (v11.2) */
    private boolean laptop;
    private int hx;
    private int hy;
    private int hz;
    private String command = "";
    private final List<CmdLine> lines = new ArrayList<CmdLine>();

    public MessageTerminalCommand() {
    }

    public MessageTerminalCommand(BlockPos host, String command) {
        this.hx = host.getX();
        this.hy = host.getY();
        this.hz = host.getZ();
        this.command = command;
    }

    /** handheld laptop variant: the host is the held item, not a block */
    public static MessageTerminalCommand laptop(String command) {
        MessageTerminalCommand m = new MessageTerminalCommand();
        m.laptop = true;
        m.command = command;
        return m;
    }

    public MessageTerminalCommand(List<CmdLine> replyLines) {
        this.reply = true;
        this.lines.addAll(replyLines);
    }

    public List<CmdLine> getLines() {
        return this.lines;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeBoolean(this.reply);
        if (!this.reply) {
            buf.writeBoolean(this.laptop);
            if (!this.laptop) {
                buf.writeInt(this.hx);
                buf.writeInt(this.hy);
                buf.writeInt(this.hz);
            }
            RbmkSnapshot.writeShortString(buf, this.command);
        } else {
            buf.writeInt(this.lines.size());
            for (int i = 0; i < this.lines.size(); ++i) {
                buf.writeInt(this.lines.get(i).color);
                RbmkSnapshot.writeShortString(buf, this.lines.get(i).text);
            }
        }
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.reply = buf.readBoolean();
        this.lines.clear();
        if (!this.reply) {
            this.laptop = buf.readBoolean();
            if (!this.laptop) {
                this.hx = buf.readInt();
                this.hy = buf.readInt();
                this.hz = buf.readInt();
            }
            this.command = RbmkSnapshot.readShortString(buf);
        } else {
            int n = buf.readInt();
            for (int i = 0; i < n; ++i) {
                int color = buf.readInt();
                String text = RbmkSnapshot.readShortString(buf);
                this.lines.add(new CmdLine(color, text));
            }
        }
    }

    public static class Handler implements IMessageHandler<MessageTerminalCommand, IMessage> {

        @Override
        public IMessage onMessage(MessageTerminalCommand msg, MessageContext ctx) {
            if (ctx.side == Side.SERVER) {
                final EntityPlayerMP player = ctx.getServerHandler().player;
                final MessageTerminalCommand req = msg;
                player.getServerWorld().addScheduledTask(() -> {
                    List<CmdLine> out = new ArrayList<CmdLine>();
                    try {
                        Handler.execute(player, req, out);
                    } catch (Throwable t) {
                        out.add(new CmdLine(C_ERR, "  Internal error: " + t));
                    }
                    ModNetwork.CHANNEL.sendTo((IMessage) new MessageTerminalCommand(out), player);
                });
                return null;
            }
            final MessageTerminalCommand replyMsg = msg;
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getMinecraft();
            mc.addScheduledTask(() -> {
                net.minecraft.client.gui.GuiScreen screen = mc.currentScreen;
                if (screen instanceof com.jiezhirbmkmod.client.gui.GuiTerminal) {
                    ((com.jiezhirbmkmod.client.gui.GuiTerminal) screen).onServerReply(replyMsg.getLines());
                }
            });
            return null;
        }

        private static void execute(EntityPlayerMP player, MessageTerminalCommand msg, List<CmdLine> out) {
            World world = player.getServerWorld();
            String trimmed = msg.command == null ? "" : msg.command.trim();
            if (trimmed.isEmpty()) {
                return;
            }
            // handheld laptop: the console comes from the held item's NBT,
            // never from the client; there is no host block to validate
            if (msg.laptop) {
                Handler.executeLaptop(player, world, trimmed, out);
                return;
            }
            BlockPos hostPos = new BlockPos(msg.hx, msg.hy, msg.hz);
            TileEntity host = world.getTileEntity(hostPos);
            if (!(host instanceof TileRbmkTerminal) && !(host instanceof TileRbmkTermScreen)) {
                out.add(new CmdLine(C_ERR, "  Terminal not found at its position."));
                return;
            }
            Handler.executeOnConsole(world, player, host, hostPos, trimmed, out);
        }

        /** laptop path: resolve the console bound into the held stack, then run the shared command set. */
        private static void executeLaptop(EntityPlayerMP player, World world, String trimmed, List<CmdLine> out) {
            ItemStack held = ItemRbmkLaptop.findHeld(player);
            if (held.isEmpty() || !(held.getItem() instanceof ItemRbmkLaptop)) {
                out.add(new CmdLine(C_ERR, "  The portable terminal is not in your hand."));
                return;
            }
            BlockPos consolePos = ItemRbmkLaptop.readConsole(held);
            if (consolePos == null) {
                out.add(new CmdLine(C_ERR, "  No reactor connected. Right-click an RBMK console or a column"));
                out.add(new CmdLine(C_ERR, "  while holding the portable terminal to link it."));
                return;
            }
            if (ItemRbmkLaptop.readDim(held) != world.provider.getDimension()) {
                out.add(new CmdLine(C_ERR, "  The linked console is in another dimension."));
                return;
            }
            String[] parts = trimmed.toLowerCase(Locale.ROOT).split("\\s+");
            String cmd = parts[0];
            // local commands make no sense from a handheld, but keep the
            // shared set consistent: 'bind'/'clear' manage the item link
            if ("bind".equals(cmd)) {
                BlockPos console = RbmkBridge.findConsoleNear(world, player.getPosition(), RbmkConfig.consoleSearchRadius);
                if (console == null) {
                    out.add(new CmdLine(C_ERR, "  No RBMK console found within " + RbmkConfig.consoleSearchRadius
                            + " blocks of you."));
                    return;
                }
                ItemRbmkLaptop.writeConsole(held, console, world.provider.getDimension());
                out.add(new CmdLine(C_OK, "  Portable terminal linked to RBMK console @ "
                        + console.getX() + " " + console.getY() + " " + console.getZ() + "."));
                return;
            }
            if ("clear".equals(cmd) || "unbind".equals(cmd)) {
                ItemRbmkLaptop.clearConsole(held);
                out.add(new CmdLine(C_OK, "  Portable terminal unlinked from the reactor."));
                return;
            }
            if ("where".equals(cmd)) {
                double dist = Math.sqrt(player.getDistanceSq((double) consolePos.getX() + 0.5D,
                        (double) consolePos.getY() + 0.5D, (double) consolePos.getZ() + 0.5D));
                if (RbmkBridge.isConsole(world.getTileEntity(consolePos))) {
                    out.add(new CmdLine(C_OK, "  Portable terminal, linked to RBMK console @ "
                            + consolePos.getX() + " " + consolePos.getY() + " " + consolePos.getZ() + " ("
                            + String.format(Locale.ROOT, "%.0f", Double.valueOf(dist)) + "m away)"));
                } else {
                    out.add(new CmdLine(C_ERR, "  The linked console is gone (was it removed?). Relink it."));
                }
                return;
            }
            if ("siren".equals(cmd)) {
                Handler.cmdSiren(world, player.getPosition(), parts, out);
                return;
            }
            if (player.getDistanceSq((double) consolePos.getX() + 0.5D, (double) consolePos.getY() + 0.5D,
                    (double) consolePos.getZ() + 0.5D) > 16384.0D) {
                out.add(new CmdLine(C_ERR, "  The linked console is too far away (max 128m)."));
                return;
            }
            TileEntity console = world.getTileEntity(consolePos);
            if (!RbmkBridge.isConsole(console)) {
                out.add(new CmdLine(C_ERR, "  The linked console is gone (was it removed?). Relink it."));
                return;
            }
            BlockPos center = RbmkControl.reactorCenter(world, console);
            Handler.runShared(world, console, center, parts, cmd, out, player);
        }

        /** block-terminal path after the host tile was validated. */
        private static void executeOnConsole(World world, EntityPlayerMP player, TileEntity host, BlockPos hostPos,
                String trimmed, List<CmdLine> out) {
            if (player.getDistanceSq((double) hostPos.getX() + 0.5D, (double) hostPos.getY() + 0.5D,
                    (double) hostPos.getZ() + 0.5D) > 4096.0D) {
                out.add(new CmdLine(C_ERR, "  Terminal out of reach (max 64m)."));
                return;
            }
            String[] parts = trimmed.toLowerCase(Locale.ROOT).split("\\s+");
            String cmd = parts[0];

            if ("help".equals(cmd)) {
                out.add(new CmdLine(C_OUT, "RBMK Terminal - available commands:"));
                for (int i = 0; i < HELP.length; ++i) {
                    out.add(new CmdLine(C_OUT, "  " + HELP[i]));
                }
                return;
            }
            if ("bind".equals(cmd)) {
                Handler.cmdBind(world, host, hostPos, out);
                return;
            }
            // 'where' works with or without a bound console (v11.1)
            if ("where".equals(cmd)) {
                Handler.cmdWhere(world, host, hostPos, out);
                return;
            }
            // 'siren' controls nearby siren blocks, no reactor link needed (v11.1)
            if ("siren".equals(cmd)) {
                Handler.cmdSiren(world, hostPos, parts, out);
                return;
            }
            if ("clear".equals(cmd) || "unbind".equals(cmd)) {
                if (host instanceof TileRbmkTerminal) {
                    ((TileRbmkTerminal) host).clearBinding();
                } else {
                    ((TileRbmkTermScreen) host).bindSource(null, player);
                }
                out.add(new CmdLine(C_OK, "  Terminal unlinked from the reactor."));
                return;
            }

            BlockPos consolePos = host instanceof TileRbmkTerminal
                    ? ((TileRbmkTerminal) host).getConsolePos()
                    : ((TileRbmkTermScreen) host).effectiveConsole();
            if (consolePos == null) {
                out.add(new CmdLine(C_ERR, "  No reactor linked. Right-click an RBMK console with the RBMK remote,"));
                out.add(new CmdLine(C_ERR, "  or stand near a console and type 'bind'."));
                return;
            }
            if (player.getDistanceSq((double) consolePos.getX() + 0.5D, (double) consolePos.getY() + 0.5D,
                    (double) consolePos.getZ() + 0.5D) > 16384.0D) {
                out.add(new CmdLine(C_ERR, "  The linked console is too far away (max 128m)."));
                return;
            }
            TileEntity console = world.getTileEntity(consolePos);
            if (!RbmkBridge.isConsole(console)) {
                out.add(new CmdLine(C_ERR, "  The linked console is gone (was it removed?). Type 'bind' near one to relink."));
                return;
            }
            BlockPos center = RbmkControl.reactorCenter(world, console);
            Handler.runShared(world, console, center, parts, cmd, out, player);
        }

        /** the reactor commands shared by every terminal variant. */
        private static void runShared(World world, TileEntity console, BlockPos center, String[] parts,
                String cmd, List<CmdLine> out, EntityPlayerMP player) {
            if ("help".equals(cmd)) {
                out.add(new CmdLine(C_OUT, "RBMK Terminal - available commands:"));
                for (int i = 0; i < HELP.length; ++i) {
                    out.add(new CmdLine(C_OUT, "  " + HELP[i]));
                }
            } else if ("status".equals(cmd)) {
                Handler.cmdStatus(world, console, out);
            } else if ("rods".equals(cmd)) {
                Handler.cmdRods(world, center, parts, out);
            } else if ("scram".equals(cmd) || "az5".equals(cmd)) {
                Handler.cmdScram(world, center, out, player);
            } else if ("watch".equals(cmd)) {
                Handler.cmdWatch(world, console, parts, out, player);
            } else {
                out.add(new CmdLine(C_ERR, "  '" + cmd + "' is not recognized as an RBMK terminal command. Type 'help'."));
            }
        }

        private static void cmdBind(World world, TileEntity host, BlockPos hostPos, List<CmdLine> out) {
            BlockPos console = RbmkBridge.findConsoleNear(world, hostPos, RbmkConfig.consoleSearchRadius);
            if (console == null) {
                out.add(new CmdLine(C_ERR, "  No RBMK console found within " + RbmkConfig.consoleSearchRadius
                        + " blocks of this terminal."));
                return;
            }
            if (host instanceof TileRbmkTerminal) {
                ((TileRbmkTerminal) host).bindConsole(console);
            } else {
                ((TileRbmkTermScreen) host).bindSource(console, null);
            }
            out.add(new CmdLine(C_OK, "  Linked to RBMK console @ "
                    + console.getX() + " " + console.getY() + " " + console.getZ() + "."));
        }

        /** 'where': show this terminal's position and the state of its reactor link. */
        private static void cmdWhere(World world, TileEntity host, BlockPos hostPos, List<CmdLine> out) {
            String kind = host instanceof TileRbmkTerminal ? "floor console" : "wall display";
            out.add(new CmdLine(C_OUT, "  Terminal     : " + kind + " @ "
                    + hostPos.getX() + " " + hostPos.getY() + " " + hostPos.getZ()));
            BlockPos linked = host instanceof TileRbmkTerminal
                    ? ((TileRbmkTerminal) host).getConsolePos()
                    : ((TileRbmkTermScreen) host).effectiveConsole();
            if (linked == null) {
                out.add(new CmdLine(C_WARN, "  Reactor link : not linked (type 'bind' near a console)"));
                return;
            }
            double dist = Math.sqrt(hostPos.distanceSq(linked));
            if (RbmkBridge.isConsole(world.getTileEntity(linked))) {
                out.add(new CmdLine(C_OK, "  Reactor link : RBMK console @ "
                        + linked.getX() + " " + linked.getY() + " " + linked.getZ() + " ("
                        + String.format(Locale.ROOT, "%.0f", Double.valueOf(dist)) + "m away)"));
            } else {
                out.add(new CmdLine(C_ERR, "  Reactor link : stale (console gone @ "
                        + linked.getX() + " " + linked.getY() + " " + linked.getZ() + "), type 'bind' to relink"));
            }
        }

        /** 'siren': manual on/off/test of the nearby rbmk_siren blocks. */
        private static void cmdSiren(World world, BlockPos hostPos, String[] parts, List<CmdLine> out) {
            if (parts.length < 2) {
                out.add(new CmdLine(C_ERR, "  Usage: siren <on|off|test>"));
                return;
            }
            String action = parts[1];
            boolean on = "on".equals(action);
            boolean off = "off".equals(action);
            boolean test = "test".equals(action);
            if (!on && !off && !test) {
                out.add(new CmdLine(C_ERR, "  Unknown action '" + action + "'. Usage: siren <on|off|test>"));
                return;
            }
            List<TileRbmkSiren> found = new ArrayList<TileRbmkSiren>();
            for (int dy = -4; dy <= 4; ++dy) {
                for (int dz = -SIREN_RADIUS; dz <= SIREN_RADIUS; ++dz) {
                    for (int dx = -SIREN_RADIUS; dx <= SIREN_RADIUS; ++dx) {
                        TileEntity te = world.getTileEntity(hostPos.add(dx, dy, dz));
                        if (te instanceof TileRbmkSiren) {
                            found.add((TileRbmkSiren) te);
                        }
                    }
                }
            }
            if (found.isEmpty()) {
                out.add(new CmdLine(C_WARN, "  No RBMK sirens found within " + SIREN_RADIUS + " blocks."));
                return;
            }
            if (test) {
                for (int i = 0; i < found.size(); ++i) {
                    found.get(i).testBlare();
                }
                out.add(new CmdLine(C_OK, "  Test blare played on " + found.size() + " siren(s)."));
                return;
            }
            for (int i = 0; i < found.size(); ++i) {
                found.get(i).setManual(on);
            }
            out.add(new CmdLine(on ? C_WARN : C_OK,
                    "  " + found.size() + " siren(s) manually " + (on ? "RINGING" : "silenced")
                    + (on ? " (use 'siren off' to silence)." : ".")));
        }

        private static void cmdStatus(World world, TileEntity console, List<CmdLine> out) {
            RbmkSnapshot s = RbmkSnapshot.empty();
            if (!RbmkBridge.read(console, s)) {
                out.add(new CmdLine(C_ERR, "  Could not read the reactor (is HBM's Nuclear Tech installed?)."));
                return;
            }
            if (s.meltdown) {
                out.add(new CmdLine(C_ERR, "  Reactor status : MELTDOWN RISK"));
            } else if (s.offline) {
                out.add(new CmdLine(C_WARN, "  Reactor status : OFFLINE (no columns scanned)"));
            } else if (s.cold) {
                out.add(new CmdLine(C_DIM, "  Reactor status : COLD (shut down)"));
            } else {
                out.add(new CmdLine(C_OK, "  Reactor status : ONLINE"));
            }
            out.add(new CmdLine(C_OUT, "  Columns scanned : " + s.columnCount));
            out.add(new CmdLine(C_OUT, "  Avg column temp  : " + DfcFormat.temperature(s.avgHeat)));
            if (s.hottestIndex >= 0) {
                out.add(new CmdLine(C_OUT, "  Peak column temp : " + DfcFormat.temperature(s.maxHeat) + " / "
                        + DfcFormat.temperature(s.maxHeatLimit) + " @ #" + s.hottestIndex));
            }
            out.add(new CmdLine(C_OUT, "  Neutron flux     : " + DfcFormat.si(s.flux, "n/s")));
            out.add(new CmdLine(C_OUT, "  Steam total      : " + DfcFormat.amount(s.steamTotal)));
            if (s.fuelCount > 0) {
                out.add(new CmdLine(C_OUT, "  Fuel enrichment  : " + DfcFormat.percent(s.avgEnrichment)
                        + "   Xenon poison: " + DfcFormat.percent(s.avgXenon)));
            }
            if (s.controlCount > 0) {
                out.add(new CmdLine(C_OUT, "  Control rods     : " + s.controlCount + " rods, avg "
                        + DfcFormat.percent(s.avgRodLevel) + " extracted"));
            }
            if (s.boilerCount > 0) {
                out.add(new CmdLine(C_OUT, "  Boiler water     : " + DfcFormat.amount(s.waterTotal)));
            }
        }

        private static void cmdRods(World world, BlockPos center, String[] parts, List<CmdLine> out) {
            List<TileEntity> rods = RbmkControl.collectRods(world, center, 7);
            if (rods.isEmpty()) {
                out.add(new CmdLine(C_WARN, "  No manual control rods found around the console."));
                return;
            }
            if (parts.length == 1) {
                double level = 0.0D;
                double target = 0.0D;
                for (int i = 0; i < rods.size(); ++i) {
                    level += RbmkControl.rodLevel(rods.get(i));
                    target += RbmkControl.rodTarget(rods.get(i));
                }
                out.add(new CmdLine(C_OUT, "  Manual control rods : " + rods.size()));
                out.add(new CmdLine(C_OUT, "  Avg extension       : " + DfcFormat.percent(level / rods.size())
                        + " (targets " + DfcFormat.percent(target / rods.size()) + ")"));
                Map<String, int[]> colors = new LinkedHashMap<String, int[]>();
                for (int i = 0; i < rods.size(); ++i) {
                    String c = RbmkControl.rodColor(rods.get(i));
                    if (c == null) {
                        c = "NONE";
                    }
                    int[] acc = colors.get(c);
                    if (acc == null) {
                        acc = new int[]{0, 0};
                        colors.put(c, acc);
                    }
                    acc[0] = acc[0] + 1;
                    acc[1] = (int) (acc[1] + RbmkControl.rodLevel(rods.get(i)) * 100.0D);
                }
                for (Map.Entry<String, int[]> e : colors.entrySet()) {
                    int[] acc = e.getValue();
                    out.add(new CmdLine(C_OUT, "  " + e.getKey() + " : " + acc[0] + " rods, avg "
                            + (acc[1] / acc[0]) + "% extracted"));
                }
                return;
            }
            double pct;
            try {
                pct = Double.parseDouble(parts[1]);
            } catch (NumberFormatException e) {
                out.add(new CmdLine(C_ERR, "  Usage: rods <0-100> [color]"));
                return;
            }
            if (pct < 0.0D || pct > 100.0D) {
                out.add(new CmdLine(C_ERR, "  Level out of range, expected 0-100."));
                return;
            }
            double level01 = pct / 100.0D;
            String color = parts.length >= 3 ? parts[2] : null;
            int set = 0;
            int matched = 0;
            for (int i = 0; i < rods.size(); ++i) {
                String rodColor = RbmkControl.rodColor(rods.get(i));
                if (color != null && (rodColor == null || !rodColor.equalsIgnoreCase(color))) {
                    continue;
                }
                ++matched;
                if (RbmkControl.setRodLevel(rods.get(i), level01)) {
                    ++set;
                }
            }
            if (set == 0) {
                out.add(new CmdLine(C_WARN, "  No control rods matched" + (color != null ? " color '" + color + "'" : "") + "."));
                return;
            }
            String shown = String.format(Locale.ROOT, "%.0f", pct);
            out.add(new CmdLine(C_OK, "  " + set + " control rod(s) target set to " + shown + "% extracted."));
        }

        private static void cmdScram(World world, BlockPos center, List<CmdLine> out,
                EntityPlayerMP player) {
            List<TileEntity> rods = RbmkControl.collectRods(world, center, 7);
            int set = 0;
            for (int i = 0; i < rods.size(); ++i) {
                if (RbmkControl.setRodLevel(rods.get(i), 0.0D)) {
                    ++set;
                }
            }
            if (set == 0) {
                out.add(new CmdLine(C_WARN, "  No manual control rods found around the console."));
                return;
            }
            // v18.0 审计：手动 SCRAM 进 rbmk_audit.log（只记成功触发的）
            com.jiezhirbmkmod.util.RbmkAuditLogger.logAt(
                    player == null ? null : player.getName(), "scram", world, center);
            out.add(new CmdLine(C_OK, "  AZ-5 SCRAM engaged: " + set + " control rod(s) fully inserted."));
            out.add(new CmdLine(C_WARN, "  The reactor will shut down once the rods reach the bottom."));
        }

        // ==================== v18.2 阈值自动化防护链 ====================

        /**
         * 'watch' 命令：阈值监视规则的管理入口。语法对齐设计稿：
         * <pre>
         *   watch heat &gt; 900 do scram     # 命中自动 AZ-5
         *   watch peak &gt; 1100 do alarm    # 命中全员报警 + 汽笛试鸣
         *   watch flux &lt; 50 do audit      # 命中只记审计
         *   watch list / watch del &lt;n&gt; / watch clear
         * </pre>
         * 开关 {@code RbmkConfig.allowThresholdWatch} 关闭时整条命令不可用
         * （审批 GUI 开启后才生效）。规则按绑定控制台为键，落盘
         * {@code rbmk_watch.json}。
         */
        private static void cmdWatch(World world, TileEntity console, String[] parts,
                List<CmdLine> out, EntityPlayerMP player) {
            if (!com.jiezhirbmkmod.dfc.RbmkConfig.allowThresholdWatch) {
                out.add(new CmdLine(C_ERR, "  Threshold guard (watch) is disabled on this server."));
                out.add(new CmdLine(C_DIM, "  Ask an operator to approve it via the op badge application GUI."));
                return;
            }
            // 子命令
            if (parts.length >= 2 && "list".equals(parts[1])) {
                java.util.List<com.jiezhirbmkmod.dfc.WatchChain.Rule> rules =
                        com.jiezhirbmkmod.dfc.WatchChain.snapshotRules();
                if (rules.isEmpty()) {
                    out.add(new CmdLine(C_DIM, "  No guard rules. Add one: watch heat > 900 do scram"));
                    return;
                }
                out.add(new CmdLine(C_OUT, "  Threshold guard rules (" + rules.size() + "):"));
                for (int i = 0; i < rules.size(); ++i) {
                    com.jiezhirbmkmod.dfc.WatchChain.Rule r = rules.get(i);
                    out.add(new CmdLine(C_OUT, "  #" + (i + 1) + " [" + r.key + "] "
                            + r.describe() + "  (by " + r.createdBy + ")"));
                }
                return;
            }
            if (parts.length >= 2 && "clear".equals(parts[1])) {
                int n = com.jiezhirbmkmod.dfc.WatchChain.clearRules();
                out.add(new CmdLine(n > 0 ? C_OK : C_WARN, "  Deleted " + n + " guard rule(s)."));
                return;
            }
            if (parts.length >= 3 && "del".equals(parts[1])) {
                int idx;
                try {
                    idx = Integer.parseInt(parts[2]);
                } catch (NumberFormatException e) {
                    out.add(new CmdLine(C_ERR, "  Usage: watch del <rule number> (see 'watch list')"));
                    return;
                }
                String removed = com.jiezhirbmkmod.dfc.WatchChain.removeRule(idx);
                if (removed == null) {
                    out.add(new CmdLine(C_ERR, "  No such rule #" + idx + " (see 'watch list')"));
                } else {
                    out.add(new CmdLine(C_OK, "  Deleted rule #" + idx + ": " + removed));
                }
                return;
            }
            // watch <metric> <op> <value> do <action>
            if (parts.length != 6 || !"do".equals(parts[4])) {
                out.add(new CmdLine(C_ERR, "  Usage: watch <metric> <op> <value> do <action>"));
                out.add(new CmdLine(C_DIM, "  metric: heat/peak/flux/water/steam/enrich/xenon  op: > or <"));
                out.add(new CmdLine(C_DIM, "  action: scram/alarm/audit   e.g. watch heat > 900 do scram"));
                return;
            }
            double threshold;
            try {
                threshold = Double.parseDouble(parts[3]);
            } catch (NumberFormatException e) {
                out.add(new CmdLine(C_ERR, "  Threshold must be a number, got '" + parts[3] + "'."));
                return;
            }
            String err = com.jiezhirbmkmod.dfc.WatchChain.addRule(world, console.getPos(),
                    parts[1], parts[2], threshold, parts[5],
                    player == null ? null : player.getName());
            if (err != null) {
                out.add(new CmdLine(C_ERR, "  " + err));
                return;
            }
            out.add(new CmdLine(C_OK, "  Guard rule armed: " + parts[1] + " " + parts[2] + " "
                    + parts[3] + " do " + parts[5]));
            out.add(new CmdLine(C_DIM, "  Scans once per second; cooldown 10s between actions; survives restarts (rbmk_watch.json)."));
        }
    }
}
