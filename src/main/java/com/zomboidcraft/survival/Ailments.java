package com.zomboidcraft.survival;

import com.mojang.serialization.Codec;
import com.zomboidcraft.ZomboidCraft;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.entity.player.Player;

/**
 * Everything medicine tracks beyond Body's wounds and breaks, as named numbers: per part ("dirty:2", "infect:2",
 * "deep:2", "lodged:2", "stitched:2", ...; the part is Body.Region's ordinal) and for the whole of you ("pain",
 * "blood_lost", "cold", "nausea", "smoke", "tooth", drug timers in seconds, "read:..." flags, "downed").
 * A missing key is 0, so a healthy survivor carries an empty map. Saved, and synced to the player's own client
 * for the HUD and aim shake. See {@link Medicine}.
 */
public final class Ailments {
    private static final Codec<Map<String, Integer>> CODEC = Codec.unboundedMap(Codec.STRING, Codec.INT);
    private static final StreamCodec<io.netty.buffer.ByteBuf, Map<String, Integer>> STREAM_CODEC =
            ByteBufCodecs.map(HashMap::new, ByteBufCodecs.STRING_UTF8, ByteBufCodecs.VAR_INT);

    public static final AttachmentType<Map<String, Integer>> TYPE = AttachmentRegistry.create(
            ZomboidCraft.id("ailments"),
            builder -> builder.persistent(CODEC).initializer(Map::of)
                    .syncWith(STREAM_CODEC, AttachmentSyncPredicate.targetOnly()));

    public static void register() {
        // loading the class registers the attachment
    }

    public static int get(Player player, String key) {
        return player.getAttachedOrElse(TYPE, Map.of()).getOrDefault(key, 0);
    }

    public static int get(Player player, String key, Body.Region part) {
        return get(player, key + ":" + part.ordinal());
    }

    /** A copy to change; hand it back with {@link #save}. */
    public static Map<String, Integer> edit(Player player) {
        return new HashMap<>(player.getAttachedOrElse(TYPE, Map.of()));
    }

    /** Stores the map, dropping zeros, and only syncs when something changed. */
    public static void save(Player player, Map<String, Integer> map) {
        map.values().removeIf(v -> v == 0);
        if (!map.equals(player.getAttachedOrElse(TYPE, Map.of()))) player.setAttached(TYPE, Map.copyOf(map));
    }

    public static void set(Player player, String key, int value) {
        Map<String, Integer> map = edit(player);
        map.put(key, value);
        save(player, map);
    }

    public static void clear(Player player) {
        player.removeAttached(TYPE);
    }

    private Ailments() {
    }
}
