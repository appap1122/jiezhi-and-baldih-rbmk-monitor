package com.jiezhirbmkmod.client.dfc;

import com.jiezhirbmkmod.dfc.DfcWatch;
import java.util.List;
import net.minecraft.client.resources.I18n;

public final class WatchNames {
    private WatchNames() {
    }

    public static void localize(List<DfcWatch> watches) {
        if (watches == null) {
            return;
        }
        for (DfcWatch w : watches) {
            w.name = WatchNames.resolve(w.name);
        }
    }

    public static String resolve(String key) {
        String translated;
        if (key == null || key.isEmpty()) {
            return "--";
        }
        if (key.indexOf(46) < 0) {
            return key;
        }
        try {
            translated = I18n.format((String)key, (Object[])new Object[0]);
        }
        catch (Throwable t) {
            translated = key;
        }
        if (translated != null && !translated.equals(key)) {
            return translated;
        }
        int dot = key.lastIndexOf(46);
        String tail = key.substring(dot + 1).replace('_', ' ').trim();
        return tail.isEmpty() ? key : tail;
    }
}

