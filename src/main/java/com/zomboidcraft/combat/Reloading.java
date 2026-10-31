package com.zomboidcraft.combat;

import com.zomboidcraft.ModItems;
import com.zomboidcraft.ZcConfig;
import com.zomboidcraft.ZomboidCraft;
import com.zomboidcraft.skills.Skill;
import com.zomboidcraft.skills.Skills;
import com.zomboidcraft.survival.TimedActions;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.functions.SetItemCountFunction;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.minecraft.world.level.storage.loot.providers.number.UniformGenerator;
import net.minecraft.world.phys.Vec3;

/**
 * Handloading (Josiah 2026-10-31, our own addition to Zomboid's guns): every shot drops its spent case (a shotgun's
 * hull) at your feet, and a reloading bench turns cases back into rounds with a primer, a bullet head (lead shot for
 * shells) and a charge of powder. A bench is found in gun shops, hunters' garages and police stations, and can be
 * picked up and set down in your base. Batches of up to ten; the Reloading skill makes them quicker and spoils
 * fewer, and Gunsmith Monthly teaches it faster. There are no dud rounds (jams were cut).
 */
public final class Reloading {
    /** The bench block (kept on its own class so ModItems can name it without loading this one). */
    public static final Block BENCH = ReloadingBenchBlock.BENCH;

    /** The most rounds one go at the press makes. */
    public static final int BATCH = 10;
    /** How near (blocks) the bench must be. */
    private static final double REACH = 4.5;
    /** Ticks a round takes at Reloading 0 and at 10. */
    private static final int SLOW = 40, FAST = 15;
    /** From this level nothing is spoiled; below it up to SPOIL of each batch is. */
    private static final int SURE = 6;
    private static final double SPOIL = 0.3;

    /** A cartridge the bench can load: what it makes, from which case, with which head (or shot), and its powder. */
    public record Load(Item round, Item casing, Item head, int powder) {
        boolean shot() {
            return head == ModItems.LEAD_SHOT;
        }
    }

    public static final List<Load> LOADS = List.of(
            new Load(ModItems.PISTOL_AMMO, ModItems.SPENT_9MM, ModItems.BULLETS_9MM, 1),
            new Load(ModItems.POCKET_AMMO, ModItems.SPENT_380, ModItems.BULLETS_9MM, 1),
            new Load(ModItems.REVOLVER_AMMO, ModItems.SPENT_38, ModItems.BULLETS_38, 1),
            new Load(ModItems.MAGNUM_AMMO, ModItems.SPENT_44, ModItems.BULLETS_44, 1),
            new Load(ModItems.RIFLE_AMMO, ModItems.SPENT_556, ModItems.BULLETS_556, 1),
            new Load(ModItems.HUNTING_RIFLE_AMMO, ModItems.SPENT_308, ModItems.BULLETS_30, 1),
            new Load(ModItems.LEVER_AMMO, ModItems.SPENT_3030, ModItems.BULLETS_30, 1),
            new Load(ModItems.HEAVY_AMMO, ModItems.SPENT_50, ModItems.BULLETS_50, 3),
            new Load(ModItems.SHOTGUN_SHELL, ModItems.SPENT_HULLS, ModItems.LEAD_SHOT, 1));

    /** The case each kind of round leaves. Rimfire, bolts and grenades leave nothing to load again. */
    private static final Map<Item, Item> CASES = Map.of(
            ModItems.PISTOL_AMMO, ModItems.SPENT_9MM, ModItems.POCKET_AMMO, ModItems.SPENT_380,
            ModItems.REVOLVER_AMMO, ModItems.SPENT_38, ModItems.MAGNUM_AMMO, ModItems.SPENT_44,
            ModItems.RIFLE_AMMO, ModItems.SPENT_556, ModItems.HUNTING_RIFLE_AMMO, ModItems.SPENT_308,
            ModItems.LEVER_AMMO, ModItems.SPENT_3030, ModItems.HEAVY_AMMO, ModItems.SPENT_50,
            ModItems.SHOTGUN_SHELL, ModItems.SPENT_HULLS);

    /** From a player's game: load a batch of LOADS[load] at the bench at pos, or pick the bench up (load -1). */
    public record Use(BlockPos pos, int load) implements CustomPacketPayload {
        public static final Type<Use> TYPE = new Type<>(ZomboidCraft.id("reloading_use"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Use> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, Use::pos, ByteBufCodecs.VAR_INT, Use::load, Use::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    private Reloading() {
    }

    public static void register() {
        PayloadTypeRegistry.serverboundPlay().register(Use.TYPE, Use.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(Use.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            player.level().getServer().execute(() -> {
                if (player.isSpectator() || player.blockPosition().distSqr(payload.pos()) > REACH * REACH
                        || !player.level().getBlockState(payload.pos()).is(BENCH)) {
                    return;
                }
                if (payload.load() < 0) {
                    pickUp(player, payload.pos());
                } else if (payload.load() < LOADS.size()) {
                    load(player, payload.pos(), LOADS.get(payload.load()));
                }
            });
        });
        LootTableEvents.MODIFY.register((key, table, source, registries) -> {
            String path = key.identifier().getPath();
            if (key.identifier().getNamespace().equals(ZomboidCraft.MOD_ID)) {
                LootPool.Builder pool = makings(path);
                if (pool != null) {
                    table.withPool(pool);
                }
            }
        });
    }

    // ---------------------------------------------------------------- brass

    /** A shot or an opened gun throws out its empties: they land by the shooter's feet, to the right. */
    public static void eject(ServerPlayer player, GunType type, int count) {
        Item casing = CASES.get(type.ammo());
        if (casing == null || count <= 0 || !ZcConfig.get().guns.dropBrass) {
            return;
        }
        Vec3 right = Vec3.directionFromRotation(0, player.getYRot() + 90);
        Vec3 at = player.position().add(right.scale(0.45)).add(0, 1.1, 0);
        ItemEntity e = new ItemEntity(player.level(), at.x, at.y, at.z, new ItemStack(casing, count));
        e.setDeltaMovement(right.scale(0.12 + player.getRandom().nextDouble() * 0.08)
                .add((player.getRandom().nextDouble() - 0.5) * 0.06, 0.18, (player.getRandom().nextDouble() - 0.5) * 0.06));
        e.setPickUpDelay(20);   // not straight back into your pockets as it flies out; lying cases clear after 5 minutes
        player.level().addFreshEntity(e);
    }

    // ---------------------------------------------------------------- the bench

    /** What one go at the bench would make from what this player carries (0 if anything's missing). */
    public static int batch(Player player, Load load) {
        Inventory inv = player.getInventory();
        int cases = count(inv, load.casing()), primers = count(inv, ModItems.PRIMERS);
        int heads = load.shot() ? charges(inv, ModItems.LEAD_SHOT) : count(inv, load.head());
        int powder = charges(inv, ModItems.SMOKELESS_POWDER) / load.powder();
        return Math.min(BATCH, Math.min(Math.min(cases, primers), Math.min(heads, powder)));
    }

    /** Ticks a batch of n takes at this player's Reloading. */
    public static int ticks(Player player, int n) {
        int level = Skills.level(player, Skill.RELOADING);
        return Math.round(n * (SLOW - (SLOW - FAST) * level / (float) Skill.MAX_LEVEL));
    }

    private static void load(ServerPlayer player, BlockPos pos, Load load) {
        int n = batch(player, load);
        if (n <= 0) {
            player.sendOverlayMessage(Component.literal("You need " + missing(player, load) + " to load "
                    + load.round().getName(new ItemStack(load.round())).getString()).withStyle(ChatFormatting.GRAY));
            return;
        }
        String name = load.round().getName(new ItemStack(load.round())).getString();
        ServerLevel level = (ServerLevel) player.level();
        level.playSound(null, pos, SoundEvents.PISTON_EXTEND, SoundSource.BLOCKS, 0.3f, 1.6f);
        TimedActions.start(player, "reload_bench", "Loading " + n + " " + name, ticks(player, n), p -> {
            int now = Math.min(n, batch(p, load));
            if (now <= 0 || !p.level().getBlockState(pos).is(BENCH)) {
                return;
            }
            Inventory inv = p.getInventory();
            take(inv, load.casing(), now);
            take(inv, ModItems.PRIMERS, now);
            if (load.shot()) {
                spend(inv, ModItems.LEAD_SHOT, now);
            } else {
                take(inv, load.head(), now);
            }
            spend(inv, ModItems.SMOKELESS_POWDER, now * load.powder());
            int lvl = Skills.level(p, Skill.RELOADING);
            double spoil = lvl >= SURE ? 0 : SPOIL * (1 - lvl / (double) SURE);
            int made = 0;
            for (int i = 0; i < now; i++) {
                if (p.getRandom().nextDouble() >= spoil) made++;
            }
            if (made > 0 && !inv.add(new ItemStack(load.round(), made))) {
                p.drop(new ItemStack(load.round(), made), false);
            }
            Skills.addXp(p, Skill.RELOADING, 3 * now);
            p.level().playSound(null, pos, SoundEvents.ARMOR_EQUIP_CHAIN.value(), SoundSource.BLOCKS, 0.5f, 1.4f);
            p.sendOverlayMessage(Component.literal("Loaded " + made + " " + name
                    + (made < now ? " (" + (now - made) + " spoiled)" : "")).withStyle(ChatFormatting.GRAY));
        });
    }

    private static String missing(Player player, Load load) {
        Inventory inv = player.getInventory();
        if (count(inv, load.casing()) == 0) return "spent " + caseName(load);
        if (count(inv, ModItems.PRIMERS) == 0) return "primers";
        if (load.shot() ? charges(inv, ModItems.LEAD_SHOT) == 0 : count(inv, load.head()) == 0) {
            return load.shot() ? "lead shot" : load.head().getName(new ItemStack(load.head())).getString();
        }
        return "smokeless powder";
    }

    private static String caseName(Load load) {
        return load.casing().getName(new ItemStack(load.casing())).getString().replace("Spent ", "").replace("Fired ", "");
    }

    /** Takes the bench up (Zomboid's Pick Up), five seconds' work. */
    private static void pickUp(ServerPlayer player, BlockPos pos) {
        TimedActions.start(player, "bench_pick_up", "Picking up the reloading bench", 100, p -> {
            if (!p.level().getBlockState(pos).is(BENCH)) {
                return;
            }
            p.level().removeBlock(pos, false);
            ItemStack bench = new ItemStack(ModItems.RELOADING_BENCH);
            if (!p.getInventory().add(bench)) {
                p.drop(bench, false);
            }
        });
    }

    // ---------------------------------------------------------------- inventory

    private static int count(Inventory inv, Item item) {
        int n = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.is(item)) n += s.getCount();
        }
        return n;
    }

    /** Charges left in jugs or bags (each one's durability). */
    private static int charges(Inventory inv, Item item) {
        int n = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.is(item)) n += s.getMaxDamage() - s.getDamageValue();
        }
        return n;
    }

    private static void take(Inventory inv, Item item, int n) {
        for (int i = 0; i < inv.getContainerSize() && n > 0; i++) {
            ItemStack s = inv.getItem(i);
            if (s.is(item)) {
                int t = Math.min(n, s.getCount());
                s.shrink(t);
                n -= t;
            }
        }
    }

    /** Uses n charges, emptiest jug first; an emptied jug or bag is thrown away. */
    private static void spend(Inventory inv, Item item, int n) {
        while (n > 0) {
            int best = -1;
            for (int i = 0; i < inv.getContainerSize(); i++) {
                ItemStack s = inv.getItem(i);
                if (s.is(item) && (best < 0 || s.getDamageValue() > inv.getItem(best).getDamageValue())) best = i;
            }
            if (best < 0) return;
            ItemStack s = inv.getItem(best);
            int t = Math.min(n, s.getMaxDamage() - s.getDamageValue());
            if (s.getDamageValue() + t >= s.getMaxDamage()) {
                inv.setItem(best, ItemStack.EMPTY);
            } else {
                s.setDamageValue(s.getDamageValue() + t);
            }
            n -= t;
        }
    }

    // ---------------------------------------------------------------- loot

    /**
     * The makings, added to the gun shop, hunters' gun safes and basements, police armouries and home garages as a
     * pool of their own (the shelves' own tables are LOOT's, generated: they're left alone).
     */
    private static LootPool.Builder makings(String path) {
        return switch (path) {
            case "shop/guns" -> pool(UniformGenerator.between(1, 3),
                    e(ModItems.PRIMERS, 30, 20, 100), e(ModItems.SMOKELESS_POWDER, 22, 1, 1), e(ModItems.LEAD_SHOT, 12, 1, 1),
                    e(ModItems.BULLETS_9MM, 14, 20, 100), e(ModItems.BULLETS_38, 8, 20, 100), e(ModItems.BULLETS_44, 6, 20, 50),
                    e(ModItems.BULLETS_556, 12, 20, 100), e(ModItems.BULLETS_30, 12, 20, 100), e(ModItems.BULLETS_50, 2, 10, 30),
                    e(ModItems.GUNSMITH_MAGAZINE, 4, 1, 1));
            case "shop/home_guns", "shop/basement_guns" -> pool(UniformGenerator.between(0, 2),
                    e(ModItems.PRIMERS, 20, 10, 60), e(ModItems.SMOKELESS_POWDER, 14, 1, 1), e(ModItems.LEAD_SHOT, 8, 1, 1),
                    e(ModItems.BULLETS_9MM, 8, 10, 50), e(ModItems.BULLETS_30, 10, 10, 50), e(ModItems.BULLETS_556, 5, 10, 50),
                    e(ModItems.SPENT_308, 10, 5, 30), e(ModItems.SPENT_9MM, 8, 5, 30), e(ModItems.SPENT_HULLS, 10, 5, 25),
                    e(ModItems.GUNSMITH_MAGAZINE, 3, 1, 1));
            case "shop/police" -> pool(UniformGenerator.between(0, 1),
                    e(ModItems.PRIMERS, 10, 20, 60), e(ModItems.SMOKELESS_POWDER, 6, 1, 1), e(ModItems.BULLETS_9MM, 10, 20, 60),
                    e(ModItems.SPENT_9MM, 14, 10, 40), e(ModItems.SPENT_HULLS, 6, 5, 20), e(ModItems.SPENT_38, 6, 5, 30));
            case "shop/home_garage" -> pool(ConstantValue.exactly(1),
                    e(null, 90, 0, 0), e(ModItems.SMOKELESS_POWDER, 3, 1, 1), e(ModItems.PRIMERS, 3, 10, 40),
                    e(ModItems.SPENT_308, 3, 5, 20), e(ModItems.LEAD_SHOT, 1, 1, 1));
            case "shop/home_books" -> pool(ConstantValue.exactly(1), e(null, 97, 0, 0), e(ModItems.GUNSMITH_MAGAZINE, 3, 1, 1));
            default -> null;
        };
    }

    private record Entry(Item item, int weight, int min, int max) {
    }

    private static Entry e(Item item, int weight, int min, int max) {
        return new Entry(item, weight, min, max);
    }

    private static LootPool.Builder pool(net.minecraft.world.level.storage.loot.providers.number.NumberProvider rolls, Entry... entries) {
        LootPool.Builder pool = LootPool.lootPool().setRolls(rolls);
        for (Entry en : entries) {
            if (en.item() == null) {
                pool.add(net.minecraft.world.level.storage.loot.entries.EmptyLootItem.emptyItem().setWeight(en.weight()));
            } else if (en.max() > 1) {
                pool.add(LootItem.lootTableItem(en.item()).setWeight(en.weight())
                        .apply(SetItemCountFunction.setCount(UniformGenerator.between(en.min(), en.max()))));
            } else {
                pool.add(LootItem.lootTableItem(en.item()).setWeight(en.weight()));
            }
        }
        return pool;
    }
}
