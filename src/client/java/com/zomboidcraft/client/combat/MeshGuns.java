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
 */
public final class MeshGuns {
    /** Triangles in item pixels (muzzle toward -z), uvs, each triangle's flat normal and each corner's smooth one. */
    record Part(int triangles, float[] pos, float[] uv, float[] normal, float[] smooth) {
    }

    record Gun(Map<String, Part> parts, Identifier texture, float outline, float[] lo, float[] hi) {
    }

    private static final int INK = 0xFF141414;
    private static final Map<String, Gun> GUNS = new HashMap<>();
    private static final Set<String> MISSING = new HashSet<>();

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

    /** Draws one part of a gun with the pose at the item model's corner (one unit a block, as a block model draws). */
    static void draw(String id, String part, PoseStack pose, SubmitNodeCollector out, int light) {
        Gun gun = get(id);
        Part p = gun == null ? null : gun.parts().get(part);
        if (p == null || p.triangles() == 0) {
            return;
        }
        pose.pushPose();
        pose.scale(1 / 16f, 1 / 16f, 1 / 16f);
        out.submitCustomGeometry(pose, RenderTypes.entityCutout(gun.texture()), (at, buffer) -> solid(p, at, buffer, light));
        out.submitCustomGeometry(pose, RenderTypes.entityCutout(gun.texture()), (at, buffer) -> ink(p, gun.outline(), at, buffer, light));
        pose.popPose();
    }

    private static void solid(Part p, PoseStack.Pose pose, VertexConsumer out, int light) {
        float[] pos = p.pos(), uv = p.uv(), n = p.normal();
        for (int t = 0; t < p.triangles(); t++) {
            for (int c = 0; c < 4; c++) {
                int i = t * 3 + Math.min(c, 2);     // quads: the last corner twice
                out.addVertex(pose, pos[i * 3], pos[i * 3 + 1], pos[i * 3 + 2])
                        .setColor(0xFFFFFFFF)
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
        for (int t = 0; t < p.triangles(); t++) {
            for (int c = 0; c < 4; c++) {
                int i = t * 3 + 2 - Math.min(c, 2);
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

    private static Gun read(String id) throws IOException {
        byte[] data;
        try (InputStream in = Minecraft.getInstance().getResourceManager().open(ZomboidCraft.id("meshes/" + id + ".zmesh"))) {
            data = in.readAllBytes();
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
                parts.put(name, new Part(tris, pos, uv, normal, smooth));
            }
            Identifier texture = ZomboidCraft.id("dynamic/mesh/" + id);
            NativeImage image = NativeImage.read(new ByteArrayInputStream(png));
            Minecraft.getInstance().getTextureManager().register(texture, new DynamicTexture(() -> "ZomboidCraft gun " + id, image));
            return new Gun(parts, texture, outline, lo, hi);
        }
    }

    private MeshGuns() {
    }
}
