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
        /** Resolved: the anchor's numbers with this gun's laid over them. */
        transient ZcConfig.GunStats merged;
        transient GunType type;

        public GunType type() {
            return type;
        }
    }

    private static final Gson GSON = new Gson();
    private static final Map<String, Spec> SPECS = new HashMap<>();
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

    public static java.util.Collection<Spec> all() {
        load();
        return SPECS.values();
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

    private LocalGuns() {
    }
}
