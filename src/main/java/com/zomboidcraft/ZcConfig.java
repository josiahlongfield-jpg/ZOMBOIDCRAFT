package com.zomboidcraft;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Every tunable number and on/off switch in the mod, like Zomboid's sandbox options.
 * Saved as config/zomboidcraft.json and editable in game with /zc config.
 */
public final class ZcConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("zomboidcraft.json");
    private static ZcConfig instance = new ZcConfig();

    public Zombies zombies = new Zombies();
    public Senses senses = new Senses();
    public Spawning spawning = new Spawning();
    public Survival survival = new Survival();
    public Progression progression = new Progression();
    public Look look = new Look();
    public World world = new World();
    public Weather weather = new Weather();

    public static class Zombies {
        /** Share of zombies that sprint once they spot you (Zomboid's "random" speed mix). */
        public double sprinterChance = 0.05;
        /** Speed change for shamblers and sprinters, as a fraction of vanilla zombie speed. */
        public double shamblerSpeed = -0.3;
        public double sprinterSpeed = 0.25;
        public boolean burnInSunlight = false;
        public boolean breakDoors = true;
        public boolean babyZombies = false;
        public boolean bashBarricades = true;
        /** Scales how quickly zombies knock boards off barricades. */
        public double barricadeDamage = 1.0;
        /** Zombies on your trail pound shut doors until they break (they never open them). */
        public boolean bashDoors = true;
        /** Average hits a shut door takes before it gives way; one zombie hits about once a second. */
        public int doorHits = 12;
    }

    public static class Senses {
        /** When off, zombies use vanilla Minecraft targeting. Takes effect for newly loaded zombies. */
        public boolean sightEnabled = true;
        /** How far a zombie sees you in full daylight, in blocks. */
        public double sightRange = 24;
        /** Width of the zombie's vision cone, in degrees. */
        public double sightConeDegrees = 140;
        /** Sight range in pitch darkness, as a share of the daylight range. */
        public double darkSight = 0.3;
        /** Sight range against a crouching player, as a share. */
        public double crouchSight = 0.5;
        /** Anything this close is noticed whichever way the zombie faces. */
        public double closeRange = 2.5;
        /** Seconds a zombie keeps chasing after losing sight of you. */
        public int memorySeconds = 15;
        /** Seconds a zombie searches where it last saw or heard you before giving up. */
        public int searchSeconds = 30;
        /** Scales every noise radius (footsteps, fighting, breaking blocks, hammering). */
        public double hearing = 1.0;
    }

    public static class Spawning {
        public boolean enabled = true;
        public int intervalSeconds = 10;
        /** No new groups while this many zombies are within 64 blocks of a player. */
        public int nearbyCap = 24;
        public int groupMin = 3;
        public int groupMax = 7;
        public int minDistance = 32;
        public int maxDistance = 56;
        /** Creepers, skeletons, spiders and other vanilla monsters in the overworld; off leaves only the dead. */
        public boolean vanillaMonsters = false;
    }

    public static class Survival {
        public boolean thirst = true;
        public double thirstPerSecond = 0.07;
        public boolean fatigue = true;
        public double fatiguePerSecond = 0.055;
        public boolean bleeding = true;
        public double clotChancePerSecond = 0.015;
        public boolean infection = true;
        public double biteChance = 0.12;
        public double scratchChance = 0.30;
        public double scratchInfectionChance = 0.07;
        public int infectionMinutes = 40;
        /** Infected players rise as zombies. */
        public boolean reanimate = true;
    }

    public static class Progression {
        public double xpMultiplier = 1.0;
    }

    public static class World {
        /** /zc city builds with vanilla look-alike blocks instead of the city's own. */
        public boolean cityStandIns = false;
    }

    public static class Weather {
        /** The mod runs the overworld's weather: never clear, with rain, fog, wind and storms. Off hands it back to vanilla. */
        public boolean enabled = true;
        /** Grey cloud ceiling all day: no blue sky, no sun, no sunsets. */
        public boolean alwaysOvercast = true;
        /** Daylight as a share of vanilla's; 0.55 is a little darker than a vanilla thunderstorm. */
        public double daylight = 0.55;
        /** Night darkness as a share of vanilla's night light; lower is darker. */
        public double nightLight = 0.8;
        /** The moon breaks through gaps in the clouds now and then at night. */
        public boolean moonBreaks = true;
        /** Scales how often rain, fog, wind and storms come; 0 keeps a steady drizzle. */
        public double eventFrequency = 1.0;
        /** Scales how often storms come, and how many of them turn dangerous. */
        public double stormFrequency = 1.0;
        /** Thick fog now and then, as its own weather or rolling in over the rain. */
        public boolean fog = true;
        /** A light haze over the distance at all times: where it fully hides things, in blocks. 0 turns it off. */
        public int hazeDistance = 200;
        /** Strong storms throw real lightning that starts fires (never right on top of you). */
        public boolean lightningStrikes = true;
        /** Hail in severe storms hurts anyone out in the open without a helmet. */
        public boolean hail = true;
        /** Cyclone winds in severe storms shove you and the dead around and blow in windows. */
        public boolean windDamage = true;
        /** Rain and wind cover your noise and fog hides you, for you and the zombies alike. */
        public boolean affectsZombies = true;
    }

    public static class Look {
        /** Zomboid-style zombie model and outfits. Client side; needs a restart. */
        public boolean customZombies = true;
        /** 256px zombie skins with shine and bump maps for shader packs; off uses the 64px skins. */
        public boolean hdSkins = true;
        /** Raised 3D pieces on zombie outfits: caps, helmets, pouches, collars, hoods, a hanging jaw. */
        public boolean outfitPieces = true;
        /** Zombie eyes glow red in the dark. */
        public boolean glowingEyes = true;
        /** Worst damage shown on HD zombies: 0 light, 1 medium, 2 heavy, 3 bones showing. */
        public int maxGore = 3;
        /** Share of zombies missing a forearm or a whole arm. */
        public double missingLimbChance = 0.15;
        /** Pitch of zombie groans; lower sounds less like Minecraft. */
        public double voicePitch = 0.8;
    }

    public static ZcConfig get() {
        return instance;
    }

    public static void load() {
        if (Files.exists(FILE)) {
            try (Reader reader = Files.newBufferedReader(FILE)) {
                ZcConfig read = GSON.fromJson(reader, ZcConfig.class);
                if (read != null) {
                    instance = read;
                }
            } catch (IOException | RuntimeException e) {
                ZomboidCraft.LOGGER.error("Couldn't read {}, using defaults", FILE, e);
            }
        }
        // Writing back adds any settings that are new in this version.
        save();
    }

    public static void save() {
        try {
            Files.createDirectories(FILE.getParent());
            try (Writer writer = Files.newBufferedWriter(FILE)) {
                GSON.toJson(instance, writer);
            }
        } catch (IOException e) {
            ZomboidCraft.LOGGER.error("Couldn't save {}", FILE, e);
        }
    }

    // ---- access by "group.name" keys, for the /zc config command and the settings screen ----

    public static List<String> keys() {
        List<String> keys = new ArrayList<>();
        for (Field group : ZcConfig.class.getFields()) {
            if (Modifier.isStatic(group.getModifiers())) {
                continue;
            }
            for (Field field : group.getType().getFields()) {
                keys.add(group.getName() + "." + field.getName());
            }
        }
        return keys;
    }

    public static String getValue(String key) {
        try {
            String[] parts = key.split("\\.", 2);
            Object group = ZcConfig.class.getField(parts[0]).get(instance);
            return String.valueOf(group.getClass().getField(parts[1]).get(group));
        } catch (ReflectiveOperationException | ArrayIndexOutOfBoundsException e) {
            throw new IllegalArgumentException("Unknown setting " + key);
        }
    }

    public static void setValue(String key, String raw) {
        try {
            String[] parts = key.split("\\.", 2);
            Object group = ZcConfig.class.getField(parts[0]).get(instance);
            Field field = group.getClass().getField(parts[1]);
            Class<?> type = field.getType();
            if (type == boolean.class) {
                if (!raw.equalsIgnoreCase("true") && !raw.equalsIgnoreCase("false")) {
                    throw new IllegalArgumentException(key + " must be true or false");
                }
                field.setBoolean(group, Boolean.parseBoolean(raw));
            } else if (type == int.class) {
                field.setInt(group, Integer.parseInt(raw));
            } else if (type == double.class) {
                field.setDouble(group, Double.parseDouble(raw));
            } else {
                field.set(group, raw);
            }
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Not a number: " + raw);
        } catch (ReflectiveOperationException | ArrayIndexOutOfBoundsException e) {
            throw new IllegalArgumentException("Unknown setting " + key);
        }
        save();
    }

    private ZcConfig() {
    }
}
