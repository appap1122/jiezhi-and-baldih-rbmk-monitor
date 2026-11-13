package com.jiezhirbmkmod.item;

import net.minecraft.item.Item;

public abstract class ItemCoil
extends Item {
    protected ItemCoil(String name) {
        this.setRegistryName("jiezhirbmkmod", name);
        this.setTranslationKey(name);
        this.setMaxStackSize(1);
    }
}

