package com.zomboidcraft.client.throwing;

import com.zomboidcraft.ZcConfig;
import com.zomboidcraft.combat.throwing.ThrowKind;
import com.zomboidcraft.combat.throwing.ThrowableItem;
import com.zomboidcraft.combat.throwing.Thrown;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * What thrown things look like going off, on the client: a struck flare's red glare, smoke and light; a lit fuse's
 * sparks; a molotov's burning rag in flight; a smoke grenade's cloud. And the arc preview while you wind up a throw.
 */
public final class ThrowEffects {
    private static final DustParticleOptions FLARE_RED = new DustParticleOptions(0xFF2A1A, 1.0f);
    private static final DustParticleOptions SPARK = new DustParticleOptions(0xFFD27A, 0.6f);
    private static final DustParticleOptions ARC = new DustParticleOptions(0xE8E2C8, 0.45f);
    private static final DustParticleOptions ARC_END = new DustParticleOptions(0xFFFFFF, 0.9f);
    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
    /** Mirrors Thrown's flight. */
    private static final double GRAVITY = 0.05, DRAG = 0.99;

    /** Each flare's light block, by entity id; and how long each smoke grenade has been smoking. */
    private static final Map<Integer, BlockPos> LIGHTS = new HashMap<>();
    private static final Map<Integer, Integer> SMOKING = new HashMap<>();
    private static ClientLevel litIn;

    private ThrowEffects() {
    }

    public static void register() {
        Thrown.CLIENT_TICK = ThrowEffects::tick;
        ClientTickEvents.END_CLIENT_TICK.register(ThrowEffects::sweep);
    }

    private static void tick(Thrown t) {
        ClientLevel level = (ClientLevel) t.level();
        RandomSource r = level.random;
        ThrowKind kind = t.kind();
        Vec3 at = t.position().add(0, 0.12, 0);
        if (t.lit()) {
            level.addParticle(SPARK, at.x, at.y + 0.1, at.z, (r.nextDouble() - 0.5) * 0.1, 0.08, (r.nextDouble() - 0.5) * 0.1);
            if (r.nextInt(3) == 0) {
                level.addParticle(ParticleTypes.SMOKE, at.x, at.y + 0.1, at.z, 0, 0.02, 0);
            }
        }
        switch (kind) {
            case FLARE -> {
                if (t.phase() == Thrown.FLYING || t.phase() == Thrown.GOING) {
                    level.addParticle(FLARE_RED, at.x, at.y + 0.05, at.z, (r.nextDouble() - 0.5) * 0.05, 0.05, (r.nextDouble() - 0.5) * 0.05);
                    if (r.nextInt(2) == 0) {
                        level.addParticle(ParticleTypes.SMOKE, true, false, at.x, at.y + 0.15, at.z, 0, 0.04, 0);
                    }
                    if (r.nextInt(6) == 0) {
                        level.addParticle(ParticleTypes.LAVA, at.x, at.y, at.z, 0, 0, 0);
                    }
                    light(level, t);
                }
            }
            case MOLOTOV -> {
                if (t.phase() == Thrown.FLYING) {
                    level.addParticle(ParticleTypes.SMALL_FLAME, at.x, at.y + 0.1, at.z, 0, 0.01, 0);
                }
            }
            case SMOKE -> {
                if (t.phase() == Thrown.GOING) {
                    int ticks = SMOKING.merge(t.getId(), 1, Integer::sum);
                    smoke(level, at, ticks);
                }
            }
            default -> {
            }
        }
    }

    /** The cloud, billowing out from the grenade over three seconds and thinning out at the end. */
    private static void smoke(ClientLevel level, Vec3 at, int ticks) {
        ZcConfig.Throwing c = ZcConfig.get().throwing;
        double grow = Math.min(1, ticks / 60.0), fade = Mth.clamp((c.smokeTicks - ticks) / 80.0, 0, 1);
        double radius = c.smokeRadius * Math.min(grow, fade);
        RandomSource r = level.random;
        level.addParticle(ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, true, true, at.x, at.y + 0.2, at.z,
                (r.nextDouble() - 0.5) * 0.06, 0.05, (r.nextDouble() - 0.5) * 0.06);
        int puffs = (int) Math.ceil(radius * 1.2 * fade);
        for (int i = 0; i < puffs; i++) {
            double a = r.nextDouble() * Math.PI * 2, d = Math.sqrt(r.nextDouble()) * radius;
            level.addParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, true, true, at.x + Math.cos(a) * d, at.y + 0.3 + r.nextDouble() * radius * 0.6,
                    at.z + Math.sin(a) * d, 0, 0.01, 0);
        }
    }

    /** A burning flare lights up what's round it: a client-only light block where it lies, moved as it moves. */
    private static void light(ClientLevel level, Thrown t) {
        int brightness = Mth.clamp(ZcConfig.get().throwing.flareLight, 0, 15);
        BlockPos pos = t.lightPos();
        BlockPos had = LIGHTS.get(t.getId());
        if (pos.equals(had) || brightness <= 0) {
            return;
        }
        if (had != null) {
            clear(level, had);
            LIGHTS.remove(t.getId());
        }
        if (level.getBlockState(pos).isAir()) {
            level.setBlock(pos, Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, brightness), FLAGS);
            LIGHTS.put(t.getId(), pos);
        }
    }

    private static void clear(ClientLevel level, BlockPos pos) {
        if (level.getBlockState(pos).is(Blocks.LIGHT)) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), FLAGS);
        }
    }

    /** Takes away the lights of flares burnt out or gone, and draws the arc of a throw being wound up. */
    private static void sweep(Minecraft client) {
        ClientLevel level = client.level;
        if (level != litIn) {
            LIGHTS.clear();
            SMOKING.clear();
            litIn = level;
        }
        if (level == null) {
            return;
        }
        LIGHTS.entrySet().removeIf(e -> {
            Entity entity = level.getEntity(e.getKey());
            if (entity instanceof Thrown t && t.isAlive() && t.kind() == ThrowKind.FLARE) {
                return false;
            }
            clear(level, e.getValue());
            return true;
        });
        SMOKING.keySet().removeIf(id -> !(level.getEntity(id) instanceof Thrown t) || !t.isAlive());
        if (client.player != null && ZcConfig.get().throwing.arcPreview) {
            arc(level, client.player);
        }
    }

    /** Where the throw would go if let go now: a dotted arc and a brighter mark where it lands. */
    private static void arc(ClientLevel level, LocalPlayer player) {
        if (!player.isUsingItem() || !(player.getUseItem().getItem() instanceof ThrowableItem item) || player.tickCount % 2 != 0) {
            return;
        }
        ThrowKind kind = item.kind();
        Vec3 look = player.getLookAngle();
        Vec3 right = look.cross(new Vec3(0, 1, 0));
        right = right.lengthSqr() < 1e-6 ? Vec3.ZERO : right.normalize();
        Vec3 p = player.getEyePosition().add(right.scale(0.25)).add(0, -0.15, 0).add(look.scale(0.35));
        Vec3 v = look.scale(ThrowableItem.speed(kind, ThrowableItem.power(player))).add(player.getDeltaMovement().multiply(1, 0, 1));
        for (int step = 0; step < 120; step++) {
            v = v.add(0, -GRAVITY, 0).scale(DRAG);
            Vec3 next = p.add(v);
            HitResult hit = level.clip(new ClipContext(p, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, player));
            if (hit.getType() != HitResult.Type.MISS) {
                Vec3 end = hit.getLocation();
                level.addParticle(ARC_END, end.x, end.y + 0.05, end.z, 0, 0, 0);
                return;
            }
            if (step > 1 && step % 2 == 0) {
                level.addParticle(ARC, next.x, next.y, next.z, 0, 0, 0);
            }
            p = next;
        }
    }
}
