package com.jiezhirbmkmod.client.gui;

import java.io.File;
import java.io.PrintWriter;

import com.jiezhirbmkmod.block.TileRbmkMonitorScreen;
import com.jiezhirbmkmod.client.RbmkExporter;
import com.jiezhirbmkmod.dfc.GraphHistory;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextFormatting;

/**
 * v18.2 运行报表一键生成（设置中心 → 运行报表）。
 *
 * <p>从客户端历史（{@link GraphHistory}）统计这一段运行期：最高/平均
 * 柱温、最大通量、报警次数（预警/严重）、数据断口数、运行时长，一键
 * 导出 CSV（明细帧）+ HTML（汇总页）到 {@code rbmk_export/}。纯客户端，
 * 导出路径与 /rbmkexport、历史导出面板一致。</p>
 */
public class GuiRunReport
extends GuiScreen {

    private static final int PANEL_W = 232;
    private static final int PANEL_H = 176;

    private static final int BTN_CSV = 0;
    private static final int BTN_HTML = 1;
    private static final int BTN_CLOSE = 2;

    private final BlockPos screenPos;

    public GuiRunReport(BlockPos screenPos) {
        this.screenPos = screenPos;
    }

    private GraphHistory history() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.world != null && mc.world.getTileEntity(this.screenPos) instanceof TileRbmkMonitorScreen) {
            return ((TileRbmkMonitorScreen) mc.world.getTileEntity(this.screenPos)).canvas().history();
        }
        return null;
    }

    @Override
    public void initGui() {
        super.initGui();
        int x = this.width / 2 - PANEL_W / 2;
        int y = this.height / 2 - PANEL_H / 2;
        int bw = (PANEL_W - 24) / 2;
        this.buttonList.add(new GuiButton(BTN_CSV, x + 10, y + PANEL_H - 74, bw, 20,
                com.jiezhirbmkmod.client.util.GuiDraw.safe("gui.jiezhirbmkmod.report.csv")));
        this.buttonList.add(new GuiButton(BTN_HTML, x + PANEL_W - 10 - bw, y + PANEL_H - 74, bw, 20,
                com.jiezhirbmkmod.client.util.GuiDraw.safe("gui.jiezhirbmkmod.report.html")));
        this.buttonList.add(new GuiButton(BTN_CLOSE, x + 10, y + PANEL_H - 46, PANEL_W - 20, 20,
                com.jiezhirbmkmod.client.util.GuiDraw.safe("gui.jiezhirbmkmod.export.close")));
    }

    /** 统计快照：标题行 + 数值行（CSV 与 HTML 共用） */
    private String[] stats(GraphHistory h) {
        int n = h.size();
        double maxHeat = 0.0D;
        double sumHeat = 0.0D;
        int heatN = 0;
        double maxFlux = 0.0D;
        int warn = 0;
        int crit = 0;
        int gaps = 0;
        boolean inGap = false;
        for (int i = 0; i < n; ++i) {
            if (h.gapAt(i)) {
                if (!inGap) {
                    ++gaps;
                    inGap = true;
                }
                continue;
            }
            inGap = false;
            double heat = h.valueAt(0, i);
            if (!Double.isNaN(heat)) {
                if (heat > maxHeat) {
                    maxHeat = heat;
                }
                sumHeat += heat;
                ++heatN;
            }
            double flux = h.valueAt(1, i);
            if (!Double.isNaN(flux) && flux > maxFlux) {
                maxFlux = flux;
            }
            byte lv = h.alertAt(i);
            if (lv == 1) {
                ++warn;
            } else if (lv >= 2) {
                ++crit;
            }
        }
        double avgHeat = heatN > 0 ? sumHeat / heatN : 0.0D;
        double seconds = n * (double) GraphHistory.SAMPLE_MS / 1000.0D;
        return new String[]{
                "\u8fd0\u884c\u62a5\u8868", // title
                String.format(java.util.Locale.ROOT, "%.1f", Double.valueOf(seconds)),
                String.valueOf(n),
                String.format(java.util.Locale.ROOT, "%.0f", Double.valueOf(maxHeat)),
                String.format(java.util.Locale.ROOT, "%.0f", Double.valueOf(avgHeat)),
                String.format(java.util.Locale.ROOT, "%.0f", Double.valueOf(maxFlux)),
                String.valueOf(warn),
                String.valueOf(crit),
                String.valueOf(gaps)
        };
    }

    private static final String[] CSV_HEADER = new String[]{
            "运行报表", "时长(秒)", "采样点", "最高柱温°C", "平均柱温°C", "最大通量F", "预警次数", "严重次数", "数据断口"};

    private void doExport(boolean html) {
        GraphHistory h = this.history();
        if (h == null || !h.hasData()) {
            GuiRbmkExport.chat(TextFormatting.RED,
                    com.jiezhirbmkmod.client.util.GuiDraw.safe(h == null
                            ? "msg.jiezhirbmkmod.export.gone"
                            : "msg.jiezhirbmkmod.export.no_data"));
            return;
        }
        String[] s = this.stats(h);
        try {
            File dir = new File(Minecraft.getMinecraft().gameDir, RbmkExporter.DIR_NAME);
            if (!dir.exists() && !dir.mkdirs()) {
                throw new IllegalStateException("cannot create " + dir);
            }
            String stamp = new java.text.SimpleDateFormat("yyyyMMdd-HHmmss").format(new java.util.Date());
            File out = new File(dir, "report-" + stamp + (html ? ".html" : ".csv"));
            try (PrintWriter w = new PrintWriter(out, "UTF-8")) {
                if (html) {
                    w.println("<!DOCTYPE html><html><head><meta charset=\"utf-8\">");
                    w.println("<title>" + s[0] + " " + stamp + "</title>");
                    w.println("<style>body{font-family:sans-serif;background:#f4f4f4;margin:40px}"
                            + "table{border-collapse:collapse;background:#fff}"
                            + "td,th{border:1px solid #ccc;padding:8px 14px}th{background:#eee}</style></head><body>");
                    w.println("<h1>RBMK " + s[0] + "</h1><table>");
                    for (int i = 1; i < CSV_HEADER.length; ++i) {
                        w.println("<tr><th>" + CSV_HEADER[i] + "</th><td>" + s[i] + "</td></tr>");
                    }
                    w.println("</table><p>生成时间：" + stamp
                            + " · 屏坐标 " + this.screenPos + "</p></body></html>");
                } else {
                    w.println("\uFEFF" + String.join(",", CSV_HEADER));
                    w.println(String.join(",", s));
                }
            }
            GuiRbmkExport.chat(TextFormatting.GREEN,
                    com.jiezhirbmkmod.client.util.GuiDraw.safe("gui.jiezhirbmkmod.report.saved", out.getName()));
            GuiRbmkExport.chat(TextFormatting.GRAY, dir.getAbsolutePath());
            this.mc.displayGuiScreen(null);
        }
        catch (Throwable t) {
            GuiRbmkExport.chat(TextFormatting.RED,
                    com.jiezhirbmkmod.client.util.GuiDraw.safe("gui.jiezhirbmkmod.report.fail", String.valueOf(t)));
        }
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == BTN_CSV) {
            this.doExport(false);
        } else if (button.id == BTN_HTML) {
            this.doExport(true);
        } else if (button.id == BTN_CLOSE) {
            this.mc.displayGuiScreen(null);
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.drawDefaultBackground();
        int x = this.width / 2 - PANEL_W / 2;
        int y = this.height / 2 - PANEL_H / 2;
        com.jiezhirbmkmod.client.util.GuiDraw.drawRect(x, y, x + PANEL_W, y + PANEL_H, 0xFF232830);
        com.jiezhirbmkmod.client.util.GuiDraw.hline(x, x + PANEL_W - 1, y, 0xFF555E6B);
        com.jiezhirbmkmod.client.util.GuiDraw.hline(x, x + PANEL_W - 1, y + PANEL_H - 1, 0xFF555E6B);
        com.jiezhirbmkmod.client.util.GuiDraw.vline(x, y, y + PANEL_H - 1, 0xFF555E6B);
        com.jiezhirbmkmod.client.util.GuiDraw.vline(x + PANEL_W - 1, y, y + PANEL_H - 1, 0xFF555E6B);
        com.jiezhirbmkmod.client.util.GuiDraw.text(this.fontRenderer,
                com.jiezhirbmkmod.client.util.GuiDraw.safe("gui.jiezhirbmkmod.report.title"),
                x + 8, y + 8, 0xFFE0E0E0);
        GraphHistory h = this.history();
        if (h != null && h.hasData()) {
            String[] s = this.stats(h);
            String[] rows = new String[]{
                    com.jiezhirbmkmod.client.util.GuiDraw.safe("gui.jiezhirbmkmod.report.r_dur", s[1]),
                    com.jiezhirbmkmod.client.util.GuiDraw.safe("gui.jiezhirbmkmod.report.r_samples", s[2]),
                    com.jiezhirbmkmod.client.util.GuiDraw.safe("gui.jiezhirbmkmod.report.r_maxheat", s[3]),
                    com.jiezhirbmkmod.client.util.GuiDraw.safe("gui.jiezhirbmkmod.report.r_avgheat", s[4]),
                    com.jiezhirbmkmod.client.util.GuiDraw.safe("gui.jiezhirbmkmod.report.r_maxflux", s[5]),
                    com.jiezhirbmkmod.client.util.GuiDraw.safe("gui.jiezhirbmkmod.report.r_alerts", s[6], s[7]),
                    com.jiezhirbmkmod.client.util.GuiDraw.safe("gui.jiezhirbmkmod.report.r_gaps", s[8])
            };
            int ry = y + 28;
            for (String row : rows) {
                com.jiezhirbmkmod.client.util.GuiDraw.text(this.fontRenderer, row, x + 10, ry, 0xFFC8CDD6);
                ry += 15;
            }
        } else {
            com.jiezhirbmkmod.client.util.GuiDraw.text(this.fontRenderer,
                    com.jiezhirbmkmod.client.util.GuiDraw.safe(h == null
                            ? "msg.jiezhirbmkmod.export.gone"
                            : "msg.jiezhirbmkmod.export.no_data"),
                    x + 10, y + 30, 0xFFE8B84A);
        }
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (keyCode == 1) {
            this.mc.displayGuiScreen(null);
        }
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
