package com.zomboidcraft.client.weather;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.Mth;

/**
 * The glow at the foot of a fire, seen from far off: a soft orange smear that flickers and lifts.
 * Drawn at full brightness, whatever the light where it is.
 */
final class EmberParticle extends SingleQuadParticle {
    private final float peak;

    EmberParticle(ClientLevel level, double x, double y, double z, float size, TextureAtlasSprite sprite) {
        super(level, x, y, z, sprite);
        xd = (random.nextDouble() - 0.5) * 0.02;
        yd = 0.02 + random.nextDouble() * 0.04;
        zd = (random.nextDouble() - 0.5) * 0.02;
        hasPhysics = false;
        gravity = 0;
        friction = 1;
        lifetime = 25 + random.nextInt(30);
        quadSize = size * (0.7f + random.nextFloat() * 0.6f);
        roll = oRoll = random.nextFloat() * Mth.TWO_PI;
        setColor(1.0f, 0.42f + random.nextFloat() * 0.18f, 0.12f);
        peak = 0.35f + random.nextFloat() * 0.3f;
        alpha = 0;
    }

    @Override
    public void tick() {
        super.tick();
        float t = age / (float) lifetime;
        alpha = peak * (float) Math.sin(Math.PI * t) * (0.75f + 0.25f * random.nextFloat());
    }

    @Override
    protected int getLightCoords(float partialTick) {
        return 0xF000F0;
    }

    @Override
    protected Layer getLayer() {
        return Layer.TRANSLUCENT;
    }
}
