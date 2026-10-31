package com.zomboidcraft.client.hud;

import com.zomboidcraft.survival.TimedActions;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import org.jetbrains.annotations.Nullable;

/**
 * The timed-action bar: what you're doing and how long it has left, a thin bar above the belt that fills, and a
 * reminder that moving stops it; beside it, Zomboid's queue of what runs next. The server says when an action starts; the bar runs on this side's clock.
 */
public final class ActionHud {
    /** What's running and how far along it is, 0-1. */
    public record Progress(String label, float done, float secondsLeft) {
    }

    private static final Hud.Fade fade = new Hud.Fade();
    private static int id;
    private static long started;
    private static Progress shown;

    /** The action running now, or null. */
    public static @Nullable Progress progress() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return null;
        TimedActions.View view = player.getAttached(TimedActions.TYPE);
        if (view == null || !view.running()) {
            id = 0;
            return null;
        }
        if (view.id() != id) {
            id = view.id();
            started = System.nanoTime();
        }
        float seconds = (System.nanoTime() - started) / 1e9f, total = view.ticks() / 20f;
        return new Progress(view.label(), Math.min(1, seconds / total), Math.max(0, total - seconds));
    }

    static void render(GuiGraphicsExtractor g) {
        Progress now = progress();
        if (now != null) shown = now;
        float a = fade.update(now != null);
        if (a <= 0 || shown == null) return;
        Font font = Minecraft.getInstance().font;
        int w = 150, x = (g.guiWidth() - w) / 2, y = g.guiHeight() - 72;
        Hud.caps(g, font, shown.label(), x, y, Hud.alpha(Hud.BONE, a), 1.2f, 0.9f);
        String left = now == null ? "" : Math.max(1, (int) Math.ceil(shown.secondsLeft())) + " s";
        float lw = Hud.capsWidth(font, left, 1f, 0.8f);
        Hud.caps(g, font, left, x + w - lw, y + 1, Hud.alpha(Hud.BONE_DIM, a), 1f, 0.8f);
        g.fill(x, y + 11, x + w, y + 13, Hud.alpha(0x2EE9E1CB, a));
        g.fill(x, y + 11, x + Math.round(w * shown.done()), y + 13, Hud.alpha(Hud.BONE, a));
        String hint = "Move, swing or get hit to stop";
        float hw = Hud.capsWidth(font, hint, 0.8f, 0.65f);
        Hud.caps(g, font, hint, x + (w - hw) / 2, y + 17, Hud.alpha(0x80E9E1CB, a), 0.8f, 0.65f);
        // Zomboid's queue: what's lined up after this one, beside the bar
        LocalPlayer player = Minecraft.getInstance().player;
        java.util.List<String> queued = player == null ? java.util.List.of() : player.getAttachedOrElse(TimedActions.QUEUED, java.util.List.of());
        if (now != null && !queued.isEmpty()) {
            int qx = x + w + 12, qy = y;
            Hud.caps(g, font, "Then", qx, qy, Hud.alpha(Hud.BONE_DIM, a), 1f, 0.65f);
            for (int i = 0; i < Math.min(4, queued.size()); i++) {
                Hud.caps(g, font, (i + 1) + "  " + queued.get(i), qx, qy + 8 + i * 8, Hud.alpha(i == 0 ? Hud.BONE : Hud.BONE_DIM, a), 0.8f, 0.7f);
            }
            if (queued.size() > 4) {
                Hud.caps(g, font, "+" + (queued.size() - 4) + " more", qx, qy + 40, Hud.alpha(Hud.BONE_FAINT, a), 0.8f, 0.65f);
            }
        }
    }

    private ActionHud() {
    }
}
