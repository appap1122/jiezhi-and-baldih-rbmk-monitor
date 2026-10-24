package com.jiezhimod.block;

import com.jiezhimod.block.TileCustomScreen;
import com.jiezhimod.item.ItemRbmkRemote;
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
        this.func_75146_a(new Slot((IInventory)this.remote, 0, 6, 45){

            public boolean func_75214_a(ItemStack stack) {
                return stack != null && stack.func_77973_b() instanceof ItemRbmkRemote;
            }

            public int func_75219_a() {
                return 1;
            }
        });
        for (int row = 0; row < 3; ++row) {
            for (int col = 0; col < 9; ++col) {
                this.func_75146_a(new Slot((IInventory)inv, col + row * 9 + 9, 75 + col * 18, 154 + row * 18));
            }
        }
        for (int col = 0; col < 9; ++col) {
            this.func_75146_a(new Slot((IInventory)inv, col, 75 + col * 18, 210));
        }
    }

    public ItemStack getRemoteStack() {
        return this.remote.func_70301_a(0);
    }

    public TileCustomScreen getTile() {
        return this.tile;
    }

    public boolean func_75145_c(EntityPlayer player) {
        if (this.tile == null || this.tile.func_145831_w() == null) {
            return false;
        }
        return player.func_70092_e((double)this.tile.func_174877_v().func_177958_n() + 0.5, (double)this.tile.func_174877_v().func_177956_o() + 0.5, (double)this.tile.func_174877_v().func_177952_p() + 0.5) <= 64.0;
    }

    public void func_75134_a(EntityPlayer player) {
        super.func_75134_a(player);
        if (player == null || player.field_70170_p == null || player.field_70170_p.field_72995_K) {
            return;
        }
        ItemStack s = this.remote.func_70301_a(0);
        if (s.func_190926_b()) {
            return;
        }
        this.remote.func_70299_a(0, ItemStack.field_190927_a);
        if (!player.field_71071_by.func_70441_a(s)) {
            player.func_71019_a(s, false);
        }
        player.field_71071_by.func_70296_d();
    }

    public ItemStack func_82846_b(EntityPlayer player, int index) {
        Slot slot;
        Slot slot2 = slot = index >= 0 && index < this.field_75151_b.size() ? (Slot)this.field_75151_b.get(index) : null;
        if (slot == null || !slot.func_75216_d()) {
            return ItemStack.field_190927_a;
        }
        ItemStack cur = slot.func_75211_c();
        ItemStack copy = cur.func_77946_l();
        if (index == 0) {
            if (!this.func_75135_a(cur, 1, this.field_75151_b.size(), true)) {
                return ItemStack.field_190927_a;
            }
        } else {
            if (!(cur.func_77973_b() instanceof ItemRbmkRemote) || this.func_75139_a(0).func_75216_d()) {
                return ItemStack.field_190927_a;
            }
            this.func_75139_a(0).func_75215_d(cur.func_77979_a(1));
            this.func_75139_a(0).func_75218_e();
        }
        if (cur.func_190926_b()) {
            slot.func_75215_d(ItemStack.field_190927_a);
        } else {
            slot.func_75218_e();
        }
        this.func_75142_b();
        return copy;
    }

    private static class RemoteInventory
    extends InventoryBasic {
        RemoteInventory() {
            super("jiezhimod:dfc_remote_slot", false, 1);
        }

        public int func_70297_j_() {
            return 1;
        }

        public boolean func_94041_b(int index, ItemStack stack) {
            return stack.func_77973_b() instanceof ItemRbmkRemote;
        }

        public void func_70296_d() {
        }
    }
}

