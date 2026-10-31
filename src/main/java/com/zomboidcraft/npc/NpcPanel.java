package com.zomboidcraft.npc;

import com.zomboidcraft.ZomboidCraft;
import com.zomboidcraft.clothing.ClothingSlot;
import com.zomboidcraft.clothing.Clothes;
import com.zomboidcraft.clothing.Garment;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

/**
 * Her page (the client's CompanionScreen) and the order wheel: what the page shows of her, sent while it's open, and
 * everything you can ask from it. Orders go through her own systems (following, NpcWork's home, job and shift,
 * NpcBond's acts, NpcWardrobe's pieces), so she answers them the way she always does.
 */
public final class NpcPanel {
    /** How close you have to be for the page to do anything. */
    private static final double REACH = 8;

    // ------------------------------------------------------------------ orders

    public static final int FOLLOW = 0, WAIT = 1, GO_HOME = 2, SET_HOME = 3, HOW_ARE_YOU = 4;
    /** Job n is JOB + its ordinal. */
    public static final int JOB = 10;
    public static final int DAY_SHIFT = 20, NIGHT_SHIFT = 21;
    /** A talk act (NpcBond.Act) is TALK + its ordinal. */
    public static final int TALK = 30;
    /** A pose to hold for a while is POSE + one of {@link #POSES}' indexes. */
    public static final int POSE = 50;
    /** What you can ask her to do on the spot: her clip, and what the page calls it. */
    public static final int[] POSES = {NpcWork.SIT, NpcWork.KNEEL, NpcWork.LOOKOUT, NpcWork.SEARCH, NpcWork.DIG, NpcWork.SLEEP,
            NpcWork.POSE, NpcWork.FIDGET};
    public static final String[] POSE_NAMES = {"Sit", "Kneel", "Keep lookout", "Search here", "Dig", "Lie down", "Strike a pose",
            "Fidget"};
    /** How long she holds a pose you asked for. */
    private static final int POSE_TICKS = 20 * 20;
    // the wardrobe's own acts (NpcWardrobe.LIKE ... TAKE_OFF + piece) pass straight through
    /** For a model in whole outfits (Npcs.dresses false): wear look n of its list. */
    public static final int LOOK = 600;
    /** For a model in whole outfits: put on or take off piece n of {@link State#wears()} (her .zsk's own pieces). */
    public static final int WEAR_ON = 700, WEAR_OFF = 800;

    /** What you can do with things: hand her one from your inventory, take one from her bag, or her weapon. */
    public static final int GIVE = 0, TAKE = 1, TAKE_WEAPON = 2;

    /** Opens (or closes) her page: while it's open the server sends what she's like twice a second. */
    public record Watch(int npc, boolean open) implements CustomPacketPayload {
        public static final Type<Watch> TYPE = new Type<>(ZomboidCraft.id("npc_watch"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Watch> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Watch::npc, ByteBufCodecs.BOOL, Watch::open, Watch::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /**
     * Bonds with her (or unbonds): bonded, you can open her page from anywhere (Josiah 2026-10-30). Bonding needs you
     * beside her; unbonding works from anywhere.
     */
    public record Bond(int npc, boolean on) implements CustomPacketPayload {
        public static final Type<Bond> TYPE = new Type<>(ZomboidCraft.id("npc_bond"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Bond> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Bond::npc, ByteBufCodecs.BOOL, Bond::on, Bond::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** One companion you're bonded with: who she is and how she looks, so her page can draw her when she's far away. */
    public record Bonded(String uuid, int id, String name, String model, String look, String hidden) {
    }

    /** Everyone you're bonded with that your world has loaded, by name (sent every two seconds). */
    public record Bonds(List<Bonded> list) implements CustomPacketPayload {
        public static final Type<Bonds> TYPE = new Type<>(ZomboidCraft.id("npc_bonds"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Bonds> CODEC = StreamCodec.of((buf, b) -> {
            buf.writeVarInt(b.list.size());
            for (Bonded e : b.list) {
                buf.writeUtf(e.uuid());
                buf.writeVarInt(e.id());
                buf.writeUtf(e.name());
                buf.writeUtf(e.model());
                buf.writeUtf(e.look());
                buf.writeUtf(e.hidden());
            }
        }, buf -> {
            List<Bonded> list = new ArrayList<>();
            for (int i = buf.readVarInt(); i > 0; i--) {
                list.add(new Bonded(buf.readUtf(), buf.readVarInt(), buf.readUtf(), buf.readUtf(), buf.readUtf(), buf.readUtf()));
            }
            return new Bonds(list);
        });

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record Order(int npc, int code) implements CustomPacketPayload {
        public static final Type<Order> TYPE = new Type<>(ZomboidCraft.id("npc_order"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Order> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Order::npc, ByteBufCodecs.VAR_INT, Order::code, Order::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record Gear(int npc, int op, int slot) implements CustomPacketPayload {
        public static final Type<Gear> TYPE = new Type<>(ZomboidCraft.id("npc_gear"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Gear> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Gear::npc, ByteBufCodecs.VAR_INT, Gear::op, ByteBufCodecs.VAR_INT, Gear::slot, Gear::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Everything her page shows that the client doesn't already know. */
    public record State(int npc, float health, float maxHealth, boolean following, boolean home, int job, boolean night,
                        float[] needs, float[] traits, int[] skills, float[] progress, float trust, float affection,
                        String feelings, String mood, ItemStack weapon, List<ItemStack> pack, List<String> diary,
                        List<String> wears)
            implements CustomPacketPayload {
        public static final Type<State> TYPE = new Type<>(ZomboidCraft.id("npc_state"));
        public static final StreamCodec<RegistryFriendlyByteBuf, State> CODEC = StreamCodec.of(State::write, State::read);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        private static void floats(RegistryFriendlyByteBuf buf, float[] a) {
            buf.writeVarInt(a.length);
            for (float f : a) buf.writeFloat(f);
        }

        private static float[] floats(RegistryFriendlyByteBuf buf) {
            float[] a = new float[buf.readVarInt()];
            for (int i = 0; i < a.length; i++) a[i] = buf.readFloat();
            return a;
        }

        private static void write(RegistryFriendlyByteBuf buf, State s) {
            buf.writeVarInt(s.npc);
            buf.writeFloat(s.health);
            buf.writeFloat(s.maxHealth);
            buf.writeBoolean(s.following);
            buf.writeBoolean(s.home);
            buf.writeVarInt(s.job);
            buf.writeBoolean(s.night);
            floats(buf, s.needs);
            floats(buf, s.traits);
            buf.writeVarInt(s.skills.length);
            for (int v : s.skills) buf.writeVarInt(v);
            floats(buf, s.progress);
            buf.writeFloat(s.trust);
            buf.writeFloat(s.affection);
            buf.writeUtf(s.feelings);
            buf.writeUtf(s.mood);
            ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, s.weapon);
            buf.writeVarInt(s.pack.size());
            for (ItemStack st : s.pack) ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, st);
            buf.writeVarInt(s.diary.size());
            for (String d : s.diary) buf.writeUtf(d);
            buf.writeVarInt(s.wears.size());
            for (String w : s.wears) buf.writeUtf(w);
        }

        private static State read(RegistryFriendlyByteBuf buf) {
            int npc = buf.readVarInt();
            float health = buf.readFloat(), max = buf.readFloat();
            boolean following = buf.readBoolean(), home = buf.readBoolean();
            int job = buf.readVarInt();
            boolean night = buf.readBoolean();
            float[] needs = floats(buf), traits = floats(buf);
            int[] skills = new int[buf.readVarInt()];
            for (int i = 0; i < skills.length; i++) skills[i] = buf.readVarInt();
            float[] progress = floats(buf);
            float trust = buf.readFloat(), affection = buf.readFloat();
            String feelings = buf.readUtf(), mood = buf.readUtf();
            ItemStack weapon = ItemStack.OPTIONAL_STREAM_CODEC.decode(buf);
            List<ItemStack> pack = new ArrayList<>();
            for (int i = buf.readVarInt(); i > 0; i--) pack.add(ItemStack.OPTIONAL_STREAM_CODEC.decode(buf));
            List<String> diary = new ArrayList<>();
            for (int i = buf.readVarInt(); i > 0; i--) diary.add(buf.readUtf());
            List<String> wears = new ArrayList<>();
            for (int i = buf.readVarInt(); i > 0; i--) wears.add(buf.readUtf());
            return new State(npc, health, max, following, home, job, night, needs, traits, skills, progress, trust, affection,
                    feelings, mood, weapon, pack, diary, wears);
        }
    }

    /** Who has whose page open. */
    private static final Map<UUID, Integer> watching = new HashMap<>();

    static void register() {
        PayloadTypeRegistry.serverboundPlay().register(Watch.TYPE, Watch.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(Order.TYPE, Order.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(Gear.TYPE, Gear.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(State.TYPE, State.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(Bond.TYPE, Bond.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(Bonds.TYPE, Bonds.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(Bond.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            player.level().getServer().execute(() -> {
                if (!(player.level().getEntity(payload.npc()) instanceof NpcEntity npc) || !npc.isAlive()) return;
                if (payload.on()) {
                    if (npc.distanceToSqr(player) > REACH * REACH || !npc.bonds.add(player.getUUID())) return;
                    npc.mind.remember(npc, "Bonded with " + player.getName().getString(), 0.4f, 0.6f);
                    tell(player, "Bonded with " + npc.speakerName() + ". Press K to bring up her page from anywhere");
                } else if (npc.bonds.remove(player.getUUID())) {
                    tell(player, "Unbonded from " + npc.speakerName());
                }
                ServerPlayNetworking.send(player, bonds(player));
            });
        });
        ServerPlayNetworking.registerGlobalReceiver(Watch.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            player.level().getServer().execute(() -> {
                if (payload.open()) {
                    watching.put(player.getUUID(), payload.npc());
                    NpcEntity npc = near(player, payload.npc());
                    if (npc != null) ServerPlayNetworking.send(player, state(npc));
                } else {
                    watching.remove(player.getUUID());
                }
            });
        });
        ServerPlayNetworking.registerGlobalReceiver(Order.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            player.level().getServer().execute(() -> {
                NpcEntity npc = near(player, payload.npc());
                if (npc != null) {
                    npc.ordered();
                    order(npc, player, payload.code());
                    ServerPlayNetworking.send(player, state(npc));
                }
            });
        });
        ServerPlayNetworking.registerGlobalReceiver(Gear.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            player.level().getServer().execute(() -> {
                NpcEntity npc = near(player, payload.npc());
                if (npc != null) {
                    gear(npc, player, payload.op(), payload.slot());
                    ServerPlayNetworking.send(player, state(npc));
                }
            });
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTickCount() % 40 == 5) {
                for (ServerPlayer p : server.getPlayerList().getPlayers()) ServerPlayNetworking.send(p, bonds(p));
            }
            if (server.getTickCount() % 10 != 0 || watching.isEmpty()) return;
            watching.entrySet().removeIf(e -> {
                ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
                if (p == null) return true;
                NpcEntity npc = near(p, e.getValue());
                if (npc != null) {
                    npc.attend(p);
                    ServerPlayNetworking.send(p, state(npc));
                }
                return false;
            });
        });
    }

    /** Her, if she's beside you, or anywhere in your world when you're bonded with her. */
    private static NpcEntity near(ServerPlayer player, int id) {
        if (player.isSpectator() || !(player.level().getEntity(id) instanceof NpcEntity npc) || !npc.isAlive()) return null;
        return npc.distanceToSqr(player) <= REACH * REACH || npc.bonds.contains(player.getUUID()) ? npc : null;
    }

    /** The companions bonded with this player that are loaded in their world (others come back as they load). */
    private static Bonds bonds(ServerPlayer player) {
        List<Bonded> list = new ArrayList<>();
        if (player.level() instanceof net.minecraft.server.level.ServerLevel level) {
            for (NpcEntity npc : level.getEntities(Npcs.NPC, n -> n.isAlive() && n.bonds.contains(player.getUUID()))) {
                list.add(new Bonded(npc.getUUID().toString(), npc.getId(), npc.getName().getString(), npc.model(), npc.look(),
                        String.join(",", npc.hidden())));
            }
        }
        // a steady order, so the selection screen doesn't reshuffle
        list.sort(java.util.Comparator.comparing(Bonded::name).thenComparing(Bonded::uuid));
        return new Bonds(list);
    }

    private static State state(NpcEntity npc) {
        NpcMind m = npc.mind;
        float[] needs = new float[NpcMind.Need.values().length];
        for (NpcMind.Need n : NpcMind.Need.values()) needs[n.ordinal()] = m.get(n);
        float[] traits = new float[NpcMind.Trait.values().length];
        for (NpcMind.Trait t : NpcMind.Trait.values()) traits[t.ordinal()] = m.trait(t);
        int[] skills = new int[NpcSkills.Skill.values().length];
        float[] progress = new float[skills.length];
        for (NpcSkills.Skill s : NpcSkills.Skill.values()) {
            skills[s.ordinal()] = npc.skills.level(s);
            progress[s.ordinal()] = npc.skills.progress(s);
        }
        NpcBond b = npc.bond;
        List<ItemStack> pack = new ArrayList<>();
        for (int i = 0; i < npc.work.pack.getContainerSize(); i++) pack.add(npc.work.pack.getItem(i).copy());
        List<String> diary = new ArrayList<>();
        for (int i = m.diary.size() - 1; i >= 0 && diary.size() < 10; i--) {
            NpcMind.Memory mem = m.diary.get(i);
            long day = Math.floorDiv(mem.at() + 3000, 24000L) + 1;
            diary.add("Day " + day + "|" + mem.what() + "|" + (mem.feel() > 0.15f ? "+" : mem.feel() < -0.15f ? "-" : "="));
        }
        return new State(npc.getId(), npc.getHealth(), npc.getMaxHealth(), npc.following, npc.work.home != null,
                npc.work.job.ordinal(), npc.work.nightShift, needs, traits, skills, progress, b.trust, b.affection,
                NpcBond.words(b.trust, b.affection, b.attraction, b.together), npc.mood(), npc.getMainHandItem().copy(), pack, diary,
                Npcs.dresses(npc.model()) ? List.of() : Npcs.pieces(npc));
    }

    // ------------------------------------------------------------------ doing what's asked

    /** An order from the page or the wheel. */
    public static void order(NpcEntity npc, ServerPlayer p, int code) {
        boolean yours = npc.following && p.getUUID().equals(npc.leader);
        if (code == FOLLOW) {
            if (!yours) npc.toggleFollow(p);
        } else if (code == WAIT) {
            // following someone else: she takes your word for it
            if (npc.following && !yours) npc.toggleFollow(p);
            if (npc.following) npc.toggleFollow(p);
        } else if (code == GO_HOME) {
            if (npc.work.home == null) {
                tell(p, "She has no home yet: stand where it should be and pick Set home here");
                return;
            }
            if (npc.following) npc.toggleFollow(p);
            BlockPos h = npc.work.home;
            npc.getNavigation().moveTo(h.getX() + 0.5, h.getY(), h.getZ() + 0.5, 1.0);
        } else if (code == SET_HOME) {
            npc.work.home = p.blockPosition();
            npc.mind.remember(npc, "Moved into a new home", 0.3f, 0.6f);
            int stores = npc.work.stores((net.minecraft.server.level.ServerLevel) p.level()).size();
            tell(p, npc.speakerName() + "'s home is here now. Stores she can use: " + stores
                    + (stores == 0 ? " (put a chest or barrel near here for her)" : ""));
        } else if (code == HOW_ARE_YOU) {
            npc.lines.sayLine(npc, NpcLines.Kind.REPLY, howAreYou(npc));
        } else if (code >= JOB && code < JOB + NpcWork.Job.values().length) {
            NpcWork.Job job = NpcWork.Job.values()[code - JOB];
            if (npc.work.home == null && job != NpcWork.Job.NONE) {
                tell(p, "Give her a home first: stand where it is and pick Set home here");
                return;
            }
            npc.work.job = job;
            if (job != NpcWork.Job.NONE && npc.following) npc.toggleFollow(p);
        } else if (code == DAY_SHIFT || code == NIGHT_SHIFT) {
            npc.work.nightShift = code == NIGHT_SHIFT;
        } else if (code >= TALK && code < TALK + NpcBond.Act.values().length) {
            npc.bond.act(npc, p, NpcBond.Act.values()[code - TALK]);
        } else if (code >= POSE && code < POSE + POSES.length) {
            if (npc.work.asleep) {
                tell(p, "She's asleep");
                return;
            }
            npc.bond.pose = POSES[code - POSE];
            npc.bond.poseTicks = POSE_TICKS;
            npc.getNavigation().stop();
        } else if (code >= LOOK && code < LOOK + 64) {
            List<String> looks = Npcs.dress(npc.model()).looks();
            int i = code - LOOK;
            if (Npcs.dresses(npc.model()) || i >= looks.size()) return;
            java.util.Set<String> hidden = new java.util.HashSet<>(npc.hidden());
            hidden.removeIf(h -> h.startsWith("look="));
            hidden.add("look=" + looks.get(i));
            npc.setHidden(hidden);
        } else if (code >= WEAR_ON && code < WEAR_OFF + 100) {
            List<String> wears = Npcs.pieces(npc);
            int i = (code - WEAR_ON) % 100;
            if (Npcs.dresses(npc.model()) || i >= wears.size()) return;
            Npcs.wear(npc, wears.get(i), code < WEAR_OFF);
        } else if (code >= NpcWardrobe.LIKE) {
            npc.wardrobe.answer(npc, p, code);
        }
    }

    private static String howAreYou(NpcEntity npc) {
        String mood = npc.mood();
        return switch (mood) {
            case "terrified" -> "I'm scared. Really scared.";
            case "in agony" -> "It hurts. It really hurts.";
            case "wired" -> "My heart won't slow down.";
            case "parched" -> "I need water. Soon.";
            case "starving" -> "I'm so hungry I can't think.";
            case "exhausted" -> "I'm dead on my feet.";
            case "on edge" -> "Something's off. I can feel it.";
            case "lonely" -> "Don't leave me on my own so much.";
            case "hopeless" -> "What's the point of any of this?";
            case "low" -> "I've been better.";
            case "in good spirits" -> "Honestly? Good. Better than I've been in a while.";
            default -> "I'm alright. Holding up.";
        };
    }

    /** Things from the page: hand her something, or take something of hers. */
    private static void gear(NpcEntity npc, ServerPlayer p, int op, int slot) {
        var inv = p.getInventory();
        if (op == GIVE) {
            if (slot < 0 || slot >= inv.getContainerSize()) return;
            ItemStack stack = inv.getItem(slot);
            if (stack.isEmpty()) return;
            NpcWardrobe.Piece piece = piece(stack);
            if (piece != null && Npcs.dresses(npc.model())) {
                // something to wear: she'll try it on if she's willing (the wardrobe decides), and keeps it either way
                ItemStack left = npc.work.pack.addItem(stack.copy());
                inv.setItem(slot, left);
                npc.wardrobe.answer(npc, p, NpcWardrobe.PUT_ON + piece.ordinal());
                return;
            }
            String own = Npcs.dresses(npc.model()) ? null : part(Npcs.pieces(npc), stack);
            if (own != null) {
                // one of her model's own pieces: she keeps it and puts it on
                ItemStack left = npc.work.pack.addItem(stack.copy());
                if (left.getCount() == stack.getCount()) {
                    npc.lines.sayLine(npc, NpcLines.Kind.REPLY, "My bag's full.");
                    return;
                }
                inv.setItem(slot, left);
                Npcs.wear(npc, own, true);
                npc.lines.sayLine(npc, NpcLines.Kind.REPLY, "Oh, that fits. Thank you.");
                return;
            }
            if (Clothes.of(stack) != null && (piece == null || !Npcs.dresses(npc.model()))) {
                npc.lines.sayLine(npc, NpcLines.Kind.REPLY, "I can't wear that one. Not yet, anyway.");
                return;
            }
            if (npc.getMainHandItem().isEmpty() && weapon(stack)) {
                npc.setItemSlot(EquipmentSlot.MAINHAND, stack.copy());
                inv.setItem(slot, ItemStack.EMPTY);
                npc.lines.sayLine(npc, NpcLines.Kind.REPLY, "I'll take good care of it.");
                return;
            }
            ItemStack left = npc.work.pack.addItem(stack.copy());
            if (left.getCount() == stack.getCount()) {
                npc.lines.sayLine(npc, NpcLines.Kind.REPLY, "My bag's full.");
                return;
            }
            inv.setItem(slot, left);
            npc.lines.sayLine(npc, NpcLines.Kind.REPLY, "Thanks. I'll hang on to it.");
        } else if (op == TAKE || op == TAKE_WEAPON) {
            if (!willingToHand(npc, p)) {
                npc.lines.sayLine(npc, NpcLines.Kind.REPLY, NpcWork.pick(npc, "That's mine.", "Get your own.", "I don't think so."));
                return;
            }
            ItemStack stack = op == TAKE_WEAPON ? npc.getMainHandItem()
                    : slot >= 0 && slot < npc.work.pack.getContainerSize() ? npc.work.pack.getItem(slot) : ItemStack.EMPTY;
            if (stack.isEmpty()) return;
            ItemStack copy = stack.copy();
            if (!inv.add(copy) && copy.getCount() == stack.getCount()) {
                tell(p, "Your pockets are full");
                return;
            }
            if (op == TAKE_WEAPON) {
                npc.setItemSlot(EquipmentSlot.MAINHAND, copy);
                if (copy.isEmpty()) npc.lines.sayLine(npc, NpcLines.Kind.REPLY, "Fine. I'll keep my head down.");
            } else {
                npc.work.pack.setItem(slot, copy);
            }
        }
    }

    /** She lets you have her things once she trusts you, or if you're together. */
    private static boolean willingToHand(NpcEntity npc, ServerPlayer p) {
        return npc.bond.together && npc.bond.about(p) || npc.bond.trust >= 45;
    }

    private static boolean weapon(ItemStack stack) {
        String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
        return stack.getItem() instanceof com.zomboidcraft.combat.RifleItem || stack.is(net.minecraft.tags.ItemTags.SWORDS)
                || stack.is(net.minecraft.tags.ItemTags.AXES) || id.contains("knife") || id.contains("machete") || id.contains("bat")
                || id.contains("pipe") || id.contains("crowbar") || id.contains("hammer");
    }

    /**
     * Which of her model's own pieces (a model in whole outfits, like "top.jacket" or "gloves") a found piece of
     * clothing is, by name: the piece's last word in the item's id ("jacket", "glove"), shoes taking any footwear and pants
     * any trousers.
     */
    public static String part(List<String> pieces, ItemStack stack) {
        if (Clothes.of(stack) == null) return null;
        String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath().toLowerCase(Locale.ROOT);
        for (String p : pieces) {
            String word = p.substring(p.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
            String one = word.endsWith("s") ? word.substring(0, word.length() - 1) : word;
            if (one.length() >= 3 && id.contains(one)) return p;
            if (one.equals("shoe") && (id.contains("boot") || id.contains("sneaker") || id.contains("trainer"))) return p;
            if (one.equals("pant") && (id.contains("jeans") || id.contains("trouser") || id.contains("slacks"))) return p;
        }
        return null;
    }

    /**
     * Which of her model's pieces a found piece of clothing is closest to, or null if her model has nothing like it
     * yet (jeans, hats, jackets...).
     */
    public static NpcWardrobe.Piece piece(ItemStack stack) {
        Garment g = Clothes.of(stack);
        if (g == null) return null;
        String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath().toLowerCase(Locale.ROOT);
        if (id.contains("heel")) return NpcWardrobe.Piece.HEELS;
        if (g.slot() == ClothingSlot.FEET) return NpcWardrobe.Piece.BOOTS;
        if (id.contains("stocking") || id.contains("tights")) return NpcWardrobe.Piece.STOCKINGS;
        if (id.contains("skirt")) return NpcWardrobe.Piece.SKIRT;
        if (id.contains("short")) return NpcWardrobe.Piece.SHORTS;
        if (id.contains("dress") || id.contains("slip")) return NpcWardrobe.Piece.DRESS;
        if (g.slot() == ClothingSlot.SHIRT || id.contains("sweater") || id.contains("jumper") || id.contains("hoodie")) {
            return NpcWardrobe.Piece.SWEATER;
        }
        return null;
    }

    private static void tell(ServerPlayer p, String s) {
        p.sendOverlayMessage(Component.literal(s).withStyle(ChatFormatting.GRAY));
    }

    private NpcPanel() {
    }
}
