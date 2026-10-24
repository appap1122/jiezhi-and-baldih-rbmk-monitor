package com.jiezhimod.block;

import com.jiezhimod.block.BlockRbmkMonitor;
import com.jiezhimod.client.dfc.ScreenCanvas;
import com.jiezhimod.client.dfc.WatchNames;
import com.jiezhimod.dfc.RbmkBridge;
import com.jiezhimod.dfc.RbmkSnapshot;
import com.jiezhimod.dfc.ScreenLayout;
import com.jiezhimod.network.ModNetwork;
import com.jiezhimod.network.msg.MessageRbmkScreen;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ITickable;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

public class TileRbmkScreen
extends TileEntity
implements ITickable {
    public static final int REFRESH_INTERVAL = 4;
    private static final double SYNC_RANGE = 96.0;
    private boolean powered;
    private int tilt = 2;
    private BlockPos sourcePos;
    private BlockPos consolePos;
    private final RbmkSnapshot data = new RbmkSnapshot();
    private final RbmkSnapshot prev = new RbmkSnapshot();
    private final List<String> events = new ArrayList<String>();
    private int refreshTimer;
    private boolean forceSync = true;
    private int intervalTicks = 4;
    public static final int[] REFRESH_CHOICES_MS = new int[]{50, 100, 200, 500, 1000};
    private int runTicks;
    private long clientBootMs;
    private long clientFaultMs;
    private boolean clientPrevMeltdown;
    private Object canvasHolder;

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
        this.func_70296_d();
        return true;
    }

    public void func_73660_a() {
        if (this.field_145850_b == null) {
            return;
        }
        if (this.field_145850_b.field_72995_K) {
            return;
        }
        ++this.refreshTimer;
        if (this.forceSync || this.refreshTimer % this.intervalTicks == 0) {
            this.refresh();
        }
    }

    public void refresh() {
        boolean ok;
        this.forceSync = false;
        this.refreshTimer = 0;
        this.prev.copyFrom(this.data);
        this.data.includeGrid = this.isGridPage();
        TileEntity source = null;
        if (this.consolePos != null && !RbmkBridge.isDataSource(source = this.field_145850_b.func_175625_s(this.consolePos))) {
            source = null;
        }
        if (source == null && this.sourcePos != null) {
            source = this.field_145850_b.func_175625_s(this.sourcePos);
        }
        if (!(ok = RbmkBridge.read(source, this.data))) {
            this.data.reset();
            this.data.linked = false;
            this.data.offline = true;
        }
        this.data.tempDelta = ScreenLayout.temperatureRate(this.prev.avgHeat, this.data.avgHeat, this.intervalTicks);
        this.runTicks = this.data.linked && !this.data.offline && !this.data.cold ? (this.runTicks += this.intervalTicks) : 0;
        this.data.runTicks = this.runTicks;
        if (this.data.meltdown) {
            this.data.meltdownFrames = this.prev.meltdownFrames + 1;
        }
        this.appendEvents(ScreenLayout.pendingEvents(this.prev, this.data));
        this.fillExtra(this.data);
        this.syncToClients();
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
        if (this.field_145850_b == null) {
            return false;
        }
        Block b = this.field_145850_b.func_180495_p(this.field_174879_c).func_177230_c();
        return b instanceof BlockRbmkMonitor;
    }

    public void togglePower(@Nullable EntityPlayer player) {
        boolean bl = this.powered = !this.powered;
        if (this.powered) {
            this.events.clear();
            this.prev.copyFrom(this.data);
        }
        this.forceSync = true;
        this.func_70296_d();
        if (player != null) {
            player.func_146105_b((ITextComponent)new TextComponentString(TextFormatting.AQUA + "[jiezhimod] " + TextFormatting.RESET + "Display " + (this.powered ? "ON" : "OFF")), true);
        }
        this.refresh();
    }

    public void powerOn() {
        this.powered = true;
        this.events.clear();
        this.prev.copyFrom(RbmkSnapshot.empty());
        this.forceSync = true;
        this.func_70296_d();
        this.refresh();
    }

    public void bindSource(BlockPos src, EntityPlayer player) {
        BlockPos cp;
        TileEntity te;
        this.sourcePos = src == null ? null : src.func_185334_h();
        this.consolePos = null;
        if (this.sourcePos != null && this.field_145850_b != null && RbmkBridge.isRbmkPart(te = this.field_145850_b.func_175625_s(this.sourcePos)) && (cp = RbmkBridge.findConsoleNear(this.field_145850_b, this.sourcePos, 24)) != null) {
            this.consolePos = cp.func_185334_h();
        }
        this.events.clear();
        this.prev.copyFrom(RbmkSnapshot.empty());
        this.forceSync = true;
        this.func_70296_d();
        if (player != null) {
            String msg = src == null ? TextFormatting.YELLOW + "Data source cleared." : (this.consolePos != null ? TextFormatting.GREEN + "Bound to RBMK console @ " + this.consolePos.func_177958_n() + " " + this.consolePos.func_177956_o() + " " + this.consolePos.func_177952_p() + " (via part)." : TextFormatting.GREEN + "Bound to RBMK source @ " + this.sourcePos.func_177958_n() + " " + this.sourcePos.func_177956_o() + " " + this.sourcePos.func_177952_p() + ".");
            player.func_146105_b((ITextComponent)new TextComponentString(TextFormatting.AQUA + "[jiezhimod] " + TextFormatting.RESET + msg), false);
        }
        this.refresh();
    }

    public void setTilt(int t) {
        this.tilt = Math.max(0, Math.min(4, t));
        this.func_70296_d();
    }

    private void syncToClients() {
        if (this.field_145850_b == null || this.field_145850_b.field_72995_K) {
            return;
        }
        ModNetwork.CHANNEL.sendToAllAround((IMessage)new MessageRbmkScreen(this), new NetworkRegistry.TargetPoint(this.field_145850_b.field_73011_w.getDimension(), (double)this.field_174879_c.func_177958_n() + 0.5, (double)this.field_174879_c.func_177956_o() + 0.5, (double)this.field_174879_c.func_177952_p() + 0.5, 96.0));
    }

    public void applyServerState(boolean powered, int tilt, RbmkSnapshot snap, List<String> ev) {
        if (powered && (!this.powered || this.clientBootMs <= 0L)) {
            this.clientBootMs = System.currentTimeMillis();
        }
        this.powered = powered;
        this.tilt = tilt;
        this.data.copyFrom(snap);
        if (this.field_145850_b != null && this.field_145850_b.field_72995_K) {
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

    public void func_145839_a(NBTTagCompound c) {
        super.func_145839_a(c);
        this.powered = c.func_74767_n("Powered");
        this.tilt = c.func_74764_b("Tilt") ? c.func_74762_e("Tilt") : 2;
        this.sourcePos = TileRbmkScreen.readPos(c, "Src");
        this.consolePos = TileRbmkScreen.readPos(c, "Console");
        this.runTicks = c.func_74762_e("RunTicks");
        this.intervalTicks = c.func_74764_b("RefreshMs") ? Math.max(1, c.func_74762_e("RefreshMs") / 50) : 4;
        this.forceSync = true;
    }

    public NBTTagCompound func_189515_b(NBTTagCompound c) {
        super.func_189515_b(c);
        c.func_74757_a("Powered", this.powered);
        c.func_74768_a("Tilt", this.tilt);
        TileRbmkScreen.writePos(c, "Src", this.sourcePos);
        TileRbmkScreen.writePos(c, "Console", this.consolePos);
        c.func_74768_a("RunTicks", this.runTicks);
        c.func_74768_a("RefreshMs", this.intervalTicks * 50);
        return c;
    }

    private static BlockPos readPos(NBTTagCompound c, String key) {
        if (!c.func_74764_b(key + "X")) {
            return null;
        }
        return new BlockPos(c.func_74762_e(key + "X"), c.func_74762_e(key + "Y"), c.func_74762_e(key + "Z"));
    }

    private static void writePos(NBTTagCompound c, String key, BlockPos p) {
        if (p == null) {
            return;
        }
        c.func_74768_a(key + "X", p.func_177958_n());
        c.func_74768_a(key + "Y", p.func_177956_o());
        c.func_74768_a(key + "Z", p.func_177952_p());
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

    public List<String> getEvents() {
        return this.events;
    }

    public boolean shouldRefresh(World world, BlockPos pos, IBlockState oldState, IBlockState newState) {
        return oldState.func_177230_c() != newState.func_177230_c();
    }

    public NBTTagCompound func_189517_E_() {
        return this.func_189515_b(new NBTTagCompound());
    }

    public AxisAlignedBB getRenderBoundingBox() {
        return new AxisAlignedBB(this.field_174879_c).func_186662_g(4.0).func_72321_a(0.0, 1.0, 0.0);
    }

    @SideOnly(value=Side.CLIENT)
    public ScreenCanvas canvas() {
        if (this.canvasHolder == null) {
            ScreenCanvas c = new ScreenCanvas();
            c.setLocation(new ResourceLocation("jiezhimod", "rbmk_" + this.field_174879_c.func_177958_n() + "_" + this.field_174879_c.func_177956_o() + "_" + this.field_174879_c.func_177952_p() + "_" + Integer.toHexString(System.identityHashCode((Object)this))));
            this.canvasHolder = c;
        }
        return (ScreenCanvas)this.canvasHolder;
    }

    public void func_145843_s() {
        super.func_145843_s();
        if (this.field_145850_b != null && this.field_145850_b.field_72995_K && this.canvasHolder instanceof ScreenCanvas) {
            ((ScreenCanvas)this.canvasHolder).dispose();
            this.canvasHolder = null;
        }
    }
}

