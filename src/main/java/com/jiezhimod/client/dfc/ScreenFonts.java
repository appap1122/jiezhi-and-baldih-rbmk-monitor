package com.jiezhimod.client.dfc;

import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ScreenFonts {
    private static final String[] MONO_CANDIDATES = new String[]{"Consolas", "DejaVu Sans Mono", "Liberation Mono", "Courier New", "Monospaced"};
    private static final String[] CJK_CANDIDATES = new String[]{"Microsoft YaHei", "微软雅黑", "SimHei", "黑体", "SimSun", "Dialog"};
    private static String monoFamily = "Monospaced";
    private static String cjkFamily = "Dialog";
    private static boolean probed = false;
    private static final Map<Integer, Font> MONO_CACHE = new HashMap<Integer, Font>();
    private static final Map<Integer, Font> MONO_BOLD_CACHE = new HashMap<Integer, Font>();
    private static final Map<Integer, Font> CJK_CACHE = new HashMap<Integer, Font>();
    private static final Map<Integer, Font> CJK_BOLD_CACHE = new HashMap<Integer, Font>();

    private ScreenFonts() {
    }

    private static void probe() {
        if (probed) {
            return;
        }
        probed = true;
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
                if (!a.equalsIgnoreCase(c) || !new Font(a, 0, 12).canDisplay('节')) continue;
                return a;
            }
        }
        return "Dialog";
    }

    public static Font mono(int size) {
        ScreenFonts.probe();
        Font f = MONO_CACHE.get(size);
        if (f == null) {
            f = new Font(monoFamily, 0, size);
            MONO_CACHE.put(size, f);
        }
        return f;
    }

    public static Font monoBold(int size) {
        ScreenFonts.probe();
        Font f = MONO_BOLD_CACHE.get(size);
        if (f == null) {
            f = new Font(monoFamily, 1, size);
            MONO_BOLD_CACHE.put(size, f);
        }
        return f;
    }

    public static Font cjk(int size) {
        ScreenFonts.probe();
        Font f = CJK_CACHE.get(size);
        if (f == null) {
            f = new Font(cjkFamily, 0, size);
            CJK_CACHE.put(size, f);
        }
        return f;
    }

    public static Font cjkBold(int size) {
        ScreenFonts.probe();
        Font f = CJK_BOLD_CACHE.get(size);
        if (f == null) {
            f = new Font(cjkFamily, 1, size);
            CJK_BOLD_CACHE.put(size, f);
        }
        return f;
    }
}

