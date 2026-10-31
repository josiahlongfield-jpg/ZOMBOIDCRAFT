package com.zomboidcraft.power;

import com.zomboidcraft.zombie.Noise;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * A generator's tank, wear and switches, Zomboid's way: it runs while it has fuel and isn't broken, burns
 * GeneratorFuelConsumption litres an in-game hour, wears as it runs (and stalls now and then once it's worn), is heard
 * a long way off, and powers what's round it only once someone who has read How to Use Generators has connected it.
 */
public class GeneratorEntity extends BlockEntity {
    private static final int HOUR = 1000;

    float fuel;
    int condition = 100;
    boolean running, connected;

    public GeneratorEntity(BlockPos pos, BlockState state) {
        super(Generators.ENTITY, pos, state);
    }

    Generators.Kind kind() {
        return getBlockState().getBlock() instanceof GeneratorBlock g ? g.kind : Generators.Kind.PORTABLE;
    }

    /** Running and wired in: what it's powering right now. */
    boolean powering() {
        return running && connected && fuel > 0 && condition > 0;
    }

    void tick(ServerLevel level) {
        if (!running) {
            return;
        }
        Generators.Kind k = kind();
        if (fuel <= 0 || condition <= 0) {
            setRunning(level, false);
            return;
        }
        fuel = Math.max(0, fuel - (float) (Generators.fuelPerHour() * k.burn / HOUR));
        long t = level.getGameTime();
        if (t % HOUR == 0) {
            if (level.random.nextInt(12) == 0) {
                condition--;
            }
            if (condition < 20 && level.random.nextInt(20) == 0) {
                setRunning(level, false);
                level.playSound(null, worldPosition, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.8f, 0.6f);
            }
        }
        if (t % 40 == 0) {
            level.playSound(null, worldPosition, SoundEvents.MINECART_RIDING, SoundSource.BLOCKS, k.volume, 0.55f);
        }
        if (t % 100 == 0) {
            Noise.emit(level, Vec3.atCenterOf(worldPosition), k.noise);
        }
        if (t % 20 == 0) {
            setChanged();
        }
        Generators.track(level, worldPosition, powering());
    }

    void setRunning(ServerLevel level, boolean on) {
        running = on;
        BlockState s = getBlockState();
        if (s.hasProperty(GeneratorBlock.LIT) && s.getValue(GeneratorBlock.LIT) != on) {
            level.setBlock(worldPosition, s.setValue(GeneratorBlock.LIT, on), 3);
        }
        Generators.track(level, worldPosition, powering());
        setChanged();
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level instanceof ServerLevel server) {
            Generators.track(server, worldPosition, false);
        }
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putFloat("fuel", fuel);
        output.putInt("condition", condition);
        output.putBoolean("running", running);
        output.putBoolean("connected", connected);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        fuel = input.getFloatOr("fuel", 0f);
        condition = input.getIntOr("condition", 100);
        running = input.getBooleanOr("running", false);
        connected = input.getBooleanOr("connected", false);
    }
}
