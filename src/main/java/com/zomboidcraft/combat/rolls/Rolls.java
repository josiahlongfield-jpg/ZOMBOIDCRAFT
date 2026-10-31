package com.zomboidcraft.combat.rolls;

import com.zomboidcraft.ZcConfig;
import com.zomboidcraft.combat.MeleeWeaponItem;
import com.zomboidcraft.combat.WeaponSpecs;
import com.zomboidcraft.combat.Weapons;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Every weapon rolls once: a tier from 1 (Rough) to 5 (Pristine) on top of its model's {@link Rarity}. The tier sets
 * its condition, where each {@link Stat} lands inside the model's range, and for the low tiers a {@link Flaw} now and
 * then. Where it was found decides the tier: the loot table it came out of names the place (a house, a gun cabinet, a
 * gun shop, a police car), and a dense horde town around it can push it up one. Distance from where you started plays
 * no part. A weapon that turns up anywhere else (a gun packed in a case, one taken off the floor) is rolled the first
 * time a player has it. Every number is in /zc config under rolls.
 */
public final class Rolls {
    /** Places, as a roll stores them; each has a tier range in the settings. */
    public static final String HOME = "home", STORE = "store", FARM = "farm", VEHICLE = "vehicle", DEAD = "dead",
            GUN_CABINET = "gun_cabinet", GUN_SHOP = "gun_shop", POLICE = "police", MILITARY = "military",
            CASE = "case", UNKNOWN = "unknown";

    private static final float[][] STAT_BANDS = {{0, .40f}, {.20f, .60f}, {.40f, .80f}, {.60f, .95f}, {.85f, 1}};
    private static final float[][] CONDITION_BANDS = {{.20f, .50f}, {.40f, .70f}, {.60f, .85f}, {.80f, .95f}, {.95f, 1}};

    public static @Nullable WeaponRoll get(ItemStack stack) {
        return stack.get(WeaponRoll.TYPE);
    }

    // ------------------------------------------------------------------ rolling

    /** Gives a weapon with no roll yet one, as found in the given place. Returns true if it did. */
    public static boolean roll(ItemStack stack, String place, RandomSource random) {
        return roll(stack, place, false, random);
    }

    /** As {@link #roll(ItemStack, String, RandomSource)}; in a dense horde town the tier may come up one. */
    public static boolean roll(ItemStack stack, String place, boolean dense, RandomSource random) {
        ZcConfig.WeaponRolls cfg = ZcConfig.get().rolls;
        if (stack.isEmpty() || stack.has(WeaponRoll.TYPE) || !cfg.enabled || Rarity.of(stack) == null) {
            return false;
        }
        int[] range = range(place);
        int tier = pickTier(range[0], range[1], random);
        if (dense && random.nextDouble() < cfg.denseTownBump) {
            tier = Math.min(5, tier + 1);
        }
        set(stack, rolled(stack, tier, place, random), random);
        return true;
    }

    /** A fresh roll at a tier: each stat inside the tier's band, and a flaw now and then for tiers 1 and 2. */
    public static WeaponRoll rolled(ItemStack stack, int tier, String place, RandomSource random) {
        ZcConfig.WeaponRolls cfg = ZcConfig.get().rolls;
        tier = Math.clamp(tier, 1, 5);
        float[] band = band(tier);
        List<Integer> stats = new ArrayList<>();
        for (Stat ignored : Stat.values()) {
            stats.add(Math.round(100 * (band[0] + random.nextFloat() * (band[1] - band[0]))));
        }
        int flaws = 0;
        double chance = tier == 1 ? cfg.tier1FlawChance : tier == 2 ? cfg.tier2FlawChance : 0;
        if (random.nextDouble() < chance) {
            List<Flaw> fit = Arrays.stream(Flaw.values()).filter(f -> f.fits(stack)).toList();
            if (!fit.isEmpty()) {
                flaws = fit.get(random.nextInt(fit.size())).bit();
            }
        }
        return new WeaponRoll(tier, stats, flaws, place, false);
    }

    /** Puts a roll on a weapon, with what follows from it: how long it lasts, its condition and its swing. */
    public static void set(ItemStack stack, WeaponRoll roll, RandomSource random) {
        stack.set(WeaponRoll.TYPE, roll);
        refresh(stack);
        if (stack.isDamageableItem()) {
            float[] c = CONDITION_BANDS[roll.tier() - 1];
            float left = c[0] + random.nextFloat() * (c[1] - c[0]);
            int max = stack.getMaxDamage();
            stack.setDamageValue(Math.clamp(Math.round((1 - left) * max), 0, max - 1));
        }
    }

    /**
     * Sets a rolled weapon's per-copy numbers from its roll: the name in its rarity's colour, its full condition, and
     * for a melee weapon its damage, swing speed and reach. Wear it already has keeps its share.
     */
    public static void refresh(ItemStack stack) {
        WeaponRoll roll = get(stack);
        Rarity rarity = Rarity.of(stack);
        if (roll == null || rarity == null) {
            return;
        }
        Component name = stack.getItem().components().get(DataComponents.ITEM_NAME);
        if (name != null) {
            // the colour on a child, so the item's own rarity colour on the outside doesn't cover it
            stack.set(DataComponents.ITEM_NAME, Component.empty().append(name.copy().withColor(rarity.rgb())));
        }
        Integer base = stack.getItem().components().get(DataComponents.MAX_DAMAGE);
        // a melee weapon keeps Zomboid's condition; its Durability stretches the chance a hit wears it (Wear)
        if (base != null && base > 0 && Weapons.spec(stack) == null) {
            float worn = stack.isDamageableItem() ? (float) stack.getDamageValue() / stack.getMaxDamage() : 0;
            int max = Math.max(1, (int) Math.round(base * multiplier(stack, Stat.DURABILITY)));
            stack.set(DataComponents.MAX_DAMAGE, max);
            stack.setDamageValue(Math.clamp(Math.round(worn * max), 0, max - 1));
        }
        WeaponSpecs.WeaponSpec spec = Weapons.spec(stack);
        if (spec != null) {
            stack.set(DataComponents.ATTRIBUTE_MODIFIERS, MeleeWeaponItem.attributes(spec, multiplier(stack, Stat.DAMAGE),
                    multiplier(stack, Stat.SPEED), multiplier(stack, Stat.REACH)));
        }
    }

    private static int pickTier(int min, int max, RandomSource random) {
        int[] weights = ints(ZcConfig.get().rolls.tierWeights, 5, new int[]{30, 30, 22, 12, 6});
        int total = 0;
        for (int t = min; t <= max; t++) {
            total += Math.max(0, weights[t - 1]);
        }
        if (total <= 0) {
            return min;
        }
        int r = random.nextInt(total);
        for (int t = min; t <= max; t++) {
            r -= Math.max(0, weights[t - 1]);
            if (r < 0) {
                return t;
            }
        }
        return max;
    }

    // ------------------------------------------------------------------ reading a roll

    /** Where the copy's stat landed inside the model's range, 0 (worst) to 1 (best); the middle if it isn't rolled. */
    public static float position(ItemStack stack, Stat stat) {
        WeaponRoll roll = get(stack);
        return roll == null ? 0.5f : roll.position(stat);
    }

    /** The share of a model's range a tier's stats land in, as {low, high}, 0 to 1. */
    public static float[] band(int tier) {
        return STAT_BANDS[Math.clamp(tier, 1, 5) - 1].clone();
    }

    /**
     * What the roll does to the stat's underlying number, flaws included: damage, swing speed, reach, knockdown chance
     * and full condition grow with a good roll; spread, recoil, aim sway and jam chance shrink. 1 for no roll.
     */
    public static double multiplier(ItemStack stack, Stat stat) {
        WeaponRoll roll = get(stack);
        if (roll == null || !Stat.of(stack).contains(stat)) {
            return 1;
        }
        double toward = 2 * roll.position(stat) - 1;       // -1 the model's worst, 1 its best
        double m = stat.higherIsBetter() ? 1 + stat.range() * toward : 1 - stat.range() * toward;
        double penalty = Math.max(1, ZcConfig.get().rolls.flawPenalty);
        for (Flaw flaw : Flaw.values()) {
            if (flaw.stat() == stat && roll.has(flaw)) {
                m = stat.higherIsBetter() ? m / penalty : m * penalty;
            }
        }
        return Math.max(0.05, m);
    }

    /** How much better (positive) or worse than an average copy this stat is, in percent. */
    public static int percent(ItemStack stack, Stat stat) {
        double m = multiplier(stack, stat);
        return (int) Math.round((stat.higherIsBetter() ? m - 1 : 1 - m) * 100);
    }

    public static List<Flaw> flaws(ItemStack stack) {
        WeaponRoll roll = get(stack);
        return roll == null ? List.of() : Arrays.stream(Flaw.values()).filter(roll::has).toList();
    }

    /** Where it was found, as a sentence ("Found in a gun cabinet"), or null if nobody knows. */
    public static @Nullable String placeLabel(ItemStack stack) {
        WeaponRoll roll = get(stack);
        if (roll == null) {
            return null;
        }
        return switch (roll.place()) {
            case HOME -> "Found in a house";
            case STORE -> "Found in a store";
            case FARM -> "Found on a farm";
            case VEHICLE -> "Found in a car";
            case DEAD -> "Taken off the dead";
            case GUN_CABINET -> "Found in a gun cabinet";
            case GUN_SHOP -> "Found in a gun shop";
            case POLICE -> "Found in a police car";
            case MILITARY -> "Found with military kit";
            case CASE -> "Found in a gun case";
            default -> null;
        };
    }

    // ------------------------------------------------------------------ tooltip

    /** The tooltip's top lines: rarity and tier, and where it was found. */
    public static void tooltip(ItemStack stack, Consumer<Component> tooltip) {
        Rarity rarity = Rarity.of(stack);
        WeaponRoll roll = get(stack);
        if (rarity == null) {
            return;
        }
        MutableComponent head = Component.literal(rarity.label()).withColor(rarity.rgb());
        if (roll == null) {
            tooltip.accept(head.append(Component.literal("  ·  not yet looked over").withStyle(ChatFormatting.DARK_GRAY)));
            return;
        }
        tooltip.accept(head.append(Component.literal("  ·  Tier " + roll.tier() + " " + Tier.label(roll.tier())
                + (roll.worked() ? " (bench worked)" : "")).withStyle(ChatFormatting.GRAY)));
        String place = placeLabel(stack);
        if (place != null) {
            tooltip.accept(Component.literal(place).withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    /** The tooltip lines under the condition: flaws, and each stat against an average copy of the model. */
    public static void details(ItemStack stack, Consumer<Component> tooltip) {
        if (get(stack) == null) {
            return;
        }
        for (Flaw flaw : flaws(stack)) {
            tooltip.accept(Component.literal("Flaw: " + flaw.label() + " (" + flaw.effect().toLowerCase() + ")")
                    .withStyle(ChatFormatting.RED));
        }
        // three to a line
        List<Stat> stats = Stat.of(stack);
        for (int i = 0; i < stats.size(); i += 3) {
            MutableComponent line = Component.empty();
            for (int j = i; j < Math.min(i + 3, stats.size()); j++) {
                Stat stat = stats.get(j);
                int pct = percent(stack, stat);
                if (j > i) {
                    line.append(Component.literal("   ").withStyle(ChatFormatting.DARK_GRAY));
                }
                line.append(Component.literal(stat.label() + " ").withStyle(ChatFormatting.GRAY));
                line.append(Component.literal(pct == 0 ? "±0%" : (pct > 0 ? "+" : "−") + Math.abs(pct) + "%")
                        .withStyle(pct >= 3 ? ChatFormatting.GREEN : pct <= -3 ? ChatFormatting.RED : ChatFormatting.WHITE));
            }
            tooltip.accept(line);
        }
    }

    // ------------------------------------------------------------------ places

    /** The place a loot table stands for, by its path (shop/guns, car/police, chests/..., entities/zombie). */
    public static String place(String path) {
        String name = path.substring(path.lastIndexOf('/') + 1);
        if (path.startsWith("shop/")) {
            return switch (name) {
                case "home_guns", "basement_guns" -> GUN_CABINET;
                case "guns", "pawn" -> GUN_SHOP;
                case "farm" -> FARM;
                default -> name.startsWith("home_") || name.startsWith("basement_") ? HOME : STORE;
            };
        }
        if (path.startsWith("car/")) {
            return switch (name) {
                case "police" -> POLICE;
                case "swat" -> MILITARY;
                default -> VEHICLE;
            };
        }
        return path.startsWith("entities/") ? DEAD : HOME;
    }

    /** A place's tier range, {lowest, highest}, from the settings. */
    public static int[] range(String place) {
        ZcConfig.WeaponRolls cfg = ZcConfig.get().rolls;
        String setting = switch (place) {
            case HOME, STORE, FARM, VEHICLE -> cfg.houses;
            case DEAD -> cfg.onTheDead;
            case GUN_CABINET -> cfg.gunCabinets;
            case GUN_SHOP -> cfg.gunShops;
            case POLICE -> cfg.police;
            case MILITARY -> cfg.military;
            case CASE -> cfg.gunCases;
            default -> cfg.elsewhere;
        };
        int[] r = ints(setting.replace('-', ','), 1, new int[]{1, 3});
        int lo = Math.clamp(r[0], 1, 5), hi = r.length > 1 ? Math.clamp(r[1], 1, 5) : lo;
        return new int[]{Math.min(lo, hi), Math.max(lo, hi)};
    }

    /** Whether a dense horde town stands around a spot: enough zombies within the settings' radius. */
    public static boolean dense(ServerLevel level, Vec3 at) {
        ZcConfig.WeaponRolls cfg = ZcConfig.get().rolls;
        if (cfg.denseTownZombies <= 0) {
            return false;
        }
        return level.getEntitiesOfClass(Zombie.class, new AABB(at, at).inflate(cfg.denseTownRadius)).size()
                >= cfg.denseTownZombies;
    }

    /** Whole numbers from a setting like "30,30,22,12,6"; the fallback if there are fewer than count or one isn't. */
    private static int[] ints(String text, int count, int[] fallback) {
        try {
            int[] out = Arrays.stream(text.split(",")).map(String::trim).mapToInt(Integer::parseInt).toArray();
            return out.length >= count ? out : fallback;
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    public static void register() {
        WeaponRoll.register();
        // weapons that come out of a loot table are rolled there, knowing the place
        LootTableEvents.MODIFY_DROPS.register((table, context, drops) -> {
            String path = table.unwrapKey().map(key -> key.identifier().getPath()).orElse("");
            String place = place(path);
            Vec3 at = context.getOptional(LootContextParams.ORIGIN);
            boolean dense = at != null && drops.stream().anyMatch(s -> Rarity.of(s) != null) && dense(context.getLevel(), at);
            for (ItemStack drop : drops) {
                roll(drop, place, dense, context.getRandom());
                com.zomboidcraft.combat.Attachments.paint(drop, context.getRandom());
            }
        });
    }

    private Rolls() {
    }
}
