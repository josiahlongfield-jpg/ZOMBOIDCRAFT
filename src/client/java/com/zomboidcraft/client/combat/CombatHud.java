package com.zomboidcraft.client.combat;

import com.zomboidcraft.ZomboidCraft;
import com.zomboidcraft.combat.Attachments;
import com.zomboidcraft.combat.GunType;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/**
 * The view through a scope or the magnifier (the gun itself isn't drawn then: you're looking down the tube), and the
 * lit reticle of a dot sight the gun stays drawn behind. The rounds left and
 * the struggle prompt live in the field-kit HUD (client/hud).
 */
public final class CombatHud {
    private static final Identifier SCOPE = ZomboidCraft.id("textures/gui/scope_4x.png");
    private static final int BLACK = 0xFF050505;
    private static final int RED = 0xE0FF2A1A;

    /** Drawn under the rest of the HUD, like the spyglass: the scope picture, or a red-dot sight's reticle. */
    public static void scope(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || !minecraft.options.getCameraType().isFirstPerson() || !player.isUsingItem()
                || !GunType.isGun(player.getUseItem()) || CombatClient.reloading(player.getUseItem())) {
            return;
        }
        int w = graphics.guiWidth(), h = graphics.guiHeight();
        if (!Attachments.scoped(player.getUseItem())) {
            reticle(graphics, Attachments.reticle(player.getUseItem()), w / 2, h / 2);
            return;
        }
        int x = (w - h) / 2;
        graphics.blit(RenderPipelines.GUI_TEXTURED, SCOPE, x, 0, 0, 0, h, h, h, h);
        graphics.fill(0, 0, x, h, BLACK);
        graphics.fill(x + h, 0, w, h, BLACK);
    }

    /** The lit mark a dot sight puts on its glass, drawn where the sight line meets the middle of the view. */
    private static void reticle(GuiGraphicsExtractor graphics, Attachments.Reticle reticle, int cx, int cy) {
        switch (reticle) {
            case DOT -> graphics.fill(cx - 1, cy - 1, cx + 1, cy + 1, RED);
            case RING -> {
                graphics.fill(cx - 1, cy - 1, cx + 1, cy + 1, RED);
                for (int i = 0; i < 24; i++) {
                    double a = i * Math.PI * 2 / 24;
                    int px = cx + (int) Math.round(Math.cos(a) * 7), py = cy + (int) Math.round(Math.sin(a) * 7);
                    graphics.fill(px, py, px + 1, py + 1, RED);
                }
            }
            case CROSS -> {
                graphics.fill(cx - 6, cy, cx - 1, cy + 1, BLACK);
                graphics.fill(cx + 2, cy, cx + 7, cy + 1, BLACK);
                graphics.fill(cx, cy - 6, cx + 1, cy - 1, BLACK);
                graphics.fill(cx, cy + 2, cx + 1, cy + 7, BLACK);
                graphics.fill(cx, cy, cx + 1, cy + 1, RED);
            }
            case NONE -> {
            }
        }
    }

    private CombatHud() {
    }
}
