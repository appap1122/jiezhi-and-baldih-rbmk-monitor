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
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

public class TileRbmkMonitorScreen
extends TileEntity
implements ITickable {
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
    private int intervalTicks = Math.max(1, com.jiezhirbmkmod.dfc.RbmkConfig.defaultRefreshMs / 50);
    public static final int[] REFRESH_CHOICES_MS = new int[]{50, 100, 200, 500, 1000};
    private int runTicks;
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
        this.refreshTimer = 0;
        this.forceSync = true;
        this.markDirty();
        return true;
    }

    public void update() {
        if (this.world == null) {
            return;
        }
        if (this.world.isRemote) {
            return;
        }
        ++this.refreshTimer;
        if (this.forceSync || this.refreshTimer % this.intervalTicks == 0) {
            this.refresh();
        }
        this.keepSirenAlive();
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
        boolean ok;
        this.forceSync = false;
        this.refreshTimer = 0;
        this.prev.copyFrom(this.data);
        this.data.includeGrid = this.isGridPage();
        World srcWorld = this.getBoundWorld();
        if (srcWorld == null) {
            // the bound dimension is gone/unloaded: report offline instead of
            // silently reading coordinates from the wrong world
            this.data.reset();
            this.data.linked = false;
            this.data.offline = true;
            this.syncToClients();
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
        this.syncToClients();
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
        this.isHacked = hacked;
        this.hackLastSpoof = -1L;
        this.markDirty();
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

    public void togglePower(@Nullable EntityPlayer player) {
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
        this.refresh();
    }

    public void powerOn() {
        this.powered = true;
        this.events.clear();
        this.prev.copyFrom(RbmkSnapshot.empty());
        this.forceSync = true;
        this.markDirty();
        this.refresh();
    }

    public void bindSource(BlockPos src, EntityPlayer player) {
        BlockPos cp;
        TileEntity te = null;
        if (src != null && this.world != null) {
            // a recorded position may point at a dummy block of an HBM
            // multiblock, resolve it to the block that owns the TileEntity
            te = RbmkBridge.resolveDataSource(this.world, src);
        }
        this.sourcePos = te != null ? te.getPos().toImmutable()
                : (src == null ? null : src.toImmutable());
        this.consolePos = null;
        this.boundDim = this.world != null && this.world.provider != null
                ? this.world.provider.getDimension() : Integer.MIN_VALUE;
        if (this.sourcePos != null && RbmkBridge.isConsole(te)) {
            this.consolePos = this.sourcePos;
        } else if (this.sourcePos != null && RbmkBridge.isRbmkPart(te)
                && (cp = RbmkBridge.findConsoleNear(this.world, this.sourcePos,
                        com.jiezhirbmkmod.dfc.RbmkConfig.consoleSearchRadius)) != null) {
            this.consolePos = cp.toImmutable();
        }
        this.events.clear();
        this.prev.copyFrom(RbmkSnapshot.empty());
        this.autoScrammed = false;
        this.forceSync = true;
        this.markDirty();
        if (player != null) {
            String msg = src == null ? TextFormatting.YELLOW + "Data source cleared." : (this.consolePos != null ? TextFormatting.GREEN + "Bound to RBMK console @ " + this.consolePos.getX() + " " + this.consolePos.getY() + " " + this.consolePos.getZ() + " (via part)." : TextFormatting.GREEN + "Bound to RBMK source @ " + this.sourcePos.getX() + " " + this.sourcePos.getY() + " " + this.sourcePos.getZ() + ".");
            player.sendStatusMessage((ITextComponent)new TextComponentString(TextFormatting.AQUA + "[jiezhirbmkmod] " + TextFormatting.RESET + msg), false);
        }
        this.refresh();
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
        ModNetwork.CHANNEL.sendToAllAround((IMessage)new MessageRbmkMonitorScreen(this), new NetworkRegistry.TargetPoint(this.world.provider.getDimension(), (double)this.pos.getX() + 0.5, (double)this.pos.getY() + 0.5, (double)this.pos.getZ() + 0.5, 96.0));
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
        this.powered = c.getBoolean("Powered");
        this.tilt = c.hasKey("Tilt") ? c.getInteger("Tilt") : 2;
        this.sourcePos = TileRbmkMonitorScreen.readPos(c, "Src");
        this.consolePos = TileRbmkMonitorScreen.readPos(c, "Console");
        this.runTicks = c.getInteger("RunTicks");
        this.intervalTicks = c.hasKey("RefreshMs") ? Math.max(1, c.getInteger("RefreshMs") / 50) : 4;
        this.autoScramDisabled = c.getBoolean("AutoScramDisabled");
        this.isHacked = c.getBoolean("Hacked");
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
        c.setInteger("Tilt", this.tilt);
        TileRbmkMonitorScreen.writePos(c, "Src", this.sourcePos);
        TileRbmkMonitorScreen.writePos(c, "Console", this.consolePos);
        c.setInteger("RunTicks", this.runTicks);
        c.setInteger("RefreshMs", this.intervalTicks * 50);
        c.setBoolean("AutoScramDisabled", this.autoScramDisabled);
        c.setBoolean("Hacked", this.isHacked);
        c.setInteger("BoundDim", this.boundDim);
        this.policy.writeNBT(c);
        return c;
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
            if (!RbmkConfig.allowNormalPlayerHack && !attacker.canUseCommand(2, "hack")) {
                TileRbmkMonitorScreen.sendSystem(attacker, TextFormatting.RED,
                        "[SYSTEM] 权限不足，无法发起网络攻击。");
                return;
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
            screen.setHacked(msg.hacked);
            if (msg.hacked) {
                TileRbmkMonitorScreen.sendSystem(attacker, TextFormatting.GREEN,
                        "[SYSTEM] 成功黑入 " + target.getName() + " 的屏幕！");
                TileRbmkMonitorScreen.auditHack(attacker, screen);
            } else {
                TileRbmkMonitorScreen.sendSystem(attacker, TextFormatting.GREEN,
                        "[SYSTEM] 已远程修复 " + target.getName() + " 附近的屏幕。");
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
        screen.setHacked(msg.hacked);
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

