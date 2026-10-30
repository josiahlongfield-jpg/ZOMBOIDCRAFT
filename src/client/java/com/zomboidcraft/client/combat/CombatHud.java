package com.zomboidcraft.client.combat;

import com.zomboidcraft.combat.Attachments;
import com.zomboidcraft.combat.GunType;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/**
 * The view through a scope you look into (the gun itself isn't drawn then: you're looking down the tube), and the lit
 * reticle of a sight the gun stays drawn behind, three times bigger through the magnifier. Each optic shows its own
 * (SightTextures: the pack's own where it came with one). The rounds left and the struggle prompt live in the
 * field-kit HUD (client/hud).
 */
public final class CombatHud {
    private static final int BLACK = 0xFF050505;

    /** Drawn under the rest of the HUD, like the spyglass: the scope's eyepiece picture, or a sight's reticle. */
    public static void scope(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || !minecraft.options.getCameraType().isFirstPerson() || !player.isUsingItem()
                || !GunType.isGun(player.getUseItem()) || CombatClient.reloading(player.getUseItem())) {
            return;
        }
        ItemStack gun = player.getUseItem();
        String key = Attachments.sightKey(gun);
        if (key == null) {
            return;
        }
        Identifier sight = SightTextures.get(key);
        int w = graphics.guiWidth(), h = graphics.guiHeight();
        if (!Attachments.scoped(gun)) {
            int size = (int) Math.round(Attachments.reticleSize(gun) * h);
            if (size > 0) {
                size = Math.max(size, 2);
                graphics.blit(RenderPipelines.GUI_TEXTURED, sight, (w - size) / 2, (h - size) / 2, 0, 0, size, size, size, size);
            }
            return;
        }
        int x = (w - h) / 2;
        graphics.blit(RenderPipelines.GUI_TEXTURED, sight, x, 0, 0, 0, h, h, h, h);
        graphics.fill(0, 0, x, h, BLACK);
        graphics.fill(x + h, 0, w, h, BLACK);
    }

    private CombatHud() {
    }
}
