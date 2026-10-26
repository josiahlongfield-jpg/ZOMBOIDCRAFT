package com.zomboidcraft.client.cars;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.zomboidcraft.ZomboidCraft;
import com.zomboidcraft.world.cars.CarProp;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Headlights: while a car's engine runs, in the dark, each headlight throws a faint beam and soft
 * pools of light on the road ahead, and the road really lights up from light blocks put only into
 * this computer's copy of the world, spaced down the beams and fading with distance (the server's
 * world never changes). The lamps themselves, tail and brake lights and the dash glow are drawn
 * with the car, in {@link CarRenderer}.
 */
public final class CarLights {
    static final Identifier GLOW = ZomboidCraft.id("textures/effect/car_glow.png");
    static final int FULL_BRIGHT = 0xF000F0;
    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
    /** Only cars this near light the world, and only so many of them. */
    private static final double NEAR = 64;
    private static final int MOST_CARS = 6;
    private static final double REACH = 40;
    /** Light blocks down the beam: how far ahead, and how bright each is. */
    private static final double[] LIGHT_AT = {1.5, 4, 7, 10.5, 14, 18, 22.5, 27, 32, 37};
    private static final int[] LIGHT_LEVEL = {14, 15, 15, 15, 14, 14, 13, 12, 11, 10};
    /** Headlights point a little down at the road. */
    private static final double DIP = Math.toRadians(2.5);
    private static final float[] WARM = {1f, 0.93f, 0.78f};

    private static final Map<Integer, Map<BlockPos, Integer>> LIT = new HashMap<>();
    private static ClientLevel litIn;
    private static boolean irisAsked;
    private static Object iris;
    private static Method shaderPackInUse;

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(CarLights::tick);
        LevelRenderEvents.COLLECT_SUBMITS.register(CarLights::draw);
    }

    /** Whether a car's lamps are on: its engine is running, or its alarm is flashing them (a burnt-out shell has none left). */
    static boolean on(CarProp car) {
        if ("burnt".equals(car.variant())) {
            return false;
        }
        return car.engineOn() || car.alarming() && car.tickCount / 8 % 2 == 0;
    }

    /** A car's headlights, left and right, from its lamps (none if the model has no headlights found). */
    private static CarLamps.Spot[] headlights(CarProp car) {
        CarAssets.Ready full = CarAssets.get(car.model(), car.variant(), 0);
        CarLamps.Lamps lamps = full == null ? null : CarLamps.get(car.model(), car.variant(), full);
        if (lamps == null) {
            return new CarLamps.Spot[0];
        }
        return lamps.spots().stream().filter(s -> s.kind() == CarLamps.HEAD).toArray(CarLamps.Spot[]::new);
    }

    /** A point on the car (model space: x to the driver's side, z ahead) in the world, the car at pos turned to yaw. */
    static Vec3 world(Vec3 pos, float yaw, double x, double y, double z) {
        double r = Math.toRadians(yaw), c = Math.cos(r), s = Math.sin(r);
        return new Vec3(pos.x + x * c - z * s, pos.y + y, pos.z + x * s + z * c);
    }

    /** The way the headlights point: ahead, dipped. */
    private static Vec3 ahead(float yaw) {
        double r = Math.toRadians(yaw);
        return new Vec3(-Math.sin(r) * Math.cos(DIP), -Math.sin(DIP), Math.cos(r) * Math.cos(DIP));
    }

    // ------------------------------------------------------------------ the light blocks

    private static void tick(Minecraft client) {
        ClientLevel level = client.level;
        if (level != litIn) {
            LIT.clear();
            litIn = level;
        }
        if (level == null || client.player == null) {
            return;
        }
        Set<Integer> lit = new HashSet<>();
        List<CarProp> cars = new ArrayList<>(level.getEntitiesOfClass(CarProp.class, client.player.getBoundingBox().inflate(NEAR), CarLights::on));
        cars.sort((a, b) -> Double.compare(a.distanceToSqr(client.player), b.distanceToSqr(client.player)));
        for (CarProp car : cars.subList(0, Math.min(MOST_CARS, cars.size()))) {
            CarLamps.Spot[] heads = headlights(car);
            if (heads.length == 0 || darkness(level, car.blockPosition().above()) < 0.05f) {
                continue;
            }
            lit.add(car.getId());
            place(level, car, heads);
        }
        LIT.entrySet().removeIf(e -> {
            if (lit.contains(e.getKey())) {
                return false;
            }
            e.getValue().keySet().forEach(pos -> clear(level, pos));
            return true;
        });
    }

    /** Light blocks down the middle of the beams, in the air short of whatever they meet. */
    private static void place(ClientLevel level, CarProp car, CarLamps.Spot[] heads) {
        double x = 0, y = 0, z = 0;
        for (CarLamps.Spot h : heads) {
            x += h.x() / heads.length;
            y += h.y() / heads.length;
            z += h.z() / heads.length;
        }
        Vec3 from = world(car.position(), car.getYRot(), 0, y, z + 0.2), dir = ahead(car.getYRot());
        BlockHitResult hit = level.clip(new ClipContext(from, from.add(dir.scale(REACH)), ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, car));
        double end = hit.getType() == HitResult.Type.MISS ? REACH : hit.getLocation().distanceTo(from) - 0.5;
        Map<BlockPos, Integer> had = LIT.getOrDefault(car.getId(), Map.of()), want = new HashMap<>();
        for (int i = 0; i < LIGHT_AT.length && LIGHT_AT[i] <= Math.max(end, LIGHT_AT[0]); i++) {
            for (double back = 0; back < 2 && LIGHT_AT[i] - back > 0.5; back += 0.5) {
                BlockPos p = BlockPos.containing(from.add(dir.scale(Math.min(LIGHT_AT[i], end) - back)));
                BlockState here = level.getBlockState(p);
                if (here.isAir() || here.is(Blocks.LIGHT) && had.containsKey(p)) {
                    want.merge(p, LIGHT_LEVEL[i], Math::max);
                    break;
                }
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
            } else if (here.isAir() && !lightOfAnother(pos)) {
                level.setBlock(pos, Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, brightness), FLAGS);
                placed.put(pos, brightness);
            }
        });
        LIT.put(car.getId(), placed);
    }

    private static boolean lightOfAnother(BlockPos pos) {
        for (Map<BlockPos, Integer> m : LIT.values()) {
            if (m.containsKey(pos)) {
                return true;
            }
        }
        return false;
    }

    private static void clear(ClientLevel level, BlockPos pos) {
        if (level.getBlockState(pos).is(Blocks.LIGHT)) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), FLAGS);
        }
    }

    // ------------------------------------------------------------------ beams and pools, every frame

    private static void draw(LevelRenderContext context) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        CameraRenderState cam = context.levelState().cameraRenderState;
        if (level == null || cam == null || cam.pos == null || mc.player == null) {
            return;
        }
        boolean shaders = shaderPackOn();
        Quads quads = new Quads(cam.pos, shaders);
        float partial = cam.cameraEntityPartialTicks;
        for (CarProp car : level.getEntitiesOfClass(CarProp.class, new AABB(cam.pos, cam.pos).inflate(64), CarLights::on)) {
            CarLamps.Spot[] heads = headlights(car);
            if (heads.length == 0) {
                continue;
            }
            float yaw = car.getYRot(partial);
            Vec3 pos = car.getPosition(partial), dir = ahead(yaw);
            for (CarLamps.Spot h : heads) {
                Vec3 lens = world(pos, yaw, h.x(), h.y(), h.z() + 0.05);
                float dark = darkness(level, BlockPos.containing(lens));
                if (dark < 0.02f) {
                    continue;
                }
                beam(level, car, lens, dir, dark, quads);
            }
        }
        if (quads.count > 0) {
            context.submitNodeCollector().submitCustomGeometry(context.poseStack(), glowType(shaders), quads::emit);
        }
    }

    /** One headlight: a faint widening beam in the air and soft pools where it lands. */
    private static void beam(ClientLevel level, CarProp car, Vec3 lens, Vec3 dir, float dark, Quads quads) {
        Vec3 side = dir.cross(new Vec3(0, 1, 0)).normalize(), up = side.cross(dir).normalize();
        BlockHitResult centre = clip(level, car, lens, dir);
        double reach = centre.getType() == HitResult.Type.MISS ? REACH : centre.getLocation().distanceTo(lens);
        double length = Math.min(reach, 28);
        float strength = 0.1f * dark;
        quads.ribbon(lens, lens.add(dir.scale(length)), 10, s -> 0.06 + s * 0.16, s -> quads.shade(WARM,
                (float) (strength * Math.min(1, s / 0.4) * Math.exp(-s / 12) * Math.min(1, (length - s) / 0.8 + 0.05))));
        // the pools: rays fanned out across the cone, a soft disc where each lands
        for (int i = -2; i <= 2; i++) {
            for (int j = 0; j < 4; j++) {
                double yawOff = Math.toRadians(i * 6), down = Math.toRadians(j == 0 ? -1.2 : -2.5 + j * 3.5);
                Vec3 ray = dir.add(side.scale(Math.tan(yawOff))).add(up.scale(-Math.tan(down))).normalize();
                splash(level, car, lens, ray, (j < 2 ? 0.7f : 1f) * (1 - Math.abs(i) * 0.18f), dark, quads);
            }
        }
    }

    private static void splash(ClientLevel level, CarProp car, Vec3 from, Vec3 dir, float weight, float dark, Quads quads) {
        BlockHitResult hit = clip(level, car, from, dir);
        if (hit.getType() == HitResult.Type.MISS) {
            return;
        }
        Direction face = hit.getDirection();
        Vec3 normal = new Vec3(face.getStepX(), face.getStepY(), face.getStepZ());
        double distance = hit.getLocation().distanceTo(from);
        float strength = (float) (0.14 * weight * dark / (1 + (distance / 22) * (distance / 22)));
        if (strength < 0.004f) {
            return;
        }
        quads.flat(hit.getLocation().add(normal.scale(0.015)), normal, Mth.clamp(distance * 0.16, 0.4, 5), quads.shade(WARM, strength));
    }

    private static BlockHitResult clip(ClientLevel level, CarProp car, Vec3 from, Vec3 dir) {
        return level.clip(new ClipContext(from, from.add(dir.scale(REACH)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, car));
    }

    // ------------------------------------------------------------------ shared with the car's own drawing

    /** Added light normally; with a shader pack, drawn the way beacon beams are, which the packs make glow. */
    static RenderType glowType(boolean shaders) {
        return shaders ? RenderTypes.beaconBeam(GLOW, true) : RenderTypes.eyes(GLOW);
    }

    /**
     * A colour at a strength, packed: added light gets dimmer for fainter (a shader pack draws that dark, so
     * blended glow keeps the full colour and gets more see-through instead).
     */
    static int shade(float[] rgb, float strength, boolean blended) {
        float k = blended ? 1 : strength;
        int r = Mth.clamp((int) (rgb[0] * k * 255), 0, 255), g = Mth.clamp((int) (rgb[1] * k * 255), 0, 255),
                b = Mth.clamp((int) (rgb[2] * k * 255), 0, 255);
        int a = blended ? Mth.clamp((int) (strength * 255), 0, 255) : 255;
        return a << 24 | r << 16 | g << 8 | b;
    }

    /** Whether a shader pack is drawing the world (asked of Iris, when it's installed). */
    static boolean shaderPackOn() {
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

    /** How dark it is somewhere, from 0 (daylight: lamps throw nothing you'd see) to 1 (night, or deep indoors). */
    static float darkness(ClientLevel level, BlockPos pos) {
        float sky = Math.max(0, level.getBrightness(LightLayer.SKY, pos) - level.getSkyDarken()) / 15f;
        return 1 - Mth.clamp((sky - 0.3f) / 0.55f, 0, 1);
    }

    // ------------------------------------------------------------------ the glow's quads

    private interface Along {
        double at(double s);
    }

    private interface Shade {
        int at(double s);
    }

    /** This frame's glow quads, four corners each, from the camera: a position, a colour, a normal. */
    private static final class Quads {
        private static final int STRIDE = 8;
        final Vec3 cam;
        final boolean blended;
        private float[] corners = new float[64 * 4 * STRIDE];
        private int[] colours = new int[64 * 4];
        int count;

        Quads(Vec3 cam, boolean blended) {
            this.cam = cam;
            this.blended = blended;
        }

        int shade(float[] rgb, float strength) {
            return CarLights.shade(rgb, strength, blended);
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

        /** A soft disc lying on a surface. */
        void flat(Vec3 c, Vec3 normal, double half, int colour) {
            Vec3 a = Math.abs(normal.y) > 0.5 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
            Vec3 b = normal.cross(a).scale(half);
            a = a.scale(half);
            quad(c.subtract(a).subtract(b), c.subtract(a).add(b), c.add(a).add(b), c.add(a).subtract(b), 0, 0, 0, 1, 1, 1, 1, 0,
                    colour, colour, normal);
        }

        /** Four corners wound to face the normal's side, so the quad isn't culled. The first two take colour a, the others b. */
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

        /** A soft strip from one point to another, turned to face the camera all along it, cut finer near its start. */
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
                side = side.lengthSqr() < 1e-10 ? dir.cross(new Vec3(0, 1, 0)).normalize() : side.normalize();
                Vec3 w = side.scale(halfWidth.at(s));
                Vec3 l = p.subtract(w), r = p.add(w);
                int c = colour.at(s);
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

    private CarLights() {
    }
}
