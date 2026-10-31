package com.zomboidcraft.power;

import com.mojang.serialization.Codec;
import com.zomboidcraft.ZcConfig;
import com.zomboidcraft.ZomboidCraft;
import com.zomboidcraft.world.county.Districts;
import com.zomboidcraft.world.gen.PowerDistricts;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

/**
 * Mains power, Zomboid style. The grid is on when the world starts and goes off on the shutoff day (Zomboid's
 * ElecShutModifier, power.shutoffDays; -1 is off from the start), unless power.alwaysOn is set. Ours goes district
 * by district (world/gen/PowerDistricts): each district at its own hour of that day, so a town can watch the next one
 * go dark. A repaired substation ({@link #restore}) brings its district back for good.
 * <p>
 * Outside a planned county the whole world is one district, {@link #WHOLE}. Generators power what's near them on
 * top of this ({@link Generators}).
 * <p>
 * Events: {@link #OFF} and {@link #ON} fire on the server thread with the level and the district id, once per change
 * (including when the always-on toggle or a restore brings one back). LANDSCAPE plays the substation arc on OFF.
 */
public final class PowerGrid {
    /** The district id used where there are no districts (not a planned county). */
    public static final String WHOLE = "grid";
    private static final long DAY = 24000;

    /** A district's mains changed. */
    @FunctionalInterface
    public interface Changed {
        void changed(ServerLevel level, String districtId);
    }

    /** A district's mains just went off. */
    public static final Event<Changed> OFF = EventFactory.createArrayBacked(Changed.class, cbs -> (level, id) -> {
        for (Changed cb : cbs) {
            cb.changed(level, id);
        }
    });
    /** A district's mains just came back (restored, or the always-on toggle). */
    public static final Event<Changed> ON = EventFactory.createArrayBacked(Changed.class, cbs -> (level, id) -> {
        for (Changed cb : cbs) {
            cb.changed(level, id);
        }
    });

    /** Districts whose OFF has been sent, on the level. */
    private static final AttachmentType<List<String>> DOWN = AttachmentRegistry.create(ZomboidCraft.id("power_down"),
            builder -> builder.persistent(Codec.STRING.listOf()).initializer(List::of));
    /** Districts whose substation has been repaired: on for good. */
    private static final AttachmentType<List<String>> RESTORED = AttachmentRegistry.create(ZomboidCraft.id("power_restored"),
            builder -> builder.persistent(Codec.STRING.listOf()).initializer(List::of));

    private PowerGrid() {
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(PowerGrid::tick);
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPED.register(server -> reset());
    }

    /** Is there power at this spot, from the mains or a running generator nearby? */
    public static boolean hasPower(ServerLevel level, BlockPos pos) {
        return mainsOn(level, pos) || Generators.powers(level, pos);
    }

    /** Are the mains on at this spot (no generators)? Only the overworld has mains. */
    public static boolean mainsOn(ServerLevel level, BlockPos pos) {
        if (level.dimension() != Level.OVERWORLD) {
            return false;
        }
        return isOn(level, district(level, pos));
    }

    /** The district feeding this spot, or {@link #WHOLE}. */
    public static String district(ServerLevel level, BlockPos pos) {
        String id = PowerDistricts.id(level, pos);
        return id == null ? WHOLE : id;
    }

    /** Is this district's mains on now? */
    public static boolean isOn(ServerLevel level, String districtId) {
        if (ZcConfig.get().power.alwaysOn || level.getAttachedOrElse(RESTORED, List.of()).contains(districtId)) {
            return true;
        }
        return level.getGameTime() < offAt(level, districtId);
    }

    /** The game time the district's mains go off: the shutoff day, at the district's own hour. */
    public static long offAt(ServerLevel level, String districtId) {
        int days = ZcConfig.get().power.shutoffDays;
        if (days < 0) {
            return 0;
        }
        long hour = Math.floorMod(districtId.hashCode() * 31L + level.getSeed(), 24L);
        return days * DAY + hour * (DAY / 24);
    }

    /** A repaired substation: its district is on for good. */
    public static void restore(ServerLevel level, String districtId) {
        Set<String> r = new LinkedHashSet<>(level.getAttachedOrElse(RESTORED, List.of()));
        if (r.add(districtId)) {
            level.setAttached(RESTORED, List.copyOf(r));
            sync(level);
        }
    }

    /** Has any district's mains gone off? */
    public static boolean anyDown(ServerLevel level) {
        return !level.getAttachedOrElse(DOWN, List.of()).isEmpty();
    }

    /** Every district id in this world. */
    public static List<String> districts(ServerLevel level) {
        List<String> ids = new ArrayList<>();
        for (Districts.District d : PowerDistricts.yards(level).keySet()) {
            ids.add(d.id());
        }
        if (ids.isEmpty()) {
            ids.add(WHOLE);
        }
        return ids;
    }

    private static List<String> ids;

    private static void tick(MinecraftServer server) {
        ServerLevel level = server.overworld();
        if (level == null || level.getGameTime() % 40 != 0) {
            return;
        }
        sync(level);
    }

    /** Sends OFF/ON for each district whose state changed since last time. */
    private static void sync(ServerLevel level) {
        if (ids == null) {
            ids = districts(level);
        }
        Set<String> down = new LinkedHashSet<>(level.getAttachedOrElse(DOWN, List.of()));
        List<String> wentOff = new ArrayList<>(), cameOn = new ArrayList<>();
        for (String id : ids) {
            boolean on = isOn(level, id);
            if (!on && down.add(id)) {
                wentOff.add(id);
            } else if (on && down.remove(id)) {
                cameOn.add(id);
            }
        }
        if (wentOff.isEmpty() && cameOn.isEmpty()) {
            return;
        }
        level.setAttached(DOWN, List.copyOf(down));
        for (String id : wentOff) {
            ZomboidCraft.LOGGER.info("Power off: {}", id);
            OFF.invoker().changed(level, id);
        }
        for (String id : cameOn) {
            ZomboidCraft.LOGGER.info("Power on: {}", id);
            ON.invoker().changed(level, id);
        }
    }

    /** Forgets the cached district list (a new world on the same server). */
    public static void reset() {
        ids = null;
    }
}
