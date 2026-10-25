package com.zomboidcraft.client.kit;

import com.zomboidcraft.ZcConfig;
import com.zomboidcraft.carry.BagItem;
import com.zomboidcraft.carry.BagSpec;
import com.zomboidcraft.carry.Carry;
import com.zomboidcraft.carry.CarrySlots;
import com.zomboidcraft.carry.GearStore;
import com.zomboidcraft.carry.Weights;
import com.zomboidcraft.client.hud.InjuryGlowLayer;
import com.zomboidcraft.combat.MeleeWeaponItem;
import com.zomboidcraft.combat.RifleItem;
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
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Weapons and the belt down the left, clothing and carry gear down the right, your survivor in the middle and a strip
 * of what your kit adds up to across the top. Every slot opens a picker of what you carry that could go in it.
 */
final class CharacterPage {
    private static final String[] WEAPON_LABELS = {"Primary", "Sidearm", "Melee"};
    private static final String[] CLOTHING_LABELS = {"Head", "Torso", "Legs", "Feet"};
    private static final EquipmentSlot[] CLOTHING_SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    private static final String[] CARRY_LABELS = {"Back", "Vest", "Waist", "Sling", "Off hand"};

    private final KitScreen screen;

    CharacterPage(KitScreen screen) {
        this.screen = screen;
    }

    void draw(GuiGraphicsExtractor g, int x, int y, int mx, int my) {
        Font font = screen.mc().font;
        LocalPlayer player = screen.player();
        InventoryMenu menu = screen.menu();
        strip(g, font, player, x + 181, y + 33);

        // the survivor
        g.fill(x + 236, y + 312, x + 380, y + 318, 0x40000000);
        InjuryGlowLayer.capturing = true;
        try {
            InventoryScreen.extractEntityInInventoryFollowsMouse(g, x + 222, y + 62, x + 394, y + 322, 112, 0.0625f, mx, my, player);
        } finally {
            InjuryGlowLayer.capturing = false;
        }

        // weapons: the first three belt slots
        KitDraw.heading(g, font, "Weapons", x + 37, y + 58, 170, false);
        int selected = player.getInventory().getSelectedSlot();
        for (int i = 0; i < 3; i++) {
            int slot = InventoryMenu.USE_ROW_SLOT_START + i;
            int ty = y + 68 + i * 46;
            ItemStack stack = menu.slots.get(slot).getItem();
            KitDraw.caps(g, font, WEAPON_LABELS[i] + " · " + (i + 1), x + 37, ty, i == selected ? KitDraw.LINK : KitDraw.DIM, 0.72f);
            int tx = x + 37, tyy = ty + 8, tw = 81, th = 32;
            KitDraw.tile(g, font, stack, tx, tyy, tw, th, screen.hovered(tx, tyy, tw, th, mx, my));
            int which = i;
            screen.hot(tx, tyy, tw, th, stack, () -> pickWeapon(slot, which, tx + tw + 6, tyy), stack.isEmpty() ? null : () -> screen.unequip(slot),
                    stack.isEmpty() ? WEAPON_LABELS[i] + ": empty" : null, slot);
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

        // the belt: the other six
        KitDraw.heading(g, font, "Belt", x + 37, y + 210, 162, false);
        for (int i = 3; i < 9; i++) {
            int slot = InventoryMenu.USE_ROW_SLOT_START + i;
            int n = i + 1;
            int tx = x + 37 + (i - 3) * 28, ty = y + 220;
            ItemStack stack = menu.slots.get(slot).getItem();
            KitDraw.tile(g, font, stack, tx, ty, 24, 24, screen.hovered(tx, ty, 24, 24, mx, my));
            KitDraw.caps(g, font, String.valueOf(i + 1), tx + 2, ty + 2, i == selected ? KitDraw.LINK : KitDraw.FAINT, 0.6f);
            if (i == selected) g.fill(tx, ty + 25, tx + 24, ty + 26, KitDraw.LINK);
            screen.hot(tx, ty, 24, 24, stack,
                    () -> screen.pick("Belt · " + n, slot, choices(slot, s -> !s.isEmpty(), null), tx, ty + 28, 24, 24),
                    stack.isEmpty() ? null : () -> screen.unequip(slot), stack.isEmpty() ? "Belt " + n + ": empty" : null, slot);
        }
        load(g, font, player, x + 37, y + 258);

        // clothing down the right
        KitDraw.heading(g, font, "Clothing", x + 400, y + 58, 179, true);
        for (int i = 0; i < 4; i++) {
            int slot = InventoryMenu.ARMOR_SLOT_START + i;
            int tx = x + 547, ty = y + 66 + i * 37;
            ItemStack stack = menu.slots.get(slot).getItem();
            KitDraw.tile(g, font, stack, tx, ty, 32, 32, screen.hovered(tx, ty, 32, 32, mx, my));
            EquipmentSlot on = CLOTHING_SLOTS[i];
            String label = CLOTHING_LABELS[i];
            screen.hot(tx, ty, 32, 32, stack,
                    () -> screen.pick(label, slot, choices(slot, s -> KitItems.wornOn(s) == on, null), tx - 104, ty, 30, 30),
                    stack.isEmpty() ? null : () -> screen.unequip(slot), stack.isEmpty() ? label + ": nothing worn" : null, slot);
            KitDraw.capsRight(g, font, CLOTHING_LABELS[i], tx - 6, ty + 2, KitDraw.DIM, 0.72f);
            if (stack.isEmpty()) {
                KitDraw.textRight(g, font, "Nothing worn", tx - 6, ty + 12, KitDraw.FAINT, 1f);
                continue;
            }
            String name = stack.getHoverName().getString();
            KitDraw.capsRight(g, font, name, tx - 6, ty + 11, KitDraw.BRIGHT, KitDraw.capsWidth(font, name, 1.1f) > 130 ? 0.85f : 1.1f);
            KitItems.Rarity r = KitItems.rarity(stack);
            String line = r.label + (stack.isDamageableItem() ? " · " + KitItems.conditionWord(KitItems.condition(stack)) : "");
            KitDraw.textRight(g, font, line, tx - 6, ty + 22, KitDraw.DIM, 0.8f);
        }

        // carry gear: bags, the sling and the off hand
        KitDraw.heading(g, font, "Carry", x + 420, y + 220, 159, true);
        int firstGear = InventoryMenu.SHIELD_SLOT + 1;
        int[] slots = {firstGear + GearStore.BACK, firstGear + GearStore.VEST, firstGear + GearStore.WAIST, firstGear + GearStore.SLING,
                InventoryMenu.SHIELD_SLOT};
        for (int i = 0; i < slots.length; i++) {
            int slot = slots[i];
            if (slot >= menu.slots.size()) continue;
            int tx = x + 579 - 26 - (slots.length - 1 - i) * 31, ty = y + 230;
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
        hx += KitDraw.hint(g, font, "Q/E", "Pages", hx, hy);
        KitDraw.hint(g, font, "I", "Close", hx, hy);
        KitDraw.textRight(g, font, "Bags and looting stay on " + screen.mc().options.keyInventory.getTranslatedKeyMessage().getString(),
                x + 579, hy + 1, KitDraw.FAINT, 0.9f);
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

    private void pickWeapon(int slot, int which, int x, int y) {
        ToIntFunction<ItemStack> order = s -> switch (which) {
            case 0 -> KitItems.gun(s) && !KitItems.sidearm(s) ? 0 : 1;
            case 1 -> KitItems.sidearm(s) ? 0 : 1;
            default -> s.getItem() instanceof MeleeWeaponItem ? 0 : 1;
        };
        screen.pick(WEAPON_LABELS[which] + " · carried", slot,
                choices(slot, s -> s.getItem() instanceof RifleItem || s.getItem() instanceof MeleeWeaponItem, order), x, y, 54, 22);
    }

    /** Carried slots, other than the target, holding something that passes the test; best first. */
    private List<Integer> choices(int target, Predicate<ItemStack> test, ToIntFunction<ItemStack> order) {
        List<Integer> out = new ArrayList<>();
        InventoryMenu menu = screen.menu();
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
