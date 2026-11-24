package com.jiezhirbmkmod.client;

import com.jiezhirbmkmod.RbmkMonitorMod;
import com.jiezhirbmkmod.client.ClientJetState;
import com.jiezhirbmkmod.client.particle.JetParticleFactory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.IParticleFactory;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleManager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly(value=Side.CLIENT)
public final class ClientJetRenderer {
    public static final int PARTICLE_ID = 2000;
    private static final int MAX_PER_TICK = 2;
    private static boolean factoryRegistered = false;

    private ClientJetRenderer() {
    }

    public static void registerParticleFactory(Minecraft mc) {
        if (factoryRegistered || mc == null || mc.effectRenderer == null) {
            return;
        }
        try {
            mc.effectRenderer.registerParticle(2000, (IParticleFactory)new JetParticleFactory());
            factoryRegistered = true;
        }
        catch (Throwable t) {
            RbmkMonitorMod.warn("Failed to register jet particle factory: {}", String.valueOf(t));
        }
    }

    public static void onClientTick(Minecraft mc) {
        if (mc == null || mc.world == null || mc.player == null) {
            return;
        }
        ClientJetRenderer.registerParticleFactory(mc);
        if (ClientJetState.isFiring((EntityPlayer)mc.player) != Boolean.TRUE) {
            return;
        }
        ClientJetRenderer.spawnExhaust(mc, (EntityPlayer)mc.player);
    }

    public static void spawnExhaust(Minecraft mc, EntityPlayer player) {
        if (mc == null || mc.effectRenderer == null) {
            return;
        }
        ParticleManager pm = mc.effectRenderer;
        EnumFacing back = player.getHorizontalFacing().getOpposite();
        double bx = back.getXOffset();
        double bz = back.getZOffset();
        for (int i = 0; i < 2; ++i) {
            double mz;
            double my;
            double mx;
            double pz;
            double ox = (Math.random() - 0.5) * 0.34;
            double oz = (Math.random() - 0.5) * 0.34;
            double px = player.posX - bx * 0.3 + ox;
            double py = player.posY + 0.1 + (Math.random() - 0.5) * 0.08;
            Particle p = pm.spawnEffectParticle(2000, px, py, pz = player.posZ - bz * 0.3 + oz, mx = -bx * 0.06 + (Math.random() - 0.5) * 0.05, my = -0.22 - Math.random() * 0.1, mz = -bz * 0.06 + (Math.random() - 0.5) * 0.05, new int[0]);
            if (p == null) continue;
            p.multipleParticleScaleBy(0.8f);
        }
    }
}

