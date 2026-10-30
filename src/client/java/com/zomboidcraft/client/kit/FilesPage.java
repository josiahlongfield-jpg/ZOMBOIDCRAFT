package com.zomboidcraft.client.kit;

import com.zomboidcraft.client.sound.UiSounds;
import com.zomboidcraft.world.files.LabFiles;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import org.jetbrains.annotations.Nullable;

/**
 * The files page: every file you've read in the world (memos, logs, orders, notes), grouped by
 * set down the left, the newest last, and the chosen one in full on the right, the way it was
 * written. Unread ones in a set show as a count, so you know there's more out there.
 */
final class FilesPage {
    private static final int ROW = 15;
    /** The file to show next time the page draws: the one just read, or the one clicked. */
    private static @Nullable String selected;

    private final KitScreen<?> screen;

    FilesPage(KitScreen<?> screen) {
        this.screen = screen;
    }

    static void show(String id) {
        selected = id;
    }

    void draw(GuiGraphicsExtractor g, int x, int y, int mx, int my) {
        Font font = screen.mc().font;
        LocalPlayer player = screen.player();
        Map<String, LabFiles.File> all = LabFiles.all();
        List<String> found = new ArrayList<>(LabFiles.found(player));
        if (selected != null && !found.contains(selected) && all.containsKey(selected)) {
            found.add(selected);     // read a moment ago; the server's word on it is on its way
        }
        // the sets in the order the json has them, each with what's been found of it
        Map<String, List<LabFiles.File>> sets = new LinkedHashMap<>();
        Map<String, Integer> totals = new LinkedHashMap<>();
        for (LabFiles.File f : all.values()) {
            totals.merge(f.set(), 1, Integer::sum);
            sets.computeIfAbsent(f.set(), k -> new ArrayList<>());
        }
        for (String id : found) {
            LabFiles.File f = all.get(id);
            if (f != null) sets.get(f.set()).add(f);
        }

        KitDraw.heading(g, font, "Files", x + 37, y + 33, 262, false);
        KitDraw.textRight(g, font, found.size() + " of " + all.size() + " found", x + 299, y + 41, KitDraw.FAINT, 0.8f);
        int ry = y + 52;
        LabFiles.File shown = selected == null ? null : all.get(selected);
        if (found.isEmpty()) {
            KitDraw.wrap(g, font, "Nothing yet. Papers, clipboards and folders left lying around can be read with the interact key.",
                    x + 42, ry, 252, KitDraw.DIM);
        }
        for (Map.Entry<String, List<LabFiles.File>> set : sets.entrySet()) {
            if (set.getValue().isEmpty() || ry > y + KitScreen.H - 40) continue;
            KitDraw.caps(g, font, set.getKey(), x + 42, ry, KitDraw.FAINT, 0.6f);
            KitDraw.textRight(g, font, set.getValue().size() + " / " + totals.get(set.getKey()), x + 294, ry, KitDraw.FAINT, 0.6f);
            ry += 8;
            for (LabFiles.File f : set.getValue()) {
                if (ry > y + KitScreen.H - 34) break;
                boolean on = shown != null && f.id().equals(shown.id()), over = screen.hovered(x + 37, ry, 262, ROW - 1, mx, my);
                if (on) g.fill(x + 37, ry, x + 299, ry + ROW - 1, 0x14E8E4DA);
                KitDraw.frame(g, x + 37, ry, 262, ROW - 1, on ? 0x80E8E4DA : over ? 0x59E8E4DA : 0x00000000);
                KitDraw.text(g, font, f.title(), x + 42, ry + 3, on ? KitDraw.BRIGHT : KitDraw.TEXT, 0.85f);
                KitDraw.textRight(g, font, f.date(), x + 294, ry + 3, KitDraw.DIM, 0.75f);
                String id = f.id();
                screen.hot(x + 37, ry, 262, ROW - 1, () -> {
                    if (!id.equals(selected)) UiSounds.play(UiSounds.CLICK);
                    selected = id;
                });
                ry += ROW;
            }
            ry += 3;
        }
        if (shown == null && !found.isEmpty()) {
            shown = all.get(found.get(found.size() - 1));
        }
        if (shown != null) {
            page(g, font, shown, x + 323, y + 33, 256);
        }

        int hx = x + 37, hy = y + KitScreen.H - 14;
        hx += KitDraw.hint(g, font, "LMB", "Read", hx, hy);
        screen.pagesAndClose(g, font, hx, hy);
    }

    /** The file itself on a sheet: who it's from and when, the title, the text. */
    private static void page(GuiGraphicsExtractor g, Font font, LabFiles.File f, int x, int y, int w) {
        KitDraw.heading(g, font, f.set(), x, y, w, true);
        int py = y + 9, ph = 250;
        KitDraw.panel(g, x, py, w, ph);
        int cx = x + 9, cy = py + 9;
        KitDraw.caps(g, font, f.from(), cx, cy, KitDraw.DIM, 0.7f);
        KitDraw.textRight(g, font, f.date(), x + w - 9, cy, KitDraw.DIM, 0.75f);
        cy += 10;
        cy += KitDraw.wrap(g, font, f.title(), cx, cy, w - 18, KitDraw.BRIGHT) + 5;
        g.fill(cx, cy, x + w - 9, cy + 1, 0x1AE8E4DA);
        cy += 5;
        for (String para : f.body().split("\n")) {
            if (cy > py + ph - 12) break;
            cy += KitDraw.wrap(g, font, para, cx, cy, w - 18, KitDraw.TEXT) + 4;
        }
    }
}
