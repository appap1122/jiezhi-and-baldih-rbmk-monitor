package com.jiezhimod.block;

import com.jiezhimod.JiezhiMod;
import com.jiezhimod.block.BlockRbmkScreen;
import com.jiezhimod.block.BlockRbmkScreenPart;
import com.jiezhimod.block.TileCustomScreen;
import com.jiezhimod.block.TileRbmkScreen;
import com.jiezhimod.dfc.ScreenStructure;
import com.jiezhimod.item.ItemRbmkRemote;
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
        if (world.func_180495_p(pos).func_177230_c() instanceof BlockRbmkScreen) {
            return pos;
        }
        for (int dy = -3; dy <= 3; ++dy) {
            for (int dz = -4; dz <= 4; ++dz) {
                for (int dx = -4; dx <= 4; ++dx) {
                    BlockPos p = pos.func_177982_a(dx, dy, dz);
                    IBlockState s = world.func_180495_p(p);
                    if (!(s.func_177230_c() instanceof BlockRbmkScreen) || !ScreenMultiblock.covers(p, (EnumFacing)s.func_177229_b(BlockRbmkScreen.FACING), pos)) continue;
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
            Block b = world.func_180495_p(p).func_177230_c();
            if (!(part == 0 ? !(b instanceof BlockRbmkScreen) : !(b instanceof BlockRbmkScreenPart))) continue;
            return false;
        }
        return true;
    }

    public static int countParts(World world, BlockPos controller, EnumFacing facing) {
        int n = 0;
        for (int part = 0; part < 12; ++part) {
            BlockPos p = ScreenStructure.partPos(controller, part, facing);
            if (p == null || !ScreenMultiblock.isPart(world.func_180495_p(p).func_177230_c())) continue;
            ++n;
        }
        return n;
    }

    public static void removeStructure(World world, BlockPos controller, EnumFacing facing, BlockPos except) {
        for (int part = 0; part < 12; ++part) {
            BlockPos p = ScreenStructure.partPos(controller, part, facing);
            if (p == null || p.equals((Object)except) || !ScreenMultiblock.isPart(world.func_180495_p(p).func_177230_c())) continue;
            world.func_175698_g(p);
        }
    }

    public static boolean handleUse(World world, BlockPos pos, EntityPlayer player, EnumHand hand) {
        ItemStack held = player.func_184586_b(hand);
        if (held != null && held.func_77973_b() instanceof ItemRbmkRemote) {
            boolean custom;
            BlockPos cp = ScreenMultiblock.findController(world, pos);
            boolean bl = custom = cp != null && world.func_175625_s(cp) instanceof TileCustomScreen;
            if (!custom || player.func_70093_af()) {
                return ((ItemRbmkRemote)held.func_77973_b()).useOnScreen(held, world, pos, player);
            }
        }
        if (world.field_72995_K) {
            return true;
        }
        BlockPos controllerPos = ScreenMultiblock.findController(world, pos);
        if (controllerPos == null) {
            return false;
        }
        TileEntity te = world.func_175625_s(controllerPos);
        if (te instanceof TileCustomScreen) {
            player.openGui((Object)JiezhiMod.instance, 1, world, controllerPos.func_177958_n(), controllerPos.func_177956_o(), controllerPos.func_177952_p());
            return true;
        }
        if (te instanceof TileRbmkScreen) {
            ((TileRbmkScreen)te).togglePower(player);
            return true;
        }
        return false;
    }
}

