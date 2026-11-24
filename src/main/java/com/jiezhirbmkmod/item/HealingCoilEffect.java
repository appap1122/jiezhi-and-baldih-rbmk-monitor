package com.jiezhirbmkmod.item;

import com.jiezhirbmkmod.item.CoilUtil;
import com.jiezhirbmkmod.item.ModItems;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

public class HealingCoilEffect {
    public static final float HEAL_PER_SECOND = 4.0f;
    public static final int INTERVAL_TICKS = 20;

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        EntityPlayer player = event.player;
        if (player == null || player.world == null) {
            return;
        }
        if (!HealingCoilEffect.shouldHealThisTick(player.ticksExisted, CoilUtil.isHolding(player, ModItems.HEALING_COIL), player.world.isRemote, player.getHealth(), player.getMaxHealth())) {
            return;
        }
        player.heal(4.0f);
    }

    public static boolean shouldHealThisTick(int ticksExisted, boolean holding, boolean remote, float health, float maxHealth) {
        return !remote && ticksExisted % 20 == 0 && holding && health > 0.0f && health < maxHealth;
    }
}

