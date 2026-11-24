package com.jiezhirbmkmod.block;

import com.jiezhirbmkmod.block.BlockRbmkScreen;
import com.jiezhirbmkmod.block.ModBlocks;
import com.jiezhirbmkmod.dfc.GraphHistory;
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

public class BlockRbmkGraph
extends BlockRbmkScreen {
    public BlockRbmkGraph() {
        this("rbmk_graph", "rbmk_graph");
    }

    protected BlockRbmkGraph(String registryName, String unlocalizedName) {
        super(registryName, unlocalizedName);
    }

    @Override
    protected Item displayItem() {
        return ModBlocks.ITEM_RBMK_GRAPH;
    }

    @Override
    @SideOnly(value=Side.CLIENT)
    public void addInformation(ItemStack stack, @Nullable World world, List<String> tooltip, ITooltipFlag flag) {
        tooltip.add(TextFormatting.GRAY + I18n.format((String)"tooltip.jiezhirbmkmod.rbmk_graph.0", (Object[])new Object[0]));
        tooltip.add(TextFormatting.GRAY + I18n.format((String)"tooltip.jiezhirbmkmod.rbmk_graph.1", (Object[])new Object[0]));
        tooltip.add(TextFormatting.AQUA + I18n.format((String)"tooltip.jiezhirbmkmod.rbmk_graph.2", (Object[])new Object[0]));
        tooltip.add(TextFormatting.AQUA + I18n.format((String)"tooltip.jiezhirbmkmod.rbmk_graph.3", (Object[])new Object[0]));
        tooltip.add(TextFormatting.DARK_GRAY + I18n.format((String)"tooltip.jiezhirbmkmod.rbmk_graph.4", (Object[])new Object[]{com.jiezhirbmkmod.dfc.RbmkConfig.windowSeconds()}));
    }
}
