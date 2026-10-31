package com.zomboidcraft.client.kit;

import com.zomboidcraft.client.cars.CarClient;
import com.zomboidcraft.npc.NpcBond;
import com.zomboidcraft.npc.NpcEntity;
import com.zomboidcraft.npc.NpcMind;
import com.zomboidcraft.npc.NpcPanel;
import com.zomboidcraft.npc.NpcSkills;
import com.zomboidcraft.npc.NpcWardrobe;
import com.zomboidcraft.npc.NpcWork;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * A companion's own page, the kit's look (Josiah 2026-10-30): her name across the top with her pages, her standing
 * idle in the middle (drag to turn her), everything you can ask of her down the left and how she is down the right.
 * The second page is her gear: what she wears (every piece of her model, on or off, and clothes you've found to offer
 * her), her weapon and bag, and your things to hand over. The third is her diary. Interact (G) or Esc closes it.
 * What it shows of her comes from the server while it's open (NpcPanel.State).
 */
public class CompanionScreen extends Screen {
    private static final int W = KitScreen.W, H = KitScreen.H;

    public enum Tab {
        HER, GEAR, DIARY
    }

    private static @Nullable NpcPanel.State latest;

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(NpcPanel.State.TYPE, (payload, context) -> latest = payload);
    }

    private final NpcEntity npc;
    private Tab tab;
    private final List<Hot> hots = new ArrayList<>();
    private int left, top;
    private @Nullable String tip;
    /** Her portrait: how far you've turned her (degrees), how close you've zoomed, whether you're dragging her round. */
    private float spin, zoom = 1;
    private boolean turning;
    /** A pose you asked for from her page, and until when she holds it there (ms). */
    private int pose;
    private long poseUntil;

    private record Hot(int x, int y, int w, int h, Runnable left, @Nullable Runnable right, @Nullable String tip) {
        boolean has(double mx, double my) {
            return mx >= x && my >= y && mx < x + w && my < y + h;
        }
    }

    public CompanionScreen(NpcEntity npc, Tab tab) {
        super(Component.literal(npc.getName().getString()));
        this.npc = npc;
        this.tab = tab;
        if (latest != null && latest.npc() != npc.getId()) latest = null;
    }

    @Override
    protected void init() {
        super.init();
        var window = Minecraft.getInstance().getWindow();
        int scale = window.getGuiScale();
        while (scale > 1 && (window.getWidth() / scale < W + 8 || window.getHeight() / scale < H + 8)) scale--;
        if (scale != window.getGuiScale()) {
            window.setGuiScale(scale);
            width = window.getGuiScaledWidth();
            height = window.getGuiScaledHeight();
        }
        left = (width - W) / 2;
        top = (height - H) / 2;
        ClientPlayNetworking.send(new NpcPanel.Watch(npc.getId(), true));
    }

    @Override
    public void removed() {
        ClientPlayNetworking.send(new NpcPanel.Watch(npc.getId(), false));
        var window = Minecraft.getInstance().getWindow();
        var options = Minecraft.getInstance().options;
        int wanted = window.calculateScale(options.guiScale().get(), options.forceUnicodeFont().get());
        if (window.getGuiScale() != wanted) window.setGuiScale(wanted);
        super.removed();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void tick() {
        // bonded, her page stays open wherever she is (Bonds)
        if (!npc.isAlive() || minecraft.player == null || !Bonds.bonded(npc) && npc.distanceToSqr(minecraft.player) > 8 * 8) onClose();
    }

    private void hot(int x, int y, int w, int h, Runnable left, @Nullable Runnable right, @Nullable String tip) {
        hots.add(new Hot(x, y, w, h, left, right, tip));
    }

    /** Back to the selection of everyone you're bonded with, on her. */
    private void companions() {
        List<NpcEntity> all = Bonds.companions(minecraft);
        int at = 0;
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i).getUUID().equals(npc.getUUID())) at = i;
        }
        minecraft.gui.setScreen(new BondsScreen(at));
    }

    private void order(int code) {
        if (code >= NpcPanel.POSE && code < NpcPanel.POSE + NpcPanel.POSES.length) {
            // her portrait strikes it too, for as long as she holds it
            pose = NpcPanel.POSES[code - NpcPanel.POSE];
            poseUntil = net.minecraft.util.Util.getMillis() + 20_000;
        }
        ClientPlayNetworking.send(new NpcPanel.Order(npc.getId(), code));
        com.zomboidcraft.client.sound.UiSounds.play(com.zomboidcraft.client.sound.UiSounds.CLICK);
    }

    private void gear(int op, int slot) {
        ClientPlayNetworking.send(new NpcPanel.Gear(npc.getId(), op, slot));
        com.zomboidcraft.client.sound.UiSounds.play(com.zomboidcraft.client.sound.UiSounds.CLICK);
    }

    // ------------------------------------------------------------------ drawing

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float partialTick) {
        extractBlurredBackground(g);
        g.fill(0, 0, width, height, 0xB8080A0C);
        g.fillGradient(0, 0, width, 60, 0xE6050607, 0x00050607);
        g.fillGradient(0, height - 50, width, height, 0x00050607, 0xD9050607);
        hots.clear();
        tip = null;
        int x = left, y = top;
        topBar(g, x, y, mx, my);
        NpcPanel.State s = latest != null && latest.npc() == npc.getId() ? latest : null;
        switch (tab) {
            case HER -> her(g, s, x, y, mx, my);
            case GEAR -> gearPage(g, s, x, y, mx, my);
            case DIARY -> diary(g, s, x, y, mx, my);
        }
        for (Hot h : hots) {
            if (h.has(mx, my) && h.tip() != null) tip = h.tip();
        }
        if (tip != null) {
            int w = Math.round(KitDraw.width(font, tip, 1f)) + 10;
            int tx = Math.min(mx + 10, width - w - 4), ty = my - 16;
            KitDraw.panel(g, tx, ty, w, 14);
            KitDraw.text(g, font, tip, tx + 5, ty + 3, KitDraw.TEXT, 1f);
        }
    }

    private void topBar(GuiGraphicsExtractor g, int x, int y, int mx, int my) {
        KitDraw.frame(g, x + 12, y + 7, 18, 18, 0x80E8E4DA);
        g.fill(x + 13, y + 8, x + 29, y + 24, 0x99141614);
        KitDraw.capsCentred(g, font, "♥", x + 21, y + 11, KitDraw.LINK, 1f);
        KitDraw.caps(g, font, npc.getName().getString(), x + 36, y + 7, KitDraw.BRIGHT, 1.3f);
        NpcPanel.State s = latest;
        KitDraw.text(g, font, "Companion" + (s != null ? " · " + s.feelings() : ""), x + 36, y + 19, KitDraw.DIM, 0.9f);

        String[] labels = {npc.getName().getString(), "Gear & trade", "Diary"};
        float total = 2 * 13;
        for (String l : labels) total += KitDraw.capsWidth(font, l, 1f) + 14;
        float px = x + W / 2f - total / 2f + 30;
        KitDraw.key(g, font, "Q", Math.round(px), y + 9, KitDraw.FAINT);
        hot(Math.round(px), y + 9, 10, 10, () -> flip(-1), null, null);
        px += 20;
        for (Tab t : Tab.values()) {
            String l = labels[t.ordinal()];
            float w = KitDraw.capsWidth(font, l, 1f);
            boolean on = t == tab, over = KitDraw.in(mx, my, Math.round(px) - 2, y + 7, Math.round(w) + 4, 14);
            KitDraw.caps(g, font, l, px, y + 10, on || over ? 0xFFFFFFFF : 0x9EE8E4DA, 1f);
            if (on) g.fill(Math.round(px), y + 20, Math.round(px + w), y + 21, KitDraw.TEXT);
            hot(Math.round(px) - 2, y + 7, Math.round(w) + 4, 14, () -> tab = t, null, null);
            px += w + 14;
        }
        px += 7 - 14 + 6;
        KitDraw.key(g, font, "R", Math.round(px), y + 9, KitDraw.DIM);
        hot(Math.round(px), y + 9, 10, 10, () -> flip(1), null, null);

        if (minecraft.level != null) {
            long day = Math.floorDiv(minecraft.level.getDefaultClockTime(), 24000L) + 1;
            long t = Math.floorMod(minecraft.level.getDefaultClockTime() + 6000, 24000L);
            String time = String.format(Locale.ROOT, "%02d:%02d", t / 1000, t % 1000 * 60 / 1000);
            KitDraw.capsRight(g, font, time + " · day " + day, x + W - 12, y + 8, KitDraw.BRIGHT, 1.2f);
            KitDraw.textRight(g, font, minecraft.level.isRaining() ? "Rain" : "Dry", x + W - 12, y + 19, KitDraw.DIM, 0.9f);
        }
    }

    private void flip(int d) {
        tab = Tab.values()[Math.floorMod(tab.ordinal() + d, Tab.values().length)];
    }

    /**
     * Her in the middle, head to foot down the page (Josiah 2026-10-30): standing in her idle facing you, not mirroring
     * what she's doing in the world; drag to turn her, scroll to zoom, and a pose you ask for plays here. Nothing
     * crops her or what she holds.
     */
    private void figure(GuiGraphicsExtractor g, int x, int y, int mx, int my) {
        var skin = com.zomboidcraft.client.npc.NpcSkin.get(npc.model());
        float tall = skin != null ? skin.top : npc.getBbHeight();
        float scale = 300f / tall * zoom;
        // her middle stays put as you zoom; the inventory drawing puts her feet (bbHeight / 2 + 0.0625) below the box's centre
        float mid = y + 186, feet = mid + tall * scale / 2;
        int cx = x + W / 2, cy = Math.round(feet - (npc.getBbHeight() / 2 + 0.0625f) * scale), reach = Math.round(3 * scale) + 400;
        boolean posing = pose != 0 && net.minecraft.util.Util.getMillis() < poseUntil;
        com.zomboidcraft.client.npc.NpcRenderer.portrait = true;
        com.zomboidcraft.client.npc.NpcRenderer.portraitYaw = 180f + spin;
        com.zomboidcraft.client.npc.NpcRenderer.portraitAction = posing ? pose : 0;
        try {
            InventoryScreen.extractEntityInInventoryFollowsMouse(g, cx - reach, cy - reach, cx + reach, cy + reach, Math.round(scale),
                    0.0625f, cx, cy, npc);
        } finally {
            com.zomboidcraft.client.npc.NpcRenderer.portrait = false;
        }
    }

    /** Asks for pose n of NpcPanel.POSES, as its chip does (the UI test films it). */
    public void pose(int n) {
        order(NpcPanel.POSE + n);
    }

    /** Sets how her portrait is turned and zoomed (the UI test films her round). */
    public void view(float spin, float zoom) {
        this.spin = spin;
        this.zoom = zoom;
    }

    // ------------------------------------------------------------------ her page

    private void her(GuiGraphicsExtractor g, @Nullable NpcPanel.State s, int x, int y, int mx, int my) {
        figure(g, x, y, mx, my);

        // the left: everything you can ask
        int lx = x + 37, lw = 185, ly = y + 34;
        KitDraw.heading(g, font, "Orders", lx, ly, lw, false);
        ly += 10;
        boolean following = s != null && s.following();
        ly = chips(g, lx, ly, lw, mx, my, new String[] {"Follow me", "Wait here", "Go home", "Set home here", "How are you?"},
                new int[] {NpcPanel.FOLLOW, NpcPanel.WAIT, NpcPanel.GO_HOME, NpcPanel.SET_HOME, NpcPanel.HOW_ARE_YOU},
                following ? 0 : s != null ? 1 : -1);
        KitDraw.caps(g, font, "Job", lx, ly + 1, KitDraw.LINK, 0.55f);
        ly += 8;
        NpcWork.Job[] jobs = NpcWork.Job.values();
        String[] jl = new String[jobs.length];
        int[] jc = new int[jobs.length];
        for (int i = 0; i < jobs.length; i++) {
            jl[i] = cap(jobs[i].label);
            jc[i] = NpcPanel.JOB + i;
        }
        ly = chips(g, lx, ly, lw, mx, my, jl, jc, s == null ? -1 : s.job());
        KitDraw.caps(g, font, "Shift", lx, ly + 1, KitDraw.LINK, 0.55f);
        ly += 8;
        ly = chips(g, lx, ly, lw, mx, my, new String[] {"Day", "Night"}, new int[] {NpcPanel.DAY_SHIFT, NpcPanel.NIGHT_SHIFT},
                s == null ? -1 : s.night() ? 1 : 0);
        ly += 2;
        KitDraw.heading(g, font, "Talk", lx, ly, lw, false);
        ly += 10;
        String[] talk = {"Compliment her", "Flirt", "Give what you hold", "Hug her", "Hold her hand", "Kiss her", "Her outfit…"};
        int[] tc = new int[talk.length];
        for (int i = 0; i < NpcBond.Act.values().length && i < talk.length; i++) tc[i] = NpcPanel.TALK + i;
        tc[talk.length - 1] = -2;
        ly = chips(g, lx, ly, lw, mx, my, talk, tc, -1);
        ly += 2;
        KitDraw.heading(g, font, "Ask her to", lx, ly, lw, false);
        ly += 10;
        int[] pc = new int[NpcPanel.POSE_NAMES.length];
        for (int i = 0; i < pc.length; i++) pc[i] = NpcPanel.POSE + i;
        ly = chips(g, lx, ly, lw, mx, my, NpcPanel.POSE_NAMES, pc, -1);
        ly += 2;
        KitDraw.heading(g, font, "Bond", lx, ly, lw, false);
        ly += 10;
        boolean bonded = Bonds.bonded(npc);
        chips(g, lx, ly, lw, mx, my, bonded ? new String[] {"All companions", "Unbond"} : new String[] {"Bond with her"},
                bonded ? new int[] {-4, -3} : new int[] {-3}, -1);

        // the right: how she is
        int rx = x + 400, rw = 179, ry = y + 34;
        KitDraw.heading(g, font, "How she is", rx, ry, rw, true);
        ry += 11;
        if (s == null) {
            KitDraw.text(g, font, "…", rx, ry, KitDraw.DIM, 1f);
            return;
        }
        KitDraw.caps(g, font, s.mood(), rx, ry, KitDraw.LINK, 1.1f);
        KitDraw.capsRight(g, font, "Health " + Math.round(100 * s.health() / Math.max(1, s.maxHealth())) + "%", rx + rw, ry + 2,
                s.health() < s.maxHealth() * 0.4f ? KitDraw.LINK : KitDraw.BRIGHT, 0.7f);
        ry += 13;
        int half = (rw - 8) / 2;
        ry = meter(g, "Trust", s.trust(), rx, ry, half, false);
        meter(g, "Affection", s.affection(), rx + half + 8, ry - 13, half, false);
        NpcMind.Need[] needs = NpcMind.Need.values();
        for (int i = 0; i < needs.length; i += 2) {
            int yy = ry;
            ry = meter(g, needs[i].label, s.needs()[i], rx, yy, half, needs[i] == NpcMind.Need.FEAR || needs[i] == NpcMind.Need.PAIN);
            if (i + 1 < needs.length) {
                meter(g, needs[i + 1].label, s.needs()[i + 1], rx + half + 8, yy, half, false);
            }
        }
        ry += 2;
        KitDraw.heading(g, font, "Who she is", rx, ry, rw, true);
        ry += 10;
        NpcMind.Trait[] traits = NpcMind.Trait.values();
        for (int i = 0; i < traits.length; i++) {
            KitDraw.caps(g, font, traits[i].low, rx, ry, KitDraw.DIM, 0.55f);
            KitDraw.capsRight(g, font, traits[i].high, rx + rw, ry, KitDraw.DIM, 0.55f);
            g.fill(rx, ry + 7, rx + rw, ry + 8, 0x33E8E4DA);
            int k = rx + Math.round((s.traits()[i] + 1) / 2f * (rw - 1));
            g.fill(k, ry + 5, k + 1, ry + 10, KitDraw.LINK);
            ry += 12;
        }
        ry += 2;
        KitDraw.heading(g, font, "Skills", rx, ry, rw, true);
        ry += 10;
        NpcSkills.Skill[] skills = NpcSkills.Skill.values();
        for (int i = 0; i < skills.length; i++) {
            int cx = rx + (i % 2) * (half + 8), cy = ry + (i / 2) * 10;
            KitDraw.text(g, font, cap(skills[i].label), cx, cy, KitDraw.TEXT, 0.8f);
            KitDraw.capsRight(g, font, "Lv " + s.skills()[i], cx + half, cy, s.skills()[i] >= 4 ? KitDraw.LINK : KitDraw.DIM, 0.7f);
        }

        int hx = x + 37, hy = y + H - 14;
        hx += KitDraw.hint(g, font, "LMB", "Ask", hx, hy);
        hx += KitDraw.hint(g, font, "Q/R", "Pages", hx, hy);
        hx += KitDraw.hint(g, font, "Drag", "Turn her", hx, hy);
        hx += KitDraw.hint(g, font, "Scroll", "Zoom", hx, hy);
        if (Bonds.bonded(npc)) hx += KitDraw.hint(g, font, "Bksp", "Companions", hx, hy);
        KitDraw.hint(g, font, "G", "Close", hx, hy);
    }

    private int meter(GuiGraphicsExtractor g, String label, float v, int x, int y, int w, boolean warn) {
        KitDraw.caps(g, font, label, x, y, KitDraw.DIM, 0.55f);
        boolean high = warn && v > 40;
        KitDraw.capsRight(g, font, String.valueOf(Math.round(v)), x + w, y - 1, high ? KitDraw.LINK : KitDraw.BRIGHT, 0.7f);
        g.fill(x, y + 7, x + w, y + 8, 0x33E8E4DA);
        g.fill(x, y + 7, x + Math.round(w * Math.min(1, v / 100f)), y + 8, high ? KitDraw.LINK : KitDraw.TEXT);
        return y + 13;
    }

    /** A row of buttons that wraps; code -2 opens her outfit panel. Returns the y below them. */
    private int chips(GuiGraphicsExtractor g, int x, int y, int w, int mx, int my, String[] labels, int[] codes, int on) {
        int cx = x, h = 12;
        for (int i = 0; i < labels.length; i++) {
            int cw = Math.round(KitDraw.width(font, labels[i], 0.75f)) + 8;
            if (cx + cw > x + w) {
                cx = x;
                y += h + 3;
            }
            boolean sel = i == on, over = KitDraw.in(mx, my, cx, y, cw, h);
            g.fill(cx, y, cx + cw, y + h, sel ? 0xEBE8E4DA : over ? 0x26E8E4DA : 0x08E8E4DA);
            KitDraw.frame(g, cx, y, cw, h, sel ? 0xFFE8E4DA : 0x4DE8E4DA);
            KitDraw.text(g, font, labels[i], cx + 4, y + 3, sel ? 0xFF121314 : KitDraw.TEXT, 0.75f);
            int code = codes[i];
            Runnable act = switch (code) {
                case -2 -> () -> minecraft.gui.setScreen(new com.zomboidcraft.client.npc.WardrobeScreen(npc, null));
                case -3 -> () -> Bonds.bond(npc, !Bonds.bonded(npc));
                case -4 -> this::companions;
                default -> () -> order(code);
            };
            hot(cx, y, cw, h, act, null, null);
            cx += cw + 3;
        }
        return y + h + 5;
    }

    // ------------------------------------------------------------------ her gear

    private void gearPage(GuiGraphicsExtractor g, @Nullable NpcPanel.State s, int x, int y, int mx, int my) {
        figure(g, x, y, mx, my);
        int lx = x + 37, lw = 185, ly = y + 34;
        KitDraw.heading(g, font, "What she wears", lx, ly, lw, false);
        ly += 11;
        boolean dresses = com.zomboidcraft.client.npc.NpcClient.dresses(npc);
        List<String> looks = dresses ? List.of() : com.zomboidcraft.client.npc.NpcClient.looks(npc);
        if (!dresses) {
            // a model in whole outfits: pick one, and put her own pieces (her .zsk's, Npcs.wear) on or off
            String current = null;
            for (String h : npc.hidden()) if (h.startsWith("look=")) current = h.substring(5);
            if (current == null && !looks.isEmpty()) current = looks.get(0);
            List<String> wears = s == null ? List.of() : s.wears();
            int n = looks.size() + wears.size(), cols = Math.max(1, (n + 6) / 7), cw = (lw - (cols - 1) * 4) / cols;
            if (n == 0) KitDraw.text(g, font, "She has just the one outfit.", lx, ly + 3, KitDraw.FAINT, 0.8f);
            for (int i = 0; i < n && i < cols * 7; i++) {
                int ex = lx + (i / 7) * (cw + 4), ey = ly + (i % 7) * 14;
                boolean look = i < looks.size();
                String name = look ? looks.get(i) : wears.get(i - looks.size());
                boolean worn = look ? name.equals(current) : !npc.hidden().contains(name);
                boolean over = KitDraw.in(mx, my, ex, ey, cw, 13);
                if (over) g.fill(ex, ey, ex + cw, ey + 13, 0x1AE8E4DA);
                KitDraw.diamond(g, ex + 4, ey + 6, 3, worn ? KitDraw.GOOD : 0x4DE8E4DA);
                String label = look ? cap(name.replace('_', ' ')) + " (outfit)" : pieceName(name);
                KitDraw.text(g, font, label, ex + 11, ey + 3, worn ? KitDraw.BRIGHT : KitDraw.DIM, 0.85f);
                String act = look ? (worn ? null : "Change into") : worn ? "Take off" : "Put on";
                if (act != null && cols == 1) KitDraw.capsRight(g, font, act, ex + cw, ey + 3, over ? KitDraw.LINK : KitDraw.FAINT, 0.55f);
                int code = look ? NpcPanel.LOOK + i : (worn ? NpcPanel.WEAR_OFF : NpcPanel.WEAR_ON) + i - looks.size();
                hot(ex, ey, cw, 13, () -> order(code), null, act == null ? null : act + ": " + label);
            }
            ly += 7 * 14;
        }
        for (NpcWardrobe.Piece p : dresses ? NpcWardrobe.Piece.values() : new NpcWardrobe.Piece[0]) {
            boolean worn = !npc.hidden().contains(p.id);
            boolean over = KitDraw.in(mx, my, lx, ly, lw, 13);
            if (over) g.fill(lx, ly, lx + lw, ly + 13, 0x1AE8E4DA);
            KitDraw.diamond(g, lx + 4, ly + 6, 3, worn ? KitDraw.GOOD : 0x4DE8E4DA);
            KitDraw.text(g, font, cap(p.label), lx + 11, ly + 3, worn ? KitDraw.BRIGHT : KitDraw.DIM, 0.85f);
            KitDraw.capsRight(g, font, worn ? "Ask her to take off" : "Ask her to put on", lx + lw, ly + 3,
                    over ? KitDraw.LINK : KitDraw.FAINT, 0.55f);
            int code = (worn ? NpcWardrobe.TAKE_OFF : NpcWardrobe.PUT_ON) + p.ordinal();
            hot(lx, ly, lw, 13, () -> order(code), null, null);
            ly += 14;
        }
        ly += 4;
        KitDraw.heading(g, font, "Her weapon", lx, ly, lw, false);
        ly += 11;
        ItemStack weapon = s == null ? ItemStack.EMPTY : s.weapon();
        boolean wo = KitDraw.in(mx, my, lx, ly, 22, 22);
        KitDraw.tile(g, font, weapon, lx, ly, 22, 22, wo);
        KitDraw.text(g, font, weapon.isEmpty() ? "Empty-handed: hand her one from your things" : weapon.getHoverName().getString(),
                lx + 27, ly + 7, weapon.isEmpty() ? KitDraw.FAINT : KitDraw.BRIGHT, 0.8f);
        if (!weapon.isEmpty()) hot(lx, ly, 22, 22, () -> gear(NpcPanel.TAKE_WEAPON, 0), null, "Ask for it back");
        ly += 28;
        KitDraw.heading(g, font, "Her bag", lx, ly, lw, false);
        ly += 11;
        List<ItemStack> pack = s == null ? List.of() : s.pack();
        // nine slots, more with a backpack on (smaller tiles then); what she's holding for you is gold-edged
        int slots = Math.max(9, pack.size()), per = slots > 14 ? 10 : 7, step = slots > 14 ? 18 : 26, tile = step - 4;
        for (int i = 0; i < slots; i++) {
            int tx = lx + (i % per) * step, ty = ly + (i / per) * step;
            ItemStack st = i < pack.size() ? pack.get(i) : ItemStack.EMPTY;
            boolean over = KitDraw.in(mx, my, tx, ty, tile, tile);
            KitDraw.tile(g, font, st, tx, ty, tile, tile, over);
            boolean mine = !st.isEmpty() && minecraft.player.getUUID().equals(com.zomboidcraft.npc.NpcCare.offeredTo(st));
            if (mine) KitDraw.frame(g, tx, ty, tile, tile, KitDraw.LINK);
            int slot = i;
            if (!st.isEmpty()) hot(tx, ty, tile, tile, () -> gear(NpcPanel.TAKE, slot), null, st.getHoverName().getString()
                    + (mine ? ": she found it for you, take it" : ": ask for it"));
        }

        // the right: your things, clothes she could wear marked
        int rx = x + 400, rw = 179, ry = y + 34;
        KitDraw.heading(g, font, "Your things", rx, ry, rw, true);
        ry += 11;
        var inv = minecraft.player.getInventory();
        for (int i = 0; i < 36; i++) {
            int tx = rx + (i % 9) * 20, ty = ry + (i / 9) * 20;
            ItemStack st = inv.getItem(i);
            boolean over = KitDraw.in(mx, my, tx, ty, 18, 18);
            KitDraw.tile(g, font, st, tx, ty, 18, 18, over);
            NpcWardrobe.Piece wear = st.isEmpty() || !com.zomboidcraft.client.npc.NpcClient.dresses(npc) ? null : NpcPanel.piece(st);
            String own = st.isEmpty() || s == null || s.wears().isEmpty() ? null : NpcPanel.part(s.wears(), st);
            if (wear != null || own != null) KitDraw.frame(g, tx, ty, 18, 18, KitDraw.LINK);
            int slot = i;
            if (!st.isEmpty()) {
                hot(tx, ty, 18, 18, () -> gear(NpcPanel.GIVE, slot), null, st.getHoverName().getString()
                        + (wear != null ? ": offer it to wear (her " + wear.label + ")"
                        : own != null ? ": offer it to wear (her " + pieceName(own).toLowerCase(java.util.Locale.ROOT) + ")" : ": hand it to her"));
            }
        }
        ry += 4 * 20 + 6;
        ry += KitDraw.wrap(g, font, "Click something to hand it over. Gold-edged clothes she can wear: she'll try them on if she likes them.",
                rx, ry, rw, KitDraw.DIM) + 6;
        KitDraw.heading(g, font, "Trade", rx, ry, rw, true);
        ry += 11;
        boolean trusts = s != null && s.trust() >= 45;
        KitDraw.wrap(g, font, trusts ? "She trusts you: ask for anything in her bag or her weapon and she'll hand it over."
                : "She doesn't trust you enough yet to hand over her things. Gifts, kindness and time together help.",
                rx, ry, rw, trusts ? KitDraw.GOOD : KitDraw.FAINT);

        int hx = x + 37, hy = y + H - 14;
        hx += KitDraw.hint(g, font, "LMB", "Give / ask", hx, hy);
        hx += KitDraw.hint(g, font, "Q/R", "Pages", hx, hy);
        hx += KitDraw.hint(g, font, "Drag", "Turn her", hx, hy);
        hx += KitDraw.hint(g, font, "Scroll", "Zoom", hx, hy);
        if (Bonds.bonded(npc)) hx += KitDraw.hint(g, font, "Bksp", "Companions", hx, hy);
        KitDraw.hint(g, font, "G", "Close", hx, hy);
    }

    // ------------------------------------------------------------------ her diary

    private void diary(GuiGraphicsExtractor g, @Nullable NpcPanel.State s, int x, int y, int mx, int my) {
        figure(g, x, y, mx, my);
        int lx = x + 37, lw = 185, ly = y + 34;
        KitDraw.heading(g, font, "What she remembers", lx, ly, lw, false);
        ly += 12;
        if (s == null || s.diary().isEmpty()) {
            KitDraw.text(g, font, "Nothing yet.", lx, ly, KitDraw.FAINT, 0.85f);
        } else {
            for (String d : s.diary()) {
                String[] f = d.split("\\|", 3);
                if (f.length < 3) continue;
                int colour = f[2].equals("+") ? KitDraw.GOOD : f[2].equals("-") ? KitDraw.BAD : KitDraw.DIM;
                KitDraw.caps(g, font, f[0], lx, ly, colour, 0.55f);
                ly += KitDraw.wrap(g, font, f[1], lx + 34, ly - 1, lw - 34, KitDraw.TEXT) + 4;
                if (ly > y + H - 30) break;
            }
        }
        int hx = x + 37, hy = y + H - 14;
        hx += KitDraw.hint(g, font, "Q/R", "Pages", hx, hy);
        hx += KitDraw.hint(g, font, "Drag", "Turn her", hx, hy);
        hx += KitDraw.hint(g, font, "Scroll", "Zoom", hx, hy);
        if (Bonds.bonded(npc)) hx += KitDraw.hint(g, font, "Bksp", "Companions", hx, hy);
        KitDraw.hint(g, font, "G", "Close", hx, hy);
    }

    /** "top.jacket" reads "Jacket", "gloves" reads "Gloves". */
    private static String pieceName(String piece) {
        return cap(piece.substring(piece.lastIndexOf('.') + 1).replace('_', ' '));
    }

    private static String cap(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        for (int i = hots.size() - 1; i >= 0; i--) {
            Hot h = hots.get(i);
            if (!h.has(event.x(), event.y())) continue;
            if (event.button() == 1 && h.right() != null) h.right().run();
            else h.left().run();
            return true;
        }
        // anywhere else down the middle: take hold of her to turn her
        turning = event.button() == 0 && Math.abs(event.x() - (left + W / 2.0)) < 95;
        return true;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (turning) spin += (float) dx * 1.4f;
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        turning = false;
        return true;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (tab != Tab.DIARY && sy != 0) zoom = net.minecraft.util.Mth.clamp(zoom * (sy > 0 ? 1.12f : 1 / 1.12f), 0.5f, 3f);
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (CarClient.INTERACT.matches(event)) {
            onClose();
            return true;
        }
        if (Bonds.KEY.matches(event)) {
            onClose();
            return true;
        }
        if (event.key() == com.mojang.blaze3d.platform.InputConstants.KEY_BACKSPACE && Bonds.bonded(npc)) {
            companions();
            return true;
        }
        if (event.key() == com.mojang.blaze3d.platform.InputConstants.KEY_Q) {
            flip(-1);
            return true;
        }
        if (event.key() == com.mojang.blaze3d.platform.InputConstants.KEY_R) {
            flip(1);
            return true;
        }
        return super.keyPressed(event);
    }
}
