package com.jiezhirbmkmod.block;

import com.jiezhirbmkmod.item.ItemRbmkOpCard;
import com.jiezhirbmkmod.proxy.CommonProxy;

import net.minecraft.block.Block;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.InventoryHelper;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;

/**
 * v17.6 签发器：1x1 的发卡柜台。
 *
 * <p>右键先过管理员门禁（权限等级 2 / 单人房主），通过才打开 GUI；
 * 普通玩家右键只会得到一句红字，方块内容（槽里的卡）不外露 ——
 * 发卡柜台本来就不该让无关人员看见抽屉里的空白卡。</p>
 *
 * <p>方块被拆时槽里的卡会整堆落地（{@code breakBlock}），卡是凭证，
 * 不能凭空消失。</p>
 *
 * <p>v17.8 防熊三重门：GUI 门禁（非管理员看不到界面）、拆除门禁
 * （只有归属者/管理员/创造模式拆得动，越权拆除被拒并留审计痕）、
 * 爆炸免疫（{@code onBlockExploded} 置空，TNT 与苦力怕动不了柜台）。
 * 柜台上的空白卡是签发流程的弹药，只能通过签发流出去，不能落在
 * 地上被人捡走。</p>
 */
public class BlockRbmkIssuer
extends Block
implements net.minecraft.block.ITileEntityProvider {

    public BlockRbmkIssuer() {
        super(Material.IRON);
        this.setRegistryName("jiezhirbmkmod", "rbmk_issuer");
        this.setTranslationKey("rbmk_issuer");
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
        return new TileRbmkIssuer();
    }

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player,
            EnumHand hand, EnumFacing facing, float hitX, float hitY, float hitZ) {
        if (world.isRemote) {
            return true;
        }
        TileEntity te = world.getTileEntity(pos);
        if (!(te instanceof TileRbmkIssuer)) {
            return true;
        }
        // 门禁在打开 GUI 之前：非管理员连界面都看不到
        if (!ItemRbmkOpCard.isTrueOp(player)) {
            player.sendStatusMessage(new TextComponentString(
                    TextFormatting.RED + "[jiezhirbmkmod] "
                            + net.minecraft.util.text.translation.I18n.translateToLocalFormatted(
                                    "msg.jiezhirbmkmod.issuer.denied")), true);
            return true;
        }
        player.openGui(com.jiezhirbmkmod.RbmkMonitorMod.instance,
                CommonProxy.GUI_ISSUER, world, pos.getX(), pos.getY(), pos.getZ());
        return true;
    }

    /** v17.8 防熊：放置即登记归属，归属者有权拆掉自己放的柜台 */
    @Override
    public void onBlockPlacedBy(net.minecraft.world.World world, BlockPos pos,
            IBlockState state, net.minecraft.entity.EntityLivingBase placer,
            net.minecraft.item.ItemStack stack) {
        TileEntity te = world.getTileEntity(pos);
        if (te instanceof TileRbmkIssuer && placer instanceof EntityPlayer) {
            ((TileRbmkIssuer) te).setOwner((EntityPlayer) placer);
        }
    }

    /**
     * v17.8 防熊：越权拆除直接拒绝（返回 false，方块纹丝不动），并留
     * 审计痕 —— 反复来拆的玩家管理员看日志就知道是谁。
     */
    @Override
    public boolean removedByPlayer(IBlockState state, World world, BlockPos pos,
            EntityPlayer player, boolean willHarvest) {
        TileEntity te = world.getTileEntity(pos);
        if (te instanceof TileRbmkIssuer && player != null
                && !((TileRbmkIssuer) te).mayBreak(player)) {
            player.sendStatusMessage(new TextComponentString(
                    TextFormatting.RED + "[jiezhirbmkmod] "
                            + net.minecraft.util.text.translation.I18n.translateToLocalFormatted(
                                    "msg.jiezhirbmkmod.issuer.no_break")), true);
            com.jiezhirbmkmod.dfc.AuditLog.log(player.getName(), "ISSUER_BREAK_DENIED",
                    "at " + pos.getX() + "," + pos.getY() + "," + pos.getZ());
            return false;
        }
        return super.removedByPlayer(state, world, pos, player, willHarvest);
    }

    /**
     * v17.8 防熊：爆炸免疫。柜台不参与任何爆炸破坏 —— 顶多被炸得
     * 抖一下，卡还在柜台里。
     */
    @Override
    public void onBlockExploded(World world, BlockPos pos, net.minecraft.world.Explosion explosion) {
        // 有意留空：不调用 super，方块不被移除
    }

    @Override
    public void breakBlock(World world, BlockPos pos, IBlockState state) {
        TileEntity te = world.getTileEntity(pos);
        if (te instanceof TileRbmkIssuer) {
            // 槽里的空白卡落地：凭证不能凭空消失
            int cards = ((TileRbmkIssuer) te).cardCount();
            if (cards > 0) {
                // v17.7: 审计 —— 签发器被拆且槽里有卡，落盘留痕
                com.jiezhirbmkmod.dfc.AuditLog.log(null, "ISSUER_BROKEN",
                        "at " + pos.getX() + "," + pos.getY() + "," + pos.getZ()
                                + " droppedCards=" + cards);
            }
            InventoryHelper.dropInventoryItems(world, pos, (TileRbmkIssuer) te);
        }
        super.breakBlock(world, pos, state);
    }

    // 与落地终端同款：方块模型渲染
    @Override
    public net.minecraft.util.EnumBlockRenderType getRenderType(IBlockState state) {
        return net.minecraft.util.EnumBlockRenderType.MODEL;
    }
}
