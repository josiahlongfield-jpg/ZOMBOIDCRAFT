package com.zomboidcraft.mixin.client;

import com.zomboidcraft.client.camera.CameraRig;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Camera.class)
public abstract class CameraMixin {
    @Shadow @Final private Quaternionf rotation;
    @Shadow @Final private Vector3f forwards;
    @Shadow @Final private Vector3f up;
    @Shadow @Final private Vector3f left;
    @Shadow private float xRot;
    @Shadow private float yRot;
    @Shadow private int matrixPropertiesDirty;

    @Shadow
    protected abstract void setPosition(Vec3 pos);

    @Shadow
    public abstract Entity entity();

    /** A mod camera rig (an aircraft's cockpit or chase view) gets the last word on where the camera is and how it's turned. */
    @Inject(method = "alignWithEntity", at = @At("TAIL"))
    private void zomboidcraft$rig(float partialTick, CallbackInfo ci) {
        Entity entity = entity();
        if (entity == null) {
            return;
        }
        CameraRig.Out out = CameraRig.apply((Camera) (Object) this, entity, partialTick);
        if (out == null) {
            zomboidcraft$bob(entity, partialTick);
            return;
        }
        rotation.set(out.rotation);
        new Vector3f(0, 0, -1).rotate(rotation, forwards);
        new Vector3f(0, 1, 0).rotate(rotation, up);
        new Vector3f(-1, 0, 0).rotate(rotation, left);
        xRot = (float) Math.toDegrees(-Math.asin(Math.max(-1, Math.min(1, forwards.y))));
        yRot = (float) Math.toDegrees(Math.atan2(-forwards.x, forwards.z));
        matrixPropertiesDirty |= 3;
        if (out.position != null) {
            setPosition(out.position);
        }
    }

    /**
     * In first person with a Tarkov gun, blade or grenade in hand, the eye bobs as Tarkov's player's head does walking,
     * running and sprinting (client/operator/TarkovMotion); the hands are drawn from the eye and keep to it.
     */
    @org.spongepowered.asm.mixin.Unique
    private void zomboidcraft$bob(Entity entity, float partialTick) {
        com.zomboidcraft.client.operator.TarkovMotion.update(partialTick);
        Vector3f shift = com.zomboidcraft.client.operator.TarkovMotion.cameraShift();
        Quaternionf turn = com.zomboidcraft.client.operator.TarkovMotion.cameraTurn();
        if (shift.lengthSquared() < 1e-10f && Math.abs(turn.w) > 0.999999f) {
            return;
        }
        Vector3f moved = new Vector3f(shift).rotate(rotation);
        rotation.mul(turn);
        new Vector3f(0, 0, -1).rotate(rotation, forwards);
        new Vector3f(0, 1, 0).rotate(rotation, up);
        new Vector3f(-1, 0, 0).rotate(rotation, left);
        xRot = (float) Math.toDegrees(-Math.asin(Math.max(-1, Math.min(1, forwards.y))));
        yRot = (float) Math.toDegrees(Math.atan2(-forwards.x, forwards.z));
        matrixPropertiesDirty |= 3;
        setPosition(entity.getEyePosition(partialTick).add(moved.x, moved.y, moved.z));
    }

    /** Through a magnified scope, the lens's turns are drawn zoomed exactly the scope's power (ScopeView). */
    @Inject(method = "calculateFov", at = @At("RETURN"), cancellable = true)
    private void zomboidcraft$scopeFov(float partialTick, CallbackInfoReturnable<Float> cir) {
        cir.setReturnValue(com.zomboidcraft.client.combat.ScopeView.fov(cir.getReturnValueF()));
    }
}
