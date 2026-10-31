package com.zomboidcraft.survival;

import com.zomboidcraft.ZcConfig;
import com.zomboidcraft.ZomboidCraft;
import com.zomboidcraft.skills.Skill;
import com.zomboidcraft.skills.Skills;
import com.zomboidcraft.survival.Body.Part;
import com.zomboidcraft.survival.Body.Region;
import com.zomboidcraft.survival.Body.Wound;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * What hurts where, and what it does to you. Long falls break legs, fire burns, arrows and blasts cut; zombies'
 * scratches and bites land mostly on the arms you raise against them (see SurvivalEvents). A broken leg slows you
 * and stops you sprinting; a hurt arm swings weaker. Wounds bleed until bandaged or clotted and heal over time,
 * faster bandaged; fractures heal three times faster in a splint.
 */
public final class Injuries {
    private static final Identifier LEGS = ZomboidCraft.id("injured_legs");
    private static final Identifier ARMS = ZomboidCraft.id("injured_arms");

    public static void register() {
        Body.register();
        Medicine.register();
        Downs.register();
        ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damage, blocked) -> {
            if (entity instanceof ServerPlayer player && !blocked && damage > 0 && on(player)) {
                hurt(player, source, damage);
            }
        });
    }

    private static boolean on(ServerPlayer player) {
        return ZcConfig.get().survival.injuries && !player.isCreative() && !player.isSpectator();
    }

    private static void hurt(ServerPlayer player, DamageSource source, float damage) {
        RandomSource random = player.getRandom();
        Body body = Body.get(player);
        Region broke = null;
        if (source.is(DamageTypeTags.IS_FALL)) {
            // about half a chance per leg from ten blocks, certain from fourteen
            float chance = Math.max(0, Math.min(1, (damage - 3) / 8f));
            for (Region leg : new Region[]{Region.LEFT_LEG, Region.RIGHT_LEG}) {
                if (!body.part(leg).fractured() && random.nextFloat() < chance) {
                    body = body.fracture(leg);
                    broke = leg;
                }
            }
        } else if (source.is(DamageTypeTags.IS_FIRE)) {
            if (random.nextFloat() < 0.15f) body = body.wound(Body.any(random), Wound.BURN, false);
        } else if (source.is(DamageTypeTags.IS_EXPLOSION)) {
            Region r = Body.any(random);
            body = body.wound(r, Wound.LACERATION, true);
            if (damage > 6 && random.nextFloat() < 0.25f && !body.part(r).fractured()) {
                body = body.fracture(r);
                broke = r;
            }
        } else if (source.is(DamageTypeTags.IS_PROJECTILE)) {
            body = body.wound(Body.any(random), Wound.LACERATION, true);
        }
        Body.set(player, body);
        if (broke != null) {
            player.sendOverlayMessage(Component.literal("You broke your " + broke.label + ".").withStyle(ChatFormatting.RED));
        }
    }

    /**
     * A zombie's scratch or bite on the part it reached. Returns where, or null when the clothes over that part
     * stopped it (see clothing/Clothes).
     */
    public static @org.jetbrains.annotations.Nullable Region zombieWound(ServerPlayer player, boolean bite) {
        RandomSource random = player.getRandom();
        Region where = Body.zombieTarget(random);
        com.zomboidcraft.clothing.Clothes.Totals worn = com.zomboidcraft.clothing.Clothes.totals(player);
        if (random.nextFloat() < (bite ? worn.bite() : worn.scratch()).getOrDefault(where, 0f)) {
            return null;
        }
        Wound wound = bite ? Wound.BITE : random.nextFloat() < 0.25f ? Wound.LACERATION : Wound.SCRATCH;
        Body.set(player, Body.get(player).wound(where, wound, true));
        return where;
    }

    /** Once a second: bleeding, clotting, healing, and what your injuries do to you. */
    static void tick(ServerPlayer player, int second) {
        ServerLevel level = (ServerLevel) player.level();
        RandomSource random = player.getRandom();
        ZcConfig.Survival config = ZcConfig.get().survival;
        SurvivalStats stats = SurvivalStats.get(player);
        Body body = Body.get(player);
        // the old wound count (saved worlds, /zc wounds) becomes bleeding scratches on random parts
        if (stats.wounds() > 0) {
            for (int i = 0; i < stats.wounds(); i++) body = body.wound(Body.zombieTarget(random), Wound.SCRATCH, true);
            SurvivalStats.set(player, stats.withWounds(0));
        }
        if (!config.injuries) body = Body.FRESH;

        int bleeding = body.bleeding();
        if (bleeding > 0 && config.bleeding) {
            int interval = Math.max(1, 5 - bleeding);
            if (second % interval == 0) {
                player.hurtServer(level, ModDamageTypes.source(level, ModDamageTypes.BLEEDING), 1f);
            }
        }
        // First Aid: bound wounds heal 5% faster a level; from level 8 a splint sets a break a third faster
        int firstAid = Skills.level(player, Skill.FIRST_AID);
        for (Region r : Region.values()) {
            Part p = body.part(r);
            if (p.fine()) continue;
            boolean bleeds = p.bleeding() && config.bleeding && random.nextFloat() >= config.clotChancePerSecond * p.wound().clot;
            int bound = 2 + (random.nextFloat() < 0.1f * firstAid ? 1 : 0);
            int woundLeft = p.wounded() ? Math.max(0, p.woundLeft() - (p.bandaged() ? bound : 1)) : 0;
            Wound wound = woundLeft == 0 ? Wound.NONE : p.wound();
            int set = firstAid >= 8 ? 4 : 3;
            int fractureLeft = p.fractured() ? Math.max(0, p.fractureLeft() - (p.splinted() ? set : 1)) : 0;
            body = body.with(r, new Part(wound, woundLeft, bleeds && wound != Wound.NONE, p.bandaged() && wound != Wound.NONE,
                    fractureLeft, p.splinted() && fractureLeft > 0));
        }
        Body.set(player, body);
        effects(player, body);
    }

    private static void effects(ServerPlayer player, Body body) {
        double legs = 0, arms = 0;
        boolean broken = false;
        for (Region r : Region.values()) {
            Part p = body.part(r);
            if (r.leg()) {
                if (p.fractured()) {
                    legs -= (p.splinted() ? 0.12 : 0.25) * Medicine.legSupport(player, r, true);
                    broken |= !p.splinted();
                } else if (p.severity() >= 2) {
                    legs -= 0.05 * Medicine.legSupport(player, r, false);
                }
            } else if (r.arm()) {
                if (p.fractured()) arms -= p.splinted() ? 0.2 : 0.35;
                else if (p.severity() >= 2) arms -= 0.1;
            }
        }
        modify(player.getAttribute(Attributes.MOVEMENT_SPEED), LEGS, legs);
        modify(player.getAttribute(Attributes.ATTACK_DAMAGE), ARMS, arms);
        if (broken && player.isSprinting() && !MedSpecs.holds(player, "crutches")) player.setSprinting(false);
    }

    private static void modify(AttributeInstance attribute, Identifier id, double amount) {
        if (attribute == null) return;
        AttributeModifier current = attribute.getModifier(id);
        if (amount == 0) {
            if (current != null) attribute.removeModifier(id);
        } else if (current == null || current.amount() != amount) {
            attribute.addOrUpdateTransientModifier(new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
    }

    /** True while a leg is broken and not yet splinted: no sprinting. */
    public static boolean cantSprint(net.minecraft.world.entity.player.Player player) {
        Body body = Body.get(player);
        return body.part(Region.LEFT_LEG).severity() == 3 || body.part(Region.RIGHT_LEG).severity() == 3
                || Medicine.painStopsSprint(player) || Downs.downed(player);
    }

    /** How shaky your aim is from hurt arms: 0 steady, up to 1 with both broken. */
    public static float armShake(net.minecraft.world.entity.player.Player player) {
        Body body = Body.get(player);
        float shake = 0;
        for (Region r : new Region[]{Region.LEFT_ARM, Region.RIGHT_ARM}) {
            Part p = body.part(r);
            shake += p.fractured() ? (p.splinted() ? 0.3f : 0.5f) : p.severity() >= 2 ? 0.15f : 0;
        }
        return Math.min(1, shake * Medicine.steadiness(player) + Medicine.painShake(player));
    }

    private Injuries() {
    }
}
