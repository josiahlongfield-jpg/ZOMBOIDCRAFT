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
            if (player != null && com.zomboidcraft.world.cars.Turret.isGunner(player)) {
                spreadReticle(graphics, player);     // at a mounted gun: its own ring, whatever's in hand
                return;
            }
            if (com.zomboidcraft.client.camera.ShoulderCam.active()) {
                spreadReticle(graphics, player);
                return;
            }
            if (look.spreadCrosshair && player != null && GunType.isGun(player.getMainHandItem())
                    && !(player.isUsingItem() && GunType.isGun(player.getUseItem()))) {
                spreadReticle(graphics, player);     // from the hip: the ring is where the shots go
                return;
            }
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

    /** The spread ring's radius as drawn (screen pixels), eased toward the gun's spread. */
    private static float ring = -1;
    private static long lastFrame;

    /**
     * A small dot in a thin ring that shows where the shots can go (over the shoulder, where Minecraft draws no
     * crosshair, and from the hip in first person): the ring's size is the gun's spread right now (wider from the hip and on the move), and it closes
     * in to just round the dot as you aim. Drawn in screen pixels so the ring stays a clean thin line, light with a
     * dark edge so it reads on snow and sky alike.
     */
    private static void spreadReticle(net.minecraft.client.gui.GuiGraphicsExtractor g, LocalPlayer player) {
        Minecraft mc = Minecraft.getInstance();
        float gui = (float) mc.getWindow().getGuiScale();
        int dot = Math.max(1, Math.round(gui * 0.75f));
        float target = dot + 3 + Math.round(gui);       // just round the dot
        net.minecraft.world.item.ItemStack held = player == null ? net.minecraft.world.item.ItemStack.EMPTY : player.getMainHandItem();
        GunType type = GunType.of(held);
        boolean aiming = type != null && player.isUsingItem();
        com.zomboidcraft.world.cars.CarModels.Model mounted = player != null && player.getVehicle() instanceof com.zomboidcraft.world.cars.CarProp car
                && com.zomboidcraft.world.cars.Turret.isGunner(player) ? com.zomboidcraft.world.cars.CarModels.get(car.model()) : null;
        if (mounted != null && mounted.turret != null) {
            // the mounted gun's cone, as the server shoots it: tighter zoomed in, where the view narrows too
            boolean zoomed = mc.options.keyUse.isDown() && mc.gui.screen() == null;
            double spread = com.zomboidcraft.world.cars.Turret.spread(zoomed, ZcConfig.get().minigun);
            double fov = mc.options.fov().get() * (zoomed ? mounted.turret.zoom : 1.0);
            float px = (float) (Math.tan(Math.toRadians(spread)) / Math.tan(Math.toRadians(fov / 2))
                    * mc.getWindow().getHeight() / 2);
            target = Math.max(target, px);
        } else if (type != null) {
            ZcConfig.GunStats stats = type.stats();
            // the same cone the server shoots inside (Guns.fire), bloom and all, and buckshot's fan round it
            double spread = com.zomboidcraft.combat.Guns.spread(player, held, type, aiming, CombatClient.bloom(type))
                    + com.zomboidcraft.combat.Guns.pelletSpread(type);
            double fov = mc.options.fov().get() * (aiming ? stats.aimZoom : 1.0);
            float px = (float) (Math.tan(Math.toRadians(spread)) / Math.tan(Math.toRadians(fov / 2))
                    * mc.getWindow().getHeight() / 2);
            target = Math.max(target, px);
        }
        long now = System.nanoTime();
        float dt = lastFrame == 0 ? 1f : Math.min(0.1f, (now - lastFrame) / 1e9f);
        lastFrame = now;
        ring = ring < 0 ? target : ring + (target - ring) * Math.min(1f, dt * 30f);

        Matrix3x2fStack pose = g.pose();
        pose.pushMatrix();
        pose.scale(1 / gui, 1 / gui);     // whole screen pixels from here
        int cx = mc.getWindow().getWidth() / 2, cy = mc.getWindow().getHeight() / 2;
        int shade = 0x8C000000, ink = 0xE6F2F2F2;
        int half = dot / 2;
        g.fill(cx - half - 1, cy - half - 1, cx - half + dot + 1, cy - half + dot + 1, shade);
        g.fill(cx - half, cy - half, cx - half + dot, cy - half + dot, ink);
        {
            int line = Math.max(1, Math.round(gui / 2));
            circle(g, cx, cy, ring + line, line + 2, shade);
            circle(g, cx, cy, ring + line, line, ink);
        }
        pose.popMatrix();
    }

    /** A ring of the given radius and thickness (screen pixels), drawn as runs of pixels row by row. */
    private static void circle(net.minecraft.client.gui.GuiGraphicsExtractor g, int cx, int cy, float r, int width, int colour) {
        float outer = r + width / 2f, inner = Math.max(0, r - width / 2f);
        int top = (int) Math.ceil(outer);
        for (int dy = -top; dy <= top; dy++) {
            double y = dy + 0.5;
            double o2 = outer * outer - y * y;
            if (o2 < 0) {
                continue;
            }
            int xo = (int) Math.round(Math.sqrt(o2));
            double i2 = inner * inner - y * y;
            int xi = i2 > 0 ? (int) Math.round(Math.sqrt(i2)) : 0;
            if (xi >= xo) {
                xi = xo - 1;
            }
            if (i2 <= 0) {
                g.fill(cx - xo, cy + dy, cx + xo, cy + dy + 1, colour);
            } else {
                g.fill(cx - xo, cy + dy, cx - xi, cy + dy + 1, colour);
                g.fill(cx + xi, cy + dy, cx + xo, cy + dy + 1, colour);
            }
        }
    }

    private Crosshair() {
    }
}
