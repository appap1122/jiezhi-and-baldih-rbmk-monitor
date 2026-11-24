package com.jiezhirbmkmod.block;

import com.jiezhirbmkmod.RbmkMonitorMod;
import com.jiezhirbmkmod.dfc.SirenPresets;
import com.jiezhirbmkmod.proxy.CommonProxy;
import java.util.List;
import java.util.Random;
import javax.annotation.Nullable;
import net.minecraft.block.Block;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyEnum;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.EnumDyeColor;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

public class BlockRbmkSiren extends Block {

    public static final PropertyEnum<net.minecraft.item.EnumDyeColor> COLOR =
            PropertyEnum.create("color", net.minecraft.item.EnumDyeColor.class);

    private static final int LIGHT_RINGING = 12;

    public BlockRbmkSiren() {
        super(Material.IRON);
        this.setRegistryName("jiezhirbmkmod", "rbmk_siren");
        this.setTranslationKey("rbmk_siren");
        this.setHardness(2.0F);
        this.setResistance(10.0F);
        this.setSoundType(SoundType.METAL);
        this.setHarvestLevel("pickaxe", 0);
        this.setCreativeTab(ModBlocks.TAB_SCREEN);
        this.setDefaultState(this.blockState.getBaseState().withProperty(COLOR, net.minecraft.item.EnumDyeColor.RED));
    }

    @Override
    protected BlockStateContainer createBlockState() {
        return new BlockStateContainer(this, COLOR);
    }

    @Override
    public int getMetaFromState(IBlockState state) {
        return ((net.minecraft.item.EnumDyeColor) state.getValue(COLOR)).ordinal();
    }

    @Override
    public IBlockState getStateFromMeta(int meta) {
        return this.getDefaultState().withProperty(COLOR, dye(meta));
    }

    public static net.minecraft.item.EnumDyeColor dye(int meta) {
        net.minecraft.item.EnumDyeColor[] all = net.minecraft.item.EnumDyeColor.values();
        int i = meta & 15;
        return i < all.length ? all[i] : net.minecraft.item.EnumDyeColor.RED;
    }

    @Override
    public IBlockState getStateForPlacement(World world, BlockPos pos, EnumFacing facing, float hitX, float hitY,
            float hitZ, int meta, EntityLivingBase placer, EnumHand hand) {
        return this.getDefaultState().withProperty(COLOR, dye(meta));
    }

    // ---- TileEntity ----

    @Override
    public boolean hasTileEntity(IBlockState state) {
        return true;
    }

    @Override
    public TileEntity createTileEntity(World world, IBlockState state) {
        return new TileRbmkSiren();
    }

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player, EnumHand hand,
            EnumFacing facing, float hitX, float hitY, float hitZ) {
        if (world.isRemote) {
            return true;
        }
        ItemStack held = player.getHeldItem(hand);
        net.minecraft.item.EnumDyeColor dyeFromHeld = dyeOf(held);
        if (dyeFromHeld != null) {
            IBlockState old = world.getBlockState(pos);
            if (((EnumDyeColor) old.getValue(COLOR)) != dyeFromHeld) {
                world.setBlockState(pos, old.withProperty(COLOR, dyeFromHeld), 3);
                if (!player.capabilities.isCreativeMode) {
                    held.shrink(1);
                }
                player.sendStatusMessage((ITextComponent) new TextComponentString(TextFormatting.AQUA
                        + "[jiezhirbmkmod] " + TextFormatting.RESET + "警报器颜色：" + dyeName(dyeFromHeld)), true);
            }
            return true;
        }
        player.openGui(RbmkMonitorMod.instance, CommonProxy.GUI_SIREN, world,
                pos.getX(), pos.getY(), pos.getZ());
        return true;
    }

    @Nullable
    private static net.minecraft.item.EnumDyeColor dyeOf(ItemStack stack) {
        if (stack.isEmpty() || stack.getItem() != net.minecraft.init.Items.DYE) {
            return null;
        }
        return net.minecraft.item.EnumDyeColor.byMetadata(stack.getMetadata());
    }

    private static String dyeName(net.minecraft.item.EnumDyeColor d) {
        return TextFormatting.WHITE + d.getName();
    }

    @Override
    public int getLightValue(IBlockState state, IBlockAccess world, BlockPos pos) {
        if (world == null || pos == null) {
            return 0;
        }
        TileEntity te = world.getTileEntity(pos);
        return te instanceof TileRbmkSiren && ((TileRbmkSiren) te).isLightOn() ? LIGHT_RINGING : 0;
    }

    @Override
    public int getLightValue(IBlockState state) {
        return 0;   
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
    @SideOnly(Side.CLIENT)
    public void randomDisplayTick(IBlockState stateIn, World worldIn, BlockPos pos, Random rand) {
        TileEntity te = worldIn.getTileEntity(pos);
        if (!(te instanceof TileRbmkSiren) || !((TileRbmkSiren) te).isRingingForVisual()) {
            return;
        }
        double x = pos.getX() + 0.5D + (rand.nextDouble() - 0.5D) * 0.6D;
        double y = pos.getY() + 0.82D;
        double z = pos.getZ() + 0.5D + (rand.nextDouble() - 0.5D) * 0.6D;
        if (rand.nextInt(3) == 0) {
            worldIn.spawnParticle(net.minecraft.util.EnumParticleTypes.VILLAGER_HAPPY, x, y, z, 0.0D, 0.01D, 0.0D);
        } else {
            worldIn.spawnParticle(net.minecraft.util.EnumParticleTypes.CRIT_MAGIC, x, y, z, 0.0D, 0.02D, 0.0D);
        }
    }

    @Override
    public void getSubBlocks(CreativeTabs tab, net.minecraft.util.NonNullList<ItemStack> list) {
        net.minecraft.item.EnumDyeColor[] all = net.minecraft.item.EnumDyeColor.values();
        for (int i = 0; i < all.length; ++i) {
            list.add(new ItemStack(this.displayItem(), 1, i));
        }
    }

    protected Item displayItem() {
        return ModBlocks.ITEM_RBMK_SIREN;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(ItemStack stack, @Nullable World world, List<String> tooltip, ITooltipFlag flag) {
        tooltip.add(TextFormatting.GRAY + I18n.format("tooltip.jiezhirbmkmod.rbmk_siren.0"));
        tooltip.add(TextFormatting.GRAY + I18n.format("tooltip.jiezhirbmkmod.rbmk_siren.1"));
        tooltip.add(TextFormatting.AQUA + I18n.format("tooltip.jiezhirbmkmod.rbmk_siren.2"));
        tooltip.add(TextFormatting.AQUA + I18n.format("tooltip.jiezhirbmkmod.rbmk_siren.3",
                Integer.valueOf(SirenPresets.COUNT)));
        tooltip.add(TextFormatting.DARK_GRAY + I18n.format("tooltip.jiezhirbmkmod.rbmk_siren.4"));
        net.minecraft.item.EnumDyeColor d = dye(stack.getMetadata());
        tooltip.add(TextFormatting.WHITE + I18n.format("tooltip.jiezhirbmkmod.rbmk_siren.5", d.getName()));
    }
}
