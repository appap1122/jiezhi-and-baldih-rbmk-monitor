package com.jiezhirbmkmod.client.media;

import com.jiezhirbmkmod.block.TileRbmkMediaScreen;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * v18.2 PNG 幻灯片轮播线程（媒体屏升级）。
 *
 * <p>终端 {@code media play slides[@秒]} 进入幻灯片模式：客户端后台线程
 * 从本机 {@code rbmk_media/slides/} 目录按文件名顺序读取 PNG，每隔 N 秒
 * 换下一张，循环播放。帧通过与 {@link MediaPlayerThread} 完全相同的
 * 单槽 {@code pollFrame()} 通道交给渲染器 —— 渲染器只在拿到非 null 帧时
 * 上传纹理，所以换片瞬间自动刷新，其余时间零上传开销。</p>
 *
 * <p>目录为空或文件读不出时推一张占位帧（复用「NO SLIDE」观感由渲染器
 * 兜底：占位帧在本类内自绘），绝不抛出、绝不让媒体屏黑屏卡死。
 * {@code shutdown()} 置标志后线程在一秒内自然退出。</p>
 */
public class SlideShowThread
extends Thread {

    /** slides 目录（与 OST / PPT 同一个 rbmk_media 根） */
    private static File slidesDir() {
        return new File(net.minecraft.client.Minecraft.getMinecraft().gameDir,
                "rbmk_media" + File.separator + "slides");
    }

    private final TileRbmkMediaScreen owner;
    private final String modeName;
    private final int secondsPerSlide;
    private volatile boolean alive = true;
    private final Object FRAME_LOCK = new Object();
    private BufferedImage pendingFrame;
    private List<File> slides = Collections.emptyList();

    public SlideShowThread(TileRbmkMediaScreen owner, String modeName) {
        this.owner = owner;
        this.modeName = modeName == null ? "slides" : modeName;
        int sec = 8;
        int at = this.modeName.indexOf('@');
        if (at >= 0) {
            try {
                sec = Integer.parseInt(this.modeName.substring(at + 1));
            }
            catch (NumberFormatException ignored) {
                sec = 8;
            }
        }
        this.secondsPerSlide = Math.max(3, Math.min(60, sec));
        this.setName("RBMK-SlideShow-" + Integer.toHexString(System.identityHashCode(owner)));
        this.setDaemon(true);
    }

    /** 与 MediaPlayerThread 同名接口：MediaClient.sync 用它判断模式是否还匹配 */
    public boolean isFor(String fileName) {
        return fileName != null && fileName.toLowerCase(java.util.Locale.ROOT)
                .matches("slides(@([3-9]|[1-5][0-9]|60))?");
    }

    public boolean isFinished() {
        return !this.alive;
    }

    /** 渲染器每帧取帧：只在换片瞬间返回非 null（其余时间零上传） */
    public BufferedImage pollFrame() {
        synchronized (FRAME_LOCK) {
            BufferedImage f = pendingFrame;
            pendingFrame = null;
            return f;
        }
    }

    private void pushFrame(BufferedImage img) {
        synchronized (FRAME_LOCK) {
            pendingFrame = img;
        }
    }

    public void shutdown() {
        this.alive = false;
    }

    public boolean hasError() {
        return false;
    }

    @Override
    public void run() {
        try {
            int idx = 0;
            long lastSwitch = 0L;
            while (alive) {
                // 目录内容每轮扫描（新增/删除图片即时生效，无需重启播放）
                slides = listSlides();
                if (slides.isEmpty()) {
                    pushFrame(placeholder());
                    sleepIgnoring(2000L);
                    continue;
                }
                if (idx >= slides.size()) {
                    idx = 0;
                }
                File f = slides.get(idx);
                BufferedImage img = null;
                try {
                    img = ImageIO.read(f);
                }
                catch (Throwable ignored) {
                    img = null;
                }
                pushFrame(img != null ? img : placeholder());
                lastSwitch = System.currentTimeMillis();
                idx = (idx + 1) % Math.max(1, slides.size());
                // 分小段睡眠：shutdown 能在一秒内响应
                long remain = secondsPerSlide * 1000L;
                while (alive && remain > 0) {
                    long step = Math.min(500L, remain);
                    sleepIgnoring(step);
                    remain -= step;
                }
                if (System.currentTimeMillis() - lastSwitch < 500L) {
                    sleepIgnoring(500L);
                }
            }
        }
        catch (Throwable t) {
            // 幻灯片线程绝不让客户端崩：安静退场
        }
    }

    private static List<File> listSlides() {
        List<File> out = new ArrayList<File>();
        try {
            File dir = slidesDir();
            File[] files = dir.listFiles();
            if (files != null) {
                for (File f : files) {
                    String n = f.getName().toLowerCase(java.util.Locale.ROOT);
                    if (f.isFile() && n.endsWith(".png") || f.isFile() && n.endsWith(".jpg")
                            || f.isFile() && n.endsWith(".jpeg")) {
                        out.add(f);
                    }
                }
            }
        }
        catch (Throwable ignored) {
        }
        Collections.sort(out);
        return out;
    }

    /** 目录为空 / 单张损坏时的占位帧（黑底红框提示） */
    private static BufferedImage placeholder() {
        int w = 512;
        int h = 384;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D g = img.createGraphics();
        g.setColor(new java.awt.Color(8, 10, 8));
        g.fillRect(0, 0, w, h);
        g.setColor(new java.awt.Color(180, 60, 40));
        g.setStroke(new java.awt.BasicStroke(8.0f));
        g.drawRect(8, 8, w - 16, h - 16);
        g.setColor(new java.awt.Color(220, 90, 60));
        g.setFont(new java.awt.Font(java.awt.Font.SANS_SERIF, java.awt.Font.BOLD, 44));
        String label = "[NO SLIDES]";
        java.awt.FontMetrics fm = g.getFontMetrics();
        g.drawString(label, (w - fm.stringWidth(label)) / 2, h / 2 + 20);
        g.setFont(new java.awt.Font(java.awt.Font.SANS_SERIF, java.awt.Font.PLAIN, 20));
        String hint = "put *.png into rbmk_media/slides/";
        java.awt.FontMetrics fm2 = g.getFontMetrics();
        g.drawString(hint, (w - fm2.stringWidth(hint)) / 2, h / 2 + 70);
        g.dispose();
        return img;
    }

    private static void sleepIgnoring(long ms) {
        try {
            Thread.sleep(ms);
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
