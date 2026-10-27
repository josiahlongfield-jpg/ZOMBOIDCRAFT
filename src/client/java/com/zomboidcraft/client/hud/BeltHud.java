package com.zomboidcraft.client.hud;

import com.zomboidcraft.carry.QuickSlots;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix3x2fStack;

/**
 * The quick slots: bare squares along the bottom, one for every slot what you wear gives you, empty or not, each
 * named underneath, with the real item models in them. It tucks away a few seconds after you last drew from it or put something on it.
 */
final class BeltHud {
    private static final int BOX = 24, GAP = 5, BOTTOM = 14;
    private static final float SHOW = 4f, NAME = 1.8f;

    private static final Hud.Fade fade = new Hud.Fade();
    private static final ItemStack[] last = new ItemStack[Inventory.getSelectionSize()];
    private static int lastSelected = -1;
    private static float sinceUse = 99, sinceSwitch = 99;
    private static final Hud.Fade name = new Hud.Fade();

    static void render(GuiGraphicsExtractor g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) return;
        Inventory inventory = player.getInventory();
        int selected = inventory.getSelectedSlot();
        if (!QuickSlots.open(player, selected) && inventory.getItem(selected).isEmpty()) {
            // the slot you were on went away with what opened it (a belt taken off): back to your hands
            inventory.setSelectedSlot(QuickSlots.HANDS);
            selected = inventory.getSelectedSlot();
        }
        watch(inventory, selected);
        float a = fade.update(Hud.peek || sinceUse < SHOW);
        float n = name.update(Hud.peek || sinceSwitch < NAME);
        if (a <= 0) return;

        // every slot you have sits in the row, empty or not, so the one you want is always where you left it; the
        // ones what you wear doesn't give you stay out of it
        int[] shown = new int[last.length];
        int count = 0;
        for (int i = 0; i < last.length; i++) {
            if (QuickSlots.open(player, i) || !inventory.getItem(i).isEmpty()) shown[count++] = i;
        }
        int width = count * BOX + (count - 1) * GAP;
        // tucking away: it fades and drops below the screen's edge, so the 3D items go with it
        float drop = (1 - a) * (1 - a) * 44;
        float x0 = (g.guiWidth() - width) / 2f;
        float y0 = g.guiHeight() - BOTTOM - BOX + drop;
        Font font = mc.font;
        for (int k = 0; k < count; k++) {
            int slot = shown[k];
            float x = x0 + k * (BOX + GAP);
            square(g, font, inventory.getItem(slot), slot, slot == selected, x, y0, a, QuickSlots.on(player));
        }

        ItemStack held = inventory.getItem(selected);
        if (n > 0 && !held.isEmpty()) {
            String title = held.getHoverName().getString();
            String word = condition(held);
            float tw = Hud.capsWidth(font, title, 1.2f, 1f);
            float ww = word == null ? 0 : Hud.capsWidth(font, word, 1f, 0.8f) + 6;
            float tx = (g.guiWidth() - tw - ww) / 2f;
            float ty = y0 - 13;
            Hud.caps(g, font, title, tx, ty, Hud.alpha(Hud.BONE, n * a), 1.2f, 1f);
            if (word != null) Hud.caps(g, font, word, tx + tw + 6, ty + 1.5f, Hud.alpha(Hud.BONE_DIM, n * a), 1f, 0.8f);
        }
    }

    private static void square(GuiGraphicsExtractor g, Font font, ItemStack stack, int slot, boolean selected,
                               float x, float y, float a, boolean labels) {
        if (selected) Hud.sprite(g, Hud.id("square_selected"), x - 4, y - 4, BOX + 8, BOX + 8, Hud.alpha(0xFFFFFFFF, a));
        else Hud.sprite(g, Hud.id("square"), x, y, BOX, BOX, Hud.alpha(0xFFFFFFFF, a));
        Matrix3x2fStack pose = g.pose();
        if (!stack.isEmpty()) {
            pose.pushMatrix();
            pose.translate(x + 2, y + 2);
            pose.scale(1.25f, 1.25f);
            g.item(stack, 0, 0);
            pose.popMatrix();
            if (stack.isDamageableItem() && stack.getDamageValue() > 0) {
                float left = 1 - stack.getDamageValue() / (float) stack.getMaxDamage();
                int colour = left > .6f ? 0xFF9CC46A : left > .3f ? 0xFFD9A33A : 0xFFC0442C;
                int bx = Math.round(x) + 3, by = Math.round(y) + BOX - 3, bw = BOX - 6;
                g.fill(bx, by, bx + bw, by + 1, Hud.alpha(0x8C000000, a));
                g.fill(bx, by, bx + Math.max(1, Math.round(bw * left)), by + 1, Hud.alpha(colour, a));
            }
            if (stack.getCount() > 1) {
                String c = String.valueOf(stack.getCount());
                float w = Hud.capsWidth(font, c, 0, 0.8f);
                Hud.caps(g, font, c, x + BOX - 2 - w, y + BOX - 9, Hud.alpha(Hud.BONE, a), 0, 0.8f);
            }
        }
        Hud.caps(g, font, String.valueOf(slot + 1), x + 2, y + 1.5f,
                Hud.alpha(selected ? 0xE6E0A85A : Hud.BONE_FAINT, a), 0, 0.7f);
        if (labels) {
            String label = QuickSlots.label(slot);
            float lw = Hud.capsWidth(font, label, 0.6f, 0.55f);
            Hud.caps(g, font, label, x + (BOX - lw) / 2f, y + BOX + 6, Hud.alpha(selected ? Hud.BONE : Hud.BONE_DIM, a), 0.6f, 0.55f);
        }
    }

    /** Wakes the belt when the hands or the belt change. */
    private static void watch(Inventory inventory, int selected) {
        sinceUse += Hud.dt;
        sinceSwitch += Hud.dt;
        if (selected != lastSelected) {
            if (lastSelected >= 0) {
                sinceUse = 0;
                sinceSwitch = 0;
            }
            lastSelected = selected;
        }
        for (int i = 0; i < last.length; i++) {
            ItemStack now = inventory.getItem(i);
            ItemStack before = last[i];
            if (before == null) {
                last[i] = now.copy();
                continue;
            }
            // a new thing on the belt shows it; a swing wearing a weapon down doesn't
            if (!ItemStack.isSameItem(before, now) || before.getCount() != now.getCount() && !now.isEmpty()) {
                sinceUse = 0;
                if (i == selected) sinceSwitch = 0;
            }
            if (!ItemStack.matches(before, now)) last[i] = now.copy();
        }
    }

    /** How a weapon or tool has worn, Zomboid's words; null for things that don't wear. */
    static String condition(ItemStack stack) {
        if (!stack.isDamageableItem()) return null;
        float left = 1 - stack.getDamageValue() / (float) stack.getMaxDamage();
        return left > .85f ? "Like new" : left > .6f ? "Worn" : left > .3f ? "Battered" : "About to break";
    }

    private BeltHud() {
    }
}
