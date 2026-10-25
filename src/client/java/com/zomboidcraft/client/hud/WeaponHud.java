package com.zomboidcraft.client.hud;

import com.zomboidcraft.client.combat.CombatClient;
import com.zomboidcraft.combat.Attachments;
import com.zomboidcraft.combat.GunState;
import com.zomboidcraft.combat.GunType;
import com.zomboidcraft.combat.MeleeWeaponItem;
import com.zomboidcraft.combat.RifleItem;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix3x2fStack;

/**
 * The weapon in your hands, bottom right: for a gun the rounds left in big figures beside its magazine, for a melee
 * weapon its name and condition in notches. It shows when you draw, fire, reload, swing or aim, and stays up while
 * the magazine runs low or the gun is jammed.
 */
final class WeaponHud {
    private static final int RIGHT = 12, BOTTOM = 12;
    private static final float SHOW = 3f;

    private static final Hud.Fade fade = new Hud.Fade();
    private static ItemStack last = ItemStack.EMPTY;
    private static int lastSlot = -1;
    private static float since = 99;
    private static float lastSwing;

    static void render(GuiGraphicsExtractor g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) return;
        ItemStack hand = player.getMainHandItem();
        boolean gun = GunType.isGun(hand);
        boolean melee = !gun && (hand.getItem() instanceof MeleeWeaponItem || hand.isDamageableItem());
        since += Hud.dt;
        int slot = player.getInventory().getSelectedSlot();
        // anything about the weapon changing (rounds, jam, wear, a new one) brings it up
        if (slot != lastSlot || !ItemStack.matches(last, hand)) {
            since = 0;
            last = hand.copy();
            lastSlot = slot;
        }
        float swing = player.getAttackAnim(delta.getGameTimeDeltaPartialTick(true));
        if (swing > 0 && lastSwing == 0) since = 0;
        lastSwing = swing;

        boolean urgent = false;
        GunState state = null;
        int capacity = 0;
        if (gun) {
            state = hand.getOrDefault(GunState.TYPE, GunState.EMPTY_MAG);
            capacity = RifleItem.capacity(hand);
            urgent = state.jammed() || CombatClient.reloading(hand) || !state.mag() || state.rounds() <= capacity / 5
                    || player.isUsingItem() && GunType.isGun(player.getUseItem());
        }
        float a = fade.update((gun || melee) && (Hud.peek || since < SHOW || urgent));
        if (a <= 0 || hand.isEmpty() || !(gun || melee)) return;
        float drift = (1 - a) * 4;
        Font font = mc.font;
        int right = g.guiWidth() - RIGHT;
        int bottom = g.guiHeight() - BOTTOM;
        if (gun) gun(g, font, hand, state, capacity, right + drift, bottom, a);
        else melee(g, font, hand, right + drift, bottom, a);
    }

    private static void gun(GuiGraphicsExtractor g, Font font, ItemStack hand, GunState state, int capacity,
                            float right, int bottom, float a) {
        // the magazine, filled to the rounds left with the top round showing
        float mx = right - 10, my = bottom - 30;
        float frac = state.mag() && capacity > 0 ? state.rounds() / (float) capacity : 0;
        if (state.mag()) {
            Hud.sprite(g, Hud.id("magazine"), mx, my, 10, 30, Hud.alpha(0xFFFFFFFF, a));
            Matrix3x2fStack pose = g.pose();
            pose.pushMatrix();
            pose.translate(mx, my);
            pose.scale(0.5f, 0.5f);
            int fill = Math.round(frac * 44);
            if (fill > 0) {
                g.fill(5, 52 - fill, 15, 52, Hud.alpha(0x38C8A050, a));
                g.fill(6, 50 - fill, 14, 53 - fill, Hud.alpha(0xFFC9A24E, a));
                g.fill(7, 50 - fill, 10, 51 - fill, Hud.alpha(0xFFF0D590, a));
            }
            pose.popMatrix();
        }

        boolean jammed = state.jammed();
        boolean reloading = CombatClient.reloading(hand);
        String caption = jammed ? "Jammed, press R" : reloading ? "Reloading" : !state.mag() ? "No magazine, press R"
                : state.rounds() == 0 ? "Empty, press R" : caliber(hand);
        float cw = Hud.capsWidth(font, caption, 1.2f, .75f);
        int captionInk = jammed ? 0xFFE03020 : state.mag() && state.rounds() == 0 || !state.mag() ? 0xFFE07A5A : Hud.BONE_DIM;
        float tr = state.mag() ? mx - 5 : right;
        Hud.caps(g, font, caption, tr - cw, bottom - 6, Hud.alpha(captionInk, a), 1.2f, .75f);

        if (!state.mag()) return;
        String small = "/ " + capacity;
        float sw = Hud.capsWidth(font, small, .5f, 1f);
        Hud.caps(g, font, small, tr - sw, bottom - 18, Hud.alpha(Hud.BONE_DIM, a), .5f, 1f);
        Component rounds = Hud.big(String.valueOf(state.rounds()));
        int ink = state.rounds() == 0 ? Hud.RED : state.rounds() <= capacity / 5 ? 0xFFE4A46A : Hud.BONE;
        Matrix3x2fStack pose = g.pose();
        pose.pushMatrix();
        pose.translate(tr - sw - 2 - font.width(rounds), bottom - 29);
        g.text(font, rounds, 0, 0, Hud.alpha(ink, a), true);
        pose.popMatrix();
    }

    private static String caliber(ItemStack gun) {
        GunType type = GunType.of(gun);
        String sights = Attachments.scoped(gun) ? "Scope" : "Iron sights";
        return type == null ? sights : type.caliber() + " · " + sights;
    }

    private static void melee(GuiGraphicsExtractor g, Font font, ItemStack hand, float right, int bottom, float a) {
        String name = hand.getHoverName().getString();
        float nw = Hud.capsWidth(font, name, 1.4f, 1f);
        Hud.caps(g, font, name, right - nw, bottom - 26, Hud.alpha(Hud.BONE, a), 1.4f, 1f);
        if (!hand.isDamageableItem()) return;
        float left = 1 - hand.getDamageValue() / (float) hand.getMaxDamage();
        int filled = Math.round(left * 6);
        for (int i = 0; i < 6; i++) {
            int x = Math.round(right) - (6 - i) * 7;
            g.fill(x, bottom - 14, x + 6, bottom - 12, Hud.alpha(i < filled ? Hud.BONE : 0x2EE9E1CB, a));
        }
        String word = BeltHud.condition(hand);
        float ww = Hud.capsWidth(font, word, 1.2f, .75f);
        Hud.caps(g, font, word, right - ww, bottom - 6, Hud.alpha(Hud.BONE_DIM, a), 1.2f, .75f);
    }

    private WeaponHud() {
    }
}
