package com.zomboidcraft.client.kit;

import com.zomboidcraft.building.base.Claim;
import com.zomboidcraft.building.base.Claims;
import com.zomboidcraft.building.base.RoomType;
import com.zomboidcraft.building.base.Rooms;
import com.zomboidcraft.client.building.BaseClient;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;

/**
 * The base page (rooms plan, "You can see it"): your claim on the left, who's in the squad and how big it is, and on
 * the right every room in it with its grade, what counts in it, and what the next grade needs.
 */
final class BasePage {
    private final KitScreen<?> screen;
    private int scroll, listHeight;
    private static final int ROW = 38;

    BasePage(KitScreen<?> screen) {
        this.screen = screen;
    }

    boolean scrolled(double mx, double my, double sy) {
        scroll = Math.max(0, scroll - (int) Math.signum(sy) * (ROW + 2));
        return true;
    }

    void draw(GuiGraphicsExtractor g, int x, int y, int mx, int my) {
        Font font = screen.mc().font;
        LocalPlayer player = screen.player();
        BaseClient.survey();
        List<Claim> mine = Claims.of(player.level(), player.getUUID());
        Claim base = null;
        for (Claim c : mine) {
            if (c.kind().equals(Claim.BASE)) {
                base = c;
                break;
            }
        }

        // the claim
        KitDraw.heading(g, font, "Base", x + 37, y + 33, 262, false);
        int py = y + 42, ph = 250;
        KitDraw.panel(g, x + 37, py, 262, ph);
        int cx = x + 46, cy = py + 9;
        if (base == null) {
            KitDraw.caps(g, font, "No base claimed", cx, cy, KitDraw.BRIGHT, 0.95f);
            cy += 16;
            cy += KitDraw.wrap(g, font, "Stand inside a building, hold G and pick Claim this building as your base. "
                    + "Only you and the squad you let in can break, build or move anything inside it.", cx, cy, 244, KitDraw.DIM) + 8;
            KitDraw.wrap(g, font, "Rooms only count inside your base.", cx, cy, 244, KitDraw.FAINT);
        } else {
            boolean own = base.owner().equals(player.getUUID());
            KitDraw.caps(g, font, own ? "Your base" : base.ownerName() + "'s base", cx, cy, KitDraw.BRIGHT, 0.95f);
            cy += 13;
            KitDraw.text(g, font, base.width() + " by " + base.depth() + " blocks, " + (base.max().getY() - base.min().getY() + 1) + " high · at "
                    + (base.min().getX() + base.width() / 2) + ", " + (base.min().getZ() + base.depth() / 2), cx, cy, KitDraw.DIM, 0.8f);
            cy += 16;
            KitDraw.caps(g, font, "Squad", cx, cy, KitDraw.FAINT, 0.65f);
            cy += 11;
            KitDraw.text(g, font, base.ownerName() + " (owner)", cx, cy, KitDraw.TEXT, 0.85f);
            cy += 12;
            for (String name : base.squadNames()) {
                KitDraw.text(g, font, name, cx, cy, KitDraw.TEXT, 0.85f);
                cy += 12;
            }
            if (base.squadNames().isEmpty()) {
                KitDraw.text(g, font, "Look at someone, hold G and add them to your squad.", cx, cy, KitDraw.FAINT, 0.75f);
                cy += 12;
            }
            cy += 8;
            cy += KitDraw.wrap(g, font, "It grows as you build onto it. Claiming another building moves your base there.", cx, cy, 244, KitDraw.FAINT) + 6;
        }

        // the rooms
        int rx = x + 323, rw = 256;
        KitDraw.heading(g, font, "Rooms", rx, y + 33, rw, true);
        List<Rooms.Line> lines = BaseClient.report;
        int top = y + 42, bottom = y + KitScreen.H - 20;
        if (base == null && mine.isEmpty()) {
            KitDraw.text(g, font, "Claim a base to start fitting out rooms.", rx, top + 6, KitDraw.FAINT, 0.8f);
        } else if (lines.isEmpty()) {
            KitDraw.wrap(g, font, "No rooms yet. An enclosed, roofed space of at least 3 by 3 becomes a room from what's in it: "
                    + "beds make a bunk room, a stove and counter a kitchen, a first-aid cabinet a med bay.", rx, top + 6, rw, KitDraw.FAINT);
        }
        scroll = Math.max(0, Math.min(scroll, listHeight - (bottom - top)));
        int ry = top - scroll;
        for (Rooms.Line l : lines) {
            if (ry >= top && ry + ROW <= bottom) row(g, font, l, rx, ry, rw);
            ry += ROW + 2;
        }
        listHeight = ry + scroll - top;

        int hx = x + 37, hy = y + KitScreen.H - 14;
        hx += KitDraw.hint(g, font, "Hold G", "Claim or squad", hx, hy);
        hx += KitDraw.hint(g, font, "Wheel", "Scroll", hx, hy);
        screen.pagesAndClose(g, font, hx, hy);
    }

    private void row(GuiGraphicsExtractor g, Font font, Rooms.Line l, int x, int y, int w) {
        RoomType type = RoomType.values()[Math.min(l.type(), RoomType.values().length - 1)];
        RoomType.Grade grade = RoomType.Grade.values()[Math.min(l.grade(), 3)];
        KitDraw.frame(g, x, y, w, ROW, 0x33E8E4DA);
        g.fill(x, y, x + 2, y + ROW, grade == RoomType.Grade.FULL ? KitDraw.GOOD : grade == RoomType.Grade.NONE ? KitDraw.FAINT : 0xFFE0A85A);
        KitDraw.caps(g, font, type.label, x + 8, y + 4, KitDraw.BRIGHT, 0.85f);
        KitDraw.capsRight(g, font, grade == RoomType.Grade.NONE ? "Not fitted" : grade.label, x + w - 6, y + 4,
                grade == RoomType.Grade.FULL ? KitDraw.GOOD : KitDraw.TEXT, 0.75f);
        String info = l.floor() + " floor" + (type == RoomType.FARM ? "" : l.powered() ? " · powered" : " · no power") + (l.fixtures().isEmpty() ? "" : " · " + l.fixtures());
        clip(g, font, info, x + 8, y + 15, w - 14, KitDraw.DIM, 0.7f);
        clip(g, font, l.next().isEmpty() ? "Fully fitted" : "Next: " + l.next(), x + 8, y + 26, w - 14,
                l.next().isEmpty() ? KitDraw.GOOD : KitDraw.FAINT | 0x40000000, 0.7f);
    }

    private static void clip(GuiGraphicsExtractor g, Font font, String s, int x, int y, int width, int colour, float scale) {
        String cut = s;
        while (cut.length() > 4 && KitDraw.width(font, cut, scale) > width) cut = cut.substring(0, cut.length() - 2);
        if (!cut.equals(s)) cut = cut.substring(0, cut.length() - 1) + "...";
        KitDraw.text(g, font, cut, x, y, colour, scale);
    }
}
