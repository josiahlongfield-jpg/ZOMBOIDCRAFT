package com.zomboidcraft.client;

import com.zomboidcraft.ZcConfig;
import com.zomboidcraft.combat.Stamina;
import com.zomboidcraft.survival.SurvivalStats;
import com.zomboidcraft.client.sound.UiSounds;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;

/** Zomboid-style status labels stacked in the top-right corner. Infection stays hidden until symptoms show. */
public final class MoodleHud {
    private static final int MILD = 0xFFE0C040;
    private static final int BAD = 0xFFE07020;
    private static final int SEVERE = 0xFFD02020;

    private record Moodle(String key, String label, int color) {
        int level() {
            return color == SEVERE ? 3 : color == BAD ? 2 : 1;
        }
    }

    /** How bad each moodle was last frame, so the heartbeat sounds once as one turns bad or severe. */
    private static Map<String, Integer> last = new HashMap<>();
    private static final Map<String, Long> beatAt = new HashMap<>();

    public static void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || player.isCreative() || player.isSpectator()) {
            return;
        }
        List<Moodle> moodles = moodles(SurvivalStats.get(player));
        if (ZcConfig.get().combat.stamina) {
            float endurance = player.getAttachedOrElse(Stamina.TYPE, Stamina.FULL);
            if (endurance < 10) moodles.add(new Moodle("breath", "Gasping for air", SEVERE));
            else if (endurance < 30) moodles.add(new Moodle("breath", "Out of breath", BAD));
            else if (endurance < 60) moodles.add(new Moodle("breath", "Winded", MILD));
        }
        if (ZcConfig.get().carry.weight) {
            int tier = com.zomboidcraft.carry.Carry.tier(player.getAttachedOrElse(com.zomboidcraft.carry.Carry.LOAD, 0f));
            if (tier == 4) moodles.add(new Moodle("load", "Crushed by your load", SEVERE));
            else if (tier == 3) moodles.add(new Moodle("load", "Overloaded", SEVERE));
            else if (tier == 2) moodles.add(new Moodle("load", "Very heavy load", BAD));
            else if (tier == 1) moodles.add(new Moodle("load", "Heavy load", MILD));
        }
        heartbeat(moodles);
        Font font = minecraft.font;
        int right = graphics.guiWidth() - 4;
        int y = 4;
        for (Moodle moodle : moodles) {
            int width = font.width(moodle.label()) + 10;
            int left = right - width;
            graphics.fill(left, y, right, y + 13, 0xB0101010);
            graphics.fill(left, y, left + 3, y + 13, moodle.color());
            graphics.text(font, moodle.label(), left + 6, y + 3, 0xFFE8E8E8);
            y += 15;
        }
    }

    private static void heartbeat(List<Moodle> moodles) {
        Map<String, Integer> now = new HashMap<>();
        boolean worse = false;
        long time = System.currentTimeMillis();
        for (Moodle moodle : moodles) {
            now.put(moodle.key(), moodle.level());
            // a moodle hovering on a line (breath while sprinting) beats at most every ten seconds
            if (moodle.level() >= 2 && moodle.level() > last.getOrDefault(moodle.key(), 0)
                    && time - beatAt.getOrDefault(moodle.key(), 0L) > 10_000) {
                beatAt.put(moodle.key(), time);
                worse = true;
            }
        }
        last = now;
        if (worse) UiSounds.play(UiSounds.MOODLE);
    }

    private static List<Moodle> moodles(SurvivalStats s) {
        List<Moodle> list = new ArrayList<>();
        if (s.thirst() <= 5) list.add(new Moodle("thirst", "Dying of thirst", SEVERE));
        else if (s.thirst() < 25) list.add(new Moodle("thirst", "Parched", BAD));
        else if (s.thirst() < 50) list.add(new Moodle("thirst", "Thirsty", MILD));

        if (s.fatigue() > 90) list.add(new Moodle("tired", "Exhausted", SEVERE));
        else if (s.fatigue() > 75) list.add(new Moodle("tired", "Very tired", BAD));
        else if (s.fatigue() > 60) list.add(new Moodle("tired", "Tired", MILD));

        if (s.wounds() > 0) {
            list.add(new Moodle("bleeding", s.wounds() == 1 ? "Bleeding" : "Bleeding x" + s.wounds(),
                    s.wounds() >= 3 ? SEVERE : BAD));
        }

        float infection = s.infectionProgress();
        if (infection > 0.8f) list.add(new Moodle("ill", "Gravely ill", SEVERE));
        else if (infection > 0.5f) list.add(new Moodle("ill", "Feverish", BAD));
        else if (infection > 0.25f) list.add(new Moodle("ill", "Queasy", MILD));
        return list;
    }

    private MoodleHud() {
    }
}
