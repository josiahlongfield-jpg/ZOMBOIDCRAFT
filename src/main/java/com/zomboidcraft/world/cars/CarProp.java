package com.zomboidcraft.world.cars;

import com.zomboidcraft.ZomboidCraft;
import com.zomboidcraft.combat.Downed;
import com.zomboidcraft.zombie.Noise;
import java.util.List;
import java.util.function.Function;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.InterpolationHandler;
import net.minecraft.world.entity.LinearInterpolationHandler;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.DismountHelper;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * A car: an entity so the client can draw its 3D model. It can't be hurt and has no collision of
 * its own while parked; invisible {@link CarHull} blocks under the model make it solid. Using its
 * back end (or sneak-using anywhere) searches the boot or the back of a van, rolled from a loot
 * table the first time anyone looks, like Zomboid: police, SWAT and ambulance vehicles carry
 * better gear, and a burnt-out car's boot is empty. Using it anywhere else gets you in, the first
 * one in driving. A driven car takes its hull up and steers like a car (the driver's game works
 * out where it goes, as with boats), runs zombies down and is loud; it lays the hull again once
 * everyone's out. Removing it (/kill) clears its hull too.
 */
public class CarProp extends Entity {
    private static final EntityDataAccessor<String> MODEL = SynchedEntityData.defineId(CarProp.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> VARIANT = SynchedEntityData.defineId(CarProp.class, EntityDataSerializers.STRING);
    /** Someone has the boot open; clients swing the lid or back doors up while it is. */
    private static final EntityDataAccessor<Boolean> OPEN = SynchedEntityData.defineId(CarProp.class, EntityDataSerializers.BOOLEAN);
    /** The engine is running: it drives, and clients play its loop. */
    private static final EntityDataAccessor<Boolean> ENGINE = SynchedEntityData.defineId(CarProp.class, EntityDataSerializers.BOOLEAN);

    private final SimpleContainer boot = new SimpleContainer(27) {
        @Override
        public boolean stillValid(Player player) {
            return isAlive() && player.distanceToSqr(CarProp.this) < 64;
        }
    };
    private boolean searched;
    /** How far the boot is open, 0 to 1, last tick and this one (client side). */
    public float lidOpen, lidOpenO;
    /** How far the wheels have rolled (metres) and the front wheels' steer (radians, left +), for drawing. */
    public double rolled, rolledO;
    public float steer, steerO;
    /** The hull goes back down the tick after the last person gets out. */
    private boolean parkPending;
    /** Where the car was last tick, server side: a driven car's speed comes from how far it went. */
    private Vec3 lastPos;
    /** When the driver got in, so the speedo waits until they've read how to drive. */
    private int mountedAt;
    /** Server side: who was driving last tick, when the engine catches after the starter, when a door shuts, when the horn is free. */
    private Entity lastDriver;
    private int catchAt = -1, doorShutAt = -1, hornFreeAt;
    private double lastSpeed, speedBefore;

    /** Called on the client every tick a car's engine runs, to keep its sound going; the client sets this. */
    public static java.util.function.Consumer<CarProp> engineSound = car -> { };

    /** The local player's driving keys; the client sets this, a server never needs it. */
    public static Function<Player, Input> clientInput = p -> Input.EMPTY;

    // Handling, in blocks and ticks: 1 block a tick is 72 km/h.
    private static final double ACCEL = 0.014, BRAKE = 0.045, HANDBRAKE = 0.025, REVERSE = 0.25;
    private static final double GRAVITY = 0.08, STEP = 0.6;

    public CarProp(EntityType<? extends CarProp> type, Level level) {
        super(type, level);
        setNoGravity(true);
    }

    public void setLook(String model, String variant) {
        entityData.set(MODEL, model);
        entityData.set(VARIANT, variant);
        setBoundingBox(makeBoundingBox(position()));
    }

    public String model() {
        return entityData.get(MODEL);
    }

    public String variant() {
        return entityData.get(VARIANT);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(MODEL, "sedan");
        builder.define(VARIANT, "usable");
        builder.define(OPEN, false);
        builder.define(ENGINE, false);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        setLook(input.getStringOr("model", "sedan"), input.getStringOr("variant", "usable"));
        searched = input.getIntOr("searched", 0) != 0;
        NonNullList<ItemStack> items = NonNullList.withSize(boot.getContainerSize(), ItemStack.EMPTY);
        ContainerHelper.loadAllItems(input, items);
        for (int i = 0; i < items.size(); i++) {
            boot.setItem(i, items.get(i));
        }
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        output.putString("model", model());
        output.putString("variant", variant());
        output.putInt("searched", searched ? 1 : 0);
        NonNullList<ItemStack> items = NonNullList.withSize(boot.getContainerSize(), ItemStack.EMPTY);
        for (int i = 0; i < items.size(); i++) {
            items.set(i, boot.getItem(i));
        }
        ContainerHelper.saveAllItems(output, items);
    }

    /** The loot table for this car's boot, from what kind of vehicle it is. */
    public String lootTable() {
        return switch (model()) {
            case "swat_van" -> "swat";
            case "ambulance", "ambulance_van" -> "ambulance";
            case "police_sedan", "police_suv" -> "police";
            case "fire_truck" -> "fire";
            case "city_bus", "school_bus" -> "bus";
            case "van", "step_van", "moving_truck", "box_truck", "tow_truck", "pickup", "garbage_truck", "garbage_truck_rear" -> "work";
            default -> "trunk";
        };
    }

    /** Right-click does nothing to a car, so it can aim a gun; the interact key ({@link CarNet}) uses it. */
    @Override
    public InteractionResult interact(Player player, InteractionHand hand, Vec3 location) {
        return InteractionResult.PASS;
    }

    /** The interact key on this car, at location relative to it: the back end searches the boot, elsewhere gets in. */
    public void use(Player player, Vec3 location) {
        if (!(level() instanceof ServerLevel server)) {
            return;
        }
        if (!player.isSecondaryUseActive() && !atBoot(location)) {
            getIn(player);
            return;
        }
        if (!searched) {
            searched = true;
            if (!"burnt".equals(variant()) && com.zomboidcraft.ZcConfig.get().world.carLoot) {
                ResourceKey<LootTable> key = ResourceKey.create(Registries.LOOT_TABLE, ZomboidCraft.id("car/" + lootTable()));
                LootTable table = server.getServer().reloadableRegistries().getLootTable(key);
                LootParams params = new LootParams.Builder(server).withParameter(LootContextParams.ORIGIN, position())
                        .withLuck(player.getLuck()).withParameter(LootContextParams.THIS_ENTITY, player)
                        .create(LootContextParamSets.CHEST);
                table.fill(boot, params, getUUID().getLeastSignificantBits());
            }
        }
        String title = switch (lootTable()) {
            case "swat", "ambulance", "work", "fire" -> "Back of the " + (model().contains("truck") ? "truck" : "van");
            case "bus" -> "Luggage rack";
            default -> "Boot";
        };
        player.openMenu(new SimpleMenuProvider((id, inventory, p) -> ChestMenu.threeRows(id, inventory, boot), Component.literal(title)));
        if (!entityData.get(OPEN)) {
            entityData.set(OPEN, true);
            server.playSound(null, getX(), getY(), getZ(), SoundEvents.IRON_TRAPDOOR_OPEN, SoundSource.BLOCKS, 0.6f, 0.7f);
        }
    }

    /** Whether a point (relative to the car, or in the world) is at its back end, where the boot is. */
    public boolean atBoot(Vec3 location) {
        CarModels.Model m = CarModels.get(model());
        if (m == null) {
            return true;
        }
        if (location.horizontalDistanceSqr() > m.size[2] * m.size[2]) {
            location = location.subtract(position());
        }
        return local(location).z < -m.size[2] / 2 + 1.3;
    }

    /** A world-space offset from the car turned into the model's space (nose toward +z, left +x). */
    private Vec3 local(Vec3 offset) {
        return offset.yRot((float) Math.toRadians(getYRot()));
    }

    /** A model-space offset turned into the world, still relative to the car. */
    private Vec3 world(double lx, double ly, double lz) {
        return new Vec3(lx, ly, lz).yRot((float) -Math.toRadians(getYRot()));
    }

    private void getIn(Player player) {
        if ("burnt".equals(variant())) {
            player.sendOverlayMessage(Component.literal("It's burnt out."));
            return;
        }
        if (player.isPassenger() || !canAddPassenger(player)) {
            if (!hasPassenger(player)) {
                player.sendOverlayMessage(Component.literal("There's no room."));
            }
            return;
        }
        player.startRiding(this);
    }

    public boolean engineOn() {
        return entityData.get(ENGINE);
    }

    /**
     * The server's engine: a new driver turns the key (it catches about three seconds into the
     * starter, or a wreck just cranks), and it stops when nobody's at the wheel.
     */
    private void engine(ServerLevel server, Entity driver) {
        if (driver != lastDriver) {
            lastDriver = driver;
            if (driver == null) {
                entityData.set(ENGINE, false);
                catchAt = -1;
            } else if (!engineOn() && driver instanceof Player player) {
                boolean runs = engineRuns();
                server.playSound(null, getX(), getY(), getZ(), runs ? CarSounds.START : CarSounds.STALL, SoundSource.NEUTRAL, 1f, 1f);
                catchAt = runs ? tickCount + 58 : -1;
                player.sendOverlayMessage(Component.literal(runs
                        ? "W/S to drive, A/D to steer, space for the handbrake, H for the horn, sneak or interact to get out."
                        : "It won't start."));
            }
        }
        if (catchAt >= 0 && tickCount >= catchAt) {
            catchAt = -1;
            entityData.set(ENGINE, true);
            Noise.emit(server, position(), 24);
        }
        if (doorShutAt >= 0 && tickCount >= doorShutAt) {
            doorShutAt = -1;
            server.playSound(null, getX(), getY(), getZ(), CarSounds.DOOR_CLOSE, SoundSource.NEUTRAL, 1f, 0.95f + random.nextFloat() * 0.1f);
        }
    }

    /** A door opens to let someone in or out, and swings shut behind them. */
    private void door(ServerLevel server) {
        server.playSound(null, getX(), getY(), getZ(), CarSounds.DOOR_OPEN, SoundSource.NEUTRAL, 1f, 0.95f + random.nextFloat() * 0.1f);
        doorShutAt = tickCount + 14;
    }

    /** The driver leans on the horn: loud, and every zombie in earshot comes. */
    public void honk(Player driver) {
        if (level() instanceof ServerLevel server && getControllingPassenger() == driver && tickCount >= hornFreeAt) {
            hornFreeAt = tickCount + 40;
            server.playSound(null, getX(), getY(), getZ(), CarSounds.HORN, SoundSource.NEUTRAL, 1.5f, 1f);
            Noise.emit(server, position(), 48);
        }
    }

    /** Wrecks and burnt-out shells don't run, whatever you do. */
    public boolean engineRuns() {
        return "usable".equals(variant()) || "abandoned".equals(variant());
    }

    private double topSpeed() {
        return "usable".equals(variant()) ? 1.1 : 0.8;
    }

    private int seats() {
        CarModels.Model m = CarModels.get(model());
        return m == null || m.seats == null ? 0 : m.seats.length;
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return passenger instanceof Player && getPassengers().size() < seats() && !"burnt".equals(variant());
    }

    @Override
    public LivingEntity getControllingPassenger() {
        return getFirstPassenger() instanceof Player player ? player : null;
    }

    /** Each rider sits at their own seat: the driver at the wheel, then front passenger, then the back. */
    @Override
    protected Vec3 getPassengerAttachmentPoint(Entity passenger, EntityDimensions dimensions, float scale) {
        CarModels.Model m = CarModels.get(model());
        int i = getPassengers().indexOf(passenger);
        if (m == null || m.seats == null || m.seats.length == 0 || i < 0) {
            return super.getPassengerAttachmentPoint(passenger, dimensions, scale);
        }
        float[] seat = m.seats[Math.min(i, m.seats.length - 1)];
        // a player's eyes are 1.02 above where they hang from a vehicle
        return world(seat[0], seat[1] - 1.02, seat[2]);
    }

    /** Riders turn with the car, and can look round but not right behind them. */
    @Override
    protected void positionRider(Entity passenger, MoveFunction move) {
        super.positionRider(passenger, move);
        if (passenger instanceof Player player && player.isLocalPlayer()) {
            float turn = getYRot() - yRotO;
            player.setYRot(player.getYRot() + turn);
            player.setYHeadRot(player.getYHeadRot() + turn);
            clampLook(player);
        }
    }

    @Override
    public void onPassengerTurned(Entity passenger) {
        clampLook(passenger);
    }

    private void clampLook(Entity passenger) {
        float rel = Mth.wrapDegrees(passenger.getYRot() - getYRot());
        float clamped = Mth.clamp(rel, -130f, 130f);
        passenger.yRotO += clamped - rel;
        passenger.setYRot(passenger.getYRot() + clamped - rel);
        passenger.setYHeadRot(passenger.getYRot());
    }

    @Override
    protected void addPassenger(Entity passenger) {
        boolean first = getPassengers().isEmpty();
        super.addPassenger(passenger);
        if (first && level() instanceof ServerLevel server) {
            Cars.clearHull(server, this);
            parkPending = false;
            mountedAt = tickCount;
        }
        if (level() instanceof ServerLevel server) {
            door(server);
        }
        if (passenger instanceof Player player && player.isLocalPlayer()) {
            player.setYRot(getYRot());
            player.setYHeadRot(getYRot());
        }
        setBoundingBox(makeBoundingBox(position()));
    }

    @Override
    protected void removePassenger(Entity passenger) {
        super.removePassenger(passenger);
        if (level() instanceof ServerLevel server && !isRemoved()) {
            door(server);
        }
        if (getPassengers().isEmpty()) {
            setDeltaMovement(Vec3.ZERO);
            parkPending = level() instanceof ServerLevel;
        }
        setBoundingBox(makeBoundingBox(position()));
    }

    /** Out of the door on your side if there's room, else the other side, the back, the front, the roof. */
    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
        CarModels.Model m = CarModels.get(model());
        if (m == null) {
            return super.getDismountLocationForPassenger(passenger);
        }
        Vec3 at = local(passenger.position().subtract(position()));
        double side = at.x >= 0 ? 1 : -1, out = m.size[0] / 2 + 0.5, z = Mth.clamp(at.z, -m.size[2] / 2 + 0.5, m.size[2] / 2 - 0.5);
        double[][] spots = {{side * out, z}, {-side * out, z}, {0, -m.size[2] / 2 - 0.7}, {0, m.size[2] / 2 + 0.7}};
        for (double[] spot : spots) {
            Vec3 base = position().add(world(spot[0], 0, spot[1]));
            for (double dy : new double[] {0, 1, -1}) {
                Vec3 to = base.add(0, dy, 0);
                if (DismountHelper.canDismountTo(level(), to, passenger, Pose.STANDING)) {
                    return to;
                }
            }
        }
        return position().add(0, m.size[1], 0);
    }

    @Override
    protected InterpolationHandler createInterpolationHandler() {
        return LinearInterpolationHandler.create(this, 3);
    }

    @Override
    public float maxUpStep() {
        return (float) STEP;
    }

    /**
     * While parked the click box covers the whole car, turned with it, so you can search it from
     * any side. While driven it's a square the car's width, so it fits down any gap the car does;
     * the nose and tail are checked separately ({@link #bodyHits}).
     */
    @Override
    protected AABB makeBoundingBox(Vec3 pos) {
        CarModels.Model m = CarModels.get(model());
        if (m == null) {
            return super.makeBoundingBox(pos);
        }
        if (isVehicle()) {
            double r = m.size[0] / 2;
            return new AABB(pos.x - r, pos.y, pos.z - r, pos.x + r, pos.y + m.size[1], pos.z + r);
        }
        double t = Math.toRadians(getYRot()), c = Math.abs(Math.cos(t)), s = Math.abs(Math.sin(t));
        double hx = (c * m.size[0] + s * m.size[2]) / 2, hz = (s * m.size[0] + c * m.size[2]) / 2;
        return new AABB(pos.x - hx, pos.y, pos.z - hz, pos.x + hx, pos.y + m.size[1], pos.z + hz);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> accessor) {
        super.onSyncedDataUpdated(accessor);
        if (MODEL.equals(accessor)) {
            setBoundingBox(makeBoundingBox(position()));
        }
    }

    @Override
    public void tick() {
        super.tick();
        lidOpenO = lidOpen;
        rolledO = rolled;
        steerO = steer;
        if (isLocalInstanceAuthoritative() && getControllingPassenger() instanceof Player driver) {
            getInterpolation().cancel();
            drive(level().isClientSide() ? clientInput.apply(driver) : Input.EMPTY);
        } else if (level().isClientSide()) {
            getInterpolation().interpolate();
        }
        if (level() instanceof ServerLevel server) {
            if (entityData.get(OPEN) && server.getGameTime() % 10 == 0 && !beingSearched(server)) {
                entityData.set(OPEN, false);
                server.playSound(null, getX(), getY(), getZ(), SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.BLOCKS, 0.6f, 0.7f);
            }
            if (parkPending && !isVehicle()) {
                parkPending = false;
                Cars.layHull(server, this);
            }
            Vec3 moved = lastPos == null ? Vec3.ZERO : position().subtract(lastPos);
            lastPos = position();
            engine(server, getControllingPassenger());
            if (getControllingPassenger() instanceof Player driver) {
                driven(server, driver, moved);
            }
        } else {
            lidOpen = Math.clamp(lidOpen + (entityData.get(OPEN) ? 0.08f : -0.1f), 0f, 1f);
            wheels();
            if (engineOn()) {
                engineSound.accept(this);
            }
        }
    }

    /** One tick of driving, on the driver's side: throttle, brakes and steering, then the move. */
    private void drive(Input in) {
        CarModels.Model m = CarModels.get(model());
        if (m == null) {
            return;
        }
        double rad = Math.toRadians(getYRot());
        Vec3 v = getDeltaMovement();
        double fx = -Math.sin(rad), fz = Math.cos(rad);
        double speed = v.x * fx + v.z * fz, lx = v.x - fx * speed, lz = v.z - fz * speed;
        double top = topSpeed();
        boolean runs = engineOn(), ground = onGround();
        if (ground) {
            if (in.forward() && !in.backward() && runs) {
                speed = speed < -0.02 ? approach(speed, 0, BRAKE) : speed + ACCEL * Math.max(0, 1 - (speed / top) * (speed / top));
            } else if (in.backward() && !in.forward() && runs) {
                speed = speed > 0.02 ? approach(speed, 0, BRAKE) : speed - ACCEL * 0.6 * Math.max(0, 1 - (speed / REVERSE) * (speed / REVERSE));
            } else {
                speed = approach(speed * 0.99, 0, 0.002);
            }
            if (in.jump()) {
                speed = approach(speed, 0, HANDBRAKE);
            }
        } else {
            speed *= 0.995;
        }

        // the wheel turns less the faster you go, and the car turns on its wheelbase like a real one
        float want = (in.left() ? 1 : 0) - (in.right() ? 1 : 0);
        float limit = (float) Math.toRadians(35 - 27 * Math.min(1, Math.abs(speed) / top));
        float target = want * limit;
        steer += Mth.clamp(target - steer, -0.12f, 0.12f);
        float yaw = getYRot();
        if (ground) {
            double wheelbase = m.wheelbase > 0.5f ? m.wheelbase : m.size[2] * 0.6;
            float turned = yaw - (float) Math.toDegrees(speed / wheelbase * Math.tan(steer));
            if (!bodyHits(position(), turned) || bodyHits(position(), yaw)) {
                yaw = turned;
            } else {
                speed *= 0.6;
            }
        }
        setYRot(yaw);

        // tyres keep most of the car going the way it points; less in the rain, little on the handbrake
        double grip = !ground ? 0 : in.jump() ? 0.15 : level().isRainingAt(blockPosition()) ? 0.35 : 0.6;
        rad = Math.toRadians(yaw);
        fx = -Math.sin(rad);
        fz = Math.cos(rad);
        double vx = fx * speed + lx * (1 - grip), vz = fz * speed + lz * (1 - grip);
        double vy = ground ? -0.04 : Math.max(-2, (v.y - GRAVITY) * 0.98);

        // running zombies down slows you
        double slowed = Math.pow(0.9, struck(m).size());
        vx *= slowed;
        vz *= slowed;

        Vec3 next = position().add(vx, 0, vz);
        if (bodyHits(next, yaw) && !bodyHits(position(), yaw)) {
            vx *= -0.1;
            vz *= -0.1;
        }
        Vec3 before = position();
        move(MoverType.SELF, new Vec3(vx, vy, vz));
        if (horizontalCollision) {
            Vec3 went = position().subtract(before);
            vx = went.x;
            vz = went.z;
        }
        setDeltaMovement(vx, onGround() ? 0 : vy, vz);
    }

    private static double approach(double value, double target, double step) {
        return value > target ? Math.max(target, value - step) : Math.min(target, value + step);
    }

    /**
     * Whether the car's body, standing at {@code at} turned to yaw, would run into anything above
     * kerb height. The body is a row of squares down its length, since the moving box is only one.
     */
    private boolean bodyHits(Vec3 at, float yaw) {
        CarModels.Model m = CarModels.get(model());
        if (m == null) {
            return false;
        }
        double side = m.size[0] * 0.8, length = m.size[2] * 0.95, r = side / 2;
        int n = Math.max(1, (int) Math.ceil(length / side));
        double rad = Math.toRadians(yaw), fx = -Math.sin(rad), fz = Math.cos(rad);
        double bottom = at.y + STEP + 0.05, top = Math.max(bottom + 0.3, at.y + m.size[1] - 0.05);
        for (int i = 0; i < n; i++) {
            double t = n == 1 ? 0 : -(length - side) / 2 + (length - side) * i / (n - 1);
            double cx = at.x + fx * t, cz = at.z + fz * t;
            if (!level().noCollision(this, new AABB(cx - r, bottom, cz - r, cx + r, top, cz + r))) {
                return true;
            }
        }
        return false;
    }

    /** Living things (riders aside) the car's body is touching. */
    private List<LivingEntity> struck(CarModels.Model m) {
        double hw = m.size[0] / 2 + 0.3, hl = m.size[2] / 2 + 0.3;
        return level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(m.size[2] / 2), e -> {
            if (e.isPassenger() || !e.isAlive() || e.isSpectator()) {
                return false;
            }
            Vec3 at = local(e.position().subtract(position()));
            return Math.abs(at.x) < hw + e.getBbWidth() / 2 && Math.abs(at.z) < hl + e.getBbWidth() / 2 && at.y < m.size[1];
        });
    }

    /** The server's side of driving: hits on whatever the car ploughs into, the engine's noise, the speedo. */
    private void driven(ServerLevel server, Player driver, Vec3 moved) {
        CarModels.Model m = CarModels.get(model());
        if (m == null) {
            return;
        }
        double speed = moved.horizontalDistance();
        // a sudden stop from speed is a crash: walls, other cars. Two slow ticks in a row, since the
        // driver's moves arrive unevenly and one tick can see none
        if (speedBefore > 0.35 && lastSpeed < speedBefore * 0.45 && speed < speedBefore * 0.45) {
            server.playSound(null, getX(), getY(), getZ(), CarSounds.CRASH, SoundSource.NEUTRAL,
                    (float) Math.min(1.5, speedBefore * 1.4), 0.9f + random.nextFloat() * 0.2f);
            Noise.emit(server, position(), 30);
            speedBefore = 0;
        } else {
            speedBefore = lastSpeed;
        }
        lastSpeed = speed;
        if (speed > 0.12) {
            for (LivingEntity hit : struck(m)) {
                float damage = (float) (speed * speed * 25 + speed * 5);
                DamageSource source = damageSources().mobAttack(driver);
                if (hit.hurtServer(server, source, damage)) {
                    Vec3 away = hit.position().subtract(position());
                    hit.knockback(Math.min(2.5, speed * 2.5), -away.x, -away.z, source, damage);
                    if (speed > 0.35) {
                        Downed.knockDown(hit);
                        server.playSound(null, hit.getX(), hit.getY(), hit.getZ(), CarSounds.CRASH, SoundSource.NEUTRAL, 0.6f,
                                1.1f + random.nextFloat() * 0.2f);
                    }
                }
            }
        }
        if (engineOn() && tickCount % 10 == 0) {
            Noise.emit(server, position(), 18 + 30 * speed);
        }
        if (engineOn() && tickCount % 5 == 0 && tickCount - mountedAt > 100) {
            driver.sendOverlayMessage(Component.literal(Math.round(speed * 72) + " km/h"));
        }
    }

    /** Rolls the wheels on by how far the car went, and on other players' cars guesses the steer from the turn. */
    private void wheels() {
        double rad = Math.toRadians(getYRot());
        double along = (getX() - xo) * -Math.sin(rad) + (getZ() - zo) * Math.cos(rad);
        rolled += along;
        if (!isLocalInstanceAuthoritative()) {
            CarModels.Model m = CarModels.get(model());
            float want = 0;
            if (m != null && Math.abs(along) > 0.02) {
                double wheelbase = m.wheelbase > 0.5f ? m.wheelbase : m.size[2] * 0.6;
                want = (float) Math.atan(-Math.toRadians(Mth.wrapDegrees(getYRot() - yRotO)) * wheelbase / along);
            }
            steer += Mth.clamp(Mth.clamp(want, -0.6f, 0.6f) - steer, -0.1f, 0.1f);
        }
    }

    private boolean beingSearched(ServerLevel server) {
        for (Player player : server.players()) {
            if (player.containerMenu instanceof ChestMenu menu && menu.getContainer() == boot) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean isPickable() {
        return !isRemoved();
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
        return distance < 96 * 96;  // a town parks a hundred or more; further off they're lost in the haze anyway
    }

    @Override
    public void remove(RemovalReason reason) {
        if (reason.shouldDestroy() && level() instanceof ServerLevel server) {
            ZomboidCraft.LOGGER.info("Car {} removed ({})", model(), reason);
            Cars.clearHull(server, this);
        }
        super.remove(reason);
    }
}
