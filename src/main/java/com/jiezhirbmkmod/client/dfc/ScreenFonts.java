package com.jiezhirbmkmod.client.dfc;

import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ScreenFonts {
    private static final String BUNDLED_FONT = "/assets/jiezhirbmkmod/font/rbmk_font.ttf";
    private static final String[] MONO_CANDIDATES = new String[]{"Consolas", "DejaVu Sans Mono", "Liberation Mono", "Courier New", "Monospaced"};
    private static final String[] CJK_CANDIDATES = new String[]{"Microsoft YaHei", "\u5fae\u8f6f\u96c5\u9ed1", "SimHei", "\u9ed1\u4f53", "SimSun", "Dialog"};
    private static String monoFamily = "Monospaced";
    private static String cjkFamily = "Dialog";
    private static Font bundledBase;
    private static boolean bundledOk;
    private static boolean probed = false;
    private static final Map<Integer, Font> MONO_CACHE = new HashMap<Integer, Font>();
    private static final Map<Integer, Font> MONO_BOLD_CACHE = new HashMap<Integer, Font>();
    private static final Map<Integer, Font> CJK_CACHE = new HashMap<Integer, Font>();
    private static final Map<Integer, Font> CJK_BOLD_CACHE = new HashMap<Integer, Font>();

    private ScreenFonts() {
    }

    private static boolean loadBundled() {
        InputStream in = null;
        try {
            in = ScreenFonts.class.getResourceAsStream(BUNDLED_FONT);
            if (in == null) {
                return false;
            }
            Font base = Font.createFont(Font.TRUETYPE_FONT, in);
            if (!base.canDisplay('\u663e') || !base.canDisplay('\u7ebf')) {
                return false;
            }
            try {
                GraphicsEnvironment.getLocalGraphicsEnvironment().registerFont(base);
            }
            catch (Throwable ignored) {
            }
            bundledBase = base;
            return true;
        }
        catch (Throwable t) {
            return false;
        }
        finally {
            if (in != null) {
                try {
                    in.close();
                }
                catch (Throwable ignored) {
                }
            }
        }
    }

    private static void probe() {
        if (probed) {
            return;
        }
        probed = true;
        bundledOk = ScreenFonts.loadBundled();
        try {
            List<String> available = Arrays.asList(GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames());
            monoFamily = ScreenFonts.pick(available, MONO_CANDIDATES, "Monospaced");
            cjkFamily = ScreenFonts.pickCjk(available);
        }
        catch (Throwable t) {
            monoFamily = "Monospaced";
            cjkFamily = "Dialog";
        }
    }

    private static String pick(List<String> available, String[] candidates, String fallback) {
        for (String c : candidates) {
            for (String a : available) {
                if (!a.equalsIgnoreCase(c)) continue;
                return a;
            }
        }
        return fallback;
    }

    private static String pickCjk(List<String> available) {
        for (String c : CJK_CANDIDATES) {
            for (String a : available) {
                if (!a.equalsIgnoreCase(c) || !new Font(a, 0, 12).canDisplay('\u663e')) continue;
                return a;
            }
        }
        return "Dialog";
    }

    public static Font mono(int size) {
        ScreenFonts.probe();
        if (!bundledOk) {
            Font f = MONO_CACHE.get(size);
            if (f == null) {
                f = new Font(monoFamily, 0, size);
                MONO_CACHE.put(size, f);
            }
            return f;
        }
        Font sys = ScreenFonts.systemMono(size, 0);
        if (sys != null && sys.canDisplay('\u663e')) {
            return sys;
        }
        return bundledBase.deriveFont(0, (float) size);
    }

    public static Font monoBold(int size) {
        ScreenFonts.probe();
        if (!bundledOk) {
            Font f = MONO_BOLD_CACHE.get(size);
            if (f == null) {
                f = new Font(monoFamily, 1, size);
                MONO_BOLD_CACHE.put(size, f);
            }
            return f;
        }
        Font sys = ScreenFonts.systemMono(size, 1);
        if (sys != null && sys.canDisplay('\u663e')) {
            return sys;
        }
        return bundledBase.deriveFont(1, (float) size);
    }

    public static Font cjk(int size) {
        ScreenFonts.probe();
        if (bundledOk) {
            return bundledBase.deriveFont(0, (float) size);
        }
        Font f = CJK_CACHE.get(size);
        if (f == null) {
            f = new Font(cjkFamily, 0, size);
            CJK_CACHE.put(size, f);
        }
        return f;
    }

    public static Font cjkBold(int size) {
        ScreenFonts.probe();
        if (bundledOk) {
            return bundledBase.deriveFont(1, (float) size);
        }
        Font f = CJK_BOLD_CACHE.get(size);
        if (f == null) {
            f = new Font(cjkFamily, 1, size);
            CJK_BOLD_CACHE.put(size, f);
        }
        return f;
    }

    private static Font systemMono(int size, int style) {
        try {
            Font f = new Font(monoFamily, style, size);
            return f.canDisplay('0') ? f : null;
        }
        catch (Throwable t) {
            return null;
        }
    }
}
