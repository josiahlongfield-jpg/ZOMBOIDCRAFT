package com.zomboidcraft.survival;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.zomboidcraft.ZomboidCraft;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

/**
 * Zomboid's two layers of eating, in place of Minecraft's food bar. Hunger is how empty your stomach feels right now;
 * the nutrition under it is what your body has banked: calories, carbs, fats and proteins, which move your weight over
 * days (see {@link Needs}).
 *
 * @param hunger   Zomboid's 0..1 scale: 0 fed, 0.15 peckish, 0.25 hungry, 0.45 very hungry, 0.7 starving. Below 0 you
 *                 have eaten past full (full at -0.05, stuffed at -0.15).
 * @param calories the bank, -2200 to 3700 like Zomboid's: above 1000 you put on weight, below 0 you lose it
 * @param carbs    grams banked, -500 to 1000; they fade back towards 0 as the body uses them
 * @param fats     grams banked, -500 to 1000
 * @param proteins grams banked, -500 to 1000: plenty speeds healing and builds Strength, too little slows both
 * @param weight   body weight in kg, 80 at the start
 */
public record Nutrition(float hunger, float calories, float carbs, float fats, float proteins, float weight) {
    public static final float PECKISH = 0.15f, HUNGRY = 0.25f, VERY_HUNGRY = 0.45f, STARVING = 0.7f;
    public static final float FULL = -0.05f, STUFFED = -0.15f, TOO_FULL = -0.3f;
    public static final float MIN_CALORIES = -2200, MAX_CALORIES = 3700, MIN_MACRO = -500, MAX_MACRO = 1000;
    /** Zomboid's weight lines: the weight traits follow them on their own. */
    public static final float EMACIATED = 50, VERY_UNDERWEIGHT = 65, UNDERWEIGHT = 75, OVERWEIGHT = 85, OBESE = 100;

    public static final Nutrition FRESH = new Nutrition(0.05f, 1000f, 0f, 0f, 0f, 80f);

    public static final Codec<Nutrition> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.FLOAT.fieldOf("hunger").forGetter(Nutrition::hunger),
            Codec.FLOAT.fieldOf("calories").forGetter(Nutrition::calories),
            Codec.FLOAT.fieldOf("carbs").forGetter(Nutrition::carbs),
            Codec.FLOAT.fieldOf("fats").forGetter(Nutrition::fats),
            Codec.FLOAT.fieldOf("proteins").forGetter(Nutrition::proteins),
            Codec.FLOAT.fieldOf("weight").forGetter(Nutrition::weight)
    ).apply(i, Nutrition::new));

    public static final StreamCodec<io.netty.buffer.ByteBuf, Nutrition> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.FLOAT, Nutrition::hunger,
            ByteBufCodecs.FLOAT, Nutrition::calories,
            ByteBufCodecs.FLOAT, Nutrition::carbs,
            ByteBufCodecs.FLOAT, Nutrition::fats,
            ByteBufCodecs.FLOAT, Nutrition::proteins,
            ByteBufCodecs.FLOAT, Nutrition::weight,
            Nutrition::new);

    public static final AttachmentType<Nutrition> TYPE = AttachmentRegistry.create(
            ZomboidCraft.id("nutrition"),
            builder -> builder
                    .persistent(CODEC)
                    .initializer(() -> FRESH)
                    .syncWith(STREAM_CODEC, AttachmentSyncPredicate.targetOnly()));

    public static void register() {
        // Loading the class registers the attachment.
    }

    public static Nutrition get(Player player) {
        return player.getAttachedOrCreate(TYPE);
    }

    public static void set(Player player, Nutrition nutrition) {
        if (!nutrition.equals(player.getAttached(TYPE))) {
            player.setAttached(TYPE, nutrition);
        }
    }

    /** The hunger moodle's stage: 0 none, 1 peckish, 2 hungry, 3 very hungry, 4 starving. */
    public int hungerStage() {
        return hunger >= STARVING ? 4 : hunger >= VERY_HUNGRY ? 3 : hunger >= HUNGRY ? 2 : hunger >= PECKISH ? 1 : 0;
    }

    /** 0 none, 1 full, 2 stuffed. */
    public int fullStage() {
        return hunger <= STUFFED ? 2 : hunger <= FULL ? 1 : 0;
    }

    /** Zomboid's weight trait names for this weight, or "" in the healthy band. */
    public String weightClass() {
        return weight < EMACIATED ? "Emaciated" : weight < VERY_UNDERWEIGHT ? "Very Underweight"
                : weight < UNDERWEIGHT ? "Underweight" : weight > OBESE ? "Obese" : weight > OVERWEIGHT ? "Overweight" : "";
    }

    public Nutrition withHunger(float value) {
        return new Nutrition(Mth.clamp(value, TOO_FULL, 1f), calories, carbs, fats, proteins, weight);
    }

    public Nutrition withWeight(float value) {
        return new Nutrition(hunger, calories, carbs, fats, proteins, Mth.clamp(value, 35f, 150f));
    }

    /** Adds (or with negative values takes) calories and macros, each kept inside Zomboid's range. */
    public Nutrition plus(float kcal, float carbs, float fats, float proteins) {
        return new Nutrition(hunger,
                Mth.clamp(calories + kcal, MIN_CALORIES, MAX_CALORIES),
                Mth.clamp(this.carbs + carbs, MIN_MACRO, MAX_MACRO),
                Mth.clamp(this.fats + fats, MIN_MACRO, MAX_MACRO),
                Mth.clamp(this.proteins + proteins, MIN_MACRO, MAX_MACRO),
                weight);
    }
}
