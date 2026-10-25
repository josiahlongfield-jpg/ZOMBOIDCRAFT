package com.zomboidcraft.survival;

import com.zomboidcraft.combat.ModSounds;
import com.zomboidcraft.skills.Skill;
import com.zomboidcraft.skills.Skills;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

/** Right-click to bind a bleeding wound. */
public class BandageItem extends Item {
    public BandageItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        SurvivalStats stats = SurvivalStats.get(player);
        if (stats.wounds() == 0) {
            return InteractionResult.PASS;
        }
        if (level instanceof ServerLevel && player instanceof ServerPlayer serverPlayer) {
            // First Aid: level 5+ binds two wounds at once, and every level heals a little.
            int firstAid = Skills.level(player, Skill.FIRST_AID);
            SurvivalStats.set(player, stats.withWounds(stats.wounds() - (firstAid >= 5 ? 2 : 1)));
            if (firstAid > 0) {
                player.heal(0.5f * firstAid);
            }
            Skills.addXp(serverPlayer, Skill.FIRST_AID, 10);
            player.getItemInHand(hand).consume(1, player);
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    ModSounds.BANDAGE, SoundSource.PLAYERS, 1f, 0.95f + level.getRandom().nextFloat() * 0.1f);
        }
        return InteractionResult.SUCCESS;
    }
}
