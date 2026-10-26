package com.zomboidcraft.client.weather;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.Mth;

/**
 * One soft puff of mist, a few blocks across, drifting with the wind. Many of them together make
 * the fog bank: a shader pack draws them like any particle, lit by the lamps they pass, so the
 * fog shows (and glows purple round a froglight) where the game's own fog distance is ignored.
 */
final class MistParticle extends SingleQuadParticle {
    private final float peak;
    private final float spin;
    private final float size;

    /** strength is the puff's opacity at its thickest. */
    MistParticle(ClientLevel level, double x, double y, double z, double dx, double dz, float strength, TextureAtlasSprite sprite) {
        super(level, x, y, z, sprite);
        xd = dx;
        yd = 0;
        zd = dz;
        hasPhysics = false;
        gravity = 0;
        friction = 1;
        lifetime = 260 + random.nextInt(220);
        // wide and faint: they only read as fog where several overlap, never one by one
        size = 5f + random.nextFloat() * 5f;
        quadSize = 0;
        // kept near level so the flat smear lies along the ground instead of tumbling
        roll = oRoll = (random.nextFloat() - 0.5f) * 0.3f;
        spin = (random.nextFloat() - 0.5f) * 0.0006f;
        peak = strength;
        // the endless night's slate grey; lamps tint it through the light map
        float shade = 0.60f + random.nextFloat() * 0.06f;
        setColor(shade, shade + 0.03f, shade + 0.08f);
        alpha = 0;
    }

    @Override
    public void tick() {
        super.tick();
        oRoll = roll;
        roll += spin;
        // rolls in and out slowly, and thins out right in front of your face
        float fade = Math.min(1, Math.min(age / 70f, (lifetime - age) / 90f));
        float near = 1;
        var player = Minecraft.getInstance().player;
        if (player != null) {
            double d = player.getEyePosition().distanceTo(new net.minecraft.world.phys.Vec3(x, y, z));
            near = (float) Mth.clamp((d - size * 0.6) / 6.0, 0, 1);
        }
        // a lightning flash shows through the murk: what was hiding in it stands out for a moment
        float flash = com.zomboidcraft.world.weather.WeatherView.flash();
        // a shader pack drops anything much fainter than a tenth, so the puff can't fade out by
        // alpha alone (it would just blink off): it grows in and shrinks away, and only dims a little
        float in = fade * near;
        quadSize = size * (0.35f + 0.65f * in);
        alpha = Mth.clamp(peak * (0.6f + 0.4f * in) * (1 - 0.5f * flash), 0, 1);
    }

    /**
     * Mist catches what little light there is: on a dark street it would otherwise be a grey
     * darker than the night behind it, and simply vanish. A floor of dim block light keeps it
     * reading as pale haze; near a lamp the real light takes over.
     */
    @Override
    protected int getLightCoords(float partialTick) {
        int packed = super.getLightCoords(partialTick);
        int block = (packed >> 4) & 0xF, sky = (packed >> 20) & 0xF;
        return sky << 20 | Math.max(block, 8) << 4;
    }

    @Override
    protected Layer getLayer() {
        return Layer.TRANSLUCENT;
    }
}
