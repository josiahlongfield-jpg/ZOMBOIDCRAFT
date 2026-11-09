package com.zomboidcraft.client.hud;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.zomboidcraft.ZomboidCraft;
import com.zomboidcraft.client.npc.NpcSkin;
import com.zomboidcraft.client.npc.NpcSkinDraw;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.ItemEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityTypes;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * The Armory's Spartan, live in 3D on a page (and on the main menu, where there's no world or player yet): Reach's
 * multiplayer Spartan ({@code config/zomboidcraft/npc/spartan.zsk}, LOCAL ONLY) wearing the chosen pieces, painted in
 * the chosen primary and secondary colours through Reach's own change-colour masks, the visor see-through and tinted,
 * standing in one of MCC's Reach poses with the MA37 where MCC gives that pose one. Drawn through Minecraft's
 * entity-in-GUI renderer as a dropped item's render state that the item renderer hands over to us
 * (ItemEntityRendererMixin), as the bench's gun stage does, so it needs no entity.
 */
public final class SpartanFigure {
    static final String MODEL = "spartan";

    /** What one figure shows; each screen keeps its own and changes it as the choices change. */
    static final class Look {
        Set<String> nodes = Set.of();
        @Nullable String gun;
        String clip = "rifle_ready";
        float r1 = 1, g1 = 1, b1 = 1, r2 = 1, g2 = 1, b2 = 1;
        float vr = 1, vg = 0.6f, vb = 0.2f;
        float yaw;
        float time;
    }

    private static final Map<ItemEntityRenderState, Look> FIGURES = new IdentityHashMap<>();
    private static final Map<String, NativeImage[]> SOURCES = new HashMap<>();
    private static final Map<String, Identifier> PAINTED = new HashMap<>();
    private static final Map<String, Long> PAINT_FOR = new HashMap<>();
    private static final Object[] BUFFERS = new Object[1024];
    private static @Nullable Identifier white;
    private static int painted;

    private SpartanFigure() {
    }

    /** Whether the Spartan's model is on this computer and loaded (it loads in the background the first time asked). */
    static boolean ready() {
        return NpcSkin.get(MODEL) != null;
    }

    /** A figure's own render state, tied to its look. */
    static ItemEntityRenderState state(Look look) {
        ItemEntityRenderState s = new ItemEntityRenderState();
        s.entityType = EntityTypes.ITEM;
        s.count = 1;
        s.lightCoords = 0xF000F0;
        FIGURES.put(s, look);
        return s;
    }

    static void forget(ItemEntityRenderState s) {
        FIGURES.remove(s);
    }

    /**
     * Draws the figure standing in the box (GUI pixels), feet toward its bottom, about {@code fill} of its height tall,
     * looking down on it by {@code pitch} degrees.
     */
    static void draw(GuiGraphicsExtractor g, ItemEntityRenderState s, int x0, int y0, int x1, int y1, float fill, float pitch) {
        if (!ready()) return;
        float scale = (y1 - y0) * fill / 2.1f;
        Quaternionf view = new Quaternionf().rotateX((float) Math.toRadians(pitch)).rotateZ((float) Math.PI);
        Vector3f mid = new Vector3f(0, 1.04f, 0);
        view.transform(mid);
        Vector3f shift = mid.negate();
        g.entity(s, scale, shift, view, null, x0, y0, x1, y1);
    }

    /** Called by the dropped-item renderer: true when the state was one of ours and the Spartan was drawn instead. */
    public static boolean submit(ItemEntityRenderState state, PoseStack pose, SubmitNodeCollector collector) {
        Look look = FIGURES.get(state);
        if (look == null) return false;
        NpcSkin skin = NpcSkin.get(MODEL);
        if (skin == null) return true;
        Matrix4f[] bones = bones(skin, look.clip, look.time);
        int light = 0xF000F0, overlay = OverlayTexture.NO_OVERLAY;
        pose.pushPose();
        pose.mulPose(new Matrix4f().rotationY((float) Math.PI + look.yaw));
        NpcSkinDraw.submitGroups(skin.groups, bones, 0, gr -> shows(gr, look) && !visor(gr), gr -> texture(skin, gr.texture, look),
                BUFFERS, light, overlay, pose, collector);
        // the visor last, see-through and tinted
        float[] m = NpcSkinDraw.boneMatrices(bones);
        Identifier glass = white();
        int tint = HudKit.argb(205, Math.round(look.vr * 255), Math.round(look.vg * 255), Math.round(look.vb * 255));
        for (int i = 0; i < skin.groups.length; i++) {
            NpcSkin.Group gr = skin.groups[i];
            if (gr.level != 0 || !visor(gr) || !shows(gr, look) || glass == null) continue;
            Object[] buf = new Object[2];
            NpcSkinDraw.skinGroup(gr, m, buf, 0);
            float[] p = (float[]) buf[0], n = (float[]) buf[1];
            collector.submitCustomGeometry(pose, RenderTypes.entityTranslucent(glass), (at, out) -> {
                int[] t = gr.tris;
                for (int k = 0; k + 2 < t.length; k += 3) {
                    for (int c = 0; c < 4; c++) {
                        int v = t[k + Math.min(c, 2)], j = v * 3;
                        out.addVertex(at, p[j], p[j + 1], p[j + 2]).setColor(tint).setUv(gr.uv[v * 2], gr.uv[v * 2 + 1])
                                .setOverlay(overlay).setLight(light).setNormal(at, n[j], n[j + 1], n[j + 2]);
                    }
                }
            });
        }
        pose.popPose();
        return true;
    }

    private static boolean visor(NpcSkin.Group g) {
        return g.mesh.contains("visor") && !g.mesh.contains("interior");
    }

    private static boolean shows(NpcSkin.Group g, Look look) {
        if (g.piece.startsWith("gun_")) return g.piece.equals(look.gun);
        return look.nodes.contains(g.piece);
    }

    /** Each bone at a time in a clip (looped), as the skin takes them: the clip's model-space bone times its inverse rest. */
    private static Matrix4f[] bones(NpcSkin skin, String clip, float t) {
        int n = skin.inverse.length;
        Matrix4f[] out = new Matrix4f[n];
        NpcSkin.Clip c = skin.clips.get(clip);
        if (c == null) {
            for (int b = 0; b < n; b++) out[b] = new Matrix4f();
            return out;
        }
        float len = c.seconds();
        float f = len > 0 ? (t % len) * c.fps() : 0;
        int a = Math.min(c.frames() - 1, (int) f), bf = (a + 1) % c.frames();
        float k = f - (int) f;
        Quaternionf qa = new Quaternionf(), qb = new Quaternionf();
        for (int b = 0; b < n; b++) {
            if (b >= c.bones()) {
                out[b] = new Matrix4f();
                continue;
            }
            int ia = a * c.bones() + b, ib = bf * c.bones() + b;
            qa.set(c.turn()[ia * 4], c.turn()[ia * 4 + 1], c.turn()[ia * 4 + 2], c.turn()[ia * 4 + 3]);
            qb.set(c.turn()[ib * 4], c.turn()[ib * 4 + 1], c.turn()[ib * 4 + 2], c.turn()[ib * 4 + 3]);
            qa.slerp(qb, k).normalize();
            float x = c.at()[ia * 3] * (1 - k) + c.at()[ib * 3] * k, y = c.at()[ia * 3 + 1] * (1 - k) + c.at()[ib * 3 + 1] * k,
                    z = c.at()[ia * 3 + 2] * (1 - k) + c.at()[ib * 3 + 2] * k;
            out[b] = new Matrix4f().translationRotate(x, y, z, qa).mul(skin.inverse[b]);
        }
        return out;
    }

    /**
     * A piece's texture. Armour with a change-colour mask is painted in the look's two colours the way Reach does it
     * (each mask channel blends its colour in: red the primary, green the secondary), made again when they change.
     */
    private static @Nullable Identifier texture(NpcSkin skin, String key, Look look) {
        NativeImage[] src = SOURCES.get(key);
        if (src == null) {
            byte[] mask = skin.raw(key + "#cc"), base = skin.raw(key);
            if (mask == null || base == null) return skin.texture(key, MODEL);
            try {
                src = new NativeImage[] {NpcSkin.decode(base), NpcSkin.decode(mask)};
            } catch (Exception e) {
                ZomboidCraft.LOGGER.warn("Bad Spartan texture {}", key, e);
                return skin.texture(key, MODEL);
            }
            SOURCES.put(key, src);
        }
        int c1 = rgb(look.r1, look.g1, look.b1), c2 = rgb(look.r2, look.g2, look.b2);
        long want = ((long) c1 << 32) | (c2 & 0xFFFFFFFFL);
        Identifier id = PAINTED.get(key);
        Long have = PAINT_FOR.get(key);
        if (id != null && have != null && have == want) return id;
        NativeImage base = src[0], mask = src[1];
        int w = base.getWidth(), h = base.getHeight(), mw = mask.getWidth(), mh = mask.getHeight();
        NativeImage out = new NativeImage(w, h, false);
        for (int y = 0; y < h; y++) {
            int my = y * mh / h;
            for (int x = 0; x < w; x++) {
                int p = base.getPixel(x, y), q = mask.getPixel(x * mw / w, my);
                float m1 = ((q >> 16) & 255) / 255f, m2 = ((q >> 8) & 255) / 255f;
                float fr = (1 - m1 + m1 * look.r1) * (1 - m2 + m2 * look.r2);
                float fg = (1 - m1 + m1 * look.g1) * (1 - m2 + m2 * look.g2);
                float fb = (1 - m1 + m1 * look.b1) * (1 - m2 + m2 * look.b2);
                int r = Math.min(255, Math.round(((p >> 16) & 255) * fr)), g = Math.min(255, Math.round(((p >> 8) & 255) * fg)),
                        b = Math.min(255, Math.round((p & 255) * fb));
                out.setPixel(x, y, 0xFF000000 | r << 16 | g << 8 | b);
            }
        }
        // a fresh id each time the colours change; the old picture goes a few frames later (HudKit.retire)
        Identifier fresh = ZomboidCraft.id("dynamic/armory/" + key.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9/._-]", "_") + "/" + (++painted));
        Minecraft.getInstance().getTextureManager().register(fresh, new DynamicTexture(() -> "Endless Night Armory " + key, out));
        if (id != null) HudKit.retire(id);
        id = fresh;
        PAINTED.put(key, id);
        PAINT_FOR.put(key, want);
        return id;
    }

    private static int rgb(float r, float g, float b) {
        return Math.round(r * 255) << 16 | Math.round(g * 255) << 8 | Math.round(b * 255);
    }

    private static @Nullable Identifier white() {
        if (white == null) {
            NativeImage img = new NativeImage(2, 2, false);
            for (int y = 0; y < 2; y++) for (int x = 0; x < 2; x++) img.setPixel(x, y, 0xFFFFFFFF);
            white = ZomboidCraft.id("dynamic/armory/visor_glass");
            Minecraft.getInstance().getTextureManager().register(white, new DynamicTexture(() -> "Endless Night visor", img));
        }
        return white;
    }
}
