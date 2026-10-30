package com.zomboidcraft.client.kit;

import com.zomboidcraft.client.npc.NpcRenderer;
import com.zomboidcraft.client.npc.NpcSkin;
import com.zomboidcraft.npc.NpcEntity;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * Your bonded companions side by side (Josiah 2026-10-30): the one you're on stands big in the middle in her idle, the
 * next smaller and faded to the right, the one before to the left. Scroll (or A/D) to slide them along; click the
 * middle one, or Enter, for her page, which has a way back here.
 */
public class BondsScreen extends Screen {
    private int centre;
    /** Where the row is drawn, easing towards centre. */
    private float shown;
    private List<NpcEntity> list = List.of();
    private long wheelAt;

    public BondsScreen(int centre) {
        super(Component.literal("Companions"));
        this.centre = centre;
        this.shown = centre;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void tick() {
        list = Bonds.companions(minecraft);
        if (list.isEmpty()) onClose();
        centre = Mth.clamp(centre, 0, Math.max(0, list.size() - 1));
    }

    @Override
    protected void init() {
        super.init();
        list = Bonds.companions(minecraft);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float partialTick) {
        extractBlurredBackground(g);
        g.fill(0, 0, width, height, 0xC8080A0C);
        g.fillGradient(0, 0, width, 60, 0xE6050607, 0x00050607);
        g.fillGradient(0, height - 50, width, height, 0x00050607, 0xD9050607);
        shown += (centre - shown) * 0.2f;

        int floor = height - 54, spread = Math.min(width / 3, Math.round(height * 0.62f));
        // the far ones first, so the one you're on is drawn over her neighbours
        Integer[] order = new Integer[list.size()];
        for (int i = 0; i < order.length; i++) order[i] = i;
        java.util.Arrays.sort(order, (a, b) -> Float.compare(Math.abs(b - shown), Math.abs(a - shown)));
        for (int i : order) {
            float d = i - shown, a = Math.min(1f, Math.abs(d));
            if (Math.abs(d) > 2.3f) continue;
            float x = width / 2f + Math.signum(d) * (Math.abs(d) <= 1 ? Math.abs(d) * spread : spread + (Math.abs(d) - 1) * spread * 0.8f);
            float tallPx = (height - 96) * (1 - 0.45f * a);
            draw(g, list.get(i), Math.round(x), floor, tallPx, Math.round(15 - 9 * a));
            if (a > 0.05f) {
                // the ones either side sit back, faded
                int half = Math.round(tallPx * 0.4f), alpha = Math.round(150 * a);
                g.fill(Math.round(x) - half, Math.round(floor - tallPx) - 12, Math.round(x) + half, floor + 6, alpha << 24 | 0x080A0C);
            }
        }

        KitDraw.caps(g, font, "Companions", 16, 12, KitDraw.BRIGHT, 1.3f);
        KitDraw.text(g, font, "Scroll to pick one. Click her to open her page.", 16, 26, KitDraw.DIM, 0.9f);
        if (!list.isEmpty()) {
            KitDraw.capsRight(g, font, (centre + 1) + " / " + list.size(), width - 16, 14, KitDraw.DIM, 1f);
            NpcEntity on = list.get(centre);
            KitDraw.capsCentred(g, font, on.getName().getString(), width / 2f, height - 36, KitDraw.BRIGHT, 1.6f);
            KitDraw.capsCentred(g, font, "Bonded", width / 2f, height - 21, KitDraw.LINK, 0.7f);
        }
        int hx = 16, hy = height - 14;
        hx += KitDraw.hint(g, font, "Scroll", "Switch", hx, hy);
        hx += KitDraw.hint(g, font, "LMB", "Her page", hx, hy);
        KitDraw.hint(g, font, "K", "Close", hx, hy);
    }

    /** One companion standing with her feet on the floor line, this tall, lit this much (15 full). */
    private void draw(GuiGraphicsExtractor g, NpcEntity npc, int cx, int floor, float tallPx, int light) {
        NpcSkin skin = NpcSkin.get(npc.model());
        float tall = skin != null ? skin.top : npc.getBbHeight();
        float scale = tallPx / tall;
        // the inventory drawing puts her feet (bbHeight / 2 + 0.0625) below the box's centre
        int cy = Math.round(floor - (npc.getBbHeight() / 2 + 0.0625f) * scale), reach = Math.round(3 * scale) + 200;
        NpcRenderer.portrait = true;
        NpcRenderer.portraitYaw = 180f;
        NpcRenderer.portraitAction = 0;
        NpcRenderer.portraitLight = light;
        try {
            InventoryScreen.extractEntityInInventoryFollowsMouse(g, cx - reach, cy - reach, cx + reach, cy + reach, Math.round(scale), 0.0625f,
                    cx, cy, npc);
        } finally {
            NpcRenderer.portrait = false;
            NpcRenderer.portraitLight = 15;
        }
    }

    private void go(int step) {
        int to = Mth.clamp(centre + step, 0, Math.max(0, list.size() - 1));
        if (to != centre) {
            centre = to;
            com.zomboidcraft.client.sound.UiSounds.play(com.zomboidcraft.client.sound.UiSounds.CLICK);
        }
    }

    /** Opens the page of the one you're on. */
    public void pick() {
        if (centre < list.size()) minecraft.gui.setScreen(new CompanionScreen(list.get(centre), CompanionScreen.Tab.HER));
    }

    /** Slides along (the UI test films it). */
    public void slide(int step) {
        go(step);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        long now = net.minecraft.util.Util.getMillis();
        if (sy != 0 && now - wheelAt > 150) {
            wheelAt = now;
            go(sy < 0 ? 1 : -1);
        }
        return true;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() != 0) return true;
        double off = event.x() - width / 2.0;
        if (Math.abs(off) < width * 0.14) pick();
        else go(off > 0 ? 1 : -1);
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int k = event.key();
        if (Bonds.KEY.matches(event)) {
            onClose();
            return true;
        }
        if (k == com.mojang.blaze3d.platform.InputConstants.KEY_A || k == com.mojang.blaze3d.platform.InputConstants.KEY_LEFT) {
            go(-1);
            return true;
        }
        if (k == com.mojang.blaze3d.platform.InputConstants.KEY_D || k == com.mojang.blaze3d.platform.InputConstants.KEY_RIGHT) {
            go(1);
            return true;
        }
        if (k == com.mojang.blaze3d.platform.InputConstants.KEY_RETURN) {
            pick();
            return true;
        }
        return super.keyPressed(event);
    }
}
