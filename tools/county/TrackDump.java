import com.zomboidcraft.world.county.County;
import com.zomboidcraft.world.county.Track;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * Writes the planned county's train lines for the preview: every line's samples, a block apart,
 * as {x, bed top y, z}, so tools/city/mesh.py --track can draw the rails and sleepers the game
 * draws along them (they're not blocks, so a CountyPreview dump has only the bed).
 *
 *   java -cp build/county:src/main/resources TrackDump OUT.json [seed]
 */
public final class TrackDump {
    public static void main(String[] args) throws Exception {
        County county = new County(args.length > 1 ? Long.parseLong(args[1]) : 12345);
        try (PrintWriter out = new PrintWriter(Files.newBufferedWriter(Path.of(args[0])))) {
            out.print("{\"tracks\": [");
            boolean firstLine = true;
            for (Track t : county.tracks()) {
                out.print(firstLine ? "\n" : ",\n");
                firstLine = false;
                out.print(" {\"id\": \"" + t.id + "\", \"kind\": \"" + t.kind.name().toLowerCase(Locale.ROOT) + "\", \"points\": [");
                for (int i = 0; i < t.samples(); i++) {
                    out.print(String.format(Locale.ROOT, "%s[%.2f,%.2f,%.2f]", i == 0 ? "" : ",", t.x(i), t.y(i), t.z(i)));
                }
                out.print("]}");
            }
            out.println("\n]}");
        }
        System.out.println("wrote " + county.tracks().size() + " lines to " + args[0]);
    }
}
