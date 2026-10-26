package com.zomboidcraft.client.weather;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.Mth;

/**
 * A billow of dark smoke from a fire: rises, spreads and thins as it goes, leaning downwind.
 * Many of them stacked make the columns you see standing over the city.
 */
final class PlumeParticle extends SingleQuadParticle {
    private final float start, peak;

    /** size is the billow's width where it leaves the fire. */
    PlumeParticle(ClientLevel level, double x, double y, double z, double windX, double windZ, float size, TextureAtlasSprite sprite) {
        super(level, x, y, z, sprite);
        xd = windX;
        yd = 0.07 + random.nextDouble() * 0.06;
        zd = windZ;
        hasPhysics = false;
        gravity = 0;
        friction = 0.995f;
        lifetime = 360 + random.nextInt(160);
        start = size;
        quadSize = size;
        roll = oRoll = random.nextFloat() * Mth.TWO_PI;
        float shade = 0.13f + random.nextFloat() * 0.08f;
        setColor(shade, shade, shade * 1.05f);
        peak = 0.55f + random.nextFloat() * 0.2f;
        alpha = 0;
    }

    @Override
    public void tick() {
        super.tick();
        float t = age / (float) lifetime;
        quadSize = start * (1 + 2.2f * t);
        alpha = peak * Math.min(1, age / 30f) * (1 - t * t);
    }

    @Override
    protected Layer getLayer() {
        return Layer.TRANSLUCENT;
    }
}
