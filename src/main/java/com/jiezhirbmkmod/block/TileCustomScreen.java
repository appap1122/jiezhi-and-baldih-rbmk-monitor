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

    // ---- v18.2 自定义页布局（布局编辑器产物，空 = 旧版固定排布）----

    private final com.jiezhirbmkmod.dfc.CustomWidgetLayout layout =
            new com.jiezhirbmkmod.dfc.CustomWidgetLayout();

    /** 布局（空 widgets = 未编辑过，画布走旧版固定排布） */
    public com.jiezhirbmkmod.dfc.CustomWidgetLayout getLayout() {
        return this.layout;
    }

    /** 编辑器保存：整体替换并持久化 + 同步 */
    public void setLayout(com.jiezhirbmkmod.dfc.CustomWidgetLayout newLayout) {
        this.layout.clear();
        if (newLayout != null) {
            for (com.jiezhirbmkmod.dfc.CustomWidgetLayout.Widget w : newLayout.widgets()) {
                this.layout.add(w.copy());
            }
        }
        this.markDirty();
        if (this.world != null && !this.world.isRemote) {
            this.world.notifyBlockUpdate(this.pos, this.world.getBlockState(this.pos),
                    this.world.getBlockState(this.pos), 3);
        }
    }

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
            RbmkSnapshot data = this.getData();
            List<DfcWatch> synced = data == null ? null : data.watches;
            return synced == null ? this.slots : synced;
        }
        return this.slots;
    }

    @Override
    protected DfcWatch firstWatch() {
        return this.slots.isEmpty() ? null : this.slots.get(0);
    }

    @Override
    protected void fillExtra(RbmkSnapshot d) {
        if (d == null) {
            return;
        }
        d.watches.clear();
        if (this.world == null) {
            return;
        }
        for (int i = 0; i < this.slots.size(); ++i) {
            DfcWatch w = this.slots.get(i);
            if (w == null) {
                continue;
            }
            RbmkBridge.refreshWatch(this.world, w);
            // refreshWatch only restores value/ratio; after a world reload the
            // cached device label is empty, which used to blank out the left
            // name column of the GUI and the wall display. Re-resolve it.
            if ((w.device == null || w.device.isEmpty())
                    && w.dim == this.world.provider.getDimension()) {
                TileEntity te = RbmkBridge.resolveProxy(
                        this.world.getTileEntity(new BlockPos(w.x, w.y, w.z)));
                w.device = RbmkBridge.deviceName(te);
            }
            d.watches.add(w.copy());
        }
    }

    @Override
    public void readFromNBT(NBTTagCompound c) {
        super.readFromNBT(c);
        // v18.2 自定义页布局（旧存档无键 → 空布局 = 旧版固定排布）
        this.layout.readNBT(c);
        this.slots.clear();
        int n = c.hasKey("WatchCount") ? c.getInteger("WatchCount") : 0;
        for (int i = 0; i < n && i < 8; ++i) {
            String p = "Watch" + i + "_";
            if (!c.hasKey(p + "Kind")) continue;
            DfcWatch w = new DfcWatch();
            w.x = c.hasKey(p + "X") ? c.getInteger(p + "X") : 0;
            w.y = c.hasKey(p + "Y") ? c.getInteger(p + "Y") : 0;
            w.z = c.hasKey(p + "Z") ? c.getInteger(p + "Z") : 0;
            w.dim = c.hasKey(p + "Dim") ? c.getInteger(p + "Dim") : 0;
            w.kind = c.hasKey(p + "Kind") ? c.getInteger(p + "Kind") : 0;
            w.index = c.hasKey(p + "Idx") ? c.getInteger(p + "Idx") : 0;
            w.alive = false;
            this.slots.add(w);
        }
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound c) {
        super.writeToNBT(c);
        // v18.2 自定义页布局
        this.layout.writeNBT(c);
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

