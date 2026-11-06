package com.zomboidcraft.building.base;

import com.zomboidcraft.ZcConfig;
import com.zomboidcraft.ZomboidCraft;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Base claims, Zomboid's safehouse with Josiah's pick 3 (2026-10-31): stand in a building and claim it from the G
 * menu, and nobody but you and the squad you let in can break, place, build or move anything inside it. It grows as you
 * build onto it. You have one base; claiming another building moves it there if nobody else holds that one, and other
 * shelters round the map stay free to use. Rooms only count inside a claim (rooms plan R2).
 */
public final class Claims {
    public static final AttachmentType<List<Claim>> CLAIMS = AttachmentRegistry.create(ZomboidCraft.id("base_claims"),
            b -> b.persistent(Claim.CODEC.listOf()).initializer(List::of));

    /** What the client was last told: every claim in the world it's in, for greying the G menu and the base page. */
    private static volatile List<Claim> clientView = List.of();

    public static final int CLAIM = 0, RELEASE = 1, ADD = 2, REMOVE = 3, LEAVE = 4;

    private static final StreamCodec<RegistryFriendlyByteBuf, List<Claim>> LIST = StreamCodec.of((buf, list) -> {
        buf.writeVarInt(list.size());
        for (Claim c : list) {
            buf.writeVarInt(c.id());
            buf.writeUUID(c.owner());
            buf.writeUtf(c.ownerName());
            buf.writeVarInt(c.squad().size());
            for (int i = 0; i < c.squad().size(); i++) {
                buf.writeUUID(c.squad().get(i));
                buf.writeUtf(i < c.squadNames().size() ? c.squadNames().get(i) : "?");
            }
            buf.writeBlockPos(c.min());
            buf.writeBlockPos(c.max());
            buf.writeUtf(c.kind());
        }
    }, buf -> {
        int n = buf.readVarInt();
        List<Claim> out = new ArrayList<>(n);
        for (int k = 0; k < n; k++) {
            int id = buf.readVarInt();
            UUID owner = buf.readUUID();
            String name = buf.readUtf();
            int s = buf.readVarInt();
            List<UUID> ids = new ArrayList<>(s);
            List<String> names = new ArrayList<>(s);
            for (int i = 0; i < s; i++) {
                ids.add(buf.readUUID());
                names.add(buf.readUtf());
            }
            out.add(new Claim(id, owner, name, ids, names, buf.readBlockPos(), buf.readBlockPos(), buf.readUtf()));
        }
        return out;
    });

    /** Every claim, sent on joining and after every change. */
    public record Sync(List<Claim> claims) implements CustomPacketPayload {
        public static final Type<Sync> TYPE = new Type<>(ZomboidCraft.id("base_claims_sync"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Sync> CODEC = LIST.map(Sync::new, Sync::claims);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** A pick from the G menu: claim here, let it go, add or drop a squad member, leave a squad. */
    public record Act(int action, UUID target) implements CustomPacketPayload {
        public static final Type<Act> TYPE = new Type<>(ZomboidCraft.id("base_claim_act"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Act> CODEC = StreamCodec.of((buf, a) -> {
            buf.writeVarInt(a.action);
            buf.writeUUID(a.target);
        }, buf -> new Act(buf.readVarInt(), buf.readUUID()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    static void register() {
        PayloadTypeRegistry.clientboundPlay().register(Sync.TYPE, Sync.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(Act.TYPE, Act.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(Act.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            player.level().getServer().execute(() -> act(player, payload.action(), payload.target()));
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> server.execute(() -> sync(handler.getPlayer())));

        // nobody outside the squad breaks or places anything inside a claim
        PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, be) -> {
            if (mayEdit(level, pos, player)) return true;
            refuse(player, level, pos);
            return false;
        });
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            if (!(player.getItemInHand(hand).getItem() instanceof BlockItem)) return InteractionResult.PASS;
            BlockPos at = level.getBlockState(hit.getBlockPos()).canBeReplaced() ? hit.getBlockPos() : hit.getBlockPos().relative(hit.getDirection());
            if (mayEdit(level, at, player)) return InteractionResult.PASS;
            refuse(player, level, at);
            return InteractionResult.FAIL;
        });
    }

    // ---------------------------------------------------------------- lookups

    public static List<Claim> all(Level level) {
        return level instanceof ServerLevel s ? s.getAttachedOrElse(CLAIMS, List.of()) : clientView;
    }

    /** The claim this spot is in, or null. */
    public static @Nullable Claim at(Level level, BlockPos pos) {
        for (Claim c : all(level)) {
            if (c.contains(pos)) return c;
        }
        return null;
    }

    /** The player's own base, or null. */
    public static @Nullable Claim baseOf(Level level, UUID owner) {
        for (Claim c : all(level)) {
            if (c.kind().equals(Claim.BASE) && c.owner().equals(owner)) return c;
        }
        return null;
    }

    /** Every claim the player belongs to, as owner or squad. */
    public static List<Claim> of(Level level, UUID who) {
        List<Claim> out = new ArrayList<>();
        for (Claim c : all(level)) {
            if (c.member(who)) out.add(c);
        }
        return out;
    }

    /** May this player change the block here? Anywhere unclaimed, yes; in a claim, only its owner and squad. */
    public static boolean mayEdit(Level level, BlockPos pos, Player player) {
        if (!ZcConfig.get().building.claims || player.isCreative()) return true;
        Claim c = at(level, pos);
        return c == null || c.member(player);
    }

    /** Why the player can't change it, for the G menu and build checks; null when they can. */
    public static @Nullable String refusal(Level level, BlockPos pos, Player player) {
        if (mayEdit(level, pos, player)) return null;
        Claim c = at(level, pos);
        return c == null ? null : "This is " + c.ownerName() + "'s " + (c.kind().equals(Claim.OUTPOST) ? "outpost" : "base") + ".";
    }

    private static void refuse(Player player, Level level, BlockPos pos) {
        String why = refusal(level, pos, player);
        if (why != null && player instanceof ServerPlayer sp) sp.sendOverlayMessage(Component.literal(why).withStyle(ChatFormatting.GRAY));
    }

    // ---------------------------------------------------------------- changes

    static void put(ServerLevel level, List<Claim> claims) {
        level.setAttached(CLAIMS, List.copyOf(claims));
        for (ServerPlayer p : level.getServer().getPlayerList().getPlayers()) sync(p);
    }

    public static void sync(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level) || !ServerPlayNetworking.canSend(player, Sync.TYPE)) return;
        ServerPlayNetworking.send(player, new Sync(level.getAttachedOrElse(CLAIMS, List.of())));
    }

    /** The client's copy (client/building/BaseClient). */
    public static void clientView(List<Claim> claims) {
        clientView = List.copyOf(claims);
    }

    static void act(ServerPlayer player, int action, UUID target) {
        if (!(player.level() instanceof ServerLevel level)) return;
        String said = switch (action) {
            case CLAIM -> claim(level, player);
            case RELEASE -> release(level, player);
            case ADD, REMOVE -> squad(level, player, target, action == ADD);
            case LEAVE -> leave(level, player, target);
            default -> null;
        };
        if (said != null) player.sendOverlayMessage(Component.literal(said));
    }

    /** Claims the building the player stands in as their base (or moves their base there); says what happened. */
    public static String claim(ServerLevel level, ServerPlayer player) {
        if (!ZcConfig.get().building.claims) return "Base claims are off in this world.";
        BlockPos[] box = building(level, player.blockPosition());
        if (box == null) return "Stand inside a building to claim it.";
        List<Claim> now = new ArrayList<>(all(level));
        Claim mine = baseOf(level, player.getUUID());
        for (Claim c : now) {
            if (c.overlaps(box[0], box[1]) && !c.owner().equals(player.getUUID())) {
                return "This building is " + c.ownerName() + "'s " + (c.kind().equals(Claim.OUTPOST) ? "outpost." : "base.");
            }
        }
        if (mine != null && mine.contains(player.blockPosition())) return "This is already your base.";
        int id = 1;
        for (Claim c : now) id = Math.max(id, c.id() + 1);
        Claim made = new Claim(mine != null ? mine.id() : id, player.getUUID(), player.getName().getString(),
                mine != null ? mine.squad() : List.of(), mine != null ? mine.squadNames() : List.of(), box[0], box[1], Claim.BASE);
        if (mine != null) now.remove(mine);
        now.add(made);
        put(level, now);
        return mine != null ? "Your base has moved to this building." : "This building is now your base.";
    }

    public static String release(ServerLevel level, ServerPlayer player) {
        Claim mine = baseOf(level, player.getUUID());
        if (mine == null) return "You haven't claimed a base.";
        List<Claim> now = new ArrayList<>(all(level));
        now.remove(mine);
        put(level, now);
        return "You've let your base go.";
    }

    public static String squad(ServerLevel level, ServerPlayer player, UUID who, boolean in) {
        Claim mine = baseOf(level, player.getUUID());
        if (mine == null) return "Claim a base first.";
        if (who.equals(player.getUUID())) return null;
        ServerPlayer other = level.getServer().getPlayerList().getPlayer(who);
        String name = other != null ? other.getName().getString() : "them";
        if (in == mine.squad().contains(who)) return in ? name + " is already in your squad." : name + " isn't in your squad.";
        List<Claim> now = new ArrayList<>(all(level));
        now.set(now.indexOf(mine), mine.withSquad(who, name, in));
        put(level, now);
        if (other != null) {
            other.sendOverlayMessage(Component.literal(in ? player.getName().getString() + " let you into their base."
                    : player.getName().getString() + " took you off their base."));
        }
        return in ? name + " can build in your base now." : name + " can't change your base any more.";
    }

    public static String leave(ServerLevel level, ServerPlayer player, UUID owner) {
        Claim theirs = baseOf(level, owner);
        if (theirs == null || !theirs.squad().contains(player.getUUID())) return null;
        List<Claim> now = new ArrayList<>(all(level));
        now.set(now.indexOf(theirs), theirs.withSquad(player.getUUID(), "", false));
        put(level, now);
        return "You've left " + theirs.ownerName() + "'s squad.";
    }

    /**
     * Something the player just built: if it's against a claim of theirs (within the grow reach), the claim takes it
     * in, so the base grows with its extensions (pick 3). It never grows into someone else's claim.
     */
    public static void grow(ServerLevel level, BlockPos pos, Player player) {
        int reach = ZcConfig.get().building.claimGrowReach;
        List<Claim> now = new ArrayList<>(all(level));
        for (int i = 0; i < now.size(); i++) {
            Claim c = now.get(i);
            if (!c.member(player) || c.contains(pos) || c.distance(pos) > reach) continue;
            Claim grown = c.grownTo(pos);
            for (Claim o : now) {
                if (o != c && o.overlaps(grown.min(), grown.max())) return;
            }
            now.set(i, grown);
            put(level, now);
            return;
        }
    }

    /**
     * The building round a spot: every roofed open space you can walk to from it, through doors and up stairwells,
     * as a box with its walls, floor and roof (a block round). Null when the spot isn't under a roof or it's too big
     * to be one building.
     */
    public static BlockPos[] building(ServerLevel level, BlockPos start) {
        if (!inside(level, start)) return null;
        ZcConfig.Building cfg = ZcConfig.get().building;
        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        seen.add(start);
        queue.add(start);
        int x0 = start.getX(), y0 = start.getY(), z0 = start.getZ(), x1 = x0, y1 = y0, z1 = z0;
        while (!queue.isEmpty()) {
            if (seen.size() > cfg.claimMaxCells) return null;
            BlockPos p = queue.poll();
            for (Direction d : Direction.values()) {
                BlockPos q = p.relative(d);
                if (seen.contains(q) || Math.abs(q.getX() - start.getX()) > cfg.claimRange || Math.abs(q.getZ() - start.getZ()) > cfg.claimRange
                        || Math.abs(q.getY() - start.getY()) > 24) {
                    continue;
                }
                if (!inside(level, q)) continue;
                seen.add(q);
                queue.add(q);
                x0 = Math.min(x0, q.getX());
                y0 = Math.min(y0, q.getY());
                z0 = Math.min(z0, q.getZ());
                x1 = Math.max(x1, q.getX());
                y1 = Math.max(y1, q.getY());
                z1 = Math.max(z1, q.getZ());
            }
        }
        if (seen.size() < 9) return null;
        return new BlockPos[]{new BlockPos(x0 - 1, y0 - 1, z0 - 1), new BlockPos(x1 + 1, y1 + 1, z1 + 1)};
    }

    /**
     * Open to the sky: nothing at all overhead, up to the top of the world. Not the sky light, which leaks down
     * through a ceiling lamp or a pane and would call a lit room outdoors.
     */
    public static boolean sky(Level level, BlockPos p) {
        // the surface heightmap is one above the highest block of any kind in the column
        return p.getY() >= level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, p.getX(), p.getZ());
    }

    /** Open (or a door) and with a roof over it. */
    private static boolean inside(ServerLevel level, BlockPos p) {
        BlockState s = level.getBlockState(p);
        boolean open = s.getBlock() instanceof DoorBlock || s.getCollisionShape(level, p).isEmpty();
        return open && !sky(level, p);
    }

    private Claims() {
    }
}
