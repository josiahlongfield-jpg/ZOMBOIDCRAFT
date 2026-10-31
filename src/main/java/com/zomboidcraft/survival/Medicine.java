package com.zomboidcraft.survival;

import com.zomboidcraft.ZcConfig;
import com.zomboidcraft.survival.Body.Part;
import com.zomboidcraft.survival.Body.Region;
import com.zomboidcraft.survival.Body.Wound;
import com.zomboidcraft.zombie.Noise;
import java.util.Map;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Zomboid's Health panel on top of {@link Injuries}: what a wound does after it's opened, and what the 94 medical
 * items are for.
 * <ul>
 * <li>Every new wound is <b>dirty</b> until cleaned; a dirty or lodged wound gets <b>infected</b> (0 to 1000), an
 * infected wound stops healing, hurts, and brings on a fever. Antibiotics and antibiotic cream fight it.</li>
 * <li>Cuts and bites can be <b>deep</b>: they heal only half way and can open up again until stitched, stapled or
 * packed. Bullets, glass and shrapnel <b>lodge</b>: the wound won't close until they're pulled out.</li>
 * <li><b>Blood loss</b> builds while you bleed (a tourniquet stops a limb), and comes back slowly, or from a
 * saline or blood bag through an IV.</li>
 * <li><b>Pain</b> comes from everything above and shakes your aim, slows you, stops you sprinting and sleeping;
 * painkillers and anaesthetic take it away for a while.</li>
 * <li><b>Colds</b> make you sneeze and cough, which zombies hear; <b>smoke</b> makes you cough; <b>nausea</b> from
 * bad food makes you vomit; sweets can give you <b>toothache</b>.</li>
 * </ul>
 * Values live in {@link Ailments}; items are in {@link MedSpecs}.
 */
public final class Medicine {
    /** The infection level where a wound stops healing and starts a fever. */
    public static final int INFECTED = 300;
    public static final int MAX = 1000;

    public static void register() {
        Ailments.register();
        WornMedical.register();
        Mood.register();
        ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damage, blocked) -> {
            if (entity instanceof ServerPlayer player && !blocked && damage > 0 && on(player)) {
                hurt(player, source, damage);
            }
        });
    }

    static boolean on(ServerPlayer player) {
        return ZcConfig.get().survival.injuries && !player.isCreative() && !player.isSpectator();
    }

    static String k(String key, Region part) {
        return key + ":" + part.ordinal();
    }

    static int get(Map<String, Integer> m, String key) {
        return m.getOrDefault(key, 0);
    }

    static int get(Map<String, Integer> m, String key, Region part) {
        return m.getOrDefault(k(key, part), 0);
    }

    static void put(Map<String, Integer> m, String key, int value) {
        m.put(key, Math.max(0, value));
    }

    static void add(Map<String, Integer> m, String key, int amount, int max) {
        m.put(key, Math.max(0, Math.min(max, get(m, key) + amount)));
    }

    /** The wounds Injuries just opened: what kind of damage opened them decides whether they're deep or lodged. */
    private static void hurt(ServerPlayer player, DamageSource source, float damage) {
        Map<String, Integer> m = Ailments.edit(player);
        Kind kind = source.is(DamageTypeTags.IS_PROJECTILE) ? Kind.SHOT
                : source.is(DamageTypeTags.IS_EXPLOSION) ? Kind.BLAST
                : source.is(DamageTypeTags.IS_FIRE) ? Kind.FIRE : Kind.OTHER;
        if (kind == Kind.FIRE) add(m, "smoke", 6, 100);
        scan(player, m, kind, damage);
        Ailments.save(player, m);
    }

    private enum Kind { NONE, SHOT, BLAST, FIRE, OTHER }

    /**
     * Compares the body with what medicine last saw: a new wound (or a worse one) starts dirty and may be deep or
     * lodged; a wound that's healed takes its dirt, infection, stitches and dressings with it.
     */
    private static void scan(ServerPlayer player, Map<String, Integer> m, Kind kind, float damage) {
        RandomSource random = player.getRandom();
        Body body = Body.get(player);
        for (Region r : Region.values()) {
            Part p = body.part(r);
            int seenKind = get(m, "wk", r), seenLeft = get(m, "wl", r);
            if (!p.wounded()) {
                if (seenKind != 0) forget(m, r);
                continue;
            }
            boolean fresh = p.wound().ordinal() != seenKind || p.woundLeft() > seenLeft + 2;
            if (fresh) {
                put(m, k("dirty", r), 1);
                m.remove(k("stitched", r));
                m.remove(k("packed", r));
                m.remove(k("rag", r));
                boolean deep = switch (p.wound()) {
                    case LACERATION -> kind == Kind.SHOT || kind == Kind.BLAST && damage > 4 || random.nextFloat() < 0.3f;
                    case BITE -> random.nextFloat() < 0.5f;
                    default -> false;
                };
                if (deep) put(m, k("deep", r), 1);
                if (kind == Kind.SHOT && random.nextFloat() < 0.6f || kind == Kind.BLAST && random.nextFloat() < 0.4f) {
                    put(m, k("lodged", r), 1);
                    player.sendOverlayMessage(Component.literal(kind == Kind.SHOT ? "There's a bullet in your " + r.label + "."
                            : "Shrapnel is stuck in your " + r.label + ".").withStyle(ChatFormatting.RED));
                }
            }
            put(m, k("wk", r), p.wound().ordinal());
            put(m, k("wl", r), p.woundLeft());
        }
    }

    private static void forget(Map<String, Integer> m, Region r) {
        for (String key : new String[]{"wk", "wl", "dirty", "infect", "deep", "lodged", "stitched", "packed", "rag",
                "salve", "burnsalve", "sealed", "wrap"}) {
            m.remove(k(key, r));
        }
    }

    /** Once a second, after Injuries has bled and healed you. */
    static void tick(ServerPlayer player, int second) {
        if (!on(player)) return;
        ServerLevel level = (ServerLevel) player.level();
        RandomSource random = player.getRandom();
        Map<String, Integer> m = Ailments.edit(player);
        scan(player, m, Kind.NONE, 0);
        Body body = Body.get(player);
        int heal = get(m, "vit") > 0 && random.nextInt(10) == 0 ? 1 : 0;
        boolean antibiotics = get(m, "antibio") > 0;
        int worstInfection = 0;

        // wounds: dirt and infection, deep wounds reopening, lodged bullets, extra healing from stitches and salves
        for (Region r : Region.values()) {
            Part p = body.part(r);
            if (!p.wounded()) continue;
            int infect = get(m, "infect", r);
            int grow = 0;
            if (get(m, "dirty", r) > 0) grow += p.bandaged() ? second % 2 : 1;
            if (get(m, "rag", r) > 0) grow += 1;
            if (get(m, "lodged", r) > 0 && get(m, "sealed", r) == 0) grow += 2;
            if (grow == 0 && p.bandaged() && infect < INFECTED) grow = -1;
            if (get(m, "salve", r) > 0) grow = Math.min(grow, -1);
            if (antibiotics) grow = -4;
            infect = Math.max(0, Math.min(MAX, infect + grow));
            put(m, k("infect", r), infect);
            worstInfection = Math.max(worstInfection, infect);

            int left = p.woundLeft();
            int seen = get(m, "wl", r);
            boolean deep = get(m, "deep", r) > 0, closed = get(m, "stitched", r) > 0 || get(m, "packed", r) > 0;
            // healing held back
            if (infect >= INFECTED) left = Math.max(left, seen);
            if (deep && get(m, "stitched", r) == 0) left = Math.max(left, Math.min(seen, p.wound().heal / 2));
            if (get(m, "lodged", r) > 0) left = Math.max(left, Math.min(seen, p.wound().heal * 3 / 4));
            // and helped along
            if (left < seen) {
                int extra = heal;
                if (get(m, "stitched", r) > 0) extra += 1;
                if (get(m, "salve", r) > 0 && (p.wound() == Wound.SCRATCH || p.wound() == Wound.BITE)) extra += 1;
                if (get(m, "burnsalve", r) > 0 && p.wound() == Wound.BURN) extra += 2;
                left = Math.max(1, left - extra);
            }
            boolean bleeding = p.bleeding();
            // a deep wound that isn't closed can open again, more so when you run
            if (deep && !closed && !bleeding && random.nextFloat() < (player.isSprinting() ? 0.02f : 0.002f)) {
                bleeding = true;
                player.sendOverlayMessage(Component.literal("The wound on your " + r.label + " opens up again.").withStyle(ChatFormatting.RED));
            }
            if (get(m, "tq", r) > 0 || get(m, "packed", r) > 0 && random.nextInt(3) == 0) bleeding = false;
            if (left != p.woundLeft() || bleeding != p.bleeding()) {
                body = body.with(r, new Part(p.wound(), left, bleeding, p.bandaged(), p.fractureLeft(), p.splinted()));
            }
            put(m, k("wl", r), left);
        }
        // a sling rests a broken arm: it knits a third faster
        for (Region r : new Region[]{Region.LEFT_ARM, Region.RIGHT_ARM}) {
            Part p = body.part(r);
            if (get(m, "sling", r) > 0 && p.fractured() && p.splinted() && second % 3 == 0) {
                body = body.with(r, new Part(p.wound(), p.woundLeft(), p.bleeding(), p.bandaged(), Math.max(0, p.fractureLeft() - 1), true));
            } else if (!p.fractured()) {
                m.remove(k("sling", r));
            }
        }
        for (Region r : Region.values()) {
            if (!body.part(r).fractured()) m.remove(k("splintkind", r));
        }
        Body.set(player, body);

        // a tourniquet that's left on too long starts doing its own harm
        for (Region r : Region.values()) {
            int tq = get(m, "tq", r);
            if (tq == 0) continue;
            put(m, k("tq", r), tq - 1);
            if (tq == 1) player.sendOverlayMessage(Component.literal("You loosen the tourniquet on your " + r.label + ".").withStyle(ChatFormatting.GRAY));
        }

        // blood
        int bleedingLimbs = body.bleeding();
        int lost = get(m, "blood_lost");
        SurvivalStats stats = SurvivalStats.get(player);
        if (bleedingLimbs > 0 && ZcConfig.get().survival.bleeding) lost += 2 * bleedingLimbs;
        else if (lost > 0 && stats.thirst() > 30 && second % 2 == 0) lost -= 1;
        put(m, "blood_lost", Math.min(MAX, lost));
        if (lost > 300) effect(player, MobEffects.SLOWNESS, 0);
        if (lost > 500) {
            effect(player, MobEffects.WEAKNESS, 0);
            if (random.nextInt(40) == 0) effect(player, MobEffects.NAUSEA, 0, 100);
        }
        if (lost > 750 && second % 5 == 0) player.hurtServer(level, ModDamageTypes.source(level, ModDamageTypes.BLEEDING), 1f);
        if (lost >= MAX) player.hurtServer(level, ModDamageTypes.source(level, ModDamageTypes.BLEEDING), Float.MAX_VALUE);

        // fever: the worst infected wound
        int fever = Math.max(0, worstInfection - INFECTED) * 100 / (MAX - INFECTED);
        put(m, "fever", fever);
        if (fever > 0) {
            stats = stats.withThirst(stats.thirst() - 0.03f * fever / 100f).withFatigue(stats.fatigue() + 0.04f * fever / 100f);
            SurvivalStats.set(player, stats);
            if (fever > 40) effect(player, MobEffects.WEAKNESS, 0);
            if (fever > 70 && second % 15 == 0) player.hurtServer(level, ModDamageTypes.source(level, ModDamageTypes.INFECTION), 1f);
            if (second % 120 == 0) player.sendOverlayMessage(Component.literal("You're burning up. A wound is infected.").withStyle(ChatFormatting.GOLD));
        }

        illness(player, level, random, m, stats, second);
        pain(player, m, body);
        timers(m);
        Ailments.save(player, m);
        WornMedical.refresh(player);
    }

    /** Colds, smoke, nausea and teeth. */
    private static void illness(ServerPlayer player, ServerLevel level, RandomSource random, Map<String, Integer> m,
                                SurvivalStats stats, int second) {
        boolean masked = MedSpecs.carries(player, "face_masks");
        // catching a cold: out in the rain
        if (get(m, "cold") == 0 && level.isRainingAt(player.blockPosition().above())
                && random.nextInt((masked ? 2 : 1) * (get(m, "vit") > 0 ? 2 : 1) * 2400) == 0) {
            put(m, "cold", 1800 + random.nextInt(1200));
            player.sendOverlayMessage(Component.literal("You've caught a cold.").withStyle(ChatFormatting.GRAY));
        }
        int cold = get(m, "cold");
        if (cold > 0) {
            put(m, "cold", cold - 1 - (get(m, "vit") > 0 ? 1 : 0) - (get(m, "antiviral") > 0 ? 3 : 0));
            SurvivalStats.set(player, stats.withFatigue(stats.fatigue() + 0.02f));
        }
        int smoke = get(m, "smoke");
        if (smoke > 0) {
            put(m, "smoke", smoke - 1);
            if (smoke > 50) effect(player, MobEffects.SLOWNESS, 0);
            if (smoke > 80 && second % 5 == 0) player.hurtServer(level, level.damageSources().drown(), 1f);
        }
        // a sneeze or a cough carries: zombies hear it
        boolean sneezy = cold > 0 && get(m, "nosneeze") == 0, coughs = smoke > 20 && get(m, "nocough") == 0;
        if ((sneezy || coughs) && random.nextInt(45) == 0) {
            Noise.emit(level, player.position(), masked ? 9 : 14);
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_HURT_DROWN, SoundSource.PLAYERS, 0.5f, 1.4f);
            player.sendOverlayMessage(Component.literal(sneezy ? "You sneeze." : "You cough.").withStyle(ChatFormatting.GRAY));
        }
        // sore eyes from smoke or no sleep blur your sight now and then
        if ((smoke > 20 || stats.fatigue() > 80) && get(m, "eyes") == 0 && random.nextInt(90) == 0) {
            effect(player, MobEffects.NAUSEA, 0, 60);
        }
        int nausea = get(m, "nausea");
        if (nausea > 0) {
            put(m, "nausea", nausea - (second % 4 == 0 ? 1 : 0));
            if (nausea > 50 && random.nextInt(30) == 0) vomit(player, level, m, stats);
        }
        if (get(m, "weak") > 0) effect(player, MobEffects.WEAKNESS, 0);
        // drink wears off a point every five seconds; well drunk, the world sways
        int drunk = get(m, "drunk");
        if (drunk > 0 && second % 5 == 0) put(m, "drunk", drunk - 1);
        if (drunk > 60 && random.nextInt(20) == 0) effect(player, MobEffects.NAUSEA, 0, 100);
    }

    private static void vomit(ServerPlayer player, ServerLevel level, Map<String, Integer> m, SurvivalStats stats) {
        add(m, "nausea", -40, 100);
        put(m, "weak", 300);
        SurvivalStats.set(player, stats.withThirst(stats.thirst() - 15f));
        player.getFoodData().setFoodLevel(Math.max(0, player.getFoodData().getFoodLevel() - 4));
        Noise.emit(level, player.position(), 8);
        player.sendOverlayMessage(Component.literal("You throw up.").withStyle(ChatFormatting.GRAY));
    }

    /** Pain from everything that hurts, less whatever you've taken for it. */
    private static void pain(ServerPlayer player, Map<String, Integer> m, Body body) {
        int pain = 0;
        for (Region r : Region.values()) {
            Part p = body.part(r);
            if (p.fractured()) pain += p.splinted() ? 12 : 30;
            if (p.wounded()) {
                int w = switch (p.wound()) {
                    case SCRATCH -> 4;
                    case BURN -> get(m, "burnsalve", r) > 0 ? 5 : 10;
                    case LACERATION -> 8;
                    case BITE -> 12;
                    case NONE -> 0;
                };
                if (get(m, "deep", r) > 0) w += 8;
                if (get(m, "lodged", r) > 0) w += 12;
                if (get(m, "wrap", r) > 0) w -= 4;
                pain += Math.max(0, w) + Math.min(20, get(m, "infect", r) / 50);
            }
            if (get(m, "tq", r) > 0) pain += 10;
        }
        if (get(m, "tooth") > 0) pain += 25;
        if (get(m, "cold") > 0) pain += 3;
        pain += get(m, "sting");
        if (body.part(Region.LEFT_LEG).severity() >= 2 || body.part(Region.RIGHT_LEG).severity() >= 2) {
            if (MedSpecs.holds(player, "walking_cane") || MedSpecs.holds(player, "crutches")) pain -= 10;
        }
        if (get(m, "coldpack") > 0) pain -= 15;
        if (player.isSleeping() && MedSpecs.carries(player, "hot_water_bottle")) pain -= 15;
        int relief = Math.max(Math.max(get(m, "pk") > 0 ? 30 : 0, get(m, "pk2") > 0 ? 60 : 0),
                get(m, "morph") > 0 || get(m, "anaes") > 0 ? 100 : 0);
        pain = Math.max(0, Math.min(100, pain - relief));
        put(m, "pain", pain);
        if (pain >= 60) effect(player, MobEffects.SLOWNESS, 0);
        if (pain >= 80 && player.isSprinting()) player.setSprinting(false);
        if (pain >= 50 && player.isSleeping() && get(m, "sleepy") == 0) {
            player.stopSleeping();
            player.sendOverlayMessage(Component.literal("You're in too much pain to sleep.").withStyle(ChatFormatting.GRAY));
        }
    }

    /** Drug and dressing timers count down a second. */
    private static void timers(Map<String, Integer> m) {
        for (Map.Entry<String, Integer> e : m.entrySet()) {
            String key = e.getKey();
            if (TIMERS.contains(key.contains(":") ? key.substring(0, key.indexOf(':')) : key)) {
                e.setValue(Math.max(0, e.getValue() - 1));
            }
        }
        // a stitch or a sting's sharp pain fades fast
        if (get(m, "sting") > 0) m.put("sting", Math.max(0, get(m, "sting") - 2));
    }

    private static final java.util.Set<String> TIMERS = java.util.Set.of("pk", "pk2", "morph", "anaes", "antibio",
            "antiviral", "vit", "nosneeze", "nocough", "eyes", "sleepy", "beta", "antidep", "coldpack", "settled", "weak",
            "salve", "burnsalve", "adren");

    // ------------------------------------------------------------ what the rest of the game asks

    /** Pain 0-100, on either side. */
    public static int pain(Player player) {
        return Ailments.get(player, "pain");
    }

    /** How much pain shakes your aim, 0-0.5; beta blockers steady it. */
    public static float painShake(Player player) {
        int pain = pain(player);
        float shake = pain >= 60 ? 0.35f : pain >= 30 ? 0.15f : 0;
        int drunk = Ailments.get(player, "drunk");
        shake += drunk >= 60 ? 0.3f : drunk >= 30 ? 0.12f : 0;
        return Ailments.get(player, "beta") > 0 ? shake * 0.4f : shake;
    }

    /** Beta blockers calm a hurt arm's tremble too. */
    public static float steadiness(Player player) {
        return Ailments.get(player, "beta") > 0 ? 0.5f : 1f;
    }

    public static boolean painStopsSprint(Player player) {
        return pain(player) >= 80;
    }

    /**
     * How much of a leg injury's slowdown you feel: crutches all but take a broken leg's away, a cane or a knee
     * brace halves it, an elastic wrap on that leg takes a little.
     */
    public static double legSupport(Player player, Region leg, boolean broken) {
        double k = 1;
        if (MedSpecs.holds(player, "crutches")) k = broken ? 0.3 : 0.5;
        else if (MedSpecs.holds(player, "walking_cane")) k = 0.6;
        if (MedSpecs.carries(player, "knee_brace")) k *= 0.5;
        if (Ailments.get(player, "wrap", leg) > 0) k *= 0.8;
        return k;
    }

    /** Zomboid's Drunk: each strength point of a drink adds 12 (0-100). */
    public static void drink(ServerPlayer player, int strength) {
        Map<String, Integer> m = Ailments.edit(player);
        add(m, "drunk", 12 * strength, 100);
        Ailments.save(player, m);
    }

    /** Rotten food, raw meat, too many pills: queasy, then sick. Anti-nausea pills stop it for a while. */
    public static void nausea(ServerPlayer player, int amount) {
        if (Ailments.get(player, "settled") > 0) return;
        Map<String, Integer> m = Ailments.edit(player);
        add(m, "nausea", amount, 100);
        Ailments.save(player, m);
    }

    private static final java.util.regex.Pattern SWEET = java.util.regex.Pattern.compile(
            "candy|chocolate|lollipop|gum|toffee|licorice|marshmallow|cookie|donut|soda|cola|caramel|fudge|jelly");

    /** A sweet now and then cracks a bad tooth: toothache until a dental kit sees to it. */
    public static void ate(ServerPlayer player, ItemStack stack) {
        net.minecraft.resources.Identifier id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (SWEET.matcher(id.getPath()).find() && player.getRandom().nextFloat() < 0.02f && Ailments.get(player, "tooth") == 0) {
            Ailments.set(player, "tooth", 1);
            player.sendOverlayMessage(Component.literal("Ow. A tooth's gone bad.").withStyle(ChatFormatting.GRAY));
        }
    }

    static void effect(ServerPlayer player, net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> effect, int amplifier) {
        effect(player, effect, amplifier, 40);
    }

    static void effect(ServerPlayer player, net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> effect, int amplifier, int duration) {
        player.addEffect(new MobEffectInstance(effect, duration, amplifier, true, false, true));
    }

    private Medicine() {
    }
}
