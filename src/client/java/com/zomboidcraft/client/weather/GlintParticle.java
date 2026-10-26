package com.zomboidcraft.client.weather;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;

/**
 * A raindrop falling through a lamp's light: a quick bright streak in the lamp's colour, there
 * and gone in a few frames. Drawn at full brightness and solid, so a shader pack keeps it.
 */
final class GlintParticle extends SingleQuadParticle {
    GlintParticle(ClientLevel level, double x, double y, double z, float r, float g, float b, TextureAtlasSprite sprite) {
        super(level, x, y, z, sprite);
        xd = 0;
        yd = -0.75 - random.nextDouble() * 0.25;
        zd = 0;
        hasPhysics = true;
        gravity = 0;
        friction = 1;
        lifetime = 3 + random.nextInt(4);
        quadSize = 0.07f + random.nextFloat() * 0.05f;
        setColor(r, g, b);
        alpha = 0.55f + random.nextFloat() * 0.4f;
    }

    /** Wind leans the fall. */
    void lean(double x, double z) {
        xd = x;
        zd = z;
    }

    @Override
    public void tick() {
        super.tick();
        if (onGround) {
            remove();
        }
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
