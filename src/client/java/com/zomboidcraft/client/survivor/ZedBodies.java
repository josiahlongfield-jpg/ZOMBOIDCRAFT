package com.zomboidcraft.client.survivor;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.zomboidcraft.ZcConfig;
import com.zomboidcraft.ZomboidCraft;
import com.zomboidcraft.client.npc.NpcSkin;
import com.zomboidcraft.client.npc.NpcSkinDraw;
import com.zomboidcraft.combat.Deaths;
import com.zomboidcraft.combat.Dismember;
import com.zomboidcraft.zombie.ZombieTypes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Zombies drawn as Project Zomboid's own: the Zomboid body in one of its rotting zombie skins, its hair and beard, and
 * one of Zomboid's outfits for where it was when it turned, item by item as Zomboid rolls them (clothing.xml), all from
 * the local zombie packs (tools/pz/zombie_pack.py, never in the jar). Our outfits name Zomboid's
 * (zombie_outfits.json, made into each pack's "pools"). Zomboid's zombie moves play on it: its own walk of the five,
 * the lunge, the bite, the crawl, falls, getting up, beating on a door, climbing through a window, and the death it
 * died, held as it lies. Red eyes glow in the dark and go out over a second when it dies.
 * <p>
 * Everything about its look is rolled from its variant (its UUID's low bits), so the zombie and the body it leaves look
 * the same, to everyone. Only near ones are drawn this way (look.pzZombieDistance); far ones, ones that have lost a
 * limb, and PCs without the packs keep the lighter body (zombie/body).
 */
public final class ZedBodies {
    private static final Identifier EYE = ZomboidCraft.id("textures/entity/zed_eye.png");
    private static final int FULL_BRIGHT = 0xF000F0;
    /** Zomboid's hair shades for the dead: mostly browns and black, some fair, ginger and grey. */
    private static final String[] HAIR = {"dark", "dark", "brown", "brown", "black", "black", "blonde", "auburn", "ginger", "grey", "platinum"};
    /** Zomboid's random clothing tints: worn, everyday colours (as survivors' clothes have them). */
    private static final int[] TINTS = {0x2B3A55, 0x6B2A2A, 0x3E5A3A, 0x7A6A4A, 0x4A4A4E, 0x8C8C88, 0x1E1E22, 0xB8B0A0,
            0x5A3D2A, 0x2F5A6A, 0x7A4A6A, 0xA05A2A, 0x4A5A2A, 0xC8C0B0, 0x6A1E2A, 0x3A3A5A};
    /** Each zombie's skin and garments are made at this size (their sources run to 1024), so a horde fits in the kept textures. */
    private static final int TEX = 512;
    /** The lighter level's textures: a quarter of the pixels, so a horde's worth fit in the textures kept. */
    private static final int TEX_LOW = 256;
    private static long lastSweep;
    /** Zomboid's body parts, as its blood and holes are laid out (tools/pz/zombie_fx.py). */
    private static final String[] PARTS = {"Head", "Chest", "Waist", "Belt", "Crotch", "LeftArm", "RightArm", "LeftLeg", "RightLeg"};
    /** Items whose only texture sits in Zomboid's texture packs, and the loose item worn in their place. */
    private static final Map<String, String> STAND_IN = Map.of(
            "Trousers_DefaultTEXTURE_TINT", "Trousers_WhiteTINT",
            "Tshirt_SportDECAL", "Tshirt_Sport",
            "Vest_DefaultTEXTURE", "Tshirt_DefaultDECAL",
            "Vest_DefaultTEXTURE_TINT", "Tshirt_DefaultTEXTURE_TINT");
    /** How fast each kind of move carries a zombie (metres a second), to step its feet in time. */
    private static final float WALK = 0.9f, FAST = 1.7f, SPRINT = 4.2f, CRAWL = 0.45f;

    /** One zombie's (or corpse's) look, rolled once. */
    /**
     * paintLow: the body's paint without the clothes' cut-outs, for the thinned meshes, whose coarser legs would carry a
     * cut-out (skin hidden under the trousers) down past the hem and leave a hole between trouser and shoe.
     */
    private record Look(SurvivorPack pack, String skin, List<SurvivorTextures.Layer> paint, List<SurvivorTextures.Layer> paintLow,
                        Map<String, List<SurvivorTextures.Layer>> pieces,
                        String hairPiece, String hairTex, String beardPiece, String beardTex, String colour, String family) {
    }

    /** What the layer draws this frame. */
    public static final class Drawn {
        SurvivorPack pack;
        Entry entry;
        Matrix4f[] skin;
        Identifier body, hair, beard;
        String hairPiece, beardPiece;
        Map<String, Identifier> clothes;
        /** How bright the eyes glow (0 out). */
        float eyes;
        /** Which meshes: 0 Zomboid's full ones, 1 the thinned copies (zombie_lod.py) past the nearest few. */
        int level;
        /** Its textures aren't made yet: nothing is drawn this frame (never the old body in its place). */
        boolean pending;
        /** How far off its place the body is drawn (blocks, in its own turned frame): climbing, beating on a door. */
        float shiftX, shiftY, shiftZ;
        float age;
        /** Last frame's pose is drawn again from its skinned buffers (a far zombie on its off frame). */
        boolean reuse;
    }

    static final class Entry {
        final SurvivorAnimator anim = new SurvivorAnimator();
        SurvivorPack pack;
        Look look;
        String lookKey;
        float[] pos, turn;
        boolean[] upper;
        float age = Float.NaN;
        long seen;
        float attackWas;
        int hurtWas;
        boolean lowWas;
        final Object[][] buffers = new Object[2][];
        int buffer;
        float submitAge = Float.NaN;
        final ZedHop.State hop = new ZedHop.State();
        /** The bones it was last drawn with, and the frame, level and cuts they were posed for. */
        Matrix4f[] lastSkin;
        long posedFrame = Long.MIN_VALUE;
        int posedLevel = -1, posedCuts;
        /** Which of the two buffers that pose was skinned into (-1: none yet). */
        int skinnedBuffer = -1;
    }

    /** What a zombie is doing this frame, as the renderer knows it. */
    public static final class Facts {
        public float age, speed, attack, deathTime, actTime;
        public int hurtTime, act, variant, outfitDamage;
        /** Limbs lost (Dismember's cut bits, the head's HEAD_GONE among them, and any lost before it rose). */
        public int cuts;
        public boolean aggressive, low, crawler, sprinter;
        public long death;
        public String outfit;
        public float shiftX, shiftY, shiftZ;
        /** A clip it's set in and held at the end of, over anything else (a body a Harvestman has set out). */
        public String posed;
    }

    /** Acts (as ZMove numbers them). */
    public static final int BASH_DOOR = 1, BASH_WIN = 2, CLIMB = 3;

    private static final Map<Integer, Entry> ENTRIES = new HashMap<>();
    private static final Map<String, Look> LOOKS = new LinkedHashMap<>(64, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Look> e) {
            return size() > 512;
        }
    };

    private ZedBodies() {
    }

    /** Whether Zomboid bodies are on and this PC has a zombie pack. */
    public static boolean available() {
        return ZcConfig.get().look.pzZombies && (SurvivorPack.zombieInstalled("f") || SurvivorPack.zombieInstalled("m"));
    }

    /**
     * Which level of Zomboid's body something this far from the camera (squared blocks) gets: 0, the full one, within
     * look.pzZombieDistance and only for the nearest look.pzZombieMax (each is a few thousand skinned triangles a
     * frame); 1, the thinned one, for every other. Josiah's rule: with the Zomboid zombies in, no other body is seen.
     */
    public static int level(double distSq) {
        ZcConfig.Look look = ZcConfig.get().look;
        double d = look.pzZombieDistance;
        if (distSq < d * d && distSq < com.zomboidcraft.client.zombie.body.ZDetail.nearestSq(look.pzZombieMax)) return 0;
        // the thinned body only for the next nearest few close by (through a scope by how near it looks); every other
        // zombie gets the thinnest Zomboid body, still a model, never a picture (Josiah: a scope would show a picture
        // up). 2026-11-02 downtown profile: level-1 bodies, each in its own textures, were 65 ms of the card's frame
        double m = look.pzMidDistance, seen = com.zomboidcraft.client.zombie.body.ZDetail.zoomed(distSq);
        if (seen < m * m && distSq < com.zomboidcraft.client.zombie.body.ZDetail.nearestSq(look.pzMidZombies)) return 1;
        return 2;
    }

    private static final Map<SurvivorPack, Integer> LEVELS = new java.util.WeakHashMap<>();

    /** The thinnest level the pack carries (packs built before the thinned meshes have only 0: drawn full at any range). */
    private static int levels(SurvivorPack pack) {
        return LEVELS.computeIfAbsent(pack, p -> {
            int most = 0;
            for (NpcSkin.Group g : p.skin.groups) most = Math.max(most, g.level);
            return most;
        });
    }

    private static boolean hasLow(SurvivorPack pack) {
        return levels(pack) >= 1;
    }

    /** A look's texture at the size wanted, or the other size while that's being made, or null. */
    private static Identifier tex(SurvivorPack pack, List<SurvivorTextures.Layer> layers, int size) {
        Identifier t = SurvivorTextures.body(pack, layers, size);
        return t != null ? t : SurvivorTextures.peek(pack, layers, size == TEX ? TEX_LOW : TEX);
    }

    // ------------------------------------------------------------------ the look

    private static long mix(long x) {
        x = (x ^ (x >>> 30)) * 0xBF58476D1CE4E5B9L;
        x = (x ^ (x >>> 27)) * 0x94D049BB133111EBL;
        return x ^ (x >>> 31);
    }

    /** A roll in [0, 1) from the variant and what it's for. */
    private static float roll(int variant, long salt) {
        return (mix(variant * 0x9E3779B97F4A7C15L + salt) >>> 40) / (float) (1L << 24);
    }

    private static int pick(int variant, long salt, int n) {
        return Math.min(n - 1, (int) (roll(variant, salt) * n));
    }

    /** How far gone it is: 0 fresh to 3 bones showing (Zomboid's three wound levels over the clean body), capped by gore. */
    public static int rot(int variant) {
        float r = roll(variant, 11);
        int level = r < 0.15f ? 0 : r < 0.45f ? 1 : r < 0.75f ? 2 : 3;
        return Math.min(level, Math.clamp(ZcConfig.get().look.maxGore, 0, 3));
    }

    /** How worn out it and its clothes are, 0 near clean to 1 filthy and torn: its own roll, leaning worse the more it's rotted. */
    public static float wear(int variant) {
        float r = roll(variant, 13);
        return Math.clamp(r * 0.78f + 0.075f * rot(variant), 0f, 1f);
    }

    private static SurvivorPack packFor(int variant) {
        String sex = roll(variant, 1) < 0.5f ? "f" : "m";
        SurvivorPack p = SurvivorPack.zombie(sex);
        return p != null ? p : SurvivorPack.zombie(sex.equals("f") ? "m" : "f");
    }

    /** Painted garments go on in Zomboid's order, under to over: socks, shoes, trousers, then shirts and coats. */
    private static int layer(String item) {
        String s = item.toLowerCase(java.util.Locale.ROOT);
        if (s.startsWith("socks") || s.startsWith("tights") || s.startsWith("stockings")) return 0;
        if (s.startsWith("underwear") || s.startsWith("bra") || s.startsWith("briefs") || s.startsWith("boxers")) return 1;
        if (s.startsWith("shoes") || s.startsWith("boots")) return 2;
        if (s.startsWith("trousers") || s.startsWith("shorts") || s.startsWith("skirt")) return 3;
        if (s.startsWith("tshirt") || s.startsWith("vest_") && !s.contains("bullet")) return 4;
        if (s.startsWith("shirt")) return 5;
        if (s.startsWith("dress") || s.startsWith("jumper") || s.startsWith("sweater")) return 6;
        if (s.startsWith("gloves")) return 8;
        return 7;
    }

    private static Look look(SurvivorPack pack, int variant, String outfit) {
        int rot = rot(variant);
        String key = pack.sex + "|" + variant + "|" + outfit + "|" + rot;
        Look have = LOOKS.get(key);
        if (have != null) return have;
        JsonObject cat = pack.catalog;
        // which of Zomboid's outfits: weighted, from where it was (our outfit's pool)
        JsonObject pools = cat.has("pools") ? cat.getAsJsonObject("pools") : new JsonObject();
        JsonArray pool = pools.has(outfit) ? pools.getAsJsonArray(outfit) : pools.getAsJsonArray("civilian");
        String chosen = null;
        if (pool != null && !pool.isEmpty()) {
            double total = 0;
            for (JsonElement e : pool) total += e.getAsJsonArray().get(1).getAsDouble();
            double r = roll(variant, 2) * total;
            for (JsonElement e : pool) {
                JsonArray a = e.getAsJsonArray();
                chosen = a.get(0).getAsString();
                r -= a.get(1).getAsDouble();
                if (r < 0) break;
            }
        }
        // its items, each rolled as Zomboid does: in by its probability, then one of the ones it may be
        List<String> items = new ArrayList<>();
        JsonObject outfits = cat.has("outfits") ? cat.getAsJsonObject("outfits") : new JsonObject();
        JsonObject o = chosen == null || !outfits.has(chosen) ? null : outfits.getAsJsonObject(chosen);
        if (o != null) {
            int n = 0;
            for (JsonElement e : o.getAsJsonArray("items")) {
                JsonObject it = e.getAsJsonObject();
                n++;
                if (roll(variant, 100 + n * 2) >= it.get("p").getAsFloat()) continue;
                JsonArray any = it.getAsJsonArray("any");
                if (any.isEmpty()) continue;
                items.add(any.get(pick(variant, 101 + n * 2, any.size())).getAsString());
            }
        }
        items.sort(java.util.Comparator.comparingInt(ZedBodies::layer));
        List<SurvivorTextures.Layer> paint = new ArrayList<>(), cuts = new ArrayList<>();
        Map<String, List<SurvivorTextures.Layer>> pieces = new LinkedHashMap<>();
        boolean hairHidden = false, beardHidden = false;
        for (String name : items) {
            SurvivorPack.Cloth c = pack.clothes.get(name);
            if (c == null || c.piece() == null && c.base().isEmpty()) {
                // painted only from the game's packed textures, which the pack can't read: Zomboid's nearest loose one
                String in = STAND_IN.get(name);
                c = in == null ? null : pack.clothes.get(in);
            }
            if (c == null) continue;
            int r = (int) (mix(variant * 31L + name.hashCode()) >>> 33);
            int tint = c.tint() || c.hue() ? TINTS[Math.floorMod(r >> 5, TINTS.length)] : -1;
            if (c.piece() != null && !c.textures().isEmpty()) {
                pieces.put(c.piece(), new ArrayList<>(List.of(new SurvivorTextures.Layer(c.textures().get(Math.floorMod(r, c.textures().size())), tint))));
            }
            if (!c.base().isEmpty()) paint.add(new SurvivorTextures.Layer(c.base().get(Math.floorMod(r, c.base().size())), tint));
            if (c.mask() != null) cuts.add(new SurvivorTextures.Layer(c.mask(), -1, SurvivorTextures.CUT));
            if (c.hat() != null && !c.hat().isEmpty()) {
                // Josiah's call, as for survivors: any hat, helmet or hood replaces the hair outright
                if (c.hat().equals("nobeard")) beardHidden = true;
                else hairHidden = true;
            }
            if (name.startsWith("Hat_") && c.piece() != null) hairHidden = true;
        }
        List<SurvivorTextures.Layer> body = new ArrayList<>();
        String skin = "zed/" + (1 + pick(variant, 7, 4)) + "/" + rot;
        if (pack.skin.raw(skin) == null) skin = "zed/1/0";
        body.add(new SurvivorTextures.Layer(skin, -1));
        if (pack.skin.raw("uw") != null) body.add(new SurvivorTextures.Layer("uw", -1));   // never bare
        // roughed up as Zomboid's are: blood and grime by body part, on the skin and over the clothes, and holes torn
        // through the clothes where the skin (and its wounds) shows back through
        List<SurvivorTextures.Layer> mess = new ArrayList<>(), tears = new ArrayList<>(), rips = new ArrayList<>();
        if (ZcConfig.get().look.roughZombies) {
            // how worn out it is, its own: some nearly clean, most marked here and there, the long-dead filthy and torn.
            // Blood, grime and tears all go by it, each part to its own degree, so no two come out alike
            float wear = wear(variant);
            for (int i = 0; i < PARTS.length; i++) {
                String part = PARTS[i];
                float b = roll(variant, 200 + i), g = roll(variant, 220 + i), h = roll(variant, 240 + i);
                if (b < 0.08f + 0.55f * wear) {
                    String k = (b < (wear - 0.55f) * 0.8f ? "zfx/blood2/" : "zfx/blood1/") + part;
                    int strength = (int) (255 * (0.35f + 0.65f * wear) * (0.45f + 0.55f * roll(variant, 260 + i)));
                    if (pack.skin.raw(k) != null) mess.add(new SurvivorTextures.Layer(k, -1, SurvivorTextures.OVER, strength));
                }
                if (g < 0.1f + 0.6f * wear && pack.skin.raw("zfx/grime/" + part) != null) {
                    int strength = (int) (255 * (0.2f + 0.6f * wear) * (0.4f + 0.6f * roll(variant, 280 + i)));
                    mess.add(new SurvivorTextures.Layer("zfx/grime/" + part, -1, SurvivorTextures.OVER, strength));
                }
                // never through the underwear (crotch, belt, and a woman's chest)
                boolean decent = part.equals("Crotch") || part.equals("Belt") || part.equals("Head")
                        || part.equals("Chest") && pack.sex.equals("f");
                if (!decent && h < 0.5f * wear - 0.08f && pack.skin.raw("zfx/hole/" + part) != null) {
                    // small rips on the lightly worn, wide tears on the worst
                    int open = (int) (255 * (0.5f + 0.5f * wear) * (0.75f + 0.25f * roll(variant, 300 + i)));
                    tears.add(new SurvivorTextures.Layer("zfx/hole/" + part, -1, SurvivorTextures.REVEAL, open));
                    rips.add(new SurvivorTextures.Layer("zfx/hole/" + part, -1, SurvivorTextures.CUT, open));
                }
            }
        }
        body.addAll(mess);
        body.add(new SurvivorTextures.Layer("keep", -1, SurvivorTextures.KEEP));
        body.addAll(paint);
        body.addAll(mess);
        body.addAll(cuts);
        body.addAll(tears);
        for (List<SurvivorTextures.Layer> l : pieces.values()) {
            l.addAll(mess);
            l.addAll(rips);
        }
        // its hair and beard: any of Zomboid's, in a shade of its own
        String colour = HAIR[pick(variant, 3, HAIR.length)];
        SurvivorPack.Style hs = hairHidden || pack.hair.isEmpty() ? null : pack.hair.get(pick(variant, 4, pack.hair.size()));
        SurvivorPack.Style bs = beardHidden || pack.sex.equals("f") || pack.beards.isEmpty() || roll(variant, 5) > 0.45f
                ? null : pack.beards.get(pick(variant, 6, pack.beards.size()));
        List<SurvivorTextures.Layer> bodyLow = new ArrayList<>(body);
        bodyLow.removeAll(cuts);
        Look look = new Look(pack, skin, body, bodyLow, pieces, hs == null ? null : hs.piece(), hs == null ? null : groupTex(pack, hs.piece()),
                bs == null ? null : bs.piece(), bs == null ? null : groupTex(pack, bs.piece()), colour,
                pack.sex + "|" + (chosen == null ? outfit : chosen));
        LOOKS.put(key, look);
        return look;
    }

    private static String groupTex(SurvivorPack pack, String piece) {
        if (piece == null) return null;
        for (NpcSkin.Group g : pack.skin.groups) if (g.piece.equals(piece)) return g.texture;
        return null;
    }

    // ------------------------------------------------------------------ which moves

    /** Its death, as Zomboid has them: shot through the head one way or the other, struck from behind, or the usual. */
    static String deathClip(long death) {
        if (death == 0) return "die";
        int kind = Deaths.kind(death);
        if (kind == Deaths.CRAWL || kind == Deaths.DOWN) return "dead";
        float h = Deaths.heading(death);
        boolean forward = Math.cos(h) > 0;   // the blow went through it from behind
        if (Deaths.part(death) == 0 && Deaths.force(death) >= 0) return forward ? "die_shot_head_fwd" : "die_shot_head_bwd";
        return forward ? "die_behind" : "die";
    }

    private static void choose(Entry e, SurvivorPack pack, Facts f, int variant) {
        SurvivorAnimator a = e.anim;
        if (f.posed != null) {
            a.act(pack, f.posed, 1, false, true);
            return;
        }
        if (f.deathTime > 0) {
            a.act(pack, deathClip(f.death), 1, false, true);
            return;
        }
        if (f.act == CLIMB) {
            a.base(pack, "climb_window", 1);
            return;
        }
        if (f.act == BASH_DOOR || f.act == BASH_WIN) {
            a.base(pack, f.act == BASH_DOOR ? (variant % 3 == 0 ? "door_shoulder" : "door_bang") : "door_claw", 1);
            return;
        }
        if (f.low && f.crawler) {
            a.base(pack, f.speed > 0.15f ? (variant % 2 == 0 ? "crawl" : "crawl2") : "crawl_idle",
                    Math.max(0.6f, Math.min(1.8f, f.speed / CRAWL)));
            e.lowWas = true;
            return;
        }
        if (f.low) {
            a.base(pack, variant % 2 == 0 ? "floor_back" : "floor_front", 1);
            e.lowWas = true;
            return;
        }
        if (e.lowWas) {
            // back on its feet: Zomboid's get-up, from the side it was lying on
            e.lowWas = false;
            a.act(pack, variant % 2 == 0 ? "get_up" : "get_up_front", 1.3f, false, false);
        }
        String clip;
        float rate = 1;
        if (f.speed < 0.2f) {
            clip = f.aggressive ? "lunge_idle" : "idle";
        } else if (oneLeg(f.cuts)) {
            // Zomboid's one-legged lurch (stumble) is a trip onto the floor: on its own it lay down and got up every
            // step. It stands in Zomboid's own stance and ZedHop hops it on the leg it has.
            clip = f.aggressive ? "lunge_idle" : "idle";
        } else if (f.sprinter && f.speed > 2.2f) {
            String[] runs = {"sprint1", "sprint2", "sprint3", "sprint5", "sprint6"};
            clip = runs[Math.floorMod(variant >> 3, runs.length)];
            rate = f.speed / SPRINT;
        } else if (f.speed > 1.3f) {
            clip = "walk_fast";
            rate = f.speed / FAST;
        } else {
            clip = "walk" + (1 + Math.floorMod(variant >> 5, 5));
            rate = f.speed / WALK;
        }
        a.base(pack, clip, Math.max(0.6f, Math.min(1.8f, rate)));
        // its attack: Zomboid's lunge and bite
        if (f.attack > 0 && e.attackWas == 0) a.act(pack, f.speed > 1 ? "lunge" : "bite", 1.3f, true, false);
        e.attackWas = f.attack;
        // a hit only makes it flinch (Josiah's rule for shots); Real Limbs' staggers come in wave 2
        if (f.hurtTime > e.hurtWas && !a.busy()) {
            a.act(pack, (variant & 1) == 0 ? "hit_head_l" : "hit_head_r", 1.6f, true, false);
        }
        e.hurtWas = f.hurtTime;
    }

    private static boolean oneLeg(int cuts) {
        boolean r = (cuts & Dismember.RIGHT_LEG) != 0, l = (cuts & Dismember.LEFT_LEG) != 0;
        return r != l;
    }

    // ------------------------------------------------------------------ posing

    /**
     * This zombie's Zomboid body this frame, or null to leave it to the lighter one (no pack, too far, a texture still
     * being made). id keys its animation: the entity's id.
     */
    public static Drawn pose(int id, Facts f, int level) {
        SurvivorPack pack = packFor(f.variant);
        if (pack == null) return null;
        level = Math.min(level, levels(pack));
        // every zombie wears one of the few looks its outfit shares, near ones too: a horde's textures fit in what's
        // kept, zombies in the same look are drawn together in one go, and a zombie keeps its look as it comes close.
        // Each nearest zombie in its own look made textures nonstop in a horde (351 in 30 s with 600 zombies, Josiah's
        // profile 2026-11-02), each one landing on a full graphics card and freezing frames
        Look look = look(pack, shared(f.variant, f.outfit), f.outfit);
        int size = level == 0 ? TEX : TEX_LOW;
        // the body at its level's size and paint, or while that's being made the other level's (not a frame without it)
        Identifier body = SurvivorTextures.body(pack, level == 0 ? look.paint() : look.paintLow(), size);
        if (body == null) body = level == 0 ? SurvivorTextures.peek(pack, look.paintLow(), TEX_LOW) : SurvivorTextures.peek(pack, look.paint(), TEX);
        Map<String, Identifier> clothes = new HashMap<>();
        boolean pending = body == null;
        for (Map.Entry<String, List<SurvivorTextures.Layer>> p : look.pieces().entrySet()) {
            Identifier t = tex(pack, p.getValue(), size);
            if (t == null) pending = true;
            else clothes.put(p.getKey(), t);
        }
        Entry e = ENTRIES.computeIfAbsent(id, k -> new Entry());
        e.seen = System.currentTimeMillis();
        if (e.pack != pack) {
            e.pack = pack;
            e.pos = null;
            e.buffers[0] = e.buffers[1] = null;
            e.upper = upperBody(pack);
        }
        // every zombie is posed every frame. Posing far ones every other frame (2026-11-02) made the horde move like a
        // flip-book (Josiah 2026-11-04: "like 2d sprite movement made from static images"); the skinning that made it
        // worth it runs on worker threads now (BodyJob)
        long frame = com.zomboidcraft.zombie.Profiler.frameNo;
        boolean reuse = false;
        if (!reuse && (e.pos == null || f.age != e.age)) {
            float dt = Float.isNaN(e.age) ? 0 : Math.max(0, Math.min(0.25f, (f.age - e.age) / 20f));
            if (Float.isNaN(e.age) && f.deathTime > 0) dt = 10;   // a body first seen lying: already down
            e.age = f.age;
            choose(e, pack, f, f.variant);
            e.anim.advance(pack, dt);
            int n = pack.skin.boneNames.length;
            if (e.pos == null) {
                e.pos = new float[n * 3];
                e.turn = new float[n * 4];
            }
            e.anim.pose(pack, e.upper, e.pos, e.turn);
            ZedHop.apply(pack, e.hop, e.pos, e.turn, f.cuts,
                    f.posed == null && f.deathTime <= 0 && f.act == 0 && !f.low, f.speed, dt);
        }
        // forget zombies not seen for 5 s, once a second at most (with a horde over 256 this swept every entry for
        // every zombie drawn, 5% of the frame in Josiah's 600-zombie profile)
        long now = System.currentTimeMillis();
        if (ENTRIES.size() > 256 && now - lastSweep > 1_000) {
            lastSweep = now;
            ENTRIES.values().removeIf(x -> now - x.seen > 5_000);
        }

        Drawn d = new Drawn();
        d.pack = pack;
        d.entry = e;
        d.level = level;
        d.pending = pending;
        d.age = f.age;
        if (reuse) {
            d.skin = e.lastSkin;
            d.reuse = true;
        } else {
            d.skin = skinOf(pack, e.pos, e.turn);
            if (f.cuts != 0) collapse(pack, d.skin, f.cuts);
            e.lastSkin = d.skin;
            e.posedFrame = frame;
            e.posedLevel = level;
            e.posedCuts = f.cuts;
            // not drawn this frame (its textures are being made): nothing skinned to draw again next frame
            if (pending) e.posedFrame = Long.MIN_VALUE;
        }
        d.body = body;
        d.clothes = clothes;
        d.hairPiece = look.hairPiece();
        d.beardPiece = look.beardPiece();
        d.hair = look.hairTex() == null ? null : SurvivorTextures.hair(pack, look.hairTex(), look.colour());
        d.beard = look.beardTex() == null ? null : SurvivorTextures.hair(pack, look.beardTex(), look.colour());
        if (d.hair == null) d.hairPiece = null;
        if (d.beard == null) d.beardPiece = null;
        d.eyes = !ZcConfig.get().look.glowingEyes || (f.cuts & Dismember.HEAD_GONE) != 0 ? 0 : f.deathTime <= 0 ? 1 : Math.max(0, 1 - f.deathTime / 20f);
        d.shiftX = f.shiftX;
        d.shiftY = f.shiftY;
        d.shiftZ = f.shiftZ;
        return d;
    }

    // ------------------------------------------------------------------ for far-off pictures

    /**
     * One zombie's look as its thinned (level 1) meshes, for pictures made from it (a far-off card): the pack, which
     * meshes it wears, each one's texture, and its family (sex and Zomboid outfit: looks alike enough to share a
     * picture sheet far off).
     */
    public record Far(SurvivorPack pack, java.util.function.Predicate<NpcSkin.Group> show,
                      java.util.function.Function<NpcSkin.Group, Identifier> textures, String family, String look) {
    }

    /** The far look of a zombie (its variant and our outfit), or null while its textures are being made. Render thread. */
    public static Far far(int variant, String outfit) {
        SurvivorPack pack = packFor(variant);
        if (pack == null) return null;
        Look look = look(pack, variant, outfit);
        Identifier body = tex(pack, look.paintLow(), TEX_LOW);
        if (body == null) return null;
        Map<String, Identifier> clothes = new HashMap<>();
        for (Map.Entry<String, List<SurvivorTextures.Layer>> p : look.pieces().entrySet()) {
            Identifier t = tex(pack, p.getValue(), TEX_LOW);
            if (t == null) return null;
            clothes.put(p.getKey(), t);
        }
        Identifier hair = look.hairTex() == null ? null : SurvivorTextures.hair(pack, look.hairTex(), look.colour());
        Identifier beard = look.beardTex() == null ? null : SurvivorTextures.hair(pack, look.beardTex(), look.colour());
        String hp = hair == null ? null : look.hairPiece(), bp = beard == null ? null : look.beardPiece();
        int level = hasLow(pack) ? 1 : 0;
        return new Far(pack,
                g -> g.level == level && (g.piece.equals("body") || g.piece.equals(hp) || g.piece.equals(bp) || clothes.containsKey(g.piece)),
                g -> g.piece.equals("body") ? body : g.piece.equals(hp) ? hair : g.piece.equals(bp) ? beard : clothes.get(g.piece),
                look.family(), pack.sex + "|" + variant + "|" + outfit);
    }

    /**
     * The skinning matrices (one per bone, rest to posed, in the body's own metres: y up, +z ahead, feet at 0) for a
     * clip of the pack's at a time (seconds; it loops), as ZedBodies would pose it. NpcSkinDraw.skin's input.
     */
    public static Matrix4f[] farPose(SurvivorPack pack, String clip, float seconds) {
        SurvivorAnimator a = new SurvivorAnimator();
        a.set(pack, clip, Math.max(0, seconds));
        int n = pack.skin.boneNames.length;
        float[] pos = new float[n * 3], turn = new float[n * 4];
        a.pose(pack, upperBody(pack), pos, turn);
        return skinOf(pack, pos, turn);
    }

    // ------------------------------------------------------------------ the far crowd

    /** Figures out in the open past the real zombies (CrowdFx): a few shared poses, a few looks an outfit. */
    private static final int CROWD_PHASES = 4, CROWD_LOOKS = 8;

    private static final class Crowd {
        final Object[][][] buffers = new Object[2][CROWD_PHASES][];
        /** The frame each mesh of each pose was last skinned for: only meshes someone in the crowd wears get skinned. */
        final long[][][] skinned = new long[2][CROWD_PHASES][];
        final float[][] bones = new float[CROWD_PHASES][];
        int flip;
        long frame = -1;
    }

    private static final Map<SurvivorPack, Crowd> CROWDS = new java.util.WeakHashMap<>();

    /** One of CROWD_LOOKS looks an outfit's far zombies share (a variant standing for them). */
    private static int shared(int variant, String outfit) {
        return outfit.hashCode() * 31 + Math.floorMod(variant, CROWD_LOOKS) * 0x9E3779B1;
    }

    /**
     * Draws one crowd figure as Zomboid's thinnest body, standing and swaying (the pose stack already at its feet,
     * turned its way, in world metres). Each pose is skinned once a frame for the whole crowd. Nothing is drawn while
     * its look's textures are being made: never another body in its place. frame: the caller's frame count.
     */
    public static void submitCrowd(int variant, String outfit, float ageTicks, long frame, PoseStack pose,
                                   SubmitNodeCollector collector, int light, double distSq) {
        int v = shared(variant, outfit);
        SurvivorPack pack = packFor(v);
        if (pack == null) return;
        // level 3, the crowd's own lighter mesh, only where a figure is small on screen as seen (a scope's zoom
        // brings it near, and it keeps level 2): Josiah 2026-11-08, "as long as I won't notice through scopes"
        double far = ZcConfig.get().look.crowdLightDistance;
        int level = levels(pack) >= 3 && far > 0 && com.zomboidcraft.client.zombie.body.ZDetail.zoomed(distSq) >= far * far
                ? 3 : Math.min(2, levels(pack));
        Crowd c = CROWDS.computeIfAbsent(pack, p -> new Crowd());
        if (c.frame != frame) {
            c.frame = frame;
            c.flip ^= 1;
            // each pose's bones once a frame; its meshes are skinned as the figures wearing them come up (skinning
            // every mesh the pack has, 4 poses x 229 meshes, was a tenth of the frame, 2026-11-03 horde profile)
            for (int p = 0; p < CROWD_PHASES; p++) {
                if (c.buffers[c.flip][p] == null) {
                    c.buffers[c.flip][p] = new Object[pack.skin.groups.length * 2];
                    c.skinned[c.flip][p] = new long[pack.skin.groups.length];
                    java.util.Arrays.fill(c.skinned[c.flip][p], -1);
                }
                c.bones[p] = NpcSkinDraw.boneMatrices(farPose(pack, "idle", ageTicks / 20f + p * 0.9f));
            }
        }
        Look look = look(pack, v, outfit);
        Identifier body = tex(pack, look.paintLow(), TEX_LOW);
        if (body == null) return;
        Map<String, Identifier> clothes = new HashMap<>();
        for (Map.Entry<String, List<SurvivorTextures.Layer>> p : look.pieces().entrySet()) {
            Identifier t = tex(pack, p.getValue(), TEX_LOW);
            if (t == null) return;
            clothes.put(p.getKey(), t);
        }
        Identifier hair = look.hairTex() == null ? null : SurvivorTextures.hair(pack, look.hairTex(), look.colour());
        String hp = hair == null ? null : look.hairPiece();
        int phase = Math.floorMod(variant >> 4, CROWD_PHASES);
        Object[] buf = c.buffers[c.flip][phase];
        long[] done = c.skinned[c.flip][phase];
        java.util.function.Predicate<NpcSkin.Group> wears = g -> g.piece.equals("body") || g.piece.equals(hp) || clothes.containsKey(g.piece);
        for (int g = 0; g < pack.skin.groups.length; g++) {
            NpcSkin.Group gr = pack.skin.groups[g];
            if (gr.level == level && done[g] != frame && wears.test(gr)) {
                NpcSkinDraw.skinGroup(gr, c.bones[phase], buf, g);
                done[g] = frame;
            }
        }
        NpcSkinDraw.submitWith(pack.skin, null, level, wears,
                g -> g.piece.equals("body") ? body : g.piece.equals(hp) ? hair : clothes.get(g.piece),
                buf, light, OverlayTexture.NO_OVERLAY, pose, collector);
    }

    /** A dead zombie's body (zombie.Corpse), lying as its death left it. */
    public static Drawn corpse(int id, int variant, String outfit, long death, float age, int cuts, int level) {
        return corpse(id, variant, outfit, death, age, cuts, level, null);
    }

    /** A body set out in a pose (Corpse.SAT, SLUMPED, HUNG): sat against a wall, slumped on its knees, hanging limp. */
    public static String posedClip(int pose) {
        return switch (pose) {
            case 1 -> "wall_dead";
            case 2 -> "knees_in";
            case 3 -> "idle";
            default -> null;
        };
    }

    public static Drawn corpse(int id, int variant, String outfit, long death, float age, int cuts, int level, String posed) {
        Facts f = new Facts();
        f.posed = posed;
        f.cuts = cuts;
        f.variant = variant;
        f.outfit = outfit;
        f.death = death;
        f.deathTime = 1000;
        f.age = age;
        Drawn d = pose(id, f, level);
        if (d != null) d.eyes = 0;
        return d;
    }

    /** Facts for a walker, filled from the entity (the rest from the renderer). */
    public static void fill(Facts f, Entity zombie) {
        double dx = zombie.getX() - zombie.xo, dz = zombie.getZ() - zombie.zo;
        f.speed = (float) Math.sqrt(dx * dx + dz * dz) * 20f;
        f.sprinter = ZombieTypes.isSprinter(zombie);
    }

    /** The bone a cut takes off at (with everything below it), by Dismember's bits: elbow, shoulder, knee, hip; the head. */
    private static final int[] CUT_BITS = {Dismember.EL_R, Dismember.EL_L, Dismember.SH_R, Dismember.SH_L, Dismember.KN_R,
            Dismember.KN_L, Dismember.HIP_R, Dismember.HIP_L, Dismember.HEAD_GONE};
    private static final String[] CUT_BONES = {"Bip01_R_Forearm", "Bip01_L_Forearm", "Bip01_R_UpperArm", "Bip01_L_UpperArm",
            "Bip01_R_Calf", "Bip01_L_Calf", "Bip01_R_Thigh", "Bip01_L_Thigh", "Bip01_Head"};
    private static final Map<SurvivorPack, int[]> CUT_AT = new java.util.WeakHashMap<>();

    /**
     * A lost limb is gone from the Zomboid body: every bone from the cut down shrinks into the joint it came off at, so
     * the skin and clothes on it close over into a stump there.
     */
    private static void collapse(SurvivorPack pack, Matrix4f[] skin, int cuts) {
        int[] at = CUT_AT.computeIfAbsent(pack, p -> {
            int[] b = new int[CUT_BONES.length];
            for (int i = 0; i < b.length; i++) b[i] = p.skin.bone(CUT_BONES[i]);
            return b;
        });
        int n = skin.length;
        for (int i = 0; i < CUT_BITS.length; i++) {
            int bone = at[i];
            if ((cuts & CUT_BITS[i]) == 0 || bone < 0) continue;
            // where the joint is now: its rest place carried by its own skinning
            Vector3f joint = pack.bind[bone].getTranslation(new Vector3f());
            skin[bone].transformPosition(joint);
            Matrix4f shrink = new Matrix4f().translation(joint).scale(0.02f).translate(-joint.x, -joint.y, -joint.z);
            Matrix4f into = new Matrix4f(shrink).mul(skin[bone]);
            for (int b = 0; b < n; b++) {
                if (below(pack, b, bone)) skin[b] = into;
            }
        }
    }

    private static boolean below(SurvivorPack pack, int b, int of) {
        for (int p = b; p >= 0; p = pack.skin.parent[p]) if (p == of) return true;
        return false;
    }

    private static boolean[] upperBody(SurvivorPack pack) {
        int n = pack.skin.boneNames.length;
        boolean[] up = new boolean[n];
        for (int b = 0; b < n; b++) {
            for (int p = b; p >= 0 && !up[b]; p = pack.skin.parent[p]) {
                if (p == pack.spine1) up[b] = true;
            }
        }
        return up;
    }

    private static Matrix4f[] skinOf(SurvivorPack pack, float[] pos, float[] turn) {
        int n = pack.skin.boneNames.length;
        Matrix4f[] out = new Matrix4f[n];
        Quaternionf q = new Quaternionf();
        for (int b = 0; b < n; b++) {
            q.set(turn[b * 4], turn[b * 4 + 1], turn[b * 4 + 2], turn[b * 4 + 3]);
            out[b] = new Matrix4f().translationRotate(pos[b * 3], pos[b * 3 + 1], pos[b * 3 + 2], q).mul(pack.skin.inverse[b]);
        }
        return out;
    }

    private static Object[] buffers(Drawn d) {
        Entry e = d.entry;
        if (e.submitAge == d.age) e.buffer = 1 - e.buffer;
        e.submitAge = d.age;
        if (e.buffers[e.buffer] == null) e.buffers[e.buffer] = new Object[d.pack.skin.groups.length * 2];
        return e.buffers[e.buffer];
    }

    // ------------------------------------------------------------------ drawing

    /** Each eye in the body's rest pose (metres; +z ahead): its centre and how wide it glows. */
    private static final float EYE_X = 0.033f, EYE_Y_M = 1.695f, EYE_Y_F = 1.69f, EYE_Z_M = 0.061f, EYE_Z_F = 0.057f, EYE_R = 0.014f;

    /**
     * Draws it, where the walker's (or corpse's) model stands in the pose given: back out of the model's flip and up
     * at its hips into the body's own metres, feet on the ground.
     */
    public static void submit(Drawn d, PoseStack pose, SubmitNodeCollector collector, int light, int overlay) {
        if (d.pending) return;
        pose.pushPose();
        pose.translate(0, 1.501f, 0);
        pose.scale(-1, -1, 1);
        pose.mulPose(new Matrix4f().rotationY((float) Math.PI));
        if (d.shiftX != 0 || d.shiftY != 0 || d.shiftZ != 0) pose.translate(d.shiftX, d.shiftY, d.shiftZ);
        Object[] buf = buffers(d);
        Entry e = d.entry;
        // on a far zombie's off frame its buffers still hold last frame's skinned body: drawn as they are (no bones)
        boolean again = d.reuse && e.buffer == e.skinnedBuffer;
        if (!again) e.skinnedBuffer = e.buffer;
        NpcSkinDraw.submitWith(d.pack.skin, again ? null : d.skin, d.level,
                g -> g.piece.equals("body") || g.piece.equals(d.hairPiece) || g.piece.equals(d.beardPiece) || d.clothes.containsKey(g.piece),
                g -> g.piece.equals("body") ? d.body : g.piece.equals(d.hairPiece) ? d.hair
                        : g.piece.equals(d.beardPiece) ? d.beard : d.clothes.get(g.piece),
                buf, light, overlay, pose, collector);
        int head = d.pack.head;
        if (d.eyes > 0.01f && head >= 0) {
            Matrix4f m = d.skin[head];
            boolean f = d.pack.sex.equals("f");
            float y = f ? EYE_Y_F : EYE_Y_M, z = (f ? EYE_Z_F : EYE_Z_M) + 0.003f;
            int a = Math.round(d.eyes * 255);
            int colour = a << 24 | 0xFFFFFF;
            collector.submitCustomGeometry(pose, RenderTypes.entityTranslucentEmissive(EYE), (p, out) -> {
                Vector3f n = m.transformDirection(new Vector3f(0, 0, 1)).normalize();
                for (int s = -1; s <= 1; s += 2) eye(m, p, out, s * EYE_X, y, z, n, colour);
            });
        }
        pose.popPose();
    }

    /** A glow card's corners, wound both ways so it shows whichever way the flips leave it. */
    private static final float[][] CORNERS = {{-1, 1, 0, 0}, {-1, -1, 0, 1}, {1, -1, 1, 1}, {1, 1, 1, 0},
            {1, 1, 1, 0}, {1, -1, 1, 1}, {-1, -1, 0, 1}, {-1, 1, 0, 0}};

    private static void eye(Matrix4f m, PoseStack.Pose p, VertexConsumer out, float cx, float cy, float cz, Vector3f n, int colour) {
        Vector3f v = new Vector3f();
        for (float[] c : CORNERS) {
            m.transformPosition(v.set(cx + c[0] * EYE_R, cy + c[1] * EYE_R, cz), v);
            out.addVertex(p, v.x, v.y, v.z).setColor(colour).setUv(c[2], c[3]).setOverlay(OverlayTexture.NO_OVERLAY)
                    .setLight(FULL_BRIGHT).setNormal(p, n.x, n.y, n.z);
        }
    }
}
