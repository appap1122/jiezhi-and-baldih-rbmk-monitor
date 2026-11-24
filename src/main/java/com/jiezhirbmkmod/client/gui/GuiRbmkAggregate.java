package com.jiezhirbmkmod.client.gui;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.jiezhirbmkmod.block.TileRbmkMonitorScreen;
import com.jiezhirbmkmod.dfc.AlertPolicy;
import com.jiezhirbmkmod.dfc.RbmkSnapshot;
import com.jiezhirbmkmod.inventory.ContainerPatrol;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * v17.9 多堆芯聚合排行 GUI（rbmk_aggregate 的"值班站长席"大屏）。
 *
 * <p>布局（256x206）：标题下一条聚合总览（堆芯数 / 在线数 / 总通量 /
 * 最热堆芯），下半是排行列表 —— 每座堆芯一行，按平均柱温<b>降序</b>：
 * 排名 / 堆芯控制台坐标 / 柱温条形图 / 通量 / 告警词。最热一行红底
 * 高亮；熔毁堆芯整行红字。数据全部来自各屏自己的客户端同步
 * （屏 → 堆芯的归属表由 {@code MessageAggregateSync} 在开 GUI 前下发），
 * 中控零转发、断电屏显示"离线"、被拆的屏静默剔除。</p>
 *
 * <p>归堆规则：同一控制台 = 同一座堆芯（屏的快照本来就是控制台视角的
 * 堆级平均），成员屏取告警最高者与快照均值；没绑控制台的屏自成一堆。
 * 排序每帧在客户端重算 —— 至多 6 堆，代价可忽略。</p>
 */
public class GuiRbmkAggregate
extends GuiContainer {

    private static final int PANEL_W = 256;
    private static final int PANEL_H = 206;
    private static final int ROW_Y = 44;
    private static final int ROW_H = 22;

    /**
     * 归属表（MessageAggregateSync 写入）：每组 long[]{sx,sy,sz,rx,ry,rz}。
     * 与巡屏中控一样存成静态暂存区 —— 包到达与 GUI 打开之间由
     * addScheduledTask 串好，打开下一帧就能画。
     */
    private static final List<long[]> PAIRS = new ArrayList<long[]>();

    /** 每座堆芯一行（draw 阶段重算，避免存可变全局） */
    private static final class Row {
        BlockPos reactor;
        double heat;
        double flux;
        boolean meltdown;
        boolean offline;
        boolean powered;
        int alert;
        int members;
        boolean anyPowered;
    }

    public GuiRbmkAggregate() {
        super(new ContainerPatrol());
        this.xSize = PANEL_W;
        this.ySize = PANEL_H;
    }

    /** MessageAggregateSync 的客户端入口 */
    public static void acceptPairs(List<long[]> list) {
        GuiRbmkAggregate.PAIRS.clear();
        if (list == null) {
            return;
        }
        for (int i = 0; i < list.size(); ++i) {
            long[] p = list.get(i);
            if (p != null && p.length >= 6) {
                GuiRbmkAggregate.PAIRS.add(p);
            }
        }
    }

    @Override
    public void initGui() {
        super.initGui();
        // 只读总览 GUI，没有按钮 —— 排行每帧自动刷新
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        // 拆掉的屏整行剔除：PAIRS 保持服务端原样，剔除发生在 draw 阶段
        // 的临时行上，重开 GUI 即恢复 —— 不静默改归属表，行为可预期
    }

    private TileRbmkMonitorScreen screenAt(BlockPos pos) {
        World w = this.mc.world;
        if (w == null || pos == null) {
            return null;
        }
        return w.getTileEntity(pos) instanceof TileRbmkMonitorScreen
                ? (TileRbmkMonitorScreen) w.getTileEntity(pos) : null;
    }

    /** 归堆：PAIRS → 行（同堆芯合并），返回未剔除的有效行（乱序） */
    private List<Row> buildRows() {
        List<Row> rows = new ArrayList<Row>();
        List<BlockPos> keys = new ArrayList<BlockPos>();
        for (int i = 0; i < GuiRbmkAggregate.PAIRS.size(); ++i) {
            long[] p = GuiRbmkAggregate.PAIRS.get(i);
            BlockPos key = new BlockPos((int) p[3], (int) p[4], (int) p[5]);
            TileRbmkMonitorScreen te = this.screenAt(new BlockPos((int) p[0], (int) p[1], (int) p[2]));
            int idx = keys.indexOf(key);
            Row row;
            if (idx < 0) {
                row = new Row();
                row.reactor = key;
                keys.add(key);
                rows.add(row);
            } else {
                row = rows.get(idx);
            }
            if (te == null) {
                // 屏被拆/未加载：这一屏不算数，但堆芯行保留（其它成员屏还在）
                row.offline = true;
                continue;
            }
            row.members++;
            row.anyPowered = row.anyPowered || te.isPowered();
            row.alert = Math.max(row.alert, te.getAlertLevel());
            row.meltdown = row.meltdown || (te.getData() != null && te.getData().meltdown);
            RbmkSnapshot d = te.getData();
            if (d != null && d.linked) {
                if (!row.powered || row.heat <= 0.0D) {
                    row.heat = d.avgHeat;
                    row.flux = d.flux;
                    row.powered = true;
                } else {
                    // 同堆多屏：快照同源，取均值防抖
                    row.heat = (row.heat + d.avgHeat) * 0.5D;
                    row.flux = Math.max(row.flux, d.flux);
                }
            }
        }
        return rows;
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        // 只读总览：不接点击
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        int x = this.guiLeft;
        int y = this.guiTop;
        com.jiezhirbmkmod.client.util.GuiDraw.drawRect(x, y, x + PANEL_W, y + PANEL_H, 0xFF232830);
        com.jiezhirbmkmod.client.util.GuiDraw.hline(x, x + PANEL_W - 1, y, 0xFF555E6B);
        com.jiezhirbmkmod.client.util.GuiDraw.hline(x, x + PANEL_W - 1, y + PANEL_H - 1, 0xFF555E6B);
        com.jiezhirbmkmod.client.util.GuiDraw.vline(x, y, y + PANEL_H - 1, 0xFF555E6B);
        com.jiezhirbmkmod.client.util.GuiDraw.vline(x + PANEL_W - 1, y, y + PANEL_H - 1, 0xFF555E6B);

        String title = com.jiezhirbmkmod.client.util.GuiDraw.safe("gui.jiezhirbmkmod.aggregate.title");
        com.jiezhirbmkmod.client.util.GuiDraw.text(this.fontRenderer, title, x + 8, y + 8, 0xFFE0E0E0);
        String hint = com.jiezhirbmkmod.client.util.GuiDraw.safe("gui.jiezhirbmkmod.aggregate.hint",
                Integer.valueOf(com.jiezhirbmkmod.block.TileRbmkAggregate.SCAN_RADIUS));
        com.jiezhirbmkmod.client.util.GuiDraw.text(this.fontRenderer, hint, x + 8, y + 20, 0xFF6B7280);

        List<Row> rows = this.buildRows();
        // 排行：柱温降序（熔毁恒置顶 —— 排行要回答的第一句话是"哪座最危险"）
        rows.sort((a, b) -> {
            if (a.meltdown != b.meltdown) {
                return a.meltdown ? -1 : 1;
            }
            return Double.compare(b.heat, a.heat);
        });

        // 聚合总览条
        int online = 0;
        double fluxSum = 0.0D;
        double hottest = 0.0D;
        for (Row r : rows) {
            if (r.powered && r.anyPowered) {
                online++;
                fluxSum += r.flux;
            }
            if (r.heat > hottest) {
                hottest = r.heat;
            }
        }
        String summary = com.jiezhirbmkmod.client.util.GuiDraw.safe("gui.jiezhirbmkmod.aggregate.summary",
                Integer.valueOf(rows.size()), Integer.valueOf(online),
                String.format(java.util.Locale.ROOT, "%.0f", Double.valueOf(fluxSum)),
                String.format(java.util.Locale.ROOT, "%.0f", Double.valueOf(hottest)));
        com.jiezhirbmkmod.client.util.GuiDraw.text(this.fontRenderer, summary,
                x + 8, y + 31, 0xFFC8CDD6);

        com.jiezhirbmkmod.client.util.GuiDraw.drawRect(x + 6, y + ROW_Y - 2,
                x + PANEL_W - 6, y + ROW_Y + rows.size() * ROW_H, 0xFF2A2F37);

        if (rows.isEmpty()) {
            com.jiezhirbmkmod.client.util.GuiDraw.text(this.fontRenderer,
                    com.jiezhirbmkmod.client.util.GuiDraw.safe("gui.jiezhirbmkmod.aggregate.empty"),
                    x + 12, y + ROW_Y + 8, 0xFF6B7280);
        }
        int listBottom = y + ROW_Y;
        for (int i = 0; i < rows.size() && listBottom + ROW_H <= y + PANEL_H - 30; ++i) {
            this.drawRow(i, listBottom, rows.get(i));
            listBottom += ROW_H;
        }
    }

    private void drawRow(int rank, int ry, Row row) {
        int x = this.guiLeft;
        int x2 = x + PANEL_W - 6;
        // 最热（rank 0）/熔毁 行底色提示
        if (row.meltdown) {
            com.jiezhirbmkmod.client.util.GuiDraw.drawRect(x + 6, ry, x2, ry + ROW_H, 0x66461B1B);
        } else if (rank == 0 && row.heat > 0.0D) {
            com.jiezhirbmkmod.client.util.GuiDraw.drawRect(x + 6, ry, x2, ry + ROW_H, 0x662E5A2E);
        }
        String head = "#" + (rank + 1) + " (" + row.reactor.getX() + ","
                + row.reactor.getY() + "," + row.reactor.getZ() + ")";
        com.jiezhirbmkmod.client.util.GuiDraw.text(this.fontRenderer, head, x + 12, ry + 3, 0xFFC8CDD6);
        String members = com.jiezhirbmkmod.client.util.GuiDraw.safe("gui.jiezhirbmkmod.aggregate.members",
                Integer.valueOf(row.members));
        com.jiezhirbmkmod.client.util.GuiDraw.text(this.fontRenderer, members,
                x + 12, ry + 12, 0xFF848B96);
        if (!row.powered || !row.anyPowered) {
            com.jiezhirbmkmod.client.util.GuiDraw.text(this.fontRenderer,
                    com.jiezhirbmkmod.client.util.GuiDraw.safe("gui.jiezhirbmkmod.aggregate.no_data"),
                    x + 120, ry + 3, 0xFF6B7280);
            return;
        }
        // 柱温条形图：按 1000°C 基准（与曲线屏冷热刻度同源）取相对长度
        int barX = x + 120;
        int barW = 60;
        int barY = ry + 4;
        com.jiezhirbmkmod.client.util.GuiDraw.drawRect(barX, barY, barX + barW, barY + 6, 0xFF14161A);
        double ratio = row.heat / 1000.0D;
        if (ratio > 1.0D) {
            ratio = 1.0D;
        }
        int fill = (int) Math.round((double) barW * ratio);
        int barColor = ratio >= 0.8D ? 0xFFE85A5A : (ratio >= 0.5D ? 0xFFE8B84A : 0xFF66C266);
        if (fill > 0) {
            com.jiezhirbmkmod.client.util.GuiDraw.drawRect(barX, barY, barX + fill, barY + 6, barColor);
        }
        String val = String.format(java.util.Locale.ROOT, "%.0f\u00b0C  %.1fk",
                Double.valueOf(row.heat), Double.valueOf(row.flux / 1000.0));
        com.jiezhirbmkmod.client.util.GuiDraw.text(this.fontRenderer, val,
                x + 120, ry + 12, 0xFFC8CDD6);
        // 告警词靠右
        int lvl = row.alert;
        int lvlColor = lvl >= AlertPolicy.LEVEL_CRIT ? 0xFFE85A5A
                : (lvl >= AlertPolicy.LEVEL_WARN ? 0xFFE8B84A : 0xFF66C266);
        String lvlText = com.jiezhirbmkmod.client.util.GuiDraw.safe(lvl >= AlertPolicy.LEVEL_CRIT
                ? "gui.jiezhirbmkmod.patrol.crit"
                : (lvl >= AlertPolicy.LEVEL_WARN ? "gui.jiezhirbmkmod.patrol.warn"
                : "gui.jiezhirbmkmod.patrol.normal"));
        com.jiezhirbmkmod.client.util.GuiDraw.text(this.fontRenderer, lvlText,
                x2 - 8 - this.fontRenderer.getStringWidth(lvlText), ry + 3, lvlColor);
        if (row.meltdown) {
            com.jiezhirbmkmod.client.util.GuiDraw.text(this.fontRenderer,
                    com.jiezhirbmkmod.client.util.GuiDraw.safe("gui.jiezhirbmkmod.patrol.meltdown"),
                    x2 - 8 - this.fontRenderer.getStringWidth(
                            com.jiezhirbmkmod.client.util.GuiDraw.safe("gui.jiezhirbmkmod.patrol.meltdown")),
                    ry + 12, 0xFFE85A5A);
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        super.drawScreen(mouseX, mouseY, partialTicks);
    }
}
