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
            ZcConfig.ZombieLore.Transmission spread = ZcConfig.get().zombieLore.transmission;
            if (entity instanceof ServerPlayer player && ZcConfig.get().survival.reanimate
                    && spread != ZcConfig.ZombieLore.Transmission.NONE
                    && (SurvivalStats.get(player).infected() || spread == ZcConfig.ZombieLore.Transmission.EVERYONES_INFECTED)) {
                rises(player);
            }
        });
    }

    private static void onServerTick(MinecraftServer server) {
        if (++ticks % 20 != 0) {
            return;
        }
        if (!RISING.isEmpty()) {
            riseDue(server);
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

        // Bleeding and healing are per body part now: see Injuries

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
        Injuries.tick(player, second);
    }

    private static void onZombieHit(ServerPlayer player) {
        RandomSource random = player.getRandom();
        SurvivalStats stats = SurvivalStats.get(player);
        ZcConfig.Survival config = ZcConfig.get().survival;
        float roll = random.nextFloat();
        // Zomboid's Transmission (zombieLore): bites and scratches, bites only, or nothing from wounds at all (none,
        // and everyone's infected, where you carry it already and rise whatever kills you)
        ZcConfig.ZombieLore.Transmission spread = ZcConfig.get().zombieLore.transmission;
        boolean bites = spread == ZcConfig.ZombieLore.Transmission.BLOOD_AND_SALIVA
                || spread == ZcConfig.ZombieLore.Transmission.SALIVA_ONLY;
        boolean scratches = spread == ZcConfig.ZombieLore.Transmission.BLOOD_AND_SALIVA;
        if (roll < config.biteChance) {
            Body.Region where = Injuries.zombieWound(player, true);
            if (where == null) {
                player.sendOverlayMessage(Component.literal("Your clothes stopped a bite.").withStyle(ChatFormatting.GRAY));
            } else {
                if (!stats.infected() && config.infection && bites) {
                    stats = stats.withInfection(0);
                }
                player.sendOverlayMessage(Component.literal("You've been bitten on the " + where.label + ".").withStyle(ChatFormatting.DARK_RED));
            }
        } else if (roll < config.biteChance + config.scratchChance) {
            Body.Region where = Injuries.zombieWound(player, false);
            if (where == null) {
                player.sendOverlayMessage(Component.literal("Your clothes took the scratch.").withStyle(ChatFormatting.GRAY));
            } else {
                if (!stats.infected() && config.infection && scratches && random.nextFloat() < config.scratchInfectionChance) {
                    stats = stats.withInfection(0);
                }
                player.sendOverlayMessage(Component.literal("You've been hurt on the " + where.label + ".").withStyle(ChatFormatting.RED));
            }
        }
        SurvivalStats.set(player, stats);
    }

    /** Infected bodies waiting to get up: where, whose name, and the server tick they rise. */
    private record Rising(net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> level, net.minecraft.core.BlockPos pos,
            Component name, long at) {
    }

    private static final java.util.List<Rising> RISING = new java.util.ArrayList<>();

    /**
     * An infected player rises again where they fell, after Zomboid's Reanimate Time (zombieLore.reanimate) in game
     * time: instant to a minute is at once, up to 12 hours is up to ten real minutes, 2-3 days 40 to 60, 1-2 weeks
     * 4 to 8 hours. Kept while the server runs.
     */
    private static void rises(ServerPlayer player) {
        RandomSource random = player.getRandom();
        int h = SurvivalStats.GAME_HOUR;
        int wait = switch (ZcConfig.get().zombieLore.reanimate) {
            case INSTANT, SECONDS_0_30, MINUTES_0_1 -> 0;
            case HOURS_0_12 -> random.nextInt(12 * h);
            case DAYS_2_3 -> 48 * h + random.nextInt(24 * h);
            case WEEKS_1_2 -> 168 * h + random.nextInt(168 * h);
        };
        ServerLevel level = (ServerLevel) player.level();
        Rising r = new Rising(level.dimension(), player.blockPosition(), player.getName().copy(), level.getServer().getTickCount() + wait * 20L);
        if (wait == 0) {
            reanimate(level, r);
        } else {
            RISING.add(r);
        }
    }

    private static void riseDue(MinecraftServer server) {
        long now = server.getTickCount();
        RISING.removeIf(r -> {
            if (now < r.at()) {
                return false;
            }
            ServerLevel level = server.getLevel(r.level());
            if (level != null) {
                reanimate(level, r);
            }
            return true;
        });
    }

    private static void reanimate(ServerLevel level, Rising r) {
        Zombie zombie = EntityTypes.ZOMBIE.spawn(level, r.pos(), EntitySpawnReason.EVENT);
        if (zombie != null) {
            zombie.setCustomName(r.name());
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
