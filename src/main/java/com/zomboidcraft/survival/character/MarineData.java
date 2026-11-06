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
 * Marine character creation (data/zomboidcraft/character/marine.json): the training every marine has, the specialist
 * roles built from Zomboid's job boosts, and the short list of traits a marine picks from. Survivor NPCs keep Zomboid's
 * civilian occupations ({@link CharacterData}); only players are marines.
 */
public final class MarineData {
    /** Profiles store a marine's role as this prefix plus the role id, so they never clash with a civilian job. */
    public static final String PREFIX = "marine:";

    public record Role(String id, String name, String code, String from, Map<Skill, Integer> skills, List<String> traits,
                       String description) {
    }

    private static Map<Skill, Integer> base;
    private static Map<String, Role> roles;
    private static List<String> traits;
    private static int picks = 2;

    public static Map<Skill, Integer> base() {
        if (roles == null) load();
        return base;
    }

    public static Map<String, Role> roles() {
        if (roles == null) load();
        return roles;
    }

    /** The traits a marine may pick, in screen order. */
    public static List<String> traits() {
        if (roles == null) load();
        return traits;
    }

    public static int picks() {
        if (roles == null) load();
        return picks;
    }

    /** The role a profile's occupation names, or null when it's a civilian job. */
    public static Role role(String occupation) {
        if (occupation == null || !occupation.startsWith(PREFIX)) return null;
        return roles().get(occupation.substring(PREFIX.length()));
    }

    public static boolean marine(String occupation) {
        return role(occupation) != null;
    }

    private static synchronized void load() {
        Map<Skill, Integer> b = new EnumMap<>(Skill.class);
        Map<String, Role> r = new LinkedHashMap<>();
        List<String> t = new ArrayList<>();
        try (InputStream in = MarineData.class.getResourceAsStream("/data/zomboidcraft/character/marine.json")) {
            if (in == null) throw new IllegalStateException("missing marine.json");
            JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            b.putAll(skills(root.getAsJsonObject("base")));
            if (root.has("picks")) picks = root.get("picks").getAsInt();
            for (JsonElement e : root.getAsJsonArray("traits")) t.add(e.getAsString());
            for (JsonElement e : root.getAsJsonArray("roles")) {
                JsonObject o = e.getAsJsonObject();
                List<String> rt = new ArrayList<>();
                for (JsonElement x : o.getAsJsonArray("traits")) rt.add(x.getAsString());
                r.put(o.get("id").getAsString(), new Role(o.get("id").getAsString(), o.get("name").getAsString(),
                        o.get("code").getAsString(), o.get("from").getAsString(), Collections.unmodifiableMap(skills(o.getAsJsonObject("skills"))),
                        List.copyOf(rt), o.get("description").getAsString()));
            }
        } catch (Exception ex) {
            ZomboidCraft.LOGGER.error("Couldn't read the marine roles", ex);
        }
        base = Collections.unmodifiableMap(b);
        traits = List.copyOf(t);
        roles = Collections.unmodifiableMap(r);
    }

    private static Map<Skill, Integer> skills(JsonObject o) {
        Map<Skill, Integer> out = new EnumMap<>(Skill.class);
        if (o == null) return out;
        for (Map.Entry<String, JsonElement> e : o.entrySet()) {
            try {
                out.put(Skill.valueOf(e.getKey()), e.getValue().getAsInt());
            } catch (IllegalArgumentException ignored) {
                ZomboidCraft.LOGGER.warn("Marine data names an unknown skill: {}", e.getKey());
            }
        }
        return out;
    }

    private MarineData() {
    }
}
