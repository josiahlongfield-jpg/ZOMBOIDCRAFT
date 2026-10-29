package com.zomboidcraft.client.creature;

import com.zomboidcraft.creature.HarvestmanEntity;
import com.zomboidcraft.creature.HarvestmanGlimpse;
import com.zomboidcraft.creature.Harvestmen;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * A Harvestman only you see (HarvestmanGlimpse): stood at the edge of your sight, still, facing you. Look straight at
 * it for a moment and it isn't there; look away and it's gone anyway, after a few seconds. It lives only in this game,
 * never in the world, so it can't be shot and leaves no trace.
 */
public final class HarvestmanPhantoms {
    private static final List<Phantom> SHOWN = new ArrayList<>();
    private static int nextId = -2_000_000;

    private record Phantom(HarvestmanEntity e, long until, int[] looked) {
    }

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(HarvestmanGlimpse.Glimpse.TYPE, (payload, context) ->
                context.client().execute(() -> show(payload)));
        ClientTickEvents.END_CLIENT_TICK.register(HarvestmanPhantoms::tick);
    }

    private static void show(HarvestmanGlimpse.Glimpse g) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.player == null) {
            return;
        }
        HarvestmanEntity e = Harvestmen.HARVESTMAN.create(level, EntitySpawnReason.EVENT);
        if (e == null) {
            return;
        }
        e.setId(nextId--);
        Vec3 p = g.pos();
        e.snapTo(p.x, p.y, p.z, 0f, 0f);
        e.standPhantom(mc.player.getEyePosition());
        level.addEntity(e);
        SHOWN.add(new Phantom(e, level.getGameTime() + g.ticks(), new int[1]));
    }

    private static void tick(Minecraft mc) {
        ClientLevel level = mc.level;
        if (level == null || mc.player == null) {
            SHOWN.clear();
            return;
        }
        Vec3 eye = mc.player.getEyePosition();
        for (var it = SHOWN.iterator(); it.hasNext(); ) {
            Phantom ph = it.next();
            HarvestmanEntity e = ph.e();
            if (e.isRemoved() || e.level() != level) {
                it.remove();
                continue;
            }
            Vec3 to = e.centre().subtract(eye);
            double d = to.length();
            boolean looked = d > 1e-3 && mc.player.getLookAngle().dot(to.scale(1 / d)) > Math.cos(Math.toRadians(12))
                    && level.clip(new ClipContext(eye, e.centre(), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player))
                    .getType() == HitResult.Type.MISS;
            ph.looked()[0] = looked ? ph.looked()[0] + 1 : 0;
            // held in plain view for a breath, or walked up on: gone between one look and the next
            if (ph.looked()[0] > 8 || d < 9 || level.getGameTime() > ph.until()) {
                e.discard();
                it.remove();
            }
        }
    }

    private HarvestmanPhantoms() {
    }
}
