package com.zomboidcraft.skills;

import com.mojang.serialization.Codec;
import com.zomboidcraft.ZomboidCraft;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * Skill XP per player, keyed by skill name. Like Zomboid, it's lost on death.
 */
public final class Skills {
    private static final Codec<Map<String, Integer>> CODEC = Codec.unboundedMap(Codec.STRING, Codec.INT);
    private static final StreamCodec<io.netty.buffer.ByteBuf, Map<String, Integer>> STREAM_CODEC =
            ByteBufCodecs.map(HashMap::new, ByteBufCodecs.STRING_UTF8, ByteBufCodecs.VAR_INT);

    public static final AttachmentType<Map<String, Integer>> TYPE = AttachmentRegistry.create(
            ZomboidCraft.id("skills"),
            builder -> builder
                    .persistent(CODEC)
                    .initializer(Map::of)
                    .syncWith(STREAM_CODEC, AttachmentSyncPredicate.targetOnly()));

    public static void register() {
        // Loading the class registers the attachment.
    }

    public static int xp(Player player, Skill skill) {
        return player.getAttachedOrCreate(TYPE).getOrDefault(skill.name(), 0);
    }

    public static int level(Player player, Skill skill) {
        return Skill.levelForXp(xp(player, skill));
    }

    public static void addXp(ServerPlayer player, Skill skill, int amount) {
        if (amount <= 0 || player.isCreative()) {
            return;
        }
        int before = xp(player, skill);
        int after = before + amount;
        Map<String, Integer> updated = new HashMap<>(player.getAttachedOrCreate(TYPE));
        updated.put(skill.name(), after);
        player.setAttached(TYPE, updated);

        int newLevel = Skill.levelForXp(after);
        if (newLevel > Skill.levelForXp(before)) {
            player.sendOverlayMessage(Component.literal(skill.displayName() + " increased to level " + newLevel)
                    .withStyle(ChatFormatting.GOLD));
        }
    }

    private Skills() {
    }
}
