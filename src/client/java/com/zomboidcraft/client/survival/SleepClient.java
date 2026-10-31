package com.zomboidcraft.client.survival;

import com.zomboidcraft.client.interact.Interactables;
import com.zomboidcraft.survival.Sleep;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * Sleeping on the client: the usable dot on beds, sofas, mattresses and cots, the interact key lies you down (crouch
 * and look at the floor to sleep on it), and the screen goes dark while you sleep, as in Zomboid.
 */
public final class SleepClient {
    private static float dark;

    private SleepClient() {
    }

    public static void register() {
        Interactables.blocks(state -> Sleep.bedOf(state) != null);
    }

    /** Lies down on what you're looking at, if you can sleep there; whether it was a place to sleep. */
    public static boolean interact(Minecraft client, HitResult hit) {
        if (!(hit instanceof BlockHitResult block) || hit.getType() != HitResult.Type.BLOCK) return false;
        LocalPlayer player = client.player;
        boolean bed = Sleep.bedOf(client.level.getBlockState(block.getBlockPos())) != null;
        boolean floor = player.isShiftKeyDown() && block.getDirection() == Direction.UP
                && block.getLocation().distanceTo(player.position()) < 1.5;
        if (!bed && !floor) return false;
        ClientPlayNetworking.send(new Sleep.LieDown(block.getBlockPos()));
        return true;
    }

    /** The dark of sleep, fading in and out, with a word on how to get up. */
    public static void render(GuiGraphicsExtractor g, float dt) {
        LocalPlayer player = Minecraft.getInstance().player;
        boolean asleep = player != null && Sleep.asleep(player);
        dark = Math.max(0, Math.min(1, dark + (asleep ? dt / 1.5f : -dt / 0.6f)));
        if (dark <= 0) return;
        int a = Math.round(dark * 0.92f * 255);
        g.fill(0, 0, g.guiWidth(), g.guiHeight(), a << 24);
        if (!asleep) return;
        Font font = Minecraft.getInstance().font;
        String line = "Asleep. Move to get up.";
        int ta = Math.round(dark * 160);
        g.text(font, line, (g.guiWidth() - font.width(line)) / 2, g.guiHeight() / 2 + 20, (ta << 24) | 0xE9E1CB, false);
    }
}
