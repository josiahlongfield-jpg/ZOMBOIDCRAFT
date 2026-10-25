package com.zomboidcraft.client.kit;

import com.zomboidcraft.client.hud.Hud;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix3x2fStack;

/** The kit screen's drawing kit: bone text on dark glass, rarity tiles, tier pips and thin bars. */
final class KitDraw {
    static final int TEXT = 0xFFE8E4DA;
    static final int BRIGHT = 0xFFF2EFE8;
    static final int DIM = 0xA8E8E4DA;
    static final int FAINT = 0x4DE8E4DA;
    static final int LINE = 0x4DE8E4DA;
    static final int EDGE = 0x29E8E4DA;
    static final int PANEL = 0xF20C0D0F;
    static final int LINK = 0xFFE0C25E;
    static final int GOOD = 0xFF8FC46A;
    static final int BAD = 0xFFD0675E;
    static final int WARN = 0xFFD9A33A;

    /** Plain text in the kit's face. */
    static void text(GuiGraphicsExtractor g, Font font, String s, float x, float y, int colour, float scale) {
        Matrix3x2fStack pose = g.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.scale(scale, scale);
        g.text(font, Hud.text(s), 0, 0, colour, false);
        pose.popMatrix();
    }

    static float width(Font font, String s, float scale) {
        return font.width(Hud.text(s)) * scale;
    }

    static void textRight(GuiGraphicsExtractor g, Font font, String s, float right, float y, int colour, float scale) {
        text(g, font, s, right - width(font, s, scale), y, colour, scale);
    }

    static void textCentred(GuiGraphicsExtractor g, Font font, String s, float cx, float y, int colour, float scale) {
        text(g, font, s, cx - width(font, s, scale) / 2, y, colour, scale);
    }

    /** Letter-spaced capitals. Returns the width. */
    static float caps(GuiGraphicsExtractor g, Font font, String s, float x, float y, int colour, float scale) {
        return Hud.caps(g, font, s, x, y, colour, 0.9f, scale);
    }

    static float capsWidth(Font font, String s, float scale) {
        return Hud.capsWidth(font, s.toUpperCase(), 0.9f, scale);
    }

    static void capsRight(GuiGraphicsExtractor g, Font font, String s, float right, float y, int colour, float scale) {
        caps(g, font, s, right - capsWidth(font, s, scale), y, colour, scale);
    }

    static void capsCentred(GuiGraphicsExtractor g, Font font, String s, float cx, float y, int colour, float scale) {
        caps(g, font, s, cx - capsWidth(font, s, scale) / 2, y, colour, scale);
    }

    /** Wrapped plain text; returns the height used. */
    static int wrap(GuiGraphicsExtractor g, Font font, String s, int x, int y, int width, int colour) {
        Component c = Hud.text(s);
        int lines = font.split(c, width).size();
        g.textWithWordWrap(font, c, x, y, width, colour, false);
        return lines * 9;
    }

    /** A section heading with a hairline running off it: "WEAPONS ————". */
    static void heading(GuiGraphicsExtractor g, Font font, String s, int x, int y, int w, boolean right) {
        float tw = capsWidth(font, s, 0.85f);
        if (right) {
            capsRight(g, font, s, x + w, y, TEXT, 0.85f);
            g.fill(x, y + 3, Math.round(x + w - tw - 5), y + 4, LINE);
        } else {
            caps(g, font, s, x, y, TEXT, 0.85f);
            g.fill(Math.round(x + tw + 5), y + 3, x + w, y + 4, LINE);
        }
    }

    /** A 1px frame. */
    static void frame(GuiGraphicsExtractor g, int x, int y, int w, int h, int colour) {
        g.fill(x, y, x + w, y + 1, colour);
        g.fill(x, y + h - 1, x + w, y + h, colour);
        g.fill(x, y + 1, x + 1, y + h - 1, colour);
        g.fill(x + w - 1, y + 1, x + w, y + h - 1, colour);
    }

    /** A dark glass panel with a faint edge. */
    static void panel(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, PANEL);
        frame(g, x, y, w, h, EDGE);
    }

    /**
     * An item tile: the rarity's gradient (or an empty socket), a sheen, the item scaled to fit, a condition line along
     * the bottom and the count in the corner. Lights up when hovered.
     */
    static void tile(GuiGraphicsExtractor g, Font font, ItemStack stack, int x, int y, int w, int h, boolean hover) {
        if (stack.isEmpty()) {
            g.fill(x, y, x + w, y + h, 0x8C14161A);
        } else {
            KitItems.Rarity r = KitItems.rarity(stack);
            g.fillGradient(x, y, x + w, y + h, r.tileTop, r.tileBottom);
            g.fillGradient(x, y, x + w, y + h * 2 / 5, 0x12FFFFFF, 0x00FFFFFF);
            float size = Math.min(w, h) - (w > 2 * h ? 2 : Math.max(4, h / 5));
            if (w > 2 * h) size = h * 1.05f;
            item(g, stack, x + w / 2f - size / 2, y + h / 2f - size / 2, size);
            if (stack.isDamageableItem()) {
                float left = KitItems.condition(stack);
                g.fill(x, y + h - 2, x + w, y + h, 0x66000000);
                g.fill(x, y + h - 2, x + Math.max(1, Math.round(w * left)), y + h, left > 0.35f ? 0xD9E8E4DA : BAD);
            }
            if (stack.getCount() > 1) {
                String c = String.valueOf(stack.getCount());
                caps(g, font, c, x + w - 2 - capsWidth(font, c, 0.8f), y + h - 9, BRIGHT, 0.8f);
            }
        }
        frame(g, x, y, w, h, hover ? 0xE6FFFFFF : 0x29E8E4DA);
        if (hover) frame(g, x - 1, y - 1, w + 2, h + 2, 0x40FFFFFF);
    }

    /** An item drawn at any size. */
    static void item(GuiGraphicsExtractor g, ItemStack stack, float x, float y, float size) {
        Matrix3x2fStack pose = g.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.scale(size / 16f, size / 16f);
        g.item(stack, 0, 0);
        pose.popMatrix();
    }

    /** Tier pips: filled diamonds up to the tier, hollow after; nothing for an unrolled item. */
    static int pips(GuiGraphicsExtractor g, int x, int y, int tier, int colour, int size) {
        if (tier <= 0) return 0;
        for (int i = 0; i < 5; i++) {
            diamond(g, x + i * (size * 2 + 1) + size, y + size, size, i < tier ? colour : 0x40E8E4DA);
        }
        return 5 * (size * 2 + 1);
    }

    /** A diamond of radius r centred on cx, cy. */
    static void diamond(GuiGraphicsExtractor g, int cx, int cy, int r, int colour) {
        for (int dy = -r; dy <= r; dy++) {
            int half = r - Math.abs(dy);
            g.fill(cx - half, cy + dy, cx + half + 1, cy + dy + 1, colour);
        }
    }

    /** A thin bar, 0-1, with an optional ghost showing a change (positive is better, drawn green; negative red). */
    static void bar(GuiGraphicsExtractor g, int x, int y, int w, int h, float value, float changed) {
        value = Math.max(0, Math.min(1, value));
        g.fill(x, y, x + w, y + h, 0x1FE8E4DA);
        int v = Math.round(w * value);
        if (Float.isNaN(changed) || Math.abs(changed - value) < 0.005f) {
            g.fill(x, y, x + v, y + h, TEXT);
            return;
        }
        changed = Math.max(0, Math.min(1, changed));
        int c = Math.round(w * changed);
        if (changed > value) {
            g.fill(x, y, x + v, y + h, TEXT);
            g.fill(x + v, y, x + c, y + h, GOOD);
        } else {
            g.fill(x, y, x + c, y + h, TEXT);
            g.fill(x + c, y, x + v, y + h, BAD);
        }
    }

    /** A key cap: a small framed letter. Returns its width. */
    static int key(GuiGraphicsExtractor g, Font font, String k, int x, int y, int colour) {
        int w = Math.max(9, Math.round(capsWidth(font, k, 0.8f)) + 5);
        frame(g, x, y, w, 9, colour);
        capsCentred(g, font, k, x + w / 2f, y + 2, colour, 0.8f);
        return w;
    }

    /** A key cap and what it does, for the footer. Returns the width used. */
    static int hint(GuiGraphicsExtractor g, Font font, String k, String what, int x, int y) {
        int w = key(g, font, k, x, y, DIM);
        text(g, font, what, x + w + 3, y + 1, DIM, 0.9f);
        return w + 3 + Math.round(width(font, what, 0.9f)) + 10;
    }

    static boolean in(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private KitDraw() {
    }
}
