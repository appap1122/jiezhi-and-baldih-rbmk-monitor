package com.jiezhirbmkmod.client.dfc;

import com.jiezhirbmkmod.client.dfc.ScreenFonts;
import com.jiezhirbmkmod.dfc.AlertPolicy;
import com.jiezhirbmkmod.dfc.DfcFormat;
import com.jiezhirbmkmod.dfc.DfcWatch;
import com.jiezhirbmkmod.dfc.GraphHistory;
import com.jiezhirbmkmod.dfc.RbmkSnapshot;
import com.jiezhirbmkmod.dfc.ScreenLayout;
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
public final class RbmkScreenCanvas {
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
    public static final int PAGE_GRAPH = 4;
    public static final int PAGE_TERM = 5;
    private static final int HEADER_H = 19;
    private static final int ROW_TOP = 23;
    private static final int ROW_BOTTOM = 188;
    private static final int COL_L_X = 4;
    private static final int COL_L_W = 124;
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
    private ResourceLocation location = new ResourceLocation("jiezhirbmkmod", "rbmk_screen_default");
    private boolean pixelDirty = true;
    private long lastBucket = Long.MIN_VALUE;
    private long lastUploadMs;
    private int themeLine = -14928312;
    private int themeTitle = -8480856;
    private long framePhaseMs;
    public static final long REFRESH_MS = 200L;
    private long mainStepMs = 200L;
    private final AlertOverlay alert = new AlertOverlay();

    /** v17.7 最近一次 setAlert 的等级（0/1/2），随采样落盘供告警标记线使用 */
    private int lastAlertLevel = 0;
    private static final long STROBE_MS = 120L;
    /** v18.0 修订 上一帧是不是"真实版断电黑屏"（给 renderRealPowerOff 节流） */
    private boolean lastRealOff = false;
    private final GraphHistory history = new GraphHistory();
    private long lastSampleMs = Long.MIN_VALUE;
    /**
     * v17.7 历史回放：>0 时曲线窗口终点往回挪这么多格采样（0 = 实时）。
     * 只影响绘制，不影响采样 —— 采样照常往 history 里推，回放只是
     * "把已经录下的东西倒带看"，松手（滚回 0）就是实时。
     */
    private int replayOffset = 0;
    private final java.util.List<com.jiezhirbmkmod.network.msg.MessageTerminalCommand.CmdLine> termLines = new java.util.ArrayList<com.jiezhirbmkmod.network.msg.MessageTerminalCommand.CmdLine>();
    private final java.util.HashMap<Font, FontMetrics> fmCache = new java.util.HashMap<Font, FontMetrics>();
    private static final java.util.HashMap<Font, Font> GLYPH_FALLBACK = new java.util.HashMap<Font, Font>();

    public RbmkScreenCanvas() {
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
        if (powered && this.alert.updateStrobe(sinceOnMs)) {
            step = Math.min(step > 50L ? step : STROBE_MS, STROBE_MS);
        } else if (powered && faultMs < 0L && ScreenLayout.phaseAt(sinceOnMs) == 2) {
            step = this.mainStepMs;
        }
        this.framePhaseMs = phase = powered ? sinceOnMs : this.paintMs();
        long bucket = phase / step;
        if (bucket == this.lastBucket && !this.pixelDirty) {
            return;
        }
        this.lastBucket = bucket;
        this.lastRealOff = false;
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
                // v18.0 修订：采样已从渲染路径挪走 —— 历史由客户端 tick
                // 驱动（ScreenAutoSampler，放置即自动记录），不再要求
                // "有人看着屏 + 停在曲线页"。这里只负责画。
                this.drawMain(data, events, page);
            }
        }
        if (powered && this.alert.strobeOn && !this.alert.blink(phase)) {
            this.rect(0, 0, 256, 192, 0x55FF2020);
            this.glowFrame(256, 192, -46261);
        }
        this.drawScanlines();
        this.image.getRGB(0, 0, 768, 576, this.pixels, 0, 768);
        // v18.2 显示滤镜：亮度衰减 + 屏显主题（琥珀/绿磷/纯白磷光），像素级一次过
        this.applyDisplayFilters();
        this.pixelDirty = true;
    }

    // ==================== v18.2 显示设置（设置中心）====================

    /** 屏幕背光亮度百分比（40~100），由渲染器每帧从 Tile 同步 */
    public volatile int displayBrightness = 100;
    /** 屏显主题：0=标准 1=琥珀磷光 2=绿磷 3=纯白 */
    public volatile int displayTheme = 0;
    /** 曲线对比模式：多通道叠加 */
    public volatile boolean graphCompare = false;
    /** 曲线对比模式：对数纵轴 */
    public volatile boolean graphLogScale = false;

    /**
     * 像素级显示滤镜：先按亮度整体衰减，再按主题重映射色相
     * （亮度加权平均 → 单色磷光）。只在脏区重绘时跑（约 5Hz），
     * 442k 像素纯整数运算开销可忽略。断电黑屏同样会被调暗——
     * 背光就是背光，断电文字也变暗是符合直觉的。
     */
    private void applyDisplayFilters() {
        int b = this.displayBrightness;
        int theme = this.displayTheme;
        if (b >= 100 && theme == 0) {
            return;
        }
        double k = Math.max(0.4D, Math.min(1.0D, b / 100.0D));
        int[] px = this.pixels;
        for (int i = 0; i < px.length; ++i) {
            int c = px[i];
            int a = (c >> 24) & 0xFF;
            if (a == 0) {
                continue;
            }
            int r = (int) (((c >> 16) & 0xFF) * k);
            int g = (int) (((c >> 8) & 0xFF) * k);
            int bl = (int) ((c & 0xFF) * k);
            if (theme == 1) {
                // 琥珀磷光：亮度映射到橙黄单色
                int lum = (r * 77 + g * 150 + bl * 29) >> 8;
                r = lum;
                g = lum * 62 / 100;
                bl = 0;
            } else if (theme == 2) {
                // 绿磷：经典 CRT 终端
                int lum = (r * 77 + g * 150 + bl * 29) >> 8;
                r = lum * 15 / 100;
                g = lum;
                bl = lum * 30 / 100;
            } else if (theme == 3) {
                // 纯白：去色 + 轻微提亮
                int lum = (r * 77 + g * 150 + bl * 29) >> 8;
                lum += (255 - lum) / 6;
                r = g = bl = lum;
            }
            px[i] = (a << 24) | (r << 16) | (g << 8) | bl;
        }
    }


    public void setAlert(int level, String cause) {
        this.alert.set(level, cause);
        // v17.7: 记下当前告警等级，采样器随曲线数据一起落盘（告警标记线的来源）
        this.lastAlertLevel = level;
    }

    /** command prompt page: the terminal tile mirrors its output lines here (client side) */
    public void setTermLines(List<com.jiezhirbmkmod.network.msg.MessageTerminalCommand.CmdLine> lines) {
        boolean changed;
        if (lines == null) {
            changed = !this.termLines.isEmpty();
            if (changed) {
                this.termLines.clear();
            }
        } else {
            changed = lines.size() != this.termLines.size();
            if (!changed) {
                for (int i = 0; i < lines.size(); ++i) {
                    com.jiezhirbmkmod.network.msg.MessageTerminalCommand.CmdLine a = lines.get(i);
                    com.jiezhirbmkmod.network.msg.MessageTerminalCommand.CmdLine b = this.termLines.get(i);
                    if (a.color != b.color || !a.text.equals(b.text)) {
                        changed = true;
                        break;
                    }
                }
            }
            if (changed) {
                this.termLines.clear();
                this.termLines.addAll(lines);
            }
        }
        if (changed) {
            this.invalidate();
        }
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
            GlStateManager.bindTexture((int)this.texId);
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
        this.texId = this.texture.getGlTextureId();
        GL13.glActiveTexture((int)33984);
        GlStateManager.bindTexture((int)this.texId);
        GL11.glTexImage2D((int)3553, (int)0, (int)32856, (int)768, (int)576, (int)0, (int)32993, (int)5121, (ByteBuffer)null);
        this.applyTextureParams();
        this.uploadBuf = ByteBuffer.allocateDirect(0x1B0000).order(ByteOrder.nativeOrder());
        Minecraft.getMinecraft().getTextureManager().loadTexture(this.location, (ITextureObject)this.texture);
        this.pixelDirty = true;
        this.lastUploadMs = 0L;
    }

    private void applyTextureParams() {
        if (this.texture == null) {
            return;
        }
        GlStateManager.bindTexture((int)this.texture.getGlTextureId());
        GlStateManager.glTexParameteri((int)3553, (int)10241, (int)9729);
        GlStateManager.glTexParameteri((int)3553, (int)10240, (int)9729);
        GlStateManager.glTexParameteri((int)3553, (int)10242, (int)33071);
        GlStateManager.glTexParameteri((int)3553, (int)10243, (int)33071);
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
        // v18.0 历史落盘：canvas 销毁（区块卸载/屏拆除/世界退出）前把
        // 这份历史立即写盘并注销 —— 下次进入世界由 seed() 接回。
        // 从没注册过的 canvas（非屏幕用途）在 HistoryStore 里是 no-op。
        com.jiezhirbmkmod.client.HistoryStore.checkpoint(this.history);
        if (this.texId > 0) {
            try {
                Minecraft.getMinecraft().getTextureManager().deleteTexture(this.location);
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

    // ---- v17.7 历史回放 ----

    /** 滚轮回放：一格滚轮 = 2 格采样（默认采样 500ms 即 1 秒）。正 = 倒回，负 = 前进。 */
    public int replayScroll(int notches) {
        if (notches == 0) {
            return this.replayOffset;
        }
        int step = notches > 0 ? 2 : -2;
        // 上限 CAPACITY-1：回放至少留 1 格，滚回 0 即恢复实时
        this.replayOffset = Math.max(0, Math.min(GraphHistory.CAPACITY - 1, this.replayOffset + step));
        return this.replayOffset;
    }

    /** 当前回放偏移（采样格数），0 = 实时 */
    public int replayOffset() {
        return this.replayOffset;
    }

    /** 客户端 GUI 读历史画小图用（巡屏中控的详情面板） */
    public GraphHistory history() {
        return this.history;
    }

    private void drawOff() {
        this.rect(0, 0, 256, 192, -15657444);
        this.glowFrame(256, 192, -13734260);
        this.textCenter("\u5f85\u673a", 72, -8480856, RbmkScreenCanvas.fCjk(12));
        this.textCenter("POWER", 90, -12957096, RbmkScreenCanvas.fMono(10));
        this.textCenter("\u53f3\u952e\u5f00\u673a \u00b7 \u8e72\u4e0b\u53f3\u952e\u8bbe\u7f6e\u544a\u8b66/\u7ea2\u77f3", 108, -14273474, RbmkScreenCanvas.fCjk(8));
    }

    /**
     * v18.0 修订 真实版断电画面：纯黑 + 提示文字。
     *
     * <p>旧版对真实屏断电是"什么都不画"——玩家只能看到一块空面板，
     * 分不清"还没通电"和"刚才断电了"。现在黑屏上写明状况与后果：
     * 断电期间曲线历史冻结不记录（见 autoSample）。</p>
     *
     * <p>与 render() 共用 pixelDirty/纹理管线：黑屏也是一帧画面，
     * 由渲染器的 uploadIfDirty 贴上 GPU。断电画面不随帧变化，
     * 画过一次且纹理干净就跳过，不给主循环加负担。</p>
     *
     * @param curve true = 曲线/轮播屏（文案点名"曲线显示屏"）
     */
    public void renderRealPowerOff(boolean curve) {
        if (this.lastRealOff && !this.pixelDirty) {
            return;
        }
        this.lastRealOff = true;
        // 18.1 自查修复：必须是不透明黑（alpha=0xFF）。画布是 TYPE_INT_ARGB，
        // 纹理上传后走 alpha 混合贴到面板上 —— 之前写的 new Color(0, true)
        // 是全透明像素，整屏上传后什么都看不见，黑屏文字根本显示不出来。
        this.g.setColor(new Color(0xFF000000, true));
        this.g.fillRect(0, 0, 768, 576);
        this.textCenter(curve ? "\u771f\u5b9e\u7248\u66f2\u7ebf\u663e\u793a\u5c4f\u65ad\u7535\uff0c\u65e0\u6cd5\u8bb0\u5f55"
                              : "\u771f\u5b9e\u7248\u663e\u793a\u5c4f\u65ad\u7535\uff0c\u65e0\u6cd5\u8bb0\u5f55",
                96, -9408400, RbmkScreenCanvas.fCjk(10));
        this.textCenter("\u6062\u590d\u4f9b\u7535\u540e\u81ea\u52a8\u7ee7\u7eed\u8bb0\u5f55", 118, -11200986, RbmkScreenCanvas.fCjk(8));
        this.image.getRGB(0, 0, 768, 576, this.pixels, 0, 768);
        this.pixelDirty = true;
    }

    private void drawBoot(long ms) {
        if (ms < 50L) {
            this.history.clear();
            this.lastSampleMs = Long.MIN_VALUE;
            this.replayOffset = 0; // 断电清历史，回放指针没有存在的意义了
        }
        this.rect(0, 0, 256, 192, -16513269);
        Font bootTitle = RbmkScreenCanvas.fCjkBold(11);
        this.text(ScreenLayout.OS_TITLE, 12, 34, -2299664, bootTitle);
        int lines = ScreenLayout.BOOT_LOG.length;
        int visible = Math.min(lines, 2 + (int)(ms / 130L));
        for (int i = 0; i < visible; ++i) {
            this.text("  " + ScreenLayout.BOOT_LOG[i], 12, 56 + i * 13, i == 0 ? -2299664 : -8480856, RbmkScreenCanvas.fCjk(8));
        }
        float prog = Math.min(1.0f, (float)ms / 700.0f);
        // 页脚排版：标题行（左"自检进度"、右百分比）压到进度条上方，两者都对齐到条的左右端
        // 以前百分比画在 y=152，墨迹 146..152 会压住条底边(147)并越出条右端，看着像贴歪了
        this.text("\u81ea\u68c0\u8fdb\u5ea6", 12, 133, -8480856, RbmkScreenCanvas.fCjk(8));
        this.textRight((int)(prog * 100.0f) + "%", 244, 133, -8480856, RbmkScreenCanvas.fMono(8));
        this.bar(12, 140, 232, 7, prog, -12003585);
        if (ms / 260L % 2L == 0L) {
            // cursor follows the real text width - x=66 used to sit in the middle of the title
            int cursorX = 13 + this.textWidthLog(ScreenLayout.OS_TITLE, bootTitle);
            this.text("_", cursorX, 34, -12003585, bootTitle);
        }
    }

    private void drawConsole(long ms) {
        this.rect(0, 0, 256, 192, -16513269);
        this.textCenter(ScreenLayout.OS_NAME, 40, -2299664, RbmkScreenCanvas.fCjkBold(14));
        this.textCenter("\u81ea\u68c0\u901a\u8fc7 \u00b7 \u7b49\u5f85\u6570\u636e\u6e90", 64, -8480856, RbmkScreenCanvas.fCjk(9));
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
        this.textCenter("\u7194 \u6bc1", 62, -46261, RbmkScreenCanvas.fCjkBold(22));
        this.textCenter("\u5806\u82af\u6e29\u5ea6\u8d85\u8fc7\u7194\u6bc1\u9608\u503c", 90, -3896694, RbmkScreenCanvas.fCjk(9));
        this.textCenter("\u7acb\u5373\u64a4\u79bb\u6216\u7d27\u6025\u63d2\u5165\u63a7\u5236\u68d2", 104, -3896694, RbmkScreenCanvas.fCjk(9));
        float p = (float)Math.max(0.0, Math.min(1.0, (double)faultMs / 4000.0));
        int x = 48;
        int w = 160;
        this.bar(x, 140, w, 6, p, -46261);
        this.textCenter("\u81ea\u52a8\u91cd\u542f\u4e2d " + Math.round(p * 100.0f) + "%", 156, -8480856, RbmkScreenCanvas.fCjk(8));
    }

    private void drawMain(RbmkSnapshot d, List<String> events, int page) {
        if (this.alert.active) {
            this.themeLine = -8512998;
            this.themeTitle = -30070;
        }
        this.drawHeader(d, page);
        if (page == 2) {
            this.drawGrid(d);
        } else if (page == 3) {
            this.drawCustom(d);
        } else if (page == 4) {
            this.drawGraph(d);
        } else if (page == 5) {
            this.drawTerm();
        } else {
            this.drawOverview(d, events);
        }
    }

    private void drawHeader(RbmkSnapshot d, int page) {
        String title = page == 2 ? "RBMK \u5806\u82af\u7f51\u683c" : (page == 3 ? "RBMK \u81ea\u5b9a\u4e49\u5c4f" : (page == 4 ? "RBMK \u66f2\u7ebf\u76d1\u89c6" : (page == 5 ? "RBMK \u7ec8\u7aef" : "RBMK \u5806\u82af\u76d1\u89c6")));
        this.rect(0, 0, 256, 19, this.alert.active ? -14021877 : -16116708);
        this.hline(0, 18, 256, this.themeLine);
        String head = this.alert.active
                ? (this.alert.lvl >= AlertOverlay.CRIT ? "!! 严重 " : "! 预警 ") + this.alert.cause
                : title;
        Font headFont = this.alert.active ? RbmkScreenCanvas.fCjkBold(8) : RbmkScreenCanvas.fCjkBold(10);
        this.text(this.clip(head, this.alert.active ? 148 : 190, headFont), 6, 14,
                this.alert.active ? -30070 : -2299664, headFont);
        if (d != null && d.linked && !d.offline) {
            this.textRight(Clock.mmss(d.runTicks), 250, 14, -8480856, RbmkScreenCanvas.fMono(9));
        } else if (d != null && d.offline) {
            this.textRight("\u79bb\u7ebf", 250, 14, -16067, RbmkScreenCanvas.fCjk(9));
        }
        if (this.alert.active && ScreenLayout.blinkOn(this.framePhaseMs)) {
            this.text("!!", 206, 14, -46261, RbmkScreenCanvas.fMonoBold(11));
            this.hline(0, 0, 256, -46261);
        }
    }

    private void drawOverview(RbmkSnapshot d, List<String> events) {
        int lx = 4;
        int lw = 124;
        if (d == null) {
            this.panel(lx, 23, lw, 60);
            this.text("\u65e0\u6570\u636e", lx + 8, 47, -8480856, RbmkScreenCanvas.fCjk(9));
            this.drawEventLog(164, 95, 88, 93, events);
            return;
        }
        this.panel(lx, 23, lw, 48);
        if (!d.linked) {
            this.text("\u6570\u636e\u6e90\u672a\u7ed1\u5b9a", lx + 8, 41, -16067, RbmkScreenCanvas.fCjkBold(10));
            this.text("\u9065\u63a7\u5668\u53f3\u952e\u63a7\u5236\u53f0", lx + 8, 57, -8480856, RbmkScreenCanvas.fCjk(8));
        } else if (d.offline) {
            this.text("\u79bb\u7ebf", lx + 8, 41, -16067, RbmkScreenCanvas.fCjkBold(10));
            this.text("\u626b\u63cf\u533a\u65e0\u67f1\u5b50", lx + 8, 57, -8480856, RbmkScreenCanvas.fCjk(8));
        } else if (d.meltdown) {
            this.text("\u7194\u6bc1\u544a\u8b66\uff01", lx + 8, 41, -46261, RbmkScreenCanvas.fCjkBold(10));
        } else if (d.cold) {
            this.text("\u51b7\u5806", lx + 8, 41, -8480856, RbmkScreenCanvas.fCjkBold(10));
            this.text("\u5e73\u5747\u67f1\u6e29 < 100\u00b0C", lx + 8, 57, -8480856, RbmkScreenCanvas.fCjk(8));
        } else {
            this.text("\u8fd0\u884c\u4e2d", lx + 8, 41, -11345782, RbmkScreenCanvas.fCjkBold(10));
            this.text("\u67f1 " + d.columnCount + " \u00b7 \u71c3\u6599 " + d.fuelCount + " \u00b7 \u68d2 " + d.controlCount, lx + 8, 57, -8480856, RbmkScreenCanvas.fCjk(8));
        }
        int y = 75;
        this.panel(lx, y, lw, 115);
        int y2 = y + 7;
        this.row(lx + 8, y2, lw - 16, "\u5e73\u5747\u67f1\u6e29", DfcFormat.temperature(d.avgHeat), this.tempColor(d.avgHeat, d.maxHeatLimit));
        this.textRight(RbmkScreenCanvas.tempRate(d.tempDelta), lx + lw - 8, y2 + 11, d.tempDelta >= 5.0 ? -46261 : (d.tempDelta <= -5.0 ? -11345782 : -8480856), RbmkScreenCanvas.fMono(7));
        this.row(lx + 8, y2 += 22, lw - 16, "\u6700\u9ad8\u67f1\u6e29", DfcFormat.temperature(d.maxHeat), d.meltRisk >= 0.85 ? -46261 : (d.meltRisk >= 0.7 ? -16067 : -2299664));
        this.bar(lx + 8, y2 + 11, lw - 16, 4, (float)(d.maxHeatLimit > 0.0 ? d.meltRisk : 0.0), RbmkScreenCanvas.heatBarColor(d.meltRisk));
        this.textRight("\u9608 " + DfcFormat.temperature(d.maxHeatLimit), lx + lw - 8, y2 + 20, -8480856, RbmkScreenCanvas.fMono(7));
        this.row(lx + 8, y2 += 31, lw - 16, "\u4e2d\u5b50\u901a\u91cf", DfcFormat.si(d.flux, "F"), d.flux > 0 ? -12003585 : -8480856);
        this.row(lx + 8, y2 + 11, lw - 16, "\u84b8\u6c7d\u603b\u91cf", DfcFormat.amount(d.steamTotal), -2299664);
        this.row(lx + 8, y2 += 22, lw - 16, "\u71c3\u6599\u5e73\u5747\u5bcc\u96c6\u5ea6", DfcFormat.percent(d.avgEnrichment), d.avgEnrichment < 0.15 ? -16067 : -2299664);
        this.row(lx + 8, y2 + 11, lw - 16, "\u5e73\u5747\u6c19\u6bd2\u5316", DfcFormat.percent(d.avgXenon), d.avgXenon > 0.6 ? -16067 : -2299664);
        this.row(lx + 8, y2 + 22, lw - 16, "\u63a7\u5236\u68d2\u62bd\u51fa\u7387", DfcFormat.percent(d.avgRodLevel), -2299664);
        this.drawRiskDial(164, 23, 88, d);
        this.drawEventLog(164, 95, 88, 93, events);
    }

    private void drawRiskDial(int x, int y, int w, RbmkSnapshot d) {
        this.panel(x, y, w, 68);
        this.text("\u7194\u6bc1\u98ce\u9669", x + w / 2 - this.textWidthLog("\u7194\u6bc1\u98ce\u9669", RbmkScreenCanvas.fCjk(8)) / 2, y + 10, -8480856, RbmkScreenCanvas.fCjk(8));
        int cx = x + w / 2;
        int cy = y + 40;
        int r = 20;
        this.ovalStroke(cx - r, cy - r, r * 2, r * 2, -14928312, 4);
        double risk = d.meltRisk;
        if (risk > 0.0) {
            this.g.setColor(new Color(RbmkScreenCanvas.heatBarColor(risk), true));
            this.g.fillArc((cx - r) * 3, (cy - r) * 3, r * 2 * 3, r * 2 * 3, 90, (int)Math.round(-360.0 * Math.min(1.0, risk)));
        }
        this.textCenterIn(DfcFormat.percent(risk), x, w, cy + 5, risk >= 0.85 ? -46261 : (risk >= 0.7 ? -16067 : -2299664), RbmkScreenCanvas.fMonoBold(11));
    }

    private void drawGrid(RbmkSnapshot d) {
        boolean haveGrid;
        this.panel(6, 28, 139, 139);
        boolean bl = haveGrid = d != null && d.linked && !d.offline && d.includeGrid && !d.grid.isEmpty();
        if (!haveGrid) {
            String line1;
            String line2 = null;
            if (d == null || !d.linked) {
                line1 = "\u672a\u7ed1\u5b9a\u6570\u636e\u6e90";
                line2 = "\u9065\u63a7\u5668\u53f3\u952e\u63a7\u5236\u53f0/\u67f1\u5217\u8bb0\u5f55\uff0c\u518d\u53f3\u952e\u672c\u5c4f";
            } else if (d.offline) {
                line1 = "\u79bb\u7ebf \u00b7 \u627e\u4e0d\u5230 RBMK \u6570\u636e";
                line2 = "\u7ed1\u5b9a\u63a7\u5236\u53f0\u540e\u8bf7\u786e\u8ba4\u63a7\u5236\u53f0\u5df2\u653e\u7f6e";
            } else if (!d.includeGrid) {
                line1 = "\u672c\u5c4f\u4e0d\u662f\u5806\u82af\u76d1\u89c6\u5668";
                line2 = "\u8bf7\u4f7f\u7528\u300cRBMK \u5806\u82af\u76d1\u89c6\u5668\u300d\u65b9\u5757";
            } else {
                line1 = "\u63a7\u5236\u53f0\u626b\u63cf\u533a\u65e0\u67f1\u5217";
                line2 = "\u5728\u63a7\u5236\u53f0 15\u00d715 \u8303\u56f4\u5185\u642d\u71c3\u6599\u67f1";
            }
            this.textCenter(line1, 91, -8480856, RbmkScreenCanvas.fCjk(9));
            if (line2 != null) {
                this.textCenter(line2, 107, -14273474, RbmkScreenCanvas.fCjk(8));
            }
        } else {
            for (int i = 0; i < 15; ++i) {
                this.text(String.valueOf(i), 8 + i * 9 + 2, 23, -12298133, RbmkScreenCanvas.fMono(6));
                this.text(String.valueOf(i), -1, 30 + i * 9 + 7, -12298133, RbmkScreenCanvas.fMono(6));
            }
            for (RbmkSnapshot.RbmkCell c : d.grid) {
                int col = c.index % 15;
                int row = c.index / 15;
                this.drawCell(8 + col * 9, 30 + row * 9, c);
            }
        }
        int sx = 153;
        int sw = 99;
        this.panel(sx, 28, sw, 160);
        int yy = 38;
        this.text("\u5806\u82af\u7edf\u8ba1", sx + 8, yy, -2299664, RbmkScreenCanvas.fCjkBold(9));
        yy += 16;
        if (d == null) {
            this.text("\u65e0\u6570\u636e", sx + 8, yy, -8480856, RbmkScreenCanvas.fCjk(8));
            return;
        }
        this.row(sx + 8, yy, sw - 16, "\u67f1\u6570", String.valueOf(d.columnCount), -2299664);
        this.row(sx + 8, yy + 10, sw - 16, "\u5e73\u5747\u67f1\u6e29", DfcFormat.temperature(d.avgHeat), this.tempColor(d.avgHeat, d.maxHeatLimit));
        this.row(sx + 8, yy + 20, sw - 16, "\u6700\u9ad8\u67f1\u6e29", DfcFormat.temperature(d.maxHeat), d.meltRisk >= 0.85 ? -46261 : -2299664);
        this.row(sx + 8, yy += 33, sw - 16, "\u71c3\u6599\u67f1", String.valueOf(d.fuelCount), -2299664);
        this.row(sx + 8, yy + 10, sw - 16, "\u63a7\u5236\u68d2", String.valueOf(d.controlCount), -2299664);
        this.row(sx + 8, yy + 20, sw - 16, "\u9505\u7089\u67f1", String.valueOf(d.boilerCount), -2299664);
        this.row(sx + 8, yy + 30, sw - 16, "\u5bcc\u96c6\u5ea6", DfcFormat.percent(d.avgEnrichment), -8480856);
        this.row(sx + 8, yy += 44, sw - 16, "\u6c19\u6bd2\u5316", DfcFormat.percent(d.avgXenon), d.avgXenon > 0.6 ? -16067 : -8480856);
        this.row(sx + 8, yy + 10, sw - 16, "\u84b8\u6c7d", DfcFormat.amount(d.steamTotal), -8480856);
        int ly = yy + 17;
        this.text("\u7c7b\u578b", sx + 8, ly, -8480856, RbmkScreenCanvas.fCjk(7));
        int lyy = ly + 8;
        int[][] legendRows = new int[][]{{1, 3}, {5, 6}, {7, -1}};
        for (int r = 0; r < legendRows.length; ++r) {
            int t;
            int cxx = sx + 8;
            for (int c = 0; c < 2 && (t = legendRows[r][c]) >= 0; ++c) {
                this.rect(cxx, lyy + r * 7, 5, 5, RbmkScreenCanvas.typeColor(t));
                this.text(RbmkSnapshot.RbmkCell.typeName(t), cxx + 9, lyy + 6 + r * 7, -8480856, RbmkScreenCanvas.fMono(6));
                cxx += 49;
            }
        }
    }

    private void drawCell(int px, int py, RbmkSnapshot.RbmkCell c) {
        int base = RbmkScreenCanvas.typeColor(c.type);
        double ratio = c.heatLimit > 0.0 ? c.heat / c.heatLimit : 0.0;
        this.rect(px, py, 9, 9, base);
        if (ratio > 0.0) {
            float a = (float)Math.min((double)0.9f, (double)0.12f + ratio * (double)0.85f);
            this.rect(px, py, 9, 9, RbmkScreenCanvas.argb(RbmkScreenCanvas.heatBarColor(ratio), a));
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
            return "0.0\u00b0C/s";
        }
        return String.format(Locale.ROOT, "%+.1f\u00b0C/s", perSecond);
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
        // v18.2 布局模式：编辑器保存过布局就按布局渲染小部件
        if (this.customLayout != null && !this.customLayout.isEmpty()) {
            this.drawCustomLayout(d);
            return;
        }
        this.panel(4, 23, 248, 154);
        int y = 35;
        List<DfcWatch> ws = d == null ? null : d.watches;
        int shown = 0;
        if (ws != null) {
            for (DfcWatch w : ws) {
                if (y > 176) break;
                if (w.kind == 0) continue;
                boolean hasBar = w.alive && w.ratio >= 0.0;
                int valueRight = hasBar ? 148 : 246;
                String name = this.clip(w.device, valueRight - 40 - 12, RbmkScreenCanvas.fCjk(8));
                this.text(name, 12, y, w.alive ? -8480856 : -14273474, RbmkScreenCanvas.fCjk(8));
                String value = w.alive ? w.value : "\u79bb\u7ebf";
                int vc = w.alive ? -2299664 : -14273474;
                Font vf = RbmkScreenCanvas.hasCjk(value) ? this.fitCjk(value, 70, 10, 8) : this.fitMono(value, 70, 10, 7, false);
                this.textRight(value, valueRight, y, vc, vf);
                if (hasBar) {
                    this.bar(158, y - 4, 86, 3, (float)w.ratio, w.ratio >= (double)0.85f ? -16067 : -12003585);
                }
                this.hline(12, y + 8, 232, -15457746);
                y += 18;
                ++shown;
            }
        }
        if (shown == 0) {
            this.text("\u6ca1\u6709\u6570\u636e\u69fd", 12, 63, -8480856, RbmkScreenCanvas.fCjk(9));
            this.text("\u9065\u63a7\u5668\u8e72\u4e0b\u53f3\u952e\u50a8\u7f50/\u7535\u6c60", 12, 79, -8480856, RbmkScreenCanvas.fCjk(8));
            this.text("\u518d\u8e72\u4e0b\u53f3\u952e\u672c\u5c4f\u63a5\u5165", 12, 91, -8480856, RbmkScreenCanvas.fCjk(8));
        }
    }

    /** v18.2 布局编辑器产物（渲染器每帧从自定义屏 Tile 同步；null = 未编辑） */
    public volatile com.jiezhirbmkmod.dfc.CustomWidgetLayout customLayout;

    private static final String[] LAYOUT_CH_NAMES =
            new String[]{"\u67f1\u6e29", "\u901a\u91cf", "\u6c34\u91cf", "\u84b8\u6c7d", "\u5bcc\u96c6", "\u6c19\u6bd2"};

    /**
     * v18.2 按布局渲染自定义页：仪表块（槽位读数+比例条）、曲线块
     * （历史通道迷你曲线）、文本块（设备名+读数）。坐标即 256×192
     * 虚拟屏逻辑坐标，与编辑器 1:1。
     */
    private void drawCustomLayout(RbmkSnapshot d) {
        List<DfcWatch> ws = d == null ? null : d.watches;
        for (com.jiezhirbmkmod.dfc.CustomWidgetLayout.Widget w : this.customLayout.widgets()) {
            int x = w.x;
            int y = w.y;
            int ww = w.w;
            int wh = w.h;
            this.panel(x, y, ww, wh);
            if (w.type == com.jiezhirbmkmod.dfc.CustomWidgetLayout.TYPE_GAUGE) {
                DfcWatch src = ws != null && w.slot < ws.size() ? ws.get(w.slot) : null;
                boolean alive = src != null && src.alive;
                String name = src == null || src.device == null || src.device.isEmpty()
                        ? ("\u69fd #" + (w.slot + 1)) : this.clip(src.device, ww - 8, RbmkScreenCanvas.fCjk(7));
                this.text(name, x + 4, y + 6, alive ? -8480856 : -14273474, RbmkScreenCanvas.fCjk(7));
                String value = alive ? src.value : "\u79bb\u7ebf";
                this.textRight(value, x + ww - 4, y + wh - 12, alive ? -2299664 : -14273474,
                        RbmkScreenCanvas.fMonoBold(8));
                if (alive && src.ratio >= 0.0D) {
                    this.bar(x + 4, y + wh - 6, ww - 8, 3, (float) src.ratio,
                            src.ratio >= 0.85D ? -16067 : -12003585);
                }
            } else if (w.type == com.jiezhirbmkmod.dfc.CustomWidgetLayout.TYPE_GRAPH) {
                // 迷你曲线：历史通道按量程归一化画进部件盒
                this.text(LAYOUT_CH_NAMES[Math.max(0, Math.min(5, w.channel))],
                        x + 4, y + 5, -14273474, RbmkScreenCanvas.fCjk(7));
                int gx = x + 4;
                int gy = y + 14;
                int gw = ww - 8;
                int gh = wh - 20;
                if (gw > 4 && gh > 4 && this.history.hasData()) {
                    double axisMax = this.history.axisMax(Math.max(0, Math.min(5, w.channel)));
                    int n = this.history.size();
                    int lastPx = -1;
                    int lastPy = -1;
                    for (int i = 0; i < n; ++i) {
                        if (this.history.gapAt(i)) {
                            lastPx = -1;
                            continue;
                        }
                        double v = this.history.valueAt(Math.max(0, Math.min(5, w.channel)), i);
                        int px = gx + (int) ((long) gw * (long) i / (long) Math.max(1, GraphHistory.CAPACITY - 1));
                        int py = gy + gh - (int) Math.round((double) gh * Math.min(1.0D, v / axisMax));
                        if (lastPx >= 0) {
                            this.g.setColor(new Color(-12003585, true));
                            this.g.setStroke(new BasicStroke(1.6f));
                            this.g.drawLine(lastPx * 3, lastPy * 3, px * 3, py * 3);
                        }
                        lastPx = px;
                        lastPy = py;
                    }
                }
            } else {
                // 文本块：设备名一行 + 读数一行（大字）
                DfcWatch src = ws != null && w.slot < ws.size() ? ws.get(w.slot) : null;
                boolean alive = src != null && src.alive;
                String name = src == null || src.device == null || src.device.isEmpty()
                        ? ("\u69fd #" + (w.slot + 1)) : this.clip(src.device, ww - 8, RbmkScreenCanvas.fCjk(7));
                this.text(name, x + 4, y + 5, alive ? -8480856 : -14273474, RbmkScreenCanvas.fCjk(7));
                String value = alive ? src.value : "\u79bb\u7ebf";
                Font vf = RbmkScreenCanvas.hasCjk(value)
                        ? this.fitCjk(value, ww - 8, wh - 16, 9)
                        : this.fitMono(value, ww - 8, wh - 16, 8, true);
                this.text(value, x + 4, y + 16, alive ? -2299664 : -14273474, vf);
            }
        }
    }

    /**
     * v18.1 自动记录入口（客户端 tick 驱动，与渲染无关）：
     * 屏一放置 / 区块一加载就开始为曲线历史采样 —— 不再要求"有人看着
     * 屏 + 停在曲线页"，导出/回放/巡屏面板拿到的历史从放置那一刻起就是
     * 完整的。
     *
     * <p>断电/待机（{@code canRecord=false}）时<b>冻结</b>，连空格都不推。
     * 18.1 自查修复：最初一版断电时按同样节奏推空格想留断口，但有两个
     * 致命副作用 —— (1) 窗口只有约 60 秒，断电超过 60 秒内存里的历史就被
     * 空格完全挤光，之后区块卸载时 HistoryStore.checkpoint 看到
     * hasData()==false 会把落盘的黑匣子文件删掉，18.0 的"历史永不丢"
     * 承诺被断电毁掉；(2) 恢复供电时 drawBoot 会 history.clear()，断电
     * 期间推的空格在曲线上根本显示不出来 —— 只有副作用没有意义。
     * 现在屏断电=采样器也断电，历史原样冻结（断电前录到哪就停在哪），
     * 与旧版待机行为一致；"断口"只保留给屏开着但数据源离线的场景
     * （堆芯断链/遥控器断开，走下方 pushGap 分支）。</p>
     */
    public void autoSample(RbmkSnapshot d, boolean canRecord) {
        if (!canRecord) {
            return; // 屏断电/待机：冻结历史，不推空格（理由见上）
        }
        long now = System.currentTimeMillis();
        if (this.lastSampleMs != Long.MIN_VALUE && now - this.lastSampleMs < GraphHistory.SAMPLE_MS) {
            return;
        }
        this.lastSampleMs = now;
        if (d != null && d.linked && !d.offline) {
            this.history.push(d.avgHeat, d.flux, d.waterTotal, d.steamTotal, d.avgEnrichment, d.avgXenon,
                    (byte) Math.max(0, Math.min(2, this.lastAlertLevel)));
        } else {
            this.history.pushGap();
        }
        // v18.0 历史落盘：只置脏标记，批量写盘由 HistoryStore 的客户端
        // tick（约 15 秒一次）与世界卸载负责 —— 采样路径上不碰盘。
        com.jiezhirbmkmod.client.HistoryStore.touch(this.history);
    }

    private static final String[] GRAPH_PAGE_A = new String[]{"\u5e73\u5747\u67f1\u6e29 \u00b0C", "\u4e2d\u5b50\u901a\u91cf F", "\u9505\u7089\u6c34\u91cf mB"};
    private static final String[] GRAPH_PAGE_B = new String[]{"\u84b8\u6c7d\u603b\u91cf mB", "\u71c3\u6599\u5bcc\u96c6\u5ea6", "\u6c19\u6bd2\u5316"};
    private static final int[] GRAPH_PAGE_A_CH = new int[]{0, 1, 2};
    private static final int[] GRAPH_PAGE_B_CH = new int[]{3, 4, 5};
    private static final int[] GRAPH_COLORS = new int[]{-46261, -12003585, -11345782};
    private static final long GRAPH_PAGE_ROTATE_MS = 20000L;

    private void drawGraph(RbmkSnapshot d) {
        if (!this.history.hasData()) {
            if (d == null || !d.linked) {
                this.textCenter("\u672a\u7ed1\u5b9a\u6570\u636e\u6e90", 91, -8480856, RbmkScreenCanvas.fCjk(9));
                this.textCenter("\u9065\u63a7\u5668\u53f3\u952e\u63a7\u5236\u53f0\u540e\u518d\u53f3\u952e\u672c\u5c4f", 107, -14273474, RbmkScreenCanvas.fCjk(8));
            } else if (d.offline) {
                this.textCenter("\u79bb\u7ebf \u00b7 \u626b\u63cf\u533a\u65e0\u67f1\u5b50", 91, -16067, RbmkScreenCanvas.fCjk(9));
            } else {
                this.textCenter("\u7b49\u5f85\u6570\u636e\u2026", 91, -8480856, RbmkScreenCanvas.fCjk(9));
            }
            return;
        }
        int n = this.history.size();
        // v17.7 回放：窗口终点 = n - replayOffset，只画到历史里的某个时刻
        int nEff = this.replayOffset > 0 ? Math.max(1, n - this.replayOffset) : n;
        // v18.2 曲线对比模式：四通道叠加在一块大面板里（设置中心开关）
        if (this.graphCompare) {
            this.drawGraphCompare(nEff);
            return;
        }
        boolean pageB = (this.framePhaseMs / GRAPH_PAGE_ROTATE_MS) % 2L == 1L;
        String[] names = pageB ? GRAPH_PAGE_B : GRAPH_PAGE_A;
        int[] chans = pageB ? GRAPH_PAGE_B_CH : GRAPH_PAGE_A_CH;
        int gx = 4;
        int gw = 81;
        int gap = 3;
        for (int i = 0; i < chans.length; ++i) {
            int x = gx + i * (gw + gap);
            this.drawGraphPanel(x, 23, gw, 166, chans[i], names[i], GRAPH_COLORS[i], nEff);
        }
        String pageName = pageB ? "\u71c3\u6599\u7ec4" : "\u70ed\u5de5\u7ec4";
        if (this.replayOffset > 0) {
            // 回放横幅：黄色，显示倒回多少秒；滚轮滚回 0 自动恢复实时
            long secs = (long) this.replayOffset * GraphHistory.SAMPLE_MS / 1000L;
            this.textCenter("\u25c0\u25c0 \u56de\u653e t-" + secs + "s \u00b7 \u6eda\u8f6e\u8c03\u8282 \u00b7 \u5230\u5934\u81ea\u52a8\u6062\u590d\u5b9e\u65f6",
                    194, -16067, RbmkScreenCanvas.fCjk(7));
        } else {
            this.textCenter("\u3010" + pageName + "\u3011\u81ea\u52a8\u8f6e\u6362 \u00b7 \u7a97\u53e3\u7ea6 " + com.jiezhirbmkmod.dfc.RbmkConfig.windowSeconds() + " \u79d2 \u00b7 \u6eda\u8f6e=\u56de\u653e",
                    194, -14273474, RbmkScreenCanvas.fCjk(7));
        }
    }

    /** v18.2 对比模式通道表：柱温/通量/水量/蒸汽，颜色复用全屏色板 */
    private static final int[] COMPARE_CH = new int[]{0, 1, 2, 3};
    private static final String[] COMPARE_NAMES =
            new String[]{"\u67f1\u6e29", "\u901a\u91cf", "\u6c34\u91cf", "\u84b8\u6c7d"};
    private static final int[] COMPARE_COLORS = new int[]{-46261, -12003585, -11345782, -16067};

    /**
     * v18.2 曲线对比模式：一块大面板叠加柱温/通量/水量/蒸汽四条曲线，
     * 每条按各自量程归一化（看形态相关性）；可开对数纵轴（对数域同样
     * 按各自量程归一）。右上角图例 + 各通道实时值。
     */
    private void drawGraphCompare(int n) {
        int x = 4;
        int y = 23;
        int w = 248;
        int h = 160;
        this.panel(x, y, w, h);
        int ix = x + 6;
        int iy = y + 16;
        int iw = w - 12;
        int ih = h - 26;
        // 网格
        for (int k = 0; k <= GraphHistory.DIVISIONS; ++k) {
            int gy = iy + (int) ((long) ih * (long) k / (long) GraphHistory.DIVISIONS);
            this.hline(ix, gy, iw, k == 0 || k == GraphHistory.DIVISIONS ? this.themeLine : -15457746);
        }
        // 四条曲线：各自量程归一（对数开关作用于纵轴映射）
        boolean log = this.graphLogScale;
        for (int c = 0; c < COMPARE_CH.length; ++c) {
            int channel = COMPARE_CH[c];
            int color = COMPARE_COLORS[c];
            double axisMax = this.history.axisMax(channel);
            double lmax = Math.log10(axisMax + 1.0D);
            int lastPx = -1;
            int lastPy = -1;
            for (int i = 0; i < n; ++i) {
                if (this.history.gapAt(i)) {
                    lastPx = -1;
                    continue;
                }
                double v = this.history.valueAt(channel, i);
                int px = ix + (int) ((long) iw * (long) i / (long) Math.max(1, GraphHistory.CAPACITY - 1));
                double t;
                if (log) {
                    t = lmax <= 0.0D ? 0.0D : Math.log10(Math.max(0.0D, v) + 1.0D) / lmax;
                } else {
                    t = Math.min(1.0D, v / axisMax);
                }
                int py = iy + ih - (int) Math.round((double) ih * t);
                if (lastPx >= 0) {
                    this.g.setColor(new Color(color, true));
                    this.g.setStroke(new BasicStroke(2.0f));
                    this.g.drawLine(lastPx * 3, lastPy * 3, px * 3, py * 3);
                }
                lastPx = px;
                lastPy = py;
            }
        }
        // 图例 + 实时值（最末采样点）
        int lx = ix + 2;
        for (int c = 0; c < COMPARE_CH.length; ++c) {
            int channel = COMPARE_CH[c];
            int color = COMPARE_COLORS[c];
            double cur = Double.NaN;
            for (int i = n - 1; i >= 0; --i) {
                if (!this.history.gapAt(i)) {
                    cur = this.history.valueAt(channel, i);
                    break;
                }
            }
            String val = Double.isNaN(cur) ? "--" : this.shortAxisLabel(cur);
            String label = COMPARE_NAMES[c] + " " + val;
            this.text(label, lx, y + 5, color, RbmkScreenCanvas.fMonoBold(6));
            lx += this.fontW(label, RbmkScreenCanvas.fMonoBold(6)) + 14;
        }
        String mode = (log ? "\u5bf9\u6570\u5750\u6807 \u00b7 " : "")
                + "\u5bf9\u6bd4\u6a21\u5f0f \u00b7 \u56db\u901a\u9053\u53e0\u52a0 \u00b7 \u8bbe\u7f6e\u4e2d\u5fc3\u53ef\u5173";
        this.textCenter(mode, y + h + 4, -14273474, RbmkScreenCanvas.fCjk(7));
    }

    private int fontW(String s, Font f) {
        return this.g.getFontMetrics(f) == null ? s.length() * 6 : this.g.getFontMetrics(f).stringWidth(s);
    }

    private void drawGraphPanel(int x, int y, int w, int h, int channel, String name, int color, int n) {
        this.panel(x, y, w, h);
        int ix = x + 3;
        int iy = y + 14;
        int iw = w - 6;
        int ih = h - 24;
        double axisMax = this.history.axisMax(channel);
        boolean lifted = axisMax > this.channelBase(channel);
        this.text(name, x + 4, y + 10, -8480856, RbmkScreenCanvas.fCjk(7));
        for (int k = 0; k <= GraphHistory.DIVISIONS; ++k) {
            int gy = iy + (int) ((long) ih * (long) k / (long) GraphHistory.DIVISIONS);
            this.hline(ix, gy, iw, k == 0 || k == GraphHistory.DIVISIONS ? this.themeLine : -15457746);
            double v = GraphHistory.gridValue(axisMax, k);
            String label = this.shortAxisLabel(v);
            this.textRight(label, x + w - 3, gy + 7, lifted && k == 0 ? -16067 : -12298133, RbmkScreenCanvas.fMono(5));
        }
        this.hline(ix, iy + ih / 2, iw, -14928312);
        // v17.7 告警标记线：历史里 L1/L2 的时刻画竖线（黄=预警 红=严重），
        // 连续告警自然连成色带——色带宽度就是告警时长。回放倒带时同窗可见。
        for (int i = 0; i < n; ++i) {
            if (this.history.gapAt(i)) {
                continue;
            }
            byte lv = this.history.alertAt(i);
            if (lv < 1) {
                continue;
            }
            int px = ix + (int) ((long) iw * (long) i / (long) Math.max(1, GraphHistory.CAPACITY - 1));
            this.g.setColor(new Color(lv >= 2 ? 0x90FF4646 : 0x90FFC840, true));
            this.g.fillRect(px * 3, iy * 3, 3, ih * 3);
        }
        double last = Double.NaN;
        int lastPx = -1;
        int lastPy = -1;
        for (int i = 0; i < n; ++i) {
            if (this.history.gapAt(i)) {
                lastPx = -1;
                continue;
            }
            double v = this.history.valueAt(channel, i);
            int px = ix + (int) ((long) iw * (long) i / (long) Math.max(1, GraphHistory.CAPACITY - 1));
            int py = iy + ih - (int) Math.round((double) ih * Math.min(1.0, v / axisMax));
            if (lastPx >= 0) {
                this.g.setColor(new Color(color, true));
                this.g.setStroke(new BasicStroke(2.0f));
                this.g.drawLine(lastPx * 3, lastPy * 3, px * 3, py * 3);
            }
            lastPx = px;
            lastPy = py;
            last = v;
        }
        if (!Double.isNaN(last)) {
            String cur = this.shortAxisLabel(last);
            this.textRight(cur, x + w - 3, y + h - 4, color, RbmkScreenCanvas.fMonoBold(7));
        }
    }

    private double channelBase(int channel) {
        switch (channel) {
            case 1: {
                return GraphHistory.FLUX_BASE_MAX;
            }
            case 2: {
                return GraphHistory.WATER_BASE_MAX;
            }
        }
        return GraphHistory.HEAT_BASE_MAX;
    }

    private String shortAxisLabel(double v) {
        if (v >= 1000000.0) {
            return String.format(Locale.ROOT, "%.1fM", v / 1000000.0);
        }
        if (v >= 10000.0) {
            return String.format(Locale.ROOT, "%.1fk", v / 1000.0);
        }
        if (v >= 100.0) {
            return String.valueOf(Math.round(v));
        }
        return String.format(Locale.ROOT, "%.0f", v);
    }

    /**
     * Command prompt page of the 4x3 terminal display: classic console look,
     * black screen, green prompt, the terminal's last output lines in their
     * own colors. Large mono font so the text stays readable from a distance
     * even without HD font packs.
     */
    private void drawTerm() {
        this.rect(0, 19, 256, 173, 0xFF0A0A0C);
        Font f = RbmkScreenCanvas.fMono(7);
        int lineH = 11;
        int top = 25;
        int maxLines = (192 - top - 2) / lineH;
        if (this.termLines.isEmpty()) {
            this.text("C:\\RBMK> _", 8, top, -11345782, RbmkScreenCanvas.fMonoBold(9));
            this.text("\u53f3\u952e\u6253\u5f00\u7ec8\u7aef \u00b7 \u8f93\u5165 help \u67e5\u770b\u5168\u90e8\u547d\u4ee4", 8, top + 18, -8480856, RbmkScreenCanvas.fCjk(7));
            return;
        }
        int from = Math.max(0, this.termLines.size() - maxLines);
        int y = top;
        for (int i = from; i < this.termLines.size(); ++i) {
            com.jiezhirbmkmod.network.msg.MessageTerminalCommand.CmdLine l = this.termLines.get(i);
            String s = this.clip(l.text, 244, f);
            this.text(s, 8, y, l.color, f);
            y += lineH;
        }
        if (ScreenLayout.blinkOn(this.framePhaseMs)) {
            this.text("_", 8, Math.min(y, 192 - 12), -11345782, RbmkScreenCanvas.fMonoBold(9));
        }
    }

    private void drawEventLog(int x, int y, int w, int h, List<String> events) {        this.panel(x, y, w, h);
        this.text("\u4e8b\u4ef6\u65e5\u5fd7", x + 6, y + 10, this.themeTitle, RbmkScreenCanvas.fCjk(8));
        this.hline(x + 4, y + 15, w - 8, this.themeLine);
        if (events == null || events.isEmpty()) {
            this.text("\u65e0\u4e8b\u4ef6", x + 8, y + 32, -8480856, RbmkScreenCanvas.fCjk(8));
            return;
        }
        Font f = RbmkScreenCanvas.fCjk(7);
        ArrayList<String> rows = new ArrayList<String>();
        for (String s : events) {
            rows.addAll(this.wrap(s, w - 14, f));
        }
        int y2 = y + 15 + 9 * Math.min(rows.size(), 8) + 2;
        for (int i = rows.size() - 1; i >= 0 && y2 > y + 20; y2 -= 9, --i) {
            this.text((String)rows.get(i), x + 7, y2, i == rows.size() - 1 ? -2299664 : -8480856, f);
        }
    }

    private int tempColor(double heat, double limit) {
        double ratio = limit > 0.0 ? heat / limit : 0.0;
        return RbmkScreenCanvas.heatBarColor(ratio);
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
        this.rect(0, 0, w, 2, RbmkScreenCanvas.argb(color, 0.55f));
        this.rect(0, h - 2, w, 2, RbmkScreenCanvas.argb(color, 0.55f));
        this.rect(0, 0, 2, h, RbmkScreenCanvas.argb(color, 0.55f));
        this.rect(w - 2, 0, 2, h, RbmkScreenCanvas.argb(color, 0.55f));
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
        Font lf = RbmkScreenCanvas.fCjk(9);
        this.text(label, x, y, -8480856, lf);
        int avail = Math.max(8, width - this.textWidthLog(label, lf) - 6);
        String vs = value == null ? "--" : value;
        Font font = vf = RbmkScreenCanvas.hasCjk(vs) ? this.fitCjk(vs, avail, 9, 7) : this.fitMono(vs, avail, 9, 6, false);
        if (this.measure(vs, vf) > avail * 3) {
            vs = this.clip(vs, avail, vf);
        }
        this.textRight(vs, x + width, y, valueColor, vf);
    }

    private static boolean hasCjk(String s) {
        for (int i = 0; i < s.length(); ++i) {
            if (s.charAt(i) < '\u2e80') continue;
            return true;
        }
        return false;
    }

    private Font fitMono(String s, int maxLogicalW, int maxSize, int minSize, boolean bold) {
        for (int sz = maxSize; sz > minSize; --sz) {
            Font f;
            Font font = f = bold ? RbmkScreenCanvas.fMonoBold(sz) : RbmkScreenCanvas.fMono(sz);
            if (this.measure(s, f) > maxLogicalW * 3) continue;
            return f;
        }
        return bold ? RbmkScreenCanvas.fMonoBold(minSize) : RbmkScreenCanvas.fMono(minSize);
    }

    private Font fitCjk(String s, int maxLogicalW, int maxSize, int minSize) {
        for (int sz = maxSize; sz > minSize; --sz) {
            Font f = RbmkScreenCanvas.fCjk(sz);
            if (this.measure(s, f) > maxLogicalW * 3) continue;
            return f;
        }
        return RbmkScreenCanvas.fCjk(minSize);
    }

    /** 缺字回退字体（同字号）：内置字体没有的字改画系统字体，绝不画成方框 */
    private static Font glyphFallback(Font f) {
        Font fb = GLYPH_FALLBACK.get(f);
        if (fb == null) {
            fb = new Font(Font.DIALOG, f.getStyle(), f.getSize());
            GLYPH_FALLBACK.put(f, fb);
        }
        return fb;
    }

    private static Font glyphFont(Font f, int cp) {
        return f.canDisplay(cp) ? f : RbmkScreenCanvas.glyphFallback(f);
    }

    private FontMetrics metrics(Font f) {
        FontMetrics m = this.fmCache.get(f);
        if (m == null) {
            m = this.g.getFontMetrics(f);
            this.fmCache.put(f, m);
        }
        return m;
    }

    /** 逐段测量，分段方式与 drawText 完全一致，保证右对齐/居中不会偏 */
    private int measure(String s, Font f) {
        if (s == null) {
            return 0;
        }
        int w = 0;
        int i = 0;
        while (i < s.length()) {
            Font use = RbmkScreenCanvas.glyphFont(f, s.codePointAt(i));
            int start = i;
            while (i < s.length()) {
                int cp = s.codePointAt(i);
                if (!RbmkScreenCanvas.glyphFont(f, cp).equals(use)) break;
                i += Character.charCount(cp);
            }
            w += this.metrics(use).stringWidth(s.substring(start, i));
        }
        return w;
    }

    /** 逐段绘制：同一种字体的连续字符合并成一次 drawString */
    private void drawText(String s, int x, int y, int argb, Font f) {
        if (s == null || s.isEmpty()) {
            return;
        }
        this.g.setColor(new Color(argb, true));
        int cx = x;
        int i = 0;
        while (i < s.length()) {
            Font use = RbmkScreenCanvas.glyphFont(f, s.codePointAt(i));
            int start = i;
            while (i < s.length()) {
                int cp = s.codePointAt(i);
                if (!RbmkScreenCanvas.glyphFont(f, cp).equals(use)) break;
                i += Character.charCount(cp);
            }
            String run = s.substring(start, i);
            this.g.setFont(use);
            this.g.drawString(run, cx, y);
            cx += this.metrics(use).stringWidth(run);
        }
    }

    private void text(String s, int x, int y, int argb, Font f) {
        this.drawText(s, x * 3, y * 3, argb, f);
    }

    private void textRight(String s, int rightX, int y, int argb, Font f) {
        this.drawText(s, rightX * 3 - this.measure(s, f), y * 3, argb, f);
    }

    private void textCenter(String s, int cy, int argb, Font f) {
        this.drawText(s, 384 - this.measure(s, f) / 2, cy * 3, argb, f);
    }

    private void textCenterIn(String s, int x, int w, int baselineY, int argb, Font f) {
        this.drawText(s, (x + w / 2) * 3 - this.measure(s, f) / 2, baselineY * 3, argb, f);
    }

    private int textWidthLog(String s, Font f) {
        return (int)Math.ceil((double)this.measure(s, f) / 3.0);
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

    private static final class AlertOverlay {
        static final int CRIT = AlertPolicy.LEVEL_CRIT;
        int lvl = AlertPolicy.LEVEL_NONE;
        String cause = "";
        boolean active;
        boolean strobeOn;

        void set(int level, String cause) {
            this.lvl = level;
            this.cause = cause == null ? "" : cause;
            this.active = level > AlertPolicy.LEVEL_NONE;
        }

        boolean updateStrobe(long sinceOnMs) {
            this.strobeOn = this.lvl >= CRIT;
            return this.strobeOn;
        }

        static final int MAX_CAUSE_ROWS = 2;

        boolean blink(long ms) {
            return (ms / (this.strobeOn ? STROBE_MS : ScreenLayout.ALERT_BLINK_MS)) % 2L == 0L;
        }
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

