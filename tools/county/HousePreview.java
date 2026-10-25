import com.zomboidcraft.world.city.Placer;
import com.zomboidcraft.world.county.House;
import com.zomboidcraft.world.county.Voxels;

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
import java.util.Locale;
import java.util.Map;
import java.util.SplittableRandom;

/**
 * Builds a row of houses on flat grass, one per style and condition asked for, and saves them in
 * CountyPreview's dump format (OUT.vox and OUT.json) for tools/city/mesh.py. With a cut height,
 * everything above it is left off so the rooms can be looked down into.
 *
 *   java -cp build/county HousePreview OUT seed cut STYLE[:CONDITION[:floors]] ...
 *
 * cut is in blocks above the ground (0 for none; -2 looks down into the basements, under the joists).
 * Houses stand 8 apart along x, fronts to the north (z = 0 at the front of each lot). Ground is dump
 * y = 4. CELLAR=any (or a use: food, armoury...), or a fourth field STYLE:COND:floors:use, picks
 * the first seed from the given one whose house has such a basement.
 */
public final class HousePreview {
    private static final int LEVEL = 4;

    public static void main(String[] args) throws IOException {
        String out = args[0];
        long seed = Long.parseLong(args[1]);
        int cut = Integer.parseInt(args[2]);
        Voxels v = new Voxels();
        int x = 0, maxD = 0;
        List<String> labels = new ArrayList<>();
        for (int i = 3; i < args.length; i++) {
            String[] a = args[i].split(":");
            House.Style style = House.Style.valueOf(a[0].toUpperCase(Locale.ROOT));
            House.Condition cond = a.length > 1 ? House.Condition.valueOf(a[1].toUpperCase(Locale.ROOT)) : House.Condition.SHUT;
            int[] size = switch (style) {
                case RANCH -> new int[] {25, 17, 1};
                case COLONIAL -> new int[] {19, 16, 2};
                case BUNGALOW -> new int[] {14, 17, 1};
                case FARMHOUSE -> new int[] {18, 18, 2};
                case CABIN -> new int[] {11, 13, 1};
                case TRAILER -> new int[] {19, 8, 1};
                case SHOTGUN -> new int[] {8, 23, 1};
                case MANSION -> new int[] {27, 21, 2};
            };
            if (a.length > 2) {
                size[2] = Integer.parseInt(a[2]);
            }
            long hs = seed * 31 + i;
            String want = a.length > 3 ? a[3] : System.getenv("CELLAR");
            for (int k = 0; want != null && k < 5000; k++, hs += 7919) {
                SplittableRandom tr = new SplittableRandom(hs);
                House t = new House(new Placer(new Voxels(), 0, 0, 0, 0), tr, style, cond, House.palette(style, tr), size[0], size[1], size[2]);
                t.build();
                if (t.describe().contains(want.equals("any") ? "cellar " : "cellar " + want)) {
                    break;
                }
            }
            SplittableRandom rng = new SplittableRandom(hs);
            House h = new House(new Placer(v, x + 2, 0, 2, 0), rng, style, cond, House.palette(style, rng), size[0], size[1], size[2]);
            h.build();
            labels.add(style + ":" + cond + "@" + (x + 2));
            if (System.getenv("ROOMS") != null) {
                System.err.print("-- " + style + " at x+" + (x + 2) + " z+2\n" + h.describe());
            }
            x += size[0] + 8;
            maxD = Math.max(maxD, size[1] + 4);
        }
        System.err.println(String.join(" ", labels));

        int m = 4, sx = x + m, sz = maxD + 2 * m, h = Math.min(LEVEL + v.maxY() + 4, 200);
        short[] vox = new short[sx * sz * h];
        List<String> palette = new ArrayList<>(List.of("air"));
        Map<String, Short> ids = new HashMap<>(Map.of("air", (short) 0));
        int top = cut != 0 ? LEVEL + cut : Integer.MAX_VALUE;
        for (int px = 0; px < sx; px++) {
            for (int pz = 0; pz < sz; pz++) {
                for (int y = 0; y < LEVEL && y <= top; y++) {
                    put(vox, palette, ids, sx, sz, h, px, y, pz, y < LEVEL - 1 ? "minecraft:dirt" : "minecraft:grass_block");
                }
            }
        }
        List<String> pal = v.palette();
        for (int cx = 0; cx <= x >> 4; cx++) {
            for (int cz = -1; cz <= maxD >> 4; cz++) {
                v.forColumn(cx, cz, (bx, y, bz, id) -> {
                    if (y + LEVEL <= top) {
                        put(vox, palette, ids, sx, sz, h, bx, y + LEVEL, bz + m, pal.get(id));
                    }
                });
            }
        }
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
            pw.printf("{\"size\":[%d,%d,%d],\"origin\":[0,0,%d],\"palette\":[%s],\"lamps\":[],\"cars\":[]}%n", sx, h, sz, -m, ps);
        }
        System.err.println("dump " + sx + "x" + h + "x" + sz + ", " + palette.size() + " block states");
    }

    private static void put(short[] vox, List<String> palette, Map<String, Short> ids, int sx, int sz, int h,
                            int x, int y, int z, String block) {
        if (x < 0 || z < 0 || y < 0 || x >= sx || z >= sz || y >= h) {
            return;
        }
        String name = block.replaceAll(",loot=[a-z_]+", "").replaceAll("\\[loot=[a-z_]+\\]", "").replaceAll("\\[loot=[a-z_]+,", "[");
        Short id = ids.get(name);
        if (id == null) {
            id = (short) palette.size();
            palette.add(name);
            ids.put(name, id);
        }
        vox[(y * sz + z) * sx + x] = id;
    }
}
