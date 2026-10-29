package com.zomboidcraft.client.kit;

import com.zomboidcraft.ZcConfig;
import com.zomboidcraft.carry.BagItem;
import com.zomboidcraft.carry.BagSpec;
import com.zomboidcraft.carry.Carry;
import com.zomboidcraft.carry.CarrySlots;
import com.zomboidcraft.carry.GearStore;
import com.zomboidcraft.carry.QuickSlots;
import com.zomboidcraft.carry.Weights;
import com.zomboidcraft.client.hud.InjuryGlowLayer;
import com.zomboidcraft.combat.MeleeWeaponItem;
import com.zomboidcraft.combat.Stamina;
import com.zomboidcraft.survival.Body;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Weapon quick slots and the belt down the left, clothing, bags and belt kit down the right, your survivor in the middle and a strip
 * of what your kit adds up to across the top. Every slot opens a picker of what you carry that could go in it.
 */
final class CharacterPage {
    /** The weapon quick slots, big down the left: your hands, the sling and the holster. */
    private static final int[] WEAPON_SLOTS = {QuickSlots.HANDS, QuickSlots.BACK, QuickSlots.HOLSTER};
    /** The rest, small along the belt. */
    private static final int[] BELT_SLOTS = {QuickSlots.HIP, QuickSlots.HIP_2, QuickSlots.SHEATH, QuickSlots.POUCH, QuickSlots.POUCH_2};
    private static final String[] CARRY_LABELS = {"Back", "Vest", "Waist", "Off hand"};
    private static final String[] WORN_LABELS = {"Belt", "Holster", "Sheath", "Wrist"};
    private static final String[] WORN_EMPTY = {"Opens two hip slots", "Opens the holster slot", "Opens the sheath slot", "Shows the time"};

    private final KitScreen<?> screen;

    CharacterPage(KitScreen<?> screen) {
        this.screen = screen;
    }

    void draw(GuiGraphicsExtractor g, int x, int y, int mx, int my) {
        Font font = screen.mc().font;
        LocalPlayer player = screen.player();
        AbstractContainerMenu menu = screen.menu();
        strip(g, font, player, x + 181, y + 33);

        // the survivor
        g.fill(x + 236, y + 312, x + 380, y + 318, 0x40000000);
        InjuryGlowLayer.capturing = true;
        try {
            InventoryScreen.extractEntityInInventoryFollowsMouse(g, x + 222, y + 62, x + 394, y + 322, 112, 0.0625f, mx, my, player);
        } finally {
            InjuryGlowLayer.capturing = false;
        }

        // weapons: hands, the sling and the holster
        KitDraw.heading(g, font, "Weapons", x + 37, y + 58, 170, false);
        int selected = player.getInventory().getSelectedSlot();
        for (int i = 0; i < WEAPON_SLOTS.length; i++) {
            int quick = WEAPON_SLOTS[i];
            int slot = InventoryMenu.USE_ROW_SLOT_START + quick;
            int ty = y + 68 + i * 46;
            ItemStack stack = menu.slots.get(slot).getItem();
            String label = QuickSlots.label(quick);
            KitDraw.caps(g, font, label + " · " + (quick + 1), x + 37, ty, quick == selected ? KitDraw.LINK : KitDraw.DIM, 0.72f);
            int tx = x + 37, tyy = ty + 8, tw = 81, th = 32;
            if (!QuickSlots.open(player, quick) && stack.isEmpty()) {
                KitDraw.frame(g, tx, tyy, tw, th, 0x1FE8E4DA);
                KitDraw.text(g, font, "Wear a holster", tx + tw + 6, tyy + 4, KitDraw.FAINT, 1.1f);
                continue;
            }
            KitDraw.tile(g, font, stack, tx, tyy, tw, th, screen.hovered(tx, tyy, tw, th, mx, my));
            screen.hot(tx, tyy, tw, th, stack, () -> pickWeapon(slot, label, tx + tw + 6, tyy), stack.isEmpty() ? null : () -> screen.unequip(slot),
                    stack.isEmpty() ? label + ": empty" : null, slot);
            if (stack.isEmpty()) {
                KitDraw.text(g, font, "Empty", tx + tw + 6, tyy + 4, KitDraw.FAINT, 1.1f);
                continue;
            }
            KitItems.Rarity r = KitItems.rarity(stack);
            String name = stack.getHoverName().getString();
            KitDraw.caps(g, font, name, tx + tw + 6, tyy + 1, KitDraw.BRIGHT, KitDraw.capsWidth(font, name, 1.15f) > 88 ? 0.9f : 1.15f);
            KitDraw.text(g, font, KitItems.kind(stack), tx + tw + 6, tyy + 12, KitDraw.DIM, 0.8f);
            int px = tx + tw + 6;
            px += KitDraw.pips(g, px, tyy + 23, KitItems.tier(stack), r.text, 1);
            if (px > tx + tw + 6) px += 3;
            float rw = KitDraw.caps(g, font, r.label, px, tyy + 22, r.text, 0.72f);
            int lx = Math.round(px + rw + 6);
            boolean over = screen.hovered(lx, tyy + 20, 30, 9, mx, my);
            KitDraw.caps(g, font, "Inspect ›", lx, tyy + 22, over ? 0xFFFFF1B8 : KitDraw.LINK, 0.72f);
            screen.hot(lx, tyy + 20, 32, 9, () -> screen.inspect(slot));
        }

        // the belt: hip, sheath and pouches, the ones what you wear gives you
        KitDraw.heading(g, font, "Belt", x + 37, y + 210, 162, false);
        int shown = 0;
        for (int quick : BELT_SLOTS) {
            int slot = InventoryMenu.USE_ROW_SLOT_START + quick;
            ItemStack stack = menu.slots.get(slot).getItem();
            if (!QuickSlots.open(player, quick) && stack.isEmpty()) continue;
            int n = quick + 1;
            int tx = x + 37 + shown++ * 33, ty = y + 220;
            String label = QuickSlots.label(quick);
            KitDraw.tile(g, font, stack, tx, ty, 24, 24, screen.hovered(tx, ty, 24, 24, mx, my));
            KitDraw.caps(g, font, String.valueOf(n), tx + 2, ty + 2, quick == selected ? KitDraw.LINK : KitDraw.FAINT, 0.6f);
            KitDraw.capsCentred(g, font, label, tx + 12, ty + 28, quick == selected ? KitDraw.LINK : KitDraw.DIM, 0.55f);
            if (quick == selected) g.fill(tx, ty + 25, tx + 24, ty + 26, KitDraw.LINK);
            Slot target = menu.slots.get(slot);
            screen.hot(tx, ty, 24, 24, stack,
                    () -> screen.pick(label + " · " + n, slot, choices(slot, target::mayPlace, null), tx, ty + 28, 24, 24),
                    stack.isEmpty() ? null : () -> screen.unequip(slot), stack.isEmpty() ? label + " " + n + ": empty" : null, slot);
        }
        if (shown == 0) {
            KitDraw.text(g, font, "No belt: wear one for hip slots", x + 37, y + 226, KitDraw.FAINT, 1f);
        }
        load(g, font, player, x + 37, y + 258);

        // clothing: what it adds up to; the pieces themselves are on the Clothing page
        KitDraw.heading(g, font, "Clothing", x + 400, y + 58, 179, true);
        com.zomboidcraft.clothing.Clothes.Totals dressed = com.zomboidcraft.clothing.Clothes.totals(player);
        String[] cl = {"Warmth", "Stops bites", "Stops scratches", "Rain kept off"};
        String[] cv = {String.valueOf(dressed.warmth()), Math.round(dressed.meanBite() * 100) + "%", Math.round(dressed.meanScratch() * 100) + "%",
                Math.round(dressed.water() * 100) + "%"};
        float[] cb = {dressed.warmth() / 20f, dressed.meanBite(), dressed.meanScratch(), dressed.water()};
        for (int i = 0; i < cl.length; i++) {
            int cy = y + 70 + i * 24;
            KitDraw.caps(g, font, cl[i], x + 400, cy, KitDraw.DIM, 0.62f);
            KitDraw.capsRight(g, font, cv[i], x + 579, cy - 1, KitDraw.BRIGHT, 1f);
            KitDraw.bar(g, x + 400, cy + 10, 179, 1, Math.min(1, cb[i]), Float.NaN);
        }
        String clothes = "Everything you wear: Clothing ›";
        float cw = KitDraw.width(font, clothes, 0.9f);
        boolean overClothes = screen.hovered(Math.round(x + 579 - cw), y + 166, Math.round(cw), 11, mx, my);
        KitDraw.textRight(g, font, clothes, x + 579, y + 168, overClothes ? 0xFFFFF1B8 : KitDraw.LINK, 0.9f);
        screen.hot(Math.round(x + 579 - cw), y + 166, Math.round(cw), 11, () -> screen.open(KitScreen.Page.CLOTHING));

        // carry gear: bags and the off hand, then the belt kit that opens quick slots
        KitDraw.heading(g, font, "Carry", x + 420, y + 216, 159, true);
        int firstGear = InventoryMenu.SHIELD_SLOT + 1;
        int[] slots = {firstGear + GearStore.BACK, firstGear + GearStore.VEST, firstGear + GearStore.WAIST, InventoryMenu.SHIELD_SLOT};
        int[] worn = {firstGear + GearStore.BELT, firstGear + GearStore.HOLSTER, firstGear + GearStore.SHEATH, firstGear + GearStore.WRIST};
        for (int i = 0; i < worn.length; i++) {
            int slot = worn[i];
            if (slot >= menu.slots.size()) continue;
            int tx = x + 579 - 26 - (worn.length - 1 - i) * 31, ty = y + 270;
            ItemStack stack = menu.slots.get(slot).getItem();
            KitDraw.tile(g, font, stack, tx, ty, 26, 26, screen.hovered(tx, ty, 26, 26, mx, my));
            boolean idle = !stack.isEmpty() && (i == 1 || i == 2) && !QuickSlots.working(player, i == 1 ? GearStore.HOLSTER : GearStore.SHEATH);
            KitDraw.capsCentred(g, font, idle ? "Needs belt" : WORN_LABELS[i], tx + 13, ty + 30, idle ? KitDraw.WARN : KitDraw.DIM, 0.62f);
            Slot target = menu.slots.get(slot);
            String label = WORN_LABELS[i], empty = WORN_EMPTY[i];
            screen.hot(tx, ty, 26, 26, stack,
                    () -> screen.pick(label, slot, choices(slot, target::mayPlace, null), tx - 40, ty - 70, 26, 26),
                    stack.isEmpty() ? null : () -> screen.unequip(slot), stack.isEmpty() ? label + ": " + empty : null, slot);
        }
        for (int i = 0; i < slots.length; i++) {
            int slot = slots[i];
            if (slot >= menu.slots.size()) continue;
            int tx = x + 579 - 26 - (slots.length - 1 - i) * 31, ty = y + 226;
            ItemStack stack = menu.slots.get(slot).getItem();
            KitDraw.tile(g, font, stack, tx, ty, 26, 26, screen.hovered(tx, ty, 26, 26, mx, my));
            KitDraw.capsCentred(g, font, CARRY_LABELS[i], tx + 13, ty + 30, KitDraw.DIM, 0.62f);
            Slot target = menu.slots.get(slot);
            Predicate<ItemStack> fits = slot == InventoryMenu.SHIELD_SLOT ? s -> !s.isEmpty() : target::mayPlace;
            String label = CARRY_LABELS[i];
            screen.hot(tx, ty, 26, 26, stack,
                    () -> screen.pick(label, slot, choices(slot, fits, null), tx - 40, ty - 70, 26, 26),
                    stack.isEmpty() ? null : () -> screen.unequip(slot), stack.isEmpty() ? label + ": empty" : null, slot);
            BagSpec spec = BagItem.spec(stack);
            if (spec != null) {
                double inside = Weights.contents(stack);
                float full = (float) Math.min(1, inside / spec.capacity());
                g.fill(tx, ty + 27, tx + 26, ty + 28, 0x33E8E4DA);
                g.fill(tx, ty + 27, tx + Math.round(26 * full), ty + 28, full < 0.8f ? KitDraw.TEXT : KitDraw.WARN);
            }
        }

        // what the keys do
        int hx = x + 37, hy = y + H() - 14;
        hx += KitDraw.hint(g, font, "LMB", "Change", hx, hy);
        hx += KitDraw.hint(g, font, "RMB", "Take off", hx, hy);
        hx += KitDraw.hint(g, font, "F", "Inspect weapon", hx, hy);
        screen.pagesAndClose(g, font, hx, hy);
        String link = "Bags and looting: Pack ›";
        float lw = KitDraw.width(font, link, 0.9f);
        boolean over = screen.hovered(Math.round(x + 579 - lw), hy - 1, Math.round(lw), 11, mx, my);
        KitDraw.textRight(g, font, link, x + 579, hy + 1, over ? 0xFFFFF1B8 : KitDraw.LINK, 0.9f);
        screen.hot(Math.round(x + 579 - lw), hy - 1, Math.round(lw), 11, () -> screen.open(KitScreen.Page.PACK));
    }

    private static int H() {
        return KitScreen.H;
    }

    /** What your kit adds up to, across the top. */
    private void strip(GuiGraphicsExtractor g, Font font, LocalPlayer player, int x, int y) {
        Body body = Body.get(player);
        int wounds = 0;
        for (Body.Region r : Body.Region.values()) if (!body.part(r).fine()) wounds++;
        double load = Carry.load(player), cap = Carry.capacity();
        double speed = player.getAttributeValue(Attributes.MOVEMENT_SPEED) / 0.1;
        float health = player.getHealth() / player.getMaxHealth();
        String[] labels = {"Health", "Wounds", "Protection", "Endurance", "Load", "Speed"};
        String[] values = {Math.round(health * 100) + "%", wounds == 0 ? "None" : String.valueOf(wounds), String.valueOf(player.getArmorValue()),
                Math.round(Stamina.get(player)) + "%", ZcConfig.get().carry.weight ? Weights.format(load) : "Off", Math.round(speed * 100) + "%"};
        float[] bars = {health, 1 - wounds / 6f, player.getArmorValue() / 20f, Stamina.get(player) / Stamina.FULL,
                (float) (load / cap / 2), (float) Math.min(1, speed)};
        int[] colours = {health < 0.35f ? KitDraw.BAD : KitDraw.BRIGHT, wounds > 0 ? KitDraw.BAD : KitDraw.BRIGHT, KitDraw.BRIGHT,
                KitDraw.BRIGHT, Carry.tier((float) (load / cap)) > 0 ? KitDraw.WARN : KitDraw.BRIGHT, speed < 0.95 ? KitDraw.WARN : KitDraw.BRIGHT};
        for (int i = 0; i < labels.length; i++) {
            int cx = x + i * 43;
            KitDraw.caps(g, font, labels[i], cx, y, KitDraw.DIM, 0.6f);
            KitDraw.caps(g, font, values[i], cx, y + 6, colours[i], 1.1f);
            KitDraw.bar(g, cx, y + 17, 37, 1, bars[i], Float.NaN);
        }
    }

    /** Pockets, belt and every worn bag, as weight against what you can carry. */
    private void load(GuiGraphicsExtractor g, Font font, LocalPlayer player, int x, int y) {
        if (!ZcConfig.get().carry.weight) return;
        double load = Carry.load(player), cap = Carry.capacity();
        float ratio = (float) (load / cap);
        KitDraw.heading(g, font, "Load", x, y, 162, false);
        KitDraw.caps(g, font, Weights.format(load) + " / " + Weights.format(cap), x, y + 10, ratio > 1 ? KitDraw.WARN : KitDraw.BRIGHT, 1f);
        KitDraw.bar(g, x, y + 21, 162, 2, ratio / 2f, Float.NaN);
        g.fill(x + 81, y + 19, x + 82, y + 25, 0xCCE8E4DA);
        int tier = Carry.tier(ratio);
        if (tier > 0) {
            String[] words = {"", "Heavy load", "Very heavy", "Overloaded", "Crushing"};
            KitDraw.text(g, font, words[Math.min(tier, 4)], x, y + 27, KitDraw.BAD, 0.9f);
        }
    }

    private void pickWeapon(int slot, String label, int x, int y) {
        ToIntFunction<ItemStack> order = s -> KitItems.gun(s) ? 0 : s.getItem() instanceof MeleeWeaponItem ? 1 : 2;
        Slot target = screen.menu().slots.get(slot);
        screen.pick(label + " · carried", slot, choices(slot, target::mayPlace, order), x, y, 54, 22);
    }

    /** Carried slots, other than the target, holding something that passes the test; best first. */
    List<Integer> choices(int target, Predicate<ItemStack> test, ToIntFunction<ItemStack> order) {
        List<Integer> out = new ArrayList<>();
        AbstractContainerMenu menu = screen.menu();
        for (int i : screen.carried()) {
            if (i == target) continue;
            ItemStack stack = menu.slots.get(i).getItem();
            if (!stack.isEmpty() && test.test(stack)) out.add(i);
        }
        Comparator<Integer> byRarity = Comparator.comparingInt(i -> -KitItems.rarity(menu.slots.get(i).getItem()).ordinal());
        Comparator<Integer> sort = order == null ? byRarity
                : Comparator.<Integer>comparingInt(i -> order.applyAsInt(menu.slots.get(i).getItem())).thenComparing(byRarity);
        out.sort(sort);
        if (out.size() > 16) return out.subList(0, 16);
        return out;
    }
}
