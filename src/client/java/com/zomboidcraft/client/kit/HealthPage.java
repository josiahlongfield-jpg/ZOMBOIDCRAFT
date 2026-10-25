package com.zomboidcraft.client.kit;

import com.zomboidcraft.ModItems;
import com.zomboidcraft.client.hud.ActionHud;
import com.zomboidcraft.combat.Bench;
import com.zomboidcraft.combat.Stamina;
import com.zomboidcraft.skills.Skill;
import com.zomboidcraft.skills.Skills;
import com.zomboidcraft.survival.Body;
import com.zomboidcraft.survival.Body.Part;
import com.zomboidcraft.survival.Body.Region;
import com.zomboidcraft.survival.Treatment;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;

/**
 * The health page: every part of you down the left, a flat figure in the middle lit where it hurts, and on the right
 * what the chosen part needs, with the bandage or splint you carry ready to put on. Treating is a timed action on the
 * server, so the bar fills here and nothing is used up until it's done.
 */
final class HealthPage {
    private static final String[] NAMES = {"Head", "Torso", "Left arm", "Right arm", "Left leg", "Right leg"};
    /** The flat figure, front on (so your right arm is on our left): x, y, w, h in model pixels. */
    private static final int[][] SHAPE = {{4, 0, 8, 8}, {4, 8, 8, 12}, {12, 8, 4, 12}, {0, 8, 4, 12}, {8, 20, 4, 12}, {4, 20, 4, 12}};
    private static final int[] SKIN = {0xFF9C7A5E, 0xFF4F5640, 0xFF4A513C, 0xFF4A513C, 0xFF3D3F44, 0xFF43454A};
    private static final int U = 6;

    private final KitScreen screen;
    private Region selected;

    HealthPage(KitScreen screen) {
        this.screen = screen;
    }

    void draw(GuiGraphicsExtractor g, int x, int y, int mx, int my) {
        Font font = screen.mc().font;
        LocalPlayer player = screen.player();
        Body body = Body.get(player);
        if (selected == null) {
            Region worst = body.worst();
            selected = worst == null ? Region.TORSO : worst;
        }
        strip(g, font, player, body, x + 181, y + 33);

        // every part, down the left
        KitDraw.heading(g, font, "Body", x + 37, y + 58, 162, false);
        for (Region r : Region.values()) {
            Part p = body.part(r);
            int ry = y + 66 + r.ordinal() * 36, rx = x + 37, rw = 162, rh = 33;
            boolean on = r == selected, over = screen.hovered(rx, ry, rw, rh, mx, my);
            if (on) g.fill(rx, ry, rx + rw, ry + rh, 0x14E8E4DA);
            KitDraw.frame(g, rx, ry, rw, rh, on ? 0x80E8E4DA : over ? 0x59E8E4DA : 0x00000000);
            KitDraw.caps(g, font, NAMES[r.ordinal()], rx + 5, ry + 5, KitDraw.BRIGHT, 0.95f);
            KitDraw.capsRight(g, font, word(p), rx + rw - 5, ry + 5, colour(p), 0.85f);
            KitDraw.text(g, font, line(player, p), rx + 5, ry + 16, KitDraw.DIM, 0.8f);
            float healed = p.fine() ? 1 : healed(p);
            g.fill(rx + 5, ry + rh - 5, rx + rw - 5, ry + rh - 4, 0x1FE8E4DA);
            g.fill(rx + 5, ry + rh - 5, rx + 5 + Math.round((rw - 10) * healed), ry + rh - 4, p.fine() ? 0xB39CC46A : colour(p));
            screen.hot(rx, ry, rw, rh, () -> select(r));
        }

        figure(g, body, x + 308 - 8 * U, y + 66, mx, my);
        treat(g, font, player, body, x + 417, y + 58, mx, my);

        int hx = x + 37, hy = y + KitScreen.H - 14;
        hx += KitDraw.hint(g, font, "LMB", "Select a part / treat it", hx, hy);
        hx += KitDraw.hint(g, font, "Q/E", "Pages", hx, hy);
        KitDraw.hint(g, font, "I", "Close", hx, hy);
        KitDraw.textRight(g, font, "Moving or a hit stops a treatment; nothing is used up", x + 579, hy + 1, KitDraw.FAINT, 0.9f);
    }

    private void select(Region r) {
        if (r == selected) return;
        selected = r;
        com.zomboidcraft.client.sound.UiSounds.play(com.zomboidcraft.client.sound.UiSounds.CLICK);
    }

    /** Health, bleeding, breaks, endurance and speed, across the top. */
    private void strip(GuiGraphicsExtractor g, Font font, LocalPlayer player, Body body, int x, int y) {
        int broken = 0;
        for (Region r : Region.values()) if (body.part(r).fractured()) broken++;
        int bleeding = body.bleeding();
        float health = player.getHealth() / player.getMaxHealth();
        double speed = player.getAttributeValue(Attributes.MOVEMENT_SPEED) / 0.1;
        float stamina = Stamina.get(player) / Stamina.FULL;
        String[] labels = {"Health", "Bleeding", "Fractures", "Endurance", "Speed"};
        String[] values = {Math.round(health * 100) + "%", bleeding == 0 ? "None" : bleeding + (bleeding > 1 ? " wounds" : " wound"),
                broken == 0 ? "None" : String.valueOf(broken), Math.round(stamina * 100) + "%", Math.round(speed * 100) + "%"};
        float[] bars = {health, bleeding / 3f, broken / 3f, stamina, (float) Math.min(1, speed)};
        int[] colours = {health < 0.35f ? KitDraw.BAD : KitDraw.BRIGHT, bleeding > 0 ? 0xFFE0624A : KitDraw.BRIGHT,
                broken > 0 ? 0xFFE0A85A : KitDraw.BRIGHT, KitDraw.BRIGHT, speed < 0.95 ? KitDraw.WARN : KitDraw.BRIGHT};
        for (int i = 0; i < labels.length; i++) {
            int cx = x + i * 52;
            KitDraw.caps(g, font, labels[i], cx, y, KitDraw.DIM, 0.6f);
            KitDraw.caps(g, font, values[i], cx, y + 6, colours[i], 1.1f);
            KitDraw.bar(g, cx, y + 17, 44, 1, bars[i], Float.NaN);
        }
    }

    /** A flat, blocky you: each part washed red as it hurts, bleeding ones pulsing, bandages and splints drawn on. */
    private void figure(GuiGraphicsExtractor g, Body body, int fx, int fy, int mx, int my) {
        g.fill(fx - 6, fy + 32 * U + 4, fx + 16 * U + 6, fy + 32 * U + 8, 0x40000000);
        float pulse = 0.55f + 0.45f * Mth.sin((System.currentTimeMillis() % 1100) / 1100f * (float) (Math.PI * 2));
        for (Region r : Region.values()) {
            int[] s = SHAPE[r.ordinal()];
            int px = fx + s[0] * U, py = fy + s[1] * U, pw = s[2] * U, ph = s[3] * U;
            Part p = body.part(r);
            int sev = p.severity();
            float a = new float[]{0, 0.32f, 0.6f, 0.85f}[sev] * (p.bleeding() ? pulse : 1);
            if (sev > 0) {
                for (int i = 4; i >= 1; i--) {
                    g.fill(px - i * 2, py - i * 2, px + pw + i * 2, py + ph + i * 2, colour(0xFF2A12, a * 0.12f * (5 - i)));
                }
            }
            g.fill(px, py, px + pw, py + ph, SKIN[r.ordinal()]);
            if (r == Region.HEAD) g.fill(px, py, px + pw, py + ph / 4, 0xFF4A3A2C);
            if (r.arm()) g.fill(px, py + ph * 4 / 5, px + pw, py + ph, 0xFFB48A6C);
            if (r.leg()) g.fill(px, py + ph * 5 / 6, px + pw, py + ph, 0xFF2A2522);
            g.fillGradient(px, py, px + pw, py + ph / 3, 0x14FFFFFF, 0x00FFFFFF);
            if (sev > 0) g.fill(px, py, px + pw, py + ph, colour(0xFF3418, a * 0.45f));
            if (p.bandaged()) {
                int by = py + ph * 2 / 5;
                g.fill(px, by, px + pw, by + Math.max(3, ph / 5), 0xEBE9E4D6);
                for (int sx = px + 5; sx < px + pw; sx += 7) g.fill(sx, by, sx + 1, by + Math.max(3, ph / 5), 0xFFD9D2C0);
            }
            if (p.fractured() && p.splinted()) {
                g.fill(px - 2, py + 2, px, py + ph - 2, 0xFF8A6A44);
                g.fill(px + pw, py + 2, px + pw + 2, py + ph - 2, 0xFF8A6A44);
            }
            boolean on = r == selected, over = screen.hovered(px, py, pw, ph, mx, my);
            if (on) KitDraw.frame(g, px - 2, py - 2, pw + 4, ph + 4, 0xFFFFFFFF);
            else if (over) KitDraw.frame(g, px - 1, py - 1, pw + 2, ph + 2, 0xB3FFFFFF);
            screen.hot(px, py, pw, ph, () -> select(r));
        }
    }

    /** The chosen part: what's wrong, and the bandage and splint you carry. */
    private void treat(GuiGraphicsExtractor g, Font font, LocalPlayer player, Body body, int x, int y, int mx, int my) {
        int w = 162;
        KitDraw.heading(g, font, "Treat", x, y, w, true);
        int py = y + 8, ph = 222;
        KitDraw.panel(g, x, py, w, ph);
        Part p = body.part(selected);
        int cx = x + 7, cy = py + 7;
        KitDraw.caps(g, font, NAMES[selected.ordinal()], cx, cy, KitDraw.BRIGHT, 1.3f);
        KitDraw.capsRight(g, font, word(p), x + w - 7, cy + 3, colour(p), 0.85f);
        cy += 17;
        cy += KitDraw.wrap(g, font, about(player, p), cx, cy, w - 14, KitDraw.DIM) + 5;
        g.fill(cx, cy, x + w - 7, cy + 1, 0x24E8E4DA);
        cy += 6;

        int firstAid = Skills.level(player, Skill.FIRST_AID);
        cy = action(g, font, player, Treatment.Kind.BANDAGE, new ItemStack(ModItems.BANDAGE), p, firstAid, cx, cy, w - 14, mx, my);
        cy = action(g, font, player, Treatment.Kind.SPLINT, new ItemStack(ModItems.SPLINT), p, firstAid, cx, cy + 4, w - 14, mx, my);
        g.fill(cx, cy + 3, x + w - 7, cy + 4, 0x24E8E4DA);
        cy += 9;

        ActionHud.Progress doing = ActionHud.progress();
        if (doing != null) {
            KitDraw.caps(g, font, doing.label(), cx, cy, KitDraw.BRIGHT, 0.8f);
            KitDraw.bar(g, cx, cy + 9, w - 14, 2, doing.done(), Float.NaN);
            cy += 16;
        }
        KitDraw.text(g, font, "First Aid level " + firstAid, cx, cy, KitDraw.DIM, 0.85f);
        String next = firstAid < 5 ? "Level 5: one bandage binds two wounds" : firstAid < 8 ? "Level 8: splints set breaks faster" : null;
        if (next != null) KitDraw.text(g, font, next, cx, cy + 9, KitDraw.FAINT, 0.8f);
    }

    /** One treatment's row: the item and how many you carry, what it would do, and its button. Returns the next y. */
    private int action(GuiGraphicsExtractor g, Font font, LocalPlayer player, Treatment.Kind kind, ItemStack icon, Part p,
                       int firstAid, int x, int y, int w, int mx, int my) {
        int carried = 0;
        for (int i : screen.carried()) {
            ItemStack s = screen.menu().slots.get(i).getItem();
            if (kind.is(s)) carried += s.getCount();
        }
        boolean needs = Treatment.needs(p, kind);
        boolean can = needs && carried > 0;
        icon.setCount(Math.max(1, carried));
        KitDraw.tile(g, font, carried > 0 ? icon : ItemStack.EMPTY, x, y, 24, 24, false);
        if (carried == 0) KitDraw.item(g, icon.copyWithCount(1), x + 4, y + 4, 16);
        String name = kind == Treatment.Kind.BANDAGE ? "Bandage" : "Splint";
        KitDraw.caps(g, font, name, x + 29, y + 1, can ? KitDraw.BRIGHT : KitDraw.DIM, 0.9f);
        String what;
        if (kind == Treatment.Kind.BANDAGE) {
            what = needs ? "Stops the bleeding; heals twice as fast" : p.wounded() ? "Already bandaged" : "No wound to bind";
        } else {
            what = needs ? "Knits three times faster" : p.fractured() ? "Already splinted" : "Nothing broken";
        }
        if (needs && carried == 0) what = "You carry none";
        KitDraw.text(g, font, what, x + 29, y + 11, KitDraw.DIM, 0.75f);
        if (needs) {
            String secs = Math.round(Treatment.ticks(kind, firstAid) / 20f) + " s";
            KitDraw.text(g, font, secs, x + 29, y + 19, KitDraw.FAINT, 0.7f);
        }
        String verb = name;
        int bw = Math.round(KitDraw.capsWidth(font, verb, 0.75f)) + 10, bx = x + w - bw, by = y + 1;
        boolean over = can && screen.hovered(bx, by, bw, 12, mx, my);
        if (can) g.fill(bx, by, bx + bw, by + 12, over ? 0xF2E8E4DA : 0x33E8E4DA);
        KitDraw.frame(g, bx, by, bw, 12, can ? 0xB3E8E4DA : 0x33E8E4DA);
        KitDraw.capsCentred(g, font, verb, bx + bw / 2f, by + 3, over ? 0xFF121314 : can ? KitDraw.BRIGHT : KitDraw.FAINT, 0.75f);
        Region r = selected;
        if (can) {
            screen.hot(bx, by, bw, 12, () -> {
                screen.button(Bench.treatButton(kind, r));
                com.zomboidcraft.client.sound.UiSounds.play(com.zomboidcraft.client.sound.UiSounds.CLICK);
            });
        }
        return y + 26;
    }

    // ------------------------------------------------------------------ words

    static String word(Part p) {
        if (p.fractured()) return p.splinted() ? "Splinted" : "Broken";
        if (!p.wounded()) return "Fine";
        if (p.bleeding()) return "Bleeding";
        return p.bandaged() ? "Bandaged" : p.wound().word();
    }

    static int colour(Part p) {
        return switch (p.severity()) {
            case 0 -> 0xFF9CC46A;
            case 1 -> KitDraw.TEXT;
            case 2 -> 0xFFE0A85A;
            default -> 0xFFE0624A;
        };
    }

    private static int colour(int rgb, float a) {
        return (Math.round(Mth.clamp(a, 0, 1) * 255) << 24) | (rgb & 0xFFFFFF);
    }

    /** How far a part has healed: the break if there is one, else the wound. */
    private static float healed(Part p) {
        if (p.fractured()) return 1 - p.fractureLeft() / (float) (Body.FRACTURE_HEAL * 3);
        if (p.wounded() && p.wound().heal > 0) return 1 - p.woundLeft() / (float) p.wound().heal;
        return 1;
    }

    /** Minutes left, rounded up, at the rate it heals now. */
    private static String minutes(LocalPlayer player, Part p) {
        int firstAid = Skills.level(player, Skill.FIRST_AID);
        float seconds;
        if (p.fractured()) seconds = p.fractureLeft() / (float) (p.splinted() ? (firstAid >= 8 ? 4 : 3) : 1);
        else seconds = p.woundLeft() / (p.bandaged() ? 2 + 0.1f * firstAid : 1);
        int m = Math.max(1, (int) Math.ceil(seconds / 60));
        return m + " min";
    }

    private static String line(LocalPlayer player, Part p) {
        if (p.fractured()) return p.splinted() ? "Splinted · knits in " + minutes(player, p) : "No sprinting until it's splinted";
        if (!p.wounded()) return "Nothing wrong here";
        if (p.bleeding()) return p.wound().word() + " and bleeding · bind it";
        return (p.bandaged() ? "Bandaged · heals in " : "Open · heals in ") + minutes(player, p);
    }

    private static String about(LocalPlayer player, Part p) {
        if (p.fractured()) {
            return p.splinted() ? "A broken bone, splinted. It knits in about " + minutes(player, p) + "; you walk a little slower till then."
                    : "A broken bone. No sprinting and much slower until it's splinted.";
        }
        if (!p.wounded()) return "Nothing wrong here.";
        String what = switch (p.wound()) {
            case SCRATCH -> "A scratch";
            case BURN -> "A burn";
            case LACERATION -> "A deep cut";
            case BITE -> "A bite";
            default -> "A wound";
        };
        if (p.bleeding()) return what + ", bleeding. Bind it before you lose more blood.";
        return what + (p.bandaged() ? ", bandaged. Heals in about " + minutes(player, p) + "." : ", open. It heals on its own, slowly.");
    }
}
