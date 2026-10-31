package com.zomboidcraft.world.gen;

import com.zomboidcraft.power.PowerGrid;
import com.zomboidcraft.world.county.Districts;
import com.zomboidcraft.world.events.WorldEvents;
import com.zomboidcraft.zombie.Noise;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

/**
 * The substation yards as the grid runs them (LOOT's com.zomboidcraft.power.PowerGrid). While a district's mains
 * are on its yard hums, a low drone a player hears within {@link #HUM} blocks. When the mains go off
 * ({@link PowerGrid#OFF}) the yard fails the way they do: the bushings crackle and arc for a few seconds, then
 * the bang and a burst of sparks and smoke, heard across the district and drawing the dead, and after it nothing, the hum gone.
 * A restored district hums again.
 * <p>
 * Sounds are vanilla stand-ins (beacon drone, lightning crackle) and the world events' explosion until AUDIO's own arrive.
 */
public final class YardEffects {
    private static final int HUM = 24, HEARD = 256, ARC_TICKS = 70;
    private static final SplittableRandom RNG = new SplittableRandom();
    private static final List<Failing> FAILING = new ArrayList<>();

    /** A yard going down: its bushings, and ticks left of arcing before the bang. */
    private record Failing(ServerLevel level, BlockPos yard, List<BlockPos> bushings, int[] left) {
    }

    private static ServerLevel yardsOf;
    private static Map<Districts.District, BlockPos> yards = Map.of();

    private YardEffects() {
    }

    public static void register() {
        PowerGrid.OFF.register(YardEffects::off);
        ServerTickEvents.END_SERVER_TICK.register(YardEffects::tick);
    }

    private static void off(ServerLevel level, String districtId) {
        for (Map.Entry<Districts.District, BlockPos> e : yards(level).entrySet()) {
            if (!e.getKey().id().equals(districtId)) {
                continue;
            }
            BlockPos yard = e.getValue();
            if (!near(level, yard, HEARD) || !level.isLoaded(yard)) {
                return;    // nobody to hear it go
            }
            FAILING.add(new Failing(level, yard, bushings(level, yard), new int[] {ARC_TICKS}));
            return;
        }
    }

    /** The yard's bushings (pw_bushing, pw_bushing_lo), where the arcs strike; the yard's middle if none. */
    private static List<BlockPos> bushings(ServerLevel level, BlockPos yard) {
        List<BlockPos> out = new ArrayList<>();
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int x = -14; x <= 14; x++) {
            for (int z = -14; z <= 14; z++) {
                for (int y = -1; y <= 9; y++) {
                    p.set(yard.getX() + x, yard.getY() + y, yard.getZ() + z);
                    if (!level.isLoaded(p)) {
                        continue;
                    }
                    String path = BuiltInRegistries.BLOCK.getKey(level.getBlockState(p).getBlock()).getPath();
                    if (path.startsWith("pw_bushing")) {
                        out.add(p.immutable());
                    }
                }
            }
        }
        if (out.isEmpty()) {
            out.add(yard.above(3));
        }
        return out;
    }

    private static void tick(MinecraftServer server) {
        for (Iterator<Failing> it = FAILING.iterator(); it.hasNext(); ) {
            Failing f = it.next();
            int left = --f.left[0];
            BlockPos b = f.bushings.get(RNG.nextInt(f.bushings.size()));
            Vec3 at = Vec3.atCenterOf(b).add(0, 0.5, 0);
            if (left > 0) {
                // arcing: sparks off the bushings, a crackle now and then, louder towards the end
                f.level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, 6 + (ARC_TICKS - left) / 6,
                        0.3, 0.4, 0.3, 0.25);
                if (left % 7 == 0 || RNG.nextInt(9) == 0) {
                    f.level.playSound(null, at.x, at.y, at.z, SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.BLOCKS,
                            0.5f + (ARC_TICKS - left) / (float) ARC_TICKS, 1.6f + RNG.nextFloat() * 0.4f);
                }
                continue;
            }
            // the bang
            f.level.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y, at.z, 3, 0.8, 0.6, 0.8, 0);
            f.level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, 80, 1.2, 1.0, 1.2, 0.8);
            f.level.sendParticles(ParticleTypes.LARGE_SMOKE, at.x, at.y + 1, at.z, 30, 0.6, 1.2, 0.6, 0.03);
            f.level.playSound(null, at.x, at.y, at.z, WorldEvents.EXPLOSION, SoundSource.BLOCKS, 2f, 1.3f);
            f.level.playSound(null, at.x, at.y, at.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.BLOCKS, 3f, 1.4f);
            Noise.emitEngine(f.level, at, 96);
            it.remove();
        }
        // the hum, every 4 seconds, to whoever stands near a live yard
        if (server.getTickCount() % 80 != 0) {
            return;
        }
        ServerLevel level = server.overworld();
        if (level == null || level.players().isEmpty()) {
            return;
        }
        for (Map.Entry<Districts.District, BlockPos> e : yards(level).entrySet()) {
            BlockPos yard = e.getValue();
            if (near(level, yard, HUM) && PowerGrid.isOn(level, e.getKey().id())) {
                level.playSound(null, yard.getX() + 0.5, yard.getY() + 2.5, yard.getZ() + 0.5, SoundEvents.BEACON_AMBIENT,
                        SoundSource.AMBIENT, 1.4f, 0.5f);
            }
        }
    }

    /** The yards, worked out once a level (they never move). */
    private static Map<Districts.District, BlockPos> yards(ServerLevel level) {
        if (yardsOf != level) {
            yards = PowerDistricts.yards(level);
            yardsOf = level;
        }
        return yards;
    }

    private static boolean near(ServerLevel level, BlockPos yard, int r) {
        for (ServerPlayer p : level.players()) {
            if (p.blockPosition().distSqr(yard) < (double) r * r) {
                return true;
            }
        }
        return false;
    }
}
