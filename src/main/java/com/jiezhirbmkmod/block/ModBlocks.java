package com.jiezhirbmkmod.block;

import com.jiezhirbmkmod.block.BlockRbmkCustomScreen;
import com.jiezhirbmkmod.block.BlockRbmkMonitor;
import com.jiezhirbmkmod.block.BlockRbmkScreen;
import com.jiezhirbmkmod.block.BlockRbmkScreenPart;
import net.minecraft.block.Block;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.registries.IForgeRegistryEntry;

public class ModBlocks {
    public static final CreativeTabs TAB_SCREEN = new CreativeTabs("jiezhirbmkmod_screen"){

        public ItemStack createIcon() {
            return new ItemStack(RBMK_SCREEN);
        }
    };
    public static final Block RBMK_SCREEN = new BlockRbmkScreen();
    public static final ItemBlock ITEM_RBMK_SCREEN = new ItemBlock(RBMK_SCREEN);
    public static final Block RBMK_MONITOR = new BlockRbmkMonitor();
    public static final ItemBlock ITEM_RBMK_MONITOR = new ItemBlock(RBMK_MONITOR);
    public static final Block RBMK_CUSTOM = new BlockRbmkCustomScreen();
    public static final ItemBlock ITEM_RBMK_CUSTOM = new ItemBlock(RBMK_CUSTOM);
    public static final Block RBMK_GRAPH = new BlockRbmkGraph();
    public static final ItemBlock ITEM_RBMK_GRAPH = new ItemBlock(RBMK_GRAPH);
    public static final Block RBMK_SCREEN_PART = new BlockRbmkScreenPart();
    public static final Block RBMK_SIREN = new BlockRbmkSiren();
    public static final ItemBlock ITEM_RBMK_SIREN = new ItemBlock(RBMK_SIREN);
    public static final Block RBMK_TERMINAL = new BlockRbmkTerminal();
    public static final ItemBlock ITEM_RBMK_TERMINAL = new ItemBlock(RBMK_TERMINAL);
    public static final Block RBMK_TERM_SCREEN = new BlockRbmkTermScreen();
    public static final ItemBlock ITEM_RBMK_TERM_SCREEN = new ItemBlock(RBMK_TERM_SCREEN);
    public static final Block RBMK_ROUTER = new BlockRbmkRouter();
    public static final ItemBlock ITEM_RBMK_ROUTER = new ItemBlock(RBMK_ROUTER);

    @SubscribeEvent
    public void onRegisterBlocks(RegistryEvent.Register<Block> event) {
        event.getRegistry().register(RBMK_SCREEN);
        event.getRegistry().register(RBMK_MONITOR);
        event.getRegistry().register(RBMK_CUSTOM);
        event.getRegistry().register(RBMK_GRAPH);
        event.getRegistry().register(RBMK_SCREEN_PART);
        event.getRegistry().register(RBMK_SIREN);
        event.getRegistry().register(RBMK_TERMINAL);
        event.getRegistry().register(RBMK_TERM_SCREEN);
        event.getRegistry().register(RBMK_ROUTER);
    }

    @SubscribeEvent
    public void onRegisterItems(RegistryEvent.Register<Item> event) {
        event.getRegistry().register(ITEM_RBMK_SCREEN);
        event.getRegistry().register(ITEM_RBMK_MONITOR);
        event.getRegistry().register(ITEM_RBMK_CUSTOM);
        event.getRegistry().register(ITEM_RBMK_GRAPH);
        event.getRegistry().register(ITEM_RBMK_SIREN);
        event.getRegistry().register(ITEM_RBMK_TERMINAL);
        event.getRegistry().register(ITEM_RBMK_TERM_SCREEN);
        event.getRegistry().register(ITEM_RBMK_ROUTER);
    }

    static {
        ITEM_RBMK_SCREEN.setRegistryName(RBMK_SCREEN.getRegistryName());
        ITEM_RBMK_SCREEN.setCreativeTab(TAB_SCREEN);
        ITEM_RBMK_MONITOR.setRegistryName(RBMK_MONITOR.getRegistryName());
        ITEM_RBMK_MONITOR.setCreativeTab(TAB_SCREEN);
        ITEM_RBMK_CUSTOM.setRegistryName(RBMK_CUSTOM.getRegistryName());
        ITEM_RBMK_CUSTOM.setCreativeTab(TAB_SCREEN);
        ITEM_RBMK_GRAPH.setRegistryName(RBMK_GRAPH.getRegistryName());
        ITEM_RBMK_GRAPH.setCreativeTab(TAB_SCREEN);
        ITEM_RBMK_SIREN.setRegistryName(RBMK_SIREN.getRegistryName());
        ITEM_RBMK_SIREN.setCreativeTab(TAB_SCREEN);
        ITEM_RBMK_SIREN.setHasSubtypes(true);
        ITEM_RBMK_TERMINAL.setRegistryName(RBMK_TERMINAL.getRegistryName());
        ITEM_RBMK_TERMINAL.setCreativeTab(TAB_SCREEN);
        ITEM_RBMK_TERM_SCREEN.setRegistryName(RBMK_TERM_SCREEN.getRegistryName());
        ITEM_RBMK_TERM_SCREEN.setCreativeTab(TAB_SCREEN);
        ITEM_RBMK_ROUTER.setRegistryName(RBMK_ROUTER.getRegistryName());
        ITEM_RBMK_ROUTER.setCreativeTab(TAB_SCREEN);
    }
}

