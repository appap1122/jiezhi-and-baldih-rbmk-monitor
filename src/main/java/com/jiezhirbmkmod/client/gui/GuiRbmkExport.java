package com.jiezhirbmkmod.client.gui;

import java.io.File;
import java.io.IOException;

import com.jiezhirbmkmod.block.ScreenMultiblock;
import com.jiezhirbmkmod.block.TileRbmkMonitorScreen;
import com.jiezhirbmkmod.client.RbmkExporter;
import com.jiezhirbmkmod.dfc.GraphHistory;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;

/**
 * v17.9 历史导出设置面板（纯客户端 GUI，无容器无网络包）。
 *
 * <p>打开方式：<b>蹲下对屏连续右键两下</b>（700ms 内同一块屏）。
 * 双击的两下各走一次服务端拨电源路径（{@code togglePower} 两次净状态
 * 不变，提示走 action-bar 一闪而过），第二下被客户端在这里接住、
 * 直接 {@code displayGuiScreen}，不再转发给服务器 GUI 路由。</p>
 *
 * <p>面板内容：屏坐标、采样点数预览、CSV/PNG 选择按钮、
 * 「导出历史图形」按钮、关闭按钮。导出逻辑与 {@code /rbmkexport}
 * 命令共用 {@link RbmkExporter}，目录由游戏运行时自动创建；成功后按
 * 需求原文输出聊天提示"你的历史图形已保存到 &lt;游戏目录&gt;/rbmk_export/
 * ，请注意查收"。</p>
 *
 * <p>只在客户端类加载（仅被 ScreenMultiblock 的 {@code isRemote} 分支与
 * 本类自身引用），专用服务器摸不到。双击计时用静态字段：只在客户端
 * 主线程被读写，且记录的只有 {@code long} 与 {@code BlockPos}，无泄漏面。</p>
 */
public class GuiRbmkExport
extends GuiScreen {

    private static final int PANEL_W = 216;
    private static final int PANEL_H = 158;

    private static final int BTN_FMT = 0;
    private static final int BTN_EXPORT = 1;
    private static final int BTN_CLOSE = 2;
    /** v18.0 时间段循环按钮：全部 → 60 秒 → 300 秒 → 全部 */
    private static final int BTN_DUR = 3;
    private static final int[] DUR_CHOICES = new int[]{0, 60, 300};

    /** 双击窗口（毫秒）：太短点不出来、太长容易误触发 */
    private static final long DOUBLE_CLICK_MS = 700L;

    /** 双击计时（仅客户端主线程读写） */
    private static long lastSneakMs = 0L;
    private static BlockPos lastSneakPos = null;

    /** 目标屏（控制台）坐标 */
    private final BlockPos screenPos;
    /** false=CSV（表格），true=PNG（长图） */
    private boolean png = false;
    /** v18.0 导出时间窗（秒），0 = 全部采样窗 */
    private int seconds = 0;

    public GuiRbmkExport(BlockPos screenPos) {
        this.screenPos = screenPos;
    }

    // ---- 双击入口 ----

    /**
     * 客户端蹲下右键时调用：检测「700ms 内同一块屏的第二下」，命中就
     * 打开本面板并返回 true（吞掉这一下）。第一下永远返回 false，
     * 交回正常路由。目标不是有效屏（含多联屏控制器解析失败）也返回
     * false，绝不误吞普通交互。
     */
    public static boolean tryOpen(World world, BlockPos pos, EntityPlayer player) {
        if (world == null || pos == null || player == null) {
            return false;
        }
        BlockPos target = ScreenMultiblock.findController(world, pos);
        if (target == null
                || !(world.getTileEntity(target) instanceof TileRbmkMonitorScreen)) {
            GuiRbmkExport.lastSneakMs = 0L;
            GuiRbmkExport.lastSneakPos = null;
            return false;
        }
        long now = System.currentTimeMillis();
        boolean same = target.equals(GuiRbmkExport.lastSneakPos);
        long dt = now - GuiRbmkExport.lastSneakMs;
        GuiRbmkExport.lastSneakPos = target;
        GuiRbmkExport.lastSneakMs = now;
        if (same && dt >= 0L && dt <= GuiRbmkExport.DOUBLE_CLICK_MS) {
            GuiRbmkExport.lastSneakMs = 0L;
            GuiRbmkExport.lastSneakPos = null;
            Minecraft.getMinecraft().displayGuiScreen(new GuiRbmkExport(target));
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
        this.buttonList.add(new GuiButton(BTN_FMT, x + 10, y + PANEL_H - 74, 92, 20, this.fmtLabel()));
        this.buttonList.add(new GuiButton(BTN_EXPORT, x + 108, y + PANEL_H - 74, 98, 20,
                com.jiezhirbmkmod.client.util.GuiDraw.safe("gui.jiezhirbmkmod.export.do")));
        // v18.0 时间段：历史落盘之后采样窗最长也就容量上限，三档循环足够
        this.buttonList.add(new GuiButton(BTN_DUR, x + 10, y + PANEL_H - 50, PANEL_W - 20, 20,
                this.durLabel()));
        this.buttonList.add(new GuiButton(BTN_CLOSE, x + 10, y + PANEL_H - 26, PANEL_W - 20, 20,
                com.jiezhirbmkmod.client.util.GuiDraw.safe("gui.jiezhirbmkmod.export.close")));
    }

    private String fmtLabel() {
        return com.jiezhirbmkmod.client.util.GuiDraw.safe(this.png
                ? "gui.jiezhirbmkmod.export.fmt_png"
                : "gui.jiezhirbmkmod.export.fmt_csv");
    }

    private String durLabel() {
        return this.seconds <= 0
                ? com.jiezhirbmkmod.client.util.GuiDraw.safe("gui.jiezhirbmkmod.export.dur_all")
                : com.jiezhirbmkmod.client.util.GuiDraw.safe("gui.jiezhirbmkmod.export.dur_s",
                        Integer.valueOf(this.seconds));
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (button.id == BTN_FMT) {
            this.png = !this.png;
            button.displayString = this.fmtLabel();
        } else if (button.id == BTN_DUR) {
            // 全部 → 60 秒 → 300 秒 → 全部；落在循环里找不到（不可能）就回到全部
            int idx = 0;
            for (int i = 0; i < DUR_CHOICES.length; ++i) {
                if (DUR_CHOICES[i] == this.seconds) {
                    idx = i;
                    break;
                }
            }
            this.seconds = DUR_CHOICES[(idx + 1) % DUR_CHOICES.length];
            button.displayString = this.durLabel();
        } else if (button.id == BTN_EXPORT) {
            this.doExport();
        } else if (button.id == BTN_CLOSE) {
            this.mc.displayGuiScreen(null);
        }
    }

    /**
     * 导出：所有失败路径都给聊天栏可读报错，绝不让异常冒出按钮点击
     * （GuiScreen 会把按钮异常直接抛进主循环）。成功输出用户指定文案
     * 并自动关面板。
     */
    private void doExport() {
        try {
            World w = this.mc.world;
            TileRbmkMonitorScreen te = null;
            if (w != null && w.getTileEntity(this.screenPos) instanceof TileRbmkMonitorScreen) {
                te = (TileRbmkMonitorScreen) w.getTileEntity(this.screenPos);
            }
            if (te == null) {
                GuiRbmkExport.chat(TextFormatting.RED,
                        com.jiezhirbmkmod.client.util.GuiDraw.safe("msg.jiezhirbmkmod.export.gone"));
                return;
            }
            GraphHistory h = te.canvas().history();
            if (h == null || !h.hasData()) {
                GuiRbmkExport.chat(TextFormatting.RED,
                        com.jiezhirbmkmod.client.util.GuiDraw.safe("msg.jiezhirbmkmod.export.no_data"));
                return;
            }
            java.io.File out = RbmkExporter.export(this.screenPos, h, this.png, this.seconds);
            // v18.0 自查修复：与 /rbmkexport 同步 —— 时间段导出时报窗口
            // 内的实际采样数，不是全窗 h.size()
            int span = h.size() - RbmkExporter.startIndexOf(h, this.seconds);
            GuiRbmkExport.chat(TextFormatting.GREEN, I18n.format("msg.jiezhirbmkmod.export.saved",
                    new File(Minecraft.getMinecraft().gameDir, RbmkExporter.DIR_NAME).getAbsolutePath()));
            GuiRbmkExport.chat(TextFormatting.GRAY, I18n.format("msg.jiezhirbmkmod.export.file",
                    out.getName(), Integer.valueOf(span)));
            this.mc.displayGuiScreen(null);
        }
        catch (Throwable t) {
            GuiRbmkExport.chat(TextFormatting.RED,
                    I18n.format("msg.jiezhirbmkmod.export.fail", String.valueOf(t)));
        }
    }

    private static void chat(TextFormatting color, String s) {
        if (Minecraft.getMinecraft().player != null) {
            Minecraft.getMinecraft().player.sendMessage(new TextComponentString(color + "[jiezhirbmkmod] "
                    + TextFormatting.RESET + s));
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
                com.jiezhirbmkmod.client.util.GuiDraw.safe("gui.jiezhirbmkmod.export.title"),
                x + 8, y + 8, 0xFFE0E0E0);
        com.jiezhirbmkmod.client.util.GuiDraw.text(this.fontRenderer,
                com.jiezhirbmkmod.client.util.GuiDraw.safe("gui.jiezhirbmkmod.export.pos",
                        Integer.valueOf(this.screenPos.getX()), Integer.valueOf(this.screenPos.getY()),
                        Integer.valueOf(this.screenPos.getZ())),
                x + 8, y + 22, 0xFFC8CDD6);
        // 采样点实时预览：屏还在就有数据（被拆则提示）
        TileRbmkMonitorScreen te = this.mc.world != null
                && this.mc.world.getTileEntity(this.screenPos) instanceof TileRbmkMonitorScreen
                ? (TileRbmkMonitorScreen) this.mc.world.getTileEntity(this.screenPos) : null;
        GraphHistory h = te != null ? te.canvas().history() : null;
        String info = h != null && h.hasData()
                ? com.jiezhirbmkmod.client.util.GuiDraw.safe("gui.jiezhirbmkmod.export.samples",
                        Integer.valueOf(h.size()), String.format(java.util.Locale.ROOT, "%.1f",
                                Double.valueOf(h.size() * (double) GraphHistory.SAMPLE_MS / 1000.0D)))
                : com.jiezhirbmkmod.client.util.GuiDraw.safe(h == null
                        ? "msg.jiezhirbmkmod.export.gone"
                        : "msg.jiezhirbmkmod.export.no_data");
        com.jiezhirbmkmod.client.util.GuiDraw.text(this.fontRenderer, info,
                x + 8, y + 34, h != null && h.hasData() ? 0xFFC8CDD6 : 0xFFE8B84A);
        com.jiezhirbmkmod.client.util.GuiDraw.text(this.fontRenderer,
                com.jiezhirbmkmod.client.util.GuiDraw.safe("gui.jiezhirbmkmod.export.hint"),
                x + 8, y + 48, 0xFF6B7280);

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    /** Esc 与关闭按钮同义 */
    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
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
