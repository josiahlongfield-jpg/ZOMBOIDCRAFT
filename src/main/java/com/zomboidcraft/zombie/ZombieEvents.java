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
    /** Zomboid's lore rolls on each zombie: how hard it bites, how much it takes, and slowing outside its hours. */
    private static final Identifier STRENGTH = ZomboidCraft.id("lore_strength");
    private static final Identifier TOUGHNESS = ZomboidCraft.id("lore_toughness");
    private static final Identifier DORMANT = ZomboidCraft.id("lore_dormant");
    /** Speed taken off outside its active hours (Day/Night Zombie Speed Effect). */
    private static final double DORMANT_SLOW = -0.3;
    private static boolean wasDormant;

    private static final Map<ServerPlayer, Vec3> lastPositions = new WeakHashMap<>();
    private static int ticks;

    public static void register() {
        ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
            if (ZombieTypes.isWalker(entity)) {
                setUp((Zombie) entity);
            }
        });

        // what was left stuck in it falls out when it goes down (zombieLore.attachedWeaponPercent)
        net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (entity instanceof Zombie zombie && ZombieTypes.isWalker(zombie) && zombie.level() instanceof ServerLevel level) {
                AttachedWeapons.drop(level, zombie);
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
        com.zomboidcraft.combat.Downed.onLoad(zombie);
        AttributeInstance speed = zombie.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) {
            double amount = switch (Lore.pace(zombie)) {
                case SPRINTER -> config.sprinterSpeed;
                case FAST_SHAMBLER -> config.fastShamblerSpeed;
                case SHAMBLER -> config.shamblerSpeed;
            };
            speed.addOrReplacePermanentModifier(new AttributeModifier(
                    SPEED_MODIFIER, amount, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
        }
        lore(zombie);
    }

    /**
     * Zomboid's strength and toughness rolls (zombieLore): strength on every bite and scratch, toughness on its
     * health, so a tough one takes half again the blows and a fragile one goes down in two thirds of them. Set on
     * every load, so a changed setting reaches zombies already in the world. Path reach follows its wits.
     */
    private static void lore(Zombie zombie) {
        AttributeInstance damage = zombie.getAttribute(Attributes.ATTACK_DAMAGE);
        if (damage != null) {
            damage.addOrReplacePermanentModifier(new AttributeModifier(
                    STRENGTH, Lore.strength(zombie) - 1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
        AttributeInstance health = zombie.getAttribute(Attributes.MAX_HEALTH);
        if (health != null) {
            boolean full = zombie.getHealth() >= zombie.getMaxHealth();
            health.addOrReplacePermanentModifier(new AttributeModifier(
                    TOUGHNESS, Lore.toughness(zombie) - 1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
            if (full || zombie.getHealth() > zombie.getMaxHealth()) {
                zombie.setHealth(zombie.getMaxHealth());
            }
        }
        zombie.getNavigation().setMaxVisitedNodesMultiplier(Lore.pathReach(zombie));
        dormant(zombie, Lore.dormant(zombie.level()));
    }

    private static void dormant(Zombie zombie, boolean on) {
        AttributeInstance speed = zombie.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null) {
            return;
        }
        if (on) {
            speed.addOrUpdateTransientModifier(new AttributeModifier(DORMANT, DORMANT_SLOW, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        } else {
            speed.removeModifier(DORMANT);
        }
    }

    /** When the clock crosses into or out of their active hours, every loaded walker speeds up or slows. */
    private static void watchHours(MinecraftServer server) {
        boolean now = Lore.dormant(server.overworld());
        if (now == wasDormant) {
            return;
        }
        wasDormant = now;
        for (ServerLevel level : server.getAllLevels()) {
            for (net.minecraft.world.entity.Entity e : level.getAllEntities()) {
                if (e instanceof Zombie zombie && ZombieTypes.isWalker(zombie)) {
                    dormant(zombie, now);
                }
            }
        }
    }

    private static void onServerTick(MinecraftServer server) {
        if (++ticks % 10 != 0) {
            return;
        }
        if (ticks % 200 == 0) {
            watchHours(server);
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
