package com.zomboidcraft.survival.character;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.zomboidcraft.ZomboidCraft;
import com.zomboidcraft.clothing.Grooming;
import io.netty.buffer.ByteBuf;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * What your survivor looks like, picked on the page after the traits (client/kit/AppearanceScreen): a woman or a man,
 * the body's skin pack (Tomb's Player Body, Tomb's Goth, Detailed Player Texture or Evil's Ink for women; Tomb's or
 * Goth for men), skin tone, build, face, body hair and make-up. Hair, beard and hair colour go to {@link Grooming},
 * so they grow out and get cut like any other.
 * <p>
 * The bodies themselves are Zomboid's and the mods' own files, which never ship: they're read from the survivor packs in
 * config/zomboidcraft/npc on each PC (tools/pz/survivor_pack.py). This only holds the choices, by name, synced to every
 * player so each client draws everyone with the files it has; a client without the packs draws the usual player.
 * Like the traits, the look belongs to this survivor: a death starts a new one.
 */
public final class Appearance {
    /**
     * The look, by the names the packs' catalogs use ("" for none). The stubble pair says which of the mods' stubble
     * textures show while the beard (or a shaved head) is growing back in; whether it's showing is Grooming's.
     */
    public record Look(String sex, String pack, int tone, String build, boolean hairy, String face, String bodyHair,
                       String eyes, String lips, String paint, String beardStubble, String headStubble) {
        public static final Codec<Look> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("sex").forGetter(Look::sex),
                Codec.STRING.fieldOf("pack").forGetter(Look::pack),
                Codec.INT.fieldOf("tone").forGetter(Look::tone),
                Codec.STRING.fieldOf("build").forGetter(Look::build),
                Codec.BOOL.fieldOf("hairy").forGetter(Look::hairy),
                Codec.STRING.fieldOf("face").forGetter(Look::face),
                Codec.STRING.fieldOf("body_hair").forGetter(Look::bodyHair),
                Codec.STRING.fieldOf("eyes").forGetter(Look::eyes),
                Codec.STRING.fieldOf("lips").forGetter(Look::lips),
                Codec.STRING.fieldOf("paint").forGetter(Look::paint),
                Codec.STRING.optionalFieldOf("beard_stubble", "").forGetter(Look::beardStubble),
                Codec.STRING.optionalFieldOf("head_stubble", "").forGetter(Look::headStubble)
        ).apply(i, Look::new));
        public static final StreamCodec<ByteBuf, Look> STREAM_CODEC = StreamCodec.of(Look::write, Look::read);

        private static void write(ByteBuf buf, Look l) {
            for (String s : new String[]{l.sex, l.pack}) ByteBufCodecs.STRING_UTF8.encode(buf, s);
            ByteBufCodecs.VAR_INT.encode(buf, l.tone);
            ByteBufCodecs.STRING_UTF8.encode(buf, l.build);
            ByteBufCodecs.BOOL.encode(buf, l.hairy);
            for (String s : new String[]{l.face, l.bodyHair, l.eyes, l.lips, l.paint, l.beardStubble, l.headStubble}) {
                ByteBufCodecs.STRING_UTF8.encode(buf, s);
            }
        }

        private static Look read(ByteBuf buf) {
            String sex = ByteBufCodecs.STRING_UTF8.decode(buf), pack = ByteBufCodecs.STRING_UTF8.decode(buf);
            int tone = ByteBufCodecs.VAR_INT.decode(buf);
            String build = ByteBufCodecs.STRING_UTF8.decode(buf);
            boolean hairy = ByteBufCodecs.BOOL.decode(buf);
            String face = ByteBufCodecs.STRING_UTF8.decode(buf), bodyHair = ByteBufCodecs.STRING_UTF8.decode(buf);
            String eyes = ByteBufCodecs.STRING_UTF8.decode(buf), lips = ByteBufCodecs.STRING_UTF8.decode(buf);
            String paint = ByteBufCodecs.STRING_UTF8.decode(buf), beardStubble = ByteBufCodecs.STRING_UTF8.decode(buf);
            return new Look(sex, pack, tone, build, hairy, face, bodyHair, eyes, lips, paint, beardStubble, ByteBufCodecs.STRING_UTF8.decode(buf));
        }

        public boolean female() {
            return !"m".equals(sex);
        }

        /** The same look kept within what any look may say: short names, a tone of 1 to 5. */
        Look clean() {
            return new Look("m".equals(sex) ? "m" : "f", cut(pack), Math.max(1, Math.min(5, tone)), cut(build), hairy && "m".equals(sex),
                    cut(face), cut(bodyHair), cut(eyes), cut(lips), cut(paint), cut(beardStubble), cut(headStubble));
        }

        private static String cut(String s) {
            return s == null ? "" : s.length() > 48 ? s.substring(0, 48) : s;
        }
    }

    /** Synced to every player: everyone draws everyone. Not kept through death (a new survivor is a new person). */
    public static final AttachmentType<Look> TYPE = AttachmentRegistry.create(ZomboidCraft.id("appearance"),
            builder -> builder.persistent(Look.CODEC).syncWith(Look.STREAM_CODEC, AttachmentSyncPredicate.all()));

    /**
     * The look from the appearance page, with the hair and beard it starts with ("pz:Name#length", Zomboid's own styles,
     * see {@link Grooming#rank}) and the hair colour (one of {@link Grooming#COLOURS}).
     */
    public record Set(Look look, String hair, String beard, String colour) implements CustomPacketPayload {
        public static final Type<Set> TYPE = new Type<>(ZomboidCraft.id("appearance_set"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Set> CODEC = StreamCodec.composite(
                Look.STREAM_CODEC.cast(), Set::look,
                ByteBufCodecs.STRING_UTF8, Set::hair,
                ByteBufCodecs.STRING_UTF8, Set::beard,
                ByteBufCodecs.STRING_UTF8, Set::colour,
                Set::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    static void register() {
        PayloadTypeRegistry.serverboundPlay().register(Set.TYPE, Set.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(Set.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            player.level().getServer().execute(() -> set(player, payload));
        });
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
            if (!alive) return;
            Look l = oldPlayer.getAttached(TYPE);
            if (l != null) newPlayer.setAttached(TYPE, l);    // through an end portal: the same person
        });
    }

    /** Made once per survivor, on the page after the traits; a creative player may make it again. */
    private static void set(ServerPlayer player, Set set) {
        if (player.hasAttached(TYPE) && !player.isCreative()) return;
        Look look = set.look().clean();
        player.setAttached(TYPE, look);
        long now = player.level().getGameTime();
        Grooming.Look was = Grooming.look(player);
        String hair = Grooming.rank(true, set.hair()) != null ? set.hair() : was.hair();
        String beard = Grooming.rank(false, set.beard()) != null ? set.beard() : "none";
        String colour = Grooming.COLOURS.contains(set.colour()) ? set.colour() : was.colour();
        // men's beards grow back, as in Zomboid; women's don't
        player.setAttached(Grooming.LOOK, new Grooming.Look(hair, now, beard, now, colour, !look.female()));
    }

    public static Look of(Player player) {
        return player.getAttached(TYPE);
    }

    private Appearance() {
    }
}
