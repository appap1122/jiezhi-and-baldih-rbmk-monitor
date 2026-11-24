package com.jiezhirbmkmod.client.gui;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.jiezhirbmkmod.block.TileRbmkMonitorScreen;
import com.jiezhirbmkmod.dfc.AlertPolicy;
import com.jiezhirbmkmod.dfc.GraphHistory;
import com.jiezhirbmkmod.dfc.RbmkSnapshot;
import com.jiezhirbmkmod.inventory.ContainerPatrol;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * v17.7 巡屏中控 GUI。与签发器同一套"安全工艺"画风：深灰面板、分区
 * 框线、无贴图依赖。
 *
 * <p>布局（256x206）：标题下四行总览（一块绑定屏一行：槽号 / 类型 /
 * 坐标 / 告警等级 / 柱温·通量），点击行选中；下半是选中屏的详情面板
 * （状态大字 + 柱温小条形图，取自该屏客户端画布自采的历史 —— 与曲线
 * 屏同一份数据，不用新同步）。</p>
 *
 * <p>自动轮播：开启后每 5 秒切到下一块有效屏；任何一块的告警等级
 * 高于当前选中屏时立即跳过去 —— "谁告警看谁"。绑定操作不在 GUI 里：
 * 潜行 + 右键中控扫描（见 {@code TileRbmkPatrol}），GUI 只读。</p>
 */
public class GuiRbmkPatrol
extends GuiContainer {

    private static final int PANEL_W = 256;
    private static final int PANEL_H = 206;
    private static final int ROW_Y = 30;
    private static final int ROW_H = 16;
    private static final int DETAIL_Y = 100;
    private static final int ROTATE_TICKS = 100;

    private static final int BTN_AUTO = 0;
    /** v17.9 地图模式按钮：四宫格缩略图总览 */
    private static final int BTN_MAP = 1;

    // 地图模式几何：内容区 244x148，2x2 宫格（120x71，横向缝 4、纵向缝 6）
    private static final int MAP_X = 6;
    private static final int MAP_Y = 28;
    private static final int CELL_W = 120;
    private static final int CELL_H = 71;
    private static final int CELL_DX = 124;
    private static final int CELL_DY = 77;

    /** 服务端下发的绑定清单（MessagePatrolSync 写入） */
    private static final List<BlockPos> BOUND = new ArrayList<BlockPos>();

    private int selected = 0;
    private boolean auto = false;
    private boolean mapMode = false;
    private int rotateTimer = 0;

    public GuiRbmkPatrol() {
        super(new ContainerPatrol());
        this.xSize = PANEL_W;
        this.ySize = PANEL_H;
    }

    /** MessagePatrolSync 的客户端入口 */
    public static void acceptBound(List<long[]> list) {
        BOUND.clear();
        if (list == null) {
            return;
        }
        for (int i = 0; i < list.size(); ++i) {
            long[] p = list.get(i);
            BOUND.add(new BlockPos((int) p[0], (int) p[1], (int) p[2]));
        }
    }

    @Override
    public void initGui() {
        super.initGui();
        this.buttonList.add(new GuiButton(BTN_AUTO, this.guiLeft + 10,
                this.guiTop + PANEL_H - 26, 110, 20, this.autoLabel()));
        this.buttonList.add(new GuiButton(BTN_MAP, this.guiLeft + PANEL_W - 100,
                this.guiTop + PANEL_H - 26, 90, 20, this.mapLabel()));
    }

    private String autoLabel() {
        return I18n.format(this.auto ? "gui.jiezhirbmkmod.patrol.auto_on"
                                     : "gui.jiezhirbmkmod.patrol.auto_off");
    }

    private String mapLabel() {
        return I18n.format(this.mapMode ? "gui.jiezhirbmkmod.patrol.map_on"
                                        : "gui.jiezhirbmkmod.patrol.map_off");
    }

    /** 地图模式下鼠标落点对应的宫格序号，不在格内返回 -1 */
    private int mapCellAt(int mouseX, int mouseY) {
        for (int i = 0; i < 4; ++i) {
            int cx = this.guiLeft + GuiRbmkPatrol.MAP_X + (i % 2) * GuiRbmkPatrol.CELL_DX;
            int cy = this.guiTop + GuiRbmkPatrol.MAP_Y + (i / 2) * GuiRbmkPatrol.CELL_DY;
            if (mouseX >= cx && mouseX < cx + GuiRbmkPatrol.CELL_W
                    && mouseY >= cy && mouseY < cy + GuiRbmkPatrol.CELL_H) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        this.pruneInvalid();
        if (this.auto && !BOUND.isEmpty()) {
            // 1) 谁告警看谁：挑告警等级最高的一块，比当前选中高就立刻跳
            int worst = -1;
            int worstLvl = -1;
            for (int i = 0; i < BOUND.size(); ++i) {
                TileRbmkMonitorScreen te = this.screenAt(BOUND.get(i));
                int lvl = te == null ? -1 : te.getAlertLevel();
                if (lvl > worstLvl) {
                    worstLvl = lvl;
                    worst = i;
                }
            }
            if (worst >= 0 && worstLvl >= AlertPolicy.LEVEL_WARN
                    && worst != this.selected) {
                this.selected = worst;
                this.rotateTimer = 0;
                return;
            }
            // 2) 没人告警：每 5 秒轮到下一块
            if (++this.rotateTimer >= ROTATE_TICKS) {
                this.rotateTimer = 0;
                this.selected = (this.selected + 1) % BOUND.size();
            }
        }
    }

    /** 客户端世界里已拆除/未加载的绑定静默剔除（服务端 NBT 不动） */
    private void pruneInvalid() {
        for (int i = BOUND.size() - 1; i >= 0; --i) {
            TileRbmkMonitorScreen te = this.screenAt(BOUND.get(i));
            if (te == null) {
                BOUND.remove(i);
                if (this.selected >= BOUND.size()) {
                    this.selected = Math.max(0, BOUND.size() - 1);
                }
            }
        }
    }

    private TileRbmkMonitorScreen screenAt(BlockPos pos) {
        World w = this.mc.world;
        if (w == null || pos == null) {
            return null;
        }
        return w.getTileEntity(pos) instanceof TileRbmkMonitorScreen
                ? (TileRbmkMonitorScreen) w.getTileEntity(pos) : null;
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        if (mouseButton != 0) {
            return;
        }
        // 地图模式：点宫格选中
        if (this.mapMode) {
            int cell = this.mapCellAt(mouseX, mouseY);
            if (cell >= 0 && cell < BOUND.size()) {
                this.selected = cell;
            }
            return;
        }
        // 列表模式：点总览行选中
        for (int i = 0; i < BOUND.size(); ++i) {
            int y0 = this.guiTop + ROW_Y + i * ROW_H;
            if (mouseY >= y0 && mouseY < y0 + ROW_H
                    && mouseX >= this.guiLeft + 6 && mouseX < this.guiLeft + PANEL_W - 6) {
                this.selected = i;
                return;
            }
        }
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (button.id == BTN_AUTO) {
            this.auto = !this.auto;
            this.rotateTimer = 0;
            button.displayString = this.autoLabel();
        } else if (button.id == BTN_MAP) {
            this.mapMode = !this.mapMode;
            button.displayString = this.mapLabel();
        }
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        int x = this.guiLeft;
        int y = this.guiTop;
        // v17.8: 绘制统一走 GuiDraw 安全中枢（矩形自动补 alpha / 文案 safe 回退）
        com.jiezhirbmkmod.client.util.GuiDraw.drawRect(x, y, x + PANEL_W, y + PANEL_H, 0xFF232830);
        com.jiezhirbmkmod.client.util.GuiDraw.hline(x, x + PANEL_W - 1, y, 0xFF555E6B);
        com.jiezhirbmkmod.client.util.GuiDraw.hline(x, x + PANEL_W - 1, y + PANEL_H - 1, 0xFF555E6B);
        com.jiezhirbmkmod.client.util.GuiDraw.vline(x, y, y + PANEL_H - 1, 0xFF555E6B);
        com.jiezhirbmkmod.client.util.GuiDraw.vline(x + PANEL_W - 1, y, y + PANEL_H - 1, 0xFF555E6B);

        String title = com.jiezhirbmkmod.client.util.GuiDraw.safe("gui.jiezhirbmkmod.patrol.title");
        com.jiezhirbmkmod.client.util.GuiDraw.text(this.fontRenderer, title, x + 8, y + 8, 0xFFE0E0E0);
        String hint = com.jiezhirbmkmod.client.util.GuiDraw.safe("gui.jiezhirbmkmod.patrol.hint",
                Integer.valueOf(com.jiezhirbmkmod.block.TileRbmkPatrol.SCAN_RADIUS));
        com.jiezhirbmkmod.client.util.GuiDraw.text(this.fontRenderer, hint, x + 8, y + 20, 0xFF6B7280);

        if (this.mapMode) {
            this.drawMap(x, y);
            return;
        }

        com.jiezhirbmkmod.client.util.GuiDraw.drawRect(x + 6, y + ROW_Y - 2,
                x + PANEL_W - 6, y + ROW_Y + BOUND.size() * ROW_H, 0xFF2A2F37);

        for (int i = 0; i < BOUND.size(); ++i) {
            int ry = y + ROW_Y + i * ROW_H;
            TileRbmkMonitorScreen te = this.screenAt(BOUND.get(i));
            if (i == this.selected) {
                com.jiezhirbmkmod.client.util.GuiDraw.drawRect(x + 6, ry,
                        x + PANEL_W - 6, ry + ROW_H, 0xFF2E5A2E);
            }
            if (te == null) {
                com.jiezhirbmkmod.client.util.GuiDraw.text(this.fontRenderer,
                        com.jiezhirbmkmod.client.util.GuiDraw.safe("gui.jiezhirbmkmod.patrol.offline_slot"),
                        x + 12, ry + 4, 0xFF6B7280);
                continue;
            }
            this.drawRow(i, ry, te);
        }

        if (BOUND.isEmpty()) {
            com.jiezhirbmkmod.client.util.GuiDraw.text(this.fontRenderer,
                    com.jiezhirbmkmod.client.util.GuiDraw.safe("gui.jiezhirbmkmod.patrol.empty"),
                    x + 12, y + ROW_Y + 8, 0xFF6B7280);
        }

        // 详情面板
        com.jiezhirbmkmod.client.util.GuiDraw.drawRect(x + 6, y + DETAIL_Y,
                x + PANEL_W - 6, y + PANEL_H - 30, 0xFF2A2F37);
        TileRbmkMonitorScreen sel = BOUND.isEmpty() ? null : this.screenAt(BOUND.get(this.selected));
        if (sel != null) {
            this.drawDetail(x + 10, y + DETAIL_Y + 4, sel);
        }
    }

    /**
     * v17.9 地图模式：2x2 宫格缩略图总览。一格一块绑定屏：边框按状态
     * 上色（熔毁/被黑红、告警黄、断电灰、正常绿），格内画该屏的柱温
     * 迷你条形图（与详情面板同一份客户端自采历史，零新包），底部一行
     * 读数。点格选中；自动轮播的"谁告警看谁"两种模式共用同一套逻辑。
     */
    private void drawMap(int x, int y) {
        for (int i = 0; i < 4; ++i) {
            int cx = x + GuiRbmkPatrol.MAP_X + (i % 2) * GuiRbmkPatrol.CELL_DX;
            int cy = y + GuiRbmkPatrol.MAP_Y + (i / 2) * GuiRbmkPatrol.CELL_DY;
            this.drawMapCell(i, cx, cy);
        }
        if (BOUND.isEmpty()) {
            com.jiezhirbmkmod.client.util.GuiDraw.text(this.fontRenderer,
                    com.jiezhirbmkmod.client.util.GuiDraw.safe("gui.jiezhirbmkmod.patrol.empty"),
                    x + 12, y + GuiRbmkPatrol.MAP_Y + 8, 0xFF6B7280);
        }
    }

    private void drawMapCell(int index, int cx, int cy) {
        int x2 = cx + GuiRbmkPatrol.CELL_W;
        int y2 = cy + GuiRbmkPatrol.CELL_H;
        TileRbmkMonitorScreen te = index < BOUND.size() ? this.screenAt(BOUND.get(index)) : null;
        int border;
        if (te == null) {
            border = index < BOUND.size() ? 0xFF4A5058 : 0xFF33383F; // 离线灰 / 空槽更暗
        } else {
            int lvl = te.getAlertLevel();
            border = lvl >= AlertPolicy.LEVEL_CRIT ? 0xFFE85A5A
                    : (lvl >= AlertPolicy.LEVEL_WARN ? 0xFFE8B84A
                    : (te.isPowered() ? 0xFF66C266 : 0xFF4A5058));
        }
        com.jiezhirbmkmod.client.util.GuiDraw.drawRect(cx, cy, x2, y2, 0xFF1B1F26);
        // 四条边框线
        com.jiezhirbmkmod.client.util.GuiDraw.hline(cx, x2 - 1, cy, border);
        com.jiezhirbmkmod.client.util.GuiDraw.hline(cx, x2 - 1, y2 - 1, border);
        com.jiezhirbmkmod.client.util.GuiDraw.vline(cx, cy, y2 - 1, border);
        com.jiezhirbmkmod.client.util.GuiDraw.vline(x2 - 1, cy, y2 - 1, border);
        // 选中格：内侧白亮细框，一眼分清"正在详情轮到的这块"
        if (index == this.selected && te != null) {
            com.jiezhirbmkmod.client.util.GuiDraw.hline(cx + 2, x2 - 3, cy + 2, 0xFFE8F0FF);
            com.jiezhirbmkmod.client.util.GuiDraw.hline(cx + 2, x2 - 3, y2 - 3, 0xFFE8F0FF);
            com.jiezhirbmkmod.client.util.GuiDraw.vline(cx + 2, cy + 2, y2 - 3, 0xFFE8F0FF);
            com.jiezhirbmkmod.client.util.GuiDraw.vline(x2 - 3, cy + 2, y2 - 3, 0xFFE8F0FF);
        }
        String head = "#" + (index + 1);
        com.jiezhirbmkmod.client.util.GuiDraw.text(this.fontRenderer, head, cx + 5, cy + 5, 0xFFC8CDD6);
        if (te == null) {
            String s = com.jiezhirbmkmod.client.util.GuiDraw.safe(index < BOUND.size()
                    ? "gui.jiezhirbmkmod.patrol.offline_slot"
                    : "gui.jiezhirbmkmod.patrol.map_cell_empty");
            com.jiezhirbmkmod.client.util.GuiDraw.text(this.fontRenderer, s,
                    cx + 5 + this.fontRenderer.getStringWidth(head) + 4, cy + 5, 0xFF6B7280);
            return;
        }
        // 类型名放标题右侧，放不下就算了（不换行、不溢出格）
        String type = com.jiezhirbmkmod.client.util.GuiDraw.safe(
                com.jiezhirbmkmod.item.ItemRefreshTuner.screenKeyOf(te));
        int typeX = cx + 5 + this.fontRenderer.getStringWidth(head) + 4;
        int typeMax = x2 - 5 - typeX;
        if (this.fontRenderer.getStringWidth(type) > typeMax) {
            type = this.fontRenderer.trimStringToWidth(type, Math.max(0, typeMax));
        }
        if (!type.isEmpty()) {
            com.jiezhirbmkmod.client.util.GuiDraw.text(this.fontRenderer, type,
                    typeX, cy + 5, 0xFF848B96);
        }
        // 柱温迷你条形图：与详情面板同一份客户端画布历史
        com.jiezhirbmkmod.client.util.GuiDraw.drawRect(cx + 5, cy + 16, x2 - 5, cy + 48, 0xFF14161A);
        RbmkSnapshot d = te.getData();
        GraphHistory h = te.canvas().history();
        if (h != null && h.hasData()) {
            double axisMax = h.axisMax(0);
            int n = h.size();
            int gw = (x2 - 5) - (cx + 5);
            for (int i2 = 0; i2 < n; ++i2) {
                if (h.gapAt(i2)) {
                    continue;
                }
                double v = h.valueAt(0, i2);
                int bh = (int) Math.round(32.0D * Math.min(1.0, v / axisMax));
                int bx = cx + 5 + (int) ((long) gw * (long) i2
                        / (long) Math.max(1, GraphHistory.CAPACITY - 1));
                int bc = v / axisMax >= 0.85 ? 0xFFE85A5A
                        : (v / axisMax >= 0.5 ? 0xFFE8B84A : 0xFF66C266);
                if (bh > 0) {
                    com.jiezhirbmkmod.client.util.GuiDraw.vline(bx, cy + 48 - bh, cy + 47, bc);
                }
            }
        }
        // 底部一行：状态优先（离线/未绑定/熔毁/告警原因），否则给读数
        int lvl = te.getAlertLevel();
        String bottom;
        int bottomColor;
        if (!te.isPowered()) {
            bottom = com.jiezhirbmkmod.client.util.GuiDraw.safe("gui.jiezhirbmkmod.patrol.pwr_off");
            bottomColor = 0xFF6B7280;
        } else if (d == null || !d.linked) {
            bottom = com.jiezhirbmkmod.client.util.GuiDraw.safe("gui.jiezhirbmkmod.patrol.unlinked");
            bottomColor = 0xFF6B7280;
        } else if (d.meltdown) {
            bottom = com.jiezhirbmkmod.client.util.GuiDraw.safe("gui.jiezhirbmkmod.patrol.meltdown");
            bottomColor = 0xFFE85A5A;
        } else if (lvl >= AlertPolicy.LEVEL_WARN) {
            bottom = te.getAlertReason();
            bottomColor = lvl >= AlertPolicy.LEVEL_CRIT ? 0xFFE85A5A : 0xFFE8B84A;
        } else {
            bottom = d == null ? "--" : String.format(java.util.Locale.ROOT, "%.0f\u00b0C  %.1fk",
                    Double.valueOf(d.avgHeat), Double.valueOf(d.flux / 1000.0));
            bottomColor = 0xFFC8CDD6;
        }
        if (this.fontRenderer.getStringWidth(bottom) > GuiRbmkPatrol.CELL_W - 10) {
            bottom = this.fontRenderer.trimStringToWidth(bottom, GuiRbmkPatrol.CELL_W - 10);
        }
        com.jiezhirbmkmod.client.util.GuiDraw.text(this.fontRenderer, bottom,
                cx + 5, cy + 52, bottomColor);
    }

    private void drawRow(int index, int ry, TileRbmkMonitorScreen te) {
        int x = this.guiLeft;
        RbmkSnapshot d = te.getData();
        int lvl = te.getAlertLevel();
        String type = com.jiezhirbmkmod.client.util.GuiDraw.safe(
                com.jiezhirbmkmod.item.ItemRefreshTuner.screenKeyOf(te));
        String head = "#" + (index + 1) + " " + type + " (" + BOUND.get(index).getX()
                + "," + BOUND.get(index).getY() + "," + BOUND.get(index).getZ() + ")";
        com.jiezhirbmkmod.client.util.GuiDraw.text(this.fontRenderer, head, x + 12, ry + 4, 0xFFC8CDD6);
        // 告警等级与关键读数靠右
        int lvlColor = lvl >= AlertPolicy.LEVEL_CRIT ? 0xFFE85A5A
                : (lvl >= AlertPolicy.LEVEL_WARN ? 0xFFE8B84A : (te.isPowered() ? 0xFF66C266 : 0xFF6B7280));
        String lvlText = com.jiezhirbmkmod.client.util.GuiDraw.safe(lvl >= AlertPolicy.LEVEL_CRIT
                ? "gui.jiezhirbmkmod.patrol.crit"
                : (lvl >= AlertPolicy.LEVEL_WARN ? "gui.jiezhirbmkmod.patrol.warn"
                : "gui.jiezhirbmkmod.patrol.normal"));
        com.jiezhirbmkmod.client.util.GuiDraw.text(this.fontRenderer, lvlText, x + 150, ry + 4, lvlColor);
        String val = d == null || !d.linked ? "--"
                : String.format(java.util.Locale.ROOT, "%.0f\u00b0C  %.1fk",
                        Double.valueOf(d.avgHeat), Double.valueOf(d.flux / 1000.0));
        com.jiezhirbmkmod.client.util.GuiDraw.text(this.fontRenderer, val, x + 186, ry + 4, 0xFFC8CDD6);
    }

    /** 选中屏详情：状态大字 + 柱温历史小条形图（客户端画布自采数据） */
    private void drawDetail(int px, int py, TileRbmkMonitorScreen te) {
        RbmkSnapshot d = te.getData();
        int lvl = te.getAlertLevel();
        String status = !te.isPowered() ? com.jiezhirbmkmod.client.util.GuiDraw.safe("gui.jiezhirbmkmod.patrol.pwr_off")
                : (d == null || !d.linked ? com.jiezhirbmkmod.client.util.GuiDraw.safe("gui.jiezhirbmkmod.patrol.unlinked")
                : (d.meltdown ? com.jiezhirbmkmod.client.util.GuiDraw.safe("gui.jiezhirbmkmod.patrol.meltdown")
                : (lvl >= AlertPolicy.LEVEL_WARN ? te.getAlertReason()
                : com.jiezhirbmkmod.client.util.GuiDraw.safe("gui.jiezhirbmkmod.patrol.running"))));
        int color = d != null && d.meltdown ? 0xFFE85A5A
                : (lvl >= AlertPolicy.LEVEL_WARN ? 0xFFE8B84A
                : (te.isPowered() && d != null && d.linked ? 0xFF66C266 : 0xFF6B7280));
        com.jiezhirbmkmod.client.util.GuiDraw.text(this.fontRenderer, status, px, py, color);
        if (d != null && d.linked && !d.offline) {
            com.jiezhirbmkmod.client.util.GuiDraw.text(this.fontRenderer,
                    String.format(java.util.Locale.ROOT, "%.0f / %.0f \u00b0C",
                            Double.valueOf(d.avgHeat), Double.valueOf(d.maxHeatLimit)),
                    px, py + 12, 0xFFC8CDD6);
        }
        // 小条形图：柱温通道，与曲线屏同一份客户端历史
        GraphHistory h = te.canvas().history();
        if (h == null || !h.hasData()) {
            return;
        }
        int gx = px;
        int gy = py + 26;
        int gw = 120;
        int gh = 40;
        com.jiezhirbmkmod.client.util.GuiDraw.drawRect(gx, gy, gx + gw, gy + gh, 0xFF14161A);
        double axisMax = h.axisMax(0);
        int n = h.size();
        for (int i = 0; i < n; ++i) {
            if (h.gapAt(i)) {
                continue;
            }
            double v = h.valueAt(0, i);
            int bh = (int) Math.round((double) gh * Math.min(1.0, v / axisMax));
            int bx = gx + (int) ((long) gw * (long) i / (long) Math.max(1, GraphHistory.CAPACITY - 1));
            int bc = v / axisMax >= 0.85 ? 0xFFE85A5A
                    : (v / axisMax >= 0.5 ? 0xFFE8B84A : 0xFF66C266);
            if (bh > 0) {
                com.jiezhirbmkmod.client.util.GuiDraw.vline(bx, gy + gh - bh, gy + gh - 1, bc);
            }
        }
        com.jiezhirbmkmod.client.util.GuiDraw.text(this.fontRenderer,
                com.jiezhirbmkmod.client.util.GuiDraw.safe("gui.jiezhirbmkmod.patrol.hist",
                        Integer.valueOf(com.jiezhirbmkmod.dfc.RbmkConfig.windowSeconds())),
                gx + gw + 6, gy + 4, 0xFF848B96);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        super.drawScreen(mouseX, mouseY, partialTicks);
    }
}
