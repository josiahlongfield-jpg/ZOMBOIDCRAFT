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
import java.util.List;
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
        /** Its size in inventory cells, Tarkov's Width and Height (w, h); none: sized by its kind (carry/grid/ItemSize). */
        public int[] cells;
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
        /**
         * Its own reload lengths in seconds, [onto a chambered round, onto an empty gun], for a gun that brings its own
         * reload animations (the operator's rifle, 2026-11-06): the game's reload is paced to them like a clip's.
         */
        public float[] reload;
        /**
         * For a gun loaded a round at a time with its own clips (the operator's Remington 870 and MP-18, 2026-11-06), its
         * lengths in seconds: [the raise (or the opening), each round, the finish (or the close), the pump onto an empty
         * gun]. The game's loading runs each part at that part's own pace, so every round goes in as its clip ends.
         */
        public float[] tube;
        /**
         * Pieces of its first-person model seen only while certain clips play (Josiah 2026-11-08: the MP-18's round has to
         * go into the chamber): by bone, the clips and the seconds of each, [from, to], it shows in. The MP-18's case
         * comes out as it opens; the round goes from the hand into the chamber.
         */
        public Map<String, Map<String, float[]>> shown;
        /** Its can is part of the gun (the operator's kitted M4A1): it fires quiet with no muzzle attachment of ours. */
        public boolean suppressed;
        /**
         * The parts built onto it in its own file (the operator's kitted M4A1, 2026-11-06): any of "sight", "suppressor",
         * "laser", "light", "foregrip", "stock". Such a gun takes none of our parts, only its magazines, and each part
         * does its job: the sight settles the aim, the can quiets it, the grip tames the kick, the laser and light
         * switch on (keys J and L).
         */
        public java.util.List<String> kit;
        /** Where its own laser and light shine from (client RailLights). */
        public Rail rail;
        /**
         * Its Tarkov first person (2026-11-06, the batch after the M4A1): the model in config/zomboidcraft/npc drawing
         * Tarkov's arms and this gun with its clips ("operator_tarkov_pm"); none: the M4A1's or our own.
         */
        public String fp;
        /** The sight's mesh in that model, whose reticle is drawn on its glass, and how wide its reticle texture spans (MOA). */
        public String sight;
        public float reticleMoa = 152.4f;
        /**
         * Its sight's magnification (the SVDS's PSO 4x, the SV-98's S&B at 5x): above 1 the operator's first person
         * looks through it as a lens (client ScopeView), its reticle the sight's own; 0 for a red dot or irons.
         */
        public float zoom;
        /**
         * The sight's own modes, Tarkov's Zooms for it (the HHS-1's magnifier 3x then 1x, a 5-25x scope's 5 and 25): the
         * sight-mode key steps through them; none: zoom alone.
         */
        public float[] zooms;
        /**
         * The other sights built on that the switch-sight key turns to, in order after the main one (Tarkov's change
         * sight: the MDR's ACOG to the RMR riding on it, Josiah 2026-11-07; any sight with one, looted ones too). The Nth
         * (from 1) draws its model's texture "reticle_N"; none: the one sight.
         */
        public java.util.List<AltSight> alt;
        /**
         * Handed to each new life, loaded with only the magazine in it and one spare (the starter kit, Josiah
         * 2026-11-06: the PM).
         */
        public boolean starter;
        /**
         * Tarkov's own left-hand holds (each foregrip's and handguard's GripPose, Josiah 2026-11-07: a looted grip must
         * actually be held), by the part's bone in the first-person model: the palm on that part and the fingers' turns.
         * The fitted foregrip's hold wins, else the handguard's (client GripHold).
         */
        public java.util.Map<String, Hold> holds;
        /** The foregrip and the handguard built on (their bones, keys of holds); none: no such part. */
        public String grip, guard;
        /**
         * A Tarkov gun built from parts (2026-11-07, bench fitting): its base and its kit as a tree; none: a gun whose
         * parts are fixed. Its parts then come off and go on at the bench (combat/TarkovFit).
         */
        public Tarkov tarkov;
        /** Resolved: the anchor's numbers with this gun's laid over them. */
        transient ZcConfig.GunStats merged;
        transient GunType type;

        public GunType type() {
            return type;
        }

        public boolean has(String part) {
            return kit != null && kit.contains(part);
        }
    }

    /**
     * One left-hand hold: the palm on its part (metres and a turn x, y, z, w in that part's bone frame) and each
     * finger bone's own turn from its parent ("Base HumanLDigit11": x, y, z, w).
     */
    public static final class Hold {
        public float[] palm;
        public java.util.Map<String, float[]> fingers;
    }

    /**
     * One more sight on a gun, as the main one is given: its mesh, magnification (0 or 1: a red dot), its modes, reticle
     * span, and where its eye point (its mod_aim_camera) sits from the main sight's: metres and a turn x, y, z, w in the
     * main one's aim frame (x right, y up, looking down -z).
     */
    /**
     * A Tarkov gun's kit tree: Tarkov's id of its base, each kit part where it sits, and which of their eye points the
     * first-person model's "ads" was made on (the kit part's path and that part's eye point, from 0).
     */
    public static final class Tarkov {
        public String base;
        public java.util.List<KitPart> kit;
        public String aimPath;
        public int aimIndex;
        /** The kit's flip-up irons: each lies folded while a sight is on the gun, as Tarkov folds them (Josiah 2026-11-07). */
        public java.util.List<Fold> folds;
    }

    /**
     * A flip-up iron's fold: its bone in the first-person model (or a part model's second bone), the hinge in that
     * bone's frame, the axis it turns about and how far (degrees) to lie flat, a rear one forward, a front one back.
     */
    public static final class Fold {
        public String bone;
        public float[] hinge, axis;
        public float angle;

        /** The turn about the hinge, in the bone's own frame. */
        public org.joml.Matrix4f matrix() {
            return new org.joml.Matrix4f().translation(hinge[0], hinge[1], hinge[2])
                    .rotate((float) Math.toRadians(angle), axis[0], axis[1], axis[2])
                    .translate(-hinge[0], -hinge[1], -hinge[2]);
        }
    }

    /**
     * One kit part: its path (slot names from the gun down, "" for the base), its Tarkov id, the bone of its own main
     * mesh in the first-person model and where that bone sits in the frame of the slot holding the part (metres and a
     * turn x, y, z, qx, qy, qz, qw), and all the bones that are its meshes (hidden once it's swapped out).
     */
    public static final class KitPart {
        public String path, item, bone;
        public float[] at;
        public java.util.List<String> bones;
    }

    public static final class AltSight {
        public String sight;
        public float zoom;
        public float[] zooms;
        public float reticleMoa = 152.4f;
        public float[] aim;
    }

    /** A gun's own laser and light. */
    public static final class Rail {
        public Device laser, light;
    }

    /**
     * One lens on a gun's rail: the first-person rig's bone (by name) it rides on, the lens there and the way it shines
     * (metres, that bone's frame), and the lens on the third-person rifle (metres, its "weapon" bone's frame).
     */
    public static final class Device {
        public String bone;
        public float[] at, dir, third;
    }

    /** Whether the gun is one of these with that part built on. */
    public static boolean kitHas(ItemStack gun, String part) {
        Spec spec = of(gun);
        return spec != null && spec.has(part);
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
        Spec spec = of(gun);
        float clip = spec != null && spec.reload != null && spec.reload.length >= 2 ? spec.reload[charge ? 1 : 0] * 20
                : clipTicks(gun.get(ID));
        if (clip <= 0) {
            return 1;
        }
        return Math.clamp(gameReloadTicks(type, charge, n) / clip, 0.2f, 5f);
    }

    /**
     * The pace of one part of a hand-loaded gun's loading, for a gun with its own clips (Spec.tube): 0 the raise or
     * opening, 1 the rounds, 2 the finish or close, 3 the pump onto an empty chamber. -1 for a gun without them.
     */
    public static float tubePace(ItemStack gun, GunType type, int part, boolean charge) {
        Spec spec = of(gun);
        if (spec == null || spec.tube == null || spec.tube.length < 3 || !type.tube) {
            return -1;
        }
        float game, own;
        if (part == 0) {
            game = type.raise;
            own = spec.tube[0];
        } else if (part == 1) {
            game = type.perShell;
            own = spec.tube[1];
        } else if (part == 2) {
            // the finish (a pump gun's first tick of it), or the close
            game = type.opens ? 1 + type.close : 1;
            own = spec.tube[2];
        } else {
            // the pump onto an empty chamber, after the finish
            game = 1 + type.cycle;
            own = spec.tube.length > 3 ? spec.tube[3] : 0;
        }
        return own <= 0 ? 1 : Math.clamp(game / (own * 20), 0.05f, 20f);
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
        // a magazine-fed shotgun (the AA-12) keeps its anchor's magazine reload but fires buckshot: its own "pellets"
        // make its numbers a shotgun's (the spread and falloff not given take the shotgun's defaults)
        Class<? extends ZcConfig.GunStats> kind = anchor.getClass();
        if (own != null && own.has("pellets") && own.get("pellets").getAsInt() > 1 && !(anchor instanceof ZcConfig.ShotgunStats)) {
            kind = ZcConfig.ShotgunStats.class;
        }
        return GSON.fromJson(tree, kind);
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
     * <p>
     * A file with Tarkov's own sounds (Josiah 2026-11-08: "make sure every piece of audio for the reload is present")
     * lists every one its clips play, in sounds: [share of the reload, name], each played as the gun's sound
     * tk.&lt;name&gt; (Tarkov's own takes of it, in the sound pack); those replace the three above. A gun loaded a round at
     * a time gives them by part instead (load: start, shell, end, pump; each a share of that part).
     */
    public record Cues(float magOut, float magIn, float charge, boolean release, @Nullable Cues tactical,
            List<Cue> sounds, Map<String, LoadPart> load) {
        public Cues(float magOut, float magIn, float charge, boolean release) {
            this(magOut, magIn, charge, release, null, List.of(), Map.of());
        }

        /** The cues for this reload: a reload onto a chambered round has its own when the file gives them ("tactical"). */
        public Cues forCharge(boolean charge) {
            return charge || tactical == null ? this : tactical;
        }

        /** Whether this reload plays Tarkov's own sounds (in place of the magazine out, in and handle). */
        public boolean own() {
            return !sounds.isEmpty();
        }

        /** A part of a hand-loaded gun's loading with Tarkov's own sounds (start, shell, end, pump), or none. */
        public List<Cue> load(String part) {
            LoadPart p = load.get(part);
            return p == null ? List.of() : p.sounds();
        }

        /** How long that part's clip runs (seconds), or 0. */
        public float loadSeconds(String part) {
            LoadPart p = load.get(part);
            return p == null ? 0 : p.dur();
        }

        /** Whether the gun is loaded a round at a time with Tarkov's own sounds. */
        public boolean ownLoad() {
            return !load.isEmpty();
        }
    }

    /** A part of a hand-loaded gun's loading: its clip's length (seconds) and its sounds, each a share of it. */
    public record LoadPart(float dur, List<Cue> sounds) {
    }

    /** One of Tarkov's reload sounds: when (a share of the reload, or of the part), and its name ("MagOut"). */
    public record Cue(float at, String name) {
    }

    /** The prefix of a Tarkov reload sound under a gun's own sounds (weapon.local/&lt;gun&gt;/tk.&lt;name&gt;). */
    public static final String TARKOV = "tk.";

    /** One of Tarkov's own reload sounds for this gun (Cue.name), from the sound pack on this computer. */
    public static net.minecraft.sounds.SoundEvent tarkovSound(ItemStack gun, String name) {
        String id = gun.get(ID);
        String g = id == null ? "none" : id.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9_.-]", "_");
        return net.minecraft.sounds.SoundEvent.createVariableRangeEvent(ZomboidCraft.id(SOUND_PREFIX + g + "/" + TARKOV
                + name.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9_.-]", "_")));
    }

    private static List<Cue> cueList(com.google.gson.JsonElement e, float dur) {
        if (e == null || !e.isJsonArray()) {
            return List.of();
        }
        List<Cue> out = new java.util.ArrayList<>();
        for (com.google.gson.JsonElement x : e.getAsJsonArray()) {
            com.google.gson.JsonArray a = x.getAsJsonArray();
            out.add(new Cue(a.get(0).getAsFloat() / dur, a.get(1).getAsString()));
        }
        out.sort(java.util.Comparator.comparingDouble(Cue::at));
        return List.copyOf(out);
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
            Cues tactical = null;
            if (o.has("tactical")) {
                com.google.gson.JsonObject t = o.getAsJsonObject("tactical");
                float td = t.get("dur").getAsFloat();
                java.util.function.Function<String, Float> tat = k -> t.has(k) && td > 0 ? t.get(k).getAsFloat() / td : -1f;
                tactical = new Cues(tat.apply("mag_out"), tat.apply("mag_in"), -1f, false, null,
                        td > 0 ? cueList(t.get("sounds"), td) : List.of(), Map.of());
            }
            // a hand-loaded gun's parts, each its own clip: the times are seconds into that part
            Map<String, LoadPart> load = new java.util.HashMap<>();
            if (o.has("load")) {
                com.google.gson.JsonObject l = o.getAsJsonObject("load");
                for (String part : l.keySet()) {
                    com.google.gson.JsonObject p = l.getAsJsonObject(part);
                    float pd = p.get("dur").getAsFloat();
                    if (pd > 0) load.put(part, new LoadPart(pd, cueList(p.get("sounds"), pd)));
                }
            }
            return java.util.Optional.of(new Cues(at.apply("mag_out"), at.apply("mag_in"), at.apply("charge"),
                    o.has("charge_sound") && o.get("charge_sound").getAsString().equals("release"), tactical,
                    cueList(o.get("sounds"), dur), Map.copyOf(load)));
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

    /**
     * A magazine's worth of a gun's own rounds, loose: there are no magazine items any more, and rounds handed to you
     * go into what you carry (combat/AmmoPouch; Josiah 2026-11-08).
     */
    public static ItemStack fullMagazine(ItemStack gun, GunType type) {
        net.minecraft.world.item.Item ammo = ammo(gun, type);
        return new ItemStack(ammo, Math.max(1, Math.min(type.capacity(gun), new ItemStack(ammo).getMaxStackSize())));
    }

    private LocalGuns() {
    }
}
