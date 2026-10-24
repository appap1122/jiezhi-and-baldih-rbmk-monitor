package com.jiezhimod.block;

import com.jiezhimod.block.BlockRbmkScreen;
import com.jiezhimod.block.ModBlocks;
import com.jiezhimod.block.ScreenMultiblock;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import javax.annotation.Nullable;
import net.minecraft.block.Block;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.properties.PropertyEnum;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

public class BlockRbmkScreenPart
extends Block {
    public static final PropertyEnum<EnumFacing> FACING = PropertyEnum.func_177706_a((String)"facing", EnumFacing.class, (Enum[])new EnumFacing[]{EnumFacing.NORTH, EnumFacing.SOUTH, EnumFacing.WEST, EnumFacing.EAST});
    private static final double HALF_THICK = 0.125;

    public BlockRbmkScreenPart() {
        super(Material.field_151573_f);
        this.setRegistryName("jiezhimod", "rbmk_screen_part");
        this.func_149663_c("rbmk_screen_part");
        this.func_149711_c(2.0f);
        this.func_149752_b(12.0f);
        this.func_149672_a(SoundType.field_185852_e);
        this.setHarvestLevel("pickaxe", 0);
        this.func_149715_a(0.35f);
        this.func_180632_j(this.field_176227_L.func_177621_b().func_177226_a(FACING, (Comparable)EnumFacing.NORTH));
    }

    protected BlockStateContainer func_180661_e() {
        return new BlockStateContainer((Block)this, new IProperty[]{FACING});
    }

    public int func_176201_c(IBlockState state) {
        return ((EnumFacing)state.func_177229_b(FACING)).func_176736_b();
    }

    public IBlockState func_176203_a(int meta) {
        return this.func_176223_P().func_177226_a(FACING, (Comparable)EnumFacing.func_176731_b((int)(meta & 3)));
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public void func_180663_b(World world, BlockPos pos, IBlockState state) {
        super.func_180663_b(world, pos, state);
        if (world.field_72995_K || ScreenMultiblock.removing) {
            return;
        }
        BlockPos controller = ScreenMultiblock.findController(world, pos);
        if (controller == null) {
            return;
        }
        IBlockState cs = world.func_180495_p(controller);
        if (!(cs.func_177230_c() instanceof BlockRbmkScreen)) {
            return;
        }
        ScreenMultiblock.removing = true;
        try {
            ScreenMultiblock.removeStructure(world, controller, (EnumFacing)cs.func_177229_b(BlockRbmkScreen.FACING), pos);
        }
        finally {
            ScreenMultiblock.removing = false;
        }
    }

    public List<ItemStack> getDrops(IBlockAccess world, BlockPos pos, IBlockState state, int fortune) {
        Block controllerBlock;
        BlockPos controller;
        ArrayList<ItemStack> drops = new ArrayList<ItemStack>();
        ItemBlock item = ModBlocks.ITEM_RBMK_SCREEN;
        if (world instanceof World && (controller = ScreenMultiblock.findController((World)world, pos)) != null && (controllerBlock = world.func_180495_p(controller).func_177230_c()) instanceof BlockRbmkScreen) {
            item = ((BlockRbmkScreen)controllerBlock).displayItem();
        }
        drops.add(new ItemStack((Item)item, 1));
        return drops;
    }

    public Item func_180660_a(IBlockState state, Random rand, int fortune) {
        return ModBlocks.ITEM_RBMK_SCREEN;
    }

    public int func_149745_a(Random random) {
        return 1;
    }

    public boolean func_180639_a(World world, BlockPos pos, IBlockState state, EntityPlayer player, EnumHand hand, EnumFacing facing, float hitX, float hitY, float hitZ) {
        return ScreenMultiblock.handleUse(world, pos, player, hand);
    }

    public EnumBlockRenderType func_149645_b(IBlockState state) {
        return EnumBlockRenderType.INVISIBLE;
    }

    public boolean func_149662_c(IBlockState state) {
        return false;
    }

    public boolean func_149686_d(IBlockState state) {
        return false;
    }

    public boolean isNormalCube(IBlockState state, IBlockAccess world, BlockPos pos) {
        return false;
    }

    static AxisAlignedBB plateBox(EnumFacing f) {
        double t = 0.125;
        switch (f) {
            case EAST: 
            case WEST: {
                return new AxisAlignedBB(0.5 - t, 0.0, 0.0, 0.5 + t, 1.0, 1.0);
            }
        }
        return new AxisAlignedBB(0.0, 0.0, 0.5 - t, 1.0, 1.0, 0.5 + t);
    }

    public AxisAlignedBB func_185496_a(IBlockState state, IBlockAccess world, BlockPos pos) {
        return BlockRbmkScreenPart.plateBox((EnumFacing)state.func_177229_b(FACING));
    }

    @Nullable
    public AxisAlignedBB func_180646_a(IBlockState state, IBlockAccess world, BlockPos pos) {
        return BlockRbmkScreenPart.plateBox((EnumFacing)state.func_177229_b(FACING));
    }
}

