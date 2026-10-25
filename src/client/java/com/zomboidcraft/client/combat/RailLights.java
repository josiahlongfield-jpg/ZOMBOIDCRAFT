package com.zomboidcraft.client.combat;

import com.zomboidcraft.ModItems;
import com.zomboidcraft.ZcConfig;
import com.zomboidcraft.combat.Attachments;
import com.zomboidcraft.combat.ModParticles;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * Gun lights and lasers, drawn on each computer for every player in sight who has one switched on. The light is
 * a light block put only into this computer's copy of the world, in the air just in front of where the beam
 * lands, and moved as the gun turns: the server's world never changes. The laser puts a green dot where it lands
 * and, faintly, the beam itself: green specks along the line, as if it were catching dust in the air, thinning
 * out with distance.
 */
public final class RailLights {
    private static final double RANGE = 32;
    /** The beam starts this far out from the laser (up close it would be drawn out of line with the gun). */
    private static final double BEAM_START = 0.9;
    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
    private static final Map<UUID, BlockPos> LIT = new HashMap<>();
    private static ClientLevel litIn;

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(RailLights::tick);
    }

    private static void tick(Minecraft client) {
        ClientLevel level = client.level;
        if (level != litIn) {
            LIT.clear();            // the old copy of the world went with its lights
            litIn = level;
        }
        if (level == null) {
            return;
        }
        Set<UUID> lit = new HashSet<>();
        for (AbstractClientPlayer player : level.players()) {
            ItemStack gun = player.getMainHandItem();
            if (!gun.is(ModItems.ASSAULT_RIFLE) || player.isSpectator()) {
                continue;
            }
            boolean light = Attachments.lightOn(gun), laser = Attachments.laserOn(gun);
            if (!light && !laser) {
                continue;
            }
            Vec3 eye = player.getEyePosition();
            Vec3 look = player.getViewVector(1f);
            HitResult hit = aim(level, player, eye, look);
            if (light) {
                lit.add(player.getUUID());
                moveLight(level, player.getUUID(), hit, eye, look);
            }
            if (laser) {
                if (hit.getType() != HitResult.Type.MISS) {
                    Vec3 at = hit.getLocation().subtract(look.scale(0.03));
                    level.addParticle(ModParticles.LASER_MOTE, at.x, at.y, at.z, 0.045, 0.95, 2);
                }
                if (ZcConfig.get().guns.laserBeam) {
                    beam(level, emitter(client, player, eye, look), hit.getLocation(), player.getRandom());
                }
            }
        }
        LIT.entrySet().removeIf(e -> {
            if (lit.contains(e.getKey())) {
                return false;
            }
            clear(level, e.getValue());     // switched off, put away, or gone
            return true;
        });
    }

    /** Specks of the beam for one tick: scattered along it, most near the gun, each lasting a few ticks. */
    private static void beam(ClientLevel level, Vec3 from, Vec3 to, RandomSource random) {
        Vec3 line = to.subtract(from);
        double length = line.length();
        if (length < BEAM_START + 0.1) {
            return;
        }
        Vec3 dir = line.scale(1 / length);
        int tries = (int) Math.min(36, length * 1.4);
        for (int i = 0; i < tries; i++) {
            double s = BEAM_START + random.nextDouble() * (length - BEAM_START);
            if (random.nextDouble() > 0.85 * Math.exp(-s / 9) + 0.12) {
                continue;
            }
            Vec3 at = from.add(dir.scale(s));
            double size = 0.012 + random.nextDouble() * 0.014;
            double alpha = (0.3 + random.nextDouble() * 0.45) * (s < 2 ? (s - BEAM_START) / (2 - BEAM_START) : 1);
            level.addParticle(ModParticles.LASER_MOTE, at.x + (random.nextDouble() - 0.5) * 0.02,
                    at.y + (random.nextDouble() - 0.5) * 0.02, at.z + (random.nextDouble() - 0.5) * 0.02,
                    size, alpha, 2 + random.nextInt(4));
        }
    }

    /** Where the beam leaves the gun: the laser's lens as first person draws it, or near the hands from outside. */
    private static Vec3 emitter(Minecraft client, AbstractClientPlayer player, Vec3 eye, Vec3 look) {
        Vec3 up = new Vec3(0, 1, 0);
        Vec3 right = look.cross(up);
        right = right.lengthSqr() < 1e-6 ? new Vec3(1, 0, 0) : right.normalize();
        up = right.cross(look).normalize();
        Vector3f lens = player == client.player ? RifleView.laserInCamera() : null;
        if (lens != null) {
            return eye.add(right.scale(lens.x)).add(up.scale(lens.y)).add(look.scale(-lens.z));
        }
        return eye.add(right.scale(0.22)).add(up.scale(-0.3)).add(look.scale(0.5));
    }

    private static HitResult aim(ClientLevel level, AbstractClientPlayer player, Vec3 eye, Vec3 look) {
        Vec3 end = eye.add(look.scale(RANGE));
        BlockHitResult block = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        Vec3 stop = block.getType() == HitResult.Type.MISS ? end : block.getLocation();
        EntityHitResult entity = ProjectileUtil.getEntityHitResult(player, eye, stop, new AABB(eye, stop).inflate(1.0),
                e -> e instanceof LivingEntity && e.isAlive() && !e.isSpectator() && e != player, eye.distanceToSqr(stop));
        return entity != null ? entity : block;
    }

    private static void moveLight(ClientLevel level, UUID id, HitResult hit, Vec3 eye, Vec3 look) {
        BlockPos pos = null;
        if (hit instanceof BlockHitResult block && block.getType() != HitResult.Type.MISS) {
            pos = block.getBlockPos().relative(block.getDirection());
        } else if (hit.getType() == HitResult.Type.ENTITY) {
            pos = BlockPos.containing(hit.getLocation().subtract(look.scale(0.6)));
        }
        // bright up close, fading with distance like a torch beam spreading out
        int brightness = pos == null ? 0 : Math.max(6, 15 - (int) (hit.getLocation().distanceTo(eye) / 4));
        BlockPos old = LIT.get(id);
        if (old != null && !old.equals(pos)) {
            clear(level, old);
            LIT.remove(id);
        }
        if (pos == null) {
            return;
        }
        BlockState here = level.getBlockState(pos);
        if (here.is(Blocks.LIGHT)) {
            if (here.getValue(LightBlock.LEVEL) != brightness && pos.equals(LIT.get(id))) {
                level.setBlock(pos, here.setValue(LightBlock.LEVEL, brightness), FLAGS);
            }
        } else if (here.isAir()) {
            level.setBlock(pos, Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, brightness), FLAGS);
            LIT.put(id, pos);
        }
    }

    private static void clear(ClientLevel level, BlockPos pos) {
        if (level.getBlockState(pos).is(Blocks.LIGHT)) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), FLAGS);
        }
    }

    private RailLights() {
    }
}
