import com.zomboidcraft.world.city.Placer;
import com.zomboidcraft.world.county.Bunker;
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
 * Builds bunker cores side by side and saves them in CountyPreview's dump format (OUT.vox and
 * OUT.json) for tools/city/mesh.py; each bunker's rooms go to stderr.
 *
 *   java -cp build/county BunkerPreview OUT seed cut KIND[:STATE] ...
 *
 * cut is the number of rows kept above each floor (4 keeps the ceiling, 3 looks down into the
 * rooms). Bunkers stand 6 apart along x, blast doors to the north; dump y 1 is the floor.
 */
public final class BunkerPreview {
    public static void main(String[] args) throws IOException {
        String out = args[0];
        long seed = Long.parseLong(args[1]);
        int cut = Integer.parseInt(args[2]);
        Voxels v = new Voxels();
        int x = 2, maxD = 0;
        for (int i = 3; i < args.length; i++) {
            String[] a = args[i].split(":");
            Bunker.Kind kind = Bunker.Kind.valueOf(a[0].toUpperCase(Locale.ROOT));
            Bunker.State state = a.length > 1 ? Bunker.State.valueOf(a[1].toUpperCase(Locale.ROOT)) : Bunker.State.SEALED;
            SplittableRandom rng = new SplittableRandom(seed * 31 + i);
            Bunker probe = new Bunker(new Placer(new Voxels(), 0, 0, 0, 0), new SplittableRandom(seed * 31 + i), kind, state);
            int[] b = probe.bounds();
            int ox = x - b[0];
            Bunker k = new Bunker(new Placer(v, ox, 1, 2, 0), rng, kind, state);
            k.build();
            System.err.print("-- " + kind + " at x+" + ox + " z+2\n" + k.describe());
            x += b[2] - b[0] + 1 + 6;
            maxD = Math.max(maxD, b[3] - b[1] + 1);
        }
        int sx = x + 2, sz = maxD + 6, h = 7;
        short[] vox = new short[sx * sz * h];
        List<String> palette = new ArrayList<>(List.of("air"));
        Map<String, Short> ids = new HashMap<>(Map.of("air", (short) 0));
        List<String> pal = v.palette();
        for (int cx = -1; cx <= sx >> 4; cx++) {
            for (int cz = -1; cz <= sz >> 4; cz++) {
                v.forColumn(cx, cz, (bx, y, bz, id) -> {
                    if (y >= 1 && y <= 1 + cut) {
                        put(vox, palette, ids, sx, sz, h, bx, y, bz, pal.get(id));
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
            pw.printf("{\"size\":[%d,%d,%d],\"origin\":[0,0,0],\"palette\":[%s],\"lamps\":[],\"cars\":[]}%n", sx, h, sz, ps);
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
