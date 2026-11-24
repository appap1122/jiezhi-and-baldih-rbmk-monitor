package com.jiezhirbmkmod.client.media;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * v17.0: the OST bundled in the mod jar is useless to the media screen on its
 * own - {@link MediaPlayerThread} only plays real files from
 * {@code .minecraft/rbmk_media/}. This helper copies the bundled tracks there
 * once (existing files are never overwritten), so
 * {@code media play alarm_siren_loop.mp3} works out of the box.
 *
 * <p>v17.6: the 2-minute main theme was dropped from the jar. It was 2.88 MB of
 * a 4 MB mod - 93% of the whole {@code ost/} folder - for one piece of BGM,
 * while the seven remaining tracks are short cues totalling 232 KB. Anything
 * a player drops into {@code rbmk_media/} still plays; only the bundled copy is
 * gone. Files already extracted by an older version are left alone, so anyone
 * who has the theme keeps it.</p>
 */
@SideOnly(Side.CLIENT)
public final class MediaLibrary {

    /** tracks shipped in assets/jiezhirbmkmod/ost/ */
    public static final String[] OST = new String[]{
        "alarm_siren_loop.mp3",
        "alarm_warn_beep.mp3",
        "alarm_crit_klaxon.mp3",
        "terminal_key.mp3",
        "terminal_connect.mp3",
        "terminal_error.mp3",
        "reactor_power_down.mp3"
    };

    private static final String RES_ROOT = "/assets/jiezhirbmkmod/ost/";

    private MediaLibrary() {
    }

    public static File dir() {
        Minecraft mc = Minecraft.getMinecraft();
        File gameDir = mc == null || mc.gameDir == null ? new File(".") : mc.gameDir;
        return new File(gameDir, "rbmk_media");
    }

    /** copy bundled OST tracks into rbmk_media; safe to call every start */
    public static void installBundledOst() {
        try {
            File dir = dir();
            if (!dir.isDirectory() && !dir.mkdirs()) {
                return;
            }
            for (int i = 0; i < OST.length; ++i) {
                File out = new File(dir, OST[i]);
                if (out.isFile() && out.length() > 1024L) {
                    continue;
                }
                InputStream in = MediaLibrary.class.getResourceAsStream(RES_ROOT + OST[i]);
                if (in == null) {
                    continue;
                }
                try {
                    Files.copy(in, out.toPath(), StandardCopyOption.REPLACE_EXISTING);
                }
                finally {
                    in.close();
                }
            }
        }
        catch (Throwable ignored) {
            // a read-only game dir must never crash startup
        }
    }

    public static List<String> listFiles() {
        List<String> out = new ArrayList<String>();
        File[] files = dir().listFiles();
        if (files == null) {
            return out;
        }
        for (int i = 0; i < files.length; ++i) {
            File f = files[i];
            if (f != null && f.isFile()) {
                out.add(f.getName());
            }
        }
        Collections.sort(out);
        return out;
    }

    public static long sizeOf(String name) {
        File f = new File(dir(), name);
        return f.isFile() ? f.length() : -1L;
    }
}
