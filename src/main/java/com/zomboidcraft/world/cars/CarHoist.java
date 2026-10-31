package com.zomboidcraft.world.cars;

import com.zomboidcraft.ZomboidCraft;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * A two-post car lift, as in any 80s garage (refs/autoshop/two-post-lift): two blue posts either side of the
 * bay, a carriage riding up the inside of each with two swing arms, rubber pads under the car's lift points,
 * the control box (green UP, red emergency stop) and the power unit (motor, pump, oil reservoir and the
 * lowering lever) on the first post.
 *
 * <p>Working it: drive the car in between the posts, nose either way; the interact key on the control box
 * swings the arms in under it (only once it's centred and not too big), then takes it up a notch at a time:
 * wheels free (tyres, brakes), chest height (suspension, steering), full height (exhaust, tank, gearbox,
 * oil). Sneak at the box to take it down a notch, and at the floor to swing the arms out. The lever on the
 * power unit is the hand pump Zomboid never had: each stroke raises it a little with no power, and sneaking
 * opens the valve to let it down slowly. Raised, the car can't be driven, and everything underneath is in reach
 * standing up ({@link Mechanics}). Each post is solid (hull blocks), and the car's hull is laid again at each
 * notch so you can walk under it but not through it.
 *
 * <p>The entity stands at the middle of the bay on the floor, turned so local +z runs along the bay.
 */
public class CarHoist extends Entity {
    /** The lift heights (metres of the car's rise): the floor, wheels free, chest height, full height. */
    public static final float[] NOTCHES = {0f, 0.5f, 1.25f, 1.85f};
    public static final String[] NOTCH_NAMES = {"the floor", "wheels free", "chest height", "full height"};
    /** The posts' middles, either side (local x), and their size. */
    public static final float POST_X = 1.7f, POST_W = 0.3f, POST_D = 0.36f, POST_H = 3.6f;
    /** The biggest car it takes: length and width, metres. */
    public static final float MAX_LENGTH = 6.6f, MAX_WIDTH = 2.6f;
    /** Rise per tick on the motor and per stroke of the hand pump, metres; the valve lets it down this fast a tick. */
    private static final float MOTOR = 0.03f, STROKE = 0.06f, VALVE = 0.012f;
    /** The control box and the pump lever (local, on the first post's outside), where the interact key works them. */
    public static final Vec3 CONTROL = new Vec3(POST_X + POST_W / 2 + 0.07, 1.35, -0.05), LEVER = new Vec3(POST_X + POST_W / 2 + 0.16, 0.85, 0.12);

    private static final EntityDataAccessor<Float> HEIGHT = SynchedEntityData.defineId(CarHoist.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> ARMS = SynchedEntityData.defineId(CarHoist.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> TARGET = SynchedEntityData.defineId(CarHoist.class, EntityDataSerializers.INT);
    /** The car on the arms (its entity id, for the client to reach under), or -1. */
    private static final EntityDataAccessor<Integer> CAR = SynchedEntityData.defineId(CarHoist.class, EntityDataSerializers.INT);
    /** Where the arms reach in to: half the car's track and the distance of its lift points from the middle, metres. */
    private static final EntityDataAccessor<Float> REACH_X = SynchedEntityData.defineId(CarHoist.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> REACH_Z = SynchedEntityData.defineId(CarHoist.class, EntityDataSerializers.FLOAT);

    private UUID carId;
    private boolean armsIn;
    /** Server: whether the motor ran last tick (for the hum) and whether the hull's laid for where the car sits. */
    private boolean running, hullLaid = true, postsLaid;
    private int lost;
    /** Client: last tick's height and arms, for smooth drawing. */
    public float heightO, armsO;

    public CarHoist(EntityType<? extends CarHoist> type, Level level) {
        super(type, level);
        setNoGravity(true);
        noPhysics = true;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(HEIGHT, 0f);
        builder.define(ARMS, 0f);
        builder.define(TARGET, 0);
        builder.define(CAR, -1);
        builder.define(REACH_X, 0.75f);
        builder.define(REACH_Z, 1.3f);
    }

    public float height() {
        return entityData.get(HEIGHT);
    }

    public float arms() {
        return entityData.get(ARMS);
    }

    public float reachX() {
        return entityData.get(REACH_X);
    }

    public float reachZ() {
        return entityData.get(REACH_Z);
    }

    public int target() {
        return entityData.get(TARGET);
    }

    /** A local point (x across, y up, z along the bay) in the world. */
    public Vec3 toWorld(Vec3 local) {
        return position().add(local.yRot((float) -Math.toRadians(getYRot())));
    }

    public Vec3 toLocal(Vec3 world) {
        return world.subtract(position()).yRot((float) Math.toRadians(getYRot()));
    }

    // ---------------------------------------------------------------- working it

    /** What the interact key does at the hoist: the control box or the pump lever, sneaking or not. */
    public enum Use { CONTROL, CONTROL_DOWN, PUMP, VALVE }

    public record Press(int hoist, int use) implements CustomPacketPayload {
        public static final Type<Press> TYPE = new Type<>(ZomboidCraft.id("hoist_use"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Press> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Press::hoist, ByteBufCodecs.VAR_INT, Press::use, Press::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public static void register() {
        PayloadTypeRegistry.serverboundPlay().register(Press.TYPE, Press.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(Press.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            player.level().getServer().execute(() -> {
                if (player.level().getEntity(payload.hoist()) instanceof CarHoist hoist && !player.isSpectator()
                        && payload.use() >= 0 && payload.use() < Use.values().length
                        && hoist.toWorld(CONTROL).distanceToSqr(player.getEyePosition()) < 4 * 4) {
                    hoist.use(player, Use.values()[payload.use()]);
                }
            });
        });
    }

    private void say(ServerPlayer player, String what) {
        player.sendOverlayMessage(Component.literal(what));
    }

    public void use(ServerPlayer player, Use use) {
        ServerLevel level = (ServerLevel) level();
        CarProp car = car();
        int notch = target();
        switch (use) {
            case CONTROL -> {
                click(level);
                if (!armsIn) {
                    swingIn(player);
                } else if (notch < NOTCHES.length - 1) {
                    entityData.set(TARGET, notch + 1);
                    say(player, "Up to " + NOTCH_NAMES[notch + 1] + ". Sneak at the box to take it down.");
                } else {
                    say(player, "It's at full height.");
                }
            }
            case CONTROL_DOWN -> {
                click(level);
                if (notch > 0) {
                    entityData.set(TARGET, notch - 1);
                    say(player, "Down to " + NOTCH_NAMES[notch - 1] + ".");
                } else if (height() > 0.01f) {
                    entityData.set(TARGET, 0);
                } else if (armsIn) {
                    armsIn = false;
                    releaseCar();
                    say(player, "Arms swung out. Drive it off.");
                }
            }
            case PUMP -> {
                if (!armsIn || car == null) {
                    say(player, "Swing the arms under a car first (the control box).");
                    return;
                }
                float h = Math.min(NOTCHES[NOTCHES.length - 1], height() + STROKE);
                setHeight(h);
                int at = 0;
                for (int i = 0; i < NOTCHES.length; i++) {
                    if (h >= NOTCHES[i] - 0.01f) {
                        at = i;
                    }
                }
                entityData.set(TARGET, at);
                level.playSound(null, toWorld(LEVER).x, getY() + 0.8, toWorld(LEVER).z, SoundEvents.PISTON_EXTEND, SoundSource.BLOCKS, 0.35f, 1.6f);
                say(player, String.format("Pumping: %.2f m up.", h));
            }
            case VALVE -> {
                if (height() <= 0.01f) {
                    say(player, "It's already on the floor.");
                    return;
                }
                int below = 0;
                for (int i = 0; i < NOTCHES.length; i++) {
                    if (NOTCHES[i] < height() - 0.02f) {
                        below = i;
                    }
                }
                entityData.set(TARGET, below);
                say(player, "You crack the valve open: it sinks to " + NOTCH_NAMES[below] + ".");
            }
        }
    }

    private void click(ServerLevel level) {
        Vec3 at = toWorld(CONTROL);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.STONE_BUTTON_CLICK_ON, SoundSource.BLOCKS, 0.5f, 1.3f);
    }

    /** Swings the arms in under the car standing in the bay, if there's one that fits. */
    private void swingIn(ServerPlayer player) {
        CarProp found = null;
        for (CarProp c : level().getEntitiesOfClass(CarProp.class, getBoundingBox().inflate(4, 2, 4))) {
            Vec3 at = toLocal(c.position());
            float turn = Mth.wrapDegrees(c.getYRot() - getYRot());
            if (Math.abs(at.x) < 1.2 && Math.abs(at.z) < 2.5 && Math.abs(at.y) < 0.6 && (Math.abs(turn) < 25 || Math.abs(turn) > 155)) {
                found = c;
            }
        }
        if (found == null) {
            say(player, "There's no car in the bay. Drive one in between the posts.");
            return;
        }
        CarModels.Model m = CarModels.get(found.model());
        if (!fits(m)) {
            say(player, "Too big for a two-post lift.");
            return;
        }
        Vec3 at = toLocal(found.position());
        if (Math.abs(at.x) > 0.45 || Math.abs(at.z) > 0.8) {
            say(player, "It's not centred: the arms won't reach its lift points. Pull it in between the posts.");
            return;
        }
        if (found.isVehicle()) {
            say(player, "Someone's still inside it.");
            return;
        }
        // the car squared up between the posts, the arms reaching for its lift points behind the wheels
        found.setPos(toWorld(new Vec3(0, 0, at.z)).x, getY(), toWorld(new Vec3(0, 0, at.z)).z);
        hold(found, m);
        level().playSound(null, getX(), getY(), getZ(), SoundEvents.CHAIN_PLACE, SoundSource.BLOCKS, 0.8f, 0.8f);
        say(player, "Arms swung in under the " + found.model().replace('_', ' ') + ". The control box takes it up.");
    }

    /** Whether a model goes up on a two-post lift: a road vehicle no longer or wider than the arms reach. */
    public static boolean fits(CarModels.Model m) {
        return m != null && CarParts.applies(m) && m.size[2] <= MAX_LENGTH && m.size[0] <= MAX_WIDTH;
    }

    /** The arms reaching for the car's lift points behind the wheels. */
    private void hold(CarProp car, CarModels.Model m) {
        carId = car.getUUID();
        entityData.set(CAR, car.getId());
        entityData.set(REACH_X, Math.min(m.size[0] / 2 - 0.25f, 0.95f));
        entityData.set(REACH_Z, Math.max(0.9f, (m.wheelbase > 0.5f ? m.wheelbase : m.size[2] * 0.6f) / 2 - 0.35f));
        armsIn = true;
    }

    /**
     * Worldgen: the lift found already holding a car at a notch, as it was left (the car stands at the lift's
     * height, square between the posts). The hull is laid on the first tick.
     */
    public void raised(CarProp car, int notch) {
        hold(car, CarModels.get(car.model()));
        notch = Mth.clamp(notch, 0, NOTCHES.length - 1);
        entityData.set(ARMS, 1f);
        entityData.set(TARGET, notch);
        setHeight(NOTCHES[notch]);
        hullLaid = false;
    }

    private void releaseCar() {
        CarProp car = car();
        if (car != null) {
            car.setLift(0);
        }
        carId = null;
        entityData.set(CAR, -1);
    }

    /** The car on the arms, server side. */
    private CarProp car() {
        if (carId == null || !(level() instanceof ServerLevel server)) {
            return null;
        }
        Entity e = server.getEntity(carId);
        return e instanceof CarProp c && !c.isRemoved() ? c : null;
    }

    /** Client side: the car on the arms, if it's about. */
    public CarProp carOnArms() {
        int id = entityData.get(CAR);
        return id >= 0 && level().getEntity(id) instanceof CarProp c ? c : null;
    }

    private void setHeight(float h) {
        entityData.set(HEIGHT, h);
    }

    @Override
    public void tick() {
        super.tick();
        heightO = height();
        armsO = arms();
        if (!(level() instanceof ServerLevel server)) {
            return;
        }
        if (!postsLaid) {
            postsLaid = true;
            posts(server, true);
        }
        entityData.set(ARMS, Mth.approach(arms(), armsIn ? 1f : 0f, 0.04f));
        CarProp car = car();
        if (car != null && entityData.get(CAR) != car.getId()) {
            entityData.set(CAR, car.getId());     // loaded from disk, where the id was another
        }
        // the car went (removed, or towed off somehow); given a few seconds, as it may load after the lift
        lost = armsIn && car == null && carId != null ? lost + 1 : 0;
        if (lost > 100) {
            armsIn = false;
            releaseCar();
        }
        float h = height(), want = arms() < 0.999f ? 0 : NOTCHES[target()];
        boolean moving = Math.abs(want - h) > 0.001f;
        if (moving && car != null) {
            if (hullLaid) {
                Cars.clearHull(server, car);
                hullLaid = false;
            }
            // up on the motor; down on the valve, slower, as a real one sinks
            float step = want > h ? MOTOR : Math.max(VALVE, MOTOR * 0.6f);
            h = Mth.approach(h, want, step);
            setHeight(h);
            if (tickCount % 10 == 0) {
                server.playSound(null, getX(), getY() + 1, getZ(), want > h ? SoundEvents.PISTON_EXTEND : SoundEvents.PISTON_CONTRACT,
                        SoundSource.BLOCKS, 0.25f, 0.5f);
                com.zomboidcraft.zombie.Noise.emit(server, position(), 14);
            }
            if (Math.abs(h % 0.3f) < MOTOR) {
                // the safety locks clicking past each tooth of the ladder
                server.playSound(null, getX(), getY() + h + 1, getZ(), SoundEvents.TRIPWIRE_CLICK_ON, SoundSource.BLOCKS, 0.4f, 0.7f);
            }
        } else if (moving) {
            setHeight(Mth.approach(h, 0, MOTOR));
        }
        if (running && !moving && car != null) {
            server.playSound(null, getX(), getY() + h + 1, getZ(), SoundEvents.IRON_DOOR_CLOSE, SoundSource.BLOCKS, 0.5f, 0.6f);
        }
        running = moving;
        if (car != null && armsIn) {
            Vec3 at = toLocal(car.position());
            Vec3 there = toWorld(new Vec3(0, 0, at.z));
            car.setPos(there.x, getY() + height(), there.z);
            car.setDeltaMovement(Vec3.ZERO);
            car.setLift(height());
            if (!moving && !hullLaid) {
                Cars.layHull(server, car);
                hullLaid = true;
            }
        }
    }

    /** Lays (or takes away) the invisible solid blocks the posts stand in, so nothing walks through them. */
    private void posts(ServerLevel server, boolean lay) {
        for (int side = -1; side <= 1; side += 2) {
            Vec3 foot = toWorld(new Vec3(side * POST_X, 0.05, 0));
            for (int i = 0; i < 4; i++) {
                BlockPos pos = BlockPos.containing(foot.x, foot.y + i, foot.z);
                if (lay && server.getBlockState(pos).canBeReplaced()) {
                    server.setBlock(pos, Cars.hull(624), 2);
                } else if (!lay && server.getBlockState(pos).is(Cars.HULL)) {
                    server.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
                }
            }
        }
    }

    @Override
    public void remove(RemovalReason reason) {
        if (reason.shouldDestroy() && level() instanceof ServerLevel server) {
            posts(server, false);
            releaseCar();
        }
        super.remove(reason);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        setHeight(input.getFloatOr("height", 0f));
        entityData.set(TARGET, input.getIntOr("target", 0));
        armsIn = input.getIntOr("arms_in", 0) != 0;
        entityData.set(ARMS, armsIn ? 1f : 0f);
        entityData.set(REACH_X, input.getFloatOr("reach_x", 0.75f));
        entityData.set(REACH_Z, input.getFloatOr("reach_z", 1.3f));
        String id = input.getStringOr("car", "");
        carId = id.isEmpty() ? null : UUID.fromString(id);
        hullLaid = input.getIntOr("hull", 1) != 0;
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        output.putFloat("height", height());
        output.putInt("target", target());
        output.putInt("arms_in", armsIn ? 1 : 0);
        output.putFloat("reach_x", reachX());
        output.putFloat("reach_z", reachZ());
        output.putString("car", carId == null ? "" : carId.toString());
        output.putInt("hull", hullLaid ? 1 : 0);
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean canBeCollidedWith(Entity other) {
        return false;
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 64 * 64;
    }
}
