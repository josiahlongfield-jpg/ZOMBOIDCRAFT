package com.zomboidcraft.client.combat;

import com.zomboidcraft.ZcConfig;
import com.zomboidcraft.combat.GunType;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import org.joml.Matrix3x2fStack;

/**
 * The game's crosshair drawn smaller (its lines kept a whole number of screen pixels wide so they stay sharp),
 * and hidden while aiming down a rifle's sights, where the sight itself is the aim point.
 */
public final class Crosshair {
    private static final int SIZE = 15;     // the crosshair sprite, in GUI pixels

    public static HudElement wrap(HudElement vanilla) {
        return (graphics, deltaTracker) -> {
            Minecraft minecraft = Minecraft.getInstance();
            ZcConfig.Look look = ZcConfig.get().look;
            LocalPlayer player = minecraft.player;
            if (look.hideCrosshairWhenAiming && player != null && player.isUsingItem()
                    && GunType.isGun(player.getUseItem()) && !CombatClient.reloading(player.getUseItem())) {
                return;
            }
            float gui = (float) minecraft.getWindow().getGuiScale();
            int line = Math.max(1, Math.round(gui * (float) look.crosshairScale));
            if (line >= gui) {
                vanilla.extractRenderState(graphics, deltaTracker);
                return;
            }
            float k = line / gui;
            float cx = (graphics.guiWidth() - SIZE) / 2 + SIZE / 2f;
            float cy = (graphics.guiHeight() - SIZE) / 2 + SIZE / 2f;
            // nudge so the shrunken sprite's corner lands on a screen pixel
            float px = (cx - SIZE / 2f * k) * gui, py = (cy - SIZE / 2f * k) * gui;
            float dx = (Math.round(px) - px) / gui, dy = (Math.round(py) - py) / gui;
            Matrix3x2fStack pose = graphics.pose();
            pose.pushMatrix();
            pose.translate(cx + dx, cy + dy);
            pose.scale(k, k);
            pose.translate(-cx, -cy);
            vanilla.extractRenderState(graphics, deltaTracker);
            pose.popMatrix();
        };
    }

    private Crosshair() {
    }
}
