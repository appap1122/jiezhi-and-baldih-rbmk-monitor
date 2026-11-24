package com.jiezhirbmkmod.block;

/**
 * v17.4 marker for the "real" screen family.
 *
 * <p>These blocks look and behave exactly like their parent screens, but the
 * tile refuses to draw anything until the block is fed: redstone power of at
 * least 10 AND a non-empty Forge Energy buffer. A freshly placed real screen is
 * powered down (black panel) instead of booting straight into standby.</p>
 *
 * <p>The check inside the tile is a plain {@code instanceof} on the block, so
 * it keeps working for the part blocks of a 4x3 multiblock as long as the
 * controller is the one holding the tile.</p>
 */
public interface IRealScreen {
}
