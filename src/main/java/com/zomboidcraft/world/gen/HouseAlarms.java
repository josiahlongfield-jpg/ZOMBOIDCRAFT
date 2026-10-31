package com.zomboidcraft.world.gen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.zomboidcraft.ZcConfig;
import com.zomboidcraft.ZomboidCraft;
import com.zomboidcraft.power.PowerGrid;
import com.zomboidcraft.skills.Skill;
import com.zomboidcraft.skills.Skills;
import com.zomboidcraft.survival.TimedActions;
import com.zomboidcraft.world.county.House;
import com.zomboidcraft.zombie.Noise;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Zomboid's house alarms. A share of the county's houses are wired (Zomboid's House Alarms Frequency,
 * world.houseAlarms; world/county/House puts up the bell box and the keypad and marks the house's box). The first
 * time someone gets in, by the door, a window or a hole in the wall, the bell or the siren goes for a minute, heard
 * a long way off and drawing every dead thing that hears it; then it's spent. Zombies breaking in set it off too
 * (Zombie House Alarm Triggering, zombieLore.houseAlarms), never the dead already shut inside.
 * <p>
 * An alarm needs power: the mains, a generator nearby, or after the shutoff its battery, which lasts up to twice
 * Zomboid's Alarm Battery Decay (world.alarmBatteryDays) days, each house its own. A flat one stays quiet.
 * <p>
 * Josiah's rule (2026-10-31): a survivor with high Electrical (5) and a screwdriver can cut an alarm short, or cut it
 * before it goes, at the keypad or the bell box (hold G there).
 */
public final class HouseAlarms {
    /** A house's box and its alarm: ringing until that game time; 0 armed, -1 spent (rung out, or cut). */
    public record Alarm(int x0, int y0, int z0, int x1, int y1, int z1, boolean siren, float battery, long until) {
        static final Codec<Alarm> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("x0").forGetter(Alarm::x0),
                Codec.INT.fieldOf("y0").forGetter(Alarm::y0),
                Codec.INT.fieldOf("z0").forGetter(Alarm::z0),
                Codec.INT.fieldOf("x1").forGetter(Alarm::x1),
                Codec.INT.fieldOf("y1").forGetter(Alarm::y1),
                Codec.INT.fieldOf("z1").forGetter(Alarm::z1),
                Codec.BOOL.fieldOf("siren").forGetter(Alarm::siren),
                Codec.FLOAT.fieldOf("battery").forGetter(Alarm::battery),
                Codec.LONG.fieldOf("until").forGetter(Alarm::until)).apply(i, Alarm::new));

        boolean armed() {
            return until == 0;
        }

        boolean inside(BlockPos p) {
            return p.getX() >= x0 && p.getX() <= x1 && p.getY() >= y0 && p.getY() <= y1 && p.getZ() >= z0 && p.getZ() <= z1;
        }

        boolean near(BlockPos p, int r) {
            return p.getX() >= x0 - r && p.getX() <= x1 + r && p.getY() >= y0 - r && p.getY() <= y1 + r
                    && p.getZ() >= z0 - r && p.getZ() <= z1 + r;
        }

        AABB box() {
            return new AABB(x0, y0, z0, x1 + 1, y1 + 1, z1 + 1);
        }

        Alarm until(long t) {
            return new Alarm(x0, y0, z0, x1, y1, z1, siren, battery, t);
        }
    }

    public static final AttachmentType<List<Alarm>> ALARMS = AttachmentRegistry.create(ZomboidCraft.id("house_alarms"),
            builder -> builder.persistent(Alarm.CODEC.listOf()).initializer(List::of));

    // AUDIO's sounds (sounds.json): the bell a 4 s loop, the siren's two tones 4.27 s, the keypad's beep and disarm
    public static final SoundEvent BELL = sound("world.alarm.house.bell", 160);
    public static final SoundEvent SIREN = sound("world.alarm.house.siren", 160);
    public static final SoundEvent BEEP = sound("block.alarm_panel.beep", 8);
    public static final SoundEvent DISARM = sound("block.alarm_panel.disarm", 8);

    /** A minute of ringing. */
    private static final int RING = 1200, EVERY = 10, SEARCH = 2, CUT_LEVEL = 5;
    private static final long DAY = 24000;

    /** The alarms going now: where the sound comes from, and which house. */
    private record Ringing(ServerLevel level, ChunkPos chunk, Alarm alarm, Vec3 at) {
    }

    private static final List<Ringing> RINGING = new ArrayList<>();
    /** Where each one last stood: in an alarmed house's box or out of it (so only coming in sets one off). */
    private static final Map<Entity, Boolean> WAS_IN = new WeakHashMap<>();

    /** The interact key at a keypad or bell box: cut the alarm's wires. */
    public record Cut(BlockPos pos) implements CustomPacketPayload {
        public static final Type<Cut> TYPE = new Type<>(ZomboidCraft.id("house_alarm_cut"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Cut> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, Cut::pos, Cut::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    private HouseAlarms() {
    }

    public static void register() {
        House.setAlarmChance(() -> House.alarmChance(ZcConfig.get().world.houseAlarms));
        ServerTickEvents.END_SERVER_TICK.register(HouseAlarms::tick);
        PayloadTypeRegistry.serverboundPlay().register(Cut.TYPE, Cut.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(Cut.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            player.level().getServer().execute(() -> cut(player, payload.pos()));
        });
    }

    /** Is this one of the alarm's fittings (the keypad, the bell box, the siren box)? */
    public static boolean fitting(BlockState state) {
        String path = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
        return path.equals("ha_keypad") || path.equals("ha_bell_box") || path.equals("ha_siren_box");
    }

    /** The level an alarm needs cutting at. */
    public static int cutLevel() {
        return CUT_LEVEL;
    }

    /** A house's alarm from the chunk writer: "!alarm:w;d;h;siren;battery" at the box's middle, turned by yaw. */
    static void add(ChunkAccess chunk, double x, int y, double z, float yaw, String spec) {
        String[] f = spec.substring("!alarm:".length()).split(";");
        if (f.length < 5) {
            return;
        }
        int w, d, h;
        float battery;
        try {
            w = Integer.parseInt(f[0]);
            d = Integer.parseInt(f[1]);
            h = Integer.parseInt(f[2]);
            battery = Float.parseFloat(f[4]);
        } catch (NumberFormatException e) {
            return;
        }
        if (Math.floorMod(Math.round(yaw / 90f), 2) == 1) {
            int t = w;
            w = d;
            d = t;
        }
        int x0 = (int) Math.round(x - w / 2.0), z0 = (int) Math.round(z - d / 2.0);
        List<Alarm> list = new ArrayList<>(chunk.getAttachedOrElse(ALARMS, List.of()));
        // the box reaches down to a cellar, up to the eaves
        list.add(new Alarm(x0, y - 5, z0, x0 + w - 1, y + h - 1, z0 + d - 1, "1".equals(f[3]), battery, 0));
        chunk.setAttached(ALARMS, List.copyOf(list));
    }

    /** Does it have the power to ring: mains, a generator, or a battery still holding up since the shutoff? */
    static boolean powered(ServerLevel level, Alarm a) {
        BlockPos mid = BlockPos.containing(a.box().getCenter());
        if (PowerGrid.hasPower(level, mid)) {
            return true;
        }
        int days = ZcConfig.get().world.alarmBatteryDays;
        if (days < 0) {
            return false;
        }
        long off = PowerGrid.offAt(level, PowerGrid.district(level, mid));
        return level.getGameTime() < off + (long) (a.battery * 2 * days * DAY);
    }

    private static void tick(MinecraftServer server) {
        ringOn();
        if (server.getTickCount() % EVERY != 0) {
            return;
        }
        ServerLevel level = server.overworld();
        if (level == null || level.players().isEmpty()) {
            return;
        }
        boolean zombies = ZcConfig.get().zombieLore.houseAlarms;
        java.util.Set<Long> seen = new java.util.HashSet<>();
        for (ServerPlayer p : level.players()) {
            int cx = p.getBlockX() >> 4, cz = p.getBlockZ() >> 4;
            for (int dx = -SEARCH; dx <= SEARCH; dx++) {
                for (int dz = -SEARCH; dz <= SEARCH; dz++) {
                    if (!seen.add(((long) (cx + dx) << 32) ^ ((cz + dz) & 0xffffffffL))) {
                        continue;
                    }
                    LevelChunk chunk = level.getChunkSource().getChunkNow(cx + dx, cz + dz);
                    if (chunk != null) {
                        watch(level, chunk, zombies);
                    }
                }
            }
        }
    }

    /** The chunk's alarms: anyone coming into an armed house's box sets it off. */
    private static void watch(ServerLevel level, LevelChunk chunk, boolean zombies) {
        List<Alarm> alarms = chunk.getAttached(ALARMS);
        if (alarms == null || alarms.isEmpty()) {
            return;
        }
        for (Alarm a : alarms) {
            if (!a.armed()) {
                if (a.until > 0 && ringing(chunk.getPos(), a) == null) {
                    resume(level, chunk, a);    // ringing when the world was saved
                }
                continue;
            }
            boolean trip = false;
            for (ServerPlayer p : level.players()) {
                if (!p.isSpectator() && a.near(p.blockPosition(), 6)) {
                    trip |= cameIn(p, a);
                }
            }
            if (zombies) {
                for (Zombie z : level.getEntitiesOfClass(Zombie.class, a.box().inflate(4), Entity::isAlive)) {
                    trip |= cameIn(z, a);
                }
            }
            if (trip && powered(level, a)) {
                ring(level, chunk, a);
            }
        }
    }

    /** Whether it has just come into the box; whoever is first seen inside (a login, the dead shut in) never counts. */
    private static boolean cameIn(Entity e, Alarm a) {
        boolean in = a.inside(e.blockPosition());
        Boolean was = WAS_IN.put(e, in);
        return in && Boolean.FALSE.equals(was);
    }

    private static void ring(ServerLevel level, LevelChunk chunk, Alarm a) {
        Alarm on = a.until(level.getGameTime() + RING);
        replace(chunk, a, on);
        RINGING.add(new Ringing(level, chunk.getPos(), on, source(level, on)));
    }

    private static void resume(ServerLevel level, LevelChunk chunk, Alarm a) {
        if (a.until <= level.getGameTime()) {
            replace(chunk, a, a.until(-1));
        } else {
            RINGING.add(new Ringing(level, chunk.getPos(), a, source(level, a)));
        }
    }

    /** Where it rings from: the bell box, or the middle of the house under the roof. */
    private static Vec3 source(ServerLevel level, Alarm a) {
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int x = a.x0 - 1; x <= a.x1 + 1; x++) {
            for (int z = a.z0 - 1; z <= a.z1 + 1; z++) {
                for (int y = a.y0 + 5; y <= a.y1; y++) {
                    p.set(x, y, z);
                    if (!level.isLoaded(p)) {
                        continue;
                    }
                    String path = BuiltInRegistries.BLOCK.getKey(level.getBlockState(p).getBlock()).getPath();
                    if (path.equals("ha_bell_box") || path.equals("ha_siren_box")) {
                        return Vec3.atCenterOf(p);
                    }
                }
            }
        }
        Vec3 c = a.box().getCenter();
        return new Vec3(c.x, a.y1, c.z);
    }

    private static Ringing ringing(ChunkPos chunk, Alarm a) {
        for (Ringing r : RINGING) {
            if (r.chunk.equals(chunk) && same(r.alarm, a)) {
                return r;
            }
        }
        return null;
    }

    private static boolean same(Alarm a, Alarm b) {
        return a.x0 == b.x0 && a.y0 == b.y0 && a.z0 == b.z0 && a.x1 == b.x1 && a.z1 == b.z1;
    }

    /** Every tick: the bells ring on, and fall quiet when their minute is up. */
    private static void ringOn() {
        for (Iterator<Ringing> it = RINGING.iterator(); it.hasNext(); ) {
            Ringing r = it.next();
            long now = r.level.getGameTime(), left = r.alarm.until - now;
            if (left <= 0) {
                spend(r.level, r.chunk, r.alarm);
                it.remove();
                continue;
            }
            int loop = r.alarm.siren ? 85 : 80;
            if ((RING - left) % loop == 0) {
                r.level.playSound(null, r.at.x, r.at.y, r.at.z, r.alarm.siren ? SIREN : BELL, SoundSource.BLOCKS, 3f, 1f);
            }
            if (left % 40 == 0) {
                Noise.emitEngine(r.level, r.at, 120);
            }
        }
    }

    private static void spend(ServerLevel level, ChunkPos at, Alarm a) {
        LevelChunk chunk = level.getChunkSource().getChunkNow(at.x(), at.z());
        if (chunk != null) {
            replace(chunk, a, a.until(-1));
        }
    }

    private static void replace(ChunkAccess chunk, Alarm old, Alarm now) {
        List<Alarm> list = new ArrayList<>(chunk.getAttachedOrElse(ALARMS, List.of()));
        for (int i = 0; i < list.size(); i++) {
            if (same(list.get(i), old)) {
                list.set(i, now);
            }
        }
        chunk.setAttached(ALARMS, List.copyOf(list));
    }

    /** The alarm a fitting at pos belongs to, with its chunk; null if there's none (or it was never marked). */
    private static Object[] find(ServerLevel level, BlockPos pos) {
        int cx = pos.getX() >> 4, cz = pos.getZ() >> 4;
        for (int dx = -SEARCH; dx <= SEARCH; dx++) {
            for (int dz = -SEARCH; dz <= SEARCH; dz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx + dx, cz + dz);
                if (chunk == null) {
                    continue;
                }
                for (Alarm a : chunk.getAttachedOrElse(ALARMS, List.of())) {
                    if (a.near(pos, 2)) {
                        return new Object[] {chunk, a};
                    }
                }
            }
        }
        return null;
    }

    /** G at a keypad or bell box: with Electrical 5 and a screwdriver, cut the wires (ringing or not). */
    private static void cut(ServerPlayer player, BlockPos pos) {
        ServerLevel level = (ServerLevel) player.level();
        if (player.isSpectator() || player.blockPosition().distSqr(pos) > 36 || !fitting(level.getBlockState(pos))) {
            return;
        }
        Object[] found = find(level, pos);
        if (found == null || ((Alarm) found[1]).until < 0) {
            tell(player, "The wires are already cut. It's dead.");
            return;
        }
        if (Skills.level(player, Skill.ELECTRICAL) < CUT_LEVEL) {
            tell(player, "You don't know which wire. Needs Electrical " + CUT_LEVEL + ".");
            return;
        }
        if (!screwdriver(player)) {
            tell(player, "You need a screwdriver in your hand to get the cover off.");
            return;
        }
        level.playSound(null, pos, BEEP, SoundSource.BLOCKS, 1f, 1f);
        int ticks = Skills.level(player, Skill.ELECTRICAL) >= 8 ? 60 : 120;
        TimedActions.start(player, "cut_alarm", "Cutting the alarm wires", ticks, p -> {
            Object[] again = find(level, pos);
            if (again == null || ((Alarm) again[1]).until < 0) {
                return;
            }
            LevelChunk chunk = (LevelChunk) again[0];
            Alarm a = (Alarm) again[1];
            replace(chunk, a, a.until(-1));
            RINGING.removeIf(r -> r.chunk.equals(chunk.getPos()) && same(r.alarm, a));
            level.playSound(null, pos, DISARM, SoundSource.BLOCKS, 1f, 1f);
            Skills.addXp(p, Skill.ELECTRICAL, 15);
            tell(p, a.until > 0 ? "The alarm dies mid-ring." : "The alarm's dead. It won't go off now.");
        });
    }

    private static boolean screwdriver(ServerPlayer player) {
        Identifier id = ZomboidCraft.id("screwdriver");
        return BuiltInRegistries.ITEM.getKey(player.getMainHandItem().getItem()).equals(id)
                || BuiltInRegistries.ITEM.getKey(player.getOffhandItem().getItem()).equals(id);
    }

    private static void tell(ServerPlayer player, String text) {
        player.sendOverlayMessage(Component.literal(text).withStyle(ChatFormatting.GRAY));
    }

    private static SoundEvent sound(String name, float range) {
        Identifier id = ZomboidCraft.id(name);
        return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createFixedRangeEvent(id, range));
    }
}
