package com.zomboidcraft.building.base;

import com.zomboidcraft.ZcConfig;
import com.zomboidcraft.ZomboidCraft;
import com.zomboidcraft.building.base.RoomType.Grade;
import com.zomboidcraft.power.PowerGrid;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import org.jetbrains.annotations.Nullable;

/**
 * The room check (rooms plan R1): from where you stand, fill out across the floor as the light switches do, stopped
 * by walls, doors, garage doors and shut windows. To be a room it must be roofed over every block, at least 3 by 3 and
 * no more than 400 blocks of floor, inside a claim you belong to. The fixtures in and round it say which of the nine
 * rooms it is and its grade; grades that need power fall back to the one below while it's off. The farm is the one
 * room that can be outdoors: planters inside the claim, round where you stand.
 *
 * <p>Other systems read one hook, {@link #current(Player)}: which room the player is in and at what grade. Each owner
 * applies its own effect from that (sleep and cooking SURVIVAL, armoury WEAPONS, motor pool VEHICLES and so on).
 */
public final class Rooms {
    /** A counted room: its type and grade, what the next grade needs (null at Full), and what it was read from. */
    public record Room(RoomType type, Grade grade, @Nullable String next, BlockPos anchor, int floor, boolean powered,
                       Map<Fixture, Integer> fixtures, Set<BlockPos> cells, int claim) {
        public int count(Fixture f) {
            return fixtures.getOrDefault(f, 0);
        }

        public String title() {
            return type.label + ": " + grade.label;
        }
    }

    // ---------------------------------------------------------------- the hook

    private static final Map<UUID, Room> CURRENT = new HashMap<>();

    /** The room the player is standing in, read about once a second; null outside any counted room. */
    public static @Nullable Room current(Player player) {
        return CURRENT.get(player.getUUID());
    }

    /** Is the player in a room of this type at this grade or better? */
    public static boolean in(Player player, RoomType type, Grade atLeast) {
        Room r = current(player);
        return r != null && r.type == type && r.grade.compareTo(atLeast) >= 0;
    }

    // ---------------------------------------------------------------- traffic

    /** The corner readout, sent on walking into a counted room (type -1: you've left it). */
    public record Status(int room, int grade, String next) implements CustomPacketPayload {
        public static final Type<Status> TYPE = new Type<>(ZomboidCraft.id("base_room_status"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Status> CODEC = StreamCodec.of((buf, s) -> {
            buf.writeVarInt(s.room);
            buf.writeVarInt(s.grade);
            buf.writeUtf(s.next);
        }, buf -> new Status(buf.readVarInt(), buf.readVarInt(), buf.readUtf()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** The base page asking for its list. */
    public record Survey() implements CustomPacketPayload {
        public static final Type<Survey> TYPE = new Type<>(ZomboidCraft.id("base_room_survey"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Survey> CODEC = StreamCodec.unit(new Survey());

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** One line on the base page. */
    public record Line(int type, int grade, String next, BlockPos at, int floor, boolean powered, String fixtures) {
    }

    public record Report(List<Line> lines) implements CustomPacketPayload {
        public static final Type<Report> TYPE = new Type<>(ZomboidCraft.id("base_room_report"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Report> CODEC = StreamCodec.of((buf, r) -> {
            buf.writeVarInt(r.lines.size());
            for (Line l : r.lines) {
                buf.writeVarInt(l.type);
                buf.writeVarInt(l.grade);
                buf.writeUtf(l.next);
                buf.writeBlockPos(l.at);
                buf.writeVarInt(l.floor);
                buf.writeBoolean(l.powered);
                buf.writeUtf(l.fixtures);
            }
        }, buf -> {
            int n = buf.readVarInt();
            List<Line> out = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                out.add(new Line(buf.readVarInt(), buf.readVarInt(), buf.readUtf(), buf.readBlockPos(), buf.readVarInt(), buf.readBoolean(), buf.readUtf()));
            }
            return new Report(out);
        });

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    static void register() {
        PayloadTypeRegistry.clientboundPlay().register(Status.TYPE, Status.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(Report.TYPE, Report.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(Survey.TYPE, Survey.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(Survey.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            player.level().getServer().execute(() -> {
                if (player.level() instanceof ServerLevel level && ServerPlayNetworking.canSend(player, Report.TYPE)) {
                    ServerPlayNetworking.send(player, new Report(lines(survey(level, player))));
                }
            });
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTickCount() % 20 != 0) return;
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                if (p.level() instanceof ServerLevel level) look(level, p);
            }
        });
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPED.register(server -> CURRENT.clear());
    }

    /** Reads the room the player is in, and tells them when it's a different one from a second ago. */
    public static void look(ServerLevel level, ServerPlayer player) {
        Room before = CURRENT.get(player.getUUID());
        Room now = read(level, player);
        if (now == null) CURRENT.remove(player.getUUID());
        else CURRENT.put(player.getUUID(), now);
        boolean same = before == null ? now == null : now != null && before.type == now.type && before.grade == now.grade
                && now.cells.contains(before.anchor);
        if (same || !ServerPlayNetworking.canSend(player, Status.TYPE)) return;
        ServerPlayNetworking.send(player, now == null ? new Status(-1, 0, "")
                : new Status(now.type.ordinal(), now.grade.ordinal(), now.next == null ? "" : now.next));
    }

    /** The counted room the player stands in, or null. */
    public static @Nullable Room read(ServerLevel level, Player player) {
        if (!ZcConfig.get().building.claims) return null;
        BlockPos feet = player.blockPosition();
        Claim claim = Claims.at(level, feet);
        if (claim == null || !claim.member(player)) return null;
        return find(level, feet, claim);
    }

    // ---------------------------------------------------------------- the check

    /** The floor filled out from a spot: its cells, the walls round them, and whether it got outdoors or too big. */
    private record Flood(Set<BlockPos> cells, Set<BlockPos> walls, boolean outdoor) {
    }

    private static Flood flood(ServerLevel level, BlockPos start, Claim claim) {
        ZcConfig.Building cfg = ZcConfig.get().building;
        Set<BlockPos> cells = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        Set<BlockPos> walls = new HashSet<>();
        cells.add(start);
        queue.add(start);
        boolean outdoor = level.canSeeSky(start);
        while (!queue.isEmpty()) {
            BlockPos p = queue.poll();
            for (Direction d : Direction.Plane.HORIZONTAL) {
                BlockPos q = p.relative(d);
                if (cells.contains(q) || walls.contains(q)) continue;
                if (!open(level, q)) {
                    walls.add(q);
                    continue;
                }
                if (!claim.contains(q)) {
                    outdoor = true;
                    continue;
                }
                if (level.canSeeSky(q)) outdoor = true;
                cells.add(q);
                if (cells.size() > cfg.roomMaxFloor) {
                    outdoor = true;
                    queue.clear();
                    break;
                }
                queue.add(q);
            }
        }
        return new Flood(cells, walls, outdoor);
    }

    /** The room round {@code start} inside {@code claim}, or null if it isn't one. */
    public static @Nullable Room find(ServerLevel level, BlockPos start, Claim claim) {
        if (!open(level, start)) return null;
        Flood fl = flood(level, start, claim);
        if (fl.outdoor) return farm(level, start, claim);
        return evaluate(level, start, claim, fl);
    }

    private static @Nullable Room evaluate(ServerLevel level, BlockPos start, Claim claim, Flood fl) {
        ZcConfig.Building cfg = ZcConfig.get().building;
        Set<BlockPos> cells = fl.cells, walls = fl.walls;
        int x0 = Integer.MAX_VALUE, x1 = Integer.MIN_VALUE, z0 = Integer.MAX_VALUE, z1 = Integer.MIN_VALUE;
        for (BlockPos c : cells) {
            x0 = Math.min(x0, c.getX());
            x1 = Math.max(x1, c.getX());
            z0 = Math.min(z0, c.getZ());
            z1 = Math.max(z1, c.getZ());
        }
        int side = cfg.roomMinSide;
        if (x1 - x0 + 1 < side || z1 - z0 + 1 < side || cells.size() < side * side) return null;

        // what's in it: each column of floor up to the ceiling (lamps hang there), and the walls round it, three high
        EnumMap<Fixture, Integer> got = new EnumMap<>(Fixture.class);
        Set<BlockPos> read = new HashSet<>();
        for (BlockPos c : cells) {
            for (int dy = 0; dy <= 6; dy++) {
                BlockPos at = c.above(dy);
                BlockState s = level.getBlockState(at);
                tally(level, at, s, got, read);
                if (dy > 0 && s.isCollisionShapeFullBlock(level, at)) break;
            }
        }
        for (BlockPos w : walls) {
            for (int dy = 0; dy <= 2; dy++) tally(level, w.above(dy), level.getBlockState(w.above(dy)), got, read);
        }
        BlockPos anchor = start;
        for (BlockPos c : cells) {
            if (c.getX() < anchor.getX() || c.getX() == anchor.getX() && c.getZ() < anchor.getZ()) anchor = c;
        }
        RoomType type = typeOf(got);
        if (type == null || type == RoomType.FARM) return null;
        boolean powered = PowerGrid.hasPower(level, start);
        if (!powered) {
            for (BlockPos c : cells) {
                if (PowerGrid.hasPower(level, c)) {
                    powered = true;
                    break;
                }
            }
        }
        int masts = type == RoomType.COMMS ? outside(level, cells, Fixture.MAST) : 0;
        int barrels = type == RoomType.POWER ? count(level, claim, Fixture.RAIN_BARREL) : 0;
        String[] next = new String[1];
        Grade grade = grade(type, got, powered, water(level, start), cells.size(), masts, barrels, false, next);
        return new Room(type, grade, next[0], anchor, cells.size(), powered, got, cells, claim.id());
    }

    /** The farm, outdoors: the planters round where you stand inside the claim, with what's beside them. */
    private static @Nullable Room farm(ServerLevel level, BlockPos start, Claim claim) {
        EnumMap<Fixture, Integer> got = new EnumMap<>(Fixture.class);
        Set<BlockPos> read = new HashSet<>();
        int r = 8, glazed = 0;
        boolean water = false;
        for (BlockPos at : BlockPos.betweenClosed(start.offset(-r, -2, -r), start.offset(r, 3, r))) {
            if (!claim.contains(at)) continue;
            BlockState s = level.getBlockState(at);
            if (s.getFluidState().is(Fluids.WATER)) water = true;
            Fixture f = tally(level, at.immutable(), s, got, read);
            if (f == Fixture.PLANTER && glass(level, at)) glazed++;
        }
        if (got.getOrDefault(Fixture.PLANTER, 0) == 0) return null;
        water |= got.getOrDefault(Fixture.RAIN_BARREL, 0) > 0;
        String[] next = new String[1];
        Grade grade = grade(RoomType.FARM, got, false, water, 0, 0, 0, glazed >= got.get(Fixture.PLANTER), next);
        return new Room(RoomType.FARM, grade, next[0], start, got.get(Fixture.PLANTER), false, got, Set.of(start), claim.id());
    }

    private static boolean glass(ServerLevel level, BlockPos planter) {
        for (int dy = 1; dy <= 8; dy++) {
            BlockState s = level.getBlockState(planter.above(dy));
            if (s.isAir()) continue;
            return s.getSoundType() == net.minecraft.world.level.block.SoundType.GLASS;
        }
        return false;
    }

    /** Walkable floor space: nothing solid in it, and not a door (a door bounds the room). */
    private static boolean open(ServerLevel level, BlockPos p) {
        BlockState s = level.getBlockState(p);
        if (s.getBlock() instanceof DoorBlock) return false;
        return s.getCollisionShape(level, p).isEmpty() && Fixture.of(s) != Fixture.GARAGE_DOOR;
    }

    private static @Nullable Fixture tally(ServerLevel level, BlockPos at, BlockState s, Map<Fixture, Integer> got, Set<BlockPos> read) {
        if (!read.add(at)) return null;
        Fixture f = Fixture.of(s);
        if (f == null) return null;
        if (f == Fixture.DOOR && s.hasProperty(DoorBlock.HALF) && s.getValue(DoorBlock.HALF) == net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER) return null;
        // a first-aid cabinet has to have something in it; one carried home is empty until you stock it
        if (f == Fixture.MEDICAL && level.getBlockEntity(at) instanceof Container box && box.isEmpty()) return null;
        got.merge(f, 1, Integer::sum);
        return f;
    }

    /** The room type this furniture adds up to: the most key fixtures wins, ties to the first in the list. */
    static @Nullable RoomType typeOf(Map<Fixture, Integer> got) {
        RoomType best = null;
        int most = 0;
        for (RoomType t : RoomType.values()) {
            int n = 0;
            for (Fixture f : t.keys) n += got.getOrDefault(f, 0);
            if (n > most) {
                most = n;
                best = t;
            }
        }
        return best;
    }

    /** Fixtures of a kind on the outside of a room, within 6 blocks of it and up to 10 above (an antenna mast). */
    private static int outside(ServerLevel level, Set<BlockPos> cells, Fixture want) {
        int x0 = Integer.MAX_VALUE, x1 = Integer.MIN_VALUE, z0 = Integer.MAX_VALUE, z1 = Integer.MIN_VALUE, y = 0;
        for (BlockPos c : cells) {
            x0 = Math.min(x0, c.getX());
            x1 = Math.max(x1, c.getX());
            z0 = Math.min(z0, c.getZ());
            z1 = Math.max(z1, c.getZ());
            y = c.getY();
        }
        int n = 0;
        for (BlockPos at : BlockPos.betweenClosed(x0 - 6, y - 1, z0 - 6, x1 + 6, y + 10, z1 + 6)) {
            if (Fixture.of(level.getBlockState(at)) == want) n++;
        }
        return n;
    }

    /** Fixtures of a kind anywhere in the claim (rain barrels feeding the water tank). */
    private static int count(ServerLevel level, Claim claim, Fixture want) {
        int n = 0;
        for (BlockPos at : BlockPos.betweenClosed(claim.min(), claim.max())) {
            if (Fixture.of(level.getBlockState(at)) == want) n++;
        }
        return n;
    }

    /**
     * Is there water at the taps here? Zomboid's taps run until the water shuts off; the shutoff (SURVIVAL's) isn't in
     * yet, so for now they always run. The Full power-and-water room is what keeps them running after it.
     */
    static boolean water(ServerLevel level, BlockPos pos) {
        return true;
    }

    // ---------------------------------------------------------------- grades

    /**
     * The grade a room of this type reaches with these fixtures, and in {@code next[0]} what the grade above needs.
     * Grades that need power stop at the grade below while it's off, so a room never goes dark, it drops back.
     */
    static Grade grade(RoomType type, Map<Fixture, Integer> f, boolean powered, boolean water, int floor, int masts, int barrels,
                       boolean glazed, String[] next) {
        Ladder l = new Ladder(next);
        int beds = n(f, Fixture.BED), lit = powered ? n(f, Fixture.LAMP_LIT) : 0;
        switch (type) {
            case BUNK -> {
                if (l.step(beds >= 2, beds == 1 ? "a second bed" : "two beds (cot, bunk or bed)")) break;
                if (l.step(n(f, Fixture.FOOTLOCKER) >= beds && n(f, Fixture.DOOR) >= 1,
                        n(f, Fixture.DOOR) == 0 ? "a door that closes" : "a footlocker for every bed (" + n(f, Fixture.FOOTLOCKER) + " of " + beds + ")")) break;
                l.step(n(f, Fixture.WINDOW) == 0, "every window boarded or shuttered");
            }
            case KITCHEN -> {
                boolean stove = n(f, Fixture.WOOD_STOVE) > 0 || n(f, Fixture.STOVE) > 0 && powered;
                if (l.step(stove && n(f, Fixture.COUNTER) >= 1, !stove ? (n(f, Fixture.STOVE) > 0 ? "power for the stove, or a wood stove" : "a stove")
                        : "a counter")) break;
                if (l.step(n(f, Fixture.FRIDGE) >= 1 && powered && n(f, Fixture.SINK) >= 1 && water,
                        n(f, Fixture.FRIDGE) == 0 ? "a fridge" : !powered ? "power for the fridge" : "a sink with water")) break;
                l.step(n(f, Fixture.SHELF) >= 3, "pantry shelving (" + n(f, Fixture.SHELF) + " of 3 shelves)");
            }
            case MED_BAY -> {
                if (l.step(beds + n(f, Fixture.HOSPITAL_BED) >= 1 && n(f, Fixture.MEDICAL) >= 1,
                        n(f, Fixture.MEDICAL) == 0 ? "a stocked first-aid cabinet" : "a bed")) break;
                if (l.step(n(f, Fixture.HOSPITAL_BED) >= 1 && lit >= 1,
                        n(f, Fixture.HOSPITAL_BED) == 0 ? "a hospital bed" : "a light on, with power")) break;
                l.step(n(f, Fixture.SURGICAL) >= 1 && n(f, Fixture.SINK) >= 1 && water && powered,
                        n(f, Fixture.SURGICAL) == 0 ? "a surgical table" : n(f, Fixture.SINK) == 0 ? "a sink with water" : "power");
            }
            case ARMOURY -> {
                if (l.step(n(f, Fixture.GUN_BENCH) >= 1, "a gun bench")) break;
                if (l.step(n(f, Fixture.GUN_RACK) >= 2 && n(f, Fixture.AMMO) >= 1,
                        n(f, Fixture.GUN_RACK) < 2 ? "two gun racks (" + n(f, Fixture.GUN_RACK) + " of 2)" : "an ammo can or locker")) break;
                l.step(lit >= 1 && n(f, Fixture.PARTS_BIN) >= 1, lit == 0 ? "a light on, with power" : "a parts bin");
            }
            case WORKSHOP -> {
                if (l.step(n(f, Fixture.WORKBENCH) >= 1, "a workbench")) break;
                if (l.step(n(f, Fixture.TOOL_RACK) >= 1 && n(f, Fixture.SAWHORSE) >= 1,
                        n(f, Fixture.TOOL_RACK) == 0 ? "a tool rack" : "a sawhorse")) break;
                l.step((n(f, Fixture.ANVIL) + n(f, Fixture.WELDER)) >= 1 && powered,
                        n(f, Fixture.ANVIL) + n(f, Fixture.WELDER) == 0 ? "an anvil or a welding bench" : "power for the power tools");
            }
            case MOTOR_POOL -> {
                if (l.step(n(f, Fixture.GARAGE_DOOR) >= 1 && floor >= 15,
                        n(f, Fixture.GARAGE_DOOR) == 0 ? "a garage door a car fits through" : "room for a car (3 by 5)")) break;
                if (l.step(n(f, Fixture.LIFT) >= 1 && n(f, Fixture.TOOL_CART) >= 1, n(f, Fixture.LIFT) == 0 ? "a two-post lift" : "a tool cart")) break;
                l.step(n(f, Fixture.FUEL_TANK) >= 1 || n(f, Fixture.DRUM) >= 4, "a fuel tank or four fuel drums (" + n(f, Fixture.DRUM) + " of 4)");
            }
            case COMMS -> {
                if (l.step(n(f, Fixture.RADIO) >= 1 && powered, n(f, Fixture.RADIO) == 0 ? "a radio set" : "power for the radio")) break;
                if (l.step(masts >= 1, "an antenna mast on the roof or the wall outside")) break;
                l.step(n(f, Fixture.MAP_TABLE) >= 1, "a map table");
            }
            case POWER -> {
                if (l.step(n(f, Fixture.GENERATOR) >= 1, "a generator")) break;
                if (l.step((n(f, Fixture.FUEL_TANK) + n(f, Fixture.DRUM)) >= 1 && n(f, Fixture.SWITCHBOARD) >= 1,
                        n(f, Fixture.FUEL_TANK) + n(f, Fixture.DRUM) == 0 ? "fuel storage" : "a power switchboard")) break;
                l.step(n(f, Fixture.WATER_TANK) >= 1 && n(f, Fixture.FILTER) >= 1 && barrels >= 1,
                        n(f, Fixture.WATER_TANK) == 0 ? "a water tank" : n(f, Fixture.FILTER) == 0 ? "a water filter" : "a rain barrel in the base to feed it");
            }
            case FARM -> {
                if (l.step(n(f, Fixture.PLANTER) >= 4 && water, n(f, Fixture.PLANTER) < 4 ? "four planters (" + n(f, Fixture.PLANTER) + " of 4)"
                        : "water nearby (a rain barrel)")) break;
                if (l.step(n(f, Fixture.FENCE) >= 6 && n(f, Fixture.COMPOST) >= 1, n(f, Fixture.FENCE) < 6 ? "a fence round it" : "a compost bin")) break;
                l.step(glazed, "a greenhouse: a glass roof over the beds");
            }
        }
        return l.grade;
    }

    private static int n(Map<Fixture, Integer> f, Fixture k) {
        return f.getOrDefault(k, 0);
    }

    /** Climbs the grades while each is met; the first unmet one is what's next. */
    private static final class Ladder {
        Grade grade = Grade.NONE;
        final String[] next;

        Ladder(String[] next) {
            this.next = next;
        }

        /** True when this step isn't met (so stop climbing). */
        boolean step(boolean met, String needs) {
            if (!met) {
                next[0] = needs;
                return true;
            }
            grade = Grade.values()[grade.ordinal() + 1];
            next[0] = null;
            return false;
        }
    }

    // ---------------------------------------------------------------- the base page

    /** Every counted room in the player's claims: each patch of floor read once. */
    public static List<Room> survey(ServerLevel level, Player player) {
        List<Room> out = new ArrayList<>();
        for (Claim claim : Claims.of(level, player.getUUID())) {
            Set<BlockPos> done = new HashSet<>();
            for (BlockPos p : BlockPos.betweenClosed(claim.min(), claim.max())) {
                if (out.size() >= 40) return out;
                if (done.contains(p)) continue;
                BlockPos at = p.immutable();
                if (!open(level, at) || level.getBlockState(at.below()).getCollisionShape(level, at.below()).isEmpty() || level.canSeeSky(at)) continue;
                Flood fl = flood(level, at, claim);
                done.addAll(fl.cells);
                Room r = fl.outdoor ? null : evaluate(level, at, claim, fl);
                if (r == null) continue;
                out.add(r);
            }
            // the farm, outdoors: read round the first planter in the claim
            for (BlockPos p : BlockPos.betweenClosed(claim.min(), claim.max())) {
                if (Fixture.of(level.getBlockState(p)) != Fixture.PLANTER) continue;
                Room r = farm(level, p.above().immutable(), claim);
                if (r != null) out.add(r);
                break;
            }
        }
        return out;
    }

    static List<Line> lines(List<Room> rooms) {
        List<Line> out = new ArrayList<>();
        for (Room r : rooms) {
            StringBuilder s = new StringBuilder();
            for (Map.Entry<Fixture, Integer> e : r.fixtures.entrySet()) {
                if (!r.type.keys.contains(e.getKey()) && e.getKey() != Fixture.BED && e.getKey() != Fixture.SINK && e.getKey() != Fixture.SHELF) continue;
                if (!s.isEmpty()) s.append(", ");
                s.append(e.getValue()).append(' ').append(e.getKey().label);
            }
            out.add(new Line(r.type.ordinal(), r.grade.ordinal(), r.next == null ? "" : r.next, r.anchor, r.floor, r.powered, s.toString()));
        }
        return out;
    }

    private Rooms() {
    }
}
