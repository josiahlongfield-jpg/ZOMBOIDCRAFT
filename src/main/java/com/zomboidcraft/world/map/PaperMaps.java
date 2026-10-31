package com.zomboidcraft.world.map;

import com.zomboidcraft.ZomboidCraft;
import com.zomboidcraft.world.county.Bunkers;
import com.zomboidcraft.world.county.County;
import com.zomboidcraft.world.county.CountyGen;
import com.zomboidcraft.world.county.Place;
import com.zomboidcraft.world.county.PlaceType;
import com.zomboidcraft.world.county.Road;
import com.zomboidcraft.world.county.Terrain;
import com.zomboidcraft.world.county.Voxels;
import com.zomboidcraft.world.gen.CountyChunkGenerator;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.SplittableRandom;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * Draws the maps you loot, from the county plan rather than the world, so they show streets and land you've never
 * been near: a town's street map at two blocks a cell, read from the town's own blocks, a road atlas of the
 * county around you at sixteen, or a marked map with a red ring round a hidden bunker. Drawing happens off the server thread and the result is posted to your client.
 */
public final class PaperMaps {
    public enum Kind {
        STREET, ATLAS, TOPO
    }

    /** How the map you just read went: false when this world has no county plan to draw from. */
    public static boolean read(ServerPlayer player, Kind kind) {
        ServerLevel level = (ServerLevel) player.level();
        if (!(level.getChunkSource().getGenerator() instanceof CountyChunkGenerator)) return false;
        CountyGen gen = CountyChunkGenerator.gen(level.getSeed());
        int px = player.getBlockX(), pz = player.getBlockZ();
        CompletableFuture.supplyAsync(() -> kind == Kind.STREET ? street(gen, px, pz) : kind == Kind.TOPO ? topo(gen, px, pz)
                        : atlas(gen, px, pz))
                .whenComplete((reveal, error) -> level.getServer().execute(() -> {
                    if (error != null) {
                        ZomboidCraft.LOGGER.error("Couldn't draw a paper map", error);
                    } else if (reveal != null && !player.hasDisconnected()) {
                        ServerPlayNetworking.send(player, reveal);
                    }
                }));
        return true;
    }

    /** The street map of the nearest town or city (or whatever place is nearest), out past its edges. */
    private static MapNet.Reveal street(CountyGen gen, int px, int pz) {
        County county = gen.county();
        Place best = null;
        double bestScore = Double.MAX_VALUE;
        for (Place p : county.placesNear(px - 900, pz - 900, px + 900, pz + 900, 0)) {
            double d = Math.max(0, p.outside(px, pz));
            boolean urban = p.type == PlaceType.TOWN || p.type == PlaceType.DOWNTOWN || p.type == PlaceType.SUBURB;
            double score = d * (urban ? 1 : 2.5);
            if (score < bestScore) {
                bestScore = score;
                best = p;
            }
        }
        int cx = best == null ? px : best.centreX(), cz = best == null ? pz : best.centreZ();
        int half = best == null ? 192 : Math.min(480, Math.max(best.width(), best.depth()) / 2 + 128);
        String title = best == null ? "Local street map"
                : name(best.type) + " street map";
        return draw(gen, title, cx - half, cz - half, 2, half, true);
    }

    private static MapNet.Reveal atlas(CountyGen gen, int px, int pz) {
        return draw(gen, "County road atlas", px - 2048, pz - 2048, 16, 2048, false);
    }

    /** A military topographic set: the whole county's lie of the land, coarser than the atlas but four times as wide. */
    private static MapNet.Reveal topo(CountyGen gen, int px, int pz) {
        return draw(gen, "Topographic map set", px - 4096, pz - 4096, 32, 4096, false);
    }

    private static String name(PlaceType type) {
        String s = type.name().toLowerCase(Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    /** A square map {@code half} blocks either side of its centre. */
    private static MapNet.Reveal draw(CountyGen gen, String title, int x0, int z0, int step, int half, boolean detail) {
        County county = gen.county();
        Terrain terrain = county.terrain;
        int n = half * 2 / step;
        byte[] cells = new byte[n * n];
        int x1 = x0 + n * step, z1 = z0 + n * step;
        List<Place> places = county.placesNear(x0, z0, x1, z1, 0);
        for (int j = 0; j < n; j++) {
            for (int i = 0; i < n; i++) {
                double x = x0 + i * step + step / 2.0, z = z0 + j * step + step / 2.0;
                double h = terrain.height(x, z);
                byte c;
                if (terrain.wet(x, z, h) || terrain.riverChannel(x, z)) c = h < terrain.water(x, z) - 5 ? MapPalette.DEEP : MapPalette.WATER;
                else if (h > 98) c = MapPalette.ROCK;
                else if (h < Terrain.WATER + 1.5 && (terrain.river(x, z) > 0.2 || terrain.lake(x, z) < -0.24)) c = MapPalette.SAND;
                else if (terrain.forest(x, z) > 0.55) c = MapPalette.FOREST;
                else c = terrain.moisture(x, z) > 0.25 ? MapPalette.GRASS : MapPalette.FIELD;
                cells[j * n + i] = c;
            }
        }
        for (Place p : places) {
            Voxels v = null;
            if (detail) {
                try {
                    v = gen.cache().get(p, county);
                } catch (RuntimeException e) {
                    ZomboidCraft.LOGGER.warn("Paper map drew {} as a plain block", p.type, e);
                }
            }
            List<String> palette = v == null ? null : v.palette();
            for (int j = 0; j < n; j++) {
                for (int i = 0; i < n; i++) {
                    int x = x0 + i * step + step / 2, z = z0 + j * step + step / 2;
                    if (!p.contains(x, z, 0)) continue;
                    byte c = v == null ? MapPalette.TOWN : top(v, x - p.x0, z - p.z0);
                    if (c != MapPalette.NONE) cells[j * n + i] = c;
                }
            }
        }
        for (Road road : county.roadsNear(x0, z0, x1, z1)) {
            byte c = road.kind == Road.Kind.HIGHWAY ? MapPalette.HIGHWAY : road.kind == Road.Kind.COUNTY ? MapPalette.ROAD : MapPalette.TRACK;
            // at an atlas's scale a road is still drawn a cell wide
            double r = Math.max(road.kind.half, step * 0.6);
            for (int k = 0; k + 1 < road.xs.length; k++) {
                double ax = road.xs[k], az = road.zs[k], bx = road.xs[k + 1], bz = road.zs[k + 1];
                double len = Math.hypot(bx - ax, bz - az);
                int steps = Math.max(1, (int) Math.ceil(len / Math.max(1, step / 2.0)));
                for (int s = 0; s <= steps; s++) {
                    double x = ax + (bx - ax) * s / steps, z = az + (bz - az) * s / steps;
                    stamp(cells, n, x0, z0, step, x, z, r, c);
                }
            }
        }
        return new MapNet.Reveal(title, x0, z0, step, n, n, cells);
    }

    /**
     * A marked map: the land round a bunker at two blocks a cell, and the red ring someone drew round
     * where it is, a little uneven and never quite centred on it.
     */
    private static MapNet.Reveal marked(CountyGen gen, int cx, int cz, int r) {
        int half = r * 2 + 48;
        MapNet.Reveal map = draw(gen, MARKED, cx - half, cz - half, 2, half, true);
        int n = map.w();
        double wobble = (cx * 31 + cz) % 7;
        for (int j = 0; j < n; j++) {
            for (int i = 0; i < n; i++) {
                double x = map.x0() + (i + 0.5) * 2 - cx, z = map.z0() + (j + 0.5) * 2 - cz;
                double ring = r + 2.5 * Math.sin(Math.atan2(z, x) * 3 + wobble);
                if (Math.abs(Math.sqrt(x * x + z * z) - ring) <= 1.6) map.cells()[j * n + i] = MapPalette.MARK;
            }
        }
        return map;
    }

    private static final String MARKED = "Marked map";

    /** What a marked map's target says: where the bunker is and where the ring was drawn. */
    private record Target(String kind, int x, int z, int ringX, int ringZ, int r) {
        static Target parse(String s) {
            String[] f = s.split(",");
            return new Target(f[0], Integer.parseInt(f[2]), Integer.parseInt(f[3]), Integer.parseInt(f[4]),
                    Integer.parseInt(f[5]), Integer.parseInt(f[6]));
        }
    }

    /**
     * Reads a marked map. The first time, it settles which bunker it leads to: one of those within a
     * few thousand blocks, built and searched off the server thread, any of them as likely (a second map
     * can lead to a bunker someone else has already been through). After that it always shows the same
     * ring. False when this world has no county plan.
     */
    public static boolean readMarked(ServerPlayer player, ItemStack stack) {
        ServerLevel level = (ServerLevel) player.level();
        if (!(level.getChunkSource().getGenerator() instanceof CountyChunkGenerator)) return false;
        CountyGen gen = CountyChunkGenerator.gen(level.getSeed());
        int px = player.getBlockX(), pz = player.getBlockZ();
        String known = stack.get(BunkerMapItem.TARGET);
        long seed = level.getRandom().nextLong();
        CompletableFuture.supplyAsync(() -> {
            String target = known != null ? known : pick(gen, px, pz, new SplittableRandom(seed));
            if (target == null) return null;
            Target t = Target.parse(target);
            return new Object[] {target, marked(gen, t.ringX(), t.ringZ(), t.r())};
        }).whenComplete((found, error) -> level.getServer().execute(() -> {
            if (error != null) {
                ZomboidCraft.LOGGER.error("Couldn't draw a marked map", error);
                return;
            }
            if (player.hasDisconnected()) return;
            if (found == null) {
                player.sendOverlayMessage(Component.literal("The ink has run. You can't make anything out.").withStyle(ChatFormatting.GRAY));
                return;
            }
            String target = (String) found[0];
            if (known == null) stack.set(BunkerMapItem.TARGET, target);
            ServerPlayNetworking.send(player, (MapNet.Reveal) found[1]);
            player.sendOverlayMessage(Component.literal("Somewhere is ringed in red. It's on your map now (M)."));
            player.sendSystemMessage(Component.literal("Pencilled beside the ring: \"" + hint(Target.parse(target).kind()) + "\"")
                    .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        }));
        return true;
    }

    private static String hint(String kind) {
        return switch (kind) {
            case "ship" -> "Engine room, forward bulkhead. One plate isn't welded.";
            case "shelter" -> "Civil defence shelter. Down through the boiler room.";
            case "forest" -> "Stumps, a vent pipe, a cable post. Look under the leaves.";
            default -> "B4, electrical. Behind the cage, under the floor.";
        };
    }

    /** Settles a new map's bunker: "kind,state,x,z,ringX,ringZ,radius", or null if none is near. */
    private static String pick(CountyGen gen, int px, int pz, SplittableRandom rng) {
        List<Place> places = new ArrayList<>(Bunkers.candidates(gen.county(), px, pz, null, 3000));
        for (int i = places.size() - 1; i > 0; i--) {
            int j = rng.nextInt(i + 1);
            Place t = places.get(i);
            places.set(i, places.get(j));
            places.set(j, t);
        }
        // only building a place shows whether it has a bunker: give up after a few
        for (Place p : places.subList(0, Math.min(8, places.size()))) {
            List<Bunkers.Found> found = Bunkers.in(gen, p, null);
            if (found.isEmpty()) continue;
            Bunkers.Found b = found.get(rng.nextInt(found.size()));
            // a ring about 80 blocks across in a town, 150 in the woods, the bunker somewhere inside it
            int r = b.kind().equals("forest") ? 75 : 40;
            double a = rng.nextDouble() * Math.PI * 2, off = rng.nextDouble() * 0.6 * r;
            int ringX = b.x() + (int) Math.round(Math.cos(a) * off), ringZ = b.z() + (int) Math.round(Math.sin(a) * off);
            return String.join(",", b.kind(), b.state(), Integer.toString(b.x()), Integer.toString(b.z()),
                    Integer.toString(ringX), Integer.toString(ringZ), Integer.toString(r));
        }
        return null;
    }

    private static void stamp(byte[] cells, int n, int x0, int z0, int step, double x, double z, double r, byte c) {
        int i0 = (int) Math.floor((x - r - x0) / step), i1 = (int) Math.floor((x + r - x0) / step);
        int j0 = (int) Math.floor((z - r - z0) / step), j1 = (int) Math.floor((z + r - z0) / step);
        for (int j = Math.max(0, j0); j <= Math.min(n - 1, j1); j++) {
            for (int i = Math.max(0, i0); i <= Math.min(n - 1, i1); i++) {
                double cx = x0 + (i + 0.5) * step, cz = z0 + (j + 0.5) * step;
                if ((cx - x) * (cx - x) + (cz - z) * (cz - z) <= (r + step * 0.5) * (r + step * 0.5)) cells[j * n + i] = c;
            }
        }
    }

    /** What a place's column looks like from above: its topmost block, sorted into an ink. */
    private static byte top(Voxels v, int lx, int lz) {
        for (int y = v.maxY(); y >= Voxels.MIN_Y; y--) {
            String block = v.get(lx, y, lz);
            if (block == null) continue;
            String b = block.toLowerCase(Locale.ROOT);
            if (b.contains("water")) return MapPalette.WATER;
            if (b.contains("leaves") || b.contains("leaf")) return MapPalette.FOREST;
            if (b.contains("asphalt") || b.contains("road") || b.contains("tarmac")) return MapPalette.STREET;
            if (b.contains("sidewalk") || b.contains("pavement") || b.contains("curb") || b.contains("kerb")
                    || b.contains("concrete") && y <= 1) return MapPalette.PAVEMENT;
            if (y >= 2) return MapPalette.BUILDING;
            if (b.contains("grass") || b.contains("dirt") || b.contains("moss") || b.contains("podzol")) return MapPalette.GRASS;
            if (b.contains("farmland") || b.contains("wheat") || b.contains("crop") || b.contains("hay")) return MapPalette.FIELD;
            if (b.contains("sand") || b.contains("gravel")) return MapPalette.SAND;
            return MapPalette.PAVEMENT;
        }
        return MapPalette.NONE;
    }

    private PaperMaps() {
    }
}
