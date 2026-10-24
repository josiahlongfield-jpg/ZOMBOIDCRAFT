package com.zomboidcraft.zombie;

import com.mojang.serialization.Codec;
import com.zomboidcraft.ZcConfig;
import com.zomboidcraft.ZomboidCraft;
import java.util.List;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.entity.Entity;

/**
 * What each zombie is wearing. Most zombies get an outfit picked from their UUID, so client
 * and server agree without syncing anything. Zombies spawned somewhere specific (a police
 * station, a hospital) can be given an outfit explicitly, which is saved and synced.
 */
public final class ZombieLook {
    public static final List<String> SKINS = List.of("pale", "olive", "dark");

    private record Outfit(String name, int weight) {
    }

    /** Outfits for zombies found out and about, weighted by how common they are. */
    private static final List<Outfit> GENERAL = List.of(
            new Outfit("civilian", 30), new Outfit("hoodie", 12), new Outfit("office", 10),
            new Outfit("farmer", 6), new Outfit("construction", 6), new Outfit("jogger", 5),
            new Outfit("police", 5), new Outfit("nurse", 4), new Outfit("security", 4),
            new Outfit("patient", 3), new Outfit("soldier", 3), new Outfit("survivor", 3),
            new Outfit("doctor", 2), new Outfit("firefighter", 2), new Outfit("prisoner", 2));
    private static final int TOTAL_WEIGHT = GENERAL.stream().mapToInt(Outfit::weight).sum();

    public static final AttachmentType<String> OUTFIT = AttachmentRegistry.create(
            ZomboidCraft.id("outfit"),
            builder -> builder
                    .persistent(Codec.STRING)
                    .syncWith(ByteBufCodecs.STRING_UTF8, AttachmentSyncPredicate.all()));

    public static void register() {
        // Loading the class registers the attachment.
    }

    public static String outfit(Entity zombie) {
        String chosen = zombie.getAttached(OUTFIT);
        if (chosen != null) {
            return chosen;
        }
        int roll = Math.floorMod(zombie.getUUID().getMostSignificantBits() >>> 8, TOTAL_WEIGHT);
        for (Outfit outfit : GENERAL) {
            roll -= outfit.weight();
            if (roll < 0) {
                return outfit.name();
            }
        }
        return "civilian";
    }

    public static String skin(Entity zombie) {
        return SKINS.get(Math.floorMod(zombie.getUUID().getMostSignificantBits(), SKINS.size()));
    }

    /**
     * How far gone the body is: 0 fresh, 1 medium, 2 rotten and soaked, 3 bones showing.
     * Capped by the gore setting.
     */
    public static int damage(Entity zombie) {
        int max = Math.clamp(ZcConfig.get().look.maxGore, 0, 3);
        int roll = Math.floorMod(zombie.getUUID().getLeastSignificantBits() >>> 20, 100);
        int level = roll < 30 ? 0 : roll < 60 ? 1 : roll < 85 ? 2 : 3;
        return Math.min(level, max);
    }

    /** 0 none, 1 right forearm, 2 left forearm, 3 right arm, 4 left arm. */
    public static int missingLimb(Entity zombie) {
        long bits = zombie.getUUID().getLeastSignificantBits() >>> 32;
        if (Math.floorMod(bits, 10000) >= ZcConfig.get().look.missingLimbChance * 10000) {
            return 0;
        }
        int pick = (int) Math.floorMod(bits >>> 16, 4L);  // three in four lose only the forearm
        boolean left = Math.floorMod(bits >>> 20, 2L) == 1;
        return pick < 3 ? (left ? 2 : 1) : (left ? 4 : 3);
    }

    /** Texture name under textures/entity/walker, such as "police_pale". */
    public static String textureName(Entity zombie) {
        return outfit(zombie) + "_" + skin(zombie);
    }

    private ZombieLook() {
    }
}
