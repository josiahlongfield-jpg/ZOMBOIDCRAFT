package com.zomboidcraft.mixin.client;

import com.zomboidcraft.client.reach.ReachFar;
import net.minecraft.client.Camera;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The Reach world's far drawing pass (see {@link ReachFar}): the camera's perspective goes out past the whole map,
 * and so does the frustum the Reach meshes are culled against.
 */
@Mixin(Camera.class)
public abstract class ReachFarPlaneMixin {
    @Shadow private float depthFar;

    /** Camera.update sets the perspective as (near, far, fov, width, height). */
    @ModifyArg(method = "update", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/Camera;setupPerspective(FFFFF)V"), index = 1)
    private float zomboidcraft$reachFar(float far) {
        return ReachFar.far(far);
    }

    /**
     * And the near plane a little further out (0.05 to 0.2 m), which keeps the depth buffer sharp enough to tell
     * far buildings and the coast apart kilometres away. Hands and held guns have their own projection.
     */
    @ModifyArg(method = "update", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/Camera;setupPerspective(FFFFF)V"), index = 0)
    private float zomboidcraft$reachNear(float near) {
        return ReachFar.near(near);
    }

    @Inject(method = "createProjectionMatrixForCulling", at = @At("HEAD"))
    private void zomboidcraft$reachCullFar(CallbackInfoReturnable<Matrix4f> cir) {
        depthFar = ReachFar.far(depthFar);
    }
}
