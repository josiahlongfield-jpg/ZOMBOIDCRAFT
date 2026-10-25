package com.zomboidcraft.world.cars;

import com.zomboidcraft.ZomboidCraft;
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
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
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
 * A parked or wrecked car: an entity so the client can draw its 3D model. It never moves, can't
 * be hurt, and has no collision of its own; invisible {@link CarHull} blocks under the model make
 * it solid. Using it searches the boot (or the back of a van), rolled from a loot table the first
 * time anyone looks, like Zomboid: police, SWAT and ambulance vehicles carry better gear. A
 * burnt-out car's boot is empty. Removing it (/kill) clears its hull too.
 */
public class CarProp extends Entity {
    private static final EntityDataAccessor<String> MODEL = SynchedEntityData.defineId(CarProp.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> VARIANT = SynchedEntityData.defineId(CarProp.class, EntityDataSerializers.STRING);

    private final SimpleContainer boot = new SimpleContainer(27) {
        @Override
        public boolean stillValid(Player player) {
            return isAlive() && player.distanceToSqr(CarProp.this) < 64;
        }
    };
    private boolean searched;

    public CarProp(EntityType<? extends CarProp> type, Level level) {
        super(type, level);
        noPhysics = true;
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

    @Override
    public InteractionResult interact(Player player, InteractionHand hand, Vec3 location) {
        if (!(level() instanceof ServerLevel server)) {
            return InteractionResult.SUCCESS;
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
        server.playSound(null, getX(), getY(), getZ(), SoundEvents.CHEST_OPEN, SoundSource.BLOCKS, 0.5f, 0.6f);
        return InteractionResult.SUCCESS;
    }

    /** The click box covers the whole car, turned with it, so you can search it from any side. */
    @Override
    protected AABB makeBoundingBox(Vec3 pos) {
        CarModels.Model m = CarModels.get(model());
        if (m == null) {
            return super.makeBoundingBox(pos);
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
        // a parked car does nothing
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
        return distance < 160 * 160;
    }

    @Override
    public void remove(RemovalReason reason) {
        if (reason.shouldDestroy() && level() instanceof ServerLevel server) {
            Cars.clearHull(server, this);
        }
        super.remove(reason);
    }
}
