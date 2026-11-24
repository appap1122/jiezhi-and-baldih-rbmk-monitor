package com.jiezhirbmkmod.block;

import com.jiezhirbmkmod.network.ModNetwork;
import com.jiezhirbmkmod.network.msg.MessageAggregateSync;
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
 * v17.9 多堆芯聚合中控：值班站长席。
 *
 * <p>交互约定（与巡屏中控同款）：</p>
 * <ul>
 *   <li><b>普通右键</b>：打开聚合排行 GUI（谁都能看 —— 看数不算操作）；</li>
 *   <li><b>潜行 + 右键</b>：服务端扫描半径 {@link TileRbmkAggregate#SCAN_RADIUS}
 *       内最近的屏填进空槽，聊天栏报本次新绑几块；</li>
 *   <li>方块模型渲染（cube_all），与巡屏中控/签发器同款做法。</li>
 * </ul>
 *
 * <p>打开 GUI 前先经 {@link MessageAggregateSync} 下发「屏 → 堆芯」
 * 归属表：屏的 consolePos 是服务端字段不同步到客户端，归堆必须
 * 在服务端算好。</p>
 */
public class BlockRbmkAggregate
extends Block
implements net.minecraft.block.ITileEntityProvider {

    public BlockRbmkAggregate() {
        super(Material.IRON);
        this.setRegistryName("jiezhirbmkmod", "rbmk_aggregate");
        this.setTranslationKey("rbmk_aggregate");
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
        return new TileRbmkAggregate();
    }

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player,
            EnumHand hand, EnumFacing facing, float hitX, float hitY, float hitZ) {
        if (world.isRemote) {
            return true;
        }
        TileEntity te = world.getTileEntity(pos);
        if (!(te instanceof TileRbmkAggregate)) {
            return true;
        }
        TileRbmkAggregate hub = (TileRbmkAggregate) te;
        if (player.isSneaking()) {
            int bound = hub.scanBind(world, pos);
            player.sendStatusMessage(new TextComponentString(TextFormatting.AQUA + "[jiezhirbmkmod] "
                    + net.minecraft.util.text.translation.I18n.translateToLocalFormatted(
                            bound > 0 ? "msg.jiezhirbmkmod.aggregate.bound"
                                      : "msg.jiezhirbmkmod.aggregate.bound_none",
                            Integer.valueOf(bound), hub.validScreens().size())), false);
            return true;
        }
        // 打开 GUI 前先把「屏 → 堆芯」归属表下发（两份清单同序）
        if (player instanceof EntityPlayerMP) {
            ModNetwork.CHANNEL.sendTo(
                    new MessageAggregateSync(hub.validScreens(), hub.reactorKeys()),
                    (EntityPlayerMP) player);
        }
        player.openGui(com.jiezhirbmkmod.RbmkMonitorMod.instance,
                CommonProxy.GUI_AGGREGATE, world, pos.getX(), pos.getY(), pos.getZ());
        return true;
    }

    // 与巡屏中控/签发器同款：方块模型渲染
    @Override
    public net.minecraft.util.EnumBlockRenderType getRenderType(IBlockState state) {
        return net.minecraft.util.EnumBlockRenderType.MODEL;
    }
}
