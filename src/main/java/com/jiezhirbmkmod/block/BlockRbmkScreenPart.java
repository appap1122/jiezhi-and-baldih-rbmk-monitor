package com.jiezhirbmkmod.block;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;

import net.minecraft.block.Block;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyBool;
import net.minecraft.block.properties.PropertyEnum;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import com.jiezhirbmkmod.dfc.AlertPolicy;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

public class BlockRbmkScreenPart extends Block {
    
    public static final PropertyEnum<EnumFacing> FACING = PropertyEnum.create("facing", EnumFacing.class, EnumFacing.NORTH, EnumFacing.SOUTH, EnumFacing.WEST, EnumFacing.EAST);
    public static final PropertyBool POWERED = PropertyBool.create("powered");
    private static final double HALF_THICK = 0.125;

    public BlockRbmkScreenPart() {
        super(Material.IRON);
        this.setRegistryName("jiezhirbmkmod", "rbmk_screen_part");
        this.setTranslationKey("rbmk_screen_part");
        this.setHardness(2.0f);
        this.setResistance(12.0f);
        this.setSoundType(SoundType.METAL);
        this.setHarvestLevel("pickaxe", 0);
        this.setLightLevel(0.35f);
        this.setDefaultState(this.blockState.getBaseState().withProperty(FACING, EnumFacing.NORTH).withProperty(POWERED, Boolean.valueOf(false)));
    }

    @Override
    protected BlockStateContainer createBlockState() {
        return new BlockStateContainer(this, FACING, POWERED);
    }

    @Override
    public int getMetaFromState(IBlockState state) {
        int i = state.getValue(FACING).getHorizontalIndex();
        return state.getValue(POWERED).booleanValue() ? i | 4 : i;
    }

    @Override
    public IBlockState getStateFromMeta(int meta) {
        return this.getDefaultState()
                .withProperty(FACING, EnumFacing.byHorizontalIndex(meta & 3))
                .withProperty(POWERED, Boolean.valueOf((meta & 4) != 0));
    }

    @Override
    public void breakBlock(World world, BlockPos pos, IBlockState state) {
        super.breakBlock(world, pos, state);
        if (world.isRemote || ScreenMultiblock.removing) {
            return;
        }
        BlockPos controller = ScreenMultiblock.findController(world, pos);
        if (controller == null) {
            return;
        }
        IBlockState cs = world.getBlockState(controller);
        if (!(cs.getBlock() instanceof BlockRbmkScreen)) {
            return;
        }
        ScreenMultiblock.removing = true;
        try {
            ScreenMultiblock.removeStructure(world, controller, cs.getValue(BlockRbmkScreen.FACING), pos);
        } finally {
            ScreenMultiblock.removing = false;
        }
    }

    @Override
    public List<ItemStack> getDrops(IBlockAccess world, BlockPos pos, IBlockState state, int fortune) {
        ArrayList<ItemStack> drops = new ArrayList<>();
        BlockPos controller = ScreenMultiblock.findController((World) world, pos);
        if (controller != null) {
            Block controllerBlock = world.getBlockState(controller).getBlock();
            if (controllerBlock instanceof BlockRbmkScreen) {
                drops.add(new ItemStack(((BlockRbmkScreen) controllerBlock).displayItem()));
            }
        }
        return drops;
    }

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player, EnumHand hand, EnumFacing facing, float hitX, float hitY, float hitZ) {
        BlockPos controller = ScreenMultiblock.findController(world, pos);
        if (controller != null) {
            return ScreenMultiblock.handleUse(world, controller, player, hand);
        }
        return false;
    }

    @Override
    public boolean canProvidePower(IBlockState state) {
        return true;
    }

    @Override
    public int getWeakPower(IBlockState state, IBlockAccess world, BlockPos pos, EnumFacing side) {
        return ((Boolean) state.getValue(POWERED)).booleanValue() ? AlertPolicy.RS_STRENGTH : 0;
    }

    @Override
    public int getStrongPower(IBlockState state, IBlockAccess world, BlockPos pos, EnumFacing side) {
        return this.getWeakPower(state, world, pos, side) > 0
                && side == ((EnumFacing) state.getValue(FACING)).getOpposite() ? AlertPolicy.RS_STRENGTH : 0;
    }

    @Override
    public EnumBlockRenderType getRenderType(IBlockState state) {
        return EnumBlockRenderType.INVISIBLE;
    }

    @Override
    public boolean isOpaqueCube(IBlockState state) {
        return false;
    }

    @Override
    public boolean isFullCube(IBlockState state) {
        return false;
    }

    @Override
    public boolean isNormalCube(IBlockState state, IBlockAccess world, BlockPos pos) {
        return false;
    }

    @Override
    public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess world, BlockPos pos) {
        return plateBox(state.getValue(FACING));
    }

    @Nullable
    @Override
    public AxisAlignedBB getCollisionBoundingBox(IBlockState state, IBlockAccess world, BlockPos pos) {
        return plateBox(state.getValue(FACING));
    }

    public static AxisAlignedBB plateBox(EnumFacing facing) {
        switch (facing) {
            case NORTH: return new AxisAlignedBB(0.0, 0.0, 1.0 - HALF_THICK, 1.0, 1.0, 1.0);
            case SOUTH: return new AxisAlignedBB(0.0, 0.0, 0.0, 1.0, 1.0, HALF_THICK);
            case WEST:  return new AxisAlignedBB(1.0 - HALF_THICK, 0.0, 0.0, 1.0, 1.0, 1.0);
            case EAST:  return new AxisAlignedBB(0.0, 0.0, 0.0, HALF_THICK, 1.0, 1.0);
            default:    return FULL_BLOCK_AABB;
        }
    }
}
