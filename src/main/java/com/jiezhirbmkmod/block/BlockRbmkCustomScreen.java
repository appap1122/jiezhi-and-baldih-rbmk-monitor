package com.jiezhirbmkmod.block;

import com.jiezhirbmkmod.block.BlockRbmkScreen;
import com.jiezhirbmkmod.block.ModBlocks;
import com.jiezhirbmkmod.block.TileCustomScreen;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

public class BlockRbmkCustomScreen
extends BlockRbmkScreen {
    public BlockRbmkCustomScreen() {
        super("rbmk_custom", "rbmk_custom");
    }

    @Override
    protected Item displayItem() {
        return ModBlocks.ITEM_RBMK_CUSTOM;
    }

    @Override
    public TileEntity createTileEntity(World world, IBlockState state) {
        return new TileCustomScreen();
    }

    @Override
    public TileEntity createNewTileEntity(World worldIn, int meta) {
        return new TileCustomScreen();
    }

    @Override
    @SideOnly(value=Side.CLIENT)
    public void addInformation(ItemStack stack, @Nullable World world, List<String> tooltip, ITooltipFlag flag) {
        tooltip.add(TextFormatting.GRAY + I18n.format((String)"tooltip.jiezhirbmkmod.rbmk_custom.0", (Object[])new Object[]{12}));
        tooltip.add(TextFormatting.GRAY + I18n.format((String)"tooltip.jiezhirbmkmod.rbmk_custom.1", (Object[])new Object[0]));
        tooltip.add(TextFormatting.AQUA + I18n.format((String)"tooltip.jiezhirbmkmod.rbmk_custom.2", (Object[])new Object[0]));
        tooltip.add(TextFormatting.AQUA + I18n.format((String)"tooltip.jiezhirbmkmod.rbmk_custom.3", (Object[])new Object[]{8}));
        tooltip.add(TextFormatting.AQUA + I18n.format((String)"tooltip.jiezhirbmkmod.rbmk_custom.4", (Object[])new Object[0]));
        tooltip.add(TextFormatting.DARK_GRAY + I18n.format((String)"tooltip.jiezhirbmkmod.rbmk_custom.5", (Object[])new Object[0]));
    }
}

