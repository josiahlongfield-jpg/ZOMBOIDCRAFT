package com.zomboidcraft.client.kit;

import com.zomboidcraft.carry.BagItem;
import com.zomboidcraft.carry.BagSpec;
import com.zomboidcraft.carry.Weights;
import com.zomboidcraft.combat.AttachmentItem;
import com.zomboidcraft.combat.Attachments;
import com.zomboidcraft.combat.GunFinish;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;

/**
 * The card that follows the mouse over a tile: a band in the item's rarity colour with its name, kind and rarity;
 * tier pips and what's loaded; the parts fitted to a gun; its stat bars; condition, weight and the keys that act on it.
 */
final class KitCard {
    static final int W = 164;

    static void draw(GuiGraphicsExtractor g, Font font, ItemStack stack, int slot, int mx, int my, int sw, int sh, boolean onBench) {
        KitItems.Rarity rarity = KitItems.rarity(stack);
        List<KitItems.Stat> stats = KitItems.gun(stack) ? KitItems.gunStats(stack, null)
                : KitItems.weapon(stack) ? KitItems.meleeStats(stack) : KitItems.clothingStats(stack);
        List<Attachments.Slot> sockets = KitItems.gun(stack) ? Attachments.slots(stack) : List.of();
        int tier = KitItems.tier(stack);
        String finish = KitItems.gun(stack) ? GunFinish.line(stack).getString() : "";

        // height first, so the card can sit where it fits
        int h = 34 + 8;
        boolean tierRow = tier > 0 || KitItems.gun(stack) || stack.getCount() > 1;
        if (tierRow) h += 16;
        if (!sockets.isEmpty()) h += 22;
        if (!stats.isEmpty()) h += stats.size() * 11 + 4;
        if (stack.isDamageableItem()) h += 14;
        h += 12; // weight
        if (!finish.isEmpty()) h += 11;
        h += 16; // hints

        int x = mx + 14, y = my - 10;
        if (x + W > sw - 4) x = mx - 14 - W;
        if (y + h > sh - 4) y = sh - 4 - h;
        if (y < 4) y = 4;
        g.fill(x + 3, y + 5, x + W + 3, y + h + 5, 0x59000000);
        g.fill(x, y, x + W, y + h, 0xF50C0D0F);
        KitDraw.frame(g, x, y, W, h, 0x2EE8E4DA);

        // the band
        g.fill(x, y, x + W, y + 34, rarity.band);
        g.fillGradient(x, y, x + W, y + 17, 0x14FFFFFF, 0x00FFFFFF);
        String name = stack.getHoverName().getString();
        float scale = KitDraw.capsWidth(font, name, 1.5f) > W - 14 ? 1.15f : 1.5f;
        KitDraw.caps(g, font, name, x + 7, y + 5, rarity.bandText, scale);
        int sub = rarity.bandText == 0xFFF2EFE8 ? 0xD9F2EFE8 : 0xD91D1B16;
        KitDraw.text(g, font, KitItems.kind(stack), x + 7, y + 22, sub, 0.9f);
        KitDraw.capsRight(g, font, rarity.label, x + W - 7, y + 23, rarity.bandText, 0.8f);
        int cy = y + 34 + 6;

        if (tierRow) {
            int px = x + 7;
            if (tier > 0) {
                px += KitDraw.pips(g, px, cy + 1, tier, KitDraw.TEXT, 2) + 4;
                KitDraw.caps(g, font, KitItems.tierName(tier), px, cy + 1, KitDraw.TEXT, 0.8f);
            } else if (KitItems.weapon(stack)) {
                KitDraw.caps(g, font, "Unrolled", px, cy + 1, KitDraw.FAINT, 0.8f);
            }
            if (KitItems.gun(stack)) {
                KitDraw.capsRight(g, font, KitItems.loaded(stack), x + W - 7, cy - 1, KitDraw.BRIGHT, 1.3f);
            } else if (stack.getCount() > 1) {
                KitDraw.capsRight(g, font, "x" + stack.getCount(), x + W - 7, cy - 1, KitDraw.BRIGHT, 1.3f);
            }
            cy += 16;
        }

        // fitted parts, one ring per socket
        if (!sockets.isEmpty()) {
            int px = x + 7;
            for (Attachments.Slot socket : sockets) {
                ItemStack part = Attachments.get(stack, socket);
                KitDraw.frame(g, px, cy, 18, 18, part.isEmpty() ? 0x33E8E4DA : 0x99E8E4DA);
                if (!part.isEmpty()) KitDraw.item(g, part, px + 2, cy + 2, 14);
                else KitDraw.capsCentred(g, font, AttachmentItem.slotName(socket).substring(0, 1), px + 9, cy + 6, KitDraw.FAINT, 0.7f);
                px += 21;
                if (px > x + W - 25) break;
            }
            cy += 22;
        }

        if (!stats.isEmpty()) {
            for (KitItems.Stat s : stats) {
                KitDraw.text(g, font, s.label(), x + 7, cy, KitDraw.DIM, 0.85f);
                KitDraw.bar(g, x + 66, cy + 2, 66, 3, s.value(), s.changed());
                KitDraw.textRight(g, font, s.shown(), x + W - 7, cy, KitDraw.TEXT, 0.85f);
                cy += 11;
            }
            cy += 4;
        }

        if (stack.isDamageableItem()) {
            float left = KitItems.condition(stack);
            KitDraw.text(g, font, "Condition", x + 7, cy, KitDraw.DIM, 0.85f);
            KitDraw.bar(g, x + 66, cy + 2, 66, 3, left, Float.NaN);
            KitDraw.textRight(g, font, KitItems.conditionWord(left), x + W - 7, cy, left > 0.35f ? KitDraw.TEXT : KitDraw.BAD, 0.85f);
            cy += 14;
        }

        BagSpec bag = BagItem.spec(stack);
        String weight = "Weight " + Weights.format(Weights.of(stack));
        if (bag != null) weight += "  ·  holds " + Weights.format(bag.capacity()) + " in " + bag.slots() + " slots";
        KitDraw.text(g, font, weight, x + 7, cy, KitDraw.DIM, 0.85f);
        cy += 12;
        if (!finish.isEmpty()) {
            KitDraw.text(g, font, finish, x + 7, cy, KitDraw.LINK, 0.85f);
            cy += 11;
        }

        // what the keys do
        g.fill(x, cy, x + W, cy + 1, 0x1FE8E4DA);
        int hx = x + 7;
        if (slot >= 0) {
            hx += KitDraw.hint(g, font, "LMB", onBench ? "Fit" : "Swap", hx, cy + 4);
            if (KitItems.weapon(stack) && !onBench) KitDraw.hint(g, font, "F", "Inspect", hx, cy + 4);
        }
    }

    private KitCard() {
    }
}
