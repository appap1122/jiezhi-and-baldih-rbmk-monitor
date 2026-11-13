package com.jiezhirbmkmod.block;

import com.jiezhirbmkmod.RbmkMonitorMod;
import com.jiezhirbmkmod.block.BlockRbmkScreen;
import com.jiezhirbmkmod.block.BlockRbmkScreenPart;
import com.jiezhirbmkmod.block.TileCustomScreen;
import com.jiezhirbmkmod.block.TileRbmkMonitorScreen;
import com.jiezhirbmkmod.dfc.ScreenStructure;
import com.jiezhirbmkmod.item.ItemRbmkRemote;
import com.jiezhirbmkmod.proxy.CommonProxy;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public final class ScreenMultiblock {
    static boolean removing = false;

    private ScreenMultiblock() {
    }

    public static boolean isPart(Block block) {
        return block instanceof BlockRbmkScreen || block instanceof BlockRbmkScreenPart;
    }

    public static boolean covers(BlockPos controller, EnumFacing facing, BlockPos target) {
        for (int part = 0; part < 12; ++part) {
            BlockPos p = ScreenStructure.partPos(controller, part, facing);
            if (p == null || !p.equals((Object)target)) continue;
            return true;
        }
        return false;
    }

    public static BlockPos findController(World world, BlockPos pos) {
        if (world.getBlockState(pos).getBlock() instanceof BlockRbmkScreen) {
            return pos;
        }
        for (int dy = -3; dy <= 3; ++dy) {
            for (int dz = -4; dz <= 4; ++dz) {
                for (int dx = -4; dx <= 4; ++dx) {
                    BlockPos p = pos.add(dx, dy, dz);
                    IBlockState s = world.getBlockState(p);
                    if (!(s.getBlock() instanceof BlockRbmkScreen) || !ScreenMultiblock.covers(p, (EnumFacing)s.getValue(BlockRbmkScreen.FACING), pos)) continue;
                    return p;
                }
            }
        }
        return null;
    }

    public static boolean structureIntact(World world, BlockPos controller, EnumFacing facing) {
        for (int part = 0; part < 12; ++part) {
            BlockPos p = ScreenStructure.partPos(controller, part, facing);
            if (p == null) {
                return false;
            }
            Block b = world.getBlockState(p).getBlock();
            if (!(part == 0 ? !(b instanceof BlockRbmkScreen) : !(b instanceof BlockRbmkScreenPart))) continue;
            return false;
        }
        return true;
    }

    public static int countParts(World world, BlockPos controller, EnumFacing facing) {
        int n = 0;
        for (int part = 0; part < 12; ++part) {
            BlockPos p = ScreenStructure.partPos(controller, part, facing);
            if (p == null || !ScreenMultiblock.isPart(world.getBlockState(p).getBlock())) continue;
            ++n;
        }
        return n;
    }

    public static void removeStructure(World world, BlockPos controller, EnumFacing facing, BlockPos except) {
        for (int part = 0; part < 12; ++part) {
            BlockPos p = ScreenStructure.partPos(controller, part, facing);
            if (p == null || p.equals((Object)except) || !ScreenMultiblock.isPart(world.getBlockState(p).getBlock())) continue;
            world.setBlockToAir(p);
        }
    }

    public static boolean handleUse(World world, BlockPos pos, EntityPlayer player, EnumHand hand) {
        ItemStack held = player.getHeldItem(hand);
        if (held != null && held.getItem() instanceof ItemRbmkRemote) {
            boolean custom;
            BlockPos cp = ScreenMultiblock.findController(world, pos);
            boolean bl = custom = cp != null && world.getTileEntity(cp) instanceof TileCustomScreen;
            if (!custom || player.isSneaking()) {
                return ((ItemRbmkRemote)held.getItem()).useOnScreen(held, world, pos, player);
            }
        }
        if (world.isRemote) {
            return true;
        }
        BlockPos controllerPos = ScreenMultiblock.findController(world, pos);
        if (controllerPos == null) {
            return false;
        }
        TileEntity te = world.getTileEntity(controllerPos);
        if (te instanceof TileCustomScreen) {
            player.openGui((Object)RbmkMonitorMod.instance, 1, world, controllerPos.getX(), controllerPos.getY(), controllerPos.getZ());
            return true;
        }
        // BUGFIX (v11.1): the terminal screen used to be checked after the
        // sneak branch, so sneaking + right-click opened the alert settings
        // GUI instead of the terminal. The terminal GUI opens both when
        // sneaking and when standing normally.
        if (te instanceof TileRbmkTermScreen) {
            player.openGui((Object)RbmkMonitorMod.instance, CommonProxy.GUI_TERMINAL, world, controllerPos.getX(), controllerPos.getY(), controllerPos.getZ());
            return true;
        }
        if (player.isSneaking()) {
            player.openGui(RbmkMonitorMod.instance, CommonProxy.GUI_ALERT, world,
                    controllerPos.getX(), controllerPos.getY(), controllerPos.getZ());
            return true;
        }
        if (te instanceof TileRbmkMonitorScreen) {
            ((TileRbmkMonitorScreen)te).togglePower(player);
            return true;
        }
        return false;
    }
}

