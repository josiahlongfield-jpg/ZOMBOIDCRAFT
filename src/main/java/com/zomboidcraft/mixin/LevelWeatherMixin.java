package com.zomboidcraft.mixin;

import com.zomboidcraft.world.weather.Climate;
import com.zomboidcraft.world.weather.Weather;
import com.zomboidcraft.world.weather.WeatherView;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * On the client, rain and thunder show the mod's weather instead of vanilla's on/off: a light
 * drizzle, a downpour, a storm, thinning out where the moon breaks through. Shader packs read
 * these too, for wet streets and cloud cover. nature.maxRainFx caps what shows.
 */
@Mixin(Level.class)
public abstract class LevelWeatherMixin {
    @Inject(method = "getRainLevel", at = @At("RETURN"), cancellable = true)
    private void zomboidcraft$rain(float partialTick, CallbackInfoReturnable<Float> cir) {
        Weather w = zomboidcraft$weather();
        if (w != null) {
            cir.setReturnValue(w.rain() * (1 - 0.85f * w.moon()) * Climate.maxRainFx());
        }
    }

    @Inject(method = "getThunderLevel", at = @At("RETURN"), cancellable = true)
    private void zomboidcraft$thunder(float partialTick, CallbackInfoReturnable<Float> cir) {
        Weather w = zomboidcraft$weather();
        if (w != null) {
            cir.setReturnValue(w.thunder());
        }
    }

    private Weather zomboidcraft$weather() {
        Level self = (Level) (Object) this;
        return self.isClientSide() && self.dimension() == Level.OVERWORLD ? WeatherView.client() : null;
    }
}
