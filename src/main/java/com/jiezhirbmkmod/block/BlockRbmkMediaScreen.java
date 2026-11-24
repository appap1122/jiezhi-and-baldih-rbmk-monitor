package com.jiezhirbmkmod.block;

import net.minecraft.block.state.IBlockState;
import net.minecraft.item.Item;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

/**
 * 4x3 multimedia display. Reuses the screen multiblock machinery of
 * {@link BlockRbmkScreen}: placing the controller spawns the 11 part blocks,
 * one master tile entity owns the playback state.
 */
public class BlockRbmkMediaScreen
extends BlockRbmkScreen {

    public BlockRbmkMediaScreen() {
        this("rbmk_media_screen", "rbmk_media_screen");
    }

    protected BlockRbmkMediaScreen(String registryName, String unlocalizedName) {
        super(registryName, unlocalizedName);
    }

    @Override
    protected Item displayItem() {
        return ModBlocks.ITEM_RBMK_MEDIA_SCREEN;
    }

    @Override
    public TileEntity createTileEntity(World world, IBlockState state) {
        return new TileRbmkMediaScreen();
    }

    @Override
    public TileEntity createNewTileEntity(World worldIn, int meta) {
        return new TileRbmkMediaScreen();
    }
}
