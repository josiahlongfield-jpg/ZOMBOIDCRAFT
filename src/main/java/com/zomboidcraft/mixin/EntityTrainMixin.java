package com.zomboidcraft.mixin;

import com.zomboidcraft.world.trains.TrainEntity;
import com.zomboidcraft.world.trains.TrainPhysics;
import com.zomboidcraft.world.trains.TrainRider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Every entity can ride inside a moving train: carried with its car each tick, and stopped by the car's walls. */
@Mixin(Entity.class)
public abstract class EntityTrainMixin implements TrainRider {
    @Unique
    private TrainEntity zomboidcraft$train;
    @Unique
    private int zomboidcraft$car;
    @Unique
    private double zomboidcraft$trainS;

    @Override
    public TrainEntity zomboidcraft$train() {
        return zomboidcraft$train;
    }

    @Override
    public int zomboidcraft$car() {
        return zomboidcraft$car;
    }

    @Override
    public double zomboidcraft$trainS() {
        return zomboidcraft$trainS;
    }

    @Override
    public void zomboidcraft$ride(TrainEntity train, int car, double s) {
        zomboidcraft$train = train;
        zomboidcraft$car = car;
        zomboidcraft$trainS = s;
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void zomboidcraft$carryOnTrain(CallbackInfo ci) {
        TrainPhysics.carry((Entity) (Object) this);
    }

    @ModifyVariable(method = "collide", at = @At("HEAD"), argsOnly = true)
    private Vec3 zomboidcraft$trainWalls(Vec3 movement) {
        return TrainPhysics.clip((Entity) (Object) this, movement);
    }
}
