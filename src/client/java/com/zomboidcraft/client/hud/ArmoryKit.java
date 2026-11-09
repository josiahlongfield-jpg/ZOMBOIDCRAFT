package com.zomboidcraft.client.hud;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.zomboidcraft.ZomboidCraft;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jetbrains.annotations.Nullable;

/**
 * What the Armory offers, from the armory kit on this PC ({@code config/zomboidcraft/hud/armory.zip}, unpacked by
 * {@link HudKit}): Reach's armour pieces (each the model regions it swaps), its 30 colours, visors, armour effects,
 * MCC's Reach poses, emblem layers and nameplates. Names and art are MCC's, so the kit never goes in the repo or jar.
 */
final class ArmoryKit {
    static final String KIT = "armory";

    record Item(String name, String desc, List<String[]> pairs) {}

    record Slot(String key, String label, List<Item> items) {}

    record Named(String name, String desc) {}

    record Visor(String name, String desc, float r, float g, float b) {}

    record Colour(String name, float r, float g, float b) {
        int argb() {
            return HudKit.argb(255, Math.round(r * 255), Math.round(g * 255), Math.round(b * 255));
        }
    }

    record Pose(String clip, String name, @Nullable String gun) {}

    record Emblem(String name, String primary, @Nullable String secondary) {}

    record Plate(String name, String file) {}

    record Soldier(String key, String name, Map<String, String> outfit, int primary, int secondary, int visor) {}

    final Map<String, String> base = new LinkedHashMap<>();
    final Map<String, Map<String, List<String>>> regions = new LinkedHashMap<>();
    final List<Soldier> soldiers = new ArrayList<>();
    final List<Slot> slots = new ArrayList<>();
    final List<Visor> visors = new ArrayList<>();
    final List<Named> effects = new ArrayList<>();
    final List<Colour> colours = new ArrayList<>();
    final List<Pose> poses = new ArrayList<>();
    final List<Emblem> foregrounds = new ArrayList<>(), backgrounds = new ArrayList<>();
    final List<Plate> plates = new ArrayList<>();
    final List<String> voices = new ArrayList<>();
    final @Nullable Path plateDir;

    private static @Nullable ArmoryKit loaded;
    private static long tried;

    /** The kit, or null when it isn't on this computer (looked for again every ten seconds). */
    static @Nullable ArmoryKit get() {
        if (loaded != null) return loaded;
        long now = System.currentTimeMillis();
        if (now - tried < 10_000) return null;
        tried = now;
        if (HudKit.get(KIT) == null) return null;
        Path file = HudKit.ROOT.resolve(KIT).resolve("kit.json");
        try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            loaded = new ArmoryKit(JsonParser.parseReader(r).getAsJsonObject());
        } catch (Exception e) {
            ZomboidCraft.LOGGER.warn("Couldn't read the Armory kit at {}", file, e);
        }
        return loaded;
    }

    private ArmoryKit(JsonObject o) {
        for (var e : o.getAsJsonObject("base").entrySet()) base.put(e.getKey(), e.getValue().getAsString());
        for (var r : o.getAsJsonObject("regions").entrySet()) {
            Map<String, List<String>> perms = new LinkedHashMap<>();
            for (var p : r.getValue().getAsJsonObject().entrySet()) perms.put(p.getKey(), strings(p.getValue().getAsJsonArray()));
            regions.put(r.getKey(), perms);
        }
        for (JsonElement e : o.getAsJsonArray("soldiers")) {
            JsonObject s = e.getAsJsonObject();
            Map<String, String> outfit = new LinkedHashMap<>();
            for (var p : s.getAsJsonObject("outfit").entrySet()) outfit.put(p.getKey(), p.getValue().getAsString());
            soldiers.add(new Soldier(s.get("key").getAsString(), s.get("name").getAsString(), outfit,
                    s.get("primary").getAsInt(), s.get("secondary").getAsInt(), s.get("visor").getAsInt()));
        }
        for (JsonElement e : o.getAsJsonArray("slots")) {
            JsonObject s = e.getAsJsonObject();
            List<Item> items = new ArrayList<>();
            for (JsonElement i : s.getAsJsonArray("items")) {
                JsonObject it = i.getAsJsonObject();
                List<String[]> pairs = new ArrayList<>();
                for (JsonElement p : it.getAsJsonArray("pairs")) pairs.add(new String[] {p.getAsJsonArray().get(0).getAsString(), p.getAsJsonArray().get(1).getAsString()});
                items.add(new Item(it.get("name").getAsString(), str(it, "desc"), pairs));
            }
            slots.add(new Slot(s.get("key").getAsString(), s.get("label").getAsString(), items));
        }
        for (JsonElement e : o.getAsJsonArray("visors")) {
            JsonObject v = e.getAsJsonObject();
            JsonArray t = v.getAsJsonArray("tint");
            visors.add(new Visor(v.get("name").getAsString(), str(v, "desc"), t.get(0).getAsFloat(), t.get(1).getAsFloat(), t.get(2).getAsFloat()));
        }
        for (JsonElement e : o.getAsJsonArray("effects")) effects.add(new Named(e.getAsJsonObject().get("name").getAsString(), str(e.getAsJsonObject(), "desc")));
        for (JsonElement e : o.getAsJsonArray("colours")) {
            JsonObject c = e.getAsJsonObject();
            JsonArray rgb = c.getAsJsonArray("rgb");
            colours.add(new Colour(c.get("name").getAsString(), rgb.get(0).getAsFloat(), rgb.get(1).getAsFloat(), rgb.get(2).getAsFloat()));
        }
        for (JsonElement e : o.getAsJsonArray("poses")) {
            JsonObject p = e.getAsJsonObject();
            poses.add(new Pose(p.get("clip").getAsString(), p.get("name").getAsString(), p.has("gun") && !p.get("gun").isJsonNull() ? p.get("gun").getAsString() : null));
        }
        JsonObject em = o.getAsJsonObject("emblems");
        for (JsonElement e : em.getAsJsonArray("foreground")) {
            JsonObject f = e.getAsJsonObject();
            foregrounds.add(new Emblem(f.get("name").getAsString(), f.get("p").getAsString(), f.has("s") ? f.get("s").getAsString() : null));
        }
        for (JsonElement e : em.getAsJsonArray("background")) {
            JsonObject b = e.getAsJsonObject();
            backgrounds.add(new Emblem(b.get("name").getAsString(), b.get("img").getAsString(), null));
        }
        for (JsonElement e : o.getAsJsonArray("nameplates")) {
            JsonObject p = e.getAsJsonObject();
            plates.add(new Plate(p.get("name").getAsString(), p.get("img").getAsString()));
        }
        voices.addAll(strings(o.getAsJsonArray("voices")));
        Path dir = null;
        try {
            if (o.has("nameplateDir")) dir = Path.of(o.get("nameplateDir").getAsString());
        } catch (Exception ignored) {
            // a bad path: nameplates show their names only
        }
        plateDir = dir;
    }

    private static String str(JsonObject o, String k) {
        return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : "";
    }

    private static List<String> strings(JsonArray a) {
        List<String> out = new ArrayList<>();
        for (JsonElement e : a) out.add(e.getAsString());
        return out;
    }

    /** Every model piece an outfit (region -> permutation) shows. */
    Set<String> nodes(Map<String, String> outfit) {
        Set<String> out = new HashSet<>();
        outfit.forEach((region, perm) -> {
            Map<String, List<String>> perms = regions.get(region);
            List<String> n = perms == null ? null : perms.get(perm);
            if (n != null) out.addAll(n);
        });
        return out;
    }
}
