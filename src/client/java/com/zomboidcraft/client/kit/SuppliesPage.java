package com.zomboidcraft.client.kit;

import com.zomboidcraft.carry.BagContainer;
import com.zomboidcraft.carry.BagSpec;
import com.zomboidcraft.carry.Carry;
import com.zomboidcraft.carry.CarrySlots;
import com.zomboidcraft.carry.Weights;
import com.zomboidcraft.ModItems;
import com.zomboidcraft.combat.MagazineItem;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Everything you carry, wherever it is, counted together and sorted by kind: medical, food, ammunition, gun parts and
 * the rest. Pick one to see what it is, how much of it you have and where it's packed; how full each bag is runs along
 * the bottom.
 */
final class SuppliesPage {
    private static final int COLS = 8, TILE = 31, GAP = 4, ROWS = 6;
    private static final int GRID_X = 135, GRID_Y = 54;

    /** One kind of thing you carry: how many in all, and the slots it's in. */
    record Entry(ItemStack stack, int count, List<Integer> slots, KitItems.Category category) {
    }

    private final KitScreen<?> screen;
    private KitItems.Category category = KitItems.Category.ALL;
    private ItemStack selected = ItemStack.EMPTY;
    private int scroll;

    SuppliesPage(KitScreen<?> screen) {
        this.screen = screen;
    }

    private List<Entry> entries() {
        AbstractContainerMenu menu = screen.menu();
        List<Entry> out = new ArrayList<>();
        for (int i : screen.carried()) {
            ItemStack stack = menu.slots.get(i).getItem();
            if (stack.isEmpty()) continue;
            boolean merged = false;
            for (int k = 0; k < out.size(); k++) {
                Entry e = out.get(k);
                if (ItemStack.isSameItemSameComponents(e.stack(), stack)) {
                    List<Integer> slots = new ArrayList<>(e.slots());
                    slots.add(i);
                    out.set(k, new Entry(e.stack(), e.count() + stack.getCount(), slots, e.category()));
                    merged = true;
                    break;
                }
            }
            if (!merged) out.add(new Entry(stack.copyWithCount(1), stack.getCount(), List.of(i), KitItems.category(stack)));
        }
        out.sort(Comparator.comparingInt((Entry e) -> e.category().ordinal())
                .thenComparing(e -> -KitItems.rarity(e.stack()).ordinal())
                .thenComparing(e -> e.stack().getHoverName().getString()));
        return out;
    }

    void draw(GuiGraphicsExtractor g, int x, int y, int mx, int my) {
        Font font = screen.mc().font;
        List<Entry> all = entries();
        Map<KitItems.Category, Integer> counts = new EnumMap<>(KitItems.Category.class);
        for (Entry e : all) counts.merge(e.category(), 1, Integer::sum);
        totals(g, font, all, x, y);

        // the kinds down the left
        KitDraw.heading(g, font, "Supplies", x + 37, y + 44, 85, false);
        int cy = y + GRID_Y;
        for (KitItems.Category c : KitItems.Category.values()) {
            int n = c == KitItems.Category.ALL ? all.size() : counts.getOrDefault(c, 0);
            if (n == 0 && c != KitItems.Category.ALL && c != category) continue;
            boolean on = c == category;
            boolean over = screen.hovered(x + 37, cy - 2, 88, 14, mx, my);
            if (on) {
                g.fill(x + 37, cy - 2, x + 125, cy + 11, 0x1FE8E4DA);
                g.fill(x + 37, cy - 2, x + 38, cy + 11, KitDraw.TEXT);
            }
            KitDraw.text(g, font, c.label, x + 42, cy + 1, on || over ? KitDraw.BRIGHT : KitDraw.DIM, 1f);
            KitDraw.textRight(g, font, String.valueOf(n), x + 122, cy + 1, KitDraw.FAINT, 0.9f);
            screen.hot(x + 37, cy - 2, 88, 14, () -> {
                category = c;
                scroll = 0;
                com.zomboidcraft.client.sound.UiSounds.play(com.zomboidcraft.client.sound.UiSounds.TAB);
            });
            cy += 15;
        }

        // the grid
        List<Entry> shown = category == KitItems.Category.ALL ? all : all.stream().filter(e -> e.category() == category).toList();
        int rows = (shown.size() + COLS - 1) / COLS;
        scroll = Math.max(0, Math.min(scroll, rows - ROWS));
        if (shown.isEmpty()) {
            KitDraw.text(g, font, "You aren't carrying anything like this.", x + GRID_X, y + GRID_Y + 4, KitDraw.DIM, 1f);
        }
        Entry pickedEntry = null;
        for (int k = scroll * COLS; k < Math.min(shown.size(), (scroll + ROWS) * COLS); k++) {
            Entry e = shown.get(k);
            int i = k - scroll * COLS;
            int tx = x + GRID_X + (i % COLS) * (TILE + GAP), ty = y + GRID_Y + (i / COLS) * (TILE + GAP);
            ItemStack shownStack = e.stack().copyWithCount(Math.min(e.count(), 99));
            boolean picked = ItemStack.isSameItemSameComponents(e.stack(), selected);
            if (picked) pickedEntry = e;
            KitDraw.tile(g, font, shownStack, tx, ty, TILE, TILE, picked || screen.hovered(tx, ty, TILE, TILE, mx, my));
            if (e.count() > 99) KitDraw.capsRight(g, font, String.valueOf(e.count()), tx + TILE - 2, ty + TILE - 9, KitDraw.BRIGHT, 0.8f);
            KitDraw.pips(g, tx + 3, ty + 3, KitItems.tier(e.stack()), KitDraw.TEXT, 1);
            screen.hot(tx, ty, TILE, TILE, e.stack(), () -> {
                selected = e.stack();
                com.zomboidcraft.client.sound.UiSounds.play(com.zomboidcraft.client.sound.UiSounds.CLICK);
            }, null, null, -1);
        }
        if (rows > ROWS) {
            int bx = x + GRID_X + COLS * (TILE + GAP), by = y + GRID_Y, bh = ROWS * (TILE + GAP) - GAP;
            g.fill(bx, by, bx + 2, by + bh, 0x1FE8E4DA);
            int th = Math.max(10, bh * ROWS / rows);
            int tt = by + (bh - th) * scroll / Math.max(1, rows - ROWS);
            g.fill(bx, tt, bx + 2, tt + th, KitDraw.DIM);
        }
        if (pickedEntry == null) {
            for (Entry e : all) if (ItemStack.isSameItemSameComponents(e.stack(), selected)) pickedEntry = e;
        }
        detail(g, font, pickedEntry, x + 440, y + GRID_Y, mx, my);
        storage(g, font, x + GRID_X, y + 272, mx, my);

        int hx = x + 37, hy = y + KitScreen.H - 14;
        hx += KitDraw.hint(g, font, "LMB", "Select", hx, hy);
        hx += KitDraw.hint(g, font, "Wheel", "Scroll", hx, hy);
        screen.pagesAndClose(g, font, hx, hy);
    }

    /** Totals across the top: rounds, nails, medical and food. */
    private void totals(GuiGraphicsExtractor g, Font font, List<Entry> all, int x, int y) {
        int rounds = 0, nails = 0, medical = 0, food = 0;
        for (Entry e : all) {
            if (KitItems.ammo(e.stack())) rounds += e.count();
            if (e.stack().getItem() instanceof MagazineItem) rounds += MagazineItem.rounds(e.stack()) * e.count();
            if (e.stack().is(ModItems.NAILS)) nails += e.count();
            if (e.category() == KitItems.Category.MEDICAL) medical += e.count();
            if (e.category() == KitItems.Category.FOOD) food += e.count();
        }
        String[] labels = {"Rounds", "Nails", "Medical", "Food"};
        int[] values = {rounds, nails, medical, food};
        int cx = x + 181;
        for (int i = 0; i < labels.length; i++) {
            KitDraw.caps(g, font, labels[i], cx + i * 64, y + 33, KitDraw.DIM, 0.6f);
            KitDraw.caps(g, font, String.valueOf(values[i]), cx + i * 64, y + 39, KitDraw.BRIGHT, 1.1f);
        }
    }

    private void detail(GuiGraphicsExtractor g, Font font, Entry e, int x, int y, int mx, int my) {
        int w = 139, h = 212;
        KitDraw.panel(g, x, y, w, h);
        if (e == null) {
            KitDraw.text(g, font, "Pick something to see", x + 8, y + 8, KitDraw.DIM, 1f);
            KitDraw.text(g, font, "where it's packed.", x + 8, y + 18, KitDraw.DIM, 1f);
            return;
        }
        ItemStack stack = e.stack();
        KitItems.Rarity r = KitItems.rarity(stack);
        g.fillGradient(x + 1, y + 1, x + w - 1, y + 70, r.tileTop, r.tileBottom);
        KitDraw.item(g, stack, x + w / 2f - 28, y + 7, 56);
        KitDraw.capsRight(g, font, "x" + e.count(), x + w - 6, y + 58, KitDraw.BRIGHT, 1.2f);
        int cy = y + 76;
        String name = stack.getHoverName().getString();
        KitDraw.caps(g, font, name, x + 8, cy, KitDraw.BRIGHT, KitDraw.capsWidth(font, name, 1.2f) > w - 16 ? 0.9f : 1.2f);
        cy += 12;
        KitDraw.text(g, font, r.label + " · " + e.category().label, x + 8, cy, r.text, 0.9f);
        cy += 10;
        cy += KitDraw.wrap(g, font, KitItems.kind(stack), x + 8, cy, w - 16, KitDraw.DIM) + 4;
        row(g, font, "Weight each", Weights.format(Weights.unit(stack)), x, cy, w);
        cy += 10;
        row(g, font, "All of it", Weights.format(Weights.unit(stack) * e.count()), x, cy, w);
        cy += 10;
        if (stack.isDamageableItem()) {
            row(g, font, "Condition", KitItems.conditionWord(KitItems.condition(stack)), x, cy, w);
            cy += 10;
        }
        cy += 4;
        KitDraw.caps(g, font, "Packed in", x + 8, cy, KitDraw.DIM, 0.6f);
        cy += 8;
        Map<String, Integer> where = new LinkedHashMap<>();
        for (int i : e.slots()) where.merge(place(i), screen.menu().slots.get(i).getItem().getCount(), Integer::sum);
        for (Map.Entry<String, Integer> p : where.entrySet()) {
            if (cy > y + h - 30) break;
            row(g, font, p.getKey(), "x" + p.getValue(), x, cy, w);
            cy += 10;
        }

        // drop one, drop all, or inspect a weapon
        int by = y + h - 18;
        int first = e.slots().get(0);
        int bx = x + 8;
        bx += button(g, font, "Drop one", bx, by, mx, my, () -> screen.click(first, 0, ContainerInput.THROW)) + 4;
        if (KitItems.weapon(stack)) {
            button(g, font, "Inspect", bx, by, mx, my, () -> screen.inspect(first));
        } else if (e.count() > 1) {
            button(g, font, "Drop all", bx, by, mx, my, () -> {
                for (int i : e.slots()) screen.click(i, 1, ContainerInput.THROW);
            });
        }
    }

    private int button(GuiGraphicsExtractor g, Font font, String label, int x, int y, int mx, int my, Runnable run) {
        int w = Math.round(KitDraw.capsWidth(font, label, 0.75f)) + 10;
        boolean over = screen.hovered(x, y, w, 12, mx, my);
        g.fill(x, y, x + w, y + 12, over ? 0x33E8E4DA : 0x14E8E4DA);
        KitDraw.frame(g, x, y, w, 12, over ? 0xCCFFFFFF : 0x4DE8E4DA);
        KitDraw.capsCentred(g, font, label, x + w / 2f, y + 3, KitDraw.BRIGHT, 0.75f);
        screen.hot(x, y, w, 12, run);
        return w;
    }

    private static void row(GuiGraphicsExtractor g, Font font, String name, String value, int x, int y, int w) {
        KitDraw.text(g, font, name, x + 8, y, KitDraw.DIM, 0.9f);
        KitDraw.textRight(g, font, value, x + w - 8, y, KitDraw.TEXT, 0.9f);
    }

    /** Where a carried slot is: "Pockets", "Belt", "Hiking backpack". */
    private String place(int index) {
        Slot slot = screen.menu().slots.get(index);
        if (slot instanceof CarrySlots.BagSlot bag) return bag.bag.bag().getHoverName().getString();
        if (slot instanceof CarrySlots.GearSlot) return "Worn";
        if (index == InventoryMenu.SHIELD_SLOT) return "Off hand";
        if (index >= InventoryMenu.USE_ROW_SLOT_START) return com.zomboidcraft.carry.QuickSlots.label(index - InventoryMenu.USE_ROW_SLOT_START);
        return "Pockets";
    }

    /** How full each place you carry things is. */
    private void storage(GuiGraphicsExtractor g, Font font, int x, int y, int mx, int my) {
        LocalPlayer player = screen.player();
        KitDraw.heading(g, font, "Where it's carried", x, y, 296, false);
        List<String> names = new ArrayList<>();
        List<Float> fill = new ArrayList<>();
        List<String> lines = new ArrayList<>();
        int pocketsUsed = 0, pocketsAll = 0;
        for (int i = InventoryMenu.INV_SLOT_START; i < InventoryMenu.INV_SLOT_END; i++) {
            if (Carry.locked(player, screen.menu().slots.get(i).getContainerSlot())) continue;
            pocketsAll++;
            if (screen.menu().slots.get(i).hasItem()) pocketsUsed++;
        }
        names.add("Pockets");
        fill.add(pocketsAll == 0 ? 0 : pocketsUsed / (float) pocketsAll);
        lines.add(pocketsUsed + " / " + pocketsAll + " slots");
        int beltUsed = 0, beltAll = 0;
        for (int i = InventoryMenu.USE_ROW_SLOT_START; i < InventoryMenu.USE_ROW_SLOT_END; i++) {
            if (!com.zomboidcraft.carry.QuickSlots.open(player, i - InventoryMenu.USE_ROW_SLOT_START)) continue;
            beltAll++;
            if (screen.menu().slots.get(i).hasItem()) beltUsed++;
        }
        names.add("Quick slots");
        fill.add(beltAll == 0 ? 0 : beltUsed / (float) beltAll);
        lines.add(beltUsed + " / " + beltAll + " slots");
        if (screen.menu() instanceof Carry.Bags bags) {
            for (BagContainer bag : bags.zomboidcraft$bags()) {
                BagSpec spec = bag.spec();
                if (spec == null) continue;
                double inside = bag.contentsWeight(-1);
                names.add(bag.bag().getHoverName().getString());
                fill.add((float) Math.min(1, inside / spec.capacity()));
                lines.add(Weights.format(inside) + " / " + Weights.format(spec.capacity()));
            }
        }
        int n = Math.min(5, names.size());
        int cw = (296 - (n - 1) * 8) / n;
        for (int i = 0; i < n; i++) {
            int cx = x + i * (cw + 8);
            String name = names.get(i);
            if (KitDraw.width(font, name, 0.9f) > cw) name = font.plainSubstrByWidth(name, (int) (cw / 0.9f) - 6) + "..";
            KitDraw.text(g, font, name, cx, y + 10, KitDraw.TEXT, 0.9f);
            float f = fill.get(i);
            KitDraw.bar(g, cx, y + 21, cw, 2, f, Float.NaN);
            if (f >= 1f) g.fill(cx, y + 21, cx + cw, y + 23, KitDraw.WARN);
            KitDraw.text(g, font, lines.get(i), cx, y + 26, KitDraw.DIM, 0.8f);
        }
    }

    boolean scrolled(double mx, double my, double sy) {
        int x = screen.left() + GRID_X, y = screen.top() + GRID_Y;
        if (!KitDraw.in(mx, my, x, y, COLS * (TILE + GAP), ROWS * (TILE + GAP))) return false;
        scroll -= (int) Math.signum(sy);
        if (scroll < 0) scroll = 0;
        return true;
    }

    boolean keyPressed(KeyEvent event) {
        return false;
    }
}
