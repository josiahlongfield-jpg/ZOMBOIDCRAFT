package com.zomboidcraft.client.kit;

import com.zomboidcraft.survival.character.Traits;
import net.minecraft.world.entity.player.Player;

/** What the sight traits do to the picture: how far the fog sits, and how much the dark gives way. */
public final class SightTraits {
    /** Short Sighted sees 60% as far before the fog, Eagle Eyed 125% (Zomboid's -40% / +25% sight). */
    public static float fogScale(Player player) {
        if (Traits.has(player, "short_sighted")) return 0.6f;
        if (Traits.has(player, "eagle_eyed")) return 1.25f;
        return 1f;
    }

    /** Cat's Eyes: a fifth of night vision's lift, always on. */
    public static float nightVision(Player player) {
        return Traits.has(player, "cats_eyes") ? 0.2f : 0f;
    }

    private SightTraits() {
    }
}
