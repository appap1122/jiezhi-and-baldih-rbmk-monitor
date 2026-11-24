package com.jiezhirbmkmod.block;

import com.jiezhirbmkmod.RbmkMonitorMod;
import com.jiezhirbmkmod.block.BlockRbmkScreen;
import com.jiezhirbmkmod.block.BlockRbmkScreenPart;
import com.jiezhirbmkmod.block.TileCustomScreen;
import com.jiezhirbmkmod.block.TileRbmkMonitorScreen;
import com.jiezhirbmkmod.dfc.ScreenStructure;
import com.jiezhirbmkmod.item.ItemRefreshTuner;
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
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
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
        if (world == null || pos == null || player == null) {
            return false;
        }
        ItemStack held = player.getHeldItem(hand);
        if (held != null && held.getItem() instanceof ItemRefreshTuner && player.isSneaking()) {
            // sneak + right-click tunes this screen instead of toggling its power
            BlockPos controllerPos = ScreenMultiblock.findController(world, pos);
            if (controllerPos == null) {
                return false;
            }
            return ((ItemRefreshTuner) held.getItem()).useOnScreen(held, world, controllerPos, player);
        }
        // v17.6 配置拷贝卡。读是潜行 + 右键，写是普通右键 —— 普通屏的普通
        // 右键本来开 GUI、真实屏的普通右键本来拨电源，卡在这里先接住：
        // 拿着卡的人这一下就是要拷配置，交换一下手就行，不算抢。
        if (held != null && held.getItem() instanceof com.jiezhirbmkmod.item.ItemRbmkConfigCard) {
            BlockPos controllerPos = ScreenMultiblock.findController(world, pos);
            if (controllerPos == null) {
                return false;
            }
            return ((com.jiezhirbmkmod.item.ItemRbmkConfigCard) held.getItem())
                    .useOnScreen(held, world, controllerPos, player);
        }
        // v17.8 配方模板卡：与配置拷贝卡同一条路由，语义同款（潜行右键读、
        // 普通右键写），只是搬的东西从 15 个调屏键扩到整张配方。
        if (held != null && held.getItem() instanceof com.jiezhirbmkmod.item.ItemRbmkRecipeCard) {
            BlockPos controllerPos = ScreenMultiblock.findController(world, pos);
            if (controllerPos == null) {
                return false;
            }
            return ((com.jiezhirbmkmod.item.ItemRbmkRecipeCard) held.getItem())
                    .useOnScreen(held, world, controllerPos, player);
        }
        // v17.6 告警扩音器。只有潜行 + 右键才绑定；普通右键必须 return false
        // 交回去，否则拿着喇叭点真实屏就拨不动电源了。
        if (held != null && held.getItem() instanceof com.jiezhirbmkmod.item.ItemRbmkSirenHorn
                && player.isSneaking()) {
            BlockPos controllerPos = ScreenMultiblock.findController(world, pos);
            if (controllerPos == null) {
                return false;
            }
            return ((com.jiezhirbmkmod.item.ItemRbmkSirenHorn) held.getItem())
                    .bindOnScreen(held, world, controllerPos, player);
        }
        if (held != null && held.getItem() instanceof ItemRbmkRemote) {
            BlockPos cp = ScreenMultiblock.findController(world, pos);
            boolean custom = cp != null && world.getTileEntity(cp) instanceof TileCustomScreen;
            if (!custom || player.isSneaking()) {
                // the remote always consumes the click: a failed bind has to
                // report why instead of falling through to the GUI branch
                ((ItemRbmkRemote) held.getItem()).useOnScreen(held, world, pos, player);
                return true;
            }
        }
        // v17.9 蹲下对屏连续右键两下 = 打开历史导出设置面板（纯客户端 GUI）。
        // 必须放在所有持卡分支之后：手里拿着调屏器/拷贝卡/配方卡/扩音器/
        // 遥控器时，潜行右键仍是它们自己的语义，绝不被双击劫持。客户端
        // 每下右键的交互包都已先行发出，所以双击的两下服务端各拨一次电源
        // （净状态不变，提示走 action-bar 一闪而过）；第一下永远交回正常
        // 路由。GuiRbmkExport 只在 isRemote 分支被触达（isRemote 先短路），
        // 专用服务器不会解析这个客户端类。
        if (world.isRemote && player.isSneaking() && hand == EnumHand.MAIN_HAND
                && com.jiezhirbmkmod.client.gui.GuiRbmkExport.tryOpen(world, pos, player)) {
            return true;
        }
        try {
            if (world.isRemote) {
                // the server performs the actual GUI opening, the client only
                // has to consume the click
                return true;
            }
            BlockPos controllerPos = ScreenMultiblock.findController(world, pos);
            if (controllerPos == null) {
                return false;
            }
            TileEntity te = world.getTileEntity(controllerPos);

            // v17.4 real screens. The check has to sit in front of the custom
            // and terminal branches, because those tiles are subclasses of
            // TileRbmkMonitorScreen as well and would otherwise swallow the
            // click before the mains switch ever sees it.
            //
            // v17.4.1: the tile is not trusted on its own. The block at the
            // controller position has to be an IRealScreen too, otherwise a
            // stale tile left behind by a broken/removed screen would eat the
            // very first click on every ordinary display and make the remote
            // look like it cannot bind anything.
            if (te instanceof TileRbmkMonitorScreen
                    && ((TileRbmkMonitorScreen) te).isRealScreen()
                    && world.getBlockState(controllerPos).getBlock() instanceof IRealScreen) {
                TileRbmkMonitorScreen real = (TileRbmkMonitorScreen) te;
                if (player.isSneaking()) {
                    // sneak is the way in to the settings GUI, and only while
                    // the panel actually has mains; a dead screen stays dead
                    if (real.getRealPowerState() == RealPower.OFF) {
                        RealPowerStatus.send(player, RealPowerStatus.refused(real));
                        return true;
                    }
                    // fall through to the normal per-type GUI routing below
                } else {
                    real.handleRealClick(player);
                    return true;
                }
            }

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
            if (te instanceof TileRbmkMonitorScreen) {
                // plain right-click always opens the screen settings GUI; the
                // power switch is on sneak + right-click so the GUI can never
                // be "swallowed" by the power path
                if (player.isSneaking()) {
                    try {
                        ((TileRbmkMonitorScreen)te).togglePower(player);
                    }
                    catch (Throwable t) {
                        // a broken data source must never leave the click dead
                        RbmkMonitorMod.warn("[ScreenMultiblock] power toggle failed at {}: {}", controllerPos, t);
                    }
                    return true;
                }
                player.openGui(RbmkMonitorMod.instance, CommonProxy.GUI_ALERT, world,
                        controllerPos.getX(), controllerPos.getY(), controllerPos.getZ());
                return true;
            }
            // no usable tile entity: report it instead of silently doing nothing
            player.sendStatusMessage((ITextComponent)new TextComponentString(TextFormatting.RED
                    + "[jiezhirbmkmod] Screen data is not ready, replace the display."), false);
            return true;
        }
        catch (Throwable t) {
            RbmkMonitorMod.warn("[ScreenMultiblock] interaction failed at {}: {}", pos, t);
            return true;
        }
    }
}

