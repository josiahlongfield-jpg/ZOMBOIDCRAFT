package com.zomboidcraft.world.cars;

import com.zomboidcraft.ZomboidCraft;
import com.zomboidcraft.skills.Skill;
import com.zomboidcraft.skills.Skills;
import com.zomboidcraft.survival.TimedActions;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
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
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * The auto shop's floor tools, each for its one job (Josiah, 2026-10-31):
 * <ul>
 * <li>the trolley jack rolls under a car's corner and lifts it, so that wheel's tyre, rim and brakes can be
 * worked on without a hoist;</li>
 * <li>the engine crane, wheeled up to a car's front, is what takes an engine out or drops one in;</li>
 * <li>the tyre machine breaks a worn tyre off its bead, patches and reseats it (Mechanics 2);</li>
 * <li>the parts washer cleans a worn part back up a little (Mechanics 4).</li>
 * </ul>
 * The interact key works each one; the jack and the crane roll to the car nearest the one using them.
 */
public class ShopTool extends Entity {
    public enum Kind {
        JACK("Trolley jack"), CRANE("Engine crane"), TYRE_MACHINE("Tyre machine"), WASHER("Parts washer");

        public final String title;

        Kind(String title) {
            this.title = title;
        }

        public static Kind of(String name) {
            for (Kind k : values()) {
                if (k.name().equalsIgnoreCase(name)) {
                    return k;
                }
            }
            return null;
        }
    }

    private static final EntityDataAccessor<Integer> KIND = SynchedEntityData.defineId(ShopTool.class, EntityDataSerializers.INT);
    /** The jack: how high its saddle stands above the floor. */
    private static final EntityDataAccessor<Float> SADDLE = SynchedEntityData.defineId(ShopTool.class, EntityDataSerializers.FLOAT);
    /** How high a jack lifts a corner off the floor: enough to spin the wheel free. */
    public static final float JACK_LIFT = 0.3f;
    /** How close to its car a crane must stand to reach in at the front. */
    public static final double CRANE_REACH = 3.2;
    /** The tyre machine and washer: the best a reseated tyre or a washed part comes back to, and by how much. */
    private static final int TYRE_BEST = 90, TYRE_GAIN = 35, WASH_BEST = 85, WASH_GAIN = 15;
    private static final String RESEATED = "reseated", WASHED = "washed";

    private UUID carId;
    private int corner = -1;
    private boolean raising;
    private float lift;
    /** Client: last tick's saddle height, for smooth drawing. */
    public float saddleO;

    public ShopTool(EntityType<? extends ShopTool> type, Level level) {
        super(type, level);
        setNoGravity(true);
        noPhysics = true;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(KIND, 0);
        builder.define(SADDLE, 0.14f);
    }

    public Kind kind() {
        int k = entityData.get(KIND);
        return Kind.values()[k >= 0 && k < Kind.values().length ? k : 0];
    }

    public void setKind(Kind kind) {
        entityData.set(KIND, kind.ordinal());
    }

    public float saddle() {
        return entityData.get(SADDLE);
    }

    /** Where the tool is worked from, in the world: the jack's handle, the crane's push bar, the machines' fronts. */
    public Vec3 handle() {
        Vec3 local = switch (kind()) {
            case JACK -> new Vec3(0, 0.5, -0.75);
            case CRANE -> new Vec3(0, 1.0, -0.55);
            case TYRE_MACHINE -> new Vec3(0, 0.95, 0.45);
            case WASHER -> new Vec3(0, 0.95, 0.4);
        };
        return position().add(local.yRot((float) -Math.toRadians(getYRot())));
    }

    // ---------------------------------------------------------------- working it

    public record Press(int tool) implements CustomPacketPayload {
        public static final Type<Press> TYPE = new Type<>(ZomboidCraft.id("shop_tool_use"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Press> CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, Press::tool, Press::new);

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
                if (player.level().getEntity(payload.tool()) instanceof ShopTool tool && !player.isSpectator()
                        && tool.handle().distanceToSqr(player.getEyePosition()) < 3.5 * 3.5) {
                    tool.use(player);
                }
            });
        });
    }

    private static void say(ServerPlayer player, String what) {
        player.sendOverlayMessage(Component.literal(what));
    }

    public void use(ServerPlayer player) {
        switch (kind()) {
            case JACK -> jack(player);
            case CRANE -> crane(player);
            case TYRE_MACHINE -> bench(player, true);
            case WASHER -> bench(player, false);
        }
    }

    /** The car nearest the player that the tool could work on, within reach of both. */
    private CarProp nearCar(ServerPlayer player, double reach) {
        CarProp best = null;
        double bestD = Double.MAX_VALUE;
        for (CarProp c : level().getEntitiesOfClass(CarProp.class, getBoundingBox().inflate(reach + 4, 2, reach + 4))) {
            CarModels.Model m = CarModels.get(c.model());
            double d = c.distanceToSqr(player);
            if (m != null && CarParts.applies(m) && !c.isVehicle() && c.distanceTo(this) < reach + m.size[2] / 2 && d < bestD) {
                best = c;
                bestD = d;
            }
        }
        return best;
    }

    /** The trolley jack: rolls under the corner of the nearest car the player stands by and pumps it up; again lets it down. */
    private void jack(ServerPlayer player) {
        CarProp held = car();
        if (held != null) {
            raising = false;
            level().playSound(null, getX(), getY(), getZ(), SoundEvents.PISTON_CONTRACT, SoundSource.BLOCKS, 0.5f, 1.4f);
            say(player, "You open the release valve: the corner sinks back onto its wheel.");
            return;
        }
        CarProp car = nearCar(player, 2.5);
        if (car == null) {
            say(player, "Roll it up to a car: stand by the wheel you want off the ground.");
            return;
        }
        if (car.hoisted() || car.jackCorner() >= 0) {
            say(player, car.hoisted() ? "That car's on the lift." : "That car's already jacked up.");
            return;
        }
        CarModels.Model m = CarModels.get(car.model());
        // the corner nearest the player: left is +x in the car's frame, the nose +z
        Vec3 at = player.position().subtract(car.position()).yRot((float) Math.toRadians(car.getYRot()));
        int c = (at.z >= 0 ? 0 : 2) + (at.x >= 0 ? 0 : 1);
        float sx = c % 2 == 0 ? 1 : -1, sz = c < 2 ? 1 : -1;
        float wb = m.wheelbase > 0.5f ? m.wheelbase : m.size[2] * 0.6f;
        // the jacking point on the sill just inside the wheel; the jack lies across the car, handle out
        Vec3 point = new Vec3(sx * (m.size[0] / 2 - 0.3), 0, sz * Math.max(0.5, wb / 2 - 0.55)).yRot((float) -Math.toRadians(car.getYRot()));
        Vec3 out = new Vec3(sx * 0.45, 0, 0).yRot((float) -Math.toRadians(car.getYRot()));
        Vec3 foot = car.position().add(point).add(out);
        snapTo(foot.x, car.getY(), foot.z, car.getYRot() + (sx > 0 ? 90 : -90), 0);
        carId = car.getUUID();
        corner = c;
        raising = true;
        lift = 0;
        level().playSound(null, getX(), getY(), getZ(), SoundEvents.CHAIN_PLACE, SoundSource.BLOCKS, 0.7f, 1.2f);
        say(player, "You roll the jack under the " + CarParts.corner(c).toLowerCase(java.util.Locale.ROOT) + " corner and pump it up.");
    }

    /** The engine crane: wheels it up to the front of the nearest car, where it can lift an engine out or in. */
    private void crane(ServerPlayer player) {
        CarProp car = nearCar(player, 3.5);
        if (car == null) {
            say(player, "Wheel it up to a car's front: it's what lifts an engine out, or drops one in.");
            return;
        }
        CarModels.Model m = CarModels.get(car.model());
        Vec3 front = car.position().add(new Vec3(0, 0, m.size[2] / 2 + 0.55).yRot((float) -Math.toRadians(car.getYRot())));
        snapTo(front.x, car.getY() - car.liftHeight(), front.z, car.getYRot() + 180, 0);
        level().playSound(null, getX(), getY(), getZ(), SoundEvents.CHAIN_PLACE, SoundSource.BLOCKS, 0.7f, 0.8f);
        say(player, "The crane's at the " + car.model().replace('_', ' ') + "'s front, its boom over the engine bay.");
    }

    /** Whether a crane stands at this car's front. */
    public static boolean craneAt(CarProp car) {
        CarModels.Model m = CarModels.get(car.model());
        if (m == null) {
            return false;
        }
        Vec3 front = car.position().add(new Vec3(0, 0, m.size[2] / 2).yRot((float) -Math.toRadians(car.getYRot())));
        for (ShopTool t : car.level().getEntitiesOfClass(ShopTool.class, car.getBoundingBox().inflate(4, 3, 4))) {
            if (t.kind() == Kind.CRANE && Math.hypot(t.getX() - front.x, t.getZ() - front.z) < CRANE_REACH - 2) {
                return true;
            }
        }
        return false;
    }

    /** The tyre machine (a tyre) or the parts washer (anything else), with the part in the player's hand. */
    private void bench(ServerPlayer player, boolean tyres) {
        ItemStack stack = player.getItemInHand(InteractionHand.MAIN_HAND);
        Mechanics.Held h = Mechanics.held(stack);
        CarParts.Part part = h == null ? null : partOf(h.id());
        String flag = tyres ? RESEATED : WASHED;
        int need = tyres ? 2 : 4, best = tyres ? TYRE_BEST : WASH_BEST;
        String why = part == null ? (tyres ? "Hold a car tyre to work on it." : "Hold a worn car part to clean it.")
                : tyres != (part == CarParts.Part.TIRE) ? (tyres ? "Only tyres go on the tyre machine." : "Tyres go on the tyre machine, not in here.")
                : part == CarParts.Part.OIL || part == CarParts.Part.BRAKE_FLUID ? "There's nothing to clean in a can of fluid."
                : Skills.level(player, Skill.MECHANICS) < need ? "Needs Mechanics " + need + "."
                : marked(stack, flag) ? (tyres ? "It's been patched once already: it won't hold another." : "It's as clean as it'll get.")
                : h.condition() >= best ? "It's in better shape than this would leave it."
                : null;
        if (why != null) {
            say(player, why);
            return;
        }
        String label = tyres ? "Patching the tyre" : "Washing the part";
        TimedActions.start(player, "shop_" + getId(), label, 20 * (tyres ? 10 : 12), p -> {
            ItemStack now = p.getItemInHand(InteractionHand.MAIN_HAND);
            Mechanics.Held again = Mechanics.held(now);
            if (now != stack || again == null || marked(now, flag)) {
                return;
            }
            int to = Math.min(best, again.condition() + (tyres ? TYRE_GAIN : WASH_GAIN));
            CustomData data = now.get(DataComponents.CUSTOM_DATA);
            CompoundTag tag = data == null ? new CompoundTag() : data.copyTag();
            tag.putString(Mechanics.PART_KEY, again.id());
            tag.putInt(Mechanics.CONDITION_KEY, to);
            tag.putInt(flag, 1);
            now.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
            Skills.addXp(p, Skill.MECHANICS, tyres ? 6 : 8);
            level().playSound(null, getX(), getY() + 1, getZ(), tyres ? SoundEvents.PISTON_EXTEND : SoundEvents.BUCKET_EMPTY,
                    SoundSource.BLOCKS, 0.6f, 1f);
            say(p, (tyres ? "Patched and reseated: " : "Cleaned up: ") + to + "%.");
        });
    }

    private static CarParts.Part partOf(String id) {
        for (CarParts.Part p : CarParts.Part.values()) {
            if (p.item.equals(id)) {
                return p;
            }
        }
        return null;
    }

    private static boolean marked(ItemStack stack, String flag) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data != null && data.copyTag().getIntOr(flag, 0) != 0;
    }

    private CarProp car() {
        if (carId == null || !(level() instanceof ServerLevel server)) {
            return null;
        }
        Entity e = server.getEntity(carId);
        return e instanceof CarProp c && !c.isRemoved() ? c : null;
    }

    @Override
    public void tick() {
        super.tick();
        saddleO = saddle();
        if (!(level() instanceof ServerLevel server) || kind() != Kind.JACK || carId == null) {
            return;
        }
        CarProp car = car();
        if (car == null || car.hoisted()) {
            if (car != null) {
                car.setJack(-1, 0);
            }
            carId = null;
            lift = 0;
            entityData.set(SADDLE, 0.14f);
            return;
        }
        float was = lift;
        lift = Mth.approach(lift, raising ? JACK_LIFT : 0f, raising ? 0.008f : 0.02f);
        if (lift != was && tickCount % 8 == 0) {
            server.playSound(null, getX(), getY(), getZ(), raising ? SoundEvents.PISTON_EXTEND : SoundEvents.PISTON_CONTRACT,
                    SoundSource.BLOCKS, 0.3f, 1.6f);
        }
        car.setJack(corner, lift);
        entityData.set(SADDLE, CarHoist.sill(CarModels.get(car.model())) + lift);
        if (!raising && lift <= 0) {
            car.setJack(-1, 0);
            carId = null;
            corner = -1;
            entityData.set(SADDLE, 0.14f);
        }
    }

    @Override
    public void remove(RemovalReason reason) {
        if (reason.shouldDestroy() && car() != null) {
            car().setJack(-1, 0);
        }
        super.remove(reason);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        Kind k = Kind.of(input.getStringOr("kind", "jack"));
        setKind(k == null ? Kind.JACK : k);
        String id = input.getStringOr("car", "");
        carId = id.isEmpty() ? null : UUID.fromString(id);
        corner = input.getIntOr("corner", -1);
        raising = input.getIntOr("raising", 0) != 0;
        lift = input.getFloatOr("lift", 0f);
        entityData.set(SADDLE, input.getFloatOr("saddle", 0.14f));
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        output.putString("kind", kind().name().toLowerCase(java.util.Locale.ROOT));
        output.putString("car", carId == null ? "" : carId.toString());
        output.putInt("corner", corner);
        output.putInt("raising", raising ? 1 : 0);
        output.putFloat("lift", lift);
        output.putFloat("saddle", saddle());
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
}
