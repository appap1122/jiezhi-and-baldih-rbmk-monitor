package com.jiezhimod.client.dfc;

import com.jiezhimod.client.dfc.ScreenFonts;
import com.jiezhimod.dfc.DfcFormat;
import com.jiezhimod.dfc.DfcWatch;
import com.jiezhimod.dfc.RbmkSnapshot;
import com.jiezhimod.dfc.ScreenLayout;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.ITextureObject;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;

@SideOnly(value=Side.CLIENT)
public final class ScreenCanvas {
    private static final int LW = 256;
    private static final int LH = 192;
    private static final int SS = 3;
    public static final int TEX_W = 768;
    public static final int TEX_H = 576;
    private static final int BG = -16513269;
    private static final int PANEL = -16051687;
    private static final int LINE = -14928312;
    private static final int TXT = -2299664;
    private static final int TXT_DIM = -8480856;
    private static final int OK = -11345782;
    private static final int WARN = -16067;
    private static final int CRIT = -46261;
    private static final int CYAN = -12003585;
    private static final int AXIS_TXT = -12298133;
    private static final int ALERT_LINE = -8512998;
    private static final int ALERT_BG = -14021877;
    private static final int ALERT_TXT = -30070;
    private static final int FAULT_BG = -15071738;
    private static final int FAULT_BAR = -12578801;
    private static final int FAULT_TXT = -3896694;
    private static final long BLINK_MS = 500L;
    public static final int PAGE_CORE = 1;
    public static final int PAGE_COMPONENTS = 2;
    public static final int PAGE_CUSTOM = 3;
    private static final int HEADER_H = 19;
    private static final int ROW_TOP = 23;
    private static final int ROW_BOTTOM = 188;
    private static final int COL_L_X = 4;
    private static final int COL_L_W = 124;
    private static final int COL_M_X = 132;
    private static final int COL_M_W = 60;
    private static final int COL_R_X = 164;
    private static final int COL_R_W = 88;
    private static final int GRID_N = 15;
    private static final int GRID_X = 8;
    private static final int GRID_Y = 30;
    private static final int GRID_CELL = 9;
    private static final int GRID_PX = 135;
    private static final int GRID_SIDE_X = 153;
    private static final int GRID_SIDE_W = 99;
    private final BufferedImage image;
    private final int[] pixels;
    private final Graphics2D g;
    private DynamicTexture texture;
    private int texId;
    private ByteBuffer uploadBuf;
    private ResourceLocation location = new ResourceLocation("jiezhimod", "rbmk_screen_default");
    private boolean pixelDirty = true;
    private long lastBucket = Long.MIN_VALUE;
    private long lastUploadMs;
    private boolean alert;
    private int themeLine = -14928312;
    private int themeTitle = -8480856;
    private long framePhaseMs;
    public static final long REFRESH_MS = 200L;
    private long mainStepMs = 200L;

    public ScreenCanvas() {
        this.image = new BufferedImage(768, 576, 2);
        this.pixels = new int[442368];
        this.g = this.image.createGraphics();
        this.g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        this.g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        this.g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        this.g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_OFF);
        this.g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
    }

    private static Font fCjk(int logical) {
        return ScreenFonts.cjk(logical * 3);
    }

    private static Font fCjkBold(int logical) {
        return ScreenFonts.cjkBold(logical * 3);
    }

    private static Font fMono(int logical) {
        return ScreenFonts.mono(logical * 3);
    }

    private static Font fMonoBold(int logical) {
        return ScreenFonts.monoBold(logical * 3);
    }

    public void render(long sinceOnMs, boolean powered, RbmkSnapshot data, List<String> events, int page) {
        this.render(sinceOnMs, powered, data, events, page, -1L);
    }

    public void render(long sinceOnMs, boolean powered, RbmkSnapshot data, List<String> events, int page, long faultMs) {
        long phase;
        long step = 50L;
        if (powered && faultMs < 0L && ScreenLayout.phaseAt(sinceOnMs) == 2) {
            step = this.mainStepMs;
        }
        this.framePhaseMs = phase = powered ? sinceOnMs : this.paintMs();
        long bucket = phase / step;
        if (bucket == this.lastBucket && !this.pixelDirty) {
            return;
        }
        this.lastBucket = bucket;
        this.alert = false;
        this.themeLine = -14928312;
        this.g.setColor(new Color(-16513269, true));
        this.g.fillRect(0, 0, 768, 576);
        if (!powered) {
            this.drawOff();
        } else if (faultMs >= 0L) {
            this.drawFault(faultMs);
        } else {
            int p = ScreenLayout.phaseAt(sinceOnMs);
            if (p == 0) {
                this.drawBoot(sinceOnMs);
            } else if (p == 1) {
                this.drawConsole(sinceOnMs);
            } else {
                this.drawMain(data, events, page);
            }
        }
        this.drawScanlines();
        this.image.getRGB(0, 0, 768, 576, this.pixels, 0, 768);
        this.pixelDirty = true;
    }

    public void setMainStepMs(long ms) {
        this.mainStepMs = Math.max(50L, ms);
    }

    public void uploadIfDirty(long nowMs) {
        boolean forced;
        boolean bl = forced = nowMs - this.lastUploadMs > 4000L;
        if (!this.pixelDirty && !forced) {
            return;
        }
        this.ensureTexture();
        if (this.uploadBuf != null) {
            this.uploadBuf.clear();
            this.uploadBuf.asIntBuffer().put(this.pixels);
            this.uploadBuf.position(0).limit(this.pixels.length * 4);
            GL13.glActiveTexture((int)33984);
            GlStateManager.func_179144_i((int)this.texId);
            GL11.glTexSubImage2D((int)3553, (int)0, (int)0, (int)0, (int)768, (int)576, (int)32993, (int)5121, (ByteBuffer)this.uploadBuf);
            this.applyTextureParams();
        }
        this.pixelDirty = false;
        this.lastUploadMs = nowMs;
    }

    private void ensureTexture() {
        if (this.texId > 0) {
            return;
        }
        this.texture = new DynamicTexture(768, 576);
        this.texId = this.texture.func_110552_b();
        GL13.glActiveTexture((int)33984);
        GlStateManager.func_179144_i((int)this.texId);
        GL11.glTexImage2D((int)3553, (int)0, (int)32856, (int)768, (int)576, (int)0, (int)32993, (int)5121, (ByteBuffer)null);
        this.applyTextureParams();
        this.uploadBuf = ByteBuffer.allocateDirect(0x1B0000).order(ByteOrder.nativeOrder());
        Minecraft.func_71410_x().func_110434_K().func_110579_a(this.location, (ITextureObject)this.texture);
        this.pixelDirty = true;
        this.lastUploadMs = 0L;
    }

    private void applyTextureParams() {
        if (this.texture == null) {
            return;
        }
        GlStateManager.func_179144_i((int)this.texture.func_110552_b());
        GlStateManager.func_187421_b((int)3553, (int)10241, (int)9729);
        GlStateManager.func_187421_b((int)3553, (int)10240, (int)9729);
        GlStateManager.func_187421_b((int)3553, (int)10242, (int)33071);
        GlStateManager.func_187421_b((int)3553, (int)10243, (int)33071);
    }

    public DynamicTexture texture() {
        this.ensureTexture();
        return this.texture;
    }

    public void setLocation(ResourceLocation loc) {
        if (loc != null) {
            this.location = loc;
        }
    }

    public void dispose() {
        if (this.texId > 0) {
            try {
                Minecraft.func_71410_x().func_110434_K().func_147645_c(this.location);
            }
            catch (Throwable throwable) {
                // empty catch block
            }
            if (this.texId > 0) {
                GL11.glDeleteTextures((int)this.texId);
            }
        }
        this.texture = null;
        this.texId = 0;
        this.uploadBuf = null;
        this.pixelDirty = true;
    }

    public void invalidate() {
        this.lastBucket = Long.MIN_VALUE;
        this.pixelDirty = true;
    }

    private void drawOff() {
        this.rect(0, 0, 256, 192, -15657444);
        this.glowFrame(256, 192, -13734260);
        this.textCenter("待机", 72, -8480856, ScreenCanvas.fCjk(12));
        this.textCenter("POWER", 90, -12957096, ScreenCanvas.fMono(10));
        this.textCenter("右键切换开机", 108, -14273474, ScreenCanvas.fCjk(8));
    }

    private void drawBoot(long ms) {
        this.rect(0, 0, 256, 192, -16513269);
        this.text("节枝OS RBMK 2.0", 12, 34, -2299664, ScreenCanvas.fCjkBold(11));
        int lines = ScreenLayout.BOOT_LOG.length;
        int visible = Math.min(lines, 2 + (int)(ms / 130L));
        for (int i = 0; i < visible; ++i) {
            this.text("  " + ScreenLayout.BOOT_LOG[i], 12, 56 + i * 13, i == 0 ? -2299664 : -8480856, ScreenCanvas.fCjk(8));
        }
        float prog = Math.min(1.0f, (float)ms / 700.0f);
        this.bar(12, 140, 232, 7, prog, -12003585);
        this.textRight((int)(prog * 100.0f) + "%", 244, 152, -8480856, ScreenCanvas.fMono(8));
        if (ms / 260L % 2L == 0L) {
            this.text("_", 66, 34, -12003585, ScreenCanvas.fCjkBold(11));
        }
    }

    private void drawConsole(long ms) {
        this.rect(0, 0, 256, 192, -16513269);
        this.textCenter("节枝OS", 40, -2299664, ScreenCanvas.fCjkBold(14));
        this.textCenter("自检通过 · 等待数据源", 64, -8480856, ScreenCanvas.fCjk(9));
        for (int i = 0; i < 3; ++i) {
            int c = (i * 3 + (int)(ms / 220L)) % 12;
            this.rect(112 + c * 3, 84, 2, 2, -12003585);
        }
    }

    private void drawFault(long faultMs) {
        this.rect(0, 0, 256, 192, -15071738);
        this.glowFrame(256, 192, -8776684);
        this.rect(0, 0, 256, 3, -2084816);
        this.rect(0, 189, 256, 3, -2084816);
        this.textCenter("熔 毁", 62, -46261, ScreenCanvas.fCjkBold(22));
        this.textCenter("堆芯温度超过熔毁阈值", 90, -3896694, ScreenCanvas.fCjk(9));
        this.textCenter("立即撤离或紧急插入控制棒", 104, -3896694, ScreenCanvas.fCjk(9));
        float p = (float)Math.max(0.0, Math.min(1.0, (double)faultMs / 4000.0));
        int x = 48;
        int w = 160;
        this.bar(x, 140, w, 6, p, -46261);
        this.textCenter("自动重启中 " + Math.round(p * 100.0f) + "%", 156, -8480856, ScreenCanvas.fCjk(8));
    }

    private void drawMain(RbmkSnapshot d, List<String> events, int page) {
        int alertLevel = ScreenLayout.alertLevel(d);
        if (alertLevel > 0) {
            this.alert = true;
            this.themeLine = -8512998;
            this.themeTitle = -30070;
        }
        this.drawHeader(d, page);
        if (page == 2) {
            this.drawGrid(d);
        } else if (page == 3) {
            this.drawCustom(d);
        } else {
            this.drawOverview(d, events);
        }
    }

    private void drawHeader(RbmkSnapshot d, int page) {
        String title = page == 2 ? "RBMK 堆芯网格" : (page == 3 ? "RBMK 自定义屏" : "RBMK 堆芯监视");
        this.rect(0, 0, 256, 19, this.alert ? -14021877 : -16116708);
        this.hline(0, 18, 256, this.themeLine);
        this.text(title, 6, 14, this.alert ? -30070 : -2299664, ScreenCanvas.fCjkBold(10));
        if (d != null && d.linked && !d.offline) {
            this.textRight(Clock.mmss(d.runTicks), 250, 14, -8480856, ScreenCanvas.fMono(9));
        } else if (d != null && d.offline) {
            this.textRight("离线", 250, 14, -16067, ScreenCanvas.fCjk(9));
        }
        if (this.alert && ScreenLayout.blinkOn(this.framePhaseMs)) {
            this.text("!!", 206, 14, -46261, ScreenCanvas.fMonoBold(11));
            this.hline(0, 0, 256, -46261);
        }
    }

    private void drawOverview(RbmkSnapshot d, List<String> events) {
        int lx = 4;
        int lw = 124;
        if (d == null) {
            this.panel(lx, 23, lw, 60);
            this.text("无数据", lx + 8, 47, -8480856, ScreenCanvas.fCjk(9));
            this.drawEventLog(164, 23, 88, events);
            return;
        }
        this.panel(lx, 23, lw, 52);
        if (!d.linked) {
            this.text("数据源未绑定", lx + 8, 41, -16067, ScreenCanvas.fCjkBold(10));
            this.text("遥控器右键控制台", lx + 8, 57, -8480856, ScreenCanvas.fCjk(8));
        } else if (d.offline) {
            this.text("离线", lx + 8, 41, -16067, ScreenCanvas.fCjkBold(10));
            this.text("扫描区无柱子", lx + 8, 57, -8480856, ScreenCanvas.fCjk(8));
        } else if (d.meltdown) {
            this.text("熔毁告警！", lx + 8, 41, -46261, ScreenCanvas.fCjkBold(10));
        } else if (d.cold) {
            this.text("冷堆", lx + 8, 41, -8480856, ScreenCanvas.fCjkBold(10));
            this.text("平均柱温 < 100°C", lx + 8, 57, -8480856, ScreenCanvas.fCjk(8));
        } else {
            this.text("运行中", lx + 8, 41, -11345782, ScreenCanvas.fCjkBold(10));
            this.text("柱 " + d.columnCount + " · 燃料 " + d.fuelCount + " · 棒 " + d.controlCount, lx + 8, 57, -8480856, ScreenCanvas.fCjk(8));
        }
        int y = 81;
        this.panel(lx, y, lw, 118);
        int y2 = y + 10;
        this.row(lx + 8, y2, lw - 16, "平均柱温", DfcFormat.temperature(d.avgHeat), this.tempColor(d.avgHeat, d.maxHeatLimit));
        this.textRight(ScreenCanvas.tempRate(d.tempDelta), lx + lw - 8, y2 + 10, d.tempDelta >= 5.0 ? -46261 : (d.tempDelta <= -5.0 ? -11345782 : -8480856), ScreenCanvas.fMono(7));
        this.row(lx + 8, y2 += 22, lw - 16, "最高柱温", DfcFormat.temperature(d.maxHeat), d.meltRisk >= 0.85 ? -46261 : (d.meltRisk >= 0.7 ? -16067 : -2299664));
        this.bar(lx + 8, y2 + 11, lw - 16, 4, (float)(d.maxHeatLimit > 0.0 ? d.meltRisk : 0.0), ScreenCanvas.heatBarColor(d.meltRisk));
        this.textRight("阈 " + DfcFormat.temperature(d.maxHeatLimit), lx + lw - 8, y2 + 20, -8480856, ScreenCanvas.fMono(7));
        this.row(lx + 8, y2 += 28, lw - 16, "中子通量", DfcFormat.si(d.flux, "F"), d.flux > 0 ? -12003585 : -8480856);
        this.row(lx + 8, y2 + 14, lw - 16, "蒸汽总量", DfcFormat.amount(d.steamTotal), -2299664);
        this.row(lx + 8, y2 += 32, lw - 16, "燃料平均富集度", DfcFormat.percent(d.avgEnrichment), d.avgEnrichment < 0.15 ? -16067 : -2299664);
        this.row(lx + 8, y2 + 14, lw - 16, "平均氙毒化", DfcFormat.percent(d.avgXenon), d.avgXenon > 0.6 ? -16067 : -2299664);
        this.row(lx + 8, y2 + 28, lw - 16, "控制棒抽出率", DfcFormat.percent(d.avgRodLevel), -2299664);
        this.drawRiskDial(132, 23, 60, d);
        this.drawEventLog(164, 23, 88, events);
    }

    private void drawRiskDial(int x, int y, int w, RbmkSnapshot d) {
        this.panel(x, y, w, 100);
        this.text("熔毁风险", x + w / 2 - this.textWidthLog("熔毁风险", ScreenCanvas.fCjk(8)) / 2, y + 12, -8480856, ScreenCanvas.fCjk(8));
        int cx = x + w / 2;
        int cy = y + 60;
        int r = 26;
        this.ovalStroke(cx - r, cy - r, r * 2, r * 2, -14928312, 4);
        double risk = d.meltRisk;
        if (risk > 0.0) {
            this.g.setColor(new Color(ScreenCanvas.heatBarColor(risk), true));
            this.g.fillArc((cx - r) * 3, (cy - r) * 3, r * 2 * 3, r * 2 * 3, 90, (int)Math.round(-360.0 * Math.min(1.0, risk)));
        }
        this.textCenterIn(DfcFormat.percent(risk), x, w, cy + 5, risk >= 0.85 ? -46261 : (risk >= 0.7 ? -16067 : -2299664), ScreenCanvas.fMonoBold(11));
    }

    private void drawGrid(RbmkSnapshot d) {
        boolean haveGrid;
        this.panel(6, 28, 139, 139);
        boolean bl = haveGrid = d != null && d.linked && !d.offline && d.includeGrid && !d.grid.isEmpty();
        if (!haveGrid) {
            this.textCenter("无网格数据", 97, -8480856, ScreenCanvas.fCjk(9));
            if (d != null && d.includeGrid && d.grid.isEmpty()) {
                this.textCenter("（绑定到控制台后显示）", 113, -14273474, ScreenCanvas.fCjk(8));
            }
        } else {
            for (int i = 0; i < 15; ++i) {
                this.text(String.valueOf(i), 8 + i * 9 + 2, 23, -12298133, ScreenCanvas.fMono(6));
                this.text(String.valueOf(i), -1, 30 + i * 9 + 7, -12298133, ScreenCanvas.fMono(6));
            }
            for (RbmkSnapshot.RbmkCell c : d.grid) {
                int col = c.index % 15;
                int row = c.index / 15;
                this.drawCell(8 + col * 9, 30 + row * 9, c);
            }
        }
        int sx = 153;
        int sw = 99;
        this.panel(sx, 28, sw, 139);
        int yy = 38;
        this.text("堆芯统计", sx + 8, yy, -2299664, ScreenCanvas.fCjkBold(9));
        yy += 16;
        if (d == null) {
            this.text("无数据", sx + 8, yy, -8480856, ScreenCanvas.fCjk(8));
            return;
        }
        this.row(sx + 8, yy, sw - 16, "柱数", String.valueOf(d.columnCount), -2299664);
        this.row(sx + 8, yy + 13, sw - 16, "平均柱温", DfcFormat.temperature(d.avgHeat), this.tempColor(d.avgHeat, d.maxHeatLimit));
        this.row(sx + 8, yy + 26, sw - 16, "最高柱温", DfcFormat.temperature(d.maxHeat), d.meltRisk >= 0.85 ? -46261 : -2299664);
        this.row(sx + 8, yy += 46, sw - 16, "燃料柱", String.valueOf(d.fuelCount), -2299664);
        this.row(sx + 8, yy + 13, sw - 16, "控制棒", String.valueOf(d.controlCount), -2299664);
        this.row(sx + 8, yy + 26, sw - 16, "锅炉柱", String.valueOf(d.boilerCount), -2299664);
        this.row(sx + 8, yy + 39, sw - 16, "富集度", DfcFormat.percent(d.avgEnrichment), -8480856);
        this.row(sx + 8, yy += 56, sw - 16, "氙毒化", DfcFormat.percent(d.avgXenon), d.avgXenon > 0.6 ? -16067 : -8480856);
        this.row(sx + 8, yy + 13, sw - 16, "蒸汽", DfcFormat.amount(d.steamTotal), -8480856);
        int ly = 125;
        this.text("图例", sx + 8, ly, -8480856, ScreenCanvas.fCjk(8));
        int[] legend = new int[]{1, 3, 5, 6, 7};
        int lyy = ly + 10;
        for (int t : legend) {
            int cxx = sx + 8;
            this.rect(cxx, lyy, 6, 6, ScreenCanvas.typeColor(t));
            this.text(RbmkSnapshot.RbmkCell.typeName(t), cxx + 10, lyy + 6, -8480856, ScreenCanvas.fCjk(7));
            lyy += 11;
        }
    }

    private void drawCell(int px, int py, RbmkSnapshot.RbmkCell c) {
        int base = ScreenCanvas.typeColor(c.type);
        double ratio = c.heatLimit > 0.0 ? c.heat / c.heatLimit : 0.0;
        this.rect(px, py, 9, 9, base);
        if (ratio > 0.0) {
            float a = (float)Math.min((double)0.9f, (double)0.12f + ratio * (double)0.85f);
            this.rect(px, py, 9, 9, ScreenCanvas.argb(ScreenCanvas.heatBarColor(ratio), a));
        }
        if (ratio >= 0.7) {
            this.rect(px, py, 9, 1, -8016);
            this.rect(px, py + 9 - 1, 9, 1, -8016);
            this.rect(px, py, 1, 9, -8016);
            this.rect(px + 9 - 1, py, 1, 9, -8016);
        }
    }

    private static String tempRate(double perSecond) {
        double abs = Math.abs(perSecond);
        if (abs < 0.05) {
            return "0.0°C/s";
        }
        return String.format(Locale.ROOT, "%+.1f°C/s", perSecond);
    }

    private static int typeColor(int type) {
        switch (type) {
            case 1: 
            case 2: 
            case 10: {
                return -14404537;
            }
            case 3: 
            case 4: {
                return -12965350;
            }
            case 5: {
                return -15058374;
            }
            case 6: {
                return -14013894;
            }
            case 7: 
            case 8: {
                return -13423814;
            }
            case 9: {
                return -12963034;
            }
            case 11: 
            case 12: 
            case 13: {
                return -14274514;
            }
        }
        return -16118511;
    }

    private void drawCustom(RbmkSnapshot d) {
        this.panel(4, 23, 248, 150);
        int y = 35;
        List<DfcWatch> ws = d == null ? null : d.watches;
        int shown = 0;
        if (ws != null) {
            for (DfcWatch w : ws) {
                if (y > 176) break;
                if (w.kind == 0) continue;
                this.text(w.device, 12, y, w.alive ? -8480856 : -14273474, ScreenCanvas.fCjk(8));
                String value = w.alive ? w.value : "离线";
                int vc = w.alive ? -2299664 : -14273474;
                Font vf = ScreenCanvas.hasCjk(value) ? this.fitCjk(value, 70, 10, 8) : this.fitMono(value, 70, 10, 7, false);
                this.textRight(value, 246, y, vc, vf);
                if (w.alive && w.ratio >= 0.0) {
                    this.bar(12, y + 10, 232, 3, (float)w.ratio, w.ratio >= (double)0.85f ? -16067 : -12003585);
                }
                this.hline(12, y + 15, 232, -15457746);
                y += 18;
                ++shown;
            }
        }
        if (shown == 0) {
            this.text("没有数据槽", 12, 63, -8480856, ScreenCanvas.fCjk(9));
            this.text("遥控器蹲下右键储罐/电池", 12, 79, -8480856, ScreenCanvas.fCjk(8));
            this.text("再蹲下右键本屏接入", 12, 91, -8480856, ScreenCanvas.fCjk(8));
        }
    }

    private void drawEventLog(int x, int y, int w, List<String> events) {
        this.panel(x, y, w, 100);
        this.text("事件日志", x + 6, y + 12, this.themeTitle, ScreenCanvas.fCjk(8));
        this.hline(x + 4, y + 17, w - 8, this.themeLine);
        if (events == null || events.isEmpty()) {
            this.text("无事件", x + 8, y + 34, -8480856, ScreenCanvas.fCjk(8));
            return;
        }
        Font f = ScreenCanvas.fCjk(7);
        ArrayList<String> rows = new ArrayList<String>();
        for (String s : events) {
            rows.addAll(this.wrap(s, w - 14, f));
        }
        int y2 = y + 17 + 9 * Math.min(rows.size(), 8) + 2;
        for (int i = rows.size() - 1; i >= 0 && y2 > y + 22; y2 -= 9, --i) {
            this.text((String)rows.get(i), x + 7, y2, i == rows.size() - 1 ? -2299664 : -8480856, f);
        }
    }

    private int tempColor(double heat, double limit) {
        double ratio = limit > 0.0 ? heat / limit : 0.0;
        return ScreenCanvas.heatBarColor(ratio);
    }

    private static int heatBarColor(double ratio) {
        if (ratio >= 0.85) {
            return -46261;
        }
        if (ratio >= 0.7) {
            return -24822;
        }
        if (ratio >= 0.5) {
            return -16067;
        }
        return -11345782;
    }

    private void drawScanlines() {
        this.g.setColor(new Color(0x8000000, true));
        for (int y = 0; y < 192; y += 3) {
            this.g.fillRect(0, y * 3, 768, 3);
        }
    }

    private void glowFrame(int w, int h, int color) {
        this.rect(0, 0, w, 2, ScreenCanvas.argb(color, 0.55f));
        this.rect(0, h - 2, w, 2, ScreenCanvas.argb(color, 0.55f));
        this.rect(0, 0, 2, h, ScreenCanvas.argb(color, 0.55f));
        this.rect(w - 2, 0, 2, h, ScreenCanvas.argb(color, 0.55f));
    }

    private void rect(int x, int y, int w, int h, int argb) {
        this.g.setColor(new Color(argb, true));
        this.g.fillRect(x * 3, y * 3, w * 3, h * 3);
    }

    private void rectRight(int rightX, int y, int w, int h, int argb) {
        this.rect(rightX - w, y, w, h, argb);
    }

    private void panel(int x, int y, int w, int h) {
        this.rect(x, y, w, h, -16051687);
        this.rect(x, y, w, 1, this.themeLine);
        this.rect(x, y + h - 1, w, 1, this.themeLine);
        this.rect(x, y, 1, h, this.themeLine);
        this.rect(x + w - 1, y, 1, h, this.themeLine);
    }

    private void hline(int x, int y, int w, int argb) {
        this.rect(x, y, w, 1, argb);
    }

    private void ovalStroke(int x, int y, int w, int h, int argb, int thickness) {
        this.g.setColor(new Color(argb, true));
        this.g.setStroke(new BasicStroke(thickness));
        this.g.drawOval(x * 3, y * 3, w * 3, h * 3);
        this.g.setStroke(new BasicStroke(1.0f));
    }

    private void bar(int x, int y, int w, int h, float ratio, int color) {
        this.rect(x, y, w, h, -15589333);
        int fw = Math.round((float)w * Math.max(0.0f, Math.min(1.0f, ratio)));
        if (fw == 0 && ratio > 0.0f) {
            fw = 1;
        }
        if (fw > 0) {
            this.rect(x, y, fw, h, color);
        }
    }

    private void row(int x, int y, int width, String label, String value, int valueColor) {
        Font vf;
        Font lf = ScreenCanvas.fCjk(9);
        this.text(label, x, y, -8480856, lf);
        int avail = Math.max(8, width - this.textWidthLog(label, lf) - 6);
        String vs = value == null ? "--" : value;
        Font font = vf = ScreenCanvas.hasCjk(vs) ? this.fitCjk(vs, avail, 9, 7) : this.fitMono(vs, avail, 9, 6, false);
        if (this.g.getFontMetrics(vf).stringWidth(vs) > avail * 3) {
            vs = this.clip(vs, avail, vf);
        }
        this.textRight(vs, x + width, y, valueColor, vf);
    }

    private static boolean hasCjk(String s) {
        for (int i = 0; i < s.length(); ++i) {
            if (s.charAt(i) < '⺀') continue;
            return true;
        }
        return false;
    }

    private Font fitMono(String s, int maxLogicalW, int maxSize, int minSize, boolean bold) {
        for (int sz = maxSize; sz > minSize; --sz) {
            Font f;
            Font font = f = bold ? ScreenCanvas.fMonoBold(sz) : ScreenCanvas.fMono(sz);
            if (this.g.getFontMetrics(f).stringWidth(s) > maxLogicalW * 3) continue;
            return f;
        }
        return bold ? ScreenCanvas.fMonoBold(minSize) : ScreenCanvas.fMono(minSize);
    }

    private Font fitCjk(String s, int maxLogicalW, int maxSize, int minSize) {
        for (int sz = maxSize; sz > minSize; --sz) {
            Font f = ScreenCanvas.fCjk(sz);
            if (this.g.getFontMetrics(f).stringWidth(s) > maxLogicalW * 3) continue;
            return f;
        }
        return ScreenCanvas.fCjk(minSize);
    }

    private void text(String s, int x, int y, int argb, Font f) {
        this.g.setFont(f);
        this.g.setColor(new Color(argb, true));
        this.g.drawString(s, x * 3, y * 3);
    }

    private void textRight(String s, int rightX, int y, int argb, Font f) {
        this.g.setFont(f);
        int pw = this.g.getFontMetrics(f).stringWidth(s);
        this.g.setColor(new Color(argb, true));
        this.g.drawString(s, rightX * 3 - pw, y * 3);
    }

    private void textCenter(String s, int cy, int argb, Font f) {
        this.g.setFont(f);
        FontMetrics fm = this.g.getFontMetrics(f);
        int w = fm.stringWidth(s);
        this.g.setColor(new Color(argb, true));
        this.g.drawString(s, 384 - w / 2, cy * 3);
    }

    private void textCenterIn(String s, int x, int w, int baselineY, int argb, Font f) {
        this.g.setFont(f);
        FontMetrics fm = this.g.getFontMetrics(f);
        int sw = fm.stringWidth(s);
        this.g.setColor(new Color(argb, true));
        this.g.drawString(s, (x + w / 2) * 3 - sw / 2, baselineY * 3);
    }

    private int textWidthLog(String s, Font f) {
        return (int)Math.ceil((double)this.g.getFontMetrics(f).stringWidth(s) / 3.0);
    }

    private String clip(String s, int maxLogicalWidth, Font f) {
        int i;
        FontMetrics fm = this.g.getFontMetrics(f);
        if (fm.stringWidth(s) <= maxLogicalWidth * 3) {
            return s;
        }
        String ell = "..";
        for (i = s.length(); i > 0 && fm.stringWidth(s.substring(0, i) + ell) > maxLogicalWidth * 3; --i) {
        }
        return s.substring(0, Math.max(0, i)) + ell;
    }

    private List<String> wrap(String s, int maxLogicalWidth, Font f) {
        ArrayList<String> lines = new ArrayList<String>();
        if (s == null || s.isEmpty()) {
            lines.add("");
            return lines;
        }
        FontMetrics fm = this.g.getFontMetrics(f);
        int maxPx = Math.max(1, maxLogicalWidth * 3);
        int start = 0;
        while (start < s.length()) {
            int w;
            int end;
            int lastBreak = -1;
            for (end = start; end < s.length() && (w = fm.stringWidth(s.substring(start, end + 1))) <= maxPx; ++end) {
                if (s.charAt(end) != ' ') continue;
                lastBreak = end;
            }
            if (end >= s.length()) {
                lines.add(s.substring(start));
                break;
            }
            int cut = lastBreak > start ? lastBreak : (end > start ? end : start + 1);
            lines.add(s.substring(start, cut));
            for (start = cut; start < s.length() && s.charAt(start) == ' '; ++start) {
            }
        }
        return lines;
    }

    private static int argb(int color, float alpha) {
        int a = color >>> 24 & 0xFF;
        int na = Math.max(0, Math.min(255, Math.round((float)a * alpha)));
        return na << 24 | color & 0xFFFFFF;
    }

    private long paintMs() {
        return System.currentTimeMillis();
    }

    private static final class Clock {
        private Clock() {
        }

        static String mmss(int ticks) {
            int secs = Math.max(0, ticks) / 20;
            return String.format("%02d:%02d", secs / 60, secs % 60);
        }
    }
}

