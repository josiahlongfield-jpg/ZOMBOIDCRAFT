package com.zomboidcraft.client.hud;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.zomboidcraft.ZomboidCraft;
import com.zomboidcraft.client.creature.spider.SpiderDraw;
import com.zomboidcraft.creature.HarvestmanEntity;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * A little health bar over every enemy you can see near you (zombies, the Harvestman, any monster): a thin red bar in
 * a dark frame, in the kit screen's colours, turned to face you. What a hit takes off shows pale a moment, then drains
 * away. It fades out with distance and isn't shown through walls, on an apparition, or while filming.
 */
public final class EnemyBars {
    private static final Identifier WHITE = ZomboidCraft.id("textures/entity/harvestman/white.png");
    private static final int FULL_BRIGHT = 0xF000F0;
    /** Shown out to this far (blocks), fading over the last few. */
    private static final double RANGE = 28, FADE = 6;
    private static final float HEIGHT = 0.055f, PAD = 0.012f;
    /** Kit palette: dark glass frame, the empty track, the health (the kit's "bad" red), what was just lost. */
    private static final int FRAME = 0x0B0C0E, TRACK = 0x2A1212, FILL = 0xD0675E, LOST = 0xE8E4DA;

    /** Per enemy: the health its pale part still shows, when it was last hit, and whether you can see it. */
    private static final class Seen {
        float shown = -1, last = -1;
        long hitAt;
        boolean visible;
        int checkedAt = -100;
        int frameSeen;
    }

    private static final Map<Integer, Seen> SEEN = new HashMap<>();
    private static int frame;

    public static void register() {
        LevelRenderEvents.COLLECT_SUBMITS.register(EnemyBars::draw);
    }

    private static void draw(LevelRenderContext context) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || com.zomboidcraft.client.camera.CinematicView.clean()
                || context.levelState().cameraRenderState == null) {
            return;
        }
        Vec3 cam = context.levelState().cameraRenderState.pos;
        float pt = mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);
        long now = System.currentTimeMillis();
        int tick = mc.player.tickCount;
        frame++;
        List<float[]> bars = new ArrayList<>();
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof LivingEntity e) || !(e instanceof Enemy || e instanceof HarvestmanEntity)
                    || e.getId() < 0 || !e.isAlive() || e.isInvisible()) {
                continue;   // a negative id is an apparition only you see (HarvestmanPhantoms)
            }
            if (e instanceof HarvestmanEntity h && h.pose() == HarvestmanEntity.DEAD) {
                continue;
            }
            Vec3 at = e.getPosition(pt);
            double d = at.distanceTo(cam);
            if (d > RANGE) {
                continue;
            }
            Seen s = SEEN.computeIfAbsent(e.getId(), k -> new Seen());
            s.frameSeen = frame;
            if (tick - s.checkedAt >= 4 || tick < s.checkedAt) {
                s.checkedAt = tick;
                s.visible = mc.player.hasLineOfSight(e);
            }
            float max = Math.max(1, e.getMaxHealth()), hp = Mth.clamp(e.getHealth() / max, 0, 1);
            if (s.last < 0) {
                s.shown = s.last = hp;
            }
            if (hp < s.last - 1e-4f) {
                s.hitAt = now;
            }
            s.last = hp;
            // what was lost holds a moment, then drains down to what's left
            if (s.shown < hp || now - s.hitAt > 450) {
                s.shown = s.shown < hp ? hp : Math.max(hp, s.shown - 0.02f);
            }
            if (!s.visible) {
                continue;
            }
            Vec3 top = null;
            float width = 0.5f;
            if (e instanceof HarvestmanEntity h) {
                width = 0.85f;
                if (h.body instanceof SpiderDraw draw) {
                    Vec3 over = draw.overHead();
                    top = over == null ? null : at.add(over);
                }
            }
            if (top == null) {
                top = new Vec3(at.x, at.y + e.getBbHeight() + 0.4, at.z);
            }
            float fade = (float) Mth.clamp((RANGE - d) / FADE, 0, 1);
            bars.add(new float[] {(float) (top.x - cam.x), (float) (top.y - cam.y), (float) (top.z - cam.z), width, hp, s.shown, fade});
        }
        if (frame % 200 == 0) {
            int f = frame;
            SEEN.values().removeIf(s -> f - s.frameSeen > 200);
        }
        if (bars.isEmpty()) {
            return;
        }
        context.submitNodeCollector().submitCustomGeometry(context.poseStack(), RenderTypes.entityTranslucent(WHITE), (pose, out) -> {
            for (float[] b : bars) {
                bar(pose, out, b);
            }
        });
    }

    /** One bar at (x, y, z) from the camera, facing it, level with the horizon. */
    private static void bar(PoseStack.Pose pose, VertexConsumer out, float[] b) {
        Vector3f c = new Vector3f(b[0], b[1], b[2]);
        Vector3f toCam = new Vector3f(c).negate();
        if (toCam.lengthSquared() < 0.01f) {
            return;
        }
        toCam.normalize();
        Vector3f right = new Vector3f(0, 1, 0).cross(toCam);
        if (right.lengthSquared() < 1e-4f) {
            right.set(1, 0, 0);
        }
        right.normalize();
        Vector3f up = new Vector3f(toCam).cross(right).normalize();
        float w = b[3] / 2, h = HEIGHT / 2, hp = b[4], shown = b[5], fade = b[6];
        int a = (int) (fade * 235);
        quad(pose, out, c, right, up, toCam, -w - PAD, w + PAD, -h - PAD, h + PAD, 0, FRAME, (int) (fade * 200));
        quad(pose, out, c, right, up, toCam, -w, w, -h, h, 0.002f, TRACK, a);
        float x0 = -w, xHp = -w + 2 * w * hp, xShown = -w + 2 * w * shown;
        if (xShown > xHp + 1e-4f) {
            quad(pose, out, c, right, up, toCam, xHp, xShown, -h, h, 0.004f, LOST, a);
        }
        if (hp > 0) {
            quad(pose, out, c, right, up, toCam, x0, xHp, -h, h, 0.006f, FILL, a);
        }
    }

    private static void quad(PoseStack.Pose pose, VertexConsumer out, Vector3f c, Vector3f right, Vector3f up, Vector3f toCam,
                             float x0, float x1, float y0, float y1, float lift, int rgb, int alpha) {
        int colour = Math.max(0, Math.min(255, alpha)) << 24 | rgb;
        float[][] corners = {{x0, y0}, {x1, y0}, {x1, y1}, {x0, y1}};
        for (float[] k : corners) {
            float px = c.x + right.x * k[0] + up.x * k[1] + toCam.x * lift;
            float py = c.y + right.y * k[0] + up.y * k[1] + toCam.y * lift;
            float pz = c.z + right.z * k[0] + up.z * k[1] + toCam.z * lift;
            out.addVertex(pose, px, py, pz)
                    .setColor(colour)
                    .setUv(0.5f, 0.5f)
                    .setOverlay(OverlayTexture.NO_OVERLAY)
                    .setLight(FULL_BRIGHT)
                    .setNormal(pose, toCam.x, toCam.y, toCam.z);
        }
    }

    private EnemyBars() {
    }
}
