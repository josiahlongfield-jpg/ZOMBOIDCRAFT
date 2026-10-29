package com.zomboidcraft.client.hud;

import com.zomboidcraft.ZcConfig;
import com.zomboidcraft.carry.Carry;
import com.zomboidcraft.client.sound.UiSounds;
import com.zomboidcraft.combat.Stamina;
import com.zomboidcraft.survival.Body;
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
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

/**
 * Moodles, Zomboid style: small enamel plates down the right edge, yellow, orange or red as a need gets worse, with a
 * sprayed stencil icon. A plate shows its name for a moment when it changes; mild ones fade after a while, bad ones
 * stay. Hunger, air and potion effects are moodles too (potions get our own names); infection stays hidden until
 * symptoms show.
 */
public final class MoodleHud {
    private static final int SIZE = 20, GAP = 3, RIGHT = 8;
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

    private record EffectMoodle(Holder<MobEffect> effect, String icon, int least, String... names) {
    }

    // our own words for Minecraft's potion effects; the rest keep the game's name on a plain vial
    private static final List<EffectMoodle> EFFECTS = List.of(
            new EffectMoodle(MobEffects.POISON, "skull", 2, "Poisoned", "Poisoned", "Badly poisoned"),
            new EffectMoodle(MobEffects.WITHER, "skull", 2, "Withering", "Withering", "Wasting away"),
            new EffectMoodle(MobEffects.HUNGER, "fork", 1, "Upset stomach", "Food poisoning", "Food poisoning"),
            new EffectMoodle(MobEffects.NAUSEA, "spiral", 1, "Dizzy", "Nauseous", "Retching"),
            new EffectMoodle(MobEffects.BLINDNESS, "eye", 2, "Can't see", "Can't see", "Blind"),
            new EffectMoodle(MobEffects.DARKNESS, "eye", 1, "Darkness closing in", "Darkness closing in", "Darkness closing in"),
            new EffectMoodle(MobEffects.SLOWNESS, "weight", 1, "Sluggish", "Heavy limbs", "Can barely move"),
            new EffectMoodle(MobEffects.MINING_FATIGUE, "weight", 1, "Tired arms", "Aching arms", "Arms like lead"),
            new EffectMoodle(MobEffects.WEAKNESS, "weight", 1, "Weak", "Very weak", "Feeble"),
            new EffectMoodle(MobEffects.REGENERATION, "heart", GOOD, "Recovering"),
            new EffectMoodle(MobEffects.HEALTH_BOOST, "heart", GOOD, "Hardy"),
            new EffectMoodle(MobEffects.ABSORPTION, "heart", GOOD, "Second wind"),
            new EffectMoodle(MobEffects.RESISTANCE, "heart", GOOD, "Tough skin"),
            new EffectMoodle(MobEffects.SPEED, "bolt", GOOD, "Adrenaline"),
            new EffectMoodle(MobEffects.HASTE, "bolt", GOOD, "Quick hands"),
            new EffectMoodle(MobEffects.STRENGTH, "bolt", GOOD, "Strong"),
            new EffectMoodle(MobEffects.JUMP_BOOST, "bolt", GOOD, "Light on your feet"));

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
            if (p.level != m.level()) {
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
                float w = Hud.capsWidth(font, p.label, 1f, .9f);
                float tx = x - 6 - w + (1 - n) * 3;
                Hud.caps(g, font, p.label, tx, y + 3, Hud.alpha(Hud.BONE, n * a), 1f, .9f);
                if (p.level != GOOD) {
                    for (int i = 0; i < 3; i++) {
                        int bx = Math.round(x - 6 - (3 - i) * 5 + (1 - n) * 3), by = Math.round(y + 13);
                        g.fill(bx, by, bx + 4, by + 1, Hud.alpha(i < p.level ? LEVEL_INK[p.level] : Hud.BONE_FAINT, n * a));
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
            case "air", "hunger", "thirst" -> m.level() == 3;
            case "load" -> m.label().startsWith("Crushed");
            default -> m.key().startsWith("effect:") && m.icon().equals("skull");
        };
    }

    private static List<Moodle> moodles(LocalPlayer player) {
        List<Moodle> list = new ArrayList<>();
        SurvivalStats s = SurvivalStats.get(player);
        add(list, "hunger", "fork", hungerLevel(player.getFoodData().getFoodLevel()), "Peckish", "Hungry", "Starving");
        add(list, "thirst", "drop", s.thirst() <= 5 ? 3 : s.thirst() < 25 ? 2 : s.thirst() < 50 ? 1 : 0,
                "Thirsty", "Parched", "Dying of thirst");
        add(list, "tired", "eye", s.fatigue() > 90 ? 3 : s.fatigue() > 75 ? 2 : s.fatigue() > 60 ? 1 : 0,
                "Tired", "Very tired", "Exhausted");
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
        float infection = s.infectionProgress();
        add(list, "ill", "thermo", infection > 0.8f ? 3 : infection > 0.5f ? 2 : infection > 0.25f ? 1 : 0,
                "Queasy", "Feverish", "Gravely ill");
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
        for (MobEffectInstance effect : player.getActiveEffects()) {
            if (!effect.showIcon()) continue;
            EffectMoodle known = null;
            for (EffectMoodle e : EFFECTS) if (effect.is(e.effect())) known = e;
            int amp = effect.getAmplifier();
            String key = "effect:" + effect.getDescriptionId();
            if (known == null) {
                boolean good = effect.getEffect().value().isBeneficial();
                String name = effect.getEffect().value().getDisplayName().getString();
                list.add(new Moodle(key, "vial", good ? GOOD : Math.min(3, amp + 1), name));
            } else if (known.least() == GOOD) {
                list.add(new Moodle(key, known.icon(), GOOD, known.names()[0]));
            } else {
                int level = Math.max(known.least(), Math.min(3, amp + 1));
                list.add(new Moodle(key, known.icon(), level, known.names()[level - 1]));
            }
        }
        return list;
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
