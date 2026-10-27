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
import org.joml.Vector3f;
import org.joml.Vector3fc;

/**
 * What the pilot sees over the view. In a jet's cockpit the head-up display is on the combiner glass
 * itself ({@link CockpitDisplays}), so this only adds the mouse-aim circle and what to press; in the
 * helicopter it's the helmet sight (after the AH-64's IHADSS): head-locked green symbols over the
 * right eye's view, heading, torque, speed, height and radar height, the horizon, the drift vector.
 * From outside (the chase view) a plain readout: speed, height, heading, engine and load.
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
            if (pilot && f.kind == Flight.Kind.JET) {
                Vector3f a = FlightClient.aim();
                float[] p = project(cam, new Vec3(a.x, a.y, a.z), scale, cx, cy);
                if (p != null) {
                    ring(g, p[0], p[1], 7, 0xC0FFFFFF);
                }
            }
            if (f.kind == Flight.Kind.HELI) {
                helmet(g, font, cam, car, f, scale, w, h);
            }
            hints(g, font, player, car, f, pilot, cx, h - 40);
            return;
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
        hints(g, font, player, car, f, pilot, cx, cy + 40);
    }

    /** What to press, and the warnings that matter most, under the middle. */
    private static void hints(GuiGraphicsExtractor g, Font font, LocalPlayer player, CarProp car, Flight f, boolean pilot, int cx, int y) {
        boolean flash = (player.tickCount / 5) % 2 == 0;
        if (!car.engineOn() && pilot && f.rpm < 0.05f) {
            warn(g, font, cx, y, "ENGINE OFF - PRESS " + CarClient.ENGINE.getTranslatedKeyMessage().getString(), DIM);
        } else if (f.stalled && flash) {
            warn(g, font, cx, y, "STALL", RED);
        } else if (!f.grounded && f.vel.y < -12 && car.getY() - groundBelow(car) < 60 && flash) {
            warn(g, font, cx, y, "PULL UP", RED);
        } else if (car.engineOn() && f.rpm < (f.kind == Flight.Kind.JET ? 0.58f : 0.95f)) {
            warn(g, font, cx, y, f.kind == Flight.Kind.JET ? "SPOOLING UP" : "ROTOR SPIN-UP", DIM);
        }
    }

    /**
     * The helmet sight: fixed to your head, so it stays put while you look round. Heading tape at the
     * top (the nose's heading, boxed), torque top left, airspeed and barometric height either side of the
     * middle, radar height with its bar on the right, the line of sight cross, the horizon, and the
     * velocity vector growing from the middle the way you're drifting.
     */
    private static void helmet(GuiGraphicsExtractor g, Font font, Camera cam, CarProp car, Flight f, float scale, int w, int h) {
        int cx = w / 2, cy = h / 2;
        int col = 0xE050FF6E;
        int left = cx - h / 2, right = cx + h / 2;
        // heading tape
        float heading = Mth.positiveModulo(f.heading + 180, 360);
        for (int d = -60; d <= 60; d += 10) {
            float mark = Mth.positiveModulo((float) (Math.floor(heading / 10) * 10 + d), 360);
            float off = Mth.wrapDegrees(mark - heading);
            if (Math.abs(off) > 60) {
                continue;
            }
            int x = cx + Math.round(off * h / 200f);
            g.fill(x, 26, x + 1, mark % 30 == 0 ? 33 : 30, col);
            if (mark % 30 == 0 && Math.abs(off) > 8) {
                String s = mark == 0 ? "N" : mark == 90 ? "E" : mark == 180 ? "S" : mark == 270 ? "W" : Integer.toString(Math.round(mark / 10));
                g.text(font, s, x - font.width(s) / 2, 35, col, false);
            }
        }
        String hs = String.format("%03d", Math.round(heading) % 360);
        outline(g, cx - 13, 12, cx + 13, 24, col);
        g.text(font, hs, cx - font.width(hs) / 2, 14, col, false);
        // torque, speed, height, radar height
        g.text(font, Math.round(f.power * 100) + "%", left, 30, col, false);
        int kts = Math.round((float) Math.hypot(f.vel.x, f.vel.z) * 1.944f);
        g.text(font, Integer.toString(kts), left, cy - 4, col, false);
        int ft = (int) Math.round((car.getY() - 63) * 3.28084);
        String alt = Integer.toString(Math.round(ft / 10f) * 10);
        g.text(font, alt, right - font.width(alt), 30, col, false);
        double radar = Math.max(0, car.getY() - groundBelow(car));
        String rs = Long.toString(Math.round(radar * 3.28084));
        g.text(font, rs, right - font.width(rs) - 12, cy - 4, col, false);
        int barTop = cy - 50, barBottom = cy + 50;
        outline(g, right - 6, barTop, right - 1, barBottom, col);
        int fill = (int) Math.round(Math.min(1, radar / 60) * (barBottom - barTop));
        g.fill(right - 5, barBottom - fill, right - 2, barBottom, col);
        String vs = String.format("%+d", Math.round(f.vel.y * 196.85 / 100) * 100);
        g.text(font, vs, right - font.width(vs) - 12, cy + 10, col, false);
        // line of sight cross
        g.fill(cx - 8, cy, cx - 3, cy + 1, col);
        g.fill(cx + 4, cy, cx + 9, cy + 1, col);
        g.fill(cx, cy - 8, cx + 1, cy - 3, col);
        g.fill(cx, cy + 4, cx + 1, cy + 9, col);
        // the horizon, true to the world, broken in the middle
        Vec3 fw = f.forward();
        Vec3 flat = new Vec3(fw.x, 0, fw.z);
        if (flat.lengthSqr() > 1e-4) {
            flat = flat.normalize();
            Vec3 side = new Vec3(-flat.z, 0, flat.x);
            for (int k = -20; k <= 20; k++) {
                if (Math.abs(k) < 3) {
                    continue;
                }
                float[] p = project(cam, flat.add(side.scale(Math.tan(Math.toRadians(k * 1.5)))), scale, cx, cy);
                if (p != null && Math.abs(p[1] - cy) < h * 0.4f) {
                    g.fill(Math.round(p[0]) - 3, Math.round(p[1]), Math.round(p[0]) + 3, Math.round(p[1]) + 1, col);
                }
            }
        }
        // the velocity vector: a line from the middle, a sixth of the screen at 10 knots of drift
        Vec3 local = new Vec3(f.vel.x, 0, f.vel.z);
        double r = Math.toRadians(f.heading);
        double ahead = -local.x * Math.sin(r) + local.z * Math.cos(r), sideways = local.x * Math.cos(r) + local.z * Math.sin(r);
        float k = h / 6f / (10 / 1.944f);
        float vx = (float) Mth.clamp(-sideways * k, -h / 3f, h / 3f), vy = (float) Mth.clamp(-ahead * k, -h / 3f, h / 3f);
        int steps = (int) Math.max(Math.abs(vx), Math.abs(vy));
        for (int i = 0; i < steps; i += 2) {
            g.fill(Math.round(cx + vx * i / steps), Math.round(cy + vy * i / steps), Math.round(cx + vx * i / steps) + 1,
                    Math.round(cy + vy * i / steps) + 1, col);
        }
        if (steps > 4) {
            ring(g, cx + vx, cy + vy, 4, col);
        }
        // the trim ball, and what's armed
        int bx = cx, by = h - 70;
        outline(g, bx - 20, by, bx + 20, by + 7, col);
        float slip = (float) Mth.clamp(sideways / 6, -1, 1);
        g.fill(bx + Math.round(slip * 16) - 2, by + 1, bx + Math.round(slip * 16) + 3, by + 6, col);
        g.text(font, "SAFE", left, h - 70, col, false);
        if (car.flight().levelLock) {
            String lock = Math.hypot(local.x, local.z) < 1.5 ? "HOVER HOLD" : "ALT HOLD";
            g.text(font, lock, cx - font.width(lock) / 2, h - 82, col, false);
        }
        g.text(font, "FUEL " + Math.round(car.fuel() * 100) + "%", right - font.width("FUEL 100%"), h - 70, col, false);
    }

    private static void outline(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, int col) {
        g.fill(x0, y0, x1, y0 + 1, col);
        g.fill(x0, y1 - 1, x1, y1, col);
        g.fill(x0, y0, x0 + 1, y1, col);
        g.fill(x1 - 1, y0, x1, y1, col);
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

    /** Where a direction lands on the screen, or null if it's behind the camera. */
    static float[] project(Camera cam, Vec3 d, float scale, int cx, int cy) {
        Vector3fc fw = cam.forwardVector(), up = cam.upVector(), left = cam.leftVector();
        double z = d.x * fw.x() + d.y * fw.y() + d.z * fw.z();
        if (z < 0.05) {
            return null;
        }
        double x = d.x * left.x() + d.y * left.y() + d.z * left.z();
        double y = d.x * up.x() + d.y * up.y() + d.z * up.z();
        return new float[]{(float) (cx - x / z * scale), (float) (cy - y / z * scale)};
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
