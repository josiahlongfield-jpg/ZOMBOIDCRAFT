package com.zomboidcraft.client.hud;

import com.mojang.blaze3d.platform.InputConstants;
import com.zomboidcraft.combat.CombatNet;
import com.zomboidcraft.survival.Body;
import com.zomboidcraft.survival.Treatment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.component.Consumable;
import org.joml.Matrix3x2fStack;

/**
 * The quick-use wheel: hold Z and the quick slots you have fan out round the crosshair. The mouse points instead of
 * turning you; let go and a weapon or tool goes to your hands, while a bandage, splint, food or drink is used there and
 * then (as a timed action on the server). Right-click while it's up to put it away.
 */
public final class QuickWheel {
    private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(com.zomboidcraft.ZomboidCraft.id("wheel"));
    public static final KeyMapping WHEEL = new KeyMapping("key.zomboidcraft.wheel", InputConstants.KEY_Z, CATEGORY);

    /** The quick slots on the wheel: the ones you have, or that still hold something. */
    private static int[] slots(LocalPlayer player) {
        int[] all = new int[Inventory.getSelectionSize()];
        int n = 0;
        for (int i = 0; i < all.length; i++) {
            if (com.zomboidcraft.carry.QuickSlots.open(player, i) || !player.getInventory().getItem(i).isEmpty()) all[n++] = i;
        }
        return java.util.Arrays.copyOf(all, n);
    }
    private static final float RADIUS = 62, BOX = 28, REACH = 60, DEAD = 12;

    private static boolean open;
    private static float px, py;
    private static final Hud.Fade fade = new Hud.Fade();

    public static void register() {
        KeyMappingHelper.registerKeyMapping(WHEEL);
        ClientTickEvents.END_CLIENT_TICK.register(QuickWheel::tick);
    }

    /** True while the wheel is up: the mouse points at it rather than turning you. */
    public static boolean open() {
        return open;
    }

    /** The mouse's turn while the wheel is up, moving the pointer instead. */
    public static void point(double yaw, double pitch) {
        px += (float) yaw * 0.5f;
        py += (float) pitch * 0.5f;
        float len = (float) Math.sqrt(px * px + py * py);
        if (len > REACH) {
            px *= REACH / len;
            py *= REACH / len;
        }
    }

    private static void tick(Minecraft mc) {
        LocalPlayer player = mc.player;
        boolean down = WHEEL.isDown() && player != null && mc.gui.screen() == null && Hud.active();
        if (down && !open) {
            open = true;
            px = py = 0;
        }
        if (!open) return;
        // clicks while it's up don't swing or use; a right-click puts it away
        boolean cancel = false;
        while (mc.options.keyUse.consumeClick()) cancel = true;
        while (mc.options.keyAttack.consumeClick()) {
            // a left-click picks, like letting go
            down = false;
        }
        if (cancel || player == null) {
            open = false;
            return;
        }
        if (!down) {
            open = false;
            int[] slots = slots(player);
            int k = pointed(slots.length);
            if (k >= 0) pick(player, slots[k]);
        }
    }

    /** Which of the wheel's n places is under the pointer, or -1 in the middle. */
    private static int pointed(int n) {
        if (n == 0 || px * px + py * py < DEAD * DEAD) return -1;
        double angle = Math.atan2(py, px) + Math.PI / 2;
        return Math.floorMod((int) Math.round(angle / (2 * Math.PI / n)), n);
    }

    private static void pick(LocalPlayer player, int slot) {
        ItemStack stack = player.getInventory().getItem(slot);
        if (used(player, stack)) {
            ClientPlayNetworking.send(new CombatNet.Action(CombatNet.Action.QUICK_USE + slot));
        } else if (!treatment(stack)) {
            player.getInventory().setSelectedSlot(slot);
        }
    }

    private static boolean treatment(ItemStack stack) {
        return Treatment.Kind.BANDAGE.is(stack) || Treatment.Kind.SPLINT.is(stack);
    }

    /** Whether letting go over this uses it rather than drawing it. */
    private static boolean used(LocalPlayer player, ItemStack stack) {
        if (stack.has(DataComponents.CONSUMABLE)) return true;
        for (Treatment.Kind kind : Treatment.Kind.values()) {
            if (kind.is(stack)) return Treatment.target(Body.get(player), kind) != null;
        }
        return false;
    }

    /** What letting go would do. */
    private static String verb(LocalPlayer player, ItemStack stack) {
        if (stack.isEmpty()) return "Empty hands";
        for (Treatment.Kind kind : Treatment.Kind.values()) {
            if (!kind.is(stack)) continue;
            Body.Region r = Treatment.target(Body.get(player), kind);
            if (r != null) return (kind == Treatment.Kind.BANDAGE ? "Bandage " : "Splint ") + r.label;
            return kind == Treatment.Kind.BANDAGE ? "Nothing to bind" : "Nothing broken";
        }
        Consumable c = stack.get(DataComponents.CONSUMABLE);
        if (c != null) return c.animation() == ItemUseAnimation.DRINK ? "Drink" : "Eat";
        return "Take in hand";
    }

    static void render(GuiGraphicsExtractor g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        float a = fade.update(open);
        if (a <= 0 || player == null) return;
        Font font = mc.font;
        float cx = g.guiWidth() / 2f, cy = g.guiHeight() / 2f;
        g.fill(0, 0, g.guiWidth(), g.guiHeight(), Hud.alpha(0x8C07080A, a));
        int[] slots = slots(player);
        int pointedAt = open ? pointed(slots.length) : -1;
        int pointed = pointedAt < 0 ? -1 : slots[pointedAt];
        Inventory inventory = player.getInventory();
        int held = inventory.getSelectedSlot();
        for (int k = 0; k < slots.length; k++) {
            int i = slots[k];
            double angle = -Math.PI / 2 + k * 2 * Math.PI / slots.length;
            boolean on = i == pointed;
            float size = on ? BOX + 4 : BOX;
            float x = cx + (float) Math.cos(angle) * RADIUS * (0.7f + 0.3f * a) - size / 2;
            float y = cy + (float) Math.sin(angle) * RADIUS * (0.7f + 0.3f * a) - size / 2;
            int ix = Math.round(x), iy = Math.round(y), is = Math.round(size);
            g.fill(ix, iy, ix + is, iy + is, Hud.alpha(on ? 0xB3201E1A : 0x8C101113, a));
            int edge = on ? 0xFFE0C25E : 0x38E9E1CB;
            g.fill(ix, iy, ix + is, iy + 1, Hud.alpha(edge, a));
            g.fill(ix, iy + is - 1, ix + is, iy + is, Hud.alpha(edge, a));
            g.fill(ix, iy, ix + 1, iy + is, Hud.alpha(edge, a));
            g.fill(ix + is - 1, iy, ix + is, iy + is, Hud.alpha(edge, a));
            if (i == held) g.fill(ix + 2, iy + is - 3, ix + is - 2, iy + is - 2, Hud.alpha(Hud.AMBER, a));
            ItemStack stack = inventory.getItem(i);
            if (!stack.isEmpty()) {
                Matrix3x2fStack pose = g.pose();
                pose.pushMatrix();
                float s = (size - 6) / 16f;
                pose.translate(x + 3, y + 3);
                pose.scale(s, s);
                g.item(stack, 0, 0);
                pose.popMatrix();
                if (stack.getCount() > 1) {
                    String c = "×" + stack.getCount();
                    float w = Hud.capsWidth(font, c, 0, 0.75f);
                    Hud.caps(g, font, c, x + size - 2 - w, y + size - 8, Hud.alpha(Hud.BONE, a), 0, 0.75f);
                }
            }
            Hud.caps(g, font, String.valueOf(i + 1), x + 2, y + 1.5f, Hud.alpha(Hud.BONE_FAINT, a), 0, 0.65f);
        }
        // the middle: what's pointed at and what letting go does
        float r = RADIUS - BOX / 2 - 6;
        g.fill(Math.round(cx - r * 0.72f), Math.round(cy - 18), Math.round(cx + r * 0.72f), Math.round(cy + 18), Hud.alpha(0x66000000, a));
        if (pointed >= 0) {
            ItemStack stack = inventory.getItem(pointed);
            String name = stack.isEmpty() ? "Nothing" : stack.getHoverName().getString();
            float nw = Hud.capsWidth(font, name, 1f, name.length() > 14 ? 0.75f : 0.95f);
            Hud.caps(g, font, name, cx - nw / 2, cy - 12, Hud.alpha(0xFFF2EFE8, a), 1f, name.length() > 14 ? 0.75f : 0.95f);
            String verb = verb(player, stack);
            float vw = Hud.capsWidth(font, verb, 1f, 0.75f);
            Hud.caps(g, font, verb, cx - vw / 2, cy + 2, Hud.alpha(0xFFE0C25E, a), 1f, 0.75f);
        } else {
            String hint = "Point, let go";
            float hw = Hud.capsWidth(font, hint, 1f, 0.75f);
            Hud.caps(g, font, hint, cx - hw / 2, cy - 4, Hud.alpha(Hud.BONE_DIM, a), 1f, 0.75f);
        }
        if (open) {
            float ax = cx + px * 0.5f, ay = cy + py * 0.5f;
            g.fill(Math.round(ax) - 1, Math.round(ay) - 1, Math.round(ax) + 2, Math.round(ay) + 2, Hud.alpha(0xFFE0C25E, a));
        }
    }

    private QuickWheel() {
    }
}
