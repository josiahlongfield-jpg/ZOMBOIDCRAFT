package com.zomboidcraft.survival;

import com.zomboidcraft.ZcConfig;
import com.zomboidcraft.skills.Combat;
import com.zomboidcraft.skills.Skill;
import com.zomboidcraft.skills.Skills;
import com.zomboidcraft.zombie.ZombieTypes;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.zombie.Zombie;

public final class SurvivalEvents {
    private static int ticks;

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(SurvivalEvents::onServerTick);
        ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damage, blocked) -> {
            if (entity instanceof ServerPlayer player && !blocked && damage > 0
                    && ZombieTypes.isWalker(source.getEntity())) {
                onZombieHit(player);
            }
        });
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            Combat.onKill(entity, source);
            if (entity instanceof ServerPlayer player && SurvivalStats.get(player).infected()
                    && ZcConfig.get().survival.reanimate) {
                reanimate(player);
            }
        });
    }

    private static void onServerTick(MinecraftServer server) {
        if (++ticks % 20 != 0) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.isAlive() && !player.isCreative() && !player.isSpectator()) {
                tickPlayer(player, ticks / 20);
            }
        }
    }

    /** Runs once per second for each surviving player. */
    private static void tickPlayer(ServerPlayer player, int second) {
        ServerLevel level = (ServerLevel) player.level();
        RandomSource random = player.getRandom();
        SurvivalStats stats = SurvivalStats.get(player);
        ZcConfig.Survival config = ZcConfig.get().survival;

        // Thirst. Sprinting skill makes running less draining.
        float sprintMultiplier = 2.5f - 0.1f * Skills.level(player, Skill.SPRINTING);
        float thirstDrain = config.thirst ? (float) config.thirstPerSecond * (player.isSprinting() ? sprintMultiplier : 1f) : 0f;
        if (player.isSprinting()) {
            Skills.addXp(player, Skill.SPRINTING, 1);
        }
        if (player.isShiftKeyDown() && !level.getEntitiesOfClass(net.minecraft.world.entity.monster.zombie.Zombie.class,
                        player.getBoundingBox().inflate(16), ZombieTypes::isWalker).isEmpty()) {
            Skills.addXp(player, Skill.SNEAKING, 1);
        }
        stats = stats.withThirst(stats.thirst() - thirstDrain);
        if (stats.thirst() < 20f) {
            effect(player, MobEffects.SLOWNESS, 0);
        }
        if (stats.thirst() <= 0f && second % 4 == 0) {
            player.hurtServer(level, ModDamageTypes.source(level, ModDamageTypes.THIRST), 1f);
        }

        // Fatigue: a full night's sleep resets it
        if (player.isSleeping()) {
            stats = player.getSleepTimer() >= 90 ? stats.withFatigue(0f) : stats.withFatigue(stats.fatigue() - 3f);
        } else {
            stats = stats.withFatigue(stats.fatigue() + (config.fatigue ? (float) config.fatiguePerSecond : 0f));
        }
        if (stats.fatigue() > 75f) {
            effect(player, MobEffects.MINING_FATIGUE, 0);
        }
        if (stats.fatigue() > 90f) {
            effect(player, MobEffects.SLOWNESS, 0);
            effect(player, MobEffects.WEAKNESS, 0);
        }

        // Bleeding: more wounds bleed faster; wounds slowly clot on their own
        if (!config.bleeding) {
            stats = stats.withWounds(0);
        }
        if (stats.wounds() > 0) {
            int interval = Math.max(1, 5 - stats.wounds());
            if (second % interval == 0) {
                player.hurtServer(level, ModDamageTypes.source(level, ModDamageTypes.BLEEDING), 1f);
            }
            int clotted = 0;
            for (int i = 0; i < stats.wounds(); i++) {
                if (random.nextFloat() < config.clotChancePerSecond) {
                    clotted++;
                }
            }
            stats = stats.withWounds(stats.wounds() - clotted);
        }

        // Infection: there is no cure
        if (stats.infected() && !config.infection) {
            stats = stats.withInfection(-1);
        }
        if (stats.infected()) {
            stats = stats.withInfection(stats.infection() + 1);
            float progress = stats.infectionProgress();
            if (progress > 0.25f && random.nextInt(40) == 0) {
                effect(player, MobEffects.NAUSEA, 0, 160);
            }
            if (progress > 0.5f) {
                effect(player, MobEffects.WEAKNESS, 0);
                effect(player, MobEffects.HUNGER, 0);
            }
            if (progress > 0.8f) {
                effect(player, MobEffects.SLOWNESS, 1);
                if (second % 10 == 0) {
                    player.hurtServer(level, ModDamageTypes.source(level, ModDamageTypes.INFECTION), 1f);
                }
            }
            if (progress >= 1f) {
                SurvivalStats.set(player, stats);
                player.hurtServer(level, ModDamageTypes.source(level, ModDamageTypes.INFECTION), Float.MAX_VALUE);
                return;
            }
        }

        SurvivalStats.set(player, stats);
    }

    private static void onZombieHit(ServerPlayer player) {
        RandomSource random = player.getRandom();
        SurvivalStats stats = SurvivalStats.get(player);
        ZcConfig.Survival config = ZcConfig.get().survival;
        float roll = random.nextFloat();
        if (roll < config.biteChance) {
            stats = stats.withWounds(stats.wounds() + 1);
            if (!stats.infected() && config.infection) {
                stats = stats.withInfection(0);
            }
            player.sendOverlayMessage(Component.literal("You've been bitten.").withStyle(ChatFormatting.DARK_RED));
        } else if (roll < config.biteChance + config.scratchChance) {
            stats = stats.withWounds(stats.wounds() + 1);
            if (!stats.infected() && config.infection && random.nextFloat() < config.scratchInfectionChance) {
                stats = stats.withInfection(0);
            }
            player.sendOverlayMessage(Component.literal("You've been scratched.").withStyle(ChatFormatting.RED));
        }
        SurvivalStats.set(player, stats);
    }

    /** An infected player rises again where they fell. */
    private static void reanimate(ServerPlayer player) {
        ServerLevel level = (ServerLevel) player.level();
        Zombie zombie = EntityTypes.ZOMBIE.spawn(level, player.blockPosition(), EntitySpawnReason.EVENT);
        if (zombie != null) {
            zombie.setCustomName(player.getName().copy());
            zombie.setPersistenceRequired();
        }
    }

    private static void effect(ServerPlayer player, net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> effect, int amplifier) {
        effect(player, effect, amplifier, 40);
    }

    private static void effect(ServerPlayer player, net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> effect, int amplifier, int duration) {
        player.addEffect(new MobEffectInstance(effect, duration, amplifier, true, false, true));
    }

    private SurvivalEvents() {
    }
}
