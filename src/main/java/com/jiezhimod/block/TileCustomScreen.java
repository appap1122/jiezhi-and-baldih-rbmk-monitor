package com.jiezhimod.block;

import com.jiezhimod.block.TileRbmkScreen;
import com.jiezhimod.dfc.DfcWatch;
import com.jiezhimod.dfc.RbmkBridge;
import com.jiezhimod.dfc.RbmkSnapshot;
import com.jiezhimod.item.ItemRbmkRemote;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;

public class TileCustomScreen
extends TileRbmkScreen {
    private final List<DfcWatch> slots = new ArrayList<DfcWatch>();

    public List<DfcWatch> getSlots() {
        return this.slots;
    }

    public int addDevice(TileEntity te) {
        List<DfcWatch> found = RbmkBridge.probeDevice(te);
        int added = 0;
        for (int i = 0; i < found.size() && this.slots.size() < 8; ++i) {
            DfcWatch w = found.get(i);
            boolean dup = false;
            for (int j = 0; j < this.slots.size(); ++j) {
                if (!this.slots.get(j).sameAs(w)) continue;
                dup = true;
                break;
            }
            if (dup) continue;
            this.slots.add(w);
            ++added;
        }
        if (added > 0) {
            this.func_70296_d();
        }
        return added;
    }

    public int[] importDevices(ItemStack remote) {
        int added = 0;
        int skipped = 0;
        int missing = 0;
        int n = ItemRbmkRemote.deviceCount(remote);
        int dim = this.field_145850_b == null ? Integer.MIN_VALUE : this.field_145850_b.field_73011_w.getDimension();
        for (int i = 0; i < n; ++i) {
            BlockPos dp = ItemRbmkRemote.deviceAt(remote, i);
            if (dp == null) continue;
            if (ItemRbmkRemote.deviceDim(remote, i) != dim) {
                ++missing;
                continue;
            }
            TileEntity dev = RbmkBridge.resolveProxy(this.field_145850_b.func_175625_s(dp));
            if (!RbmkBridge.isDevice(dev)) {
                ++missing;
                continue;
            }
            int a = this.addDevice(dev);
            if (a == 0) {
                ++skipped;
                continue;
            }
            added += a;
        }
        return new int[]{added, skipped, missing};
    }

    public boolean removeSlot(int index) {
        if (index < 0 || index >= this.slots.size()) {
            return false;
        }
        this.slots.remove(index);
        this.func_70296_d();
        return true;
    }

    public void clearSlots() {
        if (!this.slots.isEmpty()) {
            this.slots.clear();
            this.func_70296_d();
        }
    }

    public DfcWatch slotAt(int index) {
        return index >= 0 && index < this.slots.size() ? this.slots.get(index) : null;
    }

    public List<DfcWatch> displaySlots() {
        if (this.field_145850_b != null && this.field_145850_b.field_72995_K) {
            return this.getData().watches;
        }
        return this.slots;
    }

    @Override
    protected void fillExtra(RbmkSnapshot d) {
        d.watches.clear();
        if (this.field_145850_b == null) {
            return;
        }
        for (int i = 0; i < this.slots.size(); ++i) {
            DfcWatch w = this.slots.get(i);
            RbmkBridge.refreshWatch(this.field_145850_b, w);
            d.watches.add(w.copy());
        }
    }

    @Override
    public void func_145839_a(NBTTagCompound c) {
        super.func_145839_a(c);
        this.slots.clear();
        int n = c.func_74762_e("WatchCount");
        for (int i = 0; i < n && i < 8; ++i) {
            String p = "Watch" + i + "_";
            if (!c.func_74764_b(p + "Kind")) continue;
            DfcWatch w = new DfcWatch();
            w.x = c.func_74762_e(p + "X");
            w.y = c.func_74762_e(p + "Y");
            w.z = c.func_74762_e(p + "Z");
            w.dim = c.func_74762_e(p + "Dim");
            w.kind = c.func_74762_e(p + "Kind");
            w.index = c.func_74762_e(p + "Idx");
            w.alive = false;
            this.slots.add(w);
        }
    }

    @Override
    public NBTTagCompound func_189515_b(NBTTagCompound c) {
        super.func_189515_b(c);
        c.func_74768_a("WatchCount", this.slots.size());
        for (int i = 0; i < this.slots.size(); ++i) {
            DfcWatch w = this.slots.get(i);
            String p = "Watch" + i + "_";
            c.func_74768_a(p + "X", w.x);
            c.func_74768_a(p + "Y", w.y);
            c.func_74768_a(p + "Z", w.z);
            c.func_74768_a(p + "Dim", w.dim);
            c.func_74768_a(p + "Kind", w.kind);
            c.func_74768_a(p + "Idx", w.index);
        }
        return c;
    }
}

