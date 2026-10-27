package com.zomboidcraft.spawn;

import com.zomboidcraft.ZomboidCraft;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** The spawn picker's talk: the places and friends to choose from, the choice, and word when you've arrived. */
public final class SpawnNet {
    /** A place you can wake up in, with what the picker says about it. Danger runs 1 (low) to 5 (extreme). */
    public record Place(String id, String name, String kind, String group, int x, int z, int danger, String zombies,
                        String wake, String loot, String note) {
        void write(RegistryFriendlyByteBuf buf) {
            buf.writeUtf(id);
            buf.writeUtf(name);
            buf.writeUtf(kind);
            buf.writeUtf(group);
            buf.writeVarInt(x);
            buf.writeVarInt(z);
            buf.writeVarInt(danger);
            buf.writeUtf(zombies);
            buf.writeUtf(wake);
            buf.writeUtf(loot);
            buf.writeUtf(note);
        }

        static Place read(RegistryFriendlyByteBuf buf) {
            return new Place(buf.readUtf(), buf.readUtf(), buf.readUtf(), buf.readUtf(), buf.readVarInt(), buf.readVarInt(),
                    buf.readVarInt(), buf.readUtf(), buf.readUtf(), buf.readUtf(), buf.readUtf());
        }
    }

    /** Someone already out there, and the place they're nearest. */
    public record Friend(String id, String name, String where) {
    }

    /** Opens the picker: on your first time in the world, and after each death. */
    public record Open(List<Place> places, List<Friend> friends, boolean again) implements CustomPacketPayload {
        public static final Type<Open> TYPE = new Type<>(ZomboidCraft.id("spawn_open"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Open> CODEC = StreamCodec.of(
                (buf, o) -> {
                    buf.writeVarInt(o.places().size());
                    for (Place p : o.places()) p.write(buf);
                    buf.writeVarInt(o.friends().size());
                    for (Friend f : o.friends()) {
                        buf.writeUtf(f.id());
                        buf.writeUtf(f.name());
                        buf.writeUtf(f.where());
                    }
                    buf.writeBoolean(o.again());
                },
                buf -> {
                    int n = buf.readVarInt();
                    List<Place> places = new ArrayList<>(n);
                    for (int i = 0; i < n; i++) places.add(Place.read(buf));
                    int m = buf.readVarInt();
                    List<Friend> friends = new ArrayList<>(m);
                    for (int i = 0; i < m; i++) friends.add(new Friend(buf.readUtf(), buf.readUtf(), buf.readUtf()));
                    return new Open(places, friends, buf.readBoolean());
                });

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** The choice: a place by id, a friend by id, or anywhere. */
    public record Pick(int kind, String id) implements CustomPacketPayload {
        public static final int PLACE = 0, FRIEND = 1, ANY = 2;
        public static final Type<Pick> TYPE = new Type<>(ZomboidCraft.id("spawn_pick"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Pick> CODEC = StreamCodec.of(
                (buf, p) -> {
                    buf.writeVarInt(p.kind());
                    buf.writeUtf(p.id());
                },
                buf -> new Pick(buf.readVarInt(), buf.readUtf()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** How the search for a way in is going; {@code done} closes the picker. */
    public record Status(String text, boolean done) implements CustomPacketPayload {
        public static final Type<Status> TYPE = new Type<>(ZomboidCraft.id("spawn_status"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Status> CODEC = StreamCodec.of(
                (buf, s) -> {
                    buf.writeUtf(s.text());
                    buf.writeBoolean(s.done());
                },
                buf -> new Status(buf.readUtf(), buf.readBoolean()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    private SpawnNet() {
    }
}
