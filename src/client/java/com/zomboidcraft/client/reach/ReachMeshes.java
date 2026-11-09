package com.zomboidcraft.client.reach;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.IndexType;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuSampler;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import com.mojang.renderpearl.api.vertex.VertexFormat;
import com.zomboidcraft.ZomboidCraft;
import com.zomboidcraft.client.perf.GpuTimer;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Stream;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.rendertype.PreparedRenderType;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * Draws the Halo levels of the Reach world: the real level meshes and their textures, read straight from the converted
 * files on this computer (never shipped in the mod). Each level is uploaded to the graphics card once, as one vertex
 * buffer and one index buffer, and drawn every frame inside the game's own solid pass with the game's own entity
 * pipeline, so lighting, fog and shader packs treat it like any other solid thing. The invisible walls you walk on
 * come from the same level's collision (see ReachSolid).
 *
 * <p>config/zomboidcraft/reach/reach.json lists them: {"levels": [{"dir": "C:\\...\\world\\m20", "offset": [x, y, z],
 * "yaw": 0}]}. Every .glb in that folder is drawn (a .glb split into .part00, .part01... is joined back up).
 */
public final class ReachMeshes {
    private static final Path DIR = FabricLoader.getInstance().getConfigDir().resolve("zomboidcraft").resolve("reach");
    private static final int FULL_LIGHT = 0xF000F0;

    /** A level piece read from disk, waiting for the render thread to upload it. */
    private record Loaded(String name, ReachGlb.Level level, NativeImage[] images, float ox, float oy, float oz) {
    }

    /** One level piece on the graphics card. */
    private static final class Uploaded {
        String name;
        GpuBuffer vertices, indices;
        double ox, oy, oz;
        Identifier[] textures;
        GpuTextureView[] views;
        boolean[] blend;
        final List<Draw> draws = new ArrayList<>();
        int triangles;
    }

    /** One batch: a material, a run of indices and the box it fills (for culling). */
    private record Draw(int material, int indexCount, int firstIndex, int baseVertex, AABB box) {
    }

    private static final ConcurrentLinkedQueue<Loaded> READY = new ConcurrentLinkedQueue<>();
    private static final List<Uploaded> LEVELS = new ArrayList<>();
    private static volatile boolean loading;
    private static boolean loadedOnce;
    private static Identifier white;

    /** Set while collecting a frame in the Reach world, for the solid pass that follows. */
    private static boolean armed;
    private static Vec3 eye;
    private static Frustum frustum;
    private static int drawnTriangles;

    private ReachMeshes() {
    }

    public static void register() {
        LevelRenderEvents.COLLECT_SUBMITS.register(ReachMeshes::arm);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(ReachMeshes::unload));
    }

    /** Whether this client world is the Reach world (its own dimension type: 2560 tall from -1024). */
    public static boolean inReach(ClientLevel level) {
        return level != null && level.getMinY() == -1024 && level.getHeight() == 2560;
    }

    public static String status() {
        int tris = 0;
        for (Uploaded u : LEVELS) {
            tris += u.triangles;
        }
        return LEVELS.size() + " level pieces, " + tris + " triangles, " + drawnTriangles + " drawn last frame"
                + (loading ? " (still loading)" : "");
    }

    private static void arm(LevelRenderContext context) {
        Minecraft mc = Minecraft.getInstance();
        armed = false;
        if (!inReach(mc.level)) {
            return;
        }
        if (!loadedOnce && !loading) {
            loadedOnce = true;
            startLoading();
        }
        uploadReady();
        CameraRenderState cam = context.levelState().cameraRenderState;
        if (cam == null || cam.pos == null || LEVELS.isEmpty()) {
            return;
        }
        eye = cam.pos;
        frustum = cam.cullFrustum;
        armed = true;
    }

    /** Called at the end of the game's solid pass (LevelRenderer.executeSolid) with that pass still open. */
    public static void drawSolid(RenderPass pass) {
        if (!armed || GpuTimer.inShadow) {
            return;
        }
        armed = false;
        drawnTriangles = 0;
        try {
            RenderType type = RenderTypes.entityCutout(white);
            PreparedRenderType prepared = type.prepare();
            pass.setPipeline(RenderSystem.getCompiledPipeline(prepared.pipeline()));
            RenderSystem.bindDefaultUniforms(pass);
            for (PreparedRenderType.Texture t : prepared.textures()) {
                pass.setUniform(t.name(), t.textureView(), t.sampler());
            }
            GpuSampler repeat = RenderSystem.getSamplerCache().getRepeat(FilterMode.LINEAR);
            Matrix4f view = RenderSystem.getModelViewMatrixCopy();
            for (Uploaded u : LEVELS) {
                Matrix4f mv = new Matrix4f(view).translate((float) (u.ox - eye.x), (float) (u.oy - eye.y), (float) (u.oz - eye.z));
                GpuBufferSlice transform = RenderSystem.getDynamicUniforms().writeTransform(mv, new Matrix4f());
                pass.setUniform("DynamicTransforms", transform);
                pass.setVertexBuffer(0, u.vertices.slice());
                pass.setIndexBuffer(u.indices, IndexType.INT);
                int bound = -1;
                for (Draw d : u.draws) {
                    if (u.blend[d.material] || (frustum != null && !frustum.isVisible(d.box))) {
                        continue;
                    }
                    if (d.material != bound) {
                        pass.setUniform("Sampler0", u.views[d.material], repeat);
                        bound = d.material;
                    }
                    pass.drawIndexed(d.indexCount, 1, d.firstIndex, d.baseVertex, 0);
                    drawnTriangles += d.indexCount / 3;
                }
            }
        } catch (RuntimeException e) {
            ZomboidCraft.LOGGER.error("Reach world: drawing the levels failed, turning them off", e);
            unload();
        }
    }

    // ---------------------------------------------------------------- loading

    private static void startLoading() {
        Path cfg = DIR.resolve("reach.json");
        if (!Files.isRegularFile(cfg)) {
            ZomboidCraft.LOGGER.info("Reach world: no {} so no level meshes to draw", cfg);
            return;
        }
        loading = true;
        Thread t = new Thread(() -> {
            try {
                JsonObject json = JsonParser.parseString(Files.readString(cfg)).getAsJsonObject();
                JsonArray levels = json.has("levels") ? json.getAsJsonArray("levels") : new JsonArray();
                for (JsonElement e : levels) {
                    JsonObject l = e.getAsJsonObject();
                    Path dir = Path.of(l.get("dir").getAsString());
                    float yaw = l.has("yaw") ? l.get("yaw").getAsFloat() : 0f;
                    JsonArray off = l.has("offset") ? l.getAsJsonArray("offset") : null;
                    float ox = off == null ? 0 : off.get(0).getAsFloat(), oy = off == null ? 0 : off.get(1).getAsFloat(),
                            oz = off == null ? 0 : off.get(2).getAsFloat();
                    for (Path glb : pieces(dir)) {
                        try {
                            READY.add(read(glb, yaw, ox, oy, oz));
                        } catch (IOException | RuntimeException ex) {
                            ZomboidCraft.LOGGER.warn("Reach world: couldn't read {}: {}", glb, ex.toString());
                        }
                    }
                }
            } catch (IOException | RuntimeException ex) {
                ZomboidCraft.LOGGER.warn("Reach world: couldn't read {}: {}", cfg, ex.toString());
            } finally {
                loading = false;
            }
        }, "ZomboidCraft Reach meshes");
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY);
        t.start();
    }

    /** The .glb files of a level folder: whole ones, and split ones (name.glb.part00...) joined into a temp file. */
    private static List<Path> pieces(Path dir) throws IOException {
        List<Path> out = new ArrayList<>();
        if (!Files.isDirectory(dir)) {
            if (Files.isRegularFile(dir)) {
                out.add(dir);
            } else {
                ZomboidCraft.LOGGER.warn("Reach world: level folder {} isn't there", dir);
            }
            return out;
        }
        List<Path> all;
        try (Stream<Path> s = Files.list(dir)) {
            all = s.sorted(Comparator.comparing(Path::toString)).toList();
        }
        for (Path p : all) {
            String n = p.getFileName().toString();
            if (n.endsWith(".glb")) {
                out.add(p);
            } else if (n.endsWith(".glb.part00") && !Files.exists(dir.resolve(n.substring(0, n.length() - 7)))) {
                String base = n.substring(0, n.length() - 7);
                ByteArrayOutputStream joined = new ByteArrayOutputStream();
                for (int i = 0; ; i++) {
                    Path part = dir.resolve(String.format("%s.part%02d", base, i));
                    if (!Files.isRegularFile(part)) {
                        break;
                    }
                    joined.write(Files.readAllBytes(part));
                }
                Path tmp = Files.createTempFile("reach-" + base, ".glb");
                tmp.toFile().deleteOnExit();
                Files.write(tmp, joined.toByteArray());
                out.add(tmp);
            }
        }
        return out;
    }

    private static Loaded read(Path glb, float yaw, float ox, float oy, float oz) throws IOException {
        ReachGlb.Level level = ReachGlb.read(glb, yaw, ox, oy, oz);
        NativeImage[] images = new NativeImage[level.materials().size()];
        for (int m = 0; m < images.length; m++) {
            ReachGlb.Material mat = level.materials().get(m);
            if (mat.png() == null) {
                continue;
            }
            try {
                NativeImage img = NativeImage.read(mat.png());
                if (!mat.cutout() && !mat.blend()) {
                    // an opaque Halo surface keeps other things (its shine) in the picture's alpha: make it solid
                    for (int y = 0; y < img.getHeight(); y++) {
                        for (int x = 0; x < img.getWidth(); x++) {
                            img.setPixel(x, y, img.getPixel(x, y) | 0xFF000000);
                        }
                    }
                }
                images[m] = img;
            } catch (IOException | RuntimeException e) {
                images[m] = null;
            }
        }
        ZomboidCraft.LOGGER.info("Reach world: read {} ({} triangles, {} materials)", glb.getFileName(), level.triangles(),
                level.materials().size());
        return new Loaded(glb.getFileName().toString(), level, images, ox, oy, oz);
    }

    // ---------------------------------------------------------------- upload (render thread)

    private static void uploadReady() {
        Loaded l;
        int budget = 2;   // a couple of pieces a frame, so joining the world doesn't stall
        while (budget-- > 0 && (l = READY.poll()) != null) {
            try {
                LEVELS.add(upload(l));
            } catch (RuntimeException e) {
                ZomboidCraft.LOGGER.warn("Reach world: couldn't upload {}: {}", l.name(), e.toString());
            }
        }
    }

    private static Identifier whiteTexture() {
        if (white == null) {
            NativeImage img = new NativeImage(4, 4, false);
            img.fillRect(0, 0, 4, 4, 0xFFFFFFFF);
            white = ZomboidCraft.id("reach/white");
            Minecraft.getInstance().getTextureManager().register(white, new DynamicTexture(() -> "Reach white", img));
        }
        return white;
    }

    private static Uploaded upload(Loaded l) {
        Minecraft mc = Minecraft.getInstance();
        Identifier blank = whiteTexture();
        Uploaded u = new Uploaded();
        u.name = l.name();
        u.ox = l.ox();
        u.oy = l.oy();
        u.oz = l.oz();
        List<ReachGlb.Material> mats = l.level().materials();
        u.textures = new Identifier[mats.size()];
        u.views = new GpuTextureView[mats.size()];
        u.blend = new boolean[mats.size()];
        String key = Integer.toHexString(l.name().hashCode() & 0x7fffffff);
        for (int m = 0; m < mats.size(); m++) {
            Identifier id = blank;
            if (l.images()[m] != null) {
                id = ZomboidCraft.id("reach/" + key + "/" + m);
                String label = "Reach " + l.name() + " " + mats.get(m).name();
                mc.getTextureManager().register(id, new DynamicTexture(() -> label, l.images()[m]));
            }
            u.textures[m] = id;
            u.views[m] = mc.getTextureManager().getTexture(id).getTextureView();
            u.blend[m] = mats.get(m).blend();
        }

        // vertices relative to the level's offset (kept small so floats stay sharp), in the entity vertex layout
        VertexFormat format = RenderTypes.entityCutout(blank).format();
        int vertexCount = 0, indexCount = 0;
        List<ReachGlb.Batch> batches = new ArrayList<>(l.level().batches());
        batches.sort(Comparator.comparingInt(b -> b.material));
        for (ReachGlb.Batch b : batches) {
            vertexCount += b.vertices();
            indexCount += b.index.length;
        }
        int padded = (vertexCount + 3) / 4 * 4;
        ByteBufferBuilder bytes = ByteBufferBuilder.exactlySized(Math.max(64, padded * format.getVertexSize()));
        BufferBuilder out = new BufferBuilder(bytes, RenderTypes.entityCutout(blank).primitiveTopology(), format);
        ByteBuffer index = ByteBuffer.allocateDirect(Math.max(4, indexCount * 4)).order(ByteOrder.nativeOrder());
        int baseVertex = 0, firstIndex = 0;
        for (ReachGlb.Batch b : batches) {
            ReachGlb.Material mat = mats.get(b.material);
            int tint = mat.png() == null ? mat.color() : mat.color() | 0xFF000000;
            for (int i = 0; i < b.vertices(); i++) {
                int c = multiply(b.color[i], tint);
                out.addVertex(b.pos[i * 3] - l.ox(), b.pos[i * 3 + 1] - l.oy(), b.pos[i * 3 + 2] - l.oz(), c,
                        b.uv[i * 2], b.uv[i * 2 + 1], OverlayTexture.NO_OVERLAY, FULL_LIGHT,
                        b.normal[i * 3], b.normal[i * 3 + 1], b.normal[i * 3 + 2]);
            }
            for (int ix : b.index) {
                index.putInt(ix);
            }
            u.draws.add(new Draw(b.material, b.index.length, firstIndex, baseVertex,
                    new AABB(b.minX, b.minY, b.minZ, b.maxX, b.maxY, b.maxZ)));
            u.triangles += b.index.length / 3;
            baseVertex += b.vertices();
            firstIndex += b.index.length;
        }
        for (int i = vertexCount; i < padded; i++) {
            out.addVertex(0, 0, 0, 0, 0, 0, OverlayTexture.NO_OVERLAY, FULL_LIGHT, 0, 1, 0);
        }
        try (MeshData mesh = out.buildOrThrow()) {
            u.vertices = RenderSystem.getDevice().createBuffer(() -> "Reach " + l.name() + " vertices", GpuBuffer.USAGE_VERTEX,
                    mesh.vertexBuffer());
        } finally {
            bytes.close();
        }
        index.flip();
        u.indices = RenderSystem.getDevice().createBuffer(() -> "Reach " + l.name() + " indices", GpuBuffer.USAGE_INDEX, index);
        ZomboidCraft.LOGGER.info("Reach world: {} on the graphics card ({} triangles)", l.name(), u.triangles);
        return u;
    }

    private static int multiply(int a, int b) {
        int out = 0;
        for (int s = 0; s < 32; s += 8) {
            int x = (a >>> s) & 0xFF, y = (b >>> s) & 0xFF;
            out |= (x * y / 255) << s;
        }
        return out;
    }

    private static void unload() {
        armed = false;
        Minecraft mc = Minecraft.getInstance();
        for (Uploaded u : LEVELS) {
            if (u.vertices != null) {
                u.vertices.close();
            }
            if (u.indices != null) {
                u.indices.close();
            }
            for (Identifier id : u.textures) {
                if (id != null && !id.equals(white)) {
                    mc.getTextureManager().release(id);
                }
            }
        }
        LEVELS.clear();
        READY.clear();
        loadedOnce = false;
    }
}
