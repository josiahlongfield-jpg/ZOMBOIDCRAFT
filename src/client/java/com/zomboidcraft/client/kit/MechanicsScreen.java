package com.zomboidcraft.client.kit;

import com.zomboidcraft.world.cars.CarModels;
import com.zomboidcraft.world.cars.CarParts;
import com.zomboidcraft.world.cars.CarProp;
import com.zomboidcraft.world.cars.Mechanics;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * The mechanics screen, opened with the interact key at a car's front end: on the left a top-down outline of
 * the car with each part coloured by its condition and what's wrong with it (from Mechanics 1), on the right
 * every part grouped by where it's worked on, with its condition and the jobs it offers. A job you can't do
 * yet is greyed with the reason (the level, the tool, the hoist), the same reason the server would give.
 */
public class MechanicsScreen extends Screen {
    private static final int W = 420, LEFT = 150, ROW = 15;
    /** As tall as fits: 270, less in a small window (rows scroll). */
    private int H = 270;
    private static final String[] AREAS = {"Engine bay", "Underneath", "Wheels", "Body"};

    private final CarProp car;
    private float scroll;
    /** The rows as drawn last frame, for clicks: slot, job, button bounds. */
    private final List<int[]> buttons = new ArrayList<>();
    private String hover;

    public MechanicsScreen(CarProp car) {
        super(Component.literal("Mechanics"));
        this.car = car;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void tick() {
        if (car.isRemoved() || minecraft.player == null || car.distanceToSqr(minecraft.player) > 8 * 8) {
            onClose();
        }
    }

    private int x() {
        return (width - W) / 2;
    }

    private int y() {
        H = Math.min(270, height - 8);
        return (height - H) / 2;
    }

    private static int colour(int c) {
        if (c == -1) return 0x66E8E4DA;
        if (c < 25) return KitDraw.BAD;
        if (c < 60) return KitDraw.WARN;
        return KitDraw.GOOD;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float partialTick) {
        g.fill(0, 0, width, height, 0x99080A0C);
        int x = x(), y = y();
        g.fill(x, y, x + W, y + H, KitDraw.PANEL);
        KitDraw.frame(g, x, y, W, H, 0x2EE8E4DA);
        String parts = car.parts();
        CarModels.Model m = CarModels.get(car.model());
        int level = Mechanics.level(minecraft.player);
        KitDraw.caps(g, font, car.model().replace('_', ' '), x + 10, y + 9, KitDraw.BRIGHT, 1.1f);
        KitDraw.text(g, font, "Overall " + CarParts.overall(parts) + "%   " + CarParts.kind(m).name().toLowerCase(Locale.ROOT)
                + " parts   Mechanics " + level + (car.hoisted() ? "   on the hoist" : ""), x + 10, y + 22, KitDraw.DIM, 0.85f);
        outline(g, parts, m, x + 10, y + 38);
        // what's wrong, worst first: only someone who knows can read it off the car
        int fy = y + 160;
        KitDraw.caps(g, font, "Diagnosis", x + 10, fy, KitDraw.TEXT, 0.8f);
        fy += 12;
        if (level < 1) {
            fy = KitDraw.wrap(g, font, "You can't tell what's wrong yet (Mechanics 1).", x + 10, fy, LEFT - 20, KitDraw.DIM);
        } else {
            List<String> faults = CarParts.faults(parts);
            if (faults.isEmpty()) {
                KitDraw.text(g, font, "Nothing failing.", x + 10, fy, KitDraw.GOOD, 0.8f);
            }
            for (int i = 0; i < Math.min(7, faults.size()); i++) {
                KitDraw.text(g, font, faults.get(i), x + 10, fy + i * 10, KitDraw.BAD, 0.75f);
            }
        }
        rows(g, parts, x + LEFT, y + 10, W - LEFT - 10, H - 34, mx, my);
        if (hover != null) {
            KitDraw.text(g, font, hover, x + LEFT, y + H - 16, KitDraw.WARN, 0.8f);
        } else {
            KitDraw.text(g, font, "Click a job to start it. Moving or being hit stops it.", x + LEFT, y + H - 16, KitDraw.DIM, 0.8f);
        }
    }

    /** The car from above, nose up: wheels at the corners, the engine bay at the front, lights at the ends. */
    private void outline(GuiGraphicsExtractor g, String parts, CarModels.Model m, int x, int y) {
        int w = 60, h = 110, cx = x + (LEFT - 20) / 2;
        int left = cx - w / 2, top = y;
        KitDraw.frame(g, left, top, w, h, 0x66E8E4DA);
        // engine bay, cabin, boot
        g.fill(left + 4, top + 4, left + w - 4, top + 28, dim(colour(CarParts.get(parts, CarParts.Part.ENGINE, 0))));
        KitDraw.textCentred(g, font, "engine", cx, top + 12, KitDraw.TEXT, 0.7f);
        KitDraw.frame(g, left + 6, top + 34, w - 12, 44, 0x33E8E4DA);
        g.fill(left + 4, top + h - 22, left + w - 4, top + h - 4, dim(colour(CarParts.get(parts, CarParts.Part.BOOT_LID, 0))));
        KitDraw.textCentred(g, font, "boot", cx, top + h - 16, KitDraw.TEXT, 0.7f);
        // the four corners: the tyre's colour, a pip for the brake inside it
        int[][] at = {{left - 10, top + 14}, {left + w + 2, top + 14}, {left - 10, top + h - 34}, {left + w + 2, top + h - 34}};
        for (int i = 0; i < 4; i++) {
            g.fill(at[i][0], at[i][1], at[i][0] + 8, at[i][1] + 20, colour(CarParts.get(parts, CarParts.Part.TIRE, i)));
            g.fill(at[i][0] + 2, at[i][1] + 8, at[i][0] + 6, at[i][1] + 12, colour(CarParts.get(parts, CarParts.Part.BRAKES, i)));
        }
        for (int i = 0; i < 2; i++) {
            int lx = i == 0 ? left + 4 : left + w - 12;
            g.fill(lx, top - 2, lx + 8, top + 2, colour(CarParts.get(parts, CarParts.Part.HEADLIGHT, i)));
            g.fill(lx, top + h - 2, lx + 8, top + h + 2, colour(CarParts.get(parts, CarParts.Part.TAIL_LIGHT, i)));
        }
    }

    private static int dim(int colour) {
        return (colour & 0x00FFFFFF) | 0x55000000;
    }

    private void rows(GuiGraphicsExtractor g, String parts, int x, int y, int w, int h, int mx, int my) {
        buttons.clear();
        hover = null;
        int total = 0;
        g.enableScissor(x, y, x + w, y + h);
        int ry = y - Math.round(scroll);
        for (int area = 0; area < AREAS.length; area++) {
            KitDraw.caps(g, font, AREAS[area], x, ry + 3, KitDraw.TEXT, 0.8f);
            ry += ROW;
            total += ROW;
            for (int slot = 0; slot < CarParts.SLOTS.size(); slot++) {
                CarParts.Slot s = CarParts.SLOTS.get(slot);
                int c = CarParts.get(parts, slot);
                if (s.part().area.ordinal() != area || c == -2) {
                    continue;
                }
                KitDraw.text(g, font, s.title(), x + 4, ry + 3, KitDraw.TEXT, 0.78f);
                KitDraw.bar(g, x + 104, ry + 6, 40, 3, c < 0 ? 0 : c / 100f, Float.NaN);
                KitDraw.text(g, font, c < 0 ? "missing" : c + "%", x + 148, ry + 3, colour(c), 0.75f);
                int bx = x + w;
                Mechanics.Job[] jobs = Mechanics.jobs(s.part());
                for (int j = jobs.length - 1; j >= 0; j--) {
                    String label = Mechanics.label(s.part(), jobs[j]);
                    int bw = Math.round(KitDraw.width(font, label, 0.72f)) + 8;
                    bx -= bw + 3;
                    String why = Mechanics.why(minecraft.player, car, slot, jobs[j]);
                    boolean over = KitDraw.in(mx, my, bx, ry + 1, bw, 12) && my >= y && my < y + h;
                    g.fill(bx, ry + 1, bx + bw, ry + 13, why != null ? 0x08E8E4DA : over ? 0x40E8E4DA : 0x1AE8E4DA);
                    KitDraw.frame(g, bx, ry + 1, bw, 12, why != null ? 0x1FE8E4DA : 0x66E8E4DA);
                    KitDraw.text(g, font, label, bx + 4, ry + 4, why != null ? KitDraw.FAINT : KitDraw.TEXT, 0.72f);
                    if (over) {
                        hover = why != null ? why + "." : label + " the " + s.title().toLowerCase(Locale.ROOT) + ": "
                                + Math.round(Mechanics.ticks(minecraft.player, car, slot, jobs[j]) / 20f) + " s";
                    }
                    if (why == null) {
                        buttons.add(new int[]{slot, jobs[j].ordinal(), bx, ry + 1, bw, 12});
                    }
                }
                ry += ROW;
                total += ROW;
            }
            ry += 4;
            total += 4;
        }
        g.disableScissor();
        maxScroll = Math.max(0, total - h);
    }

    private int maxScroll;

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        scroll = Math.clamp(scroll - (float) sy * ROW * 2, 0, maxScroll);
        return true;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() != 0) return true;
        for (int[] b : buttons) {
            if (event.x() >= b[2] && event.x() < b[2] + b[4] && event.y() >= b[3] && event.y() < b[3] + b[5]) {
                ClientPlayNetworking.send(new Mechanics.Work(car.getId(), b[0], b[1]));
                com.zomboidcraft.client.sound.UiSounds.play(com.zomboidcraft.client.sound.UiSounds.CLICK);
                if (!minecraft.player.isCreative()) {
                    onClose();      // stand back and work: the bar shows over the belt
                }
                return true;
            }
        }
        return true;
    }
}
