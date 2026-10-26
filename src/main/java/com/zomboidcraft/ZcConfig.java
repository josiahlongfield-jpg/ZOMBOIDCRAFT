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
    public Combat combat = new Combat();
    public Guns guns = new Guns();
    /** The M9-style pistol's own numbers (shared gun settings are in guns). */
    public GunStats pistol = GunStats.pistol();
    /** The pump shotgun's own numbers, with its pellets. */
    public ShotgunStats shotgun = new ShotgunStats();
    /** The bolt-action hunting rifle's own numbers. */
    public GunStats huntingRifle = GunStats.huntingRifle();
    /** The side-by-side double-barrel's own numbers, with its pellets. */
    public ShotgunStats doubleBarrel = ShotgunStats.doubleBarrel();
    /** The .38 service revolver's own numbers. */
    public GunStats revolver = GunStats.revolver();
    /** How often a found gun comes in each of its finishes. */
    public Finishes finishes = new Finishes();
    public Carry carry = new Carry();
    /** What a gun case or an ammo can has in it when it's found shut. */
    public Cases cases = new Cases();
    /** How good each found weapon is: its tier, where its stats land inside the model's range, and flaws. */
    public WeaponRolls rolls = new WeaponRolls();

    public static class Cases {
        /** A found case comes packed with a gun, magazines and rounds, a can with rounds. Off, they're found empty. */
        public boolean packed = true;
        /** How often a rifle case holds each long gun, against the others. */
        public int assaultRifleWeight = 4;
        public int huntingRifleWeight = 4;
        public int shotgunWeight = 3;
        public int doubleBarrelWeight = 2;
        /** How often a pistol case holds the revolver rather than the pistol, against it. */
        public int pistolWeight = 3;
        public int revolverWeight = 2;
        /** Most of a packed gun's durability that's already worn off, as a fraction. */
        public double maxWear = 0.17;
        /** Chance someone left a part on: a 4x scope on the hunting rifle, a red dot on the rifle, a light on the pistol. */
        public double scopeChance = 0.4;
        public double redDotChance = 0.2;
        public double lightChance = 0.15;
        /** Most magazines packed with a long gun (a pistol case has one). */
        public int maxMagazines = 2;
        /** Chance each packed magazine is full; otherwise it holds anything from none to full. */
        public double fullMagazineChance = 0.5;
        /** Chance an ammo can holds a second calibre. */
        public double secondCalibreChance = 0.35;
    }

    /**
     * Every weapon rolls a tier, 1 (Rough) to 5 (Pristine), where it's found. The place sets the tier range (tiers
     * written low-high), a dense horde town around it can push it up one, and distance from your start plays no part.
     */
    public static class WeaponRolls {
        /** Weapons roll a tier, stats and flaws when found. Off, new finds stay as they are. */
        public boolean enabled = true;
        /** How often each tier turns up against the others (tiers 1 to 5), before the place narrows it down. */
        public String tierWeights = "30,30,22,12,6";
        /** Tier range in houses, stores, farms and cars. */
        public String houses = "1-3";
        /** Tier range of weapons the dead drop. */
        public String onTheDead = "1-2";
        /** Tier range in gun cabinets and gun safes. */
        public String gunCabinets = "3-5";
        /** Tier range in gun shops and pawn shops. */
        public String gunShops = "2-4";
        /** Tier range in police cars. */
        public String police = "2-4";
        /** Tier range with military kit (SWAT vans for now). */
        public String military = "3-5";
        /** Tier range of a gun found packed in its case. */
        public String gunCases = "2-4";
        /** Tier range of a weapon from anywhere else (the floor, a crafting bench). */
        public String elsewhere = "1-3";
        /** Zombies within denseTownRadius blocks that make a place a dense horde town (0 = off). */
        public int denseTownZombies = 20;
        public int denseTownRadius = 48;
        /** Chance loot in a dense horde town rolls one tier higher. */
        public double denseTownBump = 0.5;
        /** How far a model's stat can sit from its average copy, each way (0.15 = 15% worse to 15% better). */
        public double damageRange = 0.15;
        /** Bullet spread. */
        public double accuracyRange = 0.3;
        /** Recoil. */
        public double stabilityRange = 0.25;
        /** Aim sway on the sights. */
        public double handlingRange = 0.3;
        /** Jam chance, for guns that can jam. */
        public double reliabilityRange = 0.6;
        /** Full condition: how long it lasts. */
        public double durabilityRange = 0.3;
        /** Melee swings a second. */
        public double speedRange = 0.08;
        public double reachRange = 0.05;
        public double knockdownRange = 0.3;
        /** Chance a tier 1 or tier 2 find comes with a flaw. */
        public double tier1FlawChance = 0.6;
        public double tier2FlawChance = 0.25;
        /** How much a flaw worsens the stat it hits (1.3 = 30% worse). */
        public double flawPenalty = 1.3;
        /** Jam chance per shot of a rolled gun in new condition, before its roll (worn guns jam more on top). */
        public double jamFloor = 0.003;
    }

    /** Weights: how often a found gun turns up in each of its finishes, against its others. */
    public static class Finishes {
        /** The double-barrel: blued with plain walnut, colour case hardened with figured walnut, engraved with exhibition walnut. */
        public int doubleBarrelField = 70;
        public int doubleBarrelCaseHardened = 25;
        public int doubleBarrelEngraved = 5;
        /** The revolver: blued with checkered walnut, nickel with rosewood, engraved royal blue with exhibition walnut. */
        public int revolverService = 70;
        public int revolverNickel = 25;
        public int revolverEngraved = 5;
    }

    public static class Carry {
        /** Only a few main-inventory slots are pockets; bags carry the rest. Creative ignores it. */
        public boolean pocketsOnly = true;
        /** Pocket slots, on top of the 9 belt-and-hands slots. */
        public int pocketSlots = 4;
        /** Things have weight, and carrying too much slows and tires you. */
        public boolean weight = true;
        /** Kilograms you carry before Heavy Load sets in. */
        public double capacity = 8;
        /** A bag you pick up goes straight on when its place (back, vest, waist, off hand) is free. */
        public boolean autoWear = true;
    }

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
        /** A walker screams when it first spots you. */
        public boolean alertScreams = true;
        /** Seconds before the same zombie can scream again. */
        public int alertCooldownSeconds = 20;
        /** At most this many zombies within 16 blocks scream in the same two seconds, so a crowd doesn't scream as one. */
        public int alertMaxTogether = 3;
        /** Snarls every few seconds while chasing. */
        public boolean chaseSnarls = true;
        public double chaseSnarlMinSeconds = 2;
        public double chaseSnarlMaxSeconds = 4;
        /** A crowd murmur swells as walkers gather within 48 blocks, and a roar as more of them come after you. */
        public boolean hordeSounds = true;
        /** Walkers nearby for the murmur to start, and to reach full volume; kept under the single voices. */
        public int hordeMurmurFrom = 6;
        public int hordeMurmurFull = 30;
        public double hordeMurmurVolume = 0.45;
        /** Zombies after you for the roar to start, and to reach full volume. */
        public int hordeRoarFrom = 3;
        public int hordeRoarFull = 15;
        public double hordeRoarVolume = 0.8;
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
        /** Injuries by body part: falls break legs, zombies scratch and bite where they reach, splints and bandages. */
        public boolean injuries = true;
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
        /** Shop shelves, coolers, cases and stockroom crates hold themed loot; off leaves them empty. */
        public boolean shopLoot = true;
        /** Real 3D cars on the streets; off leaves the roads empty. */
        public boolean streetCars = true;
        /** How the city's cars are left, as shares: whole and dusty (drivable later), abandoned, wrecked, burnt out. */
        public double carUsable = 0.45;
        public double carAbandoned = 0.3;
        public double carWrecked = 0.18;
        public double carBurnt = 0.07;
        /** Car boots and the backs of vans hold loot (tools/cars/car_loot.py); off leaves them empty. */
        public boolean carLoot = true;
    }

    public static class Weather {
        /** The mod runs the overworld's weather: never clear, with rain, fog, wind and storms. Off hands it back to vanilla. */
        public boolean enabled = true;
        /** Grey cloud ceiling all day: no blue sky, no sun, no sunsets. */
        public boolean alwaysOvercast = true;
        /**
         * Endless night: the clouds keep the day nearly as dark as the night, a cold blue-grey dusk that never
         * lifts, so lamps, signs and neon glow at noon as they do at midnight. Off gives a plain overcast day.
         */
        public boolean endlessNight = true;
        /** Daylight under endless night, as a share of vanilla's; nights sit a little under 0.30. */
        public double endlessDaylight = 0.30;
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
        /** Mist you can see under shader packs: banks drifting with the wind in fog, low ground mist at night. Client side. */
        public boolean mist = true;
        /** How thick the mist gets, as a share; 0.5 halves it. Client side. */
        public double mistAmount = 1.0;
        /** Paper, leaves and plastic blown down the street when the wind picks up. Client side. */
        public boolean windDebris = true;
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
        /** The field-kit inventory screen; off brings back Minecraft's. Client side. */
        public boolean fieldKitInventory = true;
        /** The field-kit HUD: belt, moodle plates, your figure for health, a wristwatch; off brings back Minecraft's. Client side. */
        public boolean fieldKitHud = true;
        /** Weapon tooltips drawn as the kit screen's card (rarity band, tier pips, stat bars); off shows lines of text. Client side. */
        public boolean weaponCards = true;
        /** Size of the crosshair against the game's own (1 = vanilla). Client side. */
        public double crosshairScale = 0.5;
        /** No crosshair while aiming down a gun's sights: the sight is the aim point. Client side. */
        public boolean hideCrosshairWhenAiming = true;
        /** Your own arms, in your own skin, hold guns in first person. Client side. */
        public boolean firstPersonArms = true;
    }

    public static class Combat {
        /** Other players see rifles carried at the low ready or shouldered, and big melee weapons in both hands. */
        public boolean weaponHolds = true;
        /** Multiplies every melee weapon's damage (needs a restart). */
        public double meleeDamage = 1.0;
        /** Weapons lose condition with each hit and break (needs a restart). */
        public boolean weaponWear = true;
        /** Swinging, shoving and sprinting tire you; tired swings are slower and weaker. */
        public boolean stamina = true;
        /** How fast endurance comes back (1 = about 12 seconds from empty). */
        public double staminaRegen = 1.0;
        /** Multiplies the endurance each swing costs. */
        public double swingCost = 1.0;
        /** Hits can put zombies on the ground. */
        public boolean knockdowns = true;
        /** Multiplies each weapon's knockdown chance. */
        public double knockdownChance = 1.0;
        /** How long a knocked-down zombie stays down, in seconds. */
        public double downedSeconds = 3.5;
        /** Looking down and attacking a zombie on the ground stomps or strikes it hard. */
        public boolean groundAttacks = true;
        /** Damage of a bare-footed stomp. */
        public double stompDamage = 7.0;
        /** Multiplies weapon damage against zombies on the ground. */
        public double groundAttackMultiplier = 1.8;
        /** Push zombies away with the shove key (V). */
        public boolean shove = true;
        public double shoveRange = 2.3;
        /** Chance a shove knocks a zombie down (doubled from behind). */
        public double shoveKnockdownChance = 0.3;
        /** Endurance a shove costs, out of 100. */
        public double shoveCost = 7.0;
        /** Zombies that reach you can grab hold; shove or mash jump to break free. */
        public boolean grabs = true;
        /** Chance a second that a zombie next to you grabs you. */
        public double grabChance = 0.2;
        /** Jump presses it takes to struggle free. */
        public int struggleTaps = 6;
        /** Zombies close in with a sudden lunge now and then. */
        public boolean lunges = true;
        public double lungeChance = 0.06;
        /** Some zombies crawl: spawned that way, or left that way by a heavy blow or a shot to the legs. */
        public boolean crawlers = true;
        /** Share of zombies that spawn crawling. */
        public double crawlerSpawnChance = 0.05;
        /** Chance a knockdown from a heavy weapon or gunshot leaves a zombie crawling. */
        public double crippleChance = 0.2;
    }

    /**
     * One gun's own numbers. The assault rifle's sit in guns, beside the settings every gun shares; every other
     * gun has a group of its own (pistol, shotgun, huntingRifle).
     */
    public static class GunStats {
        /** Damage per bullet (zombies have 20 health). */
        public double damage = 12.0;
        public double headshotMultiplier = 3.0;
        /** Bullet spread in degrees from the hip and when aiming. */
        public double hipSpread = 3.0;
        public double aimSpread = 0.4;
        /** Multiplies how far the muzzle kicks up. */
        public double recoil = 1.0;
        /** How far a shot is heard by zombies, in blocks. */
        public double shotNoise = 90.0;
        /** Field of view while aiming, as a share of normal. */
        public double aimZoom = 0.78;
        public double range = 100.0;
        /** Ticks between shots (semi-automatic). */
        public int fireDelayTicks = 3;
        /** Rounds in a magazine. */
        public int magazineSize = 30;

        static GunStats pistol() {
            GunStats s = new GunStats();
            s.damage = 7.0;
            s.hipSpread = 2.5;
            s.aimSpread = 0.7;
            s.recoil = 0.9;
            s.shotNoise = 60.0;
            s.aimZoom = 0.88;
            s.range = 60.0;
            s.magazineSize = 15;
            return s;
        }

        /**
         * The .38 service revolver: a little less than the 9mm per hit, six shots, a heavier double-action pull between
         * them, then the cylinder swung out and loaded by hand.
         */
        static GunStats revolver() {
            GunStats s = new GunStats();
            s.damage = 6.5;
            s.hipSpread = 2.6;
            s.aimSpread = 0.6;
            s.recoil = 1.1;
            s.shotNoise = 70.0;
            s.aimZoom = 0.88;
            s.range = 60.0;
            s.fireDelayTicks = 6;
            s.magazineSize = 6;
            return s;
        }

        /**
         * The .308 bolt action: one hard-hitting, accurate round, a heavy kick, heard a long way off; its time between
         * shots covers working the bolt (lifted, back, home and down, then the hand back on the stock).
         */
        static GunStats huntingRifle() {
            GunStats s = new GunStats();
            s.damage = 30.0;
            s.headshotMultiplier = 3.0;
            s.hipSpread = 3.5;
            s.aimSpread = 0.12;
            s.recoil = 2.6;
            s.shotNoise = 140.0;
            s.aimZoom = 0.80;
            s.range = 200.0;
            s.fireDelayTicks = 25;     // never less than the bolt takes to work (GunType.fireDelay)
            s.magazineSize = 5;
            return s;
        }
    }

    /**
     * The pump shotgun's numbers: each shot is a spread of buckshot pellets, deadly close up and weak far off. Its
     * damage is per pellet, its magazine size is the shells it holds (four in the tube and one chambered), and its
     * time between shots covers working the pump.
     */
    public static class ShotgunStats extends GunStats {
        /** Pellets in each shell (00 buckshot has eight or nine). */
        public int pellets = 8;
        /** How far the pellets spread from where the shot goes, in degrees. */
        public double pelletSpread = 2.5;
        /** Pellets hit full strength out to this many blocks, then weaken to a quarter at the gun's range. */
        public double falloffStart = 10.0;
        /** How hard a close hit shoves a zombie back. */
        public double knockback = 0.45;

        ShotgunStats() {
            damage = 3.5;
            headshotMultiplier = 2.5;
            hipSpread = 2.0;
            aimSpread = 0.6;
            recoil = 2.2;
            shotNoise = 120.0;
            aimZoom = 0.85;
            range = 40.0;
            fireDelayTicks = 15;
            magazineSize = 5;
        }

        /**
         * The side-by-side: the pump's buckshot from a longer, tighter-choked pair of barrels, and two shots as fast as
         * the triggers can be pulled, then it's broken open and loaded by hand. Its magazine size is its two barrels.
         */
        static ShotgunStats doubleBarrel() {
            ShotgunStats s = new ShotgunStats();
            s.damage = 3.8;
            s.pelletSpread = 2.1;
            s.falloffStart = 12.0;
            s.knockback = 0.5;
            s.recoil = 2.5;
            s.shotNoise = 125.0;
            s.range = 45.0;
            s.fireDelayTicks = 5;
            s.magazineSize = 2;
            return s;
        }
    }

    /** Settings every gun shares, and the assault rifle's own numbers. */
    public static class Guns extends GunStats {
        public boolean enabled = true;
        /** Worn guns sometimes jam; clear them with the reload key. */
        public boolean jams = true;
        /** Guns are modular: optics, muzzle devices, grips, lights, lasers and stocks go on and come off. */
        public boolean attachments = true;
        /** Multiply aimed spread with each optic mounted. */
        public double redDotAimSpread = 0.6;
        public double holoAimSpread = 0.6;
        public double scopeAimSpread = 0.35;
        /** Field of view looking through the 4x scope, as a share of normal. */
        public double scopeZoom = 0.25;
        /** A suppressor multiplies how far zombies hear the shot; the suppressed recordings play at this volume. */
        public double suppressedNoise = 0.3;
        public double suppressedVolume = 1.0;
        /** A foregrip multiplies the kick; a gun with its stock off kicks and wanders more. */
        public double foregripRecoil = 0.75;
        public double noStockRecoil = 1.5;
        public double noStockSpread = 1.6;
        /** A laser multiplies spread from the hip. */
        public double laserHipSpread = 0.6;
        /** Smoke and a spark at the muzzle after each shot (off by default: they hid the sights). */
        public boolean muzzleSmoke = false;
        /** A short, soft flash at the muzzle with each shot (a smaller one at the end of a suppressor). */
        public boolean muzzleFlash = true;
        /** How much the gun bobs and sways on the sights: 1 = as much as at the hip, 0 = dead still. Client side. */
        public double aimSway = 0.15;
        /** First person animates reloads: the old magazine drops away, a new one goes in, the charging handle is worked on an empty gun. Client side. */
        public boolean reloadAnimation = true;
        /** A faint green beam shows in the air between a laser and its dot. Client side. */
        public boolean laserBeam = true;
        /**
         * How bright (a light level, 1 to 15) a gun light lights the world just ahead of it, fading a level every
         * three blocks down the beam. Higher lights more of the rooms around it too. Client side.
         */
        public int lightLevel = 11;
        /**
         * How many hidden lights a gun light spreads down its beam (1 to 8). More light the floor and walls further
         * along it, but cost frames when you turn. Client side.
         */
        public int lightSources = 5;
        /** How wide a gun light's cone is, in degrees from its middle to its edge. Client side. */
        public double lightCone = 13;
        /** How strongly a gun light's pool and beam are drawn in the dark (0 = not at all). Client side. */
        public double lightGlow = 1.0;
        /** A gun light's beam shows faintly in the air. Client side. */
        public boolean lightBeam = true;
        /**
         * Draws a gun light's pool and beam even with a shader pack on. Off by default: shader packs like Photon light
         * the world from the gun light themselves. Client side.
         */
        public boolean glowWithShaders = false;
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
