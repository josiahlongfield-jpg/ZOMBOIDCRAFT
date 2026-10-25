package com.zomboidcraft.client.combat;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.zomboidcraft.ZcConfig;
import com.zomboidcraft.ZomboidCraft;
import com.zomboidcraft.combat.Attachments;
import com.zomboidcraft.combat.GunType;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Gun lights and lasers, drawn on each computer for every player in sight who has one switched on. They're drawn
 * every frame from where the gun is that frame, so they keep up with the view.
 *
 * The laser runs from its lens on the right rail to a green dot on whatever is under the crosshair, with the gun on
 * the sights or pointed from the hip (RifleView turns it so its barrel lines up); while the gun is busy, in a
 * reload, it goes wherever the barrel does. The light throws a cone from its lens on the left rail: a soft pool
 * where it lands and a faint beam in the air, both in the dark only. It also lights the world there with a light
 * block put only into this computer's copy of the world, kept dim (guns.lightLevel) so it doesn't flood the rooms
 * around: the server's world never changes.
 */
public final class RailLights {
    private static final double RANGE = 32, LIGHT_RANGE = 24;
    private static final Identifier GLOW = ZomboidCraft.id("textures/effect/rail_glow.png");
    private static final int FULL_BRIGHT = 0xF000F0;
    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
    /** The laser's colours, and the light's (a cool white LED). */
    private static final float[] GREEN = {0.25f, 1f, 0.32f}, GREEN_CORE = {0.8f, 1f, 0.8f}, WHITE = {1f, 0.96f, 0.88f};
    /** From outside, where the laser and the light sit from a player's eye (x right, y up, z back). */
    private static final Vector3f LASER_OUTSIDE = new Vector3f(0.20f, -0.24f, -0.55f), LIGHT_OUTSIDE = new Vector3f(0.10f, -0.30f, -0.55f);

    private static final Map<UUID, BlockPos> LIT = new HashMap<>();
    /** Where each player's light landed in the last frame drawn, for the light block. */
    private static final Map<UUID, Land> LANDS = new HashMap<>();
    /** Iris's own "is a shader pack on" call, looked up once (none without Iris). */
    private static boolean irisAsked;
    private static Object iris;
    private static Method shaderPackInUse;
    private static ClientLevel litIn;

    /** Where a light landed (null for nothing in reach), the way its beam came, and how far it went. */
    private record Land(Vec3 at, Vec3 dir, double distance) {
    }

    /**
     * One player's gun this frame: their eye and look, the laser's and light's lenses, the way the barrel points,
     * and how far it's pointed at what's under the crosshair.
     */
    private record Held(Vec3 eye, Vec3 look, Vec3 laser, Vec3 light, Vec3 bore, float onTarget) {
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(RailLights::tick);
        LevelRenderEvents.COLLECT_SUBMITS.register(RailLights::draw);
    }

    // ------------------------------------------------------------------ the light block

    private static void tick(Minecraft client) {
        ClientLevel level = client.level;
        if (level != litIn) {
            LIT.clear();            // the old copy of the world went with its lights
            LANDS.clear();
            litIn = level;
        }
        if (level == null) {
            return;
        }
        int brightest = Mth.clamp(ZcConfig.get().guns.lightLevel, 0, 15);
        Set<UUID> lit = new HashSet<>();
        for (AbstractClientPlayer player : level.players()) {
            ItemStack gun = player.getMainHandItem();
            Land land = LANDS.get(player.getUUID());
            if (!GunType.isGun(gun) || player.isSpectator() || !Attachments.lightOn(gun) || land == null || brightest <= 0) {
                continue;
            }
            lit.add(player.getUUID());
            moveLight(level, player.getUUID(), land, brightest);
        }
        LIT.entrySet().removeIf(e -> {
            if (lit.contains(e.getKey())) {
                return false;
            }
            clear(level, e.getValue());     // switched off, put away, or gone
            return true;
        });
    }

    private static void moveLight(ClientLevel level, UUID id, Land land, int brightest) {
        BlockPos pos = null;
        if (land.at() != null) {
            // in the air just short of where it lands, back along the beam: always on the gun's side of the surface
            for (double back = 0.5; back < 3 && pos == null; back += 1) {
                BlockPos p = BlockPos.containing(land.at().subtract(land.dir().scale(back)));
                BlockState here = level.getBlockState(p);
                if (here.isAir() || here.is(Blocks.LIGHT) && p.equals(LIT.get(id))) {
                    pos = p;
                }
            }
        }
        // brightest up close, dimming as the beam spreads out
        int brightness = pos == null ? 0 : Mth.clamp(brightest - (int) (land.distance() / 5), Math.min(3, brightest), 15);
        BlockPos old = LIT.get(id);
        if (old != null && !old.equals(pos)) {
            clear(level, old);
            LIT.remove(id);
        }
        if (pos == null) {
            return;
        }
        BlockState here = level.getBlockState(pos);
        if (here.is(Blocks.LIGHT)) {
            if (here.getValue(LightBlock.LEVEL) != brightness && pos.equals(LIT.get(id))) {
                level.setBlock(pos, here.setValue(LightBlock.LEVEL, brightness), FLAGS);
            }
        } else if (here.isAir()) {
            level.setBlock(pos, Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, brightness), FLAGS);
            LIT.put(id, pos);
        }
    }

    private static void clear(ClientLevel level, BlockPos pos) {
        if (level.getBlockState(pos).is(Blocks.LIGHT)) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), FLAGS);
        }
    }

    // ------------------------------------------------------------------ drawing, every frame

    private static void draw(LevelRenderContext context) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        CameraRenderState cam = context.levelState().cameraRenderState;
        LANDS.clear();
        if (level == null || cam == null || cam.pos == null) {
            return;
        }
        ZcConfig.Guns config = ZcConfig.get().guns;
        boolean glow = config.glowWithShaders || !shaderPackOn();
        Quads quads = new Quads(cam.pos);
        for (AbstractClientPlayer player : level.players()) {
            ItemStack gun = player.getMainHandItem();
            if (!GunType.isGun(gun) || player.isSpectator()) {
                continue;
            }
            boolean light = Attachments.lightOn(gun), laser = Attachments.laserOn(gun);
            if (!light && !laser) {
                continue;
            }
            Held held = held(mc, player, cam);
            HitResult aim = aim(level, player, held.eye(), held.look(), RANGE);
            Vec3 target = aim.getType() == HitResult.Type.MISS ? held.eye().add(held.look().scale(RANGE)) : aim.getLocation();
            if (player == mc.player) {
                RifleView.aimDistance((float) held.eye().distanceTo(target));
            }
            if (laser) {
                laser(level, player, held, aim.getType() != HitResult.Type.MISS, target, quads, config.laserBeam && glow, glow);
            }
            if (light) {
                light(level, player, held, target, quads, config, glow);
            }
        }
        if (quads.count > 0) {
            context.submitNodeCollector().submitCustomGeometry(context.poseStack(), RenderTypes.eyes(GLOW), quads::emit);
        }
    }

    /** Where a player's gun is this frame: as first person draws it for this player, or roughly, from outside. */
    private static Held held(Minecraft mc, AbstractClientPlayer player, CameraRenderState cam) {
        if (player == mc.player && cam.isFirstPerson) {
            Vec3 eye = cam.pos, look = look(cam.xRot, cam.yRot);
            Basis b = new Basis(look);
            RifleView.Rail rail = RifleView.rail();
            if (rail != null) {
                // the hands are drawn at their own field of view: spread to the world's, so the beam leaves the lens
                // where the lens is seen
                float s = fovScale(cam);
                return new Held(eye, look, b.at(eye, rail.laser(), s), b.at(eye, rail.light(), s), b.way(rail.bore(), s), rail.onTarget());
            }
            return new Held(eye, look, b.at(eye, LASER_OUTSIDE, 1), b.at(eye, LIGHT_OUTSIDE, 1), look, 1);
        }
        float partial = cam.cameraEntityPartialTicks;
        Vec3 eye = player.getEyePosition(partial), look = player.getViewVector(partial);
        Basis b = new Basis(look);
        return new Held(eye, look, b.at(eye, LASER_OUTSIDE, 1), b.at(eye, LIGHT_OUTSIDE, 1), look, 1);
    }

    /** The world's field of view over the hands': how much wider a point off the middle is in the world. */
    private static float fovScale(CameraRenderState cam) {
        Matrix4f projection = cam.projectionMatrix;
        if (projection == null || !(projection.m11() > 0.01f)) {
            return 1;
        }
        float hud = cam.hudFov > 1 ? cam.hudFov : 70;
        return (float) (1 / projection.m11() / Math.tan(Math.toRadians(hud / 2)));
    }

    private static void laser(ClientLevel level, AbstractClientPlayer player, Held held, boolean onSomething, Vec3 target,
                              Quads quads, boolean beam, boolean glow) {
        boolean pointed = held.onTarget() > 0.999f;
        Vec3 dir = blend(held.bore(), target.subtract(held.laser()).normalize(), held.onTarget());
        double reach = pointed ? held.laser().distanceTo(target) + 0.05 : RANGE;
        HitResult hit = aim(level, player, held.laser(), dir, reach);
        Vec3 end;
        boolean dot = true;
        if (hit.getType() != HitResult.Type.MISS) {
            end = hit.getLocation();        // what it lands on, or anything in the way first
        } else if (pointed && onSomething) {
            end = target;
        } else {
            end = held.laser().add(dir.scale(RANGE));
            dot = false;
        }
        if (beam) {
            quads.ribbon(held.laser(), end, 14, (s, away) -> Math.max(0.0025, away * 0.0011),
                    (s, away) -> scaled(GREEN, (float) (0.1 + 0.25 * Math.exp(-s / 4))));
        }
        if (dot) {
            Vec3 c = end.add(quads.cam.subtract(end).normalize().scale(0.03));
            double size = 0.03 + 0.0025 * c.distanceTo(quads.cam);
            // with a shader pack the dot is drawn as a coloured surface, not added light: full strength keeps it bright
            quads.facing(c, size, scaled(GREEN, glow ? 0.7f : 1f));
            quads.facing(c, size * 0.35, scaled(GREEN_CORE, 1f));
        }
    }

    private static void light(ClientLevel level, AbstractClientPlayer player, Held held, Vec3 target, Quads quads, ZcConfig.Guns config,
                              boolean drawn) {
        Vec3 from = held.light();
        Vec3 axis = blend(held.bore(), target.subtract(from).normalize(), held.onTarget());
        BlockHitResult centre = level.clip(new ClipContext(from, from.add(axis.scale(LIGHT_RANGE)), ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, player));
        boolean lands = centre.getType() != HitResult.Type.MISS;
        double reach = lands ? centre.getLocation().distanceTo(from) : LIGHT_RANGE;
        LANDS.put(player.getUUID(), new Land(lands ? centre.getLocation() : null, axis, reach));

        float glow = (float) config.lightGlow;
        if (glow <= 0 || !drawn) {
            return;
        }
        double cone = Math.toRadians(Mth.clamp(config.lightCone, 2, 45));
        // the pool: a soft splash of light on whatever each part of the cone lands on, so it wraps round corners
        Vec3 u = perpendicular(axis), v = axis.cross(u);
        splash(level, player, from, axis, centre, cone * 0.55, 1f, glow, quads);
        for (int i = 0; i < 6; i++) {
            splash(level, player, from, around(axis, u, v, cone * 0.45, i * Math.PI / 3), null, cone * 0.5, 0.8f, glow, quads);
        }
        for (int i = 0; i < 10; i++) {
            splash(level, player, from, around(axis, u, v, cone * 0.9, (i + 0.5) * Math.PI / 5), null, cone * 0.45, 0.4f, glow, quads);
        }
        // the beam: faint in the air, brightest just out of the lens, thinning out
        float dark = darkness(level, BlockPos.containing(quads.cam));
        if (config.lightBeam && dark > 0.01f) {
            double length = Math.min(reach, 14), spread = Math.tan(cone * 0.6);
            float strength = 0.09f * glow * dark;
            quads.ribbon(from, from.add(axis.scale(length)), 10, (s, away) -> 0.035 + s * spread, (s, away) -> scaled(WHITE,
                    (float) (strength * Math.min(1, s / 0.5) * Math.exp(-s / 6) * Math.min(1, (length - s) / 0.6 + 0.05))));
        }
    }

    /** One part of a light's pool: a soft disc on the surface the ray lands on, sized to the cone there. */
    private static void splash(ClientLevel level, AbstractClientPlayer player, Vec3 from, Vec3 dir, BlockHitResult hit, double spread,
                               float weight, float glow, Quads quads) {
        if (hit == null) {
            hit = level.clip(new ClipContext(from, from.add(dir.scale(LIGHT_RANGE)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        }
        if (hit.getType() == HitResult.Type.MISS) {
            return;
        }
        Direction face = hit.getDirection();
        Vec3 normal = new Vec3(face.getStepX(), face.getStepY(), face.getStepZ());
        double distance = hit.getLocation().distanceTo(from);
        float dark = darkness(level, BlockPos.containing(hit.getLocation().add(normal.scale(0.5))));
        float strength = (float) (0.11 * glow * weight * dark / (1 + (distance / 9) * (distance / 9)));
        if (strength < 0.004f) {
            return;
        }
        quads.flat(hit.getLocation().add(normal.scale(0.012)), normal, Math.max(0.15, distance * Math.tan(spread)), scaled(WHITE, strength));
    }

    /**
     * Whether a shader pack is drawing the world (asked of Iris, when it is installed). The glow is added light, but
     * shader packs draw it as a surface coloured by its strength, so a faint glow comes out as a dark smear.
     */
    private static boolean shaderPackOn() {
        if (!irisAsked) {
            irisAsked = true;
            try {
                Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
                iris = api.getMethod("getInstance").invoke(null);
                shaderPackInUse = api.getMethod("isShaderPackInUse");
            } catch (ReflectiveOperationException | LinkageError e) {
                shaderPackInUse = null;
            }
        }
        try {
            return shaderPackInUse != null && (boolean) shaderPackInUse.invoke(iris);
        } catch (ReflectiveOperationException e) {
            return false;
        }
    }

    /** How dark it is somewhere, from 0 (daylight: a gun light shows nothing) to 1 (night, or deep indoors). */
    private static float darkness(ClientLevel level, BlockPos pos) {
        float sky = Math.max(0, level.getBrightness(LightLayer.SKY, pos) - level.getSkyDarken()) / 15f;
        return 1 - Mth.clamp((sky - 0.3f) / 0.55f, 0, 1);
    }

    private static HitResult aim(ClientLevel level, AbstractClientPlayer player, Vec3 from, Vec3 dir, double reach) {
        Vec3 end = from.add(dir.scale(reach));
        BlockHitResult block = level.clip(new ClipContext(from, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        Vec3 stop = block.getType() == HitResult.Type.MISS ? end : block.getLocation();
        EntityHitResult entity = ProjectileUtil.getEntityHitResult(player, from, stop, new AABB(from, stop).inflate(1.0),
                e -> e instanceof LivingEntity && e.isAlive() && !e.isSpectator() && e != player, from.distanceToSqr(stop));
        return entity != null ? entity : block;
    }

    // ------------------------------------------------------------------ vectors

    /** Which way a camera looks, from its angles (degrees). */
    private static Vec3 look(float xRot, float yRot) {
        float pitch = xRot * Mth.DEG_TO_RAD, yaw = yRot * Mth.DEG_TO_RAD;
        return new Vec3(-Mth.sin(yaw) * Mth.cos(pitch), -Mth.sin(pitch), Mth.cos(yaw) * Mth.cos(pitch));
    }

    /** A view's right, up and forward, for points given from the eye (x right, y up, z back). */
    private record Basis(Vec3 right, Vec3 up, Vec3 look) {
        Basis(Vec3 look) {
            this(rightOf(look), rightOf(look).cross(look).normalize(), look);
        }

        private static Vec3 rightOf(Vec3 look) {
            Vec3 right = look.cross(new Vec3(0, 1, 0));
            return right.lengthSqr() < 1e-6 ? new Vec3(1, 0, 0) : right.normalize();
        }

        /** A point from the eye, spread sideways and up by s. */
        Vec3 at(Vec3 eye, Vector3f p, float s) {
            return eye.add(right.scale(p.x * s)).add(up.scale(p.y * s)).add(look.scale(-p.z));
        }

        Vec3 way(Vector3f d, float s) {
            return right.scale(d.x * s).add(up.scale(d.y * s)).add(look.scale(-d.z)).normalize();
        }
    }

    /** From a toward b, by t (both unit vectors). */
    private static Vec3 blend(Vec3 a, Vec3 b, float t) {
        Vec3 mixed = a.scale(1 - t).add(b.scale(t));
        return mixed.lengthSqr() < 1e-8 ? b : mixed.normalize();
    }

    private static Vec3 perpendicular(Vec3 axis) {
        Vec3 side = axis.cross(Math.abs(axis.y) < 0.9 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0));
        return side.normalize();
    }

    /** A way off the axis by an angle, turned round it by another. */
    private static Vec3 around(Vec3 axis, Vec3 u, Vec3 v, double off, double turn) {
        double s = Math.sin(off);
        return axis.scale(Math.cos(off)).add(u.scale(s * Math.cos(turn))).add(v.scale(s * Math.sin(turn))).normalize();
    }

    /** A colour times a strength, packed (the glow is added to what's behind it, so dimmer is fainter). */
    private static int scaled(float[] rgb, float strength) {
        int r = Mth.clamp((int) (rgb[0] * strength * 255), 0, 255), g = Mth.clamp((int) (rgb[1] * strength * 255), 0, 255),
                b = Mth.clamp((int) (rgb[2] * strength * 255), 0, 255);
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    // ------------------------------------------------------------------ the glow's quads

    private interface Along {
        double at(double s, double away);
    }

    private interface Shade {
        int at(double s, double away);
    }

    /** This frame's glow quads, four corners each, from the camera: a position, a texture corner, a colour, a normal. */
    private static final class Quads {
        private static final int STRIDE = 8;
        final Vec3 cam;
        private float[] corners = new float[64 * 4 * STRIDE];
        private int[] colours = new int[64 * 4];
        int count;

        Quads(Vec3 cam) {
            this.cam = cam;
        }

        private void corner(Vec3 p, float u, float v, int colour, Vec3 normal) {
            if (colours.length <= count) {
                corners = Arrays.copyOf(corners, corners.length * 2);
                colours = Arrays.copyOf(colours, colours.length * 2);
            }
            int k = count * STRIDE;
            corners[k] = (float) (p.x - cam.x);
            corners[k + 1] = (float) (p.y - cam.y);
            corners[k + 2] = (float) (p.z - cam.z);
            corners[k + 3] = u;
            corners[k + 4] = v;
            corners[k + 5] = (float) normal.x;
            corners[k + 6] = (float) normal.y;
            corners[k + 7] = (float) normal.z;
            colours[count++] = colour;
        }

        /** A disc facing the camera. */
        void facing(Vec3 c, double half, int colour) {
            Vec3 toCam = cam.subtract(c).normalize();
            Vec3 right = perpendicular(toCam), up = toCam.cross(right);
            square(c, right.scale(half), up.scale(half), colour, toCam);
        }

        /** A disc lying on a surface. */
        void flat(Vec3 c, Vec3 normal, double half, int colour) {
            Vec3 a = Math.abs(normal.y) > 0.5 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
            Vec3 b = normal.cross(a);
            square(c, a.scale(half), b.scale(half), colour, normal);
        }

        private void square(Vec3 c, Vec3 a, Vec3 b, int colour, Vec3 normal) {
            corner(c.subtract(a).subtract(b), 0, 0, colour, normal);
            corner(c.subtract(a).add(b), 0, 1, colour, normal);
            corner(c.add(a).add(b), 1, 1, colour, normal);
            corner(c.add(a).subtract(b), 1, 0, colour, normal);
        }

        /**
         * A soft strip from one point to another, turned to face the camera all along it, its half-width and colour
         * given by how far along it is (s) and how far from the camera (away); cut finer near its start.
         */
        void ribbon(Vec3 from, Vec3 to, int steps, Along halfWidth, Shade colour) {
            Vec3 line = to.subtract(from);
            double length = line.length();
            if (length < 0.05) {
                return;
            }
            Vec3 dir = line.scale(1 / length);
            Vec3 lastL = null, lastR = null;
            int lastC = 0;
            for (int i = 0; i <= steps; i++) {
                double f = (double) i / steps, s = length * f * f;
                Vec3 p = from.add(dir.scale(s));
                Vec3 toCam = cam.subtract(p);
                double away = toCam.length();
                Vec3 side = dir.cross(toCam);
                side = side.lengthSqr() < 1e-10 ? perpendicular(dir) : side.normalize();
                Vec3 w = side.scale(halfWidth.at(s, away));
                Vec3 l = p.subtract(w), r = p.add(w);
                int c = colour.at(s, away);
                if (lastL != null) {
                    Vec3 n = away > 1e-4 ? toCam.scale(1 / away) : new Vec3(0, 1, 0);
                    corner(lastL, 0, 0.5f, lastC, n);
                    corner(lastR, 1, 0.5f, lastC, n);
                    corner(r, 1, 0.5f, c, n);
                    corner(l, 0, 0.5f, c, n);
                }
                lastL = l;
                lastR = r;
                lastC = c;
            }
        }

        void emit(PoseStack.Pose pose, VertexConsumer out) {
            for (int i = 0; i < count; i++) {
                int k = i * STRIDE;
                out.addVertex(pose, corners[k], corners[k + 1], corners[k + 2])
                        .setColor(colours[i])
                        .setUv(corners[k + 3], corners[k + 4])
                        .setOverlay(OverlayTexture.NO_OVERLAY)
                        .setLight(FULL_BRIGHT)
                        .setNormal(pose, corners[k + 5], corners[k + 6], corners[k + 7]);
            }
        }
    }

    private RailLights() {
    }
}
