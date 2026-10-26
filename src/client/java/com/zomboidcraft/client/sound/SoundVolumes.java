package com.zomboidcraft.client.sound;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.zomboidcraft.ZomboidCraft;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;

/**
 * A volume for each group of ZomboidCraft's sounds, on top of the game's own sliders. Applied on this computer
 * as each sound plays (see SoundEngineMixin), so friends on a shared world each set their own. Kept in
 * config/zomboidcraft-sounds.json.
 */
public final class SoundVolumes {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("zomboidcraft-sounds.json");
    static final String MUFFLE_KEY = "wall_muffling";

    public enum Group {
        ZOMBIE_VOICES("zombie_voices"),
        HORDES("hordes"),
        ZOMBIE_STEPS("zombie_steps"),
        DOOR_BASHING("door_bashing"),
        GUNS("guns"),
        MELEE("melee"),
        LOOTING("looting"),
        AMBIENCE("ambience"),
        VEHICLES("vehicles");

        public final String key;
        private volatile double volume = 1.0;

        Group(String key) {
            this.key = key;
        }

        public double volume() {
            return volume;
        }

        public Component label() {
            return Component.translatable("options.zomboidcraft.sound." + key);
        }
    }

    /** How strongly walls and floors muffle sounds behind them (see Muffler); 0 lets sound pass like vanilla. */
    private static volatile double muffle = 1.0;

    public static double muffle() {
        return muffle;
    }

    public static void setMuffle(double strength) {
        muffle = Math.max(0.0, Math.min(1.0, strength));
    }

    /** Which group a sound event belongs to, or null for sounds the sliders leave alone. */
    public static Group groupOf(Identifier id) {
        String path = id.getPath();
        if (id.getNamespace().equals(ZomboidCraft.MOD_ID)) {
            if (path.startsWith("zombie.horde")) {
                return Group.HORDES;
            }
            if (path.startsWith("zombie.")) {
                return Group.ZOMBIE_VOICES;
            }
            if (path.startsWith("weapon.melee.")) {
                return Group.MELEE;
            }
            if (path.startsWith("weapon.")) {
                return Group.GUNS;
            }
            if (path.startsWith("loot.") || path.startsWith("item.")) {
                return Group.LOOTING;
            }
            if (path.startsWith("car.")) {
                return Group.VEHICLES;
            }
            if (path.startsWith("amb.")) {
                return Group.AMBIENCE;
            }
            return null;
        }
        if (id.getNamespace().equals("minecraft") && path.equals("entity.lightning_bolt.thunder")) {
            return Group.AMBIENCE;  // the thunder takes come from the ambience set
        }
        // the walkers use the game's zombie sound events, which the mod's sounds replace
        if (id.getNamespace().equals("minecraft") && path.startsWith("entity.")) {
            int dot = path.indexOf('.', 7);
            if (dot < 0) {
                return null;
            }
            String mob = path.substring(7, dot), what = path.substring(dot + 1);
            if (!mob.equals("zombie") && !mob.equals("husk") && !mob.equals("zombie_villager")) {
                return null;
            }
            if (what.equals("step")) {
                return Group.ZOMBIE_STEPS;
            }
            return what.contains("door") ? Group.DOOR_BASHING : Group.ZOMBIE_VOICES;
        }
        return null;
    }

    public static float factor(Identifier id) {
        Group group = groupOf(id);
        float factor = group == null ? 1f : (float) group.volume;
        if (group == Group.GUNS && id.getPath().contains(".shot") && com.zomboidcraft.client.gear.RadioClient.wearingHeadset()) {
            // a comms headset's hearing protection takes the crack out of gunfire
            factor *= (float) Math.max(0, Math.min(1, com.zomboidcraft.ZcConfig.get().radio.headsetGunshotVolume));
        }
        return factor;
    }

    /** Sets a group's volume and re-applies volumes to the sounds already playing (the horde murmur loops). */
    public static void set(Group group, double volume) {
        group.volume = Math.max(0.0, Math.min(1.0, volume));
        Minecraft minecraft = Minecraft.getInstance();
        for (SoundSource source : SoundSource.values()) {
            if (source != SoundSource.MASTER) {
                minecraft.getSoundManager().refreshCategoryVolume(source);
            }
        }
    }

    public static void load() {
        if (!Files.exists(FILE)) {
            return;
        }
        try (Reader reader = Files.newBufferedReader(FILE)) {
            Map<String, Double> saved = GSON.fromJson(reader, new TypeToken<Map<String, Double>>() {}.getType());
            if (saved != null) {
                for (Group group : Group.values()) {
                    Double v = saved.get(group.key);
                    if (v != null) {
                        group.volume = Math.max(0.0, Math.min(1.0, v));
                    }
                }
                Double m = saved.get(MUFFLE_KEY);
                if (m != null) {
                    setMuffle(m);
                }
            }
        } catch (IOException | RuntimeException e) {
            ZomboidCraft.LOGGER.warn("Couldn't read {}, using full volume for every group", FILE, e);
        }
    }

    public static void save() {
        Map<String, Double> out = new LinkedHashMap<>();
        for (Group group : Group.values()) {
            out.put(group.key, Math.round(group.volume * 100) / 100.0);
        }
        out.put(MUFFLE_KEY, Math.round(muffle * 100) / 100.0);
        try (Writer writer = Files.newBufferedWriter(FILE)) {
            GSON.toJson(out, writer);
        } catch (IOException e) {
            ZomboidCraft.LOGGER.warn("Couldn't save {}", FILE, e);
        }
    }

    private SoundVolumes() {
    }
}
