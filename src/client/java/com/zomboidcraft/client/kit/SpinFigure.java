package com.zomboidcraft.client.kit;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.entity.LivingEntity;

/**
 * A figure that stands still in its idle pose and looks straight out, turned by dragging it round with the mouse
 * (Josiah 2026-11-02: "stop them from following the mouse position ... have the ability to click and turn the
 * character model"). Vanilla's figure is drawn with the "mouse" held dead centre, so it faces out; while
 * {@link #drawing} is set, InventoryScreenMixin turns it by {@link #yaw} and stills its legs.
 */
public final class SpinFigure {
    /** Set only while one of these figures is being drawn, so the HUD figure and other mirrors are left alone. */
    public static boolean drawing;
    /** Degrees the figure being drawn is turned round its own vertical axis. */
    public static float yaw;

    private float turn;
    private boolean dragging;
    private double lastX;

    /** Draws {@code who} inside the box, standing still and turned however far it has been dragged. */
    public void draw(GuiGraphicsExtractor g, int x1, int y1, int x2, int y2, int scale, LivingEntity who) {
        draw(g, x1, y1, x2, y2, scale, 0.0625f, who);
    }

    /** The same, lifted by {@code lift} (blocks) in its box: the appearance page puts her feet where it wants them. */
    public void draw(GuiGraphicsExtractor g, int x1, int y1, int x2, int y2, int scale, float lift, LivingEntity who) {
        drawing = true;
        yaw = turn;
        try {
            InventoryScreen.extractEntityInInventoryFollowsMouse(g, x1, y1, x2, y2, scale, lift,
                    (x1 + x2) / 2f, (y1 + y2) / 2f, who);
        } finally {
            drawing = false;
            yaw = 0;
        }
    }

    /** A press inside the figure's box starts turning it; says whether it did. */
    public boolean pressed(double mx, double my, int x1, int y1, int x2, int y2) {
        if (mx < x1 || mx >= x2 || my < y1 || my >= y2) return false;
        dragging = true;
        lastX = mx;
        return true;
    }

    public void dragged(double mx) {
        if (!dragging) return;
        turn = (turn + (float) (mx - lastX) * 1.6f) % 360f;
        lastX = mx;
    }

    public void released() {
        dragging = false;
    }

    public boolean dragging() {
        return dragging;
    }
}
