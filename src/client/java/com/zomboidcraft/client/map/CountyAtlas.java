package com.zomboidcraft.client.map;

import com.mojang.blaze3d.platform.NativeImage;
import com.zomboidcraft.ZomboidCraft;
import com.zomboidcraft.world.county.County;
import com.zomboidcraft.world.county.CountyGen;
import com.zomboidcraft.world.county.Place;
import com.zomboidcraft.world.county.PlaceCache;
import com.zomboidcraft.world.county.PlaceType;
import com.zomboidcraft.world.county.Road;
import com.zomboidcraft.world.county.Terrain;
import com.zomboidcraft.world.county.Voxels;
import com.zomboidcraft.world.gen.CountyChunkGenerator;
import com.zomboidcraft.world.map.MapNet;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.MapColor;
import org.jetbrains.annotations.Nullable;

/**
 * The whole county as a map, worked out on your own computer from the county the world was generated from (the server
 * sends its seed, {@link MapNet.Atlas}), so it shows every street, roof and wood without anyone having been there.
 *
 * <p>Four levels of 256-pixel tiles: a block a pixel, then 4, 16 and 64 blocks a pixel (the last is the whole county
 * in one tile). The two close levels read every place's buildings column by column, the colour of the top block as a
 * Minecraft map would show it, shaded for height. The two far-out levels are shrunk from the second (Josiah
 * 2026-10-29: as much detail zoomed out as in), a quick sketch of land, water and roads standing in for whatever
 * isn't drawn yet. The second level fills in for the whole county in the background, nearest you first, and every
 * finished tile is kept on disk per county and mod version.
 */
public final class CountyAtlas {
    public static final int PX = 256;
    /** Blocks a pixel at each level. */
    public static final int[] SCALE = {1, 4, 16, 64};
    public static final int LEVELS = SCALE.length;
    /** The county's half width; nothing is drawn past it. */
    public static final int HALF = 8192;
    /** Loaded tiles kept per level before the least recently drawn is let go. */
    private static final int[] KEEP = {160, 120, 64, 4};
    private static final int THREADS = 2;

    private static volatile @Nullable State state;
    private static final Map<Long, Tile> loaded = new LinkedHashMap<>(256, 0.75f, true);
    private static final ConcurrentLinkedQueue<Ready> ready = new ConcurrentLinkedQueue<>();

    private record Ready(long key, NativeImage image) {
    }

    /** What the page draws: a tile's texture once it's on the card. */
    public static final class Tile {
        final NativeImage image;
        final Identifier id;
        @Nullable DynamicTexture texture;

        Tile(NativeImage image, Identifier id) {
            this.image = image;
            this.id = id;
        }

        public Identifier texture() {
            if (texture == null) {
                texture = new DynamicTexture(() -> "ZomboidCraft county map " + id, image);
                Minecraft.getInstance().getTextureManager().register(id, texture);
            }
            return id;
        }
    }

    /** One county's atlas: its generator, its folder and the tiles being worked on. */
    private static final class State {
        final long seed;
        final boolean planned;
        final Path folder;
        volatile @Nullable CountyGen gen;
        final Set<Long> onDisk = ConcurrentHashMap.newKeySet();
        final Set<Long> working = ConcurrentHashMap.newKeySet();
        /** Tiles asked for, newest first; the page asks every frame for what it shows. */
        final Deque<Long> asked = new ArrayDeque<>();
        /** Background work once nothing's been asked for: the sketch levels, then the county near you. */
        final Deque<Long> fill = new ArrayDeque<>();
        final AtomicLong done = new AtomicLong();
        /** Each finished second-level tile shrunk to a quarter, for the far-out levels to be built from. */
        final Map<Long, int[]> small = new ConcurrentHashMap<>();
        /** The far-out levels' sketches, kept while their detail fills in. */
        final Map<Long, int[]> sketches = new ConcurrentHashMap<>();
        volatile boolean stop;

        State(long seed, boolean planned, Path folder) {
            this.seed = seed;
            this.planned = planned;
            this.folder = folder;
        }
    }

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(MapNet.Atlas.TYPE, (payload, context) ->
                context.client().execute(() -> open(payload.seed(), payload.planned())));
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.DISCONNECT.register((handler, client) ->
                client.execute(CountyAtlas::close));
        // the close levels fill in round you while you play, so the map is ready when you open it
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            if (state != null && mc.player != null && mc.player.tickCount % 100 == 0) around(mc.player.getX(), mc.player.getZ());
        });
    }

    /** Whether this world has a county map at all. */
    public static boolean active() {
        return state != null;
    }

    /** How many tiles have been worked out this session, for the page's footnote. */
    public static long made() {
        State s = state;
        return s == null ? 0 : s.done.get();
    }

    /** Whether it's the hand-planned county, whose places have names. */
    public static boolean planned() {
        State s = state;
        return s != null && s.planned;
    }

    /** Whether a tile has been drawn and is ready to show. */
    public static boolean ready(int level, double x, double z) {
        int span = PX * SCALE[level];
        return loaded.containsKey(key(level, (int) Math.floor((x + HALF) / span), (int) Math.floor((z + HALF) / span)));
    }

    /** Whether the far-out sketch is still being made. */
    public static boolean sketching() {
        State s = state;
        return s != null && (s.gen == null || !s.onDisk.contains(key(LEVELS - 1, 0, 0)));
    }

    // ------------------------------------------------------------------ opening and closing

    /** When this jar was made, in seconds; 0 when it can't be told. */
    private static long built(net.fabricmc.loader.api.ModContainer mod) {
        long sum = 0;
        try {
            for (Path p : mod.getOrigin().getPaths()) sum += Files.getLastModifiedTime(p).toMillis() / 1000;
        } catch (IOException | RuntimeException e) {
            return 0;
        }
        return sum;
    }

    /** Opens a county's atlas (the server's {@link MapNet.Atlas} does this; the UI test opens one directly). */
    public static void open(long seed, boolean planned) {
        State old = state;
        if (old != null && old.seed == seed && old.planned == planned) return;
        close();
        // every playtest jar shares one version number, so the jar's own date marks a new build as well
        String version = FabricLoader.getInstance().getModContainer(ZomboidCraft.MOD_ID)
                .map(m -> m.getMetadata().getVersion().getFriendlyString() + "_" + built(m)).orElse("dev");
        Path root = FabricLoader.getInstance().getConfigDir().resolve("zomboidcraft").resolve("atlas");
        String name = Long.toHexString(seed) + (planned ? "_plan" : "_endless");
        Path folder = root.resolve(name + "_" + version.replaceAll("[^A-Za-z0-9._-]", "_"));
        // a new mod version may have built the county differently: its old drawings go
        try (var others = Files.list(root)) {
            others.filter(p -> p.getFileName().toString().startsWith(name + "_") && !p.equals(folder)).forEach(CountyAtlas::delete);
        } catch (IOException ignored) {
            // nothing there yet
        }
        State s = new State(seed, planned, folder);
        try {
            Files.createDirectories(folder);
            try (var files = Files.list(folder)) {
                files.forEach(f -> {
                    long k = parse(f.getFileName().toString());
                    if (k != Long.MIN_VALUE) s.onDisk.add(k);
                });
            }
        } catch (IOException e) {
            ZomboidCraft.LOGGER.warn("Couldn't open the county map folder", e);
        }
        // the whole county first, then the sketch level below it, nearest the middle first
        s.fill.add(key(3, 0, 0));
        int n2 = 2 * HALF / (PX * SCALE[2]);
        for (int r = 0; r < n2; r++) {
            for (int tz = 0; tz < n2; tz++) {
                for (int tx = 0; tx < n2; tx++) {
                    if (Math.max(Math.abs(tx * 2 + 1 - n2), Math.abs(tz * 2 + 1 - n2)) / 2 == r) s.fill.add(key(2, tx, tz));
                }
            }
        }
        state = s;
        for (int i = 0; i < THREADS; i++) {
            Thread t = new Thread(() -> work(s), "ZomboidCraft county map " + i);
            t.setDaemon(true);
            t.setPriority(Thread.MIN_PRIORITY);
            t.start();
        }
        ZomboidCraft.LOGGER.info("County map for seed {} ({} tiles already drawn)", seed, s.onDisk.size());
    }

    private static void close() {
        State s = state;
        if (s != null) {
            s.stop = true;
            synchronized (s) {
                s.notifyAll();
            }
        }
        state = null;
        var textures = Minecraft.getInstance().getTextureManager();
        for (Tile t : loaded.values()) {
            if (t.texture != null) textures.release(t.id);
            else t.image.close();
        }
        loaded.clear();
        Ready r;
        while ((r = ready.poll()) != null) r.image().close();
    }

    private static void delete(Path dir) {
        try (var files = Files.walk(dir)) {
            files.sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                    // left for next time
                }
            });
        } catch (IOException ignored) {
            // left for next time
        }
    }

    // ------------------------------------------------------------------ keys and files

    /** A tile's key: its level and its column and row, counted from the county's north-west corner. */
    public static long key(int level, int tx, int tz) {
        return (long) level << 40 | (long) (tx & 0xFFFFF) << 20 | (tz & 0xFFFFF);
    }

    private static int level(long k) {
        return (int) (k >>> 40);
    }

    private static int tx(long k) {
        return (int) (k >>> 20 & 0xFFFFF);
    }

    private static int tz(long k) {
        return (int) (k & 0xFFFFF);
    }

    private static String file(long k) {
        return "L" + level(k) + "_" + tx(k) + "_" + tz(k) + ".png";
    }

    private static long parse(String name) {
        if (!name.startsWith("L") || !name.endsWith(".png")) return Long.MIN_VALUE;
        String[] f = name.substring(1, name.length() - 4).split("_");
        try {
            return key(Integer.parseInt(f[0]), Integer.parseInt(f[1]), Integer.parseInt(f[2]));
        } catch (RuntimeException e) {
            return Long.MIN_VALUE;
        }
    }

    /** How many tiles a level has across. */
    public static int across(int level) {
        return 2 * HALF / (PX * SCALE[level]);
    }

    /** The world x (or z) of a tile's west (or north) edge. */
    public static int edge(int level, int t) {
        return -HALF + t * PX * SCALE[level];
    }

    // ------------------------------------------------------------------ what the page asks for

    /**
     * The tile, if it's ready to draw; otherwise asks for it (when {@code ask}) and gives null. Call on the render
     * thread.
     */
    public static @Nullable Tile tile(int level, int tx, int tz, boolean ask) {
        State s = state;
        if (s == null || tx < 0 || tz < 0 || tx >= across(level) || tz >= across(level)) return null;
        long k = key(level, tx, tz);
        Tile t = loaded.get(k);
        if (t != null) return t;
        if (ask && !s.working.contains(k)) {
            synchronized (s) {
                s.asked.remove(k);
                s.asked.addFirst(k);
                while (s.asked.size() > 96) s.asked.removeLast();
                s.notifyAll();
            }
        }
        return null;
    }

    /** Takes what the workers finished onto the card, and lets go of the oldest tiles. Call each frame. */
    public static void pump() {
        Ready r;
        int n = 0;
        while (n++ < 6 && (r = ready.poll()) != null) {
            Tile old = loaded.remove(r.key());
            release(old);
            loaded.put(r.key(), new Tile(r.image(), ZomboidCraft.id("county_map/" + file(r.key()).replace(".png", "").toLowerCase(Locale.ROOT))));
        }
        int[] count = new int[LEVELS];
        for (long k : loaded.keySet()) count[level(k)]++;
        var it = loaded.entrySet().iterator();
        while (it.hasNext()) {
            var e = it.next();
            int lv = level(e.getKey());
            if (count[lv] <= KEEP[lv]) continue;
            count[lv]--;
            release(e.getValue());
            it.remove();
        }
    }

    private static void release(@Nullable Tile t) {
        if (t == null) return;
        if (t.texture != null) Minecraft.getInstance().getTextureManager().release(t.id);
        else t.image.close();
    }

    /** Queues the close tiles around a spot for the background, once the sketch is done. */
    public static void around(double x, double z) {
        State s = state;
        if (s == null) return;
        synchronized (s) {
            if (!s.fill.isEmpty()) return;
            int lv = 1, n = across(lv), span = PX * SCALE[lv];
            int cx = (int) Math.floor((x + HALF) / span), cz = (int) Math.floor((z + HALF) / span);
            for (int r = 0; r <= 3; r++) {
                for (int tz = cz - r; tz <= cz + r; tz++) {
                    for (int tx = cx - r; tx <= cx + r; tx++) {
                        if (Math.max(Math.abs(tx - cx), Math.abs(tz - cz)) != r || tx < 0 || tz < 0 || tx >= n || tz >= n) continue;
                        long k = key(lv, tx, tz);
                        if (!s.onDisk.contains(k)) s.fill.add(k);
                    }
                }
            }
            if (s.fill.isEmpty()) {
                // the close level round you, once the level above is done
                lv = 0;
                n = across(lv);
                span = PX;
                cx = (int) Math.floor((x + HALF) / span);
                cz = (int) Math.floor((z + HALF) / span);
                for (int r = 0; r <= 4; r++) {
                    for (int tz = cz - r; tz <= cz + r; tz++) {
                        for (int tx = cx - r; tx <= cx + r; tx++) {
                            if (Math.max(Math.abs(tx - cx), Math.abs(tz - cz)) != r || tx < 0 || tz < 0 || tx >= n || tz >= n) continue;
                            long k = key(lv, tx, tz);
                            if (!s.onDisk.contains(k)) s.fill.add(k);
                        }
                    }
                }
            }
            if (s.fill.isEmpty()) {
                // then the rest of the county at the second level, nearest first: the far-out levels are made of it
                lv = 1;
                n = across(lv);
                span = PX * SCALE[lv];
                cx = (int) Math.floor((x + HALF) / span);
                cz = (int) Math.floor((z + HALF) / span);
                for (int r = 4; r < 2 * n; r++) {
                    for (int tz = cz - r; tz <= cz + r; tz++) {
                        for (int tx = cx - r; tx <= cx + r; tx++) {
                            if (Math.max(Math.abs(tx - cx), Math.abs(tz - cz)) != r || tx < 0 || tz < 0 || tx >= n || tz >= n) continue;
                            long k = key(lv, tx, tz);
                            if (!s.onDisk.contains(k)) s.fill.add(k);
                        }
                    }
                }
            }
            s.notifyAll();
        }
    }

    // ------------------------------------------------------------------ the workers

    private static void work(State s) {
        try {
            if (s.gen == null) {
                synchronized (CountyAtlas.class) {
                    if (s.gen == null) s.gen = gen(s);
                }
            }
        } catch (RuntimeException e) {
            ZomboidCraft.LOGGER.error("Couldn't lay out the county for the map", e);
            return;
        }
        while (!s.stop) {
            long k;
            boolean wanted;
            synchronized (s) {
                Long next = null;
                wanted = true;
                while (next == null && !s.asked.isEmpty()) {
                    Long c = s.asked.pollFirst();
                    if (!s.working.contains(c)) next = c;
                }
                if (next == null) {
                    wanted = false;
                    while (next == null && !s.fill.isEmpty()) {
                        Long c = s.fill.pollFirst();
                        if (!s.working.contains(c) && !s.onDisk.contains(c)) next = c;
                    }
                }
                if (next == null) {
                    try {
                        s.wait(2000);
                    } catch (InterruptedException e) {
                        return;
                    }
                    continue;
                }
                k = next;
                s.working.add(k);
            }
            try {
                NativeImage image = null;
                Path file = s.folder.resolve(file(k));
                if (s.onDisk.contains(k)) image = read(file);
                if (image == null && level(k) >= 2) {
                    // far out: shrunk from the second level where it's drawn, the sketch where it isn't yet
                    boolean[] whole = new boolean[1];
                    image = compose(s, k, whole);
                    if (whole[0] && !s.stop) {
                        image.writeToFile(file);
                        s.onDisk.add(k);
                        s.sketches.remove(k);
                    }
                } else if (image == null) {
                    image = draw(s, k);
                    if (s.stop) {
                        image.close();
                        return;
                    }
                    image.writeToFile(file);
                    s.onDisk.add(k);
                    s.done.incrementAndGet();
                }
                if (level(k) == 1 && s.small.get(k) == null) {
                    s.small.put(k, shrink(image, 4));
                    // the far-out tiles over it take it in
                    synchronized (s) {
                        for (int lv = 2; lv < LEVELS; lv++) {
                            int f = SCALE[lv] / SCALE[1];
                            long up = key(lv, tx(k) / f, tz(k) / f);
                            if (!s.onDisk.contains(up) && !s.fill.contains(up)) s.fill.addFirst(up);
                        }
                        s.notifyAll();
                    }
                }
                if (wanted || level(k) >= 2) {
                    ready.add(new Ready(k, image));
                } else {
                    image.close();
                }
            } catch (IOException | RuntimeException e) {
                ZomboidCraft.LOGGER.warn("Couldn't draw county map tile {}", file(k), e);
            } finally {
                s.working.remove(k);
            }
        }
    }

    /** A tile shrunk by a factor, each pixel the average of the square it stands for. */
    private static int[] shrink(NativeImage image, int f) {
        int n = PX / f;
        int[] out = new int[n * n];
        for (int j = 0; j < n; j++) {
            for (int i = 0; i < n; i++) {
                int a = 0, r = 0, g = 0, b = 0;
                for (int y = 0; y < f; y++) {
                    for (int x = 0; x < f; x++) {
                        int c = image.getPixel(i * f + x, j * f + y);
                        a += c >>> 24;
                        r += c >> 16 & 0xFF;
                        g += c >> 8 & 0xFF;
                        b += c & 0xFF;
                    }
                }
                int q = f * f;
                out[j * n + i] = (a / q) << 24 | (r / q) << 16 | (g / q) << 8 | b / q;
            }
        }
        return out;
    }

    /** A second-level tile shrunk to a quarter, from memory or from disk; null if it isn't drawn yet. */
    private static int @Nullable [] small(State s, long k) {
        int[] got = s.small.get(k);
        if (got != null || !s.onDisk.contains(k)) return got;
        NativeImage image = read(s.folder.resolve(file(k)));
        if (image == null) return null;
        try {
            got = shrink(image, 4);
        } finally {
            image.close();
        }
        s.small.put(k, got);
        return got;
    }

    /**
     * A far-out tile made of the second level's tiles under it, shrunk; the sketch shows where they aren't drawn yet.
     * {@code whole[0]} says whether every one was there (only then is it kept on disk).
     */
    private static NativeImage compose(State s, long k, boolean[] whole) {
        int lv = level(k), f = SCALE[lv] / SCALE[1], cell = PX / f;
        int[] base = s.sketches.get(k);
        if (base == null) {
            NativeImage sk = draw(s, k);
            base = new int[PX * PX];
            for (int j = 0; j < PX; j++) for (int i = 0; i < PX; i++) base[j * PX + i] = sk.getPixel(i, j);
            sk.close();
            s.sketches.put(k, base);
        }
        NativeImage image = new NativeImage(PX, PX, false);
        for (int j = 0; j < PX; j++) for (int i = 0; i < PX; i++) image.setPixel(i, j, base[j * PX + i]);
        whole[0] = true;
        int n1 = across(1);
        for (int cz = 0; cz < f; cz++) {
            for (int cx = 0; cx < f; cx++) {
                int tx = tx(k) * f + cx, tz = tz(k) * f + cz;
                if (tx >= n1 || tz >= n1) continue;
                int[] sm = small(s, key(1, tx, tz));
                if (sm == null) {
                    whole[0] = false;
                    continue;
                }
                // the quarter-size tile is 64 across; it goes into a cell 64 (level 2) or 16 (level 3) across
                int step = 64 / cell;
                for (int j = 0; j < cell; j++) {
                    for (int i = 0; i < cell; i++) {
                        int c;
                        if (step == 1) {
                            c = sm[j * 64 + i];
                        } else {
                            int a = 0, r = 0, g = 0, b = 0;
                            for (int y = 0; y < step; y++) {
                                for (int x = 0; x < step; x++) {
                                    int p = sm[(j * step + y) * 64 + i * step + x];
                                    a += p >>> 24;
                                    r += p >> 16 & 0xFF;
                                    g += p >> 8 & 0xFF;
                                    b += p & 0xFF;
                                }
                            }
                            int q = step * step;
                            c = (a / q) << 24 | (r / q) << 16 | (g / q) << 8 | b / q;
                        }
                        image.setPixel(cx * cell + i, cz * cell + j, c);
                    }
                }
            }
        }
        return image;
    }

    private static CountyGen gen(State s) {
        // in single player the server's county is already laid out: share it, but build places in a cache of our own
        // that stands back while the world itself is generating
        Minecraft mc = Minecraft.getInstance();
        if (mc.getSingleplayerServer() != null) {
            CountyGen world = CountyChunkGenerator.gen(s.seed);
            if (world.county().seed == s.seed) return new CountyGen(world.county(), new PlaceCache(24, world.cache()));
        }
        County county = s.planned ? new County(s.seed) : County.endless(s.seed);
        return new CountyGen(county, new PlaceCache(24));
    }

    private static @Nullable NativeImage read(Path file) {
        try (InputStream in = Files.newInputStream(file)) {
            return NativeImage.read(in);
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    // ------------------------------------------------------------------ drawing a tile

    private static final Map<String, Integer> COLOURS = new ConcurrentHashMap<>();
    /** Our own blocks' colours from above (tools/map/map_colours.py), NO_COLOUR for props a map leaves out. */
    private static final Map<String, Integer> OURS = ours();

    private static Map<String, Integer> ours() {
        Map<String, Integer> out = new java.util.HashMap<>();
        try (InputStream in = CountyAtlas.class.getResourceAsStream("/assets/zomboidcraft/map_colours.txt")) {
            if (in == null) return out;
            for (String line : new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).split("\n")) {
                String[] f = line.trim().split(" ");
                if (f.length < 3) continue;
                out.put(ZomboidCraft.MOD_ID + ":" + f[0], f[2].equals("1") ? NO_COLOUR : 0xFF000000 | Integer.parseInt(f[1], 16));
            }
        } catch (IOException | RuntimeException e) {
            ZomboidCraft.LOGGER.warn("Map colours unreadable", e);
        }
        return out;
    }
    private static final int NO_COLOUR = 0;

    /** A block's colour on a map, 0 for ones a map sees through (glass, air). */
    private static int colour(String block) {
        return COLOURS.computeIfAbsent(block, b -> {
            String name = b;
            int cut = name.indexOf('[');
            if (cut >= 0) name = name.substring(0, cut);
            if (name.equals("air") || name.equals("minecraft:air")) return NO_COLOUR;
            Identifier id = Identifier.tryParse(name.contains(":") ? name : ZomboidCraft.MOD_ID + ":" + name);
            if (id == null) return 0xFF7A7670;
            Integer own = OURS.get(id.toString());
            if (own != null) return own;
            Block found = BuiltInRegistries.BLOCK.getOptional(id).orElse(null);
            if (found == null) return 0xFF7A7670;
            MapColor map = found.defaultMapColor();
            if (map == MapColor.NONE) return NO_COLOUR;
            // small things on a roof or a floor (pots, signs, lamps, props) are not what a map shows; leaves are
            if (!found.defaultBlockState().canOcclude() && !name.contains("leaves")) return NO_COLOUR;
            return 0xFF000000 | map.col;
        });
    }

    private static final int WATER = 0xFF000000 | MapColor.WATER.col;

    /** One tile: its colours, worked out column by column. */
    private static NativeImage draw(State s, long k) {
        int lv = level(k), step = SCALE[lv];
        int x0 = edge(lv, tx(k)), z0 = edge(lv, tz(k)), span = PX * step;
        CountyGen gen = s.gen;
        County county = gen.county();
        boolean detail = lv <= 1;
        List<Place> places = county.placesNear(x0 - step, z0 - step, x0 + span, z0 + span, County.SKIRT);
        // roads first: which pixels are road surface and how high it runs
        int n = PX + 1;
        byte[] road = new byte[n * n];
        float[] roadY = new float[n * n];
        for (Road r : county.roadsNear(x0 - step, z0 - step, x0 + span, z0 + span)) {
            byte kind = (byte) (r.kind == Road.Kind.HIGHWAY ? 3 : r.kind == Road.Kind.COUNTY ? 2 : 1);
            double half = Math.max(r.kind.half + 0.5, step * 0.7);
            for (int i = 0; i + 1 < r.xs.length; i++) {
                double ax = r.xs[i], az = r.zs[i], bx = r.xs[i + 1], bz = r.zs[i + 1];
                double len = Math.hypot(bx - ax, bz - az);
                int steps = Math.max(1, (int) Math.ceil(len / Math.max(0.5, step / 2.0)));
                for (int t = 0; t <= steps; t++) {
                    double f = t / (double) steps;
                    double x = ax + (bx - ax) * f, z = az + (bz - az) * f;
                    double y = r.ys[i] + (r.ys[i + 1] - r.ys[i]) * f;
                    stamp(road, roadY, n, x0 - step, z0 - step, step, x, z, half, kind, (float) y);
                }
            }
        }
        // heights and colours, with a row and a column more on the north and west for the shading
        int[] rgb = new int[n * n];
        float[] height = new float[n * n];
        boolean[] water = new boolean[n * n];
        boolean[] built = new boolean[n * n];
        Terrain terrain = county.terrain;
        for (int j = 0; j < n; j++) {
            for (int i = 0; i < n; i++) {
                int x = x0 + (i - 1) * step + step / 2, z = z0 + (j - 1) * step + step / 2;
                int idx = j * n + i;
                Place in = null;
                for (Place p : places) {
                    if (p.contains(x, z, 0)) {
                        in = p;
                        break;
                    }
                }
                if (in != null && detail) {
                    Voxels v = gen.voxels(in);
                    int lx = x - in.x0, lz = z - in.z0, top = Integer.MIN_VALUE, c = 0;
                    boolean wet = false;
                    for (int y = v.maxY(); y >= Voxels.MIN_Y; y--) {
                        String b = v.get(lx, y, lz);
                        if (b == null) continue;
                        // the water itself, not a water barrel or cooler (those showed as blue dots)
                        if (b.equals("water") || b.startsWith("water[") || b.startsWith("minecraft:water")) {
                            wet = true;
                            c = WATER;
                            top = y;
                            break;
                        }
                        int col = colour(b);
                        if (col == NO_COLOUR) continue;
                        c = col;
                        top = y;
                        break;
                    }
                    if (top != Integer.MIN_VALUE) {
                        rgb[idx] = c;
                        height[idx] = in.level + top;
                        water[idx] = wet;
                        // three blocks over the lot is a building: its roof
                        built[idx] = !wet && top >= 3;
                        continue;
                    }
                }
                double ground = county.ground(x, z, places);
                if (road[idx] != 0 && in == null) {
                    boolean bridge = Terrain.isWater(ground) || ground < roadY[idx] - 4;
                    rgb[idx] = road[idx] == 1 ? 0xFF8A7556 : road[idx] == 3 ? 0xFF4A4A4C : 0xFF555555;
                    height[idx] = roadY[idx];
                    if (bridge) rgb[idx] = shade(rgb[idx], 1.15f);
                    continue;
                }
                int top = (int) Math.floor(ground);
                if (in != null) {
                    // far out: a built-up place is a block of colour by what it is
                    rgb[idx] = sketch(in.type);
                    height[idx] = in.level + 4;
                    continue;
                }
                if (top < Terrain.WATER) {
                    // deeper water, darker
                    rgb[idx] = shade(WATER, Math.max(0.62f, 1f - (Terrain.WATER - top) * 0.035f));
                    height[idx] = Terrain.WATER;
                    water[idx] = true;
                    continue;
                }
                height[idx] = top;
                rgb[idx] = land(county, terrain, x, z, top, step);
            }
        }
        NativeImage image = new NativeImage(PX, PX, false);
        for (int j = 1; j < n; j++) {
            for (int i = 1; i < n; i++) {
                int idx = j * n + i;
                int c = rgb[idx];
                if (!water[idx]) {
                    // light from the north-west, like a printed relief
                    float dh = height[idx] - (height[idx - n] + height[idx - 1]) / 2f;
                    float lift = lv >= 2 ? dh / step * 1.6f : dh;
                    float f = lift > 0.4f ? 1.12f : lift < -0.4f ? 0.84f : 1f;
                    if (lv >= 2) f = 1 + Math.max(-0.22f, Math.min(0.18f, lift * 0.12f));
                    c = shade(c, f);
                }
                if (detail) {
                    // a building's edge is inked, and it throws a shadow to the south-east
                    if (built[idx] != built[idx - 1] || built[idx] != built[idx - n]) c = shade(c, 0.62f);
                    else if (!built[idx] && height[idx - n - 1] - height[idx] > 2) c = shade(c, 0.78f);
                    else if (built[idx]) c = shade(c, 0.94f);
                }
                image.setPixel(i - 1, j - 1, paper(c));
            }
        }
        return image;
    }

    /** Open ground: grass, woods, beach and bare rock, the way the generator lays them. */
    private static int land(County county, Terrain terrain, int x, int z, int top, int step) {
        boolean shore = top <= Terrain.WATER + 1;
        if (terrain.plan != null && shore && terrain.plan.coast(x, z) < 70 && terrain.plan.coast(x, z) > -220) {
            return colour("minecraft:sand");
        }
        if (shore) return colour("minecraft:mud");
        if (top > 98) return colour("minecraft:stone");
        double forest = terrain.forest(x, z);
        if (forest > 0.45) {
            // the woods' crowns, in clumps a few blocks across
            double canopy = forest - 0.45 + clump(county.seed, x, z, step) * 0.5;
            if (canopy > 0.32) {
                int leaves = shade(0xFF000000 | MapColor.PLANT.col, 0.72f + (float) (clump(county.seed + 7, x, z, step) * 0.25));
                return leaves;
            }
            return colour(forest > 0.6 ? "minecraft:podzol" : "minecraft:grass_block");
        }
        return terrain.moisture(x, z) > 0.25 ? colour("minecraft:grass_block") : shade(colour("minecraft:grass_block"), 1.06f);
    }

    /** A value from 0 to 1 that changes every few blocks: where tree crowns are. */
    private static double clump(long seed, int x, int z, int step) {
        int cell = Math.max(3, step);
        int cx = Math.floorDiv(x, cell), cz = Math.floorDiv(z, cell);
        long h = seed * 0x9E3779B97F4A7C15L ^ cx * 0x632BE59BD9B4E019L ^ cz * 0x85157AF5L;
        h ^= h >>> 29;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 32;
        return (h & 0xFFFF) / 65535.0;
    }

    /** A built-up place seen from far out: grey for the city, warm for towns, green for farms. */
    private static int sketch(PlaceType type) {
        return switch (type) {
            case DOWNTOWN -> 0xFF8C8782;
            case FARM -> 0xFFB4A66A;
            case MILITARY -> 0xFF6E7558;
            case PORT -> 0xFF8E8A84;
            default -> 0xFFA6927C;
        };
    }

    private static void stamp(byte[] road, float[] roadY, int n, int x0, int z0, int step, double x, double z, double r, byte kind, float y) {
        int i0 = (int) Math.floor((x - r - x0) / step), i1 = (int) Math.floor((x + r - x0) / step);
        int j0 = (int) Math.floor((z - r - z0) / step), j1 = (int) Math.floor((z + r - z0) / step);
        for (int j = Math.max(0, j0); j <= Math.min(n - 1, j1); j++) {
            for (int i = Math.max(0, i0); i <= Math.min(n - 1, i1); i++) {
                double cx = x0 + i * step + step / 2.0, cz = z0 + j * step + step / 2.0;
                if ((cx - x) * (cx - x) + (cz - z) * (cz - z) > r * r) continue;
                int idx = j * n + i;
                if (kind >= road[idx]) {
                    road[idx] = kind;
                    roadY[idx] = y;
                }
            }
        }
    }

    private static int shade(int argb, float f) {
        int r = Math.min(255, Math.round((argb >> 16 & 255) * f));
        int g = Math.min(255, Math.round((argb >> 8 & 255) * f));
        int b = Math.min(255, Math.round((argb & 255) * f));
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    /** A fifth of the way to the paper's colour, so the map reads printed rather than a screenshot. */
    private static int paper(int argb) {
        int r = (argb >> 16 & 255) * 4 / 5 + 0xD8 / 5;
        int g = (argb >> 8 & 255) * 4 / 5 + 0xCF / 5;
        int b = (argb & 255) * 4 / 5 + 0xB8 / 5;
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    private CountyAtlas() {
    }
}
