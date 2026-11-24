package com.jiezhirbmkmod.client.media;

import com.jiezhirbmkmod.block.TileRbmkMediaScreen;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;
import java.util.concurrent.atomic.AtomicReference;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.SourceDataLine;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Decoder thread for one media screen.
 *
 * JavaCV is optional and is accessed purely through reflection: the mod never
 * hard-links against it, so a missing / mismatched JavaCV install can only ever
 * produce the "[UNSUPPORTED CODEC]" placeholder, never a NoClassDefFoundError.
 *
 * H.264 video frames are converted to BufferedImages (one-slot queue, so memory
 * use is bounded), AAC audio is written to a SourceDataLine; both are paced by
 * one System.nanoTime() clock.
 */
@SideOnly(Side.CLIENT)
public class MediaPlayerThread
extends Thread {

    private static final int ERROR_W = 640;
    private static final int ERROR_H = 480;
    private static final String GRABBER = "org.bytedeco.javacv.FFmpegFrameGrabber";
    private static final String CONVERTER = "org.bytedeco.javacv.Java2DFrameConverter";
    private static final String FRAME = "org.bytedeco.javacv.Frame";
    private static final String AVCODEC = "org.bytedeco.ffmpeg.global.avcodec";
    private static final String AVUTIL = "org.bytedeco.ffmpeg.global.avutil";

    private final String fileName;
    private final File source;
    private final AtomicReference<BufferedImage> frameRef = new AtomicReference<BufferedImage>();
    private volatile int volume = 80;
    private volatile boolean running = true;
    private volatile boolean finished;
    private volatile String error = "";
    private SourceDataLine audioLine;

    /** resolved once: JavaCV either from the mod classpath or from mods/ + rbmk_media/libs/ */
    private static volatile boolean decoderLookupDone;
    private static volatile ClassLoader decoderLoader;

    public MediaPlayerThread(TileRbmkMediaScreen owner, String fileName, int volume) {
        super("RBMK-Media-" + (owner == null ? "?" : owner.getPos()));
        this.fileName = fileName == null ? "" : fileName;
        this.volume = Math.max(0, Math.min(100, volume));
        File dir = new File(Minecraft.getMinecraft().gameDir, "rbmk_media");
        this.source = new File(dir, this.fileName);
        this.setDaemon(true);
    }

    public boolean isFor(String name) {
        return name != null && name.equals(this.fileName);
    }

    public boolean hasError() {
        return !this.error.isEmpty();
    }

    public String getError() {
        return this.error;
    }

    public boolean isFinished() {
        return this.finished;
    }

    public void setVolume(int volume) {
        this.volume = Math.max(0, Math.min(100, volume));
    }

    /** renderer pulls one decoded picture per client frame (bounded 1-slot queue) */
    public BufferedImage pollFrame() {
        return this.frameRef.getAndSet(null);
    }

    public void shutdown() {
        this.running = false;
        this.closeAudio();
        try {
            this.interrupt();
        }
        catch (Throwable ignored) {
        }
    }

    /**
     * Class loader that can see the optional JavaCV jars. Order:
     * 1) the normal mod classpath (jars dropped into .minecraft/mods/ are added
     *    to Forge's LaunchClassLoader on 1.12.2),
     * 2) a URLClassLoader built from .minecraft/mods/*.jar and
     *    .minecraft/rbmk_media/libs/*.jar as a fallback.
     */
    public static synchronized ClassLoader decoderClassLoader() {
        if (MediaPlayerThread.decoderLookupDone) {
            return MediaPlayerThread.decoderLoader;
        }
        MediaPlayerThread.decoderLookupDone = true;
        try {
            Class.forName(GRABBER, false, MediaPlayerThread.class.getClassLoader());
            MediaPlayerThread.decoderLoader = MediaPlayerThread.class.getClassLoader();
            return MediaPlayerThread.decoderLoader;
        }
        catch (Throwable ignored) {
            // JavaCV is not on the mod classpath yet
        }
        try {
            java.util.List<java.net.URL> urls = new java.util.ArrayList<java.net.URL>();
            File gameDir = Minecraft.getMinecraft().gameDir;
            MediaPlayerThread.collectJars(new File(gameDir, "mods"), urls);
            MediaPlayerThread.collectJars(
                    new File(gameDir, "rbmk_media" + File.separator + "libs"), urls);
            if (!urls.isEmpty()) {
                MediaPlayerThread.decoderLoader = new java.net.URLClassLoader(
                        urls.toArray(new java.net.URL[0]), MediaPlayerThread.class.getClassLoader());
            }
        }
        catch (Throwable ignored) {
            MediaPlayerThread.decoderLoader = null;
        }
        return MediaPlayerThread.decoderLoader;
    }

    private static void collectJars(File dir, java.util.List<java.net.URL> out) {
        if (dir == null || !dir.isDirectory()) {
            return;
        }
        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }
        for (File f : files) {
            if (f == null || !f.isFile()) {
                continue;
            }
            if (!f.getName().toLowerCase(java.util.Locale.ROOT).endsWith(".jar")) {
                continue;
            }
            try {
                out.add(f.toURI().toURL());
            }
            catch (Throwable ignored) {
            }
        }
    }

    private static Class<?> decoderClass(String name) throws ClassNotFoundException {
        ClassLoader loader = MediaPlayerThread.decoderClassLoader();
        return loader == null ? Class.forName(name) : Class.forName(name, true, loader);
    }

    /** used by the install prompt: true when JavaCV can be reached from mods/ or libs/ */
    public static boolean isDecoderPresent() {
        try {
            MediaPlayerThread.decoderClass(GRABBER);
            return true;
        }
        catch (Throwable ignored) {
            return false;
        }
    }

    @Override
    public void run() {
        Object grabber = null;
        Class<?> grabberClass = null;
        try {
            if (!this.source.isFile()) {
                this.fail("FILE NOT FOUND");
                return;
            }
            grabberClass = MediaPlayerThread.decoderClass(GRABBER);
            Object converter = MediaPlayerThread.decoderClass(CONVERTER).getConstructor().newInstance();
            Method toImage = MediaPlayerThread.decoderClass(CONVERTER)
                    .getMethod("getBufferedImage", MediaPlayerThread.decoderClass(FRAME));
            Class<?> frameClass = MediaPlayerThread.decoderClass(FRAME);
            Field imageField = frameClass.getField("image");
            Field samplesField = frameClass.getField("samples");
            Field channelField = frameClass.getField("audioChannels");
            Method grab = grabberClass.getMethod("grab");

            grabber = grabberClass.getConstructor(File.class).newInstance(this.source);
            grabberClass.getMethod("setVideoCodec", int.class).invoke(grabber,
                    Integer.valueOf(MediaPlayerThread.staticInt(AVCODEC, "AV_CODEC_ID_H264", 27)));
            grabberClass.getMethod("setAudioCodec", int.class).invoke(grabber,
                    Integer.valueOf(MediaPlayerThread.staticInt(AVCODEC, "AV_CODEC_ID_AAC", 86018)));
            grabberClass.getMethod("setSampleFormat", int.class).invoke(grabber,
                    Integer.valueOf(MediaPlayerThread.staticInt(AVUTIL, "AV_SAMPLE_FMT_S16", 1)));
            grabberClass.getMethod("start").invoke(grabber);
            this.openAudio(((Integer) grabberClass.getMethod("getSampleRate").invoke(grabber)).intValue(),
                    ((Integer) grabberClass.getMethod("getAudioChannels").invoke(grabber)).intValue());

            double fps = ((Number) grabberClass.getMethod("getVideoFrameRate").invoke(grabber)).doubleValue();
            if (fps <= 1.0D) {
                fps = 25.0D;
            }
            long frameMs = Math.max(1L, (long) (1000.0D / fps));
            long startNs = System.nanoTime();
            long nextMs = 0L;
            Object frame = grab.invoke(grabber);
            while (this.running && frame != null) {
                if (imageField.get(frame) != null) {
                    BufferedImage image = (BufferedImage) toImage.invoke(converter, frame);
                    if (image != null) {
                        this.frameRef.set(image);
                        long nowMs = (System.nanoTime() - startNs) / 1000000L;
                        long sleep = nextMs - nowMs;
                        if (sleep > 0L) {
                            Thread.sleep(Math.min(sleep, 30L));
                        } else if (nowMs - nextMs > 500L) {
                            nextMs = nowMs;
                        }
                        nextMs += frameMs;
                    }
                }
                int channels = channelField.getInt(frame);
                Object samples = samplesField.get(frame);
                if (samples instanceof Buffer[]) {
                    this.writeAudio((Buffer[]) samples, channels);
                }
                frame = grab.invoke(grabber);
            }
        }
        catch (Throwable t) {
            this.fail("UNSUPPORTED CODEC");
        }
        finally {
            this.closeAudio();
            if (grabber != null && grabberClass != null) {
                try {
                    grabberClass.getMethod("stop").invoke(grabber);
                }
                catch (Throwable ignored) {
                }
                try {
                    grabberClass.getMethod("release").invoke(grabber);
                }
                catch (Throwable ignored) {
                }
            }
            grabber = null;
            this.finished = true;
        }
    }

    private static int staticInt(String className, String field, int fallback) {
        try {
            return MediaPlayerThread.decoderClass(className).getField(field).getInt(null);
        }
        catch (Throwable ignored) {
            return fallback;
        }
    }

    private void fail(String message) {
        this.error = message;
        this.frameRef.set(MediaPlayerThread.errorImage(message));
    }

    private void openAudio(int sampleRate, int channels) {
        try {
            if (sampleRate <= 0 || channels <= 0) {
                return;
            }
            AudioFormat format = new AudioFormat((float) sampleRate, 16, channels, true, false);
            DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);
            if (!AudioSystem.isLineSupported(info)) {
                return;
            }
            SourceDataLine line = (SourceDataLine) AudioSystem.getLine(info);
            line.open(format);
            line.start();
            this.audioLine = line;
        }
        catch (Throwable ignored) {
            // audio is optional: degrade to silent playback
            this.audioLine = null;
        }
    }

    private void closeAudio() {
        SourceDataLine line = this.audioLine;
        this.audioLine = null;
        if (line == null) {
            return;
        }
        try {
            line.stop();
        }
        catch (Throwable ignored) {
        }
        try {
            line.flush();
        }
        catch (Throwable ignored) {
        }
        try {
            line.close();
        }
        catch (Throwable ignored) {
        }
    }

    private void writeAudio(Buffer[] samples, int channelsIn) {
        SourceDataLine line = this.audioLine;
        if (line == null || samples == null || samples.length == 0 || samples[0] == null) {
            return;
        }
        try {
            int channels = channelsIn > 0 ? channelsIn : 1;
            int frames = samples[0].remaining();
            if (frames <= 0) {
                return;
            }
            float gain = (float) Math.max(0, Math.min(100, this.volume)) / 100.0f;
            ByteBuffer out = ByteBuffer.allocate(frames * channels * 2).order(ByteOrder.LITTLE_ENDIAN);
            for (int i = 0; i < frames; ++i) {
                for (int c = 0; c < channels; ++c) {
                    Buffer b = samples[Math.min(c, samples.length - 1)];
                    short sample = 0;
                    if (b instanceof ShortBuffer) {
                        ShortBuffer sb = (ShortBuffer) b;
                        int idx = sb.position() + i;
                        if (idx < sb.limit()) {
                            sample = sb.get(idx);
                        }
                    }
                    int value = (int) (sample * gain);
                    out.put((byte) (value & 0xFF));
                    out.put((byte) ((value >> 8) & 0xFF));
                }
            }
            line.write(out.array(), 0, out.position());
        }
        catch (Throwable ignored) {
        }
    }

    private static BufferedImage errorImage(String text) {
        BufferedImage image = new BufferedImage(ERROR_W, ERROR_H, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setColor(new Color(0, 0, 0, 255));
            g.fillRect(0, 0, ERROR_W, ERROR_H);
            g.setColor(new Color(230, 40, 40));
            g.setFont(new Font("SansSerif", Font.BOLD, 36));
            FontMetrics fm = g.getFontMetrics();
            String line = text == null || text.isEmpty() ? "[UNSUPPORTED CODEC]" : "[" + text + "]";
            g.drawString(line, (ERROR_W - fm.stringWidth(line)) / 2, ERROR_H / 2);
        }
        finally {
            g.dispose();
        }
        return image;
    }
}
