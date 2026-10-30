package com.zomboidcraft.client.npc;

import com.mojang.blaze3d.platform.InputConstants;
import com.zomboidcraft.client.cars.CarClient;
import com.zomboidcraft.client.hud.Hud;
import com.zomboidcraft.npc.NpcBond;
import com.zomboidcraft.npc.NpcEntity;
import com.zomboidcraft.npc.NpcNet;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * Talking to a companion (sneak + interact on her), in the kit screen's dark glass and bone: how she feels about you at
 * the top, then what you can do: compliment her, flirt, give her what's in your hand, hug her, take her hand (or let
 * go), kiss her, or talk about her outfit (WardrobeScreen). She answers in chat, yes or no as she feels. Pick with the mouse
 * or 1-7; Esc or the interact key closes.
 */
public class TalkScreen extends net.minecraft.client.gui.screens.Screen {
    private static final int PANEL = 0xF20C0D0F, EDGE = 0x29E8E4DA, LINE = 0x4DE8E4DA, TEXT = 0xFFE8E4DA, DIM = 0xA8E8E4DA,
            GOLD = 0xFFE0C25E;
    private static final int W = 230, ROW = 20, PAD = 10, HEAD = 48;
    private static final String[][] ACTS = {
            {"Compliment her", "Say something nice"},
            {"Flirt", "See if she flirts back"},
            {"Give her what you're holding", "Food, water, flowers, a gun..."},
            {"Hug her", "Arms around her"},
            {"Hold her hand", "She walks at your side"},
            {"Kiss her", "Only if she wants to"},
            {"Her outfit...", "What you think of it"},
    };

    private final NpcEntity npc;

    public TalkScreen(NpcEntity npc) {
        super(Component.literal("Talk"));
        this.npc = npc;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void tick() {
        if (minecraft.player == null || !npc.isAlive() || npc.isRemoved() || npc.distanceTo(minecraft.player) > 4) {
            onClose();
        }
    }

    private int[] box() {
        int h = HEAD + ACTS.length * ROW + PAD;
        return new int[] {(width - W) / 2, (height - h) / 2, W, h};
    }

    private int rowY(int[] b, int i) {
        return b[1] + HEAD + i * ROW;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        int[] b = box();
        g.fill(b[0], b[1], b[0] + b[2], b[1] + b[3], PANEL);
        frame(g, b[0], b[1], b[2], b[3], EDGE);
        int bits = npc.bondBits();
        float trust = bits & 127, affection = bits >> 7 & 127, attraction = bits >> 14 & 127;
        boolean together = (bits >> 21 & 1) != 0, holding = (bits >> 22 & 1) != 0;
        String mood = (bits >> 25 & 1) != 0 ? "Jealous" : (bits >> 24 & 1) != 0 ? "Romantic" : (bits >> 23 & 1) != 0 ? "Flirty" : null;
        Hud.caps(g, font, npc.getName().getString(), b[0] + PAD, b[1] + 9, TEXT, 0.9f, 0.95f);
        if (mood != null) {
            String m = mood.toUpperCase();
            g.text(font, Hud.text(m), b[0] + b[2] - PAD - font.width(Hud.text(m)), b[1] + 8, GOLD, false);
        }
        g.textWithWordWrap(font, Hud.text(NpcBond.words(trust, affection, attraction, together)), b[0] + PAD, b[1] + 22, b[2] - PAD * 2, DIM, false);
        g.fill(b[0] + PAD, b[1] + HEAD - 4, b[0] + b[2] - PAD, b[1] + HEAD - 3, LINE);
        for (int i = 0; i < ACTS.length; i++) {
            int y = rowY(b, i);
            boolean over = in(mouseX, mouseY, b[0] + 4, y, b[2] - 8, ROW - 2);
            if (over) {
                g.fill(b[0] + 4, y, b[0] + b[2] - 4, y + ROW - 2, 0x1FE8E4DA);
                frame(g, b[0] + 4, y, b[2] - 8, ROW - 2, LINE);
            }
            String label = i == 4 && holding ? "Let go of her hand" : ACTS[i][0];
            g.text(font, Hud.text((i + 1) + "  " + label), b[0] + PAD, y + 5, over ? 0xFFF2EFE8 : TEXT, false);
            String hint = i == 6 && (bits >> 29 & 1) != 0 ? "She asked you" : ACTS[i][1];
            g.text(font, Hud.text(hint), b[0] + b[2] - PAD - font.width(Hud.text(hint)), y + 5, DIM, false);
        }
    }

    private static void frame(GuiGraphicsExtractor g, int x, int y, int w, int h, int colour) {
        g.fill(x, y, x + w, y + 1, colour);
        g.fill(x, y + h - 1, x + w, y + h, colour);
        g.fill(x, y + 1, x + 1, y + h - 1, colour);
        g.fill(x + w - 1, y + 1, x + w, y + h - 1, colour);
    }

    private static boolean in(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && my >= y && mx < x + w && my < y + h;
    }

    private void pick(int i) {
        if (i == 6) {
            NpcSkin skin = NpcSkin.get(npc.model());
            if (skin != null && !skin.has("sweater")) {
                // a model in whole outfits, not piece by piece: those are picked by command
                minecraft.player.sendOverlayMessage(Component.literal("She changes outfit by command: /zc npc outfit "
                        + String.join("|", skin.looks)));
                onClose();
                return;
            }
            minecraft.gui.setScreen(new WardrobeScreen(npc, null));
            return;
        }
        ClientPlayNetworking.send(new NpcNet.Talk(npc.getId(), i));
        onClose();
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() != 0) {
            return true;
        }
        int[] b = box();
        for (int i = 0; i < ACTS.length; i++) {
            if (in(event.x(), event.y(), b[0] + 4, rowY(b, i), b[2] - 8, ROW - 2)) {
                pick(i);
                return true;
            }
        }
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (CarClient.INTERACT.matches(event)) {
            onClose();
            return true;
        }
        int key = event.key();
        if (key >= InputConstants.KEY_1 && key < InputConstants.KEY_1 + ACTS.length) {
            pick(key - InputConstants.KEY_1);
            return true;
        }
        return super.keyPressed(event);
    }
}
