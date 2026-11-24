package com.jiezhirbmkmod.item;

import com.jiezhirbmkmod.item.CoilUtil;
import com.jiezhirbmkmod.item.ModItems;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

public class GravityCoilEffect {
    public static final double BASE_JUMP = (double)0.42f;
    public static final double COIL_JUMP = 1.0075;
    private static final double GRAVITY_PER_TICK = 0.08;
    private static final double VERTICAL_DRAG = 0.98;

    @SubscribeEvent
    public void onLivingJump(LivingEvent.LivingJumpEvent event) {
        if (!(event.getEntityLiving() instanceof EntityPlayer)) {
            return;
        }
        EntityPlayer player = (EntityPlayer)event.getEntityLiving();
        if (!CoilUtil.isHolding(player, ModItems.GRAVITY_COIL)) {
            return;
        }
        player.motionY = GravityCoilEffect.coilJumpVelocity(player.motionY);
    }

    public static double coilJumpVelocity(double vanillaMotionY) {
        double extra = Math.max(0.0, vanillaMotionY - (double)0.42f);
        return 1.0075 + extra;
    }

    public static double jumpHeight(double initialVelocity) {
        double y = 0.0;
        double v = initialVelocity;
        while (v > 0.0) {
            y += v;
            v = (v - 0.08) * 0.98;
        }
        return y;
    }

    public static double coilJumpHeight() {
        return GravityCoilEffect.jumpHeight(1.0075);
    }

    public static double vanillaJumpHeight() {
        return GravityCoilEffect.jumpHeight(0.42f);
    }
}

