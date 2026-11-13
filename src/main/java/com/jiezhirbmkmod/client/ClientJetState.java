package com.jiezhirbmkmod.client;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.entity.player.EntityPlayer;

public final class ClientJetState {
    private static final Map<UUID, Entry> MAP = new HashMap<UUID, Entry>();

    private ClientJetState() {
    }

    public static void set(UUID id, boolean firing, float heatRatio) {
        Entry e = MAP.get(id);
        if (e == null) {
            e = new Entry();
            MAP.put(id, e);
        }
        e.firing = firing;
        e.heatRatio = heatRatio;
    }

    public static Boolean isFiring(EntityPlayer player) {
        if (player == null) {
            return null;
        }
        Entry e = MAP.get(player.getUniqueID());
        return e == null ? null : Boolean.valueOf(e.firing);
    }

    public static float heatRatio(EntityPlayer player) {
        if (player == null) {
            return 0.0f;
        }
        Entry e = MAP.get(player.getUniqueID());
        return e == null ? 0.0f : e.heatRatio;
    }

    public static void clear() {
        MAP.clear();
    }

    private static final class Entry {
        boolean firing;
        float heatRatio;

        private Entry() {
        }
    }
}

