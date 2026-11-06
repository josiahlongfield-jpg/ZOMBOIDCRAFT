package com.zomboidcraft.zombie;

import com.zomboidcraft.survival.character.Traits;
import net.minecraft.world.entity.player.Player;

/**
 * The character traits that touch the dead's senses and your own, as Zomboid has them. Inconspicuous and Conspicuous
 * change how far zombies pick you out; Graceful and Clumsy how far your footsteps carry; Keen Hearing, Hard of Hearing
 * and Deaf how well you hear (the player's ears, not the zombies'). Traits are synced to their own client, so the
 * hearing side runs there. The numbers stand in until SURVIVAL has the B42 ones.
 */
public final class TraitSenses {
    /** Zombies see you this much as far. */
    public static double sight(Player player) {
        if (Traits.has(player, "inconspicuous")) {
            return 0.5;
        }
        return Traits.has(player, "conspicuous") ? 2.0 : 1.0;
    }

    /** Your footsteps carry this much as far. */
    public static double footsteps(Player player) {
        if (Traits.has(player, "graceful")) {
            return 0.6;
        }
        return Traits.has(player, "clumsy") ? 1.2 : 1.0;
    }

    /** The dead's sounds (voices, hordes, steps, bashing) play this loud for you. */
    public static float zombieSounds(Player player) {
        return Traits.has(player, "keen_hearing") ? 1.35f : world(player);
    }

    /** Every world sound (not music or the menus) plays this loud for you. */
    public static float world(Player player) {
        if (Traits.has(player, "deaf")) {
            return 0.1f;
        }
        return Traits.has(player, "hard_of_hearing") ? 0.5f : 1f;
    }

    private TraitSenses() {
    }
}
