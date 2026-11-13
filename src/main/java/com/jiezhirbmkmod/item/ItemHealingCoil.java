package com.jiezhirbmkmod.item;

import com.jiezhirbmkmod.item.ItemCoil;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

public class ItemHealingCoil
extends ItemCoil {
    public ItemHealingCoil() {
        super("healing_coil");
    }

    @SideOnly(value=Side.CLIENT)
    public void addInformation(ItemStack stack, @Nullable World worldIn, List<String> tooltip, ITooltipFlag flagIn) {
        tooltip.add(TextFormatting.GREEN + I18n.format((String)"tooltip.jiezhirbmkmod.coil_healing.0", (Object[])new Object[0]));
        tooltip.add(TextFormatting.GRAY + I18n.format((String)"tooltip.jiezhirbmkmod.coil_healing.1", (Object[])new Object[]{4}));
        tooltip.add(TextFormatting.DARK_GRAY + I18n.format((String)"tooltip.jiezhirbmkmod.coil_healing.2", (Object[])new Object[0]));
    }
}

