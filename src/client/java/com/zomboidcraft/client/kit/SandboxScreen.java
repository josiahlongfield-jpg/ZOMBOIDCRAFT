package com.zomboidcraft.client.kit;

import com.mojang.blaze3d.platform.InputConstants;
import com.zomboidcraft.client.sandbox.SandboxClient;
import com.zomboidcraft.sandbox.Sandbox;
import com.zomboidcraft.sandbox.SandboxGroup;
import com.zomboidcraft.sandbox.SandboxNet;
import com.zomboidcraft.sandbox.SandboxOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.IntConsumer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * Zomboid's Sandbox page (PZ review section 20, Josiah 2026-10-31): the world's settings in Zomboid's twelve groups plus
 * ours, and a search over all of them. No presets (Josiah 05:06: the world would need redesigning for them); a diamond
 * marks each setting changed from our default. The settings themselves (the list, what each one
 * does, the store) are the world settings backbone's ({@link Sandbox}); this only draws them and sends changes.
 * <p>
 * Two ways in: for a world you're in (/zc sandbox, or the pause menu), changes go to the server, which keeps the host's;
 * from the create-world screen it edits a draft the new world starts with ({@link Sandbox#forNewWorld}). Settings whose
 * system is still being built show greyed with who's building it; Josiah's fixed rules show their value and reason.
 */
public class SandboxScreen extends Screen {
    private static final int ROW = 18, NAV_ROW = 15, NAV_W = 150, CONTROL_W = 150;

    private final Screen parent;
    /** The create-world draft: every per-world value; null when editing the world you're in. */
    private final Map<String, String> draft;

    private SandboxGroup group = SandboxGroup.POPULATION;
    private float scroll;
    private int maxScroll;
    private EditBox search, field;
    /** The setting the field is typing into, or null. */
    private SandboxOption editing;
    private String message;
    private int messageColour = KitDraw.DIM;
    private SandboxOption hovered;
    private final List<Hit> hits = new ArrayList<>();

    private record Hit(int x, int y, int w, int h, IntConsumer act) {
    }

    /** The page for the world you're in. */
    public SandboxScreen(Screen parent) {
        this(parent, null);
    }

    private SandboxScreen(Screen parent, Map<String, String> draft) {
        super(Component.literal("Sandbox"));
        this.parent = parent;
        this.draft = draft;
    }

    /** The page for a world about to be made, starting from our defaults. */
    public static SandboxScreen forNewWorld(Screen parent) {
        Map<String, String> d = new HashMap<>();
        for (SandboxOption o : Sandbox.options()) {
            if (o.perWorld()) d.put(o.id, o.defaultValue());
        }
        return new SandboxScreen(parent, d);
    }

    /** /zc sandbox opens it; so does a button on the pause menu and on the create-world screen. */
    public static void register() {
        SandboxClient.opener = () -> {
            Minecraft mc = Minecraft.getInstance();
            mc.gui.setScreen(new SandboxScreen(mc.gui.screen()));
        };
        ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
            if (screen instanceof PauseScreen && client.player != null) {
                Screens.getButtons(screen).add(Button.builder(Component.literal("Sandbox settings"),
                        b -> client.gui.setScreen(new SandboxScreen(screen))).bounds(6, 6, 110, 20).build());
            } else if (screen instanceof CreateWorldScreen) {
                Screens.getButtons(screen).add(Button.builder(Component.literal("ZomboidCraft sandbox"),
                        b -> client.gui.setScreen(forNewWorld(screen))).bounds(w - 136, 6, 130, 20).build());
            }
        });
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private int w() {
        return Math.min(600, width - 16);
    }

    private int h() {
        return Math.min(340, height - 16);
    }

    private int x0() {
        return (width - w()) / 2;
    }

    private int y0() {
        return (height - h()) / 2;
    }

    @Override
    protected void init() {
        super.init();
        search = new EditBox(font, x0() + w() - 170, y0() + 8, 160, 12, Component.literal("Search"));
        search.setBordered(false);
        search.setHint(Component.literal("Search all settings"));
        search.setMaxLength(40);
        search.setResponder(s -> scroll = 0);
        addRenderableWidget(search);
        field = new EditBox(font, 0, 0, CONTROL_W - 8, 12, Component.literal("Value"));
        field.setBordered(false);
        field.setMaxLength(400);
        field.visible = false;
        addRenderableWidget(field);
    }

    // ---- reading and writing a value, for the world or the draft ----

    private String value(SandboxOption o) {
        if (draft != null && o.perWorld()) {
            return draft.getOrDefault(o.id, o.defaultValue());
        }
        return Sandbox.value(o.id);
    }

    private void set(SandboxOption o, String raw) {
        String v;
        try {
            v = o.check(raw);
        } catch (IllegalArgumentException e) {
            say(e.getMessage(), KitDraw.BAD);
            return;
        }
        if (draft != null && o.perWorld()) {
            draft.put(o.id, v);
        } else if (o.client) {
            try {
                Sandbox.set(o.id, v);
            } catch (IllegalArgumentException e) {
                say(e.getMessage(), KitDraw.BAD);
                return;
            }
        } else {
            ClientPlayNetworking.send(new SandboxNet.Set(o.id, v));
        }
        say(o.label + ": " + shown(o, v), KitDraw.DIM);
        com.zomboidcraft.client.sound.UiSounds.play(com.zomboidcraft.client.sound.UiSounds.CLICK);
    }

    /** Shows a group (the UI test calls it too). */
    public void group(SandboxGroup g) {
        group = g;
        scroll = 0;
        search.setValue("");
    }

    /** Searches (the UI test calls it too). */
    public void search(String text) {
        search.setValue(text);
    }

    private void say(String text, int colour) {
        message = text;
        messageColour = colour;
    }

    /** Whether a setting differs from our default: a diamond marks it. */
    private boolean changed(SandboxOption o) {
        if (o.kind == SandboxOption.Kind.FIXED || o.client) return false;
        return !o.defaultValue().equals(value(o));
    }

    private List<SandboxOption> shownOptions() {
        String q = search == null ? "" : search.getValue().trim().toLowerCase(Locale.ROOT);
        if (q.isEmpty()) {
            return Sandbox.options(group);
        }
        List<SandboxOption> out = new ArrayList<>();
        for (SandboxOption o : Sandbox.options()) {
            if (o.label.toLowerCase(Locale.ROOT).contains(q) || o.id.toLowerCase(Locale.ROOT).contains(q)) out.add(o);
        }
        return out;
    }

    // ---- drawing ----

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float partialTick) {
        g.fill(0, 0, width, height, 0xB0080A0C);
        int x = x0(), y = y0(), W = w(), H = h();
        KitDraw.panel(g, x, y, W, H);
        hits.clear();
        hovered = null;

        KitDraw.caps(g, font, "Sandbox", x + 10, y + 8, KitDraw.BRIGHT, 1.3f);
        KitDraw.text(g, font, draft != null ? "The new world starts with these" : "This world's settings: the host's changes apply at once",
                x + 10 + Math.round(KitDraw.capsWidth(font, "Sandbox", 1.3f)) + 8, y + 10, KitDraw.DIM, 0.8f);
        // the search box's own frame, in the kit's look
        g.fill(search.getX() - 4, search.getY() - 3, search.getX() + 160, search.getY() + 11, 0x14E8E4DA);
        KitDraw.frame(g, search.getX() - 4, search.getY() - 3, 164, 14, search.isFocused() ? KitDraw.LINE : KitDraw.EDGE);

        // the groups
        int ny = y + 30, total = 0;
        g.fill(x + NAV_W, ny, x + NAV_W + 1, y + H - 28, KitDraw.EDGE);
        boolean searching = !search.getValue().isBlank();
        for (SandboxGroup sg : Sandbox.groups()) {
            List<SandboxOption> opts = Sandbox.options(sg);
            long live = opts.stream().filter(o -> !o.coming()).count();
            boolean on = sg == group && !searching, over = KitDraw.in(mx, my, x + 1, ny, NAV_W - 1, NAV_ROW);
            if (on || over) g.fill(x + 1, ny, x + NAV_W, ny + NAV_ROW, on ? 0x1FE8E4DA : 0x0FE8E4DA);
            if (on) g.fill(x + 1, ny, x + 3, ny + NAV_ROW, KitDraw.LINK);
            KitDraw.caps(g, font, sg.label, x + 8, ny + 4, on ? KitDraw.BRIGHT : KitDraw.DIM, 0.7f);
            KitDraw.textRight(g, font, live + "/" + opts.size(), x + NAV_W - 6, ny + 4, KitDraw.FAINT, 0.7f);
            hits.add(new Hit(x + 1, ny, NAV_W - 1, NAV_ROW, b -> group(sg)));
            ny += NAV_ROW;
            total += opts.size();
        }
        KitDraw.text(g, font, total + " settings", x + 8, ny + 6, KitDraw.FAINT, 0.7f);

        // the settings
        List<SandboxOption> opts = shownOptions();
        int lx = x + NAV_W + 10, top = y + 30, right = x + W - 10, bottom = y + H - 30;
        KitDraw.caps(g, font, searching ? "Search: " + opts.size() + " found" : group.label, lx, top, KitDraw.BRIGHT, 0.9f);
        int listTop = top + 14;
        g.enableScissor(lx - 2, listTop, right + 2, bottom);
        int ry = listTop - Math.round(scroll);
        for (SandboxOption o : opts) {
            if (ry + ROW > listTop && ry < bottom) row(g, o, lx, ry, right, mx, my, listTop, bottom, searching);
            ry += ROW;
        }
        g.disableScissor();
        maxScroll = Math.max(0, opts.size() * ROW - (bottom - listTop));
        scroll = Math.clamp(scroll, 0, maxScroll);
        if (maxScroll > 0) {
            int track = bottom - listTop, knob = Math.max(12, track * track / (track + maxScroll));
            int ky = listTop + Math.round((track - knob) * (scroll / maxScroll));
            g.fill(right + 4, ky, right + 6, ky + knob, KitDraw.LINE);
        }
        if (field.visible && editing != null) {
            g.fill(field.getX() - 3, field.getY() - 3, field.getX() + CONTROL_W - 5, field.getY() + 11, 0xFF15171A);
            KitDraw.frame(g, field.getX() - 3, field.getY() - 3, CONTROL_W - 2, 14, KitDraw.LINK);
        }

        // the footer: what the setting under the mouse does, or the last change
        int fy = y + H - 22;
        g.fill(x + 1, fy - 5, x + W - 1, fy - 4, KitDraw.EDGE);
        String line;
        int colour;
        if (hovered != null) {
            line = (hovered.tooltip == null || hovered.tooltip.isBlank() ? hovered.label : hovered.tooltip)
                    + (hovered.zomboidDefault != null ? "   Zomboid: " + shown(hovered, hovered.zomboidDefault) : "   ZomboidCraft only")
                    + (hovered.coming() && hovered.owner != null ? "   Coming with " + hovered.owner : "")
                    + (hovered.client ? "   Your screen only" : "");
            colour = KitDraw.TEXT;
        } else {
            line = message != null ? message : "Click to change. Scroll a number to step it (Shift for 10x), or click it to type.";
            colour = message != null ? messageColour : KitDraw.DIM;
        }
        int done = Math.round(KitDraw.capsWidth(font, draft != null ? "Use these" : "Done", 0.85f)) + 18;
        g.enableScissor(x + 10, fy - 2, x + W - done - 18, fy + 12);
        KitDraw.text(g, font, line, x + 10, fy + 2, colour, 0.8f);
        g.disableScissor();
        chip(g, draft != null ? "Use these" : "Done", x + W - 10 - done, fy - 1, true, true, mx, my, b -> onClose());
    }

    /** One setting: its name (greyed if its system is still coming), a diamond if changed, and its control. */
    private void row(GuiGraphicsExtractor g, SandboxOption o, int lx, int ry, int right, int mx, int my, int top, int bottom,
                     boolean searching) {
        boolean over = KitDraw.in(mx, my, lx - 2, ry, right - lx + 4, ROW) && my >= top && my < bottom;
        if (over) {
            hovered = o;
            g.fill(lx - 2, ry, right + 2, ry + ROW, 0x0FE8E4DA);
        }
        g.fill(lx, ry + ROW - 1, right, ry + ROW, 0x0DE8E4DA);
        boolean coming = o.coming() && o.kind != SandboxOption.Kind.FIXED;
        int tx = lx;
        if (changed(o)) {
            KitDraw.diamond(g, lx + 2, ry + 8, 2, KitDraw.LINK);
            tx += 8;
        }
        KitDraw.text(g, font, o.label, tx, ry + 5, coming ? KitDraw.FAINT : KitDraw.TEXT, 0.85f);
        int after = tx + Math.round(KitDraw.width(font, o.label, 0.85f)) + 6;
        if (searching) {
            KitDraw.caps(g, font, o.group.label, after, ry + 6, KitDraw.FAINT, 0.6f);
        } else if (coming) {
            KitDraw.caps(g, font, "Coming", after, ry + 6, KitDraw.FAINT, 0.6f);
        }
        int cx = right - CONTROL_W, cy = ry + 3;
        boolean live = ry >= top && ry + ROW <= bottom;
        String v = value(o);
        switch (o.kind) {
            case FIXED -> {
                KitDraw.capsRight(g, font, "Fixed: " + shown(o, v), right, cy + 3, 0xFF7FAEDB, 0.7f);
            }
            case TOGGLE -> {
                boolean onv = "true".equals(v);
                int bx = cx + CONTROL_W - 64;
                chip(g, "On", bx, cy, onv, !coming && live, mx, my, b -> set(o, "true"));
                chip(g, "Off", bx + 32, cy, !onv, !coming && live, mx, my, b -> set(o, "false"));
            }
            case CHOICE -> {
                int i = Math.max(0, o.choices.indexOf(v));
                boolean can = !coming && live && o.choices.size() > 1;
                g.fill(cx, cy, right, cy + 12, can ? 0x14E8E4DA : 0x08E8E4DA);
                KitDraw.frame(g, cx, cy, CONTROL_W, 12, can && KitDraw.in(mx, my, cx, cy, CONTROL_W, 12) ? KitDraw.LINE : KitDraw.EDGE);
                KitDraw.textCentred(g, font, shown(o, v), cx + CONTROL_W / 2f, cy + 3, can ? KitDraw.TEXT : KitDraw.FAINT, 0.75f);
                if (can) {
                    KitDraw.text(g, font, "<", cx + 4, cy + 2, KitDraw.DIM, 0.8f);
                    KitDraw.textRight(g, font, ">", right - 4, cy + 2, KitDraw.DIM, 0.8f);
                    int n = o.choices.size();
                    hits.add(new Hit(cx, cy, CONTROL_W / 2, 12, b -> set(o, o.choices.get(Math.floorMod(i - 1, n)))));
                    hits.add(new Hit(cx + CONTROL_W / 2, cy, CONTROL_W - CONTROL_W / 2, 12,
                            b -> set(o, o.choices.get(Math.floorMod(b == 1 ? i - 1 : i + 1, n)))));
                }
            }
            case NUMBER, TEXT -> {
                boolean can = !coming && live;
                boolean typing = editing == o && field.visible;
                if (!typing) {
                    g.fill(cx, cy, right, cy + 12, can ? 0x14E8E4DA : 0x08E8E4DA);
                    KitDraw.frame(g, cx, cy, CONTROL_W, 12, can && KitDraw.in(mx, my, cx, cy, CONTROL_W, 12) ? KitDraw.LINE : KitDraw.EDGE);
                    String shownV = o.kind == SandboxOption.Kind.TEXT && v.isEmpty() ? "(none)" : v;
                    g.enableScissor(Math.max(cx + 1, lx), Math.max(cy, top), right - 1, Math.min(cy + 12, bottom));
                    KitDraw.text(g, font, shownV, cx + 5, cy + 3, can ? KitDraw.TEXT : KitDraw.FAINT, 0.75f);
                    g.disableScissor();
                    if (o.kind == SandboxOption.Kind.NUMBER && can) {
                        KitDraw.textRight(g, font, num(o.min) + "-" + num(o.max), right - 4, cy + 3, KitDraw.FAINT, 0.65f);
                    }
                    if (can) hits.add(new Hit(cx, cy, CONTROL_W, 12, b -> edit(o, cx, cy)));
                }
            }
        }
    }

    /** A kit chip button; returns its width. A null action draws it inert. */
    private int chip(GuiGraphicsExtractor g, String label, int bx, int by, boolean on, boolean live, int mx, int my, IntConsumer act) {
        int bw = Math.round(KitDraw.capsWidth(font, label, 0.7f)) + 12;
        boolean over = live && act != null && KitDraw.in(mx, my, bx, by, bw, 12);
        g.fill(bx, by, bx + bw, by + 12, on ? (live ? 0xEBE8E4DA : 0x66E8E4DA) : over ? 0x26E8E4DA : 0x08E8E4DA);
        KitDraw.frame(g, bx, by, bw, 12, on ? 0xFFE8E4DA : live ? 0x4DE8E4DA : 0x1FE8E4DA);
        KitDraw.caps(g, font, label, bx + 6, by + 3, on ? 0xFF121314 : live ? KitDraw.TEXT : KitDraw.FAINT, 0.7f);
        if (live && act != null) hits.add(new Hit(bx, by, bw, 12, act));
        return bw;
    }

    private void edit(SandboxOption o, int cx, int cy) {
        editing = o;
        field.setX(cx + 4);
        field.setY(cy + 2);
        field.setValue(value(o));
        field.visible = true;
        setFocused(field);
        field.setFocused(true);
        search.setFocused(false);
    }

    private void commit() {
        if (editing != null) set(editing, field.getValue());
        stopEditing();
    }

    private void stopEditing() {
        editing = null;
        field.visible = false;
        field.setFocused(false);
        setFocused(null);
    }

    // ---- words ----

    /** A choice id or a value, the way the page shows it. */
    private static String shown(SandboxOption o, String v) {
        if (o.kind == SandboxOption.Kind.TOGGLE) return "true".equals(v) ? "On" : "Off";
        if (o.kind == SandboxOption.Kind.CHOICE || o.kind == SandboxOption.Kind.FIXED) return pretty(v);
        return v;
    }

    static String pretty(String id) {
        if (id == null || id.isEmpty()) return "";
        String s = id.replace('_', ' ');
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static String num(double d) {
        return d == Math.rint(d) && Math.abs(d) < 1e9 ? String.valueOf((long) d) : String.valueOf(d);
    }

    /** How far one scroll step moves a number: whole steps for whole settings, finer for multipliers. */
    private static double step(SandboxOption o, String v) {
        double range = o.max - o.min;
        boolean whole = !v.contains(".") && !o.defaultValue().contains(".");
        if (whole) return range <= 100 ? 1 : Math.pow(10, Math.max(0, Math.floor(Math.log10(range / 50))));
        return range <= 2 ? 0.05 : range <= 20 ? 0.1 : 1;
    }

    // ---- input ----

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (field.visible && !KitDraw.in(event.x(), event.y(), field.getX() - 3, field.getY() - 3, CONTROL_W, 14)) {
            commit();
        }
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit h = hits.get(i);
            if (KitDraw.in(event.x(), event.y(), h.x, h.y, h.w, h.h)) {
                h.act.accept(event.button());
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        // over a number: step it
        if (hovered != null && hovered.kind == SandboxOption.Kind.NUMBER && !hovered.coming()
                && mx >= x0() + w() - 10 - CONTROL_W && editing == null) {
            String v = value(hovered);
            double d;
            try {
                d = Double.parseDouble(v);
            } catch (NumberFormatException e) {
                return true;
            }
            double st = step(hovered, v) * (hasShiftDown() ? 10 : 1);
            d = Math.clamp(Math.round((d + Math.signum(sy) * st) / st) * st, hovered.min, hovered.max);
            set(hovered, num(Math.round(d * 1000) / 1000.0));
            return true;
        }
        scroll = Math.clamp(scroll - (float) sy * ROW * 2, 0, maxScroll);
        return true;
    }

    private static boolean hasShiftDown() {
        var w = Minecraft.getInstance().getWindow();
        return InputConstants.isKeyDown(w, InputConstants.KEY_LSHIFT) || InputConstants.isKeyDown(w, InputConstants.KEY_RSHIFT);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int k = event.key();
        if (field.visible) {
            if (k == InputConstants.KEY_RETURN || k == InputConstants.KEY_NUMPADENTER) {
                commit();
                return true;
            }
            if (k == InputConstants.KEY_ESCAPE) {
                stopEditing();
                return true;
            }
        }
        return super.keyPressed(event);
    }

    @Override
    public void onClose() {
        if (draft != null) {
            Map<String, String> overrides = new HashMap<>();
            for (SandboxOption o : Sandbox.options()) {
                String v = draft.get(o.id);
                if (v != null && !v.equals(o.defaultValue())) overrides.put(o.id, v);
            }
            Sandbox.forNewWorld("apocalypse", overrides);
        }
        minecraft.gui.setScreen(parent);
    }
}
