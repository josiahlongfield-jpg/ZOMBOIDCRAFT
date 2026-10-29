package com.zomboidcraft.client.kit;

import com.zomboidcraft.ZcConfig;
import com.zomboidcraft.ZomboidCraft;
import com.zomboidcraft.barricade.HammerItem;
import com.zomboidcraft.carry.BagItem;
import com.zomboidcraft.carry.BagSpec;
import com.zomboidcraft.combat.AttachmentItem;
import com.zomboidcraft.combat.Attachments;
import com.zomboidcraft.combat.GunState;
import com.zomboidcraft.combat.GunType;
import com.zomboidcraft.combat.MagazineItem;
import com.zomboidcraft.combat.MeleeWeaponItem;
import com.zomboidcraft.combat.RifleItem;
import com.zomboidcraft.combat.WeaponSpecs;
import com.zomboidcraft.combat.Weapons;
import com.zomboidcraft.survival.BandageItem;
import com.zomboidcraft.survival.SplintItem;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import org.jetbrains.annotations.Nullable;

/**
 * What the kit screen says about an item: its rarity and tier, what kind of thing it is, its condition, and the stat
 * bars on its card. Rarity and tier come from the weapon rolls when the mod has them, and fall back to Common with
 * no tier before then.
 */
final class KitItems {
    enum Rarity {
        COMMON("Common", 0xFFC9C0AD, 0xFF1D1B16, 0xFF5C5850, 0xFF34312C, 0xFFE8DFC8),
        SCARCE("Scarce", 0xFF5D6A34, 0xFFF2EFE8, 0xFF525D30, 0xFF23281A, 0xFFA8BC6E),
        RARE("Rare", 0xFF4A6D97, 0xFFF2EFE8, 0xFF3D5A7D, 0xFF1B2636, 0xFF7FAEDB),
        PRIZED("Prized", 0xFF6E2626, 0xFFF2EFE8, 0xFF5C2424, 0xFF2A1212, 0xFFD0675E),
        GRAIL("Grail", 0xFFB08D3E, 0xFF1D1B16, 0xFF6B5626, 0xFF2F2611, 0xFFE2B84C);

        final String label;
        /** The card's header band and the text on it; the tile's gradient; the word in lists. */
        final int band, bandText, tileTop, tileBottom, text;

        Rarity(String label, int band, int bandText, int tileTop, int tileBottom, int text) {
            this.label = label;
            this.band = band;
            this.bandText = bandText;
            this.tileTop = tileTop;
            this.tileBottom = tileBottom;
            this.text = text;
        }
    }

    // ------------------------------------------------------------------ rolls, when the mod has them

    private static boolean looked;
    private static Method rarityOf, rollOf, tierLabel, statOf;

    private static void look() {
        if (looked) return;
        looked = true;
        try {
            rarityOf = Class.forName("com.zomboidcraft.combat.rolls.Rarity").getMethod("of", ItemStack.class);
            rollOf = Class.forName("com.zomboidcraft.combat.rolls.Rolls").getMethod("get", ItemStack.class);
            tierLabel = Class.forName("com.zomboidcraft.combat.rolls.Tier").getMethod("label", int.class);
            statOf = Class.forName("com.zomboidcraft.combat.rolls.Stat").getMethod("of", ItemStack.class);
        } catch (ReflectiveOperationException | LinkageError e) {
            rarityOf = null;
            rollOf = null;
        }
    }

    static Rarity rarity(ItemStack stack) {
        look();
        if (rarityOf != null && !stack.isEmpty()) {
            try {
                Object r = rarityOf.invoke(null, stack);
                if (r instanceof Enum<?> e) return Rarity.valueOf(e.name());
            } catch (ReflectiveOperationException | IllegalArgumentException e) {
                rarityOf = null;
            }
        }
        return Rarity.COMMON;
    }

    private static @Nullable Object roll(ItemStack stack) {
        look();
        if (rollOf == null || stack.isEmpty()) return null;
        try {
            return rollOf.invoke(null, stack);
        } catch (ReflectiveOperationException | IllegalArgumentException e) {
            rollOf = null;
            return null;
        }
    }

    private static @Nullable Object call(@Nullable Object on, String method) {
        if (on == null) return null;
        try {
            return on.getClass().getMethod(method).invoke(on);
        } catch (ReflectiveOperationException | IllegalArgumentException e) {
            return null;
        }
    }

    /** Tier 1-5 of a rolled item, or 0 when it has no roll. */
    static int tier(ItemStack stack) {
        return call(roll(stack), "tier") instanceof Integer t ? t : 0;
    }

    static String tierName(int tier) {
        if (tierLabel != null) {
            try {
                return String.valueOf(tierLabel.invoke(null, tier));
            } catch (ReflectiveOperationException | IllegalArgumentException ignored) {
                // fall through to the plain number
            }
        }
        return "Tier " + tier;
    }

    /** Flaws rolled into a weapon (a pitted bore, a loose stock), or 0. */
    static List<String> flaws(ItemStack stack) {
        List<String> out = new ArrayList<>();
        if (roll(stack) == null) return out;
        try {
            Object list = Class.forName("com.zomboidcraft.combat.rolls.Rolls").getMethod("flaws", ItemStack.class).invoke(null, stack);
            if (list instanceof List<?> flaws) {
                for (Object f : flaws) out.add(String.valueOf(call(f, "label")));
            }
        } catch (ReflectiveOperationException | IllegalArgumentException | LinkageError ignored) {
            out.clear();
        }
        return out;
    }

    /** Where a weapon was found, in words, when its roll remembers. */
    static @Nullable String place(ItemStack stack) {
        look();
        if (roll(stack) == null) return null;
        try {
            Object label = Class.forName("com.zomboidcraft.combat.rolls.Rolls").getMethod("placeLabel", ItemStack.class).invoke(null, stack);
            String s = label instanceof net.minecraft.network.chat.Component c ? c.getString() : label == null ? "" : label.toString();
            return s.isEmpty() ? null : s;
        } catch (ReflectiveOperationException | IllegalArgumentException | LinkageError e) {
            return null;
        }
    }

    /** A weapon's rolled stats as label and 0-100, or empty when it has no roll. */
    static List<Stat> rolled(ItemStack stack) {
        List<Stat> out = new ArrayList<>();
        Object roll = roll(stack);
        if (roll == null || statOf == null) return out;
        try {
            Class<?> statClass = Class.forName("com.zomboidcraft.combat.rolls.Stat");
            Class<?> rolls = Class.forName("com.zomboidcraft.combat.rolls.Rolls");
            Method position = rolls.getMethod("position", ItemStack.class, statClass);
            Method percent = rolls.getMethod("percent", ItemStack.class, statClass);
            for (Object stat : (List<?>) statOf.invoke(null, stack)) {
                float at = ((Number) position.invoke(null, stack, stat)).floatValue();
                Object pct = percent.invoke(null, stack, stat);
                String shown = pct instanceof Number n ? (n.doubleValue() > 0 ? "+" : "") + Math.round(n.doubleValue()) + "%" : String.valueOf(pct);
                out.add(new Stat(String.valueOf(call(stat, "label")), at, Float.NaN, shown));
            }
        } catch (ReflectiveOperationException | ClassCastException | IllegalArgumentException | LinkageError ignored) {
            out.clear();
        }
        return out;
    }

    // ------------------------------------------------------------------ what it is

    static boolean weapon(ItemStack stack) {
        return stack.getItem() instanceof RifleItem || stack.getItem() instanceof MeleeWeaponItem;
    }

    /** Long guns go on the first weapon slot; pistols and revolvers on the second. */
    static boolean sidearm(ItemStack stack) {
        GunType t = GunType.of(stack);
        return t == GunType.PISTOL || t == GunType.REVOLVER || t == GunType.MAGNUM;
    }

    static boolean gun(ItemStack stack) {
        return stack.getItem() instanceof RifleItem;
    }

    static @Nullable EquipmentSlot wornOn(ItemStack stack) {
        var eq = stack.get(DataComponents.EQUIPPABLE);
        return eq == null ? null : eq.slot();
    }

    static String id(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
    }

    /** One short line on what kind of thing it is: "Bolt-action rifle", "Blunt, two-handed", "Back bag, 28 kg". */
    static String kind(ItemStack stack) {
        GunType t = GunType.of(stack);
        if (t != null) {
            String what = switch (t) {
                case ASSAULT_RIFLE -> "Self-loading rifle";
                case PISTOL -> "Pistol";
                case SHOTGUN -> "Pump shotgun";
                case HUNTING_RIFLE -> "Bolt-action rifle";
                case DOUBLE_BARREL -> "Break-action shotgun";
                case REVOLVER -> "Revolver";
                case RIMFIRE -> "Self-loading carbine";
                case SMG -> "Submachine gun";
                case SAWN_OFF -> "Sawn-off shotgun";
                case MAGNUM -> "Magnum revolver";
                case LEVER_ACTION -> "Lever-action carbine";
                case CROSSBOW -> "Compound crossbow";
                case BATTLE_RIFLE -> "Battle rifle";
                case POCKET_PISTOL -> "Pocket pistol";
                case MACHINE_PISTOL -> "Machine pistol";
                case SUPPRESSED_RIFLE -> "Suppressed rifle";
                case PRECISION_RIFLE -> "Bolt-action precision rifle";
                case HEAVY_SNIPER -> "Heavy sniper rifle";
                case GRENADE_LAUNCHER -> "Six-shot grenade launcher";
            };
            return what + ", " + t.caliber();
        }
        if (stack.getItem() instanceof MeleeWeaponItem) {
            WeaponSpecs.WeaponSpec spec = spec(stack);
            if (spec == null) return "Melee weapon";
            String kind = switch (spec.kind()) {
                case SHORT_BLADE -> "Short blade";
                case SMALL_BLUNT -> "Small blunt";
                case BLUNT -> "Blunt";
                case LONG_BLADE -> "Long blade";
                case AXE -> "Axe";
                case HEAVY_BLUNT -> "Heavy blunt";
            };
            return kind + (spec.twoHanded() ? ", two-handed" : ", one-handed");
        }
        BagSpec bag = BagItem.spec(stack);
        if (bag != null) {
            String where = switch (bag.wear()) {
                case BACK -> "Back";
                case VEST -> "Vest";
                case WAIST -> "Waist";
                case HAND -> "Carried";
            };
            return where + " bag, holds " + com.zomboidcraft.carry.Weights.format(bag.capacity());
        }
        if (stack.getItem() instanceof AttachmentItem part) return "Gun part, " + AttachmentItem.slotName(part.slot()).toLowerCase();
        if (stack.getItem() instanceof MagazineItem) return "Magazine, " + MagazineItem.rounds(stack) + " loaded";
        EquipmentSlot worn = wornOn(stack);
        if (worn != null && worn.getType() == EquipmentSlot.Type.HUMANOID_ARMOR) return "Clothing";
        return category(stack).label;
    }

    static WeaponSpecs.@Nullable WeaponSpec spec(ItemStack stack) {
        return Weapons.spec(stack);
    }

    // ------------------------------------------------------------------ supplies

    enum Category {
        ALL("All"), WEAPONS("Weapons"), MEDICAL("Medical"), FOOD("Food & drink"), AMMO("Ammunition"),
        GUN_PARTS("Gun parts"), CLOTHING("Clothing & bags"), TOOLS("Tools"), MATERIALS("Materials");

        final String label;

        Category(String label) {
            this.label = label;
        }
    }

    static Category category(ItemStack stack) {
        var item = stack.getItem();
        if (item instanceof HammerItem) return Category.TOOLS;
        if (weapon(stack)) return Category.WEAPONS;
        if (item instanceof BandageItem || item instanceof SplintItem || stack.has(DataComponents.POTION_CONTENTS)) return Category.MEDICAL;
        if (stack.has(DataComponents.FOOD) || stack.has(DataComponents.CONSUMABLE)) return Category.FOOD;
        if (item instanceof MagazineItem || ammo(stack)) return Category.AMMO;
        if (item instanceof AttachmentItem) return Category.GUN_PARTS;
        if (BagItem.spec(stack) != null || wornOn(stack) != null && wornOn(stack).getType() == EquipmentSlot.Type.HUMANOID_ARMOR) return Category.CLOTHING;
        if (stack.isDamageableItem() || item == com.zomboidcraft.ModItems.DIGITAL_WATCH) return Category.TOOLS;
        return Category.MATERIALS;
    }

    static boolean ammo(ItemStack stack) {
        for (GunType t : GunType.values()) {
            if (stack.is(t.ammo())) return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ condition

    /** Share of its life left, 1 for something that doesn't wear. */
    static float condition(ItemStack stack) {
        if (!stack.isDamageableItem()) return 1f;
        return 1f - (float) stack.getDamageValue() / stack.getMaxDamage();
    }

    /** Zomboid's words for it. */
    static String conditionWord(float left) {
        return left > 0.85f ? "Pristine" : left > 0.6f ? "Good" : left > 0.35f ? "Worn" : left > 0.15f ? "Battered" : "About to break";
    }

    // ------------------------------------------------------------------ the card's bars

    /** One bar: a label, 0-1, what it would be after a change (NaN for none), and the number to print. */
    record Stat(String label, float value, float changed, String shown) {
    }

    private static float worst(java.util.function.ToDoubleFunction<ZcConfig.GunStats> f, boolean max) {
        double v = max ? 0 : Double.MAX_VALUE;
        for (GunType t : GunType.values()) v = max ? Math.max(v, f.applyAsDouble(t.stats())) : Math.min(v, f.applyAsDouble(t.stats()));
        return (float) v;
    }

    /** The bars for a gun, scaled against the best gun in the mod, with its fitted parts counted. */
    static List<Stat> gunStats(ItemStack gun, @Nullable ItemStack after) {
        GunType t = GunType.of(gun);
        List<Stat> out = new ArrayList<>();
        if (t == null) return out;
        ZcConfig.GunStats s = t.stats();
        float dmg = (float) (s.damage * t.pellets());
        float maxDmg = 0;
        for (GunType o : GunType.values()) maxDmg = Math.max(maxDmg, (float) (o.stats().damage * o.pellets()));
        out.add(new Stat("Damage", dmg / maxDmg, Float.NaN, fmt(dmg)));
        out.add(accuracy("Aimed accuracy", s.aimSpread * Attachments.aimSpreadFactor(gun),
                after == null ? Double.NaN : s.aimSpread * Attachments.aimSpreadFactor(after), worst(x -> x.aimSpread, true)));
        out.add(accuracy("Hip accuracy", s.hipSpread * Attachments.hipSpreadFactor(gun),
                after == null ? Double.NaN : s.hipSpread * Attachments.hipSpreadFactor(after), worst(x -> x.hipSpread, true)));
        float maxRecoil = worst(x -> x.recoil, true);
        double recoil = s.recoil * Attachments.recoilFactor(gun);
        double recoilAfter = after == null ? Double.NaN : s.recoil * Attachments.recoilFactor(after);
        out.add(new Stat("Stability", (float) (1 - recoil / (maxRecoil * 1.25)),
                Double.isNaN(recoilAfter) ? Float.NaN : (float) (1 - recoilAfter / (maxRecoil * 1.25)),
                Math.round(100 * (1 - recoil / (maxRecoil * 1.25))) + ""));
        out.add(new Stat("Range", (float) (s.range / worst(x -> x.range, true)), Float.NaN, Math.round(s.range) + " m"));
        float rate = 20f / Math.max(1, t.fireDelay());
        out.add(new Stat("Rate of fire", rate / 7f, Float.NaN, fmt(rate * 60) + "/min"));
        boolean quiet = Attachments.suppressed(gun);
        boolean quietAfter = after != null && Attachments.suppressed(after);
        out.add(new Stat("Quietness", quiet ? 0.7f : 0.15f, after == null ? Float.NaN : quietAfter ? 0.7f : 0.15f,
                quiet ? "Suppressed" : "Loud"));
        return out;
    }

    private static Stat accuracy(String label, double spread, double after, float worst) {
        float v = (float) (1 - spread / (worst * 1.15));
        float a = Double.isNaN(after) ? Float.NaN : (float) (1 - after / (worst * 1.15));
        return new Stat(label, v, a, Math.round(v * 100) + "");
    }

    static List<Stat> meleeStats(ItemStack stack) {
        List<Stat> out = new ArrayList<>();
        WeaponSpecs.WeaponSpec spec = spec(stack);
        if (spec == null) return out;
        out.add(new Stat("Damage", spec.damage() / 16f, Float.NaN, fmt(spec.damage())));
        out.add(new Stat("Swing speed", spec.speed() / 2f, Float.NaN, fmt(spec.speed())));
        out.add(new Stat("Reach", (spec.reach() - 2f) / 1.6f, Float.NaN, fmt(spec.reach()) + " m"));
        out.add(new Stat("Knockdown", spec.knockdown(), Float.NaN, Math.round(spec.knockdown() * 100) + "%"));
        out.add(new Stat("Endurance cost", 1 - spec.stamina() / 20f, Float.NaN, fmt(spec.stamina())));
        return out;
    }

    /** Armour and toughness from an item's own modifiers. */
    static List<Stat> clothingStats(ItemStack stack) {
        List<Stat> out = new ArrayList<>();
        double[] armour = new double[2];
        ItemAttributeModifiers mods = stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
        mods.forEach(EquipmentSlot.CHEST, (attr, mod) -> add(armour, attr, mod.amount()));
        mods.forEach(EquipmentSlot.HEAD, (attr, mod) -> add(armour, attr, mod.amount()));
        mods.forEach(EquipmentSlot.LEGS, (attr, mod) -> add(armour, attr, mod.amount()));
        mods.forEach(EquipmentSlot.FEET, (attr, mod) -> add(armour, attr, mod.amount()));
        if (armour[0] > 0) out.add(new Stat("Protection", (float) armour[0] / 8f, Float.NaN, fmt((float) armour[0])));
        if (armour[1] > 0) out.add(new Stat("Toughness", (float) armour[1] / 3f, Float.NaN, fmt((float) armour[1])));
        return out;
    }

    private static void add(double[] armour, net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attr, double amount) {
        if (attr.is(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR)) armour[0] += amount;
        if (attr.is(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR_TOUGHNESS)) armour[1] += amount;
    }

    /** Ammunition line for a gun: what's loaded, and how much more you carry. */
    static String loaded(ItemStack gun) {
        GunType t = GunType.of(gun);
        if (t == null) return "";
        GunState state = gun.getOrDefault(GunState.TYPE, GunState.EMPTY_MAG);
        if (t.magazine() != null && !state.mag()) return "No magazine";
        return state.rounds() + " / " + RifleItem.capacity(gun);
    }

    /** A gun's finish line ("Blued, walnut"), or empty for guns with one look. */
    static String finish(ItemStack gun) {
        if (!gun(gun)) return "";
        var line = com.zomboidcraft.combat.GunFinish.line(gun);
        return line == null ? "" : line.getString();
    }

    static String fmt(float v) {
        return Math.abs(v - Math.round(v)) < 0.05f ? String.valueOf(Math.round(v)) : String.format(java.util.Locale.ROOT, "%.1f", v);
    }

    static boolean ours(ItemStack stack) {
        return ZomboidCraft.MOD_ID.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace());
    }

    private KitItems() {
    }
}
