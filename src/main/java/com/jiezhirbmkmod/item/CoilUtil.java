package com.jiezhirbmkmod.item;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

public final class CoilUtil {
    private CoilUtil() {
    }

    public static boolean isHolding(EntityPlayer player, Item coil) {
        return CoilUtil.isCoil(player.getHeldItemMainhand(), coil) || CoilUtil.isCoil(player.getHeldItemOffhand(), coil);
    }

    public static boolean isCoil(ItemStack stack, Item coil) {
        return stack != null && !stack.isEmpty() && stack.getItem() == coil;
    }
}

