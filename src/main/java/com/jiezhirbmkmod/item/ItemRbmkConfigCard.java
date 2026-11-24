package com.jiezhirbmkmod.item;

import java.util.List;

import javax.annotation.Nullable;

import com.jiezhirbmkmod.block.TileCustomScreen;
import com.jiezhirbmkmod.block.TileRbmkMediaScreen;
import com.jiezhirbmkmod.block.TileRbmkMonitorScreen;
import com.jiezhirbmkmod.block.TileRbmkPptScreen;
import com.jiezhirbmkmod.block.TileRbmkTermScreen;

import net.minecraft.client.resources.I18n;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.Style;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;

/**
 * v17.6 配置拷贝卡：把一块屏的"调好的那套"搬到另一块屏上。
 *
 * <p>一面屏墙最繁的操作不是放置，而是逐块重设：四项告警阈值、汽笛开关、
 * 红石输出条件、刷新档位、数据绑定。这套东西全部存在每块屏自己的 NBT 里
 * （见 {@link TileRbmkMonitorScreen#readFromNBT}），本来只能靠 GUI 一格一格点。
 * 拷贝卡把这套 NBT 子集整个搬进物品，再整块写进下一块屏 —— 调好一块，
 * 后面九块就是九下右键。</p>
 *
 * <p>交互：</p>
 * <ul>
 *   <li><b>潜行 + 右键屏幕</b>：读取。把该屏的配置子集存进卡的 {@code Cfg} 标签。</li>
 *   <li><b>右键屏幕</b>：写入。以屏当前 NBT 为底，覆盖卡里的子集再灌回去 ——
 *       只动拷贝的键，屏幕其余状态（供电、历史、消息）原样保留。</li>
 *   <li><b>潜行 + 右键空气</b>：清空卡。</li>
 * </ul>
 *
 * <p>拷贝的键分四组，全部是"调屏"语义：</p>
 * <ol>
 *   <li>告警阈值四项 + 汽笛开关（{@code WarnC/CritC/RateD/FluxW/Siren}，
 *       由 {@code AlertPolicy} 定义）；</li>
 *   <li>红石输出条件（{@code RsMode/RsValue/RsInvert}）；</li>
 *   <li>刷新档位（{@code RefreshMs/RefreshTier/BaseRefreshMs}）；</li>
 *   <li>数据绑定（{@code Src/Console/BoundDim}）与自动停堆开关
 *       （{@code AutoScramDisabled}）。</li>
 * </ol>
 *
 * <p>有意不拷贝的：供电状态（{@code RealPower/RealEnergy}，电是现场的事）、
 * 自定义屏的外挂设备槽（槽位绑定的是那块屏背后的记录设备，跨屏粘贴
 * 大概率指错对象）、方向与倾斜。卡的写入路径是"覆盖子集"而不是
 * "整体克隆"，所以新旧屏的方块实体类不同也不碍事。</p>
 *
 * <p>路由：与刷新率调节器同一条入口 —— {@code ScreenMultiblock.handleUse}，
 * 多方块任意一格右键都会解析到锚点屏再交进来。</p>
 */
public class ItemRbmkConfigCard
extends Item {

    /** 卡内配置子集的标签名 */
    private static final String K_CFG = "Cfg";

    /**
     * 拷贝的 NBT 键。顺序即文档顺序：阈值、汽笛、红石、刷新、停堆、绑定。
     * 加新键时同步更新类注释里的分组说明。
     */
    private static final String[] KEYS = {
            "WarnC", "CritC", "RateD", "FluxW", "Siren",
            "RsMode", "RsValue", "RsInvert",
            "RefreshMs", "RefreshTier", "BaseRefreshMs",
            "AutoScramDisabled",
            "Src", "Console", "BoundDim"};

    public ItemRbmkConfigCard() {
        this.setRegistryName("jiezhirbmkmod", "rbmk_config_card");
        this.setTranslationKey("rbmk_config_card");
        this.setMaxStackSize(1);
        this.setCreativeTab(ModItems.TAB);
    }

    /**
     * 读/写入口，由 {@code ScreenMultiblock.handleUse} 在服务端调用。
     * {@code controllerPos} 已经是锚点屏的坐标。
     */
    public boolean useOnScreen(ItemStack stack, World world, BlockPos pos, EntityPlayer player) {
        if (world.isRemote) {
            return true;
        }
        TileEntity te = world.getTileEntity(pos);
        if (!(te instanceof TileRbmkMonitorScreen)) {
            player.sendStatusMessage(new TextComponentString(
                    TextFormatting.RED + "[jiezhirbmkmod] "
                            + net.minecraft.util.text.translation.I18n.translateToLocalFormatted(
                                    "msg.jiezhirbmkmod.cfg.notscreen")), true);
            return true;
        }
        TileRbmkMonitorScreen screen = (TileRbmkMonitorScreen) te;
        if (player.isSneaking()) {
            this.readFromScreen(stack, screen, pos, player);
        } else {
            this.writeToScreen(stack, screen, pos, player);
        }
        return true;
    }

    /** 潜行 + 右键：屏幕 NBT 里的配置子集 → 卡 */
    private void readFromScreen(ItemStack stack, TileRbmkMonitorScreen screen, BlockPos pos, EntityPlayer player) {
        NBTTagCompound src = screen.writeToNBT(new NBTTagCompound());
        NBTTagCompound cfg = new NBTTagCompound();
        for (int i = 0; i < ItemRbmkConfigCard.KEYS.length; ++i) {
            String key = ItemRbmkConfigCard.KEYS[i];
            if (src.hasKey(key)) {
                cfg.setTag(key, src.getTag(key).copy());
            }
        }
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null) {
            tag = new NBTTagCompound();
            stack.setTagCompound(tag);
        }
        tag.setTag(ItemRbmkConfigCard.K_CFG, cfg);
        player.sendStatusMessage(this.line(pos)
                .appendSibling(new TextComponentTranslation(ItemRefreshTuner.screenKeyOf(screen)))
                .appendSibling(new TextComponentString(" "))
                .appendSibling(new TextComponentTranslation("msg.jiezhirbmkmod.cfg.read")
                        .setStyle(new Style().setColor(TextFormatting.GREEN))), false);
    }

    /** 右键：卡里的子集覆盖到屏幕 NBT，再灌回方块实体 */
    private void writeToScreen(ItemStack stack, TileRbmkMonitorScreen screen, BlockPos pos, EntityPlayer player) {
        NBTTagCompound tag = stack.getTagCompound();
        NBTTagCompound cfg = tag == null ? null : tag.getCompoundTag(ItemRbmkConfigCard.K_CFG);
        if (cfg == null || cfg.isEmpty()) {
            player.sendStatusMessage(new TextComponentString(
                    TextFormatting.RED + "[jiezhirbmkmod] "
                            + net.minecraft.util.text.translation.I18n.translateToLocalFormatted(
                                    "msg.jiezhirbmkmod.cfg.empty")), true);
            return;
        }
        // 以屏当前 NBT 为底，只覆盖卡里有的键 —— 屏幕自己的供电、历史、消息不动
        NBTTagCompound merged = screen.writeToNBT(new NBTTagCompound());
        for (int i = 0; i < ItemRbmkConfigCard.KEYS.length; ++i) {
            String key = ItemRbmkConfigCard.KEYS[i];
            if (cfg.hasKey(key)) {
                merged.setTag(key, cfg.getTag(key).copy());
            }
        }
        screen.readFromNBT(merged);
        screen.markDirty();
        player.sendStatusMessage(this.line(pos)
                .appendSibling(new TextComponentTranslation(ItemRefreshTuner.screenKeyOf(screen)))
                .appendSibling(new TextComponentString(" "))
                .appendSibling(new TextComponentTranslation("msg.jiezhirbmkmod.cfg.write")
                        .setStyle(new Style().setColor(TextFormatting.GREEN))), false);
    }

    /** 潜行 + 右键空气：清空卡 */
    @Override
    public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, EnumHand hand) {
        ItemStack stack = player.getHeldItem(hand);
        if (!world.isRemote && player.isSneaking() && stack.hasTagCompound()
                && stack.getTagCompound().hasKey(ItemRbmkConfigCard.K_CFG)) {
            stack.getTagCompound().removeTag(ItemRbmkConfigCard.K_CFG);
            player.sendStatusMessage(new TextComponentString(
                    TextFormatting.GOLD + "[jiezhirbmkmod] "
                            + net.minecraft.util.text.translation.I18n.translateToLocalFormatted(
                                    "msg.jiezhirbmkmod.cfg.cleared")), true);
        }
        return new ActionResult<ItemStack>(EnumActionResult.SUCCESS, stack);
    }

    private static ITextComponent line(BlockPos pos) {
        return new TextComponentString(TextFormatting.AQUA + "[jiezhirbmkmod] "
                + TextFormatting.GRAY + "（x: " + pos.getX() + "  y: " + pos.getY()
                + "  z: " + pos.getZ() + "）")
                .appendSibling(new TextComponentString(TextFormatting.WHITE + " "));
    }

    @Override
    public void addInformation(ItemStack stack, @Nullable World world, List<String> tooltip, ITooltipFlag flag) {
        tooltip.add(TextFormatting.GRAY + I18n.format("tooltip.jiezhirbmkmod.rbmk_config_card.0"));
        tooltip.add(TextFormatting.DARK_GRAY + I18n.format("tooltip.jiezhirbmkmod.rbmk_config_card.1"));
        tooltip.add(TextFormatting.DARK_GRAY + I18n.format("tooltip.jiezhirbmkmod.rbmk_config_card.2"));
        NBTTagCompound tag = stack.getTagCompound();
        boolean has = tag != null && tag.hasKey(ItemRbmkConfigCard.K_CFG)
                && !tag.getCompoundTag(ItemRbmkConfigCard.K_CFG).isEmpty();
        tooltip.add(has
                ? TextFormatting.GREEN + I18n.format("tooltip.jiezhirbmkmod.rbmk_config_card.3")
                : TextFormatting.DARK_GRAY + I18n.format("tooltip.jiezhirbmkmod.rbmk_config_card.4"));
    }
}
