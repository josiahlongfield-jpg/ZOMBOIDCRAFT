package com.zomboidcraft.client.hud;

import com.zomboidcraft.world.weather.Weather;
import com.zomboidcraft.world.weather.WeatherView;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.phys.Vec3;

/**
 * Rain on your face: out under open sky in the rain, beads of water gather round the edges of your sight (the middle
 * stays clear to aim), merge, and now and then one grows heavy enough to run down, leaving a wet trail. Heavier rain
 * and looking up bring more; a hat keeps most off; running and wind push them aside. Under a roof they dry off over
 * ten to twenty seconds. Drawn first, under the rest of the HUD.
 */
final class RainHud {
    private static final Identifier[] BEADS = {Hud.id("raindrop_round"), Hud.id("raindrop_tall"), Hud.id("raindrop_wide")};
    private static final Identifier STREAK = Hud.id("rain_streak");
    private static final int MAX = 48;
    /** A bead this big (gui pixels across) is heavy enough to run. */
    private static final float HEAVY = 7.5f;

    private static final class Drop {
        float x, y, size, alpha = 0, life, dry, runSpeed, trailTop, trailAlpha;
        int bead;
        boolean running;
    }

    private static final List<Drop> drops = new ArrayList<>();
    private static final RandomSource random = RandomSource.create();
    private static float owed;

    static void render(GuiGraphicsExtractor g) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        ClientLevel level = mc.level;
        float strength = HudSettings.rainOnFace;
        if (player == null || level == null || strength <= 0 || player.isSpectator()) {
            drops.clear();
            return;
        }
        if (player.isUnderWater()) {
            drops.clear();
            return;
        }
        float dt = Hud.dt;
        int w = g.guiWidth(), h = g.guiHeight();
        Vec3 eye = player.getEyePosition();
        boolean exposed = level.isRainingAt(BlockPos.containing(eye.x, eye.y + 0.5, eye.z));
        Weather weather = WeatherView.client();
        float rain = Math.max(level.getRainLevel(1f), weather == null ? 0 : weather.rain());

        // new drops: more in heavy rain and looking up, far fewer under a hat
        if (exposed && rain > 0.05f) {
            float look = Mth.clamp(1 - player.getXRot() / 60f, 0.3f, 2.6f);
            float hat = player.getItemBySlot(EquipmentSlot.HEAD).isEmpty() ? 1f : 0.3f;
            owed += dt * 5f * strength * rain * look * hat;
            while (owed >= 1) {
                owed--;
                land(w, h);
            }
        } else {
            owed = 0;
        }

        // running and wind push drops: outward from the middle when you run, sideways with the wind
        double speed = player.getDeltaMovement().horizontalDistance();
        float outward = (float) Math.max(0, speed - 0.15) * 0.9f;
        float side = 0;
        if (weather != null && exposed) {
            float rel = (weather.windYaw() - player.getYRot()) * Mth.DEG_TO_RAD;
            side = Mth.sin(rel) * weather.wind() * 6f;
        }

        Iterator<Drop> it = drops.iterator();
        while (it.hasNext()) {
            Drop d = it.next();
            d.life -= dt;
            // a drop beads up at once, then dries: over ten to twenty seconds under cover, slower while it's still raining on you
            if (!exposed) d.alpha -= dt * d.dry;
            else if (d.life > 0) d.alpha = Math.min(1, d.alpha + dt * 6);
            else d.alpha -= dt * d.dry * 0.5f;
            if (d.alpha <= 0) {
                it.remove();
                continue;
            }
            float cx = d.x - w / 2f, cy = d.y - h / 2f;
            float len = Math.max(1, Mth.sqrt(cx * cx + cy * cy));
            d.x += (cx / len * outward * 40 + side) * dt;
            d.y += cy / len * outward * 40 * dt;
            if (d.running) {
                d.runSpeed = Math.min(70, d.runSpeed + dt * 60);
                d.y += d.runSpeed * dt;
                d.x += (random.nextFloat() - 0.5f) * 6 * dt;
                // a running drop leaves itself behind on the way down
                d.size = Math.max(3.5f, d.size - dt * 0.9f);
                if (d.size <= 3.5f) {
                    d.running = false;
                    d.trailAlpha = Math.min(d.trailAlpha, 0.8f);
                }
            } else if (d.size >= HEAVY && random.nextFloat() < dt * 0.6f) {
                d.running = true;
                d.trailTop = d.y;
                d.trailAlpha = 1;
                d.runSpeed = 8;
            }
            d.trailAlpha = Math.max(0, d.trailAlpha - dt * 0.12f);
            if (d.y - d.size > h || d.x < -d.size || d.x > w + d.size) {
                it.remove();
                continue;
            }
            draw(g, d, strength);
        }
    }

    /** One drop lands somewhere toward the edges, merging into a bead it touches. */
    private static void land(int w, int h) {
        float x = 0, y = 0;
        for (int tries = 0; tries < 8; tries++) {
            x = random.nextFloat();
            y = random.nextFloat();
            float edge = Math.max(Math.abs(x - 0.5f), Math.abs(y - 0.5f)) * 2;
            if (edge > 0.45f && random.nextFloat() < edge * edge) break;
        }
        float px = x * w, py = y * h;
        float size = 2.5f + random.nextFloat() * random.nextFloat() * 6f;
        for (Drop d : drops) {
            float r = (d.size + size) * 0.45f;
            if (!d.running && Math.abs(d.x - px) < r && Math.abs(d.y - py) < r) {
                d.size = Math.min(14, Mth.sqrt(d.size * d.size + size * size));
                d.life = Math.max(d.life, 8);
                return;
            }
        }
        if (drops.size() >= MAX) drops.remove(0);
        Drop d = new Drop();
        d.x = px;
        d.y = py;
        d.size = size;
        d.bead = random.nextInt(BEADS.length);
        d.life = 10 + random.nextFloat() * 14;
        d.dry = 1 / (10 + random.nextFloat() * 10);
        drops.add(d);
    }

    private static void draw(GuiGraphicsExtractor g, Drop d, float strength) {
        float a = Mth.clamp(d.alpha, 0, 1) * Math.min(1, 0.55f + 0.3f * strength);
        if (d.trailAlpha > 0 && d.y - d.trailTop > 2) {
            float tw = d.size * 0.55f;
            Hud.sprite(g, STREAK, d.x - tw / 2, d.trailTop, tw, d.y - d.trailTop, Hud.alpha(0xFFFFFFFF, a * d.trailAlpha));
        }
        float s = d.size;
        Hud.sprite(g, BEADS[d.bead], d.x - s / 2, d.y - s / 2, s, d.running ? s * 1.15f : s, Hud.alpha(0xFFFFFFFF, a));
    }

    private RainHud() {
    }
}
