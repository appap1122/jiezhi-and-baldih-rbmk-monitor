package com.jiezhirbmkmod.block;

import javax.annotation.Nullable;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ITickable;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.energy.CapabilityEnergy;
import net.minecraftforge.energy.IEnergyStorage;

/**
 * TileEntity of the floor-standing RBMK terminal block.
 *
 * <p>Server side it only stores the bound console position (bound either by
 * right-clicking the block with the RBMK remote, or with the 'bind' command
 * inside the terminal GUI). Client side nothing is rendered on the block
 * itself, the content lives in the GUI that opens on right-click.</p>
 *
 * <p>v17.4: the real edition ({@link BlockRbmkTerminalReal}) carries the same
 * mains switch as the wall screens - redstone of at least
 * {@link RealPower#REDSTONE_MIN} plus a non-empty FE buffer before the prompt
 * opens. The original terminal stays at {@link RealPower#ON} forever and never
 * runs the power tick, so existing worlds behave exactly as before.</p>
 */
public class TileRbmkTerminal extends TileEntity implements ITickable, IRealPowerTile {

    private BlockPos consolePos;

    // ---- v17.4 real-terminal mains ----
    private int realPowerState = RealPower.ON;
    private int realEnergy;
    /**
     * v18.0 独立 HE 缓冲（HBM 能量单位，"厂用电"）：与屏同款 ——
     * 从相邻 HBM 能量方块反射拉电，供电判定「HE 或 FE 任一有电即可」，
     * 没装 HBM 时恒 0、纯 FE 玩法不受影响。
     */
    private long heEnergy;
    /** v18.0 电源来源指示：每次扣费刷新，随 NBT 下发到客户端状态行。 */
    private int powerSource = RealPower.SOURCE_NONE;
    private long realLastClickMs;
    private int realClickStage;
    private int realStateBeforeClick = RealPower.OFF;
    /** -1 unknown, 0 ordinary terminal, 1 real terminal. See isRealTerminal(). */
    private int realVariantCache = -1;
    /** Throttles markDirty() while the FE buffer drains every tick. */
    private int energySaveTimer;

    public BlockPos getConsolePos() {
        return this.consolePos;
    }

    public void bindConsole(BlockPos console) {
        this.consolePos = console == null ? null : console.toImmutable();
        this.markDirty();
    }

    public void clearBinding() {
        this.consolePos = null;
        this.markDirty();
    }

    /** True when this block is the {@link IRealScreen} variant. */
    public boolean isRealTerminal() {
        if (this.realVariantCache >= 0) {
            return this.realVariantCache == 1;
        }
        if (this.world == null || this.pos == null) {
            return false;
        }
        try {
            net.minecraft.block.Block b = this.world.getBlockState(this.pos).getBlock();
            boolean real = b instanceof IRealScreen;
            if (b != net.minecraft.init.Blocks.AIR) {
                this.realVariantCache = real ? 1 : 0;
            }
            return real;
        }
        catch (Throwable t) {
            return false;
        }
    }

    @Override
    public void update() {
        if (this.world == null || this.world.isRemote) {
            return;
        }
        if (!this.isRealTerminal()) {
            return;
        }
        this.tickRealPower();
    }

    private void tickRealPower() {
        // v18.0 厂用电充能：从相邻 HBM 能量方块反射拉 HE（没装 HBM 时 no-op）
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
        // v18.0 双源判定：HE 或 FE 任一有电即可（红石许可不变）
        if (!RealPower.isSupplied(this.world, this.pos, this.heEnergy > 0L, this.realEnergy > 0)) {
            if (this.realPowerState != RealPower.OFF) {
                this.realPowerState = RealPower.OFF;
                this.realClickStage = 0;
                this.markDirty();
            }
            this.powerSource = RealPower.SOURCE_NONE;
            return;
        }
        int draw = RealPower.drawFor(this.realPowerState);
        if (draw <= 0) {
            return;
        }
        // v18.0 扣费：厂用 HE 优先，不足的 FE 当量再扣外部 FE（同屏的线路损耗语义）
        long heTaken = Math.min(this.heEnergy, (long) draw * RealPower.HE_PER_FE);
        this.heEnergy -= heTaken;
        int feTaken = (int) Math.min((long) this.realEnergy, draw - heTaken / RealPower.HE_PER_FE);
        if (feTaken > 0) {
            this.realEnergy -= feTaken;
        }
        this.powerSource = heTaken > 0L ? RealPower.SOURCE_HE : RealPower.SOURCE_FE;
        if (++this.energySaveTimer >= 20) {
            this.energySaveTimer = 0;
            this.markDirty();
        }
        if (this.heEnergy <= 0L && this.realEnergy <= 0 && this.realPowerState != RealPower.OFF) {
            this.realPowerState = RealPower.OFF;
            this.realClickStage = 0;
            this.markDirty();
        }
    }

    /**
     * Same click contract as {@link TileRbmkMonitorScreen#handleRealClick}:
     * one click = one step, a second click inside {@link RealPower#DOUBLE_CLICK_MS}
     * jumps to the target computed from the state before the first click.
     */
    public boolean handleRealClick(EntityPlayer player) {
        if (this.world == null || this.world.isRemote || !this.isRealTerminal()) {
            return false;
        }
        if (!RealPower.isSupplied(this.world, this.pos, this.heEnergy > 0L, this.realEnergy > 0)) {
            RealPowerStatus.send(player, RealPowerStatus.refused(this));
            this.realClickStage = 0;
            return true;
        }
        // tick counter, not wall clock (see RealPower.DOUBLE_CLICK_TICKS)
        long now = this.world.getTotalWorldTime();
        long delta = now - this.realLastClickMs;
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
        this.markDirty();
        RealPowerStatus.send(player, RealPowerStatus.line(this));
        return true;
    }

    // ---- IRealPowerTile ----

    @Override
    public int getRealPowerState() {
        return this.realPowerState;
    }

    @Override
    public void setRealPowerState(int state) {
        this.realPowerState = RealPower.clamp(state);
        this.realClickStage = 0;
        this.markDirty();
    }

    @Override
    public int getRealEnergy() {
        return this.realEnergy;
    }

    @Override
    public void setRealEnergy(int energy) {
        this.realEnergy = Math.max(0, Math.min(RealPower.ENERGY_CAPACITY, energy));
    }

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

    @Override
    public boolean isRealReady() {
        return this.world != null && this.pos != null;
    }

    @Override
    public String getRealNameKey() {
        return "screen.jiezhirbmkmod.terminal";
    }

    // ---- Forge Energy ----

    @Override
    public boolean hasCapability(Capability<?> capability, @Nullable EnumFacing facing) {
        if (capability == CapabilityEnergy.ENERGY && this.isRealTerminal()) {
            return true;
        }
        return super.hasCapability(capability, facing);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getCapability(Capability<T> capability, @Nullable EnumFacing facing) {
        if (capability == CapabilityEnergy.ENERGY && this.isRealTerminal()) {
            return (T) this.realStorage;
        }
        return super.getCapability(capability, facing);
    }

    private final IEnergyStorage realStorage = new IEnergyStorage() {
        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            int room = RealPower.ENERGY_CAPACITY - TileRbmkTerminal.this.realEnergy;
            int n = Math.min(room, Math.min(maxReceive, RealPower.ENERGY_MAX_INPUT));
            if (n <= 0) {
                return 0;
            }
            if (!simulate) {
                TileRbmkTerminal.this.realEnergy += n;
                TileRbmkTerminal.this.markDirty();
            }
            return n;
        }

        @Override
        public int extractEnergy(int maxExtract, boolean simulate) {
            return 0;
        }

        @Override
        public int getEnergyStored() {
            return TileRbmkTerminal.this.realEnergy;
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

    // ---- NBT ----

    @Override
    public void readFromNBT(NBTTagCompound c) {
        super.readFromNBT(c);
        if (c.hasKey("ConsoleX")) {
            this.consolePos = new BlockPos(c.getInteger("ConsoleX"), c.getInteger("ConsoleY"), c.getInteger("ConsoleZ"));
        } else {
            this.consolePos = null;
        }
        this.realPowerState = c.hasKey("RealPower")
                ? RealPower.clamp(c.getInteger("RealPower")) : RealPower.ON;
        this.realEnergy = Math.max(0, Math.min(RealPower.ENERGY_CAPACITY, c.getInteger("RealEnergy")));
        this.heEnergy = Math.max(0L, Math.min(RealPower.HE_CAPACITY, c.getLong("HEEnergy")));
        this.powerSource = RealPower.SOURCE_NONE; // 每 tick 扣费时刷新，不信任旧存档值
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound c) {
        super.writeToNBT(c);
        if (this.consolePos != null) {
            c.setInteger("ConsoleX", this.consolePos.getX());
            c.setInteger("ConsoleY", this.consolePos.getY());
            c.setInteger("ConsoleZ", this.consolePos.getZ());
        }
        c.setInteger("RealPower", this.realPowerState);
        c.setInteger("RealEnergy", this.realEnergy);
        c.setLong("HEEnergy", this.heEnergy);
        c.setInteger("PowerSource", this.powerSource);
        return c;
    }
}
