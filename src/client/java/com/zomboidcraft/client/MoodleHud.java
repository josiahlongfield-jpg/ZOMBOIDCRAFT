package com.zomboidcraft.client;

import com.zomboidcraft.survival.SurvivalStats;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;

/** Zomboid-style status labels stacked in the top-right corner. Infection stays hidden until symptoms show. */
public final class MoodleHud {
    private static final int MILD = 0xFFE0C040;
    private static final int BAD = 0xFFE07020;
    private static final int SEVERE = 0xFFD02020;

    private record Moodle(String label, int color) {
    }

    public static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || player.isCreative() || player.isSpectator()) {
            return;
        }
        List<Moodle> moodles = moodles(SurvivalStats.get(player));
        Font font = minecraft.font;
        int right = graphics.guiWidth() - 4;
        int y = 4;
        for (Moodle moodle : moodles) {
            int width = font.width(moodle.label()) + 10;
            int left = right - width;
            graphics.fill(left, y, right, y + 13, 0xB0101010);
            graphics.fill(left, y, left + 3, y + 13, moodle.color());
            graphics.drawString(font, moodle.label(), left + 6, y + 3, 0xFFE8E8E8);
            y += 15;
        }
    }

    private static List<Moodle> moodles(SurvivalStats s) {
        List<Moodle> list = new ArrayList<>();
        if (s.thirst() <= 5) list.add(new Moodle("Dying of thirst", SEVERE));
        else if (s.thirst() < 25) list.add(new Moodle("Parched", BAD));
        else if (s.thirst() < 50) list.add(new Moodle("Thirsty", MILD));

        if (s.fatigue() > 90) list.add(new Moodle("Exhausted", SEVERE));
        else if (s.fatigue() > 75) list.add(new Moodle("Very tired", BAD));
        else if (s.fatigue() > 60) list.add(new Moodle("Tired", MILD));

        if (s.wounds() > 0) {
            list.add(new Moodle(s.wounds() == 1 ? "Bleeding" : "Bleeding x" + s.wounds(),
                    s.wounds() >= 3 ? SEVERE : BAD));
        }

        float infection = s.infectionProgress();
        if (infection > 0.8f) list.add(new Moodle("Gravely ill", SEVERE));
        else if (infection > 0.5f) list.add(new Moodle("Feverish", BAD));
        else if (infection > 0.25f) list.add(new Moodle("Queasy", MILD));
        return list;
    }

    private MoodleHud() {
    }
}
