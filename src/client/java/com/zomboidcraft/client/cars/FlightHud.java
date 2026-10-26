package com.zomboidcraft.client.cars;

import com.zomboidcraft.ZomboidCraft;
import com.zomboidcraft.world.cars.CarProp;
import com.zomboidcraft.world.cars.Flight;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3x2fStack;
import org.joml.Vector3f;
import org.joml.Vector3fc;

/**
 * The pilot's head-up display, first pass: a pitch ladder and horizon that bank with the aircraft,
 * the nose and flight path markers, the jet's aim circle, a heading tape, airspeed and altitude, and
 * the engine, load and warnings underneath. Everything on it is live; the per-aircraft symbology
 * (F-22, Su-27, Apache IHADSS) replaces this once the reference sheets are in.
 */
final class FlightHud {
    private static final int GREEN = 0xE060FF80, DIM = 0x9060FF80, RED = 0xF0FF4030;

    static void register() {
        HudElementRegistry.attachElementAfter(VanillaHudElements.CROSSHAIR, ZomboidCraft.id("flight_hud"), FlightHud::draw);
    }

    private static void draw(GuiGraphicsExtractor g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        CarProp car = FlightClient.aircraft(player);
        if (car == null) {
            return;
        }
        Flight f = car.flight();
        Font font = mc.font;
        int w = g.guiWidth(), h = g.guiHeight(), cx = w / 2, cy = h / 2;
        boolean pilot = FlightClient.pilot(player, car);
        Camera cam = mc.gameRenderer.mainCamera();
        boolean firstPerson = !cam.isDetached();
        float scale = (float) (h / 2 / Math.tan(Math.toRadians(cam.getFov()) / 2));

        if (firstPerson) {
            ladder(g, cam, f, scale, cx, cy);
            Vec3 nose = f.forward();
            float[] n = project(cam, nose, scale, cx, cy);
            if (n != null) {
                cross(g, n[0], n[1], 5, GREEN);
            }
            if (f.speed() > 3) {
                float[] fpm = project(cam, f.vel.normalize(), scale, cx, cy);
                if (fpm != null) {
                    ring(g, fpm[0], fpm[1], 4, GREEN);
                    g.fill(Math.round(fpm[0]) - 10, Math.round(fpm[1]), Math.round(fpm[0]) - 4, Math.round(fpm[1]) + 1, GREEN);
                    g.fill(Math.round(fpm[0]) + 5, Math.round(fpm[1]), Math.round(fpm[0]) + 11, Math.round(fpm[1]) + 1, GREEN);
                    g.fill(Math.round(fpm[0]), Math.round(fpm[1]) - 8, Math.round(fpm[0]) + 1, Math.round(fpm[1]) - 4, GREEN);
                }
            }
        }
        if (pilot && f.kind == Flight.Kind.JET) {
            Vector3f a = FlightClient.aim();
            float[] p = project(cam, new Vec3(a.x, a.y, a.z), scale, cx, cy);
            if (p != null) {
                ring(g, p[0], p[1], 7, 0xE0FFFFFF);
            }
        }

        // heading tape
        float heading = Mth.positiveModulo(car.getYRot() + 180, 360);
        for (int d = -30; d <= 30; d += 5) {
            float mark = Mth.positiveModulo((float) (Math.floor(heading / 5) * 5 + d), 360);
            float off = Mth.wrapDegrees(mark - heading);
            int x = cx + Math.round(off * 3);
            if (Math.abs(off) > 30) {
                continue;
            }
            g.fill(x, 14, x + 1, mark % 10 == 0 ? 20 : 17, DIM);
            if (mark % 10 == 0) {
                String s = String.format("%02d", Math.round(mark / 10) % 36);
                g.text(font, s, x - font.width(s) / 2, 22, DIM, false);
            }
        }
        String hs = String.format("%03d", Math.round(heading) % 360);
        g.fill(cx - 12, 3, cx + 12, 13, 0x80000000);
        g.text(font, hs, cx - font.width(hs) / 2, 4, GREEN, false);

        // speed and altitude boxes
        int kmh = Math.round(f.speed() * 3.6f);
        int alt = (int) Math.round(car.getY() - 63);
        box(g, font, cx - 120, cy - 6, kmh + "", "KM/H");
        box(g, font, cx + 90, cy - 6, alt + "", "M");
        String vs = String.format("%+.1f", f.vel.y);
        g.text(font, vs, cx + 90, cy + 10, DIM, false);

        // engine and load
        int y = h - 70;
        if (f.kind == Flight.Kind.JET) {
            line(g, font, 10, y, "THR " + Math.round(f.throttle * 100) + "%" + (f.afterburner() ? "  AB" : ""));
            line(g, font, 10, y + 10, "RPM " + Math.round(f.rpm * 100) + "%");
            line(g, font, 10, y + 20, String.format("G %.1f", f.load));
            line(g, font, 10, y + 30, String.format("AOA %.0f", Math.toDegrees(f.aoa)));
        } else {
            line(g, font, 10, y, "TQ " + Math.round(f.power * 100) + "%");
            line(g, font, 10, y + 10, "NR " + Math.round(f.rpm * 100) + "%");
            line(g, font, 10, y + 20, String.format("G %.1f", f.load));
        }
        line(g, font, 10, y + 40, "FUEL " + Math.round(car.fuel() * 100) + "%");
        boolean flash = (player.tickCount / 5) % 2 == 0;
        if (!car.engineOn() && pilot && f.rpm < 0.05f) {
            warn(g, font, cx, cy + 40, "ENGINE OFF - PRESS " + FlightClient.ENGINE.getTranslatedKeyMessage().getString(), DIM);
        } else if (f.stalled && flash) {
            warn(g, font, cx, cy + 40, "STALL", RED);
        } else if (!f.grounded && f.vel.y < -12 && car.getY() - groundBelow(car) < 60 && flash) {
            warn(g, font, cx, cy + 40, "PULL UP", RED);
        } else if (car.engineOn() && f.rpm < (f.kind == Flight.Kind.JET ? 0.58f : 0.95f)) {
            warn(g, font, cx, cy + 40, f.kind == Flight.Kind.JET ? "SPOOLING UP" : "ROTOR SPIN-UP", DIM);
        }
    }

    private static double groundBelow(CarProp car) {
        var level = car.level();
        var pos = car.blockPosition().mutable();
        for (int i = 0; i < 64; i++, pos.move(net.minecraft.core.Direction.DOWN)) {
            if (!level.getBlockState(pos).isAir()) {
                return pos.getY() + 1;
            }
        }
        return car.getY() - 64;
    }

    /** The pitch ladder: a rung every 10 degrees above and below the horizon, banked with the camera. */
    private static void ladder(GuiGraphicsExtractor g, Camera cam, Flight f, float scale, int cx, int cy) {
        Vec3 fw = f.forward();
        Vec3 flat = new Vec3(fw.x, 0, fw.z);
        if (flat.lengthSqr() < 1e-4) {
            return;
        }
        flat = flat.normalize();
        Vector3fc up = cam.upVector();
        float bank = (float) Math.atan2(-cam.leftVector().y(), up.y());
        for (int p = -60; p <= 60; p += 10) {
            double r = Math.toRadians(p);
            Vec3 d = flat.scale(Math.cos(r)).add(0, Math.sin(r), 0);
            float[] at = project(cam, d, scale, cx, cy);
            if (at == null || Math.abs(at[1] - cy) > cy * 0.8f || Math.abs(at[0] - cx) > cx * 0.8f) {
                continue;
            }
            Matrix3x2fStack pose = g.pose();
            pose.pushMatrix();
            pose.translate(at[0], at[1]);
            pose.rotate(bank);
            int half = p == 0 ? 60 : 26;
            int col = p == 0 ? GREEN : DIM;
            if (p < 0) {
                for (int x = -half; x < -6; x += 6) {
                    g.fill(x, 0, x + 3, 1, col);
                }
                for (int x = 6; x < half; x += 6) {
                    g.fill(x, 0, x + 3, 1, col);
                }
            } else {
                g.fill(-half, 0, -6, 1, col);
                g.fill(6, 0, half, 1, col);
            }
            if (p != 0) {
                String s = Integer.toString(Math.abs(p));
                g.text(Minecraft.getInstance().font, s, half + 3, -4, col, false);
            }
            pose.popMatrix();
        }
    }

    /** Where a direction lands on the screen, or null if it's behind the camera. */
    private static float[] project(Camera cam, Vec3 d, float scale, int cx, int cy) {
        Vector3fc fw = cam.forwardVector(), up = cam.upVector(), left = cam.leftVector();
        double z = d.x * fw.x() + d.y * fw.y() + d.z * fw.z();
        if (z < 0.05) {
            return null;
        }
        double x = d.x * left.x() + d.y * left.y() + d.z * left.z();
        double y = d.x * up.x() + d.y * up.y() + d.z * up.z();
        return new float[]{(float) (cx - x / z * scale), (float) (cy - y / z * scale)};
    }

    private static void cross(GuiGraphicsExtractor g, float x, float y, int r, int col) {
        int ix = Math.round(x), iy = Math.round(y);
        g.fill(ix - r, iy, ix - 1, iy + 1, col);
        g.fill(ix + 2, iy, ix + r + 1, iy + 1, col);
        g.fill(ix, iy - r, ix + 1, iy - 1, col);
    }

    private static void ring(GuiGraphicsExtractor g, float x, float y, int r, int col) {
        for (int k = 0; k < 24; k++) {
            double a = Math.PI * 2 * k / 24;
            int px = Math.round(x + (float) Math.cos(a) * r), py = Math.round(y + (float) Math.sin(a) * r);
            g.fill(px, py, px + 1, py + 1, col);
        }
    }

    private static void box(GuiGraphicsExtractor g, Font font, int x, int y, String value, String unit) {
        g.fill(x - 2, y - 2, x + 34, y + 10, 0x80000000);
        g.text(font, value, x + 32 - font.width(value), y, GREEN, false);
        g.text(font, unit, x, y + 12, DIM, false);
    }

    private static void line(GuiGraphicsExtractor g, Font font, int x, int y, String s) {
        g.text(font, s, x, y, GREEN, true);
    }

    private static void warn(GuiGraphicsExtractor g, Font font, int cx, int y, String s, int col) {
        g.text(font, s, cx - font.width(s) / 2, y, col, true);
    }

    private FlightHud() {
    }
}
