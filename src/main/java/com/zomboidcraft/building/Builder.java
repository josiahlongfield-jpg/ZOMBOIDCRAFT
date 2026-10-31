package com.zomboidcraft.building;

import com.zomboidcraft.ModBlocks;
import com.zomboidcraft.ModItems;
import com.zomboidcraft.ZomboidCraft;
import com.zomboidcraft.barricade.BarricadeBlock;
import com.zomboidcraft.combat.ModSounds;
import com.zomboidcraft.world.cars.CarProp;
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
    /**
     * Build {@code build} at what the player is looking at ({@code hit} from {@code face}), or on {@code entity} (a car's
     * id, -1 for none) for a CAR build; a MAKE build ignores them.
     */
    public record Request(int build, BlockPos hit, int face, int looking, int entity) implements CustomPacketPayload {
        public Request(int build, BlockPos hit, int face, int looking) {
            this(build, hit, face, looking, -1);
        }

        public static final Type<Request> TYPE = new Type<>(ZomboidCraft.id("build"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Request> CODEC = StreamCodec.of(
                (buf, r) -> {
                    buf.writeVarInt(r.build());
                    buf.writeBlockPos(r.hit());
                    buf.writeVarInt(r.face());
                    buf.writeVarInt(r.looking());
                    buf.writeVarInt(r.entity());
                },
                buf -> new Request(buf.readVarInt(), buf.readBlockPos(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt()));

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
        if (build.where == Build.Where.CAR) {
            startOnCar(player, build, request.entity());
            return;
        }
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
            case ALARM_CLOCK -> "Setting the alarm";
            case CAR_ALARM -> "Wiring the clock to the alarm";
            case PLANTER -> "Building a planter";
            case SPIKED_WALL -> "Sharpening and driving logs";
            case CRATE -> "Nailing a crate together";
            case WALL_FRAME -> "Framing a wall";
            case WOOD_WALL -> "Boarding the wall";
            case WINDOW_FRAME -> "Framing a window";
            case DOOR_FRAME -> "Framing a doorway";
            case WOOD_DOOR -> "Hanging a door";
            case POST -> "Setting a post";
            case FLOOR -> "Laying floorboards";
            case WOOD_STAIRS -> "Building stairs";
            case LADDER -> "Nailing up a ladder";
            case ROOF -> "Shingling the roof";
            case SHUTTERS -> "Hanging shutters";
        };
    }

    private static SoundEvent startSound(Build build) {
        return switch (build) {
            case METAL_SHEET, WELD_SHEET -> SoundEvents.FIRECHARGE_USE;
            case SANDBAGS -> SoundEvents.SHOVEL_FLATTEN.value();
            case BOARD, PLANTER, SPIKED_WALL, CRATE, WALL_FRAME, WOOD_WALL, WINDOW_FRAME, DOOR_FRAME, WOOD_DOOR, POST, FLOOR, WOOD_STAIRS, LADDER, ROOF, SHUTTERS -> SoundEvents.WOOD_HIT;
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
        // the clock's time, read before the clock is used up
        int timer = AlarmClockItem.carried(player);
        if (!useUp(player, build)) return;
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
            if (build.ordinal() >= Build.CRATE.ordinal()) {
                WoodBlocks.place(level, pos, place, build.skillLevel(player));
            } else {
                level.setBlockAndUpdate(pos, place);
            }
            if (build == Build.ALARM_CLOCK) {
                BuildBlocks.AlarmClock.wind(level, pos, timer * 20);
                say(player, "It'll ring in " + AlarmClockItem.says(timer) + ".");
            }
            at = Vec3.atCenterOf(pos);
            level.playSound(null, pos, doneSound(build), SoundSource.PLAYERS, 1f, 0.92f + level.getRandom().nextFloat() * 0.16f);
        }
        if (build.noise > 0) Noise.emit(level, at, build.noise);
        if (build.skill != null && build.xp > 0) Skills.addXp(player, build.skill, build.xp);
    }

    /** Takes what it uses (nothing in creative) and wears the tool. False when something went missing meanwhile. */
    private static boolean useUp(ServerPlayer player, Build build) {
        if (player.hasInfiniteMaterials()) return true;
        for (Build.Need n : build.needs) {
            // a skilled carpenter sometimes saves the nails
            int count = n.icon() == ModItems.NAILS && player.getRandom().nextFloat() < 0.05f * build.skillLevel(player) ? 0 : n.count();
            if (count > 0 && !Materials.take(player, n.fits(), count)) {
                say(player, "Something you needed went missing.");
                return false;
            }
        }
        if (build.tool != null) {
            ItemStack tool = Materials.find(player, build.tool.fits());
            int uses = build == Build.WELD_SHEET || build == Build.SPIKED_WALL ? 3 : 1;
            if (com.zomboidcraft.combat.Weapons.spec(tool) != null) {
                com.zomboidcraft.combat.Wear.use(tool, player, net.minecraft.world.InteractionHand.MAIN_HAND, uses);    // a hammer's chance a use
            } else if (tool.isDamageableItem()) {
                tool.hurtAndBreak(uses, (ServerLevel) player.level(), player, item -> { });
            }
        }
        return true;
    }

    /** A build on a parked car: the clock wired into its alarm. */
    private static void startOnCar(ServerPlayer player, Build build, int entity) {
        if (!checkCar(player, build, entity)) return;
        String key = "build:" + build.name() + ":car" + entity;
        if (TimedActions.doing(player, key)) return;
        ServerLevel level = (ServerLevel) player.level();
        level.playSound(null, player.blockPosition(), startSound(build), SoundSource.PLAYERS, 0.6f, 1f);
        TimedActions.start(player, key, verb(build), build.ticksFor(player), p -> {
            if (!checkCar(p, build, entity) || !(p.level().getEntity(entity) instanceof CarProp car)) return;
            int timer = AlarmClockItem.carried(p);
            if (!useUp(p, build)) return;
            car.rigAlarm(timer * 20);
            p.level().playSound(null, car.blockPosition(), SoundEvents.TRIPWIRE_ATTACH, SoundSource.PLAYERS, 1f, 1.2f);
            say(p, "Wired in. The alarm goes off in " + AlarmClockItem.says(timer) + ".");
        });
    }

    private static boolean checkCar(ServerPlayer player, Build build, int entity) {
        String missing = build.missing(player);
        if (missing != null) return say(player, missing);
        if (!(player.level().getEntity(entity) instanceof CarProp car)) return say(player, "Look at a parked car.");
        if (car.getBoundingBox().distanceToSqr(player.getEyePosition()) > (REACH + 1) * (REACH + 1)) return say(player, "That's too far away.");
        if (car.isVehicle()) return say(player, "Not with someone in it.");
        if (!car.engineRuns()) return say(player, "Its battery's dead: the alarm won't sound.");
        if (car.rigged()) return say(player, "There's a clock wired into it already.");
        return true;
    }

    private static SoundEvent doneSound(Build build) {
        return switch (build) {
            case BOARD, CRATE, WALL_FRAME, WOOD_WALL, WINDOW_FRAME, DOOR_FRAME, WOOD_DOOR, POST, FLOOR, WOOD_STAIRS, LADDER, ROOF, SHUTTERS -> ModSounds.HAMMER_NAIL;
            case METAL_SHEET -> SoundEvents.ANVIL_PLACE;
            case TRIPWIRE -> SoundEvents.CHAIN_PLACE;
            case SANDBAGS -> SoundEvents.SAND_PLACE;
            default -> SoundEvents.WOOD_PLACE;
        };
    }

    private Builder() {
    }
}
