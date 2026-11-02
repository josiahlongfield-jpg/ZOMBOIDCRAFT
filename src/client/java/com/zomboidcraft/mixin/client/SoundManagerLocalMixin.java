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
            cir.setReturnValue(((SoundManager) (Object) this).getSoundEvent(ZomboidCraft.id(ours)));
        }
    }
}
