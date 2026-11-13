package com.jiezhirbmkmod.entity;

import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.item.EntityFallingBlock;
import net.minecraft.world.World;

/**
 * A falling-block entity that can also be launched upwards. Used for the RBMK
 * cap that gets blown off during a meltdown steam explosion.
 */
public class EntityFlyingBlock
extends EntityFallingBlock {

    public EntityFlyingBlock(World worldIn) {
        super(worldIn);
    }

    public EntityFlyingBlock(World worldIn, double x, double y, double z, IBlockState state) {
        super(worldIn, x, y, z, state);
    }

    /** Launch the block with an initial upward velocity plus a little horizontal spread. */
    public void launch(double up, double spread) {
        this.motionY = up;
        this.motionX = (this.world.rand.nextDouble() - 0.5D) * 2.0D * spread;
        this.motionZ = (this.world.rand.nextDouble() - 0.5D) * 2.0D * spread;
    }
}
