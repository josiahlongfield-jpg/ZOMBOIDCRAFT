package com.zomboidcraft.mixin.client;

import com.zomboidcraft.zombie.Profiler;
import net.minecraft.client.multiplayer.ClientPacketListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Counts the world changes the client is sent, for /zc profile: every one makes Sodium rebuild and re-upload the
 * chunk section it lands in, which a city profile (2026-11-01) showed stalling the frame. Counted on return, since
 * each handler first bounces itself from the network thread onto the main one.
 */
@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {
    @Inject(method = "handleBlockUpdate", at = @At("RETURN"), require = 0)
    private void zomboidcraft$block(CallbackInfo ci) {
        Profiler.CLIENT_BLOCKS.increment();
    }

    @Inject(method = "handleChunkBlocksUpdate", at = @At("RETURN"), require = 0)
    private void zomboidcraft$section(CallbackInfo ci) {
        Profiler.CLIENT_SECTIONS.increment();
    }

    @Inject(method = "handleLightUpdatePacket", at = @At("RETURN"), require = 0)
    private void zomboidcraft$light(CallbackInfo ci) {
        Profiler.CLIENT_LIGHT.increment();
    }

    @Inject(method = "handleLevelChunkWithLight", at = @At("RETURN"), require = 0)
    private void zomboidcraft$chunk(CallbackInfo ci) {
        Profiler.CLIENT_CHUNKS.increment();
    }
}
