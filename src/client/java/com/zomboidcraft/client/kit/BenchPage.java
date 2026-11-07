package com.zomboidcraft.client.kit;

import com.zomboidcraft.ZcConfig;
import com.zomboidcraft.carry.Weights;
import com.zomboidcraft.client.sound.UiSounds;
import com.zomboidcraft.combat.AttachmentItem;
import com.zomboidcraft.combat.Attachments;
import com.zomboidcraft.combat.Bench;
import com.zomboidcraft.combat.GunFinish;
import com.zomboidcraft.combat.Repairs;
import com.zomboidcraft.skills.Skill;
import com.zomboidcraft.skills.Skills;
import net.minecraft.client.player.LocalPlayer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * The bench: one weapon up close. Down the left, every weapon you carry; in the middle, the weapon itself on a big
 * stage that fills most of the page, turned by dragging with the right mouse button, and its stats under it; on the right, either its parts (each socket with what's fitted and every part you carry that fits it, one
 * click to fit or take off, with the stats showing the change before you commit) or its condition and quality.
 */
final class BenchPage {
    enum Tab { PARTS, CONDITION }

    // the three columns, from the page's left edge: weapons, the stage and stats, then parts or condition
    private static final int LIST_X = 14, LIST_W = 112;
    private static final int MID_X = 136, MID_W = 314;
    private static final int SIDE_X = 463, SIDE_W = 139;
    private static final int STAGE_Y = 76, STAGE_H = 196;

    private final KitScreen<?> screen;
    private int slot = -1;
    private Tab tab = Tab.PARTS;
    /** The part under the mouse, so the stats can show what fitting it would do. */
    private ItemStack trying = ItemStack.EMPTY;
    private final GunStage stage = new GunStage();

    BenchPage(KitScreen<?> screen) {
        this.screen = screen;
    }

    void show(int slot) {
        this.slot = slot;
        tab = KitItems.gun(screen.menu().slots.get(slot).getItem()) ? Tab.PARTS : Tab.CONDITION;
    }

    private List<Integer> weapons() {
        List<Integer> out = new ArrayList<>();
        for (int i : screen.carried()) {
            if (KitItems.weapon(screen.menu().slots.get(i).getItem())) out.add(i);
        }
        return out;
    }

    void draw(GuiGraphicsExtractor g, int x, int y, int mx, int my) {
        Font font = screen.mc().font;
        AbstractContainerMenu menu = screen.menu();
        List<Integer> weapons = weapons();
        if (!weapons.contains(slot)) {
            int held = InventoryMenu.USE_ROW_SLOT_START + screen.player().getInventory().getSelectedSlot();
            slot = weapons.contains(held) ? held : weapons.isEmpty() ? -1 : weapons.get(0);
            if (slot >= 0 && !KitItems.gun(menu.slots.get(slot).getItem())) tab = Tab.CONDITION;
        }

        // every weapon you carry
        KitDraw.heading(g, font, "Your weapons", LIST_X + x, y + 44, LIST_W, false);
        int ly = y + 54;
        for (int i : weapons) {
            if (ly > y + 300) break;
            ItemStack stack = menu.slots.get(i).getItem();
            boolean on = i == slot;
            int lx = x + LIST_X;
            KitDraw.tile(g, font, stack, lx, ly, 40, 24, on || screen.hovered(lx, ly, LIST_W, 24, mx, my));
            String name = stack.getHoverName().getString();
            if (KitDraw.width(font, name, 0.85f) > LIST_W - 46) name = font.plainSubstrByWidth(name, (int) ((LIST_W - 52) / 0.85f)) + "..";
            KitDraw.text(g, font, name, lx + 44, ly + 4, on ? KitDraw.BRIGHT : KitDraw.DIM, 0.85f);
            KitItems.Rarity r = KitItems.rarity(stack);
            KitDraw.caps(g, font, r.label, lx + 44, ly + 14, r.text, 0.6f);
            if (on) g.fill(lx - 4, ly, lx - 3, ly + 24, KitDraw.LINK);
            int pick = i;
            screen.hot(lx, ly, LIST_W, 24, () -> {
                show(pick);
                UiSounds.play(UiSounds.CLICK);
            });
            ly += 28;
        }
        int cx = x + MID_X;
        if (weapons.isEmpty()) {
            KitDraw.text(g, font, "You aren't carrying a weapon.", cx, y + 60, KitDraw.DIM, 1.2f);
            return;
        }

        ItemStack weapon = menu.slots.get(slot).getItem();
        boolean gun = KitItems.gun(weapon);
        KitItems.Rarity r = KitItems.rarity(weapon);

        // the name, its rarity and kind
        String name = weapon.getHoverName().getString();
        KitDraw.caps(g, font, name, cx, y + 44, KitDraw.BRIGHT, KitDraw.capsWidth(font, name, 1.8f) > MID_W ? 1.3f : 1.8f);
        int bw = Math.round(KitDraw.capsWidth(font, r.label, 0.7f)) + 8;
        g.fill(cx, y + 61, cx + bw, y + 70, r.band);
        KitDraw.caps(g, font, r.label, cx + 4, y + 63, r.bandText, 0.7f);
        int tier = KitItems.tier(weapon);
        int px = cx + bw + 5;
        if (tier > 0) px += KitDraw.pips(g, px, y + 62, tier, r.text, 2) + 4;
        KitDraw.text(g, font, KitItems.kind(weapon), px, y + 62, KitDraw.DIM, 0.9f);

        // the right panel's two tabs, standing in for its heading
        int rx = x + SIDE_X;
        int tx = rx;
        for (Tab t : Tab.values()) {
            String label = t == Tab.PARTS ? "Parts" : "Condition";
            float w = KitDraw.capsWidth(font, label, 0.85f);
            boolean on = t == tab;
            boolean over = screen.hovered(tx - 2, y + 42, Math.round(w) + 4, 12, mx, my);
            KitDraw.caps(g, font, label, tx, y + 44, on || over ? KitDraw.BRIGHT : KitDraw.DIM, 0.85f);
            if (on) g.fill(tx, y + 53, Math.round(tx + w), y + 54, KitDraw.TEXT);
            screen.hot(tx - 2, y + 42, Math.round(w) + 4, 12, () -> {
                tab = t;
                UiSounds.play(UiSounds.TAB);
            });
            tx += Math.round(w) + 10;
        }
        if (tx < rx + SIDE_W) g.fill(tx, y + 47, rx + SIDE_W, y + 48, KitDraw.LINE);

        // the weapon, big and in 3D, turned by dragging: every gun and melee weapon, a modular gun with its empty sockets
        // as green ghosts
        Attachments.Slot lit = trying.isEmpty() ? null : Attachments.slotOf(trying);
        if (!stage.draw(g, weapon, cx, y + STAGE_Y, MID_W, STAGE_H, mx, my, lit)) {
            g.fillGradient(cx + 20, y + STAGE_Y + STAGE_H - 30, cx + MID_W - 20, y + STAGE_Y + STAGE_H - 14, 0x00000000, 0x33000000);
            KitDraw.item(g, weapon, cx + MID_W / 2 - 80, y + STAGE_Y + STAGE_H / 2 - 80, 160);
        } else {
            for (GunStage.Point p : stage.points) {
                int gunSlot = slot;
                Runnable act = p.fitted() ? () -> screen.button(Bench.takeOffButton(gunSlot, p.slot())) : spare(weapon, p.slot(), gunSlot);
                screen.hot(p.x() - 6, p.y() - 6, 13, 13, ItemStack.EMPTY, act, null, GunStage.label(p, weapon), -1);
            }
        }

        // its stats, showing what the part under the mouse would change
        ItemStack after = null;
        if (gun && !trying.isEmpty()) {
            ItemStack copy = weapon.copy();
            if (Attachments.mount(copy, trying) != null) after = copy;
        }
        // in two columns under the stage
        List<KitItems.Stat> stats = gun ? KitItems.gunStats(weapon, after) : KitItems.meleeStats(weapon);
        int colW = (MID_W - 14) / 2;
        int n = 0;
        int sy0 = y + STAGE_Y + STAGE_H + 7;
        for (KitItems.Stat s : stats) {
            int sx = cx + (n % 2) * (colW + 14), sy = sy0 + (n / 2) * 11;
            KitDraw.text(g, font, s.label(), sx, sy, KitDraw.DIM, 0.8f);
            KitDraw.bar(g, sx + 62, sy + 2, colW - 108, 3, s.value(), s.changed());
            KitDraw.textRight(g, font, s.shown(), sx + colW, sy, KitDraw.TEXT, 0.8f);
            n++;
        }
        if (gun) {
            int sx = cx + (n % 2) * (colW + 14), sy = sy0 + (n / 2) * 11;
            KitDraw.text(g, font, "Loaded", sx, sy, KitDraw.DIM, 0.8f);
            KitDraw.textRight(g, font, KitItems.loaded(weapon), sx + colW, sy, KitDraw.TEXT, 0.8f);
        }

        trying = ItemStack.EMPTY;
        if (tab == Tab.PARTS) parts(g, font, weapon, rx, y + 44, mx, my);
        else condition(g, font, weapon, rx, y + 44, mx, my);

        int hx = x + LIST_X, hy = y + KitScreen.H - 14;
        if (tab == Tab.PARTS && gun && Attachments.isModular(weapon)) {
            hx += KitDraw.hint(g, font, "LMB", "Fit a part / take one off", hx, hy);
        } else if (tab == Tab.CONDITION) {
            hx += KitDraw.hint(g, font, "LMB", "Repair with it", hx, hy);
        }
        hx += KitDraw.hint(g, font, "RMB drag", gun ? "Turn the gun" : "Turn it", hx, hy);
        screen.pagesAndClose(g, font, hx, hy);
    }

    /** Fits the first part you carry for this socket, or null if you carry none. */
    private @Nullable Runnable spare(ItemStack weapon, Attachments.Slot socket, int gunSlot) {
        AbstractContainerMenu menu = screen.menu();
        for (int i : screen.carried()) {
            ItemStack s = menu.slots.get(i).getItem();
            if (i != gunSlot && !s.isEmpty() && Attachments.offeredIn(weapon, s, socket)) {
                int from = i;
                return () -> screen.button(Bench.fitButton(from, gunSlot, socket));
            }
        }
        return null;
    }

    boolean pressed(double mx, double my) {
        return stage.pressed(mx, my);
    }

    void dragged(double mx, double my) {
        stage.dragged(mx, my);
    }

    void released() {
        stage.released();
    }

    /** Each socket: what's fitted, then every part you carry that fits it. */
    private void parts(GuiGraphicsExtractor g, Font font, ItemStack weapon, int x, int y, int mx, int my) {
        List<Attachments.Slot> sockets = Attachments.slots(weapon);
        if (sockets.isEmpty()) {
            KitDraw.wrap(g, font, KitItems.gun(weapon) ? "Nothing fits this gun." : "Melee weapons take no parts.", x, y + 12, 139, KitDraw.DIM);
            return;
        }
        if (!ZcConfig.get().guns.attachments) {
            KitDraw.wrap(g, font, "Attachments are turned off in settings.", x, y + 12, 139, KitDraw.BAD);
            return;
        }
        AbstractContainerMenu menu = screen.menu();
        // a Tarkov gun has up to twenty sockets: the ones with a part on or one you carry get a row, the rest a line
        List<Attachments.Slot> rows = new ArrayList<>();
        List<String> idle = new ArrayList<>();
        Map<Attachments.Slot, List<Integer>> spares = new HashMap<>();
        for (Attachments.Slot socket : sockets) {
            List<Integer> spare = new ArrayList<>();
            for (int i : screen.carried()) {
                ItemStack s = menu.slots.get(i).getItem();
                if (i != slot && !s.isEmpty() && Attachments.offeredIn(weapon, s, socket)) spare.add(i);
            }
            spares.put(socket, spare);
            if (sockets.size() <= 8 || !Attachments.get(weapon, socket).isEmpty() || !spare.isEmpty()) rows.add(socket);
            else idle.add(AttachmentItem.slotName(socket));
        }
        int idleH = idle.isEmpty() ? 0 : 10 + 9 * Math.max(1, (int) Math.ceil(String.join(", ", idle).length() / 32.0));
        boolean compact = rows.size() > 8;
        int block = compact ? Math.max(15, Math.min(24, (270 - idleH) / Math.max(1, rows.size())))
                : Math.min(33, (270 - idleH) / Math.max(1, rows.size()));
        int tile = compact ? block - 3 : Math.min(20, block - 11);
        int cy = y + 11;
        for (Attachments.Slot socket : rows) {
            if (cy + block > y + 11 + 270 - idleH) break;
            ItemStack fitted = Attachments.get(weapon, socket);
            List<Integer> spare = spares.get(socket);
            String name = AttachmentItem.slotName(socket);
            String what = fitted.isEmpty() ? "empty" : fitted.getHoverName().getString();
            int ty, ax, room;
            if (compact) {
                // one line: the fitted part's tile, its socket and name beside it, then what you carry for it
                ty = cy;
                room = Math.min(spare.size(), 3);
                ax = x + 139 - room * (tile + 2);
                KitDraw.caps(g, font, name, x + tile + 4, cy + 1, KitDraw.DIM, 0.55f);
                float nw = ax - (x + tile + 4) - 3;
                if (KitDraw.width(font, what, 0.7f) > nw) what = font.plainSubstrByWidth(what, (int) ((nw - 6) / 0.7f)) + "..";
                KitDraw.text(g, font, what, x + tile + 4, cy + tile - 7, fitted.isEmpty() ? KitDraw.FAINT : KitDraw.TEXT, 0.7f);
            } else {
                KitDraw.caps(g, font, name, x, cy, KitDraw.DIM, 0.62f);
                float lw = KitDraw.capsWidth(font, name, 0.62f) + 4;
                if (KitDraw.width(font, what, 0.8f) > 135 - lw) what = font.plainSubstrByWidth(what, (int) ((130 - lw) / 0.8f)) + "..";
                KitDraw.text(g, font, what, x + lw, cy - 1, fitted.isEmpty() ? KitDraw.FAINT : KitDraw.TEXT, 0.8f);
                ty = cy + 8;
                ax = x + tile + 7;
                room = (139 - tile - 7) / (tile + 3);
            }
            // the fitted part, ringed in brass: click to take it off
            boolean over = screen.hovered(x, ty, tile, tile, mx, my);
            KitDraw.tile(g, font, fitted, x, ty, tile, tile, over);
            if (!fitted.isEmpty()) {
                KitDraw.frame(g, x - 1, ty - 1, tile + 2, tile + 2, over ? 0xFFFFF1B8 : KitDraw.LINK);
                int gunSlot = slot;
                screen.hot(x, ty, tile, tile, fitted, () -> {
                    screen.button(Bench.takeOffButton(gunSlot, socket));
                }, null, null, -1);
            }
            if (!compact) g.fill(x + tile + 3, ty + 2, x + tile + 4, ty + tile - 2, 0x33E8E4DA);
            for (int k = 0; k < spare.size() && k < room; k++) {
                int from = spare.get(k);
                ItemStack part = menu.slots.get(from).getItem();
                boolean on = screen.hovered(ax, ty, tile, tile, mx, my);
                if (on) trying = part;
                KitDraw.tile(g, font, part, ax, ty, tile, tile, on);
                int gunSlot = slot;
                screen.hot(ax, ty, tile, tile, part, () -> screen.button(Bench.fitButton(from, gunSlot, socket)), null, null, -1);
                ax += tile + (compact ? 2 : 3);
            }
            if (spare.size() > room) {
                if (compact) KitDraw.textRight(g, font, "+" + (spare.size() - room), x + 139, ty + tile + 1, KitDraw.DIM, 0.6f);
                else KitDraw.text(g, font, "+" + (spare.size() - room), ax - 2, ty + tile / 2 - 4, KitDraw.DIM, 0.8f);
            }
            if (spare.isEmpty() && !compact) KitDraw.text(g, font, "none carried", ax, ty + tile / 2 - 3, KitDraw.FAINT, 0.8f);
            cy += block;
        }
        if (!idle.isEmpty()) {
            cy += 2;
            KitDraw.caps(g, font, "Also takes (none carried)", x, cy, KitDraw.DIM, 0.55f);
            KitDraw.wrap(g, font, String.join(", ", idle), x, cy + 8, 139, KitDraw.FAINT);
        }
    }

    /** Quality, condition and repairs. */
    private void condition(GuiGraphicsExtractor g, Font font, ItemStack weapon, int x, int y, int mx, int my) {
        KitItems.Rarity r = KitItems.rarity(weapon);
        int cy = y + 12;
        row(g, font, "Rarity", r.label, r.text, x, cy);
        cy += 10;
        int tier = KitItems.tier(weapon);
        row(g, font, "Tier", tier > 0 ? KitItems.tierName(tier) + " (" + tier + "/5)" : "Unrolled", tier > 0 ? KitDraw.TEXT : KitDraw.FAINT, x, cy);
        cy += 10;
        List<String> flaws = KitItems.flaws(weapon);
        row(g, font, "Flaws", flaws.isEmpty() ? "None" : String.join(", ", flaws), flaws.isEmpty() ? KitDraw.TEXT : KitDraw.BAD, x, cy);
        cy += 10;
        String place = KitItems.place(weapon);
        if (place != null) {
            row(g, font, "Found", place, KitDraw.TEXT, x, cy);
            cy += 10;
        }
        String finish = KitItems.finish(weapon);
        if (!finish.isEmpty()) {
            KitDraw.text(g, font, finish, x, cy, KitDraw.LINK, 0.85f);
            cy += 10;
        }
        List<KitItems.Stat> rolled = KitItems.rolled(weapon);
        if (!rolled.isEmpty()) {
            cy += 4;
            KitDraw.caps(g, font, "Rolled", x, cy, KitDraw.DIM, 0.6f);
            cy += 8;
            for (KitItems.Stat s : rolled) {
                KitDraw.text(g, font, s.label(), x, cy, KitDraw.DIM, 0.85f);
                KitDraw.bar(g, x + 62, cy + 2, 60, 3, s.value(), Float.NaN);
                KitDraw.textRight(g, font, s.shown(), x + 139, cy, KitDraw.TEXT, 0.85f);
                cy += 10;
            }
        }

        cy += 8;
        KitDraw.heading(g, font, "Condition", x, cy, 139, false);
        cy += 12;
        float left = KitItems.condition(weapon);
        KitDraw.caps(g, font, Math.round(left * 100) + "%", x, cy, left > 0.35f ? KitDraw.BRIGHT : KitDraw.BAD, 1.8f);
        KitDraw.textRight(g, font, KitItems.conditionWord(left), x + 139, cy + 6, left > 0.35f ? KitDraw.TEXT : KitDraw.BAD, 1f);
        cy += 18;
        KitDraw.bar(g, x, cy, 139, 3, left, Float.NaN);
        cy += 7;
        if (weapon.isDamageableItem()) {
            row(g, font, "Wear", weapon.getDamageValue() + " / " + weapon.getMaxDamage(), KitDraw.TEXT, x, cy);
            cy += 10;
        }
        row(g, font, "Weight", Weights.format(Weights.of(weapon)), KitDraw.TEXT, x, cy);
        cy += 14;

        repairs(g, font, weapon, x, cy, mx, my);
    }

    /** Everything you carry that can mend this weapon, with what each would give back. */
    private void repairs(GuiGraphicsExtractor g, Font font, ItemStack weapon, int x, int y, int mx, int my) {
        KitDraw.heading(g, font, "Repairs", x, y, 139, false);
        int cy = y + 11;
        LocalPlayer player = screen.player();
        int times = Repairs.times(weapon);
        int level = Skills.level(player, Skill.MAINTENANCE);
        KitDraw.text(g, font, (times == 0 ? "Never repaired" : "Repaired " + times + (times == 1 ? " time" : " times"))
                + " · Maintenance " + level, x, cy, KitDraw.DIM, 0.8f);
        cy += 10;
        if (!Repairs.repairable(weapon)) {
            KitDraw.wrap(g, font, "This doesn't wear out.", x, cy, 139, KitDraw.FAINT);
            return;
        }
        if (weapon.getDamageValue() <= 0) {
            KitDraw.wrap(g, font, "Nothing to mend: it's in full condition.", x, cy, 139, KitDraw.FAINT);
            return;
        }
        AbstractContainerMenu menu = screen.menu();
        List<Integer> usable = new ArrayList<>();
        List<ItemStack> kinds = new ArrayList<>();
        for (int i : screen.carried()) {
            ItemStack s = menu.slots.get(i).getItem();
            if (i == slot || Repairs.base(weapon, s) <= 0) continue;
            // one row per kind of material; every duplicate weapon gets its own, since each is worn differently
            boolean seen = false;
            if (!s.is(weapon.getItem())) {
                for (ItemStack k : kinds) seen |= ItemStack.isSameItemSameComponents(k, s);
            }
            if (seen) continue;
            kinds.add(s);
            usable.add(i);
        }
        if (usable.isEmpty()) {
            boolean gun = KitItems.gun(weapon);
            KitDraw.wrap(g, font, gun ? "You carry nothing to mend it with. Gun oil and spare gun parts fix guns; so does stripping another of the same gun."
                    : "You carry nothing to mend it with. Duct tape and wood glue fix melee weapons; so does stripping another of the same weapon.",
                    x, cy, 139, KitDraw.FAINT);
            return;
        }
        for (int i : usable) {
            if (cy > screen.top() + KitScreen.H - 34) break;
            ItemStack material = menu.slots.get(i).getItem();
            boolean over = screen.hovered(x, cy, 139, 20, mx, my);
            if (over) g.fill(x, cy, x + 139, cy + 20, 0x1FE8E4DA);
            KitDraw.tile(g, font, material, x, cy, 20, 20, over);
            boolean salvage = material.is(weapon.getItem());
            String name = salvage ? "Strip another" : material.getHoverName().getString();
            KitDraw.text(g, font, name, x + 24, cy + 2, KitDraw.BRIGHT, 0.9f);
            int count = 0;
            for (int k : screen.carried()) {
                if (ItemStack.isSameItemSameComponents(menu.slots.get(k).getItem(), material)) count += menu.slots.get(k).getItem().getCount();
            }
            KitDraw.text(g, font, salvage ? KitItems.conditionWord(KitItems.condition(material)) + ", used up" : count + " carried", x + 24, cy + 11,
                    KitDraw.DIM, 0.75f);
            int gain = Repairs.gain(player, weapon, material);
            int pct = Math.max(1, Math.round(100f * gain / weapon.getMaxDamage()));
            KitDraw.capsRight(g, font, "+" + pct + "%", x + 139 - 2, cy + 6, KitDraw.GOOD, 1.1f);
            int weaponSlot = slot;
            screen.hot(x, cy, 139, 20, material, () -> screen.button(Bench.repairButton(i, weaponSlot)), null, null, -1);
            cy += 23;
        }
    }

    private static void row(GuiGraphicsExtractor g, Font font, String name, String value, int colour, int x, int y) {
        KitDraw.text(g, font, name, x, y, KitDraw.DIM, 0.9f);
        KitDraw.textRight(g, font, value, x + 139, y, colour, 0.9f);
    }

    boolean scrolled(double mx, double my, double sy) {
        return false;
    }

    @Nullable ItemStack weapon() {
        return slot < 0 ? null : screen.menu().slots.get(slot).getItem();
    }
}
