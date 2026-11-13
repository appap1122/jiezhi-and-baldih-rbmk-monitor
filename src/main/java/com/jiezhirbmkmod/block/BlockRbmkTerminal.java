package com.jiezhirbmkmod.block;

import com.jiezhirbmkmod.RbmkMonitorMod;
import com.jiezhirbmkmod.dfc.RbmkBridge;
import com.jiezhirbmkmod.dfc.RbmkConfig;
import com.jiezhirbmkmod.item.ItemRbmkRemote;
import com.jiezhirbmkmod.proxy.CommonProxy;

import java.util.List;

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
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Floor-standing RBMK terminal: one full block, plain block texture, no
 * content drawn on the block itself. Right-click opens the command prompt
 * GUI (keyboard input, Tab completion, like the other screens' hardware).
 *
 * Binding ("connecting to the reactor"):
 *   - right-click an RBMK console with the RBMK remote to record it, then
 *     right-click this block with the remote to link them, or
 *   - just type 'bind' in the terminal GUI while standing near a console.
 */
public class BlockRbmkTerminal extends BlockContainer {

    public static final PropertyEnum<EnumFacing> FACING = PropertyEnum.create("facing", EnumFacing.class,
            EnumFacing.NORTH, EnumFacing.SOUTH, EnumFacing.WEST, EnumFacing.EAST);

    public BlockRbmkTerminal() {
        super(Material.IRON);
        this.setRegistryName("jiezhirbmkmod", "rbmk_terminal");
        this.setTranslationKey("rbmk_terminal");
        this.setHardness(2.0f);
        this.setResistance(12.0f);
        this.setSoundType(SoundType.METAL);
        this.setHarvestLevel("pickaxe", 0);
        this.setLightLevel(0.25f);
        this.setCreativeTab(ModBlocks.TAB_SCREEN);
        this.setDefaultState(this.blockState.getBaseState().withProperty(FACING, EnumFacing.NORTH));
    }

    @Override
    protected BlockStateContainer createBlockState() {
        return new BlockStateContainer(this, FACING);
    }

    @Override
    public int getMetaFromState(IBlockState state) {
        return state.getValue(FACING).getHorizontalIndex();
    }

    @Override
    public IBlockState getStateFromMeta(int meta) {
        return this.getDefaultState().withProperty(FACING, EnumFacing.byHorizontalIndex(meta & 3));
    }

    @Override
    public IBlockState getStateForPlacement(World world, BlockPos pos, EnumFacing facing, float hitX, float hitY, float hitZ, int meta, EntityLivingBase placer, EnumHand hand) {
        return this.getDefaultState().withProperty(FACING, placer.getHorizontalFacing().getOpposite());
    }

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player, EnumHand hand, EnumFacing facing, float hitX, float hitY, float hitZ) {
        if (world.isRemote) {
            return true;
        }
        TileEntity te = world.getTileEntity(pos);
        if (!(te instanceof TileRbmkTerminal)) {
            return true;
        }
        TileRbmkTerminal term = (TileRbmkTerminal) te;
        ItemStack held = player.getHeldItem(hand);
        if (held != null && held.getItem() instanceof ItemRbmkRemote) {
            BlockPos src = ItemRbmkRemote.readTarget(held);
            if (src == null) {
                player.sendStatusMessage((ITextComponent) new TextComponentString(TextFormatting.AQUA
                        + "[jiezhirbmkmod] " + TextFormatting.YELLOW
                        + "The remote has no source recorded. Right-click an RBMK console with it first."), false);
            } else {
                TileEntity srcTe = RbmkBridge.resolveDataSource(world, src);
                BlockPos console = RbmkBridge.isConsole(srcTe)
                        ? srcTe.getPos()
                        : RbmkBridge.findConsoleNear(world, srcTe != null ? srcTe.getPos() : src,
                                RbmkConfig.consoleSearchRadius);
                if (console == null) {
                    player.sendStatusMessage((ITextComponent) new TextComponentString(TextFormatting.AQUA
                            + "[jiezhirbmkmod] " + TextFormatting.RED + "Recorded source has no console nearby."), false);
                } else {
                    term.bindConsole(console);
                    player.sendStatusMessage((ITextComponent) new TextComponentString(TextFormatting.AQUA
                            + "[jiezhirbmkmod] " + TextFormatting.GREEN + "Terminal linked to RBMK console @ "
                            + console.getX() + " " + console.getY() + " " + console.getZ()
                            + TextFormatting.GRAY + " (right-click the terminal to open the command prompt)"), false);
                }
            }
            return true;
        }
        player.openGui((Object) RbmkMonitorMod.instance, CommonProxy.GUI_TERMINAL, world, pos.getX(), pos.getY(), pos.getZ());
        return true;
    }

    @Override
    public boolean hasTileEntity(IBlockState state) {
        return true;
    }

    // BlockContainer defaults to INVISIBLE, this one uses its block model
    @Override
    public net.minecraft.util.EnumBlockRenderType getRenderType(IBlockState state) {
        return net.minecraft.util.EnumBlockRenderType.MODEL;
    }

    @Override
    public TileEntity createTileEntity(World world, IBlockState state) {
        return new TileRbmkTerminal();
    }

    @Override
    public TileEntity createNewTileEntity(World worldIn, int meta) {
        return new TileRbmkTerminal();
    }

    @Override
    public void breakBlock(World world, BlockPos pos, IBlockState state) {
        super.breakBlock(world, pos, state);
        world.removeTileEntity(pos);
    }

    @SideOnly(value=Side.CLIENT)
    @Override
    public void addInformation(ItemStack stack, @Nullable World world, List<String> tooltip, ITooltipFlag flag) {
        tooltip.add(TextFormatting.GRAY + I18n.format("tooltip.jiezhirbmkmod.rbmk_terminal.0"));
        tooltip.add(TextFormatting.GRAY + I18n.format("tooltip.jiezhirbmkmod.rbmk_terminal.1"));
        tooltip.add(TextFormatting.AQUA + I18n.format("tooltip.jiezhirbmkmod.rbmk_terminal.2"));
        tooltip.add(TextFormatting.AQUA + I18n.format("tooltip.jiezhirbmkmod.rbmk_terminal.3"));
        tooltip.add(TextFormatting.DARK_GRAY + I18n.format("tooltip.jiezhirbmkmod.rbmk_terminal.4"));
    }
}
