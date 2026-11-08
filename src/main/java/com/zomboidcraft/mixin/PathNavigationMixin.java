package com.zomboidcraft.mixin;

import com.zomboidcraft.zombie.perf.SharedPaths;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.PathNavigationRegion;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathFinder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** A horde heading for one spot shares its paths (SharedPaths). */
@Mixin(PathNavigation.class)
public abstract class PathNavigationMixin {
    @Redirect(method = "createPath(Ljava/util/Set;IZIF)Lnet/minecraft/world/level/pathfinder/Path;",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/pathfinder/PathFinder;findPath(Lnet/minecraft/world/level/PathNavigationRegion;Lnet/minecraft/world/entity/Mob;Ljava/util/Set;FIF)Lnet/minecraft/world/level/pathfinder/Path;"),
            require = 0)
    private Path zomboidcraft$sharedPath(PathFinder finder, PathNavigationRegion region, Mob mob, Set<BlockPos> targets,
                                         float range, int accuracy, float depth) {
        return SharedPaths.find(finder, region, mob, targets, range, accuracy, depth);
    }
}
