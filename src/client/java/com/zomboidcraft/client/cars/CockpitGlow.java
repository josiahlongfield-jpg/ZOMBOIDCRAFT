package com.zomboidcraft.client.cars;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.zomboidcraft.ZomboidCraft;
import com.zomboidcraft.world.cars.CarProp;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.zip.InflaterInputStream;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;

/**
 * The lights in an aircraft's cockpit (assets/zomboidcraft/cars/ID.zglow, written by
 * tools/cars/cockpit_glow.py): the keys, legends, dial markings and lamps, as copies of the cockpit's
 * own triangles drawn again glowing just in front of it, and a page on each display. They come on
 * while someone sits in the aircraft: the keys fade up, then the screens flick on one after another,
 * the head-up display last. Wrecks and burnt shells stay dark.
 */
public final class CockpitGlow {
    /** Group kinds: the keys and lamps, a display, a head-up display. */
    static final byte KEYS = 0, SCREEN = 1, HUD = 2;

    /**
     * kinds[g] is group g's kind; group[t] is triangle t's group; pos and uv three corners a triangle,
     * normal one per triangle. dash is the middle of the lit panel, for the light it throws.
     */
    record Glow(Identifier texture, float[] dash, byte[] kinds, byte[] group, float[] pos, float[] uv, float[] normal) {
    }

    private static final Map<String, Glow> CACHE = new HashMap<>();
    /** When each aircraft's power came on (millis), while it's on. */
    private static final Map<CarProp, Long> POWER = new WeakHashMap<>();

    private CockpitGlow() {
    }

    /** A model's cockpit lights, or null if it has none. Render thread only. */
    static Glow get(String model) {
        if (CACHE.containsKey(model)) {
            return CACHE.get(model);
        }
        Glow glow = null;
        Identifier file = ZomboidCraft.id("cars/" + model + ".zglow");
        var resource = Minecraft.getInstance().getResourceManager().getResource(file);
        if (resource.isPresent()) {
            try (InputStream in = resource.get().open()) {
                glow = read(model, in.readAllBytes());
            } catch (Exception e) {
                ZomboidCraft.LOGGER.error("Couldn't load the cockpit lights of {}", model, e);
            }
        }
        CACHE.put(model, glow);
        return glow;
    }

    /**
     * Seconds since this aircraft's avionics came on (someone got in with the battery good), or -1 while
     * they're off. Only aircraft with cockpit lights, and only whole ones.
     */
    static float powered(CarProp car, String model, String variant) {
        // a flat battery leaves the cockpit dark
        boolean on = car.isVehicle() && car.battery() && ("usable".equals(variant) || "abandoned".equals(variant)) && get(model) != null;
        if (!on) {
            POWER.remove(car);
            return -1;
        }
        long now = System.currentTimeMillis();
        return (now - POWER.computeIfAbsent(car, c -> now)) / 1000f;
    }

    /** Added light normally; with a shader pack, drawn the way beacon beams are, which the packs make glow. */
    static RenderType type(Glow glow, boolean shaders) {
        return shaders ? RenderTypes.beaconBeam(glow.texture(), true) : RenderTypes.eyes(glow.texture());
    }

    /** The light the lit panel throws on the cockpit round it, for CarRenderer's draw: {x, y, z, strength}. */
    static float[] cabin(Glow glow, float age, float dark) {
        float k = Math.min(1, age / 0.8f) * dark * 0.6f;
        return k <= 0.02f ? null : new float[]{glow.dash()[0], glow.dash()[1], glow.dash()[2], k};
    }

    /**
     * Each group's colour now (0 for dark): age is seconds since the power came on, dark how dark it
     * is round the aircraft (0 to 1). The keys fade up, the screens flick on one after another, the
     * head-up display last.
     */
    static int[] colours(Glow glow, float age, float dark, boolean shaders) {
        int groups = glow.kinds().length;
        int[] colour = new int[groups];
        int screens = 0;
        for (int g = 0; g < groups; g++) {
            float strength = switch (glow.kinds()[g]) {
                case KEYS -> Math.min(1, age / 0.8f) * (0.12f + 0.88f * dark);
                case HUD -> booted(age - 0.6f - 0.3f * (groups - 1), g) * (0.75f + 0.25f * dark);
                default -> booted(age - 0.5f - 0.3f * screens++, g) * (0.8f + 0.2f * dark);
            };
            colour[g] = strength <= 0.01f ? 0 : CarLights.shade(WHITE, strength, shaders);
        }
        return colour;
    }

    /**
     * Every lit triangle, each as a quad with its last corner doubled, a hair in front of the face it
     * lights, in its group's colour; groups in live (drawn by {@link CockpitDisplays}) and triangles in
     * covered (under a {@link CockpitPanel}) are left out.
     */
    static void draw(Glow glow, int[] colour, java.util.BitSet live, java.util.BitSet covered, PoseStack.Pose pose, VertexConsumer out) {
        float[] p = glow.pos(), uv = glow.uv(), n = glow.normal();
        byte[] group = glow.group();
        for (int t = 0; t < group.length; t++) {
            int c = colour[group[t]];
            if (c == 0 || live != null && live.get(group[t]) || covered != null && covered.get(t)) {
                continue;
            }
            float lift = glow.kinds()[group[t]] == KEYS ? 0.002f : 0.003f;
            float nx = n[t * 3], ny = n[t * 3 + 1], nz = n[t * 3 + 2];
            for (int k = 0; k < 4; k++) {
                int i = t * 3 + Math.min(k, 2);
                out.addVertex(pose, p[i * 3] + nx * lift, p[i * 3 + 1] + ny * lift, p[i * 3 + 2] + nz * lift)
                        .setColor(c)
                        .setUv(uv[i * 2], uv[i * 2 + 1])
                        .setOverlay(OverlayTexture.NO_OVERLAY)
                        .setLight(CarLights.FULL_BRIGHT)
                        .setNormal(pose, nx, ny, nz);
            }
        }
    }

    private static final float[] WHITE = {1f, 1f, 1f};

    /** A screen t seconds after its turn to come on: dark, a few flickers as the backlight strikes, then on. */
    private static float booted(float t, int seed) {
        if (t <= 0) {
            return 0;
        }
        if (t >= 0.35f) {
            return 1;
        }
        int step = (int) (t * 20);
        int h = (seed * 73856093) ^ (step * 19349663);
        return (h >>> 5 & 3) == 0 ? 0.15f : step % 2 == 0 ? 0.9f : 0.4f;
    }

    private static Glow read(String model, byte[] data) throws IOException {
        CarAssets.unscramble(data, model);
        try (DataInputStream in = new DataInputStream(new InflaterInputStream(new ByteArrayInputStream(data)))) {
            byte[] magic = in.readNBytes(4);
            if (magic[0] != 'Z' || magic[1] != 'G' || magic[2] != 'L' || magic[3] != 'W' || in.readInt() != 1) {
                throw new IOException("not a cockpit lights file");
            }
            byte[] png = in.readNBytes(in.readInt());
            float[] dash = {in.readFloat(), in.readFloat(), in.readFloat()};
            byte[] kinds = new byte[in.readInt()];
            for (int g = 0; g < kinds.length; g++) {
                kinds[g] = in.readByte();
            }
            int count = in.readInt();
            byte[] group = new byte[count];
            float[] pos = new float[count * 9], uv = new float[count * 6], normal = new float[count * 3];
            for (int t = 0; t < count; t++) {
                group[t] = in.readByte();
                if (group[t] < 0 || group[t] >= kinds.length) {
                    throw new IOException("bad group");
                }
                for (int k = 0; k < 3; k++) {
                    int i = t * 3 + k;
                    pos[i * 3] = in.readFloat();
                    pos[i * 3 + 1] = in.readFloat();
                    pos[i * 3 + 2] = in.readFloat();
                    uv[i * 2] = in.readFloat();
                    uv[i * 2 + 1] = in.readFloat();
                }
                int a = t * 9;
                float ux = pos[a + 3] - pos[a], uy = pos[a + 4] - pos[a + 1], uz = pos[a + 5] - pos[a + 2];
                float vx = pos[a + 6] - pos[a], vy = pos[a + 7] - pos[a + 1], vz = pos[a + 8] - pos[a + 2];
                float nx = uy * vz - uz * vy, ny = uz * vx - ux * vz, nz = ux * vy - uy * vx;
                float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
                if (len < 1e-9f) {
                    len = 1;
                    ny = 1;
                }
                normal[t * 3] = nx / len;
                normal[t * 3 + 1] = ny / len;
                normal[t * 3 + 2] = nz / len;
            }
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(png));
            if (img == null) {
                throw new IOException("unreadable texture");
            }
            NativeImage image = new NativeImage(img.getWidth(), img.getHeight(), false);
            for (int y = 0; y < img.getHeight(); y++) {
                for (int x = 0; x < img.getWidth(); x++) {
                    image.setPixel(x, y, img.getRGB(x, y));
                }
            }
            Identifier id = ZomboidCraft.id("dynamic/cockpit/" + model);
            Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(() -> "ZomboidCraft cockpit " + model, image));
            return new Glow(id, dash, kinds, group, pos, uv, normal);
        }
    }
}
