package com.zomboidcraft.client.hud;

import com.zomboidcraft.combat.Grabs;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;

/** A zombie has hold of you: GRABBED shaking under the crosshair, how to get loose, and the struggles left. */
final class GrabHud {
    private static final Hud.Fade fade = new Hud.Fade();
    private static int left;

    static void render(GuiGraphicsExtractor g) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) return;
        Integer grabbed = player.getAttached(Grabs.GRABBED);
        if (grabbed != null && grabbed > 0) left = grabbed;
        float a = fade.update(grabbed != null && grabbed > 0);
        if (a <= 0) return;
        Font font = mc.font;
        int cx = g.guiWidth() / 2, y = g.guiHeight() / 2 + 16;
        // a little shake, two frames a tick
        float shake = (player.tickCount / 2) % 2 == 0 ? .5f : -.5f;
        float w = Hud.capsWidth(font, "Grabbed", 3.5f, 1.6f);
        Hud.caps(g, font, "Grabbed", cx - w / 2 + shake, y - shake * .6f, Hud.alpha(0xFFE04A32, a), 3.5f, 1.6f);
        String how = "Mash jump or shove (V)";
        float hw = Hud.capsWidth(font, how, 1.2f, .8f);
        Hud.caps(g, font, how, cx - hw / 2, y + 16, Hud.alpha(Hud.BONE_DIM, a), 1.2f, .8f);
        int pip = 5, gap = 2, total = left * (pip + gap) - gap;
        for (int i = 0; i < left; i++) {
            int px = cx - total / 2 + i * (pip + gap);
            g.fill(px, y + 26, px + pip, y + 27, Hud.alpha(0xFFC43420, a));
        }
    }

    private GrabHud() {
    }
}
