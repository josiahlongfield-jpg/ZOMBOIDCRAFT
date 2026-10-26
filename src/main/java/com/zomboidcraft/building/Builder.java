package com.zomboidcraft.building;

import com.zomboidcraft.ModBlocks;
import com.zomboidcraft.ModItems;
import com.zomboidcraft.ZomboidCraft;
import com.zomboidcraft.barricade.BarricadeBlock;
import com.zomboidcraft.combat.ModSounds;
import com.zomboidcraft.skills.Skills;
import com.zomboidcraft.survival.TimedActions;
import com.zomboidcraft.zombie.Noise;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
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
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Builds what the Build page asks for: checks it all again on the server, then runs it as a timed action (stand still,
 * a bar fills) and only uses the materials when it's done.
 */
public final class Builder {
    /** Build {@code build} at what the player is looking at ({@code hit} from {@code face}); a MAKE build ignores those. */
    public record Request(int build, BlockPos hit, int face, int looking) implements CustomPacketPayload {
        public static final Type<Request> TYPE = new Type<>(ZomboidCraft.id("build"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Request> CODEC = StreamCodec.of(
                (buf, r) -> {
                    buf.writeVarInt(r.build());
                    buf.writeBlockPos(r.hit());
                    buf.writeVarInt(r.face());
                    buf.writeVarInt(r.looking());
                },
                buf -> new Request(buf.readVarInt(), buf.readBlockPos(), buf.readVarInt(), buf.readVarInt()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** How far away you can build. */
    public static final double REACH = 4.5;

    public static void register() {
        PayloadTypeRegistry.serverboundPlay().register(Request.TYPE, Request.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(Request.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            player.level().getServer().execute(() -> start(player, payload));
        });
    }

    private static void start(ServerPlayer player, Request request) {
        Build[] all = Build.values();
        if (request.build() < 0 || request.build() >= all.length) return;
        Build build = all[request.build()];
        Direction face = Direction.from3DDataValue(request.face());
        Direction looking = Direction.from3DDataValue(request.looking());
        if (!check(player, build, request.hit(), face, looking)) return;
        String key = "build:" + build.name() + ":" + request.hit().asLong();
        if (TimedActions.doing(player, key)) return;
        ServerLevel level = (ServerLevel) player.level();
        level.playSound(null, player.blockPosition(), startSound(build), SoundSource.PLAYERS, 0.6f, 1f);
        TimedActions.start(player, key, verb(build), build.ticksFor(player), p -> {
            if (check(p, build, request.hit(), face, looking)) finish(p, build, request.hit(), face, looking);
        });
    }

    private static String verb(Build build) {
        return switch (build) {
            case BOARD -> "Boarding up";
            case METAL_SHEET -> "Fixing a metal sheet";
            case TRIPWIRE -> "Stringing a tripwire";
            case RAIN_BARREL -> "Setting up a rain barrel";
            case SANDBAGS -> "Filling sandbags";
            case WELD_SHEET -> "Welding a sheet";
        };
    }

    private static SoundEvent startSound(Build build) {
        return switch (build) {
            case METAL_SHEET, WELD_SHEET -> SoundEvents.FIRECHARGE_USE;
            case SANDBAGS -> SoundEvents.SHOVEL_FLATTEN.value();
            case BOARD -> SoundEvents.WOOD_HIT;
            default -> SoundEvents.ARMOR_EQUIP_LEATHER.value();
        };
    }

    /** Whether it can be built right now, saying why not. */
    private static boolean check(ServerPlayer player, Build build, BlockPos hit, Direction face, Direction looking) {
        String missing = build.missing(player);
        if (missing != null) return say(player, missing);
        if (build.where == Build.Where.MAKE) return true;
        if (player.position().distanceToSqr(Vec3.atCenterOf(hit)) > (REACH + 1) * (REACH + 1)) return say(player, "That's too far away.");
        Build.Spot spot = build.spot(player.level(), hit, face, looking);
        return spot.ok() || say(player, spot.problem());
    }

    private static boolean say(ServerPlayer player, String text) {
        player.sendOverlayMessage(Component.literal(text).withStyle(ChatFormatting.GRAY));
        return false;
    }

    private static void finish(ServerPlayer player, Build build, BlockPos hit, Direction face, Direction looking) {
        ServerLevel level = (ServerLevel) player.level();
        boolean free = player.hasInfiniteMaterials();
        if (!free) {
            for (Build.Need n : build.needs) {
                // a skilled carpenter sometimes saves the nails
                int count = n.icon() == ModItems.NAILS && player.getRandom().nextFloat() < 0.05f * Skills.level(player, build.skill) ? 0 : n.count();
                if (count > 0 && !Materials.take(player, n.fits(), count)) {
                    say(player, "Something you needed went missing.");
                    return;
                }
            }
            if (build.tool != null) {
                ItemStack tool = Materials.find(player, build.tool.fits());
                if (tool.isDamageableItem()) tool.hurtAndBreak(build == Build.WELD_SHEET ? 3 : 1, level, player, item -> { });
            }
        }
        Vec3 at;
        if (build.where == Build.Where.MAKE) {
            ItemStack made = new ItemStack(ModItems.METAL_SHEET);
            if (!player.getInventory().add(made)) net.minecraft.world.level.block.Block.popResource(level, player.blockPosition(), made);
            at = player.position();
        } else {
            Build.Spot spot = build.spot(level, hit, face, looking);
            BlockPos pos = spot.pos();
            BlockState now = level.getBlockState(pos);
            BlockState place = spot.state();
            if (build == Build.BOARD && now.is(ModBlocks.BARRICADE)) {
                int strength = Math.max(now.getValue(BarricadeBlock.STRENGTH), BarricadeBlock.strengthFor(Skills.level(player, build.skill)));
                place = now.getValue(BarricadeBlock.DAMAGE) > 0 ? now.setValue(BarricadeBlock.DAMAGE, 0)
                        : now.setValue(BarricadeBlock.PLANKS, now.getValue(BarricadeBlock.PLANKS) + 1);
                place = place.setValue(BarricadeBlock.STRENGTH, strength);
            } else if (build == Build.BOARD) {
                place = place.setValue(BarricadeBlock.STRENGTH, BarricadeBlock.strengthFor(Skills.level(player, build.skill)));
            }
            level.setBlockAndUpdate(pos, place);
            at = Vec3.atCenterOf(pos);
            level.playSound(null, pos, doneSound(build), SoundSource.PLAYERS, 1f, 0.92f + level.getRandom().nextFloat() * 0.16f);
        }
        if (build.noise > 0) Noise.emit(level, at, build.noise);
        Skills.addXp(player, build.skill, build.xp);
    }

    private static SoundEvent doneSound(Build build) {
        return switch (build) {
            case BOARD -> ModSounds.HAMMER_NAIL;
            case METAL_SHEET -> SoundEvents.ANVIL_PLACE;
            case TRIPWIRE -> SoundEvents.CHAIN_PLACE;
            case SANDBAGS -> SoundEvents.SAND_PLACE;
            default -> SoundEvents.WOOD_PLACE;
        };
    }

    private Builder() {
    }
}
