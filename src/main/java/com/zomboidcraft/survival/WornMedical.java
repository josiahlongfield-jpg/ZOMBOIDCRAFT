package com.zomboidcraft.survival;

import com.mojang.serialization.Codec;
import com.zomboidcraft.ZomboidCraft;
import com.zomboidcraft.survival.Body.Part;
import com.zomboidcraft.survival.Body.Region;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * What medical kit shows on a player's body, for everyone's client to draw (clothing/ClothingLayer): one bitmask
 * per Body.Region ordinal. Refreshed every second from Body and Ailments (Medicine.tick).
 */
public final class WornMedical {
    public static final int BANDAGE = 1, PACKED = 2, WRAP = 4, TOURNIQUET = 8, SPLINT = 16, FINGER_SPLINT = 32,
            MOLDABLE_SPLINT = 64, SLING = 128, COLLAR = 256, KNEE_BRACE = 512;

    private static final List<Integer> NONE = List.of(0, 0, 0, 0, 0, 0);

    public static final AttachmentType<List<Integer>> TYPE = AttachmentRegistry.create(
            ZomboidCraft.id("worn_medical"),
            builder -> builder.initializer(() -> NONE)
                    .syncWith(ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list()), AttachmentSyncPredicate.all()));

    public static void register() {
        // loading the class registers the attachment
    }

    /** The bits on one part (0 when nothing shows). */
    public static int on(Player player, Region part) {
        List<Integer> worn = player.getAttachedOrElse(TYPE, NONE);
        return part.ordinal() < worn.size() ? worn.get(part.ordinal()) : 0;
    }

    static void refresh(ServerPlayer player) {
        Body body = Body.get(player);
        Map<String, Integer> m = player.getAttachedOrElse(Ailments.TYPE, Map.of());
        List<Integer> worn = new ArrayList<>(NONE);
        for (Region r : Region.values()) {
            Part p = body.part(r);
            int bits = 0;
            if (p.bandaged()) bits |= BANDAGE;
            if (Medicine.get(m, "packed", r) > 0) bits |= PACKED;
            if (Medicine.get(m, "wrap", r) > 0) bits |= WRAP;
            if (Medicine.get(m, "tq", r) > 0) bits |= TOURNIQUET;
            if (Medicine.get(m, "sling", r) > 0) bits |= SLING;
            if (p.fractured() && p.splinted()) {
                int kind = Medicine.get(m, "splintkind", r);
                bits |= kind == 0 ? (r == Region.HEAD || r == Region.TORSO ? COLLAR : SPLINT) : kind;
            }
            worn.set(r.ordinal(), bits);
        }
        if (MedSpecs.carries(player, "knee_brace")) {
            Region leg = body.part(Region.LEFT_LEG).fine() || !body.part(Region.RIGHT_LEG).fine() ? Region.RIGHT_LEG : Region.LEFT_LEG;
            worn.set(leg.ordinal(), worn.get(leg.ordinal()) | KNEE_BRACE);
        }
        if (!worn.equals(player.getAttachedOrElse(TYPE, NONE))) player.setAttached(TYPE, List.copyOf(worn));
    }

    private WornMedical() {
    }
}
