package com.jiezhirbmkmod.client;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;

import javax.imageio.ImageIO;

import com.jiezhirbmkmod.dfc.GraphHistory;

import net.minecraft.util.math.BlockPos;

/**
 * v17.9 曲线历史导出中枢（仅客户端）：{@code /rbmkexport csv|png} 命令与
 * 「蹲下连点两下屏幕 → 导出设置面板」GUI 共用这一份导出逻辑，保证两条
 * 入口写出的文件格式完全一致。
 *
 * <p>目录 {@code <游戏目录>/rbmk_export/} 由游戏在导出时<b>自动创建</b>
 * （{@link #dir()} 里 {@code mkdirs()}），玩家不需要手动建目录。文件名带
 * 屏坐标与时间戳，永不覆盖旧导出。纯客户端：服务端看不到这次导出
 * （审计不含），数据也只是客户端已经收到的那份。回放滚轮只是查看方式，
 * 导出的永远是完整采样窗。</p>
 *
 * <p>本类只允许从客户端类链引用（命令注册在 ClientCommandHandler、
 * GUI 是 GuiScreen），专用服务器永远摸不到。</p>
 */
public final class RbmkExporter {

    /** 导出目录名（相对游戏目录） */
    public static final String DIR_NAME = "rbmk_export";

    static final String[] CHANNEL_NAMES = {
            "Avg heat (C)", "Neutron flux (F)", "Boiler water",
            "Steam total", "Fuel enrichment", "Xenon poison"};

    private RbmkExporter() {
    }

    /**
     * 导出目录：游戏运行时自动创建（幂等 —— 已存在时直接复用）。
     * 返回 null 表示目录建不出来（磁盘只读等），调用方要给用户可读报错。
     */
    public static File dir() {
        File dir = new File(net.minecraft.client.Minecraft.getMinecraft().gameDir, DIR_NAME);
        if (!dir.isDirectory() && !dir.mkdirs()) {
            return null;
        }
        return dir;
    }

    /**
     * 一站式导出：生成不重复文件名并写盘，返回写出的文件。
     *
     * @param png     true=PNG 长图，false=CSV 表格
     * @param seconds 只导最近 N 秒（v18.0 时间段选择）；≤0 = 全部采样窗。
     *                历史落盘后采样窗最长也就是容量上限（默认 60 秒，
     *                配置可到 300 秒），所以这里不做更多分档
     * @throws java.io.IOException 写盘失败（磁盘满/权限）时原样抛出
     */
    public static File export(BlockPos screenPos, GraphHistory h, boolean png, int seconds)
            throws java.io.IOException {
        File dir = RbmkExporter.dir();
        if (dir == null) {
            throw new java.io.IOException("cannot create export dir: "
                    + new File(net.minecraft.client.Minecraft.getMinecraft().gameDir, DIR_NAME)
                            .getAbsolutePath());
        }
        String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String base = screenPos.getX() + "_" + screenPos.getY() + "_" + screenPos.getZ()
                + "_" + stamp;
        if (seconds > 0) {
            base = base + "_" + seconds + "s";
        }
        // 同一秒内对同一块屏导出两次：追加序号而不是覆盖（"永不覆盖"承诺）
        File out = new File(dir, "graph_" + base + (png ? ".png" : ".csv"));
        for (int k = 1; out.exists(); ++k) {
            out = new File(dir, "graph_" + base + "_" + k + (png ? ".png" : ".csv"));
        }
        return png ? RbmkExporter.exportPng(out, h, seconds)
                : RbmkExporter.exportCsv(out, h, seconds);
    }

    /** 旧签名（全量导出），命令与面板旧调用方保留 */
    public static File export(BlockPos screenPos, GraphHistory h, boolean png)
            throws java.io.IOException {
        return RbmkExporter.export(screenPos, h, png, 0);
    }

    /** 时间段换算成起始采样下标：0 = 全部；超出已有数据的秒数自动回到全部 */
    public static int startIndexOf(GraphHistory h, int seconds) {
        if (seconds <= 0 || h == null || h.size() <= 0) {
            return 0;
        }
        int span = (int) Math.min((long) h.size(),
                Math.max(1L, (long) seconds * 1000L / GraphHistory.SAMPLE_MS));
        return Math.max(0, h.size() - span);
    }

    // ---- CSV ----

    static File exportCsv(File file, GraphHistory h, int seconds) throws java.io.IOException {
        int start = RbmkExporter.startIndexOf(h, seconds);
        try (Writer w = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
            w.write("# RBMK-Monitor graph history export\n");
            if (start > 0) {
                w.write("# time window: last " + seconds + " s\n");
            }
            w.write("# columns: index,time_s,avg_heat,flux,water,steam,enrichment,xenon,alert(0=none,1=L1,2=L2)\n");
            w.write("# gaps (data loss) are empty fields; alert column rides the sample grid\n");
            for (int i = start; i < h.size(); ++i) {
                StringBuilder sb = new StringBuilder(128);
                // v18.0 时间段导出：下标与时间轴相对窗口起点重新计（0 起），
                // 复盘时"第 0 秒"就是窗口开始，不用再心算减法
                sb.append(i - start).append(',');
                sb.append(String.format(java.util.Locale.ROOT, "%.1f",
                        Double.valueOf((i - start) * (double) GraphHistory.SAMPLE_MS / 1000.0D))).append(',');
                if (h.gapAt(i)) {
                    // 已写 index,time_s 两个字段，补 7 个空字段（6 通道 + alert）
                    sb.append(",,,,,,,\n");
                    w.write(sb.toString());
                    continue;
                }
                sb.append(RbmkExporter.num(h.valueAt(0, i))).append(',');
                sb.append(RbmkExporter.num(h.valueAt(1, i))).append(',');
                sb.append(RbmkExporter.num(h.valueAt(2, i))).append(',');
                sb.append(RbmkExporter.num(h.valueAt(3, i))).append(',');
                sb.append(RbmkExporter.num(h.valueAt(4, i))).append(',');
                sb.append(RbmkExporter.num(h.valueAt(5, i))).append(',');
                sb.append(h.alertAt(i)).append('\n');
                w.write(sb.toString());
            }
        }
        return file;
    }

    /** CSV 字段：两位小数足够复盘；NaN 不会走到这里（gap 先判） */
    static String num(double v) {
        if (Double.isNaN(v) || Double.isInfinite(v)) {
            return "";
        }
        return String.format(java.util.Locale.ROOT, "%.2f", Double.valueOf(v));
    }

    // ---- PNG 长图 ----

    static File exportPng(File file, GraphHistory h, int seconds) throws java.io.IOException {
        int start = RbmkExporter.startIndexOf(h, seconds);
        int span = h.size() - start;
        int width = 720;
        int panelH = 150;
        int alertH = 26;
        int headerH = 46;
        int height = headerH + GraphHistory.CHANNELS * panelH + alertH + 10;
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        try {
            g.setColor(new Color(0x18, 0x1C, 0x22));
            g.fillRect(0, 0, width, height);
            g.setColor(new Color(0xE0, 0xE0, 0xE0));
            g.setFont(new Font("SansSerif", Font.BOLD, 16));
            g.drawString("RBMK-Monitor graph history export", 14, 22);
            g.setFont(new Font("SansSerif", Font.PLAIN, 12));
            g.setColor(new Color(0x84, 0x8B, 0x96));
            g.drawString(span + " samples, window ~"
                    + String.format(java.util.Locale.ROOT, "%.1f",
                            Double.valueOf(span * (double) GraphHistory.SAMPLE_MS / 1000.0D))
                    + " s" + (start > 0 ? "  (last " + seconds + " s)" : "")
                    + "  (client-sampled, same data as the graph screen)", 14, 40);

            int[] lineColor = {0xFFC864, 0x64C8FF, 0x64E8A0, 0xC8A0FF, 0xFFE8A0, 0xA0FFC8};
            for (int c = 0; c < GraphHistory.CHANNELS; ++c) {
                int px = 56;
                int py = headerH + c * panelH + 8;
                int pw = width - 76;
                int ph = panelH - 26;
                double axisMax = h.axisMax(c);
                g.setColor(new Color(0x22, 0x28, 0x30));
                g.fillRect(px, py, pw, ph);
                g.setColor(new Color(0x3A, 0x42, 0x4C));
                for (int k = 0; k <= GraphHistory.DIVISIONS; ++k) {
                    int gy = py + (int) Math.round((double) ph * (double) k / (double) GraphHistory.DIVISIONS);
                    g.drawLine(px, gy, px + pw, gy);
                }
                // 折线（v18.0：只画时间段窗口内，横轴按窗口重排铺满图宽）
                g.setColor(new Color(lineColor[c]));
                int n = h.size();
                int lastX = Integer.MIN_VALUE;
                int lastY = Integer.MIN_VALUE;
                for (int i = start; i < n; ++i) {
                    if (h.gapAt(i)) {
                        lastX = Integer.MIN_VALUE;
                        continue;
                    }
                    double v = Math.max(0.0D, Math.min(axisMax, h.valueAt(c, i)));
                    int cx = px + (int) ((long) pw * (long) (i - start) / (long) Math.max(1, span - 1));
                    int cy = py + ph - 1 - (int) Math.round((double) (ph - 1) * (v / axisMax));
                    if (lastX >= 0) {
                        g.drawLine(lastX, lastY, cx, cy);
                    }
                    lastX = cx;
                    lastY = cy;
                }
                // 标题与轴
                g.setColor(new Color(0xE0, 0xE0, 0xE0));
                g.drawString(CHANNEL_NAMES[c], 6, py + 14);
                g.setColor(new Color(0x84, 0x8B, 0x96));
                g.drawString(RbmkExporter.axisLabel(axisMax), px + 2, py + 12);
            }
            // 告警色带
            int ay = headerH + GraphHistory.CHANNELS * panelH + 2;
            g.setColor(new Color(0xE0, 0xE0, 0xE0));
            g.drawString("Alerts (L1 yellow / L2 red)", 6, ay + 18);
            int px2 = 220;
            int pw2 = width - 240;
            for (int i = start; i < h.size(); ++i) {
                byte a = h.alertAt(i);
                if (a <= 0 || h.gapAt(i)) {
                    continue;
                }
                int cx = px2 + (int) ((long) pw2 * (long) (i - start) / (long) Math.max(1, span - 1));
                g.setColor(a >= 2 ? new Color(0xE8, 0x5A, 0x5A) : new Color(0xE8, 0xB8, 0x4A));
                g.drawLine(cx, ay, cx, ay + 18);
            }
        }
        finally {
            g.dispose();
        }
        ImageIO.write(img, "png", file);
        return file;
    }

    /** 轴顶标签：按量程挑单位，图上不再写一串 0 */
    private static String axisLabel(double axisMax) {
        if (axisMax >= 1000.0D) {
            return String.format(java.util.Locale.ROOT, "peak %.0fk", Double.valueOf(axisMax / 1000.0D));
        }
        return String.format(java.util.Locale.ROOT, "peak %.0f", Double.valueOf(axisMax));
    }
}
