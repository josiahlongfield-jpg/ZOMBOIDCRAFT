package com.zomboidcraft.client.sound;

import com.zomboidcraft.ZomboidCraft;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.EXTEfx;

/**
 * Muffles sounds through walls and floors, like hearing a zombie in the next room. The game itself lets sound pass
 * straight through blocks; here a line from your ears to each sound counts the solid blocks in the way, and an
 * OpenAL low-pass filter cuts the high end and some of the volume for each one. Runs on the sound thread (see
 * ChannelMixin); the strength is the Wall Muffling slider in Options > Music & Sounds.
 */
public final class Muffler {
    /** Sounds further than this aren't traced; they are faint anyway and the line would be long. */
    private static final double MAX_TRACE = 48;
    /** Past this many walls a sound can't get any more muffled. */
    private static final float MAX_WALLS = 5;
    /** How much each kind of block in the way counts as a wall. */
    private static final float SOLID = 1f, THIN = 0.45f, LIQUID = 0.25f;

    private static int filter = -1;   // one shared filter: OpenAL copies its settings into each source it's set on
    private static boolean broken;

    /**
     * How many walls stand between the listener and a sound at {@code at} (fractions for windows, doors and
     * water). The blocks the listener's head and the sound are in don't count.
     */
    public static float walls(Vec3 at) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        LocalPlayer player = minecraft.player;
        if (level == null || player == null) {
            return 0;
        }
        Vec3 ear = player.getEyePosition();
        double distance = ear.distanceTo(at);
        if (distance < 1.0 || distance > MAX_TRACE) {
            return 0;
        }
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        long earBlock = BlockPos.asLong(Mth.floor(ear.x), Mth.floor(ear.y), Mth.floor(ear.z));
        long soundBlock = BlockPos.asLong(Mth.floor(at.x), Mth.floor(at.y), Mth.floor(at.z));
        long last = earBlock;
        int steps = (int) Math.ceil(distance * 4);
        float walls = 0;
        for (int i = 1; i < steps; i++) {
            double t = (double) i / steps;
            pos.set(Mth.floor(ear.x + (at.x - ear.x) * t), Mth.floor(ear.y + (at.y - ear.y) * t), Mth.floor(ear.z + (at.z - ear.z) * t));
            long key = pos.asLong();
            if (key == last || key == earBlock || key == soundBlock) {
                continue;
            }
            last = key;
            BlockState state = level.getBlockState(pos);
            if (state.isAir()) {
                continue;
            }
            if (state.canOcclude()) {
                walls += SOLID;
            } else if (!state.getCollisionShape(level, pos).isEmpty()) {
                walls += THIN;
            } else if (!state.getFluidState().isEmpty()) {
                walls += LIQUID;
            }
            if (walls >= MAX_WALLS) {
                return MAX_WALLS;
            }
        }
        return walls;
    }

    /** Sets the filter on an OpenAL source for this many walls; 0 takes it off. Must run on the sound thread. */
    public static void apply(int source, float walls) {
        if (broken) {
            return;
        }
        try {
            float strength = (float) SoundVolumes.muffle();
            float w = walls * strength;
            if (w < 0.05f) {
                AL10.alSourcei(source, EXTEfx.AL_DIRECT_FILTER, EXTEfx.AL_FILTER_NULL);
                return;
            }
            if (filter < 0) {
                filter = EXTEfx.alGenFilters();
                EXTEfx.alFilteri(filter, EXTEfx.AL_FILTER_TYPE, EXTEfx.AL_FILTER_LOWPASS);
            }
            // one wall: most of the hiss and click gone and a little quieter; two or more: a dull thud
            EXTEfx.alFilterf(filter, EXTEfx.AL_LOWPASS_GAIN, (float) Math.max(0.15, Math.exp(-0.45 * w)));
            EXTEfx.alFilterf(filter, EXTEfx.AL_LOWPASS_GAINHF, (float) Math.max(0.01, Math.exp(-1.6 * w)));
            AL10.alSourcei(source, EXTEfx.AL_DIRECT_FILTER, filter);
            int error = AL10.alGetError();
            if (error != AL10.AL_NO_ERROR) {
                fail("OpenAL error " + error);
            }
        } catch (Throwable e) {
            fail(e.toString());
        }
    }

    private static void fail(String why) {
        broken = true;
        ZomboidCraft.LOGGER.warn("Wall muffling is off: this sound device doesn't support filters ({})", why);
    }

    private Muffler() {
    }
}
