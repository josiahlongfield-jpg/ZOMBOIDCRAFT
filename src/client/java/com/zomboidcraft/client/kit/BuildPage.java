package com.zomboidcraft.client.kit;

import com.zomboidcraft.ModItems;
import com.zomboidcraft.building.Build;
import com.zomboidcraft.building.Builder;
import com.zomboidcraft.building.Materials;
import com.zomboidcraft.client.building.Placer;
import com.zomboidcraft.client.sound.UiSounds;
import java.util.Locale;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Direction;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The build page: everything you can put up, down the left with whether you've got what it takes, and on the right
 * what the chosen one needs, counted against what you carry (bags too). Build closes the kit and hands you a
 * see-through copy to place (client/building/Placer); a thing made in the hand, like a welded sheet, is made at once.
 */
final class BuildPage {
    private final KitScreen<?> screen;
    private Build selected = Build.BOARD;

    BuildPage(KitScreen<?> screen) {
        this.screen = screen;
    }

    static Item icon(Build build) {
        return switch (build) {
            case BOARD -> Items.OAK_PLANKS;
            case METAL_SHEET, WELD_SHEET -> ModItems.METAL_SHEET;
            case TRIPWIRE -> ModItems.TIN_CAN;
            case RAIN_BARREL -> ModItems.PLASTIC_DRUM;
            case SANDBAGS -> ModItems.EMPTY_SANDBAG;
            case ALARM_CLOCK, CAR_ALARM -> ModItems.ALARM_CLOCK;
            case PLANTER -> ModItems.CARROT_SEEDS;
            case SPIKED_WALL -> Items.OAK_LOG;
        };
    }

    void draw(GuiGraphicsExtractor g, int x, int y, int mx, int my) {
        Font font = screen.mc().font;
        LocalPlayer player = screen.player();
        KitDraw.heading(g, font, "Build", x + 37, y + 33, 262, false);
        KitDraw.textRight(g, font, "From what you carry, bags too", x + 299, y + 41, KitDraw.FAINT, 0.8f);
        int ry = y + 52;
        for (Build b : Build.values()) {
            row(g, font, player, b, x + 37, ry, 262, mx, my);
            ry += ROW + 2;
        }
        detail(g, font, player, selected, x + 323, y + 33, 256, mx, my);

        int hx = x + 37, hy = y + KitScreen.H - 14;
        hx += KitDraw.hint(g, font, "LMB", "Select", hx, hy);
        screen.pagesAndClose(g, font, hx, hy);
        KitDraw.textRight(g, font, "Building takes time and makes noise", x + 579, hy + 1, KitDraw.FAINT, 0.9f);
    }

    /** A row's height: ten builds fit down the page. */
    private static final int ROW = 25;

    private void row(GuiGraphicsExtractor g, Font font, LocalPlayer player, Build b, int x, int y, int w, int mx, int my) {
        boolean on = b == selected, over = screen.hovered(x, y, w, ROW, mx, my);
        String missing = b.missing(player);
        if (on) g.fill(x, y, x + w, y + ROW, 0x14E8E4DA);
        KitDraw.frame(g, x, y, w, ROW, on ? 0x80E8E4DA : over ? 0x59E8E4DA : 0x00000000);
        KitDraw.tile(g, font, ItemStack.EMPTY, x + 2, y + 2, 21, 21, false);
        KitDraw.item(g, new ItemStack(icon(b)), x + 4.5f, y + 4.5f, 16);
        KitDraw.caps(g, font, b.label, x + 29, y + 4, missing == null ? KitDraw.BRIGHT : KitDraw.DIM, 0.85f);
        // the needs in a line, each green when you have enough, red when not
        float nx = x + 29;
        for (Build.Need n : needsAndTool(b)) {
            int have = Materials.count(player, n.fits());
            boolean ok = have >= n.count() || player.hasInfiniteMaterials();
            String s = (n.count() > 1 ? n.count() + " " : "") + n.label().toLowerCase(Locale.ROOT);
            KitDraw.text(g, font, s, nx, y + 15, ok ? KitDraw.DIM : KitDraw.BAD, 0.65f);
            nx += KitDraw.width(font, s, 0.65f) + 6;
        }
        KitDraw.capsRight(g, font, missing == null ? "Ready" : "Missing", x + w - 6, y + 4,
                missing == null ? KitDraw.GOOD : KitDraw.FAINT, 0.65f);
        screen.hot(x, y, w, ROW, () -> {
            if (selected != b) UiSounds.play(UiSounds.CLICK);
            selected = b;
        });
    }

    private static java.util.List<Build.Need> needsAndTool(Build b) {
        java.util.List<Build.Need> all = new java.util.ArrayList<>();
        if (b.tool != null) all.add(b.tool);
        all.addAll(b.needs);
        return all;
    }

    private void detail(GuiGraphicsExtractor g, Font font, LocalPlayer player, Build b, int x, int y, int w, int mx, int my) {
        KitDraw.heading(g, font, b.label, x, y, w, true);
        int py = y + 9, ph = 250;
        KitDraw.panel(g, x, py, w, ph);
        int cx = x + 9, cy = py + 9;
        KitDraw.item(g, new ItemStack(icon(b)), cx, cy, 40);
        int tx = cx + 48;
        String where = switch (b.where) {
            case OPENING -> "On a window or doorway";
            case GROUND -> "On solid ground";
            case CAR -> "On a parked car";
            case MAKE -> "Made in your hands";
        };
        KitDraw.caps(g, font, where, tx, cy + 3, KitDraw.TEXT, 0.75f);
        int level = b.skillLevel(player);
        String secs = String.format(Locale.ROOT, "%.1f s", b.ticksFor(player) / 20f);
        if (b.skill != null) {
            KitDraw.text(g, font, secs + " · " + b.skill.displayName() + " " + level + (b.level > 0 ? " (needs " + b.level + ")" : ""),
                    tx, cy + 15, b.level > level ? KitDraw.BAD : KitDraw.DIM, 0.8f);
            KitDraw.text(g, font, "+" + b.xp + " " + b.skill.displayName() + " xp", tx, cy + 26, KitDraw.FAINT, 0.75f);
        } else {
            KitDraw.text(g, font, secs + " · no skill needed", tx, cy + 15, KitDraw.DIM, 0.8f);
        }
        cy += 48;
        cy += KitDraw.wrap(g, font, b.about, cx, cy, w - 18, KitDraw.TEXT) + 8;

        KitDraw.caps(g, font, "Needs", cx, cy, KitDraw.FAINT, 0.65f);
        cy += 10;
        for (Build.Need n : needsAndTool(b)) {
            int have = Materials.count(player, n.fits());
            boolean tool = n == b.tool;
            boolean ok = have >= n.count() || player.hasInfiniteMaterials();
            KitDraw.item(g, new ItemStack(n.icon()), cx, cy - 2, 14);
            KitDraw.text(g, font, n.label() + (tool ? " (kept, wears down)" : ""), cx + 19, cy + 1, ok ? KitDraw.TEXT : KitDraw.DIM, 0.85f);
            String count = tool ? (have > 0 ? "On you" : "None") : have + " / " + n.count();
            KitDraw.textRight(g, font, count, x + w - 9, cy + 1, ok ? KitDraw.GOOD : KitDraw.BAD, 0.85f);
            cy += 16;
            g.fill(cx, cy - 2, x + w - 9, cy - 1, 0x1AE8E4DA);
        }
        cy += 6;
        String missing = b.missing(player);
        boolean can = missing == null;
        String verb = b.where == Build.Where.MAKE ? "Make" : "Build";
        int bw = 70, bh = 16, bx = x + w - 9 - bw, by = py + ph - 9 - bh;
        boolean over = can && screen.hovered(bx, by, bw, bh, mx, my);
        if (can) g.fill(bx, by, bx + bw, by + bh, over ? 0xF2E8E4DA : 0x33E8E4DA);
        KitDraw.frame(g, bx, by, bw, bh, can ? 0xB3E8E4DA : 0x33E8E4DA);
        KitDraw.capsCentred(g, font, verb, bx + bw / 2f, by + 5, over ? 0xFF121314 : can ? KitDraw.BRIGHT : KitDraw.FAINT, 0.85f);
        if (!can) {
            KitDraw.text(g, font, missing, cx, by + 4, KitDraw.BAD, 0.8f);
            screen.hot(bx, by, bw, bh, ItemStack.EMPTY, null, null, missing, -1);
            return;
        }
        if (b.where != Build.Where.MAKE) {
            KitDraw.text(g, font, b.where == Build.Where.CAR ? "Then look at the car and left click" : "Then look where it goes and left click",
                    cx, by + 4, KitDraw.DIM, 0.75f);
        }
        screen.hot(bx, by, bw, bh, () -> {
            UiSounds.play(UiSounds.CLICK);
            if (b.where == Build.Where.MAKE) {
                ClientPlayNetworking.send(new Builder.Request(b.ordinal(), player.blockPosition(), Direction.UP.get3DDataValue(),
                        player.getDirection().get3DDataValue()));
                screen.onClose();
            } else {
                screen.onClose();
                Placer.start(b);
            }
        });
    }
}
