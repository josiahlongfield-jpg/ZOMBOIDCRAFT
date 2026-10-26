package com.zomboidcraft.mixin.client;

import com.zomboidcraft.client.gear.RadioClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The voice chat mod's push-to-talk microphone asks this whether to send: yes, while you talk on a radio. */
@Pseudo
@Mixin(targets = "de.maxhenkel.voicechat.voice.client.PTTMicrophoneProcessor", remap = false)
public abstract class VoicePttProcessorMixin {
    @Inject(method = "isPttButtonDown()Z", at = @At("HEAD"), cancellable = true, require = 0)
    private void zomboidcraft$radioTalk(CallbackInfoReturnable<Boolean> cir) {
        if (RadioClient.transmitting) {
            cir.setReturnValue(true);
        }
    }
}
