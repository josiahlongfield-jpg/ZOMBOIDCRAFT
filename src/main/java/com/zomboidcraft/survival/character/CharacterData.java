package com.zomboidcraft.survival.character;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.zomboidcraft.ZomboidCraft;
import com.zomboidcraft.skills.Skill;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Zomboid's character creation data: the occupations and traits, read from data/zomboidcraft/character/. Both sides
 * read the same files from the jar, so the screen and the server agree on every cost.
 * <p>
 * Zomboid's signs: an occupation's points go onto your budget (Unemployed +8, Veteran -8); a trait's cost comes off it
 * (positive traits cost points, negative ones give them back). Skill bonuses are levels: added to 0 for most skills,
 * to 5 for the passive Fitness and Strength.
 */
public final class CharacterData {
    public record Occupation(String id, String name, int points, Map<Skill, Integer> skills, List<String> traits, String description) {
    }

    public record Trait(String id, String name, int cost, Map<Skill, Integer> skills, List<String> excludes, String description,
                        boolean occupationOnly) {
        public boolean positive() {
            return cost > 0;
        }

        public boolean negative() {
            return cost < 0;
        }
    }

    private static Map<String, Occupation> occupations;
    private static Map<String, Trait> traits;

    public static Map<String, Occupation> occupations() {
        if (occupations == null) load();
        return occupations;
    }

    public static Map<String, Trait> traits() {
        if (traits == null) load();
        return traits;
    }

    public static Occupation occupation(String id) {
        return occupations().get(id);
    }

    public static Trait trait(String id) {
        return traits().get(id);
    }

    public static final String UNEMPLOYED = "unemployed";

    private static synchronized void load() {
        Map<String, Occupation> occ = new LinkedHashMap<>();
        Map<String, Trait> tr = new LinkedHashMap<>();
        try {
            for (JsonElement e : read("occupations.json").getAsJsonArray("occupations")) {
                JsonObject o = e.getAsJsonObject();
                occ.put(o.get("id").getAsString(), new Occupation(o.get("id").getAsString(), o.get("name").getAsString(),
                        o.get("points").getAsInt(), skills(o), strings(o, "traits"), o.get("description").getAsString()));
            }
            for (JsonElement e : read("traits.json").getAsJsonArray("traits")) {
                JsonObject o = e.getAsJsonObject();
                tr.put(o.get("id").getAsString(), new Trait(o.get("id").getAsString(), o.get("name").getAsString(),
                        o.get("cost").getAsInt(), skills(o), strings(o, "excludes"), o.get("description").getAsString(),
                        o.has("occupationOnly") && o.get("occupationOnly").getAsBoolean()));
            }
        } catch (Exception ex) {
            ZomboidCraft.LOGGER.error("Couldn't read the occupations and traits", ex);
        }
        if (!occ.containsKey(UNEMPLOYED)) {
            occ.put(UNEMPLOYED, new Occupation(UNEMPLOYED, "Unemployed", 8, Map.of(), List.of(), ""));
        }
        occupations = Collections.unmodifiableMap(occ);
        traits = Collections.unmodifiableMap(tr);
    }

    private static JsonObject read(String file) throws Exception {
        try (InputStream in = CharacterData.class.getResourceAsStream("/data/zomboidcraft/character/" + file)) {
            if (in == null) throw new IllegalStateException("missing " + file);
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    private static Map<Skill, Integer> skills(JsonObject o) {
        Map<Skill, Integer> out = new EnumMap<>(Skill.class);
        if (!o.has("skills")) return out;
        for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("skills").entrySet()) {
            try {
                out.put(Skill.valueOf(e.getKey()), e.getValue().getAsInt());
            } catch (IllegalArgumentException ignored) {
                ZomboidCraft.LOGGER.warn("Character data names an unknown skill: {}", e.getKey());
            }
        }
        return Collections.unmodifiableMap(out);
    }

    private static List<String> strings(JsonObject o, String key) {
        List<String> out = new ArrayList<>();
        if (o.has(key)) for (JsonElement e : o.getAsJsonArray(key)) out.add(e.getAsString());
        return Collections.unmodifiableList(out);
    }

    private CharacterData() {
    }
}
