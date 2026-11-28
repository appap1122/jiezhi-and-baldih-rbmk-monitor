package com.jiezhirbmkmod.client.gui;


import com.jiezhirbmkmod.network.NetGuard;
import com.jiezhirbmkmod.client.ai.AiDiagnoseDispatcher;
import com.jiezhirbmkmod.dfc.RbmkSnapshot;
import com.jiezhirbmkmod.item.ItemRbmkPda;
import com.jiezhirbmkmod.network.ModNetwork;
import com.jiezhirbmkmod.network.msg.MessageRbmkPda;
import com.jiezhirbmkmod.network.msg.MessageTerminalCommand;
import com.jiezhirbmkmod.network.msg.MessageTerminalCommand.CmdLine;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * v18.3 RBMK PDA screen: a black-and-green handheld console.
 *
 * <p>Top half shows the latest reactor snapshot requested from the server;
 * bottom half is a miniature terminal. Commands go through the existing
 * {@link MessageTerminalCommand} (nothing is executed on the client), while
 * {@code diagnose} stays local and reuses {@link AiDiagnoseDispatcher}.</p>
 */
@SideOnly(Side.CLIENT)
public class GuiRbmkPda extends GuiScreen {

    private static final int W = 340;
    private static final int H = 240;
    private static final int C_GREEN = 0x33FF66;
    private static final int C_DIM = 0x2E9E55;
    private static final int C_WHITE = 0xD8FFD8;
    private static final int C_RED = 0xE74856;
    private static final int C_YELLOW = 0xF9F1A5;
    private static final int C_BG = 0xF0000000;
    private static final int C_LINE = 0xFF1E4A1E;
    private static final int MAX_INPUT = 120;
    private static final int MAX_LINES = 200;
    /** 快照超过该时长（毫秒）未刷新就标记「数据陈旧」 */
    private static final long STALE_MS = 5000L;

    private final ItemStack stack;
    private final List<CmdLine> lines = new ArrayList<CmdLine>();
    private String input = "";
    private RbmkSnapshot snapshot;
    private boolean bound;
    private int boundDim = Integer.MIN_VALUE;
    private int bx;
    private int by;
    private int bz;
    private int panelX;
    private int panelY;
    private long lastSnapshotTime = -1L;

    public GuiRbmkPda(ItemStack stack) {
        this.stack = stack;
        this.readBound();
        this.push(MessageTerminalCommand.C_DIM, "RBMK-Monitor PDA v18.3  /  输入 help 查看指令");
    }

    private void readBound() {
        if (!ItemRbmkPda.hasBound(this.stack)) {
            this.bound = false;
            return;
        }
        BlockPos pos = ItemRbmkPda.readBoundPos(this.stack);
        if (pos == null) {
            this.bound = false;
            return;
        }
        this.bound = true;
        this.bx = pos.getX();
        this.by = pos.getY();
        this.bz = pos.getZ();
        this.boundDim = ItemRbmkPda.readBoundDim(this.stack);
    }

    @Override
    public void initGui() {
        this.panelX = (this.width - W) / 2;
        this.panelY = (this.height - H) / 2;
        this.buttonList.clear();
        int bottom = this.panelY + H - 27;
        this.buttonList.add(new GuiButton(0, this.panelX + 10, bottom, 78, 20, "绑定控制台"));
        this.buttonList.add(new GuiButton(1, this.panelX + 92, bottom, 78, 20, "刷新数据"));
        this.buttonList.add(new GuiButton(2, this.panelX + 174, bottom, 78, 20, "解除绑定"));
        this.buttonList.add(new GuiButton(3, this.panelX + 256, bottom, 74, 20, "关闭"));
        if (this.bound) {
            this.requestSnapshot();
        }
    }

    private void requestSnapshot() {
        NetGuard.sendToServer(MessageRbmkPda.request(MessageRbmkPda.ACTION_SNAPSHOT));
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (button.id == 0) {
            NetGuard.sendToServer(MessageRbmkPda.request(MessageRbmkPda.ACTION_BIND));
        } else if (button.id == 1) {
            this.requestSnapshot();
        } else if (button.id == 2) {
            NetGuard.sendToServer(MessageRbmkPda.request(MessageRbmkPda.ACTION_UNBIND));
        } else if (button.id == 3) {
            this.mc.displayGuiScreen((GuiScreen) null);
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == 1) {
            this.mc.displayGuiScreen((GuiScreen) null);
            return;
        }
        if (keyCode == 28 || keyCode == 156) {
            this.submitInput();
            return;
        }
        if (keyCode == 14) {
            if (!this.input.isEmpty()) {
                this.input = this.input.substring(0, this.input.length() - 1);
            }
            return;
        }
        if (typedChar >= 32 && typedChar < 127 && this.input.length() < MAX_INPUT) {
            this.input += typedChar;
        }
    }

    private void submitInput() {
        String raw = this.input.trim();
        this.input = "";
        if (raw.isEmpty()) {
            return;
        }
        this.push(MessageTerminalCommand.C_PROMPT, "> " + raw);
        String cmd = raw.split("\\s+")[0].toLowerCase(Locale.ROOT);
        if ("cls".equals(cmd) || "clear_screen".equals(cmd)) {
            this.lines.clear();
            return;
        }
        if ("diagnose".equals(cmd) || cmd.startsWith("diagnose ")) {
            this.runDiagnose();
            return;
        }
        NetGuard.sendToServer(MessageTerminalCommand.pda(raw));
    }

    /** Local AI diagnosis: same dispatcher the terminal uses, same snapshot. */
    private void runDiagnose() {
        if (this.snapshot == null) {
            this.push(MessageTerminalCommand.C_ERR, "  尚未取得反应堆快照，先点「刷新数据」。");
            return;
        }
        this.push(MessageTerminalCommand.C_WARN, "[AI] 正在诊断当前反应堆…");
        AiDiagnoseDispatcher.diagnose(this.snapshot, new AiDiagnoseDispatcher.Callback() {
            @Override
            public void onResult(List<CmdLine> out) {
                GuiRbmkPda.this.pushLines(out);
            }
        });
    }

    public void onPdaPacket(MessageRbmkPda msg) {
        if (msg == null) {
            return;
        }
        if (!msg.isOk()) {
            this.push(MessageTerminalCommand.C_ERR, "  " + msg.getText());
            return;
        }
        if (msg.hasSnapshot()) {
            this.snapshot = msg.getSnapshot();
            this.lastSnapshotTime = System.currentTimeMillis();
        }
        if (msg.getAction() == MessageRbmkPda.ACTION_BIND
                || msg.getAction() == MessageRbmkPda.ACTION_SNAPSHOT) {
            this.bound = true;
            this.bx = msg.getX();
            this.by = msg.getY();
            this.bz = msg.getZ();
            this.boundDim = msg.getDim();
        }
        if (msg.getAction() == MessageRbmkPda.ACTION_UNBIND) {
            this.bound = false;
            this.snapshot = null;
            this.lastSnapshotTime = -1L;
        }
        if (!msg.getText().isEmpty()) {
            this.push(MessageTerminalCommand.C_OK, "  " + msg.getText());
        }
        if (msg.getAction() == MessageRbmkPda.ACTION_BIND) {
            this.requestSnapshot();
        }
    }

    /** Reply from the shared terminal command channel. */
    public void onServerReply(List<CmdLine> reply) {
        this.pushLines(reply);
    }

    private void pushLines(List<CmdLine> add) {
        if (add == null) {
            return;
        }
        for (int i = 0; i < add.size(); ++i) {
            CmdLine line = add.get(i);
            this.push(line.color, line.text);
        }
    }

    private void push(int color, String text) {
        this.lines.add(new CmdLine(color, text == null ? "" : text));
        while (this.lines.size() > MAX_LINES) {
            this.lines.remove(0);
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.drawRect(0, 0, this.width, this.height, 0x90000000);
        this.drawRect(this.panelX, this.panelY, this.panelX + W, this.panelY + H, C_BG);
        this.drawRect(this.panelX, this.panelY, this.panelX + W, this.panelY + 1, C_GREEN);
        this.drawRect(this.panelX, this.panelY + H - 1, this.panelX + W, this.panelY + H, C_GREEN);
        this.drawRect(this.panelX, this.panelY, this.panelX + 1, this.panelY + H, C_GREEN);
        this.drawRect(this.panelX + W - 1, this.panelY, this.panelX + W, this.panelY + H, C_GREEN);

        this.drawString(this.fontRenderer, "RBMK-MONITOR  PDA", this.panelX + 8, this.panelY + 6, C_GREEN);
        this.drawString(this.fontRenderer, "v18.3", this.panelX + W - 40, this.panelY + 6, C_DIM);
        this.drawRect(this.panelX + 8, this.panelY + 18, this.panelX + W - 8, this.panelY + 19, C_LINE);

        this.drawData();
        this.drawTerminal();
        this.drawInput();

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private void drawData() {
        int x = this.panelX + 10;
        int y = this.panelY + 24;
        if (!this.bound) {
            this.drawString(this.fontRenderer, "LINK  未绑定数据源", x, y, C_YELLOW);
            this.drawString(this.fontRenderer, "  按「绑定控制台」并看向 RBMK 控制台", x, y + 12, C_DIM);
            this.drawString(this.fontRenderer, "TEMP  --", x, y + 24, C_DIM);
            this.drawString(this.fontRenderer, "FLUX  --", x, y + 36, C_DIM);
            this.drawString(this.fontRenderer, "RODS  --", x, y + 48, C_DIM);
            return;
        }
        this.drawString(this.fontRenderer,
                "LINK  CONSOLE @ " + this.bx + " " + this.by + " " + this.bz + "  dim " + this.boundDim,
                x, y, C_DIM);
        RbmkSnapshot d = this.snapshot;
        if (d == null) {
            this.drawString(this.fontRenderer, "DATA  等待快照…（点「刷新数据」）", x, y + 12, C_YELLOW);
            return;
        }
        int tempColor = d.meltdown || d.meltRisk > 0.8D ? C_RED : C_WHITE;
        this.drawString(this.fontRenderer, String.format(Locale.ROOT,
                "TEMP  avg %.1f C   max %.1f / %.1f C", Double.valueOf(d.avgHeat),
                Double.valueOf(d.maxHeat), Double.valueOf(d.maxHeatLimit)), x, y + 12, tempColor);
        this.drawString(this.fontRenderer, String.format(Locale.ROOT,
                "FLUX  %d F   %s", Integer.valueOf(d.flux), d.meltdown ? "MELTDOWN" : ""),
                x, y + 24, d.meltdown ? C_RED : C_WHITE);
        this.drawString(this.fontRenderer, String.format(Locale.ROOT,
                "RODS  %.1f%%   control %d   risk %.2f", Double.valueOf(d.avgRodLevel),
                Integer.valueOf(d.controlCount), Double.valueOf(d.meltRisk)), x, y + 36,
                d.meltRisk > 0.8D ? C_RED : C_WHITE);
        this.drawString(this.fontRenderer, String.format(Locale.ROOT,
                "STEAM %.1f   enrich %.1f   xenon %.1f", Double.valueOf(d.steamTotal),
                Double.valueOf(d.avgEnrichment), Double.valueOf(d.avgXenon)), x, y + 48, C_DIM);
        // 状态行：离线 / 数据陈旧 / 熔毁，三选一叠加提示，避免误操作
        if (d.offline) {
            this.drawString(this.fontRenderer, "STATUS  OFFLINE（控制台读不到柱子）", x, y + 60, C_RED);
        } else if (this.lastSnapshotTime > 0
                && System.currentTimeMillis() - this.lastSnapshotTime > STALE_MS) {
            this.drawString(this.fontRenderer, "STATUS  数据陈旧（STALE）——请点「刷新数据」", x, y + 60, C_YELLOW);
        } else if (d.meltdown) {
            this.drawString(this.fontRenderer, "STATUS  MELTDOWN —— 立即 SCRAM", x, y + 60, C_RED);
        }
    }

    private void drawTerminal() {
        int x = this.panelX + 10;
        int top = this.panelY + 92;
        int bottom = this.panelY + H - 50;
        this.drawRect(this.panelX + 8, top, this.panelX + W - 8, bottom, 0x60000000);
        this.drawRect(this.panelX + 8, top, this.panelX + W - 8, top + 1, C_LINE);
        int rows = Math.max(1, (bottom - top - 4) / 10);
        int start = Math.max(0, this.lines.size() - rows);
        int y = top + 3;
        for (int i = start; i < this.lines.size(); ++i) {
            CmdLine line = this.lines.get(i);
            this.drawString(this.fontRenderer, this.clip(line.text), x, y, line.color & 0xFFFFFF);
            y += 10;
        }
    }

    private void drawInput() {
        int y = this.panelY + H - 44;
        this.drawRect(this.panelX + 8, y - 2, this.panelX + W - 8, y + 10, 0x80000000);
        boolean caret = (System.currentTimeMillis() / 500L) % 2L == 0L;
        String text = "> " + this.input + (caret ? "_" : "");
        this.drawString(this.fontRenderer, this.clip(text), this.panelX + 12, y, C_GREEN);
    }

    private String clip(String text) {
        if (text == null) {
            return "";
        }
        return text.length() <= 54 ? text : text.substring(0, 54);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
