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
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

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
 * <p>v18.0 剪辑导出：{@link #exportClip} 支持 [from,to) 采样区间
 * （剪辑 GUI 白色选框），{@link #exportMp4Clip} 把选区渲染成帧后编码为
 * MP4 —— 编码器走多媒体屏的可选 JavaCV/FFmpeg 运行库（反射加载，
 * 编译期零 JavaCV 类名），没装依赖时自动降级为「PNG 帧序列 zip」，
 * 绝不崩游戏。</p>
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

    /** MP4 帧率：每个采样一帧，10 fps 够看且文件小 */
    public static final int MP4_FPS = 10;
    /** MP4 帧数上限（1800 帧 = 3 分钟选区），防手滑框选全历史卡死客户端 */
    public static final int MP4_MAX_FRAMES = 1800;

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
        int start = RbmkExporter.startIndexOf(h, seconds);
        return RbmkExporter.exportClip(screenPos, h, start, h.size(), png,
                seconds > 0 ? "_" + seconds + "s" : "");
    }

    /** 旧签名（全量导出），命令与面板旧调用方保留 */
    public static File export(BlockPos screenPos, GraphHistory h, boolean png)
            throws java.io.IOException {
        return RbmkExporter.export(screenPos, h, png, 0);
    }

    /**
     * v18.0 剪辑导出：[fromIdx, toIdx) 采样区间。suffix 拼进文件名
     * （如 {@code "_60s"} 或 {@code "_clip"}），保证不同导出不混淆。
     */
    public static File exportClip(BlockPos screenPos, GraphHistory h,
            int fromIdx, int toIdx, boolean png, String suffix)
            throws java.io.IOException {
        int[] range = RbmkExporter.clampRange(h, fromIdx, toIdx);
        fromIdx = range[0];
        toIdx = range[1];
        File dir = RbmkExporter.dir();
        if (dir == null) {
            throw new java.io.IOException("cannot create export dir: "
                    + new File(net.minecraft.client.Minecraft.getMinecraft().gameDir, DIR_NAME)
                            .getAbsolutePath());
        }
        String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String base = screenPos.getX() + "_" + screenPos.getY() + "_" + screenPos.getZ()
                + "_" + stamp + suffix;
        // 同一秒内对同一块屏导出两次：追加序号而不是覆盖（"永不覆盖"承诺）
        File out = new File(dir, "graph_" + base + (png ? ".png" : ".csv"));
        for (int k = 1; out.exists(); ++k) {
            out = new File(dir, "graph_" + base + "_" + k + (png ? ".png" : ".csv"));
        }
        return png ? RbmkExporter.exportPng(out, h, fromIdx, toIdx)
                : RbmkExporter.exportCsv(out, h, fromIdx, toIdx);
    }

    /** 选区钳位：逆序自动交换，越界钳到 [0, size]；空历史退化为空区间 */
    private static int[] clampRange(GraphHistory h, int from, int to) {
        int n = h == null ? 0 : h.size();
        if (from > to) {
            int t = from;
            from = to;
            to = t;
        }
        from = Math.max(0, Math.min(n, from));
        to = Math.max(from, Math.min(n, to));
        return new int[]{from, to};
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
        return RbmkExporter.exportCsv(file, h, start, h.size());
    }

    /** v18.0：区间版 CSV（下标与时间轴相对窗口起点重新计，0 起） */
    static File exportCsv(File file, GraphHistory h, int fromIdx, int toIdx)
            throws java.io.IOException {
        try (Writer w = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
            w.write("# RBMK-Monitor graph history export\n");
            if (fromIdx > 0) {
                w.write("# time window: clip " + fromIdx + ".." + toIdx + "\n");
            }
            w.write("# columns: index,time_s,avg_heat,flux,water,steam,enrichment,xenon,alert(0=none,1=L1,2=L2)\n");
            w.write("# gaps (data loss) are empty fields; alert column rides the sample grid\n");
            for (int i = fromIdx; i < toIdx; ++i) {
                StringBuilder sb = new StringBuilder(128);
                sb.append(i - fromIdx).append(',');
                sb.append(String.format(java.util.Locale.ROOT, "%.1f",
                        Double.valueOf((i - fromIdx) * (double) GraphHistory.SAMPLE_MS / 1000.0D))).append(',');
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
    public static String num(double v) {
        if (Double.isNaN(v) || Double.isInfinite(v)) {
            return "";
        }
        return String.format(java.util.Locale.ROOT, "%.2f", Double.valueOf(v));
    }

    // ---- PNG 长图 ----

    static File exportPng(File file, GraphHistory h, int seconds) throws java.io.IOException {
        int start = RbmkExporter.startIndexOf(h, seconds);
        return RbmkExporter.exportPng(file, h, start, h.size());
    }

    /** v18.0：区间版 PNG */
    static File exportPng(File file, GraphHistory h, int fromIdx, int toIdx)
            throws java.io.IOException {
        BufferedImage img = RbmkExporter.renderFrame(h, fromIdx, toIdx, null);
        ImageIO.write(img, "png", file);
        return file;
    }

    // ---- v18.0 帧渲染（剪辑预览 / MP4 编码 / PNG 三方共用同一份绘制） ----

    /** 帧尺寸：与 PNG 导出一致 */
    static final int FRAME_W = 720;
    static final int FRAME_H = 726; // 46 + 6*150 + 26 + 10

    /**
     * 渲染 [fromIdx, toIdx) 区间的一帧面板图。
     * {@code clipInfo} 非空时在标题行追加剪辑说明；横轴永远铺满图宽
     * （v18.0 修复过半窗只画半宽的问题，这里天然全宽）。
     */
    static BufferedImage renderFrame(GraphHistory h, int fromIdx, int toIdx, String clipInfo) {
        int span = Math.max(1, toIdx - fromIdx);
        int width = RbmkExporter.FRAME_W;
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
                    + " s" + (clipInfo == null ? "" : "  " + clipInfo)
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
                // 折线：只画区间内，横轴按区间重排铺满图宽
                g.setColor(new Color(lineColor[c]));
                int lastX = Integer.MIN_VALUE;
                int lastY = Integer.MIN_VALUE;
                for (int i = fromIdx; i < toIdx; ++i) {
                    if (h.gapAt(i)) {
                        lastX = Integer.MIN_VALUE;
                        continue;
                    }
                    double v = Math.max(0.0D, Math.min(axisMax, h.valueAt(c, i)));
                    int cx = px + (int) ((long) pw * (long) (i - fromIdx) / (long) Math.max(1, span - 1));
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
            for (int i = fromIdx; i < toIdx; ++i) {
                byte a = h.alertAt(i);
                if (a <= 0 || h.gapAt(i)) {
                    continue;
                }
                int cx = px2 + (int) ((long) pw2 * (long) (i - fromIdx) / (long) Math.max(1, span - 1));
                g.setColor(a >= 2 ? new Color(0xE8, 0x5A, 0x5A) : new Color(0xE8, 0xB8, 0x4A));
                g.drawLine(cx, ay, cx, ay + 18);
            }
        }
        finally {
            g.dispose();
        }
        return img;
    }

    // ---- v18.0 MP4 / 帧序列 ----

    /** MP4 编码是否可用（多媒体屏的 JavaCV 运行库已装 → true）。进程内只探一次。 */
    private static volatile boolean mp4Probed;
    private static volatile boolean mp4Available;

    public static boolean mp4Available() {
        if (!RbmkExporter.mp4Probed) {
            synchronized (RbmkExporter.class) {
                if (!RbmkExporter.mp4Probed) {
                    try {
                        Class.forName("org.bytedeco.javacv.FFmpegFrameRecorder");
                        RbmkExporter.mp4Available = true;
                    }
                    catch (Throwable t) {
                        RbmkExporter.mp4Available = false;
                    }
                    RbmkExporter.mp4Probed = true;
                }
            }
        }
        return RbmkExporter.mp4Available;
    }

    /**
     * v18.0 剪辑导出 MP4：把 [fromIdx, toIdx) 渲染成帧序列编码为 H.264。
     * FFmpeg 运行库不存在时抛 {@link Mp4UnavailableException}，调用方
     * 应降级调 {@link #exportFramesZip}。
     *
     * <p>视频语义：每帧显示「以播放头为终点的滚动窗口」（窗口长度 =
     * min(选区长度, 240 采样)），播放头推进时曲线滚动 —— 等价于监控
     * 录像回放，比整段静态长图有意义得多。</p>
     */
    public static File exportMp4Clip(BlockPos screenPos, GraphHistory h,
            int fromIdx, int toIdx) throws java.io.IOException {
        int[] range = RbmkExporter.clampRange(h, fromIdx, toIdx);
        final int f0 = range[0];
        final int t0 = range[1];
        final int frames = t0 - f0;
        if (frames <= 0) {
            throw new java.io.IOException("empty clip range");
        }
        if (frames > RbmkExporter.MP4_MAX_FRAMES) {
            throw new java.io.IOException("clip too long for mp4: " + frames + " samples (max "
                    + RbmkExporter.MP4_MAX_FRAMES + ")");
        }
        if (!RbmkExporter.mp4Available()) {
            throw new Mp4UnavailableException();
        }
        File dir = RbmkExporter.dir();
        if (dir == null) {
            throw new java.io.IOException("cannot create export dir");
        }
        String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        File out = new File(dir, "graph_" + screenPos.getX() + "_" + screenPos.getY() + "_"
                + screenPos.getZ() + "_" + stamp + "_clip.mp4");
        for (int k = 1; out.exists(); ++k) {
            out = new File(dir, "graph_" + screenPos.getX() + "_" + screenPos.getY() + "_"
                    + screenPos.getZ() + "_" + stamp + "_clip_" + k + ".mp4");
        }
        int win = Math.min(frames, 240);
        try {
            RbmkExporter.encodeMp4(out.getAbsolutePath(),
                    RbmkExporter.FRAME_W, RbmkExporter.FRAME_H, RbmkExporter.MP4_FPS,
                    new RbmkExporter.FrameSource() {
                        private int i;
                        @Override
                        public int frameCount() {
                            return frames;
                        }
                        @Override
                        public BufferedImage nextFrame() {
                            // 第 i 帧：播放头从选区头滚到选区尾，每帧显示
                            // 「以播放头为终点的固定长度滚动窗口」
                            int head = Math.min(t0, f0 + win + this.i);
                            int tail = Math.max(f0, head - win + 1);
                            ++this.i;
                            return RbmkExporter.renderFrame(h, tail, head, "clip playback");
                        }
                    });
        }
        catch (java.io.IOException e) {
            throw e;
        }
        catch (Throwable t) {
            // 反射链任何一环失败（比如 FFmpeg 本机库缺失但类在）都降级
            throw new Mp4UnavailableException(t);
        }
        return out;
    }

    /** 帧供给：把「第 i 帧」解耦成可反射编码的回调 */
    private interface FrameSource {
        int frameCount();

        BufferedImage nextFrame();
    }

    /** FFmpeg 运行库缺失（或编码链路失败）的降级信号 */
    public static final class Mp4UnavailableException extends java.io.IOException {
        public Mp4UnavailableException() {
            super("JavaCV/FFmpeg runtime not installed");
        }

        public Mp4UnavailableException(Throwable cause) {
            super("mp4 encode failed: " + cause, cause);
        }
    }

    /**
     * 全反射编码 MP4（编译期零 JavaCV 类名）。帧供给回调和编码解耦，
     * 编码链任何一环失败都以非 IOException 抛出，由调用方降级。
     */
    private static void encodeMp4(String path, int w, int h, int fps, FrameSource src)
            throws Exception {
        Class<?> recCls = Class.forName("org.bytedeco.javacv.FFmpegFrameRecorder");
        Class<?> frameCls = Class.forName("org.bytedeco.javacv.Frame");
        Class<?> convCls = Class.forName("org.bytedeco.javacv.Java2DFrameConverter");
        Object rec = recCls.getConstructor(String.class, int.class, int.class)
                .newInstance(path, Integer.valueOf(w), Integer.valueOf(h));
        recCls.getMethod("setFrameRate", double.class).invoke(rec, Double.valueOf(fps));
        recCls.getMethod("setVideoCodecname", String.class).invoke(rec, "libx264");
        recCls.getMethod("start").invoke(rec);
        Object conv = convCls.getConstructor().newInstance();
        java.lang.reflect.Method convert = convCls.getMethod("convert", BufferedImage.class);
        java.lang.reflect.Method record = recCls.getMethod("record", frameCls);
        try {
            int n = src.frameCount();
            for (int i = 0; i < n; ++i) {
                BufferedImage img = src.nextFrame();
                if (img != null) {
                    record.invoke(rec, convert.invoke(conv, img));
                }
            }
        }
        finally {
            try {
                recCls.getMethod("stop").invoke(rec);
            }
            catch (Throwable ignored) {
                // stop 失败不吞 release
            }
            try {
                recCls.getMethod("release").invoke(rec);
            }
            catch (Throwable ignored) {
            }
        }
    }

    /**
     * 降级导出：帧序列 PNG 打成 zip（每帧同 MP4 语义的滚动窗口）。
     * 用户可用任意播放器/剪辑软件逐帧合成，或装上依赖后再出 mp4。
     */
    public static File exportFramesZip(BlockPos screenPos, GraphHistory h,
            int fromIdx, int toIdx) throws java.io.IOException {
        int[] range = RbmkExporter.clampRange(h, fromIdx, toIdx);
        fromIdx = range[0];
        toIdx = range[1];
        int frames = toIdx - fromIdx;
        if (frames <= 0) {
            throw new java.io.IOException("empty clip range");
        }
        File dir = RbmkExporter.dir();
        if (dir == null) {
            throw new java.io.IOException("cannot create export dir");
        }
        String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        File out = new File(dir, "graph_" + screenPos.getX() + "_" + screenPos.getY() + "_"
                + screenPos.getZ() + "_" + stamp + "_clip_frames.zip");
        for (int k = 1; out.exists(); ++k) {
            out = new File(dir, "graph_" + screenPos.getX() + "_" + screenPos.getY() + "_"
                    + screenPos.getZ() + "_" + stamp + "_clip_frames_" + k + ".zip");
        }
        int win = Math.min(frames, 240);
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(out))) {
            for (int i = 0; i < frames; ++i) {
                int head = Math.min(toIdx, fromIdx + Math.min(win, frames) + i);
                int tail = Math.max(fromIdx, head - win + 1);
                BufferedImage img = RbmkExporter.renderFrame(h, tail, head, "clip frame " + (i + 1) + "/" + frames);
                zos.putNextEntry(new ZipEntry(String.format(java.util.Locale.ROOT,
                        "frame_%06d.png", Integer.valueOf(i + 1))));
                ImageIO.write(img, "png", zos);
                zos.closeEntry();
            }
        }
        return out;
    }

    /** 轴顶标签：按量程挑单位，图上不再写一串 0 */
    private static String axisLabel(double axisMax) {
        if (axisMax >= 1000.0D) {
            return String.format(java.util.Locale.ROOT, "peak %.0fk", Double.valueOf(axisMax / 1000.0D));
        }
        return String.format(java.util.Locale.ROOT, "peak %.0f", Double.valueOf(axisMax));
    }
}
