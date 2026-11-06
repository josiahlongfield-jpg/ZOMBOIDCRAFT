package com.zomboidcraft.client.combat;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.zomboidcraft.ZomboidCraft;
import com.zomboidcraft.client.cars.CarAssets;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.zip.InflaterInputStream;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;

/**
 * The bought guns (Josiah 2026-10-29: real meshes, toon-painted, in place of the block guns). Loads a packed
 * assets/zomboidcraft/meshes/ID.zmesh (tools/meshguns/pack.py) the first time it's drawn and draws its parts in item
 * model space, so a gun's item model can swap its block body for {@link MeshGunRenderer} and keep everything else.
 * Drawn culled (entityCutoutCull; 26.3's plain entityCutout draws both sides), which the outline shell relies on.
 */
public final class MeshGuns {
    /** Triangles in item pixels (muzzle toward -z), uvs, each triangle's flat normal and each corner's smooth one. */
    record Part(int triangles, float[] pos, float[] uv, float[] normal, float[] smooth, int[] quads) {
        /** Quads drawn: pairs of triangles sharing an edge, and the rest as a triangle with its last corner twice. */
        int quadCount() {
            return quads.length / 5;
        }
    }

    record Gun(Map<String, Part> parts, Identifier texture, float outline, float[] lo, float[] hi) {
    }

    private static final int INK = 0xFF141414;
    /** Test switches (GunLookTest): draw the outline shell; wind every triangle the other way round. */
    public static volatile boolean inkOn = true, flipped = false;
    /** Set while a picture-in-picture (HUD figure, inventory mirror) draws; guns there skip the ink (PictureInPictureMixin). */
    public static boolean inPicture;
    private static final Map<String, Gun> GUNS = new HashMap<>();
    private static final Set<String> MISSING = new HashSet<>();
    private static final Map<String, float[]> BOUNDS = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * Meshes of ours this computer draws from its own file instead, packed under the same id (scrambled with it):
     * config/zomboidcraft/meshes/&lt;id&gt;.zmesh. Whoever hasn't got one sees the mod's.
     */
    public static final java.nio.file.Path OVERRIDES = net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir()
            .resolve("zomboidcraft").resolve("meshes");

    /** Mesh ids of guns kept on this computer start with this: "local/" + the gun's file name. */
    public static final String LOCAL = "local/";

    /** Whether this computer has the mesh of a local gun (its file is there). */
    public static boolean hasLocal(String gun) {
        // asked for every part of every gun drawn each frame: the disk is looked at again only every few seconds
        if (localGeneration != com.zomboidcraft.combat.LocalGuns.generation()) {
            localGeneration = com.zomboidcraft.combat.LocalGuns.generation();
            forgetLocal();
        }
        long now = System.currentTimeMillis();
        long[] seen = LOCAL_SEEN.get(gun);
        if (seen == null || now - seen[0] > 5000) {
            boolean there = java.nio.file.Files.isRegularFile(com.zomboidcraft.combat.LocalGuns.DIR.resolve(gun + ".zmesh"));
            LOCAL_SEEN.put(gun, seen = new long[] {now, there ? 1 : 0});
        }
        return seen[1] == 1;
    }

    private static int localGeneration;
    private static final Map<String, long[]> LOCAL_SEEN = new java.util.concurrent.ConcurrentHashMap<>();

    /** Forgets the local guns' meshes, so changed files are read again (/zc localgun reload). Render thread. */
    public static void forgetLocal() {
        LOCAL_SEEN.clear();
        GUNS.clear();          // the overrides too, so a changed round file is read again
        MISSING.clear();
    }

    /** The gun, or null if it can't be loaded. Render thread only. */
    static Gun get(String id) {
        if (MISSING.contains(id)) {
            return null;
        }
        Gun gun = GUNS.get(id);
        if (gun == null) {
            try {
                gun = read(id);
            } catch (Exception e) {
                ZomboidCraft.LOGGER.error("Couldn't load gun model {}", id, e);
                MISSING.add(id);
                return null;
            }
            GUNS.put(id, gun);
        }
        return gun;
    }

    /**
     * A local gun's mesh whole (every part where it rests) and, for ironsOf (the gun's id; null with a sight on), the iron
     * sights it came without, the pose at the item model's corner: what the item model would show, drawn where the caller
     * wants it (a survivor's posed third-person hold, which places the gun itself rather than through the item's display
     * transform).
     */
    public static void drawLocal(String mesh, @org.jetbrains.annotations.Nullable String ironsOf, PoseStack pose, SubmitNodeCollector out, int light) {
        Gun gun = get(mesh);
        if (gun == null) {
            return;
        }
        for (String part : gun.parts().keySet()) {
            draw(mesh, part, pose, out, light, null);
        }
        com.zomboidcraft.combat.LocalGuns.Spec spec = ironsOf == null ? null : com.zomboidcraft.combat.LocalGuns.get(ironsOf);
        if (spec != null && spec.irons != null) {
            for (com.zomboidcraft.combat.LocalGuns.Part p : spec.irons) {
                if (p == null || p.mesh == null || p.at == null || p.at.length < 3) {
                    continue;
                }
                pose.pushPose();
                pose.translate(p.at[0] / 16, p.at[1] / 16, p.at[2] / 16);
                draw(p.mesh, "body", pose, out, light, null);
                pose.popPose();
            }
        }
    }

    /** Draws one part of a gun with the pose at the item model's corner (one unit a block, as a block model draws). */
    public static void draw(String id, String part, PoseStack pose, SubmitNodeCollector out, int light) {
        draw(id, part, pose, out, light, null);
    }

    /** The same, painted with another texture laid out like the packed one (a garment's camo), when one is given. */
    public static void draw(String id, String part, PoseStack pose, SubmitNodeCollector out, int light,
                            @org.jetbrains.annotations.Nullable Identifier texture) {
        Gun gun = get(id);
        Part p = gun == null ? null : gun.parts().get(part);
        // no shadow from a mesh item: a gun or a tin casts next to none, and the profile showed the bought guns
        // (15,000-32,000 triangles each, twice over with the ink) costing as much again in the shadow pass
        if (p == null || p.triangles() == 0 || com.zomboidcraft.client.zombie.body.ZDetail.shadowPass()) {
            return;
        }
        pose.pushPose();
        pose.scale(1 / 16f, 1 / 16f, 1 / 16f);
        Identifier paint = texture != null ? texture : gun.texture();
        final int shade = tint;
        out.submitCustomGeometry(pose, RenderTypes.entityCutoutCull(paint), (at, buffer) -> solid(p, at, buffer, light, shade));
        if (inkOn && !inPicture) {
            out.submitCustomGeometry(pose, RenderTypes.entityCutoutCull(paint), (at, buffer) -> ink(p, gun.outline(), at, buffer, light));
        }
        for (Identifier over : overlays) {
            out.submitCustomGeometry(pose, RenderTypes.entityCutoutCull(over), (at, buffer) -> overlay(p, at, buffer, light));
        }
        pose.popPose();
    }

    /**
     * Layers drawn over the next mesh parts drawn, while set: a worn garment's dirt and blood. ClothingLayer sets it
     * round the one part it submits (the item renderer draws it there and then) and clears it after.
     */
    public static java.util.List<Identifier> overlays = java.util.List.of();

    /** The colour the next parts are painted through, while set (a wet garment darkens); white is as painted. */
    public static int tint = 0xFFFFFFFF;

    /**
     * A see-through layer over a part: the mesh again, a hair out along its smooth normals, painted with a cut-out
     * texture laid on by position rather than the packed uvs (each face takes the texture across the two axes it
     * faces between), so marks are the same size on every garment. Item pixels -20 to 28 map across the texture.
     */
    private static void overlay(Part p, PoseStack.Pose pose, VertexConsumer out, int light) {
        float[] pos = p.pos(), s = p.smooth(), n = p.normal();
        final float lift = 0.04f, from = -20f, span = 48f;
        for (int t = 0; t < p.triangles(); t++) {
            float ax = Math.abs(n[t * 3]), ay = Math.abs(n[t * 3 + 1]), az = Math.abs(n[t * 3 + 2]);
            for (int c = 0; c < 4; c++) {
                int k = Math.min(c, 2);
                int i = t * 3 + (flipped ? 2 - k : k);
                float x = pos[i * 3], y = pos[i * 3 + 1], z = pos[i * 3 + 2];
                // across and down: down the texture is down the part (model y is up)
                float u = ax >= ay && ax >= az ? z : ay >= az ? x : x;
                float v = ax >= ay && ax >= az ? -y : ay >= az ? z : -y;
                out.addVertex(pose, x + s[i * 3] * lift, y + s[i * 3 + 1] * lift, z + s[i * 3 + 2] * lift)
                        .setColor(0xFFFFFFFF)
                        .setUv((u - from) / span, (v - from) / span)
                        .setOverlay(OverlayTexture.NO_OVERLAY)
                        .setLight(light)
                        .setNormal(pose, n[t * 3], n[t * 3 + 1], n[t * 3 + 2]);
            }
        }
    }

    private static void solid(Part p, PoseStack.Pose pose, VertexConsumer out, int light) {
        solid(p, pose, out, light, 0xFFFFFFFF);
    }

    private static void solid(Part p, PoseStack.Pose pose, VertexConsumer out, int light, int colour) {
        float[] pos = p.pos(), uv = p.uv(), n = p.normal();
        int[] q = p.quads();
        for (int f = 0, quads = p.quadCount(); f < quads; f++) {
            for (int c = 0; c < 4; c++) {
                int k = flipped ? (4 - c) & 3 : c;     // a b c d, or a d c b the other way round
                int i = q[f * 5 + k], t = k == 3 ? q[f * 5 + 4] : i / 3;
                out.addVertex(pose, pos[i * 3], pos[i * 3 + 1], pos[i * 3 + 2])
                        .setColor(colour)
                        .setUv(uv[i * 2], uv[i * 2 + 1])
                        .setOverlay(OverlayTexture.NO_OVERLAY)
                        .setLight(light)
                        .setNormal(pose, n[t * 3], n[t * 3 + 1], n[t * 3 + 2]);
            }
        }
    }

    /**
     * The ink outline: the mesh pushed out by outline pixels along its smooth normals (the file says how far) and
     * wound the other way, so culling keeps only its far side, a black rim round the gun.
     */
    private static void ink(Part p, float outline, PoseStack.Pose pose, VertexConsumer out, int light) {
        float[] pos = p.pos(), uv = p.uv(), s = p.smooth(), n = p.normal();
        int[] q = p.quads();
        for (int f = 0, quads = p.quadCount(); f < quads; f++) {
            if (bare(s, q, f)) {
                continue;       // no smooth normal (a local gun's iron sights): no ink, as on the posing page
            }
            for (int c = 0; c < 4; c++) {
                int k = flipped ? c : (4 - c) & 3;     // wound the other way from the solid
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

    /** Whether every corner of quad f has no smooth normal (a pair only joins corners alike, so it's all or none). */
    private static boolean bare(float[] s, int[] q, int f) {
        for (int c = 0; c < 4; c++) {
            int i = q[f * 5 + c];
            if (s[i * 3] != 0 || s[i * 3 + 1] != 0 || s[i * 3 + 2] != 0) {
                return false;
            }
        }
        return true;
    }

    /**
     * Where a mesh reaches, in blocks from the item model's corner ({lo x, y, z, hi x, y, z}), for the item's extents;
     * read without making its texture, since models bake while resources reload. Null if it can't be read.
     */
    static float[] bounds(String id) {
        float[] b = BOUNDS.get(id);
        if (b == null) {
            b = new float[0];      // unreadable: remembered as empty
            try {
                Gun g = read(id, false);
                b = new float[] {g.lo()[0] / 16, g.lo()[1] / 16, g.lo()[2] / 16, g.hi()[0] / 16, g.hi()[1] / 16, g.hi()[2] / 16};
            } catch (Exception e) {
                ZomboidCraft.LOGGER.warn("Couldn't read the size of mesh {}", id, e);
            }
            BOUNDS.put(id, b);
        }
        return b.length == 0 ? null : b;
    }

    private static Gun read(String id) throws IOException {
        return read(id, true);
    }

    private static Gun read(String id, boolean texture) throws IOException {
        byte[] data;
        if (id.startsWith(LOCAL)) {
            // a gun kept on this computer, not in the mod (combat/LocalGuns)
            data = java.nio.file.Files.readAllBytes(com.zomboidcraft.combat.LocalGuns.DIR.resolve(id.substring(LOCAL.length()) + ".zmesh"));
        } else if (java.nio.file.Files.isRegularFile(OVERRIDES.resolve(id + ".zmesh"))) {
            // one of ours kept differently on this computer (Arsenal's rounds for the ammo, tools/meshguns/rounds.py)
            data = java.nio.file.Files.readAllBytes(OVERRIDES.resolve(id + ".zmesh"));
        } else {
            try (InputStream in = Minecraft.getInstance().getResourceManager().open(ZomboidCraft.id("meshes/" + id + ".zmesh"))) {
                data = in.readAllBytes();
            }
        }
        // a local gun's iron sights (what its no-irons copy lacks) get no ink, as on the posing page: the outline
        // shell round their thin plates and peep rings drew black patches over them (Josiah, 2026-11-06)
        java.util.Set<String> plain = null;
        if (id.startsWith(LOCAL) && !id.endsWith(NO_IRONS)
                && java.nio.file.Files.isRegularFile(com.zomboidcraft.combat.LocalGuns.DIR.resolve(id.substring(LOCAL.length()) + NO_IRONS + ".zmesh"))) {
            try {
                plain = new HashSet<>();
                for (Part p : read(id + NO_IRONS, false).parts().values()) {
                    for (int t = 0; t < p.triangles(); t++) {
                        plain.add(key(p.pos(), t));
                    }
                }
            } catch (Exception e) {
                ZomboidCraft.LOGGER.warn("Couldn't read the no-irons copy of {}", id, e);
                plain = null;
            }
        }
        CarAssets.unscramble(data, id);
        try (DataInputStream in = new DataInputStream(new InflaterInputStream(new ByteArrayInputStream(data)))) {
            byte[] magic = in.readNBytes(4);
            if (magic[0] != 'Z' || magic[1] != 'M' || magic[2] != 'S' || magic[3] != 'H' || in.readInt() != 3) {
                throw new IOException("not a mesh file");
            }
            float outline = in.readFloat();
            byte[] png = in.readNBytes(in.readInt());
            Map<String, Part> parts = new HashMap<>();
            float[] lo = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE}, hi = {-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
            int count = in.readInt();
            for (int k = 0; k < count; k++) {
                String name = in.readUTF();
                int tris = in.readInt();
                float[] pos = new float[tris * 9], uv = new float[tris * 6], smooth = new float[tris * 9], normal = new float[tris * 3];
                for (int i = 0; i < tris * 3; i++) {
                    for (int a = 0; a < 3; a++) {
                        float v = in.readShort() / 1000f;
                        pos[i * 3 + a] = v;
                        lo[a] = Math.min(lo[a], v);
                        hi[a] = Math.max(hi[a], v);
                    }
                    uv[i * 2] = in.readUnsignedShort() / 65535f;
                    uv[i * 2 + 1] = in.readUnsignedShort() / 65535f;
                    for (int a = 0; a < 3; a++) {
                        smooth[i * 3 + a] = in.readByte() / 127f;
                    }
                }
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
                if (plain != null) {
                    for (int t = 0; t < tris; t++) {
                        if (!plain.contains(key(pos, t))) {
                            java.util.Arrays.fill(smooth, t * 9, t * 9 + 9, 0f);     // an iron sight: no ink
                        }
                    }
                }
                parts.put(name, new Part(tris, pos, uv, normal, smooth, pair(tris, pos, uv, smooth, normal)));
            }
            if (!texture) {
                return new Gun(parts, null, outline, lo, hi);
            }
            Identifier name = ZomboidCraft.id("dynamic/mesh/" + id.toLowerCase(java.util.Locale.ROOT));
            NativeImage image = NativeImage.read(new ByteArrayInputStream(png));
            Minecraft.getInstance().getTextureManager().register(name, new DynamicTexture(() -> "ZomboidCraft mesh " + id, image));
            return new Gun(parts, name, outline, lo, hi);
        }
    }

    /** A local gun's copy without its iron sights: its file name ends with this. */
    static final String NO_IRONS = "_noirons";

    /** A triangle's corners as the file has them (thousandths of an item pixel), to find it in another mesh. */
    private static String key(float[] pos, int t) {
        StringBuilder b = new StringBuilder(64);
        for (int i = t * 9; i < t * 9 + 9; i++) {
            b.append(Math.round(pos[i] * 1000)).append(',');
        }
        return b.toString();
    }

    /**
     * Pairs triangles into quads to halve the corners sent each frame (the profile, 2026-11-01: the held gun was a
     * third of the frame, and Iris adds a cost per corner). A quad a b c d draws as a b c and c d a, so triangle
     * a b c pairs with one that runs c d a: the same two triangles, drawn exactly as before. Only near-flat pairs
     * (normals within ~18 degrees) are joined, since d takes the second triangle's normal and the rest the first's.
     * Each quad is five ints: the corners a b c d and the triangle whose normal d takes; a triangle left alone
     * repeats its last corner as d.
     */
    static int[] pair(int tris, float[] pos, float[] uv, float[] smooth, float[] normal) {
        // corners that are the same point, paint and normal share an id
        int[] id = new int[tris * 3];
        Map<String, Integer> ids = new HashMap<>();
        for (int i = 0; i < tris * 3; i++) {
            String key = Float.floatToIntBits(pos[i * 3]) + "," + Float.floatToIntBits(pos[i * 3 + 1]) + ","
                    + Float.floatToIntBits(pos[i * 3 + 2]) + "," + Float.floatToIntBits(uv[i * 2]) + ","
                    + Float.floatToIntBits(uv[i * 2 + 1]) + "," + Float.floatToIntBits(smooth[i * 3]) + ","
                    + Float.floatToIntBits(smooth[i * 3 + 1]) + "," + Float.floatToIntBits(smooth[i * 3 + 2]);
            id[i] = ids.computeIfAbsent(key, k -> ids.size());
        }
        // each directed edge (from corner, to corner) -> the triangle and the corner it starts at
        Map<Long, Integer> edges = new HashMap<>();
        for (int t = 0; t < tris; t++) {
            for (int e = 0; e < 3; e++) {
                int from = id[t * 3 + e], to = id[t * 3 + (e + 1) % 3];
                edges.putIfAbsent((long) from << 32 | to & 0xFFFFFFFFL, t * 3 + e);
            }
        }
        boolean[] used = new boolean[tris];
        int[] out = new int[tris * 5];
        int n = 0;
        for (int t = 0; t < tris; t++) {
            if (used[t]) {
                continue;
            }
            used[t] = true;
            int found = -1, start = 0;
            for (int e = 0; e < 3 && found < 0; e++) {
                // the edge from corner e+2 back to e (c -> a, with a = e) is shared if a triangle runs a -> c
                int a = t * 3 + e, c = t * 3 + (e + 2) % 3;
                Integer other = edges.get((long) id[a] << 32 | id[c] & 0xFFFFFFFFL);
                if (other == null || used[other / 3]) {
                    continue;
                }
                int u = other / 3;
                float dot = normal[t * 3] * normal[u * 3] + normal[t * 3 + 1] * normal[u * 3 + 1] + normal[t * 3 + 2] * normal[u * 3 + 2];
                if (dot < 0.95f) {
                    continue;
                }
                found = other;
                start = e;
            }
            int a = t * 3 + start, b = t * 3 + (start + 1) % 3, c = t * 3 + (start + 2) % 3;
            out[n * 5] = a;
            out[n * 5 + 1] = b;
            out[n * 5 + 2] = c;
            if (found >= 0) {
                int u = found / 3;
                used[u] = true;
                // the other triangle runs a -> c -> d from corner found
                out[n * 5 + 3] = u * 3 + (found % 3 + 2) % 3;
                out[n * 5 + 4] = u;
            } else {
                out[n * 5 + 3] = c;
                out[n * 5 + 4] = t;
            }
            n++;
        }
        return java.util.Arrays.copyOf(out, n * 5);
    }

    private MeshGuns() {
    }
}
