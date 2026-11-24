package com.jiezhirbmkmod.item;

import java.util.List;

import javax.annotation.Nullable;

import com.jiezhirbmkmod.block.TileRbmkMonitorScreen;
import com.jiezhirbmkmod.dfc.AlertPolicy;
import com.jiezhirbmkmod.dfc.SirenPresets;

import net.minecraft.client.resources.I18n;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.Style;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;

/**
 * v17.6 告警扩音器：揣在兜里的警报器。
 *
 * <p>落地警报器（rbmk_siren）靠红石线听屏的话，扩音器靠绑定 ——
 * 潜行右键一块显示屏，把它记进物品 NBT；之后每 10 刻（与屏的告警轮询
 * 同节奏）看一眼这块屏的告警等级，L1 预警播"预警两声"、L2 危急播
 * "熔毁长鸣"，L0 归零即静。声音从<b>玩家</b>位置发出而不是从屏的位置 ——
 * 走到哪响到哪，这是"扩音器"而不是"对讲机"的意思。</p>
 *
 * <p>音源就是落地警报器注册的那 6 个 SoundEvent（{@link ModSounds}），
 * 不新增任何 ogg —— 音色、长度、音量上限全部复用 {@link SirenPresets}
 * 的同一套常量，喇叭响的是同一张唱片。类别走 MASTER，保证主控室在放
 * 媒体（AMBIENT 声道）时告警仍然压得住。</p>
 *
 * <p>交互：</p>
 * <ul>
 *   <li><b>潜行 + 右键屏幕</b>：绑定（走 {@code ScreenMultiblock.handleUse}，
 *       多方块任意一格都行）；</li>
 *   <li><b>右键空气</b>：静音切换（静音时绑定仍在，只是不响）；</li>
 *   <li><b>潜行 + 右键空气</b>：解绑。</li>
 * </ul>
 *
 * <p>节流写在本体的 NBT 里（{@code HornNextTick}）：每块屏的告警等级
 * 在预警档会持续整段事件，扩音器按音效自身的长度 + 间隙循环重播 ——
 * 与落地警报器 MODE_LOOP 的语义一致，而不是每 tick 糊一嗓子。</p>
 */
public class ItemRbmkSirenHorn
extends Item {

    private static final String K_X = "HornX";
    private static final String K_Y = "HornY";
    private static final String K_Z = "HornZ";
    private static final String K_DIM = "HornDim";
    private static final String K_MUTE = "HornMute";
    private static final String K_NEXT = "HornNextTick";

    /** 巡检周期（tick）：与告警等级的刷新节奏对齐 */
    private static final int CHECK_INTERVAL = 10;
    /** 预警两声的循环间隔：17t 音效 + 30t 间隙 */
    private static final int REPEAT_WARN = 47;
    /** 熔毁长鸣的循环间隔：118t 音效 + 20t 间隙 */
    private static final int REPEAT_CRIT = 138;
    /** 扩音器音量。落地警报器上限 8.0，随身喇叭压到 2.0 —— 它只管主人一个人 */
    private static final float HORN_VOLUME = 2.0F;

    public ItemRbmkSirenHorn() {
        this.setRegistryName("jiezhirbmkmod", "rbmk_siren_horn");
        this.setTranslationKey("rbmk_siren_horn");
        this.setMaxStackSize(1);
        this.setCreativeTab(ModItems.TAB);
    }

    /**
     * 绑定入口，由 {@code ScreenMultiblock.handleUse} 在潜行右键时调用
     * （必须潜行：普通右键要让给屏自己的 GUI / 电源开关）。
     *
     * @return true = 点击被消费；false = 交还给后续分支
     */
    public boolean bindOnScreen(ItemStack stack, World world, BlockPos controllerPos, EntityPlayer player) {
        if (world.isRemote) {
            return true;
        }
        TileEntity te = world.getTileEntity(controllerPos);
        if (!(te instanceof TileRbmkMonitorScreen)) {
            player.sendStatusMessage(new TextComponentString(
                    TextFormatting.RED + "[jiezhirbmkmod] "
                            + net.minecraft.util.text.translation.I18n.translateToLocalFormatted(
                                    "msg.jiezhirbmkmod.horn.notscreen")), true);
            return true;
        }
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null) {
            tag = new NBTTagCompound();
            stack.setTagCompound(tag);
        }
        tag.setInteger(ItemRbmkSirenHorn.K_X, controllerPos.getX());
        tag.setInteger(ItemRbmkSirenHorn.K_Y, controllerPos.getY());
        tag.setInteger(ItemRbmkSirenHorn.K_Z, controllerPos.getZ());
        tag.setInteger(ItemRbmkSirenHorn.K_DIM, world.provider.getDimension());
        tag.setLong(ItemRbmkSirenHorn.K_NEXT, 0L);
        player.sendStatusMessage(new TextComponentString(
                TextFormatting.AQUA + "[jiezhirbmkmod] " + TextFormatting.GRAY
                        + "（x: " + controllerPos.getX() + "  y: " + controllerPos.getY()
                        + "  z: " + controllerPos.getZ() + "）")
                .appendSibling(new TextComponentTranslation(
                        ItemRefreshTuner.screenKeyOf(te)))
                .appendSibling(new TextComponentString(" "))
                .appendSibling(new TextComponentTranslation("msg.jiezhirbmkmod.horn.bound")
                        .setStyle(new Style().setColor(TextFormatting.GREEN))), false);
        return true;
    }

    @Override
    public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, EnumHand hand) {
        ItemStack stack = player.getHeldItem(hand);
        if (world.isRemote) {
            return new ActionResult<ItemStack>(EnumActionResult.SUCCESS, stack);
        }
        NBTTagCompound tag = stack.getTagCompound();
        if (player.isSneaking()) {
            boolean bound = tag != null && tag.hasKey(ItemRbmkSirenHorn.K_X);
            if (bound) {
                tag.removeTag(ItemRbmkSirenHorn.K_X);
                tag.removeTag(ItemRbmkSirenHorn.K_Y);
                tag.removeTag(ItemRbmkSirenHorn.K_Z);
                tag.removeTag(ItemRbmkSirenHorn.K_DIM);
                tag.setLong(ItemRbmkSirenHorn.K_NEXT, 0L);
                player.sendStatusMessage(new TextComponentString(
                        TextFormatting.GOLD + "[jiezhirbmkmod] "
                                + net.minecraft.util.text.translation.I18n.translateToLocalFormatted(
                                        "msg.jiezhirbmkmod.horn.cleared")), true);
            } else {
                // 本来就没绑定：说实话，别让玩家以为解绑了什么
                player.sendStatusMessage(new TextComponentString(
                        TextFormatting.YELLOW + "[jiezhirbmkmod] "
                                + net.minecraft.util.text.translation.I18n.translateToLocalFormatted(
                                        "msg.jiezhirbmkmod.horn.notbound")), true);
            }
            return new ActionResult<ItemStack>(EnumActionResult.SUCCESS, stack);
        }
        boolean muted = tag != null && tag.getBoolean(ItemRbmkSirenHorn.K_MUTE);
        if (tag == null) {
            tag = new NBTTagCompound();
            stack.setTagCompound(tag);
        }
        tag.setBoolean(ItemRbmkSirenHorn.K_MUTE, !muted);
        player.sendStatusMessage(new TextComponentString(
                TextFormatting.GOLD + "[jiezhirbmkmod] "
                        + net.minecraft.util.text.translation.I18n.translateToLocalFormatted(
                                !muted ? "msg.jiezhirbmkmod.horn.mute_on"
                                       : "msg.jiezhirbmkmod.horn.mute_off")), true);
        return new ActionResult<ItemStack>(EnumActionResult.SUCCESS, stack);
    }

    /**
     * 随身监听。只在物品被<b>握在手上</b>时巡检（塞在背包里不响 ——
     * 塞兜里的喇叭就该是闭嘴的，也避免整箱扩音器互相糊脸）。
     */
    @Override
    public void onUpdate(ItemStack stack, World world, net.minecraft.entity.Entity holder, int slot, boolean selected) {
        if (world.isRemote || !selected || !(holder instanceof EntityPlayer)) {
            return;
        }
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null || !tag.hasKey(ItemRbmkSirenHorn.K_X)) {
            return;
        }
        long now = world.getTotalWorldTime();
        if (now < tag.getLong(ItemRbmkSirenHorn.K_NEXT)) {
            return;
        }
        if (world.provider.getDimension() != tag.getInteger(ItemRbmkSirenHorn.K_DIM)) {
            // 跨维度看不见绑定屏：静默跳过，等回同一维度再恢复
            tag.setLong(ItemRbmkSirenHorn.K_NEXT, now + ItemRbmkSirenHorn.CHECK_INTERVAL);
            return;
        }
        TileEntity te = world.getTileEntity(new BlockPos(
                tag.getInteger(ItemRbmkSirenHorn.K_X),
                tag.getInteger(ItemRbmkSirenHorn.K_Y),
                tag.getInteger(ItemRbmkSirenHorn.K_Z)));
        if (!(te instanceof TileRbmkMonitorScreen)) {
            // 绑定的屏被拆了：不响，也不自动解绑 —— 重放回去还能续上
            tag.setLong(ItemRbmkSirenHorn.K_NEXT, now + ItemRbmkSirenHorn.CHECK_INTERVAL);
            return;
        }
        int level = ((TileRbmkMonitorScreen) te).getAlertLevel();
        int repeat;
        if (level >= AlertPolicy.LEVEL_CRIT) {
            this.blare(world, holder, stack, SirenPresets.MELTDOWN_WAIL);
            repeat = ItemRbmkSirenHorn.REPEAT_CRIT;
        } else if (level >= AlertPolicy.LEVEL_WARN) {
            this.blare(world, holder, stack, SirenPresets.WARN_BEEP);
            repeat = ItemRbmkSirenHorn.REPEAT_WARN;
        } else {
            // L0：不响，只在短周期上空转
            repeat = ItemRbmkSirenHorn.CHECK_INTERVAL;
        }
        tag.setLong(ItemRbmkSirenHorn.K_NEXT, now + (long) repeat);
    }

    private void blare(World world, net.minecraft.entity.Entity holder, ItemStack stack, int preset) {
        NBTTagCompound tag = stack.getTagCompound();
        if (tag != null && tag.getBoolean(ItemRbmkSirenHorn.K_MUTE)) {
            return;
        }
        world.playSound((EntityPlayer) null,
                holder.posX, holder.posY, holder.posZ,
                ModSounds.siren(preset), SoundCategory.MASTER,
                ItemRbmkSirenHorn.HORN_VOLUME, 1.0F);
    }

    @Override
    public void addInformation(ItemStack stack, @Nullable World world, List<String> tooltip, ITooltipFlag flag) {
        tooltip.add(TextFormatting.GRAY + I18n.format("tooltip.jiezhirbmkmod.rbmk_siren_horn.0"));
        tooltip.add(TextFormatting.DARK_GRAY + I18n.format("tooltip.jiezhirbmkmod.rbmk_siren_horn.1"));
        tooltip.add(TextFormatting.DARK_GRAY + I18n.format("tooltip.jiezhirbmkmod.rbmk_siren_horn.2"));
        NBTTagCompound tag = stack.getTagCompound();
        boolean bound = tag != null && tag.hasKey(ItemRbmkSirenHorn.K_X);
        boolean muted = tag != null && tag.getBoolean(ItemRbmkSirenHorn.K_MUTE);
        if (bound) {
            tooltip.add(TextFormatting.GREEN + I18n.format(
                    "tooltip.jiezhirbmkmod.rbmk_siren_horn.3",
                    tag.getInteger(ItemRbmkSirenHorn.K_X),
                    tag.getInteger(ItemRbmkSirenHorn.K_Y),
                    tag.getInteger(ItemRbmkSirenHorn.K_Z)));
        } else {
            tooltip.add(TextFormatting.DARK_GRAY
                    + I18n.format("tooltip.jiezhirbmkmod.rbmk_siren_horn.4"));
        }
        if (muted) {
            tooltip.add(TextFormatting.YELLOW + I18n.format("tooltip.jiezhirbmkmod.rbmk_siren_horn.5"));
        }
    }
}
