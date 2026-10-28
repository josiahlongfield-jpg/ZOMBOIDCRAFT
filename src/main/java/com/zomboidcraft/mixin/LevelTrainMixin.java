package com.zomboidcraft.mixin;

import com.zomboidcraft.world.trains.TrainPhysics;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * On your game, a train car's roof counts as the top of the world where it stands, like a building's:
 * rain stops on it, and nothing that asks "is it raining here" (rain, puddles, splashes, drops on
 * your face, the mist) reaches inside. A car's roof does the same ({@link com.zomboidcraft.world.cars.CarShelter}).
 */
@Mixin(Level.class)
public abstract class LevelTrainMixin {
    @Inject(method = "getHeight(Lnet/minecraft/world/level/levelgen/Heightmap$Types;II)I", at = @At("RETURN"), cancellable = true)
    private void zomboidcraft$trainRoof(Heightmap.Types type, int x, int z, CallbackInfoReturnable<Integer> cir) {
        Level self = (Level) (Object) this;
        if (!self.isClientSide() || type != Heightmap.Types.MOTION_BLOCKING && type != Heightmap.Types.MOTION_BLOCKING_NO_LEAVES) {
            return;
        }
        double roof = TrainPhysics.roofOver(self, x + 0.5, z + 0.5);
        double car = com.zomboidcraft.world.cars.CarShelter.roofOver(self, x, z);
        if (Double.isNaN(roof) || car > roof) {
            roof = car;
        }
        if (!Double.isNaN(roof)) {
            int top = Mth.ceil(roof);
            if (top > cir.getReturnValueI()) {
                cir.setReturnValue(top);
            }
        }
    }
}
