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
 * v18.0 画面轮播显示屏（真实版）：在普通轮播屏之上叠加主控供电判定
 * （{@link IRealScreen} 标记 → {@link TileRbmkMonitorScreen#isRealScreen()}
 * 为真）。红石不足 {@link RealPower#REDSTONE_MIN} 级或 HE/FE 双空时
 * {@code realPowerState} 被服务端强制 OFF，渲染器对真实屏直接不画 ——
 * 未通电强制黑屏。
 */
public class BlockRbmkCycleScreenReal extends BlockRbmkCycleScreen implements IRealScreen {

    public BlockRbmkCycleScreenReal() {
        super("rbmk_cycle_screen_real", "rbmk_cycle_screen_real");
    }

    @Override
    protected Item displayItem() {
        return ModBlocks.ITEM_RBMK_CYCLE_SCREEN_REAL;
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
