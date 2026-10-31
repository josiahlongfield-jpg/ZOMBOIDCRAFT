package com.zomboidcraft.combat;

import com.zomboidcraft.ZcConfig;
import com.zomboidcraft.clothing.Clothes;
import com.zomboidcraft.clothing.ClothingSlot;
import com.zomboidcraft.clothing.Garment;
import com.zomboidcraft.skills.Skill;
import com.zomboidcraft.skills.Skills;
import com.zomboidcraft.survival.Body;
import com.zomboidcraft.survival.SurvivalStats;
import com.zomboidcraft.world.weather.Weather;
import com.zomboidcraft.world.weather.WeatherDirector;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/**
 * Zomboid's six Firearms settings (Josiah 2026-10-31: Zomboid's version, with use damage chance on for both and the
 * weather only a light touch, as it never stops raining): how far shots are heard, how often guns jam, how much
 * moodles, the weather and headgear throw the aim off, and who a shot strikes just by being aimed at.
 */
public final class Firearms {
    /** Headgear that gets in the way of the sights, and how much it widens the spread (Firearm Headgear Effect). */
    private static final Map<String, Double> HEADGEAR = Map.of(
            "gas_mask", 1.3, "welding_goggles", 1.25, "hockey_mask", 1.15, "motorcycle_helmet", 1.15,
            "riot_helmet", 1.15, "football_helmet", 1.1, "fire_helmet", 1.1, "pilot_helmet", 1.05);

    private Firearms() {
    }

    private static ZcConfig.Guns config() {
        return ZcConfig.get().guns;
    }

    /** Firearm Noise Multiplier: how far a gunshot carries, 0.2 to 2 times. */
    public static double noise(double radius) {
        return radius * Math.clamp(config().noiseMultiplier, 0.2, 2.0);
    }

    /** Firearm Jam Multiplier: 0 (the default, Josiah cut jams on 10-26) turns jamming off. */
    public static double jam(double chance) {
        return chance * Math.clamp(config().jamMultiplier, 0.0, 10.0);
    }

    /** What moodles, weather and headgear do to the spread, as one multiplier (1 = no effect). */
    public static double aim(Player player) {
        ZcConfig.Guns c = config();
        double moodles = Math.clamp(c.moodleMultiplier, 0.0, 10.0) * moodles(player);
        double weather = Math.clamp(c.weatherMultiplier, 0.0, 10.0) * weather(player);
        double headgear = c.headgearEffect ? headgear(player) : 1.0;
        return (1 + moodles) * (1 + weather) * headgear;
    }

    /** Tiredness, worn-out endurance and hurt arms: up to about +0.9 at their worst together. */
    static double moodles(Player player) {
        double tired = SurvivalStats.get(player).fatigue() / 100.0 * 0.4;
        double winded = Math.max(0, 1 - Stamina.get(player) / Stamina.FULL - 0.5) * 0.5;
        Body body = Body.get(player);
        int arms = body.parts().get(Body.Region.LEFT_ARM.ordinal()).severity()
                + body.parts().get(Body.Region.RIGHT_ARM.ordinal()).severity();
        return tired + winded + Math.min(arms, 4) * 0.06;
    }

    /**
     * Wind, rain and fog, out under the sky only. Light by design: the endless rain costs about 2%, a downpour or a
     * gale about 10%, thick fog about 20%.
     */
    static double weather(Player player) {
        Weather w = WeatherDirector.of(player.level());
        if (w == null || !player.level().canSeeSky(BlockPos.containing(player.getEyePosition()))) {
            return 0;
        }
        return 0.035 * w.rain() + 0.15 * Math.max(0, w.wind() - 0.25) + 0.25 * Math.max(0, w.fog() - 0.15);
    }

    static double headgear(Player player) {
        double worst = 1.0;
        for (ClothingSlot slot : new ClothingSlot[] {ClothingSlot.HAT, ClothingSlot.EYES, ClothingSlot.FACE}) {
            Garment g = Clothes.of(slot.worn(player));
            if (g != null) {
                worst = Math.max(worst, HEADGEAR.getOrDefault(g.id(), 1.0));
            }
        }
        return worst;
    }

    /**
     * Firearm Use Damage Chance: whether a shot strikes what it is aimed at ("both", "zombies" or "off" for players and
     * zombies alike). Where it doesn't apply, Zomboid's older chance to hit decides: about half at Aiming 0, rising
     * 5% a level, less when the moodles are bad.
     */
    public static boolean strikes(Player shooter, Entity target) {
        String mode = config().useDamageChance;
        boolean player = target instanceof Player;
        if ("both".equals(mode) || "zombies".equals(mode) && !player) {
            return true;
        }
        double chance = 0.5 + 0.05 * Skills.level(shooter, Skill.AIMING) - 0.3 * moodles(shooter);
        return shooter.getRandom().nextDouble() < Math.clamp(chance, 0.1, 0.95);
    }
}
