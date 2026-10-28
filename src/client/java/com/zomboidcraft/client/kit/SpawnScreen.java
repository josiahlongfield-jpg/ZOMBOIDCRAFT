package com.zomboidcraft.client.kit;

import com.mojang.blaze3d.platform.InputConstants;
import com.zomboidcraft.ZomboidCraft;
import com.zomboidcraft.spawn.SpawnNet;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * Where you wake up: the county's towns, cities and districts down the left, the map in the middle, and what you'd be
 * walking into on the right. It stands in front of the world until you've chosen and the server has found you a way
 * in; Escape goes to the game menu and comes back.
 */
public final class SpawnScreen extends Screen {
    private static final Identifier MAP = ZomboidCraft.id("textures/gui/spawn/county.png");
    private static final String[] DANGER = {"", "Low", "Medium", "High", "Very high", "Extreme"};
    private static final int[] DANGER_COLOUR = {0, KitDraw.GOOD, 0xFFE0C25E, KitDraw.WARN, KitDraw.BAD, KitDraw.BAD};
    private static final int LIST_W = 130, CARD_W = 150, ROW = 11;

    /** What the server last offered, kept until you've woken up (the picker reopens over the game menu). */
    private static SpawnNet.Open offered;
    /** Set once you've chosen: the place being searched. */
    private static String searching;

    private final SpawnNet.Open open;
    private String selected;
    private int scroll;
    private long opened;
    private final List<int[]> rows = new ArrayList<>();
    private final List<String> rowIds = new ArrayList<>();
    private final List<int[]> pins = new ArrayList<>();
    private final List<String> pinIds = new ArrayList<>();
    private int[] wake, random;
    private final List<int[]> friendRows = new ArrayList<>();
    private int listTop, listBottom, contentHeight;

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(SpawnNet.Open.TYPE, (payload, context) -> context.client().execute(() -> {
            offered = payload;
            searching = null;
            show(context.client());
        }));
        ClientPlayNetworking.registerGlobalReceiver(SpawnNet.Status.TYPE, (payload, context) -> context.client().execute(() -> {
            Minecraft mc = context.client();
            if (payload.done()) {
                offered = null;
                searching = null;
                if (mc.gui.screen() instanceof SpawnScreen) mc.gui.setScreen(null);
            } else {
                searching = payload.text();
            }
        }));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            offered = null;
            searching = null;
        });
        // back from the game menu, or the loading screen gone: the picker comes back until you've woken
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            if (offered != null && mc.level != null && mc.gui.screen() == null) show(mc);
        });
    }

    private static void show(Minecraft mc) {
        if (!(mc.gui.screen() instanceof SpawnScreen) && (mc.gui.screen() == null || mc.level != null)) {
            mc.gui.setScreen(new SpawnScreen(offered));
        }
    }

    public SpawnScreen(SpawnNet.Open open) {
        super(Component.literal("Choose where you wake up"));
        this.open = open;
        this.selected = open.places().isEmpty() ? null : open.places().get(Math.min(open.places().size() - 1, 14)).id();
        for (SpawnNet.Place p : open.places()) {
            if (p.id().equals("seabright")) selected = p.id();
        }
    }

    @Override
    protected void init() {
        opened = System.currentTimeMillis();
    }

    @Override
    public boolean isPauseScreen() {
        return false;   // the server keeps running while you choose (it has to, to find you a way in)
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    private SpawnNet.Place place(String id) {
        for (SpawnNet.Place p : open.places()) {
            if (p.id().equals(id)) return p;
        }
        return null;
    }

    // ------------------------------------------------------------------ drawing

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float partialTick) {
        g.fill(0, 0, width, height, 0xFF0B0C0E);
        int pad = 14, top = 42, bottom = height - 22;
        KitDraw.caps(g, font, open.again() ? "You died · a new survivor" : "Grayhaven County · new survivor", pad, 10, KitDraw.DIM, 0.8f);
        KitDraw.caps(g, font, "Choose where you wake up", pad, 20, KitDraw.BRIGHT, 1.6f);
        String sub = "You start inside a building with none of the dead in it.";
        KitDraw.textRight(g, font, sub, width - pad, 26, KitDraw.DIM, 0.8f);

        int cardX = width - pad - CARD_W;
        int listX = pad;
        int mapLeft = listX + LIST_W + 10, mapRight = cardX - 10;
        int size = Math.max(60, Math.min(mapRight - mapLeft, bottom - top));
        int mapX = mapLeft + (mapRight - mapLeft - size) / 2, mapY = top;

        // friends already out there sit under the list
        int friendsH = open.friends().isEmpty() ? 0 : 12 + Math.min(4, open.friends().size()) * 16;
        list(g, listX, top, bottom - friendsH, mx, my);
        friends(g, listX, bottom - friendsH + 6, mx, my);
        map(g, mapX, mapY, size, mx, my);
        card(g, cardX, top, bottom, mx, my);

        int hx = pad;
        hx += KitDraw.hint(g, font, "LMB", "Choose a place", hx, height - 15);
        hx += KitDraw.hint(g, font, "Enter", "Wake up here", hx, height - 15);
        KitDraw.hint(g, font, "Esc", "Game menu", hx, height - 15);

        if (searching != null) {
            g.fill(0, 0, width, height, 0xD9080A0C);
            int dots = (int) (System.currentTimeMillis() / 400 % 4);
            KitDraw.capsCentred(g, font, "Finding a way in" + ".".repeat(dots), width / 2f, height / 2f - 10, KitDraw.BRIGHT, 1.4f);
            KitDraw.textCentred(g, font, searching, width / 2f, height / 2f + 6, KitDraw.DIM, 1f);
        }
    }

    private void list(GuiGraphicsExtractor g, int x, int top, int bottom, int mx, int my) {
        rows.clear();
        rowIds.clear();
        listTop = top;
        listBottom = bottom;
        Map<String, List<SpawnNet.Place>> groups = new LinkedHashMap<>();
        for (SpawnNet.Place p : open.places()) groups.computeIfAbsent(p.group(), k -> new ArrayList<>()).add(p);
        int y = top - scroll;
        g.enableScissor(x, top, x + LIST_W, bottom);
        for (Map.Entry<String, List<SpawnNet.Place>> e : groups.entrySet()) {
            if (y > top - scroll) y += 4;
            KitDraw.heading(g, font, e.getKey(), x, y + 2, LIST_W, false);
            y += 12;
            for (SpawnNet.Place p : e.getValue()) {
                boolean sel = p.id().equals(selected);
                boolean hover = mx >= x && mx < x + LIST_W && my >= y && my < y + ROW && my >= top && my < bottom;
                if (sel || hover) {
                    g.fill(x, y, x + LIST_W, y + ROW, sel ? 0x1FE8E4DA : 0x0DE8E4DA);
                    KitDraw.frame(g, x, y, LIST_W, ROW, sel ? 0xD9F2EFE8 : 0x73E8E4DA);
                }
                KitDraw.text(g, font, p.name(), x + 4, y + 2, sel ? KitDraw.BRIGHT : KitDraw.TEXT, 0.8f);
                KitDraw.capsRight(g, font, DANGER[clamp(p.danger())], x + LIST_W - 4, y + 3, DANGER_COLOUR[clamp(p.danger())], 0.65f);
                rows.add(new int[]{x, y, LIST_W, ROW});
                rowIds.add(p.id());
                y += ROW + 1;
            }
        }
        g.disableScissor();
        contentHeight = y + scroll - top;
    }

    private void map(GuiGraphicsExtractor g, int x, int y, int size, int mx, int my) {
        pins.clear();
        pinIds.clear();
        g.blit(RenderPipelines.GUI_TEXTURED, MAP, x, y, 0, 0, size, size, size, size);
        KitDraw.frame(g, x - 1, y - 1, size + 2, size + 2, KitDraw.EDGE);
        SpawnNet.Place sel = place(selected);
        for (SpawnNet.Place p : open.places()) {
            int px = x + Math.round((p.x() + 8192) / 16384f * size), py = y + Math.round((p.z() + 8192) / 16384f * size);
            boolean hover = Math.abs(mx - px) <= 4 && Math.abs(my - py) <= 4;
            if (p == sel) continue;
            KitDraw.diamond(g, px, py, hover ? 4 : 3, 0xE60B0C0E);
            KitDraw.diamond(g, px, py, hover ? 3 : 2, DANGER_COLOUR[clamp(p.danger())]);
            pins.add(new int[]{px, py});
            pinIds.add(p.id());
        }
        if (sel != null) {
            int px = x + Math.round((sel.x() + 8192) / 16384f * size), py = y + Math.round((sel.z() + 8192) / 16384f * size);
            float pulse = (float) (0.5 + 0.5 * Math.sin((System.currentTimeMillis() - opened) / 300.0));
            KitDraw.diamond(g, px, py, 6 + Math.round(pulse * 2), 0x40F2EFE8);
            KitDraw.diamond(g, px, py, 5, 0xFF0B0C0E);
            KitDraw.diamond(g, px, py, 4, KitDraw.BRIGHT);
            float w = KitDraw.capsWidth(font, sel.name(), 0.9f);
            float lx = px + 8 + w > x + size ? px - 8 - w : px + 8;
            g.fill(Math.round(lx - 2), py - 5, Math.round(lx + w + 2), py + 5, 0xB30B0C0E);
            KitDraw.caps(g, font, sel.name(), lx, py - 3, KitDraw.BRIGHT, 0.9f);
        }
        KitDraw.caps(g, font, "Dashed red line: the county fence", x + 4, y + size - 8, KitDraw.FAINT, 0.6f);
    }

    private void card(GuiGraphicsExtractor g, int x, int top, int bottom, int mx, int my) {
        SpawnNet.Place p = place(selected);
        if (p == null) return;
        int w = CARD_W, in = 8, tw = w - in * 2;
        // measure, then draw on the panel
        int y = top + in;
        int noteH = lines(p.note(), tw) * 9;
        int h = 10 + 16 + 8 + noteH + 8 + 16 + 4 + facts(p, tw) + 10 + 18 + 4 + 16 + in;
        KitDraw.panel(g, x, top, w, h);
        KitDraw.caps(g, font, p.kind(), x + in, y, KitDraw.DIM, 0.7f);
        y += 9;
        KitDraw.caps(g, font, p.name(), x + in, y, KitDraw.BRIGHT, 1.3f);
        y += 15;
        g.fill(x, y, x + w, y + 1, KitDraw.EDGE);
        y += 7;
        y += KitDraw.wrap(g, font, p.note(), x + in, y, tw, 0xD9E8E4DA);
        y += 7;
        int d = clamp(p.danger());
        KitDraw.caps(g, font, "Danger · " + DANGER[d], x + in, y, KitDraw.DIM, 0.7f);
        y += 8;
        int seg = (tw - 8) / 5;
        for (int i = 1; i <= 5; i++) {
            int sx = x + in + (i - 1) * (seg + 2);
            g.fill(sx, y, sx + seg, y + 2, i <= d ? DANGER_COLOUR[d] : 0x24E8E4DA);
        }
        y += 8;
        y = fact(g, "Zombies", p.zombies(), x + in, y, tw);
        y = fact(g, "You wake in", p.wake(), x + in, y, tw);
        y = fact(g, "Worth looting", p.loot(), x + in, y, tw);
        y += 4;
        wake = new int[]{x + in, y, tw, 16};
        boolean hw = in(wake, mx, my);
        g.fill(x + in, y, x + in + tw, y + 16, hw ? 0xFFFFFFFF : 0xFFE8E4DA);
        KitDraw.capsCentred(g, font, "Wake up here", x + w / 2f, y + 5, 0xFF1D1B16, 0.9f);
        y += 20;
        random = new int[]{x + in, y, tw, 14};
        KitDraw.frame(g, x + in, y, tw, 14, in(random, mx, my) ? 0xFFF2EFE8 : 0x73E8E4DA);
        KitDraw.capsCentred(g, font, "Pick for me", x + w / 2f, y + 4, KitDraw.TEXT, 0.85f);

    }

    private void friends(GuiGraphicsExtractor g, int x, int y, int mx, int my) {
        friendRows.clear();
        if (open.friends().isEmpty()) return;
        KitDraw.heading(g, font, "Friends in this world", x, y, LIST_W, false);
        y += 10;
        for (int i = 0; i < Math.min(4, open.friends().size()); i++) {
            SpawnNet.Friend f = open.friends().get(i);
            int[] r = {x, y, LIST_W, 14};
            KitDraw.frame(g, x, y, LIST_W, 14, in(r, mx, my) ? 0xFFF2EFE8 : 0x4DE8E4DA);
            KitDraw.text(g, font, "Near " + f.name(), x + 4, y + 3, KitDraw.TEXT, 0.8f);
            KitDraw.textRight(g, font, f.where(), x + LIST_W - 4, y + 3, KitDraw.DIM, 0.7f);
            friendRows.add(r);
            y += 16;
        }
    }

    private int facts(SpawnNet.Place p, int tw) {
        return factH(p.zombies(), tw) + factH(p.wake(), tw) + factH(p.loot(), tw);
    }

    private int factH(String text, int tw) {
        return 8 + lines(text, tw) * 9 + 3;
    }

    private int fact(GuiGraphicsExtractor g, String label, String text, int x, int y, int tw) {
        KitDraw.caps(g, font, label, x, y, KitDraw.DIM, 0.7f);
        y += 8;
        y += KitDraw.wrap(g, font, text, x, y, tw, KitDraw.TEXT);
        return y + 3;
    }

    private int lines(String s, int width) {
        return Math.max(1, font.split(com.zomboidcraft.client.hud.Hud.text(s), width).size());
    }

    private static int clamp(int danger) {
        return Math.max(1, Math.min(5, danger));
    }

    private static boolean in(int[] r, double mx, double my) {
        return r != null && mx >= r[0] && mx < r[0] + r[2] && my >= r[1] && my < r[1] + r[3];
    }

    /** For the UI test: where a place's row is on screen, and what's chosen. */
    public int[] rowFor(String id) {
        int i = rowIds.indexOf(id);
        return i < 0 ? null : rows.get(i);
    }

    public String selected() {
        return selected;
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (searching != null) return true;
        double mx = event.x(), my = event.y();
        for (int i = 0; i < rows.size(); i++) {
            if (in(rows.get(i), mx, my) && my >= listTop && my < listBottom) {
                selected = rowIds.get(i);
                return true;
            }
        }
        for (int i = 0; i < pins.size(); i++) {
            int[] p = pins.get(i);
            if (Math.abs(mx - p[0]) <= 4 && Math.abs(my - p[1]) <= 4) {
                selected = pinIds.get(i);
                return true;
            }
        }
        if (in(wake, mx, my)) {
            choose(SpawnNet.Pick.PLACE, selected);
            return true;
        }
        if (in(random, mx, my)) {
            choose(SpawnNet.Pick.ANY, "");
            return true;
        }
        for (int i = 0; i < friendRows.size(); i++) {
            if (in(friendRows.get(i), mx, my)) {
                choose(SpawnNet.Pick.FRIEND, open.friends().get(i).id());
                return true;
            }
        }
        return true;
    }

    private void choose(int kind, String id) {
        if (id == null) return;
        SpawnNet.Place p = place(id);
        searching = kind == SpawnNet.Pick.PLACE && p != null ? p.name() : "";
        ClientPlayNetworking.send(new SpawnNet.Pick(kind, id));
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        int max = Math.max(0, contentHeight - (listBottom - listTop));
        scroll = Math.max(0, Math.min(max, scroll - (int) Math.round(sy * ROW * 2)));
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == InputConstants.KEY_ESCAPE) {
            minecraft.gui.setScreen(new net.minecraft.client.gui.screens.PauseScreen(true));
            return true;
        }
        if (searching == null && (event.key() == InputConstants.KEY_RETURN || event.key() == InputConstants.KEY_NUMPADENTER)) {
            choose(SpawnNet.Pick.PLACE, selected);
            return true;
        }
        if (searching == null && (event.key() == InputConstants.KEY_DOWN || event.key() == InputConstants.KEY_UP)) {
            List<SpawnNet.Place> all = open.places();
            for (int i = 0; i < all.size(); i++) {
                if (all.get(i).id().equals(selected)) {
                    int j = Math.floorMod(i + (event.key() == InputConstants.KEY_DOWN ? 1 : -1), all.size());
                    selected = all.get(j).id();
                    break;
                }
            }
            return true;
        }
        return true;
    }
}
