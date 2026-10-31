package com.zomboidcraft.zombie;

import com.zomboidcraft.world.county.Place;
import com.zomboidcraft.world.county.PlaceType;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

/**
 * Zomboid dresses the dead for where they died (the plan's "Outfits by place" table): a hospital's are mostly
 * patients and nurses, a police station's police and a few prisoners from the cells, a farm's farmers. A building is
 * known by its kit (the hospital's hs_ blocks, the sheriff's sh_, the fire station's fs_, the school's sc_, the
 * mall's ml_), anywhere else by the county place it stands in. Each mix lists outfits by percent; whatever is left
 * over goes to the general mix (null). Outfits we don't have yet (chef, mechanic, student, lab coat) stand in as the
 * nearest one we do.
 */
public final class PlaceOutfits {
    /** One outfit's share of a mix, percent. */
    private record Share(String outfit, int percent) {
    }

    private static final List<Share> HOSPITAL = mix("patient", 45, "nurse", 20, "doctor", 10, "security", 5);
    private static final List<Share> POLICE = mix("police", 55, "prisoner", 10);
    private static final List<Share> FIRE = mix("firefighter", 60);
    private static final List<Share> PRISON = mix("prisoner", 65, "security", 20);
    private static final List<Share> ARMY = mix("soldier", 75);
    private static final List<Share> QUARANTINE = mix("patient", 45, "soldier", 30);
    private static final List<Share> LABS = mix("doctor", 40, "soldier", 20, "security", 15);
    private static final List<Share> OFFICES = mix("office", 50, "security", 10);
    private static final List<Share> SHOPS = mix("civilian", 45, "hoodie", 25, "security", 10);
    private static final List<Share> SCHOOL = mix("hoodie", 50, "office", 15);
    private static final List<Share> WORKS = mix("construction", 55);
    private static final List<Share> FARM = mix("farmer", 60);
    private static final List<Share> PARK = mix("jogger", 25);
    private static final List<Share> AIRPORT = mix("security", 15, "office", 20, "civilian", 30);
    private static final List<Share> NAVY = mix("soldier", 80);
    private static final List<Share> STADIUM = mix("soldier", 20, "patient", 25);
    private static final List<Share> STATION = mix("soldier", 20, "patient", 30);

    /** Building kits by block prefix, checked in this order (a hospital's helipad on another roof isn't a hospital). */
    private static final String[][] KITS = {{"hs_", "hospital"}, {"sh_", "police"}, {"fs_", "fire"}, {"sc_", "school"}, {"ml_", "mall"}};
    private static final Map<Block, String> BLOCK_KIT = new IdentityHashMap<>();
    /** The kit each chunk holds ("" none), found once per chunk. Server thread only. */
    private static final Map<Long, String> CHUNK_KIT = new HashMap<>();

    private static List<Share> mix(Object... pairs) {
        Share[] out = new Share[pairs.length / 2];
        for (int i = 0; i < out.length; i++) {
            out[i] = new Share((String) pairs[i * 2], (Integer) pairs[i * 2 + 1]);
        }
        return List.of(out);
    }

    /** The outfit for one of the dead at pos, or null for the general mix. */
    public static String pick(ServerLevel level, BlockPos pos, List<Place> places, RandomSource random) {
        List<Share> mix = mixFor(level, pos, places);
        if (mix == null) {
            return null;
        }
        int roll = random.nextInt(100);
        for (Share s : mix) {
            roll -= s.percent();
            if (roll < 0) {
                return s.outfit();
            }
        }
        return null;
    }

    private static List<Share> mixFor(ServerLevel level, BlockPos pos, List<Place> places) {
        List<Share> kit = switch (kit(level, pos)) {
            case "hospital" -> HOSPITAL;
            case "police" -> POLICE;
            case "fire" -> FIRE;
            case "school" -> SCHOOL;
            case "mall" -> SHOPS;
            default -> null;
        };
        if (kit != null) {
            return kit;
        }
        for (Place p : places) {
            List<Share> mix = mixFor(p, pos);
            if (mix != null) {
                return mix;
            }
        }
        return null;
    }

    /** The mix for a county place, by its type and kind (a variant's part before any "@"). */
    private static List<Share> mixFor(Place p, BlockPos pos) {
        String kind = p.variant.split("@", 2)[0];
        return switch (p.type) {
            case MILITARY -> switch (kind) {
                case "quarantine" -> QUARANTINE;
                case "ridgeview" -> LABS;
                default -> ARMY;
            };
            case PORT -> kind.equals("naval") ? NAVY : WORKS;
            case FARM -> FARM;
            // downtown's upper floors are offices; its street level is shops and everyone else
            case DOWNTOWN -> pos.getY() >= p.level + 8 ? OFFICES : null;
            case ROADSIDE -> switch (kind) {
                case "school" -> SCHOOL;
                case "truck_depot", "warehouse" -> WORKS;
                default -> null;
            };
            case LANDMARK -> switch (kind) {
                case "mercy" -> HOSPITAL;
                case "ravenrock" -> PRISON;
                case "armyfield", "ranges", "camp" -> ARMY;
                case "hollis" -> QUARANTINE;
                case "tower", "civic" -> OFFICES;
                case "mall", "market", "boardwalk" -> SHOPS;
                case "university" -> SCHOOL;
                case "shipyard", "foundry", "scrapyard" -> WORKS;
                case "park", "cape" -> PARK;
                case "airport" -> AIRPORT;
                case "stadium" -> STADIUM;
                case "union_station" -> STATION;
                default -> null;
            };
            default -> null;
        };
    }

    /** The building kit in pos's chunk: "hospital", "police", "fire", "school", "mall", or "" for none. */
    static String kit(ServerLevel level, BlockPos pos) {
        long key = (long) (pos.getX() >> 4) << 32 | (pos.getZ() >> 4) & 0xFFFFFFFFL;
        String known = CHUNK_KIT.get(key);
        if (known != null) {
            return known;
        }
        LevelChunk chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
        if (chunk == null) {
            return "";
        }
        String found = "";
        int best = KITS.length;
        for (LevelChunkSection section : chunk.getSections()) {
            if (section.hasOnlyAir()) {
                continue;
            }
            for (int k = 0; k < best; k++) {
                String name = KITS[k][1];
                if (section.maybeHas(state -> name.equals(blockKit(state.getBlock())))) {
                    found = name;
                    best = k;
                    break;
                }
            }
        }
        if (CHUNK_KIT.size() > 8192) {
            CHUNK_KIT.clear();
        }
        CHUNK_KIT.put(key, found);
        return found;
    }

    private static String blockKit(Block block) {
        return BLOCK_KIT.computeIfAbsent(block, b -> {
            String path = BuiltInRegistries.BLOCK.getKey(b).getPath();
            if (path.startsWith("hs_pad") || path.startsWith("sh_cmu")) {
                return "";   // a helipad on some other roof; the auto shops' block walls
            }
            for (String[] k : KITS) {
                if (path.startsWith(k[0])) {
                    return k[1];
                }
            }
            return "";
        });
    }

    /** Forget what chunks held (world unloaded, or rebuilt in a test). */
    public static void clear() {
        CHUNK_KIT.clear();
    }

    private PlaceOutfits() {
    }
}
