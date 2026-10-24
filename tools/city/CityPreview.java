import com.zomboidcraft.world.city.BlockSink;
import com.zomboidcraft.world.city.CityDemo;

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

/**
 * Runs the city generator outside the game and saves the blocks for the preview renderer:
 * OUT.vox is little-endian uint16 block ids, x fastest then z then y, and OUT.json holds the
 * size, origin, palette (id 0 = air) and the street light positions.
 *
 *   javac -d build/city src/main/java/com/zomboidcraft/world/city/*.java tools/city/CityPreview.java
 *   java -cp build/city CityPreview OUT [seed]
 */
public final class CityPreview implements BlockSink {
    private static final int H = 260;
    private final int x0 = CityDemo.MIN_X - 8, z0 = CityDemo.MIN_Z - 8;
    private final int sx = CityDemo.MAX_X - x0 + 9, sz = CityDemo.MAX_Z - z0 + 9;
    private final short[] vox = new short[sx * sz * H];
    private final List<String> palette = new ArrayList<>(List.of("air"));
    private final Map<String, Short> ids = new HashMap<>(Map.of("air", (short) 0));

    private int index(int x, int y, int z) {
        x -= x0;
        z -= z0;
        if (x < 0 || z < 0 || y < 0 || x >= sx || z >= sz || y >= H) {
            return -1;
        }
        return (y * sz + z) * sx + x;
    }

    @Override
    public void set(int x, int y, int z, String block) {
        int i = index(x, y, z);
        if (i < 0) {
            return;
        }
        Short id = ids.get(block);
        if (id == null) {
            id = (short) palette.size();
            palette.add(block);
            ids.put(block, id);
        }
        vox[i] = id;
    }

    @Override
    public String get(int x, int y, int z) {
        int i = index(x, y, z);
        return i < 0 || vox[i] == 0 ? null : palette.get(vox[i]);
    }

    public static void main(String[] args) throws IOException {
        CityPreview out = new CityPreview();
        CityDemo city = new CityDemo(out, args.length > 1 ? Long.parseLong(args[1]) : 1L);
        long t = System.nanoTime();
        city.build();
        System.out.printf("built in %.1fs, %d block types%n", (System.nanoTime() - t) / 1e9, out.palette.size());
        ByteBuffer buf = ByteBuffer.allocate(out.vox.length * 2).order(ByteOrder.LITTLE_ENDIAN);
        buf.asShortBuffer().put(out.vox);
        try (DataOutputStream o = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(args[0] + ".vox")))) {
            o.write(buf.array());
        }
        try (PrintWriter w = new PrintWriter(args[0] + ".json")) {
            w.printf("{\"size\":[%d,%d,%d],\"origin\":[%d,0,%d],\"palette\":[", out.sx, H, out.sz, out.x0, out.z0);
            for (int i = 0; i < out.palette.size(); i++) {
                w.print((i > 0 ? "," : "") + "\"" + out.palette.get(i) + "\"");
            }
            w.print("],\"lamps\":[");
            for (int i = 0; i < city.lamps().size(); i++) {
                int[] l = city.lamps().get(i);
                w.printf("%s[%d,%d,%d]", i > 0 ? "," : "", l[0], l[1], l[2]);
            }
            w.println("]}");
        }
    }
}
