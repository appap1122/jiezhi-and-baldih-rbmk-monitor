package com.jiezhirbmkmod.client.media;

import com.jiezhirbmkmod.block.TileRbmkMediaScreen;
import java.awt.image.BufferedImage;
import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/** Client-side bridge between a media tile and its decoder thread / GL texture. */
@SideOnly(Side.CLIENT)
public final class MediaClient {

    private static final Map<TileRbmkMediaScreen, MediaPlayerThread> PLAYERS =
            new IdentityHashMap<TileRbmkMediaScreen, MediaPlayerThread>();
    /** v18.2 幻灯片线程与视频解码线程分开管理（互不干扰） */
    private static final Map<TileRbmkMediaScreen, SlideShowThread> SLIDES =
            new IdentityHashMap<TileRbmkMediaScreen, SlideShowThread>();
    private static final Map<TileRbmkMediaScreen, Integer> TEXTURES =
            new IdentityHashMap<TileRbmkMediaScreen, Integer>();
    /** hard cap on concurrent decoders, protects RAM and audio devices */
    private static final int MAX_PLAYERS = 4;

    private MediaClient() {
    }

    /** v18.2 幻灯片模式名（slides / slides@秒） */
    private static boolean isSlideMode(String file) {
        return file != null && file.toLowerCase(java.util.Locale.ROOT)
                .matches("slides(@([3-9]|[1-5][0-9]|60))?");
    }

    /** create / keep / drop the decoder thread to match the synced state */
    public static void sync(TileRbmkMediaScreen te) {
        if (te == null || te.getWorld() == null) {
            return;
        }
        // v18.2 幻灯片模式分支：不走视频解码线程池
        if (te.isMediaPlaying() && isSlideMode(te.getMediaFile())) {
            SlideShowThread show = SLIDES.get(te);
            if (show != null && (!show.isFor(te.getMediaFile()) || show.isFinished())) {
                show.shutdown();
                SLIDES.remove(te);
                show = null;
            }
            if (show == null) {
                show = new SlideShowThread(te, te.getMediaFile());
                show.start();
                SLIDES.put(te, show);
            }
            return;
        }
        if (!te.isMediaPlaying() || te.getMediaFile().isEmpty()) {
            stop(te);
            return;
        }
        MediaPlayerThread player = PLAYERS.get(te);
        if (player != null && (!player.isFor(te.getMediaFile()) || player.isFinished())) {
            stop(te);
            player = null;
        }
        if (player == null) {
            if (PLAYERS.size() >= MAX_PLAYERS) {
                return;
            }
            player = new MediaPlayerThread(te, te.getMediaFile(), te.getMediaVolume());
            player.start();
            PLAYERS.put(te, player);
        } else {
            player.setVolume(te.getMediaVolume());
        }
    }

    /** drop the decoder and the texture bookkeeping when the screen unloads */
    public static void forget(TileRbmkMediaScreen te) {
        stop(te);
        TEXTURES.remove(te);
    }

    public static void tick(TileRbmkMediaScreen te) {
        sync(te);
    }

    public static void stop(TileRbmkMediaScreen te) {
        MediaPlayerThread player = PLAYERS.remove(te);
        if (player != null) {
            player.shutdown();
        }
        SlideShowThread show = SLIDES.remove(te);
        if (show != null) {
            show.shutdown();
        }
    }

    public static BufferedImage pollFrame(TileRbmkMediaScreen te) {
        // v18.2 幻灯片优先（同一块屏不可能同时是两种模式，互斥判断即可）
        SlideShowThread show = SLIDES.get(te);
        if (show != null) {
            return show.pollFrame();
        }
        MediaPlayerThread player = PLAYERS.get(te);
        return player == null ? null : player.pollFrame();
    }

    public static boolean hasError(TileRbmkMediaScreen te) {
        SlideShowThread show = SLIDES.get(te);
        if (show != null) {
            return show.hasError();
        }
        MediaPlayerThread player = PLAYERS.get(te);
        return player != null && player.hasError();
    }

    public static int getTexture(TileRbmkMediaScreen te) {
        Integer tex = TEXTURES.get(te);
        return tex == null ? -1 : tex.intValue();
    }

    public static void setTexture(TileRbmkMediaScreen te, int tex) {
        TEXTURES.put(te, Integer.valueOf(tex));
    }
}
