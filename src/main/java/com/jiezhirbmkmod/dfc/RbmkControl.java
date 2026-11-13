package com.jiezhirbmkmod.dfc;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Reflection bridge for WRITING control rod state of the HBM RBMK.
 * Never links against HBM classes directly, so the mod loads fine without HBM installed.
 *
 * HBM semantics (verified against upstream TileEntityRBMKControl / TileEntityRBMKConsole):
 *   - TileEntityRBMKControlManual#level       0..1, 1 = fully extracted (more reactivity)
 *   - TileEntityRBMKControlManual#setTarget(double) sets targetLevel, rod crawls there over time
 *   - AZ-5 (scram) = setTarget(0) on every manual rod = fully inserted = shutdown
 */
public final class RbmkControl {

    private static final String ROD_CLASS = "com.hbm.tileentity.machine.rbmk.TileEntityRBMKControlManual";
    private static final String CONSOLE_CLASS = "com.hbm.tileentity.machine.rbmk.TileEntityRBMKConsole";

    private static final Map<Class<?>, Boolean> ROD_CLASSES = new ConcurrentHashMap<Class<?>, Boolean>();
    private static final Map<Class<?>, Method> SET_TARGET = new ConcurrentHashMap<Class<?>, Method>();

    private RbmkControl() {
    }

    public static boolean isManualRod(TileEntity te) {
        if (te == null) {
            return false;
        }
        Class<?> c = te.getClass();
        Boolean cached = ROD_CLASSES.get(c);
        if (cached != null) {
            return cached.booleanValue();
        }
        boolean rod = false;
        for (Class<?> k = c; k != null && k != Object.class; k = k.getSuperclass()) {
            if (k.getName().equals(ROD_CLASS)) {
                rod = true;
                break;
            }
        }
        ROD_CLASSES.put(c, rod);
        return rod;
    }

    public static double rodLevel(Object rod) {
        return clamp01(numValue(fieldValue(rod, "level")));
    }

    public static double rodTarget(Object rod) {
        return clamp01(numValue(fieldValue(rod, "targetLevel")));
    }

    public static String rodColor(Object rod) {
        Object color = fieldValue(rod, "color");
        return color == null ? null : String.valueOf(color);
    }

    public static boolean setRodLevel(TileEntity rod, double level01) {
        Method m = setTargetMethod(rod.getClass());
        if (m == null) {
            return false;
        }
        try {
            m.invoke(rod, Double.valueOf(level01));
            rod.markDirty();
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private static Method setTargetMethod(Class<?> cls) {
        Method cached = SET_TARGET.get(cls);
        if (cached != null) {
            return cached;
        }
        Method found = null;
        for (Class<?> k = cls; k != null && k != Object.class; k = k.getSuperclass()) {
            try {
                Method m = k.getMethod("setTarget", Double.TYPE);
                m.setAccessible(true);
                found = m;
                break;
            } catch (Throwable ignored) {
                // keep walking the hierarchy
            }
        }
        if (found != null) {
            SET_TARGET.put(cls, found);
        }
        return found;
    }

    /**
     * The console caches the reactor center in targetX/targetY/targetZ.
     * If those fields are unreadable or still unset, derive the center from
     * the surrounding RBMK column footprint instead of using the console
     * position (which can be outside the 15x15 core).
     */
    public static BlockPos reactorCenter(World world, TileEntity console) {
        if (console == null) {
            return null;
        }
        Object tx = fieldValue(console, "targetX");
        Object ty = fieldValue(console, "targetY");
        Object tz = fieldValue(console, "targetZ");
        if (tx instanceof Number && ty instanceof Number && tz instanceof Number) {
            BlockPos center = new BlockPos(((Number) tx).intValue(), ((Number) ty).intValue(), ((Number) tz).intValue());
            if (world != null && (RbmkBridge.isConsole(world.getTileEntity(center)) || isManualRod(world.getTileEntity(center))
                    || RbmkBridge.isRbmkPart(world.getTileEntity(center)))) {
                return center;
            }
        }
        // The console target is normally populated by HBM, but on some
        // versions/addons it can still be unset (0,0,0). Fall back to the
        // bounding box of the RBMK columns around the console so rod
        // commands do not silently miss half of the reactor.
        BlockPos fallback = scanReactorCenter(world, console.getPos());
        return fallback != null ? fallback : console.getPos();
    }

    private static BlockPos scanReactorCenter(World world, BlockPos origin) {
        if (world == null || origin == null) {
            return null;
        }
        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxZ = Integer.MIN_VALUE;
        boolean found = false;
        for (int dx = -16; dx <= 16; ++dx) {
            for (int dz = -16; dz <= 16; ++dz) {
                for (int dy = -4; dy <= 4; ++dy) {
                    BlockPos p = origin.add(dx, dy, dz);
                    if (!RbmkBridge.isRbmkPart(world.getTileEntity(p))) {
                        continue;
                    }
                    found = true;
                    minX = Math.min(minX, p.getX());
                    maxX = Math.max(maxX, p.getX());
                    minZ = Math.min(minZ, p.getZ());
                    maxZ = Math.max(maxZ, p.getZ());
                }
            }
        }
        if (!found) {
            return null;
        }
        return new BlockPos((minX + maxX) / 2, origin.getY(), (minZ + maxZ) / 2);
    }

    /** Collect every manual control rod around the given center (15x15 reactor = radius 7). */
    public static List<TileEntity> collectRods(World world, BlockPos center, int radius) {
        List<TileEntity> rods = new ArrayList<TileEntity>();
        if (world == null || center == null) {
            return rods;
        }
        for (int dx = -radius; dx <= radius; ++dx) {
            for (int dz = -radius; dz <= radius; ++dz) {
                for (int dy = -3; dy <= 3; ++dy) {
                    TileEntity te = world.getTileEntity(center.add(dx, dy, dz));
                    if (RbmkControl.isManualRod(te)) {
                        rods.add(te);
                    }
                }
            }
        }
        return rods;
    }

    public static String consoleClass() {
        return CONSOLE_CLASS;
    }

    private static Object fieldValue(Object target, String name) {
        if (target == null) {
            return null;
        }
        try {
            for (Class<?> k = target.getClass(); k != null && k != Object.class; k = k.getSuperclass()) {
                try {
                    Field f = k.getDeclaredField(name);
                    f.setAccessible(true);
                    return f.get(target);
                } catch (NoSuchFieldException ignored) {
                    // try the superclass
                }
            }
        } catch (Throwable ignored) {
            // unreadable field, treat as absent
        }
        return null;
    }

    private static double numValue(Object v) {
        return v instanceof Number ? ((Number) v).doubleValue() : 0.0D;
    }

    private static double clamp01(double v) {
        return v < 0.0D ? 0.0D : (v > 1.0D ? 1.0D : v);
    }
}
