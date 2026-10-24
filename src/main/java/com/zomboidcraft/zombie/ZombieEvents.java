package com.zomboidcraft.zombie;

import com.zomboidcraft.ZcConfig;
import com.zomboidcraft.ZomboidCraft;
import com.zomboidcraft.skills.Skill;
import com.zomboidcraft.skills.Skills;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.phys.Vec3;

public final class ZombieEvents {
    private static final Identifier SPEED_MODIFIER = ZomboidCraft.id("shamble");

    private static final Map<ServerPlayer, Vec3> lastPositions = new WeakHashMap<>();
    private static int ticks;

    public static void register() {
        ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
            if (ZombieTypes.isWalker(entity)) {
                setUp((Zombie) entity);
            }
        });

        // Footsteps: checked twice a second. Sneaking is silent.
        ServerTickEvents.END_SERVER_TICK.register(ZombieEvents::onServerTick);

        PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, blockEntity) -> {
            if (level instanceof ServerLevel serverLevel) {
                Noise.emit(serverLevel, Vec3.atCenterOf(pos), Noise.BREAK_BLOCK);
            }
        });

        AttackEntityCallback.EVENT.register((player, level, hand, target, hitResult) -> {
            if (level instanceof ServerLevel serverLevel) {
                Noise.emit(serverLevel, player.position(), Noise.ATTACK);
            }
            return InteractionResult.PASS;
        });
    }

    /** Grown, slow (or rarely fast), and able to break down doors. */
    private static void setUp(Zombie zombie) {
        ZcConfig.Zombies config = ZcConfig.get().zombies;
        if (zombie.isBaby() && !config.babyZombies) {
            zombie.setBaby(false);
        }
        zombie.setCanBreakDoors(config.breakDoors);
        if (config.bashDoors) {
            // plan paths through shut doors, so they walk up to them and bash them in
            zombie.getNavigation().getNodeEvaluator().setCanOpenDoors(true);
        }
        AttributeInstance speed = zombie.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) {
            double amount = ZombieTypes.isSprinter(zombie) ? config.sprinterSpeed : config.shamblerSpeed;
            speed.addOrReplacePermanentModifier(new AttributeModifier(
                    SPEED_MODIFIER, amount, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
        }
    }

    private static void onServerTick(MinecraftServer server) {
        if (++ticks % 10 != 0) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.isSpectator() || player.isCreative()) {
                continue;
            }
            Vec3 now = player.position();
            Vec3 before = lastPositions.put(player, now);
            boolean moved = before != null && before.distanceToSqr(now) > 0.04;
            double walk = Noise.WALK - 0.3 * Skills.level(player, Skill.SNEAKING);
            double radius = !moved || player.isShiftKeyDown() ? 0 : player.isSprinting() ? Noise.SPRINT : walk;
            Noise.emit((ServerLevel) player.level(), now, radius);
        }
    }

    private ZombieEvents() {
    }
}
