package com.jiezhirbmkmod.client.gui;

import java.io.File;

import com.jiezhirbmkmod.block.TileRbmkMonitorScreen;
import com.jiezhirbmkmod.client.RbmkExporter;
import com.jiezhirbmkmod.client.util.GuiDraw;
import com.jiezhirbmkmod.dfc.GraphHistory;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;

/**
 * v18.0 剪辑式部分导出 GUI（像剪映）：从完整历史里用<b>白色选框</b>框选
 * 要导出的片段，上方大预览页配合「播放 / 暂停」逐帧预览，最后按
 * 「导出 CSV / PNG / .mp4」落到 {@code rbmk_export/}。
 *
 * <p>入口：导出设置面板的「导出部分历史图形」（图形模式）与
 * 「导出部分历史图形（视频）」（视频模式）。纯客户端 GUI，无容器无
 * 网络包；数据来自 {@link TileRbmkMonitorScreen#canvas()} 的客户端
 * 采样历史。</p>
 *
 * <p>交互约定（鼠标）：
 * <ul>
 * <li>时间轴上按住<b>左右白色手柄</b>（±5px）拖动 = 调整选区两端</li>
 * <li>按住<b>选框中间</b>拖动 = 整体平移选区（宽度不变，像剪映）</li>
 * <li>点击时间轴空白 = 最近的手柄跳到点击处</li>
 * </ul>
 * 播放：按真实采样节奏推进播放头（每个 {@link GraphHistory#SAMPLE_MS}
 * 一个采样），播到选区末端自动暂停并回到选区开头，循环预览。</p>
 *
 * <p>MP4 编码是重活（最多 1800 帧渲染），放后台 daemon 线程执行，
 * 完成/失败都发聊天栏提示；中途关闭 GUI 不打断编码。没装 JavaCV/FFmpeg
 * 依赖时自动降级为 PNG 帧序列 zip。</p>
 */
public class GuiRbmkClipEditor
extends GuiScreen {

    private static final int PANEL_W = 420;
    private static final int PANEL_H = 252;

    /** 预览区（大预览页）在面板内的矩形 */
    private static final int PREVIEW_H = 108;
    /** 时间轴在面板内的矩形 */
    private static final int TIMELINE_H = 44;

    private static final int BTN_PLAY = 0;
    private static final int BTN_CSV = 1;
    private static final int BTN_PNG = 2;
    private static final int BTN_MP4 = 3;
    private static final int BTN_CANCEL = 4;

    /** 预览滚动窗口最大长度（采样数）：再大曲线挤成一坨看不清 */
    private static final int PREVIEW_WINDOW = 240;

    /** 目标屏坐标（文件名用） */
    private final BlockPos screenPos;
    /** 客户端采样历史（打开时已判空） */
    private final GraphHistory h;
    /** true = 视频模式（只出 .mp4），false = 图形模式（CSV/PNG） */
    private final boolean videoMode;

    /** 白色选框 [selStart, selEnd)，采样下标，永远 selStart < selEnd */
    private int selStart;
    private int selEnd;
    /** 预览播放头（采样下标） */
    private int playhead;
    private boolean playing;
    private long lastFrameMs;

    /** 正在拖拽的手柄：-1 无 / 0 左 / 1 右 / 2 整框 */
    private int drag = -1;
    /** 拖整框时按下点相对选区头的偏移（采样数） */
    private int dragGrab;
    /** MP4 后台编码进行中（防重复点击；编码线程写、主线程读，必须 volatile） */
    private volatile boolean encoding;

    public GuiRbmkClipEditor(BlockPos screenPos, GraphHistory h, boolean videoMode) {
        this.screenPos = screenPos;
        this.h = h;
        this.videoMode = videoMode;
        int n = h.size();
        // 默认选区：最后 1/3 历史，像剪映新建序列给个合理初值
        int span = Math.max(1, n / 3);
        this.selStart = Math.max(0, n - span);
        this.selEnd = n;
        this.playhead = n;
    }

    // ---- 布局 ----

    private int panelX() {
        return this.width / 2 - PANEL_W / 2;
    }

    private int panelY() {
        return this.height / 2 - PANEL_H / 2;
    }

    private int previewY() {
        return this.panelY() + 20;
    }

    private int timelineY() {
        return this.previewY() + PREVIEW_H + 6;
    }

    /** 采样下标 → 时间轴 x 坐标 */
    private int idxToX(int idx) {
        int n = Math.max(1, this.h.size() - 1);
        return this.panelX() + 10 + (int) ((long) (PANEL_W - 20) * (long) idx / (long) n);
    }

    /** 时间轴 x 坐标 → 采样下标（钳位） */
    private int xToIdx(int mx) {
        int n = this.h.size();
        int rel = mx - (this.panelX() + 10);
        rel = Math.max(0, Math.min(PANEL_W - 20, rel));
        return (int) Math.round((double) n * (double) rel / (double) (PANEL_W - 20));
    }

    // ---- GUI 生命周期 ----

    @Override
    public void initGui() {
        super.initGui();
        int x = this.panelX();
        int y = this.panelY();
        int by = y + PANEL_H - 28;
        if (this.videoMode) {
            String mp4 = RbmkExporter.mp4Available()
                    ? GuiDraw.safe("gui.jiezhirbmkmod.clip.do_mp4")
                    : GuiDraw.safe("gui.jiezhirbmkmod.clip.do_mp4_zip");
            this.buttonList.add(new GuiButton(BTN_MP4, x + 10, by, 150, 20, mp4));
            this.buttonList.add(new GuiButton(BTN_CANCEL, x + PANEL_W - 106, by, 96, 20,
                    GuiDraw.safe("gui.jiezhirbmkmod.clip.cancel")));
            this.buttonList.add(new GuiButton(BTN_PLAY, x + 10, by - 26, 92, 20, this.playLabel()));
        } else {
            this.buttonList.add(new GuiButton(BTN_CSV, x + 10, by, 96, 20,
                    GuiDraw.safe("gui.jiezhirbmkmod.clip.do_csv")));
            this.buttonList.add(new GuiButton(BTN_PNG, x + 112, by, 96, 20,
                    GuiDraw.safe("gui.jiezhirbmkmod.clip.do_png")));
            this.buttonList.add(new GuiButton(BTN_CANCEL, x + PANEL_W - 106, by, 96, 20,
                    GuiDraw.safe("gui.jiezhirbmkmod.clip.cancel")));
            this.buttonList.add(new GuiButton(BTN_PLAY, x + 10, by - 26, 92, 20, this.playLabel()));
        }
    }

    private String playLabel() {
        return GuiDraw.safe(this.playing
                ? "gui.jiezhirbmkmod.clip.pause"
                : "gui.jiezhirbmkmod.clip.play");
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == BTN_PLAY) {
            this.togglePlay();
            button.displayString = this.playLabel();
        } else if (button.id == BTN_CSV) {
            this.doExport(false);
        } else if (button.id == BTN_PNG) {
            this.doExport(true);
        } else if (button.id == BTN_MP4) {
            this.doExportMp4();
        } else if (button.id == BTN_CANCEL) {
            this.mc.displayGuiScreen(null);
        }
    }

    private void togglePlay() {
        if (this.playing) {
            this.playing = false;
            return;
        }
        if (this.playhead >= this.selEnd - 1) {
            this.playhead = this.selStart;   // 播到尾了，从头预览
        }
        this.playing = true;
        this.lastFrameMs = System.currentTimeMillis();
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        if (!this.playing || this.h == null || this.h.size() <= 0) {
            return;
        }
        long now = System.currentTimeMillis();
        long dt = Math.min(250L, now - this.lastFrameMs); // 卡顿时最多补 250ms，不瞬移
        this.lastFrameMs = now;
        int step = (int) (dt / GraphHistory.SAMPLE_MS);
        if (step <= 0) {
            return;
        }
        this.playhead += step;
        if (this.playhead >= this.selEnd - 1) {
            this.playhead = this.selEnd - 1;
            this.playing = false;
            for (Object b : this.buttonList) {
                if (b instanceof GuiButton && ((GuiButton) b).id == BTN_PLAY) {
                    ((GuiButton) b).displayString = this.playLabel();
                }
            }
        }
    }

    // ---- 导出 ----

    private String clipSuffix() {
        int spanSec = (int) ((long) (this.selEnd - this.selStart)
                * GraphHistory.SAMPLE_MS / 1000L);
        return "_clip" + (spanSec > 0 ? "_" + spanSec + "s" : "");
    }

    private void doExport(boolean png) {
        try {
            File out = RbmkExporter.exportClip(this.screenPos, this.h,
                    this.selStart, this.selEnd, png, this.clipSuffix());
            GuiRbmkExport.chat(TextFormatting.GREEN, I18n.format("msg.jiezhirbmkmod.export.saved",
                    new File(Minecraft.getMinecraft().gameDir, RbmkExporter.DIR_NAME).getAbsolutePath()));
            GuiRbmkExport.chat(TextFormatting.GRAY, I18n.format("msg.jiezhirbmkmod.export.file",
                    out.getName(), Integer.valueOf(this.selEnd - this.selStart)));
            this.mc.displayGuiScreen(null);
        }
        catch (Throwable t) {
            GuiRbmkExport.chat(TextFormatting.RED,
                    I18n.format("msg.jiezhirbmkmod.export.fail", String.valueOf(t)));
        }
    }

    private void doExportMp4() {
        if (this.encoding) {
            return;
        }
        final int from = this.selStart;
        final int to = this.selEnd;
        this.encoding = true;
        GuiRbmkExport.chat(TextFormatting.AQUA, I18n.format("msg.jiezhirbmkmod.clip.exporting",
                Integer.valueOf(to - from)));
        Thread worker = new Thread(() -> {
            try {
                File out;
                try {
                    out = RbmkExporter.exportMp4Clip(GuiRbmkClipEditor.this.screenPos,
                            GuiRbmkClipEditor.this.h, from, to);
                    GuiRbmkExport.chat(TextFormatting.GREEN, I18n.format("msg.jiezhirbmkmod.clip.exported",
                            out.getName()));
                }
                catch (RbmkExporter.Mp4UnavailableException e) {
                    // FFmpeg 依赖没装（或本机库缺失）→ 帧序列 zip 降级
                    out = RbmkExporter.exportFramesZip(GuiRbmkClipEditor.this.screenPos,
                            GuiRbmkClipEditor.this.h, from, to);
                    GuiRbmkExport.chat(TextFormatting.YELLOW, I18n.format("msg.jiezhirbmkmod.clip.frames_zip",
                            out.getName()));
                }
            }
            catch (Throwable t) {
                GuiRbmkExport.chat(TextFormatting.RED,
                        I18n.format("msg.jiezhirbmkmod.export.fail", String.valueOf(t)));
            }
            finally {
                GuiRbmkClipEditor.this.encoding = false;
            }
        }, "rbmk-mp4-export");
        worker.setDaemon(true);
        worker.setPriority(Thread.MIN_PRIORITY + 1);
        worker.start();
        this.mc.displayGuiScreen(null);
    }

    // ---- 鼠标：白色选框拖拽 ----

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws java.io.IOException {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        if (mouseButton != 0 || !this.inTimeline(mouseX, mouseY)) {
            return;
        }
        int lx = this.idxToX(this.selStart);
        int rx = this.idxToX(this.selEnd);
        if (Math.abs(mouseX - lx) <= 5) {
            this.drag = 0;
        } else if (Math.abs(mouseX - rx) <= 5) {
            this.drag = 1;
        } else if (mouseX > lx && mouseX < rx) {
            this.drag = 2;
            this.dragGrab = this.xToIdx(mouseX) - this.selStart;
        } else {
            // 点空白：最近的手柄跳过去
            this.drag = Math.abs(mouseX - lx) <= Math.abs(mouseX - rx) ? 0 : 1;
            this.applyDrag(this.xToIdx(mouseX));
        }
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
        super.mouseClickMove(mouseX, mouseY, clickedMouseButton, timeSinceLastClick);
        if (this.drag >= 0 && this.inTimeline(mouseX, mouseY)) {
            this.applyDrag(this.xToIdx(mouseX));
        }
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        super.mouseReleased(mouseX, mouseY, state);
        this.drag = -1;
    }

    private void applyDrag(int idx) {
        int n = this.h.size();
        if (this.drag == 0) {
            this.selStart = Math.max(0, Math.min(this.selEnd - 1, idx));
        } else if (this.drag == 1) {
            this.selEnd = Math.min(n, Math.max(this.selStart + 1, idx));
        } else if (this.drag == 2) {
            int span = this.selEnd - this.selStart;
            int ns = Math.max(0, Math.min(n - span, idx - this.dragGrab));
            this.selStart = ns;
            this.selEnd = ns + span;
        }
        // 选区在播放头外时把播放头拉回选区内，预览永远有意义
        if (this.playhead < this.selStart || this.playhead >= this.selEnd) {
            this.playhead = this.selEnd - 1;
        }
    }

    private boolean inTimeline(int mx, int my) {
        return my >= this.timelineY() && my < this.timelineY() + TIMELINE_H
                && mx >= this.panelX() + 10 && mx < this.panelX() + PANEL_W - 10;
    }

    // ---- 绘制 ----

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.drawDefaultBackground();
        int x = this.panelX();
        int y = this.panelY();
        GuiDraw.drawRect(x, y, x + PANEL_W, y + PANEL_H, 0xFF1A1E26);
        GuiDraw.hline(x, x + PANEL_W - 1, y, 0xFF555E6B);
        GuiDraw.hline(x, x + PANEL_W - 1, y + PANEL_H - 1, 0xFF555E6B);
        GuiDraw.vline(x, y, y + PANEL_H - 1, 0xFF555E6B);
        GuiDraw.vline(x + PANEL_W - 1, y, y + PANEL_H - 1, 0xFF555E6B);
        GuiDraw.text(this.fontRenderer, GuiDraw.safe("gui.jiezhirbmkmod.clip.title"),
                x + 8, y + 7, 0xFFE0E0E0);
        GuiDraw.text(this.fontRenderer, GuiDraw.safe("gui.jiezhirbmkmod.clip.hint"),
                x + 8, y + PANEL_H - 46, 0xFF6B7280);

        this.drawPreview();
        this.drawTimeline(mouseX, mouseY);
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    /** 上方大预览页：当前播放头的滚动窗口，画热/通量两条曲线与读数 */
    private void drawPreview() {
        int x = this.panelX() + 10;
        int y = this.previewY();
        int w = PANEL_W - 20;
        // 预览窗口：以播放头为终点的固定长度窗口（与 MP4 帧同语义）
        int head = Math.max(this.selStart + 1, Math.min(this.selEnd, this.playhead + 1));
        int win = Math.min(Math.max(1, this.selEnd - this.selStart), PREVIEW_WINDOW);
        int tail = Math.max(this.selStart, head - win);
        int span = Math.max(1, head - tail);

        GuiDraw.drawRect(x, y, x + w, y + PREVIEW_H, 0xFF10141A);
        GuiDraw.hline(x, x + w, y, 0xFF3A424C);
        GuiDraw.hline(x, x + w, y + PREVIEW_H - 1, 0xFF3A424C);
        // 通道 0 热温（黄）与 1 通量（蓝），按各自量程归一
        this.drawChannel(x, y + 4, w, PREVIEW_H - 34, 0, tail, head, 0xB0FFC864);
        this.drawChannel(x, y + 4, w, PREVIEW_H - 34, 1, tail, head, 0xB064C8FF);
        // 播放头竖线（白色，铺满预览区）
        int hx = x + (int) ((long) (w - 1) * (long) (head - 1 - tail) / (long) Math.max(1, span - 1));
        GuiDraw.vline(hx, y, y + PREVIEW_H - 1, 0xFFFFFFFF);
        // 播放头读数：6 通道数值一行
        int ty = y + PREVIEW_H - 26;
        StringBuilder sb = new StringBuilder(96);
        for (int c = 0; c < GraphHistory.CHANNELS && sb.length() < 96; ++c) {
            if (this.h.gapAt(head - 1)) {
                continue;
            }
            sb.append(RbmkExporter.num(this.h.valueAt(c, head - 1))).append(c < GraphHistory.CHANNELS - 1 ? "  " : "");
        }
        GuiDraw.text(this.fontRenderer,
                this.h.gapAt(head - 1) ? GuiDraw.safe("gui.jiezhirbmkmod.clip.gap") : sb.toString(),
                x + 4, ty, 0xFFC8CDD6);
        GuiDraw.text(this.fontRenderer,
                String.format(java.util.Locale.ROOT, "%d/%d  (%.1fs)",
                        Integer.valueOf(head - this.selStart),
                        Integer.valueOf(this.selEnd - this.selStart),
                        Double.valueOf((head - this.selStart) * (double) GraphHistory.SAMPLE_MS / 1000.0D)),
                x + w - 90, ty, 0xFF848B96);
    }

    /** 预览里的一条通道折线：竖线段近似（1.12.2 GuiDraw 没有任意线段） */
    private void drawChannel(int x, int y, int w, int hgt, int ch, int fromIdx, int toIdx,
            int color) {
        double axisMax = Math.max(1.0D, this.h.axisMax(ch));
        int span = Math.max(1, toIdx - fromIdx);
        int prevY = Integer.MIN_VALUE;
        for (int i = 0; i < span; ++i) {
            int idx = fromIdx + i;
            if (this.h.gapAt(idx)) {
                prevY = Integer.MIN_VALUE;
                continue;
            }
            double v = Math.max(0.0D, Math.min(axisMax, this.h.valueAt(ch, idx)));
            int cy = y + hgt - 1 - (int) ((double) (hgt - 1) * (v / axisMax));
            int cx = x + (int) ((long) (w - 1) * (long) i / (long) Math.max(1, span - 1));
            if (prevY != Integer.MIN_VALUE) {
                int lo = Math.min(prevY, cy);
                int hi = Math.max(prevY, cy);
                for (int yy = lo; yy <= hi; ++yy) {
                    GuiDraw.drawRect(cx, yy, cx + 1, yy + 1, color);
                }
            } else {
                GuiDraw.drawRect(cx, cy, cx + 1, cy + 1, color);
            }
            prevY = cy;
        }
    }

    /** 下方时间轴：全历史缩略曲线 + 白色半透明选框 + 左右手柄 */
    private void drawTimeline(int mouseX, int mouseY) {
        int x = this.panelX() + 10;
        int y = this.timelineY();
        int w = PANEL_W - 20;
        GuiDraw.drawRect(x, y, x + w, y + TIMELINE_H, 0xFF10141A);
        // 全历史 heat 缩略
        this.drawChannel(x, y + 3, w, TIMELINE_H - 6, 0, 0, this.h.size(), 0x60FFC864);
        // 白色半透明选框
        int lx = this.idxToX(this.selStart);
        int rx = this.idxToX(this.selEnd);
        int top = y + 1;
        int bot = y + TIMELINE_H - 1;
        GuiDraw.drawRect(lx, top, rx, bot, 0x40FFFFFF);
        GuiDraw.vline(lx, top, bot, 0xFFFFFFFF);
        GuiDraw.vline(rx, top, bot, 0xFFFFFFFF);
        // 播放头在时间轴上的位置（青色）
        int ph = this.idxToX(Math.max(0, Math.min(this.h.size() - 1, this.playhead)));
        GuiDraw.vline(ph, top, bot, 0xFF40C8FF);
        // 手柄悬停放大提示
        if (Math.abs(mouseX - lx) <= 5 && mouseY >= top && mouseY <= bot) {
            GuiDraw.text(this.fontRenderer, "<", lx - 3, y - 10, 0xFFFFFFFF);
        }
        if (Math.abs(mouseX - rx) <= 5 && mouseY >= top && mouseY <= bot) {
            GuiDraw.text(this.fontRenderer, ">", rx - 3, y - 10, 0xFFFFFFFF);
        }
    }

    /** Esc 与取消同义；编码线程是 daemon，关 GUI 不打断 */
    @Override
    protected void keyTyped(char typedChar, int keyCode) throws java.io.IOException {
        super.keyTyped(typedChar, keyCode);
        if (keyCode == 1) {
            this.mc.displayGuiScreen(null);
        }
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
