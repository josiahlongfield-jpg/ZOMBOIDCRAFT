package com.zomboidcraft.world.county;

import com.zomboidcraft.world.city.Placer;
import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;

/**
 * Builds civic buildings in a row on flat grass, with the post-passes the game runs on a place
 * (headroom over stairs, hatches fitted to their ladders), and saves them in SitePreview's dump
 * format for tools/county/walkcheck.py, orientcheck.py and tools/city/mesh.py.
 *
 *   java -cp build/county:src/main/resources com.zomboidcraft.world.county.CivicPreview OUT seed KIND:w:d ...
 *
 * KIND is courthouse, townhall, library, bank, firestation, sheriff, school, highschool, theatre
 * or postoffice. Each stands in its own lot, fronts to the north (z = 0), 12 apart along x; the
 * first lot's corner is at dump x, z = 12, 12 and the ground at dump y LEVEL (12; the basements
 * go below it). Each lot's x range goes to stderr.
 */
public final class CivicPreview {
    private static final int LEVEL = 12, M = 12;

    public static void main(String[] args) throws IOException {
        String out = args[0];
        long seed = Long.parseLong(args[1]);
        Voxels v = new Voxels();
        int x = 0, maxD = 0;
        for (int i = 2; i < args.length; i++) {
            String[] a = args[i].split(":");
            int w = Integer.parseInt(a[1]), d = Integer.parseInt(a[2]);
            SplittableRandom rng = new SplittableRandom(seed * 31 + i);
            Placer p = new Placer(v, x, 0, 0, 0);
            Civic c = new Civic(p, rng);
            switch (a[0]) {
                case "courthouse" -> c.courthouse(w, d);
                case "townhall" -> c.townHall(w, d);
                case "library" -> c.library(w, d);
                case "bank" -> c.bank(w, d);
                case "firestation" -> c.fireStation(w, d);
                case "sheriff" -> c.sheriff(w, d);
                case "school" -> c.school(w, d, false);
                case "highschool" -> c.school(w, d, true);
                case "theatre" -> c.theatre(w, d);
                case "postoffice" -> new PostOffice(p, rng).build(w, d);
                default -> throw new IllegalArgumentException("no civic building " + a[0]);
            }
            System.err.println(a[0] + " at dump x " + (x + M) + ".." + (x + M + w - 1) + ", z " + M + ".." + (M + d - 1));
            x += w + 12;
            maxD = Math.max(maxD, d);
        }
        Builders.clearHeadroom(v);
        Builders.fitHatches(v);
        Builders.clearDoorways(v);
        int sx = x + 2 * M, sz = maxD + 2 * M, h = Math.min(LEVEL + v.maxY() + 4, 400);
        short[] vox = new short[sx * sz * h];
        List<String> palette = new ArrayList<>(List.of("air"));
        Map<String, Short> ids = new HashMap<>(Map.of("air", (short) 0));
        for (int xx = 0; xx < sx; xx++) {
            for (int zz = 0; zz < sz; zz++) {
                for (int y = 0; y <= LEVEL; y++) {
                    vox[(y * sz + zz) * sx + xx] = id(palette, ids, y < LEVEL ? "minecraft:dirt" : "minecraft:grass_block");
                }
            }
        }
        List<String> pal = v.palette();
        v.forEach((bx, by, bz, id) -> {
            int xx = bx + M, yy = by + LEVEL, zz = bz + M;
            if (xx >= 0 && zz >= 0 && yy >= 0 && xx < sx && zz < sz && yy < h) {
                String name = pal.get(id).replaceAll(",loot=[a-z_]+", "").replaceAll("\\[loot=[a-z_]+\\]", "").replaceAll("\\[loot=[a-z_]+,", "[");
                vox[(yy * sz + zz) * sx + xx] = id(palette, ids, name);
            }
        });
        try (DataOutputStream o = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(out + ".vox")))) {
            ByteBuffer b = ByteBuffer.allocate(vox.length * 2).order(ByteOrder.LITTLE_ENDIAN);
            for (short s : vox) {
                b.putShort(s);
            }
            o.write(b.array());
        }
        try (PrintWriter pw = new PrintWriter(out + ".json")) {
            StringBuilder ps = new StringBuilder();
            for (int i = 0; i < palette.size(); i++) {
                ps.append(i > 0 ? "," : "").append('"').append(palette.get(i).replace("\"", "\\\"")).append('"');
            }
            pw.printf("{\"size\":[%d,%d,%d],\"origin\":[%d,%d,%d],\"palette\":[%s],\"lamps\":[],\"cars\":[]}%n",
                    sx, h, sz, -M, -LEVEL, -M, ps);
        }
        System.err.println("dump " + sx + "x" + h + "x" + sz + ", " + palette.size() + " block states");
    }

    private static short id(List<String> palette, Map<String, Short> ids, String name) {
        Short id = ids.get(name);
        if (id == null) {
            id = (short) palette.size();
            palette.add(name);
            ids.put(name, id);
        }
        return id;
    }
}
