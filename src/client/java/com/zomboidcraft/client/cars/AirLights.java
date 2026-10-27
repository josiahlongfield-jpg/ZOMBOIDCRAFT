package com.zomboidcraft.client.cars;

import com.zomboidcraft.ZomboidCraft;
import com.zomboidcraft.world.cars.CarProp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
 * pilot's seat switches it). Up close they're glows in the world; further off fog and haze would swallow
 * those, so each light you could see from where you stand (nothing solid in the way) is also drawn as a
 * point of light over the view, as a light in the night sky looks through mist, and the searchlight's
 * beam and the patch it lights are dotted in the same way.
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
    /** Past this far the world's glows fade out under the haze and the points over the view take over. */
    private static final double HAZE_FROM = 18, HAZE_FULL = 40;
    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

    private record Light(float x, float y, float z, float[] rgb) {
    }

    /** A light seen from the camera this frame, for the view: where, what colour, how strong, how big. */
    private record Seen(Vec3 at, float[] rgb, float strength, int size) {
    }

    private static final List<Seen> SEEN = new ArrayList<>();
    /** The light blocks each helicopter's searchlight has put where it lands, client side only. */
    private static final Map<Integer, BlockPos> LIT = new HashMap<>();
    private static ClientLevel litIn;

    private AirLights() {
    }

    static void register() {
        LevelRenderEvents.COLLECT_SUBMITS.register(AirLights::world);
        HudElementRegistry.attachElementBefore(VanillaHudElements.CROSSHAIR, ZomboidCraft.id("air_lights"), AirLights::view);
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

    // ------------------------------------------------------------------ in the world, every frame

    private static void world(LevelRenderContext context) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        CameraRenderState cam = context.levelState().cameraRenderState;
        SEEN.clear();
        camPos = cam == null ? null : cam.pos;
        if (level == null || cam == null || cam.pos == null || mc.player == null) {
            return;
        }
        boolean shaders = CarLights.shaderPackOn();
        CarLights.Quads quads = new CarLights.Quads(cam.pos, shaders);
        float partial = cam.cameraEntityPartialTicks;
        for (CarProp car : level.getEntitiesOfClass(CarProp.class, new AABB(cam.pos, cam.pos).inflate(NEAR), AirLights::lit)) {
            Quaternionf q = car.flight().qO.slerp(car.flight().q, partial, new Quaternionf());
            Vec3 pos = car.getPosition(partial);
            float pivot = car.pivot(), flash = flash(car);
            boolean own = car.hasPassenger(mc.player);
            for (Light light : LIGHTS.get(car.model())) {
                Vec3 at = place(pos, q, pivot, light.x(), light.y(), light.z());
                double d = at.distanceTo(cam.pos);
                float near = (float) (1 - Math.clamp((d - HAZE_FROM) / (HAZE_FULL - HAZE_FROM), 0, 1));
                if (near > 0.01f) {
                    quads.spot(at, 0.18 + 0.4 * flash, quads.shade(light.rgb(), Math.min(1, 0.95f * flash * near + 0.08f)));
                }
                if (!own && near < 0.99f && clear(level, cam.pos, at, car)) {
                    SEEN.add(new Seen(at, light.rgb(), flash * (1 - near), flash > 0.5f ? 2 : 1));
                }
            }
            if (searching(car)) {
                search(level, car, q, pos, pivot, cam.pos, own, quads);
            }
        }
        if (quads.count > 0) {
            context.submitNodeCollector().submitCustomGeometry(context.poseStack(), CarLights.glowType(shaders), quads::emit);
        }
    }

    /** The searchlight: its lamp, the beam down to where it lands, and a bright patch there. */
    private static void search(ClientLevel level, CarProp car, Quaternionf q, Vec3 pos, float pivot, Vec3 cam, boolean own,
                               CarLights.Quads quads) {
        Vec3 lamp = place(pos, q, pivot, SEARCH_AT.x, SEARCH_AT.y, SEARCH_AT.z);
        Vector3f d = q.transform(new Vector3f(SEARCH_DIR));
        Vec3 dir = new Vec3(d.x, d.y, d.z);
        BlockHitResult hit = level.clip(new ClipContext(lamp, lamp.add(dir.scale(SEARCH_REACH)), ClipContext.Block.COLLIDER,
                ClipContext.Fluid.ANY, car));
        boolean lands = hit.getType() != HitResult.Type.MISS;
        double length = lands ? hit.getLocation().distanceTo(lamp) : SEARCH_REACH;
        float dark = CarLights.darkness(level, BlockPos.containing(lamp));
        float strength = 0.16f + 0.14f * dark;
        quads.spot(lamp, 0.45, quads.shade(WHITE, 1f));
        quads.ribbon(lamp, lamp.add(dir.scale(length)), 14, s -> 0.12 + s * 0.09, s -> quads.shade(WHITE,
                (float) (strength * Math.min(1, s / 1.5) * Math.exp(-s / 70) * Math.min(1, (length - s) / 1.5 + 0.1))));
        Vec3 patch = null;
        if (lands) {
            Direction face = hit.getDirection();
            Vec3 normal = new Vec3(face.getStepX(), face.getStepY(), face.getStepZ());
            patch = hit.getLocation().add(normal.scale(0.02));
            double r = Math.max(1.5, length * 0.13);
            quads.flat(patch, normal, r, quads.shade(WHITE, 0.3f + 0.25f * dark));
            quads.flat(patch.add(normal.scale(0.01)), normal, r * 0.55, quads.shade(WHITE, 0.25f + 0.2f * dark));
        }
        if (own) {
            return;
        }
        // from far off: the lamp's glare, the beam as a dotted streak, and the patch it lights
        if (clear(level, cam, lamp, car)) {
            SEEN.add(new Seen(lamp, WHITE, 1f, 3));
        }
        int steps = 24;
        for (int i = 2; i < steps; i++) {
            Vec3 p = lamp.add(dir.scale(length * i / steps));
            if (p.distanceTo(cam) > HAZE_FROM && clear(level, cam, p, car)) {
                SEEN.add(new Seen(p, WHITE, 0.35f * (1 - 0.6f * i / steps), 1));
            }
        }
        if (patch != null && patch.distanceTo(cam) > HAZE_FROM && clear(level, cam, patch.add(0, 0.3, 0), car)) {
            SEEN.add(new Seen(patch, WHITE, 0.8f, 3));
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

    // ------------------------------------------------------------------ over the view, through the haze

    private static void view(GuiGraphicsExtractor g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (SEEN.isEmpty()) {
            return;
        }
        Camera camera = mc.gameRenderer.mainCamera();
        Vec3 from = camPos;
        if (from == null) {
            return;
        }
        int w = g.guiWidth(), h = g.guiHeight();
        float scale = (float) (h / 2 / Math.tan(Math.toRadians(camera.getFov()) / 2));
        for (Seen s : SEEN) {
            float[] p = FlightHud.project(camera, s.at().subtract(from), scale, w / 2, h / 2);
            if (p == null || p[0] < -8 || p[1] < -8 || p[0] > w + 8 || p[1] > h + 8) {
                continue;
            }
            int x = Math.round(p[0]), y = Math.round(p[1]);
            float k = Math.clamp(s.strength(), 0, 1);
            int core = s.size();
            dot(g, x, y, core + 3, s.rgb(), 0.12f * k);
            dot(g, x, y, core + 1, s.rgb(), 0.35f * k);
            dot(g, x, y, core - 1, mix(s.rgb()), 0.95f * k);
        }
    }

    /** The camera's place in the last frame drawn, for the view's points. */
    private static Vec3 camPos;

    /** A square of light radius r (0 is a single pixel) round x, y. */
    private static void dot(GuiGraphicsExtractor g, int x, int y, int r, float[] rgb, float alpha) {
        if (alpha < 0.01f || r < 0) {
            return;
        }
        int a = Math.clamp(Math.round(alpha * 255), 0, 255);
        int colour = a << 24 | Math.round(rgb[0] * 255) << 16 | Math.round(rgb[1] * 255) << 8 | Math.round(rgb[2] * 255);
        g.fill(x - r, y - r, x + r + 1, y + r + 1, colour);
    }

    /** A light's hot middle: its colour washed toward white. */
    private static float[] mix(float[] rgb) {
        return new float[]{0.45f + 0.55f * rgb[0], 0.45f + 0.55f * rgb[1], 0.45f + 0.55f * rgb[2]};
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
