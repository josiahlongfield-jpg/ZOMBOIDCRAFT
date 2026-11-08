package com.zomboidcraft.mixin.client;

import com.zomboidcraft.ZomboidCraft;
import com.zomboidcraft.combat.LocalGuns;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A gun kept on this computer plays its sounds under its own names (LocalGuns.sound: weapon.local/<gun>/<our event>).
 * Where the sound pack on this computer has no recording for one (a friend without the pack, or a sound Josiah left
 * on ours), it plays our event it stands in for.
 */
@Mixin(SoundManager.class)
public abstract class SoundManagerLocalMixin {
    @Inject(method = "getSoundEvent", at = @At("RETURN"), cancellable = true)
    private void zomboidcraft$localFallback(Identifier id, CallbackInfoReturnable<WeighedSoundEvents> cir) {
        if (cir.getReturnValue() == null && id.getNamespace().equals(ZomboidCraft.MOD_ID)
                && id.getPath().startsWith(LocalGuns.SOUND_PREFIX)) {
            String ours = id.getPath().substring(id.getPath().lastIndexOf('/') + 1);
            WeighedSoundEvents found = null;
            if (id.getPath().endsWith(LocalGuns.RELEASE)) {
                // a handle's release alone: this gun's own whole handle sound first. Going straight to ours played the
                // old stand-in (or nothing) for every Tarkov gun's slide or bolt going home on an empty reload, since
                // no Tarkov pack has a separate release (Josiah 2026-11-08)
                found = ((SoundManager) (Object) this).getSoundEvent(ZomboidCraft.id(
                        id.getPath().substring(0, id.getPath().length() - LocalGuns.RELEASE.length())));
            }
            if (found == null) found = ((SoundManager) (Object) this).getSoundEvent(ZomboidCraft.id(ours));
            if (found == null && ours.endsWith(LocalGuns.RELEASE)) {
                // a handle's release alone (LocalGuns.Cues) falls back to the whole handle sound
                found = ((SoundManager) (Object) this).getSoundEvent(ZomboidCraft.id(ours.substring(0, ours.length() - LocalGuns.RELEASE.length())));
            }
            cir.setReturnValue(found);
        }
    }
}
