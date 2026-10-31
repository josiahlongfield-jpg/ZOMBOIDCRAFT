package com.zomboidcraft.sandbox;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.zomboidcraft.ZcConfig;
import com.zomboidcraft.ZomboidCraft;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;

/**
 * The world's own settings, Zomboid's Sandbox: every setting on the page (SandboxOptions) is kept with the world in
 * zomboidcraft_sandbox.json, chosen when the world is made and changeable later by the host. While a world runs its
 * values sit on top of config/zomboidcraft.json, which never takes them in: it is what a new world starts from.
 * Settings that only change what you see (client) stay on each player's PC.
 */
public final class Sandbox {
    /** Zomboid B42's presets, in its order. Apocalypse is the default: our settings out of the box. */
    public static final List<String> PRESETS = List.of("apocalypse", "rising", "outbreak", "extinction", "six_months_later");
    public static final String CUSTOM = "custom";

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final List<SandboxOption> OPTIONS = SandboxOptions.all();
    private static final Map<String, SandboxOption> BY_ID = new LinkedHashMap<>();

    static {
        for (SandboxOption o : OPTIONS) {
            if (BY_ID.put(o.id, o) != null) {
                throw new IllegalStateException("Two sandbox settings called " + o.id);
            }
        }
    }

    /** The world's values by setting id, and the preset they came from (custom once anything is changed). */
    private static final Map<String, String> VALUES = new LinkedHashMap<>();
    private static String preset = PRESETS.get(0);
    /** The config's own values for the keys a world has covered, put back when it closes and kept in its file. */
    private static final Map<String, String> BASELINE = new HashMap<>();
    private static final Map<String, JsonElement> BASELINE_JSON = new HashMap<>();
    private static Path file;
    private static MinecraftServer server;
    /** Set by the create-world page: what the next new world starts with. */
    private static String pendingPreset;
    private static Map<String, String> pendingValues;

    // ---- the registry, for the Sandbox page ----

    public static List<SandboxGroup> groups() {
        return List.of(SandboxGroup.values());
    }

    public static List<SandboxOption> options() {
        return OPTIONS;
    }

    public static List<SandboxOption> options(SandboxGroup group) {
        return OPTIONS.stream().filter(o -> o.group == group).toList();
    }

    public static SandboxOption option(String id) {
        return BY_ID.get(id);
    }

    /** The value a setting has in this world (or, for one on your own screen, on your PC). */
    public static String value(String id) {
        SandboxOption o = BY_ID.get(id);
        if (o == null) {
            throw new IllegalArgumentException("No sandbox setting " + id);
        }
        if (o.kind == SandboxOption.Kind.FIXED) {
            return o.defaultValue();
        }
        if (o.client && o.key != null) {
            return o.configValue();
        }
        String v = VALUES.get(id);
        return v != null ? v : o.configValue() != null ? o.configValue() : o.defaultValue();
    }

    public static Map<String, String> values() {
        return Collections.unmodifiableMap(VALUES);
    }

    public static String preset() {
        return preset;
    }

    /** The create-world page calls this before the world starts; the new world takes these, then they're forgotten. */
    public static void forNewWorld(String presetName, Map<String, String> values) {
        pendingPreset = presetName;
        pendingValues = values == null ? Map.of() : Map.copyOf(values);
    }

    /** Values a preset gives every per-world setting. */
    public static Map<String, String> presetValues(String presetName) {
        Map<String, String> out = new LinkedHashMap<>();
        for (SandboxOption o : OPTIONS) {
            if (o.perWorld()) {
                out.put(o.id, o.presetValue(presetName));
            }
        }
        return out;
    }

    // ---- the world's store ----

    public static void register() {
        SandboxNet.register();
        SandboxCommand.register();
        ServerLifecycleEvents.SERVER_STARTING.register(Sandbox::open);
        ServerLifecycleEvents.SERVER_STOPPED.register(s -> close());
        ServerPlayConnectionEvents.JOIN.register((handler, sender, s) -> s.execute(() -> sync(handler.getPlayer())));
    }

    private static void open(MinecraftServer s) {
        server = s;
        file = s.getWorldPath(LevelResource.ROOT).resolve("zomboidcraft_sandbox.json");
        VALUES.clear();
        preset = PRESETS.get(0);
        if (Files.exists(file)) {
            read();
            pendingPreset = null;
            pendingValues = null;
        } else if (pendingPreset != null) {
            // a new world made on the create-world page
            preset = pendingPreset;
            VALUES.putAll(presetValues(pendingPreset));
            pendingValues.forEach((id, v) -> {
                SandboxOption o = BY_ID.get(id);
                if (o != null && o.perWorld()) {
                    VALUES.put(id, o.check(v));
                }
            });
            pendingPreset = null;
            pendingValues = null;
        } else {
            // a world from before the Sandbox page, or one made without it: it keeps running as it did
            for (SandboxOption o : OPTIONS) {
                if (o.perWorld()) {
                    VALUES.put(o.id, o.configValue() != null ? o.configValue() : o.defaultValue());
                }
            }
            preset = matching();
        }
        BASELINE.clear();
        BASELINE_JSON.clear();
        for (SandboxOption o : OPTIONS) {
            if (o.perWorld() && o.key != null) {
                BASELINE.put(o.key, ZcConfig.getValue(o.key));
                BASELINE_JSON.put(o.key, ZcConfig.jsonValue(o.key));
            }
        }
        applyAll();
        write();
    }

    private static void close() {
        BASELINE.forEach(ZcConfig::applyValue);
        BASELINE.clear();
        BASELINE_JSON.clear();
        VALUES.clear();
        server = null;
        file = null;
    }

    /** /zc config reload: the file is read again, then this world's values go back on top. */
    public static void reloadConfig() {
        Map<String, String> keep = new LinkedHashMap<>(VALUES);
        boolean open = server != null;
        BASELINE.clear();
        BASELINE_JSON.clear();
        ZcConfig.load();
        if (open) {
            for (SandboxOption o : OPTIONS) {
                if (o.perWorld() && o.key != null) {
                    BASELINE.put(o.key, ZcConfig.getValue(o.key));
                    BASELINE_JSON.put(o.key, ZcConfig.jsonValue(o.key));
                }
            }
            VALUES.putAll(keep);
            applyAll();
        }
    }

    /** /zc config changed a key: if the world covers it, the world keeps the new value. */
    public static void configChanged(String key) {
        if (server == null) {
            return;
        }
        for (SandboxOption o : OPTIONS) {
            if (o.perWorld() && key.equals(o.key)) {
                VALUES.put(o.id, ZcConfig.getValue(key));
                preset = matching();
                write();
                syncAll();
            }
        }
    }

    /** Sets one setting in this world (the host's change from the page or /zc sandbox). */
    public static String set(String id, String raw) {
        SandboxOption o = BY_ID.get(id);
        if (o == null) {
            throw new IllegalArgumentException("No sandbox setting " + id);
        }
        String v = o.check(raw);
        if (o.client) {
            if (o.key != null) {
                ZcConfig.setValue(o.key, v);
            }
            return v;
        }
        if (server == null) {
            throw new IllegalArgumentException("No world is open");
        }
        VALUES.put(id, v);
        if (o.key != null) {
            ZcConfig.applyValue(o.key, v);
        }
        preset = matching();
        write();
        syncAll();
        return v;
    }

    /** Puts every per-world setting to a preset's values. */
    public static void applyPreset(String presetName) {
        if (!PRESETS.contains(presetName)) {
            throw new IllegalArgumentException("Presets: " + String.join(", ", PRESETS));
        }
        if (server == null) {
            throw new IllegalArgumentException("No world is open");
        }
        VALUES.putAll(presetValues(presetName));
        preset = presetName;
        applyAll();
        write();
        syncAll();
    }

    /** config/zomboidcraft.json is being written: put back the values it had before this world, for the keys it covers. */
    public static void unapply(JsonObject tree) {
        BASELINE_JSON.forEach((key, json) -> {
            String[] parts = key.split("\\.", 2);
            JsonElement group = tree.get(parts[0]);
            if (group != null && group.isJsonObject()) {
                group.getAsJsonObject().add(parts[1], json);
            }
        });
    }

    private static void applyAll() {
        for (SandboxOption o : OPTIONS) {
            String v = VALUES.get(o.id);
            if (v != null && o.perWorld() && o.key != null) {
                try {
                    ZcConfig.applyValue(o.key, v);
                } catch (IllegalArgumentException e) {
                    ZomboidCraft.LOGGER.warn("Sandbox setting {} = {} not taken: {}", o.id, v, e.getMessage());
                }
            }
        }
    }

    /** The preset whose values this world has exactly, or custom. */
    private static String matching() {
        for (String p : PRESETS) {
            if (presetValues(p).entrySet().stream().allMatch(e -> e.getValue().equals(VALUES.get(e.getKey())))) {
                return p;
            }
        }
        return CUSTOM;
    }

    private static void read() {
        try (Reader reader = Files.newBufferedReader(file)) {
            JsonObject root = GSON.fromJson(reader, JsonObject.class);
            if (root.has("preset")) {
                preset = root.get("preset").getAsString();
            }
            JsonObject values = root.getAsJsonObject("values");
            // settings added since the world was made start at the world's preset
            Map<String, String> start = presetValues(PRESETS.contains(preset) ? preset : PRESETS.get(0));
            for (SandboxOption o : OPTIONS) {
                if (!o.perWorld()) {
                    continue;
                }
                String v = values != null && values.has(o.id) ? values.get(o.id).getAsString() : start.get(o.id);
                try {
                    VALUES.put(o.id, o.check(v));
                } catch (IllegalArgumentException e) {
                    VALUES.put(o.id, start.get(o.id));
                }
            }
            // keep values for settings this version doesn't know, so an older jar doesn't lose them
            if (values != null) {
                values.entrySet().forEach(e -> VALUES.putIfAbsent(e.getKey(), e.getValue().getAsString()));
            }
        } catch (IOException | RuntimeException e) {
            ZomboidCraft.LOGGER.error("Couldn't read {}, using the config's settings", file, e);
            for (SandboxOption o : OPTIONS) {
                if (o.perWorld()) {
                    VALUES.put(o.id, o.configValue() != null ? o.configValue() : o.defaultValue());
                }
            }
        }
    }

    private static void write() {
        if (file == null) {
            return;
        }
        JsonObject root = new JsonObject();
        root.addProperty("preset", preset);
        JsonObject values = new JsonObject();
        VALUES.forEach(values::addProperty);
        root.add("values", values);
        try (Writer writer = Files.newBufferedWriter(file)) {
            GSON.toJson(root, writer);
        } catch (IOException e) {
            ZomboidCraft.LOGGER.error("Couldn't save {}", file, e);
        }
    }

    // ---- everyone in the world runs on its values ----

    static void sync(ServerPlayer player) {
        ServerPlayNetworking.send(player, new SandboxNet.Sync(preset, new HashMap<>(VALUES)));
    }

    private static void syncAll() {
        if (server != null) {
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                sync(p);
            }
        }
    }

    /** A client joined to someone else's world takes its values (an integrated server already shares them). */
    public static void clientSync(String presetName, Map<String, String> values) {
        if (server != null) {
            return;
        }
        if (BASELINE.isEmpty()) {
            for (SandboxOption o : OPTIONS) {
                if (o.perWorld() && o.key != null) {
                    BASELINE.put(o.key, ZcConfig.getValue(o.key));
                    BASELINE_JSON.put(o.key, ZcConfig.jsonValue(o.key));
                }
            }
        }
        VALUES.clear();
        VALUES.putAll(values);
        preset = presetName;
        applyAll();
    }

    /** Left a world joined from outside: back to this PC's own settings. */
    public static void clientLeft() {
        if (server == null) {
            close();
        }
    }

    private Sandbox() {
    }
}
