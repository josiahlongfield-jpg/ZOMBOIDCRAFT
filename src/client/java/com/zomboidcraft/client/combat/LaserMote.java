package com.zomboidcraft.client.combat;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.RandomSource;

/** A green speck where a laser catches the dust, or its dot: full bright, fading over its short life. */
public final class LaserMote extends SingleQuadParticle {
    private static final int FULL_BRIGHT = 0xF000F0;
    private final float peak;

    LaserMote(ClientLevel level, double x, double y, double z, TextureAtlasSprite sprite, float size, float alpha, int life) {
        super(level, x, y, z, sprite);
        quadSize = size;
        peak = alpha;
        this.alpha = alpha;
        lifetime = Math.max(1, life);
        hasPhysics = false;
        gravity = 0;
        xd = yd = zd = 0;
        float shade = 0.8f + random.nextFloat() * 0.2f;
        setColor(0.22f * shade, shade, 0.28f * shade);
    }

    @Override
    protected Layer getLayer() {
        return Layer.TRANSLUCENT;
    }

    @Override
    public int getLightCoords(float partialTick) {
        return FULL_BRIGHT;
    }

    @Override
    public void tick() {
        super.tick();
        alpha = peak * (1f - (float) age / lifetime);
    }

    /** The speed numbers carry the size (blocks), brightness (0 to 1) and life (ticks). */
    public record Provider(SpriteSet sprites) implements ParticleProvider<SimpleParticleType> {
        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z,
                                       double size, double alpha, double life, RandomSource random) {
            return new LaserMote(level, x, y, z, sprites.get(random), (float) size, (float) alpha, (int) life);
        }
    }
}
