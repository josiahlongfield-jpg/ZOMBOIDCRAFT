package com.zomboidcraft.combat;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.mojang.serialization.Codec;
import com.zomboidcraft.ModItems;
import com.zomboidcraft.ZomboidCraft;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
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
 * Attachments kept on this computer for the local guns (Josiah's sight pack and Brita's grips, 2026-11-03): each is one
 * of our parts (a red dot sight for a sight, a foregrip for a grip) carrying {@link #ID}, with its name, which local
 * guns it fits and how it sights read from {@code config/zomboidcraft/guns/parts/parts.json} (written on this computer by
 * brita/tools/attach/pack_parts.py, never shipped), its look from {@code <id>.zmesh} beside it. It fits only the local
 * guns its file names, where Josiah placed it in the posing tool (client GunPoses); whoever hasn't the files can't fit it.
 */
public final class LocalParts {
    public static final DataComponentType<String> ID = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
            ZomboidCraft.id("local_part"), DataComponentType.<String>builder()
                    .persistent(Codec.STRING).networkSynchronized(ByteBufCodecs.STRING_UTF8).build());

    public static final Path DIR = LocalGuns.DIR.resolve("parts");

    /** One part's line in parts.json. Sizes are metres in the gun's frame (x right, y up, z back), origin where it mounts. */
    public static final class Meta {
        public String name;
        /** "optic" or "grip". */
        public String kind;
        /** The local guns it fits. */
        public List<String> mount = List.of();
        /** Its magnification (1: a red dot or holo). */
        public float mag = 1;
        /** Its sight line's height, the front and back of its body along z. */
        public float line, front, back;
        /** A scope's eyepiece: [z, radius]. */
        public float[] eyepiece;
        /** Its lit reticles' meshes (red dots, holo, a piggyback dot), drawn glowing in it. */
        public List<String> lit = List.of();
        /** A scope's own reticle picture (<id>_reticle.png): its radius. */
        public Reticle reticle;
    }

    public static final class Reticle {
        public float r, z;
    }

    private static final Gson GSON = new Gson();
    private static Map<String, Meta> metas = Map.of();
    private static int loadedFor = -1;

    public static void register() {
        // loading the class registers the component
    }

    public static synchronized @Nullable Meta get(@Nullable String id) {
        if (id == null) return null;
        if (loadedFor != LocalGuns.generation()) {
            loadedFor = LocalGuns.generation();
            metas = read();
        }
        return metas.get(id);
    }

    public static synchronized java.util.Set<String> ids() {
        get("");
        return metas.keySet();
    }

    private static Map<String, Meta> read() {
        Path f = DIR.resolve("parts.json");
        if (!Files.isRegularFile(f)) return Map.of();
        try (Reader r = Files.newBufferedReader(f, StandardCharsets.UTF_8)) {
            Map<String, Meta> m = GSON.fromJson(r, new TypeToken<Map<String, Meta>>() { }.getType());
            return m == null ? Map.of() : new HashMap<>(m);
        } catch (RuntimeException | java.io.IOException e) {
            ZomboidCraft.LOGGER.error("Couldn't read local parts {}", f, e);
            return Map.of();
        }
    }

    /** The local part a stack is, or null. */
    public static @Nullable String of(ItemStack part) {
        return part.isEmpty() ? null : part.get(ID);
    }

    /** Whether this local part goes on this gun: a local gun its file names, in the slot of its kind. */
    public static boolean fits(ItemStack gun, ItemStack part) {
        String gunId = gun.get(LocalGuns.ID);
        Meta m = get(of(part));
        if (gunId == null || m == null || !m.mount.contains(gunId)) return false;
        Attachments.Slot slot = Attachments.slotOf(part);
        return "optic".equals(m.kind) ? slot == Attachments.Slot.OPTIC : "grip".equals(m.kind) && slot == Attachments.Slot.UNDERBARREL;
    }

    /** Whether any local part of this computer goes on this local gun in this slot. */
    public static synchronized boolean takes(String gunId, Attachments.Slot slot) {
        String kind = slot == Attachments.Slot.OPTIC ? "optic" : slot == Attachments.Slot.UNDERBARREL ? "grip" : null;
        if (kind == null) return false;
        get("");
        for (Meta m : metas.values()) {
            if (kind.equals(m.kind) && m.mount.contains(gunId)) return true;
        }
        return false;
    }

    /** The local part fitted in a gun's slot, or null. */
    public static @Nullable String fitted(ItemStack gun, Attachments.Slot slot) {
        if (gun.get(LocalGuns.ID) == null) return null;
        String id = of(Attachments.of(gun).get(slot));
        return get(id) == null ? null : id;
    }

    /** A new one of a local part, or empty if this computer hasn't got it. */
    public static ItemStack make(String id) {
        Meta m = get(id);
        if (m == null) return ItemStack.EMPTY;
        ItemStack s = new ItemStack("grip".equals(m.kind) ? ModItems.FOREGRIP : ModItems.RED_DOT_SIGHT);
        s.set(ID, id);
        if (m.name != null) s.set(DataComponents.ITEM_NAME, Component.literal(m.name));
        return s;
    }

    private LocalParts() {
    }
}
