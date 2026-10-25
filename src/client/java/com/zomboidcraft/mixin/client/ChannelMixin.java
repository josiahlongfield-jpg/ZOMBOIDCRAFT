package com.zomboidcraft.mixin.client;

import com.mojang.blaze3d.audio.Channel;
import com.zomboidcraft.client.sound.Muffler;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Muffles each sound by the walls between it and you (see {@link Muffler}): worked out as it starts, then a few
 * times a second for sounds that move, like a zombie groaning as it shuffles round a corner.
 */
@Mixin(Channel.class)
public abstract class ChannelMixin {
    @Shadow @Final private int source;

    @Unique private Vec3 zomboidcraft$at;
    @Unique private boolean zomboidcraft$relative, zomboidcraft$everywhere, zomboidcraft$started;
    @Unique private int zomboidcraft$moves;
    @Unique private float zomboidcraft$walls;

    @Inject(method = "setSelfPosition", at = @At("TAIL"))
    private void zomboidcraft$moved(Vec3 at, CallbackInfo ci) {
        zomboidcraft$at = at;
        // moving sounds are re-placed every tick; re-trace every fourth time and ease towards the new value
        if (zomboidcraft$started && ++zomboidcraft$moves % 4 == 0 && zomboidcraft$traced()) {
            float target = Muffler.walls(at);
            zomboidcraft$walls += (target - zomboidcraft$walls) * 0.5f;
            Muffler.apply(source, zomboidcraft$walls);
        }
    }

    @Inject(method = "setRelative", at = @At("TAIL"))
    private void zomboidcraft$relative(boolean relative, CallbackInfo ci) {
        zomboidcraft$relative = relative;
    }

    @Inject(method = "disableAttenuation", at = @At("TAIL"))
    private void zomboidcraft$everywhere(CallbackInfo ci) {
        zomboidcraft$everywhere = true;
    }

    @Inject(method = "linearAttenuation", at = @At("TAIL"))
    private void zomboidcraft$attenuated(float distance, CallbackInfo ci) {
        zomboidcraft$everywhere = false;
    }

    @Inject(method = "play", at = @At("HEAD"))
    private void zomboidcraft$start(CallbackInfo ci) {
        zomboidcraft$started = true;
        zomboidcraft$walls = zomboidcraft$traced() ? Muffler.walls(zomboidcraft$at) : 0;
        Muffler.apply(source, zomboidcraft$walls);
    }

    /** Menu clicks, music and the like play at your ears or everywhere at once, so walls don't touch them. */
    @Unique
    private boolean zomboidcraft$traced() {
        return zomboidcraft$at != null && !zomboidcraft$relative && !zomboidcraft$everywhere;
    }
}
