import com.zomboidcraft.world.county.County;
import com.zomboidcraft.world.county.Place;
import com.zomboidcraft.world.county.Road;
import com.zomboidcraft.world.county.Terrain;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.List;
import javax.imageio.ImageIO;

/**
 * Draws a map of the planned county: shaded terrain, water, woods, roads and the places with
 * their variant names. For tuning the planner outside the game.
 *
 *   javac -d build/county src/main/java/com/zomboidcraft/world/county/*.java tools/county/CountyMap.java
 *   java -cp build/county CountyMap OUT.png [seed] [centreX centreZ] [blocksPerPixel] [pixels]
 */
public final class CountyMap {
    public static void main(String[] args) throws Exception {
        long seed = args.length > 1 ? Long.parseLong(args[1]) : 12345L;
        int cx = args.length > 3 ? Integer.parseInt(args[2]) : 0, cz = args.length > 3 ? Integer.parseInt(args[3]) : 0;
        int scale = args.length > 4 ? Integer.parseInt(args[4]) : 4;
        int size = args.length > 5 ? Integer.parseInt(args[5]) : 1500;
        County county = new County(seed);
        Terrain t = county.terrain;
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        int x0 = cx - size * scale / 2, z0 = cz - size * scale / 2;
        int x1 = x0 + size * scale, z1 = z0 + size * scale;
        List<Place> places = county.placesNear(x0, z0, x1, z1, County.SKIRT);
        List<Road> roads = county.roadsNear(x0, z0, x1, z1);
        System.out.println(places.size() + " places, " + roads.size() + " roads");
        double[] hrow = new double[size];
        for (int j = 0; j < size; j++) {
            for (int i = 0; i < size; i++) {
                double x = x0 + i * scale, z = z0 + j * scale;
                double h = county.ground(x, z, places);
                double hn = county.ground(x + scale, z - scale, places);
                Road.Hit hit = new Road.Hit();
                for (Road r : roads) {
                    r.nearest(x, z, hit);
                }
                int rgb;
                double shade = Math.max(-1, Math.min(1, (hn - h) * 0.35 / scale));
                if (hit.road != null && hit.distance <= hit.road.kind.half + 1) {
                    rgb = hit.road.kind == Road.Kind.HIGHWAY ? 0x2a2a2c : hit.road.kind == Road.Kind.COUNTY ? 0x4a4744 : 0x8a7350;
                } else if (Terrain.isWater(h)) {
                    double depth = Math.min(1, (Terrain.WATER - h) / 14);
                    rgb = mix(0x4f6a72, 0x1f3440, depth);
                } else {
                    double f = t.forest(x, z), m = t.moisture(x, z);
                    int base = h > 92 ? 0x6e7258 : mix(0x7d8a52, 0x6c7a45, Math.max(0, m));
                    if (h < Terrain.WATER + 2.5) {
                        base = 0x8f8a6a;
                    }
                    rgb = mix(base, 0x2f4a2a, f * 0.9);
                    rgb = mix(rgb, 0xb0b0a0, Math.max(0, Math.min(1, (h - 70) / 60)) * 0.25);
                }
                rgb = shade > 0 ? mix(rgb, 0xffffff, shade * 0.4) : mix(rgb, 0x000000, -shade * 0.5);
                img.setRGB(i, j, rgb);
            }
        }
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        for (Place p : places) {
            int px = (p.x0 - x0) / scale, pz = (p.z0 - z0) / scale, w = p.width() / scale, d = p.depth() / scale;
            Color c = switch (p.type) {
                case DOWNTOWN -> new Color(200, 60, 60);
                case TOWN -> new Color(220, 140, 60);
                case SUBURB -> new Color(230, 210, 90);
                case FARM -> new Color(150, 200, 90);
                case PORT -> new Color(80, 170, 230);
                case MILITARY -> new Color(160, 160, 120);
                case ROADSIDE -> new Color(220, 120, 200);
            };
            g.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), 110));
            g.fillRect(px, pz, w, d);
            g.setColor(c);
            g.drawRect(px, pz, w, d);
            String label = p.type.name().toLowerCase() + ": " + p.variant;
            g.setColor(new Color(0, 0, 0, 170));
            g.drawString(label, px + 1, pz - 3);
            g.setColor(Color.WHITE);
            g.drawString(label, px, pz - 4);
        }
        g.setColor(Color.RED);
        g.fillOval((0 - x0) / scale - 4, (0 - z0) / scale - 4, 8, 8);
        g.dispose();
        ImageIO.write(img, "png", new File(args[0]));
    }

    static int mix(int a, int b, double t) {
        t = Math.max(0, Math.min(1, t));
        int r = (int) ((a >> 16 & 255) * (1 - t) + (b >> 16 & 255) * t);
        int gg = (int) ((a >> 8 & 255) * (1 - t) + (b >> 8 & 255) * t);
        int bb = (int) ((a & 255) * (1 - t) + (b & 255) * t);
        return r << 16 | gg << 8 | bb;
    }
}
