import com.zomboidcraft.world.county.ChunkSink;
import com.zomboidcraft.world.county.County;
import com.zomboidcraft.world.county.CountyGen;
import com.zomboidcraft.world.county.PlaceCache;

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
 * Generates a square of county chunks outside the game and saves the blocks for the preview
 * renderer, in the same format as tools/city/CityPreview.java: OUT.vox (little-endian uint16 ids,
 * x fastest, then z, then y) and OUT.json (size, origin, palette, lamps). Heights are stored from
 * Y0 up, so y = 0 in the dump is world y = Y0.
 *
 *   java -cp build/county CountyPreview OUT seed centreX centreZ radiusBlocks [y0] [height]
 *
 * The county is the planned one (the Grayhaven plan); -Dcounty.endless=true gives the old endless one.
 */
public final class CountyPreview implements ChunkSink {
    private final int x0, z0, sx, sz, y0, h;
    private final short[] vox;
    private final List<String> palette = new ArrayList<>(List.of("air"));
    private final Map<String, Short> ids = new HashMap<>(Map.of("air", (short) 0));

    CountyPreview(int x0, int z0, int sx, int sz, int y0, int h) {
        this.x0 = x0;
        this.z0 = z0;
        this.sx = sx;
        this.sz = sz;
        this.y0 = y0;
        this.h = h;
        this.vox = new short[sx * sz * h];
    }

    @Override
    public void set(int x, int y, int z, String block) {
        x -= x0;
        z -= z0;
        y -= y0;
        if (x < 0 || z < 0 || y < 0 || x >= sx || z >= sz || y >= h) {
            return;
        }
        String name = block.startsWith("minecraft:") && block.contains("loot=") ? block.replaceAll(",?loot=[a-z_]+", "").replace("[]", "") : block;
        name = name.replaceAll(",loot=[a-z_]+", "").replaceAll("\\[loot=[a-z_]+\\]", "").replaceAll("\\[loot=[a-z_]+,", "[");
        Short id = ids.get(name);
        if (id == null) {
            id = (short) palette.size();
            palette.add(name);
            ids.put(name, id);
        }
        vox[(y * sz + z) * sx + x] = id;
    }

    @Override
    public int minY() {
        return y0;
    }

    public static void main(String[] args) throws IOException {
        String out = args[0];
        long seed = Long.parseLong(args[1]);
        int cx = Integer.parseInt(args[2]), cz = Integer.parseInt(args[3]), r = Integer.parseInt(args[4]);
        int y0 = args.length > 5 ? Integer.parseInt(args[5]) : 40;
        int h = args.length > 6 ? Integer.parseInt(args[6]) : 160;
        int c0x = Math.floorDiv(cx - r, 16), c1x = Math.floorDiv(cx + r, 16);
        int c0z = Math.floorDiv(cz - r, 16), c1z = Math.floorDiv(cz + r, 16);
        CountyPreview dump = new CountyPreview(c0x * 16, c0z * 16, (c1x - c0x + 1) * 16, (c1z - c0z + 1) * 16, y0, h);
        CountyGen gen = new CountyGen(Boolean.getBoolean("county.endless") ? County.endless(seed) : new County(seed), new PlaceCache(16));
        long t = System.currentTimeMillis();
        for (int x = c0x; x <= c1x; x++) {
            for (int z = c0z; z <= c1z; z++) {
                gen.generate(x, z, dump);
            }
        }
        System.err.println("generated " + (c1x - c0x + 1) * (c1z - c0z + 1) + " chunks in " + (System.currentTimeMillis() - t) + " ms");
        try (DataOutputStream o = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(out + ".vox")))) {
            ByteBuffer b = ByteBuffer.allocate(dump.vox.length * 2).order(ByteOrder.LITTLE_ENDIAN);
            for (short s : dump.vox) {
                b.putShort(s);
            }
            o.write(b.array());
        }
        try (PrintWriter w = new PrintWriter(out + ".json")) {
            StringBuilder pal = new StringBuilder();
            for (int i = 0; i < dump.palette.size(); i++) {
                pal.append(i > 0 ? "," : "").append('"').append(dump.palette.get(i).replace("\"", "\\\"")).append('"');
            }
            w.printf("{\"size\":[%d,%d,%d],\"origin\":[%d,%d,%d],\"palette\":[%s],\"lamps\":[]}%n",
                    dump.sx, dump.h, dump.sz, dump.x0, y0, dump.z0, pal);
        }
    }
}
