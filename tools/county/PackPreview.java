import com.zomboidcraft.world.city.Decay;
import com.zomboidcraft.world.city.PackTower;
import com.zomboidcraft.world.city.Placer;
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
import java.util.Map;
import java.util.SplittableRandom;

/**
 * Stands buildings from the pack (world/city/PackTower) side by side along a street, fitted
 * out, and saves them for the preview renderer in CountyPreview's format (OUT.vox, OUT.json).
 *
 *   java -cp build/county:src/main/resources PackPreview OUT e8,e3,e11 [seed] [decay]
 *
 * Each building's front faces north onto the street (a sidewalk then asphalt at z < 0); they
 * stand 4 blocks apart from x = 0. Ground (y = 0) is dump y = 12; mesh coordinates are the
 * dump's minus 24 in x and z.
 */
public final class PackPreview {
    private static final int LEVEL = 12, M = 24;

    public static void main(String[] args) throws IOException {
        String out = args[0];
        String[] names = args[1].split(",");
        long seed = args.length > 2 ? Long.parseLong(args[2]) : 1L;
        boolean decay = args.length > 3 && args[3].equals("decay");
        Voxels v = new Voxels();
        SplittableRandom rng = new SplittableRandom(seed);
        int x = 0, depth = 0;
        for (String n : names) {
            PackTower.Template t = PackTower.all().stream().filter(tt -> tt.name().equals(n)).findFirst().orElseThrow();
            Placer pl = Placer.forLot(v, x, 0, 0, t.w(), t.d(), 0);
            new PackTower(pl, rng, t, true).build();
            if (decay) {
                new Decay(v, rng.nextLong(), x - 1, x + t.w(), -1, t.d(), t.height() + 6).run();
            }
            System.err.println(n + " at x " + x + ".." + (x + t.w() - 1) + ", " + t.w() + "x" + t.height() + "x" + t.d());
            x += t.w() + 4;
            depth = Math.max(depth, t.d());
        }
        int w = x, d = depth;
        int sx = w + 2 * M, sz = d + 2 * M, h = Math.min(LEVEL + v.maxY() + 4, 400);
        short[] vox = new short[sx * sz * h];
        List<String> palette = new ArrayList<>(List.of("air"));
        Map<String, Short> ids = new HashMap<>(Map.of("air", (short) 0));
        Setter set = (xx, y, z, block) -> {
            xx += M;
            z += M;
            if (xx < 0 || z < 0 || y < 0 || xx >= sx || z >= sz || y >= h) {
                return;
            }
            String name = block.replaceAll(",loot=[a-z_]+", "").replaceAll("\\[loot=[a-z_]+\\]", "").replaceAll("\\[loot=[a-z_]+,", "[");
            Short id = ids.get(name);
            if (id == null) {
                id = (short) palette.size();
                palette.add(name);
                ids.put(name, id);
            }
            vox[(y * sz + z) * sx + xx] = id;
        };
        for (int xx = -M; xx < w + M; xx++) {
            for (int z = -M; z < d + M; z++) {
                for (int y = 0; y < LEVEL; y++) {
                    set.set(xx, y, z, y < LEVEL - 3 ? "minecraft:stone" : "minecraft:dirt");
                }
                // a sidewalk in front, the street beyond, a back lot behind
                set.set(xx, LEVEL, z, z >= -4 && z < 0 ? "sidewalk" : z < -4 && z >= -16 ? "asphalt" : "floor_concrete_crack");
            }
        }
        List<String> pal = v.palette();
        for (int cx = 0; cx <= (w - 1) >> 4; cx++) {
            for (int cz = -1; cz <= (d - 1) >> 4; cz++) {
                v.forColumn(cx, cz, (xx, y, z, id) -> set.set(xx, y + LEVEL, z, pal.get(id)));
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
            pw.printf("{\"size\":[%d,%d,%d],\"origin\":[%d,%d,%d],\"palette\":[%s],\"lamps\":[],\"cars\":[]}%n",
                    sx, h, sz, -M, 0, -M, ps);
        }
        System.err.println("dump " + sx + "x" + h + "x" + sz + ", " + palette.size() + " block states");
    }

    private interface Setter {
        void set(int x, int y, int z, String block);
    }
}
