package com.zomboidcraft.survival;

import com.zomboidcraft.ZcConfig;
import com.zomboidcraft.ZomboidCraft;
import com.zomboidcraft.carry.Carry;
import com.zomboidcraft.skills.Skill;
import com.zomboidcraft.skills.Skills;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Zomboid's needs clock, once a second: hunger climbs, calories burn by what you're doing, the macros fade, weight
 * drifts with the calorie bank, starving hurts, and a fed, watered, rested body heals slowly on its own. Minecraft's
 * food bar is held still underneath so none of its healing, starving or saturation happens.
 * <p>
 * Time runs on Zomboid's clock: a day lasts {@code survival.dayMinutes} real minutes (60, Zomboid's default), so an
 * hour is two and a half minutes. Rates below are per Zomboid hour.
 * <p>
 * Moodle effects are attribute modifiers rather than potions, so no potion icon stands in for a moodle: hunger and
 * Strength change melee damage, thirst and tiredness slow you, tiredness slows your swing.
 */
public final class Needs {
    /** Hunger gained per hour at rest: hungry after about seven hours, starving after twenty. */
    private static final float HUNGER_PER_HOUR = 0.035f;
    /** Macros the body uses up per hour (grams). */
    private static final float CARBS_PER_HOUR = 12f, FATS_PER_HOUR = 4f, PROTEINS_PER_HOUR = 3f;
    private static final Identifier HUNGER_HIT = ZomboidCraft.id("needs_hunger_hit"),
            STRENGTH_HIT = ZomboidCraft.id("needs_strength_hit"), THIRST_SLOW = ZomboidCraft.id("needs_thirst_slow"),
            TIRED_SLOW = ZomboidCraft.id("needs_tired_slow"), TIRED_SWING = ZomboidCraft.id("needs_tired_swing");

    /** Whether Zomboid hunger replaces Minecraft's food bar. */
    public static boolean on() {
        return ZcConfig.get().survival.hunger;
    }

    /** Real seconds in one Zomboid hour. */
    public static float hourSeconds() {
        return (float) Math.max(1, ZcConfig.get().survival.dayMinutes) * 60f / 24f;
    }

    /** Every tick: keep the vanilla food bar where it neither heals, starves nor stops you sprinting or eating. */
    public static void holdFoodBar(ServerPlayer player) {
        if (!on()) return;
        var food = player.getFoodData();
        if (food.getFoodLevel() != 17) food.setFoodLevel(17);
        if (food.getSaturationLevel() != 0f) food.setSaturation(0f);
    }

    /** Once a second for each surviving player (SurvivalEvents.tickPlayer). */
    static void tick(ServerPlayer player, int second) {
        ZcConfig.Survival config = ZcConfig.get().survival;
        SurvivalStats stats = SurvivalStats.get(player);
        if (on()) {
            Nutrition n = Nutrition.get(player);
            float hour = 1f / hourSeconds(), speed = (float) config.statsDecrease;
            float activity = activity(player);
            n = n.withHunger(n.hunger() + HUNGER_PER_HOUR * hour * speed * (0.6f + 0.4f * activity));
            if (config.nutrition) {
                float burn = (float) config.caloriesPerDay / 24f * hour * speed * activity;
                n = n.plus(-burn, fade(n.carbs(), CARBS_PER_HOUR * hour), fade(n.fats(), FATS_PER_HOUR * hour),
                        fade(n.proteins(), PROTEINS_PER_HOUR * hour));
                n = n.withWeight(n.weight() + weightChange(n.calories()) * hour * (float) config.weightChange);
            }
            Nutrition.set(player, n);

            ServerLevel level = (ServerLevel) player.level();
            if (n.hungerStage() == 4 && second % 60 == 0) {
                player.hurtServer(level, level.damageSources().starve(), 1f);
            }
            // a body that has what it needs mends itself, slowly
            if (n.hungerStage() <= 2 && stats.thirst() > 30 && stats.fatigue() < 80 && Body.get(player).bleeding() == 0
                    && player.getHealth() < player.getMaxHealth() && config.regenPerHour > 0
                    && second % Math.max(1, Math.round(hourSeconds() / (float) config.regenPerHour)) == 0) {
                player.heal(1f);
            }
            passives(player, n, second);
        }
        effects(player, stats);
    }

    /** How hard the body is working, 1 at rest: sprinting, a heavy load and the cold burn more, sleep less. */
    private static float activity(ServerPlayer player) {
        if (Sleep.asleep(player)) return 0.7f;
        float a = 1f;
        if (player.isSprinting()) a = 2.2f;
        else if (player.getDeltaMovement().horizontalDistanceSqr() > 0.001) a = 1.3f;
        int load = Carry.tier(player.getAttachedOrElse(Carry.LOAD, 0f));
        return a * (1f + 0.1f * load);
    }

    /** Takes a macro back towards 0 by up to {@code step} (it returns the change). */
    private static float fade(float value, float step) {
        return value > 0 ? -Math.min(value, step) : value < 0 ? Math.min(-value, step) : 0f;
    }

    /**
     * kg per hour the calorie bank moves your weight (approximate, after Zomboid): above 1000 kcal you gain, up to
     * 0.6 kg a day at a full bank; below 0 you lose, up to 0.8 kg a day when it's empty.
     */
    static float weightChange(float calories) {
        if (calories > 1000) return Math.min(1f, (calories - 1000) / 2700f) * 0.6f / 24f;
        if (calories < 0) return -Math.min(1f, -calories / 2200f) * 0.8f / 24f;
        return 0f;
    }

    /** Fitness and Strength: trained by working the body, wasted by starving it. */
    private static void passives(ServerPlayer player, Nutrition n, int second) {
        if (player.isSprinting() && second % 3 == 0) Skills.addXp(player, Skill.FITNESS, 1);
        if (Carry.tier(player.getAttachedOrElse(Carry.LOAD, 0f)) >= 1
                && player.getDeltaMovement().horizontalDistanceSqr() > 0.001 && second % 5 == 0) {
            Skills.addXp(player, Skill.STRENGTH, 1);
        }
        if ((n.hungerStage() == 4 || n.calories() < -1500) && second % 30 == 0) {
            Skills.loseXp(player, Skill.FITNESS, 2);
            Skills.loseXp(player, Skill.STRENGTH, 2);
        }
    }

    /** A melee swing (Stamina.onSwing) works both passive skills. */
    public static void onSwing(ServerPlayer player) {
        if (player.getRandom().nextInt(3) == 0) Skills.addXp(player, Skill.STRENGTH, 1);
        if (player.getRandom().nextInt(3) == 0) Skills.addXp(player, Skill.FITNESS, 1);
    }

    /** Eating or drinking: hunger and the bank, Zomboid style. */
    public static void eat(Player player, ItemStack stack) {
        if (!on()) return;
        Foods.Value v = Foods.of(stack);
        if (v == null) return;
        Nutrition n = Nutrition.get(player).withHunger(Nutrition.get(player).hunger() - v.hunger());
        if (ZcConfig.get().survival.nutrition) n = n.plus(v.kcal(), v.carbs(), v.fats(), v.proteins());
        Nutrition.set(player, n);
        if (player instanceof ServerPlayer sp) holdFoodBar(sp);
    }

    /** Endurance comes back quicker with Fitness, slower when very hungry or starving (Stamina). */
    public static float enduranceRegen(Player player) {
        float f = 1f + 0.06f * (Skills.level(player, Skill.FITNESS) - 5);
        if (on()) {
            int hunger = Nutrition.get(player).hungerStage();
            if (hunger == 4) f *= 0.6f;
            else if (hunger == 3) f *= 0.8f;
        }
        return Math.max(0.2f, f);
    }

    /** kg more (or less) you can carry for your Strength (Carry). */
    public static double carryBonus(Player player) {
        return 0.6 * (Skills.level(player, Skill.STRENGTH) - 5);
    }

    /** Zomboid's thirst moodle stages on our 100-is-hydrated scale: 1 slightly thirsty to 4 dying of thirst. */
    public static int thirstStage(float thirst) {
        return thirst <= 16 ? 4 : thirst <= 30 ? 3 : thirst <= 75 ? 2 : thirst <= 88 ? 1 : 0;
    }

    /** Zomboid's tired stages: 1 drowsy, 2 tired, 3 very tired, 4 ridiculously tired. */
    public static int tiredStage(float fatigue) {
        return fatigue >= 90 ? 4 : fatigue >= 80 ? 3 : fatigue >= 70 ? 2 : fatigue >= 60 ? 1 : 0;
    }

    private static void effects(ServerPlayer player, SurvivalStats stats) {
        int hunger = on() ? Nutrition.get(player).hungerStage() : 0;
        modifier(player, Attributes.ATTACK_DAMAGE, HUNGER_HIT, hunger == 4 ? -0.25 : hunger == 3 ? -0.1 : 0);
        modifier(player, Attributes.ATTACK_DAMAGE, STRENGTH_HIT, 0.04 * (Skills.level(player, Skill.STRENGTH) - 5));
        int thirst = thirstStage(stats.thirst());
        modifier(player, Attributes.MOVEMENT_SPEED, THIRST_SLOW, thirst == 4 ? -0.15 : thirst == 3 ? -0.05 : 0);
        int tired = tiredStage(stats.fatigue());
        modifier(player, Attributes.MOVEMENT_SPEED, TIRED_SLOW, tired == 4 ? -0.1 : 0);
        modifier(player, Attributes.ATTACK_SPEED, TIRED_SWING, tired == 4 ? -0.3 : tired == 3 ? -0.15 : tired == 2 ? -0.05 : 0);
    }

    private static void modifier(ServerPlayer player, Holder<Attribute> attribute, Identifier id, double amount) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null) return;
        AttributeModifier current = instance.getModifier(id);
        if (amount == 0) {
            if (current != null) instance.removeModifier(id);
        } else if (current == null || current.amount() != amount) {
            instance.addOrUpdateTransientModifier(new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
    }

    private Needs() {
    }
}
