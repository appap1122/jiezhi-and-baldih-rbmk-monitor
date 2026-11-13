package com.jiezhirbmkmod.block;

import com.jiezhirbmkmod.dfc.SirenPresets;
import com.jiezhirbmkmod.item.ModSounds;
import com.jiezhirbmkmod.network.ModNetwork;
import com.jiezhirbmkmod.network.msg.MessageSirenTune;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.EnumDyeColor;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ITickable;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;

public class TileRbmkSiren extends TileEntity implements ITickable {

    private static final int MIN_REPEAT = 14;
    private static final int PULSE_MIN = 40;

    private int preset = SirenPresets.TWOTONE;
    private float volume = 1.0F;
    private float pitch = 1.0F;
    private int gapTicks = SirenPresets.defaultGap(SirenPresets.TWOTONE);
    private int mode = SirenPresets.MODE_LOOP;
    private int category = SirenPresets.CAT_BLOCK;
    private boolean flashLight = true;

    private boolean ringing;
    private boolean prevPowered;
    private long nextFireTick;
    private boolean firedOnce;
    private boolean clientPowered;
    private boolean clientPrevPowered;
    /** manual override set from the terminal 'siren' command (v11.1) */
    private boolean manualOn;

    @Override
    public void update() {
        if (this.world == null) {
            return;
        }
        if (this.world.isRemote) {
            this.clientTick();
            return;
        }
        // redstone OR the manual override from the terminal 'siren' command
        boolean powered = this.world.isBlockPowered(this.pos) || this.manualOn;
        if (powered && !this.prevPowered) {
            this.start();
        } else if (powered && this.ringing && this.mode != SirenPresets.MODE_ONCE
                && this.world.getTotalWorldTime() >= this.nextFireTick) {
            this.blare();
        } else if (!powered && this.prevPowered) {
            this.stop();
        }
        this.prevPowered = powered;
    }

    private void clientTick() {
        // redstone OR the manual override, so the flash light follows both
        boolean powered = this.world.isBlockPowered(this.pos) || this.manualOn;
        if (powered == this.clientPrevPowered) {
            return;
        }
        this.clientPrevPowered = powered;
        this.clientPowered = powered;
        if (this.flashLight) {
            this.world.checkLight(this.pos);
            this.world.markBlockRangeForRenderUpdate(this.pos, this.pos);
        }
    }

    private void start() {
        this.ringing = true;
        this.firedOnce = false;
        this.blare();
    }

    private void stop() {
        if (!this.ringing) {
            return;
        }
        this.ringing = false;
        this.nextFireTick = 0L;
        if (this.flashLight) {
            this.world.checkLight(this.pos);
        }
    }

    private void blare() {
        if (this.mode == SirenPresets.MODE_ONCE && this.firedOnce) {
            return;
        }
        this.firedOnce = true;
        SoundEvent ev = ModSounds.siren(this.preset);
        float v = SirenPresets.clampVolume(this.volume);
        float p = SirenPresets.clampPitch(this.pitch);
        int floor = this.mode == SirenPresets.MODE_PULSE ? PULSE_MIN : MIN_REPEAT;
        int repeat = SirenPresets.repeatTicksPreset(this.preset, this.gapTicks, floor);
        if (p > 0.01F) {
            repeat = Math.max(floor, (int) Math.round((double) repeat / (double) p));
        }
        this.nextFireTick = this.world.getTotalWorldTime() + (long) repeat;
        this.world.playSound((EntityPlayer) null,
                (double) this.pos.getX() + 0.5D, (double) this.pos.getY() + 0.5D, (double) this.pos.getZ() + 0.5D,
                ev, category(), v, p);
    }

    private SoundCategory category() {
        switch (this.category) {
            case SirenPresets.CAT_AMBIENT:
                return SoundCategory.AMBIENT;
            case SirenPresets.CAT_MASTER:
                return SoundCategory.MASTER;
            default:
                return SoundCategory.BLOCKS;
        }
    }

    public void applyConfig(int preset, float vol, float pt, int gap, int md, int cat, boolean flash) {
        this.preset = SirenPresets.wrap(preset);
        this.volume = SirenPresets.clampVolume(vol);
        this.pitch = SirenPresets.clampPitch(pt);
        this.gapTicks = SirenPresets.clampGap(gap);
        this.mode = SirenPresets.clampMode(md);
        this.category = SirenPresets.clampCat(cat);
        boolean flashChanged = this.flashLight != flash;
        this.flashLight = flash;
        this.nextFireTick = this.world.getTotalWorldTime();   
        this.markDirty();
        if (flashChanged) {
            this.world.checkLight(this.pos);
        }
        this.broadcast();
    }

    public void applyConfigLocal(int preset, float vol, float pt, int gap, int md, int cat, boolean flash) {
        this.preset = SirenPresets.wrap(preset);
        this.volume = SirenPresets.clampVolume(vol);
        this.pitch = SirenPresets.clampPitch(pt);
        this.gapTicks = SirenPresets.clampGap(gap);
        this.mode = SirenPresets.clampMode(md);
        this.category = SirenPresets.clampCat(cat);
        boolean changed = this.flashLight != flash;
        this.flashLight = flash;
        if (changed && this.world != null && this.world.isRemote) {
            this.world.checkLight(this.pos);
            this.world.markBlockRangeForRenderUpdate(this.pos, this.pos);
        }
    }

    public void broadcast() {
        if (this.world == null || this.world.isRemote) {
            return;
        }
        IMessage msg = new MessageSirenTune(this.world, this.pos, this);
        ModNetwork.CHANNEL.sendToAllAround(msg, new NetworkRegistry.TargetPoint(
                this.world.provider.getDimension(),
                (double) this.pos.getX() + 0.5D, (double) this.pos.getY() + 0.5D, (double) this.pos.getZ() + 0.5D,
                96.0D));
    }

    public EnumDyeColor getColor() {
        IBlockState s = this.world == null ? null : this.world.getBlockState(this.pos);
        if (s != null && s.getBlock() instanceof BlockRbmkSiren) {
            return (EnumDyeColor) s.getValue(BlockRbmkSiren.COLOR);
        }
        return EnumDyeColor.RED;
    }

    /** One-shot blare used by the terminal 'siren test' command. Does not change the ringing state. */
    public void testBlare() {
        if (this.world == null || this.world.isRemote) {
            return;
        }
        // bypass the MODE_ONCE guard so a test always plays
        boolean oldFired = this.firedOnce;
        this.firedOnce = false;
        this.blare();
        this.firedOnce = oldFired;
    }

    /**
     * Manual override for the terminal 'siren on/off' command. Synced to
     * clients through the tile update packet so the flash light matches.
     */
    public void setManual(boolean on) {
        if (this.world == null || this.world.isRemote || this.manualOn == on) {
            return;
        }
        this.manualOn = on;
        this.markDirty();
        IBlockState state = this.world.getBlockState(this.pos);
        this.world.notifyBlockUpdate(this.pos, state, state, 3);
    }

    public int getPreset() { return this.preset; }
    public float getVolume() { return this.volume; }
    public float getPitch() { return this.pitch; }
    public int getGapTicks() { return this.gapTicks; }
    public int getMode() { return this.mode; }
    public int getCategory() { return this.category; }
    public boolean isFlashLight() { return this.flashLight; }

    public boolean isRinging() {
        return this.world != null && this.world.isRemote ? this.clientPowered : this.ringing;
    }

    public boolean isLightOn() {
        return this.flashLight && isRinging();
    }

    public boolean isRingingForVisual() {
        return isRinging();
    }

    @Override
    public void readFromNBT(NBTTagCompound c) {
        super.readFromNBT(c);
        this.preset = SirenPresets.wrap(c.hasKey("Preset") ? c.getInteger("Preset") : SirenPresets.TWOTONE);
        this.volume = SirenPresets.clampVolume(c.hasKey("Volume") ? c.getFloat("Volume") : 1.0F);
        this.pitch = SirenPresets.clampPitch(c.hasKey("Pitch") ? c.getFloat("Pitch") : 1.0F);
        this.gapTicks = c.hasKey("Gap") ? SirenPresets.clampGap(c.getInteger("Gap"))
                : SirenPresets.defaultGap(this.preset);
        this.mode = SirenPresets.clampMode(c.getInteger("Mode"));
        this.category = SirenPresets.clampCat(c.getInteger("Cat"));
        this.flashLight = !c.hasKey("Flash") || c.getBoolean("Flash");
        this.manualOn = c.getBoolean("Manual");
        this.prevPowered = false;
        this.ringing = false;
        this.firedOnce = false;
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound c) {
        super.writeToNBT(c);
        c.setInteger("Preset", this.preset);
        c.setFloat("Volume", this.volume);
        c.setFloat("Pitch", this.pitch);
        c.setInteger("Gap", this.gapTicks);
        c.setInteger("Mode", this.mode);
        c.setInteger("Cat", this.category);
        c.setBoolean("Flash", this.flashLight);
        c.setBoolean("Manual", this.manualOn);
        return c;
    }

    @Override
    public NBTTagCompound getUpdateTag() {
        return this.writeToNBT(new NBTTagCompound());
    }

    @Override
    public SPacketUpdateTileEntity getUpdatePacket() {
        return new SPacketUpdateTileEntity(this.pos, 0, this.getUpdateTag());
    }

    @Override
    public void onDataPacket(NetworkManager net, SPacketUpdateTileEntity pkt) {
        this.readFromNBT(pkt.getNbtCompound());
    }

    @Override
    public boolean shouldRefresh(World world, BlockPos pos, IBlockState oldState, IBlockState newState) {
        return oldState.getBlock() != newState.getBlock();
    }
}
