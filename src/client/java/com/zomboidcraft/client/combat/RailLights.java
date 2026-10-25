package com.zomboidcraft.client.combat;

import com.zomboidcraft.ModItems;
import com.zomboidcraft.combat.Attachments;
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
import net.minecraft.core.particles.DustParticleOptions;
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
import net.minecraft.world.phys.Vec3;

/**
 * Gun lights and lasers, drawn on each computer for every player in sight who has one switched on. The light is
 * a light block put only into this computer's copy of the world, in the air just in front of where the beam
 * lands, and moved as the gun turns: the server's world never changes. The laser puts a green dot where it lands.
 */
public final class RailLights {
    private static final double RANGE = 32;
    private static final DustParticleOptions GREEN = new DustParticleOptions(0x3CFF46, 0.4f);
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
            if (laser && hit.getType() != HitResult.Type.MISS) {
                Vec3 at = hit.getLocation().subtract(look.scale(0.03));
                level.addParticle(GREEN, at.x, at.y, at.z, 0, 0, 0);
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
