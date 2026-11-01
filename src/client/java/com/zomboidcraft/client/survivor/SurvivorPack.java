package com.zomboidcraft.client.survivor;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.zomboidcraft.ZomboidCraft;
import com.zomboidcraft.client.npc.NpcSkin;
import com.zomboidcraft.clothing.Grooming;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * One survivor pack (tools/pz/survivor_pack.py, local to this PC): Zomboid's body for women ("f") or men ("m") with
 * Tomb's mesh, every skin, face, hair, beard, stubble, body hair and make-up the mods have, and Zomboid's moves. Its
 * "catalog" says what's in it, with Zomboid's own haircut rules: each style's length (level 1 to 3), what it can be
 * trimmed to, which styles hair grows into, and the hat versions.
 * <p>
 * Lengths: Grooming counts 0 shaved, 1 stubble, 2 short, 3 medium, 4 long; Zomboid's level L is length L + 1.
 */
public final class SurvivorPack {
    public record Option(String id, String name) {
    }

    /** A hair or beard style, by Zomboid's name. */
    public record Style(String id, String name, String piece, boolean goth, int level, List<String> trim, boolean grows,
                        boolean choose, Map<String, String> hats) {
        /** Its length in Grooming's terms. */
        public int length() {
            return level + 1;
        }

        /** How Grooming stores it. */
        public String key() {
            return "pz:" + id + "#" + length();
        }

        public boolean curly() {
            return id.toLowerCase(Locale.ROOT).contains("curly");
        }
    }

    /** A skin pack: its name, which set of faces it wears, and the builds each skin tone comes in. */
    public record Pack(String id, String name, String faces, Map<Integer, List<String>> tones) {
    }

    public record Faces(List<String> names, int tones) {
    }

    /**
     * One of Zomboid's clothingItems: its model (a piece, or null when it's only paint on the skin) and the textures it
     * comes in, the body textures of a painted-on one, the skin it hides (a mask texture), whether Zomboid gives it a
     * random tint or hue, and its hat category (which hair goes under it).
     */
    public record Cloth(String id, String piece, List<String> textures, List<String> base, String mask, boolean tint,
                        boolean hue, String hat) {
    }

    public final String sex;
    public final NpcSkin skin;
    public final float height;
    public final List<Pack> packs = new ArrayList<>();
    public final Map<String, Faces> faces = new LinkedHashMap<>();
    public final List<Style> hair = new ArrayList<>(), beards = new ArrayList<>();
    public final Map<String, Style> hairById = new HashMap<>(), beardById = new HashMap<>();
    public final Map<String, Cloth> clothes = new HashMap<>();
    public final List<Option> stubble = new ArrayList<>(), bodyHair = new ArrayList<>(), eyes = new ArrayList<>(),
            lips = new ArrayList<>(), paint = new ArrayList<>();

    /** Bones the animator and the hands use (-1 if missing). */
    public final int head, neck, spine1, dummy, root, translation, pelvis;
    public final int[] upper = new int[2], fore = new int[2], hand = new int[2], clavicle = new int[2];
    /** At rest: each bone's place in the model (inverse of the bind). */
    public final Matrix4f[] bind;
    /**
     * Per hand (0 right, 1 left): where a held thing sits in the hand's own frame, and how the hand's frame turns
     * into the frame Minecraft holds items in (+y ahead, +z up the forearm, +x to her right), found from her idle.
     */
    public final Vector3f[] grip = {new Vector3f(), new Vector3f()};
    public final Quaternionf[] gripTurn = {new Quaternionf(), new Quaternionf()};
    /** Her pelvis at rest (for seated clips that sit her on the seat). */
    public final Vector3f pelvisRest = new Vector3f();

    private static final Map<String, SurvivorPack> PACKS = new HashMap<>();

    /** The pack for "f" or "m", or null while it loads or if this PC hasn't got it. Render thread. */
    public static SurvivorPack get(String sex) {
        SurvivorPack p = PACKS.get(sex);
        if (p != null) return p;
        NpcSkin skin = NpcSkin.get(model(sex));
        if (skin == null) return null;
        try {
            p = new SurvivorPack(sex, skin);
        } catch (RuntimeException e) {
            ZomboidCraft.LOGGER.error("Bad survivor pack {}", model(sex), e);
            return null;
        }
        PACKS.put(sex, p);
        return p;
    }

    /** Whether this PC has either pack (they may still be loading). */
    public static boolean installed() {
        return NpcSkin.exists(model("f")) || NpcSkin.exists(model("m"));
    }

    public static boolean installed(String sex) {
        return NpcSkin.exists(model(sex));
    }

    public static String model(String sex) {
        return "pz_survivor_" + ("m".equals(sex) ? "m" : "f");
    }

    private SurvivorPack(String sex, NpcSkin skin) {
        this.sex = sex;
        this.skin = skin;
        byte[] raw = skin.raw("catalog");
        JsonObject cat = raw == null ? new JsonObject() : JsonParser.parseString(new String(raw, StandardCharsets.UTF_8)).getAsJsonObject();
        height = cat.has("height") ? cat.get("height").getAsFloat() : 1.75f;
        for (JsonElement e : arr(cat, "packs")) {
            JsonObject o = e.getAsJsonObject();
            Map<Integer, List<String>> tones = new LinkedHashMap<>();
            for (Map.Entry<String, JsonElement> t : o.getAsJsonObject("tones").entrySet()) {
                List<String> builds = new ArrayList<>();
                for (JsonElement b : t.getValue().getAsJsonArray()) {
                    String key = "skin/" + o.get("id").getAsString() + "/" + t.getKey() + "/" + b.getAsString();
                    if (skin.raw(key) != null) builds.add(b.getAsString());
                }
                if (!builds.isEmpty()) tones.put(Integer.parseInt(t.getKey()), builds);
            }
            if (!tones.isEmpty()) packs.add(new Pack(o.get("id").getAsString(), o.get("name").getAsString(), o.get("faces").getAsString(), tones));
        }
        if (cat.has("faces")) {
            for (Map.Entry<String, JsonElement> f : cat.getAsJsonObject("faces").entrySet()) {
                JsonObject o = f.getValue().getAsJsonObject();
                List<String> names = new ArrayList<>();
                for (JsonElement n : o.getAsJsonArray("names")) names.add(n.getAsString());
                faces.put(f.getKey(), new Faces(names, o.get("tones").getAsInt()));
            }
        }
        styles(arr(cat, "hair"), hair, hairById);
        styles(arr(cat, "beard"), beards, beardById);
        options(arr(cat, "stubble"), stubble);
        options(arr(cat, "bodyhair"), bodyHair);
        JsonObject makeup = cat.has("makeup") ? cat.getAsJsonObject("makeup") : new JsonObject();
        options(arr(makeup, "eyes"), eyes);
        options(arr(makeup, "lips"), lips);
        options(arr(makeup, "face"), paint);
        if (cat.has("clothes")) {
            for (Map.Entry<String, JsonElement> c : cat.getAsJsonObject("clothes").entrySet()) {
                JsonObject o = c.getValue().getAsJsonObject();
                clothes.put(c.getKey(), new Cloth(c.getKey(), o.has("piece") ? o.get("piece").getAsString() : null,
                        strings(arr(o, "tex")), strings(arr(o, "base")), o.has("mask") ? o.get("mask").getAsString() : null,
                        o.has("tint") && o.get("tint").getAsBoolean(), o.has("hue") && o.get("hue").getAsBoolean(),
                        o.has("hat") ? o.get("hat").getAsString() : null));
            }
        }

        head = skin.bone("Bip01_Head");
        neck = skin.bone("Bip01_Neck");
        spine1 = skin.bone("Bip01_Spine1");
        dummy = skin.bone("Dummy01");
        root = skin.bone("Bip01");
        translation = skin.bone("Translation_Data");
        pelvis = skin.bone("Bip01_Pelvis");
        String[] side = {"R", "L"};
        for (int s = 0; s < 2; s++) {
            clavicle[s] = skin.bone("Bip01_" + side[s] + "_Clavicle");
            upper[s] = skin.bone("Bip01_" + side[s] + "_UpperArm");
            fore[s] = skin.bone("Bip01_" + side[s] + "_Forearm");
            hand[s] = skin.bone("Bip01_" + side[s] + "_Hand");
        }
        bind = new Matrix4f[skin.inverse.length];
        for (int i = 0; i < bind.length; i++) bind[i] = new Matrix4f(skin.inverse[i]).invertAffine();
        if (pelvis >= 0) bind[pelvis].getTranslation(pelvisRest);
        grips();
    }

    /**
     * From the first frame of her idle: the palm a few centimetres past the wrist, and the turn from the hand's frame
     * to the item frame there (up the forearm, ahead, and to her right), so a held thing turns with her wrist.
     */
    private void grips() {
        NpcSkin.Clip idle = skin.clips.get("idle");
        if (idle == null || hand[0] < 0 || fore[0] < 0) return;
        for (int s = 0; s < 2; s++) {
            Vector3f h = at(idle, hand[s]), f = at(idle, fore[s]);
            Quaternionf hq = turn(idle, hand[s]);
            Vector3f up = new Vector3f(f).sub(h).normalize();
            Vector3f ahead = new Vector3f(0, 0, 1);
            ahead.sub(new Vector3f(up).mul(ahead.dot(up))).normalize();
            Vector3f right = new Vector3f(ahead).cross(up).normalize();   // x = y × z
            // item frame columns: x right, y ahead, z up
            Quaternionf item = new Quaternionf().setFromNormalized(new org.joml.Matrix3f(right, ahead, up));
            gripTurn[s].set(hq).conjugate().mul(item);
            Vector3f palm = new Vector3f(h).sub(f).normalize().mul(0.085f).add(h);
            hq.conjugate(new Quaternionf()).transform(palm.sub(h), grip[s]);
        }
    }

    private static Vector3f at(NpcSkin.Clip c, int bone) {
        return new Vector3f(c.at()[bone * 3], c.at()[bone * 3 + 1], c.at()[bone * 3 + 2]);
    }

    private static Quaternionf turn(NpcSkin.Clip c, int bone) {
        return new Quaternionf(c.turn()[bone * 4], c.turn()[bone * 4 + 1], c.turn()[bone * 4 + 2], c.turn()[bone * 4 + 3]);
    }

    private static JsonArray arr(JsonObject o, String key) {
        return o.has(key) && o.get(key).isJsonArray() ? o.getAsJsonArray(key) : new JsonArray();
    }

    private static List<String> strings(JsonArray a) {
        List<String> out = new ArrayList<>();
        for (JsonElement e : a) out.add(e.getAsString());
        return List.copyOf(out);
    }

    private static void options(JsonArray a, List<Option> out) {
        for (JsonElement e : a) {
            JsonObject o = e.getAsJsonObject();
            out.add(new Option(o.get("id").getAsString(), o.get("name").getAsString()));
        }
    }

    private void styles(JsonArray a, List<Style> out, Map<String, Style> byId) {
        for (JsonElement e : a) {
            JsonObject o = e.getAsJsonObject();
            List<String> trim = new ArrayList<>();
            for (JsonElement t : arr(o, "trim")) trim.add(t.getAsString());
            Map<String, String> hats = new HashMap<>();
            if (o.has("alt")) for (Map.Entry<String, JsonElement> h : o.getAsJsonObject("alt").entrySet()) hats.put(h.getKey(), h.getValue().getAsString());
            Style s = new Style(o.get("id").getAsString(), o.get("name").getAsString(), o.get("piece").getAsString(),
                    o.has("goth") && o.get("goth").getAsBoolean(), o.has("level") ? o.get("level").getAsInt() : 1, List.copyOf(trim),
                    o.has("grow") && o.get("grow").getAsBoolean(), !o.has("choose") || o.get("choose").getAsBoolean(), hats);
            out.add(s);
            byId.put(s.id(), s);
        }
    }

    // ------------------------------------------------------------------ what Grooming's names mean here

    /**
     * The hair style showing for what Grooming says (its own names once hair has grown a length: those grow into
     * Zomboid's "grow" style of that length, curly if the cut was curly), or null for none (shaved, or stubble).
     */
    public Style hairFor(String showing, String cut) {
        return styleFor(true, showing, cut);
    }

    public Style beardFor(String showing, String cut) {
        return styleFor(false, showing, cut);
    }

    private Style styleFor(boolean isHair, String showing, String cut) {
        if (showing == null) return null;
        Map<String, Style> byId = isHair ? hairById : beardById;
        if (showing.startsWith("pz:")) {
            int at = showing.lastIndexOf('#');
            Style s = byId.get(showing.substring(3, at < 0 ? showing.length() : at));
            if (s != null) return s;
        }
        Integer length = Grooming.rank(isHair, showing);
        if (length == null || length <= 1) return null;
        return grown(isHair, length - 1, cut != null && cut.toLowerCase(Locale.ROOT).contains("curly"));
    }

    /** Zomboid's style that hair (or a beard) grows into at this level. */
    public Style grown(boolean isHair, int level, boolean curly) {
        Style any = null, first = null;
        for (Style s : isHair ? hair : beards) {
            if (s.level() != level || !s.choose()) continue;
            if (first == null) first = s;
            if (!s.grows()) continue;
            if (s.curly() == curly) return s;
            if (any == null) any = s;
        }
        return any != null ? any : first;
    }

    /** Grooming's length now (0 to 4) for what it says is showing. */
    public static int length(boolean isHair, String showing) {
        Integer r = Grooming.rank(isHair, showing);
        return r == null ? (isHair ? 2 : 0) : r;
    }

    public Pack pack(String id) {
        for (Pack p : packs) if (p.id().equals(id)) return p;
        return packs.isEmpty() ? null : packs.get(0);
    }

    /** The texture key of a face, or null if there isn't one. */
    public String faceKey(Pack pack, String face, int tone) {
        Faces f = pack == null ? null : faces.get(pack.faces());
        if (f == null || !f.names().contains(face)) return null;
        String key = "face/" + pack.faces() + "/" + face + "/" + (f.tones() > 1 ? tone : 1);
        return skin.raw(key) != null ? key : null;
    }

    /** The skin texture key for these choices, falling back to the plain build and then the pack's first tone. */
    public String skinKey(Pack pack, int tone, String build, boolean hairy) {
        if (pack == null) return null;
        List<String> builds = pack.tones().get(tone);
        if (builds == null) {
            tone = pack.tones().keySet().iterator().next();
            builds = pack.tones().get(tone);
        }
        if (!builds.contains(build)) build = builds.get(0);
        String base = "skin/" + pack.id() + "/" + tone + "/" + build;
        // the hairy men's skins come in the plain build only
        if (hairy && skin.raw("skin/" + pack.id() + "/" + tone + "/normal/hairy") != null) return "skin/" + pack.id() + "/" + tone + "/normal/hairy";
        return base;
    }
}
