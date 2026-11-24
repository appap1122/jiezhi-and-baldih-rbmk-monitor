package com.jiezhirbmkmod.block;

import net.minecraft.block.state.IBlockState;
import net.minecraft.item.Item;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

/**
 * v18.0 画面轮播显示屏（普通版）：渲染、GUI 与多方块结构同
 * {@link BlockRbmkScreen}，仅 TE 换成 {@link TileRbmkCycleScreen}
 * 以获得自动轮播页号。免电，与普通屏一致。
 */
public class BlockRbmkCycleScreen extends BlockRbmkScreen {

    public BlockRbmkCycleScreen() {
        super("rbmk_cycle_screen", "rbmk_cycle_screen");
    }

    protected BlockRbmkCycleScreen(String registryName, String unlocalizedName) {
        super(registryName, unlocalizedName);
    }

    @Override
    protected Item displayItem() {
        return ModBlocks.ITEM_RBMK_CYCLE_SCREEN;
    }

    @Override
    public TileEntity createTileEntity(World world, IBlockState state) {
        return new TileRbmkCycleScreen();
    }

    @Override
    public TileEntity createNewTileEntity(World worldIn, int meta) {
        return new TileRbmkCycleScreen();
    }
}
