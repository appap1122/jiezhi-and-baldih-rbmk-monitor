package com.jiezhirbmkmod.block;

import com.jiezhirbmkmod.block.TileCustomScreen;
import com.jiezhirbmkmod.item.ItemRbmkRemote;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.InventoryBasic;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

public class ContainerCustomScreen
extends Container {
    public static final int PANEL_W = 312;
    public static final int PANEL_H = 240;
    public static final int MARGIN = 6;
    public static final int TITLE_Y = 4;
    public static final int POWER_Y = 14;
    public static final int POWER_W = 78;
    public static final int STATE_Y = 33;
    public static final int REMOTE_SLOT_X = 6;
    public static final int REMOTE_SLOT_Y = 45;
    public static final int REMOTE_LABEL_X = 28;
    public static final int REMOTE_LABEL_Y = 50;
    public static final int ADD_BTN_X = 6;
    public static final int ADD_BTN_Y = 67;
    public static final int ADD_BTN_W = 78;
    public static final int ADD_HINT_X = 6;
    public static final int ADD_HINT_Y = 85;
    public static final int LIST_X = 132;
    public static final int LIST_TOP = 14;
    public static final int ROW_H = 12;
    public static final int DEL_W = 34;
    public static final int DEL_H = 11;
    public static final int HINT_Y = 113;
    public static final int BTN_ROW_Y = 134;
    public static final int CLEAR_W = 72;
    public static final int CLOSE_W = 72;
    public static final int CLOSE_X = 120;
    public static final int INV_X = 75;
    public static final int INV_Y = 154;
    public static final int HOTBAR_Y = 210;
    private final TileCustomScreen tile;
    private final InventoryBasic remote = new RemoteInventory();

    public ContainerCustomScreen(InventoryPlayer inv, TileCustomScreen tile) {
        this.tile = tile;
        this.addSlotToContainer(new Slot((IInventory)this.remote, 0, 6, 45){

            public boolean isItemValid(ItemStack stack) {
                return stack != null && stack.getItem() instanceof ItemRbmkRemote;
            }

            public int getSlotStackLimit() {
                return 1;
            }
        });
        for (int row = 0; row < 3; ++row) {
            for (int col = 0; col < 9; ++col) {
                this.addSlotToContainer(new Slot((IInventory)inv, col + row * 9 + 9, 75 + col * 18, 154 + row * 18));
            }
        }
        for (int col = 0; col < 9; ++col) {
            this.addSlotToContainer(new Slot((IInventory)inv, col, 75 + col * 18, 210));
        }
    }

    public ItemStack getRemoteStack() {
        return this.remote.getStackInSlot(0);
    }

    public TileCustomScreen getTile() {
        return this.tile;
    }

    public boolean canInteractWith(EntityPlayer player) {
        if (this.tile == null || this.tile.getWorld() == null) {
            return false;
        }
        return player.getDistanceSq((double)this.tile.getPos().getX() + 0.5, (double)this.tile.getPos().getY() + 0.5, (double)this.tile.getPos().getZ() + 0.5) <= 64.0;
    }

    public void onContainerClosed(EntityPlayer player) {
        super.onContainerClosed(player);
        if (player == null || player.world == null || player.world.isRemote) {
            return;
        }
        ItemStack s = this.remote.getStackInSlot(0);
        if (s.isEmpty()) {
            return;
        }
        this.remote.setInventorySlotContents(0, ItemStack.EMPTY);
        if (!player.inventory.addItemStackToInventory(s)) {
            player.dropItem(s, false);
        }
        player.inventory.markDirty();
    }

    public ItemStack transferStackInSlot(EntityPlayer player, int index) {
        Slot slot;
        Slot slot2 = slot = index >= 0 && index < this.inventorySlots.size() ? (Slot)this.inventorySlots.get(index) : null;
        if (slot == null || !slot.getHasStack()) {
            return ItemStack.EMPTY;
        }
        ItemStack cur = slot.getStack();
        ItemStack copy = cur.copy();
        if (index == 0) {
            if (!this.mergeItemStack(cur, 1, this.inventorySlots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else {
            if (!(cur.getItem() instanceof ItemRbmkRemote) || this.getSlot(0).getHasStack()) {
                return ItemStack.EMPTY;
            }
            this.getSlot(0).putStack(cur.splitStack(1));
            this.getSlot(0).onSlotChanged();
        }
        if (cur.isEmpty()) {
            slot.putStack(ItemStack.EMPTY);
        } else {
            slot.onSlotChanged();
        }
        this.detectAndSendChanges();
        return copy;
    }

    private static class RemoteInventory
    extends InventoryBasic {
        RemoteInventory() {
            super("jiezhirbmkmod:dfc_remote_slot", false, 1);
        }

        public int getInventoryStackLimit() {
            return 1;
        }

        public boolean isItemValidForSlot(int index, ItemStack stack) {
            return stack.getItem() instanceof ItemRbmkRemote;
        }

        public void markDirty() {
        }
    }
}

