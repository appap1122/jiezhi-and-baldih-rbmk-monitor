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

@Mod(modid = Tags.MODID, name = Tags.NAME, version = Tags.VERSION)
public class RbmkMonitorMod {
    @Mod.Instance(value = Tags.MODID)
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
        logger.info("RBMK monitor display (derived from JiezhiMod by \u6811\u4e0a\u7684\u8282\u679d) v{} is loading.", (Object)Tags.VERSION);
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
        GameRegistry.registerTileEntity(TileRbmkMonitorScreen.class, (ResourceLocation)new ResourceLocation(Tags.MODID, "rbmk_monitor_screen"));
        GameRegistry.registerTileEntity(TileCustomScreen.class, (ResourceLocation)new ResourceLocation(Tags.MODID, "rbmk_custom"));
        GameRegistry.registerTileEntity(TileRbmkSiren.class, (ResourceLocation)new ResourceLocation(Tags.MODID, "rbmk_siren"));
        GameRegistry.registerTileEntity(com.jiezhirbmkmod.block.TileRbmkTerminal.class, (ResourceLocation)new ResourceLocation(Tags.MODID, "rbmk_terminal"));
        GameRegistry.registerTileEntity(com.jiezhirbmkmod.block.TileRbmkTermScreen.class, (ResourceLocation)new ResourceLocation(Tags.MODID, "rbmk_term_screen"));
        GameRegistry.registerTileEntity(com.jiezhirbmkmod.block.TileRbmkCycleScreen.class, (ResourceLocation)new ResourceLocation(Tags.MODID, "rbmk_cycle_screen"));
        GameRegistry.registerTileEntity(com.jiezhirbmkmod.block.TileRbmkMediaScreen.class, (ResourceLocation)new ResourceLocation(Tags.MODID, "rbmk_media_screen"));
        GameRegistry.registerTileEntity(com.jiezhirbmkmod.block.TileRbmkPptScreen.class, (ResourceLocation)new ResourceLocation(Tags.MODID, "rbmk_ppt_screen"));
        GameRegistry.registerTileEntity(com.jiezhirbmkmod.block.TileAlarmLight.class, (ResourceLocation)new ResourceLocation(Tags.MODID, "rbmk_alarm_light"));
        GameRegistry.registerTileEntity(com.jiezhirbmkmod.block.TileRbmkIssuer.class, (ResourceLocation)new ResourceLocation(Tags.MODID, "rbmk_issuer"));
        // v17.7 巡屏中控 + 审计命令（/rbmkaudit）
        GameRegistry.registerTileEntity(com.jiezhirbmkmod.block.TileRbmkPatrol.class, (ResourceLocation)new ResourceLocation(Tags.MODID, "rbmk_patrol"));
        // v17.9 多堆芯聚合中控
        GameRegistry.registerTileEntity(com.jiezhirbmkmod.block.TileRbmkAggregate.class, (ResourceLocation)new ResourceLocation(Tags.MODID, "rbmk_aggregate"));
        MinecraftForge.EVENT_BUS.register((Object)new com.jiezhirbmkmod.dfc.AuditCommand());
        // v17.7: 工牌吊销命令 /rbmkrevoke —— 与审计命令同款注册方式
        MinecraftForge.EVENT_BUS.register((Object)new com.jiezhirbmkmod.dfc.RevokeCommand());
        // v17.9 黑客玩法二期：反向追踪 /rbmktrace
        MinecraftForge.EVENT_BUS.register((Object)new com.jiezhirbmkmod.dfc.TraceCommand());
        // v17.9 兼容探针 /rbmkcompat（纯只读，任何玩家可用）
        MinecraftForge.EVENT_BUS.register((Object)new com.jiezhirbmkmod.dfc.CompatCommand());
        net.minecraftforge.fml.common.registry.EntityRegistry.registerModEntity(
                (ResourceLocation)new ResourceLocation(Tags.MODID, "flying_block"),
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
     * v17.9 网络握手回调（/rbmkcompat 的数据源）；v18.3 改回「缺失放行」。
     *
     * <p><b>判定规则（v18.3 起）：</b></p>
     * <ul>
     *   <li>对端<b>没有装</b>本模组（{@code remoteMods} 里没有
     *       {@link Tags#MODID}，或整个 map 为 null）→ <b>返回 true，放行</b>。
     *       本模组的所有玩法都是自包含的：vanilla 服、只装别的模组的客户端
     *       本来就不该被一个纯装饰/监控模组踢下线。放行后本模组的功能
     *       在对方那一侧不存在，本端照样能跑（屏幕只对自己显示，命令只
     *       处理本端数据）。</li>
     *   <li>对端<b>装了但版本不符</b> → <b>返回 false，拒绝</b>。包表/线上
     *       格式按大版本强制对齐（见 {@code ModNetwork.PROTOCOL}），混连
     *       会在解码阶段炸掉，必须在握手阶段挡住。</li>
     *   <li>版本一致 → 放行。</li>
     * </ul>
     *
     * <p>v18.0 曾收紧成「双侧对称：必须存在且版本一致」，代价是连 vanilla
     * 服、或在纯净客户端上进原版服直接报 "mod rejections"，玩家看不懂。
     * v18.3 改回 Forge 的常规做法：缺失即放行，只有真正的协议冲突才拒绝。
     * 放行带来的「对端没有 channel」风险由 {@code NetGuard} 兜底 ——
     * 所有收发包都过它，异常只记日志不冒泡（见该类的注释）。</p>
     *
     * <p>变化记录照旧：无论接受与否，对端版本都记进
     * {@link ProtocolGuard}，版本不符被拒时把精确原因写进控制台与审计链，
     * /rbmkcompat 能把它翻译成人话。</p>
     *
     * <p>注意：本方法在握手期被调用，双端各一次（ping 查询也会触发）；
     * 此时不许碰世界与服务器状态，只许做记录。</p>
     */
    @net.minecraftforge.fml.common.network.NetworkCheckHandler
    public boolean onNetworkCheck(java.util.Map<String, String> remoteMods,
            net.minecraftforge.fml.relauncher.Side remoteSide) {
        boolean remoteIsServer = remoteSide == net.minecraftforge.fml.relauncher.Side.SERVER;
        String v = remoteMods == null ? null : remoteMods.get(Tags.MODID);
        boolean present = v != null;
        // v18.3：缺失放行，只有"装了但对不上版本"才拒绝。
        boolean ok = !present || Tags.VERSION.equals(v);
        com.jiezhirbmkmod.dfc.ProtocolGuard.record(v, remoteIsServer, ok);
        if (!ok) {
            com.jiezhirbmkmod.dfc.ProtocolGuard.logReject(v, remoteIsServer);
        }
        return ok;
    }
}

