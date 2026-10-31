package com.zomboidcraft.world;

import com.zomboidcraft.ZcConfig;
import com.zomboidcraft.clothing.ClothingSlot;
import com.zomboidcraft.clothing.Clothes;
import com.zomboidcraft.clothing.Garment;
import com.zomboidcraft.survival.SurvivalStats;
import com.zomboidcraft.world.county.BreachSite;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

/**
 * The red spores hanging round the breach (Josiah, 2026-10-29): near the core only, they get into you
 * slowly, and a gas mask keeps them out entirely. Thickest in the ring hall, thinner through the lab's
 * floors and over the ground zero core, nothing past it; a surge stirs them up three times as thick.
 * Breathing them in builds a dose that fades once you're out; a full dose is the infection, the same
 * one a bite gives (survival.infection off turns it off here too).
 */
public final class SporeHaze {
    /** The dose that infects. */
    private static final float FULL = 100;
    /** How far round the plug the surface haze reaches. */
    private static final double CORE = 45;
    private static final Map<UUID, Float> DOSE = new HashMap<>();
    private static final Map<UUID, Integer> WARNED = new HashMap<>();

    private SporeHaze() {
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(SporeHaze::tick);
        ServerLifecycleEvents.SERVER_STOPPING.register(s -> {
            DOSE.clear();
            WARNED.clear();
        });
    }

    /** How thick the spores are where the player's head is: 0 none, 1 the ring hall's. */
    static double thickness(BreachSite.Found b, double x, double y, double z) {
        double dx = x - (b.x() + 0.5), dz = z - (b.z() + 0.5), flat = Math.hypot(dx, dz);
        double dy = y - b.y();
        if (Math.hypot(flat, dy) < 22 && y < b.ground() - 2) {
            return 1;                                                  // the ring hall round the plug
        }
        if (y < b.ground() - 2 && flat < 80) {
            return y < b.ground() - 16 ? 0.35 : 0.15;                  // the lab's floors: B3 worst
        }
        if (flat < CORE && y < b.ground() + 12) {
            return 0.5 * (1 - flat / CORE);                           // the open core over the hall
        }
        return 0;
    }

    private static boolean masked(ServerPlayer p) {
        Garment g = Clothes.of(ClothingSlot.FACE.worn(p));
        return g != null && g.id().equals("gas_mask");
    }

    private static void tick(MinecraftServer s) {
        if (s.getTickCount() % 20 != 0) {
            return;
        }
        BreachSite.Found b = Breach.site();
        ServerLevel level = s.overworld();
        boolean infects = ZcConfig.get().survival.infection;
        for (ServerPlayer p : level.players()) {
            UUID id = p.getUUID();
            double thick = b == null || p.isSpectator() || p.isCreative() ? 0 : thickness(b, p.getX(), p.getEyeY(), p.getZ());
            if (thick > 0 && Breach.surging()) {
                thick = Math.min(1, thick * 3);
            }
            if (thick > 0) {
                level.sendParticles(ParticleTypes.CRIMSON_SPORE, p.getX(), p.getEyeY(), p.getZ(), (int) (4 + thick * 20), 3, 1.5, 3, 0.01);
            }
            float dose = DOSE.getOrDefault(id, 0f);
            boolean mask = masked(p);
            if (thick > 0 && !mask) {
                dose += (float) thick;
            } else {
                dose = Math.max(0, dose - 0.25f);
            }
            int warned = WARNED.getOrDefault(id, 0);
            if (thick > 0 && !mask && warned == 0) {
                p.sendOverlayMessage(Component.literal("The air tastes of rust. The red spores are thick here; a gas mask would keep them out.")
                        .withStyle(ChatFormatting.RED));
                WARNED.put(id, 1);
            } else if (thick == 0 && dose == 0) {
                WARNED.remove(id);
            }
            if (dose > FULL * 0.4f && !mask && thick > 0 && p.getRandom().nextInt(6) == 0) {
                level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.PLAYER_HURT_DROWN, SoundSource.PLAYERS, 0.4f, 0.6f);
                p.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 100, 0, true, false, true));
            }
            if (dose >= FULL) {
                dose = 0;
                SurvivalStats stats = SurvivalStats.get(p);
                if (infects && !stats.infected()) {
                    SurvivalStats.set(p, stats.withInfection(0));
                    p.sendOverlayMessage(Component.literal("Your chest burns. You've breathed in too much of the red.").withStyle(ChatFormatting.DARK_RED));
                }
            }
            if (dose > 0) {
                DOSE.put(id, dose);
            } else {
                DOSE.remove(id);
            }
        }
    }
}
