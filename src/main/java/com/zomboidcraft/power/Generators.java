package com.zomboidcraft.power;

import com.zomboidcraft.ZcConfig;
import com.zomboidcraft.ZomboidCraft;
import com.zomboidcraft.loot.items.GasCanItem;
import com.zomboidcraft.loot.items.MagazineItem;
import com.zomboidcraft.skills.Skill;
import com.zomboidcraft.skills.Skills;
import com.zomboidcraft.survival.TimedActions;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Generators, Zomboid's way. The portable generator (and the military's quiet one) is set down from the hand, then
 * worked from the G menu: fill it from a gas can, connect it (only once you've read How to Use Generators), turn it
 * on, check it, fix it with generator parts (Electrical decides how much), pick it up. While it runs, connected, it
 * powers everything within the sandbox's Generator Tile Range and Vertical Power Range, as the mains would
 * ({@link PowerGrid#hasPower}); with Allow Exterior Generator off it powers only spots under a roof.
 */
public final class Generators {
    public static final String MAGAZINE = "mag_generators";
    private static final int FLOOR = 5;

    /** The two generators: id, tank (L), burn (x the sandbox rate), how far it's heard, hum volume. */
    public enum Kind {
        PORTABLE("portable_generator", 10f, 1.0, 20, 0.7f),
        QUIET("quiet_generator", 20f, 0.8, 6, 0.25f);

        public final String item;
        public final float tank;
        final double burn, noise;
        final float volume;

        Kind(String item, float tank, double burn, double noise, float volume) {
            this.item = item;
            this.tank = tank;
            this.burn = burn;
            this.noise = noise;
            this.volume = volume;
        }

        public static Kind of(String item) {
            for (Kind k : values()) {
                if (k.item.equals(item)) {
                    return k;
                }
            }
            return null;
        }
    }

    public static final Block PORTABLE = block(Kind.PORTABLE);
    public static final Block QUIET = block(Kind.QUIET);
    public static final BlockEntityType<GeneratorEntity> ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,
            ZomboidCraft.id("generator"), FabricBlockEntityTypeBuilder.create(GeneratorEntity::new, PORTABLE, QUIET).build());

    /** What the G menu asks a generator to do. */
    public enum Action { TOGGLE, CONNECT, FUEL, REPAIR, CHECK, PICK_UP }

    public record Use(BlockPos pos, int action) implements CustomPacketPayload {
        public static final Type<Use> TYPE = new Type<>(ZomboidCraft.id("generator_use"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Use> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, Use::pos, ByteBufCodecs.VAR_INT, Use::action, Use::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Running, connected generators by level (BlockPos.asLong), kept up as they tick. */
    private static final Map<ResourceKey<Level>, Set<Long>> LIVE = new HashMap<>();

    private Generators() {
    }

    private static Block block(Kind kind) {
        ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, ZomboidCraft.id("placed_" + kind.item));
        return Registry.register(BuiltInRegistries.BLOCK, key, new GeneratorBlock(BlockBehaviour.Properties.of()
                .strength(-1f, 6f).sound(SoundType.METAL).noOcclusion().setId(key), kind));
    }

    public static void register() {
        PayloadTypeRegistry.serverboundPlay().register(Use.TYPE, Use.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(Use.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            player.level().getServer().execute(() -> {
                if (player.isSpectator() || player.blockPosition().distSqr(payload.pos()) > 36
                        || !(player.level().getBlockEntity(payload.pos()) instanceof GeneratorEntity g)
                        || payload.action() < 0 || payload.action() >= Action.values().length) {
                    return;
                }
                use(player, g, Action.values()[payload.action()]);
            });
        });
    }

    static double fuelPerHour() {
        return ZcConfig.get().power.generatorFuel;
    }

    static void track(ServerLevel level, BlockPos pos, boolean live) {
        Set<Long> set = LIVE.computeIfAbsent(level.dimension(), k -> new HashSet<>());
        if (live) {
            set.add(pos.asLong());
        } else {
            set.remove(pos.asLong());
        }
    }

    /** Does a running, connected generator reach this spot? */
    public static boolean powers(ServerLevel level, BlockPos pos) {
        Set<Long> set = LIVE.get(level.dimension());
        if (set == null || set.isEmpty()) {
            return false;
        }
        ZcConfig.Power cfg = ZcConfig.get().power;
        if (!cfg.generatorExterior && level.canSeeSky(pos.above())) {
            return false;
        }
        double r = cfg.generatorRange;
        int up = cfg.generatorFloors * FLOOR;
        for (long l : set) {
            BlockPos g = BlockPos.of(l);
            double dx = g.getX() - pos.getX(), dz = g.getZ() - pos.getZ();
            if (dx * dx + dz * dz <= r * r && Math.abs(g.getY() - pos.getY()) <= up) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ the item

    /** Sets a generator down from the item: its fuel and wear go with it (a found one is worn, and empty). */
    public static boolean place(ServerLevel level, BlockPos at, net.minecraft.core.Direction facing, ItemStack stack, Kind kind) {
        if (!level.getBlockState(at).canBeReplaced() || !level.getBlockState(at.below()).isFaceSturdy(level, at.below(), net.minecraft.core.Direction.UP)) {
            return false;
        }
        Block block = kind == Kind.QUIET ? QUIET : PORTABLE;
        level.setBlock(at, block.defaultBlockState().setValue(GeneratorBlock.FACING, facing), 3);
        if (level.getBlockEntity(at) instanceof GeneratorEntity g) {
            CustomData data = stack.get(DataComponents.CUSTOM_DATA);
            var tag = data == null ? null : data.copyTag();
            g.fuel = tag == null ? 0f : tag.getFloatOr("fuel", 0f);
            g.condition = tag == null || !tag.contains("condition") ? 55 + level.random.nextInt(46) : tag.getIntOr("condition", 100);
            g.setChanged();
        }
        level.playSound(null, at, SoundEvents.ANVIL_PLACE, SoundSource.BLOCKS, 0.4f, 0.7f);
        return true;
    }

    /** A generator item's fuel and condition, for its tooltip. Null on a found one nobody has set down yet. */
    public static String describe(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null || !data.copyTag().contains("condition")) {
            return null;
        }
        var tag = data.copyTag();
        return String.format(Locale.ROOT, "Fuel %.1f L, condition %d%%", tag.getFloatOr("fuel", 0f), tag.getIntOr("condition", 100));
    }

    // ------------------------------------------------------------------ the G menu

    private static void use(ServerPlayer player, GeneratorEntity g, Action action) {
        ServerLevel level = (ServerLevel) player.level();
        BlockPos pos = g.getBlockPos();
        Kind kind = g.kind();
        switch (action) {
            case TOGGLE -> {
                if (g.running) {
                    g.setRunning(level, false);
                    level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.6f, 0.8f);
                    say(player, "You turn the generator off.");
                } else if (g.condition <= 0) {
                    say(player, "It's broken. Fix it with generator parts first.");
                } else if (g.fuel <= 0.01f) {
                    say(player, "It's out of fuel.");
                } else {
                    g.setRunning(level, true);
                    level.playSound(null, pos, SoundEvents.MINECART_RIDING, SoundSource.BLOCKS, 1f, 0.8f);
                    say(player, g.connected ? "The generator coughs into life." : "It runs, but nothing's connected to it.");
                }
            }
            case CONNECT -> {
                if (g.connected) {
                    say(player, "It's already connected.");
                } else if (!MagazineItem.knows(player, MAGAZINE)) {
                    say(player, "You don't know how. Read How to Use Generators first.");
                } else {
                    TimedActions.start(player, "generator_connect", "Connecting the generator", 100, p -> {
                        if (!g.isRemoved()) {
                            g.connected = true;
                            g.setRunning(level, g.running);
                            say(p, "Connected. It powers everything round it while it runs.");
                        }
                    });
                }
            }
            case FUEL -> {
                ItemStack can = can(player);
                if (can == null) {
                    say(player, "You need a gas can with fuel in it.");
                } else if (g.fuel >= kind.tank - 0.05f) {
                    say(player, "The tank's full.");
                } else {
                    TimedActions.start(player, "generator_fuel", "Filling the generator", 60, p -> {
                        ItemStack c = can(p);
                        if (c == null || g.isRemoved()) {
                            return;
                        }
                        float take = Math.min(kind.tank - g.fuel, GasCanItem.fuel(c));
                        GasCanItem.setFuel(c, GasCanItem.fuel(c) - take);
                        g.fuel += take;
                        g.setChanged();
                        level.playSound(null, pos, SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 0.6f, 1.2f);
                        say(p, String.format(Locale.ROOT, "The tank's got %.1f of %.0f L.", g.fuel, kind.tank));
                    });
                }
            }
            case REPAIR -> {
                if (g.condition >= 100) {
                    say(player, "Nothing to fix.");
                } else if (find(player, "generator_parts") < 0) {
                    say(player, "You need generator parts to fix it.");
                } else if (g.running) {
                    say(player, "Turn it off first.");
                } else {
                    TimedActions.start(player, "generator_fix", "Fixing the generator", 160, p -> {
                        int slot = find(p, "generator_parts");
                        if (slot < 0 || g.isRemoved()) {
                            return;
                        }
                        p.getInventory().getItem(slot).shrink(1);
                        int lv = Skills.level(p, Skill.ELECTRICAL);
                        g.condition = Math.min(100, g.condition + 10 + 8 * lv);
                        g.setChanged();
                        Skills.addXp(p, Skill.ELECTRICAL, 25);
                        level.playSound(null, pos, SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 0.4f, 1.4f);
                        say(p, "Fixed up to " + g.condition + "%.");
                    });
                }
            }
            case CHECK -> say(player, String.format(Locale.ROOT, "%s, fuel %.1f of %.0f L, condition %d%%, %s.",
                    g.running ? "Running" : "Off", g.fuel, kind.tank, g.condition, g.connected ? "connected" : "not connected"));
            case PICK_UP -> {
                if (g.running) {
                    say(player, "Turn it off first.");
                    return;
                }
                ItemStack out = new ItemStack(BuiltInRegistries.ITEM.getValue(ZomboidCraft.id(kind.item)));
                float fuel = g.fuel;
                int condition = g.condition;
                CustomData.update(DataComponents.CUSTOM_DATA, out, tag -> {
                    tag.putFloat("fuel", fuel);
                    tag.putInt("condition", condition);
                });
                level.removeBlock(pos, false);
                if (!player.getInventory().add(out)) {
                    player.drop(out, false);
                }
            }
        }
    }

    private static ItemStack can(ServerPlayer player) {
        Inventory inv = player.getInventory();
        ItemStack held = player.getMainHandItem();
        if (held.getItem() instanceof GasCanItem && GasCanItem.fuel(held) > 0.05f) {
            return held;
        }
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.getItem() instanceof GasCanItem && GasCanItem.fuel(s) > 0.05f) {
                return s;
            }
        }
        return null;
    }

    private static int find(ServerPlayer player, String id) {
        Inventory inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (BuiltInRegistries.ITEM.getKey(inv.getItem(i).getItem()).getPath().equals(id)) {
                return i;
            }
        }
        return -1;
    }

    private static void say(ServerPlayer player, String text) {
        player.sendOverlayMessage(Component.literal(text));
    }

    /** Is this a set-down generator? (Client: the green dot.) */
    public static boolean is(BlockState state) {
        return state.getBlock() instanceof GeneratorBlock;
    }
}
