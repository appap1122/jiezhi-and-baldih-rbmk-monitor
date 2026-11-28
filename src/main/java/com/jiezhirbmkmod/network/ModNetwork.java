package com.jiezhirbmkmod.network;

import com.jiezhirbmkmod.block.TileRbmkMonitorScreen;
import com.jiezhirbmkmod.network.msg.MessageAlarmLight;
import com.jiezhirbmkmod.network.msg.MessageRbmkCustomScreenAction;
import com.jiezhirbmkmod.network.msg.MessageRbmkJetExhaust;
import com.jiezhirbmkmod.network.msg.MessageRbmkMonitorScreen;
import com.jiezhirbmkmod.network.msg.MessageRbmkPda;
import com.jiezhirbmkmod.network.msg.MessageRbmkScreenPolicy;
import com.jiezhirbmkmod.network.msg.MessageRbmkTerminalComm;
import com.jiezhirbmkmod.network.msg.MessageSirenTune;
import com.jiezhirbmkmod.network.msg.MessageTerminalCommand;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.relauncher.Side;

public class ModNetwork {
    public static final SimpleNetworkWrapper CHANNEL = NetworkRegistry.INSTANCE.newSimpleChannel("rbmk_monitor_network");

    /**
     * v17.9 /rbmkcompat 用：网络协议代号。17.8 瘦身改了线上格式
     * （协议 7 → 8），新旧版本混连包会串台，所以把代号显式写死在
     * 一处 —— 探针命令与握手记录都引用它，不再各写各的。
     * <p>v18.0：升到 9。历史落盘/时间段导出本身不动包格式，但大版本
     * 强制对齐 —— 18.0 与 17.x 混连时握手按模组版本就会被拒
     * （{@code RbmkMonitorMod#onNetworkCheck} 要求版本一致），协议代号
     * 跟着跳一版，让 /rbmkcompat 的输出一眼能认出这是 18.0 的包表。</p>
     * <p>v18.3：升到 11。MessageTerminalCommand 增加了 PDA 标记位（线上
     * 格式变了），并新增 PDA 快照/绑定包（id 25/26），旧端必须升级。</p>
     */
    public static final int PROTOCOL = 11;

    /**
     * v17.8/17.9 网络包瘦身：全部 23 个 discriminator 注册收进这一张表。
     * <p>此前 id 0..7 与 12..21 在本类注册，id 8..11 却散在
     * {@code RbmkMonitorMod.preInit} 里 —— 一张包表拆两处，排查协议
     * 问题要翻两个文件。现在统一到这里，id 不变（两侧注册顺序必须
     * 一致，改 id 等于断线）。</p>
     *
     * <pre>
     * id  0  S→C  MessageRbmkMonitorScreen    监视屏整包状态（含快照/事件/策略）
     * id  1  C→S  MessageRbmkCustomScreenAction 自定义屏动作
     * id  2  S→C  MessageRbmkJetExhaust        喷气背包尾焰
     * id  3  C→S  MessageRbmkScreenPolicy      告警策略表单
     * id  4  C→S  MessageTerminalCommand       终端命令（客户端发）
     * id  5  S→C  MessageTerminalCommand       终端命令（服务端回）
     * id  6  C→S  MessageSirenTune             汽笛调音（客户端发）
     * id  7  S→C  MessageSirenTune             汽笛调音（服务端广播回）
     * id  8  C→S  AutoScramToggleMessage       自动停堆开关（屏内嵌套类）
     * id  9  C→S  HackToggleMessage            黑客开关（屏内嵌套类）
     * id 10  C→S  ConnectMessage               绑定控制台（屏内嵌套类）
     * id 11  S→C  ShakeMessage                 蒸汽爆炸抖屏（屏内嵌套类）
     * id 12  C→S  MessageRbmkTerminalComm      终端侧信道
     * id 13  C→S  MessageAlarmLight            警报灯颜色
     * id 14  C→S  MessageRbmkPptControl        PPT 遥控
     * id 15  C→S  MessageIssuerAuthorize       签发授权
     * id 16  S→C  MessageIssuerResult          签发结果+产卡
     * id 17  C→S  MessageHackAppSend           攻击申请提交
     * id 18  S→C  MessageHackAppReview         审批弹窗
     * id 19  C→S  MessageHackAppVote           投票
     * id 20  S→C  MessageHackAppResult         结果回信
     * id 21  S→C  MessagePatrolSync            巡屏绑定清单
     * id 22  S→C  MessageAggregateSync         多堆芯聚合归属表（屏→堆芯）
     * id 23  C→S  MessageScreenDisplaySetting  显示设置（亮度/主题/曲线）
     * id 24  C→S  MessageCustomLayoutSet      自定义页布局
     * id 25  C→S  MessageRbmkPda              PDA 快照请求 / 绑定请求
     * id 26  S→C  MessageRbmkPda              PDA 快照 / 绑定回执
     * </pre>
     */
    public static void init() {
        int id = 0;
        CHANNEL.registerMessage(MessageRbmkMonitorScreen.Handler.class, MessageRbmkMonitorScreen.class, id++, Side.CLIENT);
        CHANNEL.registerMessage(MessageRbmkCustomScreenAction.Handler.class, MessageRbmkCustomScreenAction.class, id++, Side.SERVER);
        CHANNEL.registerMessage(MessageRbmkJetExhaust.Handler.class, MessageRbmkJetExhaust.class, id++, Side.CLIENT);
        CHANNEL.registerMessage(MessageRbmkScreenPolicy.Handler.class, MessageRbmkScreenPolicy.class, id++, Side.SERVER);
        // same class for both directions: client submits a command line, server replies with output lines
        CHANNEL.registerMessage(MessageTerminalCommand.Handler.class, MessageTerminalCommand.class, id++, Side.SERVER);
        CHANNEL.registerMessage(MessageTerminalCommand.Handler.class, MessageTerminalCommand.class, id++, Side.CLIENT);
        // one class, both directions: client submits the tuner form, server applies it and broadcasts the same form back
        CHANNEL.registerMessage(MessageSirenTune.Handler.class, MessageSirenTune.class, id++, Side.SERVER);
        CHANNEL.registerMessage(MessageSirenTune.Handler.class, MessageSirenTune.class, id++, Side.CLIENT);
        // v15.0: terminal side channel ('msg' / 'password' / 'auth').
        // ids 8..11 belong to the four small control packets registered at the
        // bottom of this method (moved here from RbmkMonitorMod in v17.8, ids
        // unchanged), so this one stays pinned to 12.
        CHANNEL.registerMessage(MessageRbmkTerminalComm.Handler.class, MessageRbmkTerminalComm.class, 12, Side.SERVER);
        // v16.0: alarm light colour updates (discriminator 13)
        CHANNEL.registerMessage(MessageAlarmLight.Handler.class, MessageAlarmLight.class, 13, Side.SERVER);
        // v17.1: PPT remote / terminal slide control
        CHANNEL.registerMessage(com.jiezhirbmkmod.network.msg.MessageRbmkPptControl.Handler.class,
                com.jiezhirbmkmod.network.msg.MessageRbmkPptControl.class, 14, Side.SERVER);
        // v17.6: issuer authorize (discriminator 15)
        CHANNEL.registerMessage(com.jiezhirbmkmod.network.msg.MessageIssuerAuthorize.Handler.class,
                com.jiezhirbmkmod.network.msg.MessageIssuerAuthorize.class, 15, Side.SERVER);
        // v17.6: issuer result reply (discriminator 16) —— 签发结果+产出卡 NBT 回 GUI
        CHANNEL.registerMessage(com.jiezhirbmkmod.network.msg.MessageIssuerResult.Handler.class,
                com.jiezhirbmkmod.network.msg.MessageIssuerResult.class, 16, Side.CLIENT);
        // v17.6: hack gameplay approval flow —— 17 提交申请(S) 18 审批弹窗(C)
        //        19 投票(S) 20 结果回信(C)
        CHANNEL.registerMessage(com.jiezhirbmkmod.network.msg.MessageHackAppSend.Handler.class,
                com.jiezhirbmkmod.network.msg.MessageHackAppSend.class, 17, Side.SERVER);
        CHANNEL.registerMessage(com.jiezhirbmkmod.network.msg.MessageHackAppReview.Handler.class,
                com.jiezhirbmkmod.network.msg.MessageHackAppReview.class, 18, Side.CLIENT);
        CHANNEL.registerMessage(com.jiezhirbmkmod.network.msg.MessageHackAppVote.Handler.class,
                com.jiezhirbmkmod.network.msg.MessageHackAppVote.class, 19, Side.SERVER);
        CHANNEL.registerMessage(com.jiezhirbmkmod.network.msg.MessageHackAppResult.Handler.class,
                com.jiezhirbmkmod.network.msg.MessageHackAppResult.class, 20, Side.CLIENT);
        // v17.7 巡屏绑定清单下发（S→C）：坐标塞不进 16 位窗口属性，专用包
        CHANNEL.registerMessage(com.jiezhirbmkmod.network.msg.MessagePatrolSync.Handler.class,
                com.jiezhirbmkmod.network.msg.MessagePatrolSync.class, 21, Side.CLIENT);
        // v17.9 多堆芯聚合归属表下发（S→C，discriminator 22）：屏→堆芯
        // 归堆在服务端算（consolePos 不同步到客户端），实时数据仍由屏自己的包走
        CHANNEL.registerMessage(com.jiezhirbmkmod.network.msg.MessageAggregateSync.Handler.class,
                com.jiezhirbmkmod.network.msg.MessageAggregateSync.class, 22, Side.CLIENT);
        // v18.2 设置中心显示设置（C→S，discriminator 23）：亮度/主题/曲线对比/对数
        CHANNEL.registerMessage(com.jiezhirbmkmod.network.msg.MessageScreenDisplaySetting.Handler.class,
                com.jiezhirbmkmod.network.msg.MessageScreenDisplaySetting.class, 23, Side.SERVER);
        // v18.2 自定义页布局保存（C→S，discriminator 24）：布局编辑器整包提交
        CHANNEL.registerMessage(com.jiezhirbmkmod.network.msg.MessageCustomLayoutSet.Handler.class,
                com.jiezhirbmkmod.network.msg.MessageCustomLayoutSet.class, 24, Side.SERVER);
        // v18.3 PDA 快照/绑定往返（25 C→S 请求，26 S→C 回执）
        CHANNEL.registerMessage(MessageRbmkPda.Handler.class, MessageRbmkPda.class, 25, Side.SERVER);
        CHANNEL.registerMessage(MessageRbmkPda.Handler.class, MessageRbmkPda.class, 26, Side.CLIENT);
        // v17.8 瘦身：把散在 RbmkMonitorMod.preInit 的 8..11 收编进来（id 不变）
        CHANNEL.registerMessage(TileRbmkMonitorScreen.AutoScramToggleMessage.Handler.class,
                TileRbmkMonitorScreen.AutoScramToggleMessage.class, 8, Side.SERVER);
        CHANNEL.registerMessage(TileRbmkMonitorScreen.HackToggleMessage.Handler.class,
                TileRbmkMonitorScreen.HackToggleMessage.class, 9, Side.SERVER);
        CHANNEL.registerMessage(TileRbmkMonitorScreen.ConnectMessage.Handler.class,
                TileRbmkMonitorScreen.ConnectMessage.class, 10, Side.SERVER);
        CHANNEL.registerMessage(com.jiezhirbmkmod.dfc.SteamExplosionHandler.ShakeMessage.Handler.class,
                com.jiezhirbmkmod.dfc.SteamExplosionHandler.ShakeMessage.class, 11, Side.CLIENT);
    }
}

