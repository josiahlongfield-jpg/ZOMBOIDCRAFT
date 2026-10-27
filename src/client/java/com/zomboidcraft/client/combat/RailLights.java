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
import java.util.Random;
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
 * reload, it goes wherever the barrel does. The light shines straight out of its lens on the left rail, the way the
 * barrel points, never bent toward what's under the crosshair: a soft pool where it lands and a faint beam in the
 * air, both in the dark only. It also lights the world with light blocks put only into this computer's copy of the
 * world: the brightest just short of where it lands (guns.lightLevel), so the space it's pointed at lights up, and a
 * few faint ones on the way there, dimmest by the gun, so it doesn't light a bubble round you. The server's world
 * never changes.
 *
 * Shader packs draw glow as solid emissive surfaces and throw away the faint parts (Photon drops anything under half
 * strength), so with one on the laser's dot is drawn solid and bigger, and its beam is dust: specks lit along it,
 * fresh each frame, which the pack's frame blending smooths into a faint beam.
 */
public final class RailLights {
    private static final double RANGE = 32, LIGHT_RANGE = 24;
    /** The light where it lands: a level dimmer every POOL_FADE blocks off, never under POOL_LEAST. */
    private static final double POOL_FADE = 8;
    private static final int POOL_LEAST = 5;
    /** The faint lights on the way: none nearer the gun than TRAIL_START, TRAIL_BASE there, a level up every TRAIL_RISE blocks. */
    private static final double TRAIL_START = 2.5, TRAIL_RISE = 4;
    private static final int TRAIL_BASE = 2;
    private static final Identifier GLOW = ZomboidCraft.id("textures/effect/rail_glow.png");
    private static final int FULL_BRIGHT = 0xF000F0;
    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
    /** The laser's colours, and the light's (a cool white LED). */
    private static final float[] GREEN = {0.25f, 1f, 0.32f}, GREEN_CORE = {0.8f, 1f, 0.8f}, WHITE = {1f, 0.96f, 0.88f};
    /** Specks of dust lit along a laser's beam per block, with a shader pack on (guns.laserBeamStrength scales it). */
    private static final double DUST_PER_BLOCK = 4;
    private static final Random DUST = new Random();
    /** From outside, where the laser and the light sit from a player's eye (x right, y up, z back). */
    private static final Vector3f LASER_OUTSIDE = new Vector3f(0.20f, -0.24f, -0.55f), LIGHT_OUTSIDE = new Vector3f(0.10f, -0.30f, -0.55f);

    /**
     * Where each player's laser and light were when their body was last posed (HoldPoses), from their feet in the
     * world, and when (wall clock ns): what's used from outside while it's fresh.
     */
    private static final Map<Integer, Seen> SEEN = new HashMap<>();
    private static final long SEEN_FRESH_NS = 250_000_000L;

    /** The light blocks each player's gun light has put in, and how bright each is. */
    private static final Map<UUID, Map<BlockPos, Integer>> LIT = new HashMap<>();
    /** Where each player's light went in the last frame drawn, for the light blocks. */
    private static final Map<UUID, Land> LANDS = new HashMap<>();
    /** Iris's own "is a shader pack on" call, looked up once (none without Iris). */
    private static boolean irisAsked;
    private static Object iris;
    private static Method shaderPackInUse;
    private static ClientLevel litIn;

    /** Where a light came from, where it landed (null for nothing in reach), the way it went, and how far. */
    private record Land(Vec3 from, Vec3 at, Vec3 dir, double distance) {
    }

    /**
     * One player's gun this frame: their eye and look, the laser's and light's lenses, the way the barrel points,
     * and how far it's pointed at what's under the crosshair.
     */
    private record Held(Vec3 eye, Vec3 look, Vec3 laser, Vec3 light, Vec3 bore, float onTarget) {
    }

    private record Seen(Vec3 laser, Vec3 light, long at) {
    }

    /**
     * A player's body has just been posed holding a gun: its laser and light lenses, in the player model's pixels
     * (y down, facing -z), are put into the world the way the renderer puts the model there.
     */
    static void seen(net.minecraft.client.renderer.entity.state.AvatarRenderState state, Vector3f laser, Vector3f light) {
        SEEN.put(state.id, new Seen(fromFeet(state, laser), fromFeet(state, light), System.nanoTime()));
    }

    private static Vec3 fromFeet(net.minecraft.client.renderer.entity.state.AvatarRenderState state, Vector3f p) {
        // LivingEntityRenderer: turn by the body, flip, scale 0.9375 (a player), up 1.501 blocks
        float s = 0.9375f * state.scale;
        Vector3f v = new Vector3f(-p.x / 16f * s, -(p.y / 16f - 1.501f) * s, p.z / 16f * s);
        v.rotateY((float) Math.toRadians(180 - state.bodyRot));
        if (state.hasPose(net.minecraft.world.entity.Pose.CROUCHING)) {
            v.y -= 2 / 16f * state.scale;   // the crouch drops the whole model a little
        }
        return new Vec3(v.x, v.y, v.z);
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
            SEEN.clear();
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
            moveLights(level, player.getUUID(), land, brightest, Mth.clamp(ZcConfig.get().guns.lightSources, 1, 8));
        }
        LIT.entrySet().removeIf(e -> {
            if (lit.contains(e.getKey())) {
                return false;
            }
            e.getValue().keySet().forEach(pos -> clear(level, pos));      // switched off, put away, or gone
            return true;
        });
    }

    /**
     * Puts one player's light blocks where the light is going now: the brightest just short of what it lands on,
     * fading a little the further off that is, and faint ones spaced back toward the gun, a level dimmer every few
     * blocks nearer it, so what lights up is the space it's pointed at. Pointed at nothing in reach, the end of its
     * reach gets a dim one.
     */
    private static void moveLights(ClientLevel level, UUID id, Land land, int brightest, int sources) {
        Map<BlockPos, Integer> had = LIT.getOrDefault(id, Map.of()), want = new HashMap<>();
        double end = land.at() != null ? land.distance() - 0.5 : land.distance();
        int pool = land.at() != null
                ? Mth.clamp(brightest - (int) (land.distance() / POOL_FADE), Math.min(POOL_LEAST, brightest), 15)
                : Math.max(1, brightest / 2);
        lightAt(level, land, end, pool, had, want);
        for (int i = 1; i < sources; i++) {
            double s = end * i / sources;
            int brightness = Math.min(pool - 1, TRAIL_BASE + (int) ((s - TRAIL_START) / TRAIL_RISE));
            if (s >= TRAIL_START && brightness > 0) {
                lightAt(level, land, s, brightness, had, want);
            }
        }
        for (BlockPos old : had.keySet()) {
            if (!want.containsKey(old)) {
                clear(level, old);
            }
        }
        Map<BlockPos, Integer> placed = new HashMap<>();
        want.forEach((pos, brightness) -> {
            BlockState here = level.getBlockState(pos);
            if (here.is(Blocks.LIGHT) && had.containsKey(pos)) {
                if (!brightness.equals(had.get(pos))) {
                    level.setBlock(pos, here.setValue(LightBlock.LEVEL, brightness), FLAGS);
                }
                placed.put(pos, brightness);
            } else if (here.isAir()) {
                level.setBlock(pos, Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, brightness), FLAGS);
                placed.put(pos, brightness);
            }
        });
        LIT.put(id, placed);
    }

    /** A light block s blocks down the beam, in the air on the gun's side of anything in the way: back along it a little if that's taken. */
    private static void lightAt(ClientLevel level, Land land, double s, int brightness, Map<BlockPos, Integer> had,
                                Map<BlockPos, Integer> want) {
        for (double back = 0; back < 2 && s - back > 0.2; back += 0.5) {
            BlockPos p = free(level, BlockPos.containing(land.from().add(land.dir().scale(s - back))), had);
            if (p != null) {
                want.merge(p, brightness, Math::max);
                return;
            }
        }
    }

    /**
     * Where a light block can go for a point on the beam: the block itself if it's air (or one of this light's), or,
     * when it's grass, a flower or anything else you walk through, the first free block just above it, so tall grass
     * round the beam doesn't push the light back along it. Null when something solid is in the way.
     */
    private static BlockPos free(ClientLevel level, BlockPos p, Map<BlockPos, Integer> had) {
        for (int up = 0; up < 3; up++) {
            BlockPos q = p.above(up);
            BlockState here = level.getBlockState(q);
            if (here.isAir() || here.is(Blocks.LIGHT) && had.containsKey(q)) {
                return q;
            }
            if (!here.getCollisionShape(level, q).isEmpty()) {
                return null;
            }
        }
        return null;
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
        boolean shaders = shaderPackOn(), glow = config.glowWithShaders || !shaders;
        Quads quads = new Quads(cam.pos, shaders);
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
                laser(level, player, held, aim.getType() != HitResult.Type.MISS, target, quads, config);
            }
            if (light) {
                light(level, player, held, quads, config, glow);
            }
        }
        if (quads.count > 0) {
            // see-through glow that ignores the light level; with a shader pack, drawn the way a beacon beam's solid
            // core is, which the packs make glow
            context.submitNodeCollector().submitCustomGeometry(context.poseStack(),
                    shaders ? RenderTypes.beaconBeam(GLOW, false) : RenderTypes.eyes(GLOW), quads::emit);
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
        Seen seen = SEEN.get(player.getId());
        if (seen != null && System.nanoTime() - seen.at() < SEEN_FRESH_NS) {
            // the lenses where the gun is in the arms they're seen with, turned to what the player looks at
            Vec3 feet = player.getPosition(partial);
            return new Held(eye, look, feet.add(seen.laser()), feet.add(seen.light()), look, 1);
        }
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
                              Quads quads, ZcConfig.Guns config) {
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
        float beam = (float) Math.max(0, config.laserBeamStrength);
        if (config.laserBeam && beam > 0) {
            if (quads.solid) {
                dust(held.laser(), end, beam, quads);
            } else {
                quads.ribbon(held.laser(), end, 14, (s, away) -> Math.max(0.0025, away * 0.0011),
                        (s, away) -> quads.shade(GREEN, (float) Math.min(1, beam * (0.1 + 0.25 * Math.exp(-s / 4)))));
            }
        }
        if (dot) {
            Vec3 toCam = quads.cam.subtract(end).normalize();
            Vec3 c = end.add(toCam.scale(0.03));
            double away = c.distanceTo(quads.cam), scale = Math.max(0.1, config.laserDotSize);
            if (quads.solid) {
                // a pack draws only the solid middle of the glow (half its size across): a dot a few pixels wide
                double size = (0.024 + 0.009 * away) * scale;
                quads.facing(c, size, quads.shade(GREEN, 1f));
                quads.facing(c.add(toCam.scale(0.01)), size * 0.45, quads.shade(GREEN_CORE, 1f));
            } else {
                double size = (0.03 + 0.0025 * away) * scale;
                quads.facing(c, size, quads.shade(GREEN, 0.85f));
                quads.facing(c, size * 0.35, quads.shade(GREEN_CORE, 1f));
            }
        }
    }

    /**
     * A laser's beam with a shader pack on: specks of dust lit along it, new ones each frame, thickest near the lens
     * and thinning out toward the dot. Each is a pixel or so wide; the pack blends frames, so together they read as
     * a faint beam that glints.
     */
    private static void dust(Vec3 from, Vec3 to, float strength, Quads quads) {
        Vec3 line = to.subtract(from);
        double length = line.length();
        if (length < 0.1) {
            return;
        }
        Vec3 dir = line.scale(1 / length);
        int specks = (int) Math.min(600, strength * DUST_PER_BLOCK * length);
        int colour = quads.shade(GREEN, 1f);
        for (int i = 0; i < specks; i++) {
            Vec3 p = from.add(dir.scale(length * Math.pow(DUST.nextDouble(), 1.7)));
            quads.speck(p, Math.max(0.0015, p.distanceTo(quads.cam) * 0.0009), colour);
        }
    }

    private static void light(ClientLevel level, AbstractClientPlayer player, Held held, Quads quads, ZcConfig.Guns config,
                              boolean drawn) {
        // straight out the way the barrel points (from the hip RifleView turns the gun so this meets the middle of
        // the view guns.lightZero blocks ahead), so it doesn't jump onto whatever passes under the crosshair
        Vec3 from = held.light(), axis = held.bore();
        BlockHitResult centre = level.clip(new ClipContext(from, from.add(axis.scale(LIGHT_RANGE)), ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, player));
        boolean lands = centre.getType() != HitResult.Type.MISS;
        double reach = lands ? centre.getLocation().distanceTo(from) : LIGHT_RANGE;
        LANDS.put(player.getUUID(), new Land(from, lands ? centre.getLocation() : null, axis, reach));

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
            quads.ribbon(from, from.add(axis.scale(length)), 10, (s, away) -> 0.035 + s * spread, (s, away) -> quads.shade(WHITE,
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
        quads.flat(hit.getLocation().add(normal.scale(0.012)), normal, Math.max(0.15, distance * Math.tan(spread)), quads.shade(WHITE, strength));
    }

    /**
     * Whether a shader pack is drawing the world (asked of Iris, when it is installed). Shader packs draw the glow as
     * a solid surface and drop its faint parts, so it's drawn differently for them.
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
        /**
         * Drawn solid by a shader pack (its beacon-beam look), which throws away the faint parts: the laser's beam is
         * dust and its dot bigger. Without one the glow is see-through, fainter for weaker.
         */
        final boolean solid;
        private float[] corners = new float[64 * 4 * STRIDE];
        private int[] colours = new int[64 * 4];
        int count;

        Quads(Vec3 cam, boolean solid) {
            this.cam = cam;
            this.solid = solid;
        }

        /**
         * A colour at a strength, packed: always the full colour, more see-through for fainter. (The glow's render
         * type blends over what's behind rather than adding to it, so a dimmed colour would draw dark.)
         */
        int shade(float[] rgb, float strength) {
            int r = Mth.clamp((int) (rgb[0] * 255), 0, 255), g = Mth.clamp((int) (rgb[1] * 255), 0, 255),
                    b = Mth.clamp((int) (rgb[2] * 255), 0, 255);
            return Mth.clamp((int) (strength * 255), 0, 255) << 24 | r << 16 | g << 8 | b;
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

        /** A tiny square facing the camera, solid all over (the glow's middle only). */
        void speck(Vec3 c, double half, int colour) {
            Vec3 toCam = cam.subtract(c).normalize();
            Vec3 a = perpendicular(toCam).scale(half), b = toCam.cross(perpendicular(toCam)).scale(half);
            quad(c.subtract(a).subtract(b), c.subtract(a).add(b), c.add(a).add(b), c.add(a).subtract(b),
                    0.4f, 0.4f, 0.4f, 0.6f, 0.6f, 0.6f, 0.6f, 0.4f, colour, colour, toCam);
        }

        /** A disc lying on a surface. */
        void flat(Vec3 c, Vec3 normal, double half, int colour) {
            Vec3 a = Math.abs(normal.y) > 0.5 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
            Vec3 b = normal.cross(a);
            square(c, a.scale(half), b.scale(half), colour, normal);
        }

        private void square(Vec3 c, Vec3 a, Vec3 b, int colour, Vec3 normal) {
            quad(c.subtract(a).subtract(b), c.subtract(a).add(b), c.add(a).add(b), c.add(a).subtract(b), 0, 0, 0, 1, 1, 1, 1, 0,
                    colour, colour, normal);
        }

        /**
         * Four corners (texture corners u, v for each) wound anticlockwise as seen from the normal's side, so the
         * quad faces that way and isn't culled as a back face. The first two corners take colour a, the others b.
         */
        private void quad(Vec3 p0, Vec3 p1, Vec3 p2, Vec3 p3, float u0, float v0, float u1, float v1, float u2, float v2,
                          float u3, float v3, int a, int b, Vec3 normal) {
            if (p1.subtract(p0).cross(p2.subtract(p0)).dot(normal) >= 0) {
                corner(p0, u0, v0, a, normal);
                corner(p1, u1, v1, a, normal);
                corner(p2, u2, v2, b, normal);
                corner(p3, u3, v3, b, normal);
            } else {
                corner(p1, u1, v1, a, normal);
                corner(p0, u0, v0, a, normal);
                corner(p3, u3, v3, b, normal);
                corner(p2, u2, v2, b, normal);
            }
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
                    quad(lastL, lastR, r, l, 0, 0.5f, 1, 0.5f, 1, 0.5f, 0, 0.5f, lastC, c, n);
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
