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
    public static final Block RBMK_MEDIA_SCREEN = new BlockRbmkMediaScreen();
    public static final Block RBMK_PPT_SCREEN = new BlockRbmkPptScreen();
    public static final ItemBlock ITEM_RBMK_PPT_SCREEN = new ItemBlock(RBMK_PPT_SCREEN);
    public static final ItemBlock ITEM_RBMK_MEDIA_SCREEN = new ItemBlock(RBMK_MEDIA_SCREEN);
    public static final Block RBMK_ALARM_LIGHT = new BlockAlarmLight();
    public static final ItemBlock ITEM_RBMK_ALARM_LIGHT = new ItemBlock(RBMK_ALARM_LIGHT);
    /** v17.6 签发器：1x1 发卡柜台，管理员门禁 + GUI 签发操作员工牌 */
    public static final Block RBMK_ISSUER = new BlockRbmkIssuer();
    public static final ItemBlock ITEM_RBMK_ISSUER = new ItemBlock(RBMK_ISSUER);
    /** v17.7 巡屏中控：绑定至多 4 块屏，GUI 总览 + 自动轮播"谁告警看谁" */
    public static final Block RBMK_PATROL = new BlockRbmkPatrol();
    public static final ItemBlock ITEM_RBMK_PATROL = new ItemBlock(RBMK_PATROL);
    /** v17.9 多堆芯聚合中控：绑定至多 6 块屏按控制台归堆，排行总览 */
    public static final Block RBMK_AGGREGATE = new BlockRbmkAggregate();
    public static final ItemBlock ITEM_RBMK_AGGREGATE = new ItemBlock(RBMK_AGGREGATE);

    // ---- v17.4 real-screen family (mains-gated twins of the eight screens) ----
    public static final Block RBMK_SCREEN_REAL = new BlockRbmkScreenReal();
    public static final ItemBlock ITEM_RBMK_SCREEN_REAL = new ItemBlock(RBMK_SCREEN_REAL);
    public static final Block RBMK_MONITOR_REAL = new BlockRbmkMonitorReal();
    public static final ItemBlock ITEM_RBMK_MONITOR_REAL = new ItemBlock(RBMK_MONITOR_REAL);
    public static final Block RBMK_CUSTOM_REAL = new BlockRbmkCustomScreenReal();
    public static final ItemBlock ITEM_RBMK_CUSTOM_REAL = new ItemBlock(RBMK_CUSTOM_REAL);
    public static final Block RBMK_GRAPH_REAL = new BlockRbmkGraphReal();
    public static final ItemBlock ITEM_RBMK_GRAPH_REAL = new ItemBlock(RBMK_GRAPH_REAL);
    public static final Block RBMK_TERM_SCREEN_REAL = new BlockRbmkTermScreenReal();
    public static final ItemBlock ITEM_RBMK_TERM_SCREEN_REAL = new ItemBlock(RBMK_TERM_SCREEN_REAL);
    public static final Block RBMK_MEDIA_SCREEN_REAL = new BlockRbmkMediaScreenReal();
    public static final ItemBlock ITEM_RBMK_MEDIA_SCREEN_REAL = new ItemBlock(RBMK_MEDIA_SCREEN_REAL);
    public static final Block RBMK_PPT_SCREEN_REAL = new BlockRbmkPptScreenReal();
    public static final ItemBlock ITEM_RBMK_PPT_SCREEN_REAL = new ItemBlock(RBMK_PPT_SCREEN_REAL);
    public static final Block RBMK_TERMINAL_REAL = new BlockRbmkTerminalReal();
    public static final ItemBlock ITEM_RBMK_TERMINAL_REAL = new ItemBlock(RBMK_TERMINAL_REAL);

    // ---- v18.0 cycle screens (auto-cycling pages: overview -> grid -> graph) ----
    public static final Block RBMK_CYCLE_SCREEN = new BlockRbmkCycleScreen();
    public static final ItemBlock ITEM_RBMK_CYCLE_SCREEN = new ItemBlock(RBMK_CYCLE_SCREEN);
    public static final Block RBMK_CYCLE_SCREEN_REAL = new BlockRbmkCycleScreenReal();
    public static final ItemBlock ITEM_RBMK_CYCLE_SCREEN_REAL = new ItemBlock(RBMK_CYCLE_SCREEN_REAL);


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
        event.getRegistry().register(RBMK_MEDIA_SCREEN);
        event.getRegistry().register(RBMK_PPT_SCREEN);
        event.getRegistry().register(RBMK_ALARM_LIGHT);
        event.getRegistry().register(RBMK_ISSUER);
        event.getRegistry().register(RBMK_PATROL);
        event.getRegistry().register(RBMK_AGGREGATE);
        event.getRegistry().register(RBMK_SCREEN_REAL);
        event.getRegistry().register(RBMK_MONITOR_REAL);
        event.getRegistry().register(RBMK_CUSTOM_REAL);
        event.getRegistry().register(RBMK_GRAPH_REAL);
        event.getRegistry().register(RBMK_TERM_SCREEN_REAL);
        event.getRegistry().register(RBMK_MEDIA_SCREEN_REAL);
        event.getRegistry().register(RBMK_PPT_SCREEN_REAL);
        event.getRegistry().register(RBMK_TERMINAL_REAL);
        event.getRegistry().register(RBMK_CYCLE_SCREEN);
        event.getRegistry().register(RBMK_CYCLE_SCREEN_REAL);
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
        event.getRegistry().register(ITEM_RBMK_MEDIA_SCREEN);
        event.getRegistry().register(ITEM_RBMK_PPT_SCREEN);
        event.getRegistry().register(ITEM_RBMK_ALARM_LIGHT);
        event.getRegistry().register(ITEM_RBMK_ISSUER);
        event.getRegistry().register(ITEM_RBMK_PATROL);
        event.getRegistry().register(ITEM_RBMK_AGGREGATE);
        event.getRegistry().register(ITEM_RBMK_SCREEN_REAL);
        event.getRegistry().register(ITEM_RBMK_MONITOR_REAL);
        event.getRegistry().register(ITEM_RBMK_CUSTOM_REAL);
        event.getRegistry().register(ITEM_RBMK_GRAPH_REAL);
        event.getRegistry().register(ITEM_RBMK_TERM_SCREEN_REAL);
        event.getRegistry().register(ITEM_RBMK_MEDIA_SCREEN_REAL);
        event.getRegistry().register(ITEM_RBMK_PPT_SCREEN_REAL);
        event.getRegistry().register(ITEM_RBMK_TERMINAL_REAL);
        event.getRegistry().register(ITEM_RBMK_CYCLE_SCREEN);
        event.getRegistry().register(ITEM_RBMK_CYCLE_SCREEN_REAL);
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
        ITEM_RBMK_MEDIA_SCREEN.setRegistryName(RBMK_MEDIA_SCREEN.getRegistryName());
        ITEM_RBMK_MEDIA_SCREEN.setCreativeTab(TAB_SCREEN);
        ITEM_RBMK_PPT_SCREEN.setRegistryName(RBMK_PPT_SCREEN.getRegistryName());
        ITEM_RBMK_PPT_SCREEN.setCreativeTab(TAB_SCREEN);
        ITEM_RBMK_ALARM_LIGHT.setRegistryName(RBMK_ALARM_LIGHT.getRegistryName());
        ITEM_RBMK_ALARM_LIGHT.setCreativeTab(TAB_SCREEN);
        ITEM_RBMK_ISSUER.setRegistryName(RBMK_ISSUER.getRegistryName());
        ITEM_RBMK_ISSUER.setCreativeTab(TAB_SCREEN);
        ITEM_RBMK_PATROL.setRegistryName(RBMK_PATROL.getRegistryName());
        ITEM_RBMK_PATROL.setCreativeTab(TAB_SCREEN);
        ITEM_RBMK_AGGREGATE.setRegistryName(RBMK_AGGREGATE.getRegistryName());
        ITEM_RBMK_AGGREGATE.setCreativeTab(TAB_SCREEN);

        // v17.4 real screens
        ITEM_RBMK_SCREEN_REAL.setRegistryName(RBMK_SCREEN_REAL.getRegistryName());
        ITEM_RBMK_SCREEN_REAL.setCreativeTab(TAB_SCREEN);
        ITEM_RBMK_MONITOR_REAL.setRegistryName(RBMK_MONITOR_REAL.getRegistryName());
        ITEM_RBMK_MONITOR_REAL.setCreativeTab(TAB_SCREEN);
        ITEM_RBMK_CUSTOM_REAL.setRegistryName(RBMK_CUSTOM_REAL.getRegistryName());
        ITEM_RBMK_CUSTOM_REAL.setCreativeTab(TAB_SCREEN);
        ITEM_RBMK_GRAPH_REAL.setRegistryName(RBMK_GRAPH_REAL.getRegistryName());
        ITEM_RBMK_GRAPH_REAL.setCreativeTab(TAB_SCREEN);
        ITEM_RBMK_TERM_SCREEN_REAL.setRegistryName(RBMK_TERM_SCREEN_REAL.getRegistryName());
        ITEM_RBMK_TERM_SCREEN_REAL.setCreativeTab(TAB_SCREEN);
        ITEM_RBMK_MEDIA_SCREEN_REAL.setRegistryName(RBMK_MEDIA_SCREEN_REAL.getRegistryName());
        ITEM_RBMK_MEDIA_SCREEN_REAL.setCreativeTab(TAB_SCREEN);
        ITEM_RBMK_PPT_SCREEN_REAL.setRegistryName(RBMK_PPT_SCREEN_REAL.getRegistryName());
        ITEM_RBMK_PPT_SCREEN_REAL.setCreativeTab(TAB_SCREEN);
        ITEM_RBMK_TERMINAL_REAL.setRegistryName(RBMK_TERMINAL_REAL.getRegistryName());
        ITEM_RBMK_TERMINAL_REAL.setCreativeTab(TAB_SCREEN);

        // v18.0 cycle screens
        ITEM_RBMK_CYCLE_SCREEN.setRegistryName(RBMK_CYCLE_SCREEN.getRegistryName());
        ITEM_RBMK_CYCLE_SCREEN.setCreativeTab(TAB_SCREEN);
        ITEM_RBMK_CYCLE_SCREEN_REAL.setRegistryName(RBMK_CYCLE_SCREEN_REAL.getRegistryName());
        ITEM_RBMK_CYCLE_SCREEN_REAL.setCreativeTab(TAB_SCREEN);
    }
}

