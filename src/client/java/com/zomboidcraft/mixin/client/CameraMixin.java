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
}
