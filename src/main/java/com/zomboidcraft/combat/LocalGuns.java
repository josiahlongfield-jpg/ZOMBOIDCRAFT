package com.zomboidcraft.combat;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.zomboidcraft.ZcConfig;
import com.zomboidcraft.ZomboidCraft;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Guns kept on this computer rather than shipped in the mod (Josiah's Brita pack, 2026-11-01): each is one of our own
 * guns (its anchor, whose handling, reload and hold it keeps) carrying {@link #ID}, with its own name, calibre and
 * numbers read from {@code config/zomboidcraft/guns/<id>.json} and its look from {@code <id>.zmesh} beside it.
 * Whoever doesn't have the files sees and fires the plain anchor gun.
 */
public final class LocalGuns {
    /** The local gun a stack is, by its file name. */
    public static final DataComponentType<String> ID = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
            ZomboidCraft.id("local_gun"), DataComponentType.<String>builder()
                    .persistent(Codec.STRING).networkSynchronized(ByteBufCodecs.STRING_UTF8).build());

    public static final Path DIR = FabricLoader.getInstance().getConfigDir().resolve("zomboidcraft").resolve("guns");

    /** One local gun's file. stats holds only what differs from the anchor's; the rest comes from the anchor. */
    public static final class Spec {
        public String id;
        public String name;
        /** Our gun it handles like, by item id ("pistol", "assault_rifle", ...). */
        public String anchor;
        public String calibre;
        /** The item it fires, by id ("acp45_ammo"); none: the anchor's. */
        public String ammo;
        /** The mesh our anchor gun draws (e.g. "pbr_pistol"), which this gun's mesh takes the place of. */
        public String anchorMesh;
        public JsonObject stats;
        /**
         * Taken out of the game (Josiah 2026-11-04, the DShK): the file stays only to say so. No such gun is made, and
         * any copy of it or its magazines already in a world vanishes like our old guns (combat/Retired).
         */
        public boolean removed;
        /**
         * Its iron sights' line, item pixels: the top of the front post (or bead) and the middle of the rear notch or
         * aperture. Aiming lines the eye up along it (client RifleView); none: the anchor's own sight pose.
         */
        public Sights sights;
        /** Sights it has no mesh of its own for, drawn from our meshes on top of it (our flip-up pair). */
        public java.util.List<Part> irons;
        /** Resolved: the anchor's numbers with this gun's laid over them. */
        transient ZcConfig.GunStats merged;
        transient GunType type;

        public GunType type() {
            return type;
        }
    }

    /** A sight line: front and rear points, item pixels [x, y, z]. */
    public static final class Sights {
        public float[] front;
        public float[] rear;
        /** Where the eye sits along the gun (item pixel z), when the usual eye relief would put a stock or hammer in the way; 0: the usual. */
        public float eye;
    }

    /** One of our meshes drawn on the gun: its id and where its origin sits, item pixels. */
    public static final class Part {
        public String mesh;
        public float[] at;
    }

    private static final Gson GSON = new Gson();
    private static final Map<String, Spec> SPECS = new HashMap<>();
    private static final java.util.Set<String> REMOVED = new java.util.HashSet<>();
    private static long loadedAt = -1;

    public static void register() {
        // loading the class registers the component
    }

    /** The local gun's file for a stack, or null for one of ours (or one whose file isn't on this computer). */
    public static @Nullable Spec of(ItemStack stack) {
        String id = stack.get(ID);
        return id == null ? null : get(id);
    }

    public static @Nullable Spec get(String id) {
        load();
        return SPECS.get(id);
    }

    /** Whether a local gun was taken out of the game (its file says "removed"). */
    public static boolean removed(String id) {
        load();
        return REMOVED.contains(id);
    }

    public static java.util.Collection<Spec> all() {
        load();
        return SPECS.values();
    }

    private static final Map<String, Float> CLIP_TICKS = new java.util.concurrent.ConcurrentHashMap<>();
    private static int clipGeneration = -1;

    /**
     * How long Josiah's own reload for this gun runs (game ticks, from reloads/<id>.json's first-person view), or 0
     * when it has none. Read once per folder read, skipping over the frames.
     */
    public static float clipTicks(@Nullable String id) {
        if (id == null) {
            return 0;
        }
        if (clipGeneration != generation) {
            clipGeneration = generation;
            CLIP_TICKS.clear();
        }
        return CLIP_TICKS.computeIfAbsent(id, LocalGuns::readClipTicks);
    }

    private static float readClipTicks(String id) {
        Path f = DIR.resolve("reloads").resolve(id + ".json");
        if (!Files.isRegularFile(f)) {
            return 0f;
        }
        try (com.google.gson.stream.JsonReader r = new com.google.gson.stream.JsonReader(Files.newBufferedReader(f, StandardCharsets.UTF_8))) {
            r.beginObject();
            while (r.hasNext()) {
                if (!r.nextName().equals("views")) {
                    r.skipValue();
                    continue;
                }
                r.beginObject();
                while (r.hasNext()) {
                    if (!r.nextName().equals("fp")) {
                        r.skipValue();
                        continue;
                    }
                    r.beginObject();
                    while (r.hasNext()) {
                        if (r.nextName().equals("dur")) {
                            return (float) Math.max(0, r.nextDouble() * 20);
                        }
                        r.skipValue();
                    }
                    return 0f;
                }
                return 0f;
            }
        } catch (Exception e) {
            ZomboidCraft.LOGGER.error("Couldn't read the reload length in {}", f, e);
        }
        return 0f;
    }

    /**
     * A reload's length in the game's own timing (ticks): a magazine gun's, with or without the handle worked; a
     * hand-loaded gun's with n rounds going in, then shut or worked (as Guns runs them).
     */
    public static float gameReloadTicks(GunType type, boolean charge, int n) {
        if (!type.tube) {
            return charge ? type.chargeDone : type.tacticalDone;
        }
        return type.raise + Math.max(1, n) * type.perShell + (type.opens ? 1 + type.close : charge ? 2 + type.cycle : 1);
    }

    /**
     * How many of the game's reload ticks pass each real tick, so a local gun with its own reload takes exactly as
     * long as that reload runs (Josiah 2026-11-04: "Your clip lengths"); 1 for every other gun.
     */
    public static float reloadPace(ItemStack gun, GunType type, boolean charge, int n) {
        float clip = clipTicks(gun.get(ID));
        if (clip <= 0) {
            return 1;
        }
        return Math.clamp(gameReloadTicks(type, charge, n) / clip, 0.2f, 5f);
    }

    /** Reads the folder again (the files can change while the game runs). */
    public static synchronized void reload() {
        loadedAt = -1;
        generation++;
        load();
    }

    private static volatile int generation;

    /** Goes up each time the folder is read again, so the client knows to read the meshes again too. */
    public static int generation() {
        return generation;
    }

    private static synchronized void load() {
        if (loadedAt >= 0) {
            return;
        }
        loadedAt = System.currentTimeMillis();
        SPECS.clear();
        REMOVED.clear();
        if (!Files.isDirectory(DIR)) {
            return;
        }
        try (var files = Files.list(DIR)) {
            for (Path f : (Iterable<Path>) files::iterator) {
                if (!f.getFileName().toString().endsWith(".json")) {
                    continue;
                }
                try (Reader r = Files.newBufferedReader(f, StandardCharsets.UTF_8)) {
                    Spec s = GSON.fromJson(r, Spec.class);
                    if (s != null && s.id != null && s.removed) {
                        REMOVED.add(s.id);
                        continue;
                    }
                    if (s == null || s.id == null || s.anchor == null) {
                        continue;
                    }
                    s.type = byId(s.anchor);
                    if (s.type == null) {
                        ZomboidCraft.LOGGER.warn("Local gun {}: no gun of ours called {}", s.id, s.anchor);
                        continue;
                    }
                    s.merged = merge(s.type.stats(), s.stats);
                    SPECS.put(s.id, s);
                } catch (RuntimeException | java.io.IOException e) {
                    ZomboidCraft.LOGGER.error("Couldn't read local gun {}", f, e);
                }
            }
        } catch (java.io.IOException e) {
            ZomboidCraft.LOGGER.error("Couldn't list {}", DIR, e);
        }
        ZomboidCraft.LOGGER.info("Local guns: {} from {}", SPECS.size(), DIR);
    }

    private static @Nullable GunType byId(String id) {
        for (GunType t : GunType.values()) {
            if (t.id().equals(id)) {
                return t;
            }
        }
        return null;
    }

    /** A copy of the anchor's numbers (same class, so a shotgun keeps its pellets) with the gun's own laid over. */
    private static ZcConfig.GunStats merge(ZcConfig.GunStats anchor, @Nullable JsonObject own) {
        JsonObject tree = GSON.toJsonTree(anchor).getAsJsonObject();
        if (own != null) {
            for (Map.Entry<String, JsonElement> e : own.entrySet()) {
                tree.add(e.getKey(), e.getValue());
            }
        }
        return GSON.fromJson(tree, anchor.getClass());
    }

    /** The numbers a stack fires with: a local gun's own, else its type's. */
    public static ZcConfig.GunStats stats(ItemStack stack, GunType type) {
        Spec s = of(stack);
        return s != null && s.type == type ? s.merged : type.stats();
    }

    /** The round a stack fires, for the calibre's headshot value. */
    public static String calibre(ItemStack stack, GunType type) {
        Spec s = of(stack);
        return s != null && s.calibre != null ? s.calibre : type.caliber();
    }

    /** The round a stack (a local gun, or one of its magazines) takes: its own, else its type's. */
    public static net.minecraft.world.item.Item ammo(ItemStack stack, GunType type) {
        Spec s = of(stack);
        if (s != null && s.ammo != null) {
            var item = BuiltInRegistries.ITEM.getOptional(ZomboidCraft.id(s.ammo));
            if (item.isPresent()) {
                return item.get();
            }
        }
        return type.ammo();
    }

    /**
     * Whether a magazine goes in a gun: each local gun takes only its own (Josiah 2026-11-01: every Brita gun its own
     * magazine, like Arsenal), which is the anchor's magazine item carrying the gun's id; ours take only plain ones.
     */
    public static boolean magFits(ItemStack mag, ItemStack gun, GunType type) {
        return type.magazine() != null && mag.is(type.magazine()) && java.util.Objects.equals(mag.get(ID), gun.get(ID));
    }

    /** An empty magazine for a gun: its own for a local one. */
    public static ItemStack magazineFor(ItemStack gun, GunType type) {
        ItemStack mag = new ItemStack(type.magazine());
        String id = gun.get(ID);
        if (id != null) {
            mag.set(ID, id);
        }
        return mag;
    }

    /** Where a local gun's own sounds live: weapon.local/<gun>/<our event>, e.g. weapon.local/colt1911/weapon.pistol.shot. */
    public static final String SOUND_PREFIX = "weapon.local/";

    /**
     * The sound a gun makes for one of its anchor's events: a local gun's own, played under its own name so the sound
     * pack on this computer (config of his profile's resource packs) can give it Brita's recording, keeping the
     * anchor's hearing range. A client without that recording hears the anchor's (client SoundManagerMixin).
     */
    public static net.minecraft.sounds.SoundEvent sound(ItemStack gun, net.minecraft.sounds.SoundEvent ours) {
        String id = gun.get(ID);
        net.minecraft.resources.Identifier key = id == null ? null : BuiltInRegistries.SOUND_EVENT.getKey(ours);
        if (key == null) {
            return ours;
        }
        net.minecraft.resources.Identifier name = ZomboidCraft.id(SOUND_PREFIX + id.toLowerCase(java.util.Locale.ROOT)
                .replaceAll("[^a-z0-9_.-]", "_") + "/" + key.getPath());
        return ours.fixedRange().isPresent() ? net.minecraft.sounds.SoundEvent.createFixedRangeEvent(name, ours.fixedRange().get())
                : net.minecraft.sounds.SoundEvent.createVariableRangeEvent(name);
    }

    /** Added to an event's name for the release half of a handle sound alone (a bolt catch dropped, Cues). */
    public static final String RELEASE = "_release";

    /** sound(gun, ours), its name with a suffix (RELEASE): a part of the recording the pack keeps under its own name. */
    public static net.minecraft.sounds.SoundEvent sound(ItemStack gun, net.minecraft.sounds.SoundEvent ours, String suffix) {
        net.minecraft.sounds.SoundEvent s = sound(gun, ours);
        if (s == ours || suffix.isEmpty()) {
            return s;
        }
        net.minecraft.resources.Identifier name = s.location().withSuffix(suffix);
        return s.fixedRange().isPresent() ? net.minecraft.sounds.SoundEvent.createFixedRangeEvent(name, s.fixedRange().get())
                : net.minecraft.sounds.SoundEvent.createVariableRangeEvent(name);
    }

    /**
     * When a local gun's reload makes its sounds, from reloads/&lt;id&gt;.cues.json beside his clip (Josiah 2026-11-06: the
     * game's fixed reload ticks played the AR-15's handle sound on its bolt-catch slap and the Honey Badger's at the
     * magazine): each a share of the clip (0 to 1), or below 0 for none; release plays only the handle's release (a
     * bolt catch dropped rather than a handle pulled).
     */
    public record Cues(float magOut, float magIn, float charge, boolean release) {
    }

    private static final Map<String, java.util.Optional<Cues>> CUES = new java.util.concurrent.ConcurrentHashMap<>();
    private static int cuesGeneration = -1;

    public static @Nullable Cues cues(@Nullable String id) {
        if (id == null) {
            return null;
        }
        if (cuesGeneration != generation) {
            cuesGeneration = generation;
            CUES.clear();
        }
        return CUES.computeIfAbsent(id, LocalGuns::readCues).orElse(null);
    }

    private static java.util.Optional<Cues> readCues(String id) {
        Path f = DIR.resolve("reloads").resolve(id + ".cues.json");
        if (!Files.isRegularFile(f)) {
            return java.util.Optional.empty();
        }
        try (java.io.Reader r = Files.newBufferedReader(f, StandardCharsets.UTF_8)) {
            com.google.gson.JsonObject o = com.google.gson.JsonParser.parseReader(r).getAsJsonObject();
            float dur = o.get("dur").getAsFloat();
            if (dur <= 0) {
                return java.util.Optional.empty();
            }
            java.util.function.Function<String, Float> at = k -> o.has(k) ? o.get(k).getAsFloat() / dur : -1f;
            return java.util.Optional.of(new Cues(at.apply("mag_out"), at.apply("mag_in"), at.apply("charge"),
                    o.has("charge_sound") && o.get("charge_sound").getAsString().equals("release")));
        } catch (Exception e) {
            ZomboidCraft.LOGGER.error("Couldn't read the reload sound cues in {}", f, e);
            return java.util.Optional.empty();
        }
    }

    /** A local gun, loaded with its own rounds. */
    public static ItemStack loaded(Spec spec) {
        ItemStack gun = new ItemStack(spec.type().item());
        gun.set(ID, spec.id);
        // a gun loaded by hand (a cylinder, a tube, a crossbow's rail) counts as having its "magazine" in for good: false
        // there left the S&W 500, the 870, the Winchester, the crossbow and the RPG clicking dry for ever (2026-11-04)
        gun.set(GunState.TYPE, new GunState(true, spec.type().capacity(gun), false));
        return gun;
    }

    /** One of a local gun's own magazines, full; empty for a gun without one (a tube or a cylinder). */
    public static ItemStack fullMagazine(ItemStack gun, GunType type) {
        if (type.magazine() == null) {
            return ItemStack.EMPTY;
        }
        ItemStack mag = magazineFor(gun, type);
        mag.set(GunState.MAG_ROUNDS, type.capacity(gun));
        return mag;
    }

    private LocalGuns() {
    }
}
