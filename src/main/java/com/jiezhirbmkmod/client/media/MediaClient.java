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
    private static final Map<TileRbmkMediaScreen, Integer> TEXTURES =
            new IdentityHashMap<TileRbmkMediaScreen, Integer>();
    /** hard cap on concurrent decoders, protects RAM and audio devices */
    private static final int MAX_PLAYERS = 4;

    private MediaClient() {
    }

    /** create / keep / drop the decoder thread to match the synced state */
    public static void sync(TileRbmkMediaScreen te) {
        if (te == null || te.getWorld() == null) {
            return;
        }
        MediaPlayerThread player = PLAYERS.get(te);
        if (!te.isMediaPlaying() || te.getMediaFile().isEmpty()) {
            stop(te);
            return;
        }
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
    }

    public static BufferedImage pollFrame(TileRbmkMediaScreen te) {
        MediaPlayerThread player = PLAYERS.get(te);
        return player == null ? null : player.pollFrame();
    }

    public static boolean hasError(TileRbmkMediaScreen te) {
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
