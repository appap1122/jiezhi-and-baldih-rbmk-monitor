package com.jiezhirbmkmod.block;


import com.jiezhirbmkmod.network.NetGuard;
import com.jiezhirbmkmod.network.ModNetwork;
import com.jiezhirbmkmod.network.msg.MessagePatrolSync;
import com.jiezhirbmkmod.proxy.CommonProxy;

import net.minecraft.block.Block;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;

/**
 * v17.7 巡屏中控：值班长席位。
 *
 * <p>交互约定：</p>
 * <ul>
 *   <li><b>普通右键</b>：打开巡屏 GUI（谁都能看 —— 巡屏是查看设备，
 *       不是权限凭证）；</li>
 *   <li><b>潜行 + 右键</b>：服务端扫描半径 {@link TileRbmkPatrol#SCAN_RADIUS}
 *       内最近的屏填进空槽，聊天栏报本次新绑几块；</li>
 *   <li>方块模型渲染（cube_all），与签发器同款做法。</li>
 * </ul>
 */
public class BlockRbmkPatrol
extends Block
implements net.minecraft.block.ITileEntityProvider {

    public BlockRbmkPatrol() {
        super(Material.IRON);
        this.setRegistryName("jiezhirbmkmod", "rbmk_patrol");
        this.setTranslationKey("rbmk_patrol");
        this.setHardness(2.5F);
        this.setResistance(8.0F);
        this.setSoundType(SoundType.METAL);
        this.setCreativeTab(ModBlocks.TAB_SCREEN);
    }

    @Override
    public boolean hasTileEntity(IBlockState state) {
        return true;
    }

    @Override
    public TileEntity createNewTileEntity(World world, int meta) {
        return new TileRbmkPatrol();
    }

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player,
            EnumHand hand, EnumFacing facing, float hitX, float hitY, float hitZ) {
        if (world.isRemote) {
            return true;
        }
        TileEntity te = world.getTileEntity(pos);
        if (!(te instanceof TileRbmkPatrol)) {
            return true;
        }
        TileRbmkPatrol patrol = (TileRbmkPatrol) te;
        if (player.isSneaking()) {
            int bound = patrol.scanBind(world, pos);
            player.sendStatusMessage(new TextComponentString(TextFormatting.AQUA + "[jiezhirbmkmod] "
                    + net.minecraft.util.text.translation.I18n.translateToLocalFormatted(
                            bound > 0 ? "msg.jiezhirbmkmod.patrol.bound"
                                      : "msg.jiezhirbmkmod.patrol.bound_none",
                            Integer.valueOf(bound), patrol.validScreens().size())), false);
            return true;
        }
        // 打开 GUI 前先把绑定清单下发：方块实体字段不同步，专用包是正路
        if (player instanceof EntityPlayerMP) {
            NetGuard.sendTo(
                    new MessagePatrolSync(patrol.validScreens()), (EntityPlayerMP) player);
        }
        player.openGui(com.jiezhirbmkmod.RbmkMonitorMod.instance,
                CommonProxy.GUI_PATROL, world, pos.getX(), pos.getY(), pos.getZ());
        return true;
    }

    // 与签发器同款：方块模型渲染
    @Override
    public net.minecraft.util.EnumBlockRenderType getRenderType(IBlockState state) {
        return net.minecraft.util.EnumBlockRenderType.MODEL;
    }
}
