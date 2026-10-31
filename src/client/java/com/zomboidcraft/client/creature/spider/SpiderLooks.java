package com.zomboidcraft.client.creature.spider;

import com.mojang.blaze3d.platform.NativeImage;
import com.zomboidcraft.ZomboidCraft;
import com.zomboidcraft.creature.spider.SpiderModel;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

/**
 * The Harvestman's textures on the client, from the model's packed JPEGs: each part's skin, and its two glow maps (red,
 * for when it means harm; blue, for when it doesn't), with how far each vertex glows (so a hidden one can darken it) and
 * which triangles have any glow on them (the only ones drawn again in a glow pass).
 */
final class SpiderLooks {
    final Identifier[] texture, red, blue;
    /** Per part: how far each vertex glows (the brighter of its two maps), and the triangles lit in each. */
    final float[][] glow;
    final int[][] redTris, blueTris;

    private static SpiderLooks looks;
    private static boolean failed;

    /** The model's looks, uploaded the first time; null if they can't be. */
    static SpiderLooks get(SpiderModel model) {
        if (looks == null && !failed) {
            try {
                looks = new SpiderLooks(model);
            } catch (Exception e) {
                ZomboidCraft.LOGGER.error("Couldn't load the Harvestman's textures", e);
                failed = true;
            }
        }
        return looks;
    }

    private SpiderLooks(SpiderModel model) throws IOException {
        int n = model.groups.length;
        texture = new Identifier[n];
        red = new Identifier[n];
        blue = new Identifier[n];
        glow = new float[n][];
        redTris = new int[n][];
        blueTris = new int[n][];
        for (int g = 0; g < n; g++) {
            SpiderModel.Group gr = model.groups[g];
            texture[g] = upload(gr.name, model.jpegs.get(gr.name));
            glow[g] = new float[gr.vertices];
            byte[] r = model.jpegs.get(gr.name + "_glow"), b = model.jpegs.get(gr.name + "_glow_blue");
            float[] redAt = new float[gr.vertices], blueAt = new float[gr.vertices];
            if (r != null) {
                sample(gr, r, redAt);
                red[g] = upload(gr.name + "_glow", r);
            }
            if (b != null) {
                sample(gr, b, blueAt);
                blue[g] = upload(gr.name + "_glow_blue", b);
            }
            for (int v = 0; v < gr.vertices; v++) {
                glow[g][v] = Math.max(redAt[v], blueAt[v]);
            }
            redTris[g] = lit(gr, redAt);
            blueTris[g] = lit(gr, blueAt);
        }
    }

    /** How bright a glow map is under each vertex. */
    private static void sample(SpiderModel.Group g, byte[] jpeg, float[] out) throws IOException {
        BufferedImage img = read(jpeg, g.name);
        int w = img.getWidth(), h = img.getHeight();
        for (int v = 0; v < g.vertices; v++) {
            int x = Math.min(w - 1, (int) (g.uv[v * 2] * w)), y = Math.min(h - 1, (int) (g.uv[v * 2 + 1] * h));
            int rgb = img.getRGB(x, y);
            out[v] = Math.max((rgb >> 16) & 255, Math.max((rgb >> 8) & 255, rgb & 255)) / 255f;
        }
    }

    /** The triangles with some glow on them. */
    private static int[] lit(SpiderModel.Group g, float[] at) {
        int n = 0;
        int[] lit = new int[g.tris.length];
        for (int t = 0; t < g.tris.length; t += 3) {
            if (at[g.tris[t]] + at[g.tris[t + 1]] + at[g.tris[t + 2]] > 0.12f) {
                lit[n++] = g.tris[t];
                lit[n++] = g.tris[t + 1];
                lit[n++] = g.tris[t + 2];
            }
        }
        return java.util.Arrays.copyOf(lit, n);
    }

    private static BufferedImage read(byte[] jpeg, String name) throws IOException {
        if (jpeg == null) {
            throw new IOException("no texture " + name);
        }
        BufferedImage img = ImageIO.read(new ByteArrayInputStream(jpeg));
        if (img == null) {
            throw new IOException("unreadable texture " + name);
        }
        return img;
    }

    private static Identifier upload(String name, byte[] jpeg) throws IOException {
        BufferedImage img = read(jpeg, name);
        NativeImage out = new NativeImage(img.getWidth(), img.getHeight(), false);
        for (int y = 0; y < img.getHeight(); y++) {
            for (int x = 0; x < img.getWidth(); x++) {
                out.setPixel(x, y, 0xFF000000 | img.getRGB(x, y));
            }
        }
        Identifier id = ZomboidCraft.id("dynamic/spider/" + name);
        Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(() -> "ZomboidCraft spider " + name, out));
        return id;
    }
}
