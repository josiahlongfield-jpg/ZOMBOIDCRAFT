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
import net.minecraft.world.entity.Mob;
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
import org.joml.Quaternionf;

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
    /** The windows are smashed in: clients leave the glass out, and zombies can reach whoever's inside. */
    private static final EntityDataAccessor<Boolean> SMASHED = SynchedEntityData.defineId(CarProp.class, EntityDataSerializers.BOOLEAN);
    /** Each window's state and the holes and cracks in it ({@link CarGlass.Panes#encode}); empty until first set. */
    private static final EntityDataAccessor<String> GLASS = SynchedEntityData.defineId(CarProp.class, EntityDataSerializers.STRING);
    /** Which seat each rider has: four bits per rider in passenger order, the seat's index plus one. */
    private static final EntityDataAccessor<Integer> SEATS = SynchedEntityData.defineId(CarProp.class, EntityDataSerializers.INT);
    /** The alarm's going off (a storm set it off): the horn honks and the lamps flash. */
    private static final EntityDataAccessor<Boolean> ALARM = SynchedEntityData.defineId(CarProp.class, EntityDataSerializers.BOOLEAN);
    /** An emergency vehicle's siren is wailing (double-tap the horn). */
    private static final EntityDataAccessor<Boolean> SIREN = SynchedEntityData.defineId(CarProp.class, EntityDataSerializers.BOOLEAN);
    /** An aircraft's attitude, engine (or rotor) speed, thrust, and the top speed the server allows ({@link Flight}). */
    private static final EntityDataAccessor<org.joml.Quaternionfc> AIR_ATTITUDE = SynchedEntityData.defineId(CarProp.class, EntityDataSerializers.QUATERNION);
    private static final EntityDataAccessor<Float> AIR_RPM = SynchedEntityData.defineId(CarProp.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> AIR_POWER = SynchedEntityData.defineId(CarProp.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> AIR_TOP = SynchedEntityData.defineId(CarProp.class, EntityDataSerializers.FLOAT);
    /** A crashed aircraft whose wings, nose and tail have been torn off ({@link AirBreakup}). */
    private static final EntityDataAccessor<Boolean> BROKEN = SynchedEntityData.defineId(CarProp.class, EntityDataSerializers.BOOLEAN);
    /** Fuel, 0 to 1 of the tanks ({@link #fuelCapacity}); the aircraft's battery and condition (percent). See {@link AirReadiness}. */
    private static final EntityDataAccessor<Float> FUEL = SynchedEntityData.defineId(CarProp.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> BATTERY = SynchedEntityData.defineId(CarProp.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> CONDITION = SynchedEntityData.defineId(CarProp.class, EntityDataSerializers.INT);
    /** The driver has the headlights switched on. */
    private static final EntityDataAccessor<Boolean> LIGHTS = SynchedEntityData.defineId(CarProp.class, EntityDataSerializers.BOOLEAN);
    /** A mounted gun's barrels spun up (0 to 1), whether it's firing, and the rounds on its belt (see Turret). */
    private static final EntityDataAccessor<Float> TURRET_SPIN = SynchedEntityData.defineId(CarProp.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> TURRET_FIRING = SynchedEntityData.defineId(CarProp.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> BELT = SynchedEntityData.defineId(CarProp.class, EntityDataSerializers.INT);
    /** Every part's condition, a character a slot ({@link CarParts}); empty until the server first rolls them. */
    private static final EntityDataAccessor<String> PARTS = SynchedEntityData.defineId(CarProp.class, EntityDataSerializers.STRING);
    /** How high a hoist's arms have it, metres (0 on its wheels): up at all, it can't be driven off. */
    private static final EntityDataAccessor<Float> LIFT = SynchedEntityData.defineId(CarProp.class, EntityDataSerializers.FLOAT);
    /** A trolley jack under one corner ({@link ShopTool}): which (0-3, -1 none) and how high it's lifted. */
    private static final EntityDataAccessor<Integer> JACK = SynchedEntityData.defineId(CarProp.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> JACK_LIFT = SynchedEntityData.defineId(CarProp.class, EntityDataSerializers.FLOAT);
    /** Server: the gunner holds the trigger; rounds due by the rate; ticks till the belt's loaded; rounds fired. */
    boolean turretTrigger;
    int beltBusy, turretShots, fireTicks;
    /** The gunner is zoomed in (right-click held): the gun's tighter aimed spread. */
    boolean turretZoom;
    /** How far the gun's barrels have turned (radians), last tick and this one (client side). */
    public float barrels, barrelsO;

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
    /** On each computer, for the lamps: ticks left on the brake lights and the reversing lights. */
    public int braking, reversing;
    private double alongO;
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
    /** How much more bashing the windows take before they give, in zombie damage. -1 until first set (old saves). */
    private float windows = -1;
    /** Server side: how much more bashing each window takes, in zombie damage; null until first set. */
    private float[] glassLeft;
    /** The windows as last read from GLASS, and the text they were read from. */
    private CarGlass.Panes panes;
    private String panesFrom;
    /** On each computer, for drawing: how far each window is wound down, 0 to 1, last tick and this one. */
    public float[] woundO = new float[0], wound = new float[0];
    /** The seat the next rider to get in takes (server), or -1 for the nearest free one. */
    private int nextSeat = -1;
    /** Where the key is ({@link CarKeys}), whether someone's hotwired it, and ticks left on a hotwiring and who's at it. */
    private int keyWhere;
    private boolean customer;
    private boolean hotwired;
    /** Whether a car's condition has been decided yet ({@link #carCondition}). */
    private boolean worn;
    /** Whether a road vehicle's tank has been rolled (Zomboid's Chance Has Gas and Initial Gas), the first time it ticks. */
    private boolean fuelRolled;
    /** Zomboid's Locked Vehicle Frequency and Car Alarm, rolled once: whether the doors are locked, and whether it has an alarm armed. */
    private boolean lockRolled, locked, armed;
    /** Ticks the siren has been on (Siren Shutoff Hours), and when the horn was last pressed (a double tap is the siren). */
    private int sirenTicks, hornPressedAt = -100;
    /** Server side: wear owed each part slot below a whole percent, so slow wear adds up. */
    private final double[] partWear = new double[CarParts.SLOTS.size()];
    private double lastSpeedForWear;
    private int hotwireLeft = -1;
    private Entity hotwirer;
    /** The glovebox, filled the first time anyone opens it. */
    private final SimpleContainer glovebox = new SimpleContainer(5) {
        @Override
        public boolean stillValid(Player player) {
            return isAlive() && player.getVehicle() == CarProp.this;
        }
    };
    private boolean gloveSearched;

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
        flight = null;
        panes = null;
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
        builder.define(SMASHED, false);
        builder.define(GLASS, "");
        builder.define(SEATS, 0);
        builder.define(ALARM, false);
        builder.define(SIREN, false);
        builder.define(AIR_ATTITUDE, new org.joml.Quaternionf());
        builder.define(AIR_RPM, 0f);
        builder.define(AIR_POWER, 0f);
        builder.define(AIR_TOP, 0f);
        builder.define(BROKEN, false);
        builder.define(FUEL, 1f);
        builder.define(BATTERY, true);
        builder.define(CONDITION, 100);
        builder.define(LIGHTS, false);
        builder.define(TURRET_SPIN, 0f);
        builder.define(TURRET_FIRING, false);
        builder.define(BELT, 0);
        builder.define(PARTS, "");
        builder.define(LIFT, 0f);
        builder.define(JACK, -1);
        builder.define(JACK_LIFT, 0f);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        setLook(input.getStringOr("model", "sedan"), input.getStringOr("variant", "usable"));
        searched = input.getIntOr("searched", 0) != 0;
        entityData.set(BROKEN, input.getIntOr("broken", 0) != 0);
        airFound = input.getIntOr("air_found", 0) != 0;
        entityData.set(FUEL, input.getFloatOr("fuel", 1f));
        fuelRolled = input.getIntOr("fuel_rolled", 0) != 0;
        lockRolled = input.getIntOr("lock_rolled", 0) != 0;
        locked = input.getIntOr("locked", 0) != 0;
        armed = input.getIntOr("armed", 0) != 0;
        entityData.set(SIREN, input.getIntOr("siren", 0) != 0);
        sirenTicks = input.getIntOr("siren_ticks", 0);
        entityData.set(BATTERY, input.getIntOr("battery", 1) != 0);
        entityData.set(CONDITION, input.getIntOr("condition", 100));
        windows = input.getFloatOr("windows", windowsWhole());
        entityData.set(GLASS, input.getStringOr("glass", ""));
        String left = input.getStringOr("glass_left", "");
        glassLeft = null;
        if (!left.isEmpty()) {
            String[] parts = left.split(",");
            glassLeft = new float[parts.length];
            for (int i = 0; i < parts.length; i++) {
                try {
                    glassLeft[i] = Float.parseFloat(parts[i]);
                } catch (NumberFormatException e) {
                    glassLeft[i] = 1;
                }
            }
        }
        if (entityData.get(GLASS).isEmpty() && windows <= 0 && windowsWhole() > 0) {
            // an old save's car with its windows smashed in: every one of them gone
            CarGlass.Panes p = panes();
            java.util.Arrays.fill(p.state, CarGlass.SHATTERED);
            setPanes(p);
        }
        entityData.set(SMASHED, panes().size() > 0 ? panes().allShattered() : windows <= 0);
        NonNullList<ItemStack> items = NonNullList.withSize(boot.getContainerSize(), ItemStack.EMPTY);
        ContainerHelper.loadAllItems(input, items);
        for (int i = 0; i < items.size(); i++) {
            boot.setItem(i, items.get(i));
        }
        keyWhere = input.getIntOr("key", CarKeys.UNROLLED);
        customer = input.getIntOr("customer", 0) != 0;
        hotwired = input.getIntOr("hotwired", 0) != 0;
        worn = input.getIntOr("worn", 0) != 0;
        entityData.set(LIGHTS, input.getIntOr("lights", 0) != 0);
        entityData.set(BELT, input.getIntOr("belt", 0));
        entityData.set(PARTS, input.getStringOr("parts", ""));
        gloveSearched = input.getIntOr("glove_searched", 0) != 0;
        NonNullList<ItemStack> glove = NonNullList.withSize(glovebox.getContainerSize(), ItemStack.EMPTY);
        ContainerHelper.loadAllItems(input.childOrEmpty("glovebox"), glove);
        for (int i = 0; i < glove.size(); i++) {
            glovebox.setItem(i, glove.get(i));
        }
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        output.putString("model", model());
        output.putString("variant", variant());
        output.putInt("searched", searched ? 1 : 0);
        output.putInt("broken", broken() ? 1 : 0);
        output.putInt("air_found", airFound ? 1 : 0);
        output.putFloat("fuel", fuel());
        output.putInt("fuel_rolled", fuelRolled ? 1 : 0);
        output.putInt("lock_rolled", lockRolled ? 1 : 0);
        output.putInt("locked", locked ? 1 : 0);
        output.putInt("armed", armed ? 1 : 0);
        output.putInt("siren", siren() ? 1 : 0);
        output.putInt("siren_ticks", sirenTicks);
        output.putInt("battery", battery() ? 1 : 0);
        output.putInt("condition", condition());
        output.putFloat("windows", windows);
        output.putInt("belt", belt());
        output.putString("parts", parts());
        output.putString("glass", entityData.get(GLASS));
        if (glassLeft != null) {
            StringBuilder left = new StringBuilder();
            for (int i = 0; i < glassLeft.length; i++) {
                left.append(i > 0 ? "," : "").append(glassLeft[i]);
            }
            output.putString("glass_left", left.toString());
        }
        NonNullList<ItemStack> items = NonNullList.withSize(boot.getContainerSize(), ItemStack.EMPTY);
        for (int i = 0; i < items.size(); i++) {
            items.set(i, boot.getItem(i));
        }
        ContainerHelper.saveAllItems(output, items);
        output.putInt("key", keyWhere);
        output.putInt("customer", customer ? 1 : 0);
        output.putInt("hotwired", hotwired ? 1 : 0);
        output.putInt("worn", worn ? 1 : 0);
        output.putInt("lights", lightsOn() ? 1 : 0);
        output.putInt("glove_searched", gloveSearched ? 1 : 0);
        NonNullList<ItemStack> glove = NonNullList.withSize(glovebox.getContainerSize(), ItemStack.EMPTY);
        for (int i = 0; i < glove.size(); i++) {
            glove.set(i, glovebox.getItem(i));
        }
        ContainerHelper.saveAllItems(output.child("glovebox"), glove);
    }

    /** The loot table for this car's boot, from what kind of vehicle it is. */
    public String lootTable() {
        if (survivor()) {
            return "survivor";     // someone's whole life in the back: LOOT's survivor table
        }
        return switch (model()) {
            case "swat_van" -> "swat";
            case "ambulance", "ambulance_van" -> "ambulance";
            case "police_sedan", "police_suv" -> "police";
            case "fire_truck" -> "fire";
            case "city_bus", "school_bus" -> "bus";
            case "van", "step_van", "moving_truck", "box_truck", "tow_truck", "pickup", "garbage_truck", "garbage_truck_rear" -> "work";
            case "tractor_green", "tractor_red", "hay_wagon", "hay_wagon_empty", "horse_trailer" -> "work";
            default -> "trunk";
        };
    }

    /** Right-click does nothing to a car, so it can aim a gun; the interact key ({@link CarNet}) uses it. */
    @Override
    public InteractionResult interact(Player player, InteractionHand hand, Vec3 location) {
        return InteractionResult.PASS;
    }

    /**
     * The interact key on this car, at location relative to it: the back end searches the boot, elsewhere gets in.
     * A boat has nowhere to sit: anywhere on her searches her lockers.
     */
    public void use(Player player, Vec3 location) {
        if (!(level() instanceof ServerLevel server) || flyby) {
            return;
        }
        CarModels.Model m = CarModels.get(model());
        if (player instanceof net.minecraft.server.level.ServerPlayer sp && !hasPassenger(player) && Fuel.useCan(sp, this)) {
            return;     // a gas can in hand: pour it in, or siphon the tank
        }
        if (m != null && m.jet() && player instanceof net.minecraft.server.level.ServerPlayer sp && !hasPassenger(player)
                && AirReadiness.service(this, sp)) {
            return;     // fuel, a battery or parts in hand: service it rather than climb in
        }
        if (m != null && m.jet() || !(m != null && m.searchedOnly()) && !player.isSecondaryUseActive() && !atBoot(location)) {
            getIn(player);
            return;
        }
        if (locked && !CarKeys.carries(player, this)) {
            player.sendOverlayMessage(Component.literal("The boot's locked. Get in by a door (sneak and G smashes a window) and pop it."));
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
        String title = m != null && m.boat() ? "Lockers" : m != null && m.farm() ? switch (model()) {
            case "horse_trailer" -> "Tack room";
            case "hay_wagon", "hay_wagon_empty" -> "Wagon bed";
            default -> "Toolbox";
        } : switch (lootTable()) {
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

    /** Up on a hoist at all: its wheels are off the ground. */
    public boolean hoisted() {
        return entityData.get(LIFT) > 0.02f;
    }

    /** Up far enough (a hoist's wheels-free notch) that the wheels and brakes come off without a jack. */
    public boolean wheelsFree() {
        return entityData.get(LIFT) >= 0.45f;
    }

    /** Up high enough to stand under: everything underneath can be reached. */
    public boolean underReach() {
        return entityData.get(LIFT) >= 1.2f;
    }

    public void setLift(float metres) {
        entityData.set(LIFT, metres);
    }

    public float liftHeight() {
        return entityData.get(LIFT);
    }

    /** The corner a trolley jack holds up (0 front left, 1 front right, 2 rear left, 3 rear right), or -1. */
    public int jackCorner() {
        return entityData.get(JACK);
    }

    public float jackLift() {
        return entityData.get(JACK_LIFT);
    }

    /** Whether this wheel's corner is jacked high enough to spin the wheel free. */
    public boolean jackedFree(int corner) {
        return jackCorner() == corner && jackLift() >= ShopTool.JACK_LIFT - 0.02f;
    }

    public void setJack(int corner, float metres) {
        entityData.set(JACK, corner);
        entityData.set(JACK_LIFT, corner < 0 ? 0f : metres);
    }

    /** Whether a point (relative to the car, or in the world) is at its front end, where the bonnet and the engine are. */
    public boolean atBonnet(Vec3 location) {
        CarModels.Model m = CarModels.get(model());
        if (m == null) {
            return false;
        }
        if (location.horizontalDistanceSqr() > m.size[2] * m.size[2]) {
            location = location.subtract(position());
        }
        return local(location).z > m.size[2] / 2 - 1.1;
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
        getIn(player, -1);
    }

    /** Gets in to the seat given (by its door handle), or with -1 the free seat nearest where they stand. */
    public void getIn(Player player, int seat) {
        if (com.zomboidcraft.zombie.BeltedDead.doorOpened(this, player)) {
            return;   // the dead driver comes out at you instead
        }
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
        if (seat >= 0 && (seat >= seats() || seatTaken(seat))) {
            player.sendOverlayMessage(Component.literal("Someone's in that seat."));
            return;
        }
        if (locked && !hasPassenger(player) && !breakIn(player)) {
            return;
        }
        nextSeat = seat >= 0 ? seat : nearestFreeSeat(player);
        if (!seatFitted(nextSeat)) {
            player.sendOverlayMessage(Component.literal(nextSeat == 0 ? "The driver's seat is gone: nowhere to sit and drive."
                    : "That seat's been torn out."));
            return;
        }
        player.startRiding(this);
    }

    /** The model's seats (model space, the driver's first), for the dead belted into them. */
    public float[][] seatSpots() {
        CarModels.Model m = CarModels.get(model());
        return m == null || m.seats == null ? new float[0][] : m.seats;
    }

    /** Where to sit someone in seat s: the next rider added goes there. */
    public void seatNext(int s) {
        nextSeat = s;
    }

    /** Whether seat s still has a seat in it (Zomboid's seat parts); cars without parts have all theirs. */
    public boolean seatFitted(int s) {
        return parts().isEmpty() || s < 0 || s >= 8 || CarParts.get(parts(), CarParts.Part.SEAT, s) > 0;
    }

    /** Whether someone sits in seat s. */
    public boolean seatTaken(int s) {
        for (Entity rider : getPassengers()) {
            if (seatOf(rider) == s) {
                return true;
            }
        }
        return false;
    }

    /** A rider climbs over into another free seat; whoever ends up at the wheel drives. */
    public void switchSeat(Player rider, int seat) {
        int i = getPassengers().indexOf(rider);
        if (i < 0 || i >= 8 || seat < 0 || seat >= seats() || seatTaken(seat)) {
            return;
        }
        int packed = entityData.get(SEATS) & ~(0xF << (4 * i));
        entityData.set(SEATS, packed | (seat + 1) << (4 * i));
        if (level() instanceof ServerLevel server) {
            server.playSound(null, getX(), getY(), getZ(), SoundEvents.WOOL_STEP, SoundSource.PLAYERS, 0.6f, 0.8f);
        }
    }

    /** A point in the world in the car's model space (metres, nose toward +z, the driver's side +x). */
    public Vec3 toLocal(Vec3 at) {
        if (aircraft()) {
            // pitched and rolled with the airframe, about its pivot
            Vec3 d = at.subtract(position()).subtract(0, pivot(), 0);
            org.joml.Vector3f v = new Quaternionf(flight().q).conjugate().transform(new org.joml.Vector3f((float) d.x, (float) d.y, (float) d.z));
            return new Vec3(v.x, v.y + pivot(), v.z);
        }
        return local(at.subtract(position()));
    }

    /** A point in model space (metres, nose toward +z, the driver's side +x) in the world. */
    public Vec3 toWorld(float[] p) {
        if (aircraft()) {
            org.joml.Vector3f v = flight().q.transform(new org.joml.Vector3f(p[0], p[1] - pivot(), p[2]), new org.joml.Vector3f());
            return position().add(v.x, v.y + pivot(), v.z);
        }
        return position().add(world(p[0], p[1], p[2]));
    }

    public float turretSpin() {
        return entityData.get(TURRET_SPIN);
    }

    void setTurretSpin(float spin) {
        entityData.set(TURRET_SPIN, spin);
    }

    public boolean turretFiring() {
        return entityData.get(TURRET_FIRING);
    }

    void setTurretFiring(boolean firing) {
        entityData.set(TURRET_FIRING, firing);
    }

    /** Rounds on a mounted gun's belt. */
    public int belt() {
        return entityData.get(BELT);
    }

    public void setBelt(int rounds) {
        entityData.set(BELT, Math.max(0, rounds));
    }

    /** Where a seat's occupant's eyes are, in the world. */
    public Vec3 seatEye(int seat) {
        CarModels.Model m = CarModels.get(model());
        return m == null || m.seats == null || seat < 0 || seat >= m.seats.length ? position() : toWorld(m.seats[seat]);
    }

    /** How fast the car is going, blocks a tick: on the driver's computer from its own move, elsewhere from how far it went. */
    public double movingSpeed() {
        if (!level().isClientSide()) {
            return lastSpeed;
        }
        return isLocalInstanceAuthoritative() ? getDeltaMovement().horizontalDistance()
                : Math.hypot(getX() - xo, getZ() - zo);
    }

    /** The driver of a car on the move keeps a hand on the wheel: they can only use a handgun, and that one-handed. */
    public static boolean handsOnWheel(Entity entity) {
        return entity.getVehicle() instanceof CarProp car && car.seatOf(entity) == 0 && car.movingSpeed() > 0.04;
    }

    /** Whether someone in a car may fire, aim or swing what they hold: passengers anything, a moving driver only a handgun. */
    public static boolean mayUse(Entity entity, ItemStack stack) {
        if (Turret.isGunner(entity)) {
            return false;   // both hands on the mounted gun
        }
        if (!handsOnWheel(entity)) {
            return true;
        }
        com.zomboidcraft.combat.GunType gun = com.zomboidcraft.combat.GunType.of(stack);
        return gun != null && gun.handgun();
    }

    /** The seat a rider has (0 the driver's), or -1 if they aren't riding. */
    public int seatOf(Entity rider) {
        int i = getPassengers().indexOf(rider);
        if (i < 0) {
            return -1;
        }
        int s = i < 8 ? (entityData.get(SEATS) >> (4 * i) & 0xF) - 1 : -1;
        return s >= 0 ? s : i;
    }

    /** The empty seat nearest to where someone stands: walk up to the driver's door to drive. */
    public int nearestFreeSeat(Entity by) {
        CarModels.Model m = CarModels.get(model());
        int n = seats();
        if (n == 0) {
            return -1;
        }
        boolean[] taken = new boolean[n];
        for (Entity rider : getPassengers()) {
            int s = seatOf(rider);
            if (s >= 0 && s < n) {
                taken[s] = true;
            }
        }
        Vec3 at = local(by.position().subtract(position()));
        int best = -1;
        double bestD = Double.MAX_VALUE;
        for (int s = 0; s < n; s++) {
            if (taken[s]) {
                continue;
            }
            double dx = m.seats[s][0] - at.x, dz = m.seats[s][2] - at.z, d = dx * dx + dz * dz;
            if (d < bestD) {
                bestD = d;
                best = s;
            }
        }
        return best;
    }

    public boolean engineOn() {
        return entityData.get(ENGINE);
    }

    public boolean smashed() {
        return entityData.get(SMASHED);
    }

    /** How much bashing a car's windows take: a good car about six zombie blows, a wreck's already half gone. */
    private float windowsWhole() {
        return switch (variant()) {
            case "usable" -> 24f;
            case "abandoned" -> 18f;
            case "wrecked" -> 8f;
            default -> 0f;
        };
    }

    /** Whether someone sits safe behind a car's glass from what's at from: the window nearest it is still in and up. */
    public static boolean shielded(Entity entity, Entity from) {
        return entity.getVehicle() instanceof CarProp car && !car.reachable(entity, from);
    }

    /** Which of the windows something outside (in the world) would bash at, or null where the car has none. */
    public CarGlass.Window windowFacing(Vec3 at) {
        return CarGlass.facing(CarGlass.windows(CarModels.get(model()), variantIndex()), toLocal(at));
    }

    /**
     * Whether a mob at from can get at a rider: through the window nearest it, when that's down or broken out
     * and the rider sits close enough to it to be reached (a zombie at a bus's back window can't get the driver).
     */
    public boolean reachable(Entity rider, Entity from) {
        CarGlass.Window[] whole = CarGlass.windows(CarModels.get(model()), 0);
        if (whole.length == 0) {
            return smashed();
        }
        CarGlass.Window w = CarGlass.facing(whole, toLocal(from.getEyePosition()));
        return w != null && !panes().closed(w.index()) && toLocal(rider.getEyePosition()).distanceTo(w.centre()) < 1.6;
    }

    /** How much bashing a window of this kind takes when whole, in zombie damage. */
    private float glassWhole(CarGlass.Window w) {
        float share = switch (w.kind()) {
            case CarGlass.WINDSCREEN -> 1.25f;
            case CarGlass.REAR -> 0.8f;
            default -> 0.6f;
        };
        return Math.max(4f, windowsWhole() * share);
    }

    private float[] glassLeft(CarGlass.Window[] ws) {
        if (glassLeft == null || glassLeft.length != ws.length) {
            glassLeft = new float[ws.length];
            for (CarGlass.Window w : ws) {
                if (w != null) {
                    glassLeft[w.index()] = glassWhole(w) * (1 - (float) panes().bulletHoles(w.index()) / w.holes());
                }
            }
        }
        return glassLeft;
    }

    /**
     * A mob's blow at someone inside lands on the window nearest it instead, like a zombie on a door, cracking it,
     * until it gives; then blows through it get at whoever sits by it. Returns whether the blow reaches the rider.
     */
    public boolean blowAtRider(Entity rider, Entity attacker, float amount) {
        if (!(level() instanceof ServerLevel server)) {
            return true;
        }
        CarGlass.Window[] ws = CarGlass.windows(CarModels.get(model()), variantIndex());
        if (ws.length == 0) {
            return oldBlow(server, attacker, amount);   // an aircraft's canopy: all one piece
        }
        if (reachable(rider, attacker)) {
            return true;
        }
        CarGlass.Window open = CarGlass.facing(CarGlass.windows(CarModels.get(model()), 0), toLocal(attacker.getEyePosition()));
        if (open == null || !panes().closed(open.index()) || ws[open.index()] == null) {
            return false;   // flailing at an opening they can't reach through from there
        }
        CarGlass.Window w = ws[open.index()];
        float[] left = glassLeft(ws);
        float whole = glassWhole(w), was = left[w.index()];
        left[w.index()] -= amount;
        CarGlass.Panes p = panes();
        if (left[w.index()] > 0) {
            // a crack where it struck, at each third of the way to giving (never more than three to a window)
            if (Math.floor(was / whole * 3) != Math.floor(left[w.index()] / whole * 3)
                    && p.marks.get(w.index()).stream().filter(CarGlass.Mark::crack).count() < 3) {
                Vec3 at = toLocal(attacker.getEyePosition()).subtract(w.centre());
                float u = (float) Mth.clamp(at.dot(w.a()) / w.a().lengthSqr(), -0.7, 0.7);
                float v = (float) Mth.clamp(at.dot(w.b()) / w.b().lengthSqr(), -0.7, 0.7);
                if (!w.on(u, v)) {
                    u *= 0.4f;
                    v *= 0.4f;
                }
                p.marks.get(w.index()).add(new CarGlass.Mark(u, v, true));
                setPanes(p);
            }
            server.playSound(null, attacker.getX(), attacker.getEyeY(), attacker.getZ(), SoundEvents.GLASS_HIT, SoundSource.HOSTILE,
                    1f, 0.7f + random.nextFloat() * 0.2f);
            return false;
        }
        shatter(server, w);
        for (Entity r : getPassengers()) {
            if (r instanceof Player player && toLocal(r.getEyePosition()).distanceTo(w.centre()) < 1.6) {
                player.sendOverlayMessage(Component.literal("They've smashed the window in!"));
            }
        }
        return false;
    }

    /** The windows all as one, for what has no windows of its own listed (an aircraft's canopy). */
    private boolean oldBlow(ServerLevel server, Entity attacker, float amount) {
        if (smashed()) {
            return true;
        }
        if (windows < 0) {
            windows = windowsWhole();
        }
        windows -= amount;
        if (windows > 0) {
            server.playSound(null, attacker.getX(), attacker.getEyeY(), attacker.getZ(), SoundEvents.GLASS_HIT, SoundSource.HOSTILE,
                    1f, 0.7f + random.nextFloat() * 0.2f);
            return false;
        }
        entityData.set(SMASHED, true);
        server.playSound(null, attacker.getX(), attacker.getEyeY(), attacker.getZ(), SoundEvents.GLASS_BREAK, SoundSource.HOSTILE,
                1.2f, 0.8f + random.nextFloat() * 0.2f);
        Noise.emitEngine(server, position(), 16);
        return false;
    }

    /** A window gives: glass everywhere, a crash heard down the street. */
    private void shatter(ServerLevel server, CarGlass.Window w) {
        CarGlass.Panes p = panes();
        p.state[w.index()] = CarGlass.SHATTERED;
        setPanes(p);
        Vec3 at = toWorld(new float[]{(float) w.centre().x, (float) w.centre().y, (float) w.centre().z});
        server.playSound(null, at.x, at.y, at.z, SoundEvents.GLASS_BREAK, SoundSource.BLOCKS, 1.2f, 0.8f + random.nextFloat() * 0.2f);
        server.sendParticles(new net.minecraft.core.particles.BlockParticleOption(net.minecraft.core.particles.ParticleTypes.BLOCK,
                net.minecraft.world.level.block.Blocks.GLASS.defaultBlockState()), at.x, at.y, at.z, 24, 0.3, 0.2, 0.3, 0.1);
        Noise.emitEngine(server, position(), 16);
    }

    /**
     * A bullet goes through a window at (u, v) on it: a hole there, and it shatters if that's one more than it takes.
     * Returns whether it shattered.
     */
    public boolean shootWindow(ServerLevel server, int index, float u, float v, Vec3 at, boolean sound) {
        CarGlass.Window[] ws = CarGlass.windows(CarModels.get(model()), variantIndex());
        if (index < 0 || index >= ws.length || ws[index] == null || !panes().closed(index)) {
            return false;
        }
        server.sendParticles(new net.minecraft.core.particles.BlockParticleOption(net.minecraft.core.particles.ParticleTypes.BLOCK,
                net.minecraft.world.level.block.Blocks.GLASS.defaultBlockState()), at.x, at.y, at.z, 6, 0.04, 0.04, 0.04, 0.06);
        if (sound) {
            server.playSound(null, at.x, at.y, at.z, com.zomboidcraft.combat.Surface.GLASS.impact(), SoundSource.PLAYERS, 0.9f,
                    0.92f + random.nextFloat() * 0.16f);
        }
        CarGlass.Window w = ws[index];
        CarGlass.Panes p = panes();
        p.marks.get(index).add(new CarGlass.Mark(u, v, false));
        float[] left = glassLeft(ws);
        left[index] -= glassWhole(w) / w.holes();
        if (p.bulletHoles(index) >= w.holes() || left[index] <= 0) {
            shatter(server, w);
            return true;
        }
        setPanes(p);
        return false;
    }

    /** Winds a side window down, or back up (not one that's shattered). Returns whether it moved. */
    public boolean windWindow(int index) {
        CarGlass.Window[] ws = CarGlass.windows(CarModels.get(model()), variantIndex());
        if (index < 0 || index >= ws.length || ws[index] == null || !ws[index].winds() || !(level() instanceof ServerLevel server)) {
            return false;
        }
        CarGlass.Panes p = panes();
        if (p.state[index] == CarGlass.SHATTERED) {
            return false;
        }
        p.state[index] = p.state[index] == CarGlass.UP ? CarGlass.DOWN : CarGlass.UP;
        setPanes(p);
        Vec3 at = toWorld(new float[]{(float) ws[index].centre().x, (float) ws[index].centre().y, (float) ws[index].centre().z});
        server.playSound(null, at.x, at.y, at.z, SoundEvents.SPYGLASS_USE, SoundSource.NEUTRAL, 0.6f, p.state[index] == CarGlass.UP ? 0.7f : 0.55f);
        return true;
    }

    /** The windows as they are now (each computer reads them from the car's synced text). */
    public CarGlass.Panes panes() {
        String text = entityData.get(GLASS);
        if (panes == null || !text.equals(panesFrom)) {
            CarGlass.Window[] ws = CarGlass.windows(CarModels.get(model()), variantIndex());
            panes = CarGlass.Panes.decode(text, ws.length);
            panesFrom = text;
            if (text.isEmpty()) {
                // as the car was made: its look's broken-out windows are gone already
                for (int i = 0; i < ws.length; i++) {
                    if (ws[i] == null) {
                        panes.state[i] = CarGlass.SHATTERED;
                    }
                }
            }
        }
        return panes;
    }

    private void setPanes(CarGlass.Panes p) {
        String text = p.encode();
        panes = p;
        panesFrom = text;
        entityData.set(GLASS, text);
        entityData.set(SMASHED, p.size() > 0 && p.allShattered());
    }

    /** Each computer winds its picture of the windows toward where they are: about two seconds top to bottom. */
    private void wind() {
        CarGlass.Panes p = panes();
        if (wound.length != p.size()) {
            woundO = new float[p.size()];
            wound = new float[p.size()];
            for (int i = 0; i < wound.length; i++) {
                wound[i] = woundO[i] = p.state[i] == CarGlass.DOWN ? 1 : 0;
            }
            return;
        }
        for (int i = 0; i < wound.length; i++) {
            woundO[i] = wound[i];
            float to = p.state[i] == CarGlass.DOWN ? 1 : 0;
            wound[i] = to > wound[i] ? Math.min(to, wound[i] + 0.025f) : Math.max(to, wound[i] - 0.025f);
        }
    }

    /** Which of the model's looks this car has, as an index into its per-look data. */
    public int variantIndex() {
        return CarModels.variants().indexOf(variant());
    }

    /**
     * Nothing living but the riders gets inside an occupied car's outline: a mob that steps over
     * the edge is put back outside it, at the nearest side, so zombies crowd round and bash the
     * glass rather than climbing in. (A parked car's hull blocks do the same job.)
     */
    private void keepOut(CarModels.Model m) {
        double hw = m.size[0] / 2, hl = m.size[2] / 2;
        for (Mob mob : level().getEntitiesOfClass(Mob.class, getBoundingBox().inflate(hl), e -> !e.isPassenger() && e.isAlive())) {
            Vec3 at = local(mob.position().subtract(position()));
            double r = mob.getBbWidth() / 2 + 0.02;
            double inX = hw + r - Math.abs(at.x), inZ = hl + r - Math.abs(at.z);
            if (inX <= 0 || inZ <= 0 || at.y > m.size[1] || at.y + mob.getBbHeight() < 0) {
                continue;
            }
            Vec3 out = inX < inZ ? new Vec3(Math.copySign(hw + r, at.x == 0 ? 1 : at.x), at.y, at.z)
                    : new Vec3(at.x, at.y, Math.copySign(hl + r, at.z == 0 ? 1 : at.z));
            Vec3 move = world(out.x, out.y, out.z).add(position()).subtract(mob.position());
            mob.move(MoverType.SELF, new Vec3(move.x, 0, move.z));
            mob.setDeltaMovement(mob.getDeltaMovement().multiply(0.2, 1, 0.2));
        }
    }

    /**
     * The server's engine. It never starts by itself: the driver turns the key ({@link #toggleEngine}) and it
     * catches about three seconds into the starter. It runs on while anyone's inside, so whoever's at the
     * wheel can stop it to keep quiet, and it dies when the last one gets out.
     */
    private void engine(ServerLevel server, Entity driver) {
        if (aircraft()) {
            airEngine(server, driver);
        } else if (driver != lastDriver) {
            lastDriver = driver;
            if (driver instanceof Player player && !engineOn() && catchAt < 0) {
                player.sendOverlayMessage(Component.literal(jet() ? "Dead batteries, no ground power." : startHint(player)));
            }
        }
        if (!isVehicle() && (engineOn() || catchAt >= 0 || hotwireLeft >= 0)) {
            entityData.set(ENGINE, false);
            catchAt = -1;
            hotwireLeft = -1;
        }
        if (hotwireLeft >= 0) {
            hotwire(server, driver);
        }
        if (catchAt >= 0 && tickCount >= catchAt) {
            catchAt = -1;
            entityData.set(ENGINE, true);
            stopAlarm();      // started, by key or wires: the alarm's brain gives up
            Noise.emitEngine(server, position(), 24);
        }
        if (doorShutAt >= 0 && tickCount >= doorShutAt) {
            doorShutAt = -1;
            server.playSound(null, getX(), getY(), getZ(), aircraft() ? AirSounds.CANOPY : CarSounds.DOOR_CLOSE, SoundSource.NEUTRAL, 1f,
                    0.95f + random.nextFloat() * 0.1f);
        }
    }

    /** What the driver's told on taking the wheel of a car that isn't running: how to start it, and whether they can. */
    private String startHint(Player player) {
        if (!engineRuns()) {
            return "It's dead. It won't start.";
        }
        rollKey();
        String how = keyWhere == CarKeys.IGNITION || com.zomboidcraft.ZcConfig.get().world.vehicleEasyUse ? "The key's in the ignition: C to start it."
                : CarKeys.carries(player, this) ? "You have the key: C to start it."
                : hotwired ? "It's been hotwired: C to start it."
                : "No key: C to hotwire it. The wiring looks " + wiring(carCondition()) + ".";
        return how + " L headlights, W/S drive, A/D steer, space handbrake, H horn.";
    }

    /**
     * A car's condition, percent, decided the first time it matters from the state it was left in: a usable
     * car 55-100, an abandoned one 15-60, a wreck 0-15. Aircraft keep their own (the flight code's).
     */
    public int carCondition() {
        if (!aircraft() && !worn) {
            worn = true;
            java.util.Random rng = new java.util.Random(getUUID().getLeastSignificantBits());
            int found = survivor() ? 85 + rng.nextInt(16) : switch (variant()) {
                case "usable" -> 55 + rng.nextInt(46);
                case "abandoned" -> 15 + rng.nextInt(46);
                default -> rng.nextInt(16);
            };
            // Zomboid's General Condition; wrecks and burnt-out shells stay ruins
            setCondition("usable".equals(variant()) || "abandoned".equals(variant()) ? Math.clamp(found + Cars.conditionShift(), 0, 100) : found);
        }
        if (!aircraft() && !parts().isEmpty()) {
            return CarParts.overall(parts());
        }
        return condition();
    }

    /** The chance a hotwiring only sparks: hotwireFail in a car in perfect shape, up to hotwireWear more of the rest in a ruin. */
    private double hotwireFail() {
        com.zomboidcraft.ZcConfig.World w = com.zomboidcraft.ZcConfig.get().world;
        return w.hotwireFail + (1 - w.hotwireFail) * w.hotwireWear * (1 - carCondition() / 100.0);
    }

    private static String wiring(int condition) {
        return condition >= 80 ? "fine" : condition >= 55 ? "worn" : condition >= 30 ? "rough" : "like a rat's nest";
    }

    /** Decides where the key is, the first time it matters. */
    private void rollKey() {
        if (keyWhere == CarKeys.UNROLLED) {
            keyWhere = customer ? CarKeys.BOARD : CarKeys.roll(new java.util.Random(getUUID().getMostSignificantBits()));
        }
    }

    /** Worldgen: a customer car left at an auto shop, its key on the shop's key board ({@link CarKeys#board}). */
    public void setCustomer() {
        customer = true;
    }

    /** Whether this car's key still hangs on a shop's key board. */
    boolean onBoard() {
        rollKey();
        return keyWhere == CarKeys.BOARD;
    }

    /** Whether the key went walking with one of the dead nearby (deciding where it is if nobody has yet). */
    public boolean keyOnZombie() {
        if (!engineRuns()) {
            return false;
        }
        rollKey();
        return keyWhere == CarKeys.ZOMBIE;
    }

    /** The key's been found (dropped by a zombie, or taken from the glovebox). */
    void keyFound() {
        keyWhere = CarKeys.TAKEN;
    }

    /**
     * Whether this driver can start it: the key's in the ignition, they have it on them, or the car's been
     * hotwired. Aircraft need no key. Whether it would run at all is {@link #engineRuns}.
     */
    public boolean canStart(Player player) {
        if (jet()) {
            return true;
        }
        rollKey();
        return keyWhere == CarKeys.IGNITION || hotwired || CarKeys.carries(player, this) || com.zomboidcraft.ZcConfig.get().world.vehicleEasyUse;
    }

    public boolean lightsOn() {
        return entityData.get(LIGHTS);
    }

    /**
     * The ignition key (C), pressed at the wheel: starts the engine if they can (the starter, then it catches),
     * stops it if it's running, and with no key sets about hotwiring it, which takes a while and may only spark.
     */
    public void toggleEngine(Player player) {
        if (!(level() instanceof ServerLevel server) || getControllingPassenger() != player) {
            return;
        }
        if (jet()) {
            airEngine(player);
            return;
        }
        if (engineOn() || catchAt >= 0) {
            entityData.set(ENGINE, false);
            catchAt = -1;
            server.playSound(null, getX(), getY(), getZ(), SoundEvents.LEVER_CLICK, SoundSource.NEUTRAL, 0.5f, 0.6f);
            player.sendOverlayMessage(Component.literal("Engine off."));
            return;
        }
        if (hotwireLeft >= 0) {
            hotwireLeft = -1;
            player.sendOverlayMessage(Component.literal("You leave the wires hanging."));
            return;
        }
        if (!engineRuns()) {
            server.playSound(null, getX(), getY(), getZ(), CarSounds.STALL, SoundSource.NEUTRAL, 1f, 1f);
            player.sendOverlayMessage(Component.literal("It won't start."));
            return;
        }
        if (!canStart(player)) {
            hotwireLeft = (int) Math.round(com.zomboidcraft.ZcConfig.get().world.hotwireSeconds * 20);
            hotwirer = player;
            soundAlarm();
            player.sendOverlayMessage(Component.literal("No key. You pull the panel off under the dash: the wiring looks "
                    + wiring(carCondition()) + "..."));
            return;
        }
        crank(server);
    }

    /** The aircraft's own start-up: no key, but fuel, power and an airworthy airframe ({@link #toggleAirEngine}). */
    private void airEngine(Player player) {
        toggleAirEngine(player);
    }

    /** The starter turns over and the engine catches about three seconds later. */
    private void crank(ServerLevel server) {
        if (roadCar() && fuel() <= 0) {
            server.playSound(null, getX(), getY(), getZ(), CarSounds.STALL, SoundSource.NEUTRAL, 1f, 1f);
            if (getControllingPassenger() instanceof Player player) {
                player.sendOverlayMessage(Component.literal("It cranks and cranks but won't catch: the tank's dry."));
            }
            return;
        }
        String fault = parts().isEmpty() ? null : CarParts.startFault(parts(), new java.util.Random(random.nextLong()),
                knowsCars(getControllingPassenger()));
        if (fault != null) {
            server.playSound(null, getX(), getY(), getZ(), CarSounds.STALL, SoundSource.NEUTRAL, 1f, 1f);
            if (getControllingPassenger() instanceof Player player) {
                player.sendOverlayMessage(Component.literal(fault + " C to try again."));
            }
            return;
        }
        server.playSound(null, getX(), getY(), getZ(), CarSounds.START, SoundSource.NEUTRAL, 1f, 1f);
        catchAt = tickCount + 58;
        wear(CarParts.Part.STARTER, 0, 0.15);
        wear(CarParts.Part.SPARK_PLUGS, 0, 0.1);
    }

    /** Whether someone knows enough to tell which part's at fault (Mechanics 1). */
    private static boolean knowsCars(Entity who) {
        return who instanceof Player p && (p.isCreative() || com.zomboidcraft.skills.Skills.level(p, com.zomboidcraft.skills.Skill.MECHANICS) >= 1);
    }

    /** A tick of hotwiring: the one at it has to stay at the wheel; at the end it starts, or only sparks. */
    private void hotwire(ServerLevel server, Entity driver) {
        if (driver != hotwirer || !(driver instanceof Player player)) {
            hotwireLeft = -1;
            return;
        }
        if (hotwireLeft % 20 == 0 && hotwireLeft > 0) {
            int total = (int) Math.round(com.zomboidcraft.ZcConfig.get().world.hotwireSeconds * 20);
            player.sendOverlayMessage(Component.literal("Hotwiring... " + Math.min(99, 100 - hotwireLeft * 100 / Math.max(1, total)) + "%"));
            server.playSound(null, getX(), getY(), getZ(), SoundEvents.TRIPWIRE_CLICK_ON, SoundSource.NEUTRAL, 0.4f, 1.4f);
        }
        if (--hotwireLeft > 0) {
            return;
        }
        hotwireLeft = -1;
        if (random.nextDouble() < hotwireFail()) {
            server.playSound(null, getX(), getY(), getZ(), CarSounds.STALL, SoundSource.NEUTRAL, 1f, 1f);
            player.sendOverlayMessage(Component.literal(carCondition() < 55 ? "A spark, a cough, then nothing. The old wiring fights you: C to try again."
                    : "A spark, a cough, then nothing. C to try again."));
            return;
        }
        hotwired = true;
        player.sendOverlayMessage(Component.literal("The wires catch."));
        crank(server);
    }

    /** The headlights (L), switched at the wheel; they run off the battery, so a dead car's stay dark. */
    public void toggleLights(Player player) {
        if (!(level() instanceof ServerLevel server) || getControllingPassenger() != player) {
            return;
        }
        if (!engineRuns()) {
            player.sendOverlayMessage(Component.literal("Nothing. The battery's dead."));
            return;
        }
        if (!lightsOn() && !parts().isEmpty() && CarParts.get(parts(), CarParts.Part.HEADLIGHT, 0) <= 0
                && CarParts.get(parts(), CarParts.Part.HEADLIGHT, 1) <= 0) {
            server.playSound(null, getX(), getY(), getZ(), SoundEvents.LEVER_CLICK, SoundSource.NEUTRAL, 0.4f, 1.2f);
            player.sendOverlayMessage(Component.literal("Click. Both headlights are smashed or gone."));
            return;
        }
        entityData.set(LIGHTS, !lightsOn());
        server.playSound(null, getX(), getY(), getZ(), SoundEvents.LEVER_CLICK, SoundSource.NEUTRAL, 0.4f, 1.2f);
        if (helicopter()) {
            // a helicopter's searchlight is on with the engines: the switch puts it out
            player.sendOverlayMessage(Component.literal(lightsOn() ? "Searchlight off." : "Searchlight on."));
        }
    }

    /** Opens the glovebox for someone in a front seat, its key (if it's there) and odds and ends put in the first time. */
    public void openGlovebox(Player player) {
        if (!(level() instanceof ServerLevel server) || player.getVehicle() != this || seatOf(player) > 1) {
            return;
        }
        if (!parts().isEmpty() && CarParts.get(parts(), CarParts.Part.GLOVEBOX, 0) == -1) {
            player.sendOverlayMessage(Component.literal("The glovebox has been taken out."));
            return;
        }
        if (!gloveSearched) {
            gloveSearched = true;
            rollKey();
            if (keyWhere == CarKeys.GLOVEBOX && engineRuns()) {
                glovebox.setItem(random.nextInt(glovebox.getContainerSize()), CarKeys.make(this));
                keyFound();
            }
            if (!"burnt".equals(variant()) && com.zomboidcraft.ZcConfig.get().world.carLoot) {
                ResourceKey<LootTable> key = ResourceKey.create(Registries.LOOT_TABLE, ZomboidCraft.id("car/glovebox"));
                LootTable table = server.getServer().reloadableRegistries().getLootTable(key);
                LootParams params = new LootParams.Builder(server).withParameter(LootContextParams.ORIGIN, position())
                        .withLuck(player.getLuck()).withParameter(LootContextParams.THIS_ENTITY, player)
                        .create(LootContextParamSets.CHEST);
                for (ItemStack junk : table.getRandomItems(params, getUUID().getMostSignificantBits())) {
                    for (int i = 0; i < glovebox.getContainerSize(); i++) {
                        if (glovebox.getItem(i).isEmpty()) {
                            glovebox.setItem(i, junk);
                            break;
                        }
                    }
                }
            }
        }
        player.openMenu(new SimpleMenuProvider((id, inventory, p) -> new net.minecraft.world.inventory.HopperMenu(id, inventory, glovebox),
                Component.literal("Glovebox")));
        server.playSound(null, getX(), getY(), getZ(), SoundEvents.WOODEN_TRAPDOOR_OPEN, SoundSource.NEUTRAL, 0.5f, 1.3f);
    }

    /** A door opens to let someone in or out, and swings shut behind them. */
    private void door(ServerLevel server) {
        server.playSound(null, getX(), getY(), getZ(), aircraft() ? AirSounds.CANOPY : CarSounds.DOOR_OPEN, SoundSource.NEUTRAL, 1f,
                0.95f + random.nextFloat() * 0.1f);
        doorShutAt = tickCount + 14;
    }

    /** Server side: ticks left on the alarm. */
    private int alarmLeft;

    /**
     * Sets the alarm off for a while, if the car still has one: a car that would still start
     * (its battery not dead), nobody inside. Returns whether it went off.
     */
    public boolean tripAlarm(int ticks) {
        if (!engineRuns() || isVehicle() || alarmLeft > 0) {
            return false;
        }
        alarmLeft = ticks;
        entityData.set(ALARM, true);
        level().playSound(null, getX(), getY(), getZ(), CarSounds.ALARM, SoundSource.NEUTRAL, 2.5f, 1f);
        return true;
    }

    public boolean alarming() {
        return entityData.get(ALARM);
    }

    /** Server side: ticks until an alarm clock wired into the alarm (building/Builder) sets it off; 0 when none is. */
    private int rigLeft;

    /** Wires a clock into the alarm: it goes off in {@code ticks}. Whether it took (a car with a good battery, not already rigged). */
    public boolean rigAlarm(int ticks) {
        if (!engineRuns() || rigLeft > 0) {
            return false;
        }
        rigLeft = Math.max(1, ticks);
        return true;
    }

    public boolean rigged() {
        return rigLeft > 0;
    }

    /** The key's panic button pressed again: the alarm stops. */
    public void stopAlarm() {
        alarmLeft = 0;
        entityData.set(ALARM, false);
    }

    /** The alarm's whoop: the horn in a steady beat, and every zombie in earshot comes. Getting in stops it. */
    private void alarm(ServerLevel server) {
        if (rigLeft > 0 && --rigLeft == 0) {
            tripAlarm(com.zomboidcraft.ZcConfig.get().building.carAlarmSeconds * 20);
        }
        if (alarmLeft <= 0) {
            return;
        }
        if (--alarmLeft == 0) {
            alarmLeft = 0;
            entityData.set(ALARM, false);
            return;
        }
        if (alarmLeft % 79 == 0) {
            server.playSound(null, getX(), getY(), getZ(), CarSounds.ALARM, SoundSource.NEUTRAL, 2.5f, 1f);
        }
        if (alarmLeft % 40 == 0) {
            Noise.emitEngine(server, position(), 56);
        }
    }

    // ---------------------------------------------------------------- locks, alarms, sirens (Zomboid's settings)

    private static double share(String often) {
        return switch (often) {
            case "never" -> 0;
            case "extremely_rare" -> 0.03;
            case "rare" -> 0.1;
            case "sometimes" -> 0.3;
            case "often" -> 0.55;
            case "very_often" -> 0.8;
            default -> 0.3;
        };
    }

    /** Zomboid's Locked Vehicle Frequency and Car Alarm: whether a car that still runs is found locked, and armed. */
    private void rollLock() {
        lockRolled = true;
        if (!roadCar() || !engineRuns()) {
            return;
        }
        java.util.Random rng = new java.util.Random(getUUID().getMostSignificantBits() ^ 0x10C4L);
        com.zomboidcraft.ZcConfig.World w = com.zomboidcraft.ZcConfig.get().world;
        rollKey();
        // keys left in the ignition mean nobody locked up; a shop's customer cars are always locked
        locked = !w.vehicleEasyUse && keyWhere != CarKeys.IGNITION && (customer || survivor() || rng.nextDouble() < share(w.carLocked));
        armed = !w.vehicleEasyUse && rng.nextDouble() < share(w.carAlarm);
    }

    public boolean locked() {
        return locked;
    }

    /**
     * Someone tries a locked door: their key opens it; sneaking, they smash the nearest window and reach in (glass
     * everywhere, and an armed alarm goes off). Returns whether they're in.
     */
    private boolean breakIn(Player player) {
        if (CarKeys.carries(player, this)) {
            locked = false;
            stopAlarm();
            level().playSound(null, getX(), getY(), getZ(), SoundEvents.IRON_TRAPDOOR_OPEN, SoundSource.NEUTRAL, 0.5f, 1.8f);
            player.sendOverlayMessage(Component.literal("You unlock it with the key."));
            return true;
        }
        if (!player.isSecondaryUseActive() || !(level() instanceof ServerLevel server)) {
            player.sendOverlayMessage(Component.literal("It's locked. Sneak and press G to smash a window and reach in."));
            return false;
        }
        CarGlass.Window[] ws = CarGlass.windows(CarModels.get(model()), variantIndex());
        CarGlass.Window best = null;
        double bestD = Double.MAX_VALUE;
        for (CarGlass.Window w : ws) {
            if (w != null && panes().closed(w.index())) {
                Vec3 at = toWorld(new float[]{(float) w.centre().x, (float) w.centre().y, (float) w.centre().z});
                double d = at.distanceToSqr(player.getEyePosition());
                if (d < bestD) {
                    best = w;
                    bestD = d;
                }
            }
        }
        if (best != null) {
            shatter(server, best);
        }
        locked = false;
        soundAlarm();
        player.sendOverlayMessage(Component.literal(best != null ? "You put the window in and reach for the lock." : "You reach in and pop the lock."));
        return true;
    }

    /** A break-in sets off an armed alarm (once: after that its fuse is blown). */
    private void soundAlarm() {
        if (armed && tripAlarm(com.zomboidcraft.ZcConfig.get().building.carAlarmSeconds * 20 + 40 * 20)) {
            armed = false;
        }
    }

    /**
     * Zomboid's Recently Survivor Vehicles: a rare running car someone was living out of after the outbreak, found
     * locked, in good repair and fuelled (none, low, normal, high). Decided by the car itself, so it never changes.
     */
    public boolean survivor() {
        if (!roadCar() || !"usable".equals(variant()) || customer) {
            return false;
        }
        double share = switch (com.zomboidcraft.ZcConfig.get().world.survivorCars) {
            case "none" -> 0;
            case "normal" -> 0.03;
            case "high" -> 0.06;
            default -> 0.012;
        };
        return new java.util.Random(getUUID().getLeastSignificantBits() ^ 0x5A7L).nextDouble() < share;
    }

    /** Police cars, ambulances, fire trucks and the like: the ones with a siren. */
    public boolean emergency() {
        CarModels.Model m = CarModels.get(model());
        String id = model();
        return m != null && ("emergency".equals(m.kind) || id.startsWith("police") || id.contains("ambulance") || id.contains("fire_truck")
                || id.startsWith("swat"));
    }

    public boolean siren() {
        return entityData.get(SIREN);
    }

    private void setSiren(Player driver, boolean on) {
        if (on && (!engineRuns() || !battery())) {
            driver.sendOverlayMessage(Component.literal("The siren gives a dying squawk: no power."));
            return;
        }
        entityData.set(SIREN, on);
        sirenTicks = 0;
        driver.sendOverlayMessage(Component.literal(on ? "Siren on. Double-tap H to kill it." : "Siren off."));
    }

    /** The siren wails on whether anyone's in or not; zombies come (Siren Effects Zombies) until Siren Shutoff Hours runs out. */
    private void siren(ServerLevel server) {
        if (!siren()) {
            return;
        }
        com.zomboidcraft.ZcConfig.World w = com.zomboidcraft.ZcConfig.get().world;
        sirenTicks++;
        if (w.sirenShutoffHours > 0 && sirenTicks > w.sirenShutoffHours * 1000) {
            entityData.set(SIREN, false);     // a Zomboid hour is a Minecraft one: 1000 ticks
            return;
        }
        if (sirenTicks % 120 == 1) {
            server.playSound(null, getX(), getY(), getZ(), CarSounds.SIREN, SoundSource.NEUTRAL, 3f, 1f);
        }
        if (w.sirenEffectsZombies && sirenTicks % 40 == 0) {
            Noise.emitEngine(server, position(), 96);
        }
    }

    /** The driver leans on the horn: loud, and every zombie in earshot comes. */
    public void honk(Player driver) {
        if (level() instanceof ServerLevel server && getControllingPassenger() == driver && emergency() && tickCount - hornPressedAt < 10) {
            hornPressedAt = -100;
            setSiren(driver, !siren());
            return;
        }
        hornPressedAt = tickCount;
        if (level() instanceof ServerLevel server && getControllingPassenger() == driver && tickCount >= hornFreeAt && !jet()) {
            hornFreeAt = tickCount + 40;
            server.playSound(null, getX(), getY(), getZ(), CarSounds.HORN, SoundSource.NEUTRAL, 1.5f, 1f);
            Noise.emitEngine(server, position(), 48);
        }
    }

    /** Wrecks and burnt-out shells don't run, whatever you do. */
    public boolean engineRuns() {
        return "usable".equals(variant()) || "abandoned".equals(variant());
    }

    private boolean jet() {
        CarModels.Model m = CarModels.get(model());
        return m != null && m.jet();
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
        return (passenger instanceof Player || com.zomboidcraft.zombie.BeltedDead.belted(passenger))
                && getPassengers().size() < seats() && !"burnt".equals(variant()) && !crashed;
    }

    /** Whoever sits at the wheel drives; a car with only passengers in it goes nowhere. */
    @Override
    public LivingEntity getControllingPassenger() {
        for (Entity rider : getPassengers()) {
            if (seatOf(rider) == 0 && rider instanceof Player player) {
                return player;
            }
        }
        return null;
    }

    /** Each rider sits in the seat they took getting in: the one nearest where they stood. */
    @Override
    protected Vec3 getPassengerAttachmentPoint(Entity passenger, EntityDimensions dimensions, float scale) {
        CarModels.Model m = CarModels.get(model());
        int i = seatOf(passenger);
        if (m == null || m.seats == null || m.seats.length == 0 || i < 0) {
            return super.getPassengerAttachmentPoint(passenger, dimensions, scale);
        }
        float[] seat = m.seats[Math.min(i, m.seats.length - 1)];
        if (m.turret != null && i == m.gunner) {
            // the gunner stands behind the gun and goes round with it
            seat = Turret.turn(m.turret, seat, Turret.yaw(passenger.getYRot(), getYRot()));
        }
        if (aircraft()) {
            // the seat turns with the airframe about its middle, the eyes staying upright above it
            float pivot = pivot();
            org.joml.Vector3f at = flight().q.transform(new org.joml.Vector3f(seat[0], seat[1] - pivot, seat[2]));
            return new Vec3(at.x, at.y + pivot - 1.02 * riderScale(passenger), at.z);
        }
        // a player's eyes are 1.02 above where they hang from a vehicle (less when they're shrunk to fit it)
        return world(seat[0], seat[1] - 1.02 * riderScale(passenger), seat[2]);
    }

    /** Riders turn with the car, and can look round but not right behind them. */
    @Override
    protected void positionRider(Entity passenger, MoveFunction move) {
        super.positionRider(passenger, move);
        if (aircraft()) {
            return;     // in the air your view is the aircraft's plus where you look in it (FlightClient)
        }
        if (passenger instanceof Player player && player.isLocalPlayer()) {
            float turn = getYRot() - yRotO;
            player.setYRot(player.getYRot() + turn);
            player.setYHeadRot(player.getYHeadRot() + turn);
            clampLook(player);
        }
    }

    @Override
    public void onPassengerTurned(Entity passenger) {
        if (aircraft()) {
            return;
        }
        clampLook(passenger);
    }

    private void clampLook(Entity passenger) {
        CarModels.Model m = CarModels.get(model());
        if (m != null && m.turret != null && seatOf(passenger) == m.gunner) {
            // the gun goes all the way round; it tilts only so far
            float pitch = Mth.clamp(passenger.getXRot(), -m.turret.up, m.turret.down);
            passenger.xRotO += pitch - passenger.getXRot();
            passenger.setXRot(pitch);
            return;
        }
        float rel = Mth.wrapDegrees(passenger.getYRot() - getYRot());
        float clamped = Mth.clamp(rel, -130f, 130f);
        passenger.yRotO += clamped - rel;
        passenger.setYRot(passenger.getYRot() + clamped - rel);
        passenger.setYHeadRot(passenger.getYRot());
    }

    private static float riderScale(Entity passenger) {
        return passenger instanceof LivingEntity living ? living.getScale() : 1f;
    }

    private static final net.minecraft.resources.Identifier RIDER_SCALE = ZomboidCraft.id("car_rider_scale");

    /**
     * Riders are shrunk a little to fit the cabin (world.carRiderScale), so no knees or elbows poke out
     * through the doors (or round the cockpit), and grow back as they get out.
     */
    private void fit(Entity passenger, boolean in) {
        if (level().isClientSide() || !(passenger instanceof LivingEntity rider)) {
            return;
        }
        net.minecraft.world.entity.ai.attributes.AttributeInstance scale = rider.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.SCALE);
        if (scale == null) {
            return;
        }
        double to = com.zomboidcraft.ZcConfig.get().world.carRiderScale;
        if (in && to > 0 && to != 1) {
            scale.addOrUpdateTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(RIDER_SCALE, to - 1,
                    net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        } else if (scale.getModifier(RIDER_SCALE) != null) {
            scale.removeModifier(RIDER_SCALE);
        }
    }

    @Override
    protected void addPassenger(Entity passenger) {
        boolean first = getPassengers().isEmpty();
        fit(passenger, true);
        super.addPassenger(passenger);
        int i = getPassengers().indexOf(passenger);
        if (!level().isClientSide() && i >= 0 && i < 8) {
            int seat = nextSeat >= 0 ? nextSeat : nearestFreeSeat(passenger);
            nextSeat = -1;
            int packed = entityData.get(SEATS) & ~(0xF << (4 * i));
            entityData.set(SEATS, packed | (Math.max(seat, 0) + 1) << (4 * i));
        }
        boolean dead = com.zomboidcraft.zombie.BeltedDead.belted(passenger);
        if (first && !dead && level() instanceof ServerLevel server) {
            Cars.clearHull(server, this);
            parkPending = false;
            mountedAt = tickCount;
        }
        if (!dead && level() instanceof ServerLevel server) {
            door(server);
        }
        if (passenger instanceof Player player && player.isLocalPlayer()) {
            player.setYRot(getYRot());
            player.setYHeadRot(getYRot());
            if (aircraft()) {
                boarded.accept(this);
            }
        }
        setBoundingBox(makeBoundingBox(position()));
    }

    @Override
    protected void removePassenger(Entity passenger) {
        int i = getPassengers().indexOf(passenger);
        if (!level().isClientSide() && i >= 0 && i < 8) {
            // the riders after this one move up a place in the list, their seats with them
            int packed = entityData.get(SEATS), low = packed & ((1 << (4 * i)) - 1);
            entityData.set(SEATS, low | (i < 7 ? (packed >>> (4 * (i + 1))) << (4 * i) : 0));
        }
        fit(passenger, false);
        super.removePassenger(passenger);
        if (level() instanceof ServerLevel server && !isRemoved()) {
            door(server);
        }
        if (getPassengers().isEmpty()) {
            if (aircraft()) {
                // it may still be rolling or in the air: the server flies it on until it comes to rest
                airborne = level() instanceof ServerLevel;
            } else {
                setDeltaMovement(Vec3.ZERO);
                parkPending = level() instanceof ServerLevel;
            }
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
        if (aircraft() && (isVehicle() || airborne)) {
            // flying: a box round the fuselage and the inner wings, kept square since it can't turn
            double r = Math.min(m.size[0], m.size[2]) * 0.3, h = m.size[1] * 0.8;
            return new AABB(pos.x - r, pos.y, pos.z - r, pos.x + r, pos.y + h, pos.z + r);
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
        if (MODEL.equals(accessor) || VARIANT.equals(accessor)) {
            panes = null;   // read the windows again for the model and look they belong to
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (flyby) {
            return;     // server side, a flyby is flown by WorldEvents
        }
        lidOpenO = lidOpen;
        rolledO = rolled;
        steerO = steer;
        if (level().isClientSide()) {
            wind();
            barrelsO = barrels;
            // about seven turns a second flat out
            barrels = (barrels + turretSpin() * 2.2f) % (float) (Math.PI * 2000);
        }
        if (isLocalInstanceAuthoritative() && getControllingPassenger() instanceof Player driver) {
            getInterpolation().cancel();
            if (aircraft()) {
                fly(level().isClientSide() ? flightInput.apply(this) : Flight.Controls.NONE);
            } else {
                drive(level().isClientSide() ? clientInput.apply(driver) : Input.EMPTY);
            }
        } else if (level().isClientSide()) {
            getInterpolation().interpolate();
            if (aircraft()) {
                followFlight();
            }
        } else if (aircraft() && getControllingPassenger() == null && airborne) {
            fly(Flight.Controls.NONE);      // nobody at the controls: it glides, falls or rolls to a stop
        } else if (isVehicle() && getControllingPassenger() == null) {
            setDeltaMovement(Vec3.ZERO);  // passengers but nobody at the wheel
        }
        if (level() instanceof ServerLevel server) {
            if (!aircraft() && parts().isEmpty()) {
                rollParts();
            }
            if (!fuelRolled && !aircraft()) {
                rollFuel();
            }
            if (!lockRolled && !aircraft()) {
                rollLock();
            }
            siren(server);
            if (roadCar()) {
                burnFuel(server);
            }
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
            alarm(server);
            Turret.tick(this, server);
            CarModels.Model model = CarModels.get(model());
            if (isVehicle() && model != null) {
                keepOut(model);
            }
            if (aircraft() && !airFound) {
                airFound = true;
                AirReadiness.found(this);
            }
            if (aircraft()) {
                flown(server, moved);
            } else if (getControllingPassenger() instanceof Player driver) {
                driven(server, driver, moved);
            }
        } else {
            lidOpen = Math.clamp(lidOpen + (entityData.get(OPEN) ? 0.08f : -0.1f), 0f, 1f);
            wheels();
            if (aircraft()) {
                Flight f = flight();
                rotorO = rotor;
                rotor += f.rotorTurns() * Math.PI * 2 * Flight.DT;
                airSound.accept(this);
            } else if (engineOn()) {
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
        if (hoisted() || jackCorner() >= 0) {
            setDeltaMovement(Vec3.ZERO);    // wheels spinning in the air on the hoist's arms, or a corner up on a jack
            return;
        }
        double rad = Math.toRadians(getYRot());
        Vec3 v = getDeltaMovement();
        double fx = -Math.sin(rad), fz = Math.cos(rad);
        double speed = v.x * fx + v.z * fz, lx = v.x - fx * speed, lz = v.z - fz * speed;
        CarParts.Drive d = partDrive();
        double top = topSpeed() * d.top();
        double accel = ACCEL * d.power(), brake = BRAKE * d.brake();
        boolean runs = engineOn(), ground = onGround();
        boolean stopping = in.backward() && !in.forward() && speed > 0.02 || in.forward() && !in.backward() && speed < -0.02;
        if (runs && (in.backward() && !in.forward() && speed > 0.02 || in.forward() && !in.backward() && speed < -0.02
                || in.jump() && Math.abs(speed) > 0.01)) {
            braking = 4;
        }
        if (ground) {
            if (in.forward() && !in.backward() && runs) {
                speed = speed < -0.02 ? approach(speed, 0, brake) : speed + accel * Math.max(0, 1 - (speed / top) * (speed / top));
            } else if (in.backward() && !in.forward() && runs) {
                speed = speed > 0.02 ? approach(speed, 0, brake) : speed - accel * 0.6 * Math.max(0, 1 - (speed / REVERSE) * (speed / REVERSE));
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
        if (ground && Math.abs(speed) > 0.05) {
            // flats and bent rims drag the car to one side, uneven brakes pull it while stopping
            target += (float) ((d.pull() + (stopping && runs ? d.brakePull() : 0)) * 3);
        }
        float rate = (float) (0.12 * d.steer());
        steer += Mth.clamp(target - steer, -rate, rate);
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
        double grip = (!ground ? 0 : in.jump() ? 0.15 : level().isRainingAt(blockPosition()) ? 0.35 : 0.6) * d.grip();
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
        if (runs && speed < -0.01) {
            reversing = 4;
        }
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

    /** Hits on whatever living thing the vehicle runs into at speed (blocks a tick), thrown aside and knocked down. */
    private void strike(ServerLevel server, LivingEntity driver, CarModels.Model m, double speed) {
        if (speed <= 0.12) {
            return;
        }
        for (LivingEntity hit : struck(m)) {
            float damage = (float) (speed * speed * 25 + speed * 5);
            if (hit instanceof Player) {
                damage *= (float) com.zomboidcraft.ZcConfig.get().world.carHitDamage;     // Zomboid's Damage From Being Hit By A Car
            }
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
            Noise.emitEngine(server, position(), 30);
            crashHurt(server, speedBefore);
            if (!parts().isEmpty()) {
                crashWear(speedBefore);
            }
            speedBefore = 0;
        } else {
            speedBefore = lastSpeed;
        }
        lastSpeed = speed;
        strike(server, driver, m, speed);
        if (engineOn() && tickCount % 10 == 0) {
            Noise.emitEngine(server, position(), (18 + 30 * speed) * Math.min(2.5, partDrive().noise()));
        }
        if (!parts().isEmpty()) {
            wearDriving(moved, speed);
        }
        if (engineOn() && tickCount % 5 == 0 && tickCount - mountedAt > 100) {
            driver.sendOverlayMessage(Component.literal(Math.round(speed * 72) + " km/h" + (roadCar() ? "   " + gauge() : "")));
        }
    }

    /**
     * Wear from a tick of driving, by what the driver does with it: distance wears the tyres, the oil and the
     * engine (more with the oil low, and hard near the top speed), hard slowing wears the brakes, and a
     * jolt down onto the road (a kerb, a drop) wears the suspension.
     */
    private void wearDriving(Vec3 moved, double speed) {
        double scale = com.zomboidcraft.ZcConfig.get().world.partWear;
        if (scale <= 0) {
            return;
        }
        String p = parts();
        double km = speed / 1000 * scale;   // a block a metre
        double oil = CarParts.get(p, CarParts.Part.OIL, 0) / 100.0;
        double redline = speed > topSpeed() * 0.85 ? 3 : 1;
        wear(CarParts.Part.ENGINE, 0, km * 0.4 * redline * (oil <= 0 ? 20 : oil < 0.25 ? 4 : 1)
                * (CarParts.get(p, CarParts.Part.RADIATOR, 0) <= 0 || CarParts.get(p, CarParts.Part.FAN_BELT, 0) <= 0 ? 15 : 1));
        wear(CarParts.Part.OIL, 0, km * 2);
        wear(CarParts.Part.GEARBOX, 0, km * 0.3);
        wear(CarParts.Part.EXHAUST, 0, km * 0.3);
        wear(CarParts.Part.FUEL_PUMP, 0, km * 0.2);
        wear(CarParts.Part.FAN_BELT, 0, km * 0.4);
        for (int i = 0; i < 4; i++) {
            wear(CarParts.Part.TIRE, i, km * 1.2);
            wear(CarParts.Part.SUSPENSION, i, km * 0.3);
        }
        // the driver's moves arrive unevenly, so one tick's drop is capped at about a hard stop's
        double slowed = Math.min(0.06, lastSpeedForWear - speed);
        if (slowed > 0.02) {
            for (int i = 0; i < 4; i++) {
                wear(CarParts.Part.BRAKES, i, slowed * (i < 2 ? 0.8 : 0.5) * scale);
            }
            wear(CarParts.Part.BRAKE_FLUID, 0, slowed * 0.2 * scale);
        }
        if (moved.y < -0.3 && speed > 0.2) {
            for (int i = 0; i < 4; i++) {
                wear(CarParts.Part.SUSPENSION, i, -moved.y * 4 * scale);
                wear(CarParts.Part.RIM, i, -moved.y * 1.5 * scale);
            }
        }
        lastSpeedForWear = speed;
    }

    /**
     * Zomboid's Player Damage From Crash: everyone inside is thrown about by a crash from past about 30 km/h, worse
     * the faster it was (twice the hurt at 70 as at 50): cuts on the head and arms off the wheel and glass, and from
     * about 80 a chance of a broken bone (the survival body's 17-part health, on its six regions).
     */
    private void crashHurt(ServerLevel server, double speed) {
        if (!com.zomboidcraft.ZcConfig.get().world.playerDamageFromCrash || speed < 0.42) {
            return;
        }
        float damage = (float) (Math.pow(speed - 0.35, 1.6) * 40 * Cars.crashDamage());
        for (Entity rider : getPassengers()) {
            if (!(rider instanceof net.minecraft.server.level.ServerPlayer p) || p.isCreative()) {
                continue;
            }
            p.hurtServer(server, damageSources().flyIntoWall(), damage);
            if (!com.zomboidcraft.ZcConfig.get().survival.injuries) {
                continue;
            }
            com.zomboidcraft.survival.Body body = com.zomboidcraft.survival.Body.get(p);
            // head, torso and arms first; only a hard crash reaches the legs
            com.zomboidcraft.survival.Body.Zone zone = com.zomboidcraft.survival.Body.Zone.values()[random.nextInt(speed > 0.9 ? 6 : 4)];
            com.zomboidcraft.survival.Body.Region r = com.zomboidcraft.survival.Body.any(zone, random);
            if (random.nextDouble() < Math.min(0.9, (speed - 0.35) * 1.5)) {
                body = body.wound(r, com.zomboidcraft.survival.Body.Wound.LACERATION, true);
            }
            if (speed > 1.1 && random.nextDouble() < (speed - 1.1) * 1.5 && !body.part(r).fractured()) {
                body = body.fracture(r);
                p.sendOverlayMessage(Component.literal("You broke your " + r.label + " in the crash."));
            }
            com.zomboidcraft.survival.Body.set(p, body);
        }
    }

    /** A crash at speed (blocks a tick) knocks the front about: lights, bonnet, radiator, steering, the front wheels. */
    private void crashWear(double speed) {
        double hit = Math.min(60, speed * speed * 60) * com.zomboidcraft.ZcConfig.get().world.partWear * Cars.crashDamage();
        wear(CarParts.Part.HEADLIGHT, 0, hit * 1.5);
        wear(CarParts.Part.HEADLIGHT, 1, hit * 1.5);
        wear(CarParts.Part.BONNET, 0, hit);
        wear(CarParts.Part.RADIATOR, 0, hit);
        wear(CarParts.Part.STEERING, 0, hit * 0.5);
        wear(CarParts.Part.ENGINE, 0, hit * 0.25);
        wear(CarParts.Part.RIM, 0, hit * 0.5);
        wear(CarParts.Part.RIM, 1, hit * 0.5);
        wear(CarParts.Part.SUSPENSION, 0, hit * 0.4);
        wear(CarParts.Part.SUSPENSION, 1, hit * 0.4);
    }

    /**
     * Rolls the wheels on by how far the car went, and on other players' cars guesses the steer from the turn
     * and the brake and reversing lights from the speed.
     */
    private void wheels() {
        double rad = Math.toRadians(getYRot());
        double along = (getX() - xo) * -Math.sin(rad) + (getZ() - zo) * Math.cos(rad);
        rolled += along;
        braking = Math.max(0, braking - 1);
        reversing = Math.max(0, reversing - 1);
        if (!isLocalInstanceAuthoritative()) {
            // someone else's car: the brake lights come on when it slows harder than it coasts
            if (along > 0.02 && along < alongO - 0.015) {
                braking = 6;
            }
            if (along < -0.01) {
                reversing = 3;
            }
        }
        alongO = along;
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
        // a town parks a hundred or more, further off they're lost in the haze anyway; aircraft are seen in the sky from far off
        return distance < (aircraft() ? 256 * 256 : 96 * 96);
    }

    @Override
    public void remove(RemovalReason reason) {
        if (reason.shouldDestroy() && level() instanceof ServerLevel server) {
            ZomboidCraft.LOGGER.info("Car {} removed ({})", model(), reason);
            Cars.clearHull(server, this);
        }
        super.remove(reason);
    }

    // ---------------------------------------------------------------- aircraft

    /** The local pilot's controls, from the client ({@code FlightClient}); a server never needs it. */
    public static Function<CarProp, Flight.Controls> flightInput = car -> Flight.Controls.NONE;
    /** Sends the pilot's state to the server each tick, from the client. */
    public static java.util.function.Consumer<AirNet.State> pilotReport = state -> { };
    /** Called on the client each tick for an aircraft, to keep its sounds going. */
    public static java.util.function.Consumer<CarProp> airSound = car -> { };
    /** Client side: aircraft whose engines stay silent, as a flyby heard as a whole recording (WorldEventSound). */
    public static java.util.function.Predicate<CarProp> quiet = car -> false;
    /** Called on the client when the local player gets into an aircraft. */
    public static java.util.function.Consumer<CarProp> boarded = car -> { };

    private Flight flight;
    /** Server side: one of WorldEvents' flybys, crossing the map on its own line; never saved, never boarded. */
    private boolean flyby;
    /** Server side: nobody's flying it but it's still moving, so the server does. */
    private boolean airborne;
    private boolean crashed;
    /** The main rotor's turn (radians) this tick and last, client side, for drawing. */
    public double rotor, rotorO;
    /** Server side: which pilot the start-up hint was shown to. */
    private Entity airPilot;
    /** Client side: how hard it hit the ground last tick, for the touchdown sound (metres a second, 0 if not). */
    public float touchedDown;

    /** A jet or the helicopter: anything that flies rather than drives. */
    public boolean aircraft() {
        return jet();
    }

    public boolean helicopter() {
        return model().startsWith("heli_");
    }

    /** This aircraft's flight state (attitude, speed, engine), made the first time it's asked for. */
    public Flight flight() {
        if (flight == null) {
            flight = new Flight(helicopter() ? Flight.Kind.HELI : Flight.Kind.JET);
            org.joml.Quaternionfc synced = entityData.get(AIR_ATTITUDE);
            if (synced.w() == 1 && synced.x() == 0 && synced.y() == 0 && synced.z() == 0) {
                flight.set(getYRot(), 0, 0);
            } else {
                flight.q.set(synced);
                flight.qO.set(synced);
                flight.heading = flight.yaw();
                flight.pitch = flight.noseUp();
                flight.roll = flight.bank();
            }
            flight.rpm = entityData.get(AIR_RPM);
            float top = entityData.get(AIR_TOP);
            if (top > 0) {
                flight.top = top;
            }
        }
        return flight;
    }

    /** Height of the point the airframe turns about, above the wheels. */
    public float pivot() {
        CarModels.Model m = CarModels.get(model());
        return m == null ? 1 : m.size[1] * 0.4f;
    }

    /** Whether the aircraft's first-found state (fuel, battery, wear) has been rolled. */
    private boolean airFound;

    /** Fuelled, a good battery and in full repair: an aircraft spawned to fly straight away. */
    /**
     * One tick of a flyby (WorldEvents): puts it where its line says, nose along it, engines running at cruise
     * (or in afterburner), so everyone's game draws it flying and plays its engines as for any aircraft.
     */
    public void flyby(Vec3 at, Vec3 velocity, boolean afterburner) {
        double horizontal = Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z);
        float yaw = (float) Math.toDegrees(Math.atan2(-velocity.x, velocity.z));
        float nose = helicopter() ? -7 : (float) Math.toDegrees(Math.atan2(velocity.y, horizontal));
        flyby(at, velocity, yaw, nose, 0, afterburner);
    }

    /** The same, its attitude given outright (an air strike's dive, turns and the helicopter's hover, WorldEvents.Event.place). */
    public void flyby(Vec3 at, Vec3 velocity, float yaw, float nose, float bank, boolean afterburner) {
        flyby = true;
        noPhysics = true;
        Flight f = flight();
        f.set(yaw, nose, bank);
        f.vel = velocity;
        f.grounded = false;
        f.rpm = 1;
        f.power = f.kind == Flight.Kind.HELI ? 0.65f : afterburner ? 0.95f : 0.8f;
        f.throttle = f.power;
        setPos(at.x, at.y, at.z);
        setYRot(yaw);
        setXRot(-nose);
        setDeltaMovement(velocity.scale(Flight.DT));
        // how far the nose rides above the way it's going (the leading-edge flaps droop with it)
        f.aoa = velocity.lengthSqr() < 1 ? 0 : (float) Math.atan2(-velocity.dot(f.up()), Math.max(0.1, velocity.dot(f.forward())));
        entityData.set(ENGINE, true);
        entityData.set(AIR_ATTITUDE, new Quaternionf(f.q));
        entityData.set(AIR_RPM, f.rpm);
        entityData.set(AIR_POWER, f.power);
    }

    @Override
    public boolean shouldBeSaved() {
        return !flyby && super.shouldBeSaved();
    }

    public void readyToFly() {
        airFound = true;
        setFuel(1);
        setBattery(true);
        setCondition(100);
    }
    /** Server: ticks in the air since the last landing. */
    private int flightTicks;
    /** Whether a catapult was pulling the jet last tick (for the call when it fires). */
    private boolean launching;

    /** The parts string ({@link CarParts}), empty on a car that has none (aircraft, boats) or isn't rolled yet. */
    public String parts() {
        return entityData.get(PARTS);
    }

    public void setParts(String parts) {
        entityData.set(PARTS, parts);
    }

    /** Server side: gives a road vehicle its parts the first time it ticks, scattered about the condition it was found in. */
    private void rollParts() {
        CarModels.Model m = CarModels.get(model());
        if (parts().isEmpty() && CarParts.applies(m)) {
            int overall = carCondition();
            setParts(CarParts.roll(m, variant(), overall, new java.util.Random(getUUID().getMostSignificantBits() ^ 0x5eedL)));
        }
    }

    /** Wears one part slot by amount (percent), stopping at broken (0); missing parts stay missing. */
    public void wear(int slot, double amount) {
        String p = parts();
        int c = CarParts.get(p, slot);
        if (c <= 0 || amount <= 0) {
            return;
        }
        partWear[slot] += amount;
        int whole = (int) partWear[slot];
        if (whole > 0) {
            partWear[slot] -= whole;
            setParts(CarParts.set(p, slot, Math.max(0, c - whole)));
        }
    }

    public void wear(CarParts.Part part, int index, double amount) {
        wear(CarParts.slot(part, index), amount);
    }

    /** What the parts make of the drive; perfect for anything without them. */
    public CarParts.Drive partDrive() {
        return CarParts.drive(parts());
    }

    public float fuel() {
        return entityData.get(FUEL);
    }

    public void setFuel(float fuel) {
        entityData.set(FUEL, Math.clamp(fuel, 0f, 1f));
    }

    /** What the tanks hold when full, litres: the real aircraft's internal fuel, or a car's tank. */
    public float fuelCapacity() {
        String m = model();
        if (m.equals("jet_corvid") || m.equals("jet_gyrfalcon") || m.startsWith("heli")) {
            return m.equals("jet_corvid") ? 10300 : m.equals("jet_gyrfalcon") ? 11800 : 1420;
        }
        CarModels.Model cm = CarModels.get(m);
        if (cm != null && "bus".equals(cm.kind)) {
            return 150;
        }
        // Zomboid's tanks by class: a small car's, a standard one's, a truck's or van's
        return switch (CarParts.kind(cm)) {
            case SPORT -> 40;
            case HEAVY -> cm != null && ("car".equals(cm.kind) || cm.kind == null) ? 70 : 80;
            default -> 55;
        };
    }

    /** A car, van, truck or bus that runs on the road's fuel (not an aircraft, boat or cart). */
    public boolean roadCar() {
        return !aircraft() && CarParts.applies(CarModels.get(model()));
    }

    /**
     * The tank as found: Zomboid's Chance Has Gas decides whether there's any in it, Initial Gas how much.
     * Wrecks and burnt-out shells are dry; a survivor-kept car comes full.
     */
    private void rollFuel() {
        fuelRolled = true;
        if (!roadCar()) {
            return;
        }
        java.util.Random rng = new java.util.Random(getUUID().getLeastSignificantBits() ^ 0xF0E1L);
        com.zomboidcraft.ZcConfig.World w = com.zomboidcraft.ZcConfig.get().world;
        double has = switch (w.carChanceHasGas) {
            case "low" -> 0.4;
            case "high" -> 0.8;
            default -> 0.6;
        };
        if (survivor()) {
            setFuel(0.7f + rng.nextFloat() * 0.3f);     // kept topped up by whoever was living out of it
            return;
        }
        if (!engineRuns() || rng.nextDouble() >= has) {
            setFuel(0);
            return;
        }
        double[] range = switch (w.carInitialGas) {
            case "very_low" -> new double[] {0.02, 0.12};
            case "normal" -> new double[] {0.15, 0.45};
            case "high" -> new double[] {0.3, 0.7};
            case "very_high" -> new double[] {0.5, 0.9};
            case "full" -> new double[] {1, 1};
            default -> new double[] {0.05, 0.25};
        };
        setFuel((float) (range[0] + rng.nextDouble() * (range[1] - range[0])));
    }

    /** Server tick: the running engine drinks by how hard it's worked, and a holed tank leaks whether it runs or not. */
    private void burnFuel(ServerLevel server) {
        if (fuel() <= 0) {
            return;
        }
        float cap = fuelCapacity();
        double litres = 0;
        if (engineOn()) {
            // about 35 minutes of cruising on a full standard tank; idling a quarter-litre a minute; worn engines drink more
            double speed = getDeltaMovement().horizontalDistance();
            double perMinute = 0.25 + 1.2 * Math.pow(Math.min(1.4, speed / 0.83), 1.5);
            double load = CarParts.kind(CarModels.get(model())) == CarParts.Kind.HEAVY ? 1.6 : 1;
            double engine = parts().isEmpty() ? 1 : 1 + 0.5 * (1 - CarParts.get(parts(), CarParts.Part.ENGINE, 0) / 100.0);
            litres += perMinute * load * engine * com.zomboidcraft.ZcConfig.get().world.carGasConsumption / 1200;
        }
        int tank = parts().isEmpty() ? 100 : CarParts.get(parts(), CarParts.Part.FUEL_TANK, 0);
        if (tank >= 0 && tank < 15) {
            litres += (15 - tank) * 0.0005;     // a holed tank drips away: up to about a litre a minute
        }
        setFuel(fuel() - (float) (litres / cap));
        if (fuel() <= 0 && engineOn()) {
            entityData.set(ENGINE, false);
            server.playSound(null, getX(), getY(), getZ(), CarSounds.STALL, SoundSource.NEUTRAL, 1f, 0.9f);
            if (getControllingPassenger() instanceof Player player) {
                player.sendOverlayMessage(Component.literal("The engine coughs and dies: out of fuel."));
            }
        }
    }

    /** The dash's fuel gauge, as the driver reads it. */
    public String gauge() {
        int eighths = Math.round(fuel() * 8);
        return "E " + "\u258c".repeat(eighths) + "\u00b7".repeat(8 - eighths) + " F";
    }

    public boolean battery() {
        return entityData.get(BATTERY);
    }

    public void setBattery(boolean ok) {
        entityData.set(BATTERY, ok);
    }

    /** How sound the airframe is, percent; it won't start below {@link AirReadiness#AIRWORTHY}. */
    public int condition() {
        return entityData.get(CONDITION);
    }

    public void setCondition(int condition) {
        entityData.set(CONDITION, Math.clamp(condition, 0, 100));
    }

    /** The pilot put it down without breaking it: experience for a clean landing, wear for a heavy one. */
    private void landed(net.minecraft.server.level.ServerPlayer pilot, float sink) {
        if (flightTicks < 100) {
            return;     // a hop or a bounce, not a flight
        }
        flightTicks = 0;
        float limit = landingLimit(flight().kind, AirReadiness.skill(pilot));
        if (sink > 0.75f * limit) {
            setCondition(condition() - Math.round(10 * sink / limit));
            pilot.sendOverlayMessage(Component.literal("Heavy landing. Condition " + condition() + "%."));
            com.zomboidcraft.skills.Skills.addXp(pilot, com.zomboidcraft.skills.Skill.PILOTING, 5);
        } else {
            com.zomboidcraft.skills.Skills.addXp(pilot, com.zomboidcraft.skills.Skill.PILOTING, sink < 2.5f ? 30 : 18);
        }
    }

    /** The fastest it can come down onto its wheels (m/s) without breaking up, for a pilot of this skill (0 to 1). */
    public static float landingLimit(Flight.Kind kind, float skill) {
        return (kind == Flight.Kind.JET ? 7 : 6) + 3 * skill;
    }

    /** Torn apart in a crash: drawn without the pieces that flew off. */
    public boolean broken() {
        return entityData.get(BROKEN);
    }

    public boolean crashed() {
        return crashed;
    }

    @Override
    public boolean isFlyingVehicle() {
        return aircraft();
    }

    /** One tick of flight on whichever side moves it: the controls, the physics, then the move and what it hits. */
    private void fly(Flight.Controls c) {
        CarModels.Model m = CarModels.get(model());
        if (m == null) {
            return;
        }
        Flight f = flight();
        float top = entityData.get(AIR_TOP);
        if (top > 0) {
            f.top = top;
        }
        boolean on = engineOn() && !crashed;
        f.catapult = f.kind == Flight.Kind.JET && on && f.grounded && f.throttle > 0.8f && f.speed() < 1.45f * f.stallSpeed()
                && onCatapult() ? 14 : 0;
        if (f.catapult > 0 && !launching && level().isClientSide() && getControllingPassenger() instanceof Player pilot) {
            pilot.sendOverlayMessage(Component.literal("Catapult!"));
        }
        launching = f.catapult > 0;
        Vec3 move = f.kind == Flight.Kind.JET ? f.tickJet(c, on) : f.tickHeli(c, on, clearance(8));
        double vyBefore = f.vel.y, speedBefore = f.speed();
        boolean wasGround = f.grounded;
        Vec3 before = position();
        if (wasGround) {
            // rolling: tow tractors, chocks, bomb skids and the like are knocked aside rather than stopping it dead
            int knocked = plough(getBoundingBox().expandTowards(move.x, 0, move.z));
            if (knocked > 0) {
                f.vel = f.vel.scale(Math.pow(0.97, knocked));
                move = f.vel.scale(Flight.DT);
            }
        }
        move(MoverType.SELF, move);
        Vec3 went = position().subtract(before);
        f.grounded = onGround();
        setYRot(f.yaw());
        setXRot(-f.noseUp());
        float violence = 0;
        // in the air any hit is a crash; on the wheels only running into a wall fast is
        double hard = wasGround ? (f.kind == Flight.Kind.JET ? Math.max(40, 0.45 * f.top) : 15) : f.kind == Flight.Kind.JET ? 14 : 9;
        if (horizontalCollision && speedBefore > hard) {
            violence = (float) speedBefore / f.top;
        }
        touchedDown = 0;
        if (f.grounded && !wasGround && vyBefore < -0.5) {
            float limit = landingLimit(f.kind, f.skill);
            float bank = Math.abs(f.bank()), nose = f.noseUp();
            if (-vyBefore > limit || bank > 30 || f.kind == Flight.Kind.JET && nose < -10 || f.kind == Flight.Kind.HELI && Math.abs(nose) > 30) {
                violence = Math.max(violence, (float) Math.min(1, -vyBefore / 20 + 0.3));
            } else {
                touchedDown = (float) -vyBefore;
            }
        }
        if (f.kind == Flight.Kind.HELI && f.rpm > 0.3f && rotorStrike(m)) {
            violence = Math.max(violence, 0.5f);
        }
        if (horizontalCollision) {
            f.vel = new Vec3(went.x / Flight.DT, f.vel.y, went.z / Flight.DT);
        }
        if (verticalCollision && (f.grounded ? f.vel.y < 0 : f.vel.y > 0)) {
            f.vel = new Vec3(f.vel.x, 0, f.vel.z);
        }
        setDeltaMovement(went);
        if (level().isClientSide()) {
            Quaternionf q = f.q;
            int flags = violence > 0 ? AirNet.CRASHED : touchedDown > 0 ? AirNet.LANDED : 0;
            pilotReport.accept(new AirNet.State(q.x, q.y, q.z, q.w, f.power, f.rpm, f.throttle, flags, violence > 0 ? violence : touchedDown));
        } else {
            entityData.set(AIR_ATTITUDE, new Quaternionf(f.q));
            entityData.set(AIR_RPM, f.rpm);
            entityData.set(AIR_POWER, f.power);
            if (violence > 0 && level() instanceof ServerLevel server) {
                crash(server, violence);
            }
            // come to rest: park it, hull and all
            if (airborne && f.grounded && f.vel.lengthSqr() < 0.05 && !isVehicle()) {
                airborne = false;
                f.vel = Vec3.ZERO;
                setDeltaMovement(Vec3.ZERO);
                parkPending = true;
            }
        }
    }

    /** Whether a jet's wheels are on a carrier's catapult track (the track or its shuttle under its middle). */
    private boolean onCatapult() {
        net.minecraft.core.BlockPos at = blockPosition();
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                for (int dy = -1; dy <= 0; dy++) {
                    String id = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(
                            level().getBlockState(at.offset(dx, dy, dz)).getBlock()).getPath();
                    if (id.equals("navy_catapult") || id.equals("navy_shuttle")) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Knocks aside the loose things an aircraft rolling on its wheels runs into inside box: anything one
     * block high standing on the ground it rolls on (deck tractors, chocks, bomb skids, crates), but not
     * walls, the ground itself, parked vehicles or a catapult's shuttle. The pilot's game clears them at
     * once so the aircraft rolls on; the server breaks them for everyone. Returns how many there were.
     */
    int plough(AABB box) {
        int y = Mth.floor(getY() + 0.05), n = 0;
        net.minecraft.core.BlockPos.MutableBlockPos pos = new net.minecraft.core.BlockPos.MutableBlockPos();
        for (int x = Mth.floor(box.minX); x <= Mth.floor(box.maxX); x++) {
            for (int z = Mth.floor(box.minZ); z <= Mth.floor(box.maxZ); z++) {
                pos.set(x, y, z);
                net.minecraft.world.level.block.state.BlockState state = level().getBlockState(pos);
                if (state.isAir() || state.is(Cars.HULL) || state.getCollisionShape(level(), pos).isEmpty()
                        || state.getDestroySpeed(level(), pos) < 0) {
                    continue;
                }
                net.minecraft.resources.Identifier id = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock());
                boolean ours = id.getNamespace().equals(ZomboidCraft.MOD_ID);
                if (id.getPath().equals("navy_shuttle") || !ours && state.isCollisionShapeFullBlock(level(), pos)) {
                    continue;
                }
                net.minecraft.core.BlockPos up = pos.above();
                if (!level().getBlockState(up).getCollisionShape(level(), up).isEmpty()) {
                    continue;   // taller than one block: a wall, not clutter
                }
                if (level() instanceof ServerLevel server) {
                    server.destroyBlock(pos, false, this);
                } else {
                    level().removeBlock(pos, false);
                }
                n++;
            }
        }
        return n;
    }

    /**
     * The server checks each move the pilot's game sends by making it itself: an aircraft on its wheels
     * knocks aside what the pilot's game already cleared first, so the move isn't refused.
     */
    @Override
    public void move(MoverType type, Vec3 delta) {
        if (type == MoverType.PLAYER && aircraft() && flight().grounded && level() instanceof ServerLevel) {
            plough(getBoundingBox().expandTowards(delta.x, 0, delta.z));
        }
        super.move(type, delta);
    }

    /** How far the ground is below the aircraft, up to max. */
    private float clearance(int max) {
        net.minecraft.core.BlockPos.MutableBlockPos pos = blockPosition().mutable();
        for (int i = 0; i < max; i++, pos.move(net.minecraft.core.Direction.DOWN)) {
            if (!level().getBlockState(pos).getCollisionShape(level(), pos).isEmpty()) {
                return (float) Math.max(0, getY() - (pos.getY() + 1));
            }
        }
        return max;
    }

    /** Whether the main rotor's tips are cutting into anything solid. */
    private boolean rotorStrike(CarModels.Model m) {
        Flight f = flight();
        float radius = m.size[0] / 2 * 0.95f, hub = m.size[1] * 0.78f, pivot = pivot();
        for (int k = 0; k < 8; k++) {
            double a = Math.PI * 2 * k / 8;
            org.joml.Vector3f tip = f.q.transform(new org.joml.Vector3f((float) (Math.cos(a) * radius), hub - pivot, (float) (Math.sin(a) * radius) + 1.8f));
            net.minecraft.core.BlockPos pos = net.minecraft.core.BlockPos.containing(getX() + tip.x, getY() + pivot + tip.y, getZ() + tip.z);
            net.minecraft.world.level.block.state.BlockState state = level().getBlockState(pos);
            if (!state.is(Cars.HULL) && !state.getCollisionShape(level(), pos).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /** Someone else's aircraft (client side): turned as the server last said, eased toward it. */
    private void followFlight() {
        Flight f = flight();
        f.qO.set(f.q);
        f.q.slerp(new Quaternionf(entityData.get(AIR_ATTITUDE)), 0.45f);
        f.rpm = entityData.get(AIR_RPM);
        f.power = entityData.get(AIR_POWER);
        f.vel = new Vec3(getX() - xo, getY() - yo, getZ() - zo).scale(1 / Flight.DT);
    }

    /** The server's copy of what the pilot's game reports each tick. */
    void pilotState(AirNet.State state) {
        Flight f = flight();
        f.qO.set(f.q);
        f.q.set(state.attitude());
        f.power = state.power();
        f.rpm = state.rpm();
        f.throttle = state.throttle();
        entityData.set(AIR_ATTITUDE, new Quaternionf(f.q));
        entityData.set(AIR_RPM, f.rpm);
        entityData.set(AIR_POWER, f.power);
        if ((state.flags() & AirNet.CRASHED) != 0 && level() instanceof ServerLevel server) {
            crash(server, Math.clamp(state.violence(), 0.05f, 1f));
        } else if ((state.flags() & AirNet.LANDED) != 0 && getControllingPassenger() instanceof net.minecraft.server.level.ServerPlayer pilot) {
            landed(pilot, state.violence());
        }
    }

    /**
     * The server's side of an aircraft: the top speed it allows, the engine's noise, keeping its speed in
     * step with how far the pilot moves it (so it flies on if they bail out), and the start-up hint.
     */
    private void flown(ServerLevel server, Vec3 moved) {
        Flight f = flight();
        float top = (float) ((f.kind == Flight.Kind.JET ? com.zomboidcraft.ZcConfig.get().world.jetTopSpeed
                : com.zomboidcraft.ZcConfig.get().world.heliTopSpeed) / 3.6);
        if (entityData.get(AIR_TOP) != top) {
            entityData.set(AIR_TOP, top);
        }
        f.top = top;
        if (getControllingPassenger() != null) {
            if (f.grounded && moved.horizontalDistanceSqr() > 1e-4) {
                CarModels.Model m = CarModels.get(model());
                if (m != null && getControllingPassenger() instanceof LivingEntity pilot) {
                    strike(server, pilot, m, moved.horizontalDistance());
                }
            }
            f.vel = moved.scale(1 / Flight.DT);
            f.grounded = onGround();
            airborne = !f.grounded || f.vel.lengthSqr() > 0.05;
            if (!f.grounded) {
                flightTicks++;
                if (flightTicks % 400 == 0 && getControllingPassenger() instanceof net.minecraft.server.level.ServerPlayer pilot) {
                    com.zomboidcraft.skills.Skills.addXp(pilot, com.zomboidcraft.skills.Skill.PILOTING, 4);
                }
            }
        }
        if (engineOn() && com.zomboidcraft.ZcConfig.get().world.aircraftService) {
            // a full tank lasts about 25 minutes cruising in a jet, 30 in the helicopter; the afterburner drinks
            float burn = f.kind == Flight.Kind.JET
                    ? (0.2f * f.rpm + 0.8f * (float) Math.pow(f.thrustShare(), 1.3) + (f.afterburner() ? 1.2f : 0)) * 6.6e-5f
                    : (0.3f * f.rpm + 0.7f * f.power) * 4.6e-5f;
            setFuel(fuel() - burn);
            if (fuel() <= 0) {
                entityData.set(ENGINE, false);
                if (getControllingPassenger() instanceof Player pilot) {
                    pilot.sendOverlayMessage(Component.literal("Flameout: the tanks are dry."));
                }
            }
        }
        if (engineOn() && tickCount % 10 == 0) {
            // like Zomboid's helicopter: every zombie for miles comes to look
            Noise.emitEngine(server, position(), f.kind == Flight.Kind.HELI ? 112 : 72 + 72 * f.power);
        }
    }

    /** Aircraft engines don't start by themselves: the pilot runs the start-up ({@link #toggleAirEngine}). */
    private void airEngine(ServerLevel server, Entity driver) {
        if (driver != airPilot) {
            airPilot = driver;
            if (driver instanceof Player player) {
                AirReadiness.Manual manual = AirReadiness.manualFor(flight().kind);
                String status = "Fuel " + Math.round(fuel() * 100) + "%, " + (battery() ? "battery good" : "battery flat") + ", condition "
                        + condition() + "%." + (AirReadiness.knows(player, manual) ? "" : " You haven't read the " + manual.title
                        + ": you can only taxi.");
                player.sendOverlayMessage(Component.literal(!engineRuns() ? "It's wrecked. It won't fly again."
                        : engineOn() ? flightHelp() : "Press the engine key to run the start-up. " + status));
            }
        }
    }

    private String flightHelp() {
        return helicopter()
                ? "Space climb, Ctrl sink, W/S nose down/up, A/D bank, mouse turns, H level lock (hover), L searchlight. Alt looks round."
                : "Mouse flies, W/S throttle, A/D roll, Space brakes, H flight assist on/off. Alt looks round.";
    }

    /** The pilot starts or shuts down the engines. */
    public void toggleAirEngine(Player player) {
        if (!(level() instanceof ServerLevel server) || getControllingPassenger() != player) {
            return;
        }
        if (!engineRuns() || crashed) {
            player.sendOverlayMessage(Component.literal("It's wrecked. It won't start."));
            return;
        }
        boolean on = !engineOn();
        String why = on ? AirReadiness.cantStart(this, player) : null;
        if (why != null) {
            player.sendOverlayMessage(Component.literal(why));
            server.playSound(null, getX(), getY(), getZ(), SoundEvents.LEVER_CLICK, SoundSource.NEUTRAL, 0.6f, 1.2f);
            return;
        }
        if (on && player.isCreative()) {
            readyToFly();   // a creative player's ground crew: fuelled, battery in, repaired
        }
        entityData.set(ENGINE, on);
        // the start-up is played by each client (AirSound), which can cut it short when it's switched off
        if (!on) {
            server.playSound(null, getX(), getY(), getZ(), helicopter() ? AirSounds.HELI_SHUTDOWN : AirSounds.JET_SHUTDOWN,
                    SoundSource.NEUTRAL, 1.5f, 1f);
        }
        player.sendOverlayMessage(Component.literal(on ? (helicopter() ? "Rotors coming up to speed. " : "Engines spooling up. ") + flightHelp()
                : "Shutting down."));
        if (on) {
            Noise.emitEngine(server, position(), 48);
        }
    }

    /**
     * It's hit something too hard: a fireball, everyone thrown out, and the airframe left a wreck
     * (burnt out if it came in fast). violence: 0 (barely) to 1 (flat out).
     */
    private void crash(ServerLevel server, float violence) {
        if (crashed || !engineRuns()) {
            return;     // a wreck coming down again is no new crash
        }
        crashed = true;
        CarModels.Model m = CarModels.get(model());
        double h = m == null ? 2 : m.size[1] / 2;
        server.playSound(null, getX(), getY(), getZ(), AirSounds.CRASH, SoundSource.NEUTRAL, 3f, 0.9f + random.nextFloat() * 0.2f);
        server.explode(this, getX(), getY() + h, getZ(), 3f + 3f * violence, false, Level.ExplosionInteraction.NONE);
        for (Entity rider : List.copyOf(getPassengers())) {
            rider.stopRiding();
            if (rider instanceof LivingEntity living) {
                // nobody walks away from an aircraft crash
                living.hurtServer(server, damageSources().explosion(this, this), 1000);
                if (living.isAlive()) {
                    living.kill(server);
                }
            }
        }
        entityData.set(ENGINE, false);
        Noise.emitEngine(server, position(), 128);
        Flight f = flight();
        Quaternionf q = new Quaternionf(f.q);
        float rpm = f.rpm;
        Vec3 vel = f.vel.scale(0.3);
        // it comes apart: the wings, nose and tail tumble off burning and the rest is a burnt-out shell
        AirDebris.throwFrom(server, this, q, vel.scale(1 / 0.3 / 20), "burnt");
        setLook(model(), "burnt");
        entityData.set(BROKEN, true);
        f = flight();
        f.q.set(q);
        f.rpm = rpm;
        f.vel = vel;
        airborne = true;
        crashed = false;    // the wreck can't start again (engineRuns), but it still falls and settles
    }
}
