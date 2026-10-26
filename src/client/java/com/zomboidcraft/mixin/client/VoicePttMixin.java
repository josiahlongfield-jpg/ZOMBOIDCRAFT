package com.zomboidcraft.mixin.client;

import com.zomboidcraft.client.gear.RadioClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Holding the radio's talk key counts as holding the voice chat mod's push-to-talk key, so the microphone opens while
 * you talk on a radio. Applies only when the voice mod is installed.
 */
@Pseudo
@Mixin(targets = "de.maxhenkel.voicechat.voice.client.PTTKeyHandler", remap = false)
public abstract class VoicePttMixin {
    @Inject(method = {"isPTTDown()Z", "isAnyDown()Z"}, at = @At("HEAD"), cancellable = true, require = 0)
    private void zomboidcraft$radioTalk(CallbackInfoReturnable<Boolean> cir) {
        if (RadioClient.transmitting) {
            cir.setReturnValue(true);
        }
    }
}
