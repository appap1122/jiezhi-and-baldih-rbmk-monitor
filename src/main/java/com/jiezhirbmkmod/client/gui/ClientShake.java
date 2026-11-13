package com.jiezhirbmkmod.client.gui;

import java.util.Random;
import net.minecraftforge.client.event.EntityViewRenderEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Client side camera shake used by the meltdown explosion. The shake is
 * applied through the Forge camera setup event, so it moves the real camera
 * instead of hacking the EntityRenderer fields.
 */
@SideOnly(Side.CLIENT)
public class ClientShake {

    private static float intensity = 0.0F;
    private static int ticksLeft = 0;
    private static final Random RANDOM = new Random();

    /** strength is roughly the max camera offset in degrees; duration is in ticks. */
    public static void shake(float strength, int durationTicks) {
        if (strength <= 0.0F || durationTicks <= 0) {
            return;
        }
        strength = Math.min(8.0F, strength);
        durationTicks = Math.min(120, durationTicks);
        intensity = Math.max(intensity, strength);
        ticksLeft = Math.max(ticksLeft, durationTicks);
    }

    @SubscribeEvent
    public void onCameraSetup(EntityViewRenderEvent.CameraSetup event) {
        try {
            if (event == null || ticksLeft <= 0 || intensity <= 0.0F) {
                return;
            }
            float fade = Math.min(1.0F, (float) ticksLeft / 20.0F);
            float amp = intensity * fade;
            event.setYaw(event.getYaw() + (RANDOM.nextFloat() - 0.5F) * amp * 2.0F);
            event.setPitch(event.getPitch() + (RANDOM.nextFloat() - 0.5F) * amp * 2.0F);
            event.setRoll(event.getRoll() + (RANDOM.nextFloat() - 0.5F) * amp * 3.0F);
        }
        catch (Throwable ignored) {
            // a broken camera event must never crash the client renderer
            ticksLeft = 0;
            intensity = 0.0F;
        }
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        try {
            if (event == null || event.phase != TickEvent.Phase.END) {
                return;
            }
            if (ticksLeft > 0) {
                --ticksLeft;
            }
            if (ticksLeft <= 0) {
                intensity = 0.0F;
            }
        }
        catch (Throwable ignored) {
            ticksLeft = 0;
            intensity = 0.0F;
        }
    }
}
