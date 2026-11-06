package com.zomboidcraft.client.kit;

import com.mojang.blaze3d.platform.InputConstants;
import com.zomboidcraft.client.survivor.SurvivorPack;
import com.zomboidcraft.client.survivor.Survivors;
import com.zomboidcraft.clothing.Grooming;
import com.zomboidcraft.survival.character.Appearance;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * Zomboid's appearance page, after the traits: your survivor in the middle, turning to the mouse (or close up on the
 * face), and down the left everything the body mods offer. Woman or man; the skin (Tomb's Player Body, Tomb's Goth,
 * Detailed Player Texture or Evil's Ink for women, Tomb's or Goth for men), its tone and build (normal, toned or strong),
 * a hairy body for men; the face; Zomboid's own hair styles and Tomb's Goth ones, or a shaved head or stubble; the hair
 * colour; beards and stubble; body hair; eye make-up, lipstick and face paint. Every woman wears Tomb's black bra and
 * briefs, every man Tomb's grey briefs, under whatever she or he puts on. Click a row's arrows (or scroll over it) to
 * change it; Random rolls a whole new look.
 * <p>
 * It only comes up on a PC that has the survivor packs (config/zomboidcraft/npc). A survivor made before the page
 * existed is offered it once on joining.
 */
public final class AppearanceScreen extends Screen {
    private static boolean pending, offered;
    private static int ticksIn;

    private String sex = "f", pack = "tomb", build = "normal", face = "", hair = "", colour = "dark_brown", beard = "none";
    private int tone = 1;
    private boolean hairy, close;
    private String bodyHair = "", eyes = "", lips = "", paint = "";
    private final List<Row> rows = new ArrayList<>();
    private int[] done, random, zoom, figure;
    /** She stands still facing out and turns as you drag her (Josiah 2026-11-06), whole body and close up. */
    private final SpinFigure spin = new SpinFigure();
    private boolean ready;

    /** One option: its name, the choices (ids and names) and which is picked. */
    private record Row(String label, List<String> ids, List<String> names, int at, java.util.function.Consumer<String> set, int[] box) {
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            if (mc.player == null || mc.level == null) {
                ticksIn = 0;
                return;
            }
            ticksIn++;
            if (!SurvivorPack.installed()) return;
            if (pending && (mc.gui.screen() == null || mc.gui.screen() instanceof SpawnScreen)) {
                mc.gui.setScreen(new AppearanceScreen());
            } else if (!offered && ticksIn > 60 && mc.gui.screen() == null && !CharacterScreen.pending()
                    && Appearance.of(mc.player) == null) {
                // made before this page existed: offered once
                offered = pending = true;
                mc.gui.setScreen(new AppearanceScreen());
            }
            if (Survivors.preview != null && !(mc.gui.screen() instanceof AppearanceScreen) && (Appearance.of(mc.player) != null || !pending)) {
                Survivors.preview = null;
            }
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            pending = offered = false;
            Survivors.preview = null;
        });
    }

    /** Whether the page is still to be done: the spawn picker waits for it. */
    public static boolean pending() {
        return pending;
    }

    /** After the traits: this page if the packs are here, else straight on to where you wake. */
    static void afterTraits(Minecraft mc) {
        if (SurvivorPack.installed()) {
            pending = offered = true;
            mc.gui.setScreen(new AppearanceScreen());
        } else {
            mc.gui.setScreen(null);
        }
    }

    public AppearanceScreen() {
        super(Component.literal("What do you look like?"));
        if (!SurvivorPack.installed("f") && SurvivorPack.installed("m")) sex = "m";
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    private SurvivorPack pack() {
        return SurvivorPack.get(sex);
    }

    @Override
    public void tick() {
        if (!ready && pack() != null) {
            ready = true;
            fit(true);
        }
    }

    // ------------------------------------------------------------------ the choices

    /** Every choice kept to what the pack has (a man has no Ink skin, a Goth skin has one tone...), then tried on. */
    private void fit(boolean fresh) {
        SurvivorPack p = pack();
        if (p == null || p.packs.isEmpty()) return;
        SurvivorPack.Pack sp = p.pack(pack);
        pack = sp.id();
        if (!sp.tones().containsKey(tone)) tone = sp.tones().keySet().iterator().next();
        if (!sp.tones().get(tone).contains(build)) build = sp.tones().get(tone).get(0);
        if (!"m".equals(sex)) hairy = false;
        SurvivorPack.Faces fs = p.faces.get(sp.faces());
        List<String> faceNames = fs == null ? List.of() : fs.names();
        if (!faceNames.contains(face)) face = faceNames.isEmpty() ? "" : faceNames.get(0);
        if (fresh || !hairIds(p).contains(hair)) {
            SurvivorPack.Style s = p.grown(true, 2, false);
            hair = s == null ? "bald" : s.id();
        }
        if (!beardIds(p).contains(beard)) beard = "none";
        if (!ids(p.bodyHair).contains(bodyHair)) bodyHair = "";
        if (!ids(p.eyes).contains(eyes)) eyes = "";
        if (!ids(p.lips).contains(lips)) lips = "";
        if (!ids(p.paint).contains(paint)) paint = "";
        Survivors.preview = preview(p);
    }

    private static List<String> ids(List<SurvivorPack.Option> options) {
        List<String> out = new ArrayList<>();
        out.add("");
        for (SurvivorPack.Option o : options) out.add(o.id());
        return out;
    }

    private List<String> hairIds(SurvivorPack p) {
        List<String> out = new ArrayList<>(List.of("bald"));
        for (SurvivorPack.Option o : p.stubble) if (o.id().contains("Hair")) out.add("stubble:" + o.id());
        for (SurvivorPack.Style s : p.hair) if (s.choose()) out.add(s.id());
        return out;
    }

    private List<String> hairNames(SurvivorPack p) {
        List<String> out = new ArrayList<>(List.of("Shaved"));
        for (SurvivorPack.Option o : p.stubble) if (o.id().contains("Hair")) out.add(o.name());
        for (SurvivorPack.Style s : p.hair) if (s.choose()) out.add(s.name() + (s.goth() ? " (Goth)" : ""));
        return out;
    }

    private List<String> beardIds(SurvivorPack p) {
        List<String> out = new ArrayList<>(List.of("none"));
        if ("m".equals(sex)) {
            for (SurvivorPack.Option o : p.stubble) if (o.id().contains("Beard")) out.add("stubble:" + o.id());
            for (SurvivorPack.Style s : p.beards) out.add(s.id());
        }
        return out;
    }

    private List<String> beardNames(SurvivorPack p) {
        List<String> out = new ArrayList<>(List.of("Clean shaven"));
        if ("m".equals(sex)) {
            for (SurvivorPack.Option o : p.stubble) if (o.id().contains("Beard")) out.add(o.name());
            for (SurvivorPack.Style s : p.beards) out.add(s.name());
        }
        return out;
    }

    /** What's picked, as the game keeps it: the look, and the hair and beard in Grooming's terms. */
    private Survivors.Preview preview(SurvivorPack p) {
        String headStubble = hair.startsWith("stubble:") ? hair.substring(8) : "";
        String beardStubble = beard.startsWith("stubble:") ? beard.substring(8) : "";
        String h = hair.equals("bald") ? "bald" : hair.startsWith("stubble:") ? "buzz"
                : p.hairById.containsKey(hair) ? p.hairById.get(hair).key() : "bald";
        String b = beard.equals("none") ? "none" : beard.startsWith("stubble:") ? "stubble"
                : p.beardById.containsKey(beard) ? p.beardById.get(beard).key() : "none";
        Appearance.Look look = new Appearance.Look(sex, pack, tone, build, hairy, face, bodyHair, eyes, lips, paint, beardStubble, headStubble);
        return new Survivors.Preview(look, h, b, colour);
    }

    private void buildRows(SurvivorPack p) {
        rows.clear();
        List<String> sexes = new ArrayList<>(), sexNames = new ArrayList<>();
        if (SurvivorPack.installed("f")) {
            sexes.add("f");
            sexNames.add("Woman");
        }
        if (SurvivorPack.installed("m")) {
            sexes.add("m");
            sexNames.add("Man");
        }
        row("Body", sexes, sexNames, sex, v -> {
            sex = v;
            ready = pack() != null;
            if (ready) fit(true);
        });
        List<String> packs = new ArrayList<>(), packNames = new ArrayList<>();
        for (SurvivorPack.Pack sp : p.packs) {
            packs.add(sp.id());
            packNames.add(sp.name());
        }
        row("Skin", packs, packNames, pack, v -> pack = v);
        SurvivorPack.Pack sp = p.pack(pack);
        List<String> tones = new ArrayList<>(), toneNames = new ArrayList<>();
        for (int t : sp.tones().keySet()) {
            tones.add(String.valueOf(t));
            toneNames.add(sp.tones().size() == 1 ? "Its own" : "Tone " + t);
        }
        row("Skin tone", tones, toneNames, String.valueOf(tone), v -> tone = Integer.parseInt(v));
        List<String> builds = sp.tones().get(tone), buildNames = new ArrayList<>();
        for (String b : builds) buildNames.add(Character.toUpperCase(b.charAt(0)) + b.substring(1));
        row("Build", builds, buildNames, build, v -> build = v);
        if ("m".equals(sex) && p.skin.raw("skin/" + pack + "/" + tone + "/normal/hairy") != null) {
            row("Hairy body", List.of("no", "yes"), List.of("No", "Yes"), hairy ? "yes" : "no", v -> hairy = v.equals("yes"));
        }
        SurvivorPack.Faces fs = p.faces.get(sp.faces());
        if (fs != null && fs.names().size() > 1) row("Face", fs.names(), fs.names(), face, v -> face = v);
        row("Hair", hairIds(p), hairNames(p), hair, v -> hair = v);
        List<String> colourNames = new ArrayList<>();
        for (String c : Grooming.COLOURS) colourNames.add(label(c));
        row("Hair colour", Grooming.COLOURS, colourNames, colour, v -> colour = v);
        if ("m".equals(sex)) row("Beard", beardIds(p), beardNames(p), beard, v -> beard = v);
        option("Body hair", p.bodyHair, bodyHair, v -> bodyHair = v);
        option("Eye make-up", p.eyes, eyes, v -> eyes = v);
        option("Lipstick", p.lips, lips, v -> lips = v);
        option("Face paint", p.paint, paint, v -> paint = v);
    }

    private void option(String label, List<SurvivorPack.Option> options, String now, java.util.function.Consumer<String> set) {
        if (options.isEmpty()) return;
        List<String> names = new ArrayList<>(List.of("None"));
        for (SurvivorPack.Option o : options) names.add(o.name());
        row(label, ids(options), names, now, set);
    }

    private void row(String label, List<String> ids, List<String> names, String now, java.util.function.Consumer<String> set) {
        if (ids.isEmpty()) return;
        rows.add(new Row(label, ids, names, Math.max(0, ids.indexOf(now)), set, new int[4]));
    }

    private void step(Row r, int by) {
        int n = r.ids().size();
        r.set().accept(r.ids().get(((r.at() + by) % n + n) % n));
        fit(false);
    }

    /** Zomboid's random survivor look: any skin and tone, any face and hair, a beard or not, a little make-up sometimes. */
    private void randomise() {
        Random rnd = new Random();
        SurvivorPack p = pack();
        if (p == null) return;
        SurvivorPack.Pack sp = p.packs.get(rnd.nextInt(p.packs.size()));
        pack = sp.id();
        List<Integer> tones = new ArrayList<>(sp.tones().keySet());
        tone = tones.get(rnd.nextInt(tones.size()));
        List<String> builds = sp.tones().get(tone);
        build = builds.get(rnd.nextInt(builds.size()));
        hairy = "m".equals(sex) && rnd.nextInt(3) == 0;
        SurvivorPack.Faces fs = p.faces.get(sp.faces());
        if (fs != null && !fs.names().isEmpty()) face = fs.names().get(rnd.nextInt(fs.names().size()));
        List<String> hs = hairIds(p);
        hair = hs.get(rnd.nextInt(hs.size()));
        colour = Grooming.COLOURS.get(rnd.nextInt(Grooming.COLOURS.size()));
        List<String> bs = beardIds(p);
        beard = rnd.nextBoolean() ? "none" : bs.get(rnd.nextInt(bs.size()));
        bodyHair = rnd.nextInt(4) == 0 ? pickOf(rnd, p.bodyHair) : "";
        boolean madeUp = !"m".equals(sex) && rnd.nextInt(3) == 0;
        eyes = madeUp ? pickOf(rnd, p.eyes) : "";
        lips = madeUp ? pickOf(rnd, p.lips) : "";
        paint = "";
        fit(false);
    }

    private static String pickOf(Random rnd, List<SurvivorPack.Option> options) {
        return options.isEmpty() ? "" : options.get(rnd.nextInt(options.size())).id();
    }

    private void finish() {
        SurvivorPack p = pack();
        if (p == null) return;
        Survivors.Preview look = preview(p);
        Survivors.preview = look;   // kept on until the server's copy comes back
        ClientPlayNetworking.send(new Appearance.Set(look.look(), look.hair(), look.beard(), look.colour()));
        pending = false;
        minecraft.gui.setScreen(null);   // the spawn picker comes up next, if this world has one
    }

    // ------------------------------------------------------------------ drawing

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float partialTick) {
        g.fill(0, 0, width, height, 0xFF0B0C0E);
        int pad = 14, top = 42, bottom = height - 22;
        KitDraw.caps(g, font, "Grayhaven County · new survivor", pad, 10, KitDraw.DIM, 0.8f);
        KitDraw.caps(g, font, "What do you look like?", pad, 20, KitDraw.BRIGHT, 1.6f);
        SurvivorPack p = pack();
        if (p == null || !ready) {
            KitDraw.textCentred(g, font, "Getting the bodies ready...", width / 2f, height / 2f, KitDraw.DIM, 1f);
            return;
        }
        buildRows(p);
        int lw = Math.max(150, (width - pad * 2) * 38 / 100);
        KitDraw.heading(g, font, "Appearance", pad, top, lw, false);
        int y = top + 14, rh = Math.max(11, Math.min(16, (bottom - y - 4) / Math.max(1, rows.size())));
        for (Row r : rows) {
            boolean hover = KitDraw.in(mx, my, pad, y, lw, rh - 1);
            if (hover) g.fill(pad, y, pad + lw, y + rh - 1, 0x0DE8E4DA);
            KitDraw.caps(g, font, r.label(), pad + 4, y + (rh - 7) / 2f, KitDraw.DIM, 0.7f);
            int vx = pad + lw * 42 / 100, vw = lw - (vx - pad) - 4;
            KitDraw.frame(g, vx, y, vw, rh - 1, hover ? 0xD9F2EFE8 : 0x4DE8E4DA);
            KitDraw.text(g, font, "<", vx + 4, y + (rh - 9) / 2f + 1, KitDraw.LINK, 0.8f);
            KitDraw.textRight(g, font, ">", vx + vw - 4, y + (rh - 9) / 2f + 1, KitDraw.LINK, 0.8f);
            String name = r.names().get(r.at());
            float s = Math.min(0.8f, (vw - 24) / Math.max(1f, KitDraw.width(font, name, 1f)));
            KitDraw.textCentred(g, font, name, vx + vw / 2f, y + (rh - 9 * s) / 2f, KitDraw.TEXT, s);
            r.box()[0] = vx;
            r.box()[1] = y;
            r.box()[2] = vw;
            r.box()[3] = rh - 1;
            y += rh;
        }

        // your survivor, head to foot or close on the face
        int fx = pad + lw + 12, fw = width - pad - fx, panelTop = top, panelBottom = bottom;
        int side = Math.min(150, fw * 34 / 100), figW = fw - side - 8;
        KitDraw.panel(g, fx, panelTop, figW, panelBottom - panelTop);
        if (minecraft.player != null) {
            float tall = p.height;
            float scale, feet;
            if (close) {
                scale = (panelBottom - panelTop) / 0.55f;
                feet = panelTop + (panelBottom - panelTop) * 0.5f + (tall - 0.12f) * scale;
            } else {
                scale = (panelBottom - panelTop - 16) / tall;
                feet = panelBottom - 8;
            }
            // drawn into a picture the panel's size (a bigger one runs the graphics card out of memory): the panel's
            // middle is the point bbHeight/2 + lift over the feet, so the lift puts her feet where they go
            int x0 = fx + 1, y0 = panelTop + 1, x1 = fx + figW - 1, y1 = panelBottom - 1;
            float middle = (y0 + y1) / 2f;
            float lift = (feet - middle) / scale - minecraft.player.getBbHeight() / 2;
            g.enableScissor(x0, y0, x1, y1);
            figure = new int[]{x0, y0, x1, y1};
            spin.draw(g, x0, y0, x1, y1, Math.round(scale), lift, minecraft.player);
            g.disableScissor();
        }

        // done, random, close-up
        int sx = fx + figW + 8;
        KitDraw.panel(g, sx, panelTop, side, panelBottom - panelTop);
        int in = 8, tw = side - in * 2, ty = panelTop + in;
        KitDraw.caps(g, font, "Your survivor", sx + in, ty, KitDraw.DIM, 0.7f);
        ty += 10;
        SurvivorPack.Pack sp = p.pack(pack);
        KitDraw.text(g, font, ("m".equals(sex) ? "Man" : "Woman") + ", " + (sp == null ? "" : sp.name()), sx + in, ty, KitDraw.TEXT, 0.75f);
        ty += 10;
        KitDraw.wrap(g, font, "m".equals(sex) ? "Tomb's grey briefs are always on under your clothes."
                : "Tomb's black bra and briefs are always on under your clothes.", sx + in, ty, tw, KitDraw.FAINT);
        int by = panelBottom - in - 48;
        zoom = new int[]{sx + in, by, tw, 12};
        KitDraw.frame(g, zoom[0], zoom[1], zoom[2], zoom[3], KitDraw.in(mx, my, zoom[0], zoom[1], zoom[2], zoom[3]) ? KitDraw.BRIGHT : 0x73E8E4DA);
        KitDraw.capsCentred(g, font, close ? "Whole body" : "Close-up", zoom[0] + tw / 2f, zoom[1] + 3, KitDraw.TEXT, 0.75f);
        random = new int[]{sx + in, by + 16, tw, 12};
        KitDraw.frame(g, random[0], random[1], random[2], random[3], KitDraw.in(mx, my, random[0], random[1], random[2], random[3]) ? KitDraw.BRIGHT : 0x73E8E4DA);
        KitDraw.capsCentred(g, font, "Random", random[0] + tw / 2f, random[1] + 3, KitDraw.TEXT, 0.75f);
        done = new int[]{sx + in, by + 32, tw, 16};
        boolean hd = KitDraw.in(mx, my, done[0], done[1], done[2], done[3]);
        g.fill(done[0], done[1], done[0] + tw, done[1] + 16, hd ? 0xFFF7F4EE : 0xFFE8E4DA);
        g.fill(done[0], done[1], done[0] + tw, done[1] + 1, hd ? 0xFFFFFFFF : 0xFFF4F1EA);
        g.fill(done[0], done[1] + 15, done[0] + tw, done[1] + 16, 0xFFB9B4A8);
        String label = "Next: where you wake";
        float ls = Math.min(0.85f, (tw - 8) / Math.max(1f, KitDraw.capsWidth(font, label, 1f)));
        float lw2 = KitDraw.capsWidth(font, label, ls);
        com.zomboidcraft.client.hud.Hud.caps(g, font, label, done[0] + tw / 2f - lw2 / 2, done[1] + 4.5f, 0xFF1D1B16, ls, ls, false);

        int hx = pad;
        hx += KitDraw.hint(g, font, "LMB", "Change", hx, height - 15);
        hx += KitDraw.hint(g, font, "Scroll", "Change", hx, height - 15);
        hx += KitDraw.hint(g, font, "Drag", "Turn", hx, height - 15);
        hx += KitDraw.hint(g, font, "Enter", "Done", hx, height - 15);
        KitDraw.hint(g, font, "Esc", "Game menu", hx, height - 15);
    }

    private static String label(String id) {
        String s = id.replace('_', ' ');
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mx = event.x(), my = event.y();
        if (!ready) return true;
        for (Row r : List.copyOf(rows)) {
            int[] b = r.box();
            if (KitDraw.in(mx, my, b[0], b[1], b[2], b[3])) {
                step(r, mx < b[0] + b[2] / 2.0 ? -1 : 1);
                return true;
            }
        }
        if (done != null && KitDraw.in(mx, my, done[0], done[1], done[2], done[3])) {
            finish();
        } else if (random != null && KitDraw.in(mx, my, random[0], random[1], random[2], random[3])) {
            randomise();
        } else if (zoom != null && KitDraw.in(mx, my, zoom[0], zoom[1], zoom[2], zoom[3])) {
            close = !close;
        } else if (figure != null && event.button() == 0) {
            // take hold of her to turn her round
            spin.pressed(mx, my, figure[0], figure[1], figure[2], figure[3]);
        }
        return true;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        spin.dragged(event.x());
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        spin.released();
        return true;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (!ready || sy == 0) return true;
        for (Row r : List.copyOf(rows)) {
            int[] b = r.box();
            if (my >= b[1] && my < b[1] + b[3] && mx < b[0] + b[2]) {
                step(r, sy > 0 ? -1 : 1);
                return true;
            }
        }
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == InputConstants.KEY_ESCAPE) {
            minecraft.gui.setScreen(new net.minecraft.client.gui.screens.PauseScreen(true));
            return true;
        }
        if (event.key() == InputConstants.KEY_RETURN || event.key() == InputConstants.KEY_NUMPADENTER) {
            if (ready) finish();
            return true;
        }
        return true;
    }

    // ------------------------------------------------------------------ for the UI test

    /** Picks an option by its row label and choice id (as the rows show them). */
    public boolean choose(String label, String id) {
        SurvivorPack p = pack();
        if (p == null) return false;
        buildRows(p);
        for (Row r : rows) {
            if (r.label().equalsIgnoreCase(label) && r.ids().contains(id)) {
                r.set().accept(id);
                fit(false);
                return true;
            }
        }
        return false;
    }
}
