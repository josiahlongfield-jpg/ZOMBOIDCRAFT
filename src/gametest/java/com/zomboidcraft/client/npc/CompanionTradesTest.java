package com.zomboidcraft.client.npc;

import com.zomboidcraft.ZcConfig;
import com.zomboidcraft.clothing.ClothWear;
import com.zomboidcraft.clothing.Clothes;
import com.zomboidcraft.clothing.Garment;
import com.zomboidcraft.clothing.Spot;
import com.zomboidcraft.npc.NpcEntity;
import com.zomboidcraft.npc.NpcPanel;
import com.zomboidcraft.npc.NpcSkills;
import com.zomboidcraft.npc.NpcTrades;
import com.zomboidcraft.survival.Body.Region;
import com.zomboidcraft.world.cars.CarParts;
import com.zomboidcraft.world.cars.CarParts.Part;
import com.zomboidcraft.world.cars.CarProp;
import com.zomboidcraft.world.cars.Cars;
import com.zomboidcraft.world.cars.Mechanics;
import java.util.List;
import java.util.Locale;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Her trades (NpcTrades), filmed: at home as the tailor she takes the holed hoodie and jeans from the stores, sits and
 * patches them and puts them back; as the mechanic she tops up a sedan's oil and swaps its dead battery and headlight for
 * the spares in the stores; then you take a tail light off and she comes over to lend a hand. Frames go to the
 * shots/pd5a26 branch (.github/workflows/companionshots.yml).
 */
public class CompanionTradesTest implements FabricClientGameTest {
    private static final Logger LOG = LoggerFactory.getLogger("ZCNPC");
    private int frame;
    private int carId;

    @Override
    public void runTest(ClientGameTestContext ctx) {
        try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
            sp.getConnection().waitForChunksRender();
            ctx.getInput().resizeWindow(1280, 720);
            for (String c : List.of("gamemode creative @a", "time set noon", "weather clear", "gamerule doDaylightCycle false",
                    "gamerule doMobSpawning false", "kill @e[type=!player]")) {
                sp.getServer().runCommand(c);
            }
            BlockPos c = sp.getServer().computeOnServer(CompanionTradesTest::stage);
            ctx.waitTicks(40);
            sp.getServer().runCommand("execute as @p at @p run zc npc dark_elf raw");
            ctx.waitTicks(10);
            // bond with her, make her wait, and give her a home where the stores are
            ctx.runOnClient(mc -> {
                NpcEntity npc = clientNpc(mc);
                ClientPlayNetworking.send(new NpcPanel.Bond(npc.getId(), true));
                ClientPlayNetworking.send(new NpcPanel.Order(npc.getId(), NpcPanel.WAIT));
            });
            ctx.waitTicks(10);
            sp.getServer().runCommand("tp @a " + (c.getX() + 0.5) + " " + c.getY() + " " + (c.getZ() + 0.5));
            ctx.waitTicks(5);
            for (String cmd : List.of("zc npc home", "zc npc job tailor", "zc npc skill TAILORING 3", "zc npc skill MECHANICS 2")) {
                sp.getServer().runCommand("execute as @p at @p run " + cmd);
            }
            sp.getServer().runOnServer(s -> {
                NpcEntity npc = npc(s);
                npc.teleportTo(c.getX() + 0.5, c.getY(), c.getZ() - 2.5);
            });
            sp.getServer().runCommand(stand(c));
            ctx.waitTicks(20);
            log(sp, c, "tailor start");
            for (int i = 0; i < 44; i++) {
                ctx.waitTicks(15);
                shot(ctx, "tailor");
                if (i % 11 == 10) log(sp, c, "tailoring " + i);
            }
            // the mechanic: a sedan on the drive with low oil, a dead battery and a broken headlight
            sp.getServer().runOnServer(s -> {
                ServerLevel level = s.overworld();
                CarProp car = Cars.spawn(level, c.getX() - 5.5, c.getY(), c.getZ() + 2.5, 90, "sedan", "usable", true);
                carId = car.getId();
            });
            ctx.waitTicks(10);
            sp.getServer().runOnServer(s -> {
                if (s.overworld().getEntity(carId) instanceof CarProp car && !car.parts().isEmpty()) {
                    String parts = car.parts();
                    parts = CarParts.set(parts, CarParts.slot(Part.OIL, 0), 30);
                    parts = CarParts.set(parts, CarParts.slot(Part.BATTERY, 0), 8);
                    parts = CarParts.set(parts, CarParts.slot(Part.HEADLIGHT, 0), 5);
                    parts = CarParts.set(parts, CarParts.slot(Part.TAIL_LIGHT, 0), 70);
                    car.setParts(parts);
                }
            });
            sp.getServer().runCommand("execute as @p at @p run zc npc job mechanic");
            log(sp, c, "mechanic start");
            for (int i = 0; i < 44; i++) {
                ctx.waitTicks(15);
                shot(ctx, "mechanic");
                if (i % 11 == 10) log(sp, c, "mechanic " + i);
            }
            // helping hands: you take the left tail light off with her close by
            sp.getServer().runCommand("execute as @p at @p run zc npc job none");
            sp.getServer().runOnServer(s -> {
                ServerPlayer p = s.getPlayerList().getPlayers().get(0);
                p.getInventory().add(item("screwdriver", 1));
                // a survivor's job (creative finishes it at once): a little Mechanics of your own
                p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
                com.zomboidcraft.skills.Skills.addXp(p, com.zomboidcraft.skills.Skill.MECHANICS, 200);
                if (s.overworld().getEntity(carId) instanceof CarProp car) {
                    s.getCommands().performPrefixedCommand(s.createCommandSourceStack(), String.format(Locale.ROOT,
                            "tp @a %.2f %.2f %.2f -90 25", car.getX() - 3.4, car.getY(), car.getZ() + 1.2));
                    NpcEntity npc = npc(s);
                    npc.teleportTo(car.getX() + 1.5, car.getY(), car.getZ() + 3.5);
                }
            });
            ctx.waitTicks(10);
            int tail = CarParts.slot(Part.TAIL_LIGHT, 0);
            sp.getServer().runOnServer(s -> {
                ServerPlayer p = s.getPlayerList().getPlayers().get(0);
                if (s.overworld().getEntity(carId) instanceof CarProp car) {
                    LOG.info("ZCNPC helper near the car: {}; tail light takes {} ticks with her, {} alone",
                            NpcTrades.helper(p, car) != null, Mechanics.ticks(p, car, tail, Mechanics.Job.REMOVE),
                            Math.round(Mechanics.ticks(p, car, tail, Mechanics.Job.REMOVE) / NpcTrades.HELP_SPEED));
                }
            });
            ctx.runOnClient(mc -> {
                if (mc.level.getEntity(carId) instanceof CarProp car) {
                    LOG.info("ZCNPC client sees a helper: {} ({} ticks)", NpcTrades.helper(mc.player, car) != null,
                            Mechanics.ticks(mc.player, car, tail, Mechanics.Job.REMOVE));
                    mc.gui.setScreen(new com.zomboidcraft.client.kit.MechanicsScreen(car));
                }
            });
            ctx.waitTicks(10);
            shot(ctx, "screen");
            ctx.runOnClient(mc -> {
                mc.gui.setScreen(null);
                ClientPlayNetworking.send(new Mechanics.Work(carId, tail, Mechanics.Job.REMOVE.ordinal()));
            });
            for (int i = 0; i < 24; i++) {
                ctx.waitTicks(5);
                shot(ctx, "help");
            }
            log(sp, c, "after helping");
        }
    }

    private void shot(ClientGameTestContext ctx, String what) {
        ctx.takeScreenshot(String.format("%03d-%s", frame++, what));
    }

    private static NpcEntity npc(MinecraftServer s) {
        ServerLevel level = s.overworld();
        ServerPlayer p = s.getPlayerList().getPlayers().get(0);
        return level.getEntitiesOfClass(NpcEntity.class, new AABB(p.blockPosition()).inflate(40)).get(0);
    }

    private static NpcEntity clientNpc(net.minecraft.client.Minecraft mc) {
        return mc.level.getEntitiesOfClass(NpcEntity.class, mc.player.getBoundingBox().inflate(40)).get(0);
    }

    private void log(TestSingleplayerContext sp, BlockPos c, String when) {
        sp.getServer().runOnServer(s -> {
            NpcEntity npc = npc(s);
            LOG.info("ZCNPC {}: tailoring {}, mechanics {}, doing {}", when, npc.skills.level(NpcSkills.Skill.TAILORING),
                    npc.skills.level(NpcSkills.Skill.MECHANICS), npc.action());
            for (BlockPos at : List.of(c.offset(4, 0, 3), c.offset(-3, 0, 4))) {
                if (s.overworld().getBlockEntity(at) instanceof Container box) {
                    StringBuilder b = new StringBuilder();
                    for (int i = 0; i < box.getContainerSize(); i++) {
                        ItemStack it = box.getItem(i);
                        if (it.isEmpty()) continue;
                        b.append(it.getCount()).append(' ').append(BuiltInRegistries.ITEM.getKey(it.getItem()).getPath());
                        if (Clothes.of(it) != null) {
                            b.append(" (holes ").append(ClothWear.of(it).holeCount()).append(", patches ").append(ClothWear.of(it).patchCount()).append(')');
                        }
                        Mechanics.Held h = Mechanics.held(it);
                        if (h != null && it.has(net.minecraft.core.component.DataComponents.CUSTOM_DATA)) b.append(" (").append(h.condition()).append("%)");
                        b.append(", ");
                    }
                    LOG.info("ZCNPC   store {}: {}", at.equals(c.offset(4, 0, 3)) ? "sewing" : "garage", b);
                }
            }
            for (String l : com.zomboidcraft.npc.NpcCare.carrying(npc, s.getPlayerList().getPlayers().get(0).getUUID())) LOG.info("ZCNPC   {}", l);
            if (s.overworld().getEntity(carId) instanceof CarProp car) {
                LOG.info("ZCNPC   car: oil {}, battery {}, headlight {}, tail light {}", CarParts.get(car.parts(), CarParts.slot(Part.OIL, 0)),
                        CarParts.get(car.parts(), CarParts.slot(Part.BATTERY, 0)), CarParts.get(car.parts(), CarParts.slot(Part.HEADLIGHT, 0)),
                        CarParts.get(car.parts(), CarParts.slot(Part.TAIL_LIGHT, 0)));
            }
        });
    }

    /** A lit stone floor, a stand for the camera and two store chests: the sewing things and the garage spares. */
    private static BlockPos stage(MinecraftServer s) {
        ZcConfig.get().population.enabled = false;
        ServerLevel level = s.overworld();
        ServerPlayer p = s.getPlayerList().getPlayers().get(0);
        BlockPos c = p.blockPosition();
        for (String cmd : List.of(
                "fill ~-14 ~-1 ~-14 ~14 ~-1 ~14 minecraft:smooth_stone", "fill ~-14 ~ ~-14 ~14 ~10 ~14 minecraft:air",
                "fill ~-14 ~-2 ~-14 ~14 ~-2 ~14 minecraft:stone",
                "fill ~ ~ ~-5 ~ ~2 ~-5 minecraft:stone_bricks")) {
            s.getCommands().performPrefixedCommand(s.createCommandSourceStack().withPosition(net.minecraft.world.phys.Vec3.atBottomCenterOf(c)), cmd);
        }
        for (int x = -12; x <= 12; x += 4) {
            for (int z = -12; z <= 12; z += 4) {
                level.setBlockAndUpdate(c.offset(x, -1, z), Blocks.SEA_LANTERN.defaultBlockState());
            }
        }
        fill(level, c.offset(4, 0, 3), List.of(holed("hoodie", 3), holed("jeans", 2), item("sewing_kit", 1), item("thread_spool", 2),
                item("rag", 6)));
        fill(level, c.offset(-3, 0, 4), List.of(item("motor_oil", 2), Mechanics.stack(Part.BATTERY, 85, CarParts.Kind.STANDARD),
                Mechanics.stack(Part.HEADLIGHT, 90, CarParts.Kind.STANDARD), item("screwdriver", 1)));
        LOG.info("ZCNPC stage at {}", c.toShortString());
        s.getCommands().performPrefixedCommand(s.createCommandSourceStack(), stand(c));
        return c;
    }

    private static void fill(ServerLevel level, BlockPos at, List<ItemStack> items) {
        level.setBlockAndUpdate(at, Blocks.CHEST.defaultBlockState());
        if (level.getBlockEntity(at) instanceof Container box) {
            for (int i = 0; i < items.size(); i++) box.setItem(i, items.get(i));
            box.setChanged();
        }
    }

    /** Up on the stand, looking over the floor. */
    private static String stand(BlockPos c) {
        return "tp @a " + (c.getX() + 0.5) + " " + (c.getY() + 3) + " " + (c.getZ() - 4.5) + " 0 32";
    }

    private static ItemStack item(String id, int n) {
        return new ItemStack(BuiltInRegistries.ITEM.getValue(com.zomboidcraft.ZomboidCraft.id(id)), n);
    }

    /** A piece of clothing torn through in its first few places. */
    private static ItemStack holed(String id, int holes) {
        ItemStack s = item(id, 1);
        Garment g = Clothes.of(s);
        int mask = 0, n = 0;
        if (g != null) {
            for (Spot spot : g.covers()) {
                for (Region r : spot.regions()) {
                    if (n++ < holes) mask |= 1 << spot.place(r);
                }
            }
        }
        s.set(ClothWear.TYPE, new ClothWear(10, mask, 0L));
        return s;
    }
}
