package com.jiezhirbmkmod.client.particle;

import net.minecraft.client.particle.Particle;
import net.minecraft.world.World;

public class ParticleJetExhaust
extends Particle {
    private static final int TEX_INDEX = 0;
    private final float baseScale;

    public ParticleJetExhaust(World worldIn, double x, double y, double z, double mx, double my, double mz) {
        super(worldIn, x, y, z, mx, my, mz);
        this.particleTextureIndexX = 0;
        this.particleTextureIndexY = 0;
        this.particleMaxAge = 12 + this.rand.nextInt(7);
        this.particleAge = 0;
        this.particleGravity = -0.035f;
        this.particleScale = this.baseScale = 0.9f + this.rand.nextFloat() * 0.5f;
        this.motionX *= 0.5;
        this.motionZ *= 0.5;
        this.motionY *= 0.5;
        this.motionX += (double)(this.rand.nextFloat() - 0.5f) * 0.06;
        this.motionZ += (double)(this.rand.nextFloat() - 0.5f) * 0.06;
        this.particleRed = 1.0f;
        this.particleGreen = 0.85f;
        this.particleBlue = 0.5f;
    }

    public void onUpdate() {
        super.onUpdate();
        float t = (float)this.particleAge / (float)this.particleMaxAge;
        this.particleRed = 1.0f;
        this.particleGreen = (float)(0.85 - 0.55 * (double)t);
        this.particleBlue = (float)(0.5 - 0.42 * (double)t);
        this.particleScale = this.baseScale * (1.0f + 0.35f * (float)Math.sin((double)t * Math.PI));
    }
}

