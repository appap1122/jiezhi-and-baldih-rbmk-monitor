package com.jiezhirbmkmod.dfc;

import com.jiezhirbmkmod.dfc.DfcWatch;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;

public final class RbmkSnapshot {
    public static final int SOURCE_NONE = 0;
    public static final int SOURCE_CONSOLE = 1;
    public static final int SOURCE_PART = 2;
    public boolean linked;
    public int sourceKind;
    public boolean offline;
    public boolean cold;
    /** true when the column scan hit the 2000-column cap and was truncated */
    public boolean truncated;
    public int columnCount;
    public double avgHeat;
    public double maxHeat;
    public double maxHeatLimit;
    public double steamTotal;
    public int flux;
    public int fuelCount;
    public double avgEnrichment;
    public double avgXenon;
    public double avgCoreTemp;
    public int controlCount;
    public double avgRodLevel;
    public int boilerCount;
    public double waterTotal;
    public double meltRisk;
    public int hottestIndex;
    public double tempDelta;
    public int runTicks;
    public boolean meltdown;
    public int meltdownFrames;
    public final List<String> events = new ArrayList<String>();
    public final List<DfcWatch> watches = new ArrayList<DfcWatch>();
    public boolean includeGrid;
    public final List<RbmkCell> grid = new ArrayList<RbmkCell>();

    public static RbmkSnapshot empty() {
        RbmkSnapshot s = new RbmkSnapshot();
        s.reset();
        return s;
    }

    public void reset() {
        this.linked = false;
        this.sourceKind = 0;
        this.offline = false;
        this.cold = false;
        this.truncated = false;
        this.columnCount = 0;
        this.avgHeat = 0.0;
        this.maxHeat = 0.0;
        this.maxHeatLimit = 0.0;
        this.steamTotal = 0.0;
        this.flux = 0;
        this.fuelCount = 0;
        this.avgEnrichment = 0.0;
        this.avgXenon = 0.0;
        this.avgCoreTemp = 0.0;
        this.controlCount = 0;
        this.avgRodLevel = 0.0;
        this.boilerCount = 0;
        this.waterTotal = 0.0;
        this.meltRisk = 0.0;
        this.hottestIndex = -1;
        this.tempDelta = 0.0;
        this.runTicks = 0;
        this.meltdown = false;
        this.meltdownFrames = 0;
        this.events.clear();
        this.watches.clear();
        this.includeGrid = false;
        this.grid.clear();
    }

    public void copyFrom(RbmkSnapshot src) {
        int i;
        this.linked = src.linked;
        this.sourceKind = src.sourceKind;
        this.offline = src.offline;
        this.cold = src.cold;
        this.truncated = src.truncated;
        this.columnCount = src.columnCount;
        this.avgHeat = src.avgHeat;
        this.maxHeat = src.maxHeat;
        this.maxHeatLimit = src.maxHeatLimit;
        this.steamTotal = src.steamTotal;
        this.flux = src.flux;
        this.fuelCount = src.fuelCount;
        this.avgEnrichment = src.avgEnrichment;
        this.avgXenon = src.avgXenon;
        this.avgCoreTemp = src.avgCoreTemp;
        this.controlCount = src.controlCount;
        this.avgRodLevel = src.avgRodLevel;
        this.boilerCount = src.boilerCount;
        this.waterTotal = src.waterTotal;
        this.meltRisk = src.meltRisk;
        this.hottestIndex = src.hottestIndex;
        this.tempDelta = src.tempDelta;
        this.runTicks = src.runTicks;
        this.meltdown = src.meltdown;
        this.meltdownFrames = src.meltdownFrames;
        this.events.clear();
        this.events.addAll(src.events);
        this.watches.clear();
        for (i = 0; i < src.watches.size(); ++i) {
            this.watches.add(src.watches.get(i).copy());
        }
        this.includeGrid = src.includeGrid;
        this.grid.clear();
        for (i = 0; i < src.grid.size(); ++i) {
            this.grid.add(src.grid.get(i).copy());
        }
    }

    public void resetStats() {
        this.truncated = false;
        this.columnCount = 0;
        this.avgHeat = 0.0;
        this.maxHeat = 0.0;
        this.maxHeatLimit = 0.0;
        this.steamTotal = 0.0;
        this.flux = 0;
        this.fuelCount = 0;
        this.avgEnrichment = 0.0;
        this.avgXenon = 0.0;
        this.avgCoreTemp = 0.0;
        this.controlCount = 0;
        this.avgRodLevel = 0.0;
        this.boilerCount = 0;
        this.waterTotal = 0.0;
        this.meltRisk = 0.0;
        this.hottestIndex = -1;
        this.tempDelta = 0.0;
        this.meltdown = false;
        this.meltdownFrames = 0;
        this.grid.clear();
    }

    public void finishStats() {
        if (this.columnCount > 0) {
            this.avgHeat /= (double)this.columnCount;
        }
        if (this.fuelCount > 0) {
            this.avgEnrichment /= (double)this.fuelCount;
            this.avgXenon /= (double)this.fuelCount;
            this.avgCoreTemp /= (double)this.fuelCount;
        }
        if (this.controlCount > 0) {
            this.avgRodLevel /= (double)this.controlCount;
        }
        if (this.meltRisk > 1.0) {
            this.meltRisk = 1.0;
        }
    }

    public static void writeShortString(ByteBuf buf, String s) {
        if (s == null) {
            s = "";
        }
        buf.writeShort(s.length());
        for (int i = 0; i < s.length(); ++i) {
            buf.writeChar((int)s.charAt(i));
        }
    }

    public static String readShortString(ByteBuf buf) {
        int len = buf.readUnsignedShort();
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; ++i) {
            sb.append(buf.readChar());
        }
        return sb.toString();
    }

    public void writeTo(ByteBuf buf) {
        int i;
        buf.writeBoolean(this.linked);
        buf.writeInt(this.sourceKind);
        buf.writeBoolean(this.offline);
        buf.writeBoolean(this.cold);
        buf.writeBoolean(this.truncated);
        buf.writeInt(this.columnCount);
        // v17.8 瘦身：显示精度用不到 double，float 一半的体积足够
        // （柱温/蒸汽/丰度/氙/棒位/温差共 10 个字段，每包省 40 字节）
        buf.writeFloat((float)this.avgHeat);
        buf.writeFloat((float)this.maxHeat);
        buf.writeFloat((float)this.maxHeatLimit);
        buf.writeFloat((float)this.steamTotal);
        buf.writeInt(this.flux);
        buf.writeInt(this.fuelCount);
        buf.writeFloat((float)this.avgEnrichment);
        buf.writeFloat((float)this.avgXenon);
        buf.writeFloat((float)this.avgCoreTemp);
        buf.writeInt(this.controlCount);
        buf.writeFloat((float)this.avgRodLevel);
        buf.writeInt(this.boilerCount);
        buf.writeFloat((float)this.waterTotal);
        buf.writeFloat((float)this.meltRisk);
        buf.writeInt(this.hottestIndex);
        buf.writeFloat((float)this.tempDelta);
        buf.writeInt(this.runTicks);
        buf.writeBoolean(this.meltdown);
        buf.writeInt(this.meltdownFrames);
        buf.writeInt(this.events.size());
        for (i = 0; i < this.events.size(); ++i) {
            String s = this.events.get(i);
            buf.writeInt(s.length());
            for (int j = 0; j < s.length(); ++j) {
                buf.writeChar((int)s.charAt(j));
            }
        }
        buf.writeInt(this.watches.size());
        for (i = 0; i < this.watches.size(); ++i) {
            this.watches.get(i).writeTo(buf);
        }
        buf.writeBoolean(this.includeGrid);
        buf.writeInt(this.grid.size());
        for (i = 0; i < this.grid.size(); ++i) {
            RbmkCell c = this.grid.get(i);
            buf.writeInt(c.index);
            buf.writeByte(c.type);
            buf.writeFloat((float)c.heat);
            buf.writeFloat((float)c.heatLimit);
            buf.writeFloat((float)c.v1);
            buf.writeFloat((float)c.v2);
        }
    }

    public void readFrom(ByteBuf buf) {
        this.linked = buf.readBoolean();
        this.sourceKind = buf.readInt();
        this.offline = buf.readBoolean();
        this.cold = buf.readBoolean();
        this.truncated = buf.readBoolean();
        this.columnCount = buf.readInt();
        // v17.8 瘦身：与 writeTo 的 float 编码镜像（字段仍是 double 存储）
        this.avgHeat = buf.readFloat();
        this.maxHeat = buf.readFloat();
        this.maxHeatLimit = buf.readFloat();
        this.steamTotal = buf.readFloat();
        this.flux = buf.readInt();
        this.fuelCount = buf.readInt();
        this.avgEnrichment = buf.readFloat();
        this.avgXenon = buf.readFloat();
        this.avgCoreTemp = buf.readFloat();
        this.controlCount = buf.readInt();
        this.avgRodLevel = buf.readFloat();
        this.boilerCount = buf.readInt();
        this.waterTotal = buf.readFloat();
        this.meltRisk = buf.readFloat();
        this.hottestIndex = buf.readInt();
        this.tempDelta = buf.readFloat();
        this.runTicks = buf.readInt();
        this.meltdown = buf.readBoolean();
        this.meltdownFrames = buf.readInt();
        this.events.clear();
        int ne = buf.readInt();
        for (int i = 0; i < ne; ++i) {
            int len = buf.readInt();
            StringBuilder sb = new StringBuilder(len);
            for (int j = 0; j < len; ++j) {
                sb.append(buf.readChar());
            }
            this.events.add(sb.toString());
        }
        this.watches.clear();
        int nw = buf.readInt();
        for (int i = 0; i < nw; ++i) {
            DfcWatch w = new DfcWatch();
            w.readFrom(buf);
            this.watches.add(w);
        }
        this.includeGrid = buf.readBoolean();
        this.grid.clear();
        int ng = buf.readInt();
        for (int i = 0; i < ng; ++i) {
            RbmkCell c = new RbmkCell();
            c.index = buf.readInt();
            c.type = buf.readUnsignedByte();
            c.heat = buf.readFloat();
            c.heatLimit = buf.readFloat();
            c.v1 = buf.readFloat();
            c.v2 = buf.readFloat();
            this.grid.add(c);
        }
    }

    public static final class RbmkCell {
        public static final int TYPE_BLANK = 0;
        public static final int TYPE_FUEL = 1;
        public static final int TYPE_FUEL_SIM = 2;
        public static final int TYPE_CONTROL = 3;
        public static final int TYPE_CONTROL_AUTO = 4;
        public static final int TYPE_BOILER = 5;
        public static final int TYPE_MODERATOR = 6;
        public static final int TYPE_ABSORBER = 7;
        public static final int TYPE_REFLECTOR = 8;
        public static final int TYPE_OUTGASSER = 9;
        public static final int TYPE_BREEDER = 10;
        public static final int TYPE_STORAGE = 11;
        public static final int TYPE_COOLER = 12;
        public static final int TYPE_HEATEX = 13;
        public int index;
        public int type;
        public double heat;
        public double heatLimit;
        public double v1;
        public double v2;

        public RbmkCell copy() {
            RbmkCell c = new RbmkCell();
            c.index = this.index;
            c.type = this.type;
            c.heat = this.heat;
            c.heatLimit = this.heatLimit;
            c.v1 = this.v1;
            c.v2 = this.v2;
            return c;
        }

        public static int typeOrdinal(String name) {
            if (name == null) {
                return 0;
            }
            if (name.equals("FUEL")) {
                return 1;
            }
            if (name.equals("FUEL_SIM")) {
                return 2;
            }
            if (name.equals("BREEDER")) {
                return 10;
            }
            if (name.equals("CONTROL")) {
                return 3;
            }
            if (name.equals("CONTROL_AUTO")) {
                return 4;
            }
            if (name.equals("BOILER")) {
                return 5;
            }
            if (name.equals("MODERATOR")) {
                return 6;
            }
            if (name.equals("ABSORBER")) {
                return 7;
            }
            if (name.equals("REFLECTOR")) {
                return 8;
            }
            if (name.equals("OUTGASSER")) {
                return 9;
            }
            if (name.equals("STORAGE")) {
                return 11;
            }
            if (name.equals("COOLER")) {
                return 12;
            }
            if (name.equals("HEATEX")) {
                return 13;
            }
            return 0;
        }

        public static String typeName(int type) {
            switch (type) {
                case 1: 
                case 2: 
                case 10: {
                    return "FUEL";
                }
                case 3: 
                case 4: {
                    return "CONTROL";
                }
                case 5: {
                    return "BOILER";
                }
                case 12: {
                    return "COOLER";
                }
                case 13: {
                    return "HEATEX";
                }
                case 9: {
                    return "OUTGASSER";
                }
                case 6: {
                    return "MODERATOR";
                }
                case 7: {
                    return "ABSORBER";
                }
                case 8: {
                    return "REFLECTOR";
                }
                case 11: {
                    return "STORAGE";
                }
            }
            return "BLANK";
        }
    }
}

