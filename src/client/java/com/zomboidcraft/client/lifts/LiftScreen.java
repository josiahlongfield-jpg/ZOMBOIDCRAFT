package com.zomboidcraft.client.lifts;

import com.zomboidcraft.client.cars.CarClient;
import com.zomboidcraft.client.hud.Hud;
import com.zomboidcraft.world.lifts.LiftCar;
import com.zomboidcraft.world.lifts.LiftPhysics;
import com.zomboidcraft.world.lifts.LiftShaft;
import com.zomboidcraft.world.lifts.Lifts;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

/**
 * A lift car's floor panel, in the kit screen's dark glass and bone: a button for every floor, top
 * floor at the top, the ones called lit amber, with door open and door close under them and the
 * floor the car's at over them. Stays up while you ride; closes when you step out (or Esc, or the
 * interact key again).
 */
public class LiftScreen extends net.minecraft.client.gui.screens.Screen {
    private static final int PANEL = 0xF20C0D0F, EDGE = 0x29E8E4DA, LINE = 0x4DE8E4DA, TEXT = 0xFFE8E4DA, DIM = 0xA8E8E4DA;
    private static final int CELL = 24, GAP = 4;

    private final LiftCar car;

    public LiftScreen(LiftCar car) {
        super(Component.literal("Lift"));
        this.car = car;
    }

    boolean stillInside(Player player) {
        return !car.isRemoved() && LiftPhysics.inside(player) == car;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private int cols(int n) {
        return n <= 8 ? 2 : n <= 24 ? 3 : n <= 48 ? 4 : 6;
    }

    /** The panel's box: x, y, w, h. */
    private int[] box(LiftShaft s) {
        int n = s.landings.length, cols = cols(n), rows = (n + cols - 1) / cols;
        int w = cols * (CELL + GAP) - GAP + 24, h = rows * (CELL + GAP) - GAP + 24 + 28 + CELL + GAP;
        h = Math.min(h, height - 20);
        return new int[] {(width - w) / 2, (height - h) / 2, w, h};
    }

    /** Where floor i's button is (x, y). */
    private int[] cell(LiftShaft s, int[] b, int i) {
        int n = s.landings.length, cols = cols(n), rows = (n + cols - 1) / cols;
        int row = rows - 1 - i / cols, col = i % cols;
        return new int[] {b[0] + 12 + col * (CELL + GAP), b[1] + 12 + 28 + row * (CELL + GAP)};
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        LiftShaft s = car.shaft();
        if (s == null) {
            return;
        }
        int[] b = box(s);
        g.fill(b[0], b[1], b[0] + b[2], b[1] + b[3], PANEL);
        frame(g, b[0], b[1], b[2], b[3], EDGE);
        // the floor it's at, and the way it's going
        double y = car.carY();
        int dir = Math.abs(car.speed()) > 1e-4 ? car.dir() : 0;
        String at = s.label(s.showing(y, dir)) + (dir > 0 ? "  ▲" : dir < 0 ? "  ▼" : "");
        Hud.caps(g, font, "Lift", b[0] + 12, b[1] + 10, DIM, 0.9f, 0.85f);
        g.text(font, Hud.text(at), b[0] + b[2] - 12 - font.width(Hud.text(at)), b[1] + 9, Hud.AMBER, false);
        g.fill(b[0] + 12, b[1] + 24, b[0] + b[2] - 12, b[1] + 25, LINE);
        for (int i = 0; i < s.landings.length; i++) {
            int[] c = cell(s, b, i);
            boolean lit = car.called(i), over = in(mouseX, mouseY, c[0], c[1], CELL, CELL);
            button(g, c[0], c[1], s.label(i), lit, over);
        }
        // door open and door close
        int[] o = doorButtons(s, b);
        button(g, o[0], o[1], "◀▶", false, in(mouseX, mouseY, o[0], o[1], CELL, CELL));
        button(g, o[2], o[1], "▶◀", false, in(mouseX, mouseY, o[2], o[1], CELL, CELL));
        String key = "[" + CarClient.INTERACT.getTranslatedKeyMessage().getString() + "] close";
        g.text(font, Hud.text(key), b[0] + b[2] - 12 - font.width(Hud.text(key)), o[1] + 8, DIM, false);
    }

    private int[] doorButtons(LiftShaft s, int[] b) {
        int y = b[1] + b[3] - 12 - CELL;
        return new int[] {b[0] + 12, y, b[0] + 12 + CELL + GAP};
    }

    private void button(GuiGraphicsExtractor g, int x, int y, String label, boolean lit, boolean over) {
        g.fill(x, y, x + CELL, y + CELL, over ? 0x33E8E4DA : 0x1AE8E4DA);
        frame(g, x, y, CELL, CELL, lit ? Hud.AMBER : over ? LINE : EDGE);
        int w = font.width(Hud.text(label));
        g.text(font, Hud.text(label), x + (CELL - w) / 2, y + (CELL - 8) / 2, lit ? Hud.AMBER : TEXT, false);
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

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        LiftShaft s = car.shaft();
        if (s == null || event.button() != 0) {
            return true;
        }
        int[] b = box(s);
        for (int i = 0; i < s.landings.length; i++) {
            int[] c = cell(s, b, i);
            if (in(event.x(), event.y(), c[0], c[1], CELL, CELL)) {
                ClientPlayNetworking.send(new Lifts.Pick(car.getId(), i));
                return true;
            }
        }
        int[] o = doorButtons(s, b);
        if (in(event.x(), event.y(), o[0], o[1], CELL, CELL)) {
            ClientPlayNetworking.send(new Lifts.Pick(car.getId(), -1));
        } else if (in(event.x(), event.y(), o[2], o[1], CELL, CELL)) {
            ClientPlayNetworking.send(new Lifts.Pick(car.getId(), -2));
        }
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (CarClient.INTERACT.matches(event)) {
            onClose();
            return true;
        }
        return super.keyPressed(event);
    }
}
