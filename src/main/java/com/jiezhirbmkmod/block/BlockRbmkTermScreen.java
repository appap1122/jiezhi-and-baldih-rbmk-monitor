package com.jiezhirbmkmod.block;

import com.jiezhirbmkmod.block.ScreenMultiblock;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class BlockRbmkTermScreen extends BlockRbmkScreen {
    public BlockRbmkTermScreen() {
        super("rbmk_term_screen", "rbmk_term_screen");
    }

    // BUGFIX (v11.1): this class never overrode the tile factory, so placing the
    // block spawned the parent's TileRbmkMonitorScreen instead of the terminal
    // tile. The client GUI handler (and the command executor) check for
    // TileRbmkTerminal / TileRbmkTermScreen, so right-clicking - normal or
    // sneaking - never opened the terminal UI and commands were rejected.
    @Override
    public TileEntity createTileEntity(World world, IBlockState state) {
        return new TileRbmkTermScreen();
    }

    @Override
    public TileEntity createNewTileEntity(World worldIn, int meta) {
        return new TileRbmkTermScreen();
    }

    // Route every click (controller block or any part, sneaking or not)
    // through the shared multiblock handler: remote still binds the console,
    // everything else opens the terminal GUI.
    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player, EnumHand hand, EnumFacing facing, float hitX, float hitY, float hitZ) {
        return ScreenMultiblock.handleUse(world, pos, player, hand);
    }
}
