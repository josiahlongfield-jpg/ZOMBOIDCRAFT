package com.zomboidcraft.client.kit;

import com.zomboidcraft.carry.QuickSlots;
import com.zomboidcraft.client.hud.InjuryGlowLayer;
import com.zomboidcraft.clothing.ClothingSlot;
import com.zomboidcraft.clothing.Clothes;
import com.zomboidcraft.clothing.Garment;
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
 * Clothing: the eleven places you wear things, head to foot (clothing/ClothingSlot), what each piece does, and what it
 * all adds up to: warmth, rain kept off, armour and how much of a bite or a scratch gets through on each part of you.
 * The survivor stands in the middle, as on the Character page.
 */
final class ClothingPage {
    /** A warm enough outfit for a cold night; the warmth bar fills there. */
    private static final int WARM = 20;
    private static final int ROW = 22;

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
            KitDraw.tile(g, font, stack, tx, ty, 20, 20, over);
            KitDraw.caps(g, font, slot.label, tx + 25, ty + 2, slot == selected ? KitDraw.LINK : KitDraw.DIM, 0.6f);
            Garment piece = Clothes.of(stack);
            if (stack.isEmpty()) {
                KitDraw.text(g, font, "Nothing worn", tx + 25, ty + 10, KitDraw.FAINT, 0.85f);
            } else {
                String name = stack.getHoverName().getString();
                KitDraw.text(g, font, name, tx + 25, ty + 10, KitDraw.BRIGHT, KitDraw.width(font, name, 0.85f) > 100 ? 0.7f : 0.85f);
                KitDraw.textRight(g, font, brief(piece, stack), tx + 170, ty + 2, KitDraw.DIM, 0.62f);
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
        ry = stat(g, font, "Rain kept off", pct(totals.water()), totals.water(), rx, ry, rw);
        ry = stat(g, font, "Armour", String.valueOf(totals.armour()), totals.armour() / 20f, rx, ry, rw);
        ry += 2;
        KitDraw.caps(g, font, "Stops", rx, ry, KitDraw.FAINT, 0.6f);
        KitDraw.capsRight(g, font, "Bite", rx + rw - 46, ry, KitDraw.FAINT, 0.6f);
        KitDraw.capsRight(g, font, "Scratch", rx + rw, ry, KitDraw.FAINT, 0.6f);
        ry += 8;
        for (Body.Region r : Body.Region.values()) {
            float bite = totals.bite().getOrDefault(r, 0f), scratch = totals.scratch().getOrDefault(r, 0f);
            KitDraw.text(g, font, cap(r.label), rx, ry, KitDraw.TEXT, 0.8f);
            guard(g, font, bite, rx + rw - 86, ry, 40);
            guard(g, font, scratch, rx + rw - 40, ry, 40);
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
        screen.pagesAndClose(g, font, hx, hy);
    }

    private static int stat(GuiGraphicsExtractor g, Font font, String label, String value, float fill, int x, int y, int w) {
        KitDraw.caps(g, font, label, x, y, KitDraw.DIM, 0.62f);
        KitDraw.capsRight(g, font, value, x + w, y - 1, KitDraw.BRIGHT, 0.95f);
        KitDraw.bar(g, x, y + 9, w, 1, Math.min(1, fill), Float.NaN);
        return y + 15;
    }

    private static void guard(GuiGraphicsExtractor g, Font font, float v, int x, int y, int w) {
        g.fill(x, y + 7, x + w - 18, y + 8, 0x33E8E4DA);
        g.fill(x, y + 7, x + Math.round((w - 18) * v), y + 8, v >= 0.5f ? KitDraw.GOOD : KitDraw.TEXT);
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
            List<String> parts = new ArrayList<>();
            boolean arms = p.covers().contains(Body.Region.LEFT_ARM), legs = p.covers().contains(Body.Region.LEFT_LEG);
            if (p.covers().contains(Body.Region.HEAD)) parts.add("head");
            if (p.covers().contains(Body.Region.TORSO)) parts.add("torso");
            if (arms) parts.add("arms");
            if (legs) parts.add("legs");
            out.add("Covers the " + String.join(", ", parts));
            out.add("Stops " + pct(p.bite()) + " of bites, " + pct(p.scratch()) + " of scratches");
        }
        if (p.warmth() > 0) out.add("Warmth +" + p.warmth());
        if (p.water() > 0) out.add("Keeps off " + pct(p.water()) + " of the rain");
        if (p.armour() > 0) out.add("Armour +" + p.armour());
        if (out.isEmpty()) out.add("Looks the part, guards nothing");
        if (stack.isDamageableItem()) out.add("Condition: " + KitItems.conditionWord(KitItems.condition(stack)));
        return out;
    }

    private static String pct(float v) {
        return Math.round(v * 100) + "%";
    }

    private static String cap(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
