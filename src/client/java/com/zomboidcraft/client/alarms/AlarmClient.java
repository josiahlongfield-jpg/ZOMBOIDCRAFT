package com.zomboidcraft.client.alarms;

import com.zomboidcraft.ZomboidCraft;
import com.zomboidcraft.client.interact.InteractMenu;
import com.zomboidcraft.client.interact.Interactables;
import com.zomboidcraft.skills.Skill;
import com.zomboidcraft.skills.Skills;
import com.zomboidcraft.world.gen.HouseAlarms;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/** House alarms (world/gen/HouseAlarms): the green dot on the keypad and bell box, and held G's "Cut the alarm". */
public final class AlarmClient {
    private AlarmClient() {
    }

    public static void register() {
        Interactables.blocks(HouseAlarms::fitting);
        InteractMenu.provider((client, player, hit, out) -> {
            if (!(hit instanceof BlockHitResult b) || hit.getType() != HitResult.Type.BLOCK || client.level == null
                    || b.getLocation().distanceTo(player.getEyePosition()) > 4.5
                    || !HouseAlarms.fitting(client.level.getBlockState(b.getBlockPos()))) {
                return;
            }
            String label = "Cut the alarm wires";
            int need = HouseAlarms.cutLevel();
            if (Skills.level(player, Skill.ELECTRICAL) < need) {
                out.add(InteractMenu.Option.no(label, "Needs Electrical " + need));
                return;
            }
            var driver = ZomboidCraft.id("screwdriver");
            if (!BuiltInRegistries.ITEM.getKey(player.getMainHandItem().getItem()).equals(driver)
                    && !BuiltInRegistries.ITEM.getKey(player.getOffhandItem().getItem()).equals(driver)) {
                out.add(InteractMenu.Option.no(label, "Needs a screwdriver in hand"));
                return;
            }
            BlockPos pos = b.getBlockPos();
            float seconds = Skills.level(player, Skill.ELECTRICAL) >= 8 ? 3 : 6;
            out.add(InteractMenu.Option.timed(label, seconds, () -> ClientPlayNetworking.send(new HouseAlarms.Cut(pos))));
        });
    }
}
