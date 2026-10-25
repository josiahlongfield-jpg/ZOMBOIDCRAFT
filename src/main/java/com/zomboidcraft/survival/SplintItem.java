package com.zomboidcraft.survival;

import com.zomboidcraft.combat.ModSounds;
import com.zomboidcraft.skills.Skill;
import com.zomboidcraft.skills.Skills;
import com.zomboidcraft.survival.Body.Part;
import com.zomboidcraft.survival.Body.Region;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

/** Two sticks and a strip of cloth: right-click to set a broken bone, legs first. It knits three times faster. */
public class SplintItem extends Item {
    private static final Region[] ORDER = {Region.LEFT_LEG, Region.RIGHT_LEG, Region.LEFT_ARM, Region.RIGHT_ARM,
            Region.TORSO, Region.HEAD};

    public SplintItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        Body body = Body.get(player);
        Region target = null;
        for (Region r : ORDER) {
            Part p = body.part(r);
            if (p.fractured() && !p.splinted()) {
                target = r;
                break;
            }
        }
        if (target == null) {
            return InteractionResult.PASS;
        }
        if (level instanceof ServerLevel && player instanceof ServerPlayer serverPlayer) {
            Part p = body.part(target);
            Body.set(player, body.with(target, new Part(p.wound(), p.woundLeft(), p.bleeding(), p.bandaged(), p.fractureLeft(), true)));
            Skills.addXp(serverPlayer, Skill.FIRST_AID, 15);
            player.getItemInHand(hand).consume(1, player);
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    ModSounds.BANDAGE, SoundSource.PLAYERS, 1f, 0.8f + level.getRandom().nextFloat() * 0.1f);
            serverPlayer.sendOverlayMessage(Component.literal("You splint your " + target.label + "."));
        }
        return InteractionResult.SUCCESS;
    }
}
