package com.zomboidcraft.client.hud;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;
import com.zomboidcraft.ZomboidCraft;
import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import javax.imageio.ImageIO;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3x2fStack;

/**
 * A HUD drawn from a kit of pictures kept on this computer in {@code config/zomboidcraft/hud/<name>/} (the Reach and
 * Tarkov HUDs, Josiah 2026-11-08: "they dont leave my files"; never in the jar). {@code kit.json} places every piece
 * on a 1280x720 layout, which is fitted to the window the way the games do it: scaled to fit and centred. The pictures
 * are drawn at twice their layout size; each is resized once to the exact number of screen pixels it covers (smooth
 * downscaling, so thin lines stay clean) and drawn 1:1, and made again only when the window changes size.
 */
final class HudKit {
    static final Path ROOT = FabricLoader.getInstance().getConfigDir().resolve("zomboidcraft/hud");
    private static final Map<String, HudKit> KITS = new HashMap<>();
    private static final Map<String, Long> MISSING = new HashMap<>();

    final String name;
    final JsonObject json;
    private final Path dir;
    private final Map<String, BufferedImage> sources = new HashMap<>();
    private final Map<String, Tex> textures = new HashMap<>();
    private final Map<String, TextTex> texts = new HashMap<>();

    /** Window pixels per layout unit, and where the layout's corner sits, for this frame. */
    float s, ox, oy;
    /** The layout's size (kit.json "layout"; the HUDs use 1280x720, the character screens 1920x1080). */
    final float lw, lh;
    private int madeW = -1, madeH = -1;

    private record Tex(Identifier id, int w, int h) {}

    private record TextTex(Identifier id, String text, int w, int h, float x, float y) {}

    private HudKit(String name, Path dir, JsonObject json) {
        this.name = name;
        this.dir = dir;
        this.json = json;
        JsonArray layout = arr(json, "layout");
        this.lw = layout != null && layout.size() == 2 ? layout.get(0).getAsFloat() : 1280;
        this.lh = layout != null && layout.size() == 2 ? layout.get(1).getAsFloat() : 720;
    }

    /** The kit of that name, or null when its files aren't on this computer (looked for again every ten seconds). */
    static @Nullable HudKit get(String name) {
        HudKit kit = KITS.get(name);
        if (kit != null) return kit;
        Long tried = MISSING.get(name);
        long now = System.currentTimeMillis();
        if (tried != null && now - tried < 10_000) return null;
        Path dir = ROOT.resolve(name);
        Path file = dir.resolve("kit.json");
        unpack(name, dir, file);
        if (!Files.isRegularFile(file)) {
            MISSING.put(name, now);
            return null;
        }
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            kit = new HudKit(name, dir, JsonParser.parseReader(reader).getAsJsonObject());
            KITS.put(name, kit);
            MISSING.remove(name);
            return kit;
        } catch (Exception e) {
            ZomboidCraft.LOGGER.warn("Couldn't read the {} HUD kit at {}", name, file, e);
            MISSING.put(name, now);
            return null;
        }
    }

    /**
     * A kit can arrive as {@code hud/<name>.zip}, or split as {@code <name>.zip.001}, {@code .002}... (the pieces of one
     * zip, in order): it's unpacked into {@code hud/<name>/} when the folder has no kit yet or the zip is newer.
     */
    private static void unpack(String name, Path dir, Path kitJson) {
        java.util.List<Path> parts = new java.util.ArrayList<>();
        Path whole = ROOT.resolve(name + ".zip");
        if (Files.isRegularFile(whole)) parts.add(whole);
        else {
            for (int i = 1; i < 1000; i++) {
                Path p = ROOT.resolve(String.format(Locale.ROOT, "%s.zip.%03d", name, i));
                if (!Files.isRegularFile(p)) break;
                parts.add(p);
            }
        }
        if (parts.isEmpty()) return;
        try {
            long newest = 0;
            for (Path p : parts) newest = Math.max(newest, Files.getLastModifiedTime(p).toMillis());
            if (Files.isRegularFile(kitJson) && Files.getLastModifiedTime(kitJson).toMillis() >= newest) return;
            java.util.Vector<java.io.InputStream> streams = new java.util.Vector<>();
            for (Path p : parts) streams.add(Files.newInputStream(p));
            int count = 0;
            try (var zip = new java.util.zip.ZipInputStream(new java.io.BufferedInputStream(new java.io.SequenceInputStream(streams.elements())))) {
                java.util.zip.ZipEntry e;
                while ((e = zip.getNextEntry()) != null) {
                    Path out = dir.resolve(e.getName()).normalize();
                    if (!out.startsWith(dir)) continue;          // nothing outside the kit's own folder
                    if (e.isDirectory()) {
                        Files.createDirectories(out);
                        continue;
                    }
                    Files.createDirectories(out.getParent());
                    Files.copy(zip, out, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    count++;
                }
            }
            if (Files.isRegularFile(kitJson)) Files.setLastModifiedTime(kitJson, java.nio.file.attribute.FileTime.fromMillis(System.currentTimeMillis()));
            ZomboidCraft.LOGGER.info("Unpacked the {} kit ({} files) from {}", name, count, parts.get(0).getFileName());
        } catch (Exception ex) {
            ZomboidCraft.LOGGER.warn("Couldn't unpack the {} kit", name, ex);
        }
    }

    /** Whether the kit's files are on this computer. */
    static boolean present(String name) {
        return get(name) != null;
    }

    /** Forget every kit, so the next frame reads the files again (after copying in a new kit). */
    static void reload() {
        KITS.clear();
        MISSING.clear();
    }

    /** Fits the layout to the window for this frame; a new window size remakes the pictures as they're next drawn. */
    void begin() {
        releaseRetired();
        var window = Minecraft.getInstance().getWindow();
        int w = window.getWidth(), h = window.getHeight();
        s = Math.min(w / lw, h / lh);
        ox = (w - lw * s) / 2;
        oy = (h - lh * s) / 2;
        if (w != madeW || h != madeH) {
            madeW = w;
            madeH = h;
            textures.replaceAll((k, t) -> new Tex(t.id, -1, -1));     // stale: remade (same id) when next drawn
            texts.replaceAll((k, t) -> new TextTex(t.id, null, 0, 0, 0, 0));
        }
    }

    float px(double x) {
        return ox + (float) x * s;
    }

    float py(double y) {
        return oy + (float) y * s;
    }

    // ------------------------------------------------------------------ drawing (window pixels)

    /** Starts drawing in whole window pixels; pair with {@link #end}. */
    static void pixels(GuiGraphicsExtractor g) {
        float gui = (float) Minecraft.getInstance().getWindow().getGuiScale();
        Matrix3x2fStack pose = g.pose();
        pose.pushMatrix();
        pose.scale(1 / gui, 1 / gui);
    }

    static void end(GuiGraphicsExtractor g) {
        g.pose().popMatrix();
    }

    /** A kit entry ({file, x, y, w, h} in layout units) where the kit puts it. */
    void entry(GuiGraphicsExtractor g, @Nullable JsonObject e, int colour) {
        if (e == null) return;
        image(g, e.get("file").getAsString(), e.get("x").getAsDouble(), e.get("y").getAsDouble(),
                e.get("w").getAsDouble(), e.get("h").getAsDouble(), colour);
    }

    /** A picture over a box of the layout (layout units). */
    void image(GuiGraphicsExtractor g, String file, double x, double y, double w, double h, int colour) {
        image(g, file, file, x, y, w, h, colour);
    }

    /** As {@link #image}, kept under its own slot, so one picture drawn at two sizes isn't remade every frame. */
    void image(GuiGraphicsExtractor g, String slot, String file, double x, double y, double w, double h, int colour) {
        if ((colour >>> 24) < 3) return;
        int x0 = Math.round(px(x)), y0 = Math.round(py(y));
        int tw = Math.max(1, Math.round((float) w * s)), th = Math.max(1, Math.round((float) h * s));
        Tex t = scaled(slot, file, tw, th);
        if (t == null) return;
        g.blit(RenderPipelines.GUI_TEXTURED, t.id, x0, y0, 0, 0, t.w, t.h, t.w, t.h, colour);
    }

    /** A picture centred on a point of the layout, at a size in layout units. */
    void centred(GuiGraphicsExtractor g, String file, double cx, double cy, double w, double h, int colour) {
        image(g, file, cx - w / 2, cy - h / 2, w, h, colour);
    }

    /**
     * A strip of a picture resized so {@code srcSpan} of its source pixels cover {@code dstSpan} window pixels, then a
     * run of it starting at source column {@code u} drawn {@code w} pixels wide at (x, y): the compass tape.
     */
    void strip(GuiGraphicsExtractor g, String file, int texW, int texH, int x, int y, float u, int w, int colour) {
        Tex t = scaled(file, file, texW, texH);
        if (t == null || w <= 0 || (colour >>> 24) < 3) return;
        g.blit(RenderPipelines.GUI_TEXTURED, t.id, x, y, u, 0, w, t.h, t.w, t.h, colour);
    }

    /**
     * A picture made once at texW x texH window pixels (under its own slot) and drawn stretched over w x h window
     * pixels at (x, y): a figure that grows as its card does, remade only when the window size changes.
     */
    void picture(GuiGraphicsExtractor g, String slot, String file, int x, int y, int w, int h, int texW, int texH, int colour) {
        if (w <= 0 || h <= 0 || (colour >>> 24) < 3) return;
        Tex t = scaled(slot, file, Math.max(1, texW), Math.max(1, texH));
        if (t == null) return;
        g.blit(RenderPipelines.GUI_TEXTURED, t.id, x, y, 0, 0, w, h, w, h, colour);
    }

    /**
     * A nine-slice frame (Reach's card): {@code file} is a template whose corners are {@code ix} x {@code iy} of its own
     * pixels, one template pixel to a layout unit. Corners are drawn 1:1 and the edges and middle stretched, over the
     * window-pixel box (x, y, w, h).
     */
    void nine(GuiGraphicsExtractor g, String file, int x, int y, int w, int h, int ix, int iy, int colour) {
        if (w <= 0 || h <= 0 || (colour >>> 24) < 3) return;
        BufferedImage src = source(file);
        if (src == null) return;
        int tw = Math.max(3, Math.round(src.getWidth() * s)), th = Math.max(3, Math.round(src.getHeight() * s));
        Tex t = scaled(file, file, tw, th);
        if (t == null) return;
        int cx = Math.min(Math.round(ix * s), Math.min(w / 2, tw / 2 - 1)), cy = Math.min(Math.round(iy * s), Math.min(h / 2, th / 2 - 1));
        int[] sx = {0, cx, tw - cx, tw}, sy = {0, cy, th - cy, th};
        int[] dx = {x, x + cx, x + w - cx, x + w}, dy = {y, y + cy, y + h - cy, y + h};
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                int dw = dx[i + 1] - dx[i], dh = dy[j + 1] - dy[j], sw = sx[i + 1] - sx[i], sh = sy[j + 1] - sy[j];
                if (dw <= 0 || dh <= 0 || sw <= 0 || sh <= 0) continue;
                // stretch the source cell over the destination: texture size scaled so the cell spans dw x dh
                int texW = Math.max(1, Math.round(tw * (float) dw / sw)), texH = Math.max(1, Math.round(th * (float) dh / sh));
                float u = sx[i] * (float) dw / sw, v = sy[j] * (float) dh / sh;
                g.blit(RenderPipelines.GUI_TEXTURED, t.id, dx[i], dy[j], u, v, dw, dh, texW, texH, colour);
            }
        }
    }

    /**
     * A picture filling a box of the layout (layout units) the way a framed portrait does: scaled to cover it, the
     * overflow cut off evenly on both sides.
     */
    void cover(GuiGraphicsExtractor g, String slot, String file, double x, double y, double w, double h, int colour) {
        if ((colour >>> 24) < 3) return;
        BufferedImage src = source(file);
        if (src == null) return;
        int bw = Math.max(1, Math.round((float) w * s)), bh = Math.max(1, Math.round((float) h * s));
        float k = Math.max(bw / (float) src.getWidth(), bh / (float) src.getHeight());
        int tw = Math.max(bw, Math.round(src.getWidth() * k)), th = Math.max(bh, Math.round(src.getHeight() * k));
        Tex t = scaled(slot, file, tw, th);
        if (t == null) return;
        g.blit(RenderPipelines.GUI_TEXTURED, t.id, Math.round(px(x)), Math.round(py(y)), (tw - bw) / 2f, (th - bh) / 2f, bw, bh, tw, th, colour);
    }

    /** A plain rectangle in layout units. */
    void fill(GuiGraphicsExtractor g, double x0, double y0, double x1, double y1, int colour) {
        g.fill(Math.round(px(x0)), Math.round(py(y0)), Math.round(px(x1)), Math.round(py(y1)), colour);
    }

    /**
     * Text from one of the kit's baked fonts. {@code metric} false: (x, y) is placed at (alignX, alignY) of the ink's
     * box (Reach's text, as kitview.py does it); true: at the font's advance and ascender to descender (Tarkov's, as
     * PIL anchors it: 0 left/top, 0.5 middle, 1 right/bottom). {@code slot} names the place it's drawn, which keeps its
     * own picture until the text changes. Returns the width drawn, layout units.
     */
    float text(GuiGraphicsExtractor g, String slot, String fontKey, String text, double x, double y, double alignX, double alignY,
               boolean metric, int colour) {
        return text(g, slot, fontKey, text, x, y, alignX, alignY, metric, colour, 0);
    }

    /** As above, with {@code tracking} layout units added between letters (Reach's spaced capitals). */
    float text(GuiGraphicsExtractor g, String slot, String fontKey, String text, double x, double y, double alignX, double alignY,
               boolean metric, int colour, float tracking) {
        if (text == null || text.isEmpty() || (colour >>> 24) < 3) return 0;
        JsonObject font = obj(obj(json, "fonts"), fontKey);
        if (font == null) return 0;
        JsonObject glyphs = font.getAsJsonObject("glyphs");
        TextTex t = texts.get(slot);
        String keyText = tracking == 0 ? text : text + "\u0000" + tracking;
        if (t == null || !keyText.equals(t.text)) {
            t = makeText(slot, t == null ? null : t.id, font, glyphs, text, tracking);
            if (t == null) return 0;
            texts.put(slot, t);
        }
        // the pen's start (on the baseline), from the alignment
        float pen = 0, l = Float.MAX_VALUE, r = -Float.MAX_VALUE, top = Float.MAX_VALUE, bottom = -Float.MAX_VALUE;
        for (int i = 0; i < text.length(); i++) {
            JsonObject gl = obj(glyphs, String.valueOf(text.charAt(i)));
            if (gl == null) continue;
            if (i > 0) pen += tracking;
            l = Math.min(l, pen + f(gl, "l"));
            r = Math.max(r, pen + f(gl, "r"));
            top = Math.min(top, f(gl, "t"));
            bottom = Math.max(bottom, f(gl, "b"));
            pen += f(gl, "adv");
        }
        if (l == Float.MAX_VALUE) return 0;
        double x0, y0;
        if (metric) {
            float asc = f(font, "ascent"), desc = f(font, "descent");
            x0 = x - alignX * pen;
            y0 = y + asc - alignY * (asc + desc);
        } else {
            x0 = x - alignX * (r - l) - l;
            y0 = y - alignY * (bottom - top) - top;
        }
        int x1 = Math.round(px(x0 + t.x)), y1 = Math.round(py(y0 + t.y));
        g.blit(RenderPipelines.GUI_TEXTURED, t.id, x1, y1, 0, 0, t.w, t.h, t.w, t.h, colour);
        return metric ? pen : r - l;
    }

    /** Lays the glyphs out at the font's own (2x) size, then resizes the line to window pixels. */
    private @Nullable TextTex makeText(String slot, @Nullable Identifier id, JsonObject font, JsonObject glyphs, String text, float tracking) {
        BufferedImage atlas = source(font.get("file").getAsString());
        if (atlas == null) return null;
        float pen = 0, bx0 = Float.MAX_VALUE, by0 = Float.MAX_VALUE, bx1 = -Float.MAX_VALUE, by1 = -Float.MAX_VALUE;
        float[] at = new float[text.length()];
        for (int i = 0; i < text.length(); i++) {
            JsonObject gl = obj(glyphs, String.valueOf(text.charAt(i)));
            at[i] = Float.NaN;
            if (gl == null) continue;
            if (i > 0) pen += tracking;
            at[i] = pen;
            float gx = pen + f(gl, "ox"), gy = f(gl, "oy");
            bx0 = Math.min(bx0, gx);
            by0 = Math.min(by0, gy);
            bx1 = Math.max(bx1, gx + f(gl, "w") / 2);
            by1 = Math.max(by1, gy + f(gl, "h") / 2);
            pen += f(gl, "adv");
        }
        if (bx0 == Float.MAX_VALUE) return null;
        int iw = (int) Math.ceil((bx1 - bx0) * 2) + 1, ih = (int) Math.ceil((by1 - by0) * 2) + 1;
        BufferedImage line = new BufferedImage(iw, ih, BufferedImage.TYPE_INT_ARGB_PRE);
        Graphics2D g2 = line.createGraphics();
        g2.setComposite(AlphaComposite.SrcOver);
        for (int i = 0; i < text.length(); i++) {
            if (Float.isNaN(at[i])) continue;
            JsonObject gl = obj(glyphs, String.valueOf(text.charAt(i)));
            int u = gl.get("u").getAsInt(), v = gl.get("v").getAsInt(), w = gl.get("w").getAsInt(), h = gl.get("h").getAsInt();
            int dx = Math.round((at[i] + f(gl, "ox") - bx0) * 2), dy = Math.round((f(gl, "oy") - by0) * 2);
            g2.drawImage(atlas, dx, dy, dx + w, dy + h, u, v, u + w, v + h, null);
        }
        g2.dispose();
        int tw = Math.max(1, Math.round(iw / 2f * s)), th = Math.max(1, Math.round(ih / 2f * s));
        id = upload(id("text/" + slot), id, resize(line, tw, th));
        return new TextTex(id, tracking == 0 ? text : text + "\u0000" + tracking, tw, th, bx0, by0);
    }

    /** How wide a line of a font is, layout units (advances plus tracking), for wrapping and placing. */
    float measure(String fontKey, String text, float tracking) {
        JsonObject font = obj(obj(json, "fonts"), fontKey);
        if (font == null || text == null) return 0;
        JsonObject glyphs = font.getAsJsonObject("glyphs");
        float pen = 0;
        for (int i = 0; i < text.length(); i++) {
            JsonObject gl = obj(glyphs, String.valueOf(text.charAt(i)));
            if (gl == null) continue;
            if (i > 0) pen += tracking;
            pen += f(gl, "adv");
        }
        return pen;
    }

    /** A picture's own size in pixels (0 x 0 when it's missing). */
    int[] size(String file) {
        BufferedImage src = source(file);
        return src == null ? new int[] {0, 0} : new int[] {src.getWidth(), src.getHeight()};
    }

    // ------------------------------------------------------------------ pictures

    /** A picture of the kit resized to exactly w x h window pixels, kept under a slot (made again when the size changes). */
    private @Nullable Tex scaled(String slot, String file, int w, int h) {
        Tex t = textures.get(slot);
        if (t != null && t.w == w && t.h == h) return t;
        BufferedImage src = source(file);
        if (src == null) return null;
        Identifier id = upload(id(slot), t != null ? t.id : null, resize(src, w, h));
        Tex made = new Tex(id, w, h);
        textures.put(slot, made);
        return made;
    }

    private @Nullable BufferedImage source(String file) {
        if (sources.containsKey(file)) return sources.get(file);
        BufferedImage img = null;
        Path path = dir.resolve(file).normalize();
        if (path.startsWith(dir) && Files.isRegularFile(path)) {
            try {
                BufferedImage read = ImageIO.read(path.toFile());
                if (read != null) {
                    img = new BufferedImage(read.getWidth(), read.getHeight(), BufferedImage.TYPE_INT_ARGB_PRE);
                    Graphics2D g2 = img.createGraphics();
                    g2.drawImage(read, 0, 0, null);
                    g2.dispose();
                }
            } catch (Exception e) {
                ZomboidCraft.LOGGER.warn("Couldn't read HUD picture {}", path, e);
            }
        }
        sources.put(file, img);
        return img;
    }

    /** Smooth resize (halving steps first when shrinking a lot, then bicubic), premultiplied so edges don't darken. */
    private static BufferedImage resize(BufferedImage src, int w, int h) {
        BufferedImage cur = src;
        while (cur.getWidth() / 2 >= w && cur.getHeight() / 2 >= h && cur.getWidth() > 1 && cur.getHeight() > 1) {
            cur = draw(cur, Math.max(1, cur.getWidth() / 2), Math.max(1, cur.getHeight() / 2));
        }
        return cur.getWidth() == w && cur.getHeight() == h ? cur : draw(cur, w, h);
    }

    private static BufferedImage draw(BufferedImage src, int w, int h) {
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB_PRE);
        Graphics2D g2 = out.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
        g2.drawImage(src, 0, 0, w, h, null);
        g2.dispose();
        return out;
    }

    /**
     * Uploads a picture under a fresh id (the base id plus a count) and returns it. The picture it replaces is let go a
     * few frames later, not at once: a draw already queued this frame still points at it, and freeing it there let the
     * graphics card hand its slot to the next picture made, so one picture showed another's (Josiah 2026-11-09 08:20, the
     * main menu's highlight bar showing a stretched "MULTIPLAYER").
     */
    private static Identifier upload(Identifier base, @Nullable Identifier old, BufferedImage img) {
        NativeImage out = new NativeImage(img.getWidth(), img.getHeight(), false);
        for (int y = 0; y < img.getHeight(); y++) {
            for (int x = 0; x < img.getWidth(); x++) out.setPixel(x, y, img.getRGB(x, y));     // getRGB un-premultiplies
        }
        Identifier id = ZomboidCraft.id(base.getPath() + "/v" + (++made));
        Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(() -> "ZomboidCraft HUD " + id, out));
        if (old != null) retire(old);
        return id;
    }

    private static long made;
    private static final java.util.ArrayDeque<Object[]> RETIRED = new java.util.ArrayDeque<>();

    /** Lets a picture go once a few more frames have been drawn (no draw still queued can point at it then). */
    static void retire(Identifier id) {
        RETIRED.add(new Object[] {id, System.nanoTime()});
    }

    private static void releaseRetired() {
        long now = System.nanoTime();
        while (!RETIRED.isEmpty() && now - (long) RETIRED.peek()[1] > 250_000_000L) {
            Minecraft.getInstance().getTextureManager().release((Identifier) RETIRED.poll()[0]);
        }
    }

    private Identifier id(String file) {
        String path = ("hudkit/" + name + "/" + file).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9/._-]", "_");
        return ZomboidCraft.id(path);
    }

    // ------------------------------------------------------------------ json

    static @Nullable JsonObject obj(@Nullable JsonObject o, String key) {
        if (o == null) return null;
        JsonElement e = o.get(key);
        return e != null && e.isJsonObject() ? e.getAsJsonObject() : null;
    }

    static @Nullable JsonArray arr(@Nullable JsonObject o, String key) {
        if (o == null) return null;
        JsonElement e = o.get(key);
        return e != null && e.isJsonArray() ? e.getAsJsonArray() : null;
    }

    static float f(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e == null || e.isJsonNull() ? 0 : e.getAsFloat();
    }

    static int argb(int a, int r, int g, int b) {
        return (a & 0xFF) << 24 | (r & 0xFF) << 16 | (g & 0xFF) << 8 | (b & 0xFF);
    }

    /** White at this opacity (0..1), the colour a picture is drawn with. */
    static int white(float a) {
        return Math.round(Math.max(0, Math.min(1, a)) * 255) << 24 | 0xFFFFFF;
    }
}
