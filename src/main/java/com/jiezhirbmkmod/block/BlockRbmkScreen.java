package com.jiezhirbmkmod.block;

import com.jiezhirbmkmod.block.BlockRbmkScreenPart;
import com.jiezhirbmkmod.block.ModBlocks;
import com.jiezhirbmkmod.block.ScreenMultiblock;
import com.jiezhirbmkmod.block.TileRbmkMonitorScreen;
import com.jiezhirbmkmod.dfc.ScreenStructure;

import java.util.List;
import java.util.Random;
import javax.annotation.Nullable;

import net.minecraft.block.Block;
import net.minecraft.block.BlockContainer;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.properties.PropertyBool;
import net.minecraft.block.properties.PropertyEnum;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumParticleTypes;
import com.jiezhirbmkmod.dfc.AlertPolicy;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

public class BlockRbmkScreen extends BlockContainer {
    
    public static final PropertyEnum<EnumFacing> FACING = PropertyEnum.create("facing", EnumFacing.class, EnumFacing.NORTH, EnumFacing.SOUTH, EnumFacing.WEST, EnumFacing.EAST);
    public static final PropertyBool POWERED = PropertyBool.create("powered");

    public BlockRbmkScreen() {
        this("rbmk_screen", "rbmk_screen");
    }

    protected BlockRbmkScreen(String registryName, String unlocalizedName) {
        super(Material.IRON);
        this.setRegistryName("jiezhirbmkmod", registryName);
        this.setTranslationKey(unlocalizedName);
        this.setHardness(2.0f);
        this.setResistance(12.0f);
        this.setSoundType(SoundType.METAL);
        this.setHarvestLevel("pickaxe", 0);
        this.setLightLevel(0.35f);
        this.setCreativeTab(ModBlocks.TAB_SCREEN);
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
    public IBlockState getStateForPlacement(World world, BlockPos pos, EnumFacing facing, float hitX, float hitY, float hitZ, int meta, EntityLivingBase placer, EnumHand hand) {
        EnumFacing f = placer.getHorizontalFacing().getOpposite();
        if (f.getAxis() == EnumFacing.Axis.Y) {
            f = EnumFacing.NORTH;
        }
        return this.getDefaultState().withProperty(FACING, f);
    }

    @Override
    public void onBlockPlacedBy(World world, BlockPos pos, IBlockState state, EntityLivingBase placer, ItemStack stack) {
        if (world.isRemote) {
            return;
        }
        EnumFacing f = state.getValue(FACING);
        for (int part = 1; part < 12; ++part) {
            BlockPos bp = ScreenStructure.partPos(pos, part, f);
            if (bp != null && world.getBlockState(bp).getBlock().isReplaceable(world, bp)) continue;
            this.cancelPlacement(world, pos, placer);
            return;
        }
        IBlockState partState = ModBlocks.RBMK_SCREEN_PART.getDefaultState().withProperty(FACING, f);
        for (int part = 1; part < 12; ++part) {
            BlockPos bp = ScreenStructure.partPos(pos, part, f);
            world.setBlockState(bp, partState, 3);
        }
        TileEntity te = world.getTileEntity(pos);
        if (te instanceof TileRbmkMonitorScreen) {
            TileRbmkMonitorScreen t = (TileRbmkMonitorScreen)te;
            t.setTilt(ScreenStructure.tiltFromPitch(placer.rotationPitch));
            if (this.isRealVariant()) {
                // v17.4: a freshly mounted real screen is dead until it is wired
                // up, never standby - the panel must not look alive before the
                // mains are actually connected
                t.setRealPowerState(RealPower.OFF);
            } else {
                t.powerOn();
            }
        }
        if (placer instanceof EntityPlayer) {
            ((EntityPlayer)placer).sendStatusMessage(this.isRealVariant()
                    ? new TextComponentTranslation("msg.jiezhirbmkmod.real.placed")
                    : new TextComponentString(this.placedMessage()), false);
        }
    }

    /**
     * True for the v17.4 real family. Every {@link IRealScreen} block answers
     * true without having to override anything, so the eight subclasses stay
     * pure "change the registry name" declarations.
     */
    protected boolean isRealVariant() {
        return this instanceof IRealScreen;
    }

    protected String placedMessage() {
        return TextFormatting.AQUA + "[jiezhirbmkmod] " + TextFormatting.RESET + "Display mounted (4x3, hanging down from here). Right-click to open the settings, sneak + right-click to power on, or bind an RBMK console with the RBMK remote.";
    }

    private void cancelPlacement(World world, BlockPos pos, EntityLivingBase placer) {
        world.setBlockToAir(pos);
        if (placer instanceof EntityPlayer) {
            EntityPlayer p = (EntityPlayer)placer;
            ItemStack give = new ItemStack(this.displayItem());
            if (!p.inventory.addItemStackToInventory(give)) {
                p.dropItem(give, false);
            }
            p.sendStatusMessage(new TextComponentString(TextFormatting.RED + "[jiezhirbmkmod] Not enough space for a 4x3 display."), false);
        }
    }

    protected Item displayItem() {
        return ModBlocks.ITEM_RBMK_SCREEN;
    }

    @Override
    public void breakBlock(World world, BlockPos pos, IBlockState state) {
        super.breakBlock(world, pos, state);
        if (world.isRemote || ScreenMultiblock.removing) {
            return;
        }
        ScreenMultiblock.removing = true;
        try {
            ScreenMultiblock.removeStructure(world, pos, state.getValue(FACING), pos);
        } finally {
            ScreenMultiblock.removing = false;
        }
    }

    @Override
    public Item getItemDropped(IBlockState state, Random rand, int fortune) {
        return this.displayItem();
    }

    @Override
    public int quantityDropped(Random random) {
        return 1;
    }

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player, EnumHand hand, EnumFacing facing, float hitX, float hitY, float hitZ) {
        return ScreenMultiblock.handleUse(world, pos, player, hand);
    }

    @Override
    public boolean hasTileEntity(IBlockState state) {
        return true;
    }

    @Override
    public TileEntity createTileEntity(World world, IBlockState state) {
        return new TileRbmkMonitorScreen();
    }

    @Override
    public TileEntity createNewTileEntity(World worldIn, int meta) {
        return new TileRbmkMonitorScreen();
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
        return BlockRbmkScreenPart.plateBox(state.getValue(FACING));
    }

    @Nullable
    @Override
    public AxisAlignedBB getCollisionBoundingBox(IBlockState state, IBlockAccess world, BlockPos pos) {
        return BlockRbmkScreenPart.plateBox(state.getValue(FACING));
    }

    @Override
    public void onBlockAdded(World world, BlockPos pos, IBlockState state) {
        super.onBlockAdded(world, pos, state);
        world.notifyBlockUpdate(pos, state, state, 3);
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
    @SideOnly(Side.CLIENT)
    public void randomDisplayTick(IBlockState state, World world, BlockPos pos, Random rand) {
        TileEntity te = world.getTileEntity(pos);
        if (!(te instanceof TileRbmkMonitorScreen)) {
            return;
        }
        TileRbmkMonitorScreen t = (TileRbmkMonitorScreen) te;
        if (!t.isPowered()) {
            return;
        }
        // v17.4: a real screen with the mains cut must not smoke or spark -
        // the alarm particles belong to a live panel only
        if (!t.isShowingPicture()) {
            return;
        }
        int lvl = t.getAlertLevel();
        if (lvl < AlertPolicy.LEVEL_WARN) {
            return;
        }
        EnumFacing f = (EnumFacing) state.getValue(FACING);
        double front = 0.09D;
        double px = pos.getX() + 0.5D + f.getDirectionVec().getX() * front;
        double pz = pos.getZ() + 0.5D + f.getDirectionVec().getZ() * front;
        double py = pos.getY() + 0.75D + rand.nextDouble() * 1.5D;
        boolean crit = lvl >= AlertPolicy.LEVEL_CRIT;
        if (crit && rand.nextInt(3) == 0) {
            world.spawnParticle(EnumParticleTypes.SMOKE_LARGE, px, py, pz, 0.0D, 0.012D, 0.0D);
        }
        if (crit && rand.nextInt(7) == 0) {
            world.spawnParticle(EnumParticleTypes.END_ROD, px, py, pz,
                    (rand.nextDouble() - 0.5D) * 0.02D, 0.01D, (rand.nextDouble() - 0.5D) * 0.02D);
        } else if (!crit && rand.nextInt(9) == 0) {
            world.spawnParticle(EnumParticleTypes.REDSTONE, px, py, pz,
                    0.0D, 0.008D, 0.0D, new int[]{0xFF4040});
        }
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void addInformation(ItemStack stack, @Nullable World world, List<String> tooltip, ITooltipFlag flag) {
        tooltip.add(TextFormatting.GRAY + I18n.format("tooltip.jiezhirbmkmod.rbmk_screen.0", 12));
        tooltip.add(TextFormatting.GRAY + I18n.format("tooltip.jiezhirbmkmod.rbmk_screen.1"));
        tooltip.add(TextFormatting.GRAY + I18n.format("tooltip.jiezhirbmkmod.rbmk_screen.2"));
        tooltip.add(TextFormatting.AQUA + I18n.format("tooltip.jiezhirbmkmod.rbmk_screen.3"));
        tooltip.add(TextFormatting.AQUA + I18n.format("tooltip.jiezhirbmkmod.rbmk_screen.4"));
    }
}
