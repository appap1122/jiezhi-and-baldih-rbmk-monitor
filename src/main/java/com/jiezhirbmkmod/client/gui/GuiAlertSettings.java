package com.jiezhirbmkmod.client.gui;

import com.jiezhirbmkmod.block.TileRbmkMonitorScreen;
import com.jiezhirbmkmod.client.util.GuiDraw;
import com.jiezhirbmkmod.dfc.AlertPolicy;
import com.jiezhirbmkmod.dfc.RbmkSnapshot;
import com.jiezhirbmkmod.network.ModNetwork;
import com.jiezhirbmkmod.network.msg.MessageRbmkScreenPolicy;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly(value=Side.CLIENT)
public class GuiAlertSettings
extends GuiScreen {
    private static final int W = 264;
    private static final int H = 240;
    private static final int LABEL_X = 8;
    private static final int VALUE_X = 58;
    private static final int BTN_X = 122;
    private static final int BTN_W = 29;
    private static final int BTN_H = 14;
    private static final int BTN_GAP = 3;
    private static final int[] ROW_Y = new int[]{52, 70, 88, 106, 176};
    private static final int SEP_Y = 124;
    private static final int SIREN_Y = 130;
    private static final int MODE_Y = 150;
    private static final int INVERT_Y = 196;
    private static final int NOTE_Y = 216;

    private static final int ID_CLOSE = 0;
    private static final int ID_SIREN = 1;
    private static final int ID_INVERT = 2;
    private static final int ID_MODE_PREV = 3;
    private static final int ID_MODE_NEXT = 4;
    private static final int ID_QUIT = 5;
    private static final int ID_ADJ_BASE = 100;

    private static final String[] FIELD_LABELS = new String[]{"预警温度", "严重温度", "温升速率", "通量阈值", "触发阈值"};
    private static final int[][] STEPS = new int[][]{
            {-100, -10, 10, 100},
            {-100, -10, 10, 100},
            {-200, -50, 50, 200},
            {-20, -5, 5, 20}};

    private final TileRbmkMonitorScreen tile;
    private final BlockPos pos;
    private final AlertPolicy p = new AlertPolicy();
    private final List<GuiButton> adjButtons = new ArrayList<GuiButton>();

    public GuiAlertSettings(TileRbmkMonitorScreen tile) {
        this.tile = tile;
        this.pos = tile.getPos();
        this.p.setFrom(tile.getPolicy());
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    @Override
    public void initGui() {
        super.initGui();
        this.adjButtons.clear();
        int id = ID_ADJ_BASE;
        for (int f = 0; f < FIELD_LABELS.length; ++f) {
            int y = this.top() + ROW_Y[f];
            for (int k = 0; k < 4; ++k) {
                GuiButton b = new GuiButton(id++, this.left() + BTN_X + k * (BTN_W + BTN_GAP), y, BTN_W, BTN_H, "");
                this.buttonList.add(b);
                this.adjButtons.add(b);
            }
        }
        this.buttonList.add(new GuiButton(ID_SIREN, this.left() + LABEL_X, this.top() + SIREN_Y, 120, BTN_H, ""));
        this.buttonList.add(new GuiButton(ID_MODE_PREV, this.left() + VALUE_X, this.top() + MODE_Y, 14, BTN_H, "<"));
        this.buttonList.add(new GuiButton(ID_MODE_NEXT, this.left() + VALUE_X + 92, this.top() + MODE_Y, 14, BTN_H, ">"));
        this.buttonList.add(new GuiButton(ID_INVERT, this.left() + LABEL_X, this.top() + INVERT_Y, 120, BTN_H, ""));
        this.buttonList.add(new GuiButton(ID_CLOSE, this.left() + W - 118, this.top() + 4, 54, BTN_H, "保存"));
        this.buttonList.add(new GuiButton(ID_QUIT, this.left() + W - 60, this.top() + 4, 54, BTN_H, "关闭"));
        this.refresh();
    }

    private int left() {
        return (this.width - W) / 2;
    }

    private int top() {
        return Math.max(4, (this.height - H) / 2);
    }

    private int[] stepsOf(int field) {
        if (field < STEPS.length) {
            return STEPS[field];
        }
        int s = Math.max(1, AlertPolicy.step(this.p.rsMode));
        return new int[]{-s * 10, -s, s, s * 10};
    }

    private void refresh() {
        for (int f = 0; f < FIELD_LABELS.length; ++f) {
            int[] st = this.stepsOf(f);
            for (int k = 0; k < 4; ++k) {
                GuiButton b = this.adjButtons.get(f * 4 + k);
                b.displayString = (st[k] > 0 ? "+" : "") + st[k];
                b.enabled = this.canEdit(f);
            }
        }
        GuiButton siren = this.btn(ID_SIREN);
        if (siren != null) {
            siren.displayString = "汽笛：" + (this.p.siren ? "开" : "关");
        }
        GuiButton invert = this.btn(ID_INVERT);
        if (invert != null) {
            invert.displayString = "红石反相：" + (this.p.rsInvert ? "开" : "关");
        }
    }

    private GuiButton btn(int id) {
        for (GuiButton b : this.buttonList) {
            if (b.id == id) {
                return b;
            }
        }
        return null;   
    }

    private boolean canEdit(int field) {
        return field != FIELD_LABELS.length - 1 || AlertPolicy.hasThreshold(this.p.rsMode);
    }

    private int value(int field) {
        switch (field) {
            case 0:
                return this.p.warnC;
            case 1:
                return this.p.critC;
            case 2:
                return this.p.rateWarnD;
            case 3:
                return this.p.fluxWarn;
            default:
                return this.p.rsValue;
        }
    }

    private void setValue(int field, int v) {
        switch (field) {
            case 0:
                this.p.warnC = v;
                break;
            case 1:
                this.p.critC = v;
                break;
            case 2:
                this.p.rateWarnD = v;
                break;
            case 3:
                this.p.fluxWarn = v;
                break;
            default:
                this.p.rsValue = v;
                break;
        }
    }

    private void nudge(int field, int delta) {
        int lo = field == 4 ? AlertPolicy.min(this.p.rsMode) : (field == 0 ? AlertPolicy.WARN_MIN : (field == 1 ? AlertPolicy.CRIT_MIN : (field == 2 ? AlertPolicy.RATE_MIN : AlertPolicy.FLUX_MIN)));
        int hi = field == 4 ? AlertPolicy.max(this.p.rsMode) : (field == 0 ? AlertPolicy.WARN_MAX : (field == 1 ? AlertPolicy.CRIT_MAX : (field == 2 ? AlertPolicy.RATE_MAX : AlertPolicy.FLUX_MAX)));
        int v = this.value(field) + delta;
        this.setValue(field, Math.max(lo, Math.min(hi, v)));
        this.p.clampAll();
        this.refresh();
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        int id = button.id;
        if (id == ID_CLOSE) {
            this.p.clampAll();
            ModNetwork.CHANNEL.sendToServer((IMessage) new MessageRbmkScreenPolicy(this.pos, this.p));
            this.tile.applyPolicyLocal(this.p); 
            return;
        }
        if (id == ID_QUIT) {
            this.mc.player.closeScreen();
            return;
        }
        if (id == ID_SIREN) {
            this.p.siren = !this.p.siren;
            this.refresh();
            return;
        }
        if (id == ID_INVERT) {
            this.p.rsInvert = !this.p.rsInvert;
            this.refresh();
            return;
        }
        if (id == ID_MODE_PREV || id == ID_MODE_NEXT) {
            int n = AlertPolicy.RS_MODE_COUNT;
            int m = this.p.rsMode + (id == ID_MODE_NEXT ? 1 : -1);
            this.p.rsMode = ((m % n) + n) % n;
            this.p.clampAll();
            this.refresh();
            return;
        }
        if (id >= ID_ADJ_BASE) {
            int rel = id - ID_ADJ_BASE;
            int field = rel / 4;
            int k = rel % 4;
            if (field >= 0 && field < FIELD_LABELS.length) {
                this.nudge(field, this.stepsOf(field)[k]);
            }
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == 1) {
            this.p.setFrom(this.tile.getPolicy()); 
        }
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        super.drawScreen(mouseX, mouseY, partialTicks);
        int px = this.left();
        int py = this.top();
        GuiDraw.drawRoundedRect(px, py, W, H, 8.0f, 0xF014171B);
        this.frame(px, py);
        this.drawLive(px, py);
        this.drawAlerts(px, py);
        this.drawRedstone(px, py);
        this.drawNote(px, py);
    }

    private void frame(int px, int py) {
        int line = 0xFF4A525C;
        GuiDraw.drawRect(px, py, px + W, py + 1, line);
        GuiDraw.drawRect(px, py + H - 1, px + W, py + H, line);
        GuiDraw.drawRect(px, py, px + 1, py + H, line);
        GuiDraw.drawRect(px + W - 1, py, px + W, py + H, line);
    }

    private void drawLive(int px, int py) {
        this.drawCenteredString(this.fontRenderer, "RBMK 告警 / 红石设置", px + W / 2, py + 6, 0xFFE6EBF2);
        RbmkSnapshot d = this.tile.getData();
        int lv = this.p.level(d);
        String state;
        int color;
        switch (lv) {
            case AlertPolicy.LEVEL_CRIT:
                state = "严重";
                color = 0xFFFF5A50;
                break;
            case AlertPolicy.LEVEL_WARN:
                state = "预警";
                color = 0xFFFFB347;
                break;
            default:
                boolean live = d != null && d.linked && !d.offline;
                state = live ? "正常" : "无数据";
                color = live ? 0xFF7FD7A0 : 0xFF8A93A0;
        }
        this.fontRenderer.drawString("状态", px + LABEL_X, py + 24, 0xFF6B7683);
        this.fontRenderer.drawString(state, px + LABEL_X + 26, py + 24, color);
        if (d != null && d.linked && !d.offline) {
            this.fontRenderer.drawString("最高 " + Math.round(d.maxHeat) + "°C  限值 " + Math.round(d.maxHeatLimit)
                    + "°C  通量 " + d.flux + "F", px + VALUE_X, py + 24, 0xFFC8D0DA);
        }
        this.fontRenderer.drawString("温度/通量类模式在没数据时不输出；要给\"断线\"报警就选「信号丢失」",
                px + LABEL_X, py + 38, 0xFF6B7683);
        GuiDraw.drawRect(px + 4, py + 48, px + W - 4, py + 49, 0xFF3A4049);
    }

    private void drawAlerts(int px, int py) {
        for (int f = 0; f < 4; ++f) {
            int y = py + ROW_Y[f];
            this.fontRenderer.drawString(FIELD_LABELS[f], px + LABEL_X, y + 4, 0xFFC8D0DA);
            this.fontRenderer.drawString(this.textOf(f), px + VALUE_X, y + 4, 0xFF7FDBFF);
        }
        GuiDraw.drawRect(px + 4, py + SEP_Y, px + W - 4, py + SEP_Y + 1, 0xFF3A4049);
    }

    private void drawRedstone(int px, int py) {
        int y = py + MODE_Y;
        this.fontRenderer.drawString("红石模式", px + LABEL_X, y + 4, 0xFFC8D0DA);
        this.drawCenteredString(this.fontRenderer, AlertPolicy.modeName(this.p.rsMode),
                px + VALUE_X + 53, y + 4, 0xFF7FDBFF);
        int live = this.tile.redstonePower();
        String out = this.p.rsMode == AlertPolicy.RS_OFF ? "未启用"
                : (live > 0 ? "输出 " + live + " 格" : (AlertPolicy.hasThreshold(this.p.rsMode) ? "静默" : "条件未成立"));
        this.fontRenderer.drawString(out, px + 176, y + 4, live > 0 ? 0xFFFFB347 : 0xFF8A93A0);
        int ty = py + ROW_Y[4];
        this.fontRenderer.drawString("触发阈值", px + LABEL_X, ty + 4, 0xFFC8D0DA);
        this.fontRenderer.drawString(this.textOf(4), px + VALUE_X, ty + 4, 0xFF7FDBFF);
    }

    private void drawNote(int px, int py) {
        this.fontRenderer.drawString("整台屏 12 格均可接红石线；关机或没绑数据源时不输出。",
                px + LABEL_X, py + NOTE_Y, 0xFF5A626C);
        this.fontRenderer.drawString("汽笛音量、重叫间隔、红石去抖 ticks 在 config/jiezhirbmkmod.cfg 里调。",
                px + LABEL_X, py + NOTE_Y + 10, 0xFF5A626C);
    }

    private String textOf(int field) {
        switch (field) {
            case 0:
                return this.p.warnC + " °C";
            case 1:
                return this.p.critC + " °C";
            case 2:
                return this.p.rateWarnD <= 0 ? "关" : fmt(this.p.rateWarnD / 10.0) + " °C/s";
            case 3:
                return this.p.fluxWarn <= 0 ? "关" : this.p.fluxWarn + " F";
            default:
                if (this.p.rsMode == AlertPolicy.RS_OFF) {
                    return "—";
                }
                if (!AlertPolicy.hasThreshold(this.p.rsMode)) {
                    return "无需阈值";
                }
                return AlertPolicy.valueText(this.p.rsMode, this.p.rsValue);
        }
    }

    private static String fmt(double v) {
        return String.format(Locale.ROOT, "%.1f", v);
    }

    public static String summary(AlertPolicy p) {
        return p.siren ? "汽笛开" : "汽笛关";
    }
}
