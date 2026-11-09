package com.zomboidcraft.client.hud;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.zomboidcraft.ZomboidCraft;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import net.fabricmc.loader.api.FabricLoader;
import org.jetbrains.annotations.Nullable;

/**
 * One Spartan's armour as set in the Armory (Noble Six and John-117 each keep their own): a piece per slot (null: the
 * soldier's own), colours, visor, armour effect, emblem, nameplate, pose and firefight voice. Kept on this PC in
 * {@code config/zomboidcraft/armory.json}, with which of the two is yours.
 */
final class ArmoryLook {
    static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("zomboidcraft/armory.json");

    final String soldier;
    final Map<String, String> picks = new LinkedHashMap<>();
    int primary, secondary, visor, effect, pose, voice, plate;
    int emblemFg, emblemBg, emblemPrimary = 2, emblemSecondary = 22, emblemBack = 21;
    boolean emblemSecond = true;

    ArmoryLook(String soldier) {
        this.soldier = soldier;
    }

    /** Fresh from the kit: the soldier's own colours and visor, Noble Six's pieces as the mock-ups showed him. */
    static ArmoryLook fresh(ArmoryKit kit, ArmoryKit.Soldier s) {
        ArmoryLook l = new ArmoryLook(s.key());
        l.primary = s.primary();
        l.secondary = s.secondary();
        l.visor = s.visor();
        l.voice = Math.max(0, kit.soldiers.indexOf(s));
        if (s.key().equals("six")) {
            l.picks.put("helmet", "MARK V [B] UA/HUL");
            l.picks.put("left_shoulder", "UA/MULTI-THREAT");
            l.picks.put("right_shoulder", "FJ/PARA");
            l.picks.put("chest", "UA/MULTI-THREAT");
            l.picks.put("wrist", "TACTICAL/TACPAD");
            l.picks.put("utility", "TACTICAL/HARD CASE");
            l.picks.put("knees", "FJ/PARA");
        }
        for (int i = 0; i < kit.poses.size(); i++) if (kit.poses.get(i).clip().equals("rifle_ready")) l.pose = i;
        for (int i = 0; i < kit.foregrounds.size(); i++) if (kit.foregrounds.get(i).name().equals("SPARTAN HELMET")) l.emblemFg = i;
        return l;
    }

    /** The soldier's regions with every chosen piece put on (a slot's other regions cleared first, so no flair is left over). */
    Map<String, String> outfit(ArmoryKit kit) {
        ArmoryKit.Soldier s = soldier(kit);
        Map<String, String> o = new LinkedHashMap<>(kit.base);
        if (s != null) o.putAll(s.outfit());
        for (ArmoryKit.Slot slot : kit.slots) {
            ArmoryKit.Item item = item(slot);
            if (item == null) continue;
            Set<String> owned = new HashSet<>();
            for (ArmoryKit.Item i : slot.items()) for (String[] p : i.pairs()) owned.add(p[0]);
            for (String r : owned) if (!r.equals("head")) o.remove(r);
            for (String[] p : item.pairs()) o.put(p[0], p[1]);
        }
        return o;
    }

    @Nullable ArmoryKit.Item item(ArmoryKit.Slot slot) {
        String name = picks.get(slot.key());
        if (name == null) return null;
        for (ArmoryKit.Item i : slot.items()) if (i.name().equals(name)) return i;
        return null;
    }

    @Nullable ArmoryKit.Soldier soldier(ArmoryKit kit) {
        for (ArmoryKit.Soldier s : kit.soldiers) if (s.key().equals(soldier)) return s;
        return null;
    }

    JsonObject json() {
        JsonObject o = new JsonObject();
        JsonObject p = new JsonObject();
        picks.forEach(p::addProperty);
        o.add("pieces", p);
        o.addProperty("primary", primary);
        o.addProperty("secondary", secondary);
        o.addProperty("visor", visor);
        o.addProperty("effect", effect);
        o.addProperty("pose", pose);
        o.addProperty("voice", voice);
        o.addProperty("nameplate", plate);
        JsonObject e = new JsonObject();
        e.addProperty("foreground", emblemFg);
        e.addProperty("background", emblemBg);
        e.addProperty("primary", emblemPrimary);
        e.addProperty("secondary", emblemSecondary);
        e.addProperty("background colour", emblemBack);
        e.addProperty("secondary layer", emblemSecond);
        o.add("emblem", e);
        return o;
    }

    private void read(JsonObject o) {
        if (o.has("pieces")) for (var e : o.getAsJsonObject("pieces").entrySet()) picks.put(e.getKey(), e.getValue().getAsString());
        primary = num(o, "primary", primary);
        secondary = num(o, "secondary", secondary);
        visor = num(o, "visor", visor);
        effect = num(o, "effect", effect);
        pose = num(o, "pose", pose);
        voice = num(o, "voice", voice);
        plate = num(o, "nameplate", plate);
        if (o.has("emblem")) {
            JsonObject e = o.getAsJsonObject("emblem");
            emblemFg = num(e, "foreground", emblemFg);
            emblemBg = num(e, "background", emblemBg);
            emblemPrimary = num(e, "primary", emblemPrimary);
            emblemSecondary = num(e, "secondary", emblemSecondary);
            emblemBack = num(e, "background colour", emblemBack);
            if (e.has("secondary layer")) emblemSecond = e.get("secondary layer").getAsBoolean();
        }
    }

    private static int num(JsonObject o, String k, int fallback) {
        try {
            return o.has(k) ? o.get(k).getAsInt() : fallback;
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    /** Both soldiers' armour (each fresh where nothing was saved yet) and which one is yours. */
    record Saved(Map<String, ArmoryLook> looks, String current) {
        ArmoryLook mine() {
            ArmoryLook l = looks.get(current);
            return l != null ? l : looks.values().iterator().next();
        }
    }

    static Saved load(ArmoryKit kit) {
        JsonObject root = new JsonObject();
        try {
            if (Files.exists(FILE)) root = JsonParser.parseString(Files.readString(FILE, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (Exception e) {
            ZomboidCraft.LOGGER.warn("Couldn't read {}", FILE, e);
        }
        Map<String, ArmoryLook> looks = new LinkedHashMap<>();
        JsonObject saved = root.has("soldiers") ? root.getAsJsonObject("soldiers") : new JsonObject();
        for (ArmoryKit.Soldier s : kit.soldiers) {
            ArmoryLook l = fresh(kit, s);
            JsonElement e = saved.get(s.key());
            if (e != null && e.isJsonObject()) l.read(e.getAsJsonObject());
            looks.put(s.key(), l);
        }
        String current = root.has("current") ? root.get("current").getAsString() : "six";
        return new Saved(looks, current);
    }

    static void save(Saved s) {
        JsonObject root = new JsonObject();
        root.addProperty("current", s.current());
        JsonObject soldiers = new JsonObject();
        s.looks().forEach((k, l) -> soldiers.add(k, l.json()));
        root.add("soldiers", soldiers);
        try {
            Files.createDirectories(FILE.getParent());
            Files.writeString(FILE, new GsonBuilder().setPrettyPrinting().create().toJson(root), StandardCharsets.UTF_8);
        } catch (Exception e) {
            ZomboidCraft.LOGGER.warn("Couldn't save the Armory to {}", FILE, e);
        }
    }
}
