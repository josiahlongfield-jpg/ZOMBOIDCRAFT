package com.zomboidcraft.combat;

import com.google.gson.Gson;
import com.mojang.serialization.Codec;
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
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

/**
 * Melee weapons and grenades kept on this computer rather than shipped in the mod (the Tarkov batch, 2026-11-06: the
 * Ka-Bar, the M48 Kukri, the F-1): like {@link LocalGuns}, each is one of our own items (its anchor, whose swing or
 * throw it keeps: "machete", "frag_grenade") carrying {@link #ID}, with its own name and Tarkov first person read from
 * {@code config/zomboidcraft/hands/<id>.json}. Whoever doesn't have the files sees and uses the plain anchor.
 */
public final class LocalHands {
    public static final DataComponentType<String> ID = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
            ZomboidCraft.id("local_hands"), DataComponentType.<String>builder()
                    .persistent(Codec.STRING).networkSynchronized(ByteBufCodecs.STRING_UTF8).build());

    public static final Path DIR = FabricLoader.getInstance().getConfigDir().resolve("zomboidcraft").resolve("hands");

    /** One file. */
    public static final class Spec {
        public String id;
        public String name;
        /** Our item it swings or throws like, by id ("machete", "kitchen_knife", "frag_grenade"). */
        public String anchor;
        /**
         * Its Tarkov first person (config/zomboidcraft/npc/NAME.zsk): Tarkov's arms and this weapon with its clips. A
         * blade's: hands_in, idle_armed, swing_0 and swing_1 (each swing in turn), look. A grenade's: hands_in,
         * idle_armed, pin (the pin pulled as the wind-up starts), hold (held ready), throw, look.
         */
        public String fp;
        /** Its own sounds' folder in the Tarkov sound pack (weapon.local/NAME/...); none: its id. */
        public String sounds;
        /** Handed to each new life (the starter kit, Josiah 2026-11-06: the combat knife beside the PM). */
        public boolean starter;
        transient Item item;
    }

    private static final Gson GSON = new Gson();
    private static final Map<String, Spec> SPECS = new HashMap<>();
    private static long loadedAt = -1;

    public static void register() {
        // loading the class registers the component
    }

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

    /** One of them, made from its anchor and going by its own name. */
    public static ItemStack make(Spec spec) {
        ItemStack stack = new ItemStack(spec.item);
        stack.set(ID, spec.id);
        if (spec.name != null) stack.set(DataComponents.ITEM_NAME, Component.literal(spec.name));
        return stack;
    }

    /** Reads the folder again. */
    public static synchronized void reload() {
        loadedAt = -1;
        load();
    }

    private static synchronized void load() {
        if (loadedAt >= 0) return;
        loadedAt = System.currentTimeMillis();
        SPECS.clear();
        if (!Files.isDirectory(DIR)) return;
        try (var files = Files.list(DIR)) {
            for (Path f : (Iterable<Path>) files::iterator) {
                if (!f.getFileName().toString().endsWith(".json")) continue;
                try (Reader r = Files.newBufferedReader(f, StandardCharsets.UTF_8)) {
                    Spec s = GSON.fromJson(r, Spec.class);
                    if (s == null || s.id == null || s.anchor == null) continue;
                    s.item = BuiltInRegistries.ITEM.getValue(ZomboidCraft.id(s.anchor));
                    if (s.item == null || s.item == Items.AIR) {
                        ZomboidCraft.LOGGER.warn("Local hands {}: no item of ours called {}", s.id, s.anchor);
                        continue;
                    }
                    SPECS.put(s.id, s);
                } catch (RuntimeException | java.io.IOException e) {
                    ZomboidCraft.LOGGER.error("Couldn't read local hands {}", f, e);
                }
            }
        } catch (java.io.IOException e) {
            ZomboidCraft.LOGGER.error("Couldn't list {}", DIR, e);
        }
        ZomboidCraft.LOGGER.info("Local melee and grenades: {} from {}", SPECS.size(), DIR);
    }

    private LocalHands() {
    }
}
