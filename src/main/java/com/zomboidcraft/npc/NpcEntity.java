package com.zomboidcraft.npc;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * A character drawn from a bought mesh (NpcRenderer, assets/zomboidcraft/npc/MODEL.znpc). For now they only stand where
 * they're put and turn to watch whoever comes near: the models aren't rigged yet, so they can't walk. Which model and which
 * look ("raw", as bought, or "toon", painted like the guns) are synced and saved.
 */
public class NpcEntity extends PathfinderMob {
    private static final EntityDataAccessor<String> MODEL = SynchedEntityData.defineId(NpcEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> LOOK = SynchedEntityData.defineId(NpcEntity.class, EntityDataSerializers.STRING);

    public NpcEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder attributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20)
                .add(Attributes.MOVEMENT_SPEED, 0.0);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new LookAtPlayerGoal(this, Player.class, 10f));
        goalSelector.addGoal(2, new RandomLookAroundGoal(this));
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(MODEL, Npcs.MODELS.get(0));
        builder.define(LOOK, "raw");
    }

    public String model() {
        return entityData.get(MODEL);
    }

    public String look() {
        return entityData.get(LOOK);
    }

    public void setModel(String model, String look) {
        entityData.set(MODEL, model);
        entityData.set(LOOK, look);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putString("npcModel", model());
        output.putString("npcLook", look());
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        setModel(input.getStringOr("npcModel", Npcs.MODELS.get(0)), input.getStringOr("npcLook", "raw"));
    }
}
