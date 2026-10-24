package com.jiezhimod.block;

import com.jiezhimod.block.BlockRbmkCustomScreen;
import com.jiezhimod.block.BlockRbmkMonitor;
import com.jiezhimod.block.BlockRbmkScreen;
import com.jiezhimod.block.BlockRbmkScreenPart;
import net.minecraft.block.Block;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.registries.IForgeRegistryEntry;

public class ModBlocks {
    public static final CreativeTabs TAB_SCREEN = new CreativeTabs("jiezhimod_screen"){

        public ItemStack func_78016_d() {
            return new ItemStack(RBMK_SCREEN);
        }
    };
    public static final Block RBMK_SCREEN = new BlockRbmkScreen();
    public static final ItemBlock ITEM_RBMK_SCREEN = new ItemBlock(RBMK_SCREEN);
    public static final Block RBMK_MONITOR = new BlockRbmkMonitor();
    public static final ItemBlock ITEM_RBMK_MONITOR = new ItemBlock(RBMK_MONITOR);
    public static final Block RBMK_CUSTOM = new BlockRbmkCustomScreen();
    public static final ItemBlock ITEM_RBMK_CUSTOM = new ItemBlock(RBMK_CUSTOM);
    public static final Block RBMK_SCREEN_PART = new BlockRbmkScreenPart();

    @SubscribeEvent
    public void onRegisterBlocks(RegistryEvent.Register<Block> event) {
        event.getRegistry().register((IForgeRegistryEntry)RBMK_SCREEN);
        event.getRegistry().register((IForgeRegistryEntry)RBMK_MONITOR);
        event.getRegistry().register((IForgeRegistryEntry)RBMK_CUSTOM);
        event.getRegistry().register((IForgeRegistryEntry)RBMK_SCREEN_PART);
    }

    @SubscribeEvent
    public void onRegisterItems(RegistryEvent.Register<Item> event) {
        event.getRegistry().register((IForgeRegistryEntry)ITEM_RBMK_SCREEN);
        event.getRegistry().register((IForgeRegistryEntry)ITEM_RBMK_MONITOR);
        event.getRegistry().register((IForgeRegistryEntry)ITEM_RBMK_CUSTOM);
    }

    static {
        ITEM_RBMK_SCREEN.setRegistryName(RBMK_SCREEN.getRegistryName());
        ITEM_RBMK_SCREEN.func_77637_a(TAB_SCREEN);
        ITEM_RBMK_MONITOR.setRegistryName(RBMK_MONITOR.getRegistryName());
        ITEM_RBMK_MONITOR.func_77637_a(TAB_SCREEN);
        ITEM_RBMK_CUSTOM.setRegistryName(RBMK_CUSTOM.getRegistryName());
        ITEM_RBMK_CUSTOM.func_77637_a(TAB_SCREEN);
    }
}

