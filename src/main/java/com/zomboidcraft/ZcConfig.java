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
    /** Shooting limbs off zombies: how much each limb takes before it tears off, and how hard each kind of gun tears. */
    public Limbs limbs = new Limbs();
    /** How the dead fall and the bodies they leave, and how the living jerk when shot. */
    public Corpses corpses = new Corpses();
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
    /** The .22 rifle's own numbers. */
    public GunStats rimfire = GunStats.rimfire();
    /** The submachine gun's own numbers (it has full auto). */
    public GunStats smg = GunStats.smg();
    /** The sawn-off's own numbers, with its pellets. */
    public ShotgunStats sawnOff = ShotgunStats.sawnOff();
    /** The .44 magnum revolver's own numbers. */
    public GunStats magnum = GunStats.magnum();
    /** The lever-action carbine's own numbers. */
    public GunStats leverAction = GunStats.leverAction();
    /** The crossbow's own numbers, and how often its bolts break. */
    public CrossbowStats crossbow = new CrossbowStats();
    /** How often a found gun comes in each of its finishes. */
    public Finishes finishes = new Finishes();
    public Carry carry = new Carry();
    /** What a gun case or an ammo can has in it when it's found shut. */
    public Cases cases = new Cases();
    /** Night vision goggles on headgear: their batteries and the look through them. */
    public NightVision nightVision = new NightVision();
    /** Handheld radios that carry players' voices (through the voice chat mod), and the comms headset. */
    public Radio radio = new Radio();
    /** How good each found weapon is: its tier, where its stats land inside the model's range, and flaws. */
    public WeaponRolls rolls = new WeaponRolls();
    /** What it takes to dig down and knock walls through, so nobody tunnels under the whole map (building/DigRules). */
    public Digging digging = new Digging();
    /** What the Build page makes: how tough boards and sheets are, how loud a tripwire is, how fast a barrel fills. */
    public Building building = new Building();
    /** Keeping big hordes smooth: how much detail far zombies get, and how often far idle ones think. */
    public Performance performance = new Performance();
    public Population population = new Population();
    /** Driverless trains: the county railway and the downtown subway (TrainCommand, world.trains). */
    public Trains trains = new Trains();
    public Start start = new Start();

    public static class Start {
        /** The spawn picker in a planned county world: choose a place, wake inside a building with no dead in it. */
        public boolean picker = true;
        /** Show it again after each death (death starts a new survivor). */
        public boolean afterDeath = true;
        /** Chunks looked through for a way in before settling for the best found. */
        public int searchChunks = 48;
    }

    public static class Trains {
        /** Cruising speeds in blocks a second (60 is about 216 km/h). */
        public double railSpeed = 60;
        public double subwaySpeed = 56;
        /** Speeding up and braking, blocks a second per second: gradual, never sudden. */
        public double acceleration = 3;
        public double braking = 3.5;
        /** How hard a curve may be taken (sideways blocks/s/s): lower slows trains more on bends. */
        public double curveGrip = 4.0;
        /** Seconds the doors stay open at a stop before they close by themselves. */
        public int doorSeconds = 30;
        /** Cars in each train. */
        public int railCars = 3;
        public int subwayCars = 3;
        /** Trains running each line; 0 spreads them along it, one every railSpacing / subwaySpacing blocks of track. */
        public int railTrains = 0;
        public int subwayTrains = 0;
        public double railSpacing = 1500;
        /** How much faster a train runs on its way to a call button (curves still slow it), never past 80 blocks a second. */
        public double expressSpeed = 1.3;
        public double subwaySpacing = 900;
        /** Earshot of the flare (railway) and the buzzer (subway) a call button sets off, once, for zombies. */
        public double flareNoise = 72;
        public double sirenNoise = 64;
        /** How long the flare burns, in seconds. */
        public int flareSeconds = 45;
        /** Damage a moving train does to a zombie it hits, and to a player per block-a-second of speed. */
        public float hitZombie = 40;
        public float hitPlayerPerSpeed = 1.2f;
        /** OTTO, the robot conductor, talks in chat to riders and people on the platform. */
        public boolean ottoTalks = true;
        /** Seconds between OTTO's remarks while running between stops. */
        public int ottoChatter = 45;
        /** Zombies ride the trains: share of trains that start with some aboard, and most a train carries. */
        public double zombieRiderChance = 0.6;
        public int zombieRidersMax = 6;
    }

    public static class Performance {
        /** The nearest this many zombies on screen get every clothing detail (within look.zombieDetailDistance). Client side. */
        public int fullDetailZombies = 12;
        /** The nearest this many get most of it; the rest only the main shapes. Client side. */
        public int midDetailZombies = 20;
        /**
         * Zombies past the nearest few are drawn from a baked body: the clothes painted on and only the pieces that
         * change its outline kept 3D, about a tenth of the full one. Off draws them from the full body, thinned. Client side.
         */
        public boolean bakedZombies = true;
        /** Past this many blocks a zombie is drawn with its body and only the biggest pieces of clothing. Client side. */
        public double simpleZombieDistance = 40;
        /** Shader shadows (Photon's) are cast by the simple body, which looks the same in a shadow and costs far less. Client side. */
        public boolean simpleZombieShadows = true;
        /**
         * Zombies standing idle further than this many blocks from every player think every fourth tick instead of
         * every tick (they still hear and turn up the same). 0 turns it off.
         */
        public double idleThinkDistance = 32;
        /**
         * Zombies beyond the nearest meshZombies (and past spriteMinDistance) are drawn as a picture of their baked body
         * turned to face you, one card instead of a few hundred pieces. Client side.
         */
        public boolean spriteZombies = true;
        public int meshZombies = 40;
        public double spriteMinDistance = 24;
        /** A readout over the hotbar: FPS, zombies loaded and drawn at each detail, pieces drawn a frame. Client side. */
        public boolean showStats = false;
    }

    /**
     * The world's dead, placed once when the world is made (towns start full) and kept as light records until a player
     * comes near: only those near someone are real, thinking zombies. Sizes per place are in Budgets.
     */
    public static class Population {
        public boolean enabled = true;
        /** Multiplies every place's count (Zomboid's population setting): 0.25 to 4. */
        public double density = 1.0;
        /** At most this many real zombies at once, shared by every player (where autoCap starts)... */
        public int realCap = 250;
        /** ...moved up while the server keeps its ticks under tickTarget ms and down when it doesn't, within these. */
        public boolean autoCap = true;
        public int capMin = 120;
        public int capMax = 800;
        public double tickTarget = 40;
        /** Zombies out in the open become real within this many blocks of a player. */
        public double streetRange = 96;
        /** Zombies indoors become real within this many blocks of a player... */
        public double indoorRange = 32;
        /** ...and this many blocks up or down (about two floors), so a tower wakes floor by floor. */
        public double indoorFloors = 9;
        /** Real zombies left idle this far beyond those ranges go back to being records. */
        public double foldMargin = 16;
        /** How many become real in one go (twice a second), so a crowd arrives without a stall. */
        public int perStep = 24;
        /** Share of houses (in towns, suburbs and farms) with nobody left in them. */
        public double emptyHouses = 0.5;
        /** In-game days before a cleared place fills again; 0 never (Zomboid's respawn setting). */
        public int respawnDays = 0;
        /** The dead out in the open past the real ones are drawn as a still crowd (pictures, no cost to the server)... */
        public boolean crowd = true;
        /** ...this far round each player (blocks; the server's view distance bounds it too)... */
        public double crowdRange = 192;
        /** ...at most this many, nearest first, and none nearer than this (those are real, or about to be). */
        public int crowdMax = 3000;
        public double crowdMinDistance = 40;
    }

    public static class Cases {
        /** A found case comes packed with a gun, magazines and rounds, a can with rounds. Off, they're found empty. */
        public boolean packed = true;
        /** How often a rifle case holds each long gun, against the others. */
        public int assaultRifleWeight = 4;
        public int huntingRifleWeight = 4;
        public int shotgunWeight = 3;
        public int doubleBarrelWeight = 2;
        public int rimfireWeight = 4;
        public int smgWeight = 1;
        public int sawnOffWeight = 2;
        public int leverActionWeight = 3;
        public int crossbowWeight = 1;
        /** How often a pistol case holds each handgun, against the others. */
        public int pistolWeight = 3;
        public int revolverWeight = 2;
        public int magnumWeight = 1;
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

    public static class NightVision {
        /** Goggles need an AA battery. Off, they never run down. */
        public boolean batteries = true;
        /** How long one AA battery runs the goggles, in minutes of use. */
        public double batteryMinutes = 30;
        /** Goggles found in the world have a part-used battery somewhere between these fractions of full. */
        public double foundChargeMin = 0.2;
        public double foundChargeMax = 0.8;
        /** How often the battery used is written to the goggles, in seconds (lower saves more often). */
        public int saveEverySeconds = 10;
        /**
         * Draws the view through the tube (green or white phosphor, grain, the round tube edge) as a screen effect.
         * Off, a plain tint and the tube edge are drawn over the screen instead, for shader packs that hide the effect.
         * Client side.
         */
        public boolean tubeEffect = true;
        /** How much of the screen's height the tube fills, 0.5 to 1.2. Client side. */
        public double tubeSize = 0.92;
        /** How bright the view through the tube is, 0.2 to 2 (1 is the preview's look). Client side. */
        public double brightness = 0.85;
    }

    public static class Radio {
        /** How far each radio carries a voice, in blocks: the talking radio's reach is what counts. */
        public double toyRange = 150;
        public double twoWayRange = 1000;
        public double militaryRange = 3000;
        /** How many channels each radio tunes to. Channel numbers are shared between them. */
        public int toyChannels = 3;
        public int twoWayChannels = 22;
        public int militaryChannels = 99;
        /** How far into the reach (0 to 1) the voice starts to crackle and break up. */
        public double crackleStart = 0.65;
        /** The hiss under every radio voice, 0 to 0.1. */
        public double hiss = 0.012;
        /** How far a radio's speaker carries for other players, in blocks. */
        public double speakerRange = 12;
        /** How far zombies hear a radio's speaker, in blocks (0: they don't). */
        public double speakerNoise = 10;
        /** Talking needs the radio in a hand, unless you wear a comms headset. Off: any radio on you will do. */
        public boolean holdToTalk = true;
        /** Radios need an AA battery. Off, they never run down. */
        public boolean batteries = true;
        /** How long one AA battery runs a radio that's switched on, in minutes. */
        public double batteryMinutes = 120;
        /** How many times faster the battery drains while you talk. */
        public double talkDrain = 3;
        /** Radios found in the world have a part-used battery somewhere between these fractions of full. */
        public double foundChargeMin = 0.2;
        public double foundChargeMax = 0.8;
        /** How often the battery used is written to the radios, in seconds. */
        public int saveEverySeconds = 10;
        /** How loud gunshots are through a comms headset, 0 to 1. Client side. */
        public double headsetGunshotVolume = 0.45;
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
        /** The magnum: blued with rosewood target stocks, or nickel with exhibition walnut. */
        public int magnumBlued = 75;
        public int magnumNickel = 25;
        /** The lever gun: blued with plain walnut, or colour case hardened with figured walnut. */
        public int leverActionField = 75;
        public int leverActionCaseHardened = 25;
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
        /**
         * Quick slots come from what you wear: hands and back always, the hip with a belt, pouches with a duty or tool
         * belt, a holster and a sheath slot with those. Off gives the plain nine-slot hotbar.
         */
        public boolean quickSlots = true;
    }

    public static class Zombies {
        /** Share of zombies that sprint once they spot you (Zomboid's "random" speed mix): about one in seven. */
        public double sprinterChance = 0.15;
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
        /**
         * Hits a shut door takes before it gives way, shared by everyone pounding on it: one zombie hits about once a
         * second, so alone it takes two minutes and ten of them about twelve seconds.
         */
        public int doorHits = 120;
        /** The same for a window's glass. */
        public int windowHits = 45;
        /** Zombies climb in (and out) through windows whose glass is gone. */
        public boolean climbWindows = true;
        /** A zombie that can't get to you goes round to the nearest door or window to break in. */
        public boolean seekWayIn = true;
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
        /** The old steady spawner round each player; off since the world's population took over (population). */
        public boolean enabled = false;
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

    public static class Building {
        /** Blows one board stands before it comes loose, and how many more each step of Carpentry adds (reinforced, heavy). */
        public int boardHits = 6;
        public int boardHitsPerStrength = 3;
        /** Blows a metal sheet stands. */
        public int metalSheetHits = 32;
        /** How far zombies hear a tripwire rattle, in blocks. */
        public double tripwireNoise = 14;
        /** Chance each random tick in the rain that a rain barrel fills an eighth more (double in a storm). */
        public double rainBarrelFill = 0.5;
        /** Builds take this many times their usual time. */
        public double timeMultiplier = 1.0;
        /** How far zombies hear a ringing alarm clock, in blocks, and how long it rings, in seconds. */
        public double alarmClockNoise = 40;
        public int alarmClockRingSeconds = 25;
        /** How far a car key's panic button reaches, in blocks, and how long the alarm sounds, in seconds. */
        public double keyFobRange = 40;
        public int carAlarmSeconds = 40;
        /** In-game days a planter takes from seed to ripe while kept watered, and days a watering lasts. */
        public double planterGrowDays = 4;
        public double planterWaterDays = 1;
        /** Blows a spiked log wall stands, and the damage its spikes do to a zombie pushing or beating on it. */
        public int spikedWallHits = 24;
        public float spikeDamage = 2f;
    }

    public static class Digging {
        /** Off, every block breaks the vanilla way and there's no floor. */
        public boolean rules = true;
        /** How deep under the natural ground you can dig, in blocks. Built places (basements, bunkers) don't count. */
        public int floorDepth = 8;
        /** The level a wall needs: wood with Carpentry, brick and concrete with Masonry, steel with Metalworking. */
        public int carpentryLevel = 2;
        public int masonryLevel = 2;
        public int metalworkingLevel = 2;
        /** How far zombies hear each kind of wall coming down, in blocks. */
        public double woodNoise = 16;
        public double masonryNoise = 36;
        public double metalNoise = 14;
        /** Endurance a wall costs to knock through (a sprint uses about 1 a second). */
        public double woodEndurance = 8;
        public double masonryEndurance = 14;
        public double metalEndurance = 5;
        /** Seconds to break a wall at its needed level; every level more takes 12% off. */
        public double woodSeconds = 3;
        public double masonrySeconds = 5;
        public double metalSeconds = 6;
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
        /** The jets' top speed in km/h. Past about 300 the world can't load fast enough to keep up with them. */
        public double jetTopSpeed = 300;
        /** The helicopter's top speed in km/h. */
        public double heliTopSpeed = 250;
        /** Aircraft are found with little fuel, a flat battery and worn out, and burn fuel as they fly; off = ready to fly. */
        public boolean aircraftService = true;
        /** Air-raid sirens, far-off explosions and jets and helicopters crossing the map, heard by everyone (WorldEvents). */
        public boolean worldEvents = true;
        /** About how many minutes between those events. */
        public double worldEventMinutes = 10;
        /**
         * Air strikes among those events (Josiah 2026-10-28): a jet strafing or bombing a crowd of zombies, or the
         * Apache circling a horde shooting them; zombies caught die, players right there are hurt, blocks never.
         */
        public boolean airStrikes = true;
        /** The share of the events that are air strikes, when there's a crowd of at least airStrikeCrowd zombies near. */
        public double airStrikeShare = 0.35;
        public int airStrikeCrowd = 4;
        /** Shells a second: the jets' rotary cannon and the Apache's chain gun. */
        public double strikeCannonRate = 50;
        public double strikeChainGunRate = 10;
        /** A bomb kills zombies within strikeBombKill blocks and hurts players within strikeBombHurt, up to strikeBombDamage. */
        public double strikeBombKill = 9;
        public double strikeBombHurt = 7;
        public double strikeBombDamage = 30;
        /**
         * Where a car's key is, as shares: left in the ignition, in the glovebox, on a zombie that dies near the car
         * (each death nearby has carKeyDrop of being the one); the rest are lost, and the car has to be hotwired.
         */
        public double carKeyIgnition = 0.1;
        public double carKeyGlovebox = 0.3;
        public double carKeyZombie = 0.35;
        public double carKeyDrop = 0.3;
        /**
         * Hotwiring: seconds fiddling under the dash, and the chance it only sparks and you start again in a car in
         * perfect condition; hotwireWear is how much of the remaining chance a car at 0% adds (worse cars fail more).
         */
        public double hotwireSeconds = 8;
        public double hotwireFail = 0.25;
        public double hotwireWear = 0.8;
        /** How big a player is while in a car or aircraft (1 = full size): a little smaller so arms and legs stay inside the cabin. */
        public double carRiderScale = 0.85;
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
        /** Scales how often downpours, fog, wind and storms come; 0 keeps a steady rain. */
        public double eventFrequency = 1.0;
        /** Scales how often storms come, and how many of them turn dangerous. */
        public double stormFrequency = 1.0;
        /** Thick fog now and then, as its own weather or rolling in over the rain. */
        public boolean fog = true;
        /** The rain never eases below this (0..1); 0.55 is moderate rain, 1 a downpour. */
        public double minRain = 0.55;
        /** Fog rolling in and out on its own over any weather, from clear to a thick bank; 0 turns it off, 0.5 halves it. */
        public double rollingFog = 1.0;
        /** Mist you can see under shader packs: banks drifting with the wind in fog, low ground mist at night. Client side. */
        public boolean mist = false;
        /** How thick the mist gets, as a share; 0.5 halves it. Client side. */
        public double mistAmount = 1.0;
        /** Lightning flickering over the skyline in storms, the odd bolt between the towers, thunder after. Client side. */
        public boolean stormFlashes = true;
        /** Paper, leaves and plastic blown down the street when the wind picks up. Client side. */
        public boolean windDebris = true;
        /** Rain dripping off ledges and awnings, downpipes pouring, splashes underfoot, leaks under the roof. Client side. */
        public boolean drips = true;
        /** Fires and smoke columns burning far off on the skyline, and smoulder where lightning struck. Client side. */
        public boolean skylineFires = true;
        /** A close strike makes the lamps and neon round you flicker, sometimes browning out for a few seconds. Client side. */
        public boolean stormSurges = true;
        /** Rain sparkling in the colour of every lamp it falls past. Client side. */
        public boolean wetGlints = true;
        /** Now and then the dead grid twitches: streetlights, signs and windows round you stutter on for a while, then die. Client side. */
        public boolean ghostPower = true;
        /** Scales the wait between ghost power surges (2 to 6 minutes); 0.5 is twice as often. Client side. */
        public double ghostPowerEvery = 1.0;
        /** Thunder and wind set off car alarms and shop alarms near you in storms, and the dead come to the noise. Off for now. */
        public boolean stormAlarms = false;
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
        /** The new zombie bodies: Zomboid-shaped, in worn 3D clothes, with the new movements. Off draws the older model. Client side. */
        public boolean newZombies = true;
        /** Blocks within which zombies show every small clothing detail; further out the smallest bits are left off, and past 2.5 times this only the main shapes stay. Client side. */
        public double zombieDetailDistance = 16;
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
        /**
         * Third person (F5) is played over the shoulder: the camera close behind you and to one side, a crosshair on
         * exactly what your shots and hands will hit, the shoulder swapped with O. Off gives Minecraft's own. Client side.
         */
        public boolean shoulderCam = true;
        /** Aiming a 4x scope over the shoulder looks through it in first person, and goes back after. */
        public boolean scopeFromShoulder = true;
        /** From the hip with a gun, the crosshair is a dot in a ring the size of the gun's spread right now. Client side. */
        public boolean spreadCrosshair = true;
        /**
         * How far behind, to the side and above your eyes the shoulder camera sits (blocks), so you're left of the
         * crosshair with the way ahead open and the camera looking down on you a little. Client side.
         */
        public double shoulderBack = 1.4;
        public double shoulderSide = 0.9;
        public double shoulderUp = 0.55;
        /** Aiming a gun brings it in closer: how far behind and to the side then (blocks). Client side. */
        public double shoulderAimBack = 0.75;
        public double shoulderAimSide = 0.7;
        public double shoulderAimUp = 0.35;
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
        /**
         * Zombies die like Zomboid's: a bullet anywhere but the head (on one still standing) does this much of its
         * damage, so body hits slow them and headshots kill; hand-to-hand blows and stomps on them do zombieMelee.
         */
        public double zombieBodyShots = 0.33;
        /**
         * Only a shot to the head kills a zombie: body and limb shots wear it down and take it apart, but one with
         * every limb shot off still wriggles on the ground, and a crawler keeps coming, until it's shot in the head.
         * Any bullet or bolt in the head finishes it. Blows, stomps, fire and explosions kill as before.
         */
        public boolean onlyHeadshotsKill = true;
        public double zombieMelee = 0.5;
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
        /** Share of zombies found with no legs, dragging themselves along on their arms. */
        public double leglessSpawnChance = 0.03;
        /** Chance a knockdown from a heavy weapon or gunshot leaves a zombie crawling. */
        public double crippleChance = 0.2;
    }

    public static class Corpses {
        /**
         * A dead zombie falls the way it was going and the shot pushed it, and its body stays lying where it fell,
         * missing whatever was shot off it (off: vanilla's tip-over and puff of smoke).
         */
        public boolean enabled = true;
        /** Most bodies kept in a world while it's loaded; past this the oldest go. */
        public int maxBodies = 300;
        /** Minutes a body lies before it's gone (0 = it stays for good). */
        public double minutes = 0;
        /** Living zombies jerk and rock back where shots hit them. */
        public boolean hitReactions = true;
        /** How far off bodies are drawn, in blocks. Client side. */
        public double drawDistance = 64;
    }

    public static class Limbs {
        /** A bullet that hits an arm or a leg instead of the head can tear it off, where it was hit. */
        public boolean enabled = true;
        /**
         * How much a forearm (with the hand) takes before it tears off at the elbow, in gun damage: each hit adds the
         * bullet's or pellet's damage times its kind of gun's tearing below.
         */
        public double forearm = 20;
        /** How much an upper arm takes before the whole arm tears off at the shoulder. */
        public double arm = 28;
        /** How much a shin (with the foot) takes before it tears off at the knee. */
        public double shin = 24;
        /** How much a thigh takes before the whole leg tears off at the hip. */
        public double leg = 34;
        /** Tearing per pellet of buckshot: a shotgun up close takes a limb off nearly every time. */
        public double shotguns = 2.0;
        /**
         * Tearing for handgun rounds (pistol, revolver, .44 magnum, SMG) and the .22: they mostly go clean through,
         * though the magnum hits hard enough to take a limb in a few.
         */
        public double handguns = 0.8;
        /** Tearing for the assault rifle: a few hits take a limb. */
        public double rifles = 1.0;
        /** Tearing for the hunting rifle and lever-action: one hit can take a limb. */
        public double huntingRifles = 1.4;
        /** Tearing for crossbow bolts: a bolt pins a limb more than it tears it. */
        public double crossbows = 0.3;
        /** Tearing for any other gun. */
        public double otherGuns = 1.0;
        /** How fast a zombie hopping on one leg gets about, against walking (1 = as fast). */
        public double hopSpeed = 0.55;
        /**
         * How fast a zombie on the ground gets about with one arm left to drag itself, and with none (it wriggles
         * along on its belly), against crawling on both (1 = as fast).
         */
        public double oneArmCrawl = 0.65;
        public double wriggleSpeed = 0.3;
        /** Seconds a torn-off limb lies on the ground before it's gone. Client side. */
        public double limbSeconds = 60;
        /** Most torn-off limbs lying about at once; the oldest go first. Client side. */
        public int maxLimbs = 64;
        /**
         * Shows the shooter what each shot hit, over the hotbar: the part, how far a limb is to coming off (taken /
         * toughness), and OFF when it goes.
         */
        public boolean showHits = false;
        /**
         * How many ticks behind the server a shooter sees zombies, on top of their ping (the game sends a zombie's
         * moves every few ticks and glides it there over a few more): a shot is checked against where it was then.
         */
        public int lagTicks = 4;
        /** A big enough hit to the head bursts it: a hunting rifle headshot, or a clean blast of buckshot up close. */
        public boolean headBursts = true;
        /**
         * What one shot or blast must put into a head to burst it: its headshot damage (after buckshot falls off with
         * range) times the gun's bursting below.
         */
        public double headBurst = 240;
        /** Bursting per pellet of buckshot: three pellets in the head up close (a clean blast) do it, two don't. */
        public double burstShotguns = 10.0;
        /** Bursting for the bolt-action hunting rifle: every headshot does it. */
        public double burstHuntingRifle = 4.0;
        /** Bursting for the lever-action (0 = never; about 4 makes every headshot do it). */
        public double burstLeverAction = 0;
        /** Bursting for the .44 magnum (0 = never; about 5 makes every headshot do it). */
        public double burstMagnum = 0;
        /** Bursting for every other gun (0 = never). */
        public double burstOtherGuns = 0;
        /**
         * Pipe bombs and grenades tear limbs off too: the ones facing the blast and nearest it first, and right on top
         * of one the head sometimes bursts.
         */
        public boolean blasts = true;
        /**
         * What a blast puts into each limb right on top of it, facing it, against the toughness above (less as it's
         * farther off or turned away, give or take a quarter): 60 takes whole limbs up close, a forearm or a shin at
         * about half strength, and little near the edge.
         */
        public double blastTearing = 60;
        /** The chance a blast right on top of a walker bursts its head (less with it below the blast). */
        public double blastHeadBurst = 0.35;
        /** How close a blast must be to burst a head at all: its strength, 0 at the edge of its reach to 1 on top. */
        public double blastHeadBurstFrom = 0.6;
        /**
         * Buckshot from point-blank range with most of the blast in the chest blows a hole right through it (with only
         * headshots killing, it keeps coming).
         */
        public boolean chestHoles = true;
        /** How close counts as point-blank for that, in blocks. */
        public double chestHoleRange = 3.0;
        /** The share of the blast's pellets that must go into the chest: more than this. */
        public double chestHoleShare = 0.5;

        /** The first toughness numbers (24, 34, 28, 40), left as they were, take the easier ones of 27 October. */
        void retune() {
            if (forearm == 24 && arm == 34 && shin == 28 && leg == 40) {
                Limbs fresh = new Limbs();
                forearm = fresh.forearm;
                arm = fresh.arm;
                shin = fresh.shin;
                leg = fresh.leg;
            }
        }
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
        /** The gun has a full-auto setting, switched with the fire mode key. */
        public boolean selectFire = false;
        /**
         * How fast it fires on full auto, in rounds a minute, held steady shot to shot. Every automatic gun has its
         * own: an SMG runs faster than an assault rifle.
         */
        public double autoRpm = 600.0;
        /** Multiplies the suppressed shot's pitch (below 1 deepens it). */
        public double suppressedPitch = 1.0;
        /** Multiplies the kick of each shot on full auto (the gun is held braced for it). */
        public double autoRecoil = 0.7;
        /**
         * Spread bloom: each shot opens the spread by this much (degrees; full auto by its own, usually less, as the
         * shots pile up), it closes again at bloomRecover degrees a second, and full auto never opens it past
         * autoBloomMax. The crosshair's ring shows it, and the shots land inside the ring.
         */
        public double shotBloom = 0.5;
        public double autoBloomPerShot = 0.45;
        public double autoBloomMax = 3.5;
        public double bloomRecover = 3.0;

        static GunStats pistol() {
            GunStats s = new GunStats();
            s.damage = 7.0;
            s.hipSpread = 1.5;
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

        /**
         * The .30-30 lever gun: a deer round, short of the .308 in reach and punch but far quicker to work, six in the
         * tube; a sharp kick and a loud bark.
         */
        static GunStats leverAction() {
            GunStats s = new GunStats();
            s.damage = 22.0;
            s.headshotMultiplier = 3.0;
            s.hipSpread = 1.9;
            s.aimSpread = 0.3;
            s.recoil = 1.9;
            s.shotNoise = 130.0;
            s.aimZoom = 0.84;
            s.range = 140.0;
            s.fireDelayTicks = 9;      // never less than the lever takes to work (GunType.fireDelay)
            s.magazineSize = 6;
            return s;
        }

        /** The .22: a light round that needs a good hit, a whisper of a kick, quiet, accurate, ten in its magazine. */
        static GunStats rimfire() {
            GunStats s = new GunStats();
            s.damage = 7.0;
            s.headshotMultiplier = 3.5;
            s.hipSpread = 3.0;
            s.aimSpread = 0.25;
            s.recoil = 0.3;
            s.shotNoise = 45.0;
            s.aimZoom = 0.82;
            s.range = 100.0;
            s.magazineSize = 10;
            return s;
        }

        /** The 9mm submachine gun: the pistol's round from a stocked gun, thirty of them, semi or full auto. */
        static GunStats smg() {
            GunStats s = new GunStats();
            s.damage = 7.0;
            s.hipSpread = 1.3;
            s.aimSpread = 0.6;
            s.recoil = 0.55;
            s.shotNoise = 75.0;
            s.aimZoom = 0.85;
            s.range = 70.0;
            s.magazineSize = 30;
            s.selectFire = true;
            s.autoRpm = 950.0;      // an SMG's light bolt cycles faster than the rifle's 600
            s.autoBloomPerShot = 0.3;     // lighter round, but nearly sixteen a second
            s.autoBloomMax = 4.0;
            return s;
        }

        /** The .44 magnum: a heavy, hard-kicking round that stops most things it hits, six of them, heard far off. */
        static GunStats magnum() {
            GunStats s = new GunStats();
            s.damage = 18.0;
            s.hipSpread = 3.0;
            s.aimSpread = 0.45;
            s.recoil = 2.4;
            s.shotNoise = 125.0;
            s.aimZoom = 0.86;
            s.range = 80.0;
            s.fireDelayTicks = 8;
            s.magazineSize = 6;
            return s;
        }
    }

    /**
     * The pump shotgun's numbers: each shot is a spread of buckshot pellets, deadly close up and weak far off. Its
     * damage is per pellet, its magazine size is the shells it holds (four in the tube and one chambered), and its
     * time between shots covers working the pump.
     */
    /**
     * The crossbow: one heavy bolt at a time, silent but for the string, slow to cock. Its bolts are found again
     * where they land (in what they hit, under it), unless they break.
     */
    public static class CrossbowStats extends GunStats {
        /** Chance a bolt breaks when it hits something, rather than being left to pick up. */
        public double boltBreakChance = 0.2;
        /** Bolts a zombie can carry standing in it before more just fall at its feet. */
        public int maxStuckBolts = 6;

        CrossbowStats() {
            damage = 30.0;
            headshotMultiplier = 3.0;
            hipSpread = 1.8;
            aimSpread = 0.2;
            recoil = 0.4;
            shotNoise = 8.0;
            aimZoom = 0.85;
            range = 60.0;
            fireDelayTicks = 10;
            magazineSize = 1;
        }
    }

    public static class ShotgunStats extends GunStats {
        /** Pellets in each shell (00 buckshot has eight or nine). */
        public int pellets = 8;
        /** How far the pellets spread from where the shot goes, in degrees. */
        public double pelletSpread = 2.5;
        /** Pellets hit full strength out to this many blocks, then weaken to a quarter at the gun's range. */
        public double falloffStart = 10.0;
        /** How much a close hit checks a zombie's advance: its flinch lasts 5 ticks plus ten times this (it isn't thrown back). */
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

        /**
         * The sawn-off: the double's two shells from barrels cut short, so the shot spreads wide and weakens fast; a
         * brutal kick. Its damage is per pellet.
         */
        static ShotgunStats sawnOff() {
            ShotgunStats s = new ShotgunStats();
            s.damage = 3.8;
            s.pellets = 9;
            s.pelletSpread = 4.5;
            s.falloffStart = 6.0;
            s.knockback = 0.55;
            s.hipSpread = 2.5;
            s.aimSpread = 1.2;
            s.recoil = 3.0;
            s.shotNoise = 130.0;
            s.aimZoom = 0.92;
            s.range = 25.0;
            s.fireDelayTicks = 5;
            s.magazineSize = 2;
            return s;
        }
    }

    /** Settings every gun shares, and the assault rifle's own numbers. */
    public static class Guns extends GunStats {
        {
            selectFire = true;      // the assault rifle has semi and full auto
            hipSpread = 1.8;        // Josiah 10-27: hip rings 40% tighter at rest
            suppressedPitch = 0.88;     // Josiah 10-27: its suppressed shot a little deeper
        }

        public boolean enabled = true;
        /** Worn guns sometimes jam; clear them with the reload key. */
        public boolean jams = true;
        /**
         * Jam chance per shot of a gun in new condition, and how much is added at no condition left (rising with the
         * square of the wear), both before its reliability roll. 0.001 and 0.015: about 1 magazine in 30 jams on a
         * new gun, 1 in 8 at half condition, 1 in 3 nearly worn out.
         */
        public double jamNew = 0.001;
        public double jamWorn = 0.015;
        /** Guns are modular: optics, muzzle devices, grips, lights, lasers and stocks go on and come off. */
        public boolean attachments = true;
        /** Multiply aimed spread with each optic mounted. */
        public double redDotAimSpread = 0.6;
        public double holoAimSpread = 0.6;
        public double scopeAimSpread = 0.35;
        /** Field of view looking through the 4x scope, as a share of normal. */
        public double scopeZoom = 0.25;
        /** A suppressor multiplies how far zombies hear the shot; the suppressed recordings play at this volume. */
        public double suppressedNoise = 0.1;
        public double suppressedVolume = 0.7;
        /**
         * Seconds others see a gun stay raised (pointing where its carrier looks) after a shot, when they're in first
         * person; over the shoulder it's always up.
         */
        public double raiseAfterShot = 3.0;
        /** A foregrip multiplies the kick; a gun with its stock off kicks and wanders more. */
        public double foregripRecoil = 0.75;
        public double noStockRecoil = 1.5;
        public double noStockSpread = 1.6;
        /** A laser multiplies spread from the hip. */
        public double laserHipSpread = 0.6;
        /** Smoke and a spark at the muzzle after each shot (off by default: they hid the sights). */
        public boolean muzzleSmoke = false;
        /** How often a glancing shot off something hard whines away as a ricochet (0 to 1). */
        public double ricochetChance = 0.35;
        /** The shooter hears their round whizz into a zombie they hit within this many blocks (0: never). */
        public double hitWhizzRange = 48;
        /** Bullet holes, dust and chips where a shot hits a block, and how long the holes stay (seconds). Client side. */
        public boolean bulletHoles = true;
        public double bulletHoleSeconds = 90.0;
        /** Blood sprays from a hit and splashes the wall or floor behind it; how long the splashes stay. Client side. */
        public boolean bloodSplatter = true;
        public double bloodSeconds = 120.0;
        /** How many holes and splashes stay at once; the oldest go first. Client side. */
        public int maxDecals = 300;
        /**
         * Bullets go through glass (a little spent) and leave holes in it; this many holes and it shatters: small
         * and leaded panes, a house window, big panes (ship, roof), shop and office plate glass, glass bricks.
         */
        public int glassHolesSmall = 2;
        public int glassHolesWindow = 3;
        public int glassHolesLarge = 4;
        public int glassHolesShop = 5;
        public int glassHolesBlock = 6;
        /** A short, soft flash at the muzzle with each shot (a smaller one at the end of a suppressor). */
        public boolean muzzleFlash = true;
        /** How much the gun bobs and sways on the sights: 1 = as much as at the hip, 0 = dead still. Client side. */
        public double aimSway = 0.15;
        /** First person animates reloads: the old magazine drops away, a new one goes in, the charging handle is worked on an empty gun. Client side. */
        public boolean reloadAnimation = true;
        /** A faint green beam shows in the air between a laser and its dot. Client side. */
        public boolean laserBeam = true;
        /** How strongly the laser's beam shows (with a shader pack: how much dust it lights). Client side. */
        public double laserBeamStrength = 1.0;
        /** How big the laser's dot is: 1 = a few pixels across at any range. Client side. */
        public double laserDotSize = 1.0;
        /**
         * From the hip with only the light on, how far ahead (blocks) the gun is turned to point its light at the
         * middle of the view. The light then shines straight down the barrel. Client side.
         */
        public double lightZero = 12;
        /**
         * How bright (a light level, 1 to 15) a gun light lights the space it's pointed at, a level less every eight
         * blocks off. Higher lights a wider patch round it. Client side.
         */
        public int lightLevel = 13;
        /**
         * How many hidden lights a gun light uses (1 to 8): one where it lands and faint ones on the way there. More
         * light the floor and walls between better, but cost frames when you turn. Client side.
         */
        public int lightSources = 5;
        /**
         * Hidden lights spread round the middle one, where rays out across the light's spread land (0 to 24), so it
         * lights the whole space in front of you rather than one patch. More cost frames when you turn. Client side.
         */
        public int lightRays = 14;
        /** How far those rays spread from the middle, in degrees. Client side. */
        public double lightSpread = 22;
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

    /**
     * Which round of changed defaults this file has had. A saved file keeps its old numbers, so when a default is
     * changed on purpose, bump this and move the old default over in {@link #upgrade} (values someone set themselves
     * are left alone).
     */
    public int defaultsVersion = 0;
    private static final int DEFAULTS = 14;

    private static void upgrade(ZcConfig c) {
        if (c.defaultsVersion < 1) {
            // 2026-10-27: the assault rifle 700 -> 600 a minute, its suppressed shot deeper
            if (c.guns.autoRpm == 700.0) {
                c.guns.autoRpm = 600.0;
            }
            if (c.guns.suppressedPitch == 1.0) {
                c.guns.suppressedPitch = 0.88;
            }
        }
        if (c.defaultsVersion < 2) {
            // 2026-10-27: the shoulder camera closer in (Josiah: "the player model needs to be closer")
            if (c.look.shoulderBack == 1.9) {
                c.look.shoulderBack = 1.25;
            }
            if (c.look.shoulderSide == 0.6) {
                c.look.shoulderSide = 0.5;
            }
            if (c.look.shoulderAimBack == 1.05) {
                c.look.shoulderAimBack = 0.7;
            }
            if (c.look.shoulderAimSide == 0.5) {
                c.look.shoulderAimSide = 0.42;
            }
        }
        if (c.defaultsVersion < 3) {
            // 2026-10-27: with the baked far-off bodies, fewer zombies need the costly middle detail
            if (c.performance.midDetailZombies == 32) {
                c.performance.midDetailZombies = 20;
            }
        }
        if (c.defaultsVersion < 4) {
            // 2026-10-27 (Josiah): suppressors for sneaking (zombies hear a third as far, 30% quieter), and the hip
            // rings of the assault rifle, SMG, crossbow, M9 and lever action 40% tighter
            if (c.guns.suppressedNoise == 0.3) {
                c.guns.suppressedNoise = 0.1;
            }
            if (c.guns.suppressedVolume == 1.0) {
                c.guns.suppressedVolume = 0.7;
            }
            if (c.guns.hipSpread == 3.0) {
                c.guns.hipSpread = 1.8;
            }
            if (c.smg.hipSpread == 2.2) {
                c.smg.hipSpread = 1.3;
            }
            if (c.crossbow.hipSpread == 3.0) {
                c.crossbow.hipSpread = 1.8;
            }
            if (c.pistol.hipSpread == 2.5) {
                c.pistol.hipSpread = 1.5;
            }
            if (c.leverAction.hipSpread == 3.2) {
                c.leverAction.hipSpread = 1.9;
            }
        }
        if (c.defaultsVersion < 5) {
            // 2026-10-27 (Josiah): the shoulder camera up and further right, so you're left of the crosshair and the
            // camera looks down on you a little
            if (c.look.shoulderBack == 1.25) {
                c.look.shoulderBack = 1.4;
            }
            if (c.look.shoulderSide == 0.5) {
                c.look.shoulderSide = 0.9;
            }
            if (c.look.shoulderUp == 0.15) {
                c.look.shoulderUp = 0.55;
            }
            if (c.look.shoulderAimBack == 0.7) {
                c.look.shoulderAimBack = 0.75;
            }
            if (c.look.shoulderAimSide == 0.42) {
                c.look.shoulderAimSide = 0.7;
            }
        }
        if (c.defaultsVersion < 6) {
            // 2026-10-27 (Josiah): the gun light brighter, now spread over the space in front (lightRays)
            if (c.guns.lightLevel == 11) {
                c.guns.lightLevel = 13;
            }
        }
        if (c.defaultsVersion < 7) {
            // 2026-10-27 (Josiah): more sprinters, one in seven instead of one in twenty
            if (c.zombies.sprinterChance == 0.05) {
                c.zombies.sprinterChance = 0.15;
            }
        }
        if (c.defaultsVersion < 8) {
            // 2026-10-27 (Josiah): the world's population replaces the steady spawner, and doors hold far longer, the
            // more zombies on them the sooner they give
            c.spawning.enabled = false;
            if (c.zombies.doorHits == 12) {
                c.zombies.doorHits = 120;
            }
        }
        if (c.defaultsVersion < 9) {
            // 2026-10-27 (Josiah): trains a bit slower, and OTTO talks more often between stops
            if (c.trains.railSpeed == 13) {
                c.trains.railSpeed = 10;
            }
            if (c.trains.subwaySpeed == 11) {
                c.trains.subwaySpeed = 9;
            }
            if (c.trains.ottoChatter == 75) {
                c.trains.ottoChatter = 45;
            }
        }
        if (c.defaultsVersion < 10) {
            // 2026-10-27 (Josiah): shorter railway trains, three cars with OTTO's
            if (c.trains.railCars == 4) {
                c.trains.railCars = 3;
            }
        }
        if (c.defaultsVersion < 11) {
            // 2026-10-28 (Josiah waited ages at a stop): one train on the long county loop came round about every
            // half hour; now a train every 1500 blocks of railway (900 of subway)
            if (c.trains.railTrains == 1) {
                c.trains.railTrains = 0;
            }
            if (c.trains.subwayTrains == 1) {
                c.trains.subwayTrains = 0;
            }
        }
        if (c.defaultsVersion < 12) {
            // 2026-10-28 (Josiah): the trips between stops took far too long; both lines about three times faster
            if (c.trains.railSpeed == 10) {
                c.trains.railSpeed = 30;
            }
            if (c.trains.subwaySpeed == 9) {
                c.trains.subwaySpeed = 28;
            }
            if (c.trains.acceleration == 0.6) {
                c.trains.acceleration = 1.5;
            }
            if (c.trains.braking == 0.75) {
                c.trains.braking = 1.8;
            }
            if (c.trains.curveGrip == 1.1) {
                c.trains.curveGrip = 2.0;
            }
            if (c.trains.expressSpeed == 2.5) {
                c.trains.expressSpeed = 1.3;
            }
        }
        if (c.defaultsVersion < 13) {
            // 2026-10-28 (Josiah): the ground mist cost too much under Photon in heavy fog; off unless turned back on
            c.weather.mist = false;
        }
        if (c.defaultsVersion < 14) {
            // 2026-10-28 (Josiah): double the train speed again
            if (c.trains.railSpeed == 30) {
                c.trains.railSpeed = 60;
            }
            if (c.trains.subwaySpeed == 28) {
                c.trains.subwaySpeed = 56;
            }
            if (c.trains.acceleration == 1.5) {
                c.trains.acceleration = 3;
            }
            if (c.trains.braking == 1.8) {
                c.trains.braking = 3.5;
            }
            if (c.trains.curveGrip == 2.0) {
                c.trains.curveGrip = 4.0;
            }
        }
        c.defaultsVersion = DEFAULTS;
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
                    upgrade(instance);
                    instance.limbs.retune();
                }
            } catch (IOException | RuntimeException e) {
                ZomboidCraft.LOGGER.error("Couldn't read {}, using defaults", FILE, e);
            }
        }
        instance.defaultsVersion = Math.max(instance.defaultsVersion, DEFAULTS);
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
