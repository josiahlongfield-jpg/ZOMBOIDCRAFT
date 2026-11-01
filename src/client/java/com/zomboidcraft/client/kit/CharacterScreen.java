package com.zomboidcraft.client.kit;

import com.mojang.blaze3d.platform.InputConstants;
import com.zomboidcraft.skills.Skill;
import com.zomboidcraft.survival.character.CharacterData;
import com.zomboidcraft.survival.character.CharacterData.Occupation;
import com.zomboidcraft.survival.character.CharacterData.Trait;
import com.zomboidcraft.survival.character.CharacterNet;
import com.zomboidcraft.survival.character.Traits;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * Zomboid's character creation: occupations down the left, the positive and negative traits in the middle, and the
 * survivor you're making on the right with the points you have left. Click a trait to take it, click it again on the
 * right to give it back. It comes before the spawn picker, on your first time in a world and after every death.
 */
public final class CharacterScreen extends Screen {
    private static final int ROW = 11;

    /** What the server offered, kept until you've made your survivor (the screen comes back over the game menu). */
    private static CharacterNet.Open offered;

    private final CharacterNet.Open open;
    private String occupation = CharacterData.UNEMPLOYED;
    private final List<String> chosen = new ArrayList<>();
    private String hovered;

    private final List<Column> columns = new ArrayList<>();
    private final Column jobs = new Column(), good = new Column(), bad = new Column(), mine = new Column();
    private int[] done, random, reset;

    /** One scrolling list: its box and the rows it drew last frame. */
    private static final class Column {
        int x, top, w, bottom, scroll, content;
        final List<int[]> rows = new ArrayList<>();
        final List<String> ids = new ArrayList<>();

        boolean over(double mx, double my) {
            return mx >= x && mx < x + w && my >= top && my < bottom;
        }
    }

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(CharacterNet.Open.TYPE, (payload, context) -> context.client().execute(() -> {
            offered = payload;
            show(context.client());
        }));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> offered = null);
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            if (offered != null && mc.level != null && (mc.gui.screen() == null || mc.gui.screen() instanceof SpawnScreen)) show(mc);
        });
    }

    /** Whether you're still to make your survivor: the spawn picker waits until you have. */
    public static boolean pending() {
        return offered != null;
    }

    private static void show(Minecraft mc) {
        if (!(mc.gui.screen() instanceof CharacterScreen) && mc.level != null) mc.gui.setScreen(new CharacterScreen(offered));
    }

    public CharacterScreen(CharacterNet.Open open) {
        super(Component.literal("Who were you?"));
        this.open = open;
        columns.add(jobs);
        columns.add(good);
        columns.add(bad);
        columns.add(mine);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    private int points() {
        return Traits.points(occupation, chosen, open.free(), open.penaltyEvery());
    }

    // ------------------------------------------------------------------ drawing

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float partialTick) {
        g.fill(0, 0, width, height, 0xFF0B0C0E);
        int pad = 14, top = 42, bottom = height - 34;
        KitDraw.caps(g, font, open.again() ? "You died · a new survivor" : "Grayhaven County · new survivor", pad, 10, KitDraw.DIM, 0.8f);
        KitDraw.caps(g, font, "Who were you?", pad, 20, KitDraw.BRIGHT, 1.6f);
        int left = points();
        String pts = left + (left == 1 || left == -1 ? " point" : " points") + " left";
        KitDraw.capsRight(g, font, pts, width - pad, 21, left < 0 ? KitDraw.BAD : left == 0 ? KitDraw.TEXT : KitDraw.GOOD, 1.2f);

        int gap = 8;
        int usable = width - pad * 2 - gap * 3;
        int jw = Math.max(80, usable * 19 / 100), cw = Math.max(84, usable * 20 / 100);
        int rw = usable - jw - cw * 2;
        int x = pad;
        hovered = null;
        jobList(g, x, top, jw, bottom, mx, my);
        x += jw + gap;
        traitList(g, good, "Positive traits", true, x, top, cw, bottom, mx, my);
        x += cw + gap;
        traitList(g, bad, "Negative traits", false, x, top, cw, bottom, mx, my);
        x += cw + gap;
        card(g, x, top, rw, bottom, mx, my);
        info(g, pad, bottom + 6, width - pad * 2);

        int hx = pad;
        hx += KitDraw.hint(g, font, "LMB", "Take or give back", hx, height - 15);
        hx += KitDraw.hint(g, font, "Enter", "Done", hx, height - 15);
        KitDraw.hint(g, font, "Esc", "Game menu", hx, height - 15);
    }

    private void jobList(GuiGraphicsExtractor g, int x, int top, int w, int bottom, int mx, int my) {
        KitDraw.heading(g, font, "Occupation", x, top, w, false);
        begin(g, jobs, x, top + 12, w, bottom);
        int y = jobs.top - jobs.scroll;
        for (Occupation o : CharacterData.occupations().values()) {
            boolean sel = o.id().equals(occupation);
            boolean hover = row(g, jobs, o.id(), x, y, w, sel, mx, my);
            if (hover) hovered = "o:" + o.id();
            KitDraw.text(g, font, o.name(), x + 4, y + 2, sel ? KitDraw.BRIGHT : KitDraw.TEXT, 0.8f);
            KitDraw.capsRight(g, font, signed(o.points()), x + w - 4, y + 3, o.points() > 0 ? KitDraw.GOOD : o.points() < 0 ? KitDraw.BAD : KitDraw.DIM, 0.7f);
            y += ROW + 1;
        }
        end(g, jobs, y);
    }

    private void traitList(GuiGraphicsExtractor g, Column c, String title, boolean positive, int x, int top, int w, int bottom, int mx, int my) {
        KitDraw.heading(g, font, title, x, top, w, false);
        begin(g, c, x, top + 12, w, bottom);
        int y = c.top - c.scroll;
        for (Trait t : CharacterData.traits().values()) {
            if (t.occupationOnly() || t.positive() != positive || t.cost() == 0) continue;
            if (chosen.contains(t.id())) continue;
            String why = Traits.blocked(occupation, chosen, t.id());
            boolean hover = row(g, c, t.id(), x, y, w, false, mx, my);
            if (hover) hovered = "t:" + t.id();
            int ink = why != null ? KitDraw.FAINT : KitDraw.TEXT;
            KitDraw.text(g, font, t.name(), x + 4, y + 2, ink, 0.8f);
            // costs read the way Zomboid shows them: what it takes off your points, or gives back
            KitDraw.capsRight(g, font, signed(-t.cost()), x + w - 4, y + 3, why != null ? KitDraw.FAINT : positive ? KitDraw.BAD : KitDraw.GOOD, 0.7f);
            y += ROW + 1;
        }
        end(g, c, y);
    }

    private void card(GuiGraphicsExtractor g, int x, int top, int w, int bottom, int mx, int my) {
        Occupation o = CharacterData.occupation(occupation);
        KitDraw.panel(g, x, top, w, bottom - top);
        int in = 8, tw = w - in * 2, y = top + in;
        KitDraw.caps(g, font, "Your survivor", x + in, y, KitDraw.DIM, 0.7f);
        y += 9;
        KitDraw.caps(g, font, o == null ? "" : o.name(), x + in, y, KitDraw.BRIGHT, 1.2f);
        y += 13;
        g.fill(x, y, x + w, y + 1, KitDraw.EDGE);
        y += 6;

        // what you start knowing
        KitDraw.caps(g, font, "Starting skills", x + in, y, KitDraw.DIM, 0.7f);
        y += 9;
        List<String> all = new ArrayList<>(chosen);
        if (o != null) for (String f : o.traits()) if (!all.contains(f)) all.add(f);
        Map<Skill, Integer> bonus = Traits.bonuses(occupation, all);
        if (bonus.isEmpty()) {
            KitDraw.text(g, font, "Nothing above the usual", x + in, y, KitDraw.FAINT, 0.75f);
            y += 9;
        }
        int col = 0;
        for (Map.Entry<Skill, Integer> e : bonus.entrySet()) {
            int level = (e.getKey().passive() ? 5 : 0) + e.getValue();
            float cx = x + in + col * (tw / 2f);
            KitDraw.text(g, font, e.getKey().displayName(), cx, y, KitDraw.TEXT, 0.75f);
            KitDraw.textRight(g, font, String.valueOf(Math.max(0, Math.min(10, level))), cx + tw / 2f - 8, y,
                    e.getValue() < 0 ? KitDraw.BAD : KitDraw.GOOD, 0.75f);
            if (++col == 2) {
                col = 0;
                y += 9;
            }
        }
        if (col != 0) y += 9;
        y += 5;

        // the traits you have: the job's own first (fixed), then yours (click to give back)
        KitDraw.caps(g, font, "Traits", x + in, y, KitDraw.DIM, 0.7f);
        y += 9;
        int listBottom = bottom - 48;
        if (o != null) {
            for (String f : o.traits()) {
                Trait t = CharacterData.trait(f);
                if (t == null || y > listBottom) continue;
                KitDraw.text(g, font, t.name(), x + in, y, KitDraw.DIM, 0.75f);
                KitDraw.capsRight(g, font, "job", x + w - in, y + 1, KitDraw.FAINT, 0.6f);
                if (KitDraw.in(mx, my, x + in, y, tw, 9)) hovered = "t:" + f;
                y += 10;
            }
        }
        mine.rows.clear();
        mine.ids.clear();
        mine.x = x + in;
        mine.w = tw;
        mine.top = top;
        mine.bottom = listBottom;
        // yours in two columns, so a full build still fits
        int cwid = (tw - 6) / 2, slot = 0, rowY = y;
        for (String id : chosen) {
            Trait t = CharacterData.trait(id);
            if (t == null || rowY > listBottom) continue;
            int cx = x + in + (slot % 2) * (cwid + 6);
            boolean hover = KitDraw.in(mx, my, cx, rowY - 1, cwid, 10);
            if (hover) {
                hovered = "t:" + id;
                g.fill(cx - 2, rowY - 1, cx + cwid + 2, rowY + 9, 0x14E8E4DA);
            }
            KitDraw.text(g, font, t.name(), cx, rowY, t.positive() ? KitDraw.GOOD : KitDraw.BAD, 0.7f);
            if (hover) KitDraw.capsRight(g, font, "x", cx + cwid, rowY + 1, KitDraw.DIM, 0.6f);
            mine.rows.add(new int[]{cx, rowY - 1, cwid, 10});
            mine.ids.add(id);
            if (++slot % 2 == 0) rowY += 10;
        }
        y = rowY + (slot % 2 == 1 ? 10 : 0);
        if (chosen.isEmpty()) KitDraw.text(g, font, "Pick some from the middle", x + in, y, KitDraw.FAINT, 0.75f);

        // done: a bone slab like the spawn picker's, greyed while you're over budget
        int by = bottom - in - 32;
        int left = points();
        done = new int[]{x + in, by, tw, 16};
        boolean hd = KitDraw.in(mx, my, done[0], done[1], done[2], done[3]) && left >= 0;
        int face = left < 0 ? 0xFF4A4740 : hd ? 0xFFF7F4EE : 0xFFE8E4DA;
        g.fill(x + in, by, x + in + tw, by + 16, face);
        g.fill(x + in, by, x + in + tw, by + 1, left < 0 ? 0xFF55524A : hd ? 0xFFFFFFFF : 0xFFF4F1EA);
        g.fill(x + in, by + 15, x + in + tw, by + 16, left < 0 ? 0xFF3A3832 : 0xFFB9B4A8);
        String label = left < 0 ? "Over budget" : "Next: where you wake";
        float ls = Math.min(0.85f, (tw - 8) / Math.max(1f, KitDraw.capsWidth(font, label, 1f)));
        float lw = KitDraw.capsWidth(font, label, ls);
        com.zomboidcraft.client.hud.Hud.caps(g, font, label, x + w / 2f - lw / 2, by + 4.5f, left < 0 ? 0xFF8F8A80 : 0xFF1D1B16, ls, ls, false);
        int half = (tw - 4) / 2;
        random = new int[]{x + in, by + 20, half, 12};
        reset = new int[]{x + in + half + 4, by + 20, half, 12};
        KitDraw.frame(g, random[0], random[1], random[2], random[3], KitDraw.in(mx, my, random[0], random[1], random[2], random[3]) ? KitDraw.BRIGHT : 0x73E8E4DA);
        KitDraw.capsCentred(g, font, "Random", random[0] + half / 2f, random[1] + 3, KitDraw.TEXT, 0.75f);
        KitDraw.frame(g, reset[0], reset[1], reset[2], reset[3], KitDraw.in(mx, my, reset[0], reset[1], reset[2], reset[3]) ? KitDraw.BRIGHT : 0x73E8E4DA);
        KitDraw.capsCentred(g, font, "Clear", reset[0] + half / 2f, reset[1] + 3, KitDraw.TEXT, 0.75f);
    }

    /** The line under the columns: what the thing under the mouse is, or why you can't take it. */
    private void info(GuiGraphicsExtractor g, int x, int y, int w) {
        String name = null, text = null, why = null;
        if (hovered != null && hovered.startsWith("o:")) {
            Occupation o = CharacterData.occupation(hovered.substring(2));
            if (o != null) {
                name = o.name();
                text = o.description() + skills(o.skills());
            }
        } else if (hovered != null && hovered.startsWith("t:")) {
            Trait t = CharacterData.trait(hovered.substring(2));
            if (t != null) {
                name = t.name();
                text = t.description() + skills(t.skills());
                if (!chosen.contains(t.id())) why = Traits.blocked(occupation, chosen, t.id());
            }
        }
        if (name == null) {
            KitDraw.text(g, font, "Your occupation gives points (or costs them). Positive traits cost points; negative traits give them back.",
                    x, y, KitDraw.FAINT, 0.75f);
            return;
        }
        float nw = KitDraw.capsWidth(font, name, 0.8f);
        KitDraw.caps(g, font, name, x, y, KitDraw.BRIGHT, 0.8f);
        KitDraw.text(g, font, text, x + nw + 8, y, KitDraw.TEXT, 0.75f);
        if (why != null) KitDraw.textRight(g, font, why, x + w, y, KitDraw.WARN, 0.75f);
    }

    private static String skills(Map<Skill, Integer> skills) {
        if (skills.isEmpty()) return "";
        List<String> out = new ArrayList<>();
        skills.forEach((s, n) -> out.add((n > 0 ? "+" : "") + n + " " + s.displayName()));
        return "  " + String.join(", ", out);
    }

    private void begin(GuiGraphicsExtractor g, Column c, int x, int top, int w, int bottom) {
        c.x = x;
        c.top = top;
        c.w = w;
        // whole rows only: the list ends on a row boundary, so no row is ever cut in half by the frame
        c.bottom = top + Math.max(ROW + 1, (bottom - top) / (ROW + 1) * (ROW + 1));
        c.rows.clear();
        c.ids.clear();
        g.enableScissor(x, top, x + w, c.bottom);
    }

    private void end(GuiGraphicsExtractor g, Column c, int y) {
        g.disableScissor();
        c.content = y + c.scroll - c.top;
        if (c.content > c.bottom - c.top) {
            // a thin scroll thumb so you know there's more below
            int h = c.bottom - c.top, thumb = Math.max(10, h * h / c.content);
            int ty = c.top + (h - thumb) * c.scroll / Math.max(1, c.content - h);
            g.fill(c.x + c.w - 1, ty, c.x + c.w, ty + thumb, 0x73E8E4DA);
        }
    }

    private boolean row(GuiGraphicsExtractor g, Column c, String id, int x, int y, int w, boolean sel, int mx, int my) {
        boolean hover = mx >= x && mx < x + w && my >= y && my < y + ROW && my >= c.top && my < c.bottom;
        if (sel || hover) {
            g.fill(x, y, x + w, y + ROW, sel ? 0x1FE8E4DA : 0x0DE8E4DA);
            KitDraw.frame(g, x, y, w, ROW, sel ? 0xD9F2EFE8 : 0x73E8E4DA);
        }
        c.rows.add(new int[]{x, y, w, ROW});
        c.ids.add(id);
        return hover;
    }

    private static String signed(int n) {
        return n > 0 ? "+" + n : String.valueOf(n);
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mx = event.x(), my = event.y();
        if (pick(jobs, mx, my, id -> {
            occupation = id;
            // traits the new job gives, or now can't sit beside, drop out
            chosen.removeIf(t -> Traits.blocked(occupation, chosen.stream().filter(c -> !c.equals(t)).toList(), t) != null);
        })) return true;
        if (pick(good, mx, my, this::take) || pick(bad, mx, my, this::take)) return true;
        for (int i = 0; i < mine.rows.size(); i++) {
            int[] r = mine.rows.get(i);
            if (KitDraw.in(mx, my, r[0], r[1], r[2], r[3])) {
                chosen.remove(mine.ids.get(i));
                return true;
            }
        }
        if (done != null && KitDraw.in(mx, my, done[0], done[1], done[2], done[3])) {
            finish();
            return true;
        }
        if (random != null && KitDraw.in(mx, my, random[0], random[1], random[2], random[3])) {
            randomise();
            return true;
        }
        if (reset != null && KitDraw.in(mx, my, reset[0], reset[1], reset[2], reset[3])) {
            chosen.clear();
            return true;
        }
        return true;
    }

    private boolean pick(Column c, double mx, double my, java.util.function.Consumer<String> action) {
        if (!c.over(mx, my)) return false;
        for (int i = 0; i < c.rows.size(); i++) {
            int[] r = c.rows.get(i);
            if (KitDraw.in(mx, my, r[0], r[1], r[2], r[3])) {
                action.accept(c.ids.get(i));
                return true;
            }
        }
        return true;
    }

    private void take(String id) {
        if (Traits.blocked(occupation, chosen, id) == null) chosen.add(id);
    }

    /** Zomboid's random survivor: any job, then traits drawn until the points run out, never over budget. */
    private void randomise() {
        Random r = new Random();
        List<String> jobs = new ArrayList<>(CharacterData.occupations().keySet());
        occupation = jobs.get(r.nextInt(jobs.size()));
        chosen.clear();
        List<String> pool = new ArrayList<>();
        for (Trait t : CharacterData.traits().values()) if (!t.occupationOnly() && t.cost() != 0) pool.add(t.id());
        java.util.Collections.shuffle(pool, r);
        for (String id : pool) {
            if (chosen.size() >= 6) break;
            if (Traits.blocked(occupation, chosen, id) != null) continue;
            chosen.add(id);
            if (points() < 0) chosen.remove(id);
        }
    }

    private void finish() {
        if (points() < 0) return;
        ClientPlayNetworking.send(new CharacterNet.Create(occupation, List.copyOf(chosen)));
        offered = null;
        minecraft.gui.setScreen(null);  // the spawn picker comes up next, if this world has one
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        for (Column c : columns) {
            if (c.over(mx, my)) {
                int max = Math.max(0, c.content - (c.bottom - c.top));
                c.scroll = Math.max(0, Math.min(max, c.scroll - (int) Math.round(sy * 2) * (ROW + 1)));
            }
        }
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == InputConstants.KEY_ESCAPE) {
            minecraft.gui.setScreen(new net.minecraft.client.gui.screens.PauseScreen(true));
            return true;
        }
        if (event.key() == InputConstants.KEY_RETURN || event.key() == InputConstants.KEY_NUMPADENTER) {
            finish();
            return true;
        }
        return true;
    }

    // ------------------------------------------------------------------ for the UI test

    public void choose(String occupation, List<String> traits) {
        this.occupation = occupation;
        chosen.clear();
        for (String t : traits) take(t);
    }

    public int pointsLeft() {
        return points();
    }
}
