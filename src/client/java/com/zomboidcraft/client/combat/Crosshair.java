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
            if (com.zomboidcraft.client.camera.ShoulderCam.active()) {
                shoulderReticle(graphics, player);
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

    /**
     * Over the shoulder Minecraft draws no crosshair; this one sits where the camera's turned to look, which is what
     * the shot will hit: a dot with four short ticks, the ticks opening out with a gun from the hip and closing in
     * as it aims, with a dark edge so it reads on snow and sky alike.
     */
    private static void shoulderReticle(net.minecraft.client.gui.GuiGraphicsExtractor g, LocalPlayer player) {
        int cx = g.guiWidth() / 2, cy = g.guiHeight() / 2;
        boolean gun = player != null && GunType.isGun(player.getMainHandItem());
        boolean aiming = gun && player.isUsingItem();
        int gap = !gun ? 3 : aiming ? 2 : 5, len = 3;
        int shade = 0x99000000, ink = 0xE6F2F2F2;
        g.fill(cx - 1, cy - 1, cx + 2, cy + 2, shade);
        g.fill(cx, cy, cx + 1, cy + 1, ink);
        int[][] arms = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] a : arms) {
            int x0 = cx + a[0] * gap, y0 = cy + a[1] * gap;
            int x1 = cx + a[0] * (gap + len), y1 = cy + a[1] * (gap + len);
            int ax = Math.min(x0, x1), bx = Math.max(x0, x1) + 1, ay = Math.min(y0, y1), by = Math.max(y0, y1) + 1;
            g.fill(ax - 1, ay - 1, bx + 1, by + 1, shade);
            g.fill(ax, ay, bx, by, ink);
        }
    }

    private Crosshair() {
    }
}
