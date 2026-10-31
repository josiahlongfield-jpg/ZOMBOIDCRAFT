package com.zomboidcraft.client.gear;

import com.zomboidcraft.loot.items.LightItem;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Lit torches, lanterns, candles and glow sticks (LightItem) in anyone's hands light the world, with light blocks put
 * only into this computer's copy of it, the way gun lights do (RailLights). A torch throws its light where it's
 * pointed: the brightest just short of what it lands on, a dimmer one half way, a faint one at your feet. A lantern
 * or candle lights round the one holding it. The server's world never changes.
 */
public final class HandLights {
    private static final double RANGE = 24;
    /** A torch's light is a level dimmer every FADE blocks it travels. */
    private static final double FADE = 6;
    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
    private static final Map<UUID, Map<BlockPos, Integer>> LIT = new HashMap<>();
    private static ClientLevel litIn;

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(HandLights::tick);
    }

    private static void tick(Minecraft client) {
        ClientLevel level = client.level;
        if (level != litIn) {
            LIT.clear();
            litIn = level;
        }
        if (level == null) {
            return;
        }
        Map<UUID, Map<BlockPos, Integer>> wanted = new HashMap<>();
        for (Player player : level.players()) {
            if (player.isSpectator()) {
                continue;
            }
            Map<BlockPos, Integer> had = LIT.getOrDefault(player.getUUID(), Map.of()), want = new HashMap<>();
            for (ItemStack held : new ItemStack[] {player.getMainHandItem(), player.getOffhandItem()}) {
                if (LightItem.lit(held)) {
                    shine(level, player, (LightItem) held.getItem(), had, want);
                }
            }
            wanted.put(player.getUUID(), want);
        }
        // out with the lights no one's holding any more
        for (Map.Entry<UUID, Map<BlockPos, Integer>> e : LIT.entrySet()) {
            Map<BlockPos, Integer> want = wanted.getOrDefault(e.getKey(), Map.of());
            for (BlockPos old : e.getValue().keySet()) {
                if (!want.containsKey(old) && level.getBlockState(old).is(Blocks.LIGHT)) {
                    level.setBlock(old, Blocks.AIR.defaultBlockState(), FLAGS);
                }
            }
        }
        LIT.clear();
        wanted.forEach((id, want) -> {
            Map<BlockPos, Integer> placed = new HashMap<>();
            want.forEach((pos, brightness) -> {
                BlockState here = level.getBlockState(pos);
                if (here.is(Blocks.LIGHT) || here.isAir()) {
                    if (!here.is(Blocks.LIGHT) || here.getValue(LightBlock.LEVEL) != brightness) {
                        level.setBlock(pos, Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, brightness), FLAGS);
                    }
                    placed.put(pos, brightness);
                }
            });
            if (!placed.isEmpty()) {
                LIT.put(id, placed);
            }
        });
    }

    private static void shine(ClientLevel level, Player player, LightItem light, Map<BlockPos, Integer> had,
                              Map<BlockPos, Integer> want) {
        Vec3 eye = player.getEyePosition();
        int bright = light.brightness();
        if (light.kind() == LightItem.Kind.AROUND) {
            put(level, BlockPos.containing(eye), bright, had, want);
            return;
        }
        Vec3 look = player.getLookAngle();
        BlockHitResult hit = level.clip(new ClipContext(eye, eye.add(look.scale(RANGE)), ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, player));
        double far = hit.getType() == HitResult.Type.MISS ? RANGE : eye.distanceTo(hit.getLocation());
        int pool = Math.max(4, bright - (int) (far / FADE));
        for (double back = 0.5; back < 3 && far - back > 0.5; back += 0.5) {
            if (put(level, BlockPos.containing(eye.add(look.scale(far - back))), pool, had, want)) {
                break;
            }
        }
        if (far > 6) {
            put(level, BlockPos.containing(eye.add(look.scale(far / 2))), Math.max(3, pool - 3), had, want);
        }
        put(level, player.blockPosition(), Math.max(3, bright / 2 - 1), had, want);
    }

    /** A light in the air at p, or just above it through grass and the like. False when something solid's there. */
    private static boolean put(ClientLevel level, BlockPos p, int brightness, Map<BlockPos, Integer> had,
                               Map<BlockPos, Integer> want) {
        for (int up = 0; up < 3; up++) {
            BlockPos q = p.above(up);
            BlockState here = level.getBlockState(q);
            if (here.isAir() || here.is(Blocks.LIGHT) && (had.containsKey(q) || want.containsKey(q))) {
                want.merge(q, brightness, Math::max);
                return true;
            }
            if (!here.getCollisionShape(level, q).isEmpty()) {
                return false;
            }
        }
        return false;
    }

    private HandLights() {
    }
}
