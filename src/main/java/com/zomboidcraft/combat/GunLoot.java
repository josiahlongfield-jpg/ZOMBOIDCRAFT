package com.zomboidcraft.combat;

import com.google.gson.Gson;
import com.zomboidcraft.Loot;
import com.zomboidcraft.ModItems;
import com.zomboidcraft.ZomboidCraft;
import com.zomboidcraft.world.county.Place;
import com.zomboidcraft.world.county.PlaceType;
import com.zomboidcraft.world.gen.CountyChunkGenerator;
import com.zomboidcraft.zombie.ZombieLook;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Where guns turn up, now our old ones are gone (Josiah 2026-11-02): Arsenal's own places for each Brita gun, and the
 * Heavy Sniper on the places of the CheyTac it stands in for. Reviewed on the gun loot check page (07:44): Arsenal's
 * rarity, its own crossover default, the US army, and as many guns a container as Arsenal's full set of 400 would give,
 * picked from the guns we have.
 *
 * <p>Arsenal's numbers live on this computer only, in config/zomboidcraft/guns/loot/places.json, written from the mod's
 * own files and sandbox settings by tools on his PC (never in the repo or the jar). Each place there has:
 * <ul>
 *   <li>count: the guns one container of that place holds on average, with Arsenal's whole set;</li>
 *   <li>guns: each of our guns' chance there in Arsenal (its kill switches, the region and the crossover applied);</li>
 *   <li>extras: its rounds and magazines, {item or mag, p, min, max}.</li>
 * </ul>
 * and rarity gives each gun's letter (C S R P G: ×1, ×½, ×¼, ×1/10, ×1/25 against the others). Without the file only the
 * Heavy Sniper is there, at its own small chances (below), never scaled up to a full set.
 *
 * <p>Registered before LootSettings, so the sandbox's loot rarity for guns thins these like the rest, and before Rolls,
 * which gives each its tier for the place.
 */
public final class GunLoot {
    public static final Path FILE = LocalGuns.DIR.resolve("loot").resolve("places.json");
    /** The Heavy Sniper's id in the file. */
    public static final String HEAVY = "HEAVY";

    /** Arsenal's places, as on the check page. */
    public static final String GUNSHOP = "gunshop", PAWN = "pawn", POLICE = "police", SWAT = "swat", ARMY = "army",
            SECURITY = "security", HUNTING = "hunting", SURPLUS = "surplus", STORAGE = "storage",
            CASE_PISTOL = "case_pistol", CASE_RIFLE = "case_rifle", CCW = "ccw",
            WORN_POLICE = "worn_police", WORN_ARMY = "worn_army", WORN_SECURITY = "worn_security";

    /**
     * The Heavy Sniper's chance a container with no Arsenal file: the CheyTac's places (home in the army, hunting and
     * storage; crossing over elsewhere), kept small, since there's nothing else to pick from.
     */
    private static final Map<String, Double> HEAVY_ALONE = Map.of(ARMY, 0.02, HUNTING, 0.004, STORAGE, 0.004,
            GUNSHOP, 0.002, SWAT, 0.002, SURPLUS, 0.002, PAWN, 0.001, POLICE, 0.001, SECURITY, 0.0005);

    private static final Map<String, Double> RARITY = Map.of("C", 1.0, "S", 0.5, "R", 0.25, "P", 0.1, "G", 0.04);

    /** Tables an army base's racks and crates roll: an armoury there gets the army's, not a shop's or a cellar's. */
    private static final Set<String> GUN_TABLES = Set.of("shop/guns", "shop/basement_guns", "shop/home_guns", "shop/pawn");

    /** The file's shape. */
    public static final class Data {
        public Map<String, Spot> places = new HashMap<>();
        public Map<String, String> rarity = new HashMap<>();
    }

    public static final class Spot {
        public double count;
        public Map<String, Double> guns = new HashMap<>();
        public List<Extra> extras = new ArrayList<>();
    }

    public static final class Extra {
        /** One of our items (a round), or */
        public String item;
        /** a gun's own magazine, by the gun's id. */
        public String mag;
        public double p;
        public int min = 1, max = 1;
    }

    private static final Gson GSON = new Gson();
    private static Data data;
    private static int dataGeneration = -1;

    private GunLoot() {
    }

    /** The file, read again when the guns folder is (/zc guns reload). Null without one. */
    private static synchronized @Nullable Data data() {
        if (dataGeneration != LocalGuns.generation()) {
            dataGeneration = LocalGuns.generation();
            data = null;
            if (Files.isRegularFile(FILE)) {
                try (Reader r = Files.newBufferedReader(FILE, StandardCharsets.UTF_8)) {
                    data = GSON.fromJson(r, Data.class);
                    ZomboidCraft.LOGGER.info("Gun loot: Arsenal's places for {} places", data == null ? 0 : data.places.size());
                } catch (Exception e) {
                    ZomboidCraft.LOGGER.warn("Gun loot: couldn't read {}: {}", FILE, e.toString());
                }
            }
        }
        return data;
    }

    // ------------------------------------------------------------------ where

    private static @Nullable Place placeAt(ServerLevel level, Vec3 at) {
        if (!(level.getChunkSource().getGenerator() instanceof CountyChunkGenerator)) {
            return null;
        }
        int x = (int) Math.floor(at.x), z = (int) Math.floor(at.z);
        for (Place p : CountyChunkGenerator.gen(level.getSeed()).county().placesNear(x, z, x, z, 0)) {
            return p;
        }
        return null;
    }

    /**
     * The table a fitting really rolls: a gun rack, gun cabinet or crate on an army base or the naval wharf rolls the
     * army's (the generator gave some of them a shop's). Called as it's first searched, so old worlds get it too.
     */
    public static ResourceKey<LootTable> redirect(ServerLevel level, BlockPos pos, ResourceKey<LootTable> table) {
        String path = table.identifier().getPath();
        if (ZomboidCraft.MOD_ID.equals(table.identifier().getNamespace()) && GUN_TABLES.contains(path)
                && Loot.guarded(level, Vec3.atCenterOf(pos))) {
            return ResourceKey.create(Registries.LOOT_TABLE, ZomboidCraft.id("shop/army"));
        }
        return table;
    }

    /** A car's boot table, given where it stands: an ordinary car or truck on an army base carries the army's kit. */
    public static String carTable(ServerLevel level, Vec3 at, String table) {
        return ("trunk".equals(table) || "work".equals(table)) && Loot.guarded(level, at) ? "military" : table;
    }

    /** Arsenal's place a loot table stands for here, or null where no guns or their rounds are. */
    static @Nullable String place(ServerLevel level, String path, @Nullable Vec3 at) {
        String place = switch (path) {
            case "shop/guns" -> GUNSHOP;
            case "shop/pawn" -> PAWN;
            case "shop/police", "car/police", "shop/story_police_blockade_shooting" -> POLICE;
            case "car/swat" -> SWAT;
            case "shop/army", "car/military" -> ARMY;
            case "shop/security" -> SECURITY;
            case "shop/home_guns" -> HUNTING;     // a hunter's gun cabinet, in town or on a farm
            case "shop/basement_guns", "car/survivor" -> SURPLUS;
            // shop/storage is every crate, box and counter's default, not a storage unit (the county has none yet), and
            // Arsenal stocks no farm containers: neither takes guns
            default -> null;
        };
        if (place != null && at != null && !ARMY.equals(place) && !path.startsWith("car/")) {
            Place p = placeAt(level, at);
            if (p != null && p.type == PlaceType.HIDEOUT) {
                return SURPLUS;     // a survivalist's bunker, whatever its racks were called
            }
        }
        return place;
    }

    /** Arsenal's place for a dead zombie's gun: slung or holstered on a uniform, else a handgun in a civilian's bag. */
    static String zombiePlace(@Nullable Entity dead) {
        String outfit = dead == null ? null : dead.getAttached(ZombieLook.OUTFIT);
        if (outfit == null) {
            return CCW;
        }
        if (outfit.startsWith("police")) {
            return WORN_POLICE;
        }
        if (outfit.startsWith("soldier") || outfit.startsWith("army")) {
            return WORN_ARMY;
        }
        return outfit.startsWith("security") ? WORN_SECURITY : CCW;
    }

    // ------------------------------------------------------------------ what

    /** A gun by its id in the file: a Brita gun kept on this computer, or the Heavy Sniper. Null if it isn't here. */
    private static @Nullable ItemStack gun(String id, boolean loaded) {
        if (HEAVY.equals(id)) {
            ItemStack gun = new ItemStack(ModItems.HEAVY_SNIPER);
            gun.set(GunState.TYPE, loaded ? new GunState(true, GunType.HEAVY_SNIPER.capacity(gun), false)
                    : new GunState(GunType.HEAVY_SNIPER.tube, 0, false));
            return gun;
        }
        LocalGuns.Spec spec = LocalGuns.get(id);
        if (spec == null || spec.type() == null) {
            return null;
        }
        ItemStack gun = LocalGuns.loaded(spec);
        if (!loaded) {
            gun.set(GunState.TYPE, new GunState(spec.type().tube, 0, false));     // put away with its magazine out
        }
        return gun;
    }

    private static boolean here(String id) {
        return HEAVY.equals(id) || LocalGuns.get(id) != null;
    }

    /**
     * What turns up in one container (or on one body) of a place: its guns, each unloaded unless worn, and the rounds
     * and magazines of ours that Arsenal puts there. An ammunition shelf or crate gets only those.
     */
    public static List<ItemStack> roll(String place, RandomSource random, boolean guns, boolean extras) {
        List<ItemStack> out = new ArrayList<>();
        Data d = data();
        boolean worn = place.startsWith("worn_") || CCW.equals(place);
        Spot spot = d == null ? null : d.places.get(place);
        if (spot == null) {
            Double alone = d == null ? HEAVY_ALONE.get(place) : null;
            if (guns && alone != null && random.nextDouble() < alone) {
                out.add(gun(HEAVY, worn));
            }
            return out;
        }
        Map<String, Double> pool = new HashMap<>();
        double total = 0;
        for (var e : spot.guns.entrySet()) {
            if (e.getValue() > 0 && here(e.getKey())) {
                double w = e.getValue() * RARITY.getOrDefault(d.rarity.getOrDefault(e.getKey(), "C"), 1.0);
                pool.put(e.getKey(), w);
                total += w;
            }
        }
        if (guns && total > 0) {
            int n = (int) Math.floor(spot.count) + (random.nextDouble() < spot.count - Math.floor(spot.count) ? 1 : 0);
            for (int i = 0; i < n; i++) {
                ItemStack g = gun(pick(pool, total, random), worn);
                if (g != null) {
                    out.add(g);
                }
            }
        }
        for (Extra x : extras ? spot.extras : List.<Extra>of()) {
            if (random.nextDouble() >= x.p) {
                continue;
            }
            if (x.mag != null) {
                ItemStack anyGun = here(x.mag) ? gun(x.mag, false) : null;
                GunType type = anyGun == null ? null : GunType.of(anyGun);
                if (type != null && type.magazine() != null) {
                    out.add(LocalGuns.magazineFor(anyGun, type));
                }
            } else if (x.item != null) {
                Item item = BuiltInRegistries.ITEM.getOptional(ZomboidCraft.id(x.item)).orElse(null);
                if (item != null && !Retired.id(x.item)) {
                    int count = x.min + (x.max > x.min ? random.nextInt(x.max - x.min + 1) : 0);
                    out.add(new ItemStack(item, Math.max(1, Math.min(count, new ItemStack(item).getMaxStackSize()))));
                }
            }
        }
        return out;
    }

    /** The gun a packed gun case was put away with, or null for one holding only rounds and magazines. */
    public static @Nullable ItemStack caseGun(boolean pistol, RandomSource random) {
        List<ItemStack> got = roll(pistol ? CASE_PISTOL : CASE_RIFLE, random, true, false);
        for (ItemStack s : got) {
            if (s.getItem() instanceof RifleItem) {
                return s;
            }
        }
        return null;
    }

    /** The rounds of the guns kept on this computer that none of ours fire: for ammo cans. */
    public static List<Item> localRounds() {
        List<Item> out = new ArrayList<>();
        for (LocalGuns.Spec s : LocalGuns.all()) {
            if (s.ammo != null && s.type() != null) {
                BuiltInRegistries.ITEM.getOptional(ZomboidCraft.id(s.ammo)).filter(i -> !out.contains(i)).ifPresent(out::add);
            }
        }
        return out;
    }

    private static String pick(Map<String, Double> pool, double total, RandomSource random) {
        double r = random.nextDouble() * total;
        String last = null;
        for (var e : pool.entrySet()) {
            last = e.getKey();
            r -= e.getValue();
            if (r < 0) {
                return last;
            }
        }
        return last;
    }

    public static void register() {
        LootTableEvents.MODIFY_DROPS.register((table, context, drops) -> {
            var key = table.unwrapKey().orElse(null);
            ServerLevel level = context.getLevel();
            if (key == null || level == null) {
                return;
            }
            String ns = key.identifier().getNamespace(), path = key.identifier().getPath();
            RandomSource random = context.getRandom();
            if (path.equals("entities/zombie") || path.equals("entities/husk") || path.equals("entities/zombie_villager")
                    || ZomboidCraft.MOD_ID.equals(ns) && path.startsWith("entities/") && path.contains("zombie")) {
                drops.addAll(roll(zombiePlace(context.getOptional(LootContextParams.THIS_ENTITY)), random, true, true));
                return;
            }
            if (!ZomboidCraft.MOD_ID.equals(ns)) {
                return;
            }
            Vec3 at = context.getOptional(LootContextParams.ORIGIN);
            String block = at == null || path.startsWith("car/") ? null
                    : BuiltInRegistries.BLOCK.getKey(level.getBlockState(BlockPos.containing(at)).getBlock()).getPath();
            String place = place(level, path, at);
            if (place != null) {
                // an ammunition shelf or crate holds rounds and magazines, not guns
                drops.addAll(roll(place, random, block == null || !block.contains("ammo"), true));
            }
        });
    }
}
