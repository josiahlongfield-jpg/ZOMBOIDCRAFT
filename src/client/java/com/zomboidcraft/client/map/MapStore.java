package com.zomboidcraft.client.map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.blaze3d.platform.NativeImage;
import com.zomboidcraft.ZomboidCraft;
import com.zomboidcraft.world.map.MapNet;
import com.zomboidcraft.world.map.MapPalette;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.material.MapColor;
import org.jetbrains.annotations.Nullable;

/**
 * Your own map of the world, kept on this computer per world and dimension: what you've seen, painted a block a pixel
 * in 256-block tiles as you go, plus whatever the maps you've read showed (a county-wide layer at 32 blocks a pixel
 * for road atlases), and the pins you've dropped. Unpainted pixels stay clear: the map page draws them as fog.
 */
public final class MapStore {
    public static final int TILE = 256;
    /** The county layer: 512 pixels of 32 blocks, centred on 0, 0. */
    public static final int BASE = 512, BASE_SCALE = 32, BASE_HALF = BASE * BASE_SCALE / 2;
    /** Alpha of a pixel you've seen yourself; a map's ink has one less, so seeing it paints over it. */
    private static final int SEEN = 0xFF, INK = 0xFE;
    private static final int EXPLORE = 40, REFRESH = 10;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public enum PinKind {
        SAFE("Safehouse", 0xFFE8E4DA, 0xFF1B1A17), LOOT("Loot", 0xFF5D6A34, 0xFFF2EFE8),
        DANGER("Danger", 0xFF8A2A22, 0xFFF2EFE8), NOTE("Note", 0xFF4A6D97, 0xFFF2EFE8);

        public final String label;
        public final int bg, fg;

        PinKind(String label, int bg, int fg) {
            this.label = label;
            this.bg = bg;
            this.fg = fg;
        }
    }

    public static final class Pin {
        public PinKind kind;
        public int x, z;
        public String name;

        public Pin(PinKind kind, int x, int z, String name) {
            this.kind = kind;
            this.x = x;
            this.z = z;
            this.name = name;
        }
    }

    private static final class Saved {
        List<Pin> pins = new ArrayList<>();
        List<String> read = new ArrayList<>();
    }

    public static final class Tile {
        final NativeImage image;
        final Identifier id;
        DynamicTexture texture;
        boolean upload, save;

        Tile(NativeImage image, Identifier id) {
            this.image = image;
            this.id = id;
        }

        public Identifier texture() {
            if (texture == null) {
                texture = new DynamicTexture(() -> "ZomboidCraft map " + id, image);
                Minecraft.getInstance().getTextureManager().register(id, texture);
                upload = false;
            } else if (upload) {
                texture.upload();
                upload = false;
            }
            return id;
        }
    }

    private static @Nullable ClientLevel level;
    private static @Nullable Path folder;
    private static final Map<Long, Tile> tiles = new HashMap<>();
    /** Tiles with no file yet, so drawing the map doesn't look for them every frame. */
    private static final java.util.Set<Long> missing = new java.util.HashSet<>();
    private static @Nullable Tile base;
    private static boolean noBase;
    private static Saved saved = new Saved();
    private static boolean savedDirty;
    private static int ticks;

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(MapStore::tick);
        ClientPlayNetworking.registerGlobalReceiver(MapNet.Reveal.TYPE, (payload, context) ->
                context.client().execute(() -> reveal(payload)));
    }

    private static void tick(Minecraft mc) {
        if (mc.level != level) {
            save();
            close();
            level = mc.level;
            if (level != null) open(mc, level);
        }
        LocalPlayer player = mc.player;
        if (level == null || player == null) return;
        ticks++;
        if (ticks % 10 == 0) explore(level, player.getBlockX(), player.getBlockZ());
        if (ticks % 600 == 0) save();
    }

    // ------------------------------------------------------------------ the world's folder

    private static void open(Minecraft mc, ClientLevel level) {
        String world;
        MinecraftServer server = mc.getSingleplayerServer();
        ServerData data = mc.getCurrentServer();
        if (server != null) world = "local_" + server.getWorldData().getLevelName();
        else if (data != null) world = "server_" + data.ip;
        else world = "unknown";
        String dim = level.dimension().identifier().toString();
        folder = FabricLoader.getInstance().getConfigDir().resolve("zomboidcraft").resolve("maps")
                .resolve(safe(world)).resolve(safe(dim));
        saved = new Saved();
        try {
            Path pins = folder.resolve("map.json");
            if (Files.exists(pins)) {
                Saved s = GSON.fromJson(Files.readString(pins, StandardCharsets.UTF_8), Saved.class);
                if (s != null) {
                    if (s.pins == null) s.pins = new ArrayList<>();
                    if (s.read == null) s.read = new ArrayList<>();
                    s.pins.removeIf(p -> p == null || p.kind == null);
                    saved = s;
                }
            }
        } catch (IOException | RuntimeException e) {
            ZomboidCraft.LOGGER.warn("Couldn't read your map's pins", e);
        }
    }

    private static String safe(String s) {
        return s.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9._-]", "_");
    }

    private static void close() {
        var textures = Minecraft.getInstance().getTextureManager();
        for (Tile t : tiles.values()) {
            if (t.texture != null) textures.release(t.id);
            else t.image.close();
        }
        tiles.clear();
        missing.clear();
        noBase = false;
        if (base != null) {
            if (base.texture != null) textures.release(base.id);
            else base.image.close();
        }
        base = null;
        folder = null;
    }

    /** Writes changed tiles and the pins. */
    public static void save() {
        if (folder == null) return;
        try {
            Files.createDirectories(folder);
            for (Map.Entry<Long, Tile> e : tiles.entrySet()) {
                Tile t = e.getValue();
                if (!t.save) continue;
                t.image.writeToFile(folder.resolve("tile_" + (int) (e.getKey() >> 32) + "_" + (int) (long) e.getKey() + ".png"));
                t.save = false;
            }
            if (base != null && base.save) {
                base.image.writeToFile(folder.resolve("county.png"));
                base.save = false;
            }
            if (savedDirty) {
                Files.writeString(folder.resolve("map.json"), GSON.toJson(saved), StandardCharsets.UTF_8);
                savedDirty = false;
            }
        } catch (IOException | RuntimeException e) {
            ZomboidCraft.LOGGER.warn("Couldn't save your map", e);
        }
    }

    // ------------------------------------------------------------------ tiles

    private static long key(int tx, int tz) {
        return (long) tx << 32 | (tz & 0xFFFFFFFFL);
    }

    /** The tile holding a block, made (or loaded) when {@code make}; null if there's none. */
    public static @Nullable Tile tile(int tx, int tz, boolean make) {
        long k = key(tx, tz);
        Tile t = tiles.get(k);
        if (t != null || folder == null) return t;
        if (!make && missing.contains(k)) return null;
        Path file = folder.resolve("tile_" + tx + "_" + tz + ".png");
        NativeImage image = null;
        if (!missing.contains(k) && Files.exists(file)) image = read(file);
        if (image == null) {
            missing.add(k);
            // an empty tile is only made when something is painted on it
            if (!make) return null;
            image = new NativeImage(TILE, TILE, true);
        }
        missing.remove(k);
        t = new Tile(image, ZomboidCraft.id("map_tile/" + tx + "_" + tz));
        tiles.put(k, t);
        return t;
    }

    public static @Nullable Tile base(boolean make) {
        if (base != null || folder == null) return base;
        if (noBase && !make) return null;
        Path file = folder.resolve("county.png");
        NativeImage image = !noBase && Files.exists(file) ? read(file) : null;
        noBase = image == null;
        if (image == null && !make) return null;
        if (image == null) image = new NativeImage(BASE, BASE, true);
        base = new Tile(image, ZomboidCraft.id("map_tile/county"));
        return base;
    }

    private static @Nullable NativeImage read(Path file) {
        try (InputStream in = Files.newInputStream(file)) {
            return NativeImage.read(in);
        } catch (IOException | RuntimeException e) {
            ZomboidCraft.LOGGER.warn("Couldn't read map tile {}", file, e);
            return null;
        }
    }

    // ------------------------------------------------------------------ painting

    /** Paints what's around you: anything new within EXPLORE blocks, and everything close by again. */
    private static void explore(ClientLevel level, int px, int pz) {
        for (int dz = -EXPLORE; dz <= EXPLORE; dz++) {
            for (int dx = -EXPLORE; dx <= EXPLORE; dx++) {
                int d2 = dx * dx + dz * dz;
                if (d2 > EXPLORE * EXPLORE) continue;
                int x = px + dx, z = pz + dz;
                Tile t = tile(Math.floorDiv(x, TILE), Math.floorDiv(z, TILE), true);
                if (t == null) return;
                int lx = Math.floorMod(x, TILE), lz = Math.floorMod(z, TILE);
                if (d2 > REFRESH * REFRESH && (t.image.getPixel(lx, lz) >>> 24) == SEEN) continue;
                int colour = colour(level, x, z);
                if (colour == 0) continue;
                if (t.image.getPixel(lx, lz) != colour) {
                    t.image.setPixel(lx, lz, colour);
                    t.upload = true;
                    t.save = true;
                }
            }
        }
    }

    /** A column's colour from above, the way a paper map would shade it; 0 if its chunk isn't here. */
    private static int colour(ClientLevel level, int x, int z) {
        BlockPos probe = new BlockPos(x, 0, z);
        if (!level.isLoaded(probe)) return 0;
        int y = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
        if (y <= level.getMinY()) return 0;
        BlockPos pos = new BlockPos(x, y, z);
        BlockState state = level.getBlockState(pos);
        FluidState fluid = state.getFluidState();
        int rgb;
        float shade = 1;
        if (fluid.is(Fluids.WATER) || fluid.is(Fluids.FLOWING_WATER)) {
            int depth = 0;
            while (depth < 10 && level.getBlockState(pos.below(depth + 1)).getFluidState().is(Fluids.WATER)) depth++;
            rgb = depth > 4 ? MapPalette.argb(MapPalette.DEEP) : MapPalette.argb(MapPalette.WATER);
        } else {
            MapColor map = state.getMapColor(level, pos);
            rgb = map == MapColor.NONE ? 0x7A7670 : map.col;
            // light from the north-west, like a printed relief
            int north = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z - 1) - 1;
            shade = y > north ? 1.1f : y < north ? 0.84f : 1f;
        }
        // printed on paper: a third of the way to the paper's colour
        int r = mix(rgb >> 16 & 255, 0xD8, shade), gr = mix(rgb >> 8 & 255, 0xCF, shade), b = mix(rgb & 255, 0xB8, shade);
        return SEEN << 24 | r << 16 | gr << 8 | b;
    }

    private static int mix(int c, int paper, float shade) {
        return Math.max(0, Math.min(255, Math.round((c * 0.66f + paper * 0.34f) * shade)));
    }

    /** A map you read: its cells fill in wherever you haven't seen for yourself. */
    public static void reveal(MapNet.Reveal map) {
        if (folder == null) return;
        if (!saved.read.contains(map.title())) {
            saved.read.add(map.title());
            savedDirty = true;
        }
        if (map.step() >= BASE_SCALE / 2) {
            Tile t = base(true);
            if (t == null) return;
            int every = Math.max(1, BASE_SCALE / map.step());
            for (int j = 0; j < map.h(); j += every) {
                for (int i = 0; i < map.w(); i += every) {
                    byte cell = map.cells()[j * map.w() + i];
                    if (cell == MapPalette.NONE) continue;
                    int bx = Math.floorDiv(map.x0() + i * map.step() + BASE_HALF, BASE_SCALE);
                    int bz = Math.floorDiv(map.z0() + j * map.step() + BASE_HALF, BASE_SCALE);
                    if (bx < 0 || bz < 0 || bx >= BASE || bz >= BASE) continue;
                    t.image.setPixel(bx, bz, ink(cell));
                }
            }
            // a road is thinner than a cell: draw it again over whatever was sampled
            for (int j = 0; j < map.h(); j++) {
                for (int i = 0; i < map.w(); i++) {
                    byte cell = map.cells()[j * map.w() + i];
                    if (cell != MapPalette.HIGHWAY && cell != MapPalette.ROAD) continue;
                    int bx = Math.floorDiv(map.x0() + i * map.step() + BASE_HALF, BASE_SCALE);
                    int bz = Math.floorDiv(map.z0() + j * map.step() + BASE_HALF, BASE_SCALE);
                    if (bx >= 0 && bz >= 0 && bx < BASE && bz < BASE) t.image.setPixel(bx, bz, ink(cell));
                }
            }
            t.upload = true;
            t.save = true;
            return;
        }
        for (int j = 0; j < map.h(); j++) {
            for (int i = 0; i < map.w(); i++) {
                byte cell = map.cells()[j * map.w() + i];
                if (cell == MapPalette.NONE) continue;
                int ink = ink(cell);
                for (int sz = 0; sz < map.step(); sz++) {
                    for (int sx = 0; sx < map.step(); sx++) {
                        int x = map.x0() + i * map.step() + sx, z = map.z0() + j * map.step() + sz;
                        Tile t = tile(Math.floorDiv(x, TILE), Math.floorDiv(z, TILE), true);
                        if (t == null) return;
                        int lx = Math.floorMod(x, TILE), lz = Math.floorMod(z, TILE);
                        if ((t.image.getPixel(lx, lz) >>> 24) == SEEN && cell != MapPalette.MARK) continue;
                        t.image.setPixel(lx, lz, ink);
                        t.upload = true;
                        t.save = true;
                    }
                }
            }
        }
    }

    private static int ink(byte cell) {
        return INK << 24 | MapPalette.argb(cell) & 0xFFFFFF;
    }

    // ------------------------------------------------------------------ pins and maps read

    public static List<Pin> pins() {
        return saved.pins;
    }

    public static void pin(PinKind kind, int x, int z) {
        int n = 1;
        for (Pin p : saved.pins) if (p.kind == kind) n++;
        saved.pins.add(new Pin(kind, x, z, kind.label + " " + n));
        savedDirty = true;
    }

    public static void unpin(Pin pin) {
        if (saved.pins.remove(pin)) savedDirty = true;
    }

    public static List<String> read() {
        return saved.read;
    }

    private MapStore() {
    }
}
