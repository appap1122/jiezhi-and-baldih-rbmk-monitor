package com.jiezhirbmkmod.item;

import com.jiezhirbmkmod.block.TileRbmkMonitorScreen;
import net.minecraft.entity.player.EntityPlayer;

/**
 * v17.5 civilian refresh-rate tuner — the only tuner a normal player may use.
 *
 * <p>Same interaction as {@link ItemRefreshTuner} — sneak + right-click the
 * display under the crosshair — but the tier list stops at 250 ms:</p>
 *
 * <pre>默认 -> 低 500ms -> 中 300ms -> 中高 250ms -> 默认</pre>
 *
 * <p>No op check at all: this item is open to every player, and ops may use it
 * too if they just want the four slow tiers. It simply has no high / ultra
 * step, so on a screen left at the 200 ms default the slowest click costs 500 ms
 * and the fastest one costs 250 ms — never a per-tick redraw.</p>
 *
 * <p>It also behaves when it meets a screen an admin pushed faster: the current
 * tier is not in this cycle, so the first click puts the screen back to its
 * factory default instead of guessing.</p>
 */
public class ItemRefreshTunerBasic
extends ItemRefreshTuner {

    public ItemRefreshTunerBasic() {
        this.setRegistryName("jiezhirbmkmod", "rbmk_refresh_tuner_basic");
        this.setTranslationKey("rbmk_refresh_tuner_basic");
    }

    @Override
    protected boolean isAdminEdition() {
        return false;
    }

    @Override
    protected int[] tierOrder(EntityPlayer player) {
        return TileRbmkMonitorScreen.TIER_ORDER_BASIC;
    }

    @Override
    protected String tooltipBase() {
        return "tooltip.jiezhirbmkmod.rbmk_refresh_tuner_basic";
    }
}
