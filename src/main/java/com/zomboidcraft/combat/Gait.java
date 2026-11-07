package com.zomboidcraft.combat;

import com.zomboidcraft.ZcConfig;
import com.zomboidcraft.ZomboidCraft;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * Tarkov's own paces on foot (Josiah 2026-11-07: "whatever Tarkov has set their movement parameters to is what we will
 * use"). Tarkov's walk covers 2.5 m a second and its sprint 6.2 (the ground its player's walk and sprint clips cover);
 * Minecraft's are 4.3 and 5.6, a jog and a run. So a player walks at 58% of Minecraft's pace and sprints 10.5% faster
 * than it. Everything else that slows a player (tiredness, wounds, weight, a grab) still scales from these. The view
 * doesn't narrow or widen for it (AbstractClientPlayerMixin takes it out of the field of view).
 */
public final class Gait {
    private Gait() {
    }

    public static final Identifier PACE = ZomboidCraft.id("tarkov_pace");
    /** Tarkov's walk and sprint against Minecraft's (4.317 and 5.612 m/s). */
    public static final double WALK = 2.5 / 4.317 - 1, SPRINT = 6.2 / 5.612 - 1;

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(Gait::tick);
    }

    private static void tick(MinecraftServer server) {
        boolean on = ZcConfig.get().guns.tarkovPace;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
            if (speed == null) continue;
            double want = !on || player.isSpectator() || player.getAbilities().flying ? 0 : player.isSprinting() ? SPRINT : WALK;
            AttributeModifier now = speed.getModifier(PACE);
            if (want == 0) {
                if (now != null) speed.removeModifier(PACE);
            } else if (now == null || now.amount() != want) {
                speed.addOrUpdateTransientModifier(new AttributeModifier(PACE, want, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
            }
        }
    }
}
