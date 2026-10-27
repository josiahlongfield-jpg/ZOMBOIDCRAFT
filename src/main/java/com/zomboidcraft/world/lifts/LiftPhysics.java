package com.zomboidcraft.world.lifts;

import com.zomboidcraft.world.blocks.CityBlocks;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Riding lifts and escalators. A lift car's floor and roof hold up whatever stands on them and
 * carry it up and down with the car (the shaft's own walls and the shut landing doors keep riders
 * in), and its ceiling stops a jump. An escalator's steps move whoever stands on them along, up
 * or down. Whoever moves an entity does this: the client for its own player, the server for
 * everything else.
 */
public final class LiftPhysics {
    /** How far an escalator carries you a tick, along the stairs' run. */
    private static final double ESCALATOR = 0.07;
    /** Ground friction (0.6 x 0.91): what's added each tick works out to that much movement a tick. */
    private static final double GRIP = 1 - 0.546;
    private static Block stoppedSteps;
    private static boolean lookedUp;

    /** Whether this side moves this entity: your own player on your game, every non-player on the server. */
    public static boolean handles(Entity e) {
        if (e instanceof LiftCar || e.isPassenger() || e instanceof Projectile || e.isSpectator()) {
            return false;
        }
        return e.level().isClientSide() ? e instanceof Player p && p.isLocalPlayer() : !(e instanceof Player);
    }

    /** At the start of every entity's tick: carried with the car it stands on, then the escalator under it. */
    public static void carry(Entity e) {
        if (!handles(e)) {
            return;
        }
        escalator(e);
        List<LiftCar> cars = LiftCar.loaded(e.level().isClientSide());
        if (cars.isEmpty()) {
            return;
        }
        for (LiftCar car : cars) {
            LiftShaft s = car.shaft();
            if (s == null || car.level() != e.level() || !s.over(e.getX(), e.getZ(), 0)) {
                continue;
            }
            double was = car.carYO(), now = car.carY(), moved = now - was;
            if (moved == 0) {
                continue;
            }
            double feet = e.getY();
            for (double top : new double[] {was, was + LiftShaft.ROOF}) {
                // standing on the floor (or the roof) a tick ago: it goes where the car went
                if (feet >= top - 0.35 && feet <= top + 0.08) {
                    e.setPos(e.getX(), Math.max(feet + moved, top + moved), e.getZ());
                    if (moved > 0 && e.getDeltaMovement().y < 0) {
                        e.setDeltaMovement(e.getDeltaMovement().multiply(1, 0, 1));
                    }
                    e.resetFallDistance();
                    return;
                }
            }
        }
    }

    /** Called with an entity's movement before the world stops it: a car's floor, roof and ceiling stop it first. */
    public static Vec3 clip(Entity e, Vec3 move) {
        if (move.y == 0 || !handles(e)) {
            return move;
        }
        List<LiftCar> cars = LiftCar.loaded(e.level().isClientSide());
        if (cars.isEmpty()) {
            return move;
        }
        AABB box = e.getBoundingBox();
        double my = move.y;
        for (LiftCar car : cars) {
            LiftShaft s = car.shaft();
            if (s == null || car.level() != e.level()
                    || !s.overlaps(box.minX + Math.min(move.x, 0), box.minZ + Math.min(move.z, 0), box.maxX + Math.max(move.x, 0), box.maxZ + Math.max(move.z, 0))) {
                continue;
            }
            double y = car.carY();
            if (my < 0) {
                for (double plane : new double[] {y, y + LiftShaft.ROOF}) {
                    if (box.minY >= plane - 0.05 && box.minY + my < plane) {
                        my = plane - box.minY;
                    }
                }
            } else {
                double ceiling = y + LiftShaft.CEILING;
                if (box.minY >= y - 0.5 && box.maxY <= ceiling + 0.05 && box.maxY + my > ceiling) {
                    my = Math.max(0, ceiling - box.maxY);
                }
                double under = y - 0.15;
                if (box.maxY <= under + 0.05 && box.maxY + my > under) {
                    my = Math.max(0, under - box.maxY);
                }
            }
        }
        return my == move.y ? move : new Vec3(move.x, my, move.z);
    }

    /** Whether a player's in (or on) a lift car on the server: standing in a shaft isn't flying. */
    public static boolean riding(Player player) {
        for (LiftCar car : LiftCar.loaded(player.level().isClientSide())) {
            LiftShaft s = car.shaft();
            if (s != null && car.level() == player.level() && s.over(player.getX(), player.getZ(), -0.3)) {
                double y = car.carY();
                if (player.getY() > y - 1.5 && player.getY() < y + LiftShaft.ROOF + 1.5) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Whether a point's inside a car, for the client's hints. */
    public static LiftCar inside(Entity e) {
        for (LiftCar car : LiftCar.loaded(e.level().isClientSide())) {
            LiftShaft s = car.shaft();
            if (s != null && car.level() == e.level() && s.over(e.getX(), e.getZ(), 0)) {
                double y = car.carY();
                if (e.getY() > y - 0.2 && e.getY() < y + LiftShaft.CEILING - 1) {
                    return car;
                }
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ escalators

    /** On a running escalator's steps: carried along the way they go. */
    static void escalator(Entity e) {
        if (!e.onGround()) {
            return;
        }
        BlockPos on = e.getOnPos();
        BlockState state = e.level().getBlockState(on);
        Direction way = way(state);
        if (way == null) {
            on = e.blockPosition();
            state = e.level().getBlockState(on);
            way = way(state);
            if (way == null) {
                return;
            }
        }
        double a = ESCALATOR * GRIP;
        e.setDeltaMovement(e.getDeltaMovement().add(way.getStepX() * a, 0, way.getStepZ() * a));
    }

    /** Which way an escalator's steps carry you, or null when it's not one. The old stopped ones run up now too. */
    private static Direction way(BlockState state) {
        if (state.is(Lifts.ESCALATOR)) {
            Direction f = state.getValue(EscalatorBlock.FACING);
            return state.getValue(EscalatorBlock.RUN) == EscalatorBlock.Run.UP ? f : f.getOpposite();
        }
        if (!lookedUp) {
            lookedUp = true;
            BlockState steps = CityBlocks.resolve("tx_esc_step");
            stoppedSteps = steps == null ? null : steps.getBlock();
        }
        if (stoppedSteps != null && state.is(stoppedSteps) && state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            return state.getValue(BlockStateProperties.HORIZONTAL_FACING);
        }
        return null;
    }

    private LiftPhysics() {
    }
}
