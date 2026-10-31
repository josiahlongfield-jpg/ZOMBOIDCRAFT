package com.zomboidcraft.client.hud;

import com.zomboidcraft.ZcConfig;
import com.zomboidcraft.carry.Carry;
import com.zomboidcraft.client.sound.UiSounds;
import com.zomboidcraft.combat.Stamina;
import com.zomboidcraft.survival.Ailments;
import com.zomboidcraft.survival.Body;
import com.zomboidcraft.survival.Mood;
import com.zomboidcraft.survival.Panic;
import com.zomboidcraft.survival.Needs;
import com.zomboidcraft.survival.Nutrition;
import com.zomboidcraft.survival.SurvivalStats;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

/**
 * Moodles, Zomboid style: small enamel plates down the right edge, yellow, orange or red as a need gets worse, with a
 * sprayed stencil icon. A plate shows its name for a moment when it changes; mild ones fade after a while, bad ones
 * stay. Hunger, thirst and tiredness have Zomboid's four stages; Minecraft's potion effects never get plates of their
 * own (a bad stomach shows as Sick); infection stays hidden until symptoms show.
 */
public final class MoodleHud {
    private static final int SIZE = 12, GAP = 3, RIGHT = 8;
    /** The name's scale, and how far below its draw point the capitals start (measured in game, per unit of scale). */
    private static final float LABEL = .9f, CAP_TOP = 1.5f;
    private static final int GOOD = 4;
    private static final float MILD_SHOWS = 6f, NAME_SHOWS = 3.5f, TREND_SHOWS = 4f;
    private static final int[] LEVEL_INK = {0, 0xFFE8CF7C, 0xFFE69A66, 0xFFCE675B, 0xFF8EB07E};

    /** One moodle this frame: a need, its level (1 mild to 3 severe, or GOOD) and its words. */
    private record Moodle(String key, String icon, int level, String label) {
    }

    /** A moodle on screen, kept a little after it ends so it can fade out. */
    private static final class Plate {
        String icon, label;
        int level, trend;
        float since;
        boolean live;
        final Hud.Fade fade = new Hud.Fade(), name = new Hud.Fade();
    }

    private static final Map<String, Plate> plates = new LinkedHashMap<>();
    private static final Map<String, Long> beatAt = new HashMap<>();
    /** How wet you are, 0 dry to 1 soaked: rain and water soak in, and you dry off slowly. */
    private static float wet;
    private static long wetTick = -1;

    /** Draws the plates from {@code top} down. */
    public static void render(GuiGraphicsExtractor g, int top) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) return;
        List<Moodle> now = moodles(player);
        heartbeat(now);
        for (Plate p : plates.values()) p.live = false;
        for (Moodle m : now) {
            Plate p = plates.computeIfAbsent(m.key(), k -> new Plate());
            if (p.level != m.level() || p.label != null && !p.label.equals(m.label()) && m.key().matches("hunger|thirst|tired|full")) {
                p.trend = p.level == 0 || m.level() == GOOD || p.level == GOOD ? 0 : m.level() > p.level ? 1 : -1;
                p.level = m.level();
                p.since = 0;
            }
            p.icon = m.icon();
            p.label = m.label();
            p.live = true;
        }

        Font font = mc.font;
        int x = g.guiWidth() - RIGHT - SIZE;
        float y = top;
        for (Iterator<Plate> it = plates.values().iterator(); it.hasNext(); ) {
            Plate p = it.next();
            p.since += Hud.dt;
            boolean bad = p.level >= 2 && p.level != GOOD;
            float a = p.fade.update(p.live && (Hud.peek || bad || p.since < MILD_SHOWS));
            float n = p.name.update(p.live && (Hud.peek || p.since < NAME_SHOWS));
            if (a <= 0) {
                if (!p.live) it.remove();
                continue;
            }
            float slide = (1 - a) * 4;
            String level = p.level == GOOD ? "good" : String.valueOf(Math.max(1, Math.min(3, p.level)));
            Hud.sprite(g, Hud.id("moodle/" + p.icon + "_" + level), x + slide, y, SIZE, SIZE, Hud.alpha(0xFFFFFFFF, a));
            if (p.trend != 0 && p.since < TREND_SHOWS && p.live) {
                Hud.sprite(g, Hud.id(p.trend > 0 ? "arrow_up" : "arrow_down"), x - 3.5f + slide, y + SIZE / 2f - 2.5f, 5, 5,
                        Hud.alpha(p.trend > 0 ? 0xFFE8D9B0 : 0xFF8FBF5E, a));
            }
            if (n > 0) {
                // the name's capitals start level with the plate's top edge and the level bars end on its bottom edge
                float w = Hud.capsWidth(font, p.label, 1f, LABEL);
                float tx = x - 6 - w + (1 - n) * 3;
                Hud.caps(g, font, p.label, tx, y - CAP_TOP * LABEL, Hud.alpha(Hud.BONE, n * a), 1f, LABEL);
                if (p.level != GOOD) {
                    for (int i = 0; i < 3; i++) {
                        int bx = Math.round(x - 6 - (3 - i) * 5 + (1 - n) * 3), by = Math.round(y + SIZE - 2);
                        g.fill(bx, by, bx + 4, by + 2, Hud.alpha(i < p.level ? LEVEL_INK[p.level] : Hud.BONE_FAINT, n * a));
                    }
                }
            }
            y += (SIZE + GAP) * Math.min(1, a * 1.5f);
        }
    }

    private static void heartbeat(List<Moodle> moodles) {
        long time = System.currentTimeMillis();
        boolean worse = false;
        for (Moodle moodle : moodles) {
            Plate before = plates.get(moodle.key());
            int was = before == null ? 0 : before.level;
            // a moodle hovering on a line (breath while sprinting) beats at most every ten seconds
            if (harms(moodle) && moodle.level() >= 2 && moodle.level() != GOOD && moodle.level() > (was == GOOD ? 0 : was)
                    && time - beatAt.getOrDefault(moodle.key(), 0L) > 10_000) {
                beatAt.put(moodle.key(), time);
                worse = true;
            }
        }
        if (worse) {
            UiSounds.play(UiSounds.MOODLE);
            if (Hud.active()) BodyHud.beat();
        }
    }

    /**
     * Whether a moodle means you're being hurt, which is all the heartbeat and the red flush are for: wounds, bleeding,
     * infection, poison, drowning, starving or dying of thirst, and a load heavy enough to crush you. Being wet, tired,
     * winded or merely hungry never beats.
     */
    private static boolean harms(Moodle m) {
        return switch (m.key()) {
            case "bleeding", "injury", "ill" -> true;
            case "pain" -> m.label().equals("Agony");
            case "air" -> m.level() == 3;
            case "hunger" -> m.label().equals("Starving");
            case "thirst" -> m.label().equals("Dying of Thirst");
            case "load" -> m.label().startsWith("Crushed");
            default -> false;
        };
    }

    private static List<Moodle> moodles(LocalPlayer player) {
        List<Moodle> list = new ArrayList<>();
        SurvivalStats s = SurvivalStats.get(player);
        // Zomboid's four stages; the plate's colour tops out at red for the last two
        if (Needs.on()) {
            Nutrition n = Nutrition.get(player);
            stage(list, "hunger", "fork", n.hungerStage(), "Peckish", "Hungry", "Very Hungry", "Starving");
            int full = n.fullStage();
            if (full > 0) list.add(new Moodle("full", "fork", GOOD, full == 2 ? "Stuffed" : "Full"));
        } else {
            add(list, "hunger", "fork", hungerLevel(player.getFoodData().getFoodLevel()), "Peckish", "Hungry", "Starving");
        }
        stage(list, "thirst", "drop", Needs.thirstStage(s.thirst()), "Slightly Thirsty", "Thirsty", "Parched", "Dying of Thirst");
        stage(list, "tired", "eye", Needs.tiredStage(s.fatigue()), "Drowsy", "Tired", "Very Tired", "Ridiculously Tired");
        Body body = Body.get(player);
        int bleeding = body.bleeding();
        if (bleeding > 0) {
            add(list, "bleeding", "blood", bleeding >= 3 ? 3 : 2, "Bleeding",
                    bleeding == 1 ? "Bleeding" : "Bleeding x" + bleeding, "Heavy bleeding");
        }
        // the worst injury gets a plate of its own: a bone for a break, a flame for a burn, a slash for a wound
        Body.Region worst = body.worst();
        if (worst != null) {
            Body.Part part = body.part(worst);
            String icon = part.fractured() ? "bone" : part.wound() == Body.Wound.BURN ? "flame" : "cut";
            String label = Body.describe(worst, part);
            add(list, "injury", icon, part.severity(), label, label, label);
        }
        // one Sick plate, Zomboid's: the infection, a fevered wound, or a bad stomach (rotten food, poison, nausea)
        float infection = s.infectionProgress();
        int fever = Ailments.get(player, "fever");
        int ill = Math.max(infection > 0.8f ? 3 : infection > 0.5f ? 2 : infection > 0.25f ? 1 : 0,
                fever >= 70 ? 3 : fever >= 40 ? 2 : fever > 0 ? 1 : 0);
        int nausea = Ailments.get(player, "nausea");
        int stomach = nausea >= 75 ? 3 : nausea >= 50 ? 2 : nausea >= 25 ? 1 : 0;
        for (MobEffectInstance effect : player.getActiveEffects()) {
            if (effect.is(MobEffects.POISON)) stomach = Math.max(stomach, Math.min(3, 2 + effect.getAmplifier()));
            else if (effect.is(MobEffects.HUNGER) || effect.is(MobEffects.NAUSEA)) stomach = Math.max(stomach, Math.min(3, 1 + effect.getAmplifier()));
        }
        if (stomach > ill) add(list, "ill", "thermo", stomach, "Queasy", "Nauseous", "Sick");
        else add(list, "ill", "thermo", ill, "Queasy", "Feverish", "Gravely ill");
        // Zomboid's pain stages, from wounds, breaks, a bad tooth or a cold (LOOT's Medicine adds it up)
        int pain = Ailments.get(player, "pain");
        stage(list, "pain", "pain", pain >= 75 ? 4 : pain >= 50 ? 3 : pain >= 25 ? 2 : pain >= 10 ? 1 : 0,
                "Minor Pain", "Pain", "Severe Pain", "Agony");
        if (Ailments.get(player, "cold") > 0) list.add(new Moodle("cold", "germ", 1, "Has a Cold"));
        int drunk = Ailments.get(player, "drunk");
        stage(list, "drunk", "bottle", drunk >= 85 ? 4 : drunk >= 60 ? 3 : drunk >= 30 ? 2 : drunk >= 10 ? 1 : 0,
                "Tipsy", "Drunk", "Very Drunk", "Hammered");
        stage(list, "panic", "fear", Panic.stage(Panic.get(player)), "Slight Panic", "Panic", "Strong Panic", "Extreme Panic");
        // Zomboid's mood moodles, from LOOT's Mood (0-100, bands at 25, 50, 75 and 90)
        mood(list, player, "bored", "hourglass", Mood.Stat.BOREDOM, "Slightly Bored", "Bored", "Very Bored", "Extremely Bored");
        mood(list, player, "unhappy", "sad", Mood.Stat.UNHAPPINESS, "A Bit Sad", "Unhappy", "Very Unhappy", "Depressed");
        mood(list, player, "stress", "stress", Mood.Stat.STRESS, "Anxious", "Agitated", "Stressed", "Nervous Wreck");
        if (ZcConfig.get().combat.stamina) {
            float endurance = player.getAttachedOrElse(Stamina.TYPE, Stamina.FULL);
            add(list, "breath", "lungs", endurance < 10 ? 3 : endurance < 30 ? 2 : endurance < 60 ? 1 : 0,
                    "Winded", "Out of breath", "Gasping for air");
        }
        int air = player.getAirSupply(), maxAir = player.getMaxAirSupply();
        add(list, "air", "lungs", air <= 0 ? 3 : air < maxAir / 3 ? 2 : air < maxAir ? 1 : 0,
                "Holding your breath", "Short of air", "Drowning");
        if (ZcConfig.get().carry.weight) {
            int tier = Carry.tier(player.getAttachedOrElse(Carry.LOAD, 0f));
            add(list, "load", "pack", Math.min(3, tier), "Heavy load", "Very heavy load",
                    tier == 4 ? "Crushed by your load" : "Overloaded");
        }
        soak(player);
        add(list, "wet", "rain", wet > .85f ? 3 : wet > .5f ? 2 : wet > .2f ? 1 : 0, "Damp", "Wet", "Soaked");
        return list;
    }

    private static void mood(List<Moodle> list, LocalPlayer player, String key, String icon, Mood.Stat stat, String... names) {
        int v = Mood.get(player, stat);
        stage(list, key, icon, v >= 90 ? 4 : v >= 75 ? 3 : v >= 50 ? 2 : v >= 25 ? 1 : 0, names);
    }

    /** A Zomboid moodle with four stages (1 to 4). */
    private static void stage(List<Moodle> list, String key, String icon, int stage, String... names) {
        if (stage > 0) list.add(new Moodle(key, icon, Math.min(3, stage), names[stage - 1]));
    }

    private static void add(List<Moodle> list, String key, String icon, int level, String mild, String bad, String severe) {
        if (level > 0) list.add(new Moodle(key, icon, level, level == 1 ? mild : level == 2 ? bad : severe));
    }

    /** Vanilla food points as Zomboid hunger. */
    private static int hungerLevel(int food) {
        return food <= 6 ? 3 : food <= 11 ? 2 : food <= 15 ? 1 : 0;
    }

    private static void soak(LocalPlayer player) {
        long tick = player.level().getGameTime();
        if (tick == wetTick) return;
        // a newer world's clock can be behind the last one's
        long ticks = wetTick < 0 || tick < wetTick ? 1 : Math.min(20, tick - wetTick);
        wetTick = tick;
        // a raincoat or poncho keeps most of the rain off (clothing/Clothes water)
        float rain = 0.0015f * (1 - com.zomboidcraft.clothing.Clothes.totals(player).water());
        for (long i = 0; i < ticks; i++) {
            if (player.isInWater()) wet = Math.min(1, wet + 0.02f);
            else if (player.isInWaterOrRain()) wet = Math.min(1, wet + rain);
            else wet = Math.max(0, wet - 0.0006f);
        }
    }

    private MoodleHud() {
    }
}
