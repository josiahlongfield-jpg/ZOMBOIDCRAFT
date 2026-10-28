package com.zomboidcraft.mixin.client;

import com.zomboidcraft.world.trains.TrainPhysics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Inside a train car it's indoors: the sky light is shut out and the car's tubes light it, for
 * everything that asks the world how bright a spot is. Shader packs read the light at your eyes
 * this way to decide whether you're out in the rain (drops on the lens, fog, wet surfaces).
 */
@Mixin(ClientLevel.class)
public abstract class ClientLevelTrainMixin {
    /** Overrides the world's plain light lookup (a default method nothing else in the chain replaces). */
    public int getBrightness(LightLayer layer, BlockPos pos) {
        Level self = (Level) (Object) this;
        int raw = self.getLightEngine().getLayerListener(layer).getLightValue(pos);
        int light = TrainPhysics.brightness(self, layer, pos, raw);
        // in a car's cabin too the sky's shut out, so shader packs keep their rain and fog outside
        if (layer == LightLayer.SKY && light > TrainPhysics.INDOOR_SKY
                && com.zomboidcraft.world.cars.CarShelter.inside(self, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5)) {
            return TrainPhysics.INDOOR_SKY;
        }
        return light;
    }
}
