package com.zomboidcraft.client.cars;

import com.zomboidcraft.ZomboidCraft;
import com.zomboidcraft.world.cars.CarProp;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * An aircraft's outside lights while its engines run: red and green navigation lights that flash (the
 * jets' wing tips, port red and starboard green; the helicopter's nose and tail), and the helicopter's
 * searchlight, a beam slanting down ahead of the nose that lights up the ground where it lands (L in the
 * pilot's seat switches it). Up close they're soft glows where they are; further off, where fog and haze
 * would swallow them, each one you could see from where you stand (nothing solid in the way) is drawn a few
 * blocks in front of the camera on the line to it instead, scaled to look exactly as it would, so lights,
 * beam and the lit patch carry through the murk the way lights in the night sky do.
 *
 * A shader pack (Photon) draws glow solid and throws away any pixel fainter than half, which left none of the
 * soft glows: there the lights are solid glowing dots its bloom softens, and the beam and patch are drawn as
 * lightning, the one see-through glow it keeps (white, faded by alpha, with no texture, so faded at the edges).
 */
final class AirLights {
    private static final float[] RED = {1f, 0.12f, 0.08f}, GREEN = {0.15f, 1f, 0.3f}, WHITE = {1f, 0.97f, 0.88f};
    /** Model-space positions (nose +z, left +x, up +y) of each aircraft's lights. */
    private static final Map<String, Light[]> LIGHTS = Map.of(
            "jet_corvid", new Light[]{new Light(6.75f, 2.1f, -3.2f, RED), new Light(-6.75f, 2.1f, -3.2f, GREEN)},
            "jet_gyrfalcon", new Light[]{new Light(7.25f, 2.1f, -5f, RED), new Light(-7.25f, 2.1f, -5f, GREEN)},
            "heli_hornbill", new Light[]{new Light(0, 1.35f, 6.6f, RED), new Light(0, 3.1f, -8.65f, GREEN)});
    /** The helicopter's searchlight under its nose, and the way it points: ahead and 30 degrees down. */
    private static final Vector3f SEARCH_AT = new Vector3f(0, 0.8f, 5.4f), SEARCH_DIR = new Vector3f(0, -0.5f, 0.866f);
    private static final double SEARCH_REACH = 140, NEAR = 256;
    /**
     * Past this far a light's glow fades out where it is and comes in drawn PROXY blocks in front of the camera
     * instead, on the line to it, where fog and haze don't reach; never smaller than MIN_ANGLE across (radians).
     */
    private static final double HAZE_FROM = 16, HAZE_FULL = 32, PROXY = 6, MIN_ANGLE = 0.004, MIN_DOT = 0.003;
    private static final Identifier POINT = ZomboidCraft.id("textures/effect/air_glow.png"), DOT = ZomboidCraft.id("textures/effect/air_dot.png"),
            BEAM = ZomboidCraft.id("textures/effect/rail_glow.png");
    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

    private record Light(float x, float y, float z, float[] rgb) {
    }

    /** The light blocks each helicopter's searchlight has put where it lands, client side only. */
    private static final Map<Integer, BlockPos> LIT = new HashMap<>();
    private static ClientLevel litIn;
    /**
     * Whether this frame's lights are solid dots (under a shader pack): they need fewer pixels than a soft glow,
     * and switch from where they are to in front of the camera rather than fading across (a faded solid dot is dark).
     */
    private static boolean solid;

    private AirLights() {
    }

    static void register() {
        LevelRenderEvents.COLLECT_SUBMITS.register(AirLights::world);
        ClientTickEvents.END_CLIENT_TICK.register(AirLights::tick);
    }

    private static boolean lit(CarProp car) {
        return car.aircraft() && car.engineOn() && !car.broken() && !"burnt".equals(car.variant()) && LIGHTS.containsKey(car.model());
    }

    /** The searchlight's on with the engines, unless the pilot has switched it off. */
    private static boolean searching(CarProp car) {
        return car.helicopter() && lit(car) && !car.lightsOn();
    }

    /** Navigation lights: a dim steady glow with a bright double flash every 1.2 seconds. */
    private static float flash(CarProp car) {
        long t = (System.currentTimeMillis() + car.getId() * 137L) % 1200;
        return t < 80 || t >= 180 && t < 260 ? 1f : 0.22f;
    }

    private static Vec3 place(Vec3 pos, Quaternionf q, float pivot, float x, float y, float z) {
        Vector3f v = q.transform(new Vector3f(x, y - pivot, z));
        return new Vec3(pos.x + v.x, pos.y + pivot + v.y, pos.z + v.z);
    }

    // ------------------------------------------------------------------ every frame

    private static void world(LevelRenderContext context) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        CameraRenderState cam = context.levelState().cameraRenderState;
        if (level == null || cam == null || cam.pos == null || mc.player == null) {
            return;
        }
        boolean shaders = CarLights.shaderPackOn();
        // lights are always solid colour, dimmer for fainter; the beam fades by alpha under a shader pack
        CarLights.Quads points = new CarLights.Quads(cam.pos, false), beams = new CarLights.Quads(cam.pos, shaders);
        solid = shaders;
        float partial = cam.cameraEntityPartialTicks;
        // the aircraft here, and the far-off flybys drawn closer in (FarFlybys), whose lights stand in for the real ones'
        java.util.List<CarProp> cars = new java.util.ArrayList<>(level.getEntitiesOfClass(CarProp.class, new AABB(cam.pos, cam.pos).inflate(NEAR),
                car -> lit(car) && !FarFlybys.covers(car)));
        for (CarProp far : FarFlybys.shown()) {
            if (lit(far)) {
                cars.add(far);
            }
        }
        for (CarProp car : cars) {
            Quaternionf q = car.flight().qO.slerp(car.flight().q, partial, new Quaternionf());
            Vec3 pos = car.getPosition(partial);
            float pivot = car.pivot(), flash = flash(car);
            boolean own = car.hasPassenger(mc.player);
            for (Light light : LIGHTS.get(car.model())) {
                Vec3 at = place(pos, q, pivot, light.x(), light.y(), light.z());
                float k = flash > 0.5f ? 1f : 0.3f;
                point(level, car, cam.pos, at, 0.09 + 0.07 * flash, light.rgb(), k, own, points);
            }
            if (searching(car)) {
                search(level, car, q, pos, pivot, cam.pos, own, points, beams);
            }
        }
        if (beams.count > 0) {
            context.submitNodeCollector().submitCustomGeometry(context.poseStack(),
                    shaders ? RenderTypes.lightning() : CarLights.glowType(BEAM, false), beams::emit);
        }
        if (points.count > 0) {
            context.submitNodeCollector().submitCustomGeometry(context.poseStack(),
                    shaders ? RenderTypes.beaconBeam(DOT, false) : CarLights.glowType(POINT, false), points::emit);
        }
    }

    /**
     * A light of half size half at at: a soft glow where it is while it's near; further off, where fog and haze
     * would swallow it, the same glow drawn close in front of the camera along the line to it, sized to look
     * as big as it would (never smaller than a few pixels), as long as nothing solid is in the way.
     */
    private static void point(ClientLevel level, CarProp car, Vec3 cam, Vec3 at, double half, float[] rgb, float strength, boolean own,
                              CarLights.Quads quads) {
        double d = at.distanceTo(cam);
        float near = near(d);
        if (solid) {
            near = near < 0.5f ? 0 : 1;
        }
        if (near > 0.01f) {
            quads.spot(at, half, quads.shade(rgb, strength * near));
        }
        if (own || near > 0.99f || !clear(level, cam, at, car)) {
            return;
        }
        Vec3 dir = at.subtract(cam).scale(1 / d);
        quads.spot(cam.add(dir.scale(PROXY)), PROXY * Math.max(half / d, solid ? MIN_DOT : MIN_ANGLE), quads.shade(rgb, strength * (1 - near)));
    }

    /** How much of a light is drawn where it really is (1) rather than in front of the camera (0), by distance. */
    private static float near(double d) {
        return (float) (1 - Math.clamp((d - HAZE_FROM) / (HAZE_FULL - HAZE_FROM), 0, 1));
    }

    /** The searchlight: its lamp, the beam down to where it lands, and a bright patch there. */
    private static void search(ClientLevel level, CarProp car, Quaternionf q, Vec3 pos, float pivot, Vec3 cam, boolean own,
                               CarLights.Quads points, CarLights.Quads beams) {
        Vec3 lamp = place(pos, q, pivot, SEARCH_AT.x, SEARCH_AT.y, SEARCH_AT.z);
        Vector3f d = q.transform(new Vector3f(SEARCH_DIR));
        Vec3 dir = new Vec3(d.x, d.y, d.z);
        BlockHitResult hit = level.clip(new ClipContext(lamp, lamp.add(dir.scale(SEARCH_REACH)), ClipContext.Block.COLLIDER,
                ClipContext.Fluid.ANY, car));
        boolean lands = hit.getType() != HitResult.Type.MISS;
        double length = lands ? hit.getLocation().distanceTo(lamp) : SEARCH_REACH;
        float dark = CarLights.darkness(level, BlockPos.containing(lamp));
        float strength = 0.22f + 0.18f * dark;
        point(level, car, cam, lamp, 0.35, WHITE, 1f, own, points);
        // the beam: a smooth cone of light; where it runs off into the haze it's drawn in front of the camera instead
        int steps = 32;
        Vec3[] here = new Vec3[steps + 1], proxy = new Vec3[steps + 1];
        double[] wide = new double[steps + 1], proxyWide = new double[steps + 1];
        int[] colour = new int[steps + 1], proxyColour = new int[steps + 1];
        boolean far = false;
        for (int i = 0; i <= steps; i++) {
            double f = (double) i / steps, s = length * f * f;
            Vec3 p = lamp.add(dir.scale(s));
            double width = 0.14 + s * 0.08, dist = p.distanceTo(cam);
            float k = (float) (strength * Math.min(1, s / 1.5) * Math.exp(-s / 80) * Math.min(1, (length - s) / 1.5 + 0.1));
            float n = near(dist);
            here[i] = p;
            wide[i] = width;
            colour[i] = beams.shade(WHITE, k * n);
            boolean seen = !own && n < 0.99f && clear(level, cam, p, car);
            far |= seen;
            Vec3 toward = p.subtract(cam).scale(1 / Math.max(dist, 1e-3));
            proxy[i] = cam.add(toward.scale(PROXY));
            proxyWide[i] = PROXY * width / Math.max(dist, 1e-3);
            proxyColour[i] = beams.shade(WHITE, seen ? k * (1 - n) : 0);
        }
        boolean faded = beams.blended;
        if (faded) {
            beams.fadeStrip(here, wide, colour);
        } else {
            beams.strip(here, wide, colour);
        }
        if (far && faded) {
            beams.fadeStrip(proxy, proxyWide, proxyColour);
        } else if (far) {
            beams.strip(proxy, proxyWide, proxyColour);
        }
        if (lands) {
            Direction face = hit.getDirection();
            Vec3 normal = new Vec3(face.getStepX(), face.getStepY(), face.getStepZ());
            Vec3 patch = hit.getLocation().add(normal.scale(0.02));
            double r = Math.max(1.5, length * 0.13);
            float n = near(patch.distanceTo(cam));
            float glow = 0.35f + 0.3f * dark;
            disc(beams, patch, normal, r, beams.shade(WHITE, glow * n));
            if (!own && n < 0.99f && clear(level, cam, patch.add(normal.scale(0.4)), car)) {
                double dist = patch.distanceTo(cam);
                Vec3 toward = patch.subtract(cam).scale(1 / dist);
                // seen from afar the lit patch is a soft smudge of light, flattened as the ground is
                disc(beams, cam.add(toward.scale(PROXY)), normal, PROXY * r / dist, beams.shade(WHITE, glow * (1 - n)));
            }
        }
    }

    private static void disc(CarLights.Quads beams, Vec3 c, Vec3 normal, double r, int colour) {
        if (beams.blended) {
            beams.fadeDisc(c, normal, r, colour);
        } else {
            beams.flat(c, normal, r, colour);
        }
    }

    /** Whether nothing solid stands between the camera and a light. */
    private static boolean clear(ClientLevel level, Vec3 cam, Vec3 at, CarProp car) {
        Vec3 to = at.subtract(cam);
        double d = to.length();
        if (d < 0.5) {
            return true;
        }
        Vec3 end = cam.add(to.scale((d - 0.4) / d));
        return level.clip(new ClipContext(cam, end, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, car)).getType() == HitResult.Type.MISS;
    }

    // ------------------------------------------------------------------ the searchlight's real light

    private static void tick(Minecraft client) {
        ClientLevel level = client.level;
        if (level != litIn) {
            LIT.clear();
            litIn = level;
        }
        if (level == null || client.player == null) {
            return;
        }
        Map<Integer, BlockPos> want = new HashMap<>();
        for (CarProp car : level.getEntitiesOfClass(CarProp.class, client.player.getBoundingBox().inflate(96), AirLights::searching)) {
            Quaternionf q = car.flight().q;
            Vec3 lamp = place(car.position(), q, car.pivot(), SEARCH_AT.x, SEARCH_AT.y, SEARCH_AT.z);
            Vector3f d = q.transform(new Vector3f(SEARCH_DIR));
            Vec3 dir = new Vec3(d.x, d.y, d.z);
            BlockHitResult hit = level.clip(new ClipContext(lamp, lamp.add(dir.scale(SEARCH_REACH)), ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE, car));
            if (hit.getType() == HitResult.Type.MISS) {
                continue;
            }
            BlockPos at = BlockPos.containing(hit.getLocation().subtract(dir.scale(0.6)));
            BlockPos had = LIT.get(car.getId());
            if (at.equals(had) || level.getBlockState(at).isAir() && !LIT.containsValue(at)) {
                want.put(car.getId(), at);
            }
        }
        LIT.forEach((id, pos) -> {
            if (!pos.equals(want.get(id)) && level.getBlockState(pos).is(Blocks.LIGHT)) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), FLAGS);
            }
        });
        want.forEach((id, pos) -> {
            if (!pos.equals(LIT.get(id))) {
                level.setBlock(pos, Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, 15), FLAGS);
            }
        });
        LIT.clear();
        LIT.putAll(want);
    }
}
