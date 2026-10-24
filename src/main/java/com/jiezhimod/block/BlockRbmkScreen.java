package com.jiezhimod.block;

import com.jiezhimod.block.BlockRbmkScreenPart;
import com.jiezhimod.block.ModBlocks;
import com.jiezhimod.block.ScreenMultiblock;
import com.jiezhimod.block.TileRbmkScreen;
import com.jiezhimod.dfc.ScreenStructure;
import java.util.List;
import java.util.Random;
import javax.annotation.Nullable;
import net.minecraft.block.Block;
import net.minecraft.block.BlockContainer;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.properties.PropertyEnum;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

public class BlockRbmkScreen
extends BlockContainer {
    public static final PropertyEnum<EnumFacing> FACING = PropertyEnum.func_177706_a((String)"facing", EnumFacing.class, (Enum[])new EnumFacing[]{EnumFacing.NORTH, EnumFacing.SOUTH, EnumFacing.WEST, EnumFacing.EAST});

    public BlockRbmkScreen() {
        this("rbmk_screen", "rbmk_screen");
    }

    protected BlockRbmkScreen(String registryName, String unlocalizedName) {
        super(Material.field_151573_f);
        this.setRegistryName("jiezhimod", registryName);
        this.func_149663_c(unlocalizedName);
        this.func_149711_c(2.0f);
        this.func_149752_b(12.0f);
        this.func_149672_a(SoundType.field_185852_e);
        this.setHarvestLevel("pickaxe", 0);
        this.func_149715_a(0.35f);
        this.func_149647_a(ModBlocks.TAB_SCREEN);
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

    public IBlockState getStateForPlacement(World world, BlockPos pos, EnumFacing facing, float hitX, float hitY, float hitZ, int meta, EntityLivingBase placer, EnumHand hand) {
        EnumFacing f = placer.func_174811_aO().func_176734_d();
        if (f.func_176740_k() == EnumFacing.Axis.Y) {
            f = EnumFacing.NORTH;
        }
        return this.func_176223_P().func_177226_a(FACING, (Comparable)f);
    }

    public void func_180633_a(World world, BlockPos pos, IBlockState state, EntityLivingBase placer, ItemStack stack) {
        if (world.field_72995_K) {
            return;
        }
        EnumFacing f = (EnumFacing)state.func_177229_b(FACING);
        for (int part = 1; part < 12; ++part) {
            BlockPos bp = ScreenStructure.partPos(pos, part, f);
            if (bp != null && world.func_180495_p(bp).func_177230_c().func_176200_f((IBlockAccess)world, bp)) continue;
            this.cancelPlacement(world, pos, placer);
            return;
        }
        IBlockState partState = ModBlocks.RBMK_SCREEN_PART.func_176223_P().func_177226_a(FACING, (Comparable)f);
        for (int part = 1; part < 12; ++part) {
            BlockPos bp = ScreenStructure.partPos(pos, part, f);
            world.func_180501_a(bp, partState, 3);
        }
        TileEntity te = world.func_175625_s(pos);
        if (te instanceof TileRbmkScreen) {
            TileRbmkScreen t = (TileRbmkScreen)te;
            t.setTilt(ScreenStructure.tiltFromPitch(placer.field_70125_A));
            t.powerOn();
        }
        if (placer instanceof EntityPlayer) {
            ((EntityPlayer)placer).func_146105_b((ITextComponent)new TextComponentString(TextFormatting.AQUA + "[jiezhimod] " + TextFormatting.RESET + "Display mounted (4x3, hanging down from here). Right-click to power on, or bind an RBMK console with the RBMK remote."), false);
        }
    }

    private void cancelPlacement(World world, BlockPos pos, EntityLivingBase placer) {
        world.func_175698_g(pos);
        if (placer instanceof EntityPlayer) {
            EntityPlayer p = (EntityPlayer)placer;
            ItemStack give = new ItemStack(this.displayItem());
            if (!p.field_71071_by.func_70441_a(give)) {
                p.func_71019_a(give, false);
            }
            p.func_146105_b((ITextComponent)new TextComponentString(TextFormatting.RED + "[jiezhimod] Not enough space for a 4x3 display."), false);
        }
    }

    protected Item displayItem() {
        return ModBlocks.ITEM_RBMK_SCREEN;
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public void func_180663_b(World world, BlockPos pos, IBlockState state) {
        super.func_180663_b(world, pos, state);
        if (world.field_72995_K || ScreenMultiblock.removing) {
            return;
        }
        ScreenMultiblock.removing = true;
        try {
            ScreenMultiblock.removeStructure(world, pos, (EnumFacing)state.func_177229_b(FACING), pos);
        }
        finally {
            ScreenMultiblock.removing = false;
        }
    }

    public Item func_180660_a(IBlockState state, Random rand, int fortune) {
        return this.displayItem();
    }

    public int func_149745_a(Random random) {
        return 1;
    }

    public boolean func_180639_a(World world, BlockPos pos, IBlockState state, EntityPlayer player, EnumHand hand, EnumFacing facing, float hitX, float hitY, float hitZ) {
        return ScreenMultiblock.handleUse(world, pos, player, hand);
    }

    public boolean hasTileEntity(IBlockState state) {
        return true;
    }

    public TileEntity createTileEntity(World world, IBlockState state) {
        return new TileRbmkScreen();
    }

    public TileEntity func_149915_a(World worldIn, int meta) {
        return new TileRbmkScreen();
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

    public AxisAlignedBB func_185496_a(IBlockState state, IBlockAccess world, BlockPos pos) {
        return BlockRbmkScreenPart.plateBox((EnumFacing)state.func_177229_b(FACING));
    }

    @Nullable
    public AxisAlignedBB func_180646_a(IBlockState state, IBlockAccess world, BlockPos pos) {
        return BlockRbmkScreenPart.plateBox((EnumFacing)state.func_177229_b(FACING));
    }

    public void func_176213_c(World world, BlockPos pos, IBlockState state) {
        super.func_176213_c(world, pos, state);
        world.func_184138_a(pos, state, state, 3);
    }

    @SideOnly(value=Side.CLIENT)
    public void func_190948_a(ItemStack stack, @Nullable World world, List<String> tooltip, ITooltipFlag flag) {
        tooltip.add(TextFormatting.GRAY + I18n.func_135052_a((String)"tooltip.jiezhimod.rbmk_screen.0", (Object[])new Object[]{12}));
        tooltip.add(TextFormatting.GRAY + I18n.func_135052_a((String)"tooltip.jiezhimod.rbmk_screen.1", (Object[])new Object[0]));
        tooltip.add(TextFormatting.GRAY + I18n.func_135052_a((String)"tooltip.jiezhimod.rbmk_screen.2", (Object[])new Object[0]));
        tooltip.add(TextFormatting.AQUA + I18n.func_135052_a((String)"tooltip.jiezhimod.rbmk_screen.3", (Object[])new Object[0]));
        tooltip.add(TextFormatting.AQUA + I18n.func_135052_a((String)"tooltip.jiezhimod.rbmk_screen.4", (Object[])new Object[0]));
    }
}

