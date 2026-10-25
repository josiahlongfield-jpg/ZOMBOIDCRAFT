package com.zomboidcraft.combat;

import com.mojang.serialization.Codec;
import com.zomboidcraft.ZcConfig;
import com.zomboidcraft.ZomboidCraft;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomModelData;
import org.jetbrains.annotations.Nullable;

/**
 * What's mounted on a gun, one attachment per slot. The helpers here change the gun stack only, so
 * the same calls serve the inventory screen, the stand-in right-click gesture and commands; run them
 * on the server (and on the client too when predicting a menu click, as vanilla does).
 *
 * Mounting also tells the gun's item model what to draw: the custom model data strings hold one model
 * key per slot, in {@link Slot} order ("" when empty). A mounted optic folds the iron sights away.
 */
public record Attachments(Map<Slot, ItemStack> mounted) {
    public enum Slot implements StringRepresentable {
        OPTIC, MUZZLE, UNDERBARREL, STOCK;

        public static final Codec<Slot> CODEC = StringRepresentable.fromEnum(Slot::values);
        public static final StreamCodec<io.netty.buffer.ByteBuf, Slot> STREAM_CODEC =
                ByteBufCodecs.idMapper(i -> values()[i], Slot::ordinal);

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public static final Attachments NONE = new Attachments(Map.of());

    public static final Codec<Attachments> CODEC = Codec.unboundedMap(Slot.CODEC, ItemStack.CODEC)
            .xmap(Attachments::new, Attachments::mounted);

    public static final StreamCodec<RegistryFriendlyByteBuf, Attachments> STREAM_CODEC =
            ByteBufCodecs.map(HashMap<Slot, ItemStack>::new, Slot.STREAM_CODEC, ItemStack.STREAM_CODEC)
                    .map(Attachments::new, a -> new HashMap<>(a.mounted()));

    public static final DataComponentType<Attachments> TYPE = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
            ZomboidCraft.id("attachments"), DataComponentType.<Attachments>builder()
                    .persistent(CODEC).networkSynchronized(STREAM_CODEC).cacheEncoding().build());

    public Attachments {
        EnumMap<Slot, ItemStack> copy = new EnumMap<>(Slot.class);
        mounted.forEach((slot, stack) -> {
            if (!stack.isEmpty()) {
                copy.put(slot, stack.copyWithCount(1));
            }
        });
        mounted = java.util.Collections.unmodifiableMap(copy);
    }

    /** What's in a slot, or {@link ItemStack#EMPTY}. A copy: change the gun through {@link #mount}. */
    public ItemStack get(Slot slot) {
        ItemStack stack = mounted.get(slot);
        return stack == null ? ItemStack.EMPTY : stack.copy();
    }

    public boolean isEmpty() {
        return mounted.isEmpty();
    }

    // ItemStack has no equals of its own, so compare and hash the stacks the way vanilla's item containers do
    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Attachments other) || !mounted.keySet().equals(other.mounted.keySet())) {
            return false;
        }
        for (Map.Entry<Slot, ItemStack> e : mounted.entrySet()) {
            if (!ItemStack.matches(e.getValue(), other.mounted.get(e.getKey()))) {
                return false;
            }
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 0;
        for (Map.Entry<Slot, ItemStack> e : mounted.entrySet()) {
            hash += e.getKey().hashCode() * 31 ^ ItemStack.hashItemAndComponents(e.getValue());
        }
        return hash;
    }

    // ---- helpers on gun stacks ----

    public static Attachments of(ItemStack gun) {
        return gun.getOrDefault(TYPE, NONE);
    }

    /** What's mounted in a slot of a gun, or {@link ItemStack#EMPTY}. */
    public static ItemStack get(ItemStack gun, Slot slot) {
        return of(gun).get(slot);
    }

    /** Whether the attachment fits this gun at all (whatever is in its slot now). */
    public static boolean canMount(ItemStack gun, ItemStack attachment) {
        return ZcConfig.get().guns.attachments && gun.getItem() instanceof RifleItem
                && attachment.getItem() instanceof AttachmentItem item && item.fits(gun);
    }

    /**
     * Mounts one of the attachment stack on the gun, swapping out whatever held its slot.
     * Returns what came off (empty if the slot was free), or null if it doesn't fit and nothing changed.
     * The caller takes the one mounted item out of wherever it came from.
     */
    public static @Nullable ItemStack mount(ItemStack gun, ItemStack attachment) {
        if (!canMount(gun, attachment)) {
            return null;
        }
        Slot slot = ((AttachmentItem) attachment.getItem()).slot();
        Map<Slot, ItemStack> next = new EnumMap<>(Slot.class);
        next.putAll(of(gun).mounted());
        ItemStack old = next.put(slot, attachment.copyWithCount(1));
        set(gun, new Attachments(next));
        return old == null ? ItemStack.EMPTY : old.copy();
    }

    /** Takes the attachment off a slot and returns it (empty if there was none). */
    public static ItemStack unmount(ItemStack gun, Slot slot) {
        Attachments now = of(gun);
        if (!now.mounted().containsKey(slot)) {
            return ItemStack.EMPTY;
        }
        Map<Slot, ItemStack> next = new EnumMap<>(Slot.class);
        next.putAll(now.mounted());
        ItemStack old = next.remove(slot);
        set(gun, new Attachments(next));
        return old.copy();
    }

    /** The first filled slot, optic first: what a bare right-click takes off. */
    public static @Nullable Slot firstFilled(ItemStack gun) {
        Attachments now = of(gun);
        for (Slot slot : Slot.values()) {
            if (now.mounted().containsKey(slot)) {
                return slot;
            }
        }
        return null;
    }

    private static void set(ItemStack gun, Attachments attachments) {
        if (attachments.isEmpty()) {
            gun.remove(TYPE);
        } else {
            gun.set(TYPE, attachments);
        }
        refreshModel(gun);
    }

    /** Multiplies aimed spread by what's mounted: a red dot settles the aim. */
    public static double aimSpreadFactor(ItemStack gun) {
        ItemStack optic = of(gun).mounted().get(Slot.OPTIC);
        return optic != null && optic.getItem() instanceof AttachmentItem ? ZcConfig.get().guns.redDotAimSpread : 1.0;
    }

    // ---- the item model's view ----

    /** Rewrites the attachment keys in the gun's model data, keeping the reload step and flash numbers. */
    public static void refreshModel(ItemStack gun) {
        CustomModelData old = gun.get(DataComponents.CUSTOM_MODEL_DATA);
        List<Float> floats = old == null ? List.of() : old.floats();
        writeModelData(gun, floats.isEmpty() ? 0 : floats.get(0), floats.size() < 2 ? 0 : floats.get(1));
    }

    /** Writes the gun's model data: [floats] reload step and flash, [strings] one model key per slot. */
    static void writeModelData(ItemStack gun, float step, float flash) {
        Attachments now = of(gun);
        List<String> keys = new ArrayList<>();
        for (Slot slot : Slot.values()) {
            ItemStack stack = now.mounted().get(slot);
            keys.add(stack != null && stack.getItem() instanceof AttachmentItem item ? item.modelKey() : "");
        }
        if (step == 0 && flash == 0 && now.isEmpty()) {
            gun.remove(DataComponents.CUSTOM_MODEL_DATA);
            return;
        }
        gun.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(List.of(step, flash), List.of(),
                now.isEmpty() ? List.of() : keys, List.of()));
    }

    public static void register() {
        // loading the class registers the component
    }
}
