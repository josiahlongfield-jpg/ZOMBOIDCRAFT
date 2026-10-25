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

/** Right-click to bind your worst open wound: bleeding ones first, then the deepest. */
public class BandageItem extends Item {
    public BandageItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        Body body = Body.get(player);
        Region target = null;
        int best = -1;
        for (Region r : Region.values()) {
            Part p = body.part(r);
            if (!p.wounded() || p.bandaged()) continue;
            int score = (p.bleeding() ? 10 : 0) + p.wound().ordinal();
            if (score > best) {
                best = score;
                target = r;
            }
        }
        if (target == null) {
            return InteractionResult.PASS;
        }
        if (level instanceof ServerLevel && player instanceof ServerPlayer serverPlayer) {
            // First Aid: level 5+ binds a second wound too, and every level heals a little.
            int firstAid = Skills.level(player, Skill.FIRST_AID);
            body = bind(body, target);
            if (firstAid >= 5) {
                for (Region r : Region.values()) {
                    Part p = body.part(r);
                    if (p.wounded() && !p.bandaged()) {
                        body = bind(body, r);
                        break;
                    }
                }
            }
            Body.set(player, body);
            if (firstAid > 0) {
                player.heal(0.5f * firstAid);
            }
            Skills.addXp(serverPlayer, Skill.FIRST_AID, 10);
            player.getItemInHand(hand).consume(1, player);
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    ModSounds.BANDAGE, SoundSource.PLAYERS, 1f, 0.95f + level.getRandom().nextFloat() * 0.1f);
            serverPlayer.sendOverlayMessage(Component.literal("You bandage your " + target.label + "."));
        }
        return InteractionResult.SUCCESS;
    }

    private static Body bind(Body body, Region r) {
        Part p = body.part(r);
        return body.with(r, new Part(p.wound(), p.woundLeft(), false, true, p.fractureLeft(), p.splinted()));
    }
}
