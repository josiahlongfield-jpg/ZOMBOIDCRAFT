package com.zomboidcraft.client.hud;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.zomboidcraft.client.combat.CombatClient;
import com.zomboidcraft.combat.GunState;
import com.zomboidcraft.combat.GunType;
import com.zomboidcraft.combat.Guns;
import com.zomboidcraft.combat.LocalGuns;
import com.zomboidcraft.combat.Stamina;
import com.zomboidcraft.survival.Body;
import com.zomboidcraft.survival.SurvivalStats;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * The operator HUD, Tarkov's near-empty one (Josiah 2026-11-08, approved for the game at 11:51), drawn from the kit
 * made from his Tarkov files in config/zomboidcraft/hud/tarkov, laid out as the approved preview (tarkov_hud.py):
 * <ul>
 * <li>the stamina bar bottom centre, only while it isn't full;</li>
 * <li>Tarkov's effect icons bottom left, only while something is wrong (bleeding, a break, pain, worn out, a jam, the
 * infection);</li>
 * <li>the gun line bottom right (name, fire mode, rounds / carried) for a few seconds after drawing, reloading or
 * switching fire mode;</li>
 * <li>the X-ray body with each part's condition while the peek key (Left Alt) is held, as Tarkov's Tab screen.</li>
 * </ul>
 * Tarkov counts hit points per part; we have one health bar and wounds per part, so a part's number is your health
 * share for it, lowered by what's wrong with it there.
 */
final class TarkovHud {
    static final String KIT = "tarkov";
    private static final float GUN_SHOW = 3.5f;
    /** Tarkov's hit points per part (its own numbers), in the kit's part order. */
    private static final String[] PARTS = {"head", "chest", "stomach", "leftarm", "rightarm", "leftleg", "rightleg"};
    private static final int[] MAX = {35, 85, 70, 60, 60, 65, 65};

    private static final Hud.Fade staminaFade = new Hud.Fade(), gunFade = new Hud.Fade(), tabFade = new Hud.Fade();
    private static ItemStack lastGun = ItemStack.EMPTY;
    private static int lastSlot = -1;
    private static boolean lastReloading, lastAuto;
    private static float gunSince = 99;

    private TarkovHud() {
    }

    static boolean ready() {
        return HudKit.present(KIT);
    }

    static void render(GuiGraphicsExtractor g) {
        HudKit kit = HudKit.get(KIT);
        LocalPlayer player = Minecraft.getInstance().player;
        if (kit == null || player == null) return;
        kit.begin();
        JsonObject spec = HudKit.obj(kit.json, "layout_spec");
        HudKit.pixels(g);
        try {
            stamina(g, kit, HudKit.obj(spec, "stamina"), player);
            status(g, kit, HudKit.obj(spec, "status"), player);
            gunLine(g, kit, HudKit.obj(spec, "gunline"), player);
            float t = tabFade.update(Hud.peek);
            if (t > 0) tab(g, kit, HudKit.obj(spec, "tab"), player, t);
        } finally {
            HudKit.end(g);
        }
    }

    // ------------------------------------------------------------------ stamina

    private static void stamina(GuiGraphicsExtractor g, HudKit kit, @Nullable JsonObject s, LocalPlayer player) {
        if (s == null) return;
        float v = Stamina.get(player) / Stamina.FULL;
        float a = staminaFade.update(v < 0.995f || Hud.peek);
        if (a <= 0) return;
        JsonArray bars = HudKit.arr(s, "bars");
        if (bars == null || bars.isEmpty()) return;
        // Tarkov's top bar is stamina; its second (arm stamina) has nothing of ours behind it, so it stays out
        JsonObject bar = bars.get(0).getAsJsonObject();
        float cx = HudKit.f(s, "cx"), y = HudKit.f(bar, "y"), w = HudKit.f(bar, "w"), h = HudKit.f(bar, "h");
        kit.fill(g, cx - w / 2, y, cx + w / 2, y + h, colour(HudKit.arr(s, "back"), a));
        kit.fill(g, cx - w / 2, y, cx - w / 2 + w * Mth.clamp(v, 0, 1), y + h, colour(HudKit.arr(s, "fill"), a));
    }

    private static int colour(@Nullable JsonArray c, float a) {
        if (c == null || c.size() < 3) return HudKit.white(a);
        int alpha = c.size() > 3 ? c.get(3).getAsInt() : 255;
        return HudKit.argb(Math.round(alpha * a), c.get(0).getAsInt(), c.get(1).getAsInt(), c.get(2).getAsInt());
    }

    // ------------------------------------------------------------------ effect icons

    private static void status(GuiGraphicsExtractor g, HudKit kit, @Nullable JsonObject s, LocalPlayer player) {
        JsonObject icons = HudKit.obj(kit.json, "icons");
        if (s == null || icons == null) return;
        float x = HudKit.f(s, "x"), y = HudKit.f(s, "y"), gap = HudKit.f(s, "gap");
        for (String name : effects(player)) {
            JsonObject icon = HudKit.obj(icons, name);
            if (icon == null) continue;
            float w = HudKit.f(icon, "w"), h = HudKit.f(icon, "h");
            kit.image(g, icon.get("file").getAsString(), x, y, w, h, -1);
            x += w + gap;
        }
    }

    /** Tarkov's effects for what's wrong with you now, worst first. */
    private static List<String> effects(LocalPlayer player) {
        List<String> out = new ArrayList<>();
        Body body = Body.get(player);
        int bleeding = body.bleeding();
        boolean deepBleed = false, broken = false, unsplinted = false;
        int worst = 0;
        for (Body.Part p : body.parts()) {
            if (p.bleeding() && (p.wound() == Body.Wound.LACERATION || p.wound() == Body.Wound.BITE)) deepBleed = true;
            if (p.fractured()) {
                broken = true;
                if (!p.splinted()) unsplinted = true;
            }
            worst = Math.max(worst, p.severity());
        }
        if (bleeding > 0) out.add(bleeding >= 2 || deepBleed ? "heavy_bleeding" : "light_bleeding");
        if (broken) out.add("fracture");
        if (unsplinted || worst >= 2) out.add("pain");
        else if (worst >= 1 && bleeding == 0) out.add("wound");
        if (SurvivalStats.get(player).infected()) out.add("zombie_infection");
        float st = Stamina.get(player) / Stamina.FULL;
        if (st <= 0.05f) out.add("exhausted");
        else if (st < 0.3f) out.add("fatigue");
        ItemStack hand = player.getMainHandItem();
        if (GunType.isGun(hand) && hand.getOrDefault(GunState.TYPE, GunState.EMPTY_MAG).jammed()) out.add("misfire");
        return out;
    }

    // ------------------------------------------------------------------ gun line

    private static void gunLine(GuiGraphicsExtractor g, HudKit kit, @Nullable JsonObject s, LocalPlayer player) {
        if (s == null) return;
        ItemStack hand = player.getMainHandItem();
        GunType type = GunType.of(hand);
        int slot = player.getInventory().getSelectedSlot();
        boolean reloading = type != null && CombatClient.reloading(hand);
        boolean auto = type != null && Guns.fullAuto(hand);
        gunSince += Hud.dt;
        if (slot != lastSlot || hand.getItem() != lastGun.getItem() || reloading != lastReloading || auto != lastAuto) gunSince = 0;
        lastSlot = slot;
        lastGun = hand.copy();
        lastReloading = reloading;
        lastAuto = auto;
        float a = gunFade.update(type != null && (gunSince < GUN_SHOW || reloading || Hud.peek));
        if (a <= 0 || type == null) return;
        float right = HudKit.f(s, "right"), y = HudKit.f(s, "y");
        int ink = HudKit.white(a);
        kit.text(g, "gun_name", "gun_name", hand.getHoverName().getString(), right, y, 1, 0, true, ink);
        kit.text(g, "gun_mode", "gun_mode", mode(hand, type, auto), right, y + HudKit.f(s, "mode_dy"), 1, 0, true, ink);
        GunState state = hand.getOrDefault(GunState.TYPE, GunState.EMPTY_MAG);
        int rounds = state.mag() ? state.rounds() : 0;
        int carried = count(player.getInventory(), LocalGuns.ammo(hand, type));
        kit.text(g, "gun_ammo", "gun_ammo", rounds + " / " + carried, right, y + HudKit.f(s, "ammo_dy"), 1, 0, true, ink);
    }

    private static String mode(ItemStack hand, GunType type, boolean auto) {
        if (auto) return "Full auto";
        if (type.manual()) return type.pellets() > 1 ? "Pump action" : "Bolt action";
        return type.stats(hand).selectFire ? "Single fire" : "Semi-auto";
    }

    private static int count(Inventory inv, net.minecraft.world.item.Item item) {
        int n = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack st = inv.getItem(i);
            if (st.is(item)) n += st.getCount();
        }
        return n;
    }

    // ------------------------------------------------------------------ X-ray body

    private static void tab(GuiGraphicsExtractor g, HudKit kit, @Nullable JsonObject t, LocalPlayer player, float a) {
        JsonObject bodyArt = HudKit.obj(kit.json, "body");
        JsonObject ui = HudKit.obj(kit.json, "ui");
        if (t == null || bodyArt == null) return;
        float x0 = HudKit.f(t, "x0"), y0 = HudKit.f(t, "y0"), k = HudKit.f(t, "k");
        float bw = HudKit.f(t, "body_w") * k, bh = HudKit.f(t, "body_h") * k;
        JsonArray pad = HudKit.arr(t, "pad");
        if (pad != null && pad.size() == 4) {
            kit.fill(g, x0 - pad.get(0).getAsFloat(), y0 - pad.get(1).getAsFloat(), x0 + bw + pad.get(2).getAsFloat(),
                    y0 + bh + pad.get(3).getAsFloat(), colour(HudKit.arr(t, "shade"), a));
        }
        int[] hp = partHealth(player);
        JsonObject cols = HudKit.obj(t, "colours");
        // the body, each part tinted by its state
        for (int i = 0; i < PARTS.length; i++) {
            JsonObject p = HudKit.obj(bodyArt, PARTS[i]);
            if (p == null) continue;
            int c = partColour(cols, hp[i], MAX[i], a);
            kit.image(g, p.get("file").getAsString(), x0 + HudKit.f(p, "x") * k, y0 + HudKit.f(p, "y") * k,
                    HudKit.f(p, "w") * k, HudKit.f(p, "h") * k, c);
        }
        // each part's label, bar and number at its middle
        JsonObject border = HudKit.obj(ui, "health_bar_border"), cross = HudKit.obj(ui, "body_part_broken_cross");
        float bs = HudKit.f(t, "bar_scale");
        JsonArray labels = HudKit.arr(t, "parts");
        int ink = HudKit.white(a);
        int total = 0, totalMax = 0;
        for (int i = 0; i < PARTS.length; i++) {
            JsonObject p = HudKit.obj(bodyArt, PARTS[i]);
            if (p == null) continue;
            total += hp[i];
            totalMax += MAX[i];
            float cx = x0 + (HudKit.f(p, "x") + HudKit.f(p, "w") / 2) * k;
            float cy = Math.max(y0 + (HudKit.f(p, "y") + HudKit.f(p, "h") / 2) * k, y0 + 12);
            String label = labels != null && i < labels.size() ? labels.get(i).getAsJsonArray().get(1).getAsString() : PARTS[i].toUpperCase();
            kit.text(g, "tab_label_" + i, "tab_label", label, cx, cy - 9, 0.5, 0.5, true, ink);
            float barW = border == null ? 56 : HudKit.f(border, "w") * bs, barH = border == null ? 8 : HudKit.f(border, "h") * bs;
            if (border != null) kit.image(g, border.get("file").getAsString(), cx - barW / 2, cy, barW, barH, ink);
            float fw = (barW - 4) * Math.max(0, hp[i]) / MAX[i];
            if (fw > 0) {
                int fill = partColour(cols, hp[i], MAX[i], a * 0.9f);
                kit.fill(g, cx - barW / 2 + 2, cy + 2, cx - barW / 2 + 2 + fw, cy + barH - 2, fill);
            }
            kit.text(g, "tab_value_" + i, "tab_value", hp[i] + "/" + MAX[i], cx, cy + barH + 6, 0.5, 0.5, true, ink);
            if (broken(player, PARTS[i]) && cross != null) {
                kit.image(g, cross.get("file").getAsString(), cx + barW / 2 + 3, cy, HudKit.f(cross, "w") * 1.6f,
                        HudKit.f(cross, "h") * 1.6f, ink);
            }
        }
        kit.text(g, "tab_total", "tab_total", total + "/" + totalMax, x0 + bw / 2, y0 + bh + 18, 0.5, 0.5, true, ink);
    }

    private static int partColour(@Nullable JsonObject cols, int v, int max, float a) {
        String key = v <= 0 ? "dead" : v / (float) max > 0.75f ? "good" : v / (float) max > 0.4f ? "hurt" : "bad";
        return colour(HudKit.arr(cols, key), a);
    }

    /** Each Tarkov part's hit points: its share of your health, cut by the worst of what's wrong there. */
    private static int[] partHealth(LocalPlayer player) {
        float health = Mth.clamp(player.getHealth() / Math.max(1f, player.getMaxHealth()), 0, 1);
        Body body = Body.get(player);
        int[] out = new int[PARTS.length];
        for (int i = 0; i < PARTS.length; i++) {
            int severity = 0;
            for (Body.Region r : regions(PARTS[i])) severity = Math.max(severity, body.part(r).severity());
            float cut = switch (severity) {
                case 0 -> 1f;
                case 1 -> 0.8f;
                case 2 -> 0.55f;
                default -> 0.3f;
            };
            out[i] = Math.round(MAX[i] * health * cut);
            if (health > 0 && out[i] == 0) out[i] = 1;
        }
        return out;
    }

    private static boolean broken(LocalPlayer player, String part) {
        Body body = Body.get(player);
        for (Body.Region r : regions(part)) if (body.part(r).fractured()) return true;
        return false;
    }

    /** Our seventeen parts under each of Tarkov's seven. */
    private static List<Body.Region> regions(String part) {
        return switch (part) {
            case "head" -> List.of(Body.Region.HEAD, Body.Region.NECK);
            case "chest" -> List.of(Body.Region.UPPER_TORSO);
            case "stomach" -> List.of(Body.Region.LOWER_TORSO, Body.Region.GROIN);
            case "leftarm" -> Body.Zone.LEFT_ARM.parts();
            case "rightarm" -> Body.Zone.RIGHT_ARM.parts();
            case "leftleg" -> Body.Zone.LEFT_LEG.parts();
            default -> Body.Zone.RIGHT_LEG.parts();
        };
    }
}
