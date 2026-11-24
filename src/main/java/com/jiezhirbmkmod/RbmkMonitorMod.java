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

@Mod(modid="jiezhirbmkmod", name="RBMK\u663e\u793a\u5c4f", version="18.1")
public class RbmkMonitorMod {
    public static final String MODID = "jiezhirbmkmod";
    public static final String NAME = "RBMK\u663e\u793a\u5c4f";
    public static final String VERSION = "18.1";
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
        // v17.4.1: records the RBMK console before its own onBlockActivated can
        // swallow the click, which is what made the remote "unable to bind"
        MinecraftForge.EVENT_BUS.register((Object)new com.jiezhirbmkmod.item.RemoteBindHandler());
        // v17.6: 黑客攻击玩法开关审批状态机（超时/掉线检查挂在 server tick 上）
        MinecraftForge.EVENT_BUS.register((Object)new com.jiezhirbmkmod.dfc.HackAppManager());
        GameRegistry.registerTileEntity(TileRbmkMonitorScreen.class, (ResourceLocation)new ResourceLocation(MODID, "rbmk_monitor_screen"));
        GameRegistry.registerTileEntity(TileCustomScreen.class, (ResourceLocation)new ResourceLocation(MODID, "rbmk_custom"));
        GameRegistry.registerTileEntity(TileRbmkSiren.class, (ResourceLocation)new ResourceLocation(MODID, "rbmk_siren"));
        GameRegistry.registerTileEntity(com.jiezhirbmkmod.block.TileRbmkTerminal.class, (ResourceLocation)new ResourceLocation(MODID, "rbmk_terminal"));
        GameRegistry.registerTileEntity(com.jiezhirbmkmod.block.TileRbmkTermScreen.class, (ResourceLocation)new ResourceLocation(MODID, "rbmk_term_screen"));
        GameRegistry.registerTileEntity(com.jiezhirbmkmod.block.TileRbmkCycleScreen.class, (ResourceLocation)new ResourceLocation(MODID, "rbmk_cycle_screen"));
        GameRegistry.registerTileEntity(com.jiezhirbmkmod.block.TileRbmkMediaScreen.class, (ResourceLocation)new ResourceLocation(MODID, "rbmk_media_screen"));
        GameRegistry.registerTileEntity(com.jiezhirbmkmod.block.TileRbmkPptScreen.class, (ResourceLocation)new ResourceLocation(MODID, "rbmk_ppt_screen"));
        GameRegistry.registerTileEntity(com.jiezhirbmkmod.block.TileAlarmLight.class, (ResourceLocation)new ResourceLocation(MODID, "rbmk_alarm_light"));
        GameRegistry.registerTileEntity(com.jiezhirbmkmod.block.TileRbmkIssuer.class, (ResourceLocation)new ResourceLocation(MODID, "rbmk_issuer"));
        // v17.7 巡屏中控 + 审计命令（/rbmkaudit）
        GameRegistry.registerTileEntity(com.jiezhirbmkmod.block.TileRbmkPatrol.class, (ResourceLocation)new ResourceLocation(MODID, "rbmk_patrol"));
        // v17.9 多堆芯聚合中控
        GameRegistry.registerTileEntity(com.jiezhirbmkmod.block.TileRbmkAggregate.class, (ResourceLocation)new ResourceLocation(MODID, "rbmk_aggregate"));
        MinecraftForge.EVENT_BUS.register((Object)new com.jiezhirbmkmod.dfc.AuditCommand());
        // v17.7: 工牌吊销命令 /rbmkrevoke —— 与审计命令同款注册方式
        MinecraftForge.EVENT_BUS.register((Object)new com.jiezhirbmkmod.dfc.RevokeCommand());
        // v17.9 黑客玩法二期：反向追踪 /rbmktrace
        MinecraftForge.EVENT_BUS.register((Object)new com.jiezhirbmkmod.dfc.TraceCommand());
        // v17.9 兼容探针 /rbmkcompat（纯只读，任何玩家可用）
        MinecraftForge.EVENT_BUS.register((Object)new com.jiezhirbmkmod.dfc.CompatCommand());
        net.minecraftforge.fml.common.registry.EntityRegistry.registerModEntity(
                (ResourceLocation)new ResourceLocation(MODID, "flying_block"),
                com.jiezhirbmkmod.entity.EntityFlyingBlock.class,
                "flying_block", 0, (Object)instance, 64, 1, true);
        NetworkRegistry.INSTANCE.registerGuiHandler((Object)instance, (IGuiHandler)proxy);
        // v17.8 网络包瘦身：id 8..11 的注册已收编进 ModNetwork.init（见那张表）
        proxy.preInit();
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        proxy.init();
    }

    /**
     * v17.9 网络握手回调（/rbmkcompat 的数据源）；v18.0 收紧为双侧对称。
     *
     * <p>v18.0 之前判定是不对称的：对端是服务器时无条件放行（判定权全在
     * 服务器），只有"客户端来连"一侧要求版本一致。这在标准 Forge 服下
     * 安全（旧服或 Forge 默认校验总会拒），但留了两条真缝隙：旧服判定
     * 宽松、或 mod 列表被代理（BungeeCord 等）改写时，两侧包表不同的
     * 连接会真握手成功，第一发同步包就是解码崩溃，而不是干净的握手
     * 拒绝；客户端连没装本模组的 Forge 服则报 "missing channel" 这种
     * 玩家看不懂的错。现在收紧：<b>无论本端是哪一侧，都要求对端存在
     * 本模组且版本与本端完全一致</b> —— 这正是 Forge 对未提供
     * {@code @NetworkCheckHandler} 的模组的默认语义（此前客户端侧的
     * 无条件放行反而是放宽）。版本不等的服务器在多人列表 ping 阶段就
     * 标红、连接发起前就被本端拒绝，不再把判定权押给对端。</p>
     *
     * <p>变化记录照旧：无论接受与否，对端版本都记进
     * {@link ProtocolGuard}，拒绝时把精确原因写进控制台与审计链，
     * /rbmkcompat 能把它翻译成人话。</p>
     *
     * <p>注意：本方法在握手期被调用，双端各一次（ping 查询也会触发）；
     * 此时不许碰世界与服务器状态，只许做记录。</p>
     */
    @net.minecraftforge.fml.common.network.NetworkCheckHandler
    public boolean onNetworkCheck(java.util.Map<String, String> remoteMods,
            net.minecraftforge.fml.relauncher.Side remoteSide) {
        boolean remoteIsServer = remoteSide == net.minecraftforge.fml.relauncher.Side.SERVER;
        String v = remoteMods == null ? null : remoteMods.get(MODID);
        // v18.0 收紧：去掉客户端侧的无条件放行。单机/局域网同版本场景
        // 不受影响（对端版本必相等）；vanilla 服与版本不同的服在客户端
        // 就被拒，报错发生在玩家自己的屏幕上，可读。
        boolean ok = v != null && VERSION.equals(v);
        com.jiezhirbmkmod.dfc.ProtocolGuard.record(v, remoteIsServer, ok);
        if (!ok) {
            com.jiezhirbmkmod.dfc.ProtocolGuard.logReject(v, remoteIsServer);
        }
        return ok;
    }
}

