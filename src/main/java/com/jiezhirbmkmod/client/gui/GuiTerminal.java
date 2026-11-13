package com.jiezhirbmkmod.client.gui;

import com.jiezhirbmkmod.block.BlockRbmkCustomScreen;
import com.jiezhirbmkmod.block.BlockRbmkGraph;
import com.jiezhirbmkmod.block.BlockRbmkMonitor;
import com.jiezhirbmkmod.block.BlockRbmkRouter;
import com.jiezhirbmkmod.block.BlockRbmkScreen;
import com.jiezhirbmkmod.block.BlockRbmkScreenPart;
import com.jiezhirbmkmod.block.TileRbmkMonitorScreen;
import com.jiezhirbmkmod.block.TileRbmkTermScreen;
import com.jiezhirbmkmod.dfc.RbmkBridge;
import com.jiezhirbmkmod.dfc.RbmkConfig;
import com.jiezhirbmkmod.network.ModNetwork;
import com.jiezhirbmkmod.network.msg.MessageTerminalCommand;
import com.jiezhirbmkmod.network.msg.MessageTerminalCommand.CmdLine;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.block.Block;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.input.Mouse;

@SideOnly(Side.CLIENT)
public class GuiTerminal extends GuiScreen {
    private static final int KEY_TAB = 15;
    private static final int KEY_PGUP = 201;
    private static final int KEY_PGDN = 209;
    private static final int VISIBLE_LINES = 18;

    /** sentinel host position: the host is the held laptop, not a block */
    public static final BlockPos LAPTOP_HOST = new BlockPos(0, Integer.MIN_VALUE, 0);

    private final BlockPos hostPos;
    private final List<CmdLine> buffer = new ArrayList<>();
    private String input = "";
    private int historyIndex = 0;
    private final List<String> history = new ArrayList<>();
    /** input text kept before the token currently being Tab-completed */
    private String completionHead = "";
    /** how many lines the view is scrolled up from the bottom of the buffer */
    private int scrollLines;
    /** cached count of adjacent screen blocks (matrix decoration scaling) */
    private int screenCells = -1;
    /** temperature samples shown by the last 'graph' command */
    private double[] graphTemps;
    private int graphSampleCount;
    private long graphUntilMs;

    public GuiTerminal(BlockPos pos) {
        this.hostPos = pos;
        if (LAPTOP_HOST.equals(pos)) {
            this.buffer.add(new CmdLine(MessageTerminalCommand.C_OUT, "RBMK Portable Terminal [v14.0]"));
            this.buffer.add(new CmdLine(MessageTerminalCommand.C_OUT, "Linked console: shown by 'where'. Type 'help' for commands."));
        } else {
            this.buffer.add(new CmdLine(MessageTerminalCommand.C_OUT, "RBMK Reactor Terminal [v14.0]"));
            this.buffer.add(new CmdLine(MessageTerminalCommand.C_OUT, "Type 'help' for commands. 'history'/'graph' replay the black box."));
        }
        if (!RbmkConfig.enableHackerAttack) {
            this.buffer.add(new CmdLine(MessageTerminalCommand.C_ERR, "[SECURITY] 服务器已禁用黑客攻击。"));
        } else if (RbmkConfig.allowNormalPlayerHack) {
            this.buffer.add(new CmdLine(MessageTerminalCommand.C_WARN, "[SECURITY] 全员黑客模式已开启。"));
        } else {
            this.buffer.add(new CmdLine(MessageTerminalCommand.C_OK, "[SECURITY] 反黑客保护已启用 (仅限OP)。"));
        }
    }

    private boolean isLaptop() {
        return LAPTOP_HOST.equals(this.hostPos);
    }

    // server reply -> output lines
    public void onServerReply(List<CmdLine> lines) {
        if (lines != null) {
            this.pushLines(lines);
        }
    }

    private void pushLines(List<CmdLine> lines) {
        for (int i = 0; i < lines.size(); ++i) {
            this.buffer.add(lines.get(i));
        }
        // new output snaps the view back to the bottom, like a real terminal
        this.scrollLines = 0;
        // the laptop has no wall tile to mirror onto
        if (this.isLaptop()) {
            return;
        }
        // mirror every rendered line onto the 4x3 wall display tile so the
        // big screen shows the terminal output live (TileRbmkTermScreen)
        TileEntity te = this.mc != null && this.mc.world != null
                ? this.mc.world.getTileEntity(this.hostPos) : null;
        if (te instanceof TileRbmkTermScreen) {
            TileRbmkTermScreen tile = (TileRbmkTermScreen) te;
            for (int i = 0; i < lines.size(); ++i) {
                tile.pushTermLine(lines.get(i));
            }
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        int cells = this.screenCellCount();
        boolean matrix = cells > 1;
        if (matrix) {
            // adjacent RBMK screens found: drop the vanilla backdrop and treat
            // the whole wall as one canvas, so all text can span multiple blocks
            drawRect(0, 0, this.width, this.height, 0xE6000000);
        } else {
            this.drawDefaultBackground();
        }
        // decorative matrix/radar backdrop; scales with the number of screens
        // placed next to the terminal host
        this.drawMatrixDecoration();
        if (matrix) {
            this.drawGiantRadar(cells);
        }
        int x = 10;
        int y = 10;
        int start = Math.max(0, this.buffer.size() - VISIBLE_LINES - this.scrollLines);
        int end = Math.max(0, this.buffer.size() - this.scrollLines);
        for (int i = start; i < end; i++) {
            this.fontRenderer.drawString(this.buffer.get(i).text, x, y + (i - start) * 10, this.buffer.get(i).color);
        }
        // blinking cursor, 500ms on / 500ms off
        String cursor = System.currentTimeMillis() / 500L % 2L == 0L ? "_" : " ";
        this.fontRenderer.drawString("C:\\RBMK> " + this.input + cursor, x, this.height - 20, 0x16C60C);
        if (this.scrollLines > 0) {
            this.fontRenderer.drawString("... " + (end - start) + "/" + this.buffer.size()
                    + " lines (scroll to bottom)", x, this.height - 32, 0xFF767676);
        }
        // 'graph' overlay: real polyline drawn with drawRect
        this.drawGraphOverlay();
        this.drawMeltdownWarning();
    }

    /** Flashing red T-minus banner when the bound reactor is under 60s from meltdown. */
    private void drawMeltdownWarning() {
        TileRbmkMonitorScreen monitor = this.findHistorySource();
        if (monitor == null) {
            return;
        }
        double eta = RbmkBridge.meltdownEta(monitor.getData());
        if (eta <= 0.0 || eta >= 60.0) {
            return;
        }
        // ~4 Hz blink so it is impossible to miss but still readable
        if (System.currentTimeMillis() / 250L % 2L != 0L) {
            return;
        }
        String warning = String.format(Locale.ROOT,
                "[CRITICAL WARNING] MELTDOWN IN %.1f SECONDS!", Double.valueOf(eta));
        int tw = this.fontRenderer.getStringWidth(warning);
        int wx = Math.max(2, (this.width - tw) / 2);
        int wy = this.height - 12;
        drawRect(wx - 3, wy - 2, wx + tw + 3, wy + 10, 0xCC000000);
        this.fontRenderer.drawString(warning, wx, wy, 0xFF0000);
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel == 0) {
            return;
        }
        // one notch (>= 120 or <= -120) scrolls one line
        int steps = wheel > 0 ? 1 : -1;
        this.scrollLines = Math.max(0, Math.min(this.buffer.size() - 1, this.scrollLines + steps));
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == 1) { // ESC
            this.mc.displayGuiScreen(null);
            return;
        }
        if (keyCode == 28) { // Enter
            this.submitInput();
            return;
        }
        if (keyCode == 14) { // Backspace
            if (this.input.length() > 0) {
                this.input = this.input.substring(0, this.input.length() - 1);
            }
            return;
        }
        if (keyCode == 200 && !this.history.isEmpty()) { // Up
            if (this.historyIndex > 0) {
                this.historyIndex--;
                this.input = this.history.get(this.historyIndex);
            }
            return;
        }
        if (keyCode == 208 && !this.history.isEmpty()) { // Down
            if (this.historyIndex < this.history.size() - 1) {
                this.historyIndex++;
                this.input = this.history.get(this.historyIndex);
            } else {
                this.historyIndex = this.history.size();
                this.input = "";
            }
            return;
        }
        if (keyCode == KEY_TAB) { // Tab completion
            this.completeInput();
            return;
        }
        if (keyCode == KEY_PGUP) { // Page Up: scroll back one page
            this.scrollLines = Math.min(this.buffer.size() - 1, this.scrollLines + VISIBLE_LINES);
            return;
        }
        if (keyCode == KEY_PGDN) { // Page Down: scroll forward one page
            this.scrollLines = Math.max(0, this.scrollLines - VISIBLE_LINES);
            return;
        }
        if (typedChar >= 32 && typedChar != 127 && this.input.length() < 60) {
            this.input += typedChar;
        }
    }

    private void submitInput() {
        String raw = this.input;
        String cmd = raw.trim().toLowerCase(Locale.ROOT);
        if (cmd.isEmpty()) {
            return;
        }
        // local commands, never sent to the server
        if ("cls".equals(cmd)) {
            List<CmdLine> echo = new ArrayList<>();
            echo.add(new CmdLine(MessageTerminalCommand.C_OUT, "C:\\RBMK> " + raw));
            this.pushLines(echo);
            this.buffer.clear();
            if (!this.isLaptop()) {
                TileEntity te = this.mc != null && this.mc.world != null
                        ? this.mc.world.getTileEntity(this.hostPos) : null;
                if (te instanceof TileRbmkTermScreen) {
                    ((TileRbmkTermScreen) te).clearTerm();
                }
            }
            this.input = "";
            return;
        }
        if ("exit".equals(cmd) || "quit".equals(cmd)) {
            this.mc.displayGuiScreen(null);
            return;
        }
        if ("history".equals(cmd)) {
            this.runHistoryCommand(raw);
            this.history.add(raw);
            this.historyIndex = this.history.size();
            this.input = "";
            return;
        }
        if ("graph".equals(cmd)) {
            this.runGraphCommand(raw);
            this.history.add(raw);
            this.historyIndex = this.history.size();
            this.input = "";
            return;
        }
        if ("scram off".equals(cmd) || "scram on".equals(cmd)) {
            this.toggleAutoScram(cmd.endsWith("off"), raw);
            this.history.add(raw);
            this.historyIndex = this.history.size();
            this.input = "";
            return;
        }
        if ("hack".equals(cmd) || cmd.startsWith("hack ") || "fix".equals(cmd)) {
            boolean hacked = !"fix".equals(cmd);
            String target = "";
            if (hacked) {
                String trimmed = raw.trim();
                if (trimmed.length() > 4) {
                    target = trimmed.substring(4).trim();
                }
            }
            this.toggleHack(hacked, raw, target);
            this.history.add(raw);
            this.historyIndex = this.history.size();
            this.input = "";
            return;
        }
        if ("list".equals(cmd)) {
            this.runListCommand(raw);
            this.history.add(raw);
            this.historyIndex = this.history.size();
            this.input = "";
            return;
        }
        if (cmd.startsWith("connect ")) {
            this.runConnectCommand(raw);
            this.history.add(raw);
            this.historyIndex = this.history.size();
            this.input = "";
            return;
        }
        List<CmdLine> echo = new ArrayList<>();
        echo.add(new CmdLine(MessageTerminalCommand.C_PROMPT, "C:\\RBMK> " + raw));
        this.pushLines(echo);
        if ("help".equals(cmd)) {
            List<CmdLine> extra = new ArrayList<>();
            extra.add(new CmdLine(MessageTerminalCommand.C_DIM, "  hack                   入侵当前绑定屏幕（近距离）"));
            extra.add(new CmdLine(MessageTerminalCommand.C_DIM, "  hack <玩家ID>          远程入侵该玩家 64 格内最近的 RBMK 屏幕"));
            extra.add(new CmdLine(MessageTerminalCommand.C_DIM, "  fix                    修复当前绑定屏幕的入侵状态"));
            extra.add(new CmdLine(MessageTerminalCommand.C_DIM, "  list                   列出周围 RBMK 路由器连接的屏幕/控制台"));
            extra.add(new CmdLine(MessageTerminalCommand.C_DIM, "  connect <维度> <X> <Y> <Z>  跨维度绑定目标反应堆"));
            this.pushLines(extra);
        }
        if (this.isLaptop()) {
            // handheld mode: the server resolves the console from the held item
            ModNetwork.CHANNEL.sendToServer(MessageTerminalCommand.laptop(raw));
        } else {
            ModNetwork.CHANNEL.sendToServer(new MessageTerminalCommand(this.hostPos, raw));
        }
        this.history.add(raw);
        this.historyIndex = this.history.size();
        this.input = "";
    }

    /**
     * Locate the monitor-screen black box that belongs to this terminal: the
     * host itself when it is a monitor/terminal screen, otherwise the nearest
     * monitor screen within 24 blocks.
     */
    private TileRbmkMonitorScreen findHistorySource() {
        if (this.isLaptop() || this.mc == null || this.mc.world == null) {
            return null;
        }
        TileEntity host = this.mc.world.getTileEntity(this.hostPos);
        TileRbmkMonitorScreen fallback = null;
        if (host instanceof TileRbmkMonitorScreen) {
            fallback = (TileRbmkMonitorScreen) host;
            // a terminal screen that is not bound has an empty black box; in
            // that case keep looking for a nearby bound monitor screen
            if (fallback.getHistoryCount() > 0) {
                return fallback;
            }
        }
        for (int r = 1; r <= 24; ++r) {
            for (int dy = -3; dy <= 3; ++dy) {
                for (int dz = -r; dz <= r; ++dz) {
                    for (int dx = -r; dx <= r; ++dx) {
                        if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
                            continue;
                        }
                        TileEntity te = this.mc.world.getTileEntity(new BlockPos(
                                this.hostPos.getX() + dx, this.hostPos.getY() + dy, this.hostPos.getZ() + dz));
                        if (te instanceof TileRbmkMonitorScreen) {
                            return (TileRbmkMonitorScreen) te;
                        }
                    }
                }
            }
        }
        return fallback;
    }

    /** 'history': dump the 60-tick black box as plain text, then typed commands. */
    private void runHistoryCommand(String raw) {
        List<CmdLine> out = new ArrayList<>();
        out.add(new CmdLine(MessageTerminalCommand.C_PROMPT, "C:\\RBMK> " + raw));
        TileRbmkMonitorScreen monitor = this.findHistorySource();
        if (monitor == null) {
            out.add(new CmdLine(MessageTerminalCommand.C_DIM, "  (no RBMK monitor black box within 24 blocks)"));
        } else {
            int count = monitor.getHistoryCount();
            out.add(new CmdLine(MessageTerminalCommand.C_DIM, "  --- black box: " + count + "/"
                    + TileRbmkMonitorScreen.HISTORY_SIZE + " samples (oldest -> newest) ---"));
            if (count == 0) {
                out.add(new CmdLine(MessageTerminalCommand.C_DIM, "  (empty - wait for the next reactor refresh)"));
            }
            int from = Math.max(0, count - 20);
            for (int i = from; i < count; ++i) {
                out.add(new CmdLine(MessageTerminalCommand.C_OUT, String.format(Locale.ROOT,
                        "  #%02d  %8.1f C   flux %6d F",
                        Integer.valueOf(count - i),
                        Double.valueOf(monitor.getHistoryHeat(i)),
                        Integer.valueOf(monitor.getHistoryFlux(i)))));
            }
        }
        out.add(new CmdLine(MessageTerminalCommand.C_DIM, "  --- typed commands (this session) ---"));
        if (this.history.isEmpty()) {
            out.add(new CmdLine(MessageTerminalCommand.C_DIM, "  (none yet)"));
        } else {
            int from = Math.max(0, this.history.size() - 20);
            for (int i = from; i < this.history.size(); ++i) {
                out.add(new CmdLine(MessageTerminalCommand.C_DIM,
                        "  " + String.format(Locale.ROOT, "%3d", Integer.valueOf(i + 1)) + "  " + this.history.get(i)));
            }
        }
        this.pushLines(out);
    }

    /** 'graph': ASCII bar chart in the buffer plus a drawRect polyline overlay. */
    private void runGraphCommand(String raw) {
        List<CmdLine> out = new ArrayList<>();
        out.add(new CmdLine(MessageTerminalCommand.C_PROMPT, "C:\\RBMK> " + raw));
        TileRbmkMonitorScreen monitor = this.findHistorySource();
        int count = monitor == null ? 0 : monitor.getHistoryCount();
        if (monitor == null) {
            out.add(new CmdLine(MessageTerminalCommand.C_DIM, "  (no RBMK monitor black box within 24 blocks)"));
            this.graphUntilMs = 0L;
        } else if (count < 2) {
            out.add(new CmdLine(MessageTerminalCommand.C_DIM, "  (not enough temperature history yet - wait a few refreshes)"));
            this.graphUntilMs = 0L;
        } else {
            int cols = Math.min(56, count);
            int first = count - cols;
            double[] vals = new double[cols];
            double min = Double.MAX_VALUE;
            double max = -Double.MAX_VALUE;
            for (int i = 0; i < cols; ++i) {
                vals[i] = monitor.getHistoryHeat(first + i);
                min = Math.min(min, vals[i]);
                max = Math.max(max, vals[i]);
            }
            int rows = 10;
            double span = Math.max(1.0D, max - min);
            char[][] grid = new char[rows][cols];
            for (int r = 0; r < rows; ++r) {
                for (int c = 0; c < cols; ++c) {
                    grid[r][c] = ' ';
                }
            }
            for (int c = 0; c < cols; ++c) {
                int level = (int) Math.round((vals[c] - min) / span * (double) (rows - 1));
                for (int r = rows - 1; r >= rows - 1 - level; --r) {
                    grid[r][c] = r == rows - 1 - level ? '*' : '#';
                }
            }
            out.add(new CmdLine(MessageTerminalCommand.C_OUT, String.format(Locale.ROOT,
                    "  TEMP GRAPH  min %.1f C  max %.1f C  (%d samples)",
                    Double.valueOf(min), Double.valueOf(max), Integer.valueOf(cols))));
            for (int r = 0; r < rows; ++r) {
                out.add(new CmdLine(MessageTerminalCommand.C_OUT,
                        "  |" + new String(grid[r]) + "|"));
            }
            out.add(new CmdLine(MessageTerminalCommand.C_DIM, "  +" + repeat('-', cols) + "+"));
            out.add(new CmdLine(MessageTerminalCommand.C_DIM, "   time ->  newest sample at the right edge"));
            this.graphTemps = vals;
            this.graphSampleCount = cols;
            this.graphUntilMs = System.currentTimeMillis() + 20000L;
        }
        this.pushLines(out);
    }

    private static String repeat(char c, int times) {
        StringBuilder sb = new StringBuilder(times);
        for (int i = 0; i < times; ++i) {
            sb.append(c);
        }
        return sb.toString();
    }

    /** 'scram off' / 'scram on': toggle the bound screen's auto-SCRAM override. */
    private void toggleAutoScram(boolean disabled, String raw) {
        List<CmdLine> out = new ArrayList<>();
        out.add(new CmdLine(MessageTerminalCommand.C_PROMPT, "C:\\RBMK> " + raw));
        TileRbmkMonitorScreen monitor = this.findHistorySource();
        if (this.isLaptop() || monitor == null) {
            out.add(new CmdLine(MessageTerminalCommand.C_ERR,
                    "  (no bound RBMK screen found - open the terminal from a screen block)"));
            this.pushLines(out);
            return;
        }
        ModNetwork.CHANNEL.sendToServer(
                new TileRbmkMonitorScreen.AutoScramToggleMessage(monitor.getPos(), disabled));
        if (disabled) {
            out.add(new CmdLine(MessageTerminalCommand.C_ERR,
                    "[SYSTEM] 自动停堆保护已临时屏蔽！反应堆可能随时熔毁！"));
        } else {
            out.add(new CmdLine(MessageTerminalCommand.C_OK,
                    "[SYSTEM] 自动停堆保护已恢复。"));
        }
        this.pushLines(out);
    }

    /** Faint ASCII radar drawn behind the terminal text. */
    private void drawMatrixDecoration() {
        int level = Math.max(1, Math.min(3, this.screenCellCount()));
        int bx = 6;
        int by = 6;
        int panelW = 158 + (level - 1) * 26;
        drawRect(bx, by, bx + panelW, by + 62, 0x33001408);
        drawRect(bx, by, bx + panelW, by + 1, 0x332E7D32);
        String[] art = new String[]{
                "        .-''''''-.",
                "     .'  .-''-.  '.",
                "    /   /  .-.  \\   \\",
                "   |   |  ( + )  |   |",
                "    \\   \\  '-'  /   /",
                "     '.  '-..-'  .'",
                "        '-.....-'"
        };
        for (int i = 0; i < art.length; ++i) {
            this.fontRenderer.drawString(art[i], bx + 3 + (level - 1) * 4, by + 3 + i * 8, 0x1F39FF14);
        }
        this.fontRenderer.drawString("RBMK MONITOR MATRIX  x" + level, bx + 3, by + 52, 0x2E2E7D32);
    }

    private int screenCellCount() {
        if (this.screenCells < 0) {
            this.screenCells = this.countAdjacentScreens();
        }
        return this.screenCells;
    }

    /** Large ASCII radar shown when the terminal spans a screen matrix. */
    private void drawGiantRadar(int level) {
        String[] art = new String[]{
                "                    .-''''''''''''''''''''''''''''''''-.                    ",
                "              .-'   .-''''''''''''''''''''''''''''-.   '-.              ",
                "          .-'        /                                  \\        '-.          ",
                "        /           /          .-.          .-.          \\           \\        ",
                "       |           |          /   \\        /   \\          |           |       ",
                "       |           |         |  +  |      |  R  |         |           |       ",
                "       |           |          \\   /        \\   /          |           |       ",
                "        \\           \\          '-'          '-'          /           /        ",
                "          '-.        \\                                  /        .-'          ",
                "              '-.     '-............................-'     .-'              ",
                "                    '-...............................-'                    "
        };
        int maxW = 0;
        for (int i = 0; i < art.length; ++i) {
            maxW = Math.max(maxW, art[i].length());
        }
        int bx = Math.max(4, (this.width - maxW * 6) / 2);
        int by = 2;
        for (int i = 0; i < art.length; ++i) {
            this.fontRenderer.drawString(art[i], bx, by + i * 8, 0x2239FF14);
        }
        String title = "RBMK TOTAL CONTROL MATRIX  //  " + level + " SCREENS LINKED";
        int tw = this.fontRenderer.getStringWidth(title);
        this.fontRenderer.drawString(title, Math.max(2, (this.width - tw) / 2),
                by + art.length * 8 + 2, 0x332E7D32);
    }

    /** 'list': print every RBMK router near the terminal and what it links. */
    private void runListCommand(String raw) {
        List<CmdLine> out = new ArrayList<>();
        out.add(new CmdLine(MessageTerminalCommand.C_PROMPT, "C:\\RBMK> " + raw));
        if (this.mc == null || this.mc.world == null || this.isLaptop()) {
            out.add(new CmdLine(MessageTerminalCommand.C_ERR,
                    "  (no world context - open the terminal from a placed block)"));
            this.pushLines(out);
            return;
        }
        List<BlockPos> routers = new ArrayList<>();
        for (int dx = -16; dx <= 16; ++dx) {
            for (int dy = -8; dy <= 8; ++dy) {
                for (int dz = -16; dz <= 16; ++dz) {
                    BlockPos p = this.hostPos.add(dx, dy, dz);
                    if (this.mc.world.getBlockState(p).getBlock() instanceof BlockRbmkRouter) {
                        routers.add(p);
                    }
                }
            }
        }
        if (routers.isEmpty()) {
            out.add(new CmdLine(MessageTerminalCommand.C_WARN,
                    "  (未找到 RBMK 路由器，请在终端 16 格内放置 rbmk_router)"));
            this.pushLines(out);
            return;
        }
        int shown = 0;
        for (BlockPos router : routers) {
            if (shown++ >= 8) {
                break;
            }
            out.add(new CmdLine(MessageTerminalCommand.C_OUT, "  [路由器] "
                    + router.getX() + ", " + router.getY() + ", " + router.getZ()));
            int lines = 0;
            for (int dx = -8; dx <= 8 && lines < 8; ++dx) {
                for (int dy = -6; dy <= 6 && lines < 8; ++dy) {
                    for (int dz = -8; dz <= 8 && lines < 8; ++dz) {
                        BlockPos p = router.add(dx, dy, dz);
                        TileEntity te = this.mc.world.getTileEntity(p);
                        if (te instanceof TileRbmkMonitorScreen) {
                            TileRbmkMonitorScreen screen = (TileRbmkMonitorScreen) te;
                            out.add(new CmdLine(MessageTerminalCommand.C_DIM,
                                    "    屏幕 @ (" + p.getX() + ", " + p.getY() + ", " + p.getZ() + ") "
                                            + (screen.getData().linked ? "[已绑定]" : "[未绑定]")));
                            ++lines;
                        } else if (RbmkBridge.isConsole(te)) {
                            out.add(new CmdLine(MessageTerminalCommand.C_DIM,
                                    "    控制台 @ (" + p.getX() + ", " + p.getY() + ", " + p.getZ() + ")"));
                            ++lines;
                        }
                    }
                }
            }
            if (lines == 0) {
                out.add(new CmdLine(MessageTerminalCommand.C_DIM, "    (8 格内没有屏幕或控制台)"));
            }
        }
        this.pushLines(out);
    }

    /** 'connect <dim> <x> <y> <z>': cross-dimension bind via the server. */
    private void runConnectCommand(String raw) {
        List<CmdLine> out = new ArrayList<>();
        out.add(new CmdLine(MessageTerminalCommand.C_PROMPT, "C:\\RBMK> " + raw));
        String[] parts = raw.trim().split("\\s+");
        if (parts.length != 5) {
            out.add(new CmdLine(MessageTerminalCommand.C_ERR, "  用法: connect <维度ID> <X> <Y> <Z>"));
            this.pushLines(out);
            return;
        }
        int dim;
        int x;
        int y;
        int z;
        try {
            dim = Integer.parseInt(parts[1]);
            x = Integer.parseInt(parts[2]);
            y = Integer.parseInt(parts[3]);
            z = Integer.parseInt(parts[4]);
        }
        catch (NumberFormatException e) {
            out.add(new CmdLine(MessageTerminalCommand.C_ERR, "  维度与坐标必须是整数。"));
            this.pushLines(out);
            return;
        }
        TileRbmkMonitorScreen monitor = this.findHistorySource();
        if (monitor == null) {
            out.add(new CmdLine(MessageTerminalCommand.C_ERR,
                    "  (no bound RBMK screen found - open the terminal from a screen block)"));
            this.pushLines(out);
            return;
        }
        ModNetwork.CHANNEL.sendToServer(
                new TileRbmkMonitorScreen.ConnectMessage(monitor.getPos(), dim, new BlockPos(x, y, z)));
        out.add(new CmdLine(MessageTerminalCommand.C_WARN,
                "[SYSTEM] 跨维度连接请求已发送：维度 " + dim + " @ (" + x + ", " + y + ", " + z + ")"));
        this.pushLines(out);
    }

    /** 'hack [player]' / 'fix': enable or clear the data-spoofing hack. */
    private void toggleHack(boolean hacked, String raw, String targetName) {
        List<CmdLine> out = new ArrayList<>();
        out.add(new CmdLine(MessageTerminalCommand.C_PROMPT, "C:\\RBMK> " + raw));
        boolean remote = targetName != null && !targetName.isEmpty();
        TileRbmkMonitorScreen monitor = this.findHistorySource();
        if (!remote && (this.isLaptop() || monitor == null)) {
            out.add(new CmdLine(MessageTerminalCommand.C_ERR,
                    "  (no bound RBMK screen found - open the terminal from a screen block)"));
            this.pushLines(out);
            return;
        }
        BlockPos packetPos;
        if (remote) {
            packetPos = this.hostPos;
            if (targetName.length() > 64) {
                targetName = targetName.substring(0, 64);
            }
        } else {
            packetPos = monitor.getPos();
        }
        ModNetwork.CHANNEL.sendToServer(
                new TileRbmkMonitorScreen.HackToggleMessage(packetPos, hacked, remote ? targetName : ""));
        if (hacked) {
            if (remote) {
                out.add(new CmdLine(MessageTerminalCommand.C_WARN,
                        "[SYSTEM] 网络攻击已发送，目标：" + targetName + "（等待服务端结果）"));
            } else {
                out.add(new CmdLine(MessageTerminalCommand.C_ERR,
                        "[SYSTEM] 入侵成功：数据链路已接管，读数将被随机篡改！"));
            }
        } else {
            out.add(new CmdLine(MessageTerminalCommand.C_OK,
                    "[SYSTEM] 修复完成：数据完整性已恢复。"));
        }
        this.pushLines(out);
    }

    /** Count display blocks placed next to the terminal host. */
    private int countAdjacentScreens() {
        if (this.mc == null || this.mc.world == null || this.isLaptop()) {
            return 1;
        }
        int count = 1;
        for (int dy = -2; dy <= 2; ++dy) {
            for (int dz = -3; dz <= 3; ++dz) {
                for (int dx = -3; dx <= 3; ++dx) {
                    if (dx == 0 && dy == 0 && dz == 0) {
                        continue;
                    }
                    Block b = this.mc.world.getBlockState(new BlockPos(
                            this.hostPos.getX() + dx, this.hostPos.getY() + dy, this.hostPos.getZ() + dz)).getBlock();
                    if (b instanceof BlockRbmkScreen || b instanceof BlockRbmkMonitor || b instanceof BlockRbmkGraph
                            || b instanceof BlockRbmkCustomScreen || b instanceof BlockRbmkScreenPart) {
                        ++count;
                    }
                }
            }
        }
        return count;
    }

    /** Polyline of the last 'graph' command, drawn directly with drawRect. */
    private void drawGraphOverlay() {
        if (this.graphTemps == null || this.graphSampleCount < 2
                || System.currentTimeMillis() > this.graphUntilMs) {
            return;
        }
        int w = 170;
        int h = 96;
        int gx = Math.max(8, this.width - w - 12);
        int gy = Math.max(8, this.height - h - 46);
        drawRect(gx - 3, gy - 3, gx + w + 3, gy + h + 3, 0xE0101810);
        drawRect(gx, gy, gx + w, gy + h, 0xFF05140A);
        drawRect(gx, gy, gx + 1, gy + h, 0xFF2E7D32);
        drawRect(gx, gy + h - 1, gx + w, gy + h, 0xFF2E7D32);
        double min = Double.MAX_VALUE;
        double max = -Double.MAX_VALUE;
        for (int i = 0; i < this.graphSampleCount; ++i) {
            min = Math.min(min, this.graphTemps[i]);
            max = Math.max(max, this.graphTemps[i]);
        }
        double span = Math.max(1.0D, max - min);
        int prevY = -1;
        for (int x = gx + 2; x < gx + w - 2; ++x) {
            double pos = (double) (x - (gx + 2)) / (double) Math.max(1, w - 4)
                    * (double) (this.graphSampleCount - 1);
            int i0 = (int) pos;
            int i1 = Math.min(this.graphSampleCount - 1, i0 + 1);
            double f = pos - (double) i0;
            double v = this.graphTemps[i0] * (1.0D - f) + this.graphTemps[i1] * f;
            int yy = gy + h - 3 - (int) ((v - min) / span * (double) (h - 8));
            drawRect(x, yy, x + 1, yy + 1, 0xFF39FF14);
            drawRect(x, yy + 1, x + 1, yy + 2, 0x5539FF14);
            if (prevY >= 0 && Math.abs(prevY - yy) > 1) {
                drawRect(x, Math.min(prevY, yy), x + 1, Math.max(prevY, yy) + 1, 0xAA39FF14);
            }
            prevY = yy;
        }
        this.fontRenderer.drawString("TEMP HISTORY", gx + 4, gy + 3, 0xFF8BC34A);
        this.fontRenderer.drawString(String.format(Locale.ROOT, "%.0f C", Double.valueOf(max)), gx + 4, gy + 13, 0xFF669933);
        this.fontRenderer.drawString(String.format(Locale.ROOT, "%.0f C", Double.valueOf(min)), gx + 4, gy + h - 11, 0xFF669933);
        this.fontRenderer.drawString("time ->", gx + w - 44, gy + h - 11, 0xFF33691E);
    }

    /**
     * Tab completion: command name (first token), rod color (third token of
     * 'rods <0-100> <color>') and siren action (second token of
     * 'siren <on|off|test>').
     */
    private void completeInput() {
        String trimmed = this.input.trim();
        String[] tokens = trimmed.isEmpty() ? new String[0] : trimmed.split("\\s+");
        boolean trailing = this.input.endsWith(" ");
        int idx = trimmed.isEmpty() ? 0 : (trailing ? tokens.length : tokens.length - 1);
        if (idx == 0) {
            this.completionHead = "";
            this.completeToken(MessageTerminalCommand.COMMANDS, trailing || tokens.length == 0 ? "" : tokens[0]);
        } else if (idx == 1 && tokens.length >= 1 && "siren".equalsIgnoreCase(tokens[0])) {
            this.completionHead = tokens[0] + " ";
            this.completeToken(MessageTerminalCommand.SIREN_ACTIONS, trailing ? "" : tokens[1]);
        } else if (idx == 2 && tokens.length >= 2 && "rods".equalsIgnoreCase(tokens[0])) {
            this.completionHead = tokens[0] + " " + tokens[1] + " ";
            this.completeToken(MessageTerminalCommand.ROD_COLORS, trailing ? "" : tokens[2]);
        }
    }

    private void completeToken(String[] candidates, String prefix) {
        String lowerPrefix = prefix.toLowerCase(Locale.ROOT);
        List<String> matches = new ArrayList<>();
        for (int i = 0; i < candidates.length; ++i) {
            if (candidates[i].toLowerCase(Locale.ROOT).startsWith(lowerPrefix)) {
                matches.add(candidates[i]);
            }
        }
        if (matches.isEmpty()) {
            return;
        }
        if (matches.size() == 1) {
            this.input = this.completionHead + matches.get(0) + " ";
            return;
        }
        // several matches: extend to the longest common prefix
        String common = matches.get(0);
        for (int i = 1; i < matches.size(); ++i) {
            String lower = matches.get(i).toLowerCase(Locale.ROOT);
            while (!lower.startsWith(common.toLowerCase(Locale.ROOT))) {
                common = common.substring(0, common.length() - 1);
            }
        }
        if (common.length() > prefix.length()) {
            this.input = this.completionHead + common;
            return;
        }
        // nothing to extend: list the candidates like a real terminal
        List<CmdLine> out = new ArrayList<>();
        out.add(new CmdLine(MessageTerminalCommand.C_PROMPT, "C:\\RBMK> " + this.input));
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < matches.size(); ++i) {
            if (sb.length() > 0) {
                sb.append("   ");
            }
            sb.append(matches.get(i));
        }
        out.add(new CmdLine(MessageTerminalCommand.C_DIM, "  " + sb));
        this.pushLines(out);
    }
}
