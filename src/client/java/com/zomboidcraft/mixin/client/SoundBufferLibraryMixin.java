package com.zomboidcraft.mixin.client;

import com.mojang.blaze3d.audio.SoundBuffer;
import com.zomboidcraft.client.npc.NpcVoice;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SoundBufferLibrary.class)
public abstract class SoundBufferLibraryMixin {
    @Shadow
    @Final
    private Map<Identifier, CompletableFuture<SoundBuffer>> cache;

    /**
     * A companion's voice clip (NpcVoice) comes from her folder in config, not a resource pack. Kept in the library's
     * own cache, so it's let go with the rest when sounds reload.
     */
    @Inject(method = "getCompleteBuffer", at = @At("HEAD"), cancellable = true)
    private void zomboidcraft$npcVoice(Identifier path, CallbackInfoReturnable<CompletableFuture<SoundBuffer>> cir) {
        if (NpcVoice.ours(path)) {
            cir.setReturnValue(cache.computeIfAbsent(path, NpcVoice::load));
        }
    }
}
