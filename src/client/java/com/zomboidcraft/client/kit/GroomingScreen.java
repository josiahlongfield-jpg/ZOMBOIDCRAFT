package com.zomboidcraft.client.kit;

import com.zomboidcraft.clothing.Grooming;
import com.zomboidcraft.clothing.Grooming.Look;
import java.util.Map;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * Hair and beard (H on the kit's Clothing page): your survivor in the middle (drag to turn them), the styles you can cut to now
 * either side (scissors cut, a razor shaves), your hair colour along the bottom, and whether you grow a beard. Styles
 * longer than your hair is now are greyed out until it grows.
 */
public class GroomingScreen extends Screen {
    private final Screen back;
    private Look shown;
    /** You in the middle, turned by dragging. */
    private final SpinFigure figure = new SpinFigure();

    public GroomingScreen(Screen back) {
        super(Component.literal("Hair and beard"));
        this.back = back;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        if (minecraft.player == null) return;
        shown = Grooming.look(minecraft.player);
        long now = minecraft.level.getGameTime();
        int col = 104, gap = 2, bh = 16;
        int lx = 16, rx = width - 16 - col, y0 = 56;
        int i = 0;
        if (zomboid(lx, rx, y0, col, gap, bh, now)) return;
        for (Map.Entry<String, Integer> e : Grooming.HAIR.entrySet()) {
            if (e.getKey().equals("own")) continue;
            String need = Grooming.needs(shown, now, true, e.getKey());
            Button b = Button.builder(Component.literal(label(e.getKey()) + (need == null ? "" : need.equals("razor") ? " (razor)" : "")),
                    x -> ClientPlayNetworking.send(new Grooming.Cut(true, e.getKey()))).bounds(lx, y0 + i * (bh + gap), col, bh).build();
            b.active = need != null;
            addRenderableWidget(b);
            i++;
        }
        i = 0;
        for (Map.Entry<String, Integer> e : Grooming.BEARD.entrySet()) {
            String need = Grooming.needs(shown, now, false, e.getKey());
            String name = e.getKey().equals("none") ? "Clean shave" : label(e.getKey());
            Button b = Button.builder(Component.literal(name + (need != null && need.equals("razor") ? " (razor)" : "")),
                    x -> ClientPlayNetworking.send(new Grooming.Cut(false, e.getKey()))).bounds(rx, y0 + i * (bh + gap), col, bh).build();
            b.active = need != null && (shown.beardGrows() || !e.getKey().equals("stubble"));
            addRenderableWidget(b);
            i++;
        }
        addRenderableWidget(Button.builder(Component.literal(shown.beardGrows() ? "Beard grows: yes" : "Beard grows: no"),
                x -> ClientPlayNetworking.send(new Grooming.Beard(!shown.beardGrows())))
                .bounds(rx, y0 + i * (bh + gap) + 6, col, bh).build());
        int cw = 54, total = Grooming.COLOURS.size() * (cw + gap) - gap, cx = (width - total) / 2, cy = height - 46;
        for (int k = 0; k < Grooming.COLOURS.size(); k++) {
            String c = Grooming.COLOURS.get(k);
            Button b = Button.builder(Component.literal(label(c)), x -> ClientPlayNetworking.send(new Grooming.Colour(c)))
                    .bounds(cx + k * (cw + gap), cy, cw, bh).build();
            b.active = !c.equals(shown.colour());
            addRenderableWidget(b);
        }
    }

    /**
     * A Zomboid survivor body cuts to Zomboid's own styles: what the style showing now can be trimmed to (its
     * trimChoices), or shaved; a beard the same. Returns false for the usual body.
     */
    private boolean zomboid(int lx, int rx, int y0, int col, int gap, int bh, long now) {
        com.zomboidcraft.survival.character.Appearance.Look look = com.zomboidcraft.survival.character.Appearance.of(minecraft.player);
        com.zomboidcraft.client.survivor.SurvivorPack pack = look == null ? null : com.zomboidcraft.client.survivor.SurvivorPack.get(look.sex());
        if (pack == null) return false;
        com.zomboidcraft.client.survivor.SurvivorPack.Style hairNow = pack.hairFor(Grooming.hair(shown, now), shown.hair());
        java.util.List<String[]> hair = new java.util.ArrayList<>();   // {label, style}
        if (hairNow != null) {
            hair.add(new String[]{hairNow.name() + " (tidy)", hairNow.key()});
            for (String t : hairNow.trim()) {
                com.zomboidcraft.client.survivor.SurvivorPack.Style s = pack.hairById.get(t);
                if (s != null) hair.add(new String[]{s.name(), s.key()});
                else if (t.equalsIgnoreCase("bald")) hair.add(new String[]{"Shave it", "bald"});
            }
        }
        if (hair.stream().noneMatch(h -> h[1].equals("bald"))) hair.add(new String[]{"Shave it", "bald"});
        int i = 0;
        for (String[] h : hair) {
            String need = Grooming.needs(shown, now, true, h[1]);
            Button b = Button.builder(Component.literal(h[0] + (need != null && need.equals("razor") ? " (razor)" : "")),
                    x -> ClientPlayNetworking.send(new Grooming.Cut(true, h[1]))).bounds(lx, y0 + i * (bh + gap), col, bh).build();
            b.active = need != null;
            addRenderableWidget(b);
            i++;
        }
        i = 0;
        if (!look.female()) {
            com.zomboidcraft.client.survivor.SurvivorPack.Style beardNow = pack.beardFor(Grooming.beard(shown, now), shown.beard());
            java.util.List<String[]> beard = new java.util.ArrayList<>();
            if (beardNow != null) {
                beard.add(new String[]{beardNow.name() + " (tidy)", beardNow.key()});
                for (String t : beardNow.trim()) {
                    com.zomboidcraft.client.survivor.SurvivorPack.Style s = pack.beardById.get(t);
                    if (s != null) beard.add(new String[]{s.name(), s.key()});
                }
            }
            beard.add(new String[]{"Clean shave", "none"});
            for (String[] h : beard) {
                String need = Grooming.needs(shown, now, false, h[1]);
                Button b = Button.builder(Component.literal(h[0] + (need != null && need.equals("razor") ? " (razor)" : "")),
                        x -> ClientPlayNetworking.send(new Grooming.Cut(false, h[1]))).bounds(rx, y0 + i * (bh + gap), col, bh).build();
                b.active = need != null;
                addRenderableWidget(b);
                i++;
            }
            addRenderableWidget(Button.builder(Component.literal(shown.beardGrows() ? "Beard grows: yes" : "Beard grows: no"),
                    x -> ClientPlayNetworking.send(new Grooming.Beard(!shown.beardGrows())))
                    .bounds(rx, y0 + i * (bh + gap) + 6, col, bh).build());
        }
        colours(gap, bh);
        return true;
    }

    private void colours(int gap, int bh) {
        int cw = 54, total = Grooming.COLOURS.size() * (cw + gap) - gap, cx = (width - total) / 2, cy = height - 46;
        for (int k = 0; k < Grooming.COLOURS.size(); k++) {
            String c = Grooming.COLOURS.get(k);
            Button b = Button.builder(Component.literal(label(c)), x -> ClientPlayNetworking.send(new Grooming.Colour(c)))
                    .bounds(cx + k * (cw + gap), cy, cw, bh).build();
            b.active = !c.equals(shown.colour());
            addRenderableWidget(b);
        }
    }

    @Override
    public void tick() {
        if (minecraft.player != null && !Grooming.look(minecraft.player).equals(shown)) rebuildWidgets();
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float partialTick) {
        extractBlurredBackground(g);
        g.fill(0, 0, width, height, 0xC8080A0C);
        if (minecraft.player == null) return;
        long now = minecraft.level.getGameTime();
        Look l = Grooming.look(minecraft.player);
        KitDraw.caps(g, font, "Hair and beard", 16, 12, KitDraw.BRIGHT, 1.3f);
        String hair = Grooming.hair(l, now), beard = Grooming.beard(l, now);
        KitDraw.text(g, font, "Hair: " + (hair.equals("own") ? "as it is" : label(hair)) + ", " + Math.round(Grooming.days(l.hairCut(), now))
                + " days since a cut.  Beard: " + (beard.equals("none") ? "none" : label(beard)), 16, 28, KitDraw.DIM, 0.9f);
        KitDraw.caps(g, font, "Cut hair to", 16, 44, KitDraw.LINK, 0.8f);
        KitDraw.capsRight(g, font, "Beard", width - 16, 44, KitDraw.LINK, 0.8f);
        KitDraw.capsCentred(g, font, "Hair colour", width / 2f, height - 60, KitDraw.LINK, 0.8f);
        KitDraw.textCentred(g, font, "Scissors cut, a razor shaves. It grows back over the weeks.", width / 2f, height - 22, KitDraw.DIM, 0.8f);
        int cx = width / 2;
        figure.draw(g, cx - 90, 40, cx + 90, height - 70, 110, minecraft.player);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) return true;
        int cx = width / 2;
        return event.button() == 0 && figure.pressed(event.x(), event.y(), cx - 90, 40, cx + 90, height - 70);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        figure.dragged(event.x());
        return figure.dragging() || super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        figure.released();
        return super.mouseReleased(event);
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(back);                                   // null from the G menu: back to the game
    }

    private static String label(String id) {
        if (id.startsWith("pz:")) id = id.substring(3, id.lastIndexOf('#') < 0 ? id.length() : id.lastIndexOf('#'));
        String s = id.replace('_', ' ');
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
