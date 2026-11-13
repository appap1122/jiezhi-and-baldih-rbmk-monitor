package com.jiezhirbmkmod.item;

import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

public class ItemJetSwitch
extends Item {
    public static final int MAX_USE_TICKS = 72000;

    public ItemJetSwitch() {
        this.setRegistryName("jiezhirbmkmod", "jet_switch");
        this.setTranslationKey("jet_switch");
        this.setMaxStackSize(1);
        this.setMaxDamage(0);
    }

    public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, EnumHand hand) {
        ItemStack stack = player.getHeldItem(hand);
        player.setActiveHand(hand);
        return new ActionResult(EnumActionResult.SUCCESS, (Object)stack);
    }

    public int getMaxItemUseDuration(ItemStack stack) {
        return 72000;
    }

    @SideOnly(value=Side.CLIENT)
    public void addInformation(ItemStack stack, @Nullable World worldIn, List<String> tooltip, ITooltipFlag flagIn) {
        tooltip.add(TextFormatting.AQUA + I18n.format((String)"tooltip.jiezhirbmkmod.jet_switch.0", (Object[])new Object[0]));
        tooltip.add(TextFormatting.GRAY + I18n.format((String)"tooltip.jiezhirbmkmod.jet_switch.1", (Object[])new Object[0]));
        tooltip.add(TextFormatting.GRAY + I18n.format((String)"tooltip.jiezhirbmkmod.jet_switch.2", (Object[])new Object[0]));
        tooltip.add(TextFormatting.DARK_GRAY + I18n.format((String)"tooltip.jiezhirbmkmod.jet_switch.3", (Object[])new Object[0]));
    }
}

