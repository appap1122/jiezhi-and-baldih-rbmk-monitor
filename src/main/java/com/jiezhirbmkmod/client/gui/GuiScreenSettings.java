package com.jiezhirbmkmod.client.gui;

import com.jiezhirbmkmod.block.BlockRbmkGraph;
import com.jiezhirbmkmod.block.TileRbmkMonitorScreen;
import com.jiezhirbmkmod.network.ModNetwork;
import com.jiezhirbmkmod.network.msg.MessageScreenDisplaySetting;

import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * v18.2 屏幕设置中心（蹲下对屏连续右键两下打开）。
 *
 * <p>从 v17.9 的「双击=历史导出面板」升级而来：双击现在打开设置中心，
 * 历史导出降级为其中一个按钮。包含：</p>
 * <ul>
 *   <li><b>背光亮度</b>：100 → 80 → 60 → 40 循环，跟着屏存档走；</li>
 *   <li><b>屏显主题</b>：标准 → 琥珀磷光 → 绿磷 → 纯白 循环；</li>
 *   <li><b>曲线对比</b> / <b>对数坐标</b>：仅曲线屏显示（其他屏这两行
 *       置灰），多通道叠加对比；</li>
 *   <li><b>运行报表</b>：一键生成日报（最高温/报警/断口/时长，CSV+HTML）；</li>
 *   <li><b>历史导出</b>：跳转旧导出面板（CSV/PNG/剪辑）。</li>
 * </ul>
 *
 * <p>设置改动经 {@link MessageScreenDisplaySetting}（C→S）落到服务端
 * Tile 并持久化 + 全屏同步；按钮标签本地即时翻转，包往返后值一致。
 * 纯客户端 GUI，专用服务器摸不到（打开点在 ScreenMultiblock 的
 * isRemote 分支）。</p>
 */
public class GuiScreenSettings
extends GuiScreen {

    private static final int PANEL_W = 232;
    private static final int PANEL_H = 208;

    private static final int BTN_BRIGHT = 0;
    private static final int BTN_THEME = 1;
    private static final int BTN_COMPARE = 2;
    private static final int BTN_LOG = 3;
    private static final int BTN_REPORT = 4;
    private static final int BTN_EXPORT = 5;
    private static final int BTN_CLOSE = 6;

    private static final long DOUBLE_CLICK_MS = 700L;
    private static long lastSneakMs = 0L;
    private static BlockPos lastSneakPos = null;

    private final BlockPos screenPos;
    /** 是否曲线屏（决定对比/对数两行是否可用） */
    private final boolean curveScreen;

    /** 本地镜像（发送包后立即翻转，标签不闪） */
    private int brightness = 100;
    private int theme = 0;
    private boolean compare = false;
    private boolean logScale = false;

    public GuiScreenSettings(BlockPos screenPos) {
        this.screenPos = screenPos;
        boolean curve = false;
        int bright = 100;
        int th = 0;
        boolean cmp = false;
        boolean log = false;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.world != null && mc.world.getTileEntity(screenPos) instanceof TileRbmkMonitorScreen) {
            TileRbmkMonitorScreen te = (TileRbmkMonitorScreen) mc.world.getTileEntity(screenPos);
            IBlockState st = mc.world.getBlockState(screenPos);
            curve = st != null && st.getBlock() instanceof BlockRbmkGraph;
            bright = te.getDisplayBrightness();
            th = te.getDisplayTheme();
            cmp = te.isGraphCompare();
            log = te.isGraphLogScale();
        }
        this.curveScreen = curve;
        this.brightness = bright;
        this.theme = th;
        this.compare = cmp;
        this.logScale = log;
    }

    // ---- 双击入口（自 GuiRbmkExport 迁移：双击打开设置中心）----

    public static boolean tryOpen(World world, BlockPos pos, EntityPlayer player) {
        if (world == null || pos == null || player == null) {
            return false;
        }
        BlockPos target = com.jiezhirbmkmod.block.ScreenMultiblock.findController(world, pos);
        if (target == null
                || !(world.getTileEntity(target) instanceof TileRbmkMonitorScreen)) {
            GuiScreenSettings.lastSneakMs = 0L;
            GuiScreenSettings.lastSneakPos = null;
            return false;
        }
        long now = System.currentTimeMillis();
        boolean same = target.equals(GuiScreenSettings.lastSneakPos);
        long dt = now - GuiScreenSettings.lastSneakMs;
        GuiScreenSettings.lastSneakPos = target;
        GuiScreenSettings.lastSneakMs = now;
        if (same && dt >= 0L && dt <= GuiScreenSettings.DOUBLE_CLICK_MS) {
            GuiScreenSettings.lastSneakMs = 0L;
            GuiScreenSettings.lastSneakPos = null;
            Minecraft.getMinecraft().displayGuiScreen(new GuiScreenSettings(target));
            return true;
        }
        return false;
    }

    // ---- GUI ----

    @Override
    public void initGui() {
        super.initGui();
        int x = this.width / 2 - PANEL_W / 2;
        int y = this.height / 2 - PANEL_H / 2;
        int bw = (PANEL_W - 24) / 2;
        this.buttonList.add(new GuiButton(BTN_BRIGHT, x + 10, y + 30, bw, 20, this.brightLabel()));
        this.buttonList.add(new GuiButton(BTN_THEME, x + PANEL_W - 10 - bw, y + 30, bw, 20, this.themeLabel()));
        GuiButton cmp = new GuiButton(BTN_COMPARE, x + 10, y + 54, bw, 20, this.cmpLabel());
        GuiButton log = new GuiButton(BTN_LOG, x + PANEL_W - 10 - bw, y + 54, bw, 20, this.logLabel());
        if (!this.curveScreen) {
            cmp.enabled = false;
            log.enabled = false;
        }
        this.buttonList.add(cmp);
        this.buttonList.add(log);
        this.buttonList.add(new GuiButton(BTN_REPORT, x + 10, y + 78, bw, 20,
                com.jiezhirbmkmod.client.util.GuiDraw.safe("gui.jiezhirbmkmod.settings.report")));
        this.buttonList.add(new GuiButton(BTN_EXPORT, x + PANEL_W - 10 - bw, y + 78, bw, 20,
                com.jiezhirbmkmod.client.util.GuiDraw.safe("gui.jiezhirbmkmod.settings.export")));
        this.buttonList.add(new GuiButton(BTN_CLOSE, x + 10, y + PANEL_H - 30, PANEL_W - 20, 20,
                com.jiezhirbmkmod.client.util.GuiDraw.safe("gui.jiezhirbmkmod.export.close")));
    }

    private String brightLabel() {
        return com.jiezhirbmkmod.client.util.GuiDraw.safe("gui.jiezhirbmkmod.settings.bright",
                Integer.valueOf(this.brightness));
    }

    private String themeLabel() {
        String key;
        switch (this.theme) {
            case 1: key = "gui.jiezhirbmkmod.settings.theme_amber"; break;
            case 2: key = "gui.jiezhirbmkmod.settings.theme_green"; break;
            case 3: key = "gui.jiezhirbmkmod.settings.theme_white"; break;
            default: key = "gui.jiezhirbmkmod.settings.theme_std"; break;
        }
        return com.jiezhirbmkmod.client.util.GuiDraw.safe(key);
    }

    private String cmpLabel() {
        return com.jiezhirbmkmod.client.util.GuiDraw.safe(this.compare
                ? "gui.jiezhirbmkmod.settings.compare_on"
                : "gui.jiezhirbmkmod.settings.compare_off");
    }

    private String logLabel() {
        return com.jiezhirbmkmod.client.util.GuiDraw.safe(this.logScale
                ? "gui.jiezhirbmkmod.settings.log_on"
                : "gui.jiezhirbmkmod.settings.log_off");
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        Minecraft mc = Minecraft.getMinecraft();
        switch (button.id) {
            case BTN_BRIGHT: {
                this.brightness = TileRbmkMonitorScreen.nextBrightness(this.brightness);
                button.displayString = this.brightLabel();
                send(MessageScreenDisplaySetting.KEY_BRIGHTNESS, this.brightness);
                break;
            }
            case BTN_THEME: {
                this.theme = TileRbmkMonitorScreen.nextTheme(this.theme);
                button.displayString = this.themeLabel();
                send(MessageScreenDisplaySetting.KEY_THEME, this.theme);
                break;
            }
            case BTN_COMPARE: {
                this.compare = !this.compare;
                button.displayString = this.cmpLabel();
                send(MessageScreenDisplaySetting.KEY_COMPARE, this.compare ? 1 : 0);
                break;
            }
            case BTN_LOG: {
                this.logScale = !this.logScale;
                button.displayString = this.logLabel();
                send(MessageScreenDisplaySetting.KEY_LOG_SCALE, this.logScale ? 1 : 0);
                break;
            }
            case BTN_REPORT: {
                mc.displayGuiScreen(new GuiRunReport(this.screenPos));
                break;
            }
            case BTN_EXPORT: {
                mc.displayGuiScreen(new GuiRbmkExport(this.screenPos));
                break;
            }
            case BTN_CLOSE: {
                mc.displayGuiScreen(null);
                break;
            }
            default:
                break;
        }
    }

    private void send(int key, int value) {
        ModNetwork.CHANNEL.sendToServer(new MessageScreenDisplaySetting(this.screenPos, key, value));
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
                com.jiezhirbmkmod.client.util.GuiDraw.safe("gui.jiezhirbmkmod.settings.title"),
                x + 8, y + 8, 0xFFE0E0E0);
        com.jiezhirbmkmod.client.util.GuiDraw.text(this.fontRenderer,
                com.jiezhirbmkmod.client.util.GuiDraw.safe("gui.jiezhirbmkmod.export.pos",
                        Integer.valueOf(this.screenPos.getX()), Integer.valueOf(this.screenPos.getY()),
                        Integer.valueOf(this.screenPos.getZ())),
                x + 8, y + 20, 0xFF8A93A0);
        if (!this.curveScreen) {
            com.jiezhirbmkmod.client.util.GuiDraw.text(this.fontRenderer,
                    com.jiezhirbmkmod.client.util.GuiDraw.safe("gui.jiezhirbmkmod.settings.curve_hint"),
                    x + 8, y + 103, 0xFF6B7280);
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
