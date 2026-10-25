package com.zomboidcraft.client.kit;

import com.zomboidcraft.ZomboidCraft;
import com.zomboidcraft.carry.Weights;
import com.zomboidcraft.combat.AttachmentItem;
import com.zomboidcraft.combat.Attachments;
import com.zomboidcraft.combat.GunFinish;
import com.zomboidcraft.combat.GunState;
import com.zomboidcraft.combat.GunType;
import com.zomboidcraft.combat.WeaponSpecs;
import com.zomboidcraft.combat.rolls.Flaw;
import com.zomboidcraft.combat.rolls.Rolls;
import com.zomboidcraft.combat.rolls.Stat;
import com.zomboidcraft.combat.rolls.Tier;
import com.zomboidcraft.combat.rolls.WeaponCard;
import com.zomboidcraft.combat.rolls.WeaponRoll;
import java.util.List;
import java.util.Locale;
import net.fabricmc.fabric.api.client.rendering.v1.ClientTooltipComponentCallback;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * A weapon's tooltip in every other screen (the field kit, chests, the creative tab), drawn as the kit screen's card:
 * the rarity band, tier pips and where it was found, any flaw, each rolled stat as a thin bar against an average copy
 * of the model, condition, finish, fitted parts and the keys that use it.
 */
public final class WeaponTip implements ClientTooltipComponent {
    /** Dark glass with a faint bone edge, in place of Minecraft's purple frame. */
    public static final Identifier STYLE = ZomboidCraft.id("kit_card");
    /** Minecraft pads a tooltip's contents by this much on every side; the card fills the padding too. */
    private static final int PAD = 3;
    private static final int W = KitCard.W;

    private final ItemStack stack;
    private final KitItems.Rarity rarity;
    private final @Nullable WeaponRoll roll;
    private final @Nullable GunType gun;
    private final @Nullable String place;
    private final boolean jammed;
    private final List<Flaw> flaws;
    private final List<Stat> stats;
    private final List<Attachments.Slot> sockets;
    private final String finish;
    private final int height;

    public WeaponTip(ItemStack stack) {
        this.stack = stack;
        rarity = KitItems.rarity(stack);
        roll = Rolls.get(stack);
        gun = GunType.of(stack);
        place = Rolls.placeLabel(stack);
        jammed = gun != null && stack.getOrDefault(GunState.TYPE, GunState.EMPTY_MAG).jammed();
        flaws = Rolls.flaws(stack);
        stats = roll == null ? List.of() : Stat.of(stack);
        sockets = gun == null ? List.of() : Attachments.slots(stack);
        finish = gun == null ? "" : GunFinish.line(stack).getString();

        int h = 34 + 6 + 16;
        if (place != null) h += 11;
        if (jammed) h += 11;
        h += flaws.size() * 11;
        if (!stats.isEmpty()) h += 3 + stats.size() * 11 + 3;
        if (stack.isDamageableItem()) h += 14;
        if (!finish.isEmpty()) h += 11;
        if (!sockets.isEmpty()) h += 22;
        h += 12 + 16; // weight, keys
        height = h;
    }

    public static void register() {
        // the mixin on the tooltip hands weapons straight to the card; this covers any other path that asks for one
        ClientTooltipComponentCallback.EVENT.register(data -> data instanceof WeaponCard card ? new WeaponTip(card.stack()) : null);
    }

    @Override
    public int getWidth(Font font) {
        return W - 2 * PAD;
    }

    @Override
    public int getHeight(Font font) {
        return height - 2 * PAD;
    }

    @Override
    public void extractImage(Font font, int left, int top, int width, int tall, GuiGraphicsExtractor g) {
        int x = left - PAD, y = top - PAD;

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

        // tier and what's loaded
        int px = x + 7;
        if (roll != null) {
            px += KitDraw.pips(g, px, cy + 1, roll.tier(), KitDraw.TEXT, 2) + 4;
            KitDraw.caps(g, font, Tier.label(roll.tier()) + (roll.worked() ? "  ·  bench worked" : ""), px, cy + 1, KitDraw.TEXT, 0.8f);
        } else {
            KitDraw.caps(g, font, "Unrolled", px, cy + 1, KitDraw.FAINT, 0.8f);
        }
        if (gun != null) KitDraw.capsRight(g, font, KitItems.loaded(stack), x + W - 7, cy - 1, KitDraw.BRIGHT, 1.3f);
        cy += 16;

        if (place != null) {
            KitDraw.text(g, font, place, x + 7, cy, KitDraw.DIM, 0.85f);
            cy += 11;
        }
        if (jammed) {
            KitDraw.text(g, font, "Jammed: press R to clear", x + 7, cy, KitDraw.BAD, 0.85f);
            cy += 11;
        }
        for (Flaw flaw : flaws) {
            KitDraw.text(g, font, flaw.label(), x + 7, cy, KitDraw.BAD, 0.85f);
            String effect = flaw.effect().toLowerCase(Locale.ROOT);
            if (KitDraw.width(font, flaw.label(), 0.85f) + KitDraw.width(font, effect, 0.8f) + 10 < W - 14) {
                KitDraw.textRight(g, font, effect, x + W - 7, cy + 1, KitDraw.DIM, 0.8f);
            }
            cy += 11;
        }

        // each rolled stat: where it landed in the model's range, and what that does against an average copy
        if (!stats.isEmpty()) {
            cy += 3;
            for (Stat stat : stats) {
                int pct = Rolls.percent(stack, stat);
                KitDraw.text(g, font, stat.label(), x + 7, cy, KitDraw.DIM, 0.85f);
                bar(g, x + 66, cy + 2, 60, 3, Rolls.position(stack, stat));
                KitDraw.textRight(g, font, (pct > 0 ? "+" : "") + pct + "%", x + W - 7, cy,
                        pct >= 3 ? KitDraw.GOOD : pct <= -3 ? KitDraw.BAD : KitDraw.TEXT, 0.85f);
                cy += 11;
            }
            cy += 3;
        }

        if (stack.isDamageableItem()) {
            float left = KitItems.condition(stack);
            KitDraw.text(g, font, "Condition", x + 7, cy, KitDraw.DIM, 0.85f);
            KitDraw.bar(g, x + 66, cy + 2, 60, 3, left, Float.NaN);
            KitDraw.textRight(g, font, KitItems.conditionWord(left), x + W - 7, cy, left > 0.35f ? KitDraw.TEXT : KitDraw.BAD, 0.85f);
            cy += 14;
        }
        if (!finish.isEmpty()) {
            KitDraw.text(g, font, finish, x + 7, cy, KitDraw.LINK, 0.85f);
            cy += 11;
        }

        // fitted parts, one ring per socket
        if (!sockets.isEmpty()) {
            int sx = x + 7;
            for (Attachments.Slot socket : sockets) {
                ItemStack part = Attachments.get(stack, socket);
                KitDraw.frame(g, sx, cy, 18, 18, part.isEmpty() ? 0x33E8E4DA : 0x99E8E4DA);
                if (!part.isEmpty()) KitDraw.item(g, part, sx + 2, cy + 2, 14);
                else KitDraw.capsCentred(g, font, AttachmentItem.slotName(socket).substring(0, 1), sx + 9, cy + 6, KitDraw.FAINT, 0.7f);
                sx += 21;
                if (sx > x + W - 25) break;
            }
            cy += 22;
        }

        KitDraw.text(g, font, "Weight " + Weights.format(Weights.of(stack)), x + 7, cy, KitDraw.DIM, 0.85f);
        cy += 12;

        // what the keys do
        g.fill(x, cy, x + W, cy + 1, 0x1FE8E4DA);
        int hx = x + 7;
        if (gun != null) {
            hx += KitDraw.hint(g, font, "RMB", "Aim", hx, cy + 4);
            hx += KitDraw.hint(g, font, "LMB", "Fire", hx, cy + 4);
            KitDraw.hint(g, font, "R", gun.magazine() != null ? "Reload" : "Load", hx, cy + 4);
        } else {
            KitDraw.hint(g, font, "LMB", "Swing", hx, cy + 4);
            WeaponSpecs.WeaponSpec spec = KitItems.spec(stack);
            if (spec != null && spec.twoHanded()) KitDraw.capsRight(g, font, "Two-handed", x + W - 7, cy + 6, KitDraw.FAINT, 0.7f);
        }
    }

    /** A thin bar from worst to best, with ticks above and below at an average copy. */
    private static void bar(GuiGraphicsExtractor g, int x, int y, int w, int h, float value) {
        KitDraw.bar(g, x, y, w, h, value, Float.NaN);
        int mid = x + w / 2;
        g.fill(mid, y - 1, mid + 1, y, 0x80E8E4DA);
        g.fill(mid, y + h, mid + 1, y + h + 1, 0x80E8E4DA);
    }
}
