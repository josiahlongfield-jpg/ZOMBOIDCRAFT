package com.zomboidcraft.zombie;

import com.zomboidcraft.ZomboidCraft;
import io.netty.buffer.ByteBuf;
import java.util.Map;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * What a walker is doing to get in, for every client to draw the same: beating on a door, a window or boards
 * (BashGoal), or climbing in through a window whose glass is gone (WindowClimbGoal). The server says what, where and
 * since when; the clients draw it (ZBreak) and the server's hit shapes follow it (BodyTrack).
 */
public final class Acts {
    public static final int NONE = 0, BASH_DOOR = 1, BASH_WINDOW = 2, CLIMB = 3;
    /** Ticks between blows (BashGoal's). */
    public static final int BLOW = 20;
    /** The door's face, and a window's wall face, ahead of the zombie as the character preview draws it beating on them. */
    private static final double DOOR_FACE = 8.6 / ClimbPath.BLOCK, WINDOW_FACE = 2.4 / ClimbPath.BLOCK;
    /** Nearer than this a zombie's own box would be in the wall. */
    private static final double CLEAR = 0.32;

    /**
     * One act: what, the block (the door, window or boards; for a climb the window's lower half), which way the zombie
     * faces (a horizontal direction's 2D value: toward the block, or the way it climbs), the game tick it's timed from
     * (a blow landing; the climb's start), and for a climb where it stood as it began (blocks past the glass, and to
     * its left of the window's middle).
     */
    public record Act(int kind, BlockPos pos, int dir, long tick, float along0, float side0) {
        public Direction facing() {
            return Direction.from2DDataValue(dir);
        }

        public float yaw() {
            return facing().toYRot();
        }
    }

    public static final StreamCodec<ByteBuf, Act> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, Act::kind, BlockPos.STREAM_CODEC, Act::pos, ByteBufCodecs.VAR_INT, Act::dir,
            ByteBufCodecs.VAR_LONG, Act::tick, ByteBufCodecs.FLOAT, Act::along0, ByteBufCodecs.FLOAT, Act::side0, Act::new);

    /** Not saved: a zombie loaded again starts over. */
    public static final AttachmentType<Act> ACT = AttachmentRegistry.create(
            ZomboidCraft.id("act"),
            builder -> builder.syncWith(CODEC, AttachmentSyncPredicate.all()));

    /** Zombies beating on something, to step them into place. Server thread only. */
    private static final Map<Zombie, Boolean> BASHING = new WeakHashMap<>();

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(Acts::tick);
    }

    public static @Nullable Act get(Entity entity) {
        return entity.getAttached(ACT);
    }

    public static boolean climbing(Entity entity) {
        Act a = get(entity);
        return a != null && a.kind == CLIMB;
    }

    /**
     * Starts beating on a door, a window or boards (BashGoal): it faces it square on, and its first blow lands as
     * BashGoal's does, a second in.
     */
    public static void bash(Zombie zombie, BlockPos pos, boolean window) {
        if (zombie.level().isClientSide() || climbing(zombie)) {
            return;
        }
        // boards up off the ground are over a window
        int kind = window || pos.getY() > zombie.getBlockY() ? BASH_WINDOW : BASH_DOOR;
        long now = zombie.level().getGameTime();
        zombie.setAttached(ACT, new Act(kind, pos.immutable(), facing(zombie, pos).get2DDataValue(), now + BLOW - 1, 0f, 0f));
        BASHING.put(zombie, Boolean.TRUE);
    }

    /** A blow landed (BashGoal): the swing is timed from it, so every client's arm comes down on the sound. */
    public static void blow(Zombie zombie, BlockPos pos, boolean window) {
        Act a = get(zombie);
        long now = zombie.level().getGameTime();
        if (a == null || a.kind == CLIMB || !a.pos.equals(pos)) {
            bash(zombie, pos, window);
            a = get(zombie);
            if (a == null) {
                return;
            }
        }
        if ((now - a.tick) % BLOW != 0) {
            zombie.setAttached(ACT, new Act(a.kind, a.pos, a.dir, now, 0f, 0f));
        }
    }

    /** Stopped beating on it (BashGoal). */
    public static void stop(Zombie zombie) {
        Act a = get(zombie);
        if (a != null && a.kind != CLIMB) {
            zombie.removeAttached(ACT);
        }
        BASHING.remove(zombie);
    }

    /** Died mid-climb: it falls where it is, and stops passing through walls. */
    public static void died(Zombie zombie) {
        if (climbing(zombie)) {
            zombie.noPhysics = false;
        }
        if (get(zombie) != null) {
            zombie.removeAttached(ACT);
        }
        BASHING.remove(zombie);
    }

    /**
     * Square on to the block: along the way a door or window faces (the axis it's passed through), toward the block;
     * anything else along whichever way it's further off.
     */
    static Direction facing(Zombie zombie, BlockPos pos) {
        var state = zombie.level().getBlockState(pos);
        Direction.Axis axis = null;
        if (state.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING)) {
            axis = state.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING).getAxis();
        } else if (state.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_AXIS)) {
            axis = state.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_AXIS);
        }
        double dx = pos.getX() + 0.5 - zombie.getX(), dz = pos.getZ() + 0.5 - zombie.getZ();
        if (axis == null) {
            axis = Math.abs(dx) >= Math.abs(dz) ? Direction.Axis.X : Direction.Axis.Z;
        }
        double d = axis == Direction.Axis.X ? dx : dz;
        if (Math.abs(d) < 1e-3) {
            Direction look = zombie.getDirection();
            return look.getAxis() == axis ? look : Direction.fromAxisAndDirection(axis, Direction.AxisDirection.POSITIVE);
        }
        return Direction.fromAxisAndDirection(axis, d > 0 ? Direction.AxisDirection.POSITIVE : Direction.AxisDirection.NEGATIVE);
    }

    /**
     * Beating on something: how far the body is drawn off a zombie standing at (x, z), in blocks, so its hands land on
     * the door or the glass as the preview draws them and it stands square in front of it. With stand, how far the
     * zombie itself should step to get there (it can't stand closer to a wall than its own box allows).
     */
    public static Vec3 bashShift(BlockGetter level, Act a, double x, double z, boolean stand) {
        Direction d = a.facing();
        Direction.Axis axis = d.getAxis(), side = axis == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X;
        VoxelShape shape = level.getBlockState(a.pos).getCollisionShape(level, a.pos);
        double lo = shape.isEmpty() ? 0 : shape.min(axis), hi = shape.isEmpty() ? 1 : shape.max(axis);
        boolean positive = d.getAxisDirection() == Direction.AxisDirection.POSITIVE;
        double face = a.pos.get(axis) + (positive ? lo : hi);
        double dist = (face - (axis == Direction.Axis.X ? x : z)) * (positive ? 1 : -1);
        double want = a.kind == BASH_DOOR ? DOOR_FACE : WINDOW_FACE;
        double along = Math.clamp(dist - (stand ? Math.max(want, CLEAR) : want), -0.4, 0.7);
        double lateral = Math.clamp(a.pos.get(side) + 0.5 - (side == Direction.Axis.X ? x : z), -0.45, 0.45);
        return axis == Direction.Axis.X ? new Vec3(d.getStepX() * along, 0, lateral) : new Vec3(lateral, 0, d.getStepZ() * along);
    }

    /** Seconds into the preview's climb at game time now (ticks, with any part of one). */
    public static float climbTime(Act a, double now) {
        return ClimbPath.START + (float) (now - a.tick) / 20f;
    }

    /** The middle of a window's opening at the floor it's climbed from: its glass, under its lower half. */
    public static Vec3 anchor(BlockPos window) {
        return new Vec3(window.getX() + 0.5, window.getY() - 1, window.getZ() + 0.5);
    }

    /** Where the server keeps a climbing zombie at time u (ClimbPath.place, in the world). */
    public static Vec3 climbPlace(Act a, float u) {
        float[] p = new float[2];
        ClimbPath.place(u, a.along0, a.side0, p);
        Direction d = a.facing();
        Vec3 o = anchor(a.pos);
        // its left, facing d: (d.z, -d.x)
        return new Vec3(o.x + d.getStepX() * p[0] + d.getStepZ() * p[1], o.y, o.z + d.getStepZ() * p[0] - d.getStepX() * p[1]);
    }

    /** Beating on something: it steps square in front of it, close enough for its hands to land. */
    private static void tick(MinecraftServer server) {
        var it = BASHING.keySet().iterator();
        while (it.hasNext()) {
            Zombie zombie = it.next();
            Act a = zombie == null ? null : get(zombie);
            if (a == null || a.kind == CLIMB || zombie.isRemoved() || !zombie.isAlive()) {
                it.remove();
                continue;
            }
            if (com.zomboidcraft.combat.Downed.isLow(zombie)) {
                continue;
            }
            Vec3 step = bashShift(zombie.level(), a, zombie.getX(), zombie.getZ(), true);
            if (step.horizontalDistanceSqr() > 0.01 && zombie.level().noCollision(zombie, zombie.getBoundingBox().move(step))) {
                zombie.getMoveControl().setWantedPosition(zombie.getX() + step.x, zombie.getY(), zombie.getZ() + step.z, 0.5);
            }
        }
    }

    private Acts() {
    }
}
