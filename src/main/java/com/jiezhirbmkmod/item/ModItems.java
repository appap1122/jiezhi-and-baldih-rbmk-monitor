package com.jiezhirbmkmod.item;

import com.jiezhirbmkmod.item.ItemGravityCoil;
import com.jiezhirbmkmod.item.ItemHealingCoil;
import com.jiezhirbmkmod.item.ItemJetSwitch;
import com.jiezhirbmkmod.item.ItemJetpack;
import com.jiezhirbmkmod.item.ItemRbmkLaptop;
import com.jiezhirbmkmod.item.ItemRbmkRemote;
import com.jiezhirbmkmod.item.ItemSpeedCoil;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.registries.IForgeRegistryEntry;

public class ModItems {
    public static final CreativeTabs TAB = new CreativeTabs("jiezhirbmkmod"){

        public ItemStack createIcon() {
            return new ItemStack(SPEED_COIL);
        }
    };
    public static final Item SPEED_COIL = new ItemSpeedCoil();
    public static final Item GRAVITY_COIL = new ItemGravityCoil();
    public static final Item HEALING_COIL = new ItemHealingCoil();
    public static final Item JETPACK = new ItemJetpack();
    public static final Item JET_SWITCH = new ItemJetSwitch();
    public static final Item RBMK_REMOTE = new ItemRbmkRemote();
    public static final Item RBMK_LAPTOP = new ItemRbmkLaptop();

    @SubscribeEvent
    public void onRegisterItems(RegistryEvent.Register<Item> event) {
        event.getRegistry().register(SPEED_COIL);
        event.getRegistry().register(GRAVITY_COIL);
        event.getRegistry().register(HEALING_COIL);
        event.getRegistry().register(JETPACK);
        event.getRegistry().register(JET_SWITCH);
        event.getRegistry().register(RBMK_REMOTE);
        event.getRegistry().register(RBMK_LAPTOP);
    }

    static {
        SPEED_COIL.setCreativeTab(TAB);
        GRAVITY_COIL.setCreativeTab(TAB);
        HEALING_COIL.setCreativeTab(TAB);
        JETPACK.setCreativeTab(TAB);
        JET_SWITCH.setCreativeTab(TAB);
        RBMK_REMOTE.setCreativeTab(TAB);
        RBMK_LAPTOP.setCreativeTab(TAB);
    }
}

