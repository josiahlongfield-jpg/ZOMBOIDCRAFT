package com.zomboidcraft.combat;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;
import com.mojang.serialization.Codec;
import com.zomboidcraft.ModItems;
import com.zomboidcraft.ZomboidCraft;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * The Tarkov parts kept on this computer for the Tarkov guns (Josiah 2026-11-07: every attachment pulled for them made
 * game ready, fitted at the bench, looted ones too): {@code config/zomboidcraft/guns/tarkov/parts.json}, written on his
 * PC from the game files and never shipped. Each part is Tarkov's own item (its id), with the bench slot it goes in, its
 * own slots for other parts (where each sits in the frame of the slot holding this part, and which parts fit it, Tarkov's
 * filters), and what it does: a sight's eye points with their modes (zoom, reticle, reticle span), a grip's hold, a
 * device's laser and light, a magazine's size. Its look is the model {@code tkp_<id>} in config/zomboidcraft/npc. A part
 * in hand is a {@link ModItems#TARKOV_PART} carrying {@link #ID}.
 */
public final class TarkovParts {
    public static final DataComponentType<String> ID = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
            ZomboidCraft.id("tarkov_part"), DataComponentType.<String>builder()
                    .persistent(Codec.STRING).networkSynchronized(ByteBufCodecs.STRING_UTF8).build());

    public static final Path FILE = LocalGuns.DIR.resolve("tarkov").resolve("parts.json");

    /** One part. Places are metres and a turn (x, y, z, qx, qy, qz, qw) in the frame of the slot holding it. */
    public static final class Part {
        public String name;
        @SerializedName("short")
        public String shortName;
        /** Tarkov's kind ("Foregrip", "AssaultScope", "Mount", ... "Weapon" for a gun's own base). */
        public String kind;
        /** The bench slot it goes in ("optic", "underbarrel", ...); none: fitted only as a link (a mount). */
        public String socket;
        /** Its look: the model in config/zomboidcraft/npc; none for a gun's base (drawn by the gun's own model). */
        public String model;
        public Map<String, Port> slots = new LinkedHashMap<>();
        public List<String> conflicts = List.of();
        public int capacity;
        /** Its size in inventory cells, Tarkov's Width and Height (w, h); none: one cell. */
        public int[] cells;
        public float[] fireport;
        /** Tarkov's left-hand holds on it: wrapped round it (a grip's), cradled under it (a forend's). */
        public LocalGuns.Hold hold, cradle;
        public Device laser, light;
        public List<Aim> aims;
        transient Attachments.Slot slot;

        public Attachments.@Nullable Slot slot() {
            return slot;
        }

        public boolean is(String k) {
            return k.equals(kind);
        }

        public boolean sight() {
            return slot == Attachments.Slot.OPTIC;
        }
    }

    /** One of a part's own slots: where it sits, what goes in it, and whether the gun needs it filled. */
    public static final class Port {
        public float[] at;
        public List<String> filter = List.of();
        public boolean required;
    }

    public static final class Device {
        public float[] at, dir;
    }

    /** A sight's eye point (its mod_aim_camera, looking down -z) and its modes in order. */
    public static final class Aim {
        public float[] at;
        public List<Mode> modes = List.of();
        /** A variable scope: its two modes are the ends of its range. */
        public boolean variable;
    }

    /** One mode: its magnification (1: none), its reticle (a texture of the part's model) and, unmagnified, its span. */
    public static final class Mode {
        public float zoom = 1;
        public String reticle;
        public float moa;
    }

    private static final Gson GSON = new Gson();
    private static Map<String, Part> parts = Map.of();
    private static int loadedFor = -1;

    public static void register() {
        // loading the class registers the component
    }

    public static synchronized @Nullable Part get(@Nullable String id) {
        if (id == null) return null;
        if (loadedFor != LocalGuns.generation()) {
            loadedFor = LocalGuns.generation();
            parts = read();
            TarkovFit.forget();
        }
        return parts.get(id);
    }

    public static synchronized Map<String, Part> all() {
        get("");
        return parts;
    }

    private static Map<String, Part> read() {
        if (!Files.isRegularFile(FILE)) return Map.of();
        try (Reader r = Files.newBufferedReader(FILE, StandardCharsets.UTF_8)) {
            Map<String, Part> m = GSON.fromJson(r, new TypeToken<LinkedHashMap<String, Part>>() { }.getType());
            if (m == null) return Map.of();
            for (Part p : m.values()) {
                if (p.slots == null) p.slots = new LinkedHashMap<>();
                if (p.conflicts == null) p.conflicts = List.of();
                for (Port port : p.slots.values()) {
                    if (port.filter == null) port.filter = List.of();
                }
                p.slot = slotOf(p.socket);
            }
            return new HashMap<>(m);
        } catch (RuntimeException | java.io.IOException e) {
            ZomboidCraft.LOGGER.error("Couldn't read Tarkov parts {}", FILE, e);
            return Map.of();
        }
    }

    static Attachments.@Nullable Slot slotOf(@Nullable String socket) {
        if (socket == null) return null;
        try {
            return Attachments.Slot.valueOf(socket.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** The Tarkov part a stack is, or null. */
    public static @Nullable String of(@Nullable ItemStack stack) {
        return stack == null || stack.isEmpty() ? null : stack.get(ID);
    }

    /** That part's data, or null (not a Tarkov part, or this computer hasn't its file). */
    public static @Nullable Part part(@Nullable ItemStack stack) {
        return get(of(stack));
    }

    /** A new one of a part, or empty if this computer hasn't got it or it isn't fitted on its own (a mount). */
    public static ItemStack make(String id) {
        Part p = get(id);
        if (p == null || p.slot == null) return ItemStack.EMPTY;
        ItemStack s = new ItemStack(ModItems.TARKOV_PART);
        s.set(ID, id);
        if (p.name != null) s.set(DataComponents.ITEM_NAME, Component.literal(p.name));
        return s;
    }

    private TarkovParts() {
    }
}
