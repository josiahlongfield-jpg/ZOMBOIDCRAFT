package com.zomboidcraft.clothing;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.zomboidcraft.ZomboidCraft;
import com.zomboidcraft.survival.TimedActions;
import io.netty.buffer.ByteBuf;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/**
 * Hair and beards, Zomboid style. Hair grows out over the weeks and a beard over the days (if yours grows one), each
 * moving up a length every so often; scissors cut either to any style no longer than it is now, a razor shaves the
 * head or the face. "Own" is the skin's painted hair: nothing is drawn until it grows past it. The colour is yours to
 * set. The client draws what {@link #hair} and {@link #beard} say (ClothingLayer); a new life starts fresh.
 */
public final class Grooming {
    /** Days of growth to reach each length (0 shaved, 1 cropped, 2 short, 3 medium, 4 long). */
    private static final int[] HAIR_DAYS = {0, 4, 12, 30, 60}, BEARD_DAYS = {0, 1, 5, 15, 40};
    private static final String[] HAIR_GROWN = {"bald", "buzz", "short", "medium", "long"};
    private static final String[] BEARD_GROWN = {"none", "stubble", "short", "full", "long"};

    /** Every hair style by its length. "own" and "bald" draw nothing. */
    public static final Map<String, Integer> HAIR = new LinkedHashMap<>();
    public static final Map<String, Integer> BEARD = new LinkedHashMap<>();
    public static final List<String> COLOURS = List.of("dark_brown", "black", "brown", "auburn", "ginger", "blonde",
            "platinum", "grey", "white");

    static {
        HAIR.put("bald", 0);
        HAIR.put("buzz", 1);
        HAIR.put("own", 2);
        HAIR.put("short", 2);
        HAIR.put("side_part", 2);
        HAIR.put("spiky", 2);
        HAIR.put("mohawk", 2);
        HAIR.put("medium", 3);
        HAIR.put("bob", 3);
        HAIR.put("slicked_back", 3);
        HAIR.put("long", 4);
        HAIR.put("ponytail", 4);
        HAIR.put("bun", 4);
        HAIR.put("braid", 4);
        BEARD.put("none", 0);
        BEARD.put("stubble", 1);
        BEARD.put("moustache", 2);
        BEARD.put("goatee", 2);
        BEARD.put("chin_strap", 2);
        BEARD.put("short", 2);
        BEARD.put("full", 3);
        BEARD.put("long", 4);
    }

    /** What a survivor's hair and beard were last cut to, and when (game ticks; -1 until the first tick sets it). */
    public record Look(String hair, long hairCut, String beard, long beardCut, String colour, boolean beardGrows) {
        public static final Look NEW = new Look("own", -1, "none", -1, "dark_brown", false);
        public static final Codec<Look> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("hair").forGetter(Look::hair),
                Codec.LONG.fieldOf("hair_cut").forGetter(Look::hairCut),
                Codec.STRING.fieldOf("beard").forGetter(Look::beard),
                Codec.LONG.fieldOf("beard_cut").forGetter(Look::beardCut),
                Codec.STRING.fieldOf("colour").forGetter(Look::colour),
                Codec.BOOL.fieldOf("beard_grows").forGetter(Look::beardGrows)
        ).apply(i, Look::new));
        public static final StreamCodec<ByteBuf, Look> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, Look::hair, ByteBufCodecs.VAR_LONG, Look::hairCut,
                ByteBufCodecs.STRING_UTF8, Look::beard, ByteBufCodecs.VAR_LONG, Look::beardCut,
                ByteBufCodecs.STRING_UTF8, Look::colour, ByteBufCodecs.BOOL, Look::beardGrows, Look::new);
    }

    public static final AttachmentType<Look> LOOK = AttachmentRegistry.create(ZomboidCraft.id("grooming"),
            b -> b.initializer(() -> Look.NEW).persistent(Look.CODEC).syncWith(Look.STREAM_CODEC, AttachmentSyncPredicate.all()));

    // ------------------------------------------------------------------ what shows

    public static Look look(Player player) {
        return player.getAttachedOrElse(LOOK, Look.NEW);
    }

    /** Days since a cut, by the world's clock. */
    public static float days(long cut, long now) {
        return cut < 0 ? 0 : Math.max(0, (now - cut) / 24000f);
    }

    private static int grown(int fromRank, float days, int[] table) {
        float at = table[fromRank] + days;
        int r = fromRank;
        while (r < 4 && at >= table[r + 1]) r++;
        return r;
    }

    /** The hair style showing now: the cut, until it grows a length past it. */
    public static String hair(Look look, long now) {
        int from = HAIR.getOrDefault(look.hair(), 2);
        int r = grown(from, days(look.hairCut(), now), HAIR_DAYS);
        return r == from ? look.hair() : HAIR_GROWN[r];
    }

    public static String beard(Look look, long now) {
        if (!look.beardGrows() && BEARD.getOrDefault(look.beard(), 0) == 0) return "none";
        int from = BEARD.getOrDefault(look.beard(), 0);
        if (!look.beardGrows()) return look.beard();
        int r = grown(from, days(look.beardCut(), now), BEARD_DAYS);
        return r == from ? look.beard() : BEARD_GROWN[r];
    }

    public static int hairLength(Look look, long now) {
        return HAIR.getOrDefault(hair(look, now), 2);
    }

    public static int beardLength(Look look, long now) {
        return BEARD.getOrDefault(beard(look, now), 0);
    }

    /** What a cut to this style needs in hand, or null when it can't be cut to from here. */
    public static String needs(Look look, long now, boolean isHair, String style) {
        Integer rank = (isHair ? HAIR : BEARD).get(style);
        if (rank == null || style.equals("own")) return null;
        int length = isHair ? hairLength(look, now) : beardLength(look, now);
        if (rank > length) return null;
        if (rank == 0) return "razor";
        return "scissors";
    }

    // ------------------------------------------------------------------ the server

    public static void register() {
        PayloadTypeRegistry.serverboundPlay().register(Cut.TYPE, Cut.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(Colour.TYPE, Colour.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(Beard.TYPE, Beard.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(Cut.TYPE, (p, c) -> c.player().level().getServer().execute(() -> cut(c.player(), p)));
        ServerPlayNetworking.registerGlobalReceiver(Colour.TYPE, (p, c) -> c.player().level().getServer().execute(() -> {
            if (COLOURS.contains(p.colour())) {
                Look l = look(c.player());
                c.player().setAttached(LOOK, new Look(l.hair(), l.hairCut(), l.beard(), l.beardCut(), p.colour(), l.beardGrows()));
            }
        }));
        ServerPlayNetworking.registerGlobalReceiver(Beard.TYPE, (p, c) -> c.player().level().getServer().execute(() -> {
            ServerPlayer player = c.player();
            Look l = look(player);
            long now = player.level().getGameTime();
            // turning it on starts it from a clean shave; off keeps whatever's cut until you shave it
            player.setAttached(LOOK, new Look(l.hair(), l.hairCut(), p.grows() ? "none" : l.beard(),
                    p.grows() ? now : l.beardCut(), l.colour(), p.grows()));
        }));
        ServerTickEvents.END_SERVER_TICK.register(s -> {
            if (s.getTickCount() % 40 != 0) return;
            for (ServerPlayer player : s.getPlayerList().getPlayers()) {
                Look l = look(player);
                if (l.hairCut() < 0 || l.beardCut() < 0) {
                    long now = player.level().getGameTime();
                    player.setAttached(LOOK, new Look(l.hair(), l.hairCut() < 0 ? now : l.hairCut(), l.beard(),
                            l.beardCut() < 0 ? now : l.beardCut(), l.colour(), l.beardGrows()));
                }
            }
        });
    }

    private static void cut(ServerPlayer player, Cut cut) {
        Look l = look(player);
        long now = player.level().getGameTime();
        String need = needs(l, now, cut.hair(), cut.style());
        if (need == null) {
            tell(player, "It isn't long enough for that.");
            return;
        }
        boolean hasRazor = carries(player, "razor");
        boolean hasScissors = carries(player, "medical_scissors") || carries(player, "scissors");
        if (need.equals("razor") ? !hasRazor : !(hasScissors || !cut.hair() && hasRazor)) {
            tell(player, need.equals("razor") ? "You need a razor to shave it." : "You need scissors to cut it.");
            return;
        }
        TimedActions.start(player, "groom", cut.hair() ? "Cutting hair" : "Trimming beard", need.equals("razor") ? 60 : 100, p -> {
            Look now2 = look(p);
            long t = p.level().getGameTime();
            p.setAttached(LOOK, cut.hair()
                    ? new Look(cut.style(), t, now2.beard(), now2.beardCut(), now2.colour(), now2.beardGrows())
                    : new Look(now2.hair(), now2.hairCut(), cut.style(), t, now2.colour(), now2.beardGrows()));
            p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.SHEEP_SHEAR, SoundSource.PLAYERS, 0.7f, 1.3f);
        });
    }

    private static boolean carries(ServerPlayer player, String id) {
        Item item = BuiltInRegistries.ITEM.getValue(ZomboidCraft.id(id));
        if (item == Items.AIR) return false;
        return player.getInventory().contains(s -> s.is(item));
    }

    private static void tell(ServerPlayer player, String s) {
        player.sendOverlayMessage(Component.literal(s).withStyle(ChatFormatting.GRAY));
    }

    // ------------------------------------------------------------------ the haircut page's traffic

    /** Client to server: cut the hair (or the beard) to this style. */
    public record Cut(boolean hair, String style) implements CustomPacketPayload {
        public static final Type<Cut> TYPE = new Type<>(ZomboidCraft.id("grooming_cut"));
        public static final StreamCodec<ByteBuf, Cut> CODEC = StreamCodec.composite(
                ByteBufCodecs.BOOL, Cut::hair, ByteBufCodecs.STRING_UTF8, Cut::style, Cut::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Client to server: your natural hair colour. */
    public record Colour(String colour) implements CustomPacketPayload {
        public static final Type<Colour> TYPE = new Type<>(ZomboidCraft.id("grooming_colour"));
        public static final StreamCodec<ByteBuf, Colour> CODEC = StreamCodec.composite(ByteBufCodecs.STRING_UTF8, Colour::colour, Colour::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Client to server: whether your survivor grows a beard. */
    public record Beard(boolean grows) implements CustomPacketPayload {
        public static final Type<Beard> TYPE = new Type<>(ZomboidCraft.id("grooming_beard"));
        public static final StreamCodec<ByteBuf, Beard> CODEC = StreamCodec.composite(ByteBufCodecs.BOOL, Beard::grows, Beard::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    private Grooming() {
    }
}
