package com.zomboidcraft.client.kit;

import com.zomboidcraft.client.map.MapStore;
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
 * The map page: your own map of where you've been (and what the maps you've read showed), dark wherever you haven't,
 * with pins you drop for safehouses, loot, danger and notes. Drag to pan, scroll to zoom; it starts on you.
 */
final class MapPage {
    private static final int VX = 37, VY = 33, VW = 376, VH = 287;
    private static final float MIN_ZOOM = 0.125f, MAX_ZOOM = 4f;

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

        // a faint grid every 256 blocks, so distance reads at any zoom
        for (int gx = Math.floorDiv(bx0, t) + 1; gx * t < bx1; gx++) {
            int sx = Math.round(ox + gx * t * zoom);
            g.fill(sx, vy, sx + 1, vy + VH, 0x14E8E4DA);
        }
        for (int gz = Math.floorDiv(bz0, t) + 1; gz * t < bz1; gz++) {
            int sy = Math.round(oy + gz * t * zoom);
            g.fill(vx, sy, vx + VW, sy + 1, 0x14E8E4DA);
        }

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
        float yaw = player.getYRot() * Mth.DEG_TO_RAD;
        float px = (float) (vx + VW / 2.0 + (player.getX() - cx) * zoom), py = (float) (vy + VH / 2.0 + (player.getZ() - cz) * zoom);
        if (px > vx + 3 && py > vy + 3 && px < vx + VW - 3 && py < vy + VH - 3) {
            float fx = -Mth.sin(yaw), fz = Mth.cos(yaw);
            for (int i = 0; i <= 6; i++) {
                int ax = Math.round(px + fx * i), az = Math.round(py + fz * i);
                int w = Math.max(0, 3 - i / 2);
                g.fill(ax - w, az - w, ax + w + 1, az + w + 1, i == 0 ? 0xFF1B1A17 : 0xFFE0C25E);
            }
            KitDraw.diamond(g, Math.round(px), Math.round(py), 3, 0xFFE0C25E);
        }

        // the edge, a scale bar, north and where the mouse is
        KitDraw.frame(g, vx, vy, VW, VH, 0x40E8E4DA);
        int blocks = zoom >= 2 ? 25 : zoom >= 1 ? 50 : zoom >= 0.5f ? 100 : zoom >= 0.25f ? 250 : 500;
        int bar = Math.round(blocks * zoom);
        g.fill(vx + 6, vy + VH - 16, vx + 6 + bar + 50, vy + VH - 5, 0xD9D8CFB8);
        g.fill(vx + 9, vy + VH - 11, vx + 9 + bar, vy + VH - 9, 0xFF2A2824);
        KitDraw.text(g, font, blocks + " blocks", vx + 13 + bar, vy + VH - 14, 0xFF2A2824, 0.8f);
        KitDraw.capsRight(g, font, "N ↑", vx + VW - 6, vy + 6, 0xE6E8E4DA, 0.85f);
        if (KitDraw.in(mx, my, vx, vy, VW, VH)) {
            String at = (int) Math.floor(worldX(mx)) + ", " + (int) Math.floor(worldZ(my));
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

        int my0 = y + 236;
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
}
