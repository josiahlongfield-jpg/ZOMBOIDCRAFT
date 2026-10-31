package com.zomboidcraft.client.kit;

import com.zomboidcraft.npc.NpcEntity;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** The first G on someone you haven't bonded with: bond, or not now. Either way her page opens next. */
public class BondPrompt extends Screen {
    private static final int W = 230, H = 82;
    private final NpcEntity npc;
    private final CompanionScreen.Tab tab;

    public BondPrompt(NpcEntity npc, CompanionScreen.Tab tab) {
        super(Component.literal("Bond"));
        this.npc = npc;
        this.tab = tab;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private int x() {
        return (width - W) / 2;
    }

    private int y() {
        return (height - H) / 2;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float partialTick) {
        g.fill(0, 0, width, height, 0x99080A0C);
        int x = x(), y = y();
        g.fill(x, y, x + W, y + H, 0xF50C0D0F);
        KitDraw.frame(g, x, y, W, H, 0x2EE8E4DA);
        KitDraw.caps(g, font, "Bond with " + npc.getName().getString() + "?", x + 10, y + 9, KitDraw.BRIGHT, 1.1f);
        KitDraw.wrap(g, font, "Bonded, you can bring up her page from anywhere with K. You can bond with as many as you like, "
                + "and unbond on her page.", x + 10, y + 23, W - 20, KitDraw.DIM);
        button(g, "Bond", x + 10, y + H - 20, true, mx, my);
        button(g, "Not now", x + 62, y + H - 20, false, mx, my);
    }

    private void button(GuiGraphicsExtractor g, String label, int bx, int by, boolean primary, int mx, int my) {
        int w = Math.round(KitDraw.width(font, label, 0.85f)) + 14;
        boolean over = KitDraw.in(mx, my, bx, by, w, 13);
        g.fill(bx, by, bx + w, by + 13, primary ? 0xEBE8E4DA : over ? 0x26E8E4DA : 0x08E8E4DA);
        KitDraw.frame(g, bx, by, w, 13, primary ? 0xFFE8E4DA : 0x4DE8E4DA);
        KitDraw.text(g, font, label, bx + 7, by + 3, primary ? 0xFF121314 : KitDraw.TEXT, 0.85f);
    }

    /** Bonds (or not) and goes on to her page. */
    public void answer(boolean bond) {
        if (bond) Bonds.bond(npc, true);
        else Bonds.decline(npc);
        com.zomboidcraft.client.sound.UiSounds.play(com.zomboidcraft.client.sound.UiSounds.CLICK);
        minecraft.gui.setScreen(new CompanionScreen(npc, tab));
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        int x = x(), y = y() + H - 20;
        if (event.y() >= y && event.y() < y + 13) {
            if (event.x() >= x + 10 && event.x() < x + 56) answer(true);
            else if (event.x() >= x + 62 && event.x() < x + 130) answer(false);
        }
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == com.mojang.blaze3d.platform.InputConstants.KEY_RETURN) {
            answer(true);
            return true;
        }
        return super.keyPressed(event);
    }
}
