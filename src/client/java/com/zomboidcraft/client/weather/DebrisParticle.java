package com.zomboidcraft.client.weather;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.Mth;

/**
 * A scrap of litter caught by the wind: newspaper, a dead leaf, a shred of plastic bag. It
 * tumbles along the street, lifts in a gust and skids to a stop when the air lets go.
 */
final class DebrisParticle extends SingleQuadParticle {
    private final double pushX, pushZ;
    private final float spin;

    DebrisParticle(ClientLevel level, double x, double y, double z, double windX, double windZ, TextureAtlasSprite sprite) {
        super(level, x, y, z, sprite);
        pushX = windX;
        pushZ = windZ;
        xd = windX * (0.6 + random.nextDouble() * 0.6);
        yd = 0.02 + random.nextDouble() * 0.06;
        zd = windZ * (0.6 + random.nextDouble() * 0.6);
        hasPhysics = true;
        gravity = 0.12f;
        friction = 0.94f;
        lifetime = 70 + random.nextInt(110);
        quadSize = 0.09f + random.nextFloat() * 0.09f;
        roll = oRoll = random.nextFloat() * Mth.TWO_PI;
        spin = (random.nextFloat() - 0.5f) * 0.6f;
        float shade = 0.7f + random.nextFloat() * 0.3f;
        setColor(shade, shade, shade);
    }

    @Override
    public void tick() {
        oRoll = roll;
        // the wind keeps shoving, harder in a gust; on the ground it only skids
        double gust = 0.5 + 0.5 * Math.sin((age + x * 0.3) * 0.15);
        xd += pushX * 0.08 * gust;
        zd += pushZ * 0.08 * gust;
        if (onGround && random.nextFloat() < 0.08 * gust) {
            yd += 0.05 + random.nextFloat() * 0.08;
        }
        roll += onGround ? spin * 0.2f : spin;
        super.tick();
        if (age > lifetime - 15) {
            alpha = (lifetime - age) / 15f;
        }
    }

    @Override
    protected Layer getLayer() {
        return Layer.TRANSLUCENT;
    }
}
