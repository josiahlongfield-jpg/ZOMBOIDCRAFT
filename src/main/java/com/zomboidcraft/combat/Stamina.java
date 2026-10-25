package com.zomboidcraft.combat;

import com.mojang.serialization.Codec;
import com.zomboidcraft.ZcConfig;
import com.zomboidcraft.ZomboidCraft;
import com.zomboidcraft.skills.Skills;
import com.zomboidcraft.survival.SurvivalStats;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * Endurance, Zomboid style: swinging, shoving and sprinting use it up, standing still brings it
 * back. Run low and your swings slow and weaken; run out and you can't sprint. Synced to the
 * player's own client for the moodles.
 */
public final class Stamina {
    public static final float FULL = 100f;

    public static final AttachmentType<Float> TYPE = AttachmentRegistry.create(
            ZomboidCraft.id("endurance"),
            builder -> builder
                    .persistent(Codec.FLOAT)
                    .initializer(() -> FULL)
                    .syncWith(ByteBufCodecs.FLOAT, AttachmentSyncPredicate.targetOnly()));

    private static final Identifier TIRED_SWING = ZomboidCraft.id("tired_swing");
    private static final Identifier TIRED_HIT = ZomboidCraft.id("tired_hit");
    private static final Identifier WINDED = ZomboidCraft.id("winded");
    private static final int STEP = 5;

    private static int ticks;

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(Stamina::tick);
    }

    public static float get(Player player) {
        return player.getAttachedOrElse(TYPE, FULL);
    }

    private static boolean active(Player player) {
        return ZcConfig.get().combat.stamina && !player.isCreative() && !player.isSpectator();
    }

    /** Spends endurance. Returns how fresh the player was before (0..1), which scales the action. */
    public static float use(ServerPlayer player, double amount) {
        if (!active(player)) {
            return 1f;
        }
        float before = get(player);
        set(player, before - (float) amount);
        return before / FULL;
    }

    /** A swing at a zombie: heavier weapons cost more. */
    public static void onSwing(ServerPlayer player) {
        WeaponSpecs.WeaponSpec spec = Weapons.spec(player.getMainHandItem());
        double cost = spec != null ? spec.stamina() : 2.5;
        // Blunt or Blade 5: practised swings cost a fifth less
        if (spec != null && spec.skill() != null && Skills.level(player, spec.skill()) >= 5) cost *= 0.8;
        use(player, cost * ZcConfig.get().combat.swingCost);
    }

    /** 1 when rested, falling to 0.5 when spent: scales knockdown chances. */
    public static float vigour(Player player) {
        return active(player) ? 0.5f + 0.5f * Mth.clamp(get(player) / 40f, 0f, 1f) : 1f;
    }

    private static void set(Player player, float value) {
        float v = Math.round(Mth.clamp(value, 0f, FULL) * 10f) / 10f;
        if (v != get(player)) {
            player.setAttached(TYPE, v);
        }
    }

    private static void tick(MinecraftServer server) {
        if (++ticks % STEP != 0) {
            return;
        }
        ZcConfig.Combat config = ZcConfig.get().combat;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!active(player) || !player.isAlive()) {
                if (get(player) < FULL) {
                    set(player, FULL);
                }
                penalties(player, 0);
                continue;
            }
            float e = get(player);
            boolean moving = player.getDeltaMovement().horizontalDistanceSqr() > 0.001;
            if (player.isSprinting()) {
                e -= 0.6f;
            } else {
                // about 12 seconds from empty standing still, half as fast on the move or when tired
                float regen = (float) (FULL / (12f * 20f / STEP) * config.staminaRegen);
                if (moving) {
                    regen *= 0.5f;
                }
                if (SurvivalStats.get(player).fatigue() > 60) {
                    regen *= 0.6f;
                }
                e += regen;
            }
            set(player, e);
            penalties(player, e < 10 ? 2 : e < 25 ? 1 : 0);
        }
    }

    private static void penalties(ServerPlayer player, int tier) {
        modifier(player, Attributes.ATTACK_SPEED, TIRED_SWING, tier == 2 ? -0.45 : tier == 1 ? -0.25 : 0);
        modifier(player, Attributes.ATTACK_DAMAGE, TIRED_HIT, tier == 2 ? -0.35 : tier == 1 ? -0.2 : 0);
        modifier(player, Attributes.MOVEMENT_SPEED, WINDED, tier == 2 ? -0.15 : 0);
    }

    private static void modifier(ServerPlayer player, Holder<Attribute> attribute, Identifier id, double amount) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null) {
            return;
        }
        AttributeModifier current = instance.getModifier(id);
        if (amount == 0) {
            if (current != null) {
                instance.removeModifier(id);
            }
        } else if (current == null || current.amount() != amount) {
            instance.addOrUpdateTransientModifier(new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
    }

    private Stamina() {
    }
}
