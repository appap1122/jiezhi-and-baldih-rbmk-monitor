package com.jiezhirbmkmod;

import com.jiezhirbmkmod.block.ModBlocks;
import com.jiezhirbmkmod.block.TileCustomScreen;
import com.jiezhirbmkmod.block.TileRbmkSiren;
import com.jiezhirbmkmod.block.TileRbmkMonitorScreen;
import com.jiezhirbmkmod.item.GravityCoilEffect;
import com.jiezhirbmkmod.item.HealingCoilEffect;
import com.jiezhirbmkmod.item.JetpackEffect;
import com.jiezhirbmkmod.item.JetpackLogoutHandler;
import com.jiezhirbmkmod.item.ModItems;
import com.jiezhirbmkmod.item.SpeedCoilEffect;
import com.jiezhirbmkmod.network.ModNetwork;
import com.jiezhirbmkmod.proxy.CommonProxy;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.network.IGuiHandler;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.registry.GameRegistry;
import org.apache.logging.log4j.Logger;

@Mod(modid="jiezhirbmkmod", name="RBMK\u663e\u793a\u5c4f", version="13.1-beta")
public class RbmkMonitorMod {
    public static final String MODID = "jiezhirbmkmod";
    public static final String NAME = "RBMK\u663e\u793a\u5c4f";
    public static final String VERSION = "13.1-beta";
    @Mod.Instance(value="jiezhirbmkmod")
    public static RbmkMonitorMod instance;
    public static Logger logger;
    @SidedProxy(clientSide="com.jiezhirbmkmod.proxy.RbmkClientProxy", serverSide="com.jiezhirbmkmod.proxy.CommonProxy")
    public static CommonProxy proxy;

    public RbmkMonitorMod() {
        instance = this;
    }

    public static void warn(String message, Object ... args) {
        if (logger != null) {
            logger.warn(message, args);
        }
    }

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        logger = event.getModLog();
        logger.info("RBMK monitor display (derived from JiezhiMod by \u6811\u4e0a\u7684\u8282\u679d) v{} is loading.", (Object)VERSION);
        com.jiezhirbmkmod.dfc.RbmkConfig.load(event.getModConfigurationDirectory());
        ModNetwork.init();
        MinecraftForge.EVENT_BUS.register((Object)new ModBlocks());
        MinecraftForge.EVENT_BUS.register((Object)new ModItems());
        MinecraftForge.EVENT_BUS.register((Object)new SpeedCoilEffect());
        MinecraftForge.EVENT_BUS.register((Object)new GravityCoilEffect());
        MinecraftForge.EVENT_BUS.register((Object)new HealingCoilEffect());
        MinecraftForge.EVENT_BUS.register((Object)new JetpackEffect());
        MinecraftForge.EVENT_BUS.register((Object)new JetpackLogoutHandler());
        GameRegistry.registerTileEntity(TileRbmkMonitorScreen.class, (ResourceLocation)new ResourceLocation(MODID, "rbmk_monitor_screen"));
        GameRegistry.registerTileEntity(TileCustomScreen.class, (ResourceLocation)new ResourceLocation(MODID, "rbmk_custom"));
        GameRegistry.registerTileEntity(TileRbmkSiren.class, (ResourceLocation)new ResourceLocation(MODID, "rbmk_siren"));
        GameRegistry.registerTileEntity(com.jiezhirbmkmod.block.TileRbmkTerminal.class, (ResourceLocation)new ResourceLocation(MODID, "rbmk_terminal"));
        GameRegistry.registerTileEntity(com.jiezhirbmkmod.block.TileRbmkTermScreen.class, (ResourceLocation)new ResourceLocation(MODID, "rbmk_term_screen"));
        net.minecraftforge.fml.common.registry.EntityRegistry.registerModEntity(
                (ResourceLocation)new ResourceLocation(MODID, "flying_block"),
                com.jiezhirbmkmod.entity.EntityFlyingBlock.class,
                "flying_block", 0, (Object)instance, 64, 1, true);
        NetworkRegistry.INSTANCE.registerGuiHandler((Object)instance, (IGuiHandler)proxy);
        ModNetwork.CHANNEL.registerMessage(
                TileRbmkMonitorScreen.AutoScramToggleMessage.Handler.class,
                TileRbmkMonitorScreen.AutoScramToggleMessage.class, 8,
                net.minecraftforge.fml.relauncher.Side.SERVER);
        ModNetwork.CHANNEL.registerMessage(
                TileRbmkMonitorScreen.HackToggleMessage.Handler.class,
                TileRbmkMonitorScreen.HackToggleMessage.class, 9,
                net.minecraftforge.fml.relauncher.Side.SERVER);
        ModNetwork.CHANNEL.registerMessage(
                TileRbmkMonitorScreen.ConnectMessage.Handler.class,
                TileRbmkMonitorScreen.ConnectMessage.class, 10,
                net.minecraftforge.fml.relauncher.Side.SERVER);
        ModNetwork.CHANNEL.registerMessage(
                com.jiezhirbmkmod.dfc.SteamExplosionHandler.ShakeMessage.Handler.class,
                com.jiezhirbmkmod.dfc.SteamExplosionHandler.ShakeMessage.class, 11,
                net.minecraftforge.fml.relauncher.Side.CLIENT);
        proxy.preInit();
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        proxy.init();
    }
}

