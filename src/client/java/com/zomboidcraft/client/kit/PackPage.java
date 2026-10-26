package com.zomboidcraft.client.kit;

import com.zomboidcraft.ZcConfig;
import com.zomboidcraft.carry.BagContainer;
import com.zomboidcraft.carry.BagSpec;
import com.zomboidcraft.carry.Carry;
import com.zomboidcraft.carry.CarrySlots;
import com.zomboidcraft.carry.Weights;
import com.zomboidcraft.client.sound.UiSounds;
import com.zomboidcraft.loot.Nearby;
import com.zomboidcraft.loot.NearbyMenu;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * The pack: what you carry on the left (one worn bag at a time, your pockets, the belt and the off hand) and the world
 * on the right (the floor and every container in reach, a tab each), with field craft under it. Every tile is a real
 * slot: click to pick up and put down, right click for half, shift-click to send it across.
 */
final class PackPage {
    private static final int LX = 37, LW = 262, RX = 317, RW = 262;
    private static final int GRID_Y = 92, GRID_H = 124, MAX_TILE = 24;

    /** What the left shows: a worn bag, the pockets when there are too many for their row, or old loose kit. */
    private enum Tab { BACK, VEST, WAIST, HAND, POCKETS, LOOSE }

    private final KitScreen<?> screen;
    private Tab tab = Tab.BACK;

    PackPage(KitScreen<?> screen) {
        this.screen = screen;
    }

    void draw(GuiGraphicsExtractor g, int x, int y, int mx, int my) {
        Font font = screen.mc().font;
        carried(g, font, x + LX, y, mx, my);
        NearbyMenu near = screen.near();
        if (near != null) nearby(g, font, near, x + RX, y, mx, my);
        else {
            KitDraw.heading(g, font, "Nearby", x + RX, y + 40, RW, true);
            KitDraw.text(g, font, "Nothing in reach to search.", x + RX, y + 56, KitDraw.DIM, 1f);
        }
        craft(g, font, x + RX, y + 236, mx, my);

        int hx = x + LX, hy = y + KitScreen.H - 14;
        hx += KitDraw.hint(g, font, "LMB", "Take / put", hx, hy);
        hx += KitDraw.hint(g, font, "RMB", "Half", hx, hy);
        hx += KitDraw.hint(g, font, "Shift", "Move across", hx, hy);
        screen.pagesAndClose(g, font, hx, hy);
    }

    // ------------------------------------------------------------------ your side

    private BagContainer bag(Tab t) {
        if (t.ordinal() > Tab.HAND.ordinal() || !(screen.menu() instanceof Carry.Bags bags)) return null;
        BagContainer[] all = bags.zomboidcraft$bags();
        if (t.ordinal() >= all.length) return null;
        BagContainer bag = all[t.ordinal()];
        return bag.spec() != null ? bag : null;
    }

    private static boolean manyPockets() {
        return Carry.pockets() > 9 || !ZcConfig.get().carry.pocketsOnly;
    }

    private boolean locked(Slot slot) {
        return Carry.locked(screen.player(), slot.getContainerSlot());
    }

    private List<Tab> tabs() {
        List<Tab> list = new ArrayList<>();
        for (Tab t : List.of(Tab.BACK, Tab.VEST, Tab.WAIST, Tab.HAND)) if (bag(t) != null) list.add(t);
        if (manyPockets()) list.add(Tab.POCKETS);
        AbstractContainerMenu menu = screen.menu();
        for (int i = InventoryMenu.INV_SLOT_START; i < InventoryMenu.INV_SLOT_END; i++) {
            Slot slot = menu.slots.get(i);
            if (locked(slot) && slot.hasItem()) {
                list.add(Tab.LOOSE);
                break;
            }
        }
        return list;
    }

    private void carried(GuiGraphicsExtractor g, Font font, int x, int y, int mx, int my) {
        AbstractContainerMenu menu = screen.menu();
        LocalPlayer player = screen.player();
        KitDraw.heading(g, font, "Carried", x, y + 40, LW, false);

        // one tab per worn bag, then pockets and loose kit when they need their own
        List<Tab> tabs = tabs();
        if (!tabs.contains(tab)) tab = tabs.isEmpty() ? Tab.BACK : tabs.get(0);
        int tx = x;
        for (Tab t : tabs) {
            BagContainer bag = bag(t);
            String label = bag != null ? com.zomboidcraft.carry.Wear.values()[t.ordinal()].label : t == Tab.POCKETS ? "Pockets" : "Loose";
            int w = 22 + Math.round(KitDraw.capsWidth(font, label, 0.7f));
            boolean on = t == tab, over = screen.hovered(tx, y + 50, w, 18, mx, my);
            g.fill(tx, y + 50, tx + w, y + 68, on ? 0x26E8E4DA : over ? 0x14E8E4DA : 0x0AE8E4DA);
            KitDraw.frame(g, tx, y + 50, w, 18, on ? 0xB3E8E4DA : 0x33E8E4DA);
            if (bag != null) KitDraw.item(g, bag.bag(), tx + 2, y + 51, 16);
            else KitDraw.capsCentred(g, font, t == Tab.POCKETS ? "P" : "!", tx + 10, y + 56, KitDraw.DIM, 0.8f);
            KitDraw.caps(g, font, label, tx + 19, y + 56, on ? KitDraw.BRIGHT : KitDraw.DIM, 0.7f);
            if (on) g.fill(tx, y + 67, tx + w, y + 68, KitDraw.TEXT);
            Tab picked = t;
            screen.hot(tx, y + 50, w, 18, bag == null ? ItemStack.EMPTY : bag.bag(), () -> {
                if (tab != picked) UiSounds.play(UiSounds.TAB);
                tab = picked;
            }, null, null, -1);
            tx += w + 4;
        }

        // the tab's grid
        BagContainer bag = bag(tab);
        int ty = y + 74;
        List<Integer> grid = new ArrayList<>();
        int cols = 9;
        if (bag != null) {
            BagSpec spec = bag.spec();
            cols = spec.columns();
            for (int i = 0; i < menu.slots.size(); i++) {
                if (menu.slots.get(i) instanceof CarrySlots.BagSlot s && s.bag == bag && s.open()) grid.add(i);
            }
            double inside = bag.contentsWeight(-1);
            String name = bag.bag().getHoverName().getString();
            KitDraw.caps(g, font, name, x, ty, KitDraw.BRIGHT, KitDraw.capsWidth(font, name, 1f) > LW - 70 ? 0.8f : 1f);
            float full = (float) Math.min(1, inside / spec.capacity());
            KitDraw.capsRight(g, font, Weights.format(inside) + " / " + Weights.format(spec.capacity()), x + LW, ty + 1,
                    full >= 1 ? KitDraw.WARN : KitDraw.DIM, 0.75f);
            KitDraw.bar(g, x, ty + 11, LW, 1, full, Float.NaN);
        } else if (tab == Tab.POCKETS && tabs.contains(Tab.POCKETS) || tab == Tab.LOOSE && tabs.contains(Tab.LOOSE)) {
            boolean loose = tab == Tab.LOOSE;
            for (int i = InventoryMenu.INV_SLOT_START; i < InventoryMenu.INV_SLOT_END; i++) {
                if (locked(menu.slots.get(i)) == loose) grid.add(i);
            }
            KitDraw.caps(g, font, loose ? "Loose kit" : "Pockets", x, ty, KitDraw.BRIGHT, 1f);
            if (loose) KitDraw.capsRight(g, font, "Take out only", x + LW, ty + 1, KitDraw.BAD, 0.75f);
        } else {
            KitDraw.caps(g, font, "No bag", x, ty, KitDraw.BRIGHT, 1f);
            KitDraw.wrap(g, font, "Wear a bag on your back, vest or waist, or carry one in your off hand. Put one on from the Character page.",
                    x, y + GRID_Y + 4, LW, KitDraw.DIM);
        }
        grid(g, font, grid, cols, x, y + GRID_Y, LW, GRID_H, mx, my, -1);

        // pockets in their own row when there are few
        int py = y + 222;
        if (!manyPockets()) {
            List<Integer> pockets = new ArrayList<>();
            double weight = 0;
            for (int i = InventoryMenu.INV_SLOT_START; i < InventoryMenu.INV_SLOT_END; i++) {
                Slot slot = menu.slots.get(i);
                if (locked(slot)) continue;
                pockets.add(i);
                weight += Weights.of(slot.getItem());
            }
            KitDraw.heading(g, font, "Pockets", x, py, LW - 40, false);
            KitDraw.textRight(g, font, Weights.format(weight), x + LW, py - 1, KitDraw.DIM, 0.85f);
            grid(g, font, pockets, 9, x, py + 9, LW, 26, mx, my, -1);
        }

        // the belt and the off hand
        int by = y + 260;
        KitDraw.heading(g, font, "Belt & hands", x, by, LW - 40, false);
        KitDraw.textRight(g, font, "1-9", x + LW, by - 1, KitDraw.FAINT, 0.85f);
        List<Integer> belt = new ArrayList<>();
        for (int i = InventoryMenu.USE_ROW_SLOT_START; i < InventoryMenu.USE_ROW_SLOT_END; i++) belt.add(i);
        int held = InventoryMenu.USE_ROW_SLOT_START + player.getInventory().getSelectedSlot();
        grid(g, font, belt, 9, x, by + 9, 9 * (MAX_TILE + 2), 26, mx, my, held);
        int ox = x + LW - MAX_TILE;
        tile(g, font, InventoryMenu.SHIELD_SLOT, ox, by + 9, MAX_TILE, mx, my, false);
        KitDraw.capsCentred(g, font, "Off", ox + MAX_TILE / 2f, by + 9 + MAX_TILE + 2, KitDraw.FAINT, 0.6f);

        // what it all weighs
        if (ZcConfig.get().carry.weight) {
            double load = Carry.load(player), cap = Carry.capacity();
            float ratio = (float) (load / cap);
            int tier = Carry.tier(ratio);
            int ly = y + 302;
            KitDraw.caps(g, font, Weights.format(load) + " / " + Weights.format(cap), x, ly, tier > 0 ? KitDraw.WARN : KitDraw.BRIGHT, 0.85f);
            if (tier > 0) {
                String[] words = {"", "Heavy load", "Very heavy", "Overloaded", "Crushing"};
                KitDraw.capsRight(g, font, words[Math.min(tier, 4)], x + LW, ly, KitDraw.BAD, 0.75f);
            }
            KitDraw.bar(g, x, ly + 10, LW, 2, ratio / 2f, Float.NaN);
            g.fill(x + LW / 2, ly + 8, x + LW / 2 + 1, ly + 14, 0xCCE8E4DA);
        }
    }

    // ------------------------------------------------------------------ the world's side

    private void nearby(GuiGraphicsExtractor g, Font font, NearbyMenu near, int x, int y, int mx, int my) {
        AbstractContainerMenu menu = screen.menu();
        List<Nearby.Source> sources = near.sources();
        KitDraw.heading(g, font, "Nearby", x, y + 40, RW, true);
        if (sources.isEmpty()) {
            KitDraw.text(g, font, "Nothing in reach to search.", x, y + 56, KitDraw.DIM, 1f);
            return;
        }
        int k = Math.max(0, Math.min(near.selected, sources.size() - 1));

        // a tab for the floor and for each container in reach
        int tx = x;
        for (int s = 0; s < sources.size(); s++) {
            Nearby.Source source = sources.get(s);
            boolean on = s == k, over = screen.hovered(tx, y + 50, 22, 18, mx, my);
            if (tx + 22 > x + RW) break;
            g.fill(tx, y + 50, tx + 22, y + 68, on ? 0x26E8E4DA : over ? 0x14E8E4DA : 0x0AE8E4DA);
            KitDraw.frame(g, tx, y + 50, 22, 18, on ? 0xB3E8E4DA : 0x33E8E4DA);
            if (!source.icon().isEmpty()) KitDraw.item(g, source.icon(), tx + 3, y + 51, 16);
            else KitDraw.capsCentred(g, font, source.floor() ? "FL" : source.name().getString().substring(0, 1), tx + 11, y + 56,
                    on ? KitDraw.BRIGHT : KitDraw.DIM, 0.75f);
            if (on) g.fill(tx, y + 67, tx + 22, y + 68, KitDraw.TEXT);
            int pick = s;
            screen.hot(tx, y + 50, 22, 18, ItemStack.EMPTY, () -> select(near, pick), null, source.name().getString(), -1);
            tx += 26;
        }

        Nearby.Source source = sources.get(k);
        int ty = y + 74;
        List<Integer> grid = new ArrayList<>();
        double inside = 0;
        for (int i = near.sourceStart(k); i < near.sourceEnd(k); i++) {
            grid.add(i);
            inside += Weights.of(menu.slots.get(i).getItem());
        }
        String name = source.name().getString();
        KitDraw.caps(g, font, name, x, ty, KitDraw.BRIGHT, KitDraw.capsWidth(font, name, 1f) > RW - 110 ? 0.8f : 1f);
        boolean any = inside > 0 || grid.stream().anyMatch(i -> menu.slots.get(i).hasItem());
        if (any) {
            String label = "Take all";
            int bw = Math.round(KitDraw.capsWidth(font, label, 0.75f)) + 10, bx = x + RW - bw, bby = ty - 2;
            boolean over = screen.hovered(bx, bby, bw, 12, mx, my);
            g.fill(bx, bby, bx + bw, bby + 12, over ? 0xF2E8E4DA : 0x33E8E4DA);
            KitDraw.frame(g, bx, bby, bw, 12, 0xB3E8E4DA);
            KitDraw.capsCentred(g, font, label, bx + bw / 2f, bby + 3, over ? 0xFF121314 : KitDraw.BRIGHT, 0.75f);
            screen.hot(bx, bby, bw, 12, () -> {
                screen.button(NearbyMenu.TAKE_ALL + k);
                UiSounds.play(UiSounds.ITEM_MOVE);
            });
            KitDraw.textRight(g, font, Weights.format(inside), bx - 6, ty, KitDraw.DIM, 0.85f);
        }
        g.fill(x, ty + 11, x + RW, ty + 12, KitDraw.LINE);
        grid(g, font, grid, 9, x, y + GRID_Y, RW, GRID_H, mx, my, -1);
        if (!any) {
            String line = source.floor() ? "Nothing on the ground. Put things here to set them down." : "Empty.";
            KitDraw.wrap(g, font, line, x, y + GRID_Y + rows(grid.size(), 9, RW, GRID_H) + 6, RW, KitDraw.FAINT);
        }
    }

    private void select(NearbyMenu near, int source) {
        if (near.selected == source) return;
        near.selected = source;
        screen.mc().gameMode.handleInventoryButtonClick(near.containerId, source);
        UiSounds.play(UiSounds.TAB);
    }

    /** The 2x2 grid and what it makes. */
    private void craft(GuiGraphicsExtractor g, Font font, int x, int y, int mx, int my) {
        KitDraw.heading(g, font, "Field craft", x, y, RW, true);
        int t = MAX_TILE;
        for (int i = 0; i < 4; i++) {
            tile(g, font, InventoryMenu.CRAFT_SLOT_START + i, x + (i % 2) * (t + 2), y + 10 + (i / 2) * (t + 2), t, mx, my, false);
        }
        KitDraw.text(g, font, "›", x + 2 * (t + 2) + 10, y + 10 + t - 4, KitDraw.DIM, 1.2f);
        tile(g, font, InventoryMenu.RESULT_SLOT, x + 2 * (t + 2) + 30, y + 10 + (t + 2) / 2, t, mx, my, false);
    }

    // ------------------------------------------------------------------ tiles

    /** How tall a grid of n slots draws, at the tile size {@link #grid} would pick. */
    private static int rows(int n, int cols, int w, int h) {
        int rows = Math.max(1, (n + cols - 1) / cols);
        return rows * (size(n, cols, w, h) + 2);
    }

    private static int size(int n, int cols, int w, int h) {
        int rows = Math.max(1, (n + cols - 1) / cols);
        return Math.max(12, Math.min(MAX_TILE, Math.min(w / cols - 2, h / rows - 2)));
    }

    private void grid(GuiGraphicsExtractor g, Font font, List<Integer> slots, int cols, int x, int y, int w, int h, int mx, int my,
                      int marked) {
        if (slots.isEmpty()) return;
        int t = size(slots.size(), cols, w, h);
        for (int k = 0; k < slots.size(); k++) {
            int slot = slots.get(k);
            tile(g, font, slot, x + (k % cols) * (t + 2), y + (k / cols) * (t + 2), t, mx, my, slot == marked);
        }
    }

    private void tile(GuiGraphicsExtractor g, Font font, int slot, int x, int y, int t, int mx, int my, boolean marked) {
        AbstractContainerMenu menu = screen.menu();
        if (slot < 0 || slot >= menu.slots.size()) return;
        ItemStack stack = menu.slots.get(slot).getItem();
        KitDraw.tile(g, font, stack, x, y, t, t, screen.hovered(x, y, t, t, mx, my));
        if (marked) g.fill(x, y + t, x + t, y + t + 1, KitDraw.LINK);
        if (t >= 20) KitDraw.pips(g, x + 2, y + 2, KitItems.tier(stack), KitDraw.TEXT, 1);
        screen.hot(x, y, t, t, stack, () -> screen.slotClick(slot, 0), () -> screen.slotClick(slot, 1), null, slot);
    }
}
