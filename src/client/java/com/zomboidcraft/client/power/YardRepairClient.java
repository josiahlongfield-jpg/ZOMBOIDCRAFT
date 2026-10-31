package com.zomboidcraft.client.power;

import com.zomboidcraft.client.interact.InteractMenu;
import com.zomboidcraft.skills.Skill;
import com.zomboidcraft.skills.Skills;
import com.zomboidcraft.world.gen.YardRepair;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/** Held G at a substation's breaker, fuse board, cable drum or switchgear: that stop of the repair (YardRepair). */
public final class YardRepairClient {
    private YardRepairClient() {
    }

    public static void register() {
        InteractMenu.provider((client, player, hit, out) -> {
            if (!(hit instanceof BlockHitResult b) || hit.getType() != HitResult.Type.BLOCK || client.level == null
                    || b.getLocation().distanceTo(player.getEyePosition()) > 4.5) {
                return;
            }
            YardRepair.Part part = YardRepair.Part.of(client.level.getBlockState(b.getBlockPos()));
            if (part == null || !nearYard(client.level, b.getBlockPos())) {
                return;
            }
            if (Skills.level(player, Skill.ELECTRICAL) < part.level) {
                out.add(InteractMenu.Option.no(part.label, "Needs Electrical " + part.level));
            } else if (part != YardRepair.Part.SWITCH && !YardRepair.screwdriver(player)) {
                out.add(InteractMenu.Option.no(part.label, "Needs a screwdriver in hand"));
            } else if (!YardRepair.has(player, part)) {
                StringBuilder s = new StringBuilder("Needs ");
                part.needs().forEach((k, v) -> s.append(s.length() > 6 ? ", " : "").append(v).append(' ').append(k.replace('_', ' ')));
                out.add(InteractMenu.Option.no(part.label, s.toString()));
            } else {
                BlockPos pos = b.getBlockPos();
                float secs = part.ticks / 20f / (Skills.level(player, Skill.ELECTRICAL) >= 8 ? 2 : 1);
                out.add(InteractMenu.Option.timed(part.label, secs, () -> ClientPlayNetworking.send(new YardRepair.Fix(pos))));
            }
        });
    }

    /** A substation's transformer, bushings or gantry legs close by (the same fittings stand in bunkers and halls). */
    private static boolean nearYard(ClientLevel level, BlockPos at) {
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int x = -20; x <= 20; x++) {
            for (int z = -20; z <= 20; z++) {
                for (int y = -3; y <= 12; y++) {
                    p.set(at.getX() + x, at.getY() + y, at.getZ() + z);
                    String path = BuiltInRegistries.BLOCK.getKey(level.getBlockState(p).getBlock()).getPath();
                    if (path.equals("pw_xfmr") || path.startsWith("pw_bushing") || path.equals("pw_breaker")) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
