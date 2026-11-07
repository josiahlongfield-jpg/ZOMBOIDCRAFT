package com.zomboidcraft.survival.character;

import com.zomboidcraft.ZomboidCraft;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Character creation's talk: the server opens the screen (with its sandbox budget), the client sends the survivor. */
public final class CharacterNet {
    /**
     * Opens character creation.
     *
     * @param again        after a death, rather than the first time in this world
     * @param free         Zomboid's Free Trait Points
     * @param penaltyEvery Negative Traits Penalty: one point less for every this many negative traits (0 for none)
     * @param marine       marine creation (a role and trait picks, {@link MarineData}) instead of Zomboid's civilian one
     */
    public record Open(boolean again, int free, int penaltyEvery, boolean marine) implements CustomPacketPayload {
        public static final Type<Open> TYPE = new Type<>(ZomboidCraft.id("character_open"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Open> CODEC = StreamCodec.composite(
                ByteBufCodecs.BOOL, Open::again,
                ByteBufCodecs.VAR_INT, Open::free,
                ByteBufCodecs.VAR_INT, Open::penaltyEvery,
                ByteBufCodecs.BOOL, Open::marine,
                Open::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** The survivor: an occupation (or a marine role, "marine:<id>") and the traits bought on top of what it gives. */
    public record Create(String occupation, List<String> traits) implements CustomPacketPayload {
        public static final Type<Create> TYPE = new Type<>(ZomboidCraft.id("character_create"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Create> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, Create::occupation,
                ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list(64)), Create::traits,
                Create::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    private CharacterNet() {
    }
}
