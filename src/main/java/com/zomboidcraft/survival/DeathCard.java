package com.zomboidcraft.survival;

import com.mojang.serialization.Codec;
import com.zomboidcraft.ZcConfig;
import com.zomboidcraft.ZomboidCraft;
import com.zomboidcraft.skills.Skill;
import com.zomboidcraft.skills.Skills;
import com.zomboidcraft.spawn.SpawnNet;
import com.zomboidcraft.spawn.SpawnPicker;
import com.zomboidcraft.zombie.ZombieTypes;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/**
 * Zomboid's "This is how you died" (PZ review section 18, Josiah 2026-10-31): how long you lasted, how many zombies you
 * put down, what got you and where, whether you'll rise, and the skills that go with you. Kept per life: the clock
 * and the count start again with the next character.
 */
public final class DeathCard {
    /** The world's game time when this life began. */
    public static final AttachmentType<Long> LIFE_START = AttachmentRegistry.create(ZomboidCraft.id("life_start"),
            builder -> builder.persistent(Codec.LONG));
    /** Zombies this life has put down. */
    public static final AttachmentType<Integer> KILLS = AttachmentRegistry.create(ZomboidCraft.id("life_kills"),
            builder -> builder.persistent(Codec.INT));

    /** What the card shows. Skills are "Name level" pairs, highest first; place is blank off the county. */
    public record Card(String name, long ticks, int kills, String cause, String place, int x, int z, boolean rises,
                       List<String> skills) implements CustomPacketPayload {
        public static final Type<Card> TYPE = new Type<>(ZomboidCraft.id("death_card"));
        public static final StreamCodec<ByteBuf, Card> CODEC = StreamCodec.of((buf, c) -> {
            ByteBufCodecs.STRING_UTF8.encode(buf, c.name);
            ByteBufCodecs.VAR_LONG.encode(buf, c.ticks);
            ByteBufCodecs.VAR_INT.encode(buf, c.kills);
            ByteBufCodecs.STRING_UTF8.encode(buf, c.cause);
            ByteBufCodecs.STRING_UTF8.encode(buf, c.place);
            ByteBufCodecs.VAR_INT.encode(buf, c.x);
            ByteBufCodecs.VAR_INT.encode(buf, c.z);
            ByteBufCodecs.BOOL.encode(buf, c.rises);
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()).encode(buf, c.skills);
        }, buf -> new Card(ByteBufCodecs.STRING_UTF8.decode(buf), ByteBufCodecs.VAR_LONG.decode(buf), ByteBufCodecs.VAR_INT.decode(buf),
                ByteBufCodecs.STRING_UTF8.decode(buf), ByteBufCodecs.STRING_UTF8.decode(buf), ByteBufCodecs.VAR_INT.decode(buf),
                ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.BOOL.decode(buf), ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()).decode(buf)));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public static void register() {
        PayloadTypeRegistry.clientboundPlay().register(Card.TYPE, Card.CODEC);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> server.execute(() -> {
            ServerPlayer p = handler.getPlayer();
            if (!p.hasAttached(LIFE_START)) p.setAttached(LIFE_START, p.level().getGameTime());
        }));
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
            if (alive) {
                newPlayer.setAttached(LIFE_START, oldPlayer.getAttachedOrElse(LIFE_START, newPlayer.level().getGameTime()));
                newPlayer.setAttached(KILLS, oldPlayer.getAttachedOrElse(KILLS, 0));
            } else {
                newPlayer.setAttached(LIFE_START, newPlayer.level().getGameTime());
                newPlayer.setAttached(KILLS, 0);
            }
        });
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (source.getEntity() instanceof ServerPlayer killer && ZombieTypes.isWalker(entity)) {
                killer.setAttached(KILLS, killer.getAttachedOrElse(KILLS, 0) + 1);
            }
            if (entity instanceof ServerPlayer player) {
                ServerPlayNetworking.send(player, card(player, source.getLocalizedDeathMessage(player).getString()));
            }
        });
    }

    /** The card for someone who has just died (the UI test sends one too). */
    public static Card card(ServerPlayer player, String cause) {
        long start = player.getAttachedOrElse(LIFE_START, player.level().getGameTime());
        List<String> skills = new ArrayList<>();
        List<Skill> all = new ArrayList<>(List.of(Skill.values()));
        all.sort((a, b) -> Integer.compare(Skills.level(player, b), Skills.level(player, a)));
        for (Skill s : all) {
            int lv = Skills.level(player, s);
            if (lv > 0) skills.add(s.displayName() + " " + lv);
        }
        boolean rises = SurvivalStats.get(player).infected() && ZcConfig.get().survival.reanimate;
        return new Card(player.getName().getString(), Math.max(0, player.level().getGameTime() - start),
                player.getAttachedOrElse(KILLS, 0), cause, place(player), player.getBlockX(), player.getBlockZ(), rises, skills);
    }

    /** The nearest named place on the county map, within a few hundred blocks, or blank. */
    private static String place(ServerPlayer player) {
        if (!SpawnPicker.applies(player)) return "";
        SpawnNet.Place best = null;
        double bestD = 400 * 400;
        for (SpawnNet.Place p : SpawnPicker.places()) {
            double dx = p.x() - player.getX(), dz = p.z() - player.getZ(), d = dx * dx + dz * dz;
            if (d < bestD) {
                bestD = d;
                best = p;
            }
        }
        if (best == null) return "";
        return best.group() == null || best.group().isBlank() || best.group().equals(best.name()) ? best.name() : best.name() + ", " + best.group();
    }

    private DeathCard() {
    }
}
