package com.zomboidcraft.client.kit;

import com.zomboidcraft.survival.DeathCard;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * Zomboid's "This is how you died" (PZ review section 18, Josiah 2026-10-31), in the kit's look: days and hours
 * survived, zombies killed, what got you and where, whether you'll rise, and the skills you lose. It stands in front
 * of the game's own death screen, which is still there behind "Other options" (quitting to the menu and so on).
 */
public class DeathCardScreen extends Screen {
    private static final int W = 300, H = 196;
    private static DeathCard.Card pending;

    private final DeathCard.Card card;
    private final Screen behind;
    private int[] newChar, other;

    public DeathCardScreen(DeathCard.Card card, Screen behind) {
        super(Component.literal("This is how you died"));
        this.card = card;
        this.behind = behind;
    }

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(DeathCard.Card.TYPE, (payload, context) -> pending = payload);
        // the card goes up in front of the game's death screen as soon as both are here
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (pending != null && client.gui.screen() instanceof DeathScreen ds) {
                client.gui.setScreen(new DeathCardScreen(pending, ds));
                pending = null;
            } else if (pending != null && client.player != null && client.player.isAlive()) {
                pending = null;
            }
        });
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float partialTick) {
        g.fill(0, 0, width, height, 0xF0050506);
        // a dark red bloom behind the card, thinning out
        for (int k = 0; k < 8; k++) {
            int r = Math.round(Math.min(width, height) * (0.55f - k * 0.05f));
            g.fill(width / 2 - r, height / 2 - r * 3 / 4, width / 2 + r, height / 2 + r * 3 / 4, 0x0C3A0E0E);
        }
        int x = (width - W) / 2, y = (height - H) / 2;
        KitDraw.panel(g, x, y, W, H);
        KitDraw.caps(g, font, "This is how you died", x + 12, y + 10, KitDraw.DIM, 0.8f);
        KitDraw.caps(g, font, card.name(), x + 12, y + 22, KitDraw.BRIGHT, 1.8f);

        // the three numbers
        long days = card.ticks() / 24000, hours = card.ticks() % 24000 / 1000;
        int sw = (W - 24 - 12) / 3, sy = y + 44;
        stat(g, x + 12, sy, sw, days + (days == 1 ? " day" : " days"), "Survived · " + hours + (hours == 1 ? " hour" : " hours"));
        stat(g, x + 12 + sw + 6, sy, sw, String.valueOf(card.kills()), "Zombies killed");
        stat(g, x + 12 + (sw + 6) * 2, sy, sw, shortCause(), "What got you");

        // where: a little map square with a cross, then the words
        int my0 = sy + 40, ms = 64;
        g.fill(x + 12, my0, x + 12 + ms, my0 + ms, 0xFF1A1C19);
        KitDraw.frame(g, x + 12, my0, ms, ms, KitDraw.EDGE);
        for (int k = 1; k < 4; k++) {
            g.fill(x + 12 + k * ms / 4, my0 + 1, x + 13 + k * ms / 4, my0 + ms - 1, 0x1AE8E4DA);
            g.fill(x + 13, my0 + k * ms / 4, x + 11 + ms, my0 + 1 + k * ms / 4, 0x1AE8E4DA);
        }
        int cx = x + 12 + ms / 2, cy = my0 + ms / 2;
        for (int d = -5; d <= 5; d++) {
            g.fill(cx + d - 1, cy + d - 1, cx + d + 1, cy + d + 1, KitDraw.BAD);
            g.fill(cx + d - 1, cy - d - 1, cx + d + 1, cy - d + 1, KitDraw.BAD);
        }
        KitDraw.textCentred(g, font, card.x() + ", " + card.z(), cx, my0 + ms - 9, KitDraw.FAINT, 0.6f);

        int tx = x + 12 + ms + 10, tw = W - (tx - x) - 12, ty = my0;
        ty = KitDraw.wrap(g, font, "Died " + (card.place().isEmpty() ? "at " + card.x() + ", " + card.z() : "near " + card.place()) + ".",
                tx, ty, tw, KitDraw.TEXT) + 3;
        ty = KitDraw.wrap(g, font, card.rises() ? "You were infected, so your body will rise wearing what you had on."
                : "You weren't infected: your body stays down, with everything on it.", tx, ty, tw,
                card.rises() ? KitDraw.WARN : KitDraw.DIM) + 3;
        String lost = card.skills().isEmpty() ? "No skills to lose." : "Lost: " + String.join(", ", card.skills().subList(0, Math.min(6, card.skills().size())))
                + (card.skills().size() > 6 ? " and " + (card.skills().size() - 6) + " more." : ".");
        KitDraw.wrap(g, font, lost, tx, ty, tw, KitDraw.DIM);

        // the way on
        int by = y + H - 22;
        KitDraw.caps(g, font, "The world stays as you left it", x + 12, by + 4, KitDraw.FAINT, 0.65f);
        int nw = Math.round(KitDraw.capsWidth(font, "New character", 0.8f)) + 14;
        int ow = Math.round(KitDraw.capsWidth(font, "Other options", 0.8f)) + 14;
        newChar = new int[]{x + W - 12 - nw, by, nw, 14};
        other = new int[]{newChar[0] - 6 - ow, by, ow, 14};
        button(g, "Other options", other, false, mx, my);
        button(g, "New character", newChar, true, mx, my);
    }

    private void stat(GuiGraphicsExtractor g, int x, int y, int w, String big, String small) {
        KitDraw.frame(g, x, y, w, 32, KitDraw.EDGE);
        g.enableScissor(x + 1, y + 1, x + w - 1, y + 31);
        KitDraw.caps(g, font, big, x + 6, y + 6, KitDraw.BRIGHT, 1.3f);
        KitDraw.caps(g, font, small, x + 6, y + 21, KitDraw.DIM, 0.6f);
        g.disableScissor();
    }

    private void button(GuiGraphicsExtractor g, String label, int[] b, boolean primary, int mx, int my) {
        boolean over = KitDraw.in(mx, my, b[0], b[1], b[2], b[3]);
        g.fill(b[0], b[1], b[0] + b[2], b[1] + b[3], primary ? (over ? 0xFFF2EFE8 : 0xEBE8E4DA) : over ? 0x26E8E4DA : 0x08E8E4DA);
        KitDraw.frame(g, b[0], b[1], b[2], b[3], primary ? 0xFFE8E4DA : 0x4DE8E4DA);
        KitDraw.caps(g, font, label, b[0] + 7, b[1] + 4, primary ? 0xFF121314 : KitDraw.TEXT, 0.8f);
    }

    /** "Josiah was slain by Zombie" as "Slain by Zombie". */
    private String shortCause() {
        String c = card.cause();
        if (c.startsWith(card.name() + " ")) c = c.substring(card.name().length() + 1);
        if (c.startsWith("was ")) c = c.substring(4);
        if (c.isEmpty()) return "Unknown";
        return Character.toUpperCase(c.charAt(0)) + c.substring(1);
    }

    /** Starts the next character (the UI test calls it too). */
    public void newCharacter() {
        if (minecraft.player != null) minecraft.player.respawn();
        minecraft.gui.setScreen(null);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (newChar != null && KitDraw.in(event.x(), event.y(), newChar[0], newChar[1], newChar[2], newChar[3])) {
            newCharacter();
        } else if (other != null && KitDraw.in(event.x(), event.y(), other[0], other[1], other[2], other[3])) {
            minecraft.gui.setScreen(behind);
        }
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        return true;
    }
}
