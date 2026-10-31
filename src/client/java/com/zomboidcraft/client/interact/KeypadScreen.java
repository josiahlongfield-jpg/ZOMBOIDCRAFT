package com.zomboidcraft.client.interact;

import com.mojang.blaze3d.platform.InputConstants;
import com.zomboidcraft.client.hud.Hud;
import com.zomboidcraft.world.Keypads;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * A blast door's keypad (world/Keypads): the number pad beside the crosshair, in the kit's look. Click the keys
 * or type the digits; six of them and it sends itself, Backspace takes one back, C clears. The pad gets the
 * green dot like anything else you can use, and a tap of G (or a click) on it opens this.
 */
public final class KeypadScreen extends Screen {
    private static final int KEY = 22, GAP = 4, W = 3 * KEY + 4 * GAP;
    private static final String[] KEYS = {"1", "2", "3", "4", "5", "6", "7", "8", "9", "C", "0", "<"};

    private final BlockPos pos;
    private String typed = "";
    private final List<int[]> keys = new ArrayList<>();

    KeypadScreen(BlockPos pos) {
        super(Component.literal("Keypad"));
        this.pos = pos;
    }

    public static void register() {
        Interactables.blocks(Keypads::is);
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            if (!level.isClientSide() || !Keypads.is(level.getBlockState(hit.getBlockPos()))) return InteractionResult.PASS;
            Minecraft mc = Minecraft.getInstance();
            BlockPos at = hit.getBlockPos();
            mc.execute(() -> mc.gui.setScreen(new KeypadScreen(at)));
            return InteractionResult.SUCCESS;
        });
        InteractMenu.provider((client, player, hit, out) -> {
            BlockPos at = pad(client, hit);
            if (at != null) out.add(InteractMenu.Option.of("Enter code", () -> client.gui.setScreen(new KeypadScreen(at))));
        });
    }

    /** The interact key: true if it was on a keypad. */
    public static boolean interact(Minecraft client, HitResult hit) {
        BlockPos at = pad(client, hit);
        if (at == null) return false;
        client.gui.setScreen(new KeypadScreen(at));
        return true;
    }

    private static BlockPos pad(Minecraft client, HitResult hit) {
        if (hit instanceof BlockHitResult b && hit.getType() == HitResult.Type.BLOCK && client.level != null
                && Keypads.is(client.level.getBlockState(b.getBlockPos()))) {
            return b.getBlockPos();
        }
        return null;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float partialTick) {
        keys.clear();
        int h = 16 + 18 + 4 * (KEY + GAP) + GAP;
        int x = width / 2 + 24, y = Math.max(8, height / 2 - h / 2);
        g.fill(x, y, x + W, y + h, 0xF50C0D0F);
        g.outline(x, y, W, h, 0x2EE8E4DA);
        Hud.caps(g, font, "Keypad", x + GAP, y + 5, 0xFFF2EFE8, 1.2f, 0.8f);
        g.fill(x + 1, y + 15, x + W - 1, y + 16, 0x29E8E4DA);
        // the readout: a dash for every digit still to come
        StringBuilder shown = new StringBuilder();
        for (int i = 0; i < Keypads.DIGITS; i++) shown.append(i < typed.length() ? typed.charAt(i) : '-').append(' ');
        g.fill(x + GAP, y + 18, x + W - GAP, y + 32, 0xFF1A2318);
        text(g, shown.toString().trim(), x + W / 2f - width(shown.toString().trim(), 1f) / 2, y + 21, 0xFF9BE07A, 1f);
        int ky = y + 34 + GAP;
        for (int i = 0; i < KEYS.length; i++) {
            int kx = x + GAP + (i % 3) * (KEY + GAP), ry = ky + (i / 3) * (KEY + GAP);
            boolean over = mx >= kx && mx < kx + KEY && my >= ry && my < ry + KEY;
            g.fill(kx, ry, kx + KEY, ry + KEY, over ? 0xFF3A3C3E : 0xFF26282A);
            g.outline(kx, ry, KEY, KEY, 0x40E8E4DA);
            String k = KEYS[i];
            text(g, k, kx + KEY / 2f - width(k, 1f) / 2, ry + KEY / 2f - 4, 0xFFE8E4DA, 1f);
            keys.add(new int[]{i, kx, ry});
        }
    }

    private void text(GuiGraphicsExtractor g, String s, float x, float y, int colour, float scale) {
        var pose = g.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.scale(scale, scale);
        g.text(font, Hud.text(s), 0, 0, colour, false);
        pose.popMatrix();
    }

    private float width(String s, float scale) {
        return font.width(Hud.text(s)) * scale;
    }

    private void press(String k) {
        com.zomboidcraft.client.sound.UiSounds.play(com.zomboidcraft.client.sound.UiSounds.CLICK);
        if (k.equals("C")) {
            typed = "";
        } else if (k.equals("<")) {
            typed = typed.isEmpty() ? "" : typed.substring(0, typed.length() - 1);
        } else if (typed.length() < Keypads.DIGITS) {
            typed += k;
            if (typed.length() == Keypads.DIGITS) {
                ClientPlayNetworking.send(new Keypads.Enter(pos, typed));
                onClose();
            }
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        for (int[] k : keys) {
            if (event.x() >= k[1] && event.x() < k[1] + KEY && event.y() >= k[2] && event.y() < k[2] + KEY) {
                press(KEYS[k[0]]);
                return true;
            }
        }
        onClose();
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int k = event.key();
        if (k >= InputConstants.KEY_0 && k <= InputConstants.KEY_9) {
            press(String.valueOf(k - InputConstants.KEY_0));
            return true;
        }
        if (k >= InputConstants.KEY_NUMPAD0 && k <= InputConstants.KEY_NUMPAD9) {
            press(String.valueOf(k - InputConstants.KEY_NUMPAD0));
            return true;
        }
        if (k == InputConstants.KEY_BACKSPACE) {
            press("<");
            return true;
        }
        if (k == InputConstants.KEY_C) {
            press("C");
            return true;
        }
        return super.keyPressed(event);
    }
}
