package com.zomboidcraft.client.combat;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.zomboidcraft.ZomboidCraft;
import com.zomboidcraft.combat.GunState;
import com.zomboidcraft.combat.GunType;
import com.zomboidcraft.combat.LocalGuns;
import java.io.ByteArrayInputStream;
import java.io.Reader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomModelData;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * The reloads Josiah set by hand in the reload editor (2026-11-04), played in the game: for each local gun, three
 * clips (first person; third person standing still; third person on the move, gun up), each frame the gun, her arm
 * bones and each moving piece (the magazine out and in, a cylinder, a pump, a lever, loose rounds) where he left them.
 * Read from {@code config/zomboidcraft/guns/reloads/<id>.json} (baked on this computer from his saved edits, never
 * shipped): the gun's pieces as triangles in item pixels with its own picture, and the frames in the pose files' own
 * frames (first person: the hand frame; third person: her Bip01_Spine1's), so they line up with {@link GunPoses}.
 * A clip is stretched over however long the game's reload of that gun takes; a gun without a file reloads as before.
 */
public final class ReloadClips {
    /** One piece: its triangles (item pixels), and either the gun's picture or a flat colour per corner. */
    record Piece(MeshGuns.Part part, boolean textured, int[] colours) {
    }

    /** One frame: the gun (item pixels to the clip's frame, 3x4 rows), the bones [x y z qx qy qz qw], each piece on the gun. */
    record Frame(float[] gun, Map<String, float[]> bones, Map<String, float[]> pieces) {
    }

    record View(float dur, Frame[] frames) {
    }

    record Clip(String id, float fps, Identifier texture, Map<String, Piece> pieces, Map<String, View> views) {
    }

    /** What a clip frame gives at a moment: the gun (item pixels to the frame), bones, and pieces on the gun. */
    public record At(Matrix4f gun, Map<String, Matrix4f> bones, Map<String, Matrix4f> pieces) {
    }

    private static final Path DIR = LocalGuns.DIR.resolve("reloads");
    private static final Map<String, Clip> CACHE = new HashMap<>();
    private static final Clip NONE = new Clip("", 0, null, Map.of(), Map.of());
    private static int generation = -1;
    private static final int INK = 0xFF141414;

    private ReloadClips() {
    }

    /** The clip a local gun has (render thread: its picture is made on first use), or null. */
    static @Nullable Clip of(@Nullable String id) {
        if (id == null) return null;
        if (generation != LocalGuns.generation()) {
            generation = LocalGuns.generation();
            CACHE.clear();
        }
        Clip c = CACHE.computeIfAbsent(id, ReloadClips::read);
        return c == NONE ? null : c;
    }

    private static Clip read(String id) {
        Path f = DIR.resolve(id + ".json");
        if (!Files.isRegularFile(f)) return NONE;
        try (Reader r = Files.newBufferedReader(f, StandardCharsets.UTF_8)) {
            JsonObject o = JsonParser.parseReader(r).getAsJsonObject();
            Identifier tex = ZomboidCraft.id("dynamic/reload/" + id.toLowerCase(java.util.Locale.ROOT));
            NativeImage image = NativeImage.read(new ByteArrayInputStream(Base64.getDecoder().decode(o.get("tex").getAsString())));
            Minecraft.getInstance().getTextureManager().register(tex, new DynamicTexture(() -> "ZomboidCraft reload " + id, image));
            Map<String, Piece> pieces = new HashMap<>();
            for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("groups").entrySet()) {
                pieces.put(e.getKey(), piece(e.getValue().getAsJsonObject()));
            }
            Map<String, View> views = new HashMap<>();
            for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("views").entrySet()) {
                JsonObject v = e.getValue().getAsJsonObject();
                JsonArray fs = v.getAsJsonArray("frames");
                Frame[] frames = new Frame[fs.size()];
                for (int i = 0; i < frames.length; i++) {
                    JsonObject fo = fs.get(i).getAsJsonObject();
                    frames[i] = new Frame(floats(fo.getAsJsonArray("g")), map(fo.getAsJsonObject("b")), map(fo.getAsJsonObject("p")));
                }
                views.put(e.getKey(), new View(v.get("dur").getAsFloat(), frames));
            }
            return new Clip(id, o.get("fps").getAsFloat(), tex, pieces, views);
        } catch (Exception e) {
            ZomboidCraft.LOGGER.error("Couldn't read reload clip {}", f, e);
            return NONE;
        }
    }

    private static Piece piece(JsonObject g) {
        float[] pos = f32(g.get("p").getAsString());
        int tris = pos.length / 9;
        float[] uv = g.has("u") ? f32(g.get("u").getAsString()) : new float[tris * 6];
        int[] colours = new int[tris * 3];
        if (g.has("cv")) {
            float[] c = f32(g.get("cv").getAsString());
            for (int i = 0; i < colours.length; i++) colours[i] = argb(c[i * 3], c[i * 3 + 1], c[i * 3 + 2]);
        } else {
            JsonArray c = g.getAsJsonArray("c");
            int all = c == null ? 0xFFFFFFFF : argb(c.get(0).getAsFloat(), c.get(1).getAsFloat(), c.get(2).getAsFloat());
            java.util.Arrays.fill(colours, all);
        }
        float[] normal = new float[tris * 3];
        for (int t = 0; t < tris; t++) {
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
        // each corner's smooth normal: the faces meeting at its point, for the ink outline
        Map<Long, float[]> sum = new HashMap<>();
        for (int i = 0; i < tris * 3; i++) {
            float[] s = sum.computeIfAbsent(key(pos, i), k -> new float[3]);
            for (int a = 0; a < 3; a++) s[a] += normal[i / 3 * 3 + a];
        }
        float[] smooth = new float[tris * 9];
        for (int i = 0; i < tris * 3; i++) {
            float[] s = sum.get(key(pos, i));
            float len = (float) Math.sqrt(s[0] * s[0] + s[1] * s[1] + s[2] * s[2]);
            for (int a = 0; a < 3; a++) smooth[i * 3 + a] = len < 1e-6f ? 0 : s[a] / len;
        }
        MeshGuns.Part part = new MeshGuns.Part(tris, pos, uv, normal, smooth, MeshGuns.pair(tris, pos, uv, smooth, normal));
        return new Piece(part, g.has("u"), colours);
    }

    private static long key(float[] pos, int i) {
        long x = Math.round(pos[i * 3] * 1000), y = Math.round(pos[i * 3 + 1] * 1000), z = Math.round(pos[i * 3 + 2] * 1000);
        return (x & 0x1FFFFF) << 42 | (y & 0x1FFFFF) << 21 | z & 0x1FFFFF;
    }

    private static int argb(float r, float g, float b) {
        int ri = Mth.clamp(Math.round(r * 255), 0, 255), gi = Mth.clamp(Math.round(g * 255), 0, 255), bi = Mth.clamp(Math.round(b * 255), 0, 255);
        return 0xFF000000 | ri << 16 | gi << 8 | bi;
    }

    private static float[] f32(String b64) {
        ByteBuffer b = ByteBuffer.wrap(Base64.getDecoder().decode(b64)).order(ByteOrder.LITTLE_ENDIAN);
        float[] out = new float[b.remaining() / 4];
        b.asFloatBuffer().get(out);
        return out;
    }

    private static float[] floats(JsonArray a) {
        float[] f = new float[a.size()];
        for (int i = 0; i < f.length; i++) f[i] = a.get(i).getAsFloat();
        return f;
    }

    private static Map<String, float[]> map(@Nullable JsonObject o) {
        Map<String, float[]> m = new HashMap<>();
        if (o != null) for (Map.Entry<String, JsonElement> e : o.entrySet()) m.put(e.getKey(), floats(e.getValue().getAsJsonArray()));
        return m;
    }

    // ------------------------------------------------------------------ sampling

    /** The clip's frame at u (0 to 1 of the reload), blended between its two nearest frames. */
    static @Nullable At at(Clip clip, String view, float u) {
        View v = clip.views().get(view);
        if (v == null || v.frames().length == 0) return null;
        float x = Mth.clamp(u, 0, 1) * (v.frames().length - 1);
        int i = Math.min(v.frames().length - 2, (int) Math.floor(x));
        if (i < 0) i = 0;
        float a = v.frames().length < 2 ? 0 : x - i;
        Frame f0 = v.frames()[i], f1 = v.frames()[Math.min(v.frames().length - 1, i + 1)];
        Matrix4f gun = rows(f0.gun(), f1.gun(), a);
        Map<String, Matrix4f> bones = new HashMap<>();
        Vector3f p0 = new Vector3f(), p1 = new Vector3f();
        for (Map.Entry<String, float[]> e : f0.bones().entrySet()) {
            float[] b0 = e.getValue(), b1 = f1.bones().getOrDefault(e.getKey(), b0);
            p0.set(b0[0], b0[1], b0[2]);
            p1.set(b1[0], b1[1], b1[2]);
            Quaternionf q = new Quaternionf(b0[3], b0[4], b0[5], b0[6]).normalize()
                    .slerp(new Quaternionf(b1[3], b1[4], b1[5], b1[6]).normalize(), a);
            bones.put(e.getKey(), new Matrix4f().translationRotate(p0.lerp(p1, a, new Vector3f()), q));
        }
        Map<String, Matrix4f> pieces = new HashMap<>();
        // a piece hidden in one of the two frames shows as the nearer one has it
        Frame near = a < 0.5f ? f0 : f1;
        for (Map.Entry<String, float[]> e : near.pieces().entrySet()) {
            float[] m0 = f0.pieces().get(e.getKey()), m1 = f1.pieces().get(e.getKey());
            pieces.put(e.getKey(), m0 != null && m1 != null ? rows(m0, m1, a) : rows(e.getValue(), e.getValue(), 0));
        }
        return new At(gun, bones, pieces);
    }

    /** A 3x4 row-major matrix, blended. */
    private static Matrix4f rows(float[] a, float[] b, float t) {
        float[] m = new float[12];
        for (int i = 0; i < 12; i++) m[i] = Mth.lerp(t, a[i], b[i]);
        return new Matrix4f(m[0], m[4], m[8], 0, m[1], m[5], m[9], 0, m[2], m[6], m[10], 0, m[3], m[7], m[11], 1);
    }

    // ------------------------------------------------------------------ drawing

    /** The gun and its pieces as the clip has them; pose is the clip's frame. */
    static void draw(Clip clip, At at, PoseStack pose, SubmitNodeCollector out, int light) {
        pose.pushPose();
        pose.mulPose(at.gun());
        Piece body = clip.pieces().get("_body");
        if (body != null) submit(clip, body, pose, out, light);
        for (Map.Entry<String, Matrix4f> e : at.pieces().entrySet()) {
            Piece p = clip.pieces().get(e.getKey());
            if (p == null) continue;
            pose.pushPose();
            pose.mulPose(e.getValue());
            submit(clip, p, pose, out, light);
            pose.popPose();
        }
        pose.popPose();
    }

    private static void submit(Clip clip, Piece p, PoseStack pose, SubmitNodeCollector out, int light) {
        if (p.part().triangles() == 0 || com.zomboidcraft.client.zombie.body.ZDetail.shadowPass()) return;
        Identifier paint = p.textured() ? clip.texture() : white();
        out.submitCustomGeometry(pose, RenderTypes.entityCutoutCull(paint), (m, buffer) -> solid(p, m, buffer, light));
        if (MeshGuns.inkOn && !MeshGuns.inPicture) {
            out.submitCustomGeometry(pose, RenderTypes.entityCutoutCull(paint), (m, buffer) -> ink(p, m, buffer, light));
        }
    }

    private static void solid(Piece piece, PoseStack.Pose pose, VertexConsumer out, int light) {
        MeshGuns.Part p = piece.part();
        float[] pos = p.pos(), uv = p.uv(), n = p.normal();
        int[] q = p.quads();
        for (int f = 0, quads = p.quadCount(); f < quads; f++) {
            for (int c = 0; c < 4; c++) {
                int i = q[f * 5 + c], t = c == 3 ? q[f * 5 + 4] : i / 3;
                out.addVertex(pose, pos[i * 3], pos[i * 3 + 1], pos[i * 3 + 2])
                        .setColor(piece.textured() ? 0xFFFFFFFF : piece.colours()[i])
                        .setUv(uv[i * 2], uv[i * 2 + 1])
                        .setOverlay(OverlayTexture.NO_OVERLAY)
                        .setLight(light)
                        .setNormal(pose, n[t * 3], n[t * 3 + 1], n[t * 3 + 2]);
            }
        }
    }

    private static final Identifier WHITE = ZomboidCraft.id("dynamic/reload/white");
    private static boolean whiteMade;

    /** A plain white picture for the pieces painted by colour (loose rounds, a speedloader). */
    private static Identifier white() {
        if (!whiteMade) {
            whiteMade = true;
            NativeImage img = new NativeImage(4, 4, false);
            for (int y = 0; y < 4; y++) for (int x = 0; x < 4; x++) img.setPixel(x, y, 0xFFFFFFFF);
            Minecraft.getInstance().getTextureManager().register(WHITE, new DynamicTexture(() -> "ZomboidCraft reload white", img));
        }
        return WHITE;
    }

    private static void ink(Piece piece, PoseStack.Pose pose, VertexConsumer out, int light) {
        MeshGuns.Part p = piece.part();
        float[] pos = p.pos(), uv = p.uv(), s = p.smooth(), n = p.normal();
        int[] q = p.quads();
        float outline = 0.12f;
        for (int f = 0, quads = p.quadCount(); f < quads; f++) {
            for (int c = 0; c < 4; c++) {
                int k = (4 - c) & 3;
                int i = q[f * 5 + k], t = k == 3 ? q[f * 5 + 4] : i / 3;
                out.addVertex(pose, pos[i * 3] + s[i * 3] * outline, pos[i * 3 + 1] + s[i * 3 + 1] * outline,
                                pos[i * 3 + 2] + s[i * 3 + 2] * outline)
                        .setColor(INK)
                        .setUv(uv[i * 2], uv[i * 2 + 1])
                        .setOverlay(OverlayTexture.NO_OVERLAY)
                        .setLight(light)
                        .setNormal(pose, -n[t * 3], -n[t * 3 + 1], -n[t * 3 + 2]);
            }
        }
    }

    /** A holder's third-person reload at this moment: the clip, its frame, and how far its arms are blended in. */
    public record ThirdAt(Object clip, At at, float w) {
    }

    /** The third-person reload frame for a holder of a local gun mid-reload (standing, or moving: gun up), or null. */
    public static @Nullable ThirdAt third(Entity holder, ItemStack stack, float age, boolean moving) {
        if (!com.zomboidcraft.ZcConfig.get().guns.reloadAnimation) return null;
        Clip c = of(stack.get(LocalGuns.ID));
        if (c == null) return null;
        float u = progress(holder, stack, age);
        if (u < 0) return null;
        String view = moving ? "ta" : "tp";
        At at = at(c, view, u);
        return at == null ? null : new ThirdAt(c, at, blend(u, dur(c, view)));
    }

    /** Draws a third-person reload frame's gun, pieces, sight and grip; pose is her Spine1's frame. */
    public static void drawThird(ThirdAt t, ItemStack stack, PoseStack pose, SubmitNodeCollector out, int light) {
        draw((Clip) t.clip(), t.at(), pose, out, light);
        pose.pushPose();
        pose.mulPose(new Matrix4f(t.at().gun()).scale(16));
        LocalAttachments.drawParts(stack.get(LocalGuns.ID),
                com.zomboidcraft.combat.LocalParts.fitted(stack, com.zomboidcraft.combat.Attachments.Slot.OPTIC),
                com.zomboidcraft.combat.LocalParts.fitted(stack, com.zomboidcraft.combat.Attachments.Slot.UNDERBARREL), pose, out, light);
        pose.popPose();
    }

    // ------------------------------------------------------------------ how far into the reload

    /** Each holder's reload as seen from its gun's model data: when it began and how long it should take. */
    private static final class Track {
        float start = -1, stopAt = -1, estimate;
        int lastStep;
        boolean jam, charge;
        String gun;
    }

    private static final Map<Integer, Track> TRACKS = new HashMap<>();

    /**
     * How far through the reload a holder is (0 to 1), or -1 when not reloading (or only clearing a jam, which keeps
     * the old moves). age: the holder's ticks with the frame's part. Magazine guns: the game's own reload length (an
     * empty gun's, with its handle worked, or a fresh magazine onto a chambered round); hand-loaded guns: the rounds
     * that will go in, then the time to close it up after the last.
     */
    public static float progress(Entity holder, ItemStack gun, float age) {
        GunType type = GunType.of(gun);
        String id = gun.get(LocalGuns.ID);
        Track t = TRACKS.computeIfAbsent(holder.getId(), k -> new Track());
        if (type == null || id == null) {
            t.lastStep = 0;
            t.start = -1;
            return -1;
        }
        float raw = step(gun);
        int step = (int) raw;
        if (!id.equals(t.gun)) {
            t.gun = id;
            t.start = -1;
            t.lastStep = 0;
        }
        if (step != t.lastStep) {
            if (t.lastStep == 0 && step >= 1) {
                t.start = age;
                t.stopAt = -1;
                t.jam = step == 3;
                t.charge = raw > 1.25f;
                GunState st = gun.getOrDefault(GunState.TYPE, GunState.EMPTY_MAG);
                if (type.tube) {
                    int n = Math.max(1, type.capacity(gun) - st.rounds());
                    if (holder == Minecraft.getInstance().player) n = Math.max(1, Math.min(n, ammo(holder, gun, type)));
                    t.estimate = type.raise + n * type.perShell + finish(type, t.charge);
                } else {
                    t.estimate = t.charge ? type.chargeDone : type.tacticalDone;
                }
            } else if (type.tube && t.start >= 0 && t.stopAt < 0 && step != 1) {
                t.stopAt = age;
            }
            t.lastStep = step;
        }
        if (t.start < 0 || t.jam) return -1;
        float since = age - t.start;
        float u;
        if (!type.tube) {
            u = since / Math.max(1, t.estimate);
        } else if (t.stopAt < 0) {
            u = Math.min(0.92f, since / Math.max(1, t.estimate));
        } else {
            float atStop = Math.min(0.92f, (t.stopAt - t.start) / Math.max(1, t.estimate));
            u = atStop + (1 - atStop) * (age - t.stopAt) / Math.max(1, finish(type, t.charge));
        }
        if (u >= 1 || (!type.tube && step == 0 && since > 2)) {
            t.start = -1;
            return -1;
        }
        return Math.max(0, u);
    }

    /** Ticks from the last round in to the gun back in the hands. */
    private static float finish(GunType type, boolean charge) {
        return type.opens ? type.close + 6 : charge && type.cycle > 0 ? type.cycle + 4 : 8;
    }

    private static int ammo(Entity holder, ItemStack gun, GunType type) {
        if (!(holder instanceof net.minecraft.world.entity.player.Player p)) return 99;
        net.minecraft.world.item.Item round = LocalGuns.ammo(gun, type);
        int n = 0;
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
            ItemStack s = p.getInventory().getItem(i);
            if (s.is(round)) n += s.getCount();
        }
        return p.hasInfiniteMaterials() ? 99 : n;
    }

    private static float step(ItemStack gun) {
        CustomModelData data = gun.get(DataComponents.CUSTOM_MODEL_DATA);
        return data == null || data.floats().isEmpty() ? 0 : data.floats().get(0);
    }

    /** A clip view's length in seconds (1 if it hasn't one). */
    static float dur(Clip clip, String view) {
        View v = clip.views().get(view);
        return v == null ? 1 : v.dur();
    }

    /** Bones from the held pose blended toward the clip's (w of the way); a bone only one side has is taken as it is. */
    public static Map<String, Matrix4f> mix(Map<String, Matrix4f> held, Map<String, Matrix4f> clip, float w) {
        Map<String, Matrix4f> out = new HashMap<>(held);
        Vector3f p0 = new Vector3f(), p1 = new Vector3f();
        Quaternionf q0 = new Quaternionf(), q1 = new Quaternionf();
        for (Map.Entry<String, Matrix4f> e : clip.entrySet()) {
            Matrix4f h = held.get(e.getKey()), c = e.getValue();
            if (h == null || w >= 1) {
                out.put(e.getKey(), new Matrix4f(c));
                continue;
            }
            h.getTranslation(p0);
            c.getTranslation(p1);
            h.getUnnormalizedRotation(q0).normalize();
            c.getUnnormalizedRotation(q1).normalize();
            out.put(e.getKey(), new Matrix4f().translationRotate(p0.lerp(p1, w, new Vector3f()), q0.slerp(q1, w, new Quaternionf())));
        }
        return out;
    }

    /** How much of the clip's arms show over the held pose: in over the first moments, out over the last. */
    static float blend(float u, float dur) {
        float edge = Math.min(0.5f, 0.18f / Math.max(0.3f, dur));
        float x = Math.min(u, 1 - u) / edge;
        x = Mth.clamp(x, 0, 1);
        return x * x * (3 - 2 * x);
    }
}
