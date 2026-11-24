package com.jiezhirbmkmod.block;

import net.minecraft.block.state.IBlockState;
import net.minecraft.item.Item;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

/**
 * v17.1 presentation screen: reuses the 4x3 multiblock of
 * {@link BlockRbmkMediaScreen}, but the master tile renders local slide images
 * ({@code .minecraft/rbmk_media/ppt/slide_X.png}) instead of a video feed.
 */
public class BlockRbmkPptScreen
extends BlockRbmkMediaScreen {

    public BlockRbmkPptScreen() {
        super("rbmk_ppt_screen", "rbmk_ppt_screen");
    }

    /**
     * v17.4: needed by {@link BlockRbmkPptScreenReal}. Without it the subclass
     * cannot call its parent and the whole mod fails to compile.
     */
    protected BlockRbmkPptScreen(String registryName, String unlocalizedName) {
        super(registryName, unlocalizedName);
    }

    @Override
    protected Item displayItem() {
        return ModBlocks.ITEM_RBMK_PPT_SCREEN;
    }

    @Override
    public TileEntity createTileEntity(World world, IBlockState state) {
        return new TileRbmkPptScreen();
    }

    @Override
    public TileEntity createNewTileEntity(World worldIn, int meta) {
        return new TileRbmkPptScreen();
    }
}
