package com.zomboidcraft.world.map;

import com.zomboidcraft.ZomboidCraft;
import com.zomboidcraft.world.county.County;
import com.zomboidcraft.world.county.CountyGen;
import com.zomboidcraft.world.county.Place;
import com.zomboidcraft.world.county.PlaceType;
import com.zomboidcraft.world.county.Road;
import com.zomboidcraft.world.county.Terrain;
import com.zomboidcraft.world.county.Voxels;
import com.zomboidcraft.world.gen.CountyChunkGenerator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Draws the maps you loot, from the county plan rather than the world, so they show streets and land you've never
 * been near: a town's street map at two blocks a cell, read from the town's own blocks, or a road atlas of the
 * county around you at sixteen. Drawing happens off the server thread and the result is posted to your client.
 */
public final class PaperMaps {
    public enum Kind {
        STREET, ATLAS
    }

    /** How the map you just read went: false when this world has no county plan to draw from. */
    public static boolean read(ServerPlayer player, Kind kind) {
        ServerLevel level = (ServerLevel) player.level();
        if (!(level.getChunkSource().getGenerator() instanceof CountyChunkGenerator)) return false;
        CountyGen gen = CountyChunkGenerator.gen(level.getSeed());
        int px = player.getBlockX(), pz = player.getBlockZ();
        CompletableFuture.supplyAsync(() -> kind == Kind.STREET ? street(gen, px, pz) : atlas(gen, px, pz))
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
                if (Terrain.isWater(h) || terrain.riverChannel(x, z)) c = h < Terrain.WATER - 5 ? MapPalette.DEEP : MapPalette.WATER;
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
