package com.jiezhirbmkmod.item;

import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

public class ItemJetpack
extends ItemArmor {
    public ItemJetpack() {
        super(ItemArmor.ArmorMaterial.IRON, 0, EntityEquipmentSlot.CHEST);
        this.setRegistryName("jiezhirbmkmod", "jetpack");
        this.setTranslationKey("jetpack");
        this.setMaxStackSize(1);
    }

    @SideOnly(value=Side.CLIENT)
    public void addInformation(ItemStack stack, @Nullable World worldIn, List<String> tooltip, ITooltipFlag flagIn) {
        tooltip.add(TextFormatting.AQUA + I18n.format((String)"tooltip.jiezhirbmkmod.jetpack.0", (Object[])new Object[0]));
        tooltip.add(TextFormatting.GRAY + I18n.format((String)"tooltip.jiezhirbmkmod.jetpack.1", (Object[])new Object[0]));
        tooltip.add(TextFormatting.GRAY + I18n.format((String)"tooltip.jiezhirbmkmod.jetpack.2", (Object[])new Object[]{18}));
        tooltip.add(TextFormatting.GRAY + I18n.format((String)"tooltip.jiezhirbmkmod.jetpack.3", (Object[])new Object[0]));
        tooltip.add(TextFormatting.DARK_GRAY + I18n.format((String)"tooltip.jiezhirbmkmod.jetpack.4", (Object[])new Object[0]));
    }
}

