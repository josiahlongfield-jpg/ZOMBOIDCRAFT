package com.zomboidcraft.client.hud;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.zomboidcraft.ModItems;
import com.zomboidcraft.ZcConfig;
import com.zomboidcraft.client.combat.CombatClient;
import com.zomboidcraft.combat.GunState;
import com.zomboidcraft.combat.GunType;
import com.zomboidcraft.combat.LocalGuns;
import com.zomboidcraft.combat.RifleItem;
import com.zomboidcraft.combat.Stamina;
import com.zomboidcraft.npc.NpcEntity;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * The Spartan HUD, Halo: Reach's own (Josiah 2026-11-08, approved for the game at 11:51), drawn from the kit pulled
 * from his MCC files into config/zomboidcraft/hud/reach. It is laid out exactly as the approved previews drew it
 * (scratchpad reach/kitview.py does the same sums): compass and shield over health top centre, motion tracker bottom
 * left, grenades top left over the armour ability, the weapon top right with its rounds as ticks, carried ammo and
 * the gun on your back. Our guns borrow the nearest Reach gun's panel and reticle.
 *
 * What it reads: shields stay full (there are none yet), health is your health, the ability meter is your endurance,
 * the tracker shows enemies within 25 m while they move (zombies dimmer) and friends always, the carried number is the
 * loose rounds for the gun in your pack, the back slot is the next gun on your belt.
 */
final class ReachHud {
    static final String KIT = "reach";
    private static final float RANGE = 25f;

    /** When each enemy was last seen moving, for the tracker (Reach shows only moving foes, fading as they stop). */
    private static final Map<Entity, Long> moved = new WeakHashMap<>();
    /** The reticle's spread as drawn, eased (0 at rest, 1 fully bloomed). */
    private static float spread;

    private ReachHud() {
    }

    static boolean ready() {
        return HudKit.present(KIT);
    }

    // ------------------------------------------------------------------ the HUD

    static void render(GuiGraphicsExtractor g) {
        HudKit kit = HudKit.get(KIT);
        LocalPlayer player = Minecraft.getInstance().player;
        if (kit == null || player == null) return;
        kit.begin();
        JsonObject el = HudKit.obj(kit.json, "elements");
        HudKit.pixels(g);
        try {
            compass(g, kit, HudKit.obj(el, "compass"), Mth.positiveModulo(player.getYRot() + 180f, 360f));
            frames(g, kit, HudKit.obj(el, "shield"), 1f);
            frames(g, kit, HudKit.obj(el, "health"), player.getHealth() / Math.max(1f, player.getMaxHealth()));
            tracker(g, kit, HudKit.obj(el, "tracker"), player);
            JsonObject gren = HudKit.obj(el, "grenades");
            if (gren != null) {
                kit.entry(g, HudKit.obj(gren, "image"), -1);
                text(g, kit, "grenades", HudKit.obj(gren, "count"), String.valueOf(count(player.getInventory(), ModItems.FRAG_GRENADE)));
            }
            frames(g, kit, HudKit.obj(el, "ability"), Stamina.get(player) / Stamina.FULL);
            weapon(g, kit, player);
        } finally {
            HudKit.end(g);
        }
    }

    /** A meter drawn as one of its pre-drawn fill steps. */
    private static void frames(GuiGraphicsExtractor g, HudKit kit, @Nullable JsonObject el, float fill) {
        JsonArray fr = HudKit.arr(el, "frames");
        if (fr == null || fr.isEmpty()) return;
        int k = Mth.clamp(Math.round(Mth.clamp(fill, 0, 1) * (fr.size() - 1)), 0, fr.size() - 1);
        kit.entry(g, fr.get(k).getAsJsonObject(), -1);
    }

    private static void text(GuiGraphicsExtractor g, HudKit kit, String slot, @Nullable JsonObject spec, String s) {
        if (spec == null) return;
        kit.text(g, slot, spec.get("font").getAsString(), s, HudKit.f(spec, "x"), HudKit.f(spec, "y"),
                HudKit.f(spec, "align_x"), HudKit.f(spec, "align_y"), false, -1);
    }

    /**
     * Reach's compass: a window on a 360-degree tape, cut into thin slices that each follow the HUD's bow and fade
     * toward the ends. Heading 0 is north, 90 east (the tape's east mark sits at {@code east_texel}).
     */
    private static void compass(GuiGraphicsExtractor g, HudKit kit, @Nullable JsonObject c, float heading) {
        if (c == null) return;
        String file = c.get("file").getAsString();
        float texels = HudKit.f(c, "texels"), win = HudKit.f(c, "window_texels");
        float cw = HudKit.f(c, "w"), ch = HudKit.f(c, "h"), cx = HudKit.f(c, "cx"), cy = HudKit.f(c, "cy"), fade = HudKit.f(c, "fade");
        JsonArray bow = HudKit.arr(c, "bow");
        float winPx = cw * kit.s;
        float k = winPx / win;                                    // window pixels per tape texel
        int stripW = Math.max(1, Math.round(texels * k)), stripH = Math.max(1, Math.round(ch * kit.s));
        float centre = (HudKit.f(c, "east_texel") + (heading - 90f) / 90f * HudKit.f(c, "texels_per_90")) * k;
        int slice = Math.max(3, Math.round(winPx / 48f));
        int n = (int) Math.ceil(winPx / slice);
        float left = kit.px(cx - cw / 2);
        for (int i = 0; i < n; i++) {
            int x0 = i * slice, w = Math.min(slice, Math.round(winPx) - x0);
            if (w <= 0) break;
            float tm = (x0 + w / 2f) / winPx;
            float a = Mth.clamp((1 - Math.abs(tm * 2 - 1)) / Math.max(1e-3f, fade), 0, 1);
            float dx = 0, dy = 0;
            if (bow != null && bow.size() > 1) {
                float bi = tm * (bow.size() - 1);
                int j = Math.min(bow.size() - 2, (int) bi);
                float f = bi - j;
                JsonArray b0 = bow.get(j).getAsJsonArray(), b1 = bow.get(j + 1).getAsJsonArray();
                dx = b0.get(0).getAsFloat() * (1 - f) + b1.get(0).getAsFloat() * f;
                dy = b0.get(1).getAsFloat() * (1 - f) + b1.get(1).getAsFloat() * f;
            }
            int x = Math.round(left + x0 + dx * kit.s), y = Math.round(kit.py(cy - ch / 2 + dy));
            float u = Mth.positiveModulo(centre - winPx / 2 + x0, (float) stripW);
            int colour = HudKit.white(a);
            int first = Math.min(w, (int) Math.floor(stripW - u));
            kit.strip(g, file, stripW, stripH, x, y, u, Math.max(0, first), colour);
            if (first < w) kit.strip(g, file, stripW, stripH, x + Math.max(0, first), y, 0, w - Math.max(0, first), colour);
        }
    }

    /**
     * The motion tracker: everything within 25 m, you at the middle facing up, mapped through the kit's grid onto the
     * tilted disc. Enemies show while they move (Reach's rule) and fade a second after they stop; friends always.
     */
    private static void tracker(GuiGraphicsExtractor g, HudKit kit, @Nullable JsonObject tr, LocalPlayer player) {
        if (tr == null) return;
        kit.entry(g, HudKit.obj(tr, "image"), -1);
        text(g, kit, "tracker_25m", HudKit.obj(tr, "text_25m"), "25m");
        JsonArray grid = HudKit.arr(tr, "grid");
        JsonObject blips = HudKit.obj(tr, "blips");
        if (grid == null || blips == null || player.level() == null) return;
        float span = HudKit.f(tr, "grid_span"), bs = HudKit.f(tr, "blip_size") * 2;
        double yaw = Math.toRadians(player.getYRot());
        double fx = -Math.sin(yaw), fz = Math.cos(yaw);      // facing
        double rx = -Math.cos(yaw), rz = -Math.sin(yaw);     // your right
        long now = System.currentTimeMillis();
        for (Entity e : player.level().getEntities(player, player.getBoundingBox().inflate(RANGE, 12, RANGE))) {
            if (!(e instanceof LivingEntity living) || !living.isAlive()) continue;
            boolean friend = e instanceof Player || e instanceof NpcEntity;
            boolean enemy = !friend && e instanceof Enemy;
            if (!friend && !enemy) continue;
            float a = 1f;
            if (enemy) {
                double mx = e.getX() - e.xo, mz = e.getZ() - e.zo;
                if (mx * mx + mz * mz > 1e-5) moved.put(e, now);
                Long when = moved.get(e);
                if (when == null) continue;
                a = 1f - Mth.clamp((now - when - 600) / 900f, 0, 1);
                if (a <= 0) continue;
                if (e instanceof Zombie) a *= 0.6f;          // the dead dimmer, the Covenant bright (H4)
            }
            double dx = e.getX() - player.getX(), dz = e.getZ() - player.getZ();
            float u = (float) ((dx * rx + dz * rz) / RANGE), v = (float) (-(dx * fx + dz * fz) / RANGE);
            if (u * u + v * v > 1) continue;
            float[] p = gridPoint(grid, span, u, v);
            String file = blips.has(friend ? "friend" : "enemy") ? blips.get(friend ? "friend" : "enemy").getAsString() : null;
            if (file != null) kit.image(g, file, p[0] - bs / 2, p[1] - bs / 2, bs, bs, HudKit.white(a));
        }
    }

    /** Where a flat tracker point (u, v in -span..span, v negative ahead) lands on the tilted disc, layout units. */
    private static float[] gridPoint(JsonArray grid, float span, float u, float v) {
        int n = grid.size();
        float gi = (u + span) / (2 * span) * (n - 1), gj = (v + span) / (2 * span) * (n - 1);
        int i = Mth.clamp((int) gi, 0, n - 2), j = Mth.clamp((int) gj, 0, n - 2);
        float fi = gi - i, fj = gj - j;
        float[] a = lerp(pt(grid, j, i), pt(grid, j, i + 1), fi), b = lerp(pt(grid, j + 1, i), pt(grid, j + 1, i + 1), fi);
        return lerp(a, b, fj);
    }

    private static float[] pt(JsonArray grid, int row, int col) {
        JsonArray p = grid.get(row).getAsJsonArray().get(col).getAsJsonArray();
        return new float[] {p.get(0).getAsFloat(), p.get(1).getAsFloat()};
    }

    private static float[] lerp(float[] a, float[] b, float f) {
        return new float[] {a[0] * (1 - f) + b[0] * f, a[1] * (1 - f) + b[1] * f};
    }

    /** The weapon panel: the gun in hand as Reach draws it (ticks for the rounds left), carried ammo, the gun on your back. */
    private static void weapon(GuiGraphicsExtractor g, HudKit kit, LocalPlayer player) {
        ItemStack hand = player.getMainHandItem();
        GunType type = GunType.of(hand);
        JsonObject weapons = HudKit.obj(kit.json, "weapons");
        if (type == null || weapons == null) return;
        JsonObject w = HudKit.obj(weapons, reachWeapon(type));
        if (w == null) return;
        GunState state = hand.getOrDefault(GunState.TYPE, GunState.EMPTY_MAG);
        int cap = RifleItem.capacity(hand), rounds = state.mag() ? state.rounds() : 0;
        JsonArray panel = HudKit.arr(w, "panel");
        if (panel != null && !panel.isEmpty()) {
            int n = panel.size() - 1;
            int k = rounds <= 0 || cap <= 0 ? 0 : Mth.clamp((int) Math.ceil(rounds / (double) cap * n - 1e-6), 1, n);
            kit.entry(g, panel.get(k).getAsJsonObject(), -1);
        }
        text(g, kit, "reserve", HudKit.obj(w, "number"), String.valueOf(carried(player, hand, type)));
        ItemStack back = backGun(player, hand);
        GunType backType = back.isEmpty() ? null : GunType.of(back);
        if (backType != null) {
            JsonObject bw = HudKit.obj(weapons, reachWeapon(backType));
            JsonObject pack = HudKit.obj(bw, "backpack");
            if (pack != null) {
                kit.entry(g, HudKit.obj(pack, "image"), -1);
                GunState bs = back.getOrDefault(GunState.TYPE, GunState.EMPTY_MAG);
                int total = (bs.mag() ? bs.rounds() : 0) + carried(player, back, backType);
                text(g, kit, "back_count", HudKit.obj(pack, "count"), String.valueOf(total));
            }
        }
    }

    /** The first other gun on the belt (the hotbar), the one Reach shows on your back. */
    private static ItemStack backGun(LocalPlayer player, ItemStack hand) {
        Inventory inv = player.getInventory();
        for (int i = 0; i < 9; i++) {
            ItemStack s = inv.getItem(i);
            if (s != hand && GunType.isGun(s)) return s;
        }
        return ItemStack.EMPTY;
    }

    /** Loose rounds for this gun anywhere in the inventory. */
    private static int carried(LocalPlayer player, ItemStack gun, GunType type) {
        return count(player.getInventory(), LocalGuns.ammo(gun, type));
    }

    private static int count(Inventory inv, Item item) {
        int n = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.is(item)) n += s.getCount();
        }
        return n;
    }

    /** The Reach gun whose panel and reticle ours borrows. */
    static String reachWeapon(GunType type) {
        return switch (type) {
            case PISTOL, POCKET_PISTOL, MACHINE_PISTOL, REVOLVER, MAGNUM -> "magnum";
            case PRECISION_RIFLE, LEVER_ACTION, BATTLE_RIFLE -> "dmr";
            case HUNTING_RIFLE, HEAVY_SNIPER -> "sniper_rifle";
            case SHOTGUN, DOUBLE_BARREL, SAWN_OFF -> "shotgun";
            case GRENADE_LAUNCHER -> "grenade_launcher";
            case CROSSBOW -> "needle_rifle";
            default -> "assault_rifle";
        };
    }

    // ------------------------------------------------------------------ the reticle

    /**
     * Reach's reticle for the gun in hand, in place of the crosshair; false when there's nothing to draw (no kit, no
     * gun), so the usual crosshair shows. Its spread follows the gun's real bloom (the cone the server shoots in), held
     * fully open while reloading, the way Reach's does.
     */
    static boolean reticle(GuiGraphicsExtractor g, LocalPlayer player) {
        HudKit kit = HudKit.get(KIT);
        ItemStack hand = player.getMainHandItem();
        GunType type = GunType.of(hand);
        if (kit == null || type == null) return false;
        JsonObject w = HudKit.obj(HudKit.obj(kit.json, "weapons"), reachWeapon(type));
        JsonArray files = HudKit.arr(w, "reticle");
        if (w == null || files == null || files.isEmpty()) return false;
        ZcConfig.GunStats stats = type.stats(hand);
        double most = Math.max(stats.autoBloomMax, stats.shotBloom);
        float target = most > 0 ? (float) Mth.clamp(CombatClient.bloom(type) / most, 0, 1) : 0;
        if (CombatClient.reloading(hand)) target = 1;
        spread += (target - spread) * Math.min(1f, Hud.dt * 14f);
        int k = Mth.clamp(Math.round(spread * (files.size() - 1)), 0, files.size() - 1);
        float size = HudKit.f(w, "reticle_size");
        kit.begin();
        HudKit.pixels(g);
        kit.centred(g, files.get(k).getAsString(), 640, 360, size, size, -1);
        HudKit.end(g);
        return true;
    }
}
