import com.zomboidcraft.world.city.Placer;
import com.zomboidcraft.world.city.Tower;
import com.zomboidcraft.world.county.Bunker;
import com.zomboidcraft.world.county.Garage;
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
 * Builds a downtown tower (a few floors) with its parking levels and the bunker under them, on a
 * paved square, and saves a slice of it in CountyPreview's dump format for tools/city/mesh.py.
 *
 *   java -cp build/county GaragePreview OUT seed w d levels STATE ylo yhi [xlo xhi]
 *
 * Only blocks from ylo to yhi (tower frame, ground = 0) are kept, so a slice can look down into
 * one level; dump y 0 is ylo. xlo..xhi (tower frame) cuts a section through it. The tower stands
 * at x, z = M, M in the dump; marks go to stderr.
 */
public final class GaragePreview {
    private static final int M = 90;

    public static void main(String[] args) throws IOException {
        String out = args[0];
        long seed = Long.parseLong(args[1]);
        int w = Integer.parseInt(args[2]), d = Integer.parseInt(args[3]), levels = Integer.parseInt(args[4]);
        Bunker.State state = Bunker.State.valueOf(args[5].toUpperCase(Locale.ROOT));
        int ylo = Integer.parseInt(args[6]), yhi = Integer.parseInt(args[7]);
        int xlo = args.length > 8 ? Integer.parseInt(args[8]) + M : 0, xhi = args.length > 9 ? Integer.parseInt(args[9]) + M : 1 << 20;
        Voxels v = new Voxels();
        int sx = w + 2 * M, sz = d + 2 * M;
        for (int x = M - 12; x < M + w + 12; x++) {
            for (int z = M - 14; z < M + d + 12; z++) {
                v.set(x, 0, z, z < M - 6 ? "asphalt" : "sidewalk");
            }
        }
        Placer pl = new Placer(v, M, 0, M, 0);
        SplittableRandom rng = new SplittableRandom(seed);
        Tower t = new Tower(pl, rng, w, d, 3, true, Tower.GRID, 0);
        t.build();
        SplittableRandom g = new SplittableRandom(seed * 7 + 1);
        Garage garage = new Garage(pl, g, w, d, levels, t.coreX(), t.coreZ());
        garage.build();
        Placer bp = garage.bunker(state, new int[] {2, 2, sx - 3, sz - 3});
        System.err.println(bp == null ? "no bunker fits" : "bunker built");
        for (Voxels.Mark m : v.marks) {
            System.err.println("mark " + m.what() + " " + m.x() + " " + m.y() + " " + m.z());
        }
        int h = yhi - ylo + 1;
        short[] vox = new short[sx * sz * h];
        List<String> palette = new ArrayList<>(List.of("air"));
        Map<String, Short> ids = new HashMap<>(Map.of("air", (short) 0));
        List<String> pal = v.palette();
        for (int cx = 0; cx <= sx >> 4; cx++) {
            for (int cz = 0; cz <= sz >> 4; cz++) {
                v.forColumn(cx, cz, (bx, y, bz, id) -> {
                    if (y >= ylo && y <= yhi && bx < sx && bz < sz && bx >= xlo && bx <= xhi && bz >= 0) {
                        String name = pal.get(id).replaceAll(",loot=[a-z_]+", "").replaceAll("\\[loot=[a-z_]+\\]", "")
                                .replaceAll("\\[loot=[a-z_]+,", "[");
                        Short k = ids.get(name);
                        if (k == null) {
                            k = (short) palette.size();
                            palette.add(name);
                            ids.put(name, k);
                        }
                        vox[((y - ylo) * sz + bz) * sx + bx] = k;
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
        StringBuilder cars = new StringBuilder();
        for (Voxels.Car c : v.cars) {
            if (c.y() >= ylo && c.y() <= yhi) {
                cars.append(cars.length() > 0 ? "," : "").append(String.format(Locale.ROOT, "[%.2f,%d,%.2f,%.1f]",
                        c.x(), c.y() - ylo, c.z(), c.yaw()));
            }
        }
        try (PrintWriter pw = new PrintWriter(out + ".json")) {
            StringBuilder ps = new StringBuilder();
            for (int i = 0; i < palette.size(); i++) {
                ps.append(i > 0 ? "," : "").append('"').append(palette.get(i).replace("\"", "\\\"")).append('"');
            }
            pw.printf("{\"size\":[%d,%d,%d],\"origin\":[0,0,0],\"palette\":[%s],\"lamps\":[],\"cars\":[%s]}%n", sx, h, sz, ps, cars);
        }
        System.err.println("dump " + sx + "x" + h + "x" + sz + ", " + palette.size() + " block states, " + v.cars.size() + " cars");
    }
}
