package com.jiezhirbmkmod.client.gui;

import com.jiezhirbmkmod.block.BlockRbmkCustomScreen;
import com.jiezhirbmkmod.block.BlockRbmkGraph;
import com.jiezhirbmkmod.block.BlockRbmkMonitor;
import com.jiezhirbmkmod.block.BlockRbmkRouter;
import com.jiezhirbmkmod.block.BlockRbmkScreen;
import com.jiezhirbmkmod.block.BlockRbmkScreenPart;
import com.jiezhirbmkmod.block.TileRbmkMonitorScreen;
import com.jiezhirbmkmod.block.TileRbmkTermScreen;
import com.jiezhirbmkmod.client.ai.AiDiagnoseDispatcher;
import com.jiezhirbmkmod.dfc.RbmkBridge;
import com.jiezhirbmkmod.dfc.RbmkConfig;
import com.jiezhirbmkmod.dfc.RbmkSnapshot;
import com.jiezhirbmkmod.network.ModNetwork;
import com.jiezhirbmkmod.network.msg.MessageRbmkTerminalComm;
import com.jiezhirbmkmod.network.msg.MessageRbmkPptControl;
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
    /** cached warning data source; the 24-block fallback scan is too costly per frame */
    private TileRbmkMonitorScreen warningSource;
    private long warningSourceCheckMs;

    /** v15.0 anti-grief lock state of the bound screen, learned from server replies */
    private boolean screenLocked;
    private boolean screenAuthed;

    /** v15.2 media status shown at the bottom-left corner */
    private String mediaStatus = "";
    private String mediaFileName = "";

    // ---- v18.0 pipeline filter / macro ----

    /** one 'filter <field> <op> <value>' stage of a pipeline */
    private static final class FilterStage {
        final String field;
        final String op;
        final double value;
        FilterStage(String field, String op, double value) {
            this.field = field;
            this.op = op;
            this.value = value;
        }
        boolean passes(String line) {
            String low = line.toLowerCase(java.util.Locale.ROOT);
            int idx = low.indexOf(this.field);
            if (idx < 0) {
                return false;   // 没有目标字段词的行直接滤掉
            }
            java.util.regex.Matcher m = java.util.regex.Pattern.compile(
                    "-?[0-9]+(?:\\.[0-9]+)?").matcher(line.substring(idx));
            if (!m.find()) {
                return false;
            }
            double v = Double.parseDouble(m.group());
            if (">".equals(this.op)) {
                return v > this.value;
            }
            if ("<".equals(this.op)) {
                return v < this.value;
            }
            if (">=".equals(this.op)) {
                return v >= this.value;
            }
            if ("<=".equals(this.op)) {
                return v <= this.value;
            }
            return v == this.value; // '=' / '=='
        }
    }

    /** active pipeline filter stages; null = passthrough */
    private List<FilterStage> pipelineStages;
    /** filters self-expire so a lost server reply cannot swallow later output */
    private long pipelineExpireMs;
    /** macro name -> sub commands (session-local, like a real shell) */
    private final java.util.Map<String, List<String>> macros =
            new java.util.LinkedHashMap<String, List<String>>();
    /** guards nested macro execution */
    private int macroDepth;

    /** 把 'heat'/'temp'/'temperature' 等口语字段名映射到输出行里的实际词。 */
    private static String filterFieldAlias(String raw) {
        String f = raw.toLowerCase(java.util.Locale.ROOT).trim();
        if ("heat".equals(f) || "temp".equals(f) || "temperature".equals(f) || "温度".equals(f)) {
            return "temp";
        }
        return f;
    }

    public GuiTerminal(BlockPos pos) {
        this.hostPos = pos;
        if (LAPTOP_HOST.equals(pos)) {
            this.buffer.add(new CmdLine(MessageTerminalCommand.C_OUT, "RBMK Portable Terminal [v18.0]"));
            this.buffer.add(new CmdLine(MessageTerminalCommand.C_OUT, "Linked console: shown by 'where'. Type 'help' for commands."));
        } else {
            this.buffer.add(new CmdLine(MessageTerminalCommand.C_OUT, "RBMK Reactor Terminal [v18.0]"));
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
        if (lines == null) {
            return;
        }
        // v15.0: the server tags its security replies so the client knows
        // whether 'hack' may be attempted on this screen
        for (int i = 0; i < lines.size(); ++i) {
            String text = lines.get(i).text;
            if (text == null) {
                continue;
            }
            if (text.contains("[LOCK-ON]")) {
                this.screenLocked = true;
                this.screenAuthed = true;
            } else if (text.contains("[LOCK-DENY]")) {
                this.screenLocked = true;
                this.screenAuthed = false;
            } else if (text.contains("[AUTH-OK]")) {
                this.screenAuthed = true;
            } else if (text.contains("[AUTH-FAIL]")) {
                this.screenAuthed = false;
            } else if (text.contains("[AUTH-REQ]")) {
                this.screenLocked = true;
                this.screenAuthed = false;
            }
        }
        this.pushLines(lines);
    }

    private void pushLines(List<CmdLine> lines) {
        // v18.0 管道过滤：回显行与错误/警告放行，其余按阶段逐条过筛；
        // 超时的过滤器自动失效（回包丢失不吞后续输出）
        if (this.pipelineStages != null) {
            if (System.currentTimeMillis() > this.pipelineExpireMs) {
                this.pipelineStages = null;
            } else {
                List<CmdLine> kept = new ArrayList<>(lines.size());
                for (int i = 0; i < lines.size(); ++i) {
                    CmdLine cl = lines.get(i);
                    String t = cl.text == null ? "" : cl.text;
                    if (cl.color == MessageTerminalCommand.C_PROMPT
                            || cl.color == MessageTerminalCommand.C_ERR
                            || cl.color == MessageTerminalCommand.C_WARN) {
                        kept.add(cl);
                        continue;
                    }
                    boolean ok = true;
                    for (int s = 0; s < this.pipelineStages.size() && ok; ++s) {
                        ok = this.pipelineStages.get(s).passes(t);
                    }
                    if (ok) {
                        kept.add(cl);
                    }
                }
                lines = kept;
                if (kept.isEmpty()) {
                    this.pipelineStages = null; // 本轮回包已结束，摘除过滤器
                }
            }
        }
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
        // v15.2: media playback state, bottom-left
        if (!this.mediaStatus.isEmpty()) {
            this.fontRenderer.drawString(this.mediaStatus, x, this.height - 44, 0xFFFF4040);
        }
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
        TileRbmkMonitorScreen monitor = this.warningSource();
        if (monitor == null) {
            return;
        }
        double eta = RbmkBridge.meltdownEta(monitor.getData());
        // defensive: never format/draw NaN or infinite values
        if (Double.isNaN(eta) || Double.isInfinite(eta) || eta <= 0.0 || eta >= 60.0) {
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

    /** Resolve the warning data source at most once per second. */
    private TileRbmkMonitorScreen warningSource() {
        long now = System.currentTimeMillis();
        if (now < this.warningSourceCheckMs
                && (this.warningSource == null || !this.warningSource.isInvalid())) {
            return this.warningSource;
        }
        this.warningSourceCheckMs = now + 1000L;
        this.warningSource = this.findHistorySource();
        return this.warningSource;
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

    /** v18.0：解析并执行一条带管道的输入。 */
    private void runPipeline(String raw) {
        String[] parts = raw.split("\\|");
        List<FilterStage> stages = new ArrayList<>();
        for (int i = 1; i < parts.length; ++i) {
            String seg = parts[i].trim();
            String[] tok = seg.split("\\s+");
            // 'filter' 关键字可省略：'| heat > 800' 与 '| filter heat > 800' 等价
            int o = (tok.length > 0 && "filter".equals(tok[0].toLowerCase(Locale.ROOT))) ? 1 : 0;
            if (tok.length - o < 3) {
                List<CmdLine> err = new ArrayList<>();
                err.add(new CmdLine(MessageTerminalCommand.C_ERR,
                        "  usage: <command> | filter <field> <op> <value>   (op: > < >= <= =)"));
                this.pushLines(err);
                return;
            }
            try {
                stages.add(new FilterStage(filterFieldAlias(tok[o]), tok[o + 1],
                        Double.parseDouble(tok[o + 2])));
            }
            catch (NumberFormatException e) {
                List<CmdLine> err = new ArrayList<>();
                err.add(new CmdLine(MessageTerminalCommand.C_ERR,
                        "  bad filter value: '" + tok[o + 2] + "'"));
                this.pushLines(err);
                return;
            }
        }
        List<CmdLine> echo = new ArrayList<>();
        echo.add(new CmdLine(MessageTerminalCommand.C_PROMPT, "C:\\RBMK> " + raw));
        this.pushLines(echo);
        // 先挂过滤器再执行第一段，回包行进 pushLines 时逐条过筛；
        // 10 秒后自动失效，防止回包丢失吞掉后续输出
        this.pipelineStages = stages;
        this.pipelineExpireMs = System.currentTimeMillis() + 10000L;
        this.input = parts[0].trim();
        this.submitInput();
    }

    /** v18.0：宏指令 set / run / list / del（会话内存，重启即清，像真 shell）。 */
    private void runMacroCommand(String raw) {
        String[] tok = raw.trim().split("\\s+", 3);
        List<CmdLine> out = new ArrayList<>();
        if (tok.length == 1 || "list".equals(tok[1])) {
            if (this.macros.isEmpty()) {
                out.add(new CmdLine(MessageTerminalCommand.C_DIM, "  no macros. usage: macro set <name> <cmd1>;<cmd2>"));
            } else {
                for (java.util.Map.Entry<String, List<String>> e : this.macros.entrySet()) {
                    out.add(new CmdLine(MessageTerminalCommand.C_OUT, "  " + e.getKey() + " = "
                            + String.join(";", e.getValue())));
                }
            }
        } else if ("set".equals(tok[1])) {
            if (tok.length < 3 || tok[2].trim().isEmpty()) {
                out.add(new CmdLine(MessageTerminalCommand.C_ERR, "  usage: macro set <name> <cmd1>;<cmd2>"));
            } else {
                String rest = tok[2].trim();
                String name = rest.split("\\s+", 2)[0].toLowerCase(Locale.ROOT);
                String body = rest.length() > name.length() ? rest.substring(name.length()).trim() : "";
                if (body.isEmpty()) {
                    out.add(new CmdLine(MessageTerminalCommand.C_ERR, "  usage: macro set <name> <cmd1>;<cmd2>"));
                } else {
                    List<String> cmds = new ArrayList<>();
                    for (String c : body.split(";")) {
                        if (!c.trim().isEmpty()) {
                            cmds.add(c.trim());
                        }
                    }
                    if (cmds.isEmpty()) {
                        out.add(new CmdLine(MessageTerminalCommand.C_ERR, "  macro body is empty"));
                    } else {
                        this.macros.put(name, cmds);
                        out.add(new CmdLine(MessageTerminalCommand.C_OK, "  macro '" + name
                                + "' saved (" + cmds.size() + " command(s)). run it: macro run " + name));
                    }
                }
            }
        } else if ("run".equals(tok[1]) && tok.length == 3) {
            List<String> cmds = this.macros.get(tok[2].trim().toLowerCase(Locale.ROOT));
            if (cmds == null) {
                out.add(new CmdLine(MessageTerminalCommand.C_ERR, "  no macro named '" + tok[2].trim() + "'"));
            } else if (this.macroDepth >= 3) {
                out.add(new CmdLine(MessageTerminalCommand.C_ERR, "  macro nesting too deep (a macro must not run itself)"));
            } else {
                out.add(new CmdLine(MessageTerminalCommand.C_DIM, "  running macro '" + tok[2].trim()
                        + "' (" + cmds.size() + " command(s))..."));
                this.pushLines(out);
                this.macroDepth++;
                try {
                    for (String c : cmds) {
                        this.input = c;
                        this.submitInput();
                    }
                }
                finally {
                    this.macroDepth--;
                }
                return; // 子命令已各自回显/入库，不再重复输出
            }
        } else if ("del".equals(tok[1]) && tok.length == 3) {
            if (this.macros.remove(tok[2].trim().toLowerCase(Locale.ROOT)) != null) {
                out.add(new CmdLine(MessageTerminalCommand.C_OK, "  macro '" + tok[2].trim() + "' deleted"));
            } else {
                out.add(new CmdLine(MessageTerminalCommand.C_ERR, "  no macro named '" + tok[2].trim() + "'"));
            }
        } else {
            out.add(new CmdLine(MessageTerminalCommand.C_ERR,
                    "  usage: macro set|run|list|del <name> [commands]"));
        }
        this.pushLines(out);
    }

    private void submitInput() {
        String raw = this.input;
        String cmd = raw.trim().toLowerCase(Locale.ROOT);
        if (cmd.isEmpty()) {
            return;
        }
        // v18.0 管道：'status | filter heat > 800' —— 第一段照常执行，
        // 其余段必须都是 'filter <字段> <op> <值>'，只过滤回显行
        if (raw.indexOf('|') >= 0) {
            this.runPipeline(raw);
            return;
        }
        // v18.0 宏：macro set / run / list / del
        if ("macro".equals(cmd) || cmd.startsWith("macro ")) {
            this.runMacroCommand(raw);
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
        if ("msg".equals(cmd) || cmd.startsWith("msg ")) {
            this.runMsgCommand(raw);
            this.history.add(raw);
            this.historyIndex = this.history.size();
            this.input = "";
            return;
        }
        if ("password".equals(cmd) || cmd.startsWith("password ")) {
            this.runCodeCommand(raw, false);
            this.history.add(raw);
            this.historyIndex = this.history.size();
            this.input = "";
            return;
        }
        if ("auth".equals(cmd) || cmd.startsWith("auth ")) {
            this.runCodeCommand(raw, true);
            this.history.add(raw);
            this.historyIndex = this.history.size();
            this.input = "";
            return;
        }
        if ("redstone".equals(cmd) || cmd.startsWith("redstone ")) {
            this.runRedstoneCommand(raw);
            this.history.add(raw);
            this.historyIndex = this.history.size();
            this.input = "";
            return;
        }
        if ("media".equals(cmd) || cmd.startsWith("media ")) {
            this.runMediaCommand(raw);
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
        // v17.0: AI diagnostics (client-side only, never sent to the server)
        if ("diagnose".equals(cmd) || cmd.startsWith("diagnose ")) {
            this.runDiagnoseCommand(raw);
            this.history.add(raw);
            this.historyIndex = this.history.size();
            this.input = "";
            return;
        }
        if ("ai".equals(cmd) || cmd.startsWith("ai ")) {
            this.runAiCommand(raw);
            this.history.add(maskAiKey(raw));
            this.historyIndex = this.history.size();
            this.input = "";
            return;
        }
        // v17.1: PPT backup controls
        if ("ppt".equals(cmd) || cmd.startsWith("ppt ")) {
            this.runPptCommand(raw);
            this.history.add(raw);
            this.historyIndex = this.history.size();
            this.input = "";
            return;
        }
        // v17.1: generic picture viewer
        if ("pic".equals(cmd) || cmd.startsWith("pic ")) {
            this.runPicCommand(raw);
            this.history.add(raw);
            this.historyIndex = this.history.size();
            this.input = "";
            return;
        }
        // v18.0 裸宏名直接运行（输入 'emergency' 等价 'macro run emergency'）
        if (this.macros.containsKey(cmd)) {
            this.input = "macro run " + cmd;
            this.submitInput();
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
            extra.add(new CmdLine(MessageTerminalCommand.C_DIM, "  msg <玩家ID> <内容>     跨维度文字通讯（单条最多 30 字符）"));
            extra.add(new CmdLine(MessageTerminalCommand.C_DIM, "  password <4位数字>      设置/修改本屏幕安全码（主人专属）"));
            extra.add(new CmdLine(MessageTerminalCommand.C_DIM, "  auth <4位数字>          验证安全码后才能 hack"));
            extra.add(new CmdLine(MessageTerminalCommand.C_DIM, "  redstone <X> <Y> <Z> <0-15>  远程红石超控指定坐标"));
            extra.add(new CmdLine(MessageTerminalCommand.C_DIM, "  media play/pause/resume/stop <文件>   4x3 多媒体屏播放控制"));
            extra.add(new CmdLine(MessageTerminalCommand.C_DIM, "  media volume <0-100>    设置多媒体屏音量"));
            extra.add(new CmdLine(MessageTerminalCommand.C_DIM, "  media list              列出 rbmk_media 目录里的音视频文件"));
            extra.add(new CmdLine(MessageTerminalCommand.C_DIM, "  media install           重新打开多媒体解码库安装引导"));
            extra.add(new CmdLine(MessageTerminalCommand.C_DIM, "  ppt load|next|prev|goto <页>   PPT 汇报屏控制（终端备用）"));
            extra.add(new CmdLine(MessageTerminalCommand.C_DIM, "  pic list|view <文件>|clear   图片查看器（rbmk_media/pictures）"));
            extra.add(new CmdLine(MessageTerminalCommand.C_DIM, "  diagnose                让 AI 专家深度诊断当前反应堆"));
            extra.add(new CmdLine(MessageTerminalCommand.C_DIM, "  diagnose local|ai|auto  切换诊断模式并立即诊断反应堆"));
            extra.add(new CmdLine(MessageTerminalCommand.C_DIM, "  ai key <API_KEY>        设置大模型 API Key（保存到 .ai_config）"));
            extra.add(new CmdLine(MessageTerminalCommand.C_DIM, "  ai url <API地址>        设置自定义 API 地址"));
            extra.add(new CmdLine(MessageTerminalCommand.C_DIM, "  ai status               显示诊断模式 / Key / API 地址"));
            extra.add(new CmdLine(MessageTerminalCommand.C_DIM, "  <cmd> | filter <字段> <op> <值>   管道过滤（如 status | filter heat > 800）"));
            extra.add(new CmdLine(MessageTerminalCommand.C_DIM, "  macro set <宏名> <指令1>;<指令2>  保存宏（会话内有效）"));
            extra.add(new CmdLine(MessageTerminalCommand.C_DIM, "  macro run|del <宏名> / macro list 执行、删除、列出宏"));
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

    /** bare 'diagnose': snapshot readings -> AI expert thread -> terminal output */
    private void runSnapshotAiDiagnose(String raw) {
        List<CmdLine> out = new ArrayList<>();
        out.add(new CmdLine(MessageTerminalCommand.C_PROMPT, "C:\\RBMK> " + raw));
        TileRbmkMonitorScreen monitor = this.findHistorySource();
        final RbmkSnapshot data = monitor == null ? null : monitor.getData();
        out.add(new CmdLine(MessageTerminalCommand.C_WARN, "[SYSTEM] 正在请求 AI 专家进行深度诊断..."));
        this.pushLines(out);
        com.jiezhirbmkmod.client.ai.AiDiagnoseThread.start(data,
                new com.jiezhirbmkmod.client.ai.AiDiagnoseThread.Callback() {
                    @Override
                    public void onResult(boolean ok, List<CmdLine> lines) {
                        GuiTerminal.this.pushLines(lines);
                    }
                });
    }

    private static boolean isSafePictureName(String name) {
        if (name == null || name.isEmpty() || name.length() > 96) {
            return false;
        }
        if (name.indexOf('/') >= 0 || name.indexOf('\\') >= 0 || name.contains("..")) {
            return false;
        }
        for (int i = 0; i < name.length(); ++i) {
            if (name.charAt(i) < 0x20) {
                return false;
            }
        }
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg");
    }

    /** v17.1 'pic list|view <文件>|clear': generic image viewer on the PPT screen. */
    private void runPicCommand(String raw) {
        List<CmdLine> out = new ArrayList<>();
        out.add(new CmdLine(MessageTerminalCommand.C_PROMPT, "C:\\RBMK> " + raw));
        String[] parts = raw.trim().split("\\s+", 3);
        String sub = parts.length > 1 ? parts[1].trim().toLowerCase(Locale.ROOT) : "";
        String arg = parts.length > 2 ? parts[2].trim() : "";

        boolean created = com.jiezhirbmkmod.block.TileRbmkPptScreen.ensurePicturesDir();
        if (created) {
            out.add(new CmdLine(MessageTerminalCommand.C_WARN,
                    "[PIC] 文件夹已创建：.minecraft/rbmk_media/pictures/，请放入 .png / .jpg 图片。"));
        }

        if ("list".equals(sub)) {
            List<String> pics = com.jiezhirbmkmod.block.TileRbmkPptScreen.listPictures();
            out.add(new CmdLine(MessageTerminalCommand.C_DIM,
                    "  --- " + com.jiezhirbmkmod.block.TileRbmkPptScreen.picturesDir().getAbsolutePath() + " ---"));
            if (pics.isEmpty()) {
                out.add(new CmdLine(MessageTerminalCommand.C_ERR,
                        "[ERROR] pictures 文件夹为空！请先放入 .png / .jpg 图片。"));
            } else {
                int shown = Math.min(pics.size(), 24);
                for (int i = 0; i < shown; ++i) {
                    String name = pics.get(i);
                    java.io.File f = new java.io.File(
                            com.jiezhirbmkmod.block.TileRbmkPptScreen.picturesDir(), name);
                    out.add(new CmdLine(MessageTerminalCommand.C_OUT, String.format(Locale.ROOT,
                            "  %-30s %7.1f KB", name, Double.valueOf(f.length() / 1024.0D))));
                }
                out.add(new CmdLine(MessageTerminalCommand.C_DIM, "  用法: pic view <文件名>"));
            }
            this.pushLines(out);
            return;
        }

        if (this.mc == null || this.mc.world == null) {
            out.add(new CmdLine(MessageTerminalCommand.C_ERR, "  [PIC] 当前没有世界，无法控制屏幕。"));
            this.pushLines(out);
            return;
        }
        com.jiezhirbmkmod.block.TileRbmkPptScreen screen =
                com.jiezhirbmkmod.block.TileRbmkPptScreen.findNear(this.mc.world, this.hostPos, 32);
        if (screen == null) {
            out.add(new CmdLine(MessageTerminalCommand.C_ERR, "  [PIC] 32 格内没有 PPT/图片屏。"));
            this.pushLines(out);
            return;
        }

        if ("view".equals(sub)) {
            if (!isSafePictureName(arg)) {
                out.add(new CmdLine(MessageTerminalCommand.C_ERR,
                        "  用法: pic view <文件名.png|.jpg>（不能包含 / \\ 或 ..，长度 1~96）"));
                this.pushLines(out);
                return;
            }
            java.io.File file = new java.io.File(
                    com.jiezhirbmkmod.block.TileRbmkPptScreen.picturesDir(), arg);
            if (!file.isFile()) {
                out.add(new CmdLine(MessageTerminalCommand.C_ERR, "  [PIC] 找不到图片: " + arg));
                this.pushLines(out);
                return;
            }
            boolean decodable;
            try {
                decodable = javax.imageio.ImageIO.read(file) != null;
            }
            catch (Throwable t) {
                decodable = false;
            }
            if (!decodable) {
                out.add(new CmdLine(MessageTerminalCommand.C_ERR,
                        "[ERROR] 图片无法读取（文件损坏或不是 png/jpg）：" + arg));
                this.pushLines(out);
                return;
            }
            screen.invalidatePictureTexture();
            ModNetwork.CHANNEL.sendToServer(new MessageRbmkPptControl(
                    MessageRbmkPptControl.PICTURE, 0, screen.getPos(), arg));
            out.add(new CmdLine(MessageTerminalCommand.C_OK, "  [PIC] 正在显示: " + arg));
        } else if ("clear".equals(sub)) {
            screen.invalidatePictureTexture();
            ModNetwork.CHANNEL.sendToServer(new MessageRbmkPptControl(
                    MessageRbmkPptControl.CLEAR, 0, screen.getPos(), ""));
            out.add(new CmdLine(MessageTerminalCommand.C_OK, "  [PIC] 屏幕已清空（黑屏）。"));
        } else {
            out.add(new CmdLine(MessageTerminalCommand.C_ERR,
                    "  用法: pic list | pic view <文件名> | pic clear"));
            this.pushLines(out);
            return;
        }
        this.pushLines(out);
    }

    /** v17.1 'ppt load|next|prev|goto <n>': terminal backup control of the PPT screen. */
    private void runPptCommand(String raw) {
        List<CmdLine> out = new ArrayList<>();
        out.add(new CmdLine(MessageTerminalCommand.C_PROMPT, "C:\\RBMK> " + raw));
        String[] parts = raw.trim().split("\\s+", 3);
        String sub = parts.length > 1 ? parts[1].trim().toLowerCase(Locale.ROOT) : "";
        String arg = parts.length > 2 ? parts[2].trim() : "";
        if (this.mc == null || this.mc.world == null) {
            out.add(new CmdLine(MessageTerminalCommand.C_ERR, "  [PPT] 当前没有世界，无法控制屏幕。"));
            this.pushLines(out);
            return;
        }
        com.jiezhirbmkmod.block.TileRbmkPptScreen screen =
                com.jiezhirbmkmod.block.TileRbmkPptScreen.findNear(this.mc.world, this.hostPos, 32);
        if (screen == null) {
            out.add(new CmdLine(MessageTerminalCommand.C_ERR, "  [PPT] 32 格内没有 PPT 汇报屏。"));
            this.pushLines(out);
            return;
        }
        int total = Math.max(1, screen.getTotalSlides());
        int cur = Math.max(1, Math.min(total, screen.getCurrentSlide()));
        if ("load".equals(sub)) {
            boolean created = com.jiezhirbmkmod.block.TileRbmkPptScreen.ensureSlidesDir();
            if (created) {
                out.add(new CmdLine(MessageTerminalCommand.C_WARN,
                        "[PPT] 文件夹已创建，请将 slide_1.png 等图片放入 .minecraft/rbmk_media/ppt/ 文件夹中。"));
            }
            int count = com.jiezhirbmkmod.block.TileRbmkPptScreen.countSlides();
            if (count <= 0) {
                out.add(new CmdLine(MessageTerminalCommand.C_ERR,
                        "[ERROR] 文件夹为空！请先放入图片。"));
                this.pushLines(out);
                return;
            }
            screen.invalidatePptTexture();
            out.add(new CmdLine(MessageTerminalCommand.C_OK, "  [PPT] 已加载 " + count + " 页幻灯片。"));
            cur = 1;
            total = Math.max(1, count);
            ModNetwork.CHANNEL.sendToServer(new MessageRbmkPptControl(
                    MessageRbmkPptControl.LOAD, count, screen.getPos()));
        } else if ("next".equals(sub)) {
            cur = Math.min(total, cur + 1);
            ModNetwork.CHANNEL.sendToServer(new MessageRbmkPptControl(
                    MessageRbmkPptControl.NEXT, 0, screen.getPos()));
        } else if ("prev".equals(sub)) {
            cur = Math.max(1, cur - 1);
            ModNetwork.CHANNEL.sendToServer(new MessageRbmkPptControl(
                    MessageRbmkPptControl.PREV, 0, screen.getPos()));
        } else if ("goto".equals(sub)) {
            int page;
            try {
                page = Integer.parseInt(arg);
            }
            catch (NumberFormatException e) {
                out.add(new CmdLine(MessageTerminalCommand.C_ERR,
                        "  用法: ppt goto <页码>（当前 " + cur + " / " + total + "）"));
                this.pushLines(out);
                return;
            }
            if (page < 1 || page > total) {
                out.add(new CmdLine(MessageTerminalCommand.C_ERR,
                        "  [PPT] 页码必须在 1 ~ " + total + " 之间。"));
                this.pushLines(out);
                return;
            }
            cur = page;
            ModNetwork.CHANNEL.sendToServer(new MessageRbmkPptControl(
                    MessageRbmkPptControl.GOTO, page, screen.getPos()));
        } else {
            out.add(new CmdLine(MessageTerminalCommand.C_ERR,
                    "  用法: ppt load | ppt next | ppt prev | ppt goto <页码>"));
            this.pushLines(out);
            return;
        }
        out.add(new CmdLine(MessageTerminalCommand.C_OUT, "  [PPT] Slide " + cur + " / " + total));
        this.pushLines(out);
    }


    /** v17.0 'diagnose local|ai|auto': switch the diagnosis mode and run immediately. */
    private void runDiagnoseCommand(String raw) {
        if ("diagnose".equals(raw.trim().toLowerCase(Locale.ROOT))) {
            this.runSnapshotAiDiagnose(raw);
            return;
        }
        List<CmdLine> out = new ArrayList<>();
        out.add(new CmdLine(MessageTerminalCommand.C_PROMPT, "C:\\RBMK> " + raw));
        String[] parts = raw.trim().split("\\s+", 2);
        String mode = parts.length > 1 ? parts[1].trim().toLowerCase(Locale.ROOT) : "";
        if (!"local".equals(mode) && !"ai".equals(mode) && !"auto".equals(mode)) {
            out.add(new CmdLine(MessageTerminalCommand.C_ERR, "  用法: diagnose local|ai|auto"));
            out.add(new CmdLine(MessageTerminalCommand.C_DIM,
                    "  local=本地逻辑树  ai=强制大模型  auto=有 Key 用 AI，否则本地"));
            this.pushLines(out);
            return;
        }
        RbmkConfig.setAiMode(mode);
        out.add(new CmdLine(MessageTerminalCommand.C_DIM, "  诊断模式已切换: " + mode
                + (RbmkConfig.hasAiKey() ? "（已配置 API Key）" : "（未配置 API Key）")));
        this.pushLines(out);

        TileRbmkMonitorScreen monitor = this.findHistorySource();
        final RbmkSnapshot data = monitor == null ? null : monitor.getData();
        AiDiagnoseDispatcher.diagnose(data, new AiDiagnoseDispatcher.Callback() {
            @Override
            public void onResult(List<CmdLine> lines) {
                GuiTerminal.this.pushLines(lines);
            }
        });
    }

    /** v17.0 'ai key|url|status': persist the AI endpoint settings in .ai_config. */
    private void runAiCommand(String raw) {
        List<CmdLine> out = new ArrayList<>();
        out.add(new CmdLine(MessageTerminalCommand.C_PROMPT, "C:\\RBMK> " + maskAiKey(raw)));
        String[] parts = raw.trim().split("\\s+", 3);
        String sub = parts.length > 1 ? parts[1].trim().toLowerCase(Locale.ROOT) : "";
        if ("key".equals(sub)) {
            if (parts.length < 3 || parts[2].trim().isEmpty()) {
                out.add(new CmdLine(MessageTerminalCommand.C_ERR, "  用法: ai key <API_KEY>"));
            } else {
                String key = parts[2].trim();
                RbmkConfig.setAiKey(key);
                out.add(new CmdLine(MessageTerminalCommand.C_OK,
                        "[AI] API Key 已保存: " + maskKey(key)));
                out.add(new CmdLine(MessageTerminalCommand.C_DIM,
                        "  当前模式 " + RbmkConfig.aiModeName() + "，配置文件: " + RbmkConfig.aiConfigPath()));
            }
        } else if ("url".equals(sub)) {
            if (parts.length < 3 || parts[2].trim().isEmpty()) {
                out.add(new CmdLine(MessageTerminalCommand.C_ERR, "  用法: ai url <API地址>"));
            } else {
                RbmkConfig.setAiUrl(parts[2].trim());
                out.add(new CmdLine(MessageTerminalCommand.C_OK,
                        "[AI] API 地址已保存: " + RbmkConfig.aiApiUrl));
            }
        } else if ("status".equals(sub)) {
            out.add(new CmdLine(MessageTerminalCommand.C_OUT, "[AI] 诊断状态:"));
            out.add(new CmdLine(MessageTerminalCommand.C_OUT,
                    "  模式: " + RbmkConfig.aiModeName() + "  (local / ai / auto)"));
            out.add(new CmdLine(RbmkConfig.hasAiKey()
                            ? MessageTerminalCommand.C_OK : MessageTerminalCommand.C_WARN,
                    "  API Key: " + (RbmkConfig.hasAiKey() ? "已配置 " + maskKey(RbmkConfig.aiApiKey) : "未配置")));
            out.add(new CmdLine(MessageTerminalCommand.C_OUT, "  API 地址: " + RbmkConfig.aiApiUrl));
            out.add(new CmdLine(MessageTerminalCommand.C_DIM,
                    "  配置文件: " + RbmkConfig.aiConfigPath()));
        } else {
            out.add(new CmdLine(MessageTerminalCommand.C_ERR, "  用法: ai key <API_KEY> | ai url <API地址> | ai status"));
        }
        this.pushLines(out);
    }

    private static String maskAiKey(String raw) {
        String t = raw.trim();
        // "ai key   <secret>" / tabs / multiple spaces: never echo the secret
        if (t.toLowerCase(Locale.ROOT).matches("^ai\\s+key\\s+.+$")) {
            return t.substring(0, 7).replaceAll("\\s+$", "") + " ****";
        }
        return t;
    }

    private static String maskKey(String key) {
        if (key == null || key.trim().isEmpty()) {
            return "(空)";
        }
        String k = key.trim();
        if (k.length() <= 8) {
            return "****";
        }
        return k.substring(0, 3) + "****" + k.substring(k.length() - 4);
    }

    /** 'msg <player> <text>': cross-dimension text message, capped at 30 chars. */
    private void runMsgCommand(String raw) {
        List<CmdLine> out = new ArrayList<>();
        out.add(new CmdLine(MessageTerminalCommand.C_PROMPT, "C:\\RBMK> " + raw));
        String[] parts = raw.trim().split("\\s+", 3);
        if (parts.length < 3 || parts[1].isEmpty() || parts[2].trim().isEmpty()) {
            out.add(new CmdLine(MessageTerminalCommand.C_ERR,
                    "  用法: msg <玩家ID> <内容>（单条最多 30 字符）"));
            this.pushLines(out);
            return;
        }
        String target = parts[1];
        String text = parts[2].trim();
        if (text.length() > MessageRbmkTerminalComm.MAX_MESSAGE) {
            text = text.substring(0, MessageRbmkTerminalComm.MAX_MESSAGE);
            out.add(new CmdLine(MessageTerminalCommand.C_WARN, "  [MSG] 内容超过 30 字符，已自动截断。"));
        }
        BlockPos origin = this.hostPos;
        TileRbmkMonitorScreen monitor = this.findHistorySource();
        if (monitor != null) {
            origin = monitor.getPos();
        }
        MessageRbmkTerminalComm packet = new MessageRbmkTerminalComm(
                MessageRbmkTerminalComm.ACTION_MSG, origin, target, text);
        if (this.mc != null && this.mc.player != null) {
            packet.setSender(this.mc.player.getName());
        }
        ModNetwork.CHANNEL.sendToServer(packet);
        out.add(new CmdLine(MessageTerminalCommand.C_DIM,
                "  [MSG] 已发往 " + target + "，等待服务端回执..."));
        this.pushLines(out);
    }

    /** 'password <4 digits>' / 'auth <4 digits>': anti-grief screen lock. */
    private void runCodeCommand(String raw, boolean auth) {
        List<CmdLine> out = new ArrayList<>();
        out.add(new CmdLine(MessageTerminalCommand.C_PROMPT, "C:\\RBMK> " + raw));
        String[] parts = raw.trim().split("\\s+", 2);
        String code = parts.length >= 2 ? parts[1].trim() : "";
        if (!code.matches("\\d{4}")) {
            out.add(new CmdLine(MessageTerminalCommand.C_ERR,
                    auth ? "  用法: auth <4位数字>" : "  用法: password <4位数字>"));
            this.pushLines(out);
            return;
        }
        TileRbmkMonitorScreen monitor = this.findHistorySource();
        if (monitor == null || this.isLaptop()) {
            out.add(new CmdLine(MessageTerminalCommand.C_ERR,
                    "  没有找到可用的 RBMK 屏幕（请从屏幕方块打开终端）。"));
            this.pushLines(out);
            return;
        }
        ModNetwork.CHANNEL.sendToServer(new MessageRbmkTerminalComm(
                auth ? MessageRbmkTerminalComm.ACTION_AUTH : MessageRbmkTerminalComm.ACTION_SET_CODE,
                monitor.getPos(), "", code));
        out.add(new CmdLine(MessageTerminalCommand.C_DIM,
                auth ? "  [AUTH] 验证请求已发送..." : "  [LOCK] 安全码设置请求已发送..."));
        this.pushLines(out);
    }

    /** 'redstone <x> <y> <z> <0-15>': force a remote redstone signal at that position. */
    private void runRedstoneCommand(String raw) {
        List<CmdLine> out = new ArrayList<>();
        out.add(new CmdLine(MessageTerminalCommand.C_PROMPT, "C:\\RBMK> " + raw));
        String[] parts = raw.trim().split("\\s+");
        if (parts.length != 5) {
            out.add(new CmdLine(MessageTerminalCommand.C_ERR,
                    "  用法: redstone <X> <Y> <Z> <0-15>"));
            this.pushLines(out);
            return;
        }
        int x;
        int y;
        int z;
        int strength;
        try {
            x = Integer.parseInt(parts[1]);
            y = Integer.parseInt(parts[2]);
            z = Integer.parseInt(parts[3]);
            strength = Integer.parseInt(parts[4]);
        }
        catch (NumberFormatException e) {
            out.add(new CmdLine(MessageTerminalCommand.C_ERR, "  坐标与强度必须是整数。"));
            this.pushLines(out);
            return;
        }
        if (Math.abs((long)x) > 30000000L || Math.abs((long)z) > 30000000L || y < 0 || y > 255) {
            out.add(new CmdLine(MessageTerminalCommand.C_ERR,
                    "  坐标越界：X/Z 需在 ±3000 万内，Y 需在 0-255。"));
            this.pushLines(out);
            return;
        }
        if (strength < 0 || strength > 15) {
            out.add(new CmdLine(MessageTerminalCommand.C_ERR, "  红石强度必须在 0-15 之间。"));
            this.pushLines(out);
            return;
        }
        TileRbmkMonitorScreen monitor = this.findHistorySource();
        if (monitor == null) {
            out.add(new CmdLine(MessageTerminalCommand.C_ERR,
                    "  没有找到可用的 RBMK 屏幕（请从屏幕终端执行该指令）。"));
            this.pushLines(out);
            return;
        }
        String payload = x + " " + y + " " + z + " " + strength;
        ModNetwork.CHANNEL.sendToServer(new MessageRbmkTerminalComm(
                MessageRbmkTerminalComm.ACTION_REDSTONE, monitor.getPos(), "", payload));
        out.add(new CmdLine(MessageTerminalCommand.C_DIM,
                "  [REDSTONE] 已发送远程红石请求 @ (" + x + ", " + y + ", " + z + ") 强度 " + strength + "..."));
        this.pushLines(out);
    }

    /** 'media play <file>' / pause / resume / stop / volume <0-100>'. */
    private void runMediaCommand(String raw) {
        List<CmdLine> out = new ArrayList<>();
        out.add(new CmdLine(MessageTerminalCommand.C_PROMPT, "C:\\RBMK> " + raw));
        String[] parts = raw.trim().split("\\s+");
        if (parts.length < 2) {
            out.add(new CmdLine(MessageTerminalCommand.C_ERR,
                    "  用法: media list | play <文件> | pause | resume | stop | volume <0-100>"));
            this.pushLines(out);
            return;
        }
        String action = parts[1].toLowerCase(Locale.ROOT);
        String arg = parts.length >= 3 ? parts[2] : "";
        if ("list".equals(action)) {
            // local file listing: the decoder reads .minecraft/rbmk_media
            com.jiezhirbmkmod.client.media.MediaLibrary.installBundledOst();
            out.add(new CmdLine(MessageTerminalCommand.C_DIM,
                    "  --- " + com.jiezhirbmkmod.client.media.MediaLibrary.dir().getAbsolutePath() + " ---"));
            List<String> files = com.jiezhirbmkmod.client.media.MediaLibrary.listFiles();
            if (files.isEmpty()) {
                out.add(new CmdLine(MessageTerminalCommand.C_DIM, "  (空)"));
            } else {
                int shown = Math.min(files.size(), 24);
                for (int i = 0; i < shown; ++i) {
                    String name = files.get(i);
                    long size = com.jiezhirbmkmod.client.media.MediaLibrary.sizeOf(name);
                    out.add(new CmdLine(MessageTerminalCommand.C_OUT, String.format(Locale.ROOT,
                            "  %-28s %7.1f KB", name, Double.valueOf(size / 1024.0D))));
                }
            }
            out.add(new CmdLine(MessageTerminalCommand.C_DIM, "  用法: media play <文件名>"));
            this.pushLines(out);
            return;
        }
        if ("install".equals(action)) {
            // v15.2: re-open the multimedia dependency guide locally
            if (this.mc != null) {
                this.mc.displayGuiScreen(new GuiMediaInstallPrompt());
            }
            return;
        }
        if ("play".equals(action)) {
            if (!arg.matches("[A-Za-z0-9._-]{1,64}")) {
                out.add(new CmdLine(MessageTerminalCommand.C_ERR,
                        "  文件名只能包含字母、数字、点、下划线、横线（最多 64 字符）。"));
                this.pushLines(out);
                return;
            }
            this.mediaFileName = arg;
            this.mediaStatus = "[MEDIA] Playing: " + arg;
        } else if ("pause".equals(action)) {
            this.mediaStatus = this.mediaFileName.isEmpty() ? "" : "[MEDIA] Paused: " + this.mediaFileName;
        } else if ("resume".equals(action)) {
            this.mediaStatus = this.mediaFileName.isEmpty() ? "" : "[MEDIA] Playing: " + this.mediaFileName;
        } else if ("stop".equals(action)) {
            this.mediaStatus = "";
            this.mediaFileName = "";
        } else if ("volume".equals(action)) {
            int vol;
            try {
                vol = Integer.parseInt(arg);
            }
            catch (NumberFormatException e) {
                out.add(new CmdLine(MessageTerminalCommand.C_ERR, "  用法: media volume <0-100>"));
                this.pushLines(out);
                return;
            }
            if (vol < 0 || vol > 100) {
                out.add(new CmdLine(MessageTerminalCommand.C_ERR, "  音量必须在 0-100 之间。"));
                this.pushLines(out);
                return;
            }
        } else {
            out.add(new CmdLine(MessageTerminalCommand.C_ERR,
                    "  用法: media list | play <文件> | pause | resume | stop | volume <0-100>"));
            this.pushLines(out);
            return;
        }
        TileRbmkMonitorScreen monitor = this.findHistorySource();
        if (monitor == null) {
            out.add(new CmdLine(MessageTerminalCommand.C_ERR,
                    "  没有找到可用的 RBMK 屏幕（请从屏幕终端执行该指令）。"));
            this.pushLines(out);
            return;
        }
        String payload = arg.isEmpty() ? action : action + " " + arg;
        ModNetwork.CHANNEL.sendToServer(new MessageRbmkTerminalComm(
                MessageRbmkTerminalComm.ACTION_MEDIA, monitor.getPos(), "", payload));
        out.add(new CmdLine(MessageTerminalCommand.C_DIM, "  [MEDIA] 请求已发送，等待服务端回执..."));
        this.pushLines(out);
    }

    /** 'hack [player]' / 'fix': enable or clear the data-spoofing hack. */
    private void toggleHack(boolean hacked, String raw, String targetName) {
        List<CmdLine> out = new ArrayList<>();
        out.add(new CmdLine(MessageTerminalCommand.C_PROMPT, "C:\\RBMK> " + raw));
        // v15.0: a locked screen may only be hacked after a successful 'auth'
        if (hacked && this.screenLocked && !this.screenAuthed) {
            out.add(new CmdLine(MessageTerminalCommand.C_ERR,
                    "[SECURITY] 该屏幕已启用安全码，请先输入 auth <4位数字> 验证。"));
            this.pushLines(out);
            return;
        }
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
