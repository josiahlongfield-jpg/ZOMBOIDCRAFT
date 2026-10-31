import com.zomboidcraft.world.county.Builders;
import com.zomboidcraft.world.county.County;
import com.zomboidcraft.world.county.Place;
import com.zomboidcraft.world.county.PlaceType;
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

/**
 * Builds one place on its own, on flat grass with a county road out of each gate, and saves it
 * for the preview renderer in the format of CountyPreview (OUT.vox and OUT.json). Much quicker
 * than finding a place of the kind in a generated county when working on a builder.
 *
 *   java -cp build/county SitePreview OUT type variant width depth [seed] [margin]
 *
 * The place's ground (its y = 0) is dump y = 12; a port's water is on its north side.
 */
public final class SitePreview {
    private static final int LEVEL = Integer.getInteger("level", 12);

    public static void main(String[] args) throws IOException {
        String out = args[0];
        PlaceType type = PlaceType.valueOf(args[1].toUpperCase(Locale.ROOT));
        String variant = args[2];
        int w = Integer.parseInt(args[3]), d = Integer.parseInt(args[4]);
        long seed = args.length > 5 ? Long.parseLong(args[5]) : 1L;
        int m = args.length > 6 ? Integer.parseInt(args[6]) : 24;
        // the Waterfront's harbour is east of its seawall (Waterfront.SEAWALL, x = 36), as Layout lays it out
        boolean harbour = variant.equals("waterfront");
        int basin = type == PlaceType.PORT ? d * 2 / 5 : harbour ? w - 1 - 36 : -1;
        // -Dwside=3 -Dquay=N: a site whose sea is on its west side, N wide (Ravenrock's landing); -Dplevel: its ground's y
        int wside = Integer.getInteger("wside", type == PlaceType.PORT ? 0 : harbour ? 1 : -1);
        basin = Integer.getInteger("quay", basin);
        Place p = new Place(type, variant, 0, 0, w - 1, d - 1, Integer.getInteger("plevel", 64), seed, wside, basin);
        Voxels v = new Voxels();
        long t = System.currentTimeMillis();
        Builders.build(p, new County(seed), v);
        System.err.println("built " + p + " in " + (System.currentTimeMillis() - t) + " ms, top y " + v.maxY());
        for (Voxels.Mark mk : v.marks) {
            System.err.println("mark " + mk.what() + " at " + mk.x() + " " + mk.y() + " " + mk.z());
        }

        int sx = w + 2 * m, sz = d + 2 * m, h = Math.min(LEVEL + v.maxY() + 4, 400);
        short[] vox = new short[sx * sz * h];
        List<String> palette = new ArrayList<>(List.of("air"));
        Map<String, Short> ids = new HashMap<>(Map.of("air", (short) 0));
        Setter set = (x, y, z, block) -> {
            x += m;
            z += m;
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
        };
        // the land: soil to the ground, grass on top, the basin flooded; roads from the gates
        int water = Integer.getInteger("plevel") != null ? LEVEL + 62 - p.level : LEVEL - 4;
        for (int x = -m; x < w + m; x++) {
            for (int z = -m; z < d + m; z++) {
                boolean basinHere = basin > 0 && (p.waterSide == 1 ? x > w - 1 - basin : p.waterSide == 3 ? x < basin : z < basin && x >= -m);
                int top = basinHere ? Math.min(LEVEL - 13, water - 9) : LEVEL;
                for (int y = 0; y < top; y++) {
                    set.set(x, y, z, y < top - 3 ? "minecraft:stone" : "minecraft:dirt");
                }
                set.set(x, top, z, basinHere ? "minecraft:mud" : "minecraft:grass_block");
                if (basinHere) {
                    for (int y = top + 1; y <= water; y++) {
                        set.set(x, y, z, "minecraft:water");
                    }
                }
            }
        }
        for (int side = 0; side < 4; side++) {
            if (side == p.waterSide) {
                continue;
            }
            for (int k = 1; k <= m; k++) {
                for (int a = -4; a <= 4; a++) {
                    int x, z;
                    String axis = (side & 1) == 0 ? "z" : "x";
                    switch (side) {
                        case 0 -> { x = p.gateX() + a; z = -k; }
                        case 1 -> { x = w - 1 + k; z = p.gateZ() + a; }
                        case 2 -> { x = p.gateX() + a; z = d - 1 + k; }
                        default -> { x = -k; z = p.gateZ() + a; }
                    }
                    set.set(x, LEVEL, z, a == 0 && k % 10 < 5 ? "road_line_yellow[axis=" + axis + "]" : "asphalt");
                }
            }
        }
        List<String> pal = v.palette();
        for (int cx = 0; cx <= (w - 1) >> 4; cx++) {
            for (int cz = 0; cz <= (d - 1) >> 4; cz++) {
                v.forColumn(cx, cz, (x, y, z, id) -> set.set(x, y + LEVEL, z, pal.get(id)));
            }
        }
        // VEHICLES' two-post hoists ("!hoist:notch;models") as the preview can show them: the posts as
        // steel columns 1.7 either side of the bay's centre line, the car on it raised a block per notch past the first
        List<Voxels.Car> shown = new ArrayList<>();
        for (Voxels.Car c : v.cars) {
            String models = c.models();
            if (models == null || !models.startsWith("!hoist:")) {
                shown.add(c);
                continue;
            }
            int semi = models.indexOf(';');
            int notch = Integer.parseInt(models.substring(7, semi < 0 ? models.length() : semi));
            boolean alongZ = Math.round(c.yaw() / 90.0) % 2 == 0;
            for (int sgn = -1; sgn <= 1; sgn += 2) {
                int px = (int) Math.floor(alongZ ? c.x() + sgn * 1.7 : c.x()), pz = (int) Math.floor(alongZ ? c.z() : c.z() + sgn * 1.7);
                for (int y = 0; y < 4; y++) {
                    set.set(px, c.y() + y + LEVEL, pz, y == 0 ? "as_column_base" : "as_column");
                }
            }
            if (semi >= 0) {
                shown.add(new Voxels.Car(c.x(), c.y() + Math.max(0, notch - 1), c.z(), c.yaw(), c.minLength(), c.maxLength(), c.seed(),
                        models.substring(semi + 1).replace("#customer", "")));
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
        for (Voxels.Car c : shown) {
            cars.append(cars.length() > 0 ? "," : "").append(String.format(Locale.ROOT, "[%.2f,%d,%.2f,%.1f,%.1f,%.1f,\"%s\"]",
                    c.x() + m, c.y() + LEVEL, c.z() + m, c.yaw(), c.minLength(), c.maxLength(), c.models() == null ? "" : c.models().replace("#customer", "")));
        }
        try (PrintWriter pw = new PrintWriter(out + ".json")) {
            StringBuilder ps = new StringBuilder();
            for (int i = 0; i < palette.size(); i++) {
                ps.append(i > 0 ? "," : "").append('"').append(palette.get(i).replace("\"", "\\\"")).append('"');
            }
            pw.printf("{\"size\":[%d,%d,%d],\"origin\":[%d,%d,%d],\"palette\":[%s],\"lamps\":[],\"cars\":[%s]}%n",
                    sx, h, sz, -m, 0, -m, ps, cars);
        }
        System.err.println("dump " + sx + "x" + h + "x" + sz + ", " + palette.size() + " block states, " + v.cars.size() + " cars");
    }

    private interface Setter {
        void set(int x, int y, int z, String block);
    }
}
