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
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.client.renderer.block.statemap.IStateMapper;
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
    @Override
    public void preInit() {
        MinecraftForge.EVENT_BUS.register((Object)this);
        MinecraftForge.EVENT_BUS.register((Object)new ClientShake());
    }

    @Override
    public void init() {
        ClientRegistry.bindTileEntitySpecialRenderer(TileRbmkMonitorScreen.class, (TileEntitySpecialRenderer)new RenderRbmkMonitorScreen());
        // the terminal wall display is its own TE class, give it the same renderer
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
        // router block borrows the vanilla iron-block model, no extra assets needed
        ModelLoader.setCustomModelResourceLocation(Item.getItemFromBlock(ModBlocks.RBMK_ROUTER), 0,
                new ModelResourceLocation("minecraft:iron_block", "inventory"));
        ModelLoader.setCustomStateMapper(ModBlocks.RBMK_ROUTER,
                new IStateMapper() {
                    @Override
                    public java.util.Map<IBlockState, ModelResourceLocation> putStateModelLocations(Block blockIn) {
                        java.util.Map<IBlockState, ModelResourceLocation> map = new java.util.HashMap<IBlockState, ModelResourceLocation>();
                        for (IBlockState state : blockIn.getBlockState().getValidStates()) {
                            map.put(state, new ModelResourceLocation("minecraft:iron_block", "normal"));
                        }
                        return map;
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
        return null;
    }

    @SubscribeEvent
    public void onModelRegistry(ModelRegistryEvent event) {
        Item customItem;
        Item graphItem;
        Item monitorItem;
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
        this.bindItemModel(ModItems.RBMK_LAPTOP, "jiezhirbmkmod:rbmk_laptop");
        this.bindItemModel(ModItems.RBMK_REMOTE, "jiezhirbmkmod:rbmk_remote");
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
        ClientJetRenderer.onClientTick(Minecraft.getMinecraft());
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

