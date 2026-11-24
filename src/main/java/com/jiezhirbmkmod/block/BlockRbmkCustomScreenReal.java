package com.jiezhirbmkmod.block;

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

/**
 * v17.4 "real" edition of the custom text page.
 *
 * <p>Same rendering, same GUI and same multiblock as the parent screen - only
 * the registry name, the item icon and the power behaviour differ. Everything
 * that reads the block state (the TESR page switch, the GUI opener, the remote
 * binder) still sees the parent type, so no logic is duplicated here.</p>
 */
public class BlockRbmkCustomScreenReal extends BlockRbmkCustomScreen implements IRealScreen {

    public BlockRbmkCustomScreenReal() {
        super("rbmk_custom_real", "rbmk_custom_real");
    }

    @Override
    protected Item displayItem() {
        return ModBlocks.ITEM_RBMK_CUSTOM_REAL;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(ItemStack stack, @Nullable World world, List<String> tooltip, ITooltipFlag flag) {
        tooltip.add(TextFormatting.GOLD + I18n.format("tooltip.jiezhirbmkmod.real.header"));
        super.addInformation(stack, world, tooltip, flag);
        tooltip.add(TextFormatting.YELLOW + I18n.format("tooltip.jiezhirbmkmod.real.power.0"));
        tooltip.add(TextFormatting.YELLOW + I18n.format("tooltip.jiezhirbmkmod.real.power.1"));
        tooltip.add(TextFormatting.GRAY + I18n.format("tooltip.jiezhirbmkmod.real.click"));
        tooltip.add(TextFormatting.DARK_GRAY + I18n.format("tooltip.jiezhirbmkmod.real.sneak"));
    }
}
