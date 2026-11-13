package com.jiezhirbmkmod.client.particle;

import com.jiezhirbmkmod.client.particle.ParticleJetExhaust;
import net.minecraft.client.particle.IParticleFactory;
import net.minecraft.client.particle.Particle;
import net.minecraft.world.World;

public class JetParticleFactory
implements IParticleFactory {
    public Particle createParticle(int id, World world, double x, double y, double z, double mx, double my, double mz, int ... params) {
        return new ParticleJetExhaust(world, x, y, z, mx, my, mz);
    }
}

