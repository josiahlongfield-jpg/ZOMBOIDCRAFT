package com.zomboidcraft.client.hud;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.InputConstants;
import com.zomboidcraft.ZomboidCraft;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import org.jetbrains.annotations.Nullable;

/**
 * Endless Night's main menu (Josiah 2026-11-08 23:49 "a main menu the same as halo reach", list agreed 2026-11-09 00:43,
 * mock-up main-menu-v1 approved 00:49): the first screen, in place of Minecraft's title screen, laid out as Reach's
 * lobby: the picked item's picture in a framed box top left, the wings and a big title, MAIN MENU running down the side,
 * "Ready" over the list (values on the right, Reach's highlight bar), the description under it, the squad roster top
 * right, button prompts along the bottom, and your soldier standing in the rain. Drawn from the {@code charsel} kit on
 * this computer only; without it Minecraft's title screen stays. F1 opens Minecraft's own title screen until you next
 * leave a world.
 */
public final class MainMenuScreen extends Screen {
    private static final int WHITE = argb(236, 240, 244), DIM = argb(176, 184, 192), ROW = argb(205, 210, 216), VALUE = argb(212, 216, 220);
    private static final int SOON = argb(120, 126, 134);
    /** Minecraft's own title screen was asked for (F1): leave it alone until the player next leaves a world. */
    private static boolean bypass;

    record Item(String name, String icon, String desc, boolean ready) {}

    private static final List<Item> ITEMS = List.of(
            new Item("CONTINUE", "continue", "Pick up where you left off.", true),
            new Item("NEW SOLDIER", "new", "Choose your side and make a new soldier: Noble Team or a Tarkov operator.", true),
            new Item("ARMORY", "armory", "Change your Spartan's armour, colours and emblem.", true),
            new Item("FIREFIGHT", "firefight", "Hold out against waves of the Covenant-led horde on a Reach map.", false),
            new Item("MULTIPLAYER", "multiplayer", "Join your friends' server.", true),
            new Item("INTEL", "intel", "Field notes on the Covenant, your weapons and Noble Team.", false),
            new Item("SETTINGS", "settings", "Video, sound, controls and HUD.", true),
            new Item("QUIT", "quit", "Leave Endless Night.", true));

    private int sel;
    private @Nullable HudKit k;
    private long last = System.nanoTime();
    private float rainT;
    private final List<float[]> hits = new java.util.ArrayList<>();
    private double lastMx = Double.NaN, lastMy = Double.NaN;
    private final @Nullable LastWorld lastWorld = LastWorld.read();
    private final JsonObject soldier = readSoldier();
    private final MenuSlides slides = new MenuSlides();
    /** Which of your soldiers stands in the gap: false your Spartan, true your Tarkov operator (Q switches). */
    private boolean operator = "operator".equals(soldier.has("side") ? soldier.get("side").getAsString() : "");
    /** The left panel sits this far left of Reach's own spot, to leave room for the soldier (Josiah 00:54). */
    private static final float L = -150;
    /** MY SQUAD's left edge: 100 in from the right, as the menu is from the left (Josiah 00:58); the soldier between. */
    private static final float SQUAD_X = 1340, FIG_X = 1080;

    public MainMenuScreen() {
        super(Component.literal("Endless Night"));
    }

    /** Whether the menu can be drawn here (its art is on this computer). */
    public static boolean ready() {
        return HudKit.present(SoldierScreen.KIT);
    }

    /** Puts the menu in place of Minecraft's title screen, and remembers the world you last played. */
    public static void register() {
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            if (screen instanceof TitleScreen && !bypass && ready()) client.execute(() -> {
                if (client.gui.screen() == screen) client.gui.setScreen(new MainMenuScreen());
            });
        });
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            MinecraftServer server = client.getSingleplayerServer();
            if (server != null) LastWorld.save(server);
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> bypass = false);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    // ------------------------------------------------------------------ drawing

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float partialTick) {
        long now = System.nanoTime();
        float dt = Math.min(0.1f, (now - last) / 1e9f);
        last = now;
        rainT += dt;
        var window = Minecraft.getInstance().getWindow();
        int ww = window.getWidth(), wh = window.getHeight();
        k = HudKit.get(SoldierScreen.KIT);
        HudKit.pixels(g);
        g.fill(0, 0, ww, wh, 0xFF05070A);
        if (k == null) {
            HudKit.end(g);
            return;
        }
        k.begin();
        hits.clear();
        double lx = (mx * window.getGuiScale() - k.ox) / k.s, ly = (my * window.getGuiScale() - k.oy) / k.s;
        boolean moved = mx != lastMx || my != lastMy;
        lastMx = mx;
        lastMy = my;

        // the Reach world's landmarks one after another (or the kit's street while there are none), darker toward the
        // menu side, and the rain
        if (!slides.draw(g, ww, wh, dt, HudKit.argb(255, 158, 158, 158))) {
            k.picture(g, "mm_bg", "mm/bg.jpg", 0, 0, ww, wh, ww, wh, HudKit.argb(255, 158, 158, 158));
        }
        k.picture(g, "mm_shade", "mm/shade.png", 0, 0, ww, wh, 480, 270, 0xFFFFFFFF);
        rain(g, ww, wh, 0.55f);
        // your soldier, standing in the gap between the menu and the squad (main-menu-v3b, approved 01:00)
        shadow(g, FIG_X, 985, 360, 34);
        String fig = operator && k.size("fig/menu_op.png")[1] > 0 ? "fig/menu_op.png" : "fig/menu_six.png";
        figure(g, fig, "mm_fig_" + fig, FIG_X, 1000, 760);
        rain(g, ww, wh, 0.35f);

        // the picked item's picture in its frame, with its icon
        Item it = ITEMS.get(sel);
        float bx = 250 + L, by = 68, bw = 700, bh = 262;
        k.cover(g, "mm_prev", "mm/bg.jpg", bx, by, bw, bh, HudKit.argb(255, 218, 218, 218));
        outline(g, bx - 2, by - 2, bw + 4, bh + 4, 3, 0xFF0A0A0C);
        outline(g, bx - 4, by - 4, bw + 8, bh + 8, 2, HudKit.argb(220, 225, 225, 225));
        k.image(g, "mm_icon_" + it.icon(), "mm/icon_" + it.icon() + ".png", bx + 14, by + 22, 140, 140, 0xFFFFFFFF);

        // the wings, the title and the line under it, MAIN MENU down the side
        k.image(g, "mm/icon_wings.png", 4, 342, 100, 100, HudKit.argb(200, 255, 255, 255));
        k.text(g, "mm_title", "light_84", "ENDLESS NIGHT", 262 + L, 342, 0, 0, true, argb(225, 228, 232), 1);
        k.text(g, "mm_sub", "med_26", subline(), 264 + L, 455, 0, 0, true, DIM, 0);
        int[] v = k.size("mm/mainmenu_v.png");
        if (v[1] > 0) k.image(g, "mm/mainmenu_v.png", 172 + L, 470, v[0] / 2f, v[1] / 2f, HudKit.argb(150, 120, 122, 126));

        // Ready, the list and the description
        k.text(g, "mm_ready", "med_28", "Ready", 264 + L, 548, 0, 0, true, WHITE, 0);
        fadeline(g, 250 + L, 588, 720, 90);
        float y = 604;
        for (int i = 0; i < ITEMS.size(); i++) {
            Item item = ITEMS.get(i);
            if (moved && inside(lx, ly, 252 + L, y - 3, 700, 36)) sel = i;
            hits.add(new float[] {252 + L, y - 3, 952 + L, y + 33, i});
            if (i == sel) highlight(g, 252 + L, y - 3, 700, 36);
            k.text(g, "mm_row" + i, "med_25", item.name(), 266 + L, y, 0, 0, true, !item.ready() ? SOON : i == sel ? WHITE : ROW, 1);
            String value = value(i);
            if (!value.isEmpty()) k.text(g, "mm_val" + i + value, "med_24", value, 940 + L, y, 1, 0, true, item.ready() ? VALUE : SOON, 1);
            y += 38;
        }
        fadeline(g, 250 + L, y + 6, 720, 90);
        k.text(g, "mm_desc" + sel, "med_25", description(sel), 264 + L, y + 20, 0, 0, true, DIM, 0);

        squad(g);
        prompts(g);
        toggle(g, lx, ly, moved);
        HudKit.end(g);
    }

    private String subline() {
        String side = soldier.has("side") ? soldier.get("side").getAsString() : "spartan";
        return name() + "  ·  " + (side.equals("operator") ? "Operator" : "Spartan") + "  ·  Personal game on this PC.";
    }

    private String name() {
        if (soldier.has("noble")) {
            String n = soldier.get("noble").getAsString();
            for (SoldierScreen.Noble noble : SoldierScreen.NOBLES) {
                if (noble.id().equals(n)) return n.equals("six") ? "Noble Six" : SoldierScreen.title(noble.name());
            }
        }
        if (soldier.has("faction")) return soldier.get("faction").getAsString().toUpperCase(java.util.Locale.ROOT) + " operator";
        return "Noble Six";
    }

    private String value(int i) {
        return switch (ITEMS.get(i).name()) {
            case "CONTINUE" -> lastWorld == null ? "NO SAVED GAME" : lastWorld.name().toUpperCase(java.util.Locale.ROOT);
            case "ARMORY" -> "NOBLE SIX";
            case "FIREFIGHT", "INTEL" -> "COMING SOON";
            case "MULTIPLAYER" -> "FRIENDS SERVER";
            default -> "";
        };
    }

    private String description(int i) {
        Item it = ITEMS.get(i);
        if (it.name().equals("CONTINUE")) {
            return lastWorld == null ? "No saved game yet: pick New Soldier to start one." : "Pick up where you left off in " + lastWorld.name() + ".";
        }
        return it.ready() ? it.desc() : it.desc() + " Coming soon.";
    }

    /** Reach's MY PARTY box: the header, the lobby line, your own row in your armour's colour with your emblem. */
    private void squad(GuiGraphicsExtractor g) {
        float px = SQUAD_X;
        k.fill(g, px, 96, px + 400, 134, HudKit.argb(235, 40, 40, 42));
        k.text(g, "mm_sq", "med_26", "MY SQUAD", px + 8, 101, 0, 0, true, WHITE, 0);
        k.fill(g, px + 30, 138, px + 480, 180, HudKit.argb(200, 70, 70, 72));
        k.text(g, "mm_sql", "med_26", "In Main Menu", px + 60, 144, 0, 0, true, WHITE, 0);
        k.text(g, "mm_sqn", "med_24", "1/16", px + 468, 146, 1, 0, true, DIM, 0);
        int[] c = colour();
        k.fill(g, px + 52, 184, px + 480, 230, HudKit.argb(240, c[0], c[1], c[2]));
        k.text(g, "mm_me" + name(), "med_28", name(), px + 80, 191, 0, 0, true, WHITE, 0);
        k.text(g, "mm_tag", "med_24", "B312", px + 360, 194, 0, 0, true, argb(230, 232, 220), 0);
        k.image(g, "mm_wings_s", "mm/icon_wings.png", px + 432, 187, 40, 40, 0xFFFFFFFF);
        k.image(g, "mm/spinner.png", px + 4, 186, 40, 40, HudKit.argb(230, 230, 230, 230));
    }

    /** Your armour's primary colour (the soldier file), olive until you've picked one. */
    private int[] colour() {
        JsonObject armour = HudKit.obj(soldier, "armour");
        if (armour != null && armour.has("primary colour")) {
            int i = armour.get("primary colour").getAsInt();
            if (i >= 0 && i < SoldierScreen.COLOURS.length) return SoldierScreen.COLOURS[i];
        }
        return new int[] {95, 100, 60};
    }

    /** SPARTAN | OPERATOR under the squad's right edge, Q (or a click) switching who stands in the gap. */
    private void toggle(GuiGraphicsExtractor g, double lx, double ly, boolean moved) {
        String[] labels = {"SPARTAN", "OPERATOR"};
        float[] ws = new float[2];
        float tw = 6;
        for (int i = 0; i < 2; i++) tw += ws[i] = k.measure("med_24", labels[i], 2) + 44;
        float right = SQUAD_X + 480, x0 = right - tw, y = 1010;
        k.fill(g, x0, y, right, y + 44, HudKit.argb(200, 20, 22, 26));
        outline(g, x0, y, tw, 44, 2, HudKit.argb(160, 200, 205, 210));
        float x = x0 + 3;
        for (int i = 0; i < 2; i++) {
            boolean on = (i == 1) == operator;
            hits.add(new float[] {x, y, x + ws[i], y + 44, 100 + i});
            if (on) highlight(g, x, y + 3, ws[i], 38);
            k.text(g, "mm_tg" + i, "med_24", labels[i], x + ws[i] / 2, y + 9, 0.5f, 0, true, on ? WHITE : argb(150, 156, 164), 2);
            x += ws[i];
        }
        float kw = k.measure("med_18", "Q", 0) + 20, kx = x0 - kw - 12;
        k.nine(g, "mm/pill.png", Math.round(k.px(kx)), Math.round(k.py(y + 7)), Math.round(kw * k.s), Math.round(30 * k.s), 15, 15,
                HudKit.argb(230, 200, 200, 200));
        k.text(g, "mm_tq", "med_18", "Q", kx + 10, y + 10, 0, 0, true, argb(30, 30, 30), 0);
    }

    private void prompts(GuiGraphicsExtractor g) {
        float x = 262 + L, y = 1012;
        String[][] keys = {{"ENTER", "Select"}, {"ESC", "Back"}, {"TAB", "Change Squad"}, {"F1", "Menu"}};
        int[][] cols = {{90, 170, 70}, {190, 60, 60}, {210, 180, 50}, {200, 200, 200}};
        for (int i = 0; i < keys.length; i++) {
            float kw = k.measure("med_18", keys[i][0], 0) + 22;
            k.nine(g, "mm/pill.png", Math.round(k.px(x)), Math.round(k.py(y)), Math.round(kw * k.s), Math.round(32 * k.s), 16, 16,
                    HudKit.argb(230, cols[i][0], cols[i][1], cols[i][2]));
            k.text(g, "mm_k" + i, "med_18", keys[i][0], x + 11, y + 5, 0, 0, true, i == 3 ? argb(30, 30, 30) : 0xFFFFFFFF, 0);
            x += kw + 8;
            x += k.text(g, "mm_kl" + i, "med_26", keys[i][1], x, y + 2, 0, 0, true, WHITE, 0) + 34;
        }
    }

    /** Rain streaks falling over the whole window (a fixed set of drops, each falling on its own loop). */
    private void rain(GuiGraphicsExtractor g, int ww, int wh, float strength) {
        java.util.Random r = new java.util.Random(strength > 0.5f ? 3 : 7);
        int drops = Math.round(450 * strength);
        float px = ww / 1920f;
        for (int i = 0; i < drops; i++) {
            float x0 = r.nextFloat() * ww * 1.1f, len = (18 + r.nextFloat() * 28) * px, speed = (1400 + r.nextFloat() * 700) * px;
            float y = (r.nextFloat() * (wh + 200) + rainT * speed) % (wh + 200) - 100;
            float x = x0 - y * 0.18f;
            int a = Math.round((25 + r.nextFloat() * 45) * strength * 1.6f);
            int col = HudKit.argb(Math.min(255, a), 200, 215, 230);
            int steps = 3;
            for (int s = 0; s < steps; s++) {
                float sy = y + len * s / steps, sx = x - len * 0.18f * s / steps;
                g.fill(Math.round(sx), Math.round(sy), Math.round(sx) + 1, Math.round(sy + len / steps), col);
            }
        }
    }

    private void figure(GuiGraphicsExtractor g, String file, String slot, float cx, float bottom, float h) {
        int[] src = k.size(file);
        if (src[1] == 0) return;
        float w = src[0] * h / src[1];
        k.image(g, slot, file, cx - w / 2, bottom - h, w, h, 0xFFFFFFFF);
    }

    private void shadow(GuiGraphicsExtractor g, float cx, float cy, float w, float h) {
        float tw = w * 340 / 300f, th = h * 80 / 40f;
        k.image(g, "mm_sh", "ui/shadow.png", cx - tw / 2, cy - th / 2, tw, th, 0xFFFFFFFF);
    }

    private void fadeline(GuiGraphicsExtractor g, float x, float y, float w, int alpha) {
        k.image(g, "mm_fade" + Math.round(w), "ui/fadeline.png", x, y, w, 2, HudKit.argb(alpha, 255, 255, 255));
    }

    private void highlight(GuiGraphicsExtractor g, float x, float y, float w, float h) {
        k.image(g, "mm_hl", "ui/highlight.png", x, y, w, h, HudKit.argb(150, 175, 205, 230));
        k.fill(g, x, y, x + 4, y + h, argb(225, 240, 255));
    }

    private void outline(GuiGraphicsExtractor g, float x, float y, float w, float h, float t, int colour) {
        k.fill(g, x, y, x + w, y + t, colour);
        k.fill(g, x, y + h - t, x + w, y + h, colour);
        k.fill(g, x, y + t, x + t, y + h - t, colour);
        k.fill(g, x + w - t, y + t, x + w, y + h - t, colour);
    }

    private static boolean inside(double lx, double ly, float x, float y, float w, float h) {
        return lx >= x && lx < x + w && ly >= y && ly < y + h;
    }

    private static int argb(int r, int g, int b) {
        return HudKit.argb(255, r, g, b);
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (k == null) return true;
        var window = Minecraft.getInstance().getWindow();
        double lx = (event.x() * window.getGuiScale() - k.ox) / k.s, ly = (event.y() * window.getGuiScale() - k.oy) / k.s;
        for (float[] h : hits) {
            if (lx >= h[0] && lx < h[2] && ly >= h[1] && ly < h[3]) {
                if (h[4] >= 100) {
                    operator = h[4] == 101;
                    return true;
                }
                sel = (int) h[4];
                activate();
                return true;
            }
        }
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.key();
        if (key == InputConstants.KEY_UP || key == InputConstants.KEY_W) sel = Math.floorMod(sel - 1, ITEMS.size());
        else if (key == InputConstants.KEY_DOWN || key == InputConstants.KEY_S) sel = Math.floorMod(sel + 1, ITEMS.size());
        else if (key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER || key == InputConstants.KEY_SPACE) activate();
        else if (key == InputConstants.KEY_Q) operator = !operator;
        else if (key == InputConstants.KEY_F1) {
            bypass = true;
            minecraft.gui.setScreen(new TitleScreen());
        }
        return true;
    }

    private void activate() {
        switch (ITEMS.get(sel).name()) {
            case "CONTINUE" -> {
                if (lastWorld != null && lastWorld.exists()) minecraft.createWorldOpenFlows().openWorld(lastWorld.id(), () -> minecraft.gui.setScreen(this));
                else minecraft.gui.setScreen(new SelectWorldScreen(this));
            }
            case "NEW SOLDIER" -> minecraft.gui.setScreen(new SelectWorldScreen(this));
            case "ARMORY" -> minecraft.gui.setScreen(SoldierScreen.armoryFrom(this));
            case "MULTIPLAYER" -> minecraft.gui.setScreen(new JoinMultiplayerScreen(this));
            case "SETTINGS" -> minecraft.gui.setScreen(new OptionsScreen(this, minecraft.options));
            case "QUIT" -> minecraft.stop();
            default -> {}      // Firefight and Intel aren't built yet
        }
    }

    // ------------------------------------------------------------------ files

    private static JsonObject readSoldier() {
        Path file = FabricLoader.getInstance().getConfigDir().resolve("zomboidcraft/soldier.json");
        try {
            if (Files.isRegularFile(file)) return JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (Exception e) {
            ZomboidCraft.LOGGER.warn("Couldn't read {}", file, e);
        }
        return new JsonObject();
    }

    /** The world played last on this computer (its saves folder and name), for Continue. */
    record LastWorld(String id, String name) {
        private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("zomboidcraft/last_world.json");

        boolean exists() {
            return Files.isDirectory(FabricLoader.getInstance().getGameDir().resolve("saves").resolve(id));
        }

        static void save(MinecraftServer server) {
            try {
                Path root = server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
                JsonObject o = new JsonObject();
                o.addProperty("id", root.getFileName().toString());
                o.addProperty("name", server.getWorldData().getLevelName());
                Files.createDirectories(FILE.getParent());
                Files.writeString(FILE, o.toString(), StandardCharsets.UTF_8);
            } catch (Exception e) {
                ZomboidCraft.LOGGER.warn("Couldn't note the last world", e);
            }
        }

        static @Nullable LastWorld read() {
            try {
                if (!Files.isRegularFile(FILE)) return null;
                JsonObject o = JsonParser.parseString(Files.readString(FILE, StandardCharsets.UTF_8)).getAsJsonObject();
                return new LastWorld(o.get("id").getAsString(), o.has("name") ? o.get("name").getAsString() : o.get("id").getAsString());
            } catch (Exception e) {
                return null;
            }
        }
    }
}
