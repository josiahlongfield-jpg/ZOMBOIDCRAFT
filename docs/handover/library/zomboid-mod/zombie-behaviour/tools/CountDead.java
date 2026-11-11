import com.zomboidcraft.world.county.*;
import com.zomboidcraft.zombie.population.Budgets;
import java.util.*;
public class CountDead {
    public static void main(String[] a) {
        for (String s : a) {
            long seed = Long.parseLong(s);
            County c = new County(seed);
            double sum = 0; boolean isle = false; int n = 0;
            Map<String, double[]> byType = new TreeMap<>();
            int x0=Integer.MAX_VALUE,z0=Integer.MAX_VALUE,x1=Integer.MIN_VALUE,z1=Integer.MIN_VALUE;
            for (Place p : c.allPlaces()) {
                n++;
                x0=Math.min(x0,p.x0);z0=Math.min(z0,p.z0);x1=Math.max(x1,p.x1);z1=Math.max(z1,p.z1);
                if (c.island((p.x0 + p.x1) / 2, (p.z0 + p.z1) / 2)) { isle = true; continue; }
                int t = Budgets.of(p).total();
                sum += t;
                byType.computeIfAbsent(p.type.name(), k -> new double[2])[0] += t;
                byType.get(p.type.name())[1]++; if (p.type.name().equals("DOWNTOWN")) byType.computeIfAbsent("  dt " + p.variant + " " + p.width() + "x" + p.depth(), k -> new double[2])[1]++;
            }
            if (isle) sum += Budgets.ISLAND.total();
            double area = (double)(x1-x0)*(z1-z0);
            System.out.printf("seed %d places %d table %.0f island %s area %.0fx%.0f wildMax %.0f%n", seed, n, sum, isle, (double)(x1-x0), (double)(z1-z0), area*Budgets.WILD);
            byType.forEach((k,v)->System.out.printf("  %s %d places %.0f%n", k, (int)v[1], v[0]));
        }
    }
}
