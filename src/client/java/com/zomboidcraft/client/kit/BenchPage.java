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
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * The bench: one weapon up close. Down the left, every weapon you carry; in the middle, the weapon itself and its
 * stats; on the right, either its parts (each socket with what's fitted and every part you carry that fits it, one
 * click to fit or take off, with the stats showing the change before you commit) or its condition and quality.
 */
final class BenchPage {
    enum Tab { PARTS, CONDITION }

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
        KitDraw.heading(g, font, "Your weapons", x + 37, y + 44, 133, false);
        int ly = y + 54;
        for (int i : weapons) {
            if (ly > y + 300) break;
            ItemStack stack = menu.slots.get(i).getItem();
            boolean on = i == slot;
            KitDraw.tile(g, font, stack, x + 37, ly, 54, 24, on || screen.hovered(x + 37, ly, 133, 24, mx, my));
            String name = stack.getHoverName().getString();
            if (KitDraw.width(font, name, 0.95f) > 74) name = font.plainSubstrByWidth(name, 72) + "..";
            KitDraw.text(g, font, name, x + 95, ly + 4, on ? KitDraw.BRIGHT : KitDraw.DIM, 0.95f);
            KitItems.Rarity r = KitItems.rarity(stack);
            KitDraw.caps(g, font, r.label, x + 95, ly + 14, r.text, 0.65f);
            if (on) g.fill(x + 33, ly, x + 34, ly + 24, KitDraw.LINK);
            int pick = i;
            screen.hot(x + 37, ly, 133, 24, () -> {
                show(pick);
                UiSounds.play(UiSounds.CLICK);
            });
            ly += 29;
        }
        if (weapons.isEmpty()) {
            KitDraw.text(g, font, "You aren't carrying a weapon.", x + 185, y + 60, KitDraw.DIM, 1.2f);
            return;
        }

        ItemStack weapon = menu.slots.get(slot).getItem();
        boolean gun = KitItems.gun(weapon);
        KitItems.Rarity r = KitItems.rarity(weapon);

        // the name, its rarity and kind, and the two tabs
        String name = weapon.getHoverName().getString();
        KitDraw.caps(g, font, name, x + 185, y + 44, KitDraw.BRIGHT, KitDraw.capsWidth(font, name, 1.8f) > 235 ? 1.3f : 1.8f);
        int bw = Math.round(KitDraw.capsWidth(font, r.label, 0.7f)) + 8;
        g.fill(x + 185, y + 61, x + 185 + bw, y + 70, r.band);
        KitDraw.caps(g, font, r.label, x + 189, y + 63, r.bandText, 0.7f);
        int tier = KitItems.tier(weapon);
        int px = x + 185 + bw + 5;
        if (tier > 0) px += KitDraw.pips(g, px, y + 62, tier, r.text, 2) + 4;
        KitDraw.text(g, font, KitItems.kind(weapon), px, y + 62, KitDraw.DIM, 0.9f);
        int tx = x + 185;
        for (Tab t : Tab.values()) {
            String label = t == Tab.PARTS ? "Parts" : "Condition";
            float w = KitDraw.capsWidth(font, label, 0.85f);
            boolean on = t == tab;
            boolean over = screen.hovered(tx - 2, y + 75, Math.round(w) + 4, 12, mx, my);
            KitDraw.caps(g, font, label, tx, y + 77, on || over ? KitDraw.BRIGHT : KitDraw.DIM, 0.85f);
            if (on) g.fill(tx, y + 86, Math.round(tx + w), y + 87, KitDraw.TEXT);
            screen.hot(tx - 2, y + 75, Math.round(w) + 4, 12, () -> {
                tab = t;
                UiSounds.play(UiSounds.TAB);
            });
            tx += Math.round(w) + 12;
        }

        // the weapon, big: a gun turns on the stage with its empty sockets as green ghosts
        Attachments.Slot lit = trying.isEmpty() ? null : Attachments.slotOf(trying);
        if (!(gun && Attachments.isModular(weapon) && stage.draw(g, weapon, x + 185, y + 92, 235, 104, mx, my, lit))) {
            g.fillGradient(x + 200, y + 182, x + 405, y + 196, 0x00000000, 0x33000000);
            KitDraw.item(g, weapon, x + 302 - 56, y + 86, 112);
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
        List<KitItems.Stat> stats = gun ? KitItems.gunStats(weapon, after) : KitItems.meleeStats(weapon);
        KitDraw.heading(g, font, "Stats", x + 185, y + 202, 235, false);
        int sy = y + 213;
        for (KitItems.Stat s : stats) {
            KitDraw.text(g, font, s.label(), x + 185, sy, KitDraw.DIM, 0.9f);
            KitDraw.bar(g, x + 260, sy + 2, 120, 4, s.value(), s.changed());
            KitDraw.textRight(g, font, s.shown(), x + 420, sy, KitDraw.TEXT, 0.9f);
            sy += 12;
        }
        if (gun) {
            KitDraw.text(g, font, "Loaded", x + 185, sy, KitDraw.DIM, 0.9f);
            KitDraw.textRight(g, font, KitItems.loaded(weapon), x + 420, sy, KitDraw.TEXT, 0.9f);
        }

        trying = ItemStack.EMPTY;
        if (tab == Tab.PARTS) parts(g, font, weapon, x + 440, y + 44, mx, my);
        else condition(g, font, weapon, x + 440, y + 44, mx, my);

        int hx = x + 37, hy = y + KitScreen.H - 14;
        if (tab == Tab.PARTS && gun) {
            hx += KitDraw.hint(g, font, "LMB", "Fit a part / take one off", hx, hy);
            hx += KitDraw.hint(g, font, "Drag", "Turn the gun", hx, hy);
        } else if (tab == Tab.CONDITION) {
            hx += KitDraw.hint(g, font, "LMB", "Repair with it", hx, hy);
        }
        screen.pagesAndClose(g, font, hx, hy);
    }

    /** Fits the first part you carry for this socket, or null if you carry none. */
    private @Nullable Runnable spare(ItemStack weapon, Attachments.Slot socket, int gunSlot) {
        AbstractContainerMenu menu = screen.menu();
        for (int i : screen.carried()) {
            ItemStack s = menu.slots.get(i).getItem();
            if (i != gunSlot && !s.isEmpty() && Attachments.slotOf(s) == socket && Attachments.fits(weapon, s.getItem())) {
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
        KitDraw.heading(g, font, "Parts", x, y, 139, false);
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
        int block = Math.min(33, 270 / sockets.size());
        int tile = Math.min(20, block - 11);
        int cy = y + 11;
        for (Attachments.Slot socket : sockets) {
            ItemStack fitted = Attachments.get(weapon, socket);
            List<Integer> spare = new ArrayList<>();
            for (int i : screen.carried()) {
                ItemStack s = menu.slots.get(i).getItem();
                if (i != slot && !s.isEmpty() && Attachments.slotOf(s) == socket && Attachments.fits(weapon, s.getItem())) spare.add(i);
            }
            KitDraw.caps(g, font, AttachmentItem.slotName(socket), x, cy, KitDraw.DIM, 0.62f);
            String what = fitted.isEmpty() ? "empty" : fitted.getHoverName().getString();
            float lw = KitDraw.capsWidth(font, AttachmentItem.slotName(socket), 0.62f) + 4;
            if (KitDraw.width(font, what, 0.8f) > 135 - lw) what = font.plainSubstrByWidth(what, (int) ((130 - lw) / 0.8f)) + "..";
            KitDraw.text(g, font, what, x + lw, cy - 1, fitted.isEmpty() ? KitDraw.FAINT : KitDraw.TEXT, 0.8f);
            int ty = cy + 8;
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
            g.fill(x + tile + 3, ty + 2, x + tile + 4, ty + tile - 2, 0x33E8E4DA);
            int ax = x + tile + 7;
            int room = (139 - tile - 7) / (tile + 3);
            for (int k = 0; k < spare.size() && k < room; k++) {
                int from = spare.get(k);
                ItemStack part = menu.slots.get(from).getItem();
                boolean on = screen.hovered(ax, ty, tile, tile, mx, my);
                if (on) trying = part;
                KitDraw.tile(g, font, part, ax, ty, tile, tile, on);
                int gunSlot = slot;
                screen.hot(ax, ty, tile, tile, part, () -> screen.button(Bench.fitButton(from, gunSlot, socket)), null, null, -1);
                ax += tile + 3;
            }
            if (spare.size() > room) KitDraw.text(g, font, "+" + (spare.size() - room), ax - 2, ty + tile / 2 - 4, KitDraw.DIM, 0.8f);
            if (spare.isEmpty()) KitDraw.text(g, font, "none carried", ax, ty + tile / 2 - 3, KitDraw.FAINT, 0.8f);
            cy += block;
        }
    }

    /** Quality, condition and repairs. */
    private void condition(GuiGraphicsExtractor g, Font font, ItemStack weapon, int x, int y, int mx, int my) {
        KitDraw.heading(g, font, "Quality", x, y, 139, false);
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
