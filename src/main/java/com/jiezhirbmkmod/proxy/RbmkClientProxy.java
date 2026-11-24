package com.jiezhirbmkmod.proxy;

import com.jiezhirbmkmod.RbmkMonitorMod;
import com.jiezhirbmkmod.block.ContainerCustomScreen;
import com.jiezhirbmkmod.block.ModBlocks;
import com.jiezhirbmkmod.block.TileCustomScreen;
import com.jiezhirbmkmod.block.TileRbmkMonitorScreen;
import com.jiezhirbmkmod.block.TileRbmkSiren;
import com.jiezhirbmkmod.block.TileRbmkTerminal;
import com.jiezhirbmkmod.client.ClientJetRenderer;
import com.jiezhirbmkmod.client.JetHeatBarRenderer;
import com.jiezhirbmkmod.client.dfc.RenderRbmkMonitorScreen;
import com.jiezhirbmkmod.client.gui.ClientShake;
import com.jiezhirbmkmod.client.gui.GuiAlertSettings;
import com.jiezhirbmkmod.client.gui.GuiCustomScreen;
import com.jiezhirbmkmod.item.ModItems;
import com.jiezhirbmkmod.proxy.CommonProxy;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

public class RbmkClientProxy
extends CommonProxy {
    /** v15.2: JavaCV prompt is shown at most once per world join */
    private boolean mediaPromptChecked;
    @Override
    public void preInit() {
        MinecraftForge.EVENT_BUS.register((Object)this);
        MinecraftForge.EVENT_BUS.register((Object)new ClientShake());
        // v17.8: 两条光照路径**同时注册**，按每盏灯的 spotMode 分派
        //   （见 TileAlarmLight#usesSss）。以前是启动时二选一，改设置要重启；
        //   现在面板上点一下当场换。
        //   几何一侧不用付代价：DepthDebugPass 在收集到的光源数为 0 时直接 return，
        //   全几何模式连一次全屏 GLSL 都不会跑。
        com.jiezhirbmkmod.client.render.DepthDebugPass.ENABLED = true;
        com.jiezhirbmkmod.client.render.DepthDebugPass.registerIfEnabled();
        MinecraftForge.EVENT_BUS.register(com.jiezhirbmkmod.client.render.AlarmLightVolume.class);
        com.jiezhirbmkmod.client.KeyBindings.register();
    }

    @Override
    public void init() {
        // v17.0: drop the bundled OST into .minecraft/rbmk_media so the media
        // screen can play it without the player hunting for files
        com.jiezhirbmkmod.client.media.MediaLibrary.installBundledOst();
        MinecraftForge.EVENT_BUS.register(new com.jiezhirbmkmod.client.KeyInputHandler());
        // v17.7 曲线屏滚轮回放（无 GUI 时盯着曲线屏滚动即倒带）
        MinecraftForge.EVENT_BUS.register(new com.jiezhirbmkmod.client.GraphReplayWheel());
        // v18.0 曲线历史落盘：客户端 tick 定时批量写盘 + 世界卸载全量落盘
        // （事件处理器是静态方法，直接注册类对象，与 AlarmLightVolume 同款）
        MinecraftForge.EVENT_BUS.register(com.jiezhirbmkmod.client.HistoryStore.class);
        // v17.9 曲线历史导出（客户端聊天命令 /rbmkexport csv|png）
        net.minecraftforge.client.ClientCommandHandler.instance.registerCommand(
                new com.jiezhirbmkmod.client.RbmkExportCommand());
        ClientRegistry.bindTileEntitySpecialRenderer(TileRbmkMonitorScreen.class, (TileEntitySpecialRenderer)new RenderRbmkMonitorScreen());
        ClientRegistry.bindTileEntitySpecialRenderer(com.jiezhirbmkmod.block.TileRbmkMediaScreen.class,
                (TileEntitySpecialRenderer)new com.jiezhirbmkmod.client.dfc.RenderRbmkMediaScreen());
        ClientRegistry.bindTileEntitySpecialRenderer(com.jiezhirbmkmod.block.TileAlarmLight.class,
                (TileEntitySpecialRenderer)new com.jiezhirbmkmod.client.render.RenderAlarmLight());
        // the terminal wall display is its own TE subclass, give it the same renderer
        ClientRegistry.bindTileEntitySpecialRenderer(com.jiezhirbmkmod.block.TileRbmkTermScreen.class, (TileEntitySpecialRenderer)new RenderRbmkMonitorScreen());
        net.minecraftforge.fml.client.registry.RenderingRegistry.registerEntityRenderingHandler(
                com.jiezhirbmkmod.entity.EntityFlyingBlock.class,
                new net.minecraftforge.fml.client.registry.IRenderFactory<com.jiezhirbmkmod.entity.EntityFlyingBlock>() {
                    @Override
                    public net.minecraft.client.renderer.entity.Render<? super com.jiezhirbmkmod.entity.EntityFlyingBlock> createRenderFor(
                            net.minecraft.client.renderer.entity.RenderManager manager) {
                        return new net.minecraft.client.renderer.entity.RenderFallingBlock(manager);
                    }
                });
    }

    @Override
    public Object getClientGuiElement(int id, EntityPlayer player, World world, int x, int y, int z) {
        TileEntity te;
        if (id == 1 && (te = world.getTileEntity(new BlockPos(x, y, z))) instanceof TileCustomScreen) {
            TileCustomScreen tile = (TileCustomScreen)te;
            ContainerCustomScreen container = new ContainerCustomScreen(player.inventory, tile);
            return new GuiCustomScreen(tile, container);
        }
        if (id == 2) {
            TileEntity t2 = world.getTileEntity(new BlockPos(x, y, z));
            if (t2 instanceof TileRbmkMonitorScreen) {
                return new GuiAlertSettings((TileRbmkMonitorScreen) t2);
            }
        }
        if (id == 3) {
            TileEntity t3 = world.getTileEntity(new BlockPos(x, y, z));
            if (t3 instanceof TileRbmkSiren) {
                return new com.jiezhirbmkmod.client.gui.GuiRbmkSiren((TileRbmkSiren) t3);
            }
        }
        if (id == 4) {
            TileEntity t4 = world.getTileEntity(new BlockPos(x, y, z));
            if (t4 instanceof TileRbmkTerminal || t4 instanceof com.jiezhirbmkmod.block.TileRbmkTermScreen) {
                return new com.jiezhirbmkmod.client.gui.GuiTerminal(new BlockPos(x, y, z));
            }
        }
        if (id == 5) {
            // portable laptop: host is the held item, not a tile entity
            net.minecraft.item.ItemStack held = com.jiezhirbmkmod.item.ItemRbmkLaptop.findHeld(player);
            if (held.getItem() instanceof com.jiezhirbmkmod.item.ItemRbmkLaptop) {
                return new com.jiezhirbmkmod.client.gui.GuiTerminal(com.jiezhirbmkmod.client.gui.GuiTerminal.LAPTOP_HOST);
            }
        }
        if (id == CommonProxy.GUI_ISSUER) {
            TileEntity te8 = world.getTileEntity(new BlockPos(x, y, z));
            if (te8 instanceof com.jiezhirbmkmod.block.TileRbmkIssuer) {
                return new com.jiezhirbmkmod.client.gui.GuiRbmkIssuer(
                        (com.jiezhirbmkmod.block.TileRbmkIssuer) te8);
            }
        }
        // v17.6 黑客攻击玩法申请选择界面（开关状态由窗口属性同步，无需 Tile）
        if (id == CommonProxy.GUI_HACK_APP) {
            return new com.jiezhirbmkmod.client.gui.GuiHackAppConfirm();
        }
        // v17.7 巡屏中控：绑定清单已在 openGui 前经 MessagePatrolSync 下发
        if (id == CommonProxy.GUI_PATROL) {
            return new com.jiezhirbmkmod.client.gui.GuiRbmkPatrol();
        }
        // v17.9 多堆芯聚合中控：归属表已在 openGui 前经 MessageAggregateSync 下发
        if (id == CommonProxy.GUI_AGGREGATE) {
            return new com.jiezhirbmkmod.client.gui.GuiRbmkAggregate();
        }
        if (id == CommonProxy.GUI_ALARM_LIGHT) {
            TileEntity te7 = world.getTileEntity(new BlockPos(x, y, z));
            if (te7 instanceof com.jiezhirbmkmod.block.TileAlarmLight) {
                return new com.jiezhirbmkmod.client.gui.GuiAlarmLightPanel(
                        (com.jiezhirbmkmod.block.TileAlarmLight) te7);
            }
        }
        return null;
    }

    @SubscribeEvent
    public void onModelRegistry(ModelRegistryEvent event) {
        Item customItem;
        Item graphItem;
        Item monitorItem;
        Item routerItem;
        Item screenItem = Item.getItemFromBlock((Block)ModBlocks.RBMK_SCREEN);
        if (screenItem != null) {
            this.bindItemModel(screenItem, "jiezhirbmkmod:rbmk_screen");
        }
        if ((monitorItem = Item.getItemFromBlock((Block)ModBlocks.RBMK_MONITOR)) != null) {
            this.bindItemModel(monitorItem, "jiezhirbmkmod:rbmk_monitor");
        }
        if ((customItem = Item.getItemFromBlock((Block)ModBlocks.RBMK_CUSTOM)) != null) {
            this.bindItemModel(customItem, "jiezhirbmkmod:rbmk_custom");
        }
        if ((graphItem = Item.getItemFromBlock((Block)ModBlocks.RBMK_GRAPH)) != null) {
            this.bindItemModel(graphItem, "jiezhirbmkmod:rbmk_graph");
        }
        if (Item.getItemFromBlock((Block)ModBlocks.RBMK_TERM_SCREEN) != null) {
            this.bindItemModel(Item.getItemFromBlock((Block)ModBlocks.RBMK_TERM_SCREEN), "jiezhirbmkmod:rbmk_term_screen");
        }
        if (Item.getItemFromBlock((Block)ModBlocks.RBMK_TERMINAL) != null) {
            this.bindItemModel(Item.getItemFromBlock((Block)ModBlocks.RBMK_TERMINAL), "jiezhirbmkmod:rbmk_terminal");
        }
        if ((routerItem = Item.getItemFromBlock((Block)ModBlocks.RBMK_ROUTER)) != null) {
            this.bindItemModel(routerItem, "jiezhirbmkmod:rbmk_router");
        }
        if (Item.getItemFromBlock((Block)ModBlocks.RBMK_PPT_SCREEN) != null) {
            this.bindItemModel(Item.getItemFromBlock((Block)ModBlocks.RBMK_PPT_SCREEN), "jiezhirbmkmod:rbmk_ppt_screen");
        }
        if (Item.getItemFromBlock((Block)ModBlocks.RBMK_MEDIA_SCREEN) != null) {
            this.bindItemModel(Item.getItemFromBlock((Block)ModBlocks.RBMK_MEDIA_SCREEN), "jiezhirbmkmod:rbmk_media_screen");
        }
        // ★ v17.10：报警灯原来被塞在上面那个 media_screen 的 if 里 —— 两块风马牛不相及
        //   的方块共用一个判空条件。media 屏哪天取不到 ItemBlock，报警灯就跟着不绑，
        //   物品栏直接变成紫黑 missing model，而且因为世界里的灯是 TESR、看着完全正常，
        //   这种问题极难联想到"某块屏没注册上"。现在单独一行，谁也不牵连谁。
        this.bindItemModel(ModBlocks.ITEM_RBMK_ALARM_LIGHT, "jiezhirbmkmod:rbmk_alarm_light");

        // v17.6 只读操作手册：贴图直接用原版成书纹理（minecraft:items/book_written），
        // 不做新贴图 —— 它长得像原版书就是设计要求，看起来越普通越像"正式文件"。
        this.bindItemModel(ModItems.RBMK_MANUAL, "jiezhirbmkmod:rbmk_manual");
        // v17.6 卷二（应急规程）、配置拷贝卡、手持检测仪、告警扩音器
        this.bindItemModel(ModItems.RBMK_MANUAL_VOL2, "jiezhirbmkmod:rbmk_manual_vol2");
        this.bindItemModel(ModItems.RBMK_CONFIG_CARD, "jiezhirbmkmod:rbmk_config_card");
        this.bindItemModel(ModItems.RBMK_PROBE, "jiezhirbmkmod:rbmk_probe");
        this.bindItemModel(ModItems.RBMK_SIREN_HORN, "jiezhirbmkmod:rbmk_siren_horn");
        this.bindItemModel(ModItems.RBMK_OP_CARD, "jiezhirbmkmod:rbmk_op_card");
        // v17.8 配方模板卡（金色系新贴图）+ 手册卷三（沿用原版成书纹理）
        this.bindItemModel(ModItems.RBMK_RECIPE_CARD, "jiezhirbmkmod:rbmk_recipe_card");
        this.bindItemModel(ModItems.RBMK_MANUAL_VOL3, "jiezhirbmkmod:rbmk_manual_vol3");
        // v17.6 签发器（方块物品）
        this.bindItemModel(Item.getItemFromBlock((Block)ModBlocks.RBMK_ISSUER), "jiezhirbmkmod:rbmk_issuer");
        this.bindItemModel(Item.getItemFromBlock((Block)ModBlocks.RBMK_PATROL), "jiezhirbmkmod:rbmk_patrol");
        // v17.9 多堆芯聚合中控（方块物品）—— 单独一行，不与任何 if 牵连
        this.bindItemModel(Item.getItemFromBlock((Block)ModBlocks.RBMK_AGGREGATE), "jiezhirbmkmod:rbmk_aggregate");
        // v18.0 画面轮播显示屏（普通/真实）
        this.bindItemModel(Item.getItemFromBlock((Block)ModBlocks.RBMK_CYCLE_SCREEN), "jiezhirbmkmod:rbmk_cycle_screen");
        this.bindItemModel(Item.getItemFromBlock((Block)ModBlocks.RBMK_CYCLE_SCREEN_REAL), "jiezhirbmkmod:rbmk_cycle_screen_real");

        // v17.4 real-screen family. The block item models are registered
        // explicitly (same as the eight originals above), otherwise the real
        // screens would show the purple missing model in the inventory even
        // though their world rendering is fine.
        String[] realNames = new String[]{
                "rbmk_screen_real", "rbmk_monitor_real", "rbmk_custom_real",
                "rbmk_graph_real", "rbmk_term_screen_real", "rbmk_media_screen_real",
                "rbmk_ppt_screen_real", "rbmk_terminal_real"};
        Block[] realBlocks = new Block[]{
                ModBlocks.RBMK_SCREEN_REAL, ModBlocks.RBMK_MONITOR_REAL,
                ModBlocks.RBMK_CUSTOM_REAL, ModBlocks.RBMK_GRAPH_REAL,
                ModBlocks.RBMK_TERM_SCREEN_REAL, ModBlocks.RBMK_MEDIA_SCREEN_REAL,
                ModBlocks.RBMK_PPT_SCREEN_REAL, ModBlocks.RBMK_TERMINAL_REAL};
        for (int i = 0; i < realNames.length; ++i) {
            Item realItem = Item.getItemFromBlock(realBlocks[i]);
            if (realItem != null) {
                this.bindItemModel(realItem, "jiezhirbmkmod:" + realNames[i]);
            }
        }
        this.bindItemModel(ModItems.RBMK_LAPTOP, "jiezhirbmkmod:rbmk_laptop");
        this.bindItemModel(ModItems.RBMK_REMOTE, "jiezhirbmkmod:rbmk_remote");
        this.bindItemModel(ModItems.RBMK_PPT_REMOTE, "jiezhirbmkmod:rbmk_ppt_remote");
        this.bindItemModel(ModItems.RBMK_REFRESH_TUNER, "jiezhirbmkmod:rbmk_refresh_tuner");
        this.bindItemModel(ModItems.RBMK_REFRESH_TUNER_BASIC, "jiezhirbmkmod:rbmk_refresh_tuner_basic");
        this.bindItemModel(ModItems.SPEED_COIL, "jiezhirbmkmod:speed_coil");
        this.bindItemModel(ModItems.GRAVITY_COIL, "jiezhirbmkmod:gravity_coil");
        this.bindItemModel(ModItems.HEALING_COIL, "jiezhirbmkmod:healing_coil");
        this.bindItemModel(ModItems.JETPACK, "jiezhirbmkmod:jetpack");
        this.bindItemModel(ModItems.JET_SWITCH, "jiezhirbmkmod:jet_switch");
        this.bindSirenColors();
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        ClientJetRenderer.onClientTick(mc);
        this.checkMediaPrompt(mc);
    }

    /** one-shot JavaCV check per world join, never interrupts an open GUI */
    private void checkMediaPrompt(Minecraft mc) {
        if (mc == null || mc.player == null || mc.world == null) {
            this.mediaPromptChecked = false;
            return;
        }
        if (this.mediaPromptChecked || mc.currentScreen != null) {
            return;
        }
        this.mediaPromptChecked = true;
        try {
            if (com.jiezhirbmkmod.client.gui.GuiMediaInstallPrompt.shouldPrompt()) {
                mc.displayGuiScreen(new com.jiezhirbmkmod.client.gui.GuiMediaInstallPrompt());
            }
        }
        catch (Throwable t) {
            RbmkMonitorMod.warn("[RBMK-Monitor] media prompt failed: {}", t);
        }
    }

    @SubscribeEvent
    public void onRenderGameOverlay(RenderGameOverlayEvent.Post event) {
        if (event.getType() != RenderGameOverlayEvent.ElementType.ALL) {
            return;
        }
        JetHeatBarRenderer.draw(Minecraft.getMinecraft());
    }

    private void bindSirenColors() {
        Item sirenItem = Item.getItemFromBlock((Block) ModBlocks.RBMK_SIREN);
        if (sirenItem == null) {
            return;
        }
        net.minecraft.item.EnumDyeColor[] all = net.minecraft.item.EnumDyeColor.values();
        for (int i = 0; i < all.length; ++i) {
            this.bindItemModel(sirenItem, "jiezhirbmkmod:rbmk_siren_" + all[i].getName());
        }
        for (int i = 0; i < all.length; ++i) {
            try {
                net.minecraftforge.client.model.ModelLoader.setCustomModelResourceLocation(sirenItem, i,
                        new ModelResourceLocation("jiezhirbmkmod:rbmk_siren_" + all[i].getName(), "inventory"));
            }
            catch (RuntimeException e) {
                RbmkMonitorMod.warn("Failed to bind siren model {} for meta {}", all[i].getName(), Integer.valueOf(i));
            }
        }
    }

    private void bindItemModel(Item item, String modelPath) {
        try {
            if (item == null) {
                return;
            }
            ModelLoader.setCustomModelResourceLocation((Item)item, (int)0, (ModelResourceLocation)new ModelResourceLocation(modelPath, "inventory"));
        }
        catch (RuntimeException e) {
            RbmkMonitorMod.warn("Failed to bind item model {} for {}", modelPath, String.valueOf(item));
        }
    }
}

