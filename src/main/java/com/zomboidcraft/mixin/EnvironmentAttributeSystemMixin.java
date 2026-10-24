package com.zomboidcraft.mixin;

import com.zomboidcraft.world.weather.WeatherLayers;
import net.minecraft.world.attribute.EnvironmentAttributeSystem;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Adds the gloom on top of vanilla's timelines and weather, so it has the last word. */
@Mixin(EnvironmentAttributeSystem.class)
public abstract class EnvironmentAttributeSystemMixin {
    @Inject(method = "addDynamicLayers", at = @At("TAIL"))
    private static void zomboidcraft$addWeather(EnvironmentAttributeSystem.Builder builder, Level level, CallbackInfo ci) {
        WeatherLayers.add(builder, level);
    }
}
