package com.jiezhirbmkmod.block;

import com.jiezhirbmkmod.RbmkMonitorMod;
import com.jiezhirbmkmod.block.BlockRbmkMonitor;
import com.jiezhirbmkmod.client.dfc.RbmkScreenCanvas;
import com.jiezhirbmkmod.client.dfc.WatchNames;
import com.jiezhirbmkmod.dfc.AlertPolicy;
import com.jiezhirbmkmod.dfc.DfcWatch;
import com.jiezhirbmkmod.dfc.RbmkBridge;
import com.jiezhirbmkmod.dfc.RbmkConfig;
import com.jiezhirbmkmod.dfc.RbmkControl;
import com.jiezhirbmkmod.dfc.RbmkSnapshot;
import com.jiezhirbmkmod.dfc.ScreenLayout;
import com.jiezhirbmkmod.dfc.ScreenStructure;
import com.jiezhirbmkmod.dfc.SteamExplosionHandler;
import com.jiezhirbmkmod.item.ModSounds;
import com.jiezhirbmkmod.network.ModNetwork;
import com.jiezhirbmkmod.network.msg.MessageRbmkMonitorScreen;
import com.jiezhirbmkmod.network.msg.MessageTerminalCommand;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ITickable;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.energy.CapabilityEnergy;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraft.util.text.Style;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

public class TileRbmkMonitorScreen
extends TileEntity
implements ITickable, IRealPowerTile {
    public static final int REFRESH_INTERVAL = 4;
    private static final double SYNC_RANGE = 96.0;
    private boolean powered;
    private int tilt = 2;
    private int boundDim = Integer.MIN_VALUE;
    private BlockPos sourcePos;
    private BlockPos consolePos;
    private final RbmkSnapshot data = new RbmkSnapshot();
    private final RbmkSnapshot prev = new RbmkSnapshot();
    private final List<String> events = new ArrayList<String>();
    private int refreshTimer;
    private boolean forceSync = true;

    // ---- v17.4 real-screen mains ----
    /** 0 = off, 1 = standby, 2 = on. Always ON for the original (non-real) screens. */
    private int realPowerState = RealPower.ON;
    /** Forge Energy buffer, only ever filled for the real screens. */
    private int realEnergy;
    /**
     * v18.0 独立 HE 缓冲（HBM 能量单位，"厂用电"）：从相邻 HBM 能量方块
     * 经 {@link HbmEnergyProbe} 反射拉电，与 FE 缓冲互为备份 —— 供电判定
     * 是「HE 或 FE 任一有电即可」。没装 HBM 时恒 0，纯 FE 玩法不受影响。
     */
    private long heEnergy;
    /**
     * v18.0 电源来源指示（拟真主控室的电源牌）：每次扣费刷新，
     * {@link RealPower#SOURCE_HE}=厂用 HE / {@link RealPower#SOURCE_FE}=外部 FE /
     * {@link RealPower#SOURCE_NONE}=无。随 updateTag 下发，客户端状态行可读。
     */
    private int powerSource = RealPower.SOURCE_NONE;
    private long realLastClickMs;
    /** 0 = idle, 1 = the first click of a possible double is waiting. */
    private int realClickStage;
    /** State the screen was in before the first click of the current pair. */
    private int realStateBeforeClick = RealPower.OFF;
    private int intervalTicks = Math.max(1, com.jiezhirbmkmod.dfc.RbmkConfig.defaultRefreshMs / 50);
    /** Legacy ms list used by {@link #setRefreshMs(int)} only; 300 covers the v17.4 mid tier */
    public static final int[] REFRESH_CHOICES_MS = new int[]{50, 100, 200, 300, 500, 1000};
    /** v17.4 refresh tiers, ordered slow -> fast as shown by the tuner item */
    public static final int REFRESH_TIER_DEFAULT = 0;
    public static final int REFRESH_TIER_LOW = 1;
    public static final int REFRESH_TIER_MID = 2;
    public static final int REFRESH_TIER_HIGH = 3;
    public static final int REFRESH_TIER_ULTRA = 4;
    /**
     * v17.5: 250 ms, between mid and default. Only reachable from the civilian
     * tuner, which stops here instead of offering high/ultra: a normal player
     * should not be able to push a wall of screens to 50 ms and stall the server.
     */
    public static final int REFRESH_TIER_MIDHIGH = 5;
    public static final int REFRESH_TIER_COUNT = 6;

    /** Admin order: default -> low -> mid -> high -> ultra */
    public static final int[] TIER_ORDER_FULL =
            new int[]{REFRESH_TIER_DEFAULT, REFRESH_TIER_LOW, REFRESH_TIER_MID,
                      REFRESH_TIER_HIGH, REFRESH_TIER_ULTRA};

    /** Civilian order: default -> low -> mid -> midhigh, never faster than 250 ms */
    public static final int[] TIER_ORDER_BASIC =
            new int[]{REFRESH_TIER_DEFAULT, REFRESH_TIER_LOW, REFRESH_TIER_MID,
                      REFRESH_TIER_MIDHIGH};
    /** -1 = not stored yet (old world / freshly placed screen), inferred from the current ms */
    private int refreshTier = -1;
    /**
     * v17.4: the value this screen was born with, snapshotted from
     * {@code defaultRefreshMs} the first time it is saved. The "default" tier
     * returns to this, so a screen keeps its factory rate even when the server
     * config changes later, and every player gets a stable set of five tiers.
     * -1 = not snapshotted yet (fresh or pre-17.4 world).
     */
    private int baseMs = -1;
    private int runTicks;
    /**
     * v17.4.1: true once "Powered" was seen in the saved NBT. A screen whose
     * tile entity was not created in time during placement never received the
     * initial powerOn() and then looked "bound but blank" forever; the first
     * tick repairs that, but only for screens that were never saved with a
     * power flag, so a player who switched a panel off keeps it off.
     */
    private boolean powerFlagFromNbt;
    private long clientBootMs;
    private long clientFaultMs;
    private boolean clientPrevMeltdown;
    private Object canvasHolder;

    /** black-box ring buffer: last 60 refreshes of core temperature / flux */
    public static final int HISTORY_SIZE = 60;
    private final double[] heatHistory = new double[HISTORY_SIZE];
    private final int[] fluxHistory = new int[HISTORY_SIZE];
    private int historyHead = 0;
    private int historyCount = 0;
    /** true once auto-SCRAM fired for the current heat-up, prevents repeat spam */
    private boolean autoScrammed = false;
    /** player override: temporarily suppress auto-SCRAM on this screen */
    public boolean autoScramDisabled = false;
    /** true while the terminal hack is active on this screen */
    public boolean isHacked = false;
    private long hackLastSpoof = -1L;

    // ---- v17.9 黑客玩法二期：反向追踪痕迹（NBT 持久化，修复后保留） ----
    /**
     * 最后一次黑入这块屏的玩家名。痕迹语义：修复（hack 状态解除）也
     * <b>不抹掉</b> —— 管理员的 {@code /rbmktrace} 要能事后定位，痕迹
     * 是证据不是状态；真正的状态只有 {@link #isHacked}。
     */
    private String hackerName = "";
    /** 被黑那一刻的 world.getTotalWorldTime()，-1 = 无痕迹 */
    private long hackTimeTicks = -1L;
    /** 修复那一刻的 world time，-1 = 仍处于被黑状态（或从未修复过） */
    private long hackFixedTicks = -1L;

    /** v15.0 cross-dimension text message: last line received + who sent it */
    private String lastMessage = "";
    private String lastMessageSender = "";
    /** v15.0 anti-grief lock: 4-digit security code, empty means unlocked */
    private String securityCode = "";
    /** player that first set the security code; empty means unclaimed */
    private String ownerName = "";
    /** v15.1 remote redstone override: last target + strength issued from a terminal */
    private int remoteRedstoneX = Integer.MIN_VALUE;
    private int remoteRedstoneY;
    private int remoteRedstoneZ;
    private int remoteRedstoneStrength;
    /** per-screen hack authorisations (dim:pos:player -> expiry), server memory only */
    private static final java.util.Map<String, Long> HACK_AUTH =
            new java.util.concurrent.ConcurrentHashMap<String, Long>();
    private static final long AUTH_WINDOW_MS = 300000L;

    private final AlertPolicy policy = new AlertPolicy();
    private int alertLevel = AlertPolicy.LEVEL_NONE;
    private int lastAlertLevel = AlertPolicy.LEVEL_NONE;
    private long sirenTick = -1L;
    private String alertReason = "";
    private int rsOutput = 0;
    private final int[] rsStreak = new int[1];

    public int getIntervalTicks() {
        return this.intervalTicks;
    }

    public void setClientRefreshMs(int ms) {
        this.intervalTicks = Math.max(1, ms / 50);
    }

    public int getRefreshMs() {
        return this.intervalTicks * 50;
    }

    /**
     * Legacy raw-ms setter, kept so old call sites still compile. It clears the
     * tier so the next {@link #getRefreshTier()} re-infers it from the value.
     * Prefer {@link #applyRefreshTier(int)}: only that one keeps a tier stable
     * across saves when two tiers happen to share a millisecond value.
     */
    public boolean setRefreshMs(int ms) {
        int ticks = 0;
        for (int v : REFRESH_CHOICES_MS) {
            if (v != ms) continue;
            ticks = Math.max(1, v / 50);
            break;
        }
        if (ticks <= 0 || ticks == this.intervalTicks) {
            return false;
        }
        this.intervalTicks = ticks;
        this.refreshTier = -1;
        this.refreshTimer = 0;
        this.forceSync = true;
        this.markDirty();
        return true;
    }

    /**
     * v17.4: the factory value of this screen, i.e. what "default" means here.
     *
     * <p>It is snapshotted into NBT the first time the screen is saved, so a
     * change to {@code defaultRefreshMs} only affects screens placed after the
     * change. Every player, including one who cannot edit the server config,
     * therefore still has a stable five-tier set instead of a "default" tier
     * that silently duplicates "mid".</p>
     */
    public int getBaseMs() {
        if (this.baseMs > 0) {
            return this.baseMs;
        }
        return Math.max(50, Math.min(1000, RbmkConfig.defaultRefreshMs));
    }

    /** Sets the factory value, ignoring nonsense input. */
    public void setBaseMs(int ms) {
        this.baseMs = ms < 50 ? 50 : (ms > 1000 ? 1000 : ms);
        this.markDirty();
    }

    /**
     * Refresh interval in ms for one of the six tuner tiers.
     *
     * <p>Every tier gets its own tick count so adjacent steps are visible on
     * screen: default 200 ms (4 ticks), midhigh 250 (5), mid 300 (6),
     * high 100 (2), ultra 50 (1), low 500 (10).</p>
     */
    public int refreshMsForTier(int tier) {
        switch (tier) {
            case REFRESH_TIER_LOW:
                return 500;
            case REFRESH_TIER_MID:
                return 300;
            case REFRESH_TIER_MIDHIGH:
                return 250;
            case REFRESH_TIER_HIGH:
                return 100;
            case REFRESH_TIER_ULTRA:
                return 50;
            case REFRESH_TIER_DEFAULT:
            default:
                return this.getBaseMs();
        }
    }

    /** Translation key for a tier name, resolved client side. */
    public static String refreshTierKey(int tier) {
        switch (tier) {
            case REFRESH_TIER_LOW:
                return "tier.jiezhirbmkmod.refresh.low";
            case REFRESH_TIER_MID:
                return "tier.jiezhirbmkmod.refresh.mid";
            case REFRESH_TIER_HIGH:
                return "tier.jiezhirbmkmod.refresh.high";
            case REFRESH_TIER_ULTRA:
                return "tier.jiezhirbmkmod.refresh.ultra";
            case REFRESH_TIER_MIDHIGH:
                return "tier.jiezhirbmkmod.refresh.midhigh";
            case REFRESH_TIER_DEFAULT:
            default:
                return "tier.jiezhirbmkmod.refresh.default";
        }
    }

    /**
     * Best guess for screens saved before the tier field existed. Compares
     * against this screen's own factory value, never against the live config,
     * so a config edit cannot rename tiers on screens that already exist.
     */
    public int inferRefreshTier(int ms) {
        if (ms == this.getBaseMs()) {
            return REFRESH_TIER_DEFAULT;
        }
        if (ms <= 60) {
            return REFRESH_TIER_ULTRA;
        }
        if (ms <= 120) {
            return REFRESH_TIER_HIGH;
        }
        if (ms <= 280) {
            return REFRESH_TIER_MIDHIGH;
        }
        if (ms <= 400) {
            return REFRESH_TIER_MID;
        }
        return REFRESH_TIER_LOW;
    }

    public int getRefreshTier() {
        if (this.refreshTier >= 0 && this.refreshTier < REFRESH_TIER_COUNT) {
            return this.refreshTier;
        }
        return this.inferRefreshTier(this.getRefreshMs());
    }

    /**
     * Steps to the next tier in {@code order} and returns it.
     *
     * <p>If the current tier is not part of {@code order} (an admin left this
     * screen on high or ultra, then a normal player walks up with the civilian
     * tuner) the cycle restarts at the first entry, i.e. default. That is
     * deliberately conservative: the screen drops back to 200 ms instead of
     * jumping to some arbitrary middle value.</p>
     */
    public int cycleRefreshTier(int[] order) {
        int[] seq = order == null || order.length == 0 ? TIER_ORDER_FULL : order;
        int cur = this.getRefreshTier();
        int at = -1;
        for (int i = 0; i < seq.length; i++) {
            if (seq[i] == cur) {
                at = i;
                break;
            }
        }
        int next = seq[(at + 1) % seq.length];
        this.applyRefreshTier(next);
        return next;
    }

    public void applyRefreshTier(int tier) {
        int clamped = tier < 0 || tier >= REFRESH_TIER_COUNT ? REFRESH_TIER_DEFAULT : tier;
        this.intervalTicks = Math.max(1, this.refreshMsForTier(clamped) / 50);
        this.refreshTier = clamped;
        this.refreshTimer = 0;
        this.forceSync = true;
        this.markDirty();
    }

    public void update() {
        if (this.world == null) {
            return;
        }
        if (this.world.isRemote) {
            return;
        }
        ++this.refreshTimer;
        // v17.4.1: repair a panel that was placed before its tile entity
        // existed (the old place -> powerOn() path silently did nothing then).
        // Only the flag is set here, the regular refresh below does the work.
        if (!this.powerFlagFromNbt && !this.powered && !this.isRealScreen()) {
            this.powered = true;
            this.forceSync = true;
            this.markDirty();
        }
        // v17.4: the mains check runs first, because it may set forceSync and
        // the packet below has to carry the resulting state in the same tick.
        // It is wrapped because the original screens must keep refreshing even
        // if anything in the real-screen branch ever throws: a single failed
        // tick used to abort update() and froze the whole panel for good.
        try {
            this.tickRealPower();
        }
        catch (Throwable t) {
            RbmkMonitorMod.warn("[TileRbmkMonitorScreen] mains tick failed at {}: {}", this.pos, t);
        }
        if (this.forceSync || this.refreshTimer % this.intervalTicks == 0) {
            this.refresh();
        }
        try {
            this.keepSirenAlive();
        }
        catch (Throwable t) {
            RbmkMonitorMod.warn("[TileRbmkMonitorScreen] siren tick failed at {}: {}", this.pos, t);
        }
    }

    private void keepSirenAlive() {
        if (!this.powered || !this.policy.siren || this.alertLevel < AlertPolicy.LEVEL_WARN) {
            return;
        }
        boolean crit = this.alertLevel >= AlertPolicy.LEVEL_CRIT;
        long period = (crit ? com.jiezhirbmkmod.dfc.RbmkConfig.critRepeatSec
                : com.jiezhirbmkmod.dfc.RbmkConfig.warnHysteresisSec) * 20L;
        if (period <= 0L) {
            return;
        }
        long now = this.world.getTotalWorldTime();
        if (this.sirenTick >= 0L && now - this.sirenTick < period) {
            return;
        }
        this.fireSiren(crit);
    }

    public void refresh() {
        if (this.world == null) {
            return;
        }
        this.forceSync = false;
        this.refreshTimer = 0;
        try {
            this.refreshBody();
        }
        catch (Throwable t) {
            // v17.4.1: a throwing data source must not brick the panel. Before
            // this guard a single exception left the tile with a stale, empty
            // snapshot and no further packets - the exact "bound but blank"
            // symptom reported after the real-screen update.
            RbmkMonitorMod.warn("[TileRbmkMonitorScreen] refresh failed at {}: {}", this.pos, t);
            this.data.reset();
            this.data.linked = false;
            this.data.offline = true;
        }
        finally {
            // always push, even after a failure: the client has to learn that
            // the source went offline instead of keeping the last picture
            this.syncToClients();
        }
    }

    private void refreshBody() {
        boolean ok;
        this.prev.copyFrom(this.data);
        this.data.includeGrid = this.isGridPage();
        World srcWorld = this.getBoundWorld();
        if (srcWorld == null) {
            // the bound dimension is gone/unloaded: report offline instead of
            // silently reading coordinates from the wrong world
            this.data.reset();
            this.data.linked = false;
            this.data.offline = true;
            return;
        }
        TileEntity source = null;
        if (this.consolePos != null) {
            source = srcWorld.getTileEntity(this.consolePos);
            if (!RbmkBridge.isDataSource(source)) {
                source = null;
            }
        }
        if (source == null && this.sourcePos != null) {
            source = RbmkBridge.resolveDataSource(srcWorld, this.sourcePos);
        }
        if (!(ok = RbmkBridge.read(source, this.data))) {
            this.data.reset();
            this.data.linked = false;
            this.data.offline = true;
        }
        this.data.tempDelta = ScreenLayout.temperatureRate(this.prev.avgHeat, this.data.avgHeat, this.intervalTicks);
        this.runTicks = this.data.linked && !this.data.offline && !this.data.cold ? (this.runTicks += this.intervalTicks) : 0;
        this.data.runTicks = this.runTicks;
        this.recordHistory();
        if (this.data.meltdown) {
            this.data.meltdownFrames = this.prev.meltdownFrames + 1;
        }
        if (!this.world.isRemote && this.data.meltdown && !this.prev.meltdown) {
            SteamExplosionHandler.trigger(this.world, this.pos);
        }
        if (this.data.truncated && !this.prev.truncated) {
            this.appendEvents(java.util.Collections.singletonList(
                    "扫描已截断：有效 RBMK 柱子超过 2000，仅统计前 2000 根"));
        }
        this.appendEvents(ScreenLayout.pendingEvents(this.prev, this.data));
        this.fillExtra(this.data);
        this.checkAutoScram();
        // spoof the displayed readings after the real data was used for safety
        this.applyHackEffects();
        this.evalAlert();
        if (this.isHacked) {
            // keep the screen flashing red while the hack is active
            this.alertLevel = AlertPolicy.LEVEL_CRIT;
            this.lastAlertLevel = AlertPolicy.LEVEL_CRIT;
            this.alertReason = "HACKED: 数据完整性校验失败";
        }
        this.updateRedstone();
    }

    /** Corrupt the displayed temperature or flux once every 5 seconds. */
    private void applyHackEffects() {
        if (!this.isHacked || this.world == null) {
            return;
        }
        long now = this.world.getTotalWorldTime();
        if (this.hackLastSpoof >= 0L && now - this.hackLastSpoof < 100L) {
            return;
        }
        this.hackLastSpoof = now;
        String msg;
        if (this.world.rand.nextBoolean()) {
            double fake = 500.0D + this.world.rand.nextDouble() * 1200.0D;
            this.data.avgHeat = fake;
            if (this.data.maxHeat < fake) {
                this.data.maxHeat = fake;
            }
            if (this.data.maxHeatLimit < fake) {
                this.data.maxHeatLimit = fake + 100.0D;
            }
            msg = "[SYSTEM] 数据完整性校验失败：温度被篡改为 " + (int) fake + " C";
        } else {
            int fake = 300 + this.world.rand.nextInt(5000);
            this.data.flux = fake;
            msg = "[SYSTEM] 数据完整性校验失败：中子通量被篡改为 " + fake + " F";
        }
        List<String> news = new ArrayList<String>();
        news.add(msg);
        this.appendEvents(news);
        if (this instanceof TileRbmkTermScreen) {
            ((TileRbmkTermScreen) this).pushTermLine(new MessageTerminalCommand.CmdLine(
                    MessageTerminalCommand.C_ERR, msg));
        }
    }

    /** Enable/disable the hack state (called from the patch packet handler). */
    public void setHacked(boolean hacked) {
        this.setHacked(hacked, "");
    }

    /**
     * v17.9 带痕迹的开关：置 true 时记下入侵者与时间；置 false 只盖
     * 「已修复」时间戳，入侵者名字保留给 {@code /rbmktrace}。
     * 修复一台从没被黑过的屏（defensive fix 包）不产生任何痕迹。
     */
    public void setHacked(boolean hacked, String hacker) {
        this.isHacked = hacked;
        this.hackLastSpoof = -1L;
        if (this.world != null && !this.world.isRemote) {
            long now = this.world.getTotalWorldTime();
            if (hacked) {
                this.hackerName = hacker == null ? "" : hacker.trim();
                this.hackTimeTicks = now;
                this.hackFixedTicks = -1L;
                // v18.0 审计：入侵开启进 rbmk_audit.log（修复不记，痕迹留给 /rbmktrace）
                com.jiezhirbmkmod.util.RbmkAuditLogger.logAt(this.hackerName, "hack", this.world, this.pos);
            } else if (this.hackTimeTicks >= 0L && this.hackFixedTicks < 0L) {
                // 只在「真的有痕迹且还没盖过戳」时记录修复时间
                this.hackFixedTicks = now;
            }
        }
        this.markDirty();
    }

    /** 最后一次黑入本屏的玩家名（无痕迹时为空串） */
    public String getHackerName() {
        return this.hackerName == null ? "" : this.hackerName;
    }

    /** 被黑时的 world time（tick），无痕迹返回 -1 */
    public long getHackTimeTicks() {
        return this.hackTimeTicks;
    }

    /** 修复时的 world time（tick），未修复/无痕迹返回 -1 */
    public long getHackFixedTicks() {
        return this.hackFixedTicks;
    }

    /** 该屏是否有可追踪的入侵痕迹（无论当前是否仍被黑着） */
    public boolean hasHackerTrace() {
        return this.hackTimeTicks >= 0L;
    }

    /**
     * v17.9 /rbmktrace 数据源：收集周围半径内已加载区块里的全部显示屏
     * （含 {@link TileRbmkTermScreen} 等子类）。v18.0 起实现统一收编进
     * {@link ScreenScan} —— 报警灯/巡屏/聚合中控/trace 四处共用同一条
     * "只扫已加载区块、距离升序"的扫描策略。
     */
    public static java.util.List<TileRbmkMonitorScreen> collectScreens(World world, BlockPos center, int radius) {
        return ScreenScan.collect(world, center, radius);
    }

    // ------------------------------------------------------------------
    // v15.0: cross-dimension text messaging + anti-grief security code
    // ------------------------------------------------------------------

    public String getLastMessage() {
        return this.lastMessage == null ? "" : this.lastMessage;
    }

    public String getLastMessageSender() {
        return this.lastMessageSender == null ? "" : this.lastMessageSender;
    }

    public boolean hasSecurityCode() {
        return this.securityCode != null && !this.securityCode.isEmpty();
    }

    public String getOwnerName() {
        return this.ownerName == null ? "" : this.ownerName;
    }

    /** true when the screen is unclaimed or owned by that player */
    public boolean isOwner(String playerName) {
        if (this.ownerName == null || this.ownerName.isEmpty()) {
            return true;
        }
        return playerName != null && this.ownerName.equalsIgnoreCase(playerName);
    }

    public void setOwnerName(String playerName) {
        this.ownerName = playerName == null ? "" : playerName;
        this.markDirty();
    }

    /** only a 4-digit code is accepted; anything else clears the lock */
    public void setSecurityCode(String code) {
        String c = code == null ? "" : code.trim();
        if (c.length() != 4) {
            c = "";
        } else {
            for (int i = 0; i < 4; ++i) {
                if (!Character.isDigit(c.charAt(i))) {
                    c = "";
                    break;
                }
            }
        }
        this.securityCode = c;
        this.markDirty();
    }

    public boolean checkSecurityCode(String code) {
        return this.hasSecurityCode() && this.securityCode.equals(code);
    }

    /** remember the last remote redstone override issued from this screen's terminal */
    public void setRemoteRedstone(int x, int y, int z, int strength) {
        this.remoteRedstoneX = x;
        this.remoteRedstoneY = y;
        this.remoteRedstoneZ = z;
        this.remoteRedstoneStrength = strength;
        this.markDirty();
    }

    public boolean hasRemoteRedstone() {
        return this.remoteRedstoneX != Integer.MIN_VALUE && this.remoteRedstoneStrength > 0;
    }

    /** true when the given position is the override this screen installed itself */
    public boolean isRemoteRedstoneAt(int x, int y, int z) {
        return this.remoteRedstoneX == x && this.remoteRedstoneY == y && this.remoteRedstoneZ == z;
    }

    public int getRemoteRedstoneStrength() {
        return this.remoteRedstoneStrength;
    }

    /**
     * Store an incoming terminal message in NBT, mirror it on the wall display
     * and force a screen refresh so nearby clients see it immediately.
     */
    public void receiveTerminalMessage(String sender, String text) {
        this.lastMessageSender = sender == null ? "" : sender;
        this.lastMessage = text == null ? "" : text;
        this.forceSync = true;
        this.markDirty();
        if (this.world == null) {
            return;
        }
        String line = "[" + this.lastMessageSender + "] " + this.lastMessage;
        this.appendEvents(java.util.Collections.singletonList("[MSG] " + line));
        if (!this.world.isRemote) {
            if (this instanceof TileRbmkTermScreen) {
                ((TileRbmkTermScreen) this).pushTermLine(new MessageTerminalCommand.CmdLine(
                        MessageTerminalCommand.C_WARN, line));
            }
            this.syncToClients();
        }
    }

    private static String hackAuthKey(World world, BlockPos pos, String player) {
        int dim = world != null && world.provider != null ? world.provider.getDimension() : 0;
        return dim + ":" + pos.getX() + ":" + pos.getY() + ":" + pos.getZ() + ":"
                + (player == null ? "" : player.toLowerCase(java.util.Locale.ROOT));
    }

    /** remember that this player proved the security code (5 minute window) */
    public static void markAuthed(TileRbmkMonitorScreen screen, String player) {
        if (screen == null || screen.world == null || player == null) {
            return;
        }
        HACK_AUTH.put(TileRbmkMonitorScreen.hackAuthKey(screen.world, screen.getPos(), player),
                System.currentTimeMillis() + AUTH_WINDOW_MS);
        // v17.7: 密码授权成功落盘 —— 屏被攻击前总有一次密码通过
        BlockPos ap = screen.getPos();
        com.jiezhirbmkmod.dfc.AuditLog.log(player, "SCREEN_AUTH",
                "screen @ " + ap.getX() + "," + ap.getY() + "," + ap.getZ());
    }

    public static boolean isAuthed(TileRbmkMonitorScreen screen, String player) {
        if (screen == null || screen.world == null || player == null) {
            return false;
        }
        String key = TileRbmkMonitorScreen.hackAuthKey(screen.world, screen.getPos(), player);
        Long until = HACK_AUTH.get(key);
        if (until == null) {
            return false;
        }
        if (System.currentTimeMillis() > until.longValue()) {
            HACK_AUTH.remove(key);
            return false;
        }
        return true;
    }

    /** nearest screen around a point, optionally restricted to a known owner */
    public static TileRbmkMonitorScreen findScreenNear(World world, BlockPos center, int radius, String owner) {
        if (world == null || center == null || radius <= 0) {
            return null;
        }
        int minX = center.getX() - radius;
        int maxX = center.getX() + radius;
        int minZ = center.getZ() - radius;
        int maxZ = center.getZ() + radius;
        int minY = center.getY() - radius;
        int maxY = center.getY() + radius;
        double maxSq = (double) radius * (double) radius;
        TileRbmkMonitorScreen best = null;
        double bestSq = maxSq + 1.0D;
        // only walk already-loaded chunks, never load terrain from a command
        for (int cx = minX >> 4; cx <= maxX >> 4; ++cx) {
            for (int cz = minZ >> 4; cz <= maxZ >> 4; ++cz) {
                Chunk chunk;
                try {
                    chunk = world.getChunkProvider().getLoadedChunk(cx, cz);
                }
                catch (Throwable ignored) {
                    continue;
                }
                if (chunk == null) {
                    continue;
                }
                for (TileEntity te : chunk.getTileEntityMap().values()) {
                    if (!(te instanceof TileRbmkMonitorScreen)) {
                        continue;
                    }
                    BlockPos p = te.getPos();
                    if (p.getX() < minX || p.getX() > maxX || p.getZ() < minZ || p.getZ() > maxZ
                            || p.getY() < minY || p.getY() > maxY) {
                        continue;
                    }
                    double d = p.distanceSq(center);
                    if (d > maxSq || d >= bestSq) {
                        continue;
                    }
                    TileRbmkMonitorScreen screen = (TileRbmkMonitorScreen) te;
                    if (owner != null && !owner.isEmpty() && !screen.isOwner(owner)) {
                        continue;
                    }
                    bestSq = d;
                    best = screen;
                }
            }
        }
        return best;
    }

    /** false (and a red status line) when the screen is locked and not yet authorised */
    private static boolean hackAllowed(EntityPlayerMP attacker, TileRbmkMonitorScreen screen) {
        if (screen == null || !screen.hasSecurityCode()) {
            return true;
        }
        if (TileRbmkMonitorScreen.isAuthed(screen, attacker.getName())) {
            return true;
        }
        TileRbmkMonitorScreen.sendSystem(attacker, TextFormatting.RED,
                "[SECURITY] 该屏幕已启用安全码，请先在终端输入 auth <4位数字>。");
        // also answer through the terminal channel so the open GUI learns the lock state
        List<MessageTerminalCommand.CmdLine> lines = new ArrayList<MessageTerminalCommand.CmdLine>();
        lines.add(new MessageTerminalCommand.CmdLine(MessageTerminalCommand.C_ERR,
                "[AUTH-REQ] 该屏幕已启用安全码，请先输入 auth <4位数字>。"));
        ModNetwork.CHANNEL.sendTo((IMessage) new MessageTerminalCommand(lines), attacker);
        return false;
    }

    /**
     * Auto-SCRAM: when the bound reactor's core temperature exceeds the
     * configured trip point, insert every manual control rod once. The guard
     * flag re-arms only after the reactor cooled down well below the limit.
     */
    private void checkAutoScram() {
        if (this.world == null || this.world.isRemote) {
            return;
        }
        double temp = this.data.avgHeat;
        boolean overLimit = RbmkConfig.enableAutoScram && !this.autoScramDisabled
                && this.data.linked && !this.data.offline
                && temp >= (double) RbmkConfig.autoScramTemp;
        if (this.autoScrammed) {
            if (!overLimit && (!this.data.linked || this.data.offline
                    || temp < (double) RbmkConfig.autoScramTemp * 0.9D)) {
                this.autoScrammed = false;
            }
            return;
        }
        if (!overLimit) {
            return;
        }
        this.autoScrammed = true;
        World targetWorld = this.getBoundWorld();
        if (targetWorld == null) {
            return;
        }
        TileEntity console = this.consolePos != null
                ? RbmkBridge.resolveDataSource(targetWorld, this.consolePos) : null;
        BlockPos center = console != null ? RbmkControl.reactorCenter(targetWorld, console) : this.sourcePos;
        int set = 0;
        if (center != null) {
            List<TileEntity> rods = RbmkControl.collectRods(targetWorld, center, 128);
            for (int i = 0; i < rods.size(); ++i) {
                if (RbmkControl.setRodLevel(rods.get(i), 0.0D)) {
                    ++set;
                }
            }
        }
        String msg = "[SYSTEM] 温度超限！已触发自动紧急停堆！";
        List<String> news = new ArrayList<String>();
        news.add(msg);
        this.appendEvents(news);
        if (this instanceof TileRbmkTermScreen) {
            ((TileRbmkTermScreen) this).pushTermLine(new MessageTerminalCommand.CmdLine(
                    MessageTerminalCommand.C_ERR, msg));
        }
        RbmkMonitorMod.warn("Auto-SCRAM at {}: {} rod(s) inserted, avg temp {} C, console {}",
                this.pos, Integer.valueOf(set), Double.valueOf(temp), this.consolePos);
        // v18.0 审计：自动 SCRAM 记为 SYSTEM 操作
        com.jiezhirbmkmod.util.RbmkAuditLogger.logAt("SYSTEM", "auto-scram", this.world, this.pos);
    }

    protected void evalAlert() {
        int lv = this.powered ? this.policy.level(this.data) : AlertPolicy.LEVEL_NONE;
        String reason = lv == AlertPolicy.LEVEL_NONE ? "" : this.policy.reason(this.data);
        boolean changed = lv != this.alertLevel || !reason.equals(this.alertReason);
        this.alertLevel = lv;
        this.alertReason = reason;
        if (changed) {
            if (lv > this.lastAlertLevel) {
                List<String> news = new ArrayList<String>();
                news.add("【告警" + (lv >= AlertPolicy.LEVEL_CRIT ? "·严重" : "·预警") + "】" + reason);
                this.appendEvents(news);
                this.fireSiren(lv >= AlertPolicy.LEVEL_CRIT);
            } else if (lv == AlertPolicy.LEVEL_NONE) {
                List<String> news = new ArrayList<String>();
                news.add("告警解除（读数已回到阈值内）");
                this.appendEvents(news);
            }
        }
        this.lastAlertLevel = lv;
    }

    private void fireSiren(boolean crit) {
        if (this.world == null || this.world.isRemote) {
            return;
        }
        this.sirenTick = this.world.getTotalWorldTime();
        if (!this.powered || !this.policy.siren) {
            return;
        }
        SoundEvent ev = crit ? ModSounds.ALERT_CRIT : ModSounds.ALERT_WARN;
        float volume = crit ? com.jiezhirbmkmod.dfc.RbmkConfig.volumeCrit
                : com.jiezhirbmkmod.dfc.RbmkConfig.volumeWarn;
        this.world.playSound((EntityPlayer) null, (double) this.pos.getX() + 0.5D,
                (double) this.pos.getY() + 0.5D, (double) this.pos.getZ() + 0.5D,
                ev, crit ? SoundCategory.AMBIENT : SoundCategory.BLOCKS, volume, 1.0F);
    }

    protected void updateRedstone() {
        boolean cond = this.rawRedstone();
        int want = AlertPolicy.latchedOutput(this.rsOutput, cond, this.rsStreak,
                com.jiezhirbmkmod.dfc.RbmkConfig.rsConfirmCycles);
        if (want == this.rsOutput) {
            return;
        }
        this.rsOutput = want;
        if (this.world == null || this.world.isRemote || !this.world.isBlockLoaded(this.pos)) {
            return;
        }
        IBlockState cur = this.world.getBlockState(this.pos);
        if (!(cur.getBlock() instanceof BlockRbmkScreen)) {
            return;
        }
        boolean on = want > 0;
        List<String> news = new ArrayList<String>();
        news.add("红石输出：" + (on ? "ON" : "OFF") + "（" + AlertPolicy.modeName(this.policy.rsMode)
                + (AlertPolicy.hasThreshold(this.policy.rsMode) ? " " + AlertPolicy.valueText(this.policy.rsMode, this.policy.rsValue) : "")
                + (this.policy.rsInvert ? "，已反相" : "") + "）");
        this.appendEvents(news);
        EnumFacing facing = (EnumFacing) cur.getValue(BlockRbmkScreen.FACING);
        if ((cur.getValue(BlockRbmkScreen.POWERED)).booleanValue() != on) {
            this.world.setBlockState(this.pos, cur.withProperty(BlockRbmkScreen.POWERED, Boolean.valueOf(on)), 3);
        }
        for (int part = 1; part < 12; ++part) {
            BlockPos p = ScreenStructure.partPos(this.pos, part, facing);
            if (p == null) {
                continue;
            }
            IBlockState ps = this.world.getBlockState(p);
            if (!(ps.getBlock() instanceof BlockRbmkScreenPart)
                    || (ps.getValue(BlockRbmkScreenPart.POWERED)).booleanValue() == on) {
                continue;
            }
            this.world.setBlockState(p, ps.withProperty(BlockRbmkScreenPart.POWERED, Boolean.valueOf(on)), 3);
        }
    }

    private boolean rawRedstone() {
        if (!this.powered || this.policy.rsMode == AlertPolicy.RS_OFF) {
            return false;
        }
        boolean cond = this.policy.condition(this.data, this.alertLevel, this.firstWatch());
        return this.policy.rsInvert ? !cond : cond;
    }

    public int redstonePower() {
        return this.rsOutput;
    }

    @Nullable
    protected DfcWatch firstWatch() {
        return null;
    }

    protected void fillExtra(RbmkSnapshot d) {
    }

    private void appendEvents(List<String> news) {
        if (news == null || news.isEmpty()) {
            return;
        }
        for (String s : news) {
            if (!this.events.isEmpty() && this.events.get(this.events.size() - 1).equals(s)) continue;
            this.events.add(s);
        }
        while (this.events.size() > 12) {
            this.events.remove(0);
        }
    }

    private boolean isGridPage() {
        if (this.world == null) {
            return false;
        }
        Block b = this.world.getBlockState(this.pos).getBlock();
        return b instanceof BlockRbmkMonitor;
    }

    // ==================== v17.4 real-screen mains ====================

    /**
     * Cached answer of {@link #isRealScreen()}: -1 unknown, 0 ordinary, 1 real.
     *
     * <p>The energy capability is asked once per neighbour per tick, and every
     * answer used to call {@code world.getBlockState(pos)}, which can force a
     * chunk load inside an energy network traversal. The block behind a tile
     * entity never changes during its lifetime, so one lookup is enough.</p>
     */
    private int realVariantCache = -1;
    /** Throttles markDirty() while the FE buffer drains every tick. */
    private int energySaveTimer;

    /** True when this block is one of the {@link IRealScreen} variants. */
    public boolean isRealScreen() {
        if (this.realVariantCache >= 0) {
            return this.realVariantCache == 1;
        }
        if (this.world == null || this.pos == null) {
            return false;
        }
        try {
            Block b = this.world.getBlockState(this.pos).getBlock();
            boolean real = b instanceof IRealScreen;
            // never cache an air result: a tile entity can be created before
            // its block state is visible, and a wrong "false" would stick
            if (b != net.minecraft.init.Blocks.AIR) {
                this.realVariantCache = real ? 1 : 0;
            }
            return real;
        }
        catch (Throwable t) {
            // an unloaded chunk must read as "ordinary screen", never as a
            // real one: failing the other way would silently black out every
            // normal display on the server
            return false;
        }
    }

    @Override
    public boolean isRealReady() {
        return this.world != null && this.pos != null;
    }

    /**
     * Short type name for the chat line. Same order of checks the refresh
     * tuner uses, so both items call the same screen by the same name.
     */
    @Override
    public String getRealNameKey() {
        if (this instanceof TileRbmkPptScreen) {
            return "screen.jiezhirbmkmod.ppt";
        }
        if (this instanceof TileRbmkMediaScreen) {
            return "screen.jiezhirbmkmod.media";
        }
        if (this instanceof TileRbmkTermScreen) {
            return "screen.jiezhirbmkmod.term";
        }
        if (this instanceof TileCustomScreen) {
            return "screen.jiezhirbmkmod.custom";
        }
        return "screen.jiezhirbmkmod.monitor";
    }

    @Override
    public int getRealPowerState() {
        return this.realPowerState;
    }

    @Override
    public void setRealPowerState(int state) {
        int next = RealPower.clamp(state);
        if (this.realPowerState == next) {
            return;
        }
        this.realPowerState = next;
        this.realClickStage = 0;
        this.forceSync = true;
        this.markDirty();
        if (this.world != null && !this.world.isRemote && this.pos != null) {
            IBlockState st = this.world.getBlockState(this.pos);
            this.world.notifyBlockUpdate(this.pos, st, st, 3);
        }
    }

    @Override
    public int getRealEnergy() {
        return this.realEnergy;
    }

    @Override
    public void setRealEnergy(int energy) {
        this.realEnergy = Math.max(0, Math.min(RealPower.ENERGY_CAPACITY, energy));
    }

    /** v18.0 独立 HE 缓冲（厂用电），供状态行与 GUI 读取。 */
    @Override
    public long getHeEnergy() {
        return this.heEnergy;
    }

    @Override
    public void setHeEnergy(long energy) {
        this.heEnergy = Math.max(0L, Math.min(RealPower.HE_CAPACITY, energy));
    }

    @Override
    public int getPowerSource() {
        return this.powerSource;
    }

    /**
     * True when the panel may actually draw. The original screens are never
     * gated, so every existing world keeps working exactly as before.
     */
    public boolean isShowingPicture() {
        return !this.isRealScreen() || this.realPowerState == RealPower.ON;
    }

    /**
     * Server tick for the mains. Also the only place allowed to force a screen
     * off, so an unpowered panel can never be switched on by a stale click.
     *
     * <p>Redstone of at least {@link RealPower#REDSTONE_MIN} next to the block
     * is always required; on the energy side HE (mains, drawn from adjacent
     * HBM power blocks) or FE (external backup) - either non-empty keeps the
     * panel alive. Losing every supply drops the screen straight to off.</p>
     */
    private void tickRealPower() {
        if (!this.isRealScreen()) {
            return;
        }
        // v18.0 厂用电充能：从相邻 HBM 能量方块反射拉 HE。没装 HBM 时
        // available() 恒 false，整段 no-op；FE 侧照旧由 capability 自动进。
        if (HbmEnergyProbe.available()) {
            long room = RealPower.HE_CAPACITY - this.heEnergy;
            if (room > 0L) {
                long got = HbmEnergyProbe.drawFromNeighbors(this.world, this.pos,
                        Math.min(room, RealPower.HE_MAX_INPUT));
                if (got > 0L) {
                    this.heEnergy += got;
                }
            }
        }
        // v18.0 双源判定：红石许可照旧，能量侧「HE 或 FE 任一有电即可」
        if (!RealPower.isSupplied(this.world, this.pos, this.heEnergy > 0L, this.realEnergy > 0)) {
            if (this.realPowerState != RealPower.OFF) {
                this.realPowerState = RealPower.OFF;
                this.realClickStage = 0;
                this.forceSync = true;
                this.markDirty();
            }
            this.powerSource = RealPower.SOURCE_NONE;
            return;
        }
        int draw = RealPower.drawFor(this.realPowerState);
        if (draw <= 0) {
            return;
        }
        // v18.0 扣费：厂用 HE 优先（1 FE 当量 = 10 HE），HE 不足的部分再扣
        // 外部 FE。takeHe/10 向下取整产生的半点差额当线路损耗（真实厂用
        // 变压器也有损耗，且方向对玩家略有利，不会出现"扣不动却黑屏"）。
        long heTaken = Math.min(this.heEnergy, (long) draw * RealPower.HE_PER_FE);
        this.heEnergy -= heTaken;
        int feTaken = (int) Math.min((long) this.realEnergy, draw - heTaken / RealPower.HE_PER_FE);
        if (feTaken > 0) {
            this.realEnergy -= feTaken;
        }
        // 电源牌：扣到 HE 就算厂用供电（HE 优先），全靠 FE 才标外部
        this.powerSource = heTaken > 0L ? RealPower.SOURCE_HE : RealPower.SOURCE_FE;
        // the buffer drains every tick; save it once a second so a restart
        // cannot hand the player a free refill from a stale chunk save
        if (++this.energySaveTimer >= 20) {
            this.energySaveTimer = 0;
            this.markDirty();
        }
        if (this.heEnergy <= 0L && this.realEnergy <= 0 && this.realPowerState != RealPower.OFF) {
            this.realPowerState = RealPower.OFF;
            this.realClickStage = 0;
            this.forceSync = true;
            this.markDirty();
        }
    }

    /**
     * Click handler for the real screens: one click moves one step, a second
     * click inside {@link RealPower#DOUBLE_CLICK_MS} jumps to the double-click
     * target computed from the state before the first click.
     *
     * <p>The first click already took effect, which is why the second one
     * replaces the state instead of advancing it again - a dead screen clicked
     * twice lands on ON, not back on STANDBY.</p>
     */
    public boolean handleRealClick(EntityPlayer player) {
        if (this.world == null || this.world.isRemote || !this.isRealScreen()) {
            return false;
        }
        if (!RealPower.isSupplied(this.world, this.pos,
                this.heEnergy > 0L, this.realEnergy > 0)) {
            RealPowerStatus.send(player, RealPowerStatus.refused(this));
            this.realClickStage = 0;
            return true;
        }
        // tick counter, not wall clock: currentTimeMillis() can jump backwards
        // after a clock correction, which turned every later click into a
        // negative delta and killed the double-click entirely
        long now = this.world.getTotalWorldTime();
        long delta = now - this.realLastClickMs;
        // a reset/corrupt stage or a non-monotonic value must never wedge the
        // state machine: anything outside 0..1 simply starts a new single click
        if (this.realClickStage != 0 && this.realClickStage != 1) {
            this.realClickStage = 0;
        }
        boolean second = this.realClickStage == 1
                && delta >= 0L && delta <= RealPower.DOUBLE_CLICK_TICKS;
        if (second) {
            this.realPowerState = RealPower.doubleTarget(this.realStateBeforeClick);
            this.realClickStage = 0;
        } else {
            this.realStateBeforeClick = this.realPowerState;
            this.realPowerState = RealPower.nextSingle(this.realPowerState);
            this.realClickStage = 1;
            this.realLastClickMs = now;
        }
        this.forceSync = true;
        this.markDirty();
        RealPowerStatus.send(player, RealPowerStatus.line(this));
        return true;
    }

    @Override
    public boolean hasCapability(Capability<?> capability, @Nullable EnumFacing facing) {
        if (capability == CapabilityEnergy.ENERGY && this.isRealScreen()) {
            return true;
        }
        return super.hasCapability(capability, facing);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getCapability(Capability<T> capability, @Nullable EnumFacing facing) {
        if (capability == CapabilityEnergy.ENERGY && this.isRealScreen()) {
            return (T) this.realStorage;
        }
        return super.getCapability(capability, facing);
    }

    /**
     * FE side of the mains. Deliberately accept-only: a screen is a consumer,
     * never a buffer other machines can drain.
     */
    private final IEnergyStorage realStorage = new IEnergyStorage() {
        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            int room = RealPower.ENERGY_CAPACITY - TileRbmkMonitorScreen.this.realEnergy;
            int n = Math.min(room, Math.min(maxReceive, RealPower.ENERGY_MAX_INPUT));
            if (n <= 0) {
                return 0;
            }
            if (!simulate) {
                TileRbmkMonitorScreen.this.realEnergy += n;
                TileRbmkMonitorScreen.this.markDirty();
            }
            return n;
        }

        @Override
        public int extractEnergy(int maxExtract, boolean simulate) {
            return 0;
        }

        @Override
        public int getEnergyStored() {
            return TileRbmkMonitorScreen.this.realEnergy;
        }

        @Override
        public int getMaxEnergyStored() {
            return RealPower.ENERGY_CAPACITY;
        }

        @Override
        public boolean canExtract() {
            return false;
        }

        @Override
        public boolean canReceive() {
            return true;
        }
    };

    public void togglePower(@Nullable EntityPlayer player) {
        // v18.0 审计：电源开关进 rbmk_audit.log
        com.jiezhirbmkmod.util.RbmkAuditLogger.logAt(
                player == null ? null : player.getName(), "togglePower", this.world, this.pos);
        // an explicit switch wins over the placement fallback in update()
        this.powerFlagFromNbt = true;
        boolean bl = this.powered = !this.powered;
        if (this.powered) {
            this.events.clear();
            this.prev.copyFrom(this.data);
        }
        this.forceSync = true;
        this.markDirty();
        if (player != null) {
            player.sendStatusMessage((ITextComponent)new TextComponentString(TextFormatting.AQUA + "[jiezhirbmkmod] " + TextFormatting.RESET + "Display " + (this.powered ? "ON" : "OFF")), true);
        }
        try {
            this.refresh();
        }
        catch (Throwable t) {
            // never let a broken data source abort the block interaction
            RbmkMonitorMod.warn("[TileRbmkMonitorScreen] refresh after power toggle failed at {}: {}", this.pos, t);
        }
    }

    public void powerOn() {
        this.powered = true;
        this.events.clear();
        this.prev.copyFrom(RbmkSnapshot.empty());
        this.forceSync = true;
        this.markDirty();
        this.refresh();
    }

    /**
     * Bind this panel to a data source.
     *
     * <p>Returns true when the very first read after binding succeeded, so the
     * caller can tell "bound and live" from "bound but the console is not
     * formed / not loaded". The chat feedback is sent from here because only
     * this method knows which of the two cases happened.</p>
     */
    public boolean bindSource(BlockPos src, EntityPlayer player) {
        if (this.world == null) {
            TileRbmkMonitorScreen.reply(player, "msg.jiezhirbmkmod.remote.bind_fail");
            return false;
        }
        boolean readOk = false;
        try {
            BlockPos cp;
            TileEntity te = null;
            if (src != null) {
                if (!this.world.isBlockLoaded(src)) {
                    // a coordinate from a stale remote must not overwrite a
                    // working binding with an unloaded position
                    TileRbmkMonitorScreen.reply(player, "msg.jiezhirbmkmod.remote.chunk_unloaded");
                    return false;
                }
                // a recorded position may point at a dummy block of an HBM
                // multiblock, resolve it to the block that owns the TileEntity
                te = RbmkBridge.resolveDataSource(this.world, src);
            }
            this.sourcePos = te != null ? te.getPos().toImmutable()
                    : (src == null ? null : src.toImmutable());
            this.consolePos = null;
            // the dimension has to follow the resolved source, not the panel:
            // writing the panel's own dimension made refresh() look up the
            // coordinates in the wrong world whenever the two ever differ
            World srcWorld = te != null && te.getWorld() != null ? te.getWorld() : this.world;
            this.boundDim = srcWorld != null && srcWorld.provider != null
                    ? srcWorld.provider.getDimension() : Integer.MIN_VALUE;
            if (this.sourcePos != null && RbmkBridge.isConsole(te)) {
                this.consolePos = this.sourcePos;
            } else if (this.sourcePos != null && RbmkBridge.isRbmkPart(te)
                    && (cp = RbmkBridge.findConsoleNear(srcWorld, this.sourcePos,
                            com.jiezhirbmkmod.dfc.RbmkConfig.consoleSearchRadius)) != null) {
                this.consolePos = cp.toImmutable();
            }
            this.events.clear();
            this.prev.copyFrom(RbmkSnapshot.empty());
            this.autoScrammed = false;
            this.forceSync = true;
            this.markDirty();
            this.refresh();
            readOk = this.data.linked && !this.data.offline;
            if (player != null) {
                if (src == null) {
                    TileRbmkMonitorScreen.reply(player, "msg.jiezhirbmkmod.remote.bind_cleared");
                } else if (!readOk) {
                    // bound in NBT but nothing readable: say so instead of
                    // pretending success, this is the case players report as
                    // "it bound but shows nothing"
                    TileRbmkMonitorScreen.reply(player, "msg.jiezhirbmkmod.remote.bind_offline",
                            TileRbmkMonitorScreen.posText(this.consolePos != null ? this.consolePos : this.sourcePos));
                } else if (this.consolePos != null) {
                    TileRbmkMonitorScreen.reply(player, "msg.jiezhirbmkmod.remote.bind_ok",
                            TileRbmkMonitorScreen.posText(this.consolePos));
                } else {
                    TileRbmkMonitorScreen.reply(player, "msg.jiezhirbmkmod.remote.bind_part",
                            TileRbmkMonitorScreen.posText(this.sourcePos));
                }
                if (readOk && src != null && this.isRealScreen()
                        && this.realPowerState != RealPower.ON) {
                    // the most common false alarm: a real screen that was just
                    // bound perfectly well but is still switched off
                    TileRbmkMonitorScreen.reply(player, "msg.jiezhirbmkmod.remote.bind_real_off");
                }
            }
        }
        catch (Throwable t) {
            RbmkMonitorMod.warn("[TileRbmkMonitorScreen] bind failed at {}: {}", this.pos, t);
            TileRbmkMonitorScreen.reply(player, "msg.jiezhirbmkmod.remote.bind_fail");
            readOk = false;
        }
        return readOk;
    }

    /** Server-side chat line; never uses the client-only I18n. */
    private static void reply(EntityPlayer player, String key, Object... args) {
        if (player == null) {
            return;
        }
        player.sendStatusMessage(new TextComponentTranslation(key, args), false);
    }

    /** Plain "x y z" for chat lines; BlockPos.toString() is unreadable. */
    private static String posText(BlockPos p) {
        return p == null ? "?" : p.getX() + " " + p.getY() + " " + p.getZ();
    }

    public void setTilt(int t) {
        this.tilt = Math.max(0, Math.min(4, t));
        this.markDirty();
    }

    public BlockPos getConsolePos() {
        return this.consolePos;
    }

    /** the console this screen actually reads: explicit consolePos, else the bound source itself */
    public BlockPos effectiveConsole() {
        return this.consolePos != null ? this.consolePos : this.sourcePos;
    }

    /** Resolve the world the data source lives in (may be another dimension). */
    private World getBoundWorld() {
        if (this.world == null) {
            return null;
        }
        if (this.boundDim == Integer.MIN_VALUE || this.world.provider == null
                || this.boundDim == this.world.provider.getDimension()) {
            return this.world;
        }
        MinecraftServer server = this.world.getMinecraftServer();
        if (server == null) {
            return null;
        }
        World other = server.getWorld(this.boundDim);
        return other;
    }

    /** Cross-dimension bind used by the terminal 'connect' command. */
    public void bindExternal(World targetWorld, BlockPos pos) {
        if (this.world == null || this.world.isRemote || targetWorld == null
                || targetWorld.provider == null || pos == null || !targetWorld.isBlockLoaded(pos)) {
            return;
        }
        TileEntity te = RbmkBridge.resolveDataSource(targetWorld, pos);
        BlockPos resolved = te != null ? te.getPos().toImmutable() : pos.toImmutable();
        this.sourcePos = resolved;
        this.consolePos = null;
        if (RbmkBridge.isConsole(te)) {
            this.consolePos = resolved;
        } else if (RbmkBridge.isRbmkPart(te)) {
            BlockPos cp = RbmkBridge.findConsoleNear(targetWorld, resolved,
                    com.jiezhirbmkmod.dfc.RbmkConfig.consoleSearchRadius);
            if (cp != null) {
                this.consolePos = cp.toImmutable();
            }
        }
        this.boundDim = targetWorld.provider != null
                ? targetWorld.provider.getDimension() : Integer.MIN_VALUE;
        this.events.clear();
        this.prev.copyFrom(RbmkSnapshot.empty());
        this.autoScrammed = false;
        this.forceSync = true;
        this.markDirty();
        this.refresh();
    }

    private void syncToClients() {
        if (this.world == null || this.world.isRemote) {
            return;
        }
        // rate limit: only a forced push or the configured refresh boundary may
        // actually hit the network, everything else returns immediately
        if (!this.forceSync && this.intervalTicks > 0
                && this.refreshTimer % this.intervalTicks != 0) {
            return;
        }
        try {
            ModNetwork.CHANNEL.sendToAllAround((IMessage)new MessageRbmkMonitorScreen(this), new NetworkRegistry.TargetPoint(this.world.provider.getDimension(), (double)this.pos.getX() + 0.5, (double)this.pos.getY() + 0.5, (double)this.pos.getZ() + 0.5, 96.0));
        }
        catch (Throwable t) {
            // a failed sync must never abort refresh() or the block interaction
            RbmkMonitorMod.warn("[TileRbmkMonitorScreen] sync failed at {}: {}", this.pos, t);
        }
    }

    public void applyPolicy(AlertPolicy p) {
        if (p == null) {
            return;
        }
        p.clampAll();
        this.policy.setFrom(p);
        this.lastAlertLevel = AlertPolicy.LEVEL_NONE;
        this.sirenTick = -1L;
        this.forceSync = true;
        this.markDirty();
        if (this.world != null && !this.world.isRemote) {
            this.refresh();
        }
    }

    public void applyAlertState(AlertPolicy p, int level, String reason, int rsPower) {
        if (p != null) {
            this.policy.setFrom(p);
        }
        String r = reason == null ? "" : reason;
        boolean visual = level != this.alertLevel
                || !r.equals(this.alertReason)
                || rsPower != this.rsOutput;
        this.alertLevel = level;
        this.alertReason = r;
        this.rsOutput = rsPower;
        if (visual && this.world != null && this.world.isRemote
                && this.canvasHolder instanceof RbmkScreenCanvas) {
            ((RbmkScreenCanvas) this.canvasHolder).invalidate();
        }
    }

    public void applyPolicyLocal(AlertPolicy p) {
        if (this.world == null || !this.world.isRemote || p == null) {
            return;
        }
        this.policy.setFrom(p);
        this.alertLevel = this.powered ? this.policy.level(this.data) : AlertPolicy.LEVEL_NONE;
        this.alertReason = this.alertLevel == AlertPolicy.LEVEL_NONE ? "" : this.policy.reason(this.data);
        if (this.canvasHolder instanceof RbmkScreenCanvas) {
            ((RbmkScreenCanvas) this.canvasHolder).invalidate();
        }
    }

    public AlertPolicy getPolicy() {
        return this.policy;
    }

    public int getAlertLevel() {
        return this.alertLevel;
    }

    public String getAlertReason() {
        return this.alertReason == null ? "" : this.alertReason;
    }

    public void applyServerState(boolean powered, int tilt, RbmkSnapshot snap, List<String> ev) {
        this.applyServerState(powered, tilt, snap, ev, this.realPowerState);
    }

    /** v17.4 overload that also carries the real-screen mains state. */
    public void applyServerState(boolean powered, int tilt, RbmkSnapshot snap, List<String> ev,
                                 int realState) {
        this.realPowerState = RealPower.clamp(realState);
        if (powered && (!this.powered || this.clientBootMs <= 0L)) {
            this.clientBootMs = System.currentTimeMillis();
        }
        this.powered = powered;
        this.tilt = tilt;
        this.data.copyFrom(snap == null ? RbmkSnapshot.empty() : snap);
        if (this.world != null && this.world.isRemote && snap != null) {
            // keep a client-side black box so the terminal GUI can replay it
            this.recordHistory();
        }
        if (this.world != null && this.world.isRemote) {
            WatchNames.localize(this.data.watches);
        }
        this.events.clear();
        if (ev != null) {
            this.events.addAll(ev);
        }
        if (powered && snap != null) {
            if (snap.meltdown && !this.clientPrevMeltdown) {
                this.clientFaultMs = System.currentTimeMillis();
            }
            this.clientPrevMeltdown = snap.meltdown;
        }
    }

    public long clientBootElapsedMs() {
        if (this.clientBootMs <= 0L) {
            return Long.MAX_VALUE;
        }
        return System.currentTimeMillis() - this.clientBootMs;
    }

    public long clientFaultElapsedMs() {
        if (this.clientFaultMs <= 0L) {
            return -1L;
        }
        long elapsed = System.currentTimeMillis() - this.clientFaultMs;
        if (elapsed < 4000L) {
            return elapsed;
        }
        this.clientFaultMs = 0L;
        this.clientBootMs = System.currentTimeMillis();
        return -1L;
    }

    public void readFromNBT(NBTTagCompound c) {
        if (c == null) {
            return;
        }
        super.readFromNBT(c);
        this.powerFlagFromNbt = c.hasKey("Powered");
        this.powered = c.getBoolean("Powered");
        this.realPowerState = c.hasKey("RealPower")
                ? RealPower.clamp(c.getInteger("RealPower")) : RealPower.ON;
        this.realEnergy = Math.max(0, Math.min(RealPower.ENERGY_CAPACITY, c.getInteger("RealEnergy")));
        this.heEnergy = Math.max(0L, Math.min(RealPower.HE_CAPACITY, c.getLong("HEEnergy")));
        this.powerSource = RealPower.SOURCE_NONE; // 每 tick 扣费时刷新，不信任旧存档值
        this.tilt = c.hasKey("Tilt") ? c.getInteger("Tilt") : 2;
        this.sourcePos = TileRbmkMonitorScreen.readPos(c, "Src");
        this.consolePos = TileRbmkMonitorScreen.readPos(c, "Console");
        this.runTicks = c.getInteger("RunTicks");
        this.baseMs = c.hasKey("BaseRefreshMs") ? c.getInteger("BaseRefreshMs") : -1;
        this.intervalTicks = c.hasKey("RefreshMs")
                ? Math.max(1, c.getInteger("RefreshMs") / 50)
                : Math.max(1, this.getBaseMs() / 50);
        this.refreshTier = c.hasKey("RefreshTier") ? c.getInteger("RefreshTier") : -1;
        this.autoScramDisabled = c.getBoolean("AutoScramDisabled");
        this.isHacked = c.getBoolean("Hacked");
        // v17.9 反向追踪痕迹（旧存档没有这几个键，走默认空值）
        this.hackerName = TileRbmkMonitorScreen.readString(c, "Hacker");
        this.hackTimeTicks = c.hasKey("HackTime") ? c.getLong("HackTime") : -1L;
        this.hackFixedTicks = c.hasKey("HackFixed") ? c.getLong("HackFixed") : -1L;
        if (this.hackTimeTicks >= 0L && this.hackerName.isEmpty()) {
            // 有时间没名字（理论不该出现）：痕迹降级为匿名，仍可报「这块屏被黑过」
            this.hackerName = "?";
        }
        this.lastMessage = TileRbmkMonitorScreen.readString(c, "LastMsg");
        this.lastMessageSender = TileRbmkMonitorScreen.readString(c, "LastMsgSender");
        this.securityCode = TileRbmkMonitorScreen.readString(c, "SecurityCode");
        this.ownerName = TileRbmkMonitorScreen.readString(c, "Owner");
        this.remoteRedstoneX = c.hasKey("RRX") ? c.getInteger("RRX") : Integer.MIN_VALUE;
        this.remoteRedstoneY = c.getInteger("RRY");
        this.remoteRedstoneZ = c.getInteger("RRZ");
        this.remoteRedstoneStrength = c.getInteger("RRS");
        this.boundDim = c.hasKey("BoundDim") ? c.getInteger("BoundDim") : Integer.MIN_VALUE;
        this.policy.readNBT(c);
        this.lastAlertLevel = this.alertLevel = AlertPolicy.LEVEL_NONE;
        this.sirenTick = -1L;
        this.forceSync = true;
    }

    public NBTTagCompound writeToNBT(NBTTagCompound c) {
        if (c == null) {
            c = new NBTTagCompound();
        }
        super.writeToNBT(c);
        c.setBoolean("Powered", this.powered);
        c.setInteger("RealPower", this.realPowerState);
        c.setInteger("RealEnergy", this.realEnergy);
        c.setLong("HEEnergy", this.heEnergy);
        c.setInteger("PowerSource", this.powerSource);
        c.setInteger("Tilt", this.tilt);
        TileRbmkMonitorScreen.writePos(c, "Src", this.sourcePos);
        TileRbmkMonitorScreen.writePos(c, "Console", this.consolePos);
        c.setInteger("RunTicks", this.runTicks);
        c.setInteger("RefreshMs", this.intervalTicks * 50);
        c.setInteger("RefreshTier", this.getRefreshTier());
        // freeze the factory value on first save: later config edits must not
        // retroactively move an existing screen's "default" tier
        if (this.baseMs <= 0) {
            this.baseMs = this.getBaseMs();
        }
        c.setInteger("BaseRefreshMs", this.baseMs);
        c.setBoolean("AutoScramDisabled", this.autoScramDisabled);
        c.setBoolean("Hacked", this.isHacked);
        // v17.9 反向追踪痕迹：名字 + 被黑时间 + 修复时间（有痕迹才写，省键）
        if (this.hackTimeTicks >= 0L) {
            c.setString("Hacker", this.getHackerName());
            c.setLong("HackTime", this.hackTimeTicks);
            if (this.hackFixedTicks >= 0L) {
                c.setLong("HackFixed", this.hackFixedTicks);
            }
        }
        c.setString("LastMsg", this.lastMessage == null ? "" : this.lastMessage);
        c.setString("LastMsgSender", this.lastMessageSender == null ? "" : this.lastMessageSender);
        c.setString("SecurityCode", this.securityCode == null ? "" : this.securityCode);
        c.setString("Owner", this.ownerName == null ? "" : this.ownerName);
        c.setInteger("RRX", this.remoteRedstoneX);
        c.setInteger("RRY", this.remoteRedstoneY);
        c.setInteger("RRZ", this.remoteRedstoneZ);
        c.setInteger("RRS", this.remoteRedstoneStrength);
        c.setInteger("BoundDim", this.boundDim);
        this.policy.writeNBT(c);
        return c;
    }

    /** null-safe NBT string read (missing keys and malformed tags both yield "") */
    private static String readString(NBTTagCompound c, String key) {
        if (c == null || !c.hasKey(key)) {
            return "";
        }
        String s = c.getString(key);
        return s == null ? "" : s;
    }

    private static BlockPos readPos(NBTTagCompound c, String key) {
        if (!c.hasKey(key + "X")) {
            return null;
        }
        return new BlockPos(c.getInteger(key + "X"), c.getInteger(key + "Y"), c.getInteger(key + "Z"));
    }

    private static void writePos(NBTTagCompound c, String key, BlockPos p) {
        if (p == null) {
            return;
        }
        c.setInteger(key + "X", p.getX());
        c.setInteger(key + "Y", p.getY());
        c.setInteger(key + "Z", p.getZ());
    }

    public boolean isPowered() {
        return this.powered;
    }

    public int getTilt() {
        return this.tilt;
    }

    @Nullable
    public BlockPos getSourcePos() {
        return this.sourcePos;
    }

    public RbmkSnapshot getData() {
        return this.data;
    }

    /** Append the current sample to the 60-entry black-box ring buffer. */
    private void recordHistory() {
        if (!this.data.linked || this.data.offline) {
            return;
        }
        this.heatHistory[this.historyHead] = this.data.avgHeat;
        this.fluxHistory[this.historyHead] = this.data.flux;
        this.historyHead = (this.historyHead + 1) % HISTORY_SIZE;
        if (this.historyCount < HISTORY_SIZE) {
            ++this.historyCount;
        }
    }

    /** Number of valid samples currently held (0..60). */
    public int getHistoryCount() {
        return this.historyCount;
    }

    private int historySlot(int index) {
        return (this.historyHead - this.historyCount + index + HISTORY_SIZE) % HISTORY_SIZE;
    }

    /** index 0 = oldest sample, getHistoryCount()-1 = newest. */
    public double getHistoryHeat(int index) {
        if (index < 0 || index >= this.historyCount) {
            return 0.0;
        }
        return this.heatHistory[this.historySlot(index)];
    }

    public int getHistoryFlux(int index) {
        if (index < 0 || index >= this.historyCount) {
            return 0;
        }
        return this.fluxHistory[this.historySlot(index)];
    }

    public List<String> getEvents() {
        return this.events;
    }

    public boolean shouldRefresh(World world, BlockPos pos, IBlockState oldState, IBlockState newState) {
        return oldState.getBlock() != newState.getBlock();
    }

    public NBTTagCompound getUpdateTag() {
        return this.writeToNBT(new NBTTagCompound());
    }

    public AxisAlignedBB getRenderBoundingBox() {
        return new AxisAlignedBB(this.pos).grow(4.0).expand(0.0, 1.0, 0.0);
    }

    @SideOnly(value=Side.CLIENT)
    public RbmkScreenCanvas canvas() {
        if (this.canvasHolder == null) {
            RbmkScreenCanvas c = new RbmkScreenCanvas();
            c.setLocation(new ResourceLocation("jiezhirbmkmod", "rbmk_" + this.pos.getX() + "_" + this.pos.getY() + "_" + this.pos.getZ() + "_" + Integer.toHexString(System.identityHashCode((Object)this))));
            this.canvasHolder = c;
            // v18.0 历史落盘：新 canvas 是空的，从内存注册表/磁盘把上一份
            // 历史接回来 —— 存档重载、游戏重启、区块卸载重载曲线不再清零。
            // 只在客户端执行（本方法 @SideOnly(CLIENT)，服务端摸不到）。
            if (this.world != null && this.world.isRemote && this.pos != null
                    && this.world.provider != null) {
                com.jiezhirbmkmod.client.HistoryStore.seed(
                        this.world.provider.getDimension(), this.pos, c.history());
            }
        }
        return (RbmkScreenCanvas)this.canvasHolder;
    }

    public void invalidate() {
        super.invalidate();
        if (this.world != null && this.world.isRemote && this.canvasHolder instanceof RbmkScreenCanvas) {
            ((RbmkScreenCanvas)this.canvasHolder).dispose();
            this.canvasHolder = null;
        }
    }

    /**
     * Client -> server packet used by the terminal 'scram off' / 'scram on'
     * commands to toggle the per-screen auto-SCRAM override.
     */
    public static class AutoScramToggleMessage
    implements IMessage {
        private BlockPos pos;
        private boolean disabled;

        public AutoScramToggleMessage() {
            this.pos = BlockPos.ORIGIN;
        }

        public AutoScramToggleMessage(BlockPos pos, boolean disabled) {
            this.pos = pos == null ? BlockPos.ORIGIN : pos;
            this.disabled = disabled;
        }

        public void fromBytes(ByteBuf buf) {
            this.pos = new BlockPos(buf.readInt(), buf.readInt(), buf.readInt());
            this.disabled = buf.readBoolean();
        }

        public void toBytes(ByteBuf buf) {
            buf.writeInt(this.pos.getX());
            buf.writeInt(this.pos.getY());
            buf.writeInt(this.pos.getZ());
            buf.writeBoolean(this.disabled);
        }

        public static class Handler
        implements IMessageHandler<AutoScramToggleMessage, IMessage> {
            public IMessage onMessage(AutoScramToggleMessage msg, MessageContext ctx) {
                if (ctx == null || ctx.getServerHandler() == null || ctx.getServerHandler().player == null
                        || msg == null || msg.pos == null) {
                    return null;
                }
                EntityPlayerMP player = ctx.getServerHandler().player;
                if (player.getServerWorld() == null) {
                    return null;
                }
                try {
                    player.getServerWorld().addScheduledTask(() -> {
                        TileEntity te = player.world.getTileEntity(msg.pos);
                        if (!(te instanceof TileRbmkMonitorScreen)) {
                            return;
                        }
                        TileRbmkMonitorScreen screen = (TileRbmkMonitorScreen) te;
                        if (player.getDistanceSq((double) msg.pos.getX() + 0.5,
                                (double) msg.pos.getY() + 0.5,
                                (double) msg.pos.getZ() + 0.5) > 4096.0) {
                            return;
                        }
                        screen.autoScramDisabled = msg.disabled;
                        screen.markDirty();
                    });
                }
                catch (Throwable t) {
                    if (RbmkMonitorMod.logger != null) {
                        RbmkMonitorMod.logger.warn("Could not schedule auto-scram packet: " + t);
                    }
                }
                return null;
            }
        }
    }

    /**
     * Client -> server packet used by the terminal 'hack [player]' / 'fix'
     * commands. Without a target name the packet points at a nearby screen;
     * with one the server resolves the nearest screen around that player.
     */
    public static class HackToggleMessage
    implements IMessage {
        private BlockPos pos;
        private boolean hacked;
        private String target;

        public HackToggleMessage() {
            this.pos = BlockPos.ORIGIN;
            this.target = "";
        }

        public HackToggleMessage(BlockPos pos, boolean hacked) {
            this(pos, hacked, "");
        }

        public HackToggleMessage(BlockPos pos, boolean hacked, String target) {
            this.pos = pos == null ? BlockPos.ORIGIN : pos;
            this.hacked = hacked;
            this.target = target == null ? "" : target;
        }

        public void fromBytes(ByteBuf buf) {
            this.pos = new BlockPos(buf.readInt(), buf.readInt(), buf.readInt());
            this.hacked = buf.readBoolean();
            String t = net.minecraftforge.fml.common.network.ByteBufUtils.readUTF8String(buf);
            if (t != null && t.length() > 64) {
                t = t.substring(0, 64);
            }
            this.target = t == null ? "" : t;
        }

        public void toBytes(ByteBuf buf) {
            buf.writeInt(this.pos.getX());
            buf.writeInt(this.pos.getY());
            buf.writeInt(this.pos.getZ());
            buf.writeBoolean(this.hacked);
            net.minecraftforge.fml.common.network.ByteBufUtils.writeUTF8String(buf, this.target);
        }

        public static class Handler
        implements IMessageHandler<HackToggleMessage, IMessage> {
            public IMessage onMessage(HackToggleMessage msg, MessageContext ctx) {
                if (ctx == null || ctx.getServerHandler() == null || ctx.getServerHandler().player == null) {
                    return null;
                }
                EntityPlayerMP attacker = ctx.getServerHandler().player;
                if (attacker.getServerWorld() == null) {
                    return null;
                }
                try {
                    attacker.getServerWorld().addScheduledTask(
                            () -> TileRbmkMonitorScreen.handleHack(attacker, msg));
                }
                catch (Throwable t) {
                    if (RbmkMonitorMod.logger != null) {
                        RbmkMonitorMod.logger.warn("Could not schedule hack packet: " + t);
                    }
                }
                return null;
            }
        }
    }

    private static void sendSystem(EntityPlayerMP player, TextFormatting color, String text) {
        player.sendStatusMessage((ITextComponent) new TextComponentString(color + text), false);
    }

    private static TileRbmkMonitorScreen findNearestScreen(World world, BlockPos center, int radius) {
        if (world == null || center == null || radius <= 0) {
            return null;
        }
        int minX = center.getX() - radius;
        int maxX = center.getX() + radius;
        int minZ = center.getZ() - radius;
        int maxZ = center.getZ() + radius;
        int minY = center.getY() - radius;
        int maxY = center.getY() + radius;
        int minCx = minX >> 4;
        int maxCx = maxX >> 4;
        int minCz = minZ >> 4;
        int maxCz = maxZ >> 4;
        double maxSq = (double) radius * (double) radius;
        TileRbmkMonitorScreen best = null;
        double bestSq = maxSq + 1.0D;
        // only walk already-loaded chunks: never load terrain from a command
        for (int cx = minCx; cx <= maxCx; ++cx) {
            for (int cz = minCz; cz <= maxCz; ++cz) {
                Chunk chunk;
                try {
                    chunk = world.getChunkProvider().getLoadedChunk(cx, cz);
                }
                catch (Throwable ignored) {
                    continue;
                }
                if (chunk == null) {
                    continue;
                }
                for (TileEntity te : chunk.getTileEntityMap().values()) {
                    if (!(te instanceof TileRbmkMonitorScreen)) {
                        continue;
                    }
                    BlockPos p = te.getPos();
                    if (p.getX() < minX || p.getX() > maxX || p.getZ() < minZ || p.getZ() > maxZ
                            || p.getY() < minY || p.getY() > maxY) {
                        continue;
                    }
                    double d = p.distanceSq(center);
                    if (d <= maxSq && d < bestSq) {
                        bestSq = d;
                        best = (TileRbmkMonitorScreen) te;
                    }
                }
            }
        }
        return best;
    }

    private static void auditHack(EntityPlayerMP attacker, TileRbmkMonitorScreen screen) {
        if (attacker == null || screen == null) {
            return;
        }
        MinecraftServer server = attacker.getServer();
        if (server == null || server.getPlayerList() == null) {
            return;
        }
        BlockPos p = screen.getPos();
        String line = "[AUDIT] 玩家 " + attacker.getName() + " 在 (" + p.getX() + ", "
                + p.getY() + ", " + p.getZ() + ") 黑入了目标屏幕。";
        try {
            for (EntityPlayerMP p2 : server.getPlayerList().getPlayers()) {
                if (p2 != null && p2.canUseCommand(2, "audit")) {
                    p2.sendStatusMessage((ITextComponent) new TextComponentString(
                            TextFormatting.YELLOW + line), false);
                }
            }
        }
        catch (Throwable ignored) {
            // an audit broadcast must never break the hack handler
        }
        // v17.7: 权限审计链 —— 黑入事件落盘，事后可回溯
        com.jiezhirbmkmod.dfc.AuditLog.log(attacker.getName(), "HACK",
                "screen @ " + p.getX() + "," + p.getY() + "," + p.getZ());
        if (RbmkMonitorMod.logger != null) {
            RbmkMonitorMod.logger.info(line);
        }
    }

    private static void handleHack(EntityPlayerMP attacker, HackToggleMessage msg) {
        if (attacker == null || msg == null || attacker.world == null) {
            return;
        }
        try {
            TileRbmkMonitorScreen.handleHackChecked(attacker, msg);
        }
        catch (Throwable t) {
            if (RbmkMonitorMod.logger != null) {
                RbmkMonitorMod.logger.warn("Hack handler failed: " + t);
            }
        }
    }

    private static void handleHackChecked(EntityPlayerMP attacker, HackToggleMessage msg) {
        if (msg.hacked) {
            if (!RbmkConfig.enableHackerAttack) {
                TileRbmkMonitorScreen.sendSystem(attacker, TextFormatting.RED,
                        "[SYSTEM] 服务器已关闭黑客攻击玩法。");
                return;
            }
            // v17.6: 操作员工牌也可通行 —— 已签发的卡拿在手上即视作模组管理员
            if (!RbmkConfig.allowNormalPlayerHack
                    && !com.jiezhirbmkmod.item.ItemRbmkOpCard.isModOp(attacker)) {
                TileRbmkMonitorScreen.sendSystem(attacker, TextFormatting.RED,
                        "[SYSTEM] 权限不足，无法发起网络攻击。");
                return;
            }
            // 持工牌（非真 OP）发起一次网络攻击同样扣 1 次使用次数
            if (!com.jiezhirbmkmod.item.ItemRbmkOpCard.isTrueOp(attacker)) {
                com.jiezhirbmkmod.item.ItemRbmkOpCard.consumeUse(attacker);
            }
        }
        String targetName = msg.target == null ? "" : msg.target.trim();
        if (!targetName.isEmpty()) {
            // remote mode: the sender must still stand near their own terminal
            if (msg.pos != null && msg.pos.getY() != Integer.MIN_VALUE
                    && attacker.getDistanceSq((double) msg.pos.getX() + 0.5,
                            (double) msg.pos.getY() + 0.5,
                            (double) msg.pos.getZ() + 0.5) > 4096.0) {
                TileRbmkMonitorScreen.sendSystem(attacker, TextFormatting.RED,
                        "[SYSTEM] 距离终端过远，无法发起网络攻击。");
                return;
            }
            MinecraftServer server = attacker.getServer();
            if (server == null || server.getPlayerList() == null) {
                return;
            }
            EntityPlayerMP target = null;
            for (EntityPlayerMP p : server.getPlayerList().getPlayers()) {
                if (p == null || p.world == null || p.world.provider == null) {
                    continue;
                }
                if (p.world.provider.getDimension() == attacker.world.provider.getDimension()
                        && p.getName().equalsIgnoreCase(targetName)) {
                    target = p;
                    break;
                }
            }
            if (target == null || target.world == null) {
                TileRbmkMonitorScreen.sendSystem(attacker, TextFormatting.RED,
                        "[SYSTEM] 目标玩家不在线或不在当前维度。");
                return;
            }
            TileRbmkMonitorScreen screen = TileRbmkMonitorScreen.findNearestScreen(
                    target.world, target.getPosition(), 64);
            if (screen == null) {
                TileRbmkMonitorScreen.sendSystem(attacker, TextFormatting.RED,
                        "[SYSTEM] 目标玩家 64 格内没有可入侵的 RBMK 屏幕。");
                return;
            }
            if (msg.hacked && !TileRbmkMonitorScreen.hackAllowed(attacker, screen)) {
                return;
            }
            // v17.9: 带痕迹开关 —— 入侵者名字进屏的 NBT，/rbmktrace 可事后定位
            screen.setHacked(msg.hacked, attacker.getName());
            if (msg.hacked) {
                TileRbmkMonitorScreen.sendSystem(attacker, TextFormatting.GREEN,
                        "[SYSTEM] 成功黑入 " + target.getName() + " 的屏幕！");
                TileRbmkMonitorScreen.auditHack(attacker, screen);
            } else {
                TileRbmkMonitorScreen.sendSystem(attacker, TextFormatting.GREEN,
                        "[SYSTEM] 已远程修复 " + target.getName() + " 附近的屏幕。");
                // v17.7: 修复事件落盘（谁把被黑的屏修好了）
                BlockPos fp = screen.getPos();
                com.jiezhirbmkmod.dfc.AuditLog.log(attacker.getName(), "HACK_FIX",
                        "screen @ " + fp.getX() + "," + fp.getY() + "," + fp.getZ());
            }
            return;
        }
        if (msg.pos == null) {
            return;
        }
        TileEntity te = attacker.world.getTileEntity(msg.pos);
        if (!(te instanceof TileRbmkMonitorScreen)) {
            return;
        }
        TileRbmkMonitorScreen screen = (TileRbmkMonitorScreen) te;
        if (attacker.getDistanceSq((double) msg.pos.getX() + 0.5,
                (double) msg.pos.getY() + 0.5,
                (double) msg.pos.getZ() + 0.5) > 4096.0) {
            return;
        }
        if (msg.hacked && !TileRbmkMonitorScreen.hackAllowed(attacker, screen)) {
            return;
        }
        // v17.9: 带痕迹开关（近距路径，同远程路径）
        screen.setHacked(msg.hacked, attacker.getName());
        if (msg.hacked) {
            TileRbmkMonitorScreen.auditHack(attacker, screen);
        }
    }

    /**
     * Client -> server packet for the terminal 'connect dim x y z' command:
     * binds the screen to a data source that may live in another dimension.
     */
    public static class ConnectMessage
    implements IMessage {
        private BlockPos pos;
        private int dim;
        private BlockPos target;

        public ConnectMessage() {
            this.pos = BlockPos.ORIGIN;
            this.target = BlockPos.ORIGIN;
        }

        public ConnectMessage(BlockPos pos, int dim, BlockPos target) {
            this.pos = pos == null ? BlockPos.ORIGIN : pos;
            this.dim = dim;
            this.target = target == null ? BlockPos.ORIGIN : target;
        }

        public void fromBytes(ByteBuf buf) {
            this.pos = new BlockPos(buf.readInt(), buf.readInt(), buf.readInt());
            this.dim = buf.readInt();
            this.target = new BlockPos(buf.readInt(), buf.readInt(), buf.readInt());
        }

        public void toBytes(ByteBuf buf) {
            buf.writeInt(this.pos.getX());
            buf.writeInt(this.pos.getY());
            buf.writeInt(this.pos.getZ());
            buf.writeInt(this.dim);
            buf.writeInt(this.target.getX());
            buf.writeInt(this.target.getY());
            buf.writeInt(this.target.getZ());
        }

        public static class Handler
        implements IMessageHandler<ConnectMessage, IMessage> {
            public IMessage onMessage(ConnectMessage msg, MessageContext ctx) {
                if (ctx == null || ctx.getServerHandler() == null || ctx.getServerHandler().player == null
                        || msg == null || msg.pos == null || msg.target == null) {
                    return null;
                }
                EntityPlayerMP player = ctx.getServerHandler().player;
                if (player.getServerWorld() == null) {
                    return null;
                }
                try {
                    player.getServerWorld().addScheduledTask(() -> {
                        MinecraftServer server = player.getServer();
                        if (server == null) {
                            return;
                        }
                        if (Math.abs(msg.target.getX()) > 30000000 || Math.abs(msg.target.getZ()) > 30000000
                                || msg.target.getY() < 0 || msg.target.getY() > 255) {
                            TileRbmkMonitorScreen.sendSystem(player, TextFormatting.RED,
                                    "[SYSTEM] 坐标超出合法范围。");
                            return;
                        }
                        World targetWorld = msg.dim == player.world.provider.getDimension()
                                ? player.world : server.getWorld(msg.dim);
                        if (targetWorld == null) {
                            TileRbmkMonitorScreen.sendSystem(player, TextFormatting.RED,
                                    "[SYSTEM] 目标维度不存在或未加载。");
                            return;
                        }
                        if (!targetWorld.isBlockLoaded(msg.target)) {
                            TileRbmkMonitorScreen.sendSystem(player, TextFormatting.RED,
                                    "[SYSTEM] 目标区块未加载，无法绑定。");
                            return;
                        }
                        TileEntity te = player.world.getTileEntity(msg.pos);
                        if (!(te instanceof TileRbmkMonitorScreen)) {
                            return;
                        }
                        TileRbmkMonitorScreen screen = (TileRbmkMonitorScreen) te;
                        if (player.getDistanceSq((double) msg.pos.getX() + 0.5,
                                (double) msg.pos.getY() + 0.5,
                                (double) msg.pos.getZ() + 0.5) > 4096.0) {
                            return;
                        }
                        TileEntity target = RbmkBridge.resolveDataSource(targetWorld, msg.target);
                        if (target == null || !RbmkBridge.isDataSource(target)) {
                            TileRbmkMonitorScreen.sendSystem(player, TextFormatting.RED,
                                    "[SYSTEM] 目标坐标没有可绑定的 RBMK 控制台或柱体。");
                            return;
                        }
                        screen.bindExternal(targetWorld, target.getPos());
                        TileRbmkMonitorScreen.sendSystem(player, TextFormatting.GREEN,
                                "[SYSTEM] 已跨维度绑定至维度 " + msg.dim + " @ ("
                                        + target.getPos().getX() + ", " + target.getPos().getY() + ", "
                                        + target.getPos().getZ() + ")。");
                    });
                }
                catch (Throwable t) {
                    if (RbmkMonitorMod.logger != null) {
                        RbmkMonitorMod.logger.warn("Could not schedule connect packet: " + t);
                    }
                }
                return null;
            }
        }
    }
}

