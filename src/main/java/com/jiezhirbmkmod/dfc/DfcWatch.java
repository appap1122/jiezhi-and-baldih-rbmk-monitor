package com.jiezhirbmkmod.dfc;

import com.jiezhirbmkmod.dfc.RbmkSnapshot;
import io.netty.buffer.ByteBuf;

public final class DfcWatch {
    public static final int KIND_NONE = 0;
    public static final int KIND_FLUID = 1;
    public static final int KIND_ENERGY = 2;
    public static final int KIND_SPIN = 3;
    public static final int KIND_TURBINE_POWER = 4;
    public static final int MAX = 8;
    public int x;
    public int y;
    public int z;
    public int dim;
    public int kind = 0;
    public int index;
    public String device = "";
    public String name = "";
    public String value = "--";
    public double ratio = -1.0;
    public boolean alive;

    public DfcWatch() {
    }

    public DfcWatch(int x, int y, int z, int dim, int kind, int index) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.dim = dim;
        this.kind = kind;
        this.index = index;
        this.alive = true;
    }

    public boolean sameAs(DfcWatch o) {
        return o != null && this.x == o.x && this.y == o.y && this.z == o.z && this.dim == o.dim && this.kind == o.kind && this.index == o.index;
    }

    public DfcWatch copy() {
        DfcWatch w = new DfcWatch(this.x, this.y, this.z, this.dim, this.kind, this.index);
        w.device = this.device;
        w.name = this.name;
        w.value = this.value;
        w.ratio = this.ratio;
        w.alive = this.alive;
        return w;
    }

    public void clearReading() {
        this.value = "--";
        this.ratio = -1.0;
        this.alive = false;
    }

    public String posText() {
        return this.x + " " + this.y + " " + this.z;
    }

    public void writeTo(ByteBuf buf) {
        buf.writeInt(this.x);
        buf.writeInt(this.y);
        buf.writeInt(this.z);
        buf.writeInt(this.dim);
        buf.writeByte(this.kind);
        buf.writeInt(this.index);
        buf.writeBoolean(this.alive);
        buf.writeDouble(this.ratio);
        RbmkSnapshot.writeShortString(buf, this.device);
        RbmkSnapshot.writeShortString(buf, this.name);
        RbmkSnapshot.writeShortString(buf, this.value);
    }

    public void readFrom(ByteBuf buf) {
        this.x = buf.readInt();
        this.y = buf.readInt();
        this.z = buf.readInt();
        this.dim = buf.readInt();
        this.kind = buf.readByte();
        this.index = buf.readInt();
        this.alive = buf.readBoolean();
        this.ratio = buf.readDouble();
        this.device = RbmkSnapshot.readShortString(buf);
        this.name = RbmkSnapshot.readShortString(buf);
        this.value = RbmkSnapshot.readShortString(buf);
    }
}

