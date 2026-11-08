package com.zomboidcraft.client.hud;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.InputConstants;
import com.zomboidcraft.ZomboidCraft;
import com.zomboidcraft.survival.character.CharacterNet;
import com.zomboidcraft.survival.character.MarineData;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * Endless Night's new soldier (Josiah 2026-11-08, round 5 approved 20:37 "Looks good put that in"): Halo: Reach's menu
 * style over the dimmed game. Choose your side; a Spartan picks from Noble Team (wide cards, the one under the mouse
 * grows and glows, every figure always inside its own border), Noble Six can go to the Armory, then a loadout; an
 * operator picks USEC or BEAR, a face and a voice. The art (Reach's UI bitmaps, the posed Noble renders, the Tarkov
 * faces, the fonts) is the {@code charsel} kit on this computer only (config/zomboidcraft/hud/charsel); without it the
 * marine screen shows instead. Picking a side picks the HUD.
 */
public final class SoldierScreen extends Screen {
    static final String KIT = "charsel";
    private static final int WHITE = argb(236, 242, 248), DIM = argb(150, 168, 186), BLUE = argb(120, 180, 230), FAINT = argb(95, 112, 130);
    private static final int SOFT = argb(210, 225, 240), SOFT_DIM = argb(165, 180, 196), LIST = argb(200, 214, 228), LIST_DIM = argb(180, 196, 212);
    private static final int LOCKED = argb(120, 132, 146);

    enum Page {SIDE, NOBLE, ARMORY, LOADOUT, OPERATOR}

    record Noble(String id, String name, String number, String desc, String weapon, String ability, int loadout) {}

    static final List<Noble> NOBLES = List.of(
            new Noble("carter", "CARTER-A259", "NOBLE ONE", "Commander. Leads the team.", "dmr", "jetpack", 1),
            new Noble("kat", "KAT-B320", "NOBLE TWO", "Lieutenant commander. Tech and intel.", "magnum", "sprint", 0),
            new Noble("jun", "JUN-A266", "NOBLE THREE", "Marksman and scout.", "sniper_rifle", "active_camo", 2),
            new Noble("emile", "EMILE-A239", "NOBLE FOUR", "Close-quarters assault.", "shotgun", "armor_lock", 3),
            new Noble("jorge", "JORGE-052", "NOBLE FIVE", "Heavy weapons. Carries the M247H machine gun.", "machine_gun", "drop_shield", 4),
            new Noble("six", "NOBLE SIX", "YOU", "Your own Spartan: your armour, colours and emblem.", "dmr", "evade", 1),
            new Noble("john", "JOHN-117", "BONUS", "Mark V. Unlocked as a bonus.", "assault_rifle", "sprint", 0));
    private static final int SIX = 5;

    record Loadout(String name, String primary, String primaryName, String secondary, String secondaryName, int grenades, String ability) {}

    static final List<Loadout> LOADOUTS = List.of(
            new Loadout("ASSAULT", "assault_rifle", "MA37 ASSAULT RIFLE", "magnum", "M6G MAGNUM", 2, "sprint"),
            new Loadout("MARKSMAN", "dmr", "M392 DMR", "magnum", "M6G MAGNUM", 2, "jetpack"),
            new Loadout("RECON", "sniper_rifle", "SRS99 SNIPER RIFLE", "magnum", "M6G MAGNUM", 1, "active_camo"),
            new Loadout("BREACHER", "shotgun", "M45 SHOTGUN", "assault_rifle", "MA37 ASSAULT RIFLE", 2, "armor_lock"),
            new Loadout("HEAVY", "grenade_launcher", "M319 GRENADE LAUNCHER", "assault_rifle", "MA37 ASSAULT RIFLE", 2, "drop_shield"));
    /** Jorge's heavy kit is his own machine gun. */
    private static final Loadout JORGE_HEAVY = new Loadout("HEAVY", "machine_gun", "M247H MACHINE GUN", "assault_rifle", "MA37 ASSAULT RIFLE", 2, "drop_shield");

    /** The Armory's categories, each with its pieces (true: still locked) or, for the colours, the swatches. */
    static final Map<String, List<Object[]>> ARMORY = new LinkedHashMap<>();
    static final int[][] COLOURS = {{110, 112, 110}, {160, 162, 160}, {220, 220, 214}, {150, 40, 36}, {120, 60, 90}, {200, 120, 110}, {200, 110, 40},
            {215, 110, 90}, {225, 170, 130}, {200, 150, 50}, {210, 190, 60}, {205, 200, 150}, {130, 150, 110}, {60, 110, 50}, {95, 100, 60},
            {40, 120, 120}, {80, 170, 170}, {70, 160, 200}, {40, 80, 160}, {40, 60, 120}, {30, 50, 100}, {90, 60, 140}, {150, 90, 160},
            {160, 140, 200}, {120, 20, 30}, {90, 30, 50}, {220, 140, 170}, {90, 60, 40}, {150, 120, 85}, {165, 150, 110}};

    static {
        piece("HELMET", "MJOLNIR MKV(B)", "CQC", "MILITARY POLICE", "AIR ASSAULT", "GRENADIER", "SCOUT", "OPERATOR", "!PILOT", "!EOD", "!JFO", "!GUNGNIR", "!HAUNTED");
        piece("LEFT SHOULDER", "DEFAULT", "JFO", "UA", "SECURITY", "COMMANDO", "OPERATOR", "!SNIPER", "!GUNGNIR", "!UA/MULTI-THREAT", "!EVA", "!EOD");
        piece("RIGHT SHOULDER", "DEFAULT", "JFO", "UA", "SECURITY", "COMMANDO", "OPERATOR", "!SNIPER", "!GUNGNIR", "!UA/MULTI-THREAT", "!EVA", "!EOD");
        piece("CHEST", "DEFAULT", "UA/HAZOP", "HP/HALO", "UA/COUNTERASSAULT", "TACTICAL/PATROL", "TACTICAL/RECON", "!ASSAULT/SAPPER",
                "!COLLAR/GRENADIER", "!UA/BASE SECURITY", "!TACTICAL/HARD CASE");
        piece("WRIST", "DEFAULT", "ASSAULT/BREACHER", "!TACTICAL/TACPAD", "!TACTICAL/UGPS");
        piece("UTILITY", "DEFAULT", "UA/NXRA", "TACTICAL/TRAUMA KIT", "!TACTICAL/HARD CASE", "!TACTICAL/SOFT CASE", "!TACTICAL/UGPS");
        piece("KNEE GUARDS", "DEFAULT", "FJ/PARA", "!GROUNDED");
        piece("VISOR COLOUR", "DEFAULT", "SILVER", "BLUE", "!GOLD", "!BLACK");
        piece("ARMOUR EFFECT", "NONE", "!HEART ATTACK", "!PESTILENCE", "!BIRTHDAY PARTY", "!INCLEMENT WEATHER");
        piece("PRIMARY COLOUR");
        piece("SECONDARY COLOUR");
        piece("EMBLEM", "SEVENTH COLUMN", "BULLSEYE", "DOUBLE CROWN", "HEADSHOT", "!RAMPANCY", "!FLAMING NINJA");
    }

    private static void piece(String cat, String... names) {
        List<Object[]> items = new java.util.ArrayList<>();
        for (String n : names) items.add(new Object[] {n.startsWith("!") ? n.substring(1) : n, n.startsWith("!")});
        ARMORY.put(cat, items);
    }

    private final CharacterNet.Open open;
    private Page page = Page.SIDE;
    private int side = 0, noble = SIX, loadout = 1, faction = 0, face = 13, voice = 1;
    private int cat = 0, item = 0, primary = 0, secondary = 2;
    private final Map<String, Integer> equipped = new LinkedHashMap<>();
    private final float[] grow = new float[NOBLES.size()];
    private long last = System.nanoTime();
    private @Nullable HudKit k;
    /** Hit boxes from the last frame, layout units: {x0, y0, x1, y1, kind, index}. */
    private final List<float[]> hits = new java.util.ArrayList<>();
    /** The mouse picks what it moves over; held still, it leaves the keys' choice alone. */
    private double lastMx = Double.NaN, lastMy = Double.NaN;
    private boolean moved;

    public SoldierScreen(CharacterNet.Open open) {
        super(Component.literal("New soldier"));
        this.open = open;
        grow[noble] = 1;
        for (String c : ARMORY.keySet()) equipped.put(c, 0);
    }

    /** Whether the new-soldier screens can be drawn here (their art is on this computer). */
    public static boolean ready() {
        return HudKit.present(KIT);
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
        extractBlurredBackground(g);
        k = HudKit.get(KIT);
        if (k == null) return;
        k.begin();
        hits.clear();
        HudKit.pixels(g);
        var window = Minecraft.getInstance().getWindow();
        int ww = window.getWidth(), wh = window.getHeight();
        // Reach's menus sit over the game, dimmed toward steel blue, darker at the edges, with the diamond lattice
        g.fill(0, 0, ww, wh, HudKit.argb(173, 10, 16, 26));
        k.picture(g, "vignette", "ui/vignette.png", 0, 0, ww, wh, 480, 270, 0xFFFFFFFF);
        k.image(g, "ui/lattice.png", k.lw - 900, -60, 1100, 1100, 0xFFFFFFFF);
        float ease = 1f - (float) Math.exp(-dt * 14);
        for (int i = 0; i < grow.length; i++) grow[i] += ((i == noble ? 1 : 0) - grow[i]) * ease;
        double lx = layoutX(mx), ly = layoutY(my);
        moved = mx != lastMx || my != lastMy;
        lastMx = mx;
        lastMy = my;
        switch (page) {
            case SIDE -> side(g, lx, ly);
            case NOBLE -> nobles(g, lx, ly);
            case ARMORY -> armory(g);
            case LOADOUT -> loadouts(g);
            case OPERATOR -> operator(g);
        }
        HudKit.end(g);
    }

    private double layoutX(double guiX) {
        return (guiX * Minecraft.getInstance().getWindow().getGuiScale() - k.ox) / k.s;
    }

    private double layoutY(double guiY) {
        return (guiY * Minecraft.getInstance().getWindow().getGuiScale() - k.oy) / k.s;
    }

    private void side(GuiGraphicsExtractor g, double lx, double ly) {
        header(g, "ENDLESS NIGHT  /  NEW SOLDIER", "CHOOSE YOUR SIDE");
        for (int i = 0; i < 2; i++) {
            if (moved && inside(lx, ly, 150 + i * 860, 230, 760, 680)) side = i;
            hit(150 + i * 860, 230, 760, 680, 0, i);
        }
        card(g, 150, 230, 760, 680, side == 0);
        card(g, 1010, 230, 760, 680, side == 1);
        shadow(g, 340, 868, 260, 26);
        figure(g, "fig/front_six.png", "side_six", 340, 880, 590, 1f);
        text(g, "s_sp", "light_54", "SPARTAN", 870, 262, 1, side == 0 ? WHITE : DIM, 3);
        String[] sp = {"Noble Team or your own Noble Six", "MJOLNIR armour, energy shields", "Armour abilities", "Reach weapons, Reach HUD", "Recruit the rest of Noble"};
        for (int i = 0; i < sp.length; i++) text(g, "s_spl" + i, "med_24", sp[i], 870, 360 + 42 * i, 1, side == 0 ? SOFT : SOFT_DIM, 0);
        portrait(g, "side_face", faceFile(13, true), 1050, 360, 330, 420, side == 1 ? 1f : 0.85f);
        text(g, "s_op", "light_54", "OPERATOR", 1730, 262, 1, side == 1 ? WHITE : DIM, 3);
        String[] op = {"PMC for the UNSC (USEC or BEAR)", "Tarkov weapons, mods and gear", "Tarkov body: hits, bleeds, breaks", "Near-empty Tarkov HUD", "Cross-faction weapons, if allowed"};
        for (int i = 0; i < op.length; i++) text(g, "s_opl" + i, "med_24", op[i], 1730, 360 + 42 * i, 1, side == 1 ? SOFT : SOFT_DIM, 0);
        text(g, "s_foot", "med_24", "Your side sets your HUD, your body and what you can carry.", k.lw / 2, 935, 0.5, DIM, 0);
        footer(g, "ENTER", "Select", "ESC", "Back", "A / D", "Switch");
    }

    private void nobles(GuiGraphicsExtractor g, double lx, double ly) {
        Noble chosen = NOBLES.get(noble);
        header(g, "NEW SOLDIER  /  SPARTAN", "NOBLE TEAM");
        // round 5: wide cards from x 100 to 1510, the hovered one 30% wider and a little taller; figures at full size
        // (470 tall, 590 hovered) fitted inside their own card, never past its border
        float grows = 0.3f, gap = 8, x0 = 100, x1 = 1510;
        float wb = (x1 - x0 - 6 * gap) / (NOBLES.size() + grows);
        float x = x0;
        float[][] boxes = new float[NOBLES.size()][];
        for (int i = 0; i < NOBLES.size(); i++) {
            float a = grow[i], w = wb * (1 + grows * a);
            boxes[i] = new float[] {x, 250 - 22 * a, w, 690 + 44 * a};
            x += w + gap;
        }
        for (int i = 0; i < NOBLES.size(); i++) {
            float[] b = boxes[i];
            if (moved && inside(lx, ly, b[0], b[1], b[2], b[3])) noble = i;
            hit(b[0], b[1], b[2], b[3], 1, i);
        }
        Integer[] order = new Integer[NOBLES.size()];
        for (int i = 0; i < order.length; i++) order[i] = i;
        java.util.Arrays.sort(order, (p, q) -> Float.compare(grow[p], grow[q]));     // the hovered card on top
        for (int i : order) {
            float a = grow[i];
            float[] b = boxes[i];
            boolean lit = a > 0.5f;
            int fill = HudKit.argb(120 + Math.round(30 * a), 30 + Math.round(22 * a), 46 + Math.round(38 * a), 64 + Math.round(52 * a));
            card(g, b[0], b[1], b[2], b[3], fill, lit ? argb(215, 235, 250) : HudKit.argb(110, 170, 200, 225), lit);
            nobleFigure(g, i, b, wb, grows);
            text(g, "n_lab" + i, lit ? "med_22" : "med_18", NOBLES.get(i).number(), b[0] + b[2] / 2, b[1] + 30, 0.5, lit ? WHITE : FAINT, 2);
        }
        float ix = 1545;
        text(g, "n_num", "med_24", chosen.number(), ix, 260, 0, BLUE, 3);
        text(g, "n_name", "light_50", chosen.name(), ix, 290, 0, WHITE, 0);
        fadeline(g, ix, 365, 330, 180);
        float y = para(g, "n_desc", "med_25", chosen.desc(), ix, 388, 330, 34, argb(205, 220, 235));
        text(g, "n_starts", "med_20", "STARTS WITH", ix, y + 26, 0, DIM, 3);
        glyph(g, "n_wpn", "weapons/" + chosen.weapon(), ix, y + 64, 60, 175, WHITE);
        text(g, "n_wpnn", "med_21", chosen.weapon().replace('_', ' ').toUpperCase(java.util.Locale.ROOT), ix, y + 146, 0, WHITE, 0);
        glyph(g, "n_ab", "abilities/" + chosen.ability(), ix + 215, y + 66, 58, 120, WHITE);
        text(g, "n_abn", "med_21", chosen.ability().replace('_', ' ').toUpperCase(java.util.Locale.ROOT), ix + 215, y + 146, 0, WHITE, 0);
        para(g, "n_note", "med_21", "The others join you as squad mates once you meet them in the world.", ix, y + 210, 330, 29, DIM);
        String play = "Play as " + (noble == SIX ? "Noble Six" : title(chosen.name()));
        if (noble == SIX) footer(g, "ENTER", play, "ESC", "Back", "A / D", "Browse", "Q", "Armory");
        else footer(g, "ENTER", play, "ESC", "Back", "A / D", "Browse");
    }

    /** A Noble's figure fitted inside its card (as the round 5 mock-up), made once at rest and once hovered. */
    private void nobleFigure(GuiGraphicsExtractor g, int i, float[] b, float wb, float grows) {
        String file = "fig/front_" + NOBLES.get(i).id() + ".png";
        int[] src = k.size(file);
        if (src[0] == 0) return;
        float a = grow[i];
        float[] now = fit(src, b[2], b[3], a), rest = fit(src, wb, 690, 0), hover = fit(src, wb * (1 + grows), 734, 1);
        float[] made = a < 0.5f ? rest : hover;
        float cx = b[0] + b[2] / 2, bottom = b[1] + b[3] - 16;
        shadow(g, cx, bottom - 8, now[0] * 0.8f, 26);
        int w = Math.round(now[0] * k.s), h = Math.round(now[1] * k.s);
        int x = Math.round(k.px(cx) - w / 2f), y = Math.round(k.py(bottom)) - h;
        k.picture(g, "n_fig" + i + (a < 0.5f ? "r" : "h"), file, x, y, w, h, Math.round(made[0] * k.s), Math.round(made[1] * k.s),
                HudKit.white(0.62f + 0.38f * a));
    }

    /** The figure's size inside a card of that size (6 in at the sides, 52 under the top, 16 off the bottom). */
    private static float[] fit(int[] src, float cw, float ch, float a) {
        float bw = cw - 12, bh = Math.min(ch - 68, 470 + 120 * a);
        float f = Math.min(bw / src[0], bh / src[1]);
        return new float[] {src[0] * f, src[1] * f};
    }

    private void armory(GuiGraphicsExtractor g) {
        header(g, "NEW SOLDIER  /  NOBLE SIX", "ARMORY");
        List<String> cats = List.copyOf(ARMORY.keySet());
        for (int i = 0; i < cats.size(); i++) {
            float y = 230 + 52 * i;
            if (i == cat) highlight(g, 112, y - 4, 420, 44);
            text(g, "a_cat" + i, "med_26", cats.get(i), 132, y, 0, i == cat ? WHITE : LIST_DIM, 2);
            hit(112, y - 4, 420, 44, 2, i);
        }
        shadow(g, 900, 975, 380, 34);
        figure(g, "fig/quarter_six.png", "armory_six", 900, 985, 800, 1f);
        String c = cats.get(cat);
        float ax = 1330;
        text(g, "a_head", "med_22", c, ax, 230, 0, BLUE, 3);
        List<Object[]> items = ARMORY.get(c);
        boolean colours = items.isEmpty();
        float y = 268;
        if (colours) {
            int sel = c.startsWith("PRIMARY") ? primary : secondary;
            for (int i = 0; i < COLOURS.length; i++) {
                float sx = ax + (i % 6) * 76, sy = y + (i / 6) * 76;
                int[] col = COLOURS[i];
                k.fill(g, sx, sy, sx + 66, sy + 66, HudKit.argb(255, col[0], col[1], col[2]));
                if (i == sel) outline(g, sx - 4, sy - 4, 74, 74, 3, argb(225, 240, 255));
                hit(sx, sy, 66, 66, 4, i);
            }
            y += 5 * 76;
        } else {
            int on = equipped.get(c);
            for (int i = 0; i < items.size(); i++) {
                boolean locked = (Boolean) items.get(i)[1];
                if (i == item) highlight(g, ax - 8, y - 4, 470, 40);
                int colour = locked ? LOCKED : i == item ? WHITE : LIST;
                text(g, "a_it" + i, "med_24", (String) items.get(i)[0], ax + 12, y, 0, colour, 1);
                if (i == on) text(g, "a_on" + i, "med_18", "EQUIPPED", ax + 452, y + 6, 1, BLUE, 2);
                if (locked) imageH(g, "ui/lock.png", "a_lock", ax + 430, y + 2, 30, HudKit.white(0.8f));
                hit(ax - 8, y - 4, 470, 40, 3, i);
                y += 44;
            }
        }
        fadeline(g, ax, y + 8, 460, 150);
        String about;
        if (colours) about = "Your " + c.toLowerCase(java.util.Locale.ROOT) + ". It shows on your armour in the world.";
        else {
            Object[] it = items.get(Math.min(item, items.size() - 1));
            about = (Boolean) it[1] ? it[0] + " is locked. Locked pieces open as you level up." : it[0] + ". Press Enter to equip it.";
        }
        para(g, "a_about", "med_22", about, ax, y + 24, 460, 30, DIM);
        // the primary colour row along the bottom, as Reach's armory keeps it
        text(g, "a_prim", "med_18", "PRIMARY", ax, 905, 0, DIM, 3);
        for (int i = 0; i < COLOURS.length; i++) {
            float sx = ax + (i % 15) * 31, sy = 932 + (i / 15) * 31;
            int[] col = COLOURS[i];
            k.fill(g, sx, sy, sx + 26, sy + 26, HudKit.argb(255, col[0], col[1], col[2]));
            if (i == primary) outline(g, sx, sy, 26, 26, 2, 0xFFFFFFFF);
        }
        imageH(g, "ui/rank.png", "a_rank", 120, 880, 54, 0xFFFFFFFF);
        text(g, "a_rn", "light_28", "NOBLE SIX", 186, 884, 0, WHITE, 0);
        text(g, "a_rr", "med_18", "RECRUIT", 186, 918, 0, DIM, 3);
        footer(g, "ENTER", "Equip", "ESC", "Back", "W / S", "Browse", "A / D", "Category");
    }

    private void loadouts(GuiGraphicsExtractor g) {
        header(g, "NEW SOLDIER  /  " + NOBLES.get(noble).name(), "CHOOSE A LOADOUT");
        for (int i = 0; i < LOADOUTS.size(); i++) {
            float y = 240 + 116 * i;
            Loadout l = loadout(i);
            card(g, 120, y, 520, 96, i == loadout);
            glyph(g, "l_g" + i, "weapons/" + l.primary(), 150, y + 25, 46, 300, i == loadout ? WHITE : DIM);
            text(g, "l_n" + i, "light_30", l.name(), 610, y + 30, 1, i == loadout ? WHITE : LIST_DIM, 2);
            hit(120, y, 520, 96, 5, i);
        }
        Loadout l = loadout(loadout);
        card(g, 720, 240, 1080, 676, argb(22, 34, 48, 140), HudKit.argb(110, 170, 200, 225), false);
        text(g, "l_name", "light_46", l.name(), 770, 270, 0, WHITE, 3);
        fadeline(g, 770, 336, 700, 160);
        slot(g, "l_p", 770, 370, "PRIMARY", l.primary(), 130, l.primaryName());
        slot(g, "l_s", 1290, 370, "SECONDARY", l.secondary(), 110, l.secondaryName());
        text(g, "l_gl", "med_20", "GRENADES", 770, 640, 0, BLUE, 3);
        imageH(g, "ui/grenades.png", "l_gr", 770, 680, 110, 0xFFFFFFFF);
        text(g, "l_gn", "light_30", "x" + l.grenades() + "  FRAG", 880, 720, 0, WHITE, 0);
        text(g, "l_al", "med_20", "ARMOUR ABILITY", 1290, 640, 0, BLUE, 3);
        glyph(g, "l_ab", "abilities/" + l.ability(), 1290, 680, 100, 200, WHITE);
        text(g, "l_an", "light_30", l.ability().replace('_', ' ').toUpperCase(java.util.Locale.ROOT), 1410, 715, 0, WHITE, 0);
        para(g, "l_note", "med_22", "Your loadout is what you spawn with. Ammo is finite: resupply comes from guarded pod drops.", 770, 830, 980, 30, DIM);
        footer(g, "ENTER", "Deploy", "ESC", "Back", "W / S", "Browse");
    }

    private Loadout loadout(int i) {
        return i == 4 && NOBLES.get(noble).id().equals("jorge") ? JORGE_HEAVY : LOADOUTS.get(i);
    }

    private void slot(GuiGraphicsExtractor g, String key, float x, float y, String label, String weapon, float h, String name) {
        text(g, key + "l", "med_20", label, x, y, 0, BLUE, 3);
        float gh = glyph(g, key + "g", "weapons/" + weapon, x, y + 40, h, 470, WHITE);
        text(g, key + "n", "med_24", name, x, y + 40 + gh + 16, 0, WHITE, 1);
    }

    private void operator(GuiGraphicsExtractor g) {
        header(g, "NEW SOLDIER  /  OPERATOR", "OPERATOR");
        String[] tabs = {"USEC", "BEAR"};
        for (int i = 0; i < 2; i++) {
            card(g, 120 + i * 270, 230, 250, 70, i == faction);
            text(g, "o_t" + i, "light_30", tabs[i], 245 + i * 270, 250, 0.5, i == faction ? WHITE : DIM, 4);
            hit(120 + i * 270, 230, 250, 70, 6, i);
        }
        text(g, "o_face", "med_20", "FACE", 120, 330, 0, BLUE, 3);
        int[] faces = faces();
        for (int n = 0; n < faces.length; n++) {
            float x = 120 + (n % 4) * 132, y = 366 + (n / 4) * 132;
            portrait(g, "o_f" + n, faceFile(faces[n], false), x, y, 122, 122, faces[n] == face ? 1f : 0.7f);
            if (faces[n] == face) outline(g, x - 3, y - 3, 128, 128, 3, argb(225, 240, 255));
            hit(x, y, 122, 122, 7, faces[n]);
        }
        text(g, "o_voice", "med_20", "VOICE", 120, 778, 0, BLUE, 3);
        highlight(g, 112, 808, 380, 40);
        text(g, "o_v", "med_24", tabs[faction] + " " + (voice + 1), 132, 812, 0, WHITE, 1);
        imageH(g, "ui/arrow_l.png", "o_al", 470, 812, 28, 0xFFFFFFFF);
        imageH(g, "ui/arrow_r.png", "o_ar", 510, 812, 28, 0xFFFFFFFF);
        hit(462, 806, 36, 40, 8, -1);
        hit(502, 806, 36, 40, 8, 1);
        card(g, 760, 230, 1040, 690, argb(18, 28, 40, 140), HudKit.argb(110, 170, 200, 225), false);
        portrait(g, "o_bare", faceFile(face, false), 800, 270, 470, 590, 1f);
        portrait(g, "o_kit", faceFile(face, true), 1300, 270, 470, 590, 1f);
        text(g, "o_hl", "med_20", "HEAD", 1035, 870, 0.5, DIM, 3);
        text(g, "o_kl", "med_20", "IN KIT", 1535, 870, 0.5, DIM, 3);
        para(g, "o_note", "med_22", "Your body is Tarkov's: hits, bleeds and breaks per part. Tarkov gear and weapons come with you into the world.", 120, 880, 600, 30, DIM);
        footer(g, "ENTER", "Deploy", "ESC", "Back", "A / D", "USEC / BEAR");
    }

    /** USEC's faces or BEAR's (the Tarkov face sheet: BEAR 0 to 10, USEC 11 to 22). */
    private int[] faces() {
        int from = faction == 0 ? 11 : 0, to = faction == 0 ? 22 : 10;
        int[] out = new int[to - from + 1];
        for (int i = 0; i < out.length; i++) out[i] = from + i;
        return out;
    }

    private @Nullable String faceFile(int idx, boolean kit) {
        JsonObject f = HudKit.obj(HudKit.obj(k.json, "face"), String.valueOf(idx));
        return f == null ? null : f.get(kit ? "kit" : "bare").getAsString();
    }

    // ------------------------------------------------------------------ pieces

    private void header(GuiGraphicsExtractor g, String crumb, String title) {
        text(g, "h_crumb" + page, "med_22", crumb, 120, 66, 0, DIM, 3);
        text(g, "h_title" + page, "light_64", title, 116, 92, 0, WHITE, 2);
        fadeline(g, 120, 176, 760, 210);
    }

    private void footer(GuiGraphicsExtractor g, String... pairs) {
        float x = 120, y = k.lh - 66;
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            float kw = k.measure("med_20", pairs[i], 0) + 20;
            outline(g, x, y - 2, kw, 32, 2, HudKit.argb(200, 255, 255, 255));
            text(g, "f_k" + page + i, "med_20", pairs[i], x + 10, y + 1, 0, WHITE, 0);
            x += kw + 12;
            x += text(g, "f_l" + page + i, "med_22", pairs[i + 1], x, y + 1, 0, DIM, 0) + 40;
        }
    }

    private void card(GuiGraphicsExtractor g, float x, float y, float w, float h, boolean lit) {
        card(g, x, y, w, h, lit ? HudKit.argb(150, 52, 84, 116) : HudKit.argb(120, 26, 40, 56),
                lit ? argb(215, 235, 250) : HudKit.argb(110, 170, 200, 225), lit);
    }

    /** Reach's loadout card: its 10px corner pieces nine-sliced, a fill and a line, and the blue glow when it's chosen. */
    private void card(GuiGraphicsExtractor g, float x, float y, float w, float h, int fill, int line, boolean lit) {
        int X = Math.round(k.px(x)), Y = Math.round(k.py(y)), W = Math.round(w * k.s), H = Math.round(h * k.s);
        k.nine(g, "ui/card_fill.png", X, Y, W, H, 36, 40, fill);
        k.nine(g, "ui/card_line.png", X, Y, W, H, 36, 40, line);
        if (lit) {
            int pad = Math.round(20 * k.s);
            k.nine(g, "ui/glow.png", X - pad, Y - pad, W + 2 * pad, H + 2 * pad, 56, 56, HudKit.argb(140, 150, 210, 255));
        }
    }

    private void fadeline(GuiGraphicsExtractor g, float x, float y, float w, int alpha) {
        k.image(g, "fade" + Math.round(w), "ui/fadeline.png", x, y, w, 2, HudKit.argb(alpha, 255, 255, 255));
    }

    /** The list highlight: Reach's roster gradient fading out to the right, with a bright bar at its start. */
    private void highlight(GuiGraphicsExtractor g, float x, float y, float w, float h) {
        k.image(g, "hl" + Math.round(w), "ui/highlight.png", x, y, w, h, HudKit.argb(150, 175, 205, 230));
        k.fill(g, x, y, x + 4, y + h, argb(225, 240, 255));
    }

    private void shadow(GuiGraphicsExtractor g, float cx, float cy, float w, float h) {
        float tw = w * 340 / 300f, th = h * 80 / 40f;
        k.image(g, "sh" + Math.round(w), "ui/shadow.png", cx - tw / 2, cy - th / 2, tw, th, 0xFFFFFFFF);
    }

    /** A picture at a height (layout units), its width from its own shape. */
    private float imageH(GuiGraphicsExtractor g, String file, String slot, float x, float y, float h, int colour) {
        int[] src = k.size(file);
        if (src[1] == 0) return 0;
        float w = src[0] * h / src[1];
        k.image(g, slot, file, x, y, w, h, colour);
        return w;
    }

    /** A Reach glyph at a height, no wider than maxW; returns the height drawn. */
    private float glyph(GuiGraphicsExtractor g, String slot, String name, float x, float y, float h, float maxW, int colour) {
        JsonObject e = HudKit.obj(HudKit.obj(k.json, "glyph"), name);
        if (e == null) return 0;
        String file = e.get("file").getAsString();
        int[] src = k.size(file);
        if (src[1] == 0) return 0;
        float w = src[0] * h / src[1];
        if (w > maxW) {
            h = h * maxW / w;
            w = maxW;
        }
        k.image(g, slot, file, x, y, w, h, colour);
        return h;
    }

    /** A figure at a height, its bottom middle at (cx, bottom). */
    private void figure(GuiGraphicsExtractor g, String file, String slot, float cx, float bottom, float h, float alpha) {
        int[] src = k.size(file);
        if (src[1] == 0) return;
        float w = src[0] * h / src[1];
        k.image(g, slot, file, cx - w / 2, bottom - h, w, h, HudKit.white(alpha));
    }

    private void portrait(GuiGraphicsExtractor g, String slot, @Nullable String file, float x, float y, float w, float h, float alpha) {
        if (file != null) k.cover(g, slot, file, x, y, w, h, HudKit.white(alpha));
        outline(g, x, y, w, h, 2, HudKit.argb(140, 170, 200, 225));
    }

    private void outline(GuiGraphicsExtractor g, float x, float y, float w, float h, float t, int colour) {
        k.fill(g, x, y, x + w, y + t, colour);
        k.fill(g, x, y + h - t, x + w, y + h, colour);
        k.fill(g, x, y + t, x + t, y + h - t, colour);
        k.fill(g, x + w - t, y + t, x + w, y + h - t, colour);
    }

    private float text(GuiGraphicsExtractor g, String slot, String font, String s, double x, double y, double alignX, int colour, float tracking) {
        return k.text(g, slot, font, s, x, y, alignX, 0, true, colour, tracking);
    }

    /** Wrapped text, a line every {@code lh}; returns the y under the last line. */
    private float para(GuiGraphicsExtractor g, String slot, String font, String s, float x, float y, float width, float lh, int colour) {
        String line = "";
        int n = 0;
        for (String word : s.split(" ")) {
            String t = line.isEmpty() ? word : line + " " + word;
            if (!line.isEmpty() && k.measure(font, t, 0) > width) {
                text(g, slot + n++, font, line, x, y, 0, colour, 0);
                y += lh;
                line = word;
            } else {
                line = t;
            }
        }
        if (!line.isEmpty()) {
            text(g, slot + n, font, line, x, y, 0, colour, 0);
            y += lh;
        }
        return y;
    }

    private void hit(float x, float y, float w, float h, int kind, int index) {
        hits.add(new float[] {x, y, x + w, y + h, kind, index});
    }

    private static boolean inside(double lx, double ly, float x, float y, float w, float h) {
        return lx >= x && lx < x + w && ly >= y && ly < y + h;
    }

    private static int argb(int r, int g, int b) {
        return HudKit.argb(255, r, g, b);
    }

    private static int argb(int r, int g, int b, int a) {
        return HudKit.argb(a, r, g, b);
    }

    /** "CARTER-A259" as "Carter-A259". */
    static String title(String s) {
        StringBuilder out = new StringBuilder();
        char prev = ' ';
        for (char c : s.toCharArray()) {
            out.append(Character.isLetter(prev) ? Character.toLowerCase(c) : Character.toUpperCase(c));
            prev = c;
        }
        return out.toString();
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (k == null) return true;
        double lx = layoutX(event.x()), ly = layoutY(event.y());
        for (int i = hits.size() - 1; i >= 0; i--) {
            float[] h = hits.get(i);
            if (lx < h[0] || lx >= h[2] || ly < h[1] || ly >= h[3]) continue;
            int index = (int) h[5];
            switch ((int) h[4]) {
                case 0 -> { side = index; confirm(); }
                case 1 -> { noble = index; confirm(); }
                case 2 -> { cat = index; item = equipped.get(List.copyOf(ARMORY.keySet()).get(cat)); }
                case 3 -> { item = index; equip(); }
                case 4 -> { if (List.copyOf(ARMORY.keySet()).get(cat).startsWith("PRIMARY")) primary = index; else secondary = index; }
                case 5 -> { if (loadout == index) confirm(); else loadout = index; }
                case 6 -> { if (faction != index) { faction = index; face = faces()[Math.min(2, faces().length - 1)]; } }
                case 7 -> face = index;
                case 8 -> voice = (voice + index + 4) % 4;
                default -> {}
            }
            return true;
        }
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.key();
        boolean left = key == InputConstants.KEY_A || key == InputConstants.KEY_LEFT, right = key == InputConstants.KEY_D || key == InputConstants.KEY_RIGHT;
        boolean up = key == InputConstants.KEY_W || key == InputConstants.KEY_UP, down = key == InputConstants.KEY_S || key == InputConstants.KEY_DOWN;
        if (key == InputConstants.KEY_ESCAPE) {
            back();
        } else if (key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER || key == InputConstants.KEY_SPACE) {
            if (page == Page.ARMORY) equip();
            else confirm();
        } else switch (page) {
            case SIDE -> { if (left || right) side = 1 - side; }
            case NOBLE -> {
                if (left) noble = (noble + NOBLES.size() - 1) % NOBLES.size();
                if (right) noble = (noble + 1) % NOBLES.size();
                if (key == InputConstants.KEY_Q && noble == SIX) page = Page.ARMORY;
            }
            case ARMORY -> {
                List<String> cats = List.copyOf(ARMORY.keySet());
                if (left || right) {
                    cat = (cat + (left ? cats.size() - 1 : 1)) % cats.size();
                    item = equipped.get(cats.get(cat));
                }
                List<Object[]> items = ARMORY.get(cats.get(cat));
                if (items.isEmpty()) {
                    int step = up ? -6 : down ? 6 : 0;
                    if (cats.get(cat).startsWith("PRIMARY")) primary = Math.floorMod(primary + step, COLOURS.length);
                    else secondary = Math.floorMod(secondary + step, COLOURS.length);
                } else if (up || down) {
                    item = Math.floorMod(item + (up ? -1 : 1), items.size());
                }
            }
            case LOADOUT -> {
                if (up) loadout = (loadout + LOADOUTS.size() - 1) % LOADOUTS.size();
                if (down) loadout = (loadout + 1) % LOADOUTS.size();
            }
            case OPERATOR -> {
                if (left || right) {
                    faction = 1 - faction;
                    face = faces()[Math.min(2, faces().length - 1)];
                }
                int[] f = faces();
                int at = 0;
                for (int i = 0; i < f.length; i++) if (f[i] == face) at = i;
                if (up) face = f[Math.floorMod(at - 4, f.length)];
                if (down) face = f[Math.floorMod(at + 4, f.length)];
                if (key == InputConstants.KEY_E) voice = (voice + 1) % 4;
            }
        }
        return true;
    }

    private void equip() {
        String c = List.copyOf(ARMORY.keySet()).get(cat);
        List<Object[]> items = ARMORY.get(c);
        if (!items.isEmpty() && item < items.size() && !(Boolean) items.get(item)[1]) equipped.put(c, item);
    }

    private void confirm() {
        switch (page) {
            case SIDE -> page = side == 0 ? Page.NOBLE : Page.OPERATOR;
            case NOBLE -> {
                loadout = NOBLES.get(noble).loadout();
                page = Page.LOADOUT;
            }
            case ARMORY -> page = Page.NOBLE;
            case LOADOUT, OPERATOR -> finish();
        }
    }

    private void back() {
        switch (page) {
            case SIDE -> minecraft.gui.setScreen(new net.minecraft.client.gui.screens.PauseScreen(true));
            case NOBLE, OPERATOR -> page = Page.SIDE;
            case ARMORY, LOADOUT -> page = Page.NOBLE;
        }
    }

    /** The soldier is made: the HUD follows the side, the choice is kept on this computer, the server makes the marine. */
    private void finish() {
        boolean spartan = side == 0;
        HudSettings.setStyle(spartan ? HudSettings.Style.SPARTAN : HudSettings.Style.OPERATOR);
        save();
        String role = MarineData.roles().keySet().iterator().next();
        ClientPlayNetworking.send(new CharacterNet.Create(MarineData.PREFIX + role, List.of()));
        com.zomboidcraft.client.kit.CharacterScreen.made();
        minecraft.gui.setScreen(null);     // then where you wake
    }

    private void save() {
        JsonObject o = new JsonObject();
        o.addProperty("side", side == 0 ? "spartan" : "operator");
        if (side == 0) {
            o.addProperty("noble", NOBLES.get(noble).id());
            o.addProperty("loadout", loadout(loadout).name());
            JsonObject armour = new JsonObject();
            equipped.forEach((c, i) -> {
                List<Object[]> items = ARMORY.get(c);
                if (!items.isEmpty()) armour.addProperty(c.toLowerCase(java.util.Locale.ROOT), (String) items.get(i)[0]);
            });
            armour.addProperty("primary colour", primary);
            armour.addProperty("secondary colour", secondary);
            o.add("armour", armour);
        } else {
            o.addProperty("faction", faction == 0 ? "usec" : "bear");
            o.addProperty("face", face);
            o.addProperty("voice", voice + 1);
        }
        Path file = FabricLoader.getInstance().getConfigDir().resolve("zomboidcraft/soldier.json");
        try {
            Files.createDirectories(file.getParent());
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            Files.writeString(file, gson.toJson(o), StandardCharsets.UTF_8);
        } catch (Exception e) {
            ZomboidCraft.LOGGER.warn("Couldn't save the new soldier to {}", file, e);
        }
    }

    // ------------------------------------------------------------------ for the UI test

    public void show(String pageName, int pick) {
        page = Page.valueOf(pageName);
        switch (page) {
            case SIDE -> side = pick;
            case NOBLE -> noble = pick;
            case LOADOUT -> loadout = pick;
            case OPERATOR -> face = pick;
            case ARMORY -> cat = pick;
        }
        java.util.Arrays.fill(grow, 0);
        grow[noble] = 1;
    }
}
