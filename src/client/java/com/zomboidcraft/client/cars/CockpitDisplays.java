package com.zomboidcraft.client.cars;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.zomboidcraft.ZomboidCraft;
import com.zomboidcraft.world.cars.CarModels;
import com.zomboidcraft.world.cars.CarProp;
import com.zomboidcraft.world.cars.Flight;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.levelgen.Heightmap;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * The cockpit displays of the aircraft you're sitting in, live: each screen of its cockpit lights
 * ({@link CockpitGlow}) gets a texture of its own, repainted from the flight several times a second
 * ({@link CockpitPages}), and the head-up display every frame, drawn on the combiner glass so its
 * horizon and flight path marker sit on the world behind them, as a real one does. Other aircraft
 * keep the still pages the glow file carries.
 */
final class CockpitDisplays {
    /** Each model's screens in the order tools/cars/cockpit_glow.py lists them (glow groups 1, 2, ...); null keeps the still page. */
    private static final Map<String, String[]> PAGES = Map.of(
            "jet_corvid", new String[]{"tsd", "adi", "engines", "stores", "cni", "icaw", "hud_jet"},
            "jet_gyrfalcon", new String[]{"hud_flanker"},
            "heli_hornbill", new String[]{"flight", "tsd_heli", "tapes", "eufd", null, "weapons", "tsd_heli", "tapes", "eufd"});

    /**
     * Combiner glass measured from the seat's view (tools/cars/hud_preview.py GLASS) where the glow file's
     * still page sits elsewhere: corners top left, top right, bottom right, bottom left (model space).
     */
    private static final Map<String, float[][]> HUD_GLASS = Map.of(
            "jet_gyrfalcon", new float[][]{{0.068f, 3.685f, 5.83f}, {-0.088f, 3.685f, 5.83f}, {-0.088f, 3.52f, 5.83f}, {0.068f, 3.52f, 5.83f}});

    /**
     * Screens whose glow quad runs on behind something nearer the seat (the F-22's upfront displays tuck in
     * behind the control panel box): the part of the quad's width, left to right, the page is drawn on.
     */
    private static final Map<String, float[]> CROP = Map.of(
            "jet_corvid/5", new float[]{0, 0.66f},
            "jet_corvid/6", new float[]{0.33f, 1});

    /** Pixels per metre of screen, and the most a side gets. The head-up display's glass is finer. */
    private static final float PX_PER_M = 2000, HUD_PX_PER_M = 3600;
    private static final int MOST = 400;

    /** A live screen: its glow group, page, corners in model space (top left, top right, bottom right, bottom left, in drawing order), texture. */
    static final class Screen {
        final int group;
        final String page;
        final boolean hud;
        final float[][] corner;
        final float[][] uv;
        final float[] normal;
        final int w, h;
        final BufferedImage image;
        final DynamicTexture texture;
        final Identifier id;
        /** The corners in the order they're drawn, and their uvs ({@link #uv}). */
        float[][] drawOrder;
        long painted;

        Screen(int group, String page, boolean hud, float[][] corner, float[][] uv, float[] normal, int w, int h, Identifier id) {
            this.group = group;
            this.page = page;
            this.hud = hud;
            this.corner = corner;
            this.uv = uv;
            this.normal = normal;
            this.w = w;
            this.h = h;
            this.id = id;
            this.image = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            this.texture = new DynamicTexture(() -> "ZomboidCraft cockpit display " + id, w, h, true);
            Minecraft.getInstance().getTextureManager().register(id, texture);
        }
    }

    record Set(List<Screen> screens, BitSet groups) {
    }

    private static final Map<String, Set> SETS = new HashMap<>();
    /** Highest load seen on each aircraft since its power came on, for the head-up display's peak g. */
    private static final Map<CarProp, float[]> PEAK = new WeakHashMap<>();

    private CockpitDisplays() {
    }

    /** The live screens of a model's cockpit, or null if it has none. Render thread only. */
    static Set of(String model, CockpitGlow.Glow glow) {
        if (SETS.containsKey(model)) {
            return SETS.get(model);
        }
        Set set = null;
        String[] pages = PAGES.get(model);
        if (pages != null && glow != null) {
            try {
                set = build(model, glow, pages);
            } catch (RuntimeException e) {
                ZomboidCraft.LOGGER.error("Couldn't set up the cockpit displays of {}", model, e);
            }
        }
        SETS.put(model, set);
        return set;
    }

    private static Set build(String model, CockpitGlow.Glow glow, String[] pages) {
        List<Screen> screens = new ArrayList<>();
        BitSet groups = new BitSet();
        float[] p = glow.pos(), uv = glow.uv(), n = glow.normal();
        for (int g = 1; g < glow.kinds().length && g - 1 < pages.length; g++) {
            String page = pages[g - 1];
            if (page == null || !CockpitPages.PAGES.containsKey(page)) {
                continue;
            }
            // the screen's quad: its corners are the triangles' corners at the ends of its uv rectangle
            float u0 = 1, v0 = 1, u1 = 0, v1 = 0;
            int first = -1;
            for (int t = 0; t < glow.group().length; t++) {
                if (glow.group()[t] != g) {
                    continue;
                }
                first = first < 0 ? t : first;
                for (int k = 0; k < 3; k++) {
                    int i = t * 3 + k;
                    u0 = Math.min(u0, uv[i * 2]);
                    u1 = Math.max(u1, uv[i * 2]);
                    v0 = Math.min(v0, uv[i * 2 + 1]);
                    v1 = Math.max(v1, uv[i * 2 + 1]);
                }
            }
            if (first < 0) {
                continue;
            }
            float[][] corner = new float[4][];
            for (int t = 0; t < glow.group().length; t++) {
                if (glow.group()[t] != g) {
                    continue;
                }
                for (int k = 0; k < 3; k++) {
                    int i = t * 3 + k;
                    boolean left = uv[i * 2] - u0 < (u1 - u0) / 2, top = uv[i * 2 + 1] - v0 < (v1 - v0) / 2;
                    int c = top ? (left ? 0 : 1) : (left ? 3 : 2);
                    corner[c] = new float[]{p[i * 3], p[i * 3 + 1], p[i * 3 + 2]};
                }
            }
            if (corner[0] == null || corner[1] == null || corner[2] == null || corner[3] == null) {
                continue;
            }
            float[] crop = CROP.get(model + "/" + g);
            if (crop != null) {
                corner = new float[][]{lerp(corner[0], corner[1], crop[0]), lerp(corner[0], corner[1], crop[1]),
                        lerp(corner[3], corner[2], crop[1]), lerp(corner[3], corner[2], crop[0])};
            }
            float[] normal = {n[first * 3], n[first * 3 + 1], n[first * 3 + 2]};
            boolean hud = glow.kinds()[g] == CockpitGlow.HUD;
            if (hud && HUD_GLASS.containsKey(model)) {
                corner = HUD_GLASS.get(model);
                float[] a = sub(corner[1], corner[0]), b = sub(corner[3], corner[0]), eye = sub(eye(model), corner[0]);
                float[] f = {a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
                float len = (float) Math.sqrt(f[0] * f[0] + f[1] * f[1] + f[2] * f[2]);
                float sign = f[0] * eye[0] + f[1] * eye[1] + f[2] * eye[2] < 0 ? -1 : 1;
                normal = new float[]{f[0] / len * sign, f[1] / len * sign, f[2] / len * sign};
            }
            float width = dist(corner[0], corner[1]), height = dist(corner[0], corner[3]);
            float k = hud ? HUD_PX_PER_M : PX_PER_M;
            int most = hud ? 640 : MOST;
            float scale = Math.min(k, most / Math.max(width, height));
            int w = Math.max(32, Math.round(width * scale)), h = Math.max(24, Math.round(height * scale));
            // drawn as a quad facing the way the glow file's triangles do (toward the seat)
            float[][] order = {corner[0], corner[1], corner[2], corner[3]};
            float[][] tex = {{0, 0}, {1, 0}, {1, 1}, {0, 1}};
            float[] a = sub(order[1], order[0]), b = sub(order[2], order[0]);
            float[] face = {a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
            if (face[0] * normal[0] + face[1] * normal[1] + face[2] * normal[2] < 0) {
                order = new float[][]{corner[0], corner[3], corner[2], corner[1]};
                tex = new float[][]{{0, 0}, {0, 1}, {1, 1}, {1, 0}};
            }
            Screen s = new Screen(g, page, hud, corner, tex, normal, w, h, ZomboidCraft.id("dynamic/cockpit/" + model + "/" + g));
            s.drawOrder = order;
            screens.add(s);
            groups.set(g);
        }
        return screens.isEmpty() ? null : new Set(screens, groups);
    }

    private static float dist(float[] a, float[] b) {
        float dx = a[0] - b[0], dy = a[1] - b[1], dz = a[2] - b[2];
        return (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private static float[] lerp(float[] a, float[] b, float t) {
        return new float[]{a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, a[2] + (b[2] - a[2]) * t};
    }

    private static float[] sub(float[] a, float[] b) {
        return new float[]{a[0] - b[0], a[1] - b[1], a[2] - b[2]};
    }

    /**
     * Repaints whichever screens are due: the head-up display each frame (up to 60 a second), the rest
     * eight times a second. attitude is the aircraft's turn as drawn this frame; eye the pilot's eye in model space.
     */
    static void paint(CarProp car, Set set, Quaternionf attitude, float[] eye) {
        long now = System.currentTimeMillis();
        CockpitPages.Data data = null;
        for (Screen s : set.screens()) {
            if (now - s.painted < (s.hud ? 16 : 125)) {
                continue;
            }
            if (data == null) {
                data = data(car, attitude, now, true);
            }
            s.painted = now;
            paint(s, data, eye);
        }
    }

    private static void paint(Screen s, CockpitPages.Data data, float[] eye) {
        Graphics2D g = s.image.createGraphics();
        try {
            g.setComposite(java.awt.AlphaComposite.Clear);
            g.fillRect(0, 0, s.w, s.h);
            g.setComposite(java.awt.AlphaComposite.SrcOver);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            CockpitPages.PAGES.get(s.page).paint(g, s.w, s.h, data, s.hud ? glass(s, eye) : null);
        } catch (RuntimeException e) {
            ZomboidCraft.LOGGER.error("Cockpit page {} failed", s.page, e);
        } finally {
            g.dispose();
        }
        NativeImage pixels = s.texture.getPixels();
        if (pixels == null) {
            return;
        }
        int[] argb = s.image.getRGB(0, 0, s.w, s.h, null, 0, s.w);
        for (int y = 0; y < s.h; y++) {
            for (int x = 0; x < s.w; x++) {
                int c = argb[y * s.w + x];
                int a = c >>> 24;
                // drawn as added light: premultiplied, its strength the brightest channel
                int r = (c >> 16 & 255) * a / 255, gr = (c >> 8 & 255) * a / 255, b = (c & 255) * a / 255;
                int k = Math.max(r, Math.max(gr, b));
                pixels.setPixel(x, y, k << 24 | r << 16 | gr << 8 | b);
            }
        }
        s.texture.upload();
    }

    /** Where a direction from the eye meets the glass, in the screen's pixels. */
    private static CockpitPages.Glass glass(Screen s, float[] eye) {
        float[] tl = s.corner[0], u = sub(s.corner[1], tl), v = sub(s.corner[3], tl);
        float uu = u[0] * u[0] + u[1] * u[1] + u[2] * u[2], vv = v[0] * v[0] + v[1] * v[1] + v[2] * v[2];
        float[] n = {u[1] * v[2] - u[2] * v[1], u[2] * v[0] - u[0] * v[2], u[0] * v[1] - u[1] * v[0]};
        float[] e = sub(eye, tl);
        float en = e[0] * n[0] + e[1] * n[1] + e[2] * n[2];
        return d -> {
            float dn = d.x * n[0] + d.y * n[1] + d.z * n[2];
            float t = -en / dn;
            if (Math.abs(dn) < 1e-6f || t <= 0) {
                return null;
            }
            float px = e[0] + d.x * t, py = e[1] + d.y * t, pz = e[2] + d.z * t;
            float a = (px * u[0] + py * u[1] + pz * u[2]) / uu, b = (px * v[0] + py * v[1] + pz * v[2]) / vv;
            return new float[]{a * s.w, b * s.h};
        };
    }

    /** Everything the pages show, from the aircraft as it is this frame; tracks: look for other aircraft about too. */
    static CockpitPages.Data data(CarProp car, Quaternionf q, long now, boolean withTracks) {
        Flight f = car.flight();
        var level = car.level();
        Vector3f vel = new Vector3f((float) f.vel.x, (float) f.vel.y, (float) f.vel.z);
        float altitude = (float) (car.getY() - 63);
        int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING, car.getBlockX(), car.getBlockZ());
        float radar = (float) Math.max(0, car.getY() - ground);
        Vector3f nose = new Quaternionf(q).transform(new Vector3f(0, 0, 1)), left = new Quaternionf(q).transform(new Vector3f(1, 0, 0)),
                up = new Quaternionf(q).transform(new Vector3f(0, 1, 0));
        float heading = (float) Math.toDegrees(Math.atan2(nose.x, -nose.z));
        heading = (heading % 360 + 360) % 360;
        float pitch = (float) Math.toDegrees(Math.asin(Math.max(-1, Math.min(1, nose.y))));
        float bank = (float) Math.toDegrees(Math.atan2(left.y, up.y));
        float[] peak = PEAK.computeIfAbsent(car, c -> new float[]{1});
        if (f.grounded) {
            peak[0] = 1;
        }
        peak[0] = Math.max(peak[0], f.load);
        List<float[]> tracks = new ArrayList<>();
        for (Entity e : withTracks ? Minecraft.getInstance().level.entitiesForRendering() : List.<Entity>of()) {
            if (e != car && e instanceof CarProp other && other.aircraft() && e.distanceToSqr(car) < 6000.0 * 6000.0) {
                double dx = e.getX() - car.getX(), dz = e.getZ() - car.getZ();
                float bearing = (float) Math.toDegrees(Math.atan2(dx, -dz));
                float range = (float) Math.hypot(dx, dz);
                Vector3f on = other.flight().q.transform(new Vector3f(0, 0, 1));
                float course = (float) Math.toDegrees(Math.atan2(on.x, -on.z));
                tracks.add(new float[]{bearing, range, (float) (e.getY() - 63) * 3.28f, other.isVehicle() ? 1 : 0, course});
            }
        }
        float hours = Math.floorMod(Minecraft.getInstance().level.getDefaultClockTime(), 24000L) / 1000f + 6;
        boolean brake = car.braking > 0 || f.airbrake;
        return new CockpitPages.Data(car, f.kind, new Quaternionf(q), vel, vel.length(), altitude, radar, vel.y, heading, pitch, bank,
                f.load, peak[0], (float) Math.toDegrees(f.aoa), f.throttle, f.power, f.rpm, f.afterburner(), car.engineOn(), f.grounded,
                f.stalled, brake, car.fuel(), car.fuel() * car.fuelCapacity() * 0.8f, car.battery(), car.condition(), hours % 24, tracks, now);
    }

    static RenderType type(Screen s, boolean shaders) {
        return shaders ? RenderTypes.beaconBeam(s.id, true) : RenderTypes.eyes(s.id);
    }

    /** A live screen's quad, a hair in front of its face, in the strength its glow group has now. */
    static void draw(Screen s, int colour, PoseStack.Pose pose, VertexConsumer out) {
        if (colour == 0) {
            return;
        }
        float lift = 0.0035f;
        float nx = s.normal[0], ny = s.normal[1], nz = s.normal[2];
        for (int k = 0; k < 4; k++) {
            float[] c = s.drawOrder[k];
            out.addVertex(pose, c[0] + nx * lift, c[1] + ny * lift, c[2] + nz * lift)
                    .setColor(colour)
                    .setUv(s.uv[k][0], s.uv[k][1])
                    .setOverlay(OverlayTexture.NO_OVERLAY)
                    .setLight(CarLights.FULL_BRIGHT)
                    .setNormal(pose, nx, ny, nz);
        }
    }

    /** The pilot's eye in model space: the first seat's, from models.json. */
    static float[] eye(String model) {
        CarModels.Model m = CarModels.get(model);
        return m == null || m.seats == null || m.seats.length == 0 ? new float[]{0, 0, 0} : m.seats[0];
    }
}
