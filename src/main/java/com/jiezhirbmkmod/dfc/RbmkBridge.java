package com.jiezhirbmkmod.dfc;

import com.jiezhirbmkmod.dfc.DfcFormat;
import com.jiezhirbmkmod.dfc.DfcWatch;
import com.jiezhirbmkmod.dfc.RbmkSnapshot;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.block.Block;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

public final class RbmkBridge {
    /** Horizontal (X/Z) scan radius for a reactor without a reachable console. */
    public static final int SCAN_RADIUS_XZ = 128;
    /** Hard cap on columns read per refresh; protects the server thread. */
    public static final int MAX_SCAN_COLUMNS = 2000;
    /** The grid page only has room for 15x15 cells. */
    public static final int MAX_GRID_CELLS = 225;
    private static final Map<String, Method> METHODS = new ConcurrentHashMap<String, Method>();
    private static final Set<String> MISSING = Collections.newSetFromMap(new ConcurrentHashMap());
    private static final Map<String, Field> FIELDS = new ConcurrentHashMap<String, Field>();
    private static final Set<String> MISSING_FIELDS = Collections.newSetFromMap(new ConcurrentHashMap());
    private static final Map<Class<?>, List<Field>> TANK_FIELDS = new ConcurrentHashMap();
    private static final Map<Class<?>, Boolean> POWER_API = new ConcurrentHashMap();
    private static final Map<Class<?>, Boolean> TURBINE_PART = new ConcurrentHashMap();
    private static final Map<Class<?>, Boolean> PROXY_CLASS = new ConcurrentHashMap();
    private static final Map<Class<?>, Boolean> RBMK_PART = new ConcurrentHashMap();
    private static final Map<Class<?>, Boolean> CONSOLE_CLASS = new ConcurrentHashMap();

    private RbmkBridge() {
    }

    private static Method findMethod(Class<?> cls, String name) {
        String key = cls.getName() + "#" + name;
        Method cached = METHODS.get(key);
        if (cached != null) {
            return cached;
        }
        if (MISSING.contains(key)) {
            return null;
        }
        Method found = null;
        try {
            found = cls.getMethod(name, new Class[0]);
        }
        catch (Throwable ignored) {
            try {
                Method m = cls.getDeclaredMethod(name, new Class[0]);
                m.setAccessible(true);
                found = m;
            }
            catch (Throwable ignored2) {
                found = null;
            }
        }
        if (found == null) {
            MISSING.add(key);
        } else {
            METHODS.put(key, found);
        }
        return found;
    }

    private static Field findField(Class<?> cls, String name) {
        String key = cls.getName() + "#" + name;
        Field cached = FIELDS.get(key);
        if (cached != null) {
            return cached;
        }
        if (MISSING_FIELDS.contains(key)) {
            return null;
        }
        try {
            Field f = cls.getField(name);
            FIELDS.put(key, f);
            return f;
        }
        catch (Throwable ignored) {
            try {
                Field f = cls.getDeclaredField(name);
                f.setAccessible(true);
                FIELDS.put(key, f);
                return f;
            }
            catch (Throwable ignored2) {
                MISSING_FIELDS.add(key);
                return null;
            }
        }
    }

    private static Object getFieldValue(Object target, String name) {
        if (target == null) {
            return null;
        }
        Field f = RbmkBridge.findField(target.getClass(), name);
        if (f == null) {
            return null;
        }
        try {
            return f.get(target);
        }
        catch (Throwable t) {
            return null;
        }
    }

    private static Object callOn(Object target, String name) {
        if (target == null) {
            return null;
        }
        Method m = RbmkBridge.findMethod(target.getClass(), name);
        if (m == null) {
            return null;
        }
        try {
            return m.invoke(target, new Object[0]);
        }
        catch (Throwable t) {
            return null;
        }
    }

    private static double numValue(Object v) {
        return v instanceof Number ? ((Number)v).doubleValue() : 0.0;
    }

    private static double clamp01(double v) {
        if (v < 0.0) {
            return 0.0;
        }
        return v > 1.0 ? 1.0 : v;
    }

    public static boolean isConsole(TileEntity te) {
        if (te == null) {
            return false;
        }
        Class<?> cls = te.getClass();
        Boolean cached = CONSOLE_CLASS.get(cls);
        if (cached != null) {
            return cached.booleanValue();
        }
        boolean console = false;
        for (Class<?> c = cls; c != null && c != Object.class; c = c.getSuperclass()) {
            if (c.getName().endsWith("TileEntityRBMKConsole")) {
                console = true;
                break;
            }
        }
        CONSOLE_CLASS.put(cls, console);
        return console;
    }

    public static boolean isRbmkPart(TileEntity te) {
        if (te == null) {
            return false;
        }
        Class<?> c = te.getClass();
        Boolean cached = RBMK_PART.get(c);
        if (cached != null) {
            return cached.booleanValue();
        }
        // HBM 1.12.2 exposes getConsoleType()/getNBTForConsole() on
        // TileEntityRBMKBase; older/other forks expose getConsoleData().
        // Accept either API instead of only the legacy one, which made every
        // 1.12.2 RBMK column invisible to the remote and the terminals.
        boolean modernApi = RbmkBridge.findMethod(c, "getConsoleType") != null
                && RbmkBridge.findMethod(c, "getNBTForConsole") != null;
        boolean legacyApi = RbmkBridge.findMethod(c, "getConsoleData") != null;
        boolean rbmkClass = c.getName().startsWith("com.hbm.tileentity.machine.rbmk.")
                || RbmkBridge.extendsRbmkBase(c);
        boolean part = rbmkClass && (modernApi || legacyApi);
        RBMK_PART.put(c, part);
        return part;
    }

    private static boolean extendsRbmkBase(Class<?> c) {
        for (Class<?> k = c; k != null && k != Object.class; k = k.getSuperclass()) {
            if ("com.hbm.tileentity.machine.rbmk.TileEntityRBMKBase".equals(k.getName())) {
                return true;
            }
        }
        return false;
    }

    public static boolean isDataSource(TileEntity te) {
        return RbmkBridge.isConsole(te) || RbmkBridge.isRbmkPart(te);
    }

    public static BlockPos findConsoleNear(World world, BlockPos origin, int radius) {
        if (world == null || origin == null) {
            return null;
        }
        BlockPos.MutableBlockPos cur = new BlockPos.MutableBlockPos();
        // The console is usually on the reactor base layer; allow a little
        // vertical slack for elevated consoles without scanning forever.
        for (int dy = -6; dy <= 6; ++dy) {
            for (int dz = -radius; dz <= radius; ++dz) {
                for (int dx = -radius; dx <= radius; ++dx) {
                    cur.setPos(origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz);
                    if (!RbmkBridge.isConsole(world.getTileEntity((BlockPos)cur))) continue;
                    return cur.toImmutable();
                }
            }
        }
        return null;
    }

    public static boolean read(TileEntity raw, RbmkSnapshot out) {
        if (raw == null || out == null) {
            return false;
        }
        TileEntity te = RbmkBridge.resolveProxy(raw);
        // A successful read must clear the previous failure state, otherwise
        // a screen that was once offline would stay "offline" forever.
        out.offline = false;
        out.cold = false;
        if (RbmkBridge.isConsole(te)) {
            out.sourceKind = 1;
            out.linked = true;
            RbmkBridge.readConsole(te, out);
            return true;
        }
        if (RbmkBridge.isRbmkPart(te)) {
            BlockPos cp;
            out.sourceKind = 2;
            out.linked = true;
            World world = te.getWorld();
            BlockPos blockPos = cp = world == null ? null : RbmkBridge.findConsoleNear(world, te.getPos(), com.jiezhirbmkmod.dfc.RbmkConfig.consoleSearchRadius);
            if (cp != null) {
                RbmkBridge.readConsole(world.getTileEntity(cp), out);
            } else {
                RbmkBridge.readPartGrid(world, te.getPos(), out);
            }
            return true;
        }
        return false;
    }

    private static void readConsole(TileEntity console, RbmkSnapshot out) {
        out.resetStats();
        Object colsObj = RbmkBridge.getFieldValue(console, "columns");
        if (!(colsObj instanceof Object[])) {
            out.offline = true;
            return;
        }
        Object[] cols = (Object[])colsObj;
        int nonNull = 0;
        for (int i = 0; i < cols.length; ++i) {
            Object col;
            if ((col = cols[i]) == null) continue;
            if (nonNull >= RbmkBridge.MAX_SCAN_COLUMNS) {
                out.truncated = true;
                break;
            }
            try {
                RbmkBridge.readColumn(col, i, i < RbmkBridge.MAX_GRID_CELLS ? i : -1, out);
                ++nonNull;
            }
            catch (Throwable t) {
                // a broken column must never take down the terminal
            }
        }
        out.columnCount = nonNull;
        if (nonNull == 0) {
            out.offline = true;
            return;
        }
        if (out.flux == 0) {
            // Legacy consoles expose only the flux history buffer.
            Object fluxObj = RbmkBridge.getFieldValue(console, "fluxBuffer");
            if (fluxObj instanceof int[] && ((int[])fluxObj).length > 0) {
                int[] buf = (int[])fluxObj;
                out.flux = buf[buf.length - 1];
            }
        }
        if (out.flux == 0) {
            // HBM 1.12.2 keeps the flux graph buffer in graph.dataBuffer.
            Object graph = RbmkBridge.getFieldValue(console, "graph");
            Object bufObj = RbmkBridge.getFieldValue(graph, "dataBuffer");
            if (bufObj instanceof int[] && ((int[])bufObj).length > 0) {
                int[] buf = (int[])bufObj;
                out.flux = buf[buf.length - 1];
            }
        }
        out.finishStats();
        out.cold = out.avgHeat < com.jiezhirbmkmod.dfc.RbmkConfig.coldThreshold && out.flux == 0;
    }

    private static int findColumnLayer(World world, BlockPos origin) {
        int dy;
        int x = origin.getX();
        int z = origin.getZ();
        int baseY = origin.getY();
        for (dy = 0; dy <= 16; ++dy) {
            if (!RbmkBridge.isRbmkPart(world.getTileEntity(new BlockPos(x, baseY + dy, z)))) continue;
            return baseY + dy;
        }
        for (dy = -1; dy >= -6; --dy) {
            if (!RbmkBridge.isRbmkPart(world.getTileEntity(new BlockPos(x, baseY + dy, z)))) continue;
            return baseY + dy;
        }
        return baseY;
    }

    private static void readColumn(Object col, int index, int gridIndex, RbmkSnapshot out) {
        NBTTagCompound data = RbmkBridge.asCompound(RbmkBridge.getFieldValue(col, "data"));
        String type = RbmkBridge.columnTypeName(col, data);
        double heat = RbmkBridge.columnNumber(col, data, "heat");
        double limit = RbmkBridge.columnNumber(col, data, "maxHeat");
        if (limit <= 0.0) {
            limit = 1500.0;
        }
        out.flux = RbmkBridge.addFlux(out.flux, RbmkBridge.columnNumber(col, data, "flux"));
        RbmkBridge.readColumnValues(type, col, data, heat, limit, index, gridIndex, out);
    }

    /** Build a column snapshot from a single RBMK part (no console nearby). */
    private static void readPartColumn(TileEntity te, int index, int gridIndex, RbmkSnapshot out) {
        Object typeObj = RbmkBridge.callOn(te, "getConsoleType");
        String type = typeObj == null ? "" : String.valueOf(typeObj);
        NBTTagCompound data = RbmkBridge.asCompound(RbmkBridge.callOn(te, "getNBTForConsole"));
        double heat = RbmkBridge.numValue(RbmkBridge.getFieldValue(te, "heat"));
        double limit = RbmkBridge.numValue(RbmkBridge.callOn(te, "maxHeat"));
        if (limit <= 0.0) {
            limit = RbmkBridge.columnNumber(te, data, "maxHeat");
        }
        if (limit <= 0.0) {
            limit = 1500.0;
        }
        out.flux = RbmkBridge.addFlux(out.flux, RbmkBridge.columnNumber(te, data, "flux"));
        RbmkBridge.readColumnValues(type, te, data, heat, limit, index, gridIndex, out);
    }

    private static void readColumnValues(String type, Object source, NBTTagCompound data,
            double heat, double limit, int index, int gridIndex, RbmkSnapshot out) {
        out.avgHeat += heat;
        // HBM 1.12.2 exposes "realSimSteam" per column (and "steam" on
        // boilers); some forks only have "reasimSteam".
        double steam;
        if (RbmkBridge.hasColumnNumber(source, data, "reasimSteam")) {
            steam = RbmkBridge.columnNumber(source, data, "reasimSteam");
        } else if (RbmkBridge.hasColumnNumber(source, data, "realSimSteam")) {
            steam = RbmkBridge.columnNumber(source, data, "realSimSteam");
        } else {
            steam = RbmkBridge.columnNumber(source, data, "steam");
        }
        out.steamTotal += steam;
        double ratio = heat / limit;
        if (ratio > out.meltRisk) {
            out.meltRisk = ratio;
            out.maxHeat = heat;
            out.maxHeatLimit = limit;
            out.hottestIndex = index;
        }
        if (heat >= limit) {
            out.meltdown = true;
        }
        boolean fuel = "FUEL".equals(type) || "FUEL_SIM".equals(type) || "BREEDER".equals(type);
        boolean control = "CONTROL".equals(type) || "CONTROL_AUTO".equals(type);
        boolean boiler = "BOILER".equals(type);
        double v1 = 0.0;
        double v2 = 0.0;
        if (fuel) {
            ++out.fuelCount;
            double enrichment = RbmkBridge.columnNumber(source, data, "enrichment");
            double xenon = RbmkBridge.columnNumber(source, data, "xenon");
            double coreTemp = RbmkBridge.columnNumber(source, data, "c_coreHeat");
            double melting = RbmkBridge.columnNumber(source, data, "c_maxHeat");
            out.avgEnrichment += enrichment;
            out.avgXenon += xenon;
            out.avgCoreTemp += coreTemp;
            if (melting > 0.0 && coreTemp >= melting) {
                out.meltdown = true;
            }
            if (RbmkBridge.columnNumber(source, data, "meltdown") > 0.0) {
                out.meltdown = true;
            }
            v1 = enrichment;
            v2 = xenon;
        } else if (control) {
            ++out.controlCount;
            double level = RbmkBridge.clamp01(RbmkBridge.columnNumber(source, data, "level"));
            out.avgRodLevel += level;
            v1 = level;
        } else if (boiler) {
            ++out.boilerCount;
            double water = RbmkBridge.columnNumber(source, data, "water");
            double maxWater = RbmkBridge.columnNumber(source, data, "maxWater");
            out.waterTotal += water;
            if (maxWater > 0.0) {
                v1 = RbmkBridge.clamp01(water / maxWater);
            }
        }
        if (out.includeGrid && gridIndex >= 0 && out.grid.size() < RbmkBridge.MAX_GRID_CELLS) {
            RbmkSnapshot.RbmkCell c = new RbmkSnapshot.RbmkCell();
            c.index = gridIndex;
            c.type = RbmkSnapshot.RbmkCell.typeOrdinal(type);
            c.heat = heat;
            c.heatLimit = limit;
            c.v1 = v1;
            c.v2 = v2;
            out.grid.add(c);
        }
    }

    private static void readPartGrid(World world, BlockPos origin, RbmkSnapshot out) {
        if (world == null || origin == null) {
            out.offline = true;
            return;
        }
        out.resetStats();
        int y = RbmkBridge.findColumnLayer(world, origin);
        int ox = origin.getX();
        int oz = origin.getZ();
        int span = RbmkBridge.SCAN_RADIUS_XZ * 2 + 1;
        int nonNull = 0;
        boolean truncated = false;
        BlockPos.MutableBlockPos cur = new BlockPos.MutableBlockPos();
        outer:
        for (int dz = -RbmkBridge.SCAN_RADIUS_XZ; dz <= RbmkBridge.SCAN_RADIUS_XZ; ++dz) {
            for (int dx = -RbmkBridge.SCAN_RADIUS_XZ; dx <= RbmkBridge.SCAN_RADIUS_XZ; ++dx) {
                cur.setPos(ox + dx, y, oz + dz);
                TileEntity te = world.getTileEntity((BlockPos) cur);
                if (!RbmkBridge.isRbmkPart(te)) continue;
                if (nonNull >= RbmkBridge.MAX_SCAN_COLUMNS) {
                    truncated = true;
                    break outer;
                }
                int sweepIndex = (dz + RbmkBridge.SCAN_RADIUS_XZ) * span + (dx + RbmkBridge.SCAN_RADIUS_XZ);
                // only the central 15x15 area fits on the grid page
                int gridIndex = dx >= -7 && dx <= 7 && dz >= -7 && dz <= 7
                        ? (dz + 7) * 15 + (dx + 7) : -1;
                try {
                    Object col = RbmkBridge.callOn(te, "getConsoleData");
                    if (col != null) {
                        RbmkBridge.readColumn(col, sweepIndex, gridIndex, out);
                    } else {
                        RbmkBridge.readPartColumn(te, sweepIndex, gridIndex, out);
                    }
                    ++nonNull;
                }
                catch (Throwable t) {
                    // skip this column, never let one bad TE crash the terminal
                }
            }
        }
        out.truncated = truncated;
        out.columnCount = nonNull;
        if (nonNull == 0) {
            out.offline = true;
            return;
        }
        out.finishStats();
        out.cold = out.avgHeat < com.jiezhirbmkmod.dfc.RbmkConfig.coldThreshold && out.flux == 0;
    }

    private static NBTTagCompound asCompound(Object o) {
        return o instanceof NBTTagCompound ? (NBTTagCompound)o : null;
    }

    private static String columnTypeName(Object col, NBTTagCompound data) {
        Object type = RbmkBridge.getFieldValue(col, "type");
        if (type == null && data != null && data.hasKey("consoleType")) {
            type = data.getString("consoleType");
        }
        return type == null ? "" : String.valueOf(type);
    }

    private static boolean hasColumnNumber(Object source, NBTTagCompound data, String key) {
        if (data != null && data.hasKey(key, 99)) {
            return true;
        }
        Object v = RbmkBridge.getFieldValue(source, key);
        if (v instanceof Number) {
            return true;
        }
        return RbmkBridge.callOn(source, key) instanceof Number;
    }

    private static double columnNumber(Object source, NBTTagCompound data, String key) {
        if (data != null && data.hasKey(key, 99)) {
            return data.getDouble(key);
        }
        Object v = RbmkBridge.getFieldValue(source, key);
        if (v instanceof Number) {
            return ((Number)v).doubleValue();
        }
        return RbmkBridge.numValue(RbmkBridge.callOn(source, key));
    }

    private static int addFlux(int current, double flux) {
        if (flux <= 0.0) {
            return current;
        }
        double sum = (double)current + flux;
        return sum >= 2.147483647E9 ? Integer.MAX_VALUE : (int)Math.round(sum);
    }

    public static boolean isDevice(TileEntity te) {
        return te != null && (!RbmkBridge.findTanks(te).isEmpty() || RbmkBridge.hasPowerApi(te) || RbmkBridge.findTurbineCore(te) != null);
    }

    private static boolean isProxy(TileEntity te) {
        if (te == null) {
            return false;
        }
        Class<?> c = te.getClass();
        Boolean cached = PROXY_CLASS.get(c);
        if (cached == null) {
            // HBM 1.12.2's TileEntityProxyBase exposes getTE() but has no
            // cachedPosition field; requiring that field made every proxy
            // (including several machine cores) unresolvable.
            cached = RbmkBridge.findMethod(c, "getTE") != null;
            PROXY_CLASS.put(c, cached);
        }
        return cached;
    }

    public static TileEntity resolveProxy(TileEntity te) {
        Object next;
        TileEntity cur = te;
        for (int hop = 0; hop < 3 && RbmkBridge.isProxy(cur) && (next = RbmkBridge.callOn(cur, "getTE")) instanceof TileEntity && next != cur; ++hop) {
            cur = (TileEntity)next;
        }
        return cur;
    }

    /**
     * Resolve the data source from a clicked block position.
     *
     * HBM's RBMK console and every RBMK column are BlockDummyable
     * multiblocks: only the core block owns a TileEntity, the other blocks
     * of the structure have none. Looking up the TileEntity directly at the
     * clicked position therefore failed for most of the reactor, which made
     * the remote/portable terminal report "not an RBMK block". If the direct
     * lookup is not a data source, ask the block for its core position and
     * read the TileEntity there.
     */
    public static TileEntity resolveDataSource(World world, BlockPos pos) {
        if (world == null || pos == null) {
            return null;
        }
        TileEntity direct = RbmkBridge.resolveProxy(world.getTileEntity(pos));
        if (RbmkBridge.isDataSource(direct)) {
            return direct;
        }
        BlockPos core = RbmkBridge.findBlockCore(world, pos);
        if (core != null && !core.equals(pos)) {
            TileEntity te = RbmkBridge.resolveProxy(world.getTileEntity(core));
            if (RbmkBridge.isDataSource(te)) {
                return te;
            }
        }
        return direct;
    }

    /**
     * Calls the HBM BlockDummyable#findCore method by reflection, without
     * linking against HBM. Both known signatures are supported.
     */
    private static BlockPos findBlockCore(World world, BlockPos pos) {
        Block block = world.getBlockState(pos).getBlock();
        try {
            Method m = block.getClass().getMethod("findCore", IBlockAccess.class, BlockPos.class);
            Object r = m.invoke(block, world, pos);
            if (r instanceof BlockPos) {
                return (BlockPos)r;
            }
        }
        catch (Throwable ignored) {
            // try the legacy int signature below
        }
        try {
            Method m = block.getClass().getMethod("findCore", IBlockAccess.class, Integer.TYPE, Integer.TYPE, Integer.TYPE);
            Object r = m.invoke(block, world, pos.getX(), pos.getY(), pos.getZ());
            if (r instanceof int[] && ((int[])r).length >= 3) {
                int[] a = (int[])r;
                return new BlockPos(a[0], a[1], a[2]);
            }
        }
        catch (Throwable ignored) {
            // not a dummyable block
        }
        return null;
    }

    public static TileEntity findDeviceAt(World world, BlockPos pos, int radius) {
        if (world == null || pos == null) {
            return null;
        }
        TileEntity direct = RbmkBridge.resolveProxy(world.getTileEntity(pos));
        if (RbmkBridge.isDevice(direct)) {
            return direct;
        }
        TileEntity best = null;
        double bestDist = Double.MAX_VALUE;
        for (int dx = -radius; dx <= radius; ++dx) {
            for (int dy = -radius; dy <= radius; ++dy) {
                for (int dz = -radius; dz <= radius; ++dz) {
                    double dist;
                    TileEntity t;
                    if (dx == 0 && dy == 0 && dz == 0 || !RbmkBridge.isDevice(t = RbmkBridge.resolveProxy(world.getTileEntity(pos.add(dx, dy, dz)))) || !((dist = (double)(dx * dx + dy * dy + dz * dz)) < bestDist)) continue;
                    bestDist = dist;
                    best = t;
                }
            }
        }
        return best;
    }

    public static List<DfcWatch> probeDevice(TileEntity raw) {
        Object core;
        TileEntity te = RbmkBridge.resolveProxy(raw);
        ArrayList<DfcWatch> out = new ArrayList<DfcWatch>();
        if (te == null || te.getWorld() == null) {
            return out;
        }
        int dim = te.getWorld().provider.getDimension();
        int px = te.getPos().getX();
        int py = te.getPos().getY();
        int pz = te.getPos().getZ();
        String dev = RbmkBridge.deviceName(te);
        List<Object> tanks = RbmkBridge.findTanks(te);
        for (int i = 0; i < tanks.size(); ++i) {
            DfcWatch w = new DfcWatch(px, py, pz, dim, 1, i);
            w.device = dev;
            w.alive = true;
            RbmkBridge.readFluid(tanks.get(i), w);
            out.add(w);
        }
        if (RbmkBridge.hasPowerApi(te)) {
            DfcWatch w = new DfcWatch(px, py, pz, dim, 2, 0);
            w.device = dev;
            w.alive = true;
            RbmkBridge.readEnergy(te, w);
            out.add(w);
        }
        if ((core = RbmkBridge.findTurbineCore(te)) != null) {
            DfcWatch spin = new DfcWatch(px, py, pz, dim, 3, 0);
            spin.device = dev;
            spin.alive = true;
            RbmkBridge.readTurbineSpin(core, spin);
            out.add(spin);
            DfcWatch power = new DfcWatch(px, py, pz, dim, 4, 0);
            power.device = dev;
            power.alive = true;
            RbmkBridge.readTurbinePower(core, power);
            out.add(power);
        }
        return out;
    }

    public static void refreshWatch(World world, DfcWatch w) {
        if (world == null || w == null) {
            return;
        }
        if (w.kind != 1 && w.kind != 2 && w.kind != 3 && w.kind != 4) {
            w.clearReading();
            return;
        }
        if (world.provider.getDimension() != w.dim) {
            w.clearReading();
            return;
        }
        TileEntity te = RbmkBridge.resolveProxy(world.getTileEntity(new BlockPos(w.x, w.y, w.z)));
        if (te == null) {
            w.clearReading();
            return;
        }
        if (w.kind == 1) {
            List<Object> tanks = RbmkBridge.findTanks(te);
            if (w.index < 0 || w.index >= tanks.size()) {
                w.clearReading();
                return;
            }
            w.alive = true;
            RbmkBridge.readFluid(tanks.get(w.index), w);
            return;
        }
        if (w.kind == 2) {
            if (!RbmkBridge.hasPowerApi(te)) {
                w.clearReading();
                return;
            }
            w.alive = true;
            RbmkBridge.readEnergy(te, w);
            return;
        }
        Object core = RbmkBridge.findTurbineCore(te);
        if (core == null) {
            w.clearReading();
            return;
        }
        w.alive = true;
        if (w.kind == 3) {
            RbmkBridge.readTurbineSpin(core, w);
        } else {
            RbmkBridge.readTurbinePower(core, w);
        }
    }

    public static String deviceName(TileEntity te) {
        if (te == null) {
            return "";
        }
        String cn = te.getClass().getSimpleName();
        if (cn.contains("Battery")) {
            return "\u7535\u6c60";
        }
        if (cn.contains("Barrel")) {
            return "\u50a8\u7f50";
        }
        if (cn.contains("FENSU") || cn.contains("FEnSU")) {
            return "\u98de\u8f6e\u50a8\u80fd";
        }
        if (cn.contains("Tank")) {
            return "\u50a8\u6db2\u7f50";
        }
        if (cn.contains("Flywheel")) {
            return "\u98de\u8f6e";
        }
        if (cn.contains("Blade")) {
            return "\u53f6\u7247";
        }
        if (cn.contains("Generator")) {
            return "\u53d1\u7535\u673a";
        }
        if (cn.contains("Separator")) {
            return "\u5206\u79bb\u5668";
        }
        if (cn.startsWith("TileEntity")) {
            cn = cn.substring("TileEntity".length());
        }
        return cn;
    }

    private static List<Object> findTanks(TileEntity te) {
        ArrayList<Object> out = new ArrayList<Object>();
        if (te == null) {
            return out;
        }
        RbmkBridge.collectTankArray(RbmkBridge.callOn(te, "getAllTanks"), out);
        if (!out.isEmpty()) {
            return out;
        }
        RbmkBridge.collectTankArray(RbmkBridge.callOn(te, "getSendingTanks"), out);
        if (!out.isEmpty()) {
            return out;
        }
        for (Field f : RbmkBridge.tankFields(te.getClass())) {
            Object v;
            try {
                v = f.get(te);
            }
            catch (Throwable t) {
                continue;
            }
            if (v instanceof Object[]) {
                for (Object o : (Object[])v) {
                    if (!RbmkBridge.isTankObject(o)) continue;
                    out.add(o);
                }
                continue;
            }
            if (!RbmkBridge.isTankObject(v)) continue;
            out.add(v);
        }
        return out;
    }

    private static void collectTankArray(Object v, List<Object> out) {
        if (v instanceof Object[]) {
            for (Object o : (Object[])v) {
                if (!RbmkBridge.isTankObject(o)) continue;
                out.add(o);
            }
        }
    }

    private static boolean isTankObject(Object o) {
        return o != null && o.getClass().getName().endsWith("FluidTankNTM");
    }

    private static boolean isTankType(Class<?> c) {
        if (c == null) {
            return false;
        }
        if (c.isArray()) {
            return RbmkBridge.isTankType(c.getComponentType());
        }
        return c.getName().endsWith("FluidTankNTM");
    }

    private static List<Field> tankFields(Class<?> cls) {
        List<Field> cached = TANK_FIELDS.get(cls);
        if (cached != null) {
            return cached;
        }
        ArrayList<Field> found = new ArrayList<Field>();
        for (Class<?> c = cls; c != null && c != Object.class; c = c.getSuperclass()) {
            Field[] fs;
            try {
                fs = c.getDeclaredFields();
            }
            catch (Throwable t) {
                continue;
            }
            for (Field f : fs) {
                if (!RbmkBridge.isTankType(f.getType())) continue;
                try {
                    f.setAccessible(true);
                }
                catch (Throwable t) {
                    continue;
                }
                found.add(f);
            }
        }
        TANK_FIELDS.put(cls, found);
        return found;
    }

    private static void readFluid(Object tank, DfcWatch w) {
        double fill = RbmkBridge.numValue(RbmkBridge.callOn(tank, "getFill"));
        double max = RbmkBridge.numValue(RbmkBridge.callOn(tank, "getMaxFill"));
        w.name = RbmkBridge.tankTypeKey(tank);
        w.value = DfcFormat.amount(fill) + " / " + DfcFormat.amount(max);
        w.ratio = max > 0.0 ? RbmkBridge.clamp01(fill / max) : -1.0;
    }

    private static String tankTypeKey(Object tank) {
        Object type = RbmkBridge.callOn(tank, "getTankType");
        if (type == null) {
            return "--";
        }
        Object key = RbmkBridge.callOn(type, "getTranslationKey");
        if (key instanceof String && !((String)key).isEmpty()) {
            return (String)key;
        }
        Object nm = RbmkBridge.callOn(type, "getName");
        if (nm instanceof String) {
            String s = ((String)nm).replace('_', ' ').trim();
            return s.isEmpty() ? "--" : s;
        }
        return "--";
    }

    private static boolean hasPowerApi(TileEntity te) {
        if (te == null) {
            return false;
        }
        Class<?> c = te.getClass();
        Boolean cached = POWER_API.get(c);
        if (cached != null) {
            return cached;
        }
        boolean ok = RbmkBridge.findMethod(c, "getPower") != null && RbmkBridge.findMethod(c, "getMaxPower") != null;
        POWER_API.put(c, ok);
        return ok;
    }

    private static void readEnergy(TileEntity te, DfcWatch w) {
        double cur = RbmkBridge.numValue(RbmkBridge.callOn(te, "getPower"));
        double max = RbmkBridge.numValue(RbmkBridge.callOn(te, "getMaxPower"));
        w.name = "\u50a8\u80fd";
        w.value = DfcFormat.energy(cur) + " / " + DfcFormat.energy(max);
        w.ratio = max > 0.0 ? RbmkBridge.clamp01(cur / max) : -1.0;
    }

    static Object findTurbineCore(TileEntity te) {
        if (te == null) {
            return null;
        }
        Class<?> c = te.getClass();
        Boolean cached = TURBINE_PART.get(c);
        if (cached == null) {
            boolean part = false;
            for (Class<?> k = c; k != null && k != Object.class; k = k.getSuperclass()) {
                Field[] fs;
                try {
                    fs = k.getDeclaredFields();
                }
                catch (Throwable t) {
                    continue;
                }
                for (Field f : fs) {
                    if (!f.getType().getName().endsWith("MTCoreTE")) continue;
                    part = true;
                    break;
                }
                if (part) break;
            }
            TURBINE_PART.put(c, part);
            cached = part;
        }
        if (!cached.booleanValue()) {
            return null;
        }
        return RbmkBridge.getFieldValue(te, "core");
    }

    private static void readTurbineSpin(Object core, DfcWatch w) {
        double rps = RbmkBridge.numValue(RbmkBridge.getFieldValue(core, "rps"));
        w.name = "\u8f6c\u901f";
        w.value = DfcFormat.spin(rps);
        w.ratio = DfcFormat.spinRatio(rps);
    }

    private static void readTurbinePower(Object core, DfcWatch w) {
        Object v = RbmkBridge.getFieldValue(core, "displayPowerGenerated");
        if (!(v instanceof Number)) {
            v = RbmkBridge.getFieldValue(core, "powerOutput");
        }
        double p = RbmkBridge.numValue(v);
        w.name = "\u8f93\u51fa\u529f\u7387";
        w.value = DfcFormat.burst(p);
        w.ratio = -1.0;
    }
}

