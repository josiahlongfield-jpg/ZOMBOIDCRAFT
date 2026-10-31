package com.zomboidcraft.survival.character;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.zomboidcraft.ZcConfig;
import com.zomboidcraft.ZomboidCraft;
import com.zomboidcraft.skills.Skill;
import com.zomboidcraft.skills.Skills;
import com.zomboidcraft.survival.Nutrition;
import com.zomboidcraft.survival.character.CharacterData.Occupation;
import com.zomboidcraft.survival.character.CharacterData.Trait;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * Who your survivor was: Zomboid's occupation and traits, chosen on your first join and again after every death
 * (CharacterNet, client/kit/CharacterScreen). The occupation and the skill traits set your starting skills, and every
 * skill they raise learns faster (Zomboid's 75%, 100% and 125% for one, two and three levels). The traits themselves
 * are read by the systems they change through {@link #has}: hunger, thirst and sleep (Needs, SurvivalEvents, Sleep),
 * healing and infection (Injuries, SurvivalEvents), panic (Panic), reading (SkillBookItem), and the other threads'
 * hearing, sight, stealth, cars and bags.
 * <p>
 * Weight traits follow your weight, and the Strength and Fitness traits follow those skills, as in Zomboid: the
 * stored trait only sets where you start ({@link #shown} gives what you are now).
 */
public final class Traits {
    /** The survivor's occupation and every trait they have, the occupation's free ones included. */
    public record Profile(String occupation, List<String> traits) {
        static final Codec<Profile> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("occupation").forGetter(Profile::occupation),
                Codec.STRING.listOf().fieldOf("traits").forGetter(Profile::traits)
        ).apply(i, Profile::new));
        static final StreamCodec<io.netty.buffer.ByteBuf, Profile> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, Profile::occupation,
                ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), Profile::traits,
                Profile::new);
    }

    /** Not kept through death: a new survivor is a new person. Synced to the player's own client. */
    public static final AttachmentType<Profile> TYPE = AttachmentRegistry.create(ZomboidCraft.id("character"),
            builder -> builder.persistent(Profile.CODEC).syncWith(Profile.STREAM_CODEC, AttachmentSyncPredicate.targetOnly()));

    public static void register() {
        PayloadTypeRegistry.clientboundPlay().register(CharacterNet.Open.TYPE, CharacterNet.Open.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(CharacterNet.Create.TYPE, CharacterNet.Create.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(CharacterNet.Create.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            player.level().getServer().execute(() -> create(player, payload));
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> server.execute(() -> offer(handler.getPlayer(), false)));
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
            if (alive) {
                Profile p = oldPlayer.getAttached(TYPE);
                if (p != null) newPlayer.setAttached(TYPE, p);    // through an end portal: the same person
                return;
            }
            offer(newPlayer, true);
        });
    }

    /** Opens character creation for a survivor who hasn't got a past yet. */
    public static void offer(ServerPlayer player, boolean again) {
        if (!ZcConfig.get().survival.characterCreation || player.hasAttached(TYPE) || !county(player)) return;
        ServerPlayNetworking.send(player, new CharacterNet.Open(again, ZcConfig.get().survival.freeTraitPoints, penaltyEvery()));
    }

    /** Only in the planned county's overworld, where the spawn picker runs too (test and vanilla worlds skip it). */
    static boolean county(ServerPlayer player) {
        var level = player.level();
        return level == level.getServer().overworld()
                && level.getChunkSource().getGenerator() instanceof com.zomboidcraft.world.gen.CountyChunkGenerator gen && gen.planned();
    }

    /** Zomboid's Negative Traits Penalty as "one point less for every N negative traits"; 0 for none. */
    static int penaltyEvery() {
        return switch (ZcConfig.get().survival.negativeTraitsPenalty) {
            case "one_every_3" -> 3;
            case "one_every_2" -> 2;
            case "one_every_trait" -> 1;
            default -> 0;
        };
    }

    // ------------------------------------------------------------------ the rules, shared with the screen

    /** Points left with this occupation and these chosen traits (free ones not counted). Below 0 isn't allowed. */
    public static int points(String occupation, List<String> chosen, int free, int penaltyEvery) {
        Occupation o = CharacterData.occupation(occupation);
        int points = free + (o == null ? 0 : o.points());
        int negatives = 0;
        for (String id : chosen) {
            Trait t = CharacterData.trait(id);
            if (t == null) continue;
            points -= t.cost();
            if (t.negative()) negatives++;
        }
        if (penaltyEvery > 0) points -= negatives / penaltyEvery;
        return points;
    }

    /** Why this trait can't be added now, or null if it can. */
    public static String blocked(String occupation, List<String> chosen, String id) {
        Trait t = CharacterData.trait(id);
        if (t == null) return "Unknown trait";
        if (t.occupationOnly()) return "Comes only with an occupation";
        Set<String> have = new LinkedHashSet<>(chosen);
        Occupation o = CharacterData.occupation(occupation);
        if (o != null) have.addAll(o.traits());
        if (have.contains(id)) return o != null && o.traits().contains(id) ? "Your occupation gives you this" : "Already chosen";
        for (String h : have) {
            Trait other = CharacterData.trait(h);
            if (t.excludes().contains(h) || other != null && other.excludes().contains(id)) {
                return "Can't have it with " + (other == null ? h : other.name());
            }
        }
        return null;
    }

    /** Skill levels the occupation and traits add (negative for Weak, Unfit and the like). */
    public static Map<Skill, Integer> bonuses(String occupation, List<String> traits) {
        Map<Skill, Integer> out = new EnumMap<>(Skill.class);
        Occupation o = CharacterData.occupation(occupation);
        if (o != null) o.skills().forEach((s, n) -> out.merge(s, n, Integer::sum));
        for (String id : traits) {
            Trait t = CharacterData.trait(id);
            if (t != null) t.skills().forEach((s, n) -> out.merge(s, n, Integer::sum));
        }
        return out;
    }

    // ------------------------------------------------------------------ creating the survivor

    private static void create(ServerPlayer player, CharacterNet.Create create) {
        if (!ZcConfig.get().survival.characterCreation || player.hasAttached(TYPE)) return;
        String occupation = CharacterData.occupation(create.occupation()) != null ? create.occupation() : CharacterData.UNEMPLOYED;
        List<String> chosen = new ArrayList<>();
        for (String id : create.traits()) {
            if (blocked(occupation, chosen, id) == null) chosen.add(id);
        }
        int free = ZcConfig.get().survival.freeTraitPoints, every = penaltyEvery();
        // a budget that doesn't add up (an old screen, a tampered packet) loses its traits rather than its job
        if (points(occupation, chosen, free, every) < 0) chosen.clear();
        apply(player, occupation, chosen);
    }

    /** Gives a player this past: their traits, their starting skills and where their weight starts. */
    public static void apply(ServerPlayer player, String occupation, List<String> chosen) {
        Occupation o = CharacterData.occupation(occupation);
        List<String> all = new ArrayList<>(chosen);
        if (o != null) for (String free : o.traits()) if (!all.contains(free)) all.add(free);
        Profile profile = new Profile(occupation, List.copyOf(all));
        player.setAttached(TYPE, profile);
        BOOSTS.remove(player.getUUID());

        // starting skills: a level for each point, on top of 5 for Fitness and Strength
        Map<String, Integer> xp = new HashMap<>(player.getAttachedOrCreate(Skills.TYPE));
        for (Map.Entry<Skill, Integer> e : bonuses(occupation, all).entrySet()) {
            Skill skill = e.getKey();
            int level = Math.max(0, Math.min(Skill.MAX_LEVEL, (skill.passive() ? 5 : 0) + e.getValue()));
            int want = Skill.xpForLevel(level);
            // a survivor already partway through life (the first time creation runs in an old world) keeps what's learned
            xp.put(skill.name(), skill.passive() ? want : Math.max(want, Skills.xp(player, skill)));
        }
        player.setAttached(Skills.TYPE, xp);

        // weight traits set where your weight starts; it moves with what you eat from there
        float weight = all.contains("obese") ? 105 : all.contains("overweight") ? 92
                : all.contains("very_underweight") ? 60 : all.contains("underweight") ? 70 : 80;
        Nutrition n = Nutrition.get(player);
        Nutrition.set(player, n.withWeight(weight));

        player.sendSystemMessage(Component.literal("You were a " + (o == null ? "survivor" : o.name())
                + (all.isEmpty() ? "." : ": " + names(all) + ".")).withStyle(ChatFormatting.GRAY));
    }

    private static String names(List<String> ids) {
        List<String> out = new ArrayList<>();
        for (String id : ids) {
            Trait t = CharacterData.trait(id);
            out.add(t == null ? id : t.name());
        }
        return String.join(", ", out);
    }

    // ------------------------------------------------------------------ reading them

    public static Profile profile(Player player) {
        return player.getAttached(TYPE);
    }

    public static boolean has(Player player, String trait) {
        Profile p = player.getAttached(TYPE);
        return p != null && p.traits().contains(trait);
    }

    /** The traits as Zomboid shows them now: weight, Strength and Fitness traits follow the body, not the start. */
    public static List<String> shown(Player player) {
        Profile p = player.getAttached(TYPE);
        List<String> out = new ArrayList<>();
        if (p == null) return out;
        for (String id : p.traits()) {
            if (!Set.of("obese", "overweight", "underweight", "very_underweight", "strong", "stout", "weak", "feeble",
                    "athletic", "fit", "unfit", "out_of_shape").contains(id)) out.add(id);
        }
        float w = Nutrition.get(player).weight();
        if (w > Nutrition.OBESE) out.add("obese");
        else if (w > Nutrition.OVERWEIGHT) out.add("overweight");
        else if (w < Nutrition.VERY_UNDERWEIGHT) out.add("very_underweight");
        else if (w < Nutrition.UNDERWEIGHT) out.add("underweight");
        int str = Skills.level(player, Skill.STRENGTH), fit = Skills.level(player, Skill.FITNESS);
        if (str >= 9) out.add("strong");
        else if (str >= 6) out.add("stout");
        else if (str <= 1) out.add("weak");
        else if (str <= 4) out.add("feeble");
        if (fit >= 9) out.add("athletic");
        else if (fit >= 6) out.add("fit");
        else if (fit <= 1) out.add("unfit");
        else if (fit <= 4) out.add("out_of_shape");
        return out;
    }

    private static final Map<java.util.UUID, Map<Skill, Integer>> BOOSTS = new HashMap<>();

    /**
     * How much faster a skill learns for this survivor: Zomboid's boost for a skill the occupation or traits raised
     * (+75%, +100%, +125% for one, two, three or more levels), times Fast or Slow Learner, and Reluctant Fighter's
     * slower weapon skills.
     */
    public static double xpScale(Player player, Skill skill) {
        Profile p = player.getAttached(TYPE);
        if (p == null) return 1;
        Map<Skill, Integer> boosts = BOOSTS.computeIfAbsent(player.getUUID(), u -> bonuses(p.occupation(), p.traits()));
        double scale = 1;
        int b = skill.passive() ? 0 : boosts.getOrDefault(skill, 0);
        if (b >= 3) scale *= 2.25;
        else if (b == 2) scale *= 2.0;
        else if (b == 1) scale *= 1.75;
        if (p.traits().contains("fast_learner")) scale *= 1.3;
        if (p.traits().contains("slow_learner")) scale *= 0.7;
        if (p.traits().contains("reluctant_fighter") && (skill == Skill.BLUNT || skill == Skill.BLADE || skill == Skill.SPEAR
                || skill == Skill.AIMING)) scale *= 0.75;
        return scale;
    }

    /** Hunger builds this much faster: Hearty Appetite and Light Eater. */
    public static float hungerScale(Player player) {
        return has(player, "hearty_appetite") ? 1.5f : has(player, "light_eater") ? 0.75f : 1f;
    }

    /** Thirst builds this much faster: High and Low Thirst. */
    public static float thirstScale(Player player) {
        return has(player, "high_thirst") ? 2f : has(player, "low_thirst") ? 0.5f : 1f;
    }

    /** Tiredness builds this much faster: Wakeful, Sleepyhead, and the security guard's Night Owl. */
    public static float fatigueScale(Player player) {
        float f = has(player, "needs_less_sleep") ? 0.7f : has(player, "needs_more_sleep") ? 1.3f : 1f;
        return has(player, "night_owl") ? f * 0.8f : f;
    }

    /** How much each hour asleep rests you: Restless Sleeper's sleep does less. */
    public static float restScale(Player player) {
        return has(player, "restless_sleeper") ? 0.7f : 1f;
    }

    /** How hard fear lands: Brave much less, Cowardly much more, Desensitized not at all. */
    public static float panicScale(Player player) {
        if (has(player, "desensitized")) return 0f;
        return has(player, "brave") ? 0.3f : has(player, "cowardly") ? 2f : 1f;
    }

    /** Endurance comes back slower when you're Short of Breath, or carrying too much weight. */
    public static float enduranceScale(Player player) {
        float f = has(player, "short_of_breath") ? 0.7f : 1f;
        float w = Nutrition.get(player).weight();
        if (w > Nutrition.OBESE) f *= 0.8f;
        else if (w > Nutrition.OVERWEIGHT) f *= 0.9f;
        return f;
    }

    /** Reading time: Fast Reader 30% quicker, Slow Reader 30% slower. Illiterate can't read at all (see SkillBookItem). */
    public static float readScale(Player player) {
        return has(player, "fast_reader") ? 0.7f : has(player, "slow_reader") ? 1.3f : 1f;
    }

    private Traits() {
    }
}
