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
 * The view through a 4x scope (the gun itself isn't drawn then: you're looking down the tube). The rounds left and
 * the struggle prompt live in the field-kit HUD (client/hud).
 */
public final class CombatHud {
    private static final Identifier SCOPE = ZomboidCraft.id("textures/gui/scope_4x.png");
    private static final int BLACK = 0xFF050505;

    /** Drawn under the rest of the HUD, like the spyglass. */
    public static void scope(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || !minecraft.options.getCameraType().isFirstPerson() || !player.isUsingItem()
                || !GunType.isGun(player.getUseItem()) || !Attachments.scoped(player.getUseItem())
                || CombatClient.reloading(player.getUseItem())) {
            return;
        }
        int w = graphics.guiWidth(), h = graphics.guiHeight();
        int x = (w - h) / 2;
        graphics.blit(RenderPipelines.GUI_TEXTURED, SCOPE, x, 0, 0, 0, h, h, h, h);
        graphics.fill(0, 0, x, h, BLACK);
        graphics.fill(x + h, 0, w, h, BLACK);
    }

    private CombatHud() {
    }
}
