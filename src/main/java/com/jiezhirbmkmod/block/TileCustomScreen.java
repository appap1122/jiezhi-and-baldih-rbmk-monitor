package com.jiezhirbmkmod.block;

import com.jiezhirbmkmod.block.TileRbmkMonitorScreen;
import com.jiezhirbmkmod.dfc.DfcWatch;
import com.jiezhirbmkmod.dfc.RbmkBridge;
import com.jiezhirbmkmod.dfc.RbmkSnapshot;
import com.jiezhirbmkmod.item.ItemRbmkRemote;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;

public class TileCustomScreen
extends TileRbmkMonitorScreen {
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
            this.markDirty();
        }
        return added;
    }

    public int[] importDevices(ItemStack remote) {
        int added = 0;
        int skipped = 0;
        int missing = 0;
        int n = ItemRbmkRemote.deviceCount(remote);
        int dim = this.world == null ? Integer.MIN_VALUE : this.world.provider.getDimension();
        for (int i = 0; i < n; ++i) {
            BlockPos dp = ItemRbmkRemote.deviceAt(remote, i);
            if (dp == null) continue;
            if (ItemRbmkRemote.deviceDim(remote, i) != dim) {
                ++missing;
                continue;
            }
            TileEntity dev = RbmkBridge.resolveProxy(this.world.getTileEntity(dp));
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
        this.markDirty();
        return true;
    }

    public void clearSlots() {
        if (!this.slots.isEmpty()) {
            this.slots.clear();
            this.markDirty();
        }
    }

    public DfcWatch slotAt(int index) {
        return index >= 0 && index < this.slots.size() ? this.slots.get(index) : null;
    }

    public List<DfcWatch> displaySlots() {
        if (this.world != null && this.world.isRemote) {
            return this.getData().watches;
        }
        return this.slots;
    }

    @Override
    protected DfcWatch firstWatch() {
        return this.slots.isEmpty() ? null : this.slots.get(0);
    }

    @Override
    protected void fillExtra(RbmkSnapshot d) {
        d.watches.clear();
        if (this.world == null) {
            return;
        }
        for (int i = 0; i < this.slots.size(); ++i) {
            DfcWatch w = this.slots.get(i);
            RbmkBridge.refreshWatch(this.world, w);
            d.watches.add(w.copy());
        }
    }

    @Override
    public void readFromNBT(NBTTagCompound c) {
        super.readFromNBT(c);
        this.slots.clear();
        int n = c.getInteger("WatchCount");
        for (int i = 0; i < n && i < 8; ++i) {
            String p = "Watch" + i + "_";
            if (!c.hasKey(p + "Kind")) continue;
            DfcWatch w = new DfcWatch();
            w.x = c.getInteger(p + "X");
            w.y = c.getInteger(p + "Y");
            w.z = c.getInteger(p + "Z");
            w.dim = c.getInteger(p + "Dim");
            w.kind = c.getInteger(p + "Kind");
            w.index = c.getInteger(p + "Idx");
            w.alive = false;
            this.slots.add(w);
        }
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound c) {
        super.writeToNBT(c);
        c.setInteger("WatchCount", this.slots.size());
        for (int i = 0; i < this.slots.size(); ++i) {
            DfcWatch w = this.slots.get(i);
            String p = "Watch" + i + "_";
            c.setInteger(p + "X", w.x);
            c.setInteger(p + "Y", w.y);
            c.setInteger(p + "Z", w.z);
            c.setInteger(p + "Dim", w.dim);
            c.setInteger(p + "Kind", w.kind);
            c.setInteger(p + "Idx", w.index);
        }
        return c;
    }
}

