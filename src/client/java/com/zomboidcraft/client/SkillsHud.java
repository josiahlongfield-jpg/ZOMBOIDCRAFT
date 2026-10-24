package com.zomboidcraft.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.zomboidcraft.ZomboidCraft;
import com.zomboidcraft.skills.Skill;
import com.zomboidcraft.skills.Skills;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import org.lwjgl.glfw.GLFW;

/** Hold K to see skill levels and progress. */
public final class SkillsHud {
    private static final int WIDTH = 150;
    private static KeyMapping key;

    public static void register() {
        KeyMapping.Category category = KeyMapping.Category.register(ZomboidCraft.id("main"));
        key = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.zomboidcraft.skills", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_K, category, 0));
    }

    public static void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || key == null || !key.isDown() || minecraft.screen != null) {
            return;
        }
        Font font = minecraft.font;
        int left = 6;
        int top = 6;
        int rows = Skill.values().length;
        graphics.fill(left - 4, top - 4, left + WIDTH + 4, top + 14 + rows * 18, 0xC0101010);
        graphics.text(font, "Skills", left, top, 0xFFE8C060);
        int y = top + 14;
        for (Skill skill : Skill.values()) {
            int xp = Skills.xp(player, skill);
            int level = Skill.levelForXp(xp);
            graphics.text(font, skill.displayName() + "  " + level, left, y, 0xFFE8E8E8);
            float progress = 1f;
            if (level < Skill.MAX_LEVEL) {
                int from = Skill.xpForLevel(level);
                int to = Skill.xpForLevel(level + 1);
                progress = (xp - from) / (float) (to - from);
            }
            graphics.fill(left, y + 10, left + WIDTH, y + 13, 0xFF333333);
            graphics.fill(left, y + 10, left + Math.round(WIDTH * progress), y + 13, 0xFF6FA040);
            y += 18;
        }
    }

    private SkillsHud() {
    }
}
