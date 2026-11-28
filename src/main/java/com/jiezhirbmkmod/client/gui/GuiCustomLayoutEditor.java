package com.jiezhirbmkmod.client.gui;


import com.jiezhirbmkmod.network.NetGuard;
import java.io.IOException;

import com.jiezhirbmkmod.block.TileCustomScreen;
import com.jiezhirbmkmod.dfc.CustomWidgetLayout;
import com.jiezhirbmkmod.network.ModNetwork;
import com.jiezhirbmkmod.network.msg.MessageCustomLayoutSet;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.math.BlockPos;

/**
 * v18.2 自定义页布局编辑器（自定义屏 GUI → 「布局编辑器…」打开）。
 *
 * <p>左侧 256×192 虚拟屏（2 倍缩放）：点选小部件、按住拖动移动；
 * 右侧按钮列：类型循环（仪表/曲线/文本）、数据源循环（仪表/文本=
 * watch 槽位，曲线=历史通道）、放大/缩小、添加、删除、导出（布局串进
 * 剪贴板）、导入（从剪贴板读布局串）、保存（发服务端持久化）、关闭。</p>
 *
 * <p>保存走 {@link MessageCustomLayoutSet} 整包提交；导出串是
 * Base64(压缩 NBT)，可以贴给朋友直接导入。纯客户端 GUI。</p>
 */
public class GuiCustomLayoutEditor
extends GuiScreen {

    private static final int BTN_TYPE = 0;
    private static final int BTN_SOURCE = 1;
    private static final int BTN_GROW = 2;
    private static final int BTN_SHRINK = 3;
    private static final int BTN_ADD = 4;
    private static final int BTN_DEL = 5;
    private static final int BTN_EXPORT = 6;
    private static final int BTN_IMPORT = 7;
    private static final int BTN_SAVE = 8;
    private static final int BTN_CANCEL = 9;

    private static final int PANEL_W = 236;
    private static final int BUTTON_W = 108;

    private final BlockPos pos;
    /** 工作副本（保存前不动屏上的真布局） */
    private final CustomWidgetLayout layout = new CustomWidgetLayout();
    private int selected = -1;
    private boolean dragging = false;
    private int dragOffX;
    private int dragOffY;
    private String status = "";

    public GuiCustomLayoutEditor(BlockPos pos, CustomWidgetLayout current) {
        this.pos = pos;
        if (current != null) {
            for (CustomWidgetLayout.Widget w : current.widgets()) {
                this.layout.add(w.copy());
            }
        }
    }

    // ---- 坐标换算 ----

    private int boardX() {
        return Math.max(4, this.width / 2 - PANEL_W / 2);
    }

    private int boardY() {
        return Math.max(4, this.height / 2 - 196);
    }

    private int scale() {
        // 高度富余用 2 倍，小窗口 1 倍
        return this.height >= 460 ? 2 : 1;
    }

    private int toVx(int mouseX) {
        return (mouseX - this.boardX()) / this.scale();
    }

    private int toVy(int mouseY) {
        return (mouseY - this.boardY()) / this.scale();
    }

    // ---- GUI ----

    @Override
    public void initGui() {
        super.initGui();
        int bx = this.boardX() + 256 * this.scale() + 8; // 按钮列贴在虚拟屏右侧
        int by = this.boardY();
        int bh = 18;
        int row = 0;
        this.buttonList.add(new GuiButton(BTN_TYPE, bx, by + row++ * (bh + 4), BUTTON_W, bh, this.typeLabel()));
        this.buttonList.add(new GuiButton(BTN_SOURCE, bx, by + row++ * (bh + 4), BUTTON_W, bh, this.sourceLabel()));
        this.buttonList.add(new GuiButton(BTN_GROW, bx, by + row++ * (bh + 4), BUTTON_W, bh, "放大 +"));
        this.buttonList.add(new GuiButton(BTN_SHRINK, bx, by + row++ * (bh + 4), BUTTON_W, bh, "缩小 -"));
        this.buttonList.add(new GuiButton(BTN_ADD, bx, by + row++ * (bh + 4), BUTTON_W, bh, "添加小部件"));
        this.buttonList.add(new GuiButton(BTN_DEL, bx, by + row++ * (bh + 4), BUTTON_W, bh, "删除选中"));
        this.buttonList.add(new GuiButton(BTN_EXPORT, bx, by + row++ * (bh + 4), BUTTON_W, bh, "导出到剪贴板"));
        this.buttonList.add(new GuiButton(BTN_IMPORT, bx, by + row++ * (bh + 4), BUTTON_W, bh, "从剪贴板导入"));
        this.buttonList.add(new GuiButton(BTN_SAVE, bx, by + row++ * (bh + 4), BUTTON_W, bh, "保存"));
        this.buttonList.add(new GuiButton(BTN_CANCEL, bx, by + row++ * (bh + 4), BUTTON_W, bh, "取消"));
    }

    private CustomWidgetLayout.Widget selected() {
        if (this.selected < 0 || this.selected >= this.layout.widgets().size()) {
            return null;
        }
        return this.layout.widgets().get(this.selected);
    }

    private String typeLabel() {
        CustomWidgetLayout.Widget w = this.selected();
        int t = w == null ? 0 : w.type;
        return "类型：" + (t == CustomWidgetLayout.TYPE_GAUGE ? "仪表块"
                : t == CustomWidgetLayout.TYPE_GRAPH ? "曲线块" : "文本块");
    }

    private String sourceLabel() {
        CustomWidgetLayout.Widget w = this.selected();
        if (w == null) {
            return "数据源：-";
        }
        if (w.type == CustomWidgetLayout.TYPE_GRAPH) {
            String[] ch = {"柱温", "通量", "水量", "蒸汽", "富集", "氙毒"};
            return "通道：" + ch[Math.max(0, Math.min(5, w.channel))];
        }
        return "槽位：#" + (w.slot + 1);
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        CustomWidgetLayout.Widget w = this.selected();
        switch (button.id) {
            case BTN_TYPE: {
                if (w != null) {
                    w.type = (w.type + 1) % CustomWidgetLayout.TYPE_COUNT;
                    button.displayString = this.typeLabel();
                    this.refreshSources();
                }
                break;
            }
            case BTN_SOURCE: {
                if (w != null) {
                    if (w.type == CustomWidgetLayout.TYPE_GRAPH) {
                        w.channel = (w.channel + 1) % 6;
                    } else {
                        w.slot = (w.slot + 1) % 8;
                    }
                    button.displayString = this.sourceLabel();
                }
                break;
            }
            case BTN_GROW: {
                if (w != null) {
                    w.w = Math.min(CustomWidgetLayout.CANVAS_W, w.w + 8);
                    w.h = Math.min(CustomWidgetLayout.CANVAS_H, w.h + 4);
                    w.clamp();
                }
                break;
            }
            case BTN_SHRINK: {
                if (w != null) {
                    w.w = Math.max(CustomWidgetLayout.MIN_W, w.w - 8);
                    w.h = Math.max(CustomWidgetLayout.MIN_H, w.h - 4);
                    w.clamp();
                }
                break;
            }
            case BTN_ADD: {
                CustomWidgetLayout.Widget nw = new CustomWidgetLayout.Widget();
                nw.x = 8 + this.layout.widgets().size() * 6;
                nw.y = 8 + this.layout.widgets().size() * 6;
                if (this.layout.add(nw)) {
                    this.selected = this.layout.widgets().size() - 1;
                    this.refreshButtons();
                } else {
                    this.status = "已达上限（12 个）";
                }
                break;
            }
            case BTN_DEL: {
                if (w != null) {
                    this.layout.widgets().remove(this.selected);
                    this.selected = -1;
                    this.refreshButtons();
                }
                break;
            }
            case BTN_EXPORT: {
                String s = this.layout.exportString();
                if (s == null) {
                    this.status = "导出失败";
                } else {
                    this.setClipboardString(s);
                    this.status = "布局串已复制到剪贴板";
                }
                break;
            }
            case BTN_IMPORT: {
                String clip = this.getClipboardString();
                CustomWidgetLayout tmp = new CustomWidgetLayout();
                if (tmp.importString(clip)) {
                    this.layout.clear();
                    for (CustomWidgetLayout.Widget wi : tmp.widgets()) {
                        this.layout.add(wi.copy());
                    }
                    this.selected = -1;
                    this.refreshButtons();
                    this.status = "导入成功";
                } else {
                    this.status = "剪贴板里没有有效的布局串";
                }
                break;
            }
            case BTN_SAVE: {
                NetGuard.sendToServer(new MessageCustomLayoutSet(this.pos,
                        this.layout.exportString() == null ? "" : this.layout.exportString()));
                this.status = "已保存";
                this.mc.displayGuiScreen(null);
                break;
            }
            case BTN_CANCEL: {
                this.mc.displayGuiScreen(null);
                break;
            }
            default:
                break;
        }
    }

    private void refreshSources() {
        for (GuiButton b : this.buttonList) {
            if (b.id == BTN_SOURCE) {
                b.displayString = this.sourceLabel();
            } else if (b.id == BTN_TYPE) {
                b.displayString = this.typeLabel();
            }
        }
    }

    private void refreshButtons() {
        GuiButton del = this.findButton(BTN_DEL);
        if (del != null) {
            del.enabled = this.selected() != null;
        }
    }

    private GuiButton findButton(int id) {
        for (GuiButton b : this.buttonList) {
            if (b.id == id) {
                return b;
            }
        }
        return null;
    }

    // ---- 鼠标拖动 ----

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        if (mouseButton == 0) {
            int vx = this.toVx(mouseX);
            int vy = this.toVy(mouseY);
            CustomWidgetLayout.Widget w = this.layout.widgetAt(vx, vy);
            if (w != null) {
                this.selected = this.layout.widgets().indexOf(w);
                this.dragging = true;
                this.dragOffX = vx - w.x;
                this.dragOffY = vy - w.y;
                this.refreshSources();
                this.refreshButtons();
            } else {
                this.selected = -1;
                this.refreshSources();
                this.refreshButtons();
            }
        }
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
        super.mouseClickMove(mouseX, mouseY, clickedMouseButton, timeSinceLastClick);
        if (this.dragging && clickedMouseButton == 0) {
            CustomWidgetLayout.Widget w = this.selected();
            if (w != null) {
                w.x = this.toVx(mouseX) - this.dragOffX;
                w.y = this.toVy(mouseY) - this.dragOffY;
                w.clamp();
            }
        }
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        super.mouseReleased(mouseX, mouseY, state);
        this.dragging = false;
    }

    // ---- 绘制 ----

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.drawDefaultBackground();
        int bx = this.boardX();
        int by = this.boardY();
        int sc = this.scale();
        int bw = 256 * sc;
        int bh = 192 * sc;
        // 面板底
        com.jiezhirbmkmod.client.util.GuiDraw.drawRect(bx - 2, by - 2, bx + bw + 2, by + bh + 2, 0xFF232830);
        // 虚拟屏底 + 网格
        com.jiezhirbmkmod.client.util.GuiDraw.drawRect(bx, by, bx + bw, by + bh, 0xFF101418);
        for (int gx = 0; gx <= 256; gx += 16) {
            com.jiezhirbmkmod.client.util.GuiDraw.vline(bx + gx * sc, by, by + bh, 0x30FFFFFF);
        }
        for (int gy = 0; gy <= 192; gy += 16) {
            com.jiezhirbmkmod.client.util.GuiDraw.hline(bx, bx + bw, by + gy * sc, 0x30FFFFFF);
        }
        // 小部件
        for (int i = 0; i < this.layout.widgets().size(); ++i) {
            CustomWidgetLayout.Widget w = this.layout.widgets().get(i);
            int x = bx + w.x * sc;
            int y = by + w.y * sc;
            int ww = w.w * sc;
            int wh = w.h * sc;
            int fill;
            switch (w.type) {
                case CustomWidgetLayout.TYPE_GRAPH: fill = 0x6616C60C; break;
                case CustomWidgetLayout.TYPE_TEXT: fill = 0x6684C1FF; break;
                default: fill = 0x66F9F1A5; break;
            }
            com.jiezhirbmkmod.client.util.GuiDraw.drawRect(x, y, x + ww, y + wh, fill);
            int border = i == this.selected ? 0xFFFF9F1A : 0xFFFFFFFF;
            com.jiezhirbmkmod.client.util.GuiDraw.hline(x, x + ww - 1, y, border);
            com.jiezhirbmkmod.client.util.GuiDraw.hline(x, x + ww - 1, y + wh - 1, border);
            com.jiezhirbmkmod.client.util.GuiDraw.vline(x, y, y + wh - 1, border);
            com.jiezhirbmkmod.client.util.GuiDraw.vline(x + ww - 1, y, y + wh - 1, border);
            String label = w.type == CustomWidgetLayout.TYPE_GAUGE ? "仪表#" + (w.slot + 1)
                    : w.type == CustomWidgetLayout.TYPE_GRAPH ? "曲线·ch" + w.channel
                    : "文本#" + (w.slot + 1);
            this.fontRenderer.drawStringWithShadow(label, x + 3, y + 2,
                    i == this.selected ? 0xFFFFE08A : 0xFFE0E0E0);
        }
        // 标题与状态
        this.fontRenderer.drawStringWithShadow("自定义页布局编辑器（拖动移动 · 缩放/类型在右侧）",
                bx, by + bh + 4, 0xFFE0E0E0);
        if (!this.status.isEmpty()) {
            this.fontRenderer.drawStringWithShadow(this.status, bx, by + bh + 16, 0xFF9FE89F);
        }
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (keyCode == 1) {
            this.mc.displayGuiScreen(null);
        }
        // 方向键微调选中部件
        CustomWidgetLayout.Widget w = this.selected();
        if (w != null) {
            if (keyCode == 200) w.y = Math.max(0, w.y - 2);
            else if (keyCode == 208) w.y = Math.min(CustomWidgetLayout.CANVAS_H - w.h, w.y + 2);
            else if (keyCode == 203) w.x = Math.max(0, w.x - 2);
            else if (keyCode == 205) w.x = Math.min(CustomWidgetLayout.CANVAS_W - w.w, w.x + 2);
        }
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
