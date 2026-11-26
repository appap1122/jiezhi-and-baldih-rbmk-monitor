package com.jiezhirbmkmod.entity;

import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.item.EntityFallingBlock;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * A falling-block entity that can also be launched upwards. Used for the RBMK
 * cap that gets blown off during a meltdown steam explosion.
 *
 * <p>v18.2：新增「落地即散」模式——碎片雨的一部分方块落地时不落成方块，
 * 直接碎掉消失（小概率留一簇余火），模拟 HBM 那种「碎片砸散在地上」
 * 的观感。该标志只在服务端逻辑里被读取（{@code onUpdate} 的落地方块
 * 分支本就只在服务端跑），不需要额外同步。</p>
 */
public class EntityFlyingBlock
extends EntityFallingBlock {

    /** v18.2 落地即散（true = 落地不放置方块、直接消失） */
    private boolean vanishOnLand;
    /** v18.2 落地消失时留火的概率（百分比，0~100） */
    private int emberChance = 12;

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

    /** v18.2 标记这块碎片落地时是否直接碎散消失（不放置方块）。 */
    public void setVanishOnLand(boolean vanish) {
        this.vanishOnLand = vanish;
    }

    public boolean isVanishOnLand() {
        return this.vanishOnLand;
    }

    @Override
    public void onUpdate() {
        // v18.2 落地即散：必须在 super 之前接住，否则 EntityFallingBlock
        // 会先把方块放回世界再 setDead。只在服务端处理；客户端实体会随
        // 服务端 setDead 后由跟踪系统自然移除，无需同步任何状态。
        if (this.vanishOnLand && !this.world.isRemote && this.onGround) {
            this.shatter();
            return;
        }
        super.onUpdate();
    }

    /** 落地碎散：消失 + 小概率在落点留一簇余火。 */
    private void shatter() {
        try {
            this.setDead();
            if (this.world.rand.nextInt(100) < this.emberChance) {
                BlockPos p = new BlockPos(this.posX, this.posY + 0.5D, this.posZ);
                if (this.world.isAirBlock(p)
                        && this.world.getBlockState(p.down()).getBlock().isFullCube(this.world.getBlockState(p.down()))) {
                    this.world.setBlockState(p, Blocks.FIRE.getDefaultState());
                }
            }
            // 碎散尘土：少量烟粒子标记落点
            if (this.world instanceof net.minecraft.world.WorldServer) {
                ((net.minecraft.world.WorldServer) this.world).spawnParticle(
                        net.minecraft.util.EnumParticleTypes.SMOKE_NORMAL,
                        this.posX, this.posY + 0.3D, this.posZ,
                        (this.world.rand.nextDouble() - 0.5D) * 0.05D, 0.05D,
                        (this.world.rand.nextDouble() - 0.5D) * 0.05D);
            }
        }
        catch (Throwable ignored) {
            // 碎散装饰逻辑绝不能把实体更新线程炸了
            this.setDead();
        }
    }
}
