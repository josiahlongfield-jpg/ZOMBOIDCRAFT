package com.zomboidcraft.client.cars;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.Mth;

/**
 * One billow of a coloured smoke signal: thick and bright where it pours out of the can, then swelling, climbing
 * and leaning downwind as it pales, so a column of them reads from the air as the colour the operator called.
 */
final class SignalSmokeParticle extends SingleQuadParticle {
    private final float start, peak, r0, g0, b0;

    SignalSmokeParticle(ClientLevel level, double x, double y, double z, float r, float g, float b, TextureAtlasSprite sprite) {
        super(level, x, y, z, sprite);
        xd = (random.nextDouble() - 0.5) * 0.08 + 0.025;
        yd = 0.035 + random.nextDouble() * 0.05;
        zd = (random.nextDouble() - 0.5) * 0.08 + 0.012;
        hasPhysics = false;
        gravity = 0;
        friction = 0.985f;
        lifetime = 140 + random.nextInt(90);
        start = 0.7f + random.nextFloat() * 0.6f;
        quadSize = start;
        roll = oRoll = random.nextFloat() * Mth.TWO_PI;
        float shade = 0.9f + random.nextFloat() * 0.18f;
        r0 = Math.min(1, r * shade);
        g0 = Math.min(1, g * shade);
        b0 = Math.min(1, b * shade);
        setColor(r0, g0, b0);
        peak = 0.8f + random.nextFloat() * 0.15f;
        alpha = 0;
    }

    @Override
    public void tick() {
        super.tick();
        float t = age / (float) lifetime;
        quadSize = start * (1 + 4.5f * (float) Math.sqrt(t));
        // pales toward a dirty white as it thins
        float pale = 0.45f * t;
        setColor(r0 + (0.82f - r0) * pale, g0 + (0.8f - g0) * pale, b0 + (0.78f - b0) * pale);
        alpha = peak * Math.min(1, age / 6f) * (1 - t * t);
    }

    @Override
    protected Layer getLayer() {
        return Layer.TRANSLUCENT;
    }
}
