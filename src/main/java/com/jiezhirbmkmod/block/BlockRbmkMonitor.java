package com.jiezhirbmkmod.block;

import com.jiezhirbmkmod.block.BlockRbmkScreen;
import com.jiezhirbmkmod.block.ModBlocks;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

public class BlockRbmkMonitor
extends BlockRbmkScreen {
    public BlockRbmkMonitor() {
        super("rbmk_monitor", "rbmk_monitor");
    }

    @Override
    protected Item displayItem() {
        return ModBlocks.ITEM_RBMK_MONITOR;
    }

    @Override
    @SideOnly(value=Side.CLIENT)
    public void addInformation(ItemStack stack, @Nullable World world, List<String> tooltip, ITooltipFlag flag) {
        tooltip.add(TextFormatting.GRAY + I18n.format((String)"tooltip.jiezhirbmkmod.rbmk_monitor.0", (Object[])new Object[]{12}));
        tooltip.add(TextFormatting.GRAY + I18n.format((String)"tooltip.jiezhirbmkmod.rbmk_monitor.1", (Object[])new Object[0]));
        tooltip.add(TextFormatting.AQUA + I18n.format((String)"tooltip.jiezhirbmkmod.rbmk_monitor.2", (Object[])new Object[0]));
        tooltip.add(TextFormatting.AQUA + I18n.format((String)"tooltip.jiezhirbmkmod.rbmk_monitor.3", (Object[])new Object[0]));
        tooltip.add(TextFormatting.AQUA + I18n.format((String)"tooltip.jiezhirbmkmod.rbmk_monitor.4", (Object[])new Object[0]));
    }
}

