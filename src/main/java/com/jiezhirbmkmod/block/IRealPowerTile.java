package com.jiezhirbmkmod.block;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * v17.4 common surface of every "real" tile that has a mains switch.
 *
 * <p>Two unrelated tile classes need it: {@link TileRbmkMonitorScreen} (which
 * carries the seven 4x3 screens) and {@link TileRbmkTerminal} (the floor box).
 * They have no shared superclass beyond {@code TileEntity}, so the status text
 * and the supply check are written against this interface instead of against
 * either of them.</p>
 */
public interface IRealPowerTile {

    /** {@link RealPower#OFF}, {@link RealPower#STANDBY} or {@link RealPower#ON}. */
    int getRealPowerState();

    void setRealPowerState(int state);

    /** FE currently in the internal buffer. */
    int getRealEnergy();

    void setRealEnergy(int energy);

    /** v18.0 HE currently in the independent HBM (mains) buffer. */
    long getHeEnergy();

    void setHeEnergy(long energy);

    /** v18.0 电源来源指示：{@link RealPower#SOURCE_NONE}/{@code SOURCE_HE}/{@code SOURCE_FE}。 */
    int getPowerSource();

    World getWorld();

    BlockPos getPos();

    /** Language key of the short type name used in chat, e.g. "显示屏". */
    String getRealNameKey();

    /** True once the tile has been ticked at least once (guards null world). */
    boolean isRealReady();
}
