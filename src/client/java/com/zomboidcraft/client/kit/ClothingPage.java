package com.zomboidcraft.client.kit;

import com.zomboidcraft.carry.QuickSlots;
import com.zomboidcraft.client.hud.InjuryGlowLayer;
import com.zomboidcraft.clothing.ClothWear;
import com.zomboidcraft.clothing.ClothingSlot;
import com.zomboidcraft.clothing.Clothes;
import com.zomboidcraft.clothing.Garment;
import com.zomboidcraft.clothing.Spot;
import com.zomboidcraft.clothing.Tailoring;
import com.zomboidcraft.survival.Body;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Clothing: the fourteen places you wear things, head to foot (clothing/ClothingSlot), what each piece does and how
 * worn it is, and what it all adds up to: warmth, wind and rain kept out, comfort, speed, armour, and how much of a
 * bite, a scratch or a bullet gets stopped on each part of you. P patches the piece under the mouse, T rips it up
 * (clothing/Tailoring). The survivor stands in the middle, as on the Character page.
 */
final class ClothingPage {
    /** A warm enough outfit for a cold night; the warmth bar fills there. */
    private static final int WARM = 20;
    private static final int ROW = 19;

    private final KitScreen<?> screen;
    /** The slot whose piece the right-hand card describes, when nothing is hovered. */
    private ClothingSlot selected = ClothingSlot.SHIRT;

    ClothingPage(KitScreen<?> screen) {
        this.screen = screen;
    }

    void draw(GuiGraphicsExtractor g, int x, int y, int mx, int my) {
        Font font = screen.mc().font;
        LocalPlayer player = screen.player();
        AbstractContainerMenu menu = screen.menu();
        Clothes.Totals totals = Clothes.totals(player);

        // the survivor, as on the Character page
        g.fill(x + 236, y + 312, x + 380, y + 318, 0x40000000);
        InjuryGlowLayer.capturing = true;
        try {
            InventoryScreen.extractEntityInInventoryFollowsMouse(g, x + 222, y + 62, x + 394, y + 322, 112, 0.0625f, mx, my, player);
        } finally {
            InjuryGlowLayer.capturing = false;
        }

        // what you're wearing, head to foot
        KitDraw.heading(g, font, "Worn", x + 37, y + 34, 172, false);
        ClothingSlot hovered = null;
        for (int i = 0; i < ClothingSlot.ALL.size(); i++) {
            ClothingSlot slot = ClothingSlot.ALL.get(i);
            int menuSlot = slot.menuSlot();
            if (menuSlot >= menu.slots.size()) continue;
            int tx = x + 37, ty = y + 44 + i * ROW;
            ItemStack stack = menu.slots.get(menuSlot).getItem();
            boolean over = screen.hovered(tx, ty, 172, ROW - 2, mx, my);
            if (over) hovered = slot;
            if (slot == selected) g.fill(tx, ty, tx + 172, ty + ROW - 2, 0x14E8E4DA);
            KitDraw.tile(g, font, stack, tx, ty, 17, 17, over);
            KitDraw.caps(g, font, slot.label, tx + 22, ty + 1, slot == selected ? KitDraw.LINK : KitDraw.DIM, 0.58f);
            Garment piece = Clothes.of(stack);
            if (stack.isEmpty()) {
                KitDraw.text(g, font, "Nothing worn", tx + 22, ty + 8, KitDraw.FAINT, 0.8f);
            } else {
                String name = stack.getHoverName().getString();
                KitDraw.text(g, font, name, tx + 22, ty + 8, KitDraw.BRIGHT, KitDraw.width(font, name, 0.8f) > 100 ? 0.66f : 0.8f);
                KitDraw.textRight(g, font, brief(piece, stack), tx + 170, ty + 1, KitDraw.DIM, 0.58f);
                if (piece != null && ClothWear.of(stack).holeCount() > 0) {
                    KitDraw.textRight(g, font, ClothWear.of(stack).holeCount() + " holes", tx + 170, ty + 9, KitDraw.WARN, 0.58f);
                }
            }
            Slot target = menu.slots.get(menuSlot);
            String label = slot.label;
            screen.hot(tx, ty, 172, ROW - 2, stack, () -> {
                        selected = slot;
                        screen.pick(label, menuSlot, screen.character.choices(menuSlot, target::mayPlace, null), tx + 176, ty, 26, 26);
                    }, stack.isEmpty() ? null : () -> screen.unequip(menuSlot),
                    stack.isEmpty() ? label + ": nothing worn" : null, menuSlot);
        }

        // what it all adds up to
        int rx = x + 400, rw = 179;
        KitDraw.heading(g, font, "All together", rx, y + 34, rw, true);
        int ry = y + 46;
        ry = stat(g, font, "Warmth", String.valueOf(totals.warmth()), totals.warmth() / (float) WARM, rx, ry, rw);
        ry = stat(g, font, "Wind kept out", pct(totals.wind()), totals.wind(), rx, ry, rw);
        ry = stat(g, font, "Rain kept off", pct(totals.water()), totals.water(), rx, ry, rw);
        ry = stat(g, font, "Comfort", pct(1 - totals.discomfort()), 1 - totals.discomfort(), rx, ry, rw);
        ry = stat(g, font, "Slowed", totals.run() > 0 ? "-" + pct(totals.run()) + " run" : "Not at all", totals.run() * 4, rx, ry, rw);
        ry = stat(g, font, "Armour", String.valueOf(totals.armour()), totals.armour() / 20f, rx, ry, rw);
        ry += 2;
        KitDraw.caps(g, font, "Stops", rx, ry, KitDraw.FAINT, 0.6f);
        KitDraw.capsRight(g, font, "Bite", rx + rw - 72, ry, KitDraw.FAINT, 0.6f);
        KitDraw.capsRight(g, font, "Scratch", rx + rw - 36, ry, KitDraw.FAINT, 0.6f);
        KitDraw.capsRight(g, font, "Bullet", rx + rw, ry, KitDraw.FAINT, 0.6f);
        ry += 8;
        for (Body.Region r : Body.Region.values()) {
            KitDraw.text(g, font, cap(r.label), rx, ry, KitDraw.TEXT, 0.8f);
            guard(g, font, totals.bite().getOrDefault(r, 0f), rx + rw - 108, ry, 36);
            guard(g, font, totals.scratch().getOrDefault(r, 0f), rx + rw - 72, ry, 36);
            guard(g, font, totals.bullet().getOrDefault(r, 0f), rx + rw - 36, ry, 36);
            ry += 11;
        }

        // the piece under the mouse, or the last one picked
        ClothingSlot shown = hovered != null ? hovered : selected;
        int sm = shown.menuSlot();
        ItemStack stack = sm < menu.slots.size() ? menu.slots.get(sm).getItem() : ItemStack.EMPTY;
        ry += 6;
        KitDraw.heading(g, font, shown.label, rx, ry, rw, true);
        ry += 11;
        if (stack.isEmpty()) {
            KitDraw.wrap(g, font, "Nothing worn here. Click the row to put something on.", rx, ry, rw, KitDraw.FAINT);
        } else {
            KitDraw.caps(g, font, stack.getHoverName().getString(), rx, ry, KitDraw.BRIGHT, 1f);
            ry += 11;
            for (String line : lines(Clothes.of(stack), stack)) {
                KitDraw.text(g, font, line, rx, ry, KitDraw.TEXT, 0.8f);
                ry += 9;
            }
        }

        // what the keys do
        int hx = x + 37, hy = y + KitScreen.H - 14;
        hx += KitDraw.hint(g, font, "LMB", "Change", hx, hy);
        hx += KitDraw.hint(g, font, "RMB", "Take off", hx, hy);
        hx += KitDraw.hint(g, font, "P", "Patch", hx, hy);
        hx += KitDraw.hint(g, font, "T", "Rip up", hx, hy);
        hx += KitDraw.hint(g, font, "W", "Wash", hx, hy);
        screen.pagesAndClose(g, font, hx, hy);
    }

    private static int stat(GuiGraphicsExtractor g, Font font, String label, String value, float fill, int x, int y, int w) {
        KitDraw.caps(g, font, label, x, y, KitDraw.DIM, 0.62f);
        KitDraw.capsRight(g, font, value, x + w, y - 1, KitDraw.BRIGHT, 0.95f);
        KitDraw.bar(g, x, y + 9, w, 1, Math.min(1, fill), Float.NaN);
        return y + 15;
    }

    private static void guard(GuiGraphicsExtractor g, Font font, float v, int x, int y, int w) {
        g.fill(x, y + 7, x + w - 20, y + 8, 0x33E8E4DA);
        g.fill(x, y + 7, x + Math.round((w - 20) * v), y + 8, v >= 0.5f ? KitDraw.GOOD : KitDraw.TEXT);
        KitDraw.textRight(g, font, pct(v), x + w, y + 1, v > 0 ? KitDraw.BRIGHT : KitDraw.FAINT, 0.75f);
    }

    /** One short line for the row: the thing this piece is best at. */
    private static String brief(@Nullable Garment p, ItemStack stack) {
        if (p == null) return "";
        if (p.slot() == ClothingSlot.BELT) return QuickSlots.pouches(stack) ? "+4 slots" : "+2 slots";
        List<String> out = new ArrayList<>();
        if (p.bite() > 0) out.add("Bite " + pct(p.bite()));
        if (p.armour() > 0) out.add("Arm " + p.armour());
        if (p.warmth() > 0 && out.size() < 2) out.add("Warm " + p.warmth());
        return String.join(" · ", out);
    }

    /** Everything a piece does, for the card on the right. */
    private static List<String> lines(@Nullable Garment p, ItemStack stack) {
        List<String> out = new ArrayList<>();
        if (p == null) {
            out.add("Not clothing: it does nothing worn.");
            return out;
        }
        if (p.slot() == ClothingSlot.BELT) {
            out.add("Opens two hip slots" + (QuickSlots.pouches(stack) ? " and two pouches" : ""));
            out.add("Holds a hip holster and a sheath");
            return out;
        }
        if (!p.covers().isEmpty()) {
            List<String> spots = new ArrayList<>();
            for (Spot spot : p.covers()) spots.add(spot.label);
            out.add("Covers the " + String.join(", ", spots));
            out.add("Stops " + pct(p.bite()) + " of bites, " + pct(p.scratch()) + " of scratches");
        }
        if (p.bullet() > 0) out.add("Stops " + pct(p.bullet()) + " of bullets");
        if (p.insulation() > 0) out.add("Warmth +" + p.warmth() + ", keeps out " + pct(p.wind()) + " of the wind");
        if (p.water() > 0) out.add("Keeps off " + pct(p.water()) + " of the rain");
        if (p.discomfort() > 0) out.add(p.discomfort() >= 0.25f ? "Uncomfortable to wear" : "A little uncomfortable");
        if (p.run() > 0 || p.swing() > 0) {
            out.add("Slows you: " + pct(p.run()) + " running" + (p.swing() > 0 ? ", " + pct(p.swing()) + " swinging" : ""));
        }
        if (p.armour() > 0) out.add("Armour +" + p.armour());
        if (out.isEmpty()) out.add("Looks the part, guards nothing");
        ClothWear wear = ClothWear.of(stack);
        if (wear.ruined(p)) {
            out.add("Ruined: guards nothing now");
        } else {
            String worn = "Condition " + pct(wear.left(p));
            if (wear.holeCount() > 0) worn += ", " + wear.holeCount() + (wear.holeCount() == 1 ? " hole" : " holes");
            if (wear.patchCount() > 0) worn += ", " + wear.patchCount() + (wear.patchCount() == 1 ? " patch" : " patches");
            out.add(worn);
        }
        com.zomboidcraft.clothing.ClothDirt dirt = com.zomboidcraft.clothing.ClothDirt.of(stack);
        if (!dirt.describe().isEmpty()) out.add(dirt.describe());
        if (Tailoring.patchable(p) && Tailoring.nextPatch(p, wear) != null) out.add("P to patch: sewing kit, thread, a strip");
        if (!dirt.clean()) out.add("W to wash: by water, soap for the blood");
        return out;
    }

    private static String pct(float v) {
        return Math.round(v * 100) + "%";
    }

    private static String cap(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
