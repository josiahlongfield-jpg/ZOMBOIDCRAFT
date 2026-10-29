package com.zomboidcraft.client.kit;

import com.zomboidcraft.client.map.CountyAtlas;
import com.zomboidcraft.client.map.MapPlayers;
import com.zomboidcraft.client.map.MapStore;
import com.zomboidcraft.world.county.Plan;
import com.zomboidcraft.world.map.MapNet;
import com.zomboidcraft.world.trains.Line;
import com.zomboidcraft.world.trains.Trains;
import com.zomboidcraft.client.sound.UiSounds;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import org.joml.Matrix3x2fStack;

/**
 * The map page. In the county it's the whole county ({@link CountyAtlas}): every roof, street and wood from above,
 * the rail and subway lines with their stations, the names of the city's districts and the towns, and every survivor
 * where they stand. Elsewhere it's your own map of where you've been. Pins on top for safehouses, loot, danger and
 * notes. Drag to pan, scroll to zoom; it starts on you.
 */
final class MapPage {
    private static final int VX = 37, VY = 33, VW = 376, VH = 287;
    private static final float MIN_ZOOM = 0.02f, MAX_ZOOM = 4f;

    private final KitScreen<?> screen;
    private double cx, cz;
    private float zoom = 1f;
    private boolean follow = true;
    private MapStore.PinKind kind = MapStore.PinKind.NOTE;
    private boolean pressed;
    private double pressX, pressY, lastX, lastY;
    private boolean dragged;
    private int listScroll;

    MapPage(KitScreen<?> screen) {
        this.screen = screen;
    }

    /** Points the map at a spot and zoom (the UI test's views). */
    void look(double x, double z, float zoom) {
        cx = x;
        cz = z;
        this.zoom = Mth.clamp(zoom, MIN_ZOOM, MAX_ZOOM);
        follow = false;
    }

    // ------------------------------------------------------------------ the mouse

    private boolean inView(double mx, double my) {
        return KitDraw.in(mx, my, screen.left() + VX, screen.top() + VY, VW, VH);
    }

    boolean pressed(double mx, double my, int button) {
        if (!inView(mx, my)) return false;
        if (button == 1) {
            MapStore.Pin pin = pinAt(mx, my);
            if (pin != null) {
                MapStore.unpin(pin);
                UiSounds.play(UiSounds.CLICK);
            }
            return true;
        }
        pressed = true;
        dragged = false;
        pressX = lastX = mx;
        pressY = lastY = my;
        return true;
    }

    boolean dragged(double mx, double my) {
        if (!pressed) return false;
        if (Math.abs(mx - pressX) + Math.abs(my - pressY) > 3) dragged = true;
        if (dragged) {
            cx -= (mx - lastX) / zoom;
            cz -= (my - lastY) / zoom;
            follow = false;
        }
        lastX = mx;
        lastY = my;
        return true;
    }

    boolean released(double mx, double my) {
        if (!pressed) return false;
        pressed = false;
        if (!dragged && inView(mx, my) && pinAt(mx, my) == null) {
            MapStore.pin(kind, (int) Math.floor(worldX(mx)), (int) Math.floor(worldZ(my)));
            UiSounds.play(UiSounds.CLICK);
        }
        return true;
    }

    boolean scrolled(double mx, double my, double amount) {
        if (inView(mx, my)) {
            // zoom about the point under the mouse
            double wx = worldX(mx), wz = worldZ(my);
            zoom = Mth.clamp(zoom * (amount > 0 ? 1.4f : 1 / 1.4f), MIN_ZOOM, MAX_ZOOM);
            cx = wx - (mx - screen.left() - VX - VW / 2.0) / zoom;
            cz = wz - (my - screen.top() - VY - VH / 2.0) / zoom;
            follow = false;
            return true;
        }
        if (mx > screen.left() + VX + VW) {
            listScroll = Math.max(0, listScroll - (int) Math.signum(amount));
            return true;
        }
        return false;
    }

    private double worldX(double mx) {
        return cx + (mx - screen.left() - VX - VW / 2.0) / zoom;
    }

    private double worldZ(double my) {
        return cz + (my - screen.top() - VY - VH / 2.0) / zoom;
    }

    private MapStore.Pin pinAt(double mx, double my) {
        int vx = screen.left() + VX, vy = screen.top() + VY;
        for (MapStore.Pin p : MapStore.pins()) {
            double sx = vx + VW / 2.0 + (p.x + 0.5 - cx) * zoom, sy = vy + VH / 2.0 + (p.z + 0.5 - cz) * zoom;
            if (Math.abs(mx - sx) <= 5 && Math.abs(my - sy) <= 5) return p;
        }
        return null;
    }

    // ------------------------------------------------------------------ drawing

    void draw(GuiGraphicsExtractor g, int x, int y, int mx, int my) {
        Font font = screen.mc().font;
        LocalPlayer player = screen.player();
        if (follow) {
            cx = player.getX();
            cz = player.getZ();
        }
        int vx = x + VX, vy = y + VY;
        view(g, font, player, vx, vy, mx, my);
        side(g, font, player, x + 425, y + 33, mx, my);

        int hx = x + 37, hy = y + KitScreen.H - 14;
        hx += KitDraw.hint(g, font, "LMB", "Pin", hx, hy);
        hx += KitDraw.hint(g, font, "RMB", "Remove", hx, hy);
        hx += KitDraw.hint(g, font, "Drag", "Pan", hx, hy);
        hx += KitDraw.hint(g, font, "Wheel", "Zoom", hx, hy);
        screen.pagesAndClose(g, font, hx, hy);
    }

    private void view(GuiGraphicsExtractor g, Font font, LocalPlayer player, int vx, int vy, int mx, int my) {
        // the fog: everything you haven't seen or read about
        g.fill(vx, vy, vx + VW, vy + VH, 0xFF141518);
        double wx0 = cx - VW / 2.0 / zoom, wz0 = cz - VH / 2.0 / zoom;
        double wx1 = cx + VW / 2.0 / zoom, wz1 = cz + VH / 2.0 / zoom;
        float ox = (float) (vx - wx0 * zoom), oy = (float) (vy - wz0 * zoom);

        g.enableScissor(vx, vy, vx + VW, vy + VH);
        if (CountyAtlas.active()) {
            atlas(g, wx0, wz0, wx1, wz1, ox, oy);
        } else {
            explored(g, vx, vy, wx0, wz0, wx1, wz1, ox, oy);
        }
        int bx0 = (int) Math.ceil(wx0), bz0 = (int) Math.ceil(wz0), bx1 = (int) Math.floor(wx1), bz1 = (int) Math.floor(wz1);
        int t = zoom >= 0.25f ? MapStore.TILE : zoom >= 0.06f ? 1024 : 4096;

        // a faint grid (every 256 blocks close in, wider out), so distance reads at any zoom
        for (int gx = Math.floorDiv(bx0, t) + 1; gx * t < bx1; gx++) {
            int sx = Math.round(ox + gx * t * zoom);
            g.fill(sx, vy, sx + 1, vy + VH, 0x14E8E4DA);
        }
        for (int gz = Math.floorDiv(bz0, t) + 1; gz * t < bz1; gz++) {
            int sy = Math.round(oy + gz * t * zoom);
            g.fill(vx, sy, vx + VW, sy + 1, 0x14E8E4DA);
        }

        if (CountyAtlas.active()) {
            lines(g, font, vx, vy);
            labels(g, font, vx, vy);
        }
        others(g, font, player, vx, vy);
        g.disableScissor();

        // pins
        MapStore.Pin hovered = KitDraw.in(mx, my, vx, vy, VW, VH) ? pinAt(mx, my) : null;
        for (MapStore.Pin p : MapStore.pins()) {
            int sx = Math.round((float) (vx + VW / 2.0 + (p.x + 0.5 - cx) * zoom));
            int sy = Math.round((float) (vy + VH / 2.0 + (p.z + 0.5 - cz) * zoom));
            if (sx < vx + 4 || sy < vy + 4 || sx > vx + VW - 4 || sy > vy + VH - 4) continue;
            g.fill(sx - 4, sy - 3, sx + 5, sy + 4, 0x99000000);
            g.fill(sx - 4, sy - 4, sx + 4, sy + 4, p.kind.bg);
            KitDraw.capsCentred(g, font, p.kind.label.substring(0, 1), sx, sy - 2.5f, p.kind.fg, 0.7f);
            if (p == hovered) KitDraw.frame(g, sx - 6, sy - 6, 12, 12, 0xFFFFFFFF);
        }

        // you
        float px = (float) (vx + VW / 2.0 + (player.getX() - cx) * zoom), py = (float) (vy + VH / 2.0 + (player.getZ() - cz) * zoom);
        if (px > vx + 3 && py > vy + 3 && px < vx + VW - 3 && py < vy + VH - 3) {
            arrow(g, px, py, player.getYRot(), 0xFFE0C25E);
        }

        // the edge, a scale bar, north and where the mouse is
        KitDraw.frame(g, vx, vy, VW, VH, 0x40E8E4DA);
        int blocks = zoom >= 2 ? 25 : zoom >= 1 ? 50 : zoom >= 0.5f ? 100 : zoom >= 0.25f ? 250 : zoom >= 0.1f ? 500 : zoom >= 0.04f ? 1000 : 2500;
        int bar = Math.round(blocks * zoom);
        g.fill(vx + 6, vy + VH - 16, vx + 6 + bar + 50, vy + VH - 5, 0xD9D8CFB8);
        g.fill(vx + 9, vy + VH - 11, vx + 9 + bar, vy + VH - 9, 0xFF2A2824);
        KitDraw.text(g, font, blocks + " blocks", vx + 13 + bar, vy + VH - 14, 0xFF2A2824, 0.8f);
        KitDraw.capsRight(g, font, "N ↑", vx + VW - 6, vy + 6, 0xE6E8E4DA, 0.85f);
        if (KitDraw.in(mx, my, vx, vy, VW, VH)) {
            String at = (int) Math.floor(worldX(mx)) + ", " + (int) Math.floor(worldZ(my)) + near(worldX(mx), worldZ(my));
            KitDraw.text(g, font, hovered != null ? hovered.name : at, vx + 6, vy + 6, KitDraw.DIM, 0.8f);
        }
        if (!follow) {
            int bw = 54, bx = vx + VW - bw - 6, by = vy + VH - 18;
            boolean over = screen.hovered(bx, by, bw, 12, mx, my);
            g.fill(bx, by, bx + bw, by + 12, over ? 0xF2E8E4DA : 0xB30C0D0F);
            KitDraw.frame(g, bx, by, bw, 12, 0x99E8E4DA);
            KitDraw.capsCentred(g, font, "Back to me", bx + bw / 2f, by + 3, over ? 0xFF121314 : KitDraw.TEXT, 0.7f);
            screen.hot(bx, by, bw, 12, () -> follow = true);
        }
    }

    /** Outside the county: what you've seen and the maps you've read, dark everywhere else. */
    private void explored(GuiGraphicsExtractor g, int vx, int vy, double wx0, double wz0, double wx1, double wz1, float ox, float oy) {
        // the county layer from road atlases, under everything
        MapStore.Tile base = MapStore.base(false);
        if (base != null) {
            int s = MapStore.BASE_SCALE, half = MapStore.BASE_HALF;
            int b0 = Math.max(0, (int) Math.ceil((wx0 + half) / s)), b1 = Math.min(MapStore.BASE, (int) Math.floor((wx1 + half) / s));
            int c0 = Math.max(0, (int) Math.ceil((wz0 + half) / s)), c1 = Math.min(MapStore.BASE, (int) Math.floor((wz1 + half) / s));
            if (b1 > b0 && c1 > c0) {
                Matrix3x2fStack pose = g.pose();
                pose.pushMatrix();
                pose.translate((float) (vx - (half + wx0) * zoom), (float) (vy - (half + wz0) * zoom));
                pose.scale(s * zoom, s * zoom);
                blit(g, base.texture(), b0, c0, b1 - b0, c1 - c0, MapStore.BASE);
                pose.popMatrix();
            }
        }

        // what you've seen, and the street maps you've read
        int bx0 = (int) Math.ceil(wx0), bz0 = (int) Math.ceil(wz0), bx1 = (int) Math.floor(wx1), bz1 = (int) Math.floor(wz1);
        int t = MapStore.TILE;
        Matrix3x2fStack pose = g.pose();
        pose.pushMatrix();
        pose.translate(ox, oy);
        pose.scale(zoom, zoom);
        for (int tz = Math.floorDiv(bz0, t); tz <= Math.floorDiv(bz1 - 1, t); tz++) {
            for (int tx = Math.floorDiv(bx0, t); tx <= Math.floorDiv(bx1 - 1, t); tx++) {
                MapStore.Tile tile = MapStore.tile(tx, tz, false);
                if (tile == null) continue;
                int ix0 = Math.max(bx0, tx * t), ix1 = Math.min(bx1, tx * t + t);
                int iz0 = Math.max(bz0, tz * t), iz1 = Math.min(bz1, tz * t + t);
                if (ix1 <= ix0 || iz1 <= iz0) continue;
                g.blit(RenderPipelines.GUI_TEXTURED, tile.texture(), ix0, iz0, ix0 - tx * t, iz0 - tz * t, ix1 - ix0, iz1 - iz0, t, t);
            }
        }
        pose.popMatrix();
    }

    /** The county from the far-out sketch down to the level that suits the zoom, finer tiles over coarser. */
    private void atlas(GuiGraphicsExtractor g, double wx0, double wz0, double wx1, double wz1, float ox, float oy) {
        CountyAtlas.pump();
        // the finest level whose pixels aren't much smaller than a screen pixel
        int want = zoom >= 0.5f ? 0 : zoom >= 0.125f ? 1 : zoom >= 0.03f ? 2 : 3;
        for (int lv = CountyAtlas.LEVELS - 1; lv >= want; lv--) {
            int span = CountyAtlas.PX * CountyAtlas.SCALE[lv];
            int t0 = (int) Math.floor((wx0 + CountyAtlas.HALF) / span), t1 = (int) Math.floor((wx1 + CountyAtlas.HALF) / span);
            int u0 = (int) Math.floor((wz0 + CountyAtlas.HALF) / span), u1 = (int) Math.floor((wz1 + CountyAtlas.HALF) / span);
            for (int tz = u0; tz <= u1; tz++) {
                for (int tx = t0; tx <= t1; tx++) {
                    CountyAtlas.Tile tile = CountyAtlas.tile(lv, tx, tz, lv == want);
                    if (tile == null) continue;
                    int ex = CountyAtlas.edge(lv, tx), ez = CountyAtlas.edge(lv, tz);
                    Matrix3x2fStack pose = g.pose();
                    pose.pushMatrix();
                    pose.translate(ox + (float) (ex * zoom), oy + (float) (ez * zoom));
                    pose.scale(span * zoom / CountyAtlas.PX, span * zoom / CountyAtlas.PX);
                    blit(g, tile.texture(), 0, 0, CountyAtlas.PX, CountyAtlas.PX, CountyAtlas.PX);
                    pose.popMatrix();
                }
            }
        }
    }

    private float sx(int vx, double x) {
        return (float) (vx + VW / 2.0 + (x - cx) * zoom);
    }

    private float sy(int vy, double z) {
        return (float) (vy + VH / 2.0 + (z - cz) * zoom);
    }

    /** The rail and subway lines, and their stations. */
    private void lines(GuiGraphicsExtractor g, Font font, int vx, int vy) {
        for (Line line : Trains.lines(true).values()) {
            boolean subway = line.kind == Line.Kind.SUBWAY;
            int ink = subway ? 0xFF3D5A7D : 0xFF3A2A22, tie = subway ? 0xFFA9C4E0 : 0xFFE8E4DA;
            double len = line.route.length(), every = Math.max(2, 2.5 / zoom);
            float lx = sx(vx, line.route.x(0)), ly = sy(vy, line.route.z(0));
            int n = 0;
            for (double s = every; s <= len + every; s += every) {
                double at = Math.min(s, len);
                float x = sx(vx, line.route.x(at)), y = sy(vy, line.route.z(at));
                if (Math.max(Math.max(x, lx), Math.max(y, ly)) > -20 && Math.min(x, lx) < vx + VW + 20 && Math.min(y, ly) < vy + VH + 20
                        && Math.max(x, lx) > vx - 20 && Math.max(y, ly) > vy - 20) {
                    int w = zoom >= 0.5f ? 2 : 1;
                    segment(g, lx, ly, x, y, w, ink);
                    // sleepers on the railway, dashes on the subway (it runs under the streets)
                    if (zoom >= 0.06f && (subway ? n % 2 == 0 : n % 3 == 0)) segment(g, lx, ly, x, y, 0, tie);
                }
                lx = x;
                ly = y;
                n++;
            }
        }
        for (Line line : Trains.lines(true).values()) {
            boolean subway = line.kind == Line.Kind.SUBWAY;
            for (Line.Stop stop : line.stops) {
                int x = Math.round(sx(vx, stop.x())), y = Math.round(sy(vy, stop.z()));
                if (x < vx - 40 || y < vy - 10 || x > vx + VW + 40 || y > vy + VH + 10) continue;
                int r = zoom >= 0.25f ? 3 : 2;
                g.fill(x - r - 1, y - r - 1, x + r + 2, y + r + 2, subway ? 0xFF3D5A7D : 0xFF3A2A22);
                g.fill(x - r, y - r, x + r + 1, y + r + 1, 0xFFF2EFE8);
                if (zoom >= 0.05f) halo(g, font, stop.name(), x + r + 3, y - 3, subway ? 0xFFA9C4E0 : 0xFFF2EFE8, 0.62f, false);
            }
        }
    }

    /** A line from one screen point to another, a pixel (or two) wide; width 0 draws a short tick across it. */
    private static void segment(GuiGraphicsExtractor g, float ax, float ay, float bx, float by, int w, int colour) {
        float dx = bx - ax, dy = by - ay;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (w == 0) {
            if (len < 0.01f) return;
            float nx = -dy / len * 2, ny = dx / len * 2;
            int x = Math.round(ax), y = Math.round(ay);
            g.fill(Math.round(x - nx), Math.round(y - ny), Math.round(x - nx) + 1, Math.round(y - ny) + 1, colour);
            g.fill(Math.round(x + nx), Math.round(y + ny), Math.round(x + nx) + 1, Math.round(y + ny) + 1, colour);
            return;
        }
        int steps = Math.max(1, Math.round(len));
        for (int i = 0; i <= steps; i++) {
            int x = Math.round(ax + dx * i / steps), y = Math.round(ay + dy * i / steps);
            g.fill(x, y, x + w, y + w, colour);
        }
    }

    /** The plan's names: districts and towns from afar, landmarks and facilities closer in. */
    private void labels(GuiGraphicsExtractor g, Font font, int vx, int vy) {
        if (!CountyAtlas.planned()) return;
        for (Plan.Spot spot : Plan.get().spots) {
            if (spot.name() == null || spot.name().isBlank() || spot.kind().equals("station")) continue;
            float scale;
            int colour;
            switch (spot.kind()) {
                case "city", "district", "town" -> {
                    scale = zoom >= 0.06f ? 1.1f : 0.85f;
                    colour = 0xFFF2EFE8;
                }
                case "suburb", "hamlet" -> {
                    if (zoom < 0.04f) continue;
                    scale = 0.8f;
                    colour = 0xFFE8E4DA;
                }
                default -> {
                    if (zoom < 0.12f) continue;
                    scale = 0.68f;
                    colour = 0xFFE0C25E;
                }
            }
            int x = Math.round(sx(vx, spot.x())), y = Math.round(sy(vy, spot.z()));
            if (x < vx - 80 || y < vy - 10 || x > vx + VW + 80 || y > vy + VH + 10) continue;
            halo(g, font, spot.name(), x, y - 3, colour, scale, true);
        }
    }

    /** " · Downtown" for the nearest named place within a walk, or nothing. */
    private static String near(double x, double z) {
        if (!CountyAtlas.planned()) return "";
        Plan.Spot best = null;
        double bestD = 700;
        for (Plan.Spot spot : Plan.get().spots) {
            if (spot.name() == null || spot.name().isBlank()) continue;
            double d = Math.hypot(spot.x() - x, spot.z() - z);
            if (d < bestD) {
                bestD = d;
                best = spot;
            }
        }
        return best == null ? "" : " · " + best.name();
    }

    /** Letter-spaced caps with a dark edge, so a name reads over roofs and water alike. */
    private static void halo(GuiGraphicsExtractor g, Font font, String text, float x, float y, int colour, float scale, boolean centred) {
        float left = centred ? x - KitDraw.capsWidth(font, text, scale) / 2f : x;
        for (int[] d : new int[][]{{-1, 0}, {1, 0}, {0, -1}, {0, 1}}) {
            KitDraw.caps(g, font, text, left + d[0] * 0.75f, y + d[1] * 0.75f, 0xE60C0D0F, scale);
        }
        KitDraw.caps(g, font, text, left, y, colour, scale);
    }

    /** Everyone else, where they stand and which way they face, with their name. */
    private void others(GuiGraphicsExtractor g, Font font, LocalPlayer me, int vx, int vy) {
        String dim = me.level().dimension().identifier().toString();
        for (MapNet.Spot p : MapPlayers.spots()) {
            if (p.id().equals(me.getUUID()) || !p.dim().equals(dim)) continue;
            float x = sx(vx, p.x()), y = sy(vy, p.z());
            int colour = MapPlayers.colour(p.id());
            boolean inside = x > vx + 3 && y > vy + 3 && x < vx + VW - 3 && y < vy + VH - 3;
            if (!inside) {
                // off the edge: a mark on the edge, pointing the way
                x = Mth.clamp(x, vx + 4, vx + VW - 5);
                y = Mth.clamp(y, vy + 4, vy + VH - 5);
                KitDraw.diamond(g, Math.round(x), Math.round(y), 3, colour);
                continue;
            }
            arrow(g, x, y, p.yaw(), colour);
            halo(g, font, p.name(), x, y + 6, colour, 0.62f, true);
        }
    }

    private static void arrow(GuiGraphicsExtractor g, float px, float py, float yawDeg, int colour) {
        float yaw = yawDeg * Mth.DEG_TO_RAD;
        float fx = -Mth.sin(yaw), fz = Mth.cos(yaw);
        for (int i = 0; i <= 6; i++) {
            int ax = Math.round(px + fx * i), az = Math.round(py + fz * i);
            int w = Math.max(0, 3 - i / 2);
            g.fill(ax - w, az - w, ax + w + 1, az + w + 1, i == 0 ? 0xFF1B1A17 : colour);
        }
        KitDraw.diamond(g, Math.round(px), Math.round(py), 3, colour);
    }

    private static void blit(GuiGraphicsExtractor g, Identifier id, int u, int v, int w, int h, int size) {
        g.blit(RenderPipelines.GUI_TEXTURED, id, u, v, u, v, w, h, size, size);
    }

    /** Pin kinds, your pins and the maps you've read. */
    private void side(GuiGraphicsExtractor g, Font font, LocalPlayer player, int x, int y, int mx, int my) {
        int w = 154;
        KitDraw.heading(g, font, "Pins", x, y, w, true);
        MapStore.PinKind[] kinds = MapStore.PinKind.values();
        for (int i = 0; i < kinds.length; i++) {
            MapStore.PinKind k = kinds[i];
            int bx = x + (i % 2) * 78, by = y + 9 + (i / 2) * 15, bw = 76, bh = 13;
            boolean on = k == kind, over = screen.hovered(bx, by, bw, bh, mx, my);
            g.fill(bx, by, bx + bw, by + bh, on ? 0xEBE8E4DA : over ? 0x26E8E4DA : 0x00000000);
            KitDraw.frame(g, bx, by, bw, bh, on ? 0xFFE8E4DA : 0x4DE8E4DA);
            g.fill(bx + 4, by + 3, bx + 11, by + 10, k.bg);
            KitDraw.caps(g, font, k.label, bx + 15, by + 3, on ? 0xFF121314 : KitDraw.TEXT, 0.72f);
            screen.hot(bx, by, bw, bh, () -> kind = k);
        }
        int cy = y + 42;
        cy += KitDraw.wrap(g, font, "Pick a kind, then click the map to drop a pin. Pins stay on your map in this world.",
                x, cy, w, KitDraw.FAINT) + 4;

        List<MapStore.Pin> pins = MapStore.pins();
        int rows = 7, top = cy;
        listScroll = Math.min(listScroll, Math.max(0, pins.size() - rows));
        if (pins.isEmpty()) KitDraw.text(g, font, "No pins yet.", x, cy, KitDraw.DIM, 0.85f);
        for (int i = listScroll; i < Math.min(pins.size(), listScroll + rows); i++) {
            MapStore.Pin p = pins.get(i);
            int ry = top + (i - listScroll) * 17;
            boolean over = screen.hovered(x, ry, w, 16, mx, my);
            if (over) g.fill(x, ry, x + w, ry + 16, 0x1AE8E4DA);
            g.fill(x + 3, ry + 4, x + 11, ry + 12, p.kind.bg);
            KitDraw.caps(g, font, p.name, x + 15, ry + 2, KitDraw.BRIGHT, 0.75f);
            int d = (int) Math.round(Math.hypot(p.x - player.getX(), p.z - player.getZ()));
            KitDraw.text(g, font, d + " blocks · " + p.x + ", " + p.z, x + 15, ry + 9, KitDraw.DIM, 0.7f);
            screen.hot(x, ry, w, 16, net.minecraft.world.item.ItemStack.EMPTY, () -> {
                cx = p.x + 0.5;
                cz = p.z + 0.5;
                follow = false;
            }, () -> MapStore.unpin(p), null, -1);
        }
        if (pins.size() > rows) {
            KitDraw.textRight(g, font, (listScroll + 1) + "-" + Math.min(pins.size(), listScroll + rows) + " of " + pins.size(),
                    x + w, top + rows * 17, KitDraw.FAINT, 0.75f);
        }

        int my0 = y + 220;
        if (CountyAtlas.active()) {
            survivors(g, font, player, x, my0, w, mx, my);
            return;
        }
        KitDraw.heading(g, font, "Maps read", x, my0, w, true);
        List<String> read = MapStore.read();
        if (read.isEmpty()) {
            KitDraw.wrap(g, font, "Loot a street map or road atlas and read it: its area fills in.", x, my0 + 10, w, KitDraw.FAINT);
        }
        for (int i = 0; i < Math.min(4, read.size()); i++) {
            String title = read.get(read.size() - 1 - i);
            KitDraw.text(g, font, title, x, my0 + 10 + i * 10, KitDraw.TEXT, 0.8f);
            KitDraw.textRight(g, font, "Read", x + w, my0 + 10 + i * 10, 0xFF9CC46A, 0.8f);
        }
    }

    /** Everyone in the world: how far off they are, and a click takes the map to them. */
    private void survivors(GuiGraphicsExtractor g, Font font, LocalPlayer me, int x, int y, int w, int mx, int my) {
        KitDraw.heading(g, font, "Survivors", x, y, w, true);
        String dim = me.level().dimension().identifier().toString();
        int row = 0;
        for (MapNet.Spot p : MapPlayers.spots()) {
            if (row >= 5) break;
            int ry = y + 10 + row++ * 13;
            boolean self = p.id().equals(me.getUUID()), here = p.dim().equals(dim);
            boolean over = here && screen.hovered(x, ry, w, 12, mx, my);
            if (over) g.fill(x, ry, x + w, ry + 12, 0x1AE8E4DA);
            KitDraw.diamond(g, x + 4, ry + 5, 3, self ? 0xFFE0C25E : MapPlayers.colour(p.id()));
            KitDraw.text(g, font, self ? p.name() + " (you)" : p.name(), x + 11, ry + 2, KitDraw.BRIGHT, 0.8f);
            String far = !here ? "Elsewhere" : self ? "" : Math.round(Math.hypot(p.x() - me.getX(), p.z() - me.getZ())) + " blocks";
            KitDraw.textRight(g, font, far, x + w, ry + 2, KitDraw.DIM, 0.75f);
            if (here) {
                screen.hot(x, ry, w, 12, () -> {
                    cx = p.x();
                    cz = p.z();
                    follow = self;
                });
            }
        }
        if (row == 0) KitDraw.text(g, font, "Only you.", x, y + 12, KitDraw.FAINT, 0.85f);
        if (CountyAtlas.sketching()) {
            KitDraw.text(g, font, "Drawing the county...", x, y + 82, KitDraw.FAINT, 0.75f);
        }
    }
}
