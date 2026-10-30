package com.zomboidcraft.client.npc;

import com.mojang.blaze3d.platform.InputConstants;
import com.zomboidcraft.client.cars.CarClient;
import com.zomboidcraft.client.hud.Hud;
import com.zomboidcraft.npc.NpcEntity;
import com.zomboidcraft.npc.NpcNet;
import com.zomboidcraft.npc.NpcWardrobe;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * Her clothes, in the talk panel's dark glass and bone. Two ways in: from the talk panel (sneak + interact, "Her
 * outfit") you tell her what you think of what she's wearing or suggest something else, and she takes it or not; from
 * the green dot at her waist you pick her outfit yourself and she changes into it. Mouse or 1-9; Esc or interact closes.
 */
public class WardrobeScreen extends net.minecraft.client.gui.screens.Screen {
    private static final int PANEL = 0xF20C0D0F, EDGE = 0x29E8E4DA, LINE = 0x4DE8E4DA, TEXT = 0xFFE8E4DA, DIM = 0xA8E8E4DA,
            GOLD = 0xFFE0C25E;
    private static final int W = 230, ROW = 20, PAD = 10, HEAD = 40;

    private record Row(String label, String hint, int act) {
    }

    private final NpcEntity npc;
    private final boolean dressHer;

    public WardrobeScreen(NpcEntity npc, boolean dressHer) {
        super(Component.literal(dressHer ? "Her clothes" : "Her outfit"));
        this.npc = npc;
        this.dressHer = dressHer;
    }

    private NpcWardrobe.Outfit worn() {
        NpcWardrobe.Outfit o = NpcWardrobe.Outfit.of(npc.bondBits() >> 26 & 7);
        return o == null ? NpcWardrobe.Outfit.WORK : o;
    }

    private List<Row> rows() {
        List<Row> rows = new ArrayList<>();
        NpcWardrobe.Outfit worn = worn();
        if (!dressHer) {
            rows.add(new Row("Looks great on you", "Tell her", NpcWardrobe.LIKE));
            rows.add(new Row("Not my favourite", "Be honest", NpcWardrobe.DISLIKE));
        }
        for (NpcWardrobe.Outfit o : NpcWardrobe.Outfit.values()) {
            if (o == NpcWardrobe.Outfit.SLEEP || o == worn) continue;
            rows.add(new Row((dressHer ? "Put her in " : "Try ") + o.label, dressHer ? "" : "Suggest it",
                    (dressHer ? NpcWardrobe.DRESS : NpcWardrobe.SUGGEST) + o.ordinal()));
        }
        return rows;
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

    private int[] box(int n) {
        int h = HEAD + n * ROW + PAD;
        return new int[] {(width - W) / 2, (height - h) / 2, W, h};
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        List<Row> rows = rows();
        int[] b = box(rows.size());
        g.fill(b[0], b[1], b[0] + b[2], b[1] + b[3], PANEL);
        frame(g, b[0], b[1], b[2], b[3], EDGE);
        Hud.caps(g, font, dressHer ? "Her clothes" : "Her outfit", b[0] + PAD, b[1] + 9, TEXT, 0.9f, 0.95f);
        if (!dressHer && (npc.bondBits() >> 29 & 1) != 0) {
            String m = "SHE ASKED";
            g.text(font, Hud.text(m), b[0] + b[2] - PAD - font.width(Hud.text(m)), b[1] + 8, GOLD, false);
        }
        g.text(font, Hud.text("She's wearing " + worn().label), b[0] + PAD, b[1] + 22, DIM, false);
        g.fill(b[0] + PAD, b[1] + HEAD - 4, b[0] + b[2] - PAD, b[1] + HEAD - 3, LINE);
        for (int i = 0; i < rows.size(); i++) {
            int y = b[1] + HEAD + i * ROW;
            boolean over = in(mouseX, mouseY, b[0] + 4, y, b[2] - 8, ROW - 2);
            if (over) {
                g.fill(b[0] + 4, y, b[0] + b[2] - 4, y + ROW - 2, 0x1FE8E4DA);
                frame(g, b[0] + 4, y, b[2] - 8, ROW - 2, LINE);
            }
            g.text(font, Hud.text((i + 1) + "  " + rows.get(i).label()), b[0] + PAD, y + 5, over ? 0xFFF2EFE8 : TEXT, false);
            String hint = rows.get(i).hint();
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

    private void pick(Row row) {
        ClientPlayNetworking.send(new NpcNet.Talk(npc.getId(), row.act()));
        onClose();
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() != 0) {
            return true;
        }
        List<Row> rows = rows();
        int[] b = box(rows.size());
        for (int i = 0; i < rows.size(); i++) {
            if (in(event.x(), event.y(), b[0] + 4, b[1] + HEAD + i * ROW, b[2] - 8, ROW - 2)) {
                pick(rows.get(i));
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
        List<Row> rows = rows();
        int key = event.key();
        if (key >= InputConstants.KEY_1 && key < InputConstants.KEY_1 + Math.min(9, rows.size())) {
            pick(rows.get(key - InputConstants.KEY_1));
            return true;
        }
        return super.keyPressed(event);
    }
}
