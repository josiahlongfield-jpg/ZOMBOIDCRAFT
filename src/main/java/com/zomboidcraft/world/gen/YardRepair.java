package com.zomboidcraft.world.gen;

import com.mojang.serialization.Codec;
import com.zomboidcraft.ZomboidCraft;
import com.zomboidcraft.power.PowerGrid;
import com.zomboidcraft.skills.Skill;
import com.zomboidcraft.skills.Skills;
import com.zomboidcraft.survival.TimedActions;
import com.zomboidcraft.world.county.Districts;
import com.zomboidcraft.zombie.Noise;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Bringing a dead district's power back (Josiah's pick, 2026-10-31: "fix parts round the yard"). At the district's
 * substation the burnt parts are replaced one stop at a time, each a timed job with Electrical, a screwdriver and the
 * parts, each loud enough to bring the dead:
 * <ul>
 * <li>the breaker ({@code pw_breaker}): Electrical 6, generator parts and 2 electronics scrap (city and town yards);</li>
 * <li>the fuse board ({@code bunk_fusebox}): Electrical 3, 3 fuses;</li>
 * <li>the cable ({@code pw_reel}, new cable run off the drum to the transformer): Electrical 4, 2 wire spools and
 * electrical tape;</li>
 * </ul>
 * then the main switch at the switchgear ({@code pw_desk}, {@code pw_annun}, {@code pw_vb_lo}, {@code pw_vb_hi})
 * once the rest is done, which calls LOOT's {@link PowerGrid#restore}: the district is on for good. Hamlet yards
 * have no breaker, and the island's power station no fuse board, so three stops there. Only a district whose mains are off has anything burnt to fix.
 */
public final class YardRepair {
    /** A stop: the blocks it's done at, the level it takes, what it uses, how long, its bit. */
    public enum Part {
        BREAKER("Replace the breaker", 6, 400, new String[] {"generator_parts", "electronics_scrap", "electronics_scrap"}, "pw_breaker"),
        FUSES("Replace the fuse board's fuses", 3, 200, new String[] {"fuse_box", "fuse_box", "fuse_box"}, "bunk_fusebox"),
        CABLE("Run new cable to the transformer", 4, 300, new String[] {"wire_spool", "wire_spool", "electrical_tape"}, "pw_reel"),
        SWITCH("Throw the main switch", 0, 60, new String[0], "pw_desk", "pw_annun", "pw_vb_lo", "pw_vb_hi");

        public final String label;
        public final int level, ticks;
        public final String[] uses, blocks;

        Part(String label, int level, int ticks, String[] uses, String... blocks) {
            this.label = label;
            this.level = level;
            this.ticks = ticks;
            this.uses = uses;
            this.blocks = blocks;
        }

        int bit() {
            return 1 << ordinal();
        }

        /** The stop a block is, or null. */
        public static Part of(BlockState state) {
            String path = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
            for (Part p : values()) {
                for (String b : p.blocks) {
                    if (path.equals(b)) {
                        return p;
                    }
                }
            }
            return null;
        }

        /** What it uses, counted: item path to how many. */
        public Map<String, Integer> needs() {
            Map<String, Integer> n = new HashMap<>();
            for (String u : uses) {
                n.merge(u, 1, Integer::sum);
            }
            return n;
        }
    }

    /** Each district's stops done so far (Part bits), on the overworld. */
    private static final AttachmentType<Map<String, Integer>> DONE = AttachmentRegistry.create(ZomboidCraft.id("yard_repairs"),
            builder -> builder.persistent(Codec.unboundedMap(Codec.STRING, Codec.INT)).initializer(Map::of));

    /** G at a yard's fitting: do that stop. */
    public record Fix(BlockPos pos) implements CustomPacketPayload {
        public static final Type<Fix> TYPE = new Type<>(ZomboidCraft.id("yard_repair"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Fix> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, Fix::pos, Fix::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** How far from a yard's middle its fittings stand. */
    private static final int YARD = 40, STATION = 160;

    private YardRepair() {
    }

    public static void register() {
        PayloadTypeRegistry.serverboundPlay().register(Fix.TYPE, Fix.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(Fix.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            player.level().getServer().execute(() -> fix(player, payload.pos()));
        });
    }

    /** Does the player carry what the stop uses? */
    public static boolean has(Player player, Part part) {
        for (Map.Entry<String, Integer> e : part.needs().entrySet()) {
            if (count(player.getInventory(), e.getKey()) < e.getValue()) {
                return false;
            }
        }
        return true;
    }

    /** Is a screwdriver in either hand? */
    public static boolean screwdriver(Player player) {
        return BuiltInRegistries.ITEM.getKey(player.getMainHandItem().getItem()).getPath().equals("screwdriver")
                || BuiltInRegistries.ITEM.getKey(player.getOffhandItem().getItem()).getPath().equals("screwdriver");
    }

    /** The district whose yard this spot is in, or null. */
    static Districts.District yard(ServerLevel level, BlockPos pos) {
        for (Map.Entry<Districts.District, BlockPos> e : PowerDistricts.yards(level).entrySet()) {
            BlockPos c = e.getValue();
            int r = e.getKey().kind() == Districts.Kind.STATION ? STATION : YARD;
            if (Math.abs(c.getX() - pos.getX()) <= r && Math.abs(c.getZ() - pos.getZ()) <= r
                    && Math.abs(c.getY() - pos.getY()) <= 32) {
                return e.getKey();
            }
        }
        return null;
    }

    private static void fix(ServerPlayer player, BlockPos pos) {
        ServerLevel level = (ServerLevel) player.level();
        Part part = Part.of(level.getBlockState(pos));
        if (player.isSpectator() || level.dimension() != Level.OVERWORLD || part == null
                || player.blockPosition().distSqr(pos) > 36) {
            return;
        }
        Districts.District d = yard(level, pos);
        if (d == null) {
            return;
        }
        String id = d.id();
        if (PowerGrid.isOn(level, id)) {
            tell(player, "The yard's live. Nothing's burnt out here yet.");
            return;
        }
        Map<String, Integer> done = level.getAttachedOrElse(DONE, Map.of());
        int mask = done.getOrDefault(id, 0), need = needed(d);
        if ((mask & part.bit()) != 0) {
            tell(player, "That's done. " + left(mask, need));
            return;
        }
        if (part == Part.SWITCH && (mask & need & ~Part.SWITCH.bit()) != (need & ~Part.SWITCH.bit())) {
            tell(player, "Throwing it now would only blow it again. " + left(mask, need));
            return;
        }
        if (Skills.level(player, Skill.ELECTRICAL) < part.level) {
            tell(player, "You don't know enough to do that. Needs Electrical " + part.level + ".");
            return;
        }
        if (part != Part.SWITCH && !screwdriver(player)) {
            tell(player, "You need a screwdriver in your hand.");
            return;
        }
        if (!has(player, part)) {
            tell(player, "You need " + String.join(", ", describe(part)) + ".");
            return;
        }
        Vec3 at = Vec3.atCenterOf(pos);
        loud(level, at, part);
        int ticks = Skills.level(player, Skill.ELECTRICAL) >= 8 ? part.ticks / 2 : part.ticks;
        TimedActions.start(player, "yard_" + part.name().toLowerCase(java.util.Locale.ROOT), part.label, ticks, p -> {
            if (PowerGrid.isOn(level, id) || !has(p, part)) {
                return;
            }
            for (Map.Entry<String, Integer> e : part.needs().entrySet()) {
                take(p.getInventory(), e.getKey(), e.getValue());
            }
            Map<String, Integer> now = new HashMap<>(level.getAttachedOrElse(DONE, Map.of()));
            int m = now.getOrDefault(id, 0) | part.bit();
            now.put(id, m);
            level.setAttached(DONE, Map.copyOf(now));
            loud(level, at, part);
            Skills.addXp(p, Skill.ELECTRICAL, part == Part.SWITCH ? 40 : 20);
            if (part == Part.SWITCH) {
                PowerGrid.restore(level, id);
                level.playSound(null, at.x, at.y, at.z, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 3f, 0.5f);
                level.playSound(null, at.x, at.y, at.z, SoundEvents.BEACON_AMBIENT, SoundSource.BLOCKS, 3f, 0.5f);
                tell(p, "The yard hums. " + d.name() + " has power again.");
            } else {
                tell(p, part.label.replace("Replace", "Replaced").replace("Run", "Ran") + ". " + left(m, need));
            }
        });
    }

    /** Every stop is loud: the clank of the work and a shower of sparks, heard a long way across a dead town. */
    private static void loud(ServerLevel level, Vec3 at, Part part) {
        level.playSound(null, at.x, at.y, at.z, part == Part.SWITCH ? SoundEvents.IRON_DOOR_CLOSE : SoundEvents.ANVIL_USE,
                SoundSource.BLOCKS, 1.5f, part == Part.SWITCH ? 0.6f : 0.9f);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y + 0.5, at.z, 16, 0.3, 0.3, 0.3, 0.3);
        Noise.emitEngine(level, at, 56);
    }

    /** The stops this district's yard has. */
    private static int needed(Districts.District d) {
        return switch (d.kind()) {
            case HAMLET -> Part.FUSES.bit() | Part.CABLE.bit() | Part.SWITCH.bit();
            // the island's power station: its own switchyard and halls, no fuse board
            case STATION -> Part.BREAKER.bit() | Part.CABLE.bit() | Part.SWITCH.bit();
            default -> Part.FUSES.bit() | Part.CABLE.bit() | Part.SWITCH.bit() | Part.BREAKER.bit();
        };
    }

    private static String left(int mask, int need) {
        StringBuilder s = new StringBuilder();
        for (Part p : Part.values()) {
            if ((need & p.bit()) != 0 && (mask & p.bit()) == 0) {
                s.append(s.length() == 0 ? "Still to do: " : ", ").append(p.label.toLowerCase(java.util.Locale.ROOT));
            }
        }
        return s.length() == 0 ? "" : s + ".";
    }

    private static java.util.List<String> describe(Part part) {
        java.util.List<String> out = new java.util.ArrayList<>();
        for (Map.Entry<String, Integer> e : part.needs().entrySet()) {
            out.add(e.getValue() + " " + e.getKey().replace('_', ' '));
        }
        return out;
    }

    private static int count(Inventory inv, String id) {
        int n = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (BuiltInRegistries.ITEM.getKey(inv.getItem(i).getItem()).getPath().equals(id)) {
                n += inv.getItem(i).getCount();
            }
        }
        return n;
    }

    private static void take(Inventory inv, String id, int n) {
        for (int i = 0; i < inv.getContainerSize() && n > 0; i++) {
            if (BuiltInRegistries.ITEM.getKey(inv.getItem(i).getItem()).getPath().equals(id)) {
                int k = Math.min(n, inv.getItem(i).getCount());
                inv.getItem(i).shrink(k);
                n -= k;
            }
        }
    }

    private static void tell(ServerPlayer player, String text) {
        player.sendOverlayMessage(Component.literal(text).withStyle(ChatFormatting.GRAY));
    }
}
