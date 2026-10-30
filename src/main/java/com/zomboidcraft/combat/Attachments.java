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
import net.minecraft.resources.Identifier;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomModelData;
import org.jetbrains.annotations.Nullable;

/**
 * What's fitted to a gun, one part per slot. Guns are modular: the body is always there, and the stock,
 * the magazine and everything on the rails come off and go on. The helpers here change the gun stack only,
 * so the same calls serve the weapon screen, the stand-in right-click gesture and commands; run them on the
 * server (and on the client too when predicting a menu click, as vanilla does).
 *
 * The magazine slot is the gun's {@link GunState}: fitting a magazine loads it, taking it off unloads it.
 * Which parts fit which gun comes from {@link GunSlotData}, generated with the models. A gun whose stack
 * says nothing has what it comes with (its stock), so found guns and guns from before attachments are whole.
 *
 * Fitting also tells the gun's item model what to draw: the custom model data strings hold one model key per
 * slot, in {@link Slot} order, "none" for an empty slot. A fitted optic folds the iron sights down.
 */
public record Attachments(Map<Slot, ItemStack> mounted) {
    public enum Slot implements StringRepresentable {
        // RIGHT_RAIL and MAGNIFIER came later: they go last so the model data's key order stays as it was
        OPTIC, MUZZLE, UNDERBARREL, SIDE_RAIL, STOCK, MAGAZINE, BIPOD, RIGHT_RAIL, MAGNIFIER;

        public static final Codec<Slot> CODEC = StringRepresentable.fromEnum(Slot::values);
        public static final StreamCodec<io.netty.buffer.ByteBuf, Slot> STREAM_CODEC =
                ByteBufCodecs.idMapper(i -> values()[i], Slot::ordinal);

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public static final Attachments NONE = new Attachments(Map.of());
    public static final String EMPTY_KEY = "none";

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
            if (!stack.isEmpty() && slot != Slot.MAGAZINE) {
                // a part saved in another slot (the laser shared the side rail with the light before it had
                // its own on the right) moves to its own
                Slot own = stack.getItem() instanceof AttachmentItem item ? item.slot() : slot;
                if (!copy.containsKey(own)) {
                    copy.put(own, stack.copyWithCount(1));
                }
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

    // ---- which gun takes what ----

    /** The gun's id within the mod ("assault_rifle"), or null if it isn't one of ours. */
    public static @Nullable String gunId(ItemStack gun) {
        Identifier id = BuiltInRegistries.ITEM.getKey(gun.getItem());
        return ZomboidCraft.MOD_ID.equals(id.getNamespace()) && GunSlotData.FITS.containsKey(id.getPath()) ? id.getPath() : null;
    }

    /** Whether the stack is a gun with slots. */
    public static boolean isModular(ItemStack gun) {
        return gunId(gun) != null;
    }

    /** The slots this gun has, in order. */
    public static List<Slot> slots(ItemStack gun) {
        String id = gunId(gun);
        List<Slot> out = new ArrayList<>();
        if (id != null) {
            for (Slot slot : Slot.values()) {
                if (GunSlotData.FITS.get(id).containsKey(slot)) {
                    out.add(slot);
                }
            }
        }
        return out;
    }

    /** The slot a part goes in, or null if it isn't a gun part. */
    public static @Nullable Slot slotOf(ItemStack part) {
        if (part.getItem() instanceof AttachmentItem item) {
            return item.slot();
        }
        return part.getItem() instanceof MagazineItem ? Slot.MAGAZINE : null;
    }

    /** Whether this kind of part goes on this gun at all (whatever is in its slot now). */
    public static boolean fits(ItemStack gun, Item part) {
        String id = gunId(gun);
        Slot slot = slotOf(new ItemStack(part));
        if (id == null || slot == null) {
            return false;
        }
        List<String> parts = GunSlotData.FITS.get(id).get(slot);
        return parts != null && parts.contains(BuiltInRegistries.ITEM.getKey(part).getPath());
    }

    /** Whether the part can be fitted now: attachments are on in settings and it fits the gun. */
    public static boolean canMount(ItemStack gun, ItemStack part) {
        return ZcConfig.get().guns.attachments && !part.isEmpty() && fits(gun, part.getItem()) && goesWith(gun, part);
    }

    /** The magnifier only sits behind a red dot or holo sight (or open rails): never with a scope in front of it. */
    private static boolean goesWith(ItemStack gun, ItemStack part) {
        Slot slot = slotOf(part);
        if (slot == Slot.MAGNIFIER) {
            Optic optic = optic(gun);
            return optic == null || optic.zoom() >= 1 && !optic.overlay();
        }
        if (slot == Slot.OPTIC && hasMagnifier(gun) && part.getItem() instanceof AttachmentItem item) {
            Optic optic = optic(item.modelKey());
            return optic.zoom() >= 1 && !optic.overlay();
        }
        return true;
    }

    // ---- helpers on gun stacks ----

    /** What's fitted to the gun: its own record, or what it comes with if the stack says nothing. */
    public static Attachments of(ItemStack gun) {
        Attachments own = gun.get(TYPE);
        return own != null ? own : factory(gun);
    }

    private static Attachments factory(ItemStack gun) {
        String id = gunId(gun);
        Map<Slot, String> comes = id == null ? null : GunSlotData.DEFAULTS.get(id);
        if (comes == null || comes.isEmpty()) {
            return NONE;
        }
        Map<Slot, ItemStack> parts = new EnumMap<>(Slot.class);
        comes.forEach((slot, item) -> parts.put(slot, new ItemStack(BuiltInRegistries.ITEM.getValue(ZomboidCraft.id(item)))));
        return new Attachments(parts);
    }

    /** What's in a slot of a gun, or {@link ItemStack#EMPTY}. The magazine slot hands back the loaded magazine. */
    public static ItemStack get(ItemStack gun, Slot slot) {
        if (slot == Slot.MAGAZINE) {
            GunState g = gun.getOrDefault(GunState.TYPE, GunState.EMPTY_MAG);
            if (!g.mag() || magazine(gun) == null) {
                return ItemStack.EMPTY;     // out, or a tube gun's shells, which live in the gun
            }
            ItemStack mag = new ItemStack(magazine(gun));
            mag.set(GunState.MAG_ROUNDS, g.rounds());
            return mag;
        }
        return of(gun).get(slot);
    }

    /** The model key of what's in a slot, or null if it's empty. */
    public static @Nullable String key(ItemStack gun, Slot slot) {
        if (slot == Slot.MAGAZINE && GunType.of(gun) == GunType.CROSSBOW) {
            // a crossbow's bolt shows on its rail while it's loaded
            return gun.getOrDefault(GunState.TYPE, GunState.EMPTY_MAG).rounds() > 0 ? "bolt" : null;
        }
        if (slot == Slot.MAGAZINE) {
            return gun.getOrDefault(GunState.TYPE, GunState.EMPTY_MAG).mag() && magazine(gun) != null
                    ? GunSlotData.MODEL_KEYS.get(BuiltInRegistries.ITEM.getKey(magazine(gun)).getPath()) : null;
        }
        ItemStack stack = of(gun).mounted().get(slot);
        return stack != null && stack.getItem() instanceof AttachmentItem item ? item.modelKey() : null;
    }

    /** The magazine this gun takes, or null for a tube gun. */
    private static @Nullable Item magazine(ItemStack gun) {
        GunType type = GunType.of(gun);
        return (type == null ? GunType.ASSAULT_RIFLE : type).magazine();
    }

    public static boolean has(ItemStack gun, Slot slot, String key) {
        return key.equals(key(gun, slot));
    }

    /**
     * Fits one of the part stack to the gun, swapping out whatever held its slot.
     * Returns what came off (empty if the slot was free), or null if it doesn't fit and nothing changed.
     * The caller takes the one fitted item out of wherever it came from.
     */
    public static @Nullable ItemStack mount(ItemStack gun, ItemStack part) {
        if (!canMount(gun, part)) {
            return null;
        }
        Slot slot = slotOf(part);
        if (slot == Slot.MAGAZINE) {
            ItemStack old = get(gun, Slot.MAGAZINE);
            GunState g = gun.getOrDefault(GunState.TYPE, GunState.EMPTY_MAG);
            gun.set(GunState.TYPE, new GunState(true, Math.min(MagazineItem.rounds(part), RifleItem.capacity(gun)), g.jammed()));
            refreshModel(gun);
            return old;
        }
        Map<Slot, ItemStack> next = new EnumMap<>(Slot.class);
        next.putAll(of(gun).mounted());
        ItemStack old = next.put(slot, part.copyWithCount(1));
        set(gun, new Attachments(next));
        return old == null ? ItemStack.EMPTY : old.copy();
    }

    /** Takes the part off a slot and returns it (empty if there was none). */
    public static ItemStack unmount(ItemStack gun, Slot slot) {
        if (slot == Slot.MAGAZINE) {
            ItemStack old = get(gun, Slot.MAGAZINE);
            if (!old.isEmpty()) {
                GunState g = gun.getOrDefault(GunState.TYPE, GunState.EMPTY_MAG);
                gun.set(GunState.TYPE, new GunState(false, 0, g.jammed()));
                refreshModel(gun);
            }
            return old;
        }
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

    /** The first filled slot, optic first and the magazine last: what a bare right-click takes off. */
    public static @Nullable Slot firstFilled(ItemStack gun) {
        for (Slot slot : slots(gun)) {
            if (key(gun, slot) != null) {
                return slot;
            }
        }
        return null;
    }

    private static void set(ItemStack gun, Attachments attachments) {
        // always written, even empty: an absent record means "what it came with"
        gun.set(TYPE, attachments);
        refreshModel(gun);
    }

    // ---- what the parts do ----

    /**
     * What an optic does on the sights: multiplies aimed spread, narrows the view to this share of normal (1: the
     * gun's own), how big its reticle (textures/gui/sight/<key>.png, drawn by tools/meshguns/reticles.py) is drawn as
     * a share of the screen's height (0: none), and whether looking through it shows its eyepiece picture in place of
     * the gun. Keyed by model key, as tools/meshguns/attachments.json lists them.
     */
    public record Optic(double spread, double zoom, double sight, boolean overlay) {
    }

    private static Optic optic(String key) {
        ZcConfig.Guns cfg = ZcConfig.get().guns;
        return switch (key) {
            case "red_dot", "mini_dot", "compact_dot" -> new Optic(cfg.redDotAimSpread, 1, 0.012, false);
            case "holo" -> new Optic(cfg.holoAimSpread, 1, 0.065, false);
            case "scope_4x" -> new Optic(cfg.scopeAimSpread, cfg.scopeZoom, 0, true);
            case "rifle_optic" -> new Optic(cfg.scopeAimSpread, 1 / 2.0, 0.5, false);
            case "combat_scope" -> new Optic(cfg.scopeAimSpread, 1 / 3.0, 0.5, false);
            case "pistol_scope" -> new Optic(cfg.scopeAimSpread, 1 / 2.0, 0.5, false);
            case "crossbow_scope" -> new Optic(cfg.scopeAimSpread, 1 / 3.0, 0, true);
            case "hunting_scope" -> new Optic(cfg.scopeAimSpread * 0.8, 1 / 6.0, 0, true);
            case "precision_scope" -> new Optic(cfg.scopeAimSpread * 0.7, 1 / 8.0, 0, true);
            case "long_scope" -> new Optic(cfg.scopeAimSpread * 0.6, 1 / 10.0, 0, true);
            default -> new Optic(1, 1, 0, false);
        };
    }

    /** Whether looking through this optic (by model key) shows the scope picture in place of the gun. */
    public static boolean overlay(String key) {
        return optic(key).overlay();
    }

    /** The optic on the gun, or null on its own sights. */
    public static @Nullable Optic optic(ItemStack gun) {
        String key = key(gun, Slot.OPTIC);
        return key == null ? null : optic(key);
    }

    /** A 3x magnifier is fitted behind the optic, swung in or aside. */
    public static boolean hasMagnifier(ItemStack gun) {
        return key(gun, Slot.MAGNIFIER) != null;
    }

    /** The magnifier is swung in behind the optic: the dot or ring in front is seen three times bigger. */
    public static boolean magnified(ItemStack gun) {
        return hasMagnifier(gun) && !gun.getOrDefault(GunState.MAGNIFIER_ASIDE, false);
    }

    /** Multiplies aimed spread: optics settle the aim, a gun with no stock wanders. */
    public static double aimSpreadFactor(ItemStack gun) {
        ZcConfig.Guns cfg = ZcConfig.get().guns;
        Optic optic = optic(gun);
        double f = optic == null ? 1.0 : optic.spread();
        if (magnified(gun)) {
            f = Math.min(f, cfg.scopeAimSpread);
        }
        return f * (stockless(gun) ? cfg.noStockSpread : 1.0);
    }

    /** Multiplies spread from the hip: a laser tightens it while it's switched on. */
    public static double hipSpreadFactor(ItemStack gun) {
        ZcConfig.Guns cfg = ZcConfig.get().guns;
        return (laserOn(gun) ? cfg.laserHipSpread : 1.0) * (stockless(gun) ? cfg.noStockSpread : 1.0);
    }

    public static boolean lightOn(ItemStack gun) {
        return gun.getOrDefault(GunState.RAIL_ON, false) && hasLight(gun);
    }

    public static boolean laserOn(ItemStack gun) {
        return gun.getOrDefault(GunState.LASER_ON, false) && hasLaser(gun);
    }

    public static boolean hasLight(ItemStack gun) {
        return key(gun, Slot.SIDE_RAIL) != null;        // the weapon light or the compact one
    }

    public static boolean hasLaser(ItemStack gun) {
        return has(gun, Slot.RIGHT_RAIL, "laser");
    }

    /** Multiplies the kick: any grip tames it, no stock makes it worse. */
    public static double recoilFactor(ItemStack gun) {
        ZcConfig.Guns cfg = ZcConfig.get().guns;
        String under = key(gun, Slot.UNDERBARREL);
        boolean grip = under != null && !under.equals("launcher");
        return (grip ? cfg.foregripRecoil : 1.0) * (stockless(gun) ? cfg.noStockRecoil : 1.0);
    }

    /** A bipod is folded down on the gun: crouched, it steadies the aim and soaks up the kick. */
    public static boolean hasBipod(ItemStack gun) {
        return key(gun, Slot.BIPOD) != null;
    }

    /** An underbarrel grenade launcher is mounted. */
    public static boolean hasLauncher(ItemStack gun) {
        return has(gun, Slot.UNDERBARREL, "launcher");
    }

    public static boolean suppressed(ItemStack gun) {
        return key(gun, Slot.MUZZLE) != null || GunType.of(gun) == GunType.SUPPRESSED_RIFLE;     // its can is built in
    }

    /** Looking down the sights shows the scope's eyepiece picture, not the gun: a scope you look into. */
    public static boolean scoped(ItemStack gun) {
        Optic optic = optic(gun);
        return optic != null && optic.overlay();
    }

    /** The field of view on the sights, as a share of normal, or a negative number for the gun's own. */
    public static double aimZoom(ItemStack gun) {
        Optic optic = optic(gun);
        double zoom = optic == null ? 1 : optic.zoom();
        if (magnified(gun)) {
            zoom = Math.min(zoom, 1 / 3.0);
        }
        return zoom < 1 ? zoom : -1;
    }

    /** The optic's model key, which names the picture or reticle it shows (textures/gui/sight), or null. */
    public static @Nullable String sightKey(ItemStack gun) {
        return key(gun, Slot.OPTIC);
    }

    /** How big the reticle of a sight the gun stays drawn behind shows, as a share of the screen's height: three
     * times bigger through the magnifier. 0 when there's none to draw. */
    public static double reticleSize(ItemStack gun) {
        Optic optic = optic(gun);
        return optic == null || optic.overlay() ? 0 : optic.sight() * (magnified(gun) ? 3 : 1);
    }

    private static boolean stockless(ItemStack gun) {
        return slots(gun).contains(Slot.STOCK) && key(gun, Slot.STOCK) == null;
    }

    // ---- the item model's view ----

    /** Writes the model data of a gun that has none yet (straight from a command or the creative tab), so it's
     * drawn with what it comes with. Returns true if it did. */
    public static boolean ensureModel(ItemStack gun) {
        if (!isModular(gun) || gun.has(DataComponents.CUSTOM_MODEL_DATA)) {
            return false;
        }
        refreshModel(gun);
        return true;
    }

    /** Rewrites the part keys in the gun's model data, keeping the reload step and flash numbers. */
    public static void refreshModel(ItemStack gun) {
        CustomModelData old = gun.get(DataComponents.CUSTOM_MODEL_DATA);
        List<Float> floats = old == null ? List.of() : old.floats();
        writeModelData(gun, floats.isEmpty() ? 0 : floats.get(0), floats.size() < 2 ? 0 : floats.get(1));
    }

    /** Writes the gun's model data: [floats] reload step and flash, [strings] one model key per slot, then its finish. */
    static void writeModelData(ItemStack gun, float step, float flash) {
        List<String> keys = new ArrayList<>();
        for (Slot slot : Slot.values()) {
            String key = key(gun, slot);
            if (key != null && slot == Slot.MAGNIFIER && !magnified(gun)) {
                key += "_aside";        // swung off the sight line on its mount
            }
            keys.add(key == null ? EMPTY_KEY : key);
        }
        String finish = GunFinish.key(gun);
        keys.add(finish == null ? EMPTY_KEY : finish);
        gun.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(List.of(step, flash), List.of(), keys, List.of()));
    }

    public static void register() {
        // loading the class registers the component
    }
}
