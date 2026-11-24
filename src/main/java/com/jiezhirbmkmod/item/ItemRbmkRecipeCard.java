package com.jiezhirbmkmod.item;

import java.util.List;

import javax.annotation.Nullable;

import com.jiezhirbmkmod.block.TileRbmkMonitorScreen;

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
 * v17.8 配方模板卡：一块屏的<b>整张显示配方</b>快照。
 *
 * <p>配置拷贝卡（{@link ItemRbmkConfigCard}）只搬 15 个"调屏"键；模板卡
 * 走另一条路 —— <b>黑名单</b>快照：除了"现场的事"（供电、能量、运行刻、
 * 告警缓存、消息、安全码、归属、倾斜角），其余全部入库。自定义屏的
 * 监视槽清单（{@code WatchCount/WatchN_*}）、媒体屏的文件与音量、
 * PPT 屏的图片模式、曲线屏的绑定与阈值 —— 一卡全收。</p>
 *
 * <p>黑名单而非白名单的理由：屏幕家族是子类树
 * （{@code TileRbmkMonitorScreen} → Custom/Term/Media/Ppt），子类各自
 * 追加内容键，白名单每加一个键都要改这里；黑名单只在出现新的
 * <b>瞬时</b>键时才需要更新，默认行为反而是对的。</p>
 *
 * <p>粘贴语义同样是"覆盖子集"：以目标屏当前 NBT 为底，只覆盖卡里有的
 * 键。跨屏型粘贴（比如把曲线屏的配方贴到媒体屏）不报错 —— 目标
 * 读不认识的键会自动忽略，各屏自己认领自己的键。</p>
 *
 * <p>交互与拷贝卡同款：</p>
 * <ul>
 *   <li><b>潜行 + 右键屏幕</b>：快照整张配方进卡；</li>
 *   <li><b>右键屏幕</b>：把卡里的配方覆盖到屏上（写路径有审计留痕）；</li>
 *   <li><b>潜行 + 右键空气</b>：清空卡。</li>
 * </ul>
 *
 * <p>安全红线：坐标（x/y/z）、TileEntity 的 id 与 ForgeData 一律不入库，
 * 粘贴路径走的是"读目标 NBT → 覆盖 → 灌回"，目标自己的坐标永远
 * 不动 —— 模板卡搬的是"显示什么"，不是"这块屏是谁"。</p>
 *
 * <p>路由：与配置拷贝卡同一条入口 {@code ScreenMultiblock.handleUse}。</p>
 */
public class ItemRbmkRecipeCard
extends Item {

    /** 卡内快照的标签名 */
    private static final String K_SNAP = "Snap";
    /** 来源屏类型记录（纯展示用，粘贴时不参与） */
    private static final String K_FROM = "From";

    /**
     * 瞬时键黑名单：这些键描述"现场状态"，不属于配方，不拷贝。
     * 新增瞬时键时同步更新本表与类注释。
     */
    private static final String[] TRANSIENT = {
            // TileEntity 底座自带：身份与位置，动了等于把 TileEntity 搬家
            "id", "x", "y", "z", "ForgeData", "ForgeModData", "ForgeCaps",
            // 供电与运行时
            "Powered", "RealPower", "RealEnergy", "RunTicks",
            // 告警/消息缓存
            "Hacked", "LastMsg", "LastMsgSender",
            // 身份与密码：归属和口令是"这块屏的事"，不是配方
            "SecurityCode", "Owner",
            // 外观姿态与媒体瞬时态
            "Tilt", "MediaPlaying", "MediaError",
            // PPT 播放位置（放映到第几页不是配方）
            "currentSlide", "totalSlides"};

    public ItemRbmkRecipeCard() {
        this.setRegistryName("jiezhirbmkmod", "rbmk_recipe_card");
        this.setTranslationKey("rbmk_recipe_card");
        this.setMaxStackSize(1);
        this.setCreativeTab(ModItems.TAB);
    }

    /** 读/写入口，由 {@code ScreenMultiblock.handleUse} 在服务端调用 */
    public boolean useOnScreen(ItemStack stack, World world, BlockPos pos, EntityPlayer player) {
        if (world.isRemote) {
            return true;
        }
        TileEntity te = world.getTileEntity(pos);
        if (!(te instanceof TileRbmkMonitorScreen)) {
            player.sendStatusMessage(new TextComponentString(
                    TextFormatting.RED + "[jiezhirbmkmod] "
                            + net.minecraft.util.text.translation.I18n.translateToLocalFormatted(
                                    "msg.jiezhirbmkmod.recipe.notscreen")), true);
            return true;
        }
        TileRbmkMonitorScreen screen = (TileRbmkMonitorScreen) te;
        if (player.isSneaking()) {
            this.snapshotFromScreen(stack, screen, pos, player);
        } else {
            this.pasteToScreen(stack, screen, pos, player);
        }
        return true;
    }

    /** 潜行 + 右键：整张配方快照 → 卡 */
    private void snapshotFromScreen(ItemStack stack, TileRbmkMonitorScreen screen,
            BlockPos pos, EntityPlayer player) {
        NBTTagCompound src = screen.writeToNBT(new NBTTagCompound());
        NBTTagCompound snap = new NBTTagCompound();
        int copied = 0;
        for (String key : src.getKeySet()) {
            if (ItemRbmkRecipeCard.isTransient(key)) {
                continue;
            }
            snap.setTag(key, src.getTag(key).copy());
            ++copied;
        }
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null) {
            tag = new NBTTagCompound();
            stack.setTagCompound(tag);
        }
        tag.setTag(ItemRbmkRecipeCard.K_SNAP, snap);
        tag.setString(ItemRbmkRecipeCard.K_FROM,
                net.minecraft.util.text.translation.I18n.translateToLocalFormatted(
                        ItemRefreshTuner.screenKeyOf(screen)));
        player.sendStatusMessage(this.line(pos)
                .appendSibling(new TextComponentTranslation("msg.jiezhirbmkmod.recipe.read",
                        Integer.valueOf(copied))
                        .setStyle(new Style().setColor(TextFormatting.GREEN))), false);
    }

    /** 右键：卡里的配方覆盖到屏幕 NBT，再灌回方块实体 */
    private void pasteToScreen(ItemStack stack, TileRbmkMonitorScreen screen,
            BlockPos pos, EntityPlayer player) {
        NBTTagCompound tag = stack.getTagCompound();
        NBTTagCompound snap = tag == null ? null : tag.getCompoundTag(ItemRbmkRecipeCard.K_SNAP);
        if (snap == null || snap.isEmpty()) {
            player.sendStatusMessage(new TextComponentString(
                    TextFormatting.RED + "[jiezhirbmkmod] "
                            + net.minecraft.util.text.translation.I18n.translateToLocalFormatted(
                                    "msg.jiezhirbmkmod.recipe.empty")), true);
            return;
        }
        // 以屏当前 NBT 为底：屏自己的供电、历史、消息、坐标原样保留
        NBTTagCompound merged = screen.writeToNBT(new NBTTagCompound());
        for (String key : snap.getKeySet()) {
            merged.setTag(key, snap.getTag(key).copy());
        }
        screen.readFromNBT(merged);
        screen.markDirty();
        // v17.8: 模板粘贴是批量改屏动作，落盘留痕
        com.jiezhirbmkmod.dfc.AuditLog.log(player.getName(), "RECIPE_PASTE",
                "at " + pos.getX() + "," + pos.getY() + "," + pos.getZ()
                        + " from=" + (tag.hasKey(ItemRbmkRecipeCard.K_FROM)
                                ? tag.getString(ItemRbmkRecipeCard.K_FROM) : "?")
                        + " keys=" + snap.getKeySet().size());
        player.sendStatusMessage(this.line(pos)
                .appendSibling(new TextComponentTranslation("msg.jiezhirbmkmod.recipe.write",
                        Integer.valueOf(snap.getKeySet().size()))
                        .setStyle(new Style().setColor(TextFormatting.GREEN))), false);
    }

    /** 快照键是否属于瞬时黑名单 */
    private static boolean isTransient(String key) {
        for (int i = 0; i < ItemRbmkRecipeCard.TRANSIENT.length; ++i) {
            if (ItemRbmkRecipeCard.TRANSIENT[i].equals(key)) {
                return true;
            }
        }
        return false;
    }

    /** 潜行 + 右键空气：清空卡 */
    @Override
    public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, EnumHand hand) {
        ItemStack stack = player.getHeldItem(hand);
        if (!world.isRemote && player.isSneaking() && stack.hasTagCompound()
                && stack.getTagCompound().hasKey(ItemRbmkRecipeCard.K_SNAP)) {
            stack.getTagCompound().removeTag(ItemRbmkRecipeCard.K_SNAP);
            stack.getTagCompound().removeTag(ItemRbmkRecipeCard.K_FROM);
            player.sendStatusMessage(new TextComponentString(
                    TextFormatting.GOLD + "[jiezhirbmkmod] "
                            + net.minecraft.util.text.translation.I18n.translateToLocalFormatted(
                                    "msg.jiezhirbmkmod.recipe.cleared")), true);
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
        tooltip.add(TextFormatting.GRAY + I18n.format("tooltip.jiezhirbmkmod.rbmk_recipe_card.0"));
        tooltip.add(TextFormatting.DARK_GRAY + I18n.format("tooltip.jiezhirbmkmod.rbmk_recipe_card.1"));
        tooltip.add(TextFormatting.DARK_GRAY + I18n.format("tooltip.jiezhirbmkmod.rbmk_recipe_card.2"));
        NBTTagCompound tag = stack.getTagCompound();
        boolean has = tag != null && tag.hasKey(ItemRbmkRecipeCard.K_SNAP)
                && !tag.getCompoundTag(ItemRbmkRecipeCard.K_SNAP).isEmpty();
        if (has && tag.hasKey(ItemRbmkRecipeCard.K_FROM)
                && !tag.getString(ItemRbmkRecipeCard.K_FROM).isEmpty()) {
            tooltip.add(TextFormatting.GREEN + I18n.format(
                    "tooltip.jiezhirbmkmod.rbmk_recipe_card.3",
                    tag.getString(ItemRbmkRecipeCard.K_FROM)));
        } else {
            tooltip.add(TextFormatting.DARK_GRAY
                    + I18n.format("tooltip.jiezhirbmkmod.rbmk_recipe_card.4"));
        }
    }
}
