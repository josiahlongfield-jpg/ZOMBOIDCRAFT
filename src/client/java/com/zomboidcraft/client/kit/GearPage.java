package com.zomboidcraft.client.kit;

import com.zomboidcraft.carry.BagContainer;
import com.zomboidcraft.carry.Carry;
import com.zomboidcraft.carry.GearStore;
import com.zomboidcraft.carry.QuickSlots;
import com.zomboidcraft.carry.Weights;
import com.zomboidcraft.ZcConfig;
import com.zomboidcraft.loot.NearbyMenu;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;

/**
 * Tarkov's gear tab, copied (Josiah 2026-11-07, approved mock-up artifact 5dJAYHen2tryTazFePaHaK): worn gear on the left
 * (earpiece, headwear, face cover; armband, body armour, eyewear; the weapon on the sling and on the back; holster and
 * scabbard; top and trousers), what you carry in the middle (the rig and its pouches, four one-cell pockets, the
 * backpack's grid, three special slots) and the floor and containers in reach on the right. Drawn with Tarkov's own
 * art when it's on this computer ({@link TarkovArt}). No secure container (Josiah 09:39: your loot waits on your body).
 */
final class GearPage {
    private static final int SQ_W = 52, SQ_H = 44, GAP = 6, HDR = 9;
    private static final int LX = 12, MX = 190, RX = 410, RW = 194;

    private final KitScreen<?> screen;

    GearPage(KitScreen<?> screen) {
        this.screen = screen;
    }

    private static int gear(int g) {
        return InventoryMenu.SHIELD_SLOT + 1 + g;
    }

    private static int quick(int q) {
        return InventoryMenu.USE_ROW_SLOT_START + q;
    }

    void draw(GuiGraphicsExtractor g, int x, int y, int mx, int my) {
        Font font = screen.mc().font;
        TarkovArt.draw(g, "bg", x + 4, y + 30, KitScreen.W - 8, KitScreen.H - 46, 0xD0FFFFFF);
        worn(g, font, x + LX, y + 34, mx, my);
        carried(g, font, x + MX, y + 34, mx, my);
        NearbyMenu near = screen.near();
        if (near != null) screen.pack.nearby(g, font, near, x + RX, y, RW, mx, my);
        else {
            header(g, font, "Nearby", x + RX, y + 36, RW);
            KitDraw.text(g, font, "Nothing in reach to search.", x + RX, y + 50, KitDraw.DIM, 1f);
        }

        int hx = x + LX, hy = y + KitScreen.H - 14;
        hx += KitDraw.hint(g, font, "Drag", "Move", hx, hy);
        hx += KitDraw.hint(g, font, "R", "Turn", hx, hy);
        hx += KitDraw.hint(g, font, "Shift+LMB", "Quick move", hx, hy);
        hx += KitDraw.hint(g, font, "Shift+RMB", "Drop", hx, hy);
        screen.pagesAndClose(g, font, hx, hy);
    }

    // ------------------------------------------------------------------ worn

    private void worn(GuiGraphicsExtractor g, Font font, int x, int y, int mx, int my) {
        int row = HDR + SQ_H + 5;
        slot(g, font, gear(GearStore.EARPIECE), "Earpiece", "earpiece", x, y, SQ_W, SQ_H, mx, my);
        slot(g, font, InventoryMenu.ARMOR_SLOT_START, "Headwear", "headwear", x + SQ_W + GAP, y, SQ_W, SQ_H, mx, my);
        slot(g, font, gear(GearStore.FACE), "Face cover", "face", x + 2 * (SQ_W + GAP), y, SQ_W, SQ_H, mx, my);
        y += row;
        slot(g, font, gear(GearStore.ARMBAND), "Armband", "armband", x, y, SQ_W, SQ_H, mx, my);
        slot(g, font, gear(GearStore.ARMOUR), "Body armor", "armour", x + SQ_W + GAP, y, SQ_W, SQ_H, mx, my);
        slot(g, font, gear(GearStore.EYES), "Eyewear", "eyes", x + 2 * (SQ_W + GAP), y, SQ_W, SQ_H, mx, my);
        y += row;
        int wide = 3 * SQ_W + 2 * GAP;
        slot(g, font, quick(QuickSlots.HANDS), "On sling", null, x, y, wide, 26, mx, my);
        y += HDR + 26 + 5;
        slot(g, font, quick(QuickSlots.BACK), "On back", null, x, y, wide, 26, mx, my);
        y += HDR + 26 + 5;
        int small = (wide - 3 * 4) / 4;
        slot(g, font, quick(QuickSlots.HOLSTER), "Holster", "holster", x, y, small, 38, mx, my);
        slot(g, font, quick(QuickSlots.SHEATH), "Scabbard", "scabbard", x + small + 4, y, small, 38, mx, my);
        slot(g, font, InventoryMenu.ARMOR_SLOT_START + 1, "Top", null, x + 2 * (small + 4), y, small, 38, mx, my);
        slot(g, font, InventoryMenu.ARMOR_SLOT_START + 2, "Trousers", null, x + 3 * (small + 4), y, small, 38, mx, my);
        y += HDR + 38 + 6;

        // the weight, as Tarkov shows it under the gear
        if (ZcConfig.get().carry.weight) {
            LocalPlayer player = screen.player();
            double load = Carry.load(player), cap = Carry.capacity();
            int tier = Carry.tier((float) (load / cap));
            TarkovArt.fit(g, "kg", x, y, 9, 10, 0xFFFFFFFF);
            KitDraw.caps(g, font, Weights.format(load) + " / " + Weights.format(cap), x + 12, y + 1,
                    tier >= 3 ? KitDraw.BAD : tier > 0 ? KitDraw.WARN : KitDraw.BRIGHT, 0.85f);
            KitDraw.bar(g, x, y + 12, wide, 2, (float) Math.min(1, load / cap / 2), Float.NaN);
        }
    }

    // ------------------------------------------------------------------ carried

    private void carried(GuiGraphicsExtractor g, Font font, int x, int y, int mx, int my) {
        int gw = 210 - 46;
        // the rig: its slot, then its pouches
        header(g, font, "Tactical rig", x, y, 210);
        slot(g, font, gear(GearStore.VEST), null, "rig", x, y + HDR + 2, 40, 40, mx, my);
        BagContainer rig = bag(1);
        if (rig != null) screen.pack.cells(g, font, rig, x + 46, y + HDR + 2, gw, 70, 16, mx, my);
        else KitDraw.text(g, font, "No rig", x + 46, y + HDR + 16, KitDraw.FAINT, 0.9f);
        y += HDR + 2 + 72 + 4;

        // pockets: four single cells
        header(g, font, "Pockets", x, y, 210);
        AbstractContainerMenu menu = screen.menu();
        int px = x;
        int shown = 0;
        for (int i = InventoryMenu.INV_SLOT_START; i < InventoryMenu.INV_SLOT_END && shown < Math.max(1, Carry.pockets()); i++) {
            if (Carry.locked(screen.player(), menu.slots.get(i).getContainerSlot())) continue;
            tile(g, font, i, px, y + HDR + 2, 18, 18, null, mx, my);
            px += 20;
            shown++;
        }
        y += HDR + 2 + 18 + 5;

        // the backpack and its grid
        header(g, font, "Backpack", x, y, 210);
        slot(g, font, gear(GearStore.BACK), null, "backpack", x, y + HDR + 2, 40, 40, mx, my);
        BagContainer pack = bag(0);
        if (pack != null) screen.pack.cells(g, font, pack, x + 46, y + HDR + 2, gw, 116, 16, mx, my);
        else KitDraw.text(g, font, "No backpack", x + 46, y + HDR + 16, KitDraw.FAINT, 0.9f);
        y += HDR + 2 + 118 + 4;

        // special slots
        header(g, font, "Special slots", x, y, 210);
        for (int s = 0; s < GearStore.SPECIAL_COUNT; s++) {
            tile(g, font, gear(GearStore.SPECIAL + s), x + s * 22, y + HDR + 2, 20, 20, "special", mx, my);
        }
    }

    private BagContainer bag(int which) {
        if (!(screen.menu() instanceof Carry.Bags bags)) return null;
        BagContainer[] all = bags.zomboidcraft$bags();
        if (which >= all.length) return null;
        BagContainer bag = all[which];
        return bag.spec() != null ? bag : null;
    }

    // ------------------------------------------------------------------ pieces

    /** A section header, as Tarkov's strip with its slanted end. */
    private static void header(GuiGraphicsExtractor g, Font font, String label, int x, int y, int w) {
        int sw = Math.min(w, Math.round(KitDraw.capsWidth(font, label, 0.62f)) + 22);
        if (!TarkovArt.strip(g, "hpanel", x, y, sw, HDR)) {
            g.fill(x, y, x + sw, y + HDR, 0xCC101112);
        }
        KitDraw.caps(g, font, label, x + 3, y + 2, KitDraw.BRIGHT, 0.62f);
    }

    /** A worn slot: its header, Tarkov's empty slot with the silhouette of what goes there, or what's in it. */
    private void slot(GuiGraphicsExtractor g, Font font, int slot, String label, String silhouette, int x, int y, int w, int h,
                      int mx, int my) {
        if (label != null) {
            int sw = w;
            if (!TarkovArt.strip(g, "hslot", x, y, sw, HDR)) g.fill(x, y, x + sw, y + HDR, 0xCC101112);
            KitDraw.caps(g, font, label, x + 3, y + 2, KitDraw.BRIGHT, 0.58f);
            y += HDR;
        }
        tile(g, font, slot, x, y, w, h, silhouette, mx, my);
    }

    private void tile(GuiGraphicsExtractor g, Font font, int slot, int x, int y, int w, int h, String silhouette, int mx, int my) {
        AbstractContainerMenu menu = screen.menu();
        if (slot < 0 || slot >= menu.slots.size()) return;
        ItemStack stack = menu.slots.get(slot).getItem();
        boolean over = screen.hovered(x, y, w, h, mx, my);
        if (stack.isEmpty()) {
            if (!TarkovArt.draw(g, "slotfill", x, y, w, h)) g.fill(x, y, x + w, y + h, 0xD0141516);
            if (silhouette != null) TarkovArt.fit(g, silhouette, x + 3, y + 3, w - 6, h - 6, 0x8CFFFFFF);
            ItemStack carried = menu.getCarried();
            int edge = !carried.isEmpty() && over ? (menu.slots.get(slot).mayPlace(carried) ? 0xFF7FBF4D : 0xFFC4452F)
                    : over ? 0xE6FFFFFF : 0x40FFFFFF;
            KitDraw.frame(g, x, y, w, h, edge);
        } else {
            KitDraw.tile(g, font, stack, x, y, w, h, over);
        }
        screen.hot(x, y, w, h, stack, () -> screen.slotClick(slot, 0), () -> screen.slotClick(slot, 1), null, slot);
    }
}
