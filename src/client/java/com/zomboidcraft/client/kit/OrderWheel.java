package com.zomboidcraft.client.kit;

import com.zomboidcraft.npc.NpcEntity;
import com.zomboidcraft.npc.NpcPanel;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * The quick orders for a companion (Josiah 2026-10-30): middle-click while her green dot shows and this ring opens
 * round the crosshair. Point at an order and let go of the middle button (or click) to give it; let go in the middle
 * to give none.
 */
public class OrderWheel extends Screen {
    private static final String[] LABELS = {"Follow me", "Wait here", "Go home", "Set home here", "Sit", "Keep lookout",
            "How are you?", "Her page"};
    private static final int[] CODES = {NpcPanel.FOLLOW, NpcPanel.WAIT, NpcPanel.GO_HOME, NpcPanel.SET_HOME, NpcPanel.POSE,
            NpcPanel.POSE + 2, NpcPanel.HOW_ARE_YOU, -1};
    private static final int RADIUS = 62, DEAD = 14;

    private final NpcEntity npc;
    private int chosen = -1;

    public OrderWheel(NpcEntity npc) {
        super(Component.literal("Orders"));
        this.npc = npc;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void tick() {
        if (!npc.isAlive() || minecraft.player == null || npc.distanceToSqr(minecraft.player) > 8 * 8) onClose();
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float partialTick) {
        int cx = width / 2, cy = height / 2;
        g.fill(0, 0, width, height, 0x66080A0C);
        float dx = mx - cx, dy = my - cy;
        chosen = -1;
        if (dx * dx + dy * dy > DEAD * DEAD) {
            // slice 0 straight up, round clockwise
            float a = (float) Math.atan2(dx, -dy);
            chosen = Math.floorMod(Math.round(a / ((float) (Math.PI * 2) / LABELS.length)), LABELS.length);
        }
        KitDraw.capsCentred(g, font, npc.getName().getString(), cx, cy - 4, KitDraw.LINK, 0.8f);
        for (int i = 0; i < LABELS.length; i++) {
            float a = i * (float) (Math.PI * 2) / LABELS.length;
            int x = Math.round(cx + Mth.sin(a) * RADIUS), y = Math.round(cy - Mth.cos(a) * RADIUS);
            float w = KitDraw.width(font, LABELS[i], 0.85f) + 12;
            boolean on = i == chosen;
            int bx = Math.round(x - w / 2), by = y - 8;
            g.fill(bx, by, bx + Math.round(w), by + 16, on ? 0xF2E8E4DA : 0xE60C0D0F);
            KitDraw.frame(g, bx, by, Math.round(w), 16, on ? 0xFFE8E4DA : 0x4DE8E4DA);
            KitDraw.textCentred(g, font, LABELS[i], x, by + 4, on ? 0xFF121314 : KitDraw.TEXT, 0.85f);
        }
        KitDraw.diamond(g, cx, cy + 8, 2, 0xFF8FC46A);
    }

    private void give() {
        int i = chosen;
        onClose();
        if (i < 0) return;
        if (CODES[i] < 0) {
            minecraft.gui.setScreen(new CompanionScreen(npc, CompanionScreen.Tab.HER));
            return;
        }
        ClientPlayNetworking.send(new NpcPanel.Order(npc.getId(), CODES[i]));
        com.zomboidcraft.client.sound.UiSounds.play(com.zomboidcraft.client.sound.UiSounds.CLICK);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0) give();
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (event.button() == 2) give();
        return true;
    }
}
