package com.zomboidcraft.client.kit;

import com.mojang.blaze3d.platform.InputConstants;
import com.zomboidcraft.ModItems;
import com.zomboidcraft.building.Build;
import com.zomboidcraft.building.DigRules;
import com.zomboidcraft.building.WoodBlocks;
import com.zomboidcraft.building.Moving;
import com.zomboidcraft.carry.Carry;
import com.zomboidcraft.carry.Weights;
import com.zomboidcraft.skills.Skill;
import com.zomboidcraft.skills.Skills;
import com.zomboidcraft.world.blocks.CityBlocks;
import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.KeyMapping;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base customisation wave 1 in a real client: a furnished room, then lifting each piece the way a player does (hold
 * left click), refused without its tool, breaking by Zomboid's chance at low skill and never at 10, and what it then
 * weighs on you; the steps of knocking
 * through walls; earth that only goes back into the ground; and the Skills page. Screenshots are numbered in order so
 * they stitch into the preview video. Run on CI by .github/workflows/basetest.yml.
 */
public class BaseMoveTest implements FabricClientGameTest {
    private static final Logger LOG = LoggerFactory.getLogger("ZCBASE");
    private static int shot;
    /** The room's corner, and the row of furniture along z = row. */
    private static BlockPos origin;

    private static final List<String> ROW = List.of("home_chair", "home_sofa_blue_l", "home_sofa_blue_m", "home_sofa_blue_r",
            "home_sink_white", "home_fridge", "home_wardrobe", "home_toilet", "home_lamp_table");

    @Override
    public void runTest(ClientGameTestContext ctx) {
        try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
            sp.getConnection().waitForChunksRender();
            for (String c : new String[] {"time set noon", "weather clear", "gamerule doDaylightCycle false", "gamerule doMobSpawning false", "effect give @a night_vision infinite 0 true"}) {
                sp.getServer().runCommand(c);
            }
            ctx.getInput().resizeWindow(1280, 720);
            sp.getServer().runOnServer(BaseMoveTest::room);
            ctx.waitTicks(40);
            sp.getConnection().waitForChunksRender();
            if (ROOMS) {
                rooms(ctx, sp);
                effects(ctx, sp);
                return;
            }
            stand(ctx, sp, 4, 1, 60, 30);
            frame(ctx, "room");

            wood(ctx, sp);
            buildPage(ctx);
            if (!LIFTING) return;

            LOG.info("ZCBASE break chance by level (tool jobs / pipe wrench): {}", chances());
            // a chair: small and loose, by hand, never breaks
            lift(ctx, sp, "home_chair", 0);
            // a sofa end: furniture, it won't come without a hammer
            lift(ctx, sp, "home_sofa_blue_l", 1);
            // with a hammer at Carpentry 0 it comes, but 75% of the time it comes apart
            hold(sp, new ItemStack(ModItems.HAMMER));
            lift(ctx, sp, "home_sofa_blue_l", 1);
            lift(ctx, sp, "home_sofa_blue_m", 2);
            // at Carpentry 10 nothing breaks
            sp.getServer().runOnServer(s -> Skills.addXp(player(s), Skill.CARPENTRY, Skill.xpForLevel(10)));
            lift(ctx, sp, "home_sofa_blue_r", 3);
            sp.getServer().runOnServer(s -> {
                if (s.overworld().getBlockEntity(at(6)) instanceof RandomizableContainer c) c.setLootTable(null);
            });
            lift(ctx, sp, "home_wardrobe", 6);
            // a sink: plumbed in, it wants a pipe wrench, then emptying, then it comes (25% break at Plumbing 0)
            hold(sp, ItemStack.EMPTY);
            lift(ctx, sp, "home_sink_white", 4);
            hold(sp, new ItemStack(ModItems.PIPE_WRENCH));
            lift(ctx, sp, "home_sink_white", 4);
            sp.getServer().runOnServer(s -> {
                if (s.overworld().getBlockEntity(at(4)) instanceof RandomizableContainer c) c.setLootTable(null);
            });
            lift(ctx, sp, "home_sink_white", 4);
            lift(ctx, sp, "home_toilet", 7);
            // the fridge: no tool and it never breaks, it's just heavy
            hold(sp, ItemStack.EMPTY);
            sp.getServer().runOnServer(s -> {
                if (s.overworld().getBlockEntity(at(5)) instanceof RandomizableContainer c) c.setLootTable(null);
            });
            lift(ctx, sp, "home_fridge", 5);
            String load = sp.getServer().computeOnServer(s -> {
                ServerPlayer p = player(s);
                return String.format("load %.1f of %.1f kg", Carry.load(p), Carry.capacity());
            });
            LOG.info("ZCBASE carrying: {}", load);
            frame(ctx, "carrying");

            gMenu(ctx, sp);
            walls(ctx, sp);
            soil(ctx, sp);
            pages(ctx);
        }
    }

    /** Rooms plan step 1: the base claim and the room check, filmed. Set false to film the wood building waves again. */
    private static final boolean ROOMS = true;

    /** The barracks: a block south of the first room, three rooms in a row with plank doors between them. */
    private static BlockPos barracks;

    private static void put(ServerLevel level, int x, int y, int z, String name) {
        BlockState st = CityBlocks.resolve(name.contains("[") ? name : name + "[facing=south]");
        if (st == null) st = CityBlocks.resolve(name);
        if (st == null) {
            LOG.info("ZCBASE missing block {}", name);
            return;
        }
        level.setBlockAndUpdate(barracks.offset(x, y, z), st);
    }

    private static void barracks(MinecraftServer s) {
        ServerLevel level = s.overworld();
        barracks = origin.offset(0, 0, 12);
        BlockState floor = CityBlocks.resolve("home_floor_oak"), wall = CityBlocks.resolve("home_wall_cream"),
                brick = CityBlocks.resolve("brick_red"), ceiling = CityBlocks.resolve("home_floor_oak");
        for (int x = -1; x <= 17; x++) {
            for (int z = -1; z <= 9; z++) {
                level.setBlockAndUpdate(barracks.offset(x, 0, z), floor);
                boolean in = x >= 0 && x <= 16 && z >= 0 && z <= 8;
                level.setBlockAndUpdate(barracks.offset(x, 5, z), in ? ceiling : Blocks.AIR.defaultBlockState());
                for (int y = 1; y <= 4; y++) {
                    boolean edge = x == 0 || x == 16 || z == 0 || z == 8;
                    boolean part = x == 5 || x == 11;
                    level.setBlockAndUpdate(barracks.offset(x, y, z), !in ? Blocks.AIR.defaultBlockState() : edge ? brick : part ? wall : Blocks.AIR.defaultBlockState());
                }
            }
        }
        // the front door on the north wall of the bunk room, and doors through both partitions
        door(level, barracks.offset(2, 1, 0), Direction.SOUTH);
        door(level, barracks.offset(5, 1, 4), Direction.EAST);
        door(level, barracks.offset(11, 1, 4), Direction.EAST);
        // a ceiling lamp in each room
        for (int x : new int[] {2, 8, 13}) level.setBlockAndUpdate(barracks.offset(x, 5, 4), CityBlocks.resolve("ceiling_light"));
    }

    private static void door(ServerLevel level, BlockPos lower, Direction facing) {
        BlockState d = WoodBlocks.DOOR.defaultBlockState().setValue(net.minecraft.world.level.block.DoorBlock.FACING, facing);
        level.setBlock(lower, d.setValue(net.minecraft.world.level.block.DoorBlock.HALF, net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER), 18);
        level.setBlock(lower.above(), d.setValue(net.minecraft.world.level.block.DoorBlock.HALF, net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER), 18);
    }

    /** Stands in the barracks at (x, z). */
    private static void standIn(ClientGameTestContext ctx, TestSingleplayerContext sp, double x, double z, float yaw, float pitch) {
        sp.getServer().runCommand(String.format(java.util.Locale.ROOT, "tp @a %.2f %d %.2f %.1f %.1f",
                barracks.getX() + x + 0.5, barracks.getY() + 1, barracks.getZ() + z + 0.5, yaw, pitch));
        ctx.waitTicks(10);
    }

    /** What the room check reads where the player stands, for the log. */
    private static String reading(TestSingleplayerContext sp) {
        return sp.getServer().computeOnServer(s -> {
            var r = com.zomboidcraft.building.base.Rooms.read(s.overworld(), player(s));
            var c = com.zomboidcraft.building.base.Claims.at(s.overworld(), player(s).blockPosition());
            String why = c == null ? "no claim at " + player(s).blockPosition() : com.zomboidcraft.building.base.Rooms.explain(s.overworld(), player(s).blockPosition(), c);
            return r == null ? "no room [" + why + "]" : r.title() + " (next: " + r.next() + ", floor " + r.floor() + ", powered " + r.powered() + ", " + r.fixtures() + ")";
        });
    }

    private static void rooms(ClientGameTestContext ctx, TestSingleplayerContext sp) {
        sp.getServer().runOnServer(BaseMoveTest::barracks);
        sp.getServer().runOnServer(s -> {
            ServerLevel level = s.overworld();
            // the bunk room, west: two bunks to start with
            put(level, 1, 1, 6, "bunk_bed_head");
            put(level, 1, 1, 7, "bunk_bed_foot");
            put(level, 3, 1, 6, "bunk_bed_head");
            put(level, 3, 1, 7, "bunk_bed_foot");
        });
        ctx.waitTicks(20);
        sp.getConnection().waitForChunksRender();
        standIn(ctx, sp, 2, 1, 0f, 22f);
        frame(ctx, "barracks-unclaimed");
        LOG.info("ZCBASE before claim: {}", reading(sp));
        menu(ctx, "gmenu-claim");
        String lines = ctx.computeOnClient(mc -> String.valueOf(com.zomboidcraft.client.interact.InteractMenu.collect(mc, mc.player, mc.hitResult)
                .stream().map(o -> o.label() + (o.can() ? "" : " [" + o.why() + "]")).toList()));
        LOG.info("ZCBASE g menu indoors: {}", lines);
        String claimed = sp.getServer().computeOnServer(s -> {
            String said = com.zomboidcraft.building.base.Claims.claim(s.overworld(), player(s));
            var c = com.zomboidcraft.building.base.Claims.baseOf(s.overworld(), player(s).getUUID());
            return said + " box " + (c == null ? "none" : c.min() + " to " + c.max());
        });
        LOG.info("ZCBASE claim: {}", claimed);
        ctx.waitTicks(25);
        LOG.info("ZCBASE bunk two beds: {}", reading(sp));
        frame(ctx, "bunk-basic");
        sp.getServer().runOnServer(s -> {
            put(s.overworld(), 2, 1, 7, "bunk_footlocker");
            put(s.overworld(), 4, 1, 7, "bunk_footlocker");
        });
        standIn(ctx, sp, 7, 3, 0f, 22f);
        standIn(ctx, sp, 2, 1, 0f, 22f);
        ctx.waitTicks(25);
        LOG.info("ZCBASE bunk with footlockers: {}", reading(sp));
        frame(ctx, "bunk-full");

        // the med bay, middle: a stocked first-aid cabinet and a hospital bed, then the light on and a sink (the surgical table is still to make)
        sp.getServer().runOnServer(s -> {
            ServerLevel level = s.overworld();
            put(level, 7, 1, 7, "bunk_st_medical");
            if (level.getBlockEntity(barracks.offset(7, 1, 7)) instanceof net.minecraft.world.Container box) {
                if (box instanceof RandomizableContainer rc) rc.setLootTable(null);
                box.setItem(0, new ItemStack(net.minecraft.world.item.Items.PAPER, 4));
            }
            put(level, 9, 1, 6, "hs_bed_head");
            put(level, 9, 1, 7, "hs_bed_foot");
            level.setBlockAndUpdate(barracks.offset(8, 5, 4), CityBlocks.resolve("ceiling_light_off"));
        });
        standIn(ctx, sp, 8, 1, 0f, 22f);
        ctx.waitTicks(25);
        LOG.info("ZCBASE med bay, light off: {}", reading(sp));
        frame(ctx, "medbay-basic");
        sp.getServer().runOnServer(s -> s.overworld().setBlockAndUpdate(barracks.offset(8, 5, 4), CityBlocks.resolve("ceiling_light")));
        standIn(ctx, sp, 7, 1, 10f, 22f);
        ctx.waitTicks(25);
        LOG.info("ZCBASE med bay, light on: {}", reading(sp));
        sp.getServer().runOnServer(s -> {
            put(s.overworld(), 10, 1, 1, "hs_sink");
        });
        standIn(ctx, sp, 8, 2, 0f, 22f);
        ctx.waitTicks(25);
        LOG.info("ZCBASE med bay, sink: {}", reading(sp));
        frame(ctx, "medbay-equipped");

        // the kitchen, east: a wood stove and a counter, then a fridge and sink, then the pantry
        sp.getServer().runOnServer(s -> {
            ServerLevel level = s.overworld();
            put(level, 12, 1, 7, "home_wood_stove");
            put(level, 13, 1, 7, "home_counter_oak");
            put(level, 14, 1, 7, "home_fridge");
            put(level, 15, 1, 7, "home_sink_oak");
            put(level, 15, 1, 1, "store_shelf_grocery");
            put(level, 14, 1, 1, "store_shelf_grocery");
        });
        standIn(ctx, sp, 13, 2, 0f, 22f);
        ctx.waitTicks(25);
        LOG.info("ZCBASE kitchen, two shelves: {}", reading(sp));
        frame(ctx, "kitchen-equipped");

        // someone outside the squad can't touch it; let in, they can
        String guard = sp.getServer().computeOnServer(s -> {
            ServerLevel level = s.overworld();
            var other = net.fabricmc.fabric.api.entity.FakePlayer.get(level, new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "Rook"));
            other.setGameMode(GameType.SURVIVAL);
            BlockPos bed = barracks.offset(1, 1, 6);
            StringBuilder b = new StringBuilder();
            b.append(" stranger may edit=").append(com.zomboidcraft.building.base.Claims.mayEdit(level, bed, other))
                    .append(" (").append(com.zomboidcraft.building.base.Claims.refusal(level, bed, other)).append(")");
            b.append(" | moving: ").append(Moving.refusal(Moving.piece(level.getBlockState(bed), level, bed), other, level, bed));
            b.append(" | ").append(com.zomboidcraft.building.base.Claims.squad(level, player(s), other.getUUID(), true));
            b.append(" | squad may edit=").append(com.zomboidcraft.building.base.Claims.mayEdit(level, bed, other));
            b.append(" | outside may edit=").append(com.zomboidcraft.building.base.Claims.mayEdit(level, barracks.offset(30, 1, 4), other));
            return b.toString();
        });
        LOG.info("ZCBASE protection:{}", guard);

        // building onto it grows the claim: a post against the east wall, outside
        sp.getServer().runOnServer(s -> {
            ServerPlayer p = player(s);
            Skills.addXp(p, Skill.CARPENTRY, Skill.xpForLevel(6));
            p.getInventory().clearContent();
            p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.HAMMER));
            p.getInventory().add(new ItemStack(BuiltInRegistries.ITEM.getValue(com.zomboidcraft.ZomboidCraft.id("plank")), 16));
            p.getInventory().add(new ItemStack(ModItems.NAILS, 16));
        });
        String before = sp.getServer().computeOnServer(s -> String.valueOf(com.zomboidcraft.building.base.Claims.baseOf(s.overworld(), player(s).getUUID()).max()));
        sp.getServer().runCommand(String.format(java.util.Locale.ROOT, "tp @a %.2f %d %.2f %.1f %.1f",
                barracks.getX() + 17.5, barracks.getY() + 1, barracks.getZ() + 6.5, 180f, 40f));
        ctx.waitTicks(10);
        yard = barracks;
        String growCheck = sp.getServer().computeOnServer(s -> {
            ServerPlayer p = player(s);
            Build.Spot spot = Build.POST.spot(s.overworld(), barracks.offset(17, 0, 4), Direction.UP, Direction.NORTH);
            return "at " + p.blockPosition() + " missing " + Build.POST.missing(p) + " spot " + spot.ok() + " " + spot.problem() + " " + spot.pos()
                    + " claim " + com.zomboidcraft.building.base.Claims.refusal(s.overworld(), spot.pos() == null ? p.blockPosition() : spot.pos(), p);
        });
        LOG.info("ZCBASE grow check: {}", growCheck);
        build(ctx, sp, Build.POST, barracks.offset(17, 0, 4), Direction.UP, Direction.NORTH);
        String after = sp.getServer().computeOnServer(s -> String.valueOf(com.zomboidcraft.building.base.Claims.baseOf(s.overworld(), player(s).getUUID()).max()));
        LOG.info("ZCBASE grow: max corner {} -> {}", before, after);

        // the base page
        standIn(ctx, sp, 13, 3, 180f, 20f);
        ctx.getInput().pressKey(InputConstants.KEY_E);
        ctx.waitTicks(10);
        ctx.runOnClient(mc -> {
            if (mc.gui.screen() instanceof KitScreen<?> k) k.open(KitScreen.Page.BASE);
        });
        frame(ctx, "base-page");
        String report = ctx.computeOnClient(mc -> String.valueOf(com.zomboidcraft.client.building.BaseClient.report));
        LOG.info("ZCBASE base page: {}", report);
        ctx.runOnClient(mc -> mc.gui.setScreen(null));
        ctx.waitTicks(5);
    }

    /** What the room does for the player where they stand, for the log. */
    private static String effectsHere(TestSingleplayerContext sp) {
        return sp.getServer().computeOnServer(s -> {
            ServerPlayer p = player(s);
            ServerLevel level = s.overworld();
            return "bunk=" + com.zomboidcraft.building.base.RoomEffects.bunk(p)
                    + " rest x" + com.zomboidcraft.building.base.RoomEffects.restScale(p)
                    + " wake stress -" + com.zomboidcraft.building.base.RoomEffects.wakeStressRelief(p)
                    + " sound sleep " + com.zomboidcraft.building.base.RoomEffects.soundSleep(p)
                    + " | treat x" + com.zomboidcraft.building.base.RoomEffects.treatScale(p)
                    + " hospital rest " + com.zomboidcraft.building.base.RoomEffects.hospitalRest(p)
                    + " | cook time x" + com.zomboidcraft.building.base.RoomEffects.cookScale(p)
                    + " cook xp x" + com.zomboidcraft.building.base.RoomEffects.cookXp(p)
                    + " counter keeps cold " + com.zomboidcraft.building.base.RoomEffects.keepsCold(p, level, barracks.offset(13, 1, 7))
                    + " far crate keeps cold " + com.zomboidcraft.building.base.RoomEffects.keepsCold(p, level, barracks.offset(30, 1, 4));
        });
    }

    private static String sleeper(TestSingleplayerContext sp) {
        return sp.getServer().computeOnServer(s -> {
            ServerPlayer p = player(s);
            return "asleep " + com.zomboidcraft.survival.Sleep.asleep(p)
                    + " fatigue " + Math.round(com.zomboidcraft.survival.SurvivalStats.get(p).fatigue())
                    + " stress " + com.zomboidcraft.survival.Mood.get(p, com.zomboidcraft.survival.Mood.Stat.STRESS)
                    + " boredom " + com.zomboidcraft.survival.Mood.get(p, com.zomboidcraft.survival.Mood.Stat.BOREDOM)
                    + " unhappy " + com.zomboidcraft.survival.Mood.get(p, com.zomboidcraft.survival.Mood.Stat.UNHAPPINESS);
        });
    }

    /** Rooms step 2: what the bunk room, med bay and kitchen do. */
    private static void effects(ClientGameTestContext ctx, TestSingleplayerContext sp) {
        sp.getServer().runCommand("gamemode survival @a");
        // each room, as it stands, lights on
        standIn(ctx, sp, 2, 4, 180f, 20f);
        ctx.waitTicks(25);
        LOG.info("ZCBASE fx bunk, light on: {} | {}", reading(sp), effectsHere(sp));
        sp.getServer().runOnServer(s -> s.overworld().setBlockAndUpdate(barracks.offset(2, 5, 4), CityBlocks.resolve("ceiling_light_off")));
        ctx.waitTicks(25);
        LOG.info("ZCBASE fx bunk, light off: {} | {}", reading(sp), effectsHere(sp));
        standIn(ctx, sp, 8, 3, 180f, 20f);
        ctx.waitTicks(25);
        LOG.info("ZCBASE fx med bay: {} | {}", reading(sp), effectsHere(sp));
        standIn(ctx, sp, 13, 3, 180f, 20f);
        ctx.waitTicks(25);
        LOG.info("ZCBASE fx kitchen: {} | {}", reading(sp), effectsHere(sp));
        standIn(ctx, sp, 30, 4, 180f, 20f);
        ctx.waitTicks(25);
        LOG.info("ZCBASE fx outside: {} | {}", reading(sp), effectsHere(sp));

        // a night in the bunk room, lights off: tired, on edge and bored going in
        sp.getServer().runOnServer(s -> {
            ServerPlayer p = player(s);
            com.zomboidcraft.survival.SurvivalStats.set(p, com.zomboidcraft.survival.SurvivalStats.get(p).withFatigue(60f));
            com.zomboidcraft.survival.Mood.add(p, com.zomboidcraft.survival.Mood.Stat.STRESS, 40);
            com.zomboidcraft.survival.Mood.add(p, com.zomboidcraft.survival.Mood.Stat.BOREDOM, 50);
            com.zomboidcraft.survival.Mood.add(p, com.zomboidcraft.survival.Mood.Stat.UNHAPPINESS, 30);
        });
        standIn(ctx, sp, 1, 4, 180f, 30f);
        ctx.waitTicks(25);
        LOG.info("ZCBASE fx before sleep: {}", sleeper(sp));
        BlockPos bunk = barracks.offset(1, 1, 6);
        ctx.runOnClient(mc -> net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(new com.zomboidcraft.survival.Sleep.LieDown(bunk)));
        ctx.waitTicks(30);
        String wakes = sp.getServer().computeOnServer(s -> "inside base " + com.zomboidcraft.building.base.RoomEffects.canWake(player(s), barracks.offset(8, 1, 4))
                + ", outside " + com.zomboidcraft.building.base.RoomEffects.canWake(player(s), barracks.offset(40, 1, 4)));
        LOG.info("ZCBASE fx asleep: {} | {} | can wake: {}", sleeper(sp), effectsHere(sp), wakes);
        frame(ctx, "bunk-asleep");
        // the test server doesn't fast-forward the night, so: twenty seconds of rest, then near rested to see the waking
        ctx.waitTicks(400);
        LOG.info("ZCBASE fx after 20s asleep: {}", sleeper(sp));
        sp.getServer().runOnServer(s -> com.zomboidcraft.survival.SurvivalStats.set(player(s), com.zomboidcraft.survival.SurvivalStats.get(player(s)).withFatigue(2.5f)));
        for (int i = 0; i < 15; i++) {
            ctx.waitTicks(20);
            if (sp.getServer().computeOnServer(s -> !com.zomboidcraft.survival.Sleep.asleep(player(s)))) break;
        }
        LOG.info("ZCBASE fx woke: {}", sleeper(sp));
        frame(ctx, "bunk-woke");

        // the hospital bed in the med bay
        sp.getServer().runOnServer(s -> {
            com.zomboidcraft.survival.Sleep.wake(player(s), null);
            com.zomboidcraft.survival.SurvivalStats.set(player(s), com.zomboidcraft.survival.SurvivalStats.get(player(s)).withFatigue(60f));
        });
        standIn(ctx, sp, 9, 4, 180f, 30f);
        ctx.waitTicks(25);
        BlockPos hospital = barracks.offset(9, 1, 6);
        ctx.runOnClient(mc -> net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(new com.zomboidcraft.survival.Sleep.LieDown(hospital)));
        ctx.waitTicks(30);
        LOG.info("ZCBASE fx hospital bed: {} | {}", sleeper(sp), effectsHere(sp));
        frame(ctx, "medbay-hospital-bed");
        sp.getServer().runOnServer(s -> com.zomboidcraft.survival.Sleep.wake(player(s), "You get up."));
        ctx.waitTicks(10);
    }

    /** Wave 1 (lifting, the G menu, walls, soil, the Skills page) is merged; set true to film it again. */
    private static final boolean LIFTING = false;

    /** The wood building yard: a grass platform east of the room. */
    private static BlockPos yard;

    /**
     * Wave 2a through the real Build requests: a crate, a run of wall frames, two boarded, a window frame, a door frame
     * with a door hung in it, a frame left open; then two posts, floorboards out from them to the span limit (one past
     * it refused), and the post knocked out so the floor sags, creaks and falls in.
     */
    private static void wood(ClientGameTestContext ctx, TestSingleplayerContext sp) {
        sp.getServer().runOnServer(s -> {
            ServerPlayer p = player(s);
            ServerLevel level = s.overworld();
            yard = origin.offset(20, 1, 2);
            for (int x = -2; x <= 14; x++) {
                for (int z = -12; z <= 10; z++) level.setBlockAndUpdate(yard.offset(x, -1, z), Blocks.GRASS_BLOCK.defaultBlockState());
            }
            Skills.addXp(p, Skill.CARPENTRY, Skill.xpForLevel(6));
            p.getInventory().clearContent();
            p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.HAMMER));
            p.getInventory().add(new ItemStack(BuiltInRegistries.ITEM.getValue(com.zomboidcraft.ZomboidCraft.id("hinges")), 6));
            p.getInventory().add(new ItemStack(BuiltInRegistries.ITEM.getValue(com.zomboidcraft.ZomboidCraft.id("padlock")), 1));
            p.getInventory().add(new ItemStack(BuiltInRegistries.ITEM.getValue(com.zomboidcraft.ZomboidCraft.id("screws")), 4));
            Skills.addXp(p, Skill.CARPENTRY, Skill.xpForLevel(8));
            p.getInventory().add(new ItemStack(BuiltInRegistries.ITEM.getValue(com.zomboidcraft.ZomboidCraft.id("plank")), 64));
            p.getInventory().add(new ItemStack(BuiltInRegistries.ITEM.getValue(com.zomboidcraft.ZomboidCraft.id("plank")), 64));
            for (int i = 0; i < 3; i++) {
                p.getInventory().add(new ItemStack(ModItems.NAILS, 64));
                p.getInventory().add(new ItemStack(BuiltInRegistries.ITEM.getValue(com.zomboidcraft.ZomboidCraft.id("plank")), 64));
            }
            com.zomboidcraft.ZcConfig.get().building.floorSagSeconds = 9;
        });
        // the wall run along x at z = 4, built from z = 1 looking south
        standYard(ctx, sp, 3, 0.5, 0f, 25f);
        frame(ctx, "yard");
        build(ctx, sp, Build.CRATE, yard.offset(8, -1, 1), Direction.UP, Direction.SOUTH);
        for (int x = 0; x <= 5; x++) {
            Build b = x == 1 ? Build.DOOR_FRAME : x == 4 ? Build.WINDOW_FRAME : Build.WALL_FRAME;
            build(ctx, sp, b, yard.offset(x, -1, 4), Direction.UP, Direction.SOUTH);
        }
        frame(ctx, "frames-up");
        for (int x : new int[] {0, 2, 3}) build(ctx, sp, Build.WOOD_WALL, yard.offset(x, 0, 4), Direction.NORTH, Direction.SOUTH);
        frame(ctx, "boarded");
        // a second course on top: walls stand on walls and window frames
        for (int x : new int[] {0, 2, 3, 4, 5}) build(ctx, sp, Build.WALL_FRAME, yard.offset(x, 0, 4), Direction.UP, Direction.SOUTH);
        for (int x : new int[] {0, 2, 3, 4}) build(ctx, sp, Build.WOOD_WALL, yard.offset(x, 1, 4), Direction.NORTH, Direction.SOUTH);
        standYard(ctx, sp, 3, -2.5, 0f, 8f);
        frame(ctx, "two-high");
        standYard(ctx, sp, 3, 0.5, 0f, 25f);
        build(ctx, sp, Build.WOOD_DOOR, yard.offset(1, 0, 4), Direction.NORTH, Direction.SOUTH);
        standYard(ctx, sp, 2, -1.5, 0f, 8f);
        frame(ctx, "door-hung");
        sp.getServer().runOnServer(s -> {
            BlockPos d = yard.offset(1, 0, 4);
            BlockState door = s.overworld().getBlockState(d);
            if (door.hasProperty(net.minecraft.world.level.block.DoorBlock.OPEN)) {
                ((net.minecraft.world.level.block.DoorBlock) door.getBlock()).setOpen(null, s.overworld(), door, d, true);
            }
        });
        frame(ctx, "door-open");
        standYard(ctx, sp, 2.5, 8.5, 180f, 8f);
        frame(ctx, "wall-inside");
        // walls take a beating: the boards split, then come off
        sp.getServer().runOnServer(s -> {
            ServerLevel level = s.overworld();
            level.setBlockAndUpdate(yard.offset(2, 0, 4), level.getBlockState(yard.offset(2, 0, 4)).setValue(WoodBlocks.Wall.DAMAGE, 3));
            level.setBlockAndUpdate(yard.offset(3, 0, 4), level.getBlockState(yard.offset(3, 0, 4)).setValue(WoodBlocks.Wall.DAMAGE, 6));
        });
        standYard(ctx, sp, 2.5, -1.5, 0f, 8f);
        frame(ctx, "walls-damaged");
        // wave 2b: shutters on the window frame, a ladder up the wall, a shed roof over it, stairs up to a deck
        standYard(ctx, sp, 3, 0.5, 0f, 25f);
        build(ctx, sp, Build.SHUTTERS, yard.offset(4, 0, 4), Direction.NORTH, Direction.SOUTH);
        build(ctx, sp, Build.LADDER, yard.offset(2, 0, 4), Direction.NORTH, Direction.SOUTH);
        build(ctx, sp, Build.LADDER, yard.offset(2, 1, 4), Direction.NORTH, Direction.SOUTH);
        standYard(ctx, sp, 4, 8.5, 180f, 8f);
        frame(ctx, "shutters-shut");
        sp.getServer().runOnServer(s -> {
            BlockState sh = s.overworld().getBlockState(yard.offset(4, 0, 4));
            if (sh.is(WoodBlocks.SHUTTERS)) s.overworld().setBlockAndUpdate(yard.offset(4, 0, 4), sh.setValue(WoodBlocks.Shutters.OPEN, true));
        });
        frame(ctx, "shutters-open");
        standYard(ctx, sp, 2.5, 2.5, 0f, -15f);
        for (int x = 0; x <= 5; x++) build(ctx, sp, Build.ROOF, yard.offset(x, 1, 4), Direction.UP, Direction.SOUTH);
        for (int x = 0; x <= 5; x++) build(ctx, sp, Build.ROOF, yard.offset(x, 3, 5), Direction.SOUTH, Direction.SOUTH);
        standYard(ctx, sp, 2.5, -3.5, -20f, 0f);
        frame(ctx, "ladder-and-roof");
        standYard(ctx, sp, 9, 4.5, 0f, 25f);
        for (int z = 6; z <= 7; z++) {
            build(ctx, sp, Build.POST, yard.offset(9, -1, z), Direction.UP, Direction.SOUTH);
            build(ctx, sp, Build.FLOOR, yard.offset(9, 0, z), Direction.UP, Direction.SOUTH);
            build(ctx, sp, Build.POST, yard.offset(8, -1, z), Direction.UP, Direction.SOUTH);
            build(ctx, sp, Build.WOOD_STAIRS, yard.offset(8, 0, z), Direction.UP, Direction.EAST);
            build(ctx, sp, Build.WOOD_STAIRS, yard.offset(7, -1, z), Direction.UP, Direction.EAST);
        }
        standYard(ctx, sp, 3.5, 6.5, -90f, 10f);
        frame(ctx, "stairs");
        String b2 = sp.getServer().computeOnServer(s -> {
            StringBuilder b = new StringBuilder();
            b.append(" shutters=").append(s.overworld().getBlockState(yard.offset(4, 0, 4)));
            b.append(" ladder=").append(s.overworld().getBlockState(yard.offset(2, 1, 3)).getBlock());
            for (int x = 0; x <= 5; x++) b.append(" roof").append(x).append('=').append(s.overworld().getBlockState(yard.offset(x, 2, 4)).getBlock().getName().getString())
                    .append('/').append(s.overworld().getBlockState(yard.offset(x, 3, 5)).getBlock().getName().getString());
            b.append(" stairs=").append(s.overworld().getBlockState(yard.offset(7, 0, 6))).append(" / ").append(s.overworld().getBlockState(yard.offset(8, 1, 6)));
            return b.toString();
        });
        LOG.info("ZCBASE wave 2b:{}", b2);
        wave2c(ctx, sp);
        // posts and a floor out over open ground at z = -3
        standYard(ctx, sp, 11, -6.5, 15f, -5f);
        build(ctx, sp, Build.POST, yard.offset(8, -1, -3), Direction.UP, Direction.SOUTH);
        build(ctx, sp, Build.POST, yard.offset(8, 0, -3), Direction.UP, Direction.SOUTH);
        build(ctx, sp, Build.FLOOR, yard.offset(8, 1, -3), Direction.UP, Direction.SOUTH);
        for (int x = 9; x <= 12; x++) build(ctx, sp, Build.FLOOR, yard.offset(x - 1, 2, -3), Direction.EAST, Direction.SOUTH);
        standYard(ctx, sp, 10, -9.5, 0f, -6f);
        frame(ctx, "floor-out");
        String states = sp.getServer().computeOnServer(s -> {
            StringBuilder b = new StringBuilder();
            for (int x = 0; x <= 5; x++) b.append(" x").append(x).append('=').append(s.overworld().getBlockState(yard.offset(x, 0, 4)));
            b.append(" crate=").append(s.overworld().getBlockState(yard.offset(8, 0, 1)).getBlock());
            for (int x = 8; x <= 12; x++) b.append(" floor").append(x).append('=').append(s.overworld().getBlockState(yard.offset(x, 2, -3)).getBlock());
            b.append(" wall hits=").append(WoodBlocks.wallHits(s.overworld().getBlockState(yard.offset(0, 0, 4))));
            return b.toString();
        });
        LOG.info("ZCBASE wood built:{}", states);
        // the top post knocked out: the floor goes, a stage every three seconds, then the next board in with it
        sp.getServer().runOnServer(s -> s.overworld().destroyBlock(yard.offset(8, 1, -3), true));
        for (int k = 0; k < 6; k++) {
            ctx.waitTicks(40);
            int t = k;
            String sag = sp.getServer().computeOnServer(s -> {
                StringBuilder b = new StringBuilder();
                for (int x = 8; x <= 12; x++) {
                    BlockState f = s.overworld().getBlockState(yard.offset(x, 2, -3));
                    b.append(' ').append(f.is(WoodBlocks.FLOOR) ? String.valueOf(f.getValue(WoodBlocks.Floor.SAG)) : "-");
                }
                return b.toString();
            });
            LOG.info("ZCBASE floor sag {}s:{}", (t + 1) * 2 + 1, sag);
            frame(ctx, "floor-sag-" + k);
        }
    }

    /**
     * Wave 2c: two walls reinforced, the door padlocked (it won't open by hand; its key unlocks it), a fence run with a
     * gate, a hidden door at the end of the wall run, and a lookout tower.
     */
    private static void wave2c(ClientGameTestContext ctx, TestSingleplayerContext sp) {
        // a fresh load: the pack only carries so much, and the yard so far has used most of it
        sp.getServer().runOnServer(s -> {
            ServerPlayer p = player(s);
            p.getInventory().clearContent();
            p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.HAMMER));
            p.getInventory().add(new ItemStack(BuiltInRegistries.ITEM.getValue(com.zomboidcraft.ZomboidCraft.id("hinges")), 4));
            p.getInventory().add(new ItemStack(BuiltInRegistries.ITEM.getValue(com.zomboidcraft.ZomboidCraft.id("padlock")), 1));
            p.getInventory().add(new ItemStack(BuiltInRegistries.ITEM.getValue(com.zomboidcraft.ZomboidCraft.id("screws")), 4));
            p.getInventory().add(new ItemStack(ModItems.NAILS, 64));
            for (int i = 0; i < 2; i++) p.getInventory().add(new ItemStack(BuiltInRegistries.ITEM.getValue(com.zomboidcraft.ZomboidCraft.id("plank")), 64));
            LOG.info("ZCBASE 2c load: {} planks", com.zomboidcraft.building.Materials.count(p, st -> BuiltInRegistries.ITEM.getKey(st.getItem()).getPath().equals("plank")));
        });
        standYard(ctx, sp, 3, 0.5, 0f, 25f);
        build(ctx, sp, Build.REINFORCE, yard.offset(0, 0, 4), Direction.NORTH, Direction.SOUTH);
        build(ctx, sp, Build.REINFORCE, yard.offset(3, 0, 4), Direction.NORTH, Direction.SOUTH);
        sp.getServer().runOnServer(s -> {
            BlockState d = s.overworld().getBlockState(yard.offset(1, 0, 4));
            if (d.is(WoodBlocks.DOOR)) s.overworld().setBlockAndUpdate(yard.offset(1, 0, 4), d.setValue(net.minecraft.world.level.block.DoorBlock.OPEN, false));
        });
        build(ctx, sp, Build.PADLOCK, yard.offset(1, 0, 4), Direction.NORTH, Direction.SOUTH);
        build(ctx, sp, Build.HIDDEN_DOOR, yard.offset(6, -1, 4), Direction.UP, Direction.SOUTH);
        standYard(ctx, sp, 3, -2.5, 0f, 8f);
        frame(ctx, "reinforced-padlocked-hidden");
        String lock = sp.getServer().computeOnServer(s -> {
            ServerPlayer p = player(s);
            ServerLevel level = s.overworld();
            BlockPos door = yard.offset(1, 0, 4);
            var hit = new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(door), Direction.NORTH, door, false);
            StringBuilder b = new StringBuilder(" fitted " + level.getBlockState(door));
            level.getBlockState(door).useWithoutItem(level, p, hit);
            b.append(" | by hand: open=").append(level.getBlockState(door).getValue(net.minecraft.world.level.block.DoorBlock.OPEN));
            // a full pack sets the key down at your feet: pick it up
            for (var e : level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, p.getBoundingBox().inflate(4))) {
                if (BuiltInRegistries.ITEM.getKey(e.getItem().getItem()).getPath().equals("padlock_key")) {
                    p.setItemInHand(InteractionHand.OFF_HAND, e.getItem().copy());
                    e.discard();
                }
            }
            ItemStack key = p.getOffhandItem().isEmpty() ? ItemStack.EMPTY : p.getOffhandItem();
            for (ItemStack st : p.getInventory()) {
                if (key.isEmpty() && BuiltInRegistries.ITEM.getKey(st.getItem()).getPath().equals("padlock_key")) key = st;
            }
            b.append(" | key ").append(key.getHoverName().getString());
            level.getBlockState(door).useItemOn(key, level, p, InteractionHand.MAIN_HAND, hit);
            b.append(" | after key: locked=").append(level.getBlockState(door).getValue(WoodBlocks.LockDoor.LOCKED));
            level.getBlockState(door).useWithoutItem(level, p, hit);
            b.append(" | by hand: open=").append(level.getBlockState(door).getValue(net.minecraft.world.level.block.DoorBlock.OPEN));
            BlockPos hidden = yard.offset(6, 0, 4);
            level.getBlockState(hidden).useWithoutItem(level, p, new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(hidden), Direction.NORTH, hidden, false));
            b.append(" | hidden ").append(level.getBlockState(hidden)).append(" / ").append(level.getBlockState(hidden.above()));
            b.append(" | reinforced hits ").append(WoodBlocks.wallHits(level.getBlockState(yard.offset(0, 0, 4))))
                    .append(" vs plain ").append(WoodBlocks.wallHits(level.getBlockState(yard.offset(2, 0, 4))));
            return b.toString();
        });
        LOG.info("ZCBASE padlock:{}", lock);
        frame(ctx, "unlocked-open-hidden-open");
        // a fence run with a gate in it
        standYard(ctx, sp, 2, -2.5, 180f, 25f);
        for (int x = 0; x <= 4; x++) build(ctx, sp, x == 2 ? Build.WOOD_GATE : Build.WOOD_FENCE, yard.offset(x, -1, -5), Direction.UP, Direction.NORTH);
        standYard(ctx, sp, 2, -1.5, 180f, 15f);
        frame(ctx, "fence-and-gate");
        // the lookout tower, three by three, north of the fence
        standYard(ctx, sp, 0, -6.5, 180f, 25f);
        build(ctx, sp, Build.WATCHTOWER, yard.offset(-1, -1, -8), Direction.UP, Direction.NORTH);
        standYard(ctx, sp, 5, -5.5, 127f, -12f);
        frame(ctx, "lookout-tower");
        String tower = sp.getServer().computeOnServer(s -> {
            StringBuilder b = new StringBuilder();
            b.append(" leg=").append(s.overworld().getBlockState(yard.offset(-1, 2, -8)).getBlock());
            b.append(" deck=").append(s.overworld().getBlockState(yard.offset(0, 3, -9)).getBlock());
            b.append(" rail=").append(s.overworld().getBlockState(yard.offset(1, 4, -10)).getBlock());
            b.append(" ladder=").append(s.overworld().getBlockState(yard.offset(-1, 3, -7)).getBlock())
                    .append(" climbable=").append(s.overworld().getBlockState(yard.offset(-1, 3, -7)).is(net.minecraft.tags.BlockTags.CLIMBABLE));
            return b.toString();
        });
        LOG.info("ZCBASE tower:{}", tower);
    }

    private static void standYard(ClientGameTestContext ctx, TestSingleplayerContext sp, double x, double z, float yaw, float pitch) {
        sp.getServer().runCommand(String.format(java.util.Locale.ROOT, "tp @a %.2f %d %.2f %.1f %.1f",
                yard.getX() + x + 0.5, yard.getY(), yard.getZ() + z + 0.5, yaw, pitch));
        ctx.waitTicks(10);
    }

    /** Sends the Build request the Placer would on a left click, waits out the bar, and logs what's there after. */
    private static void build(ClientGameTestContext ctx, TestSingleplayerContext sp, Build b, BlockPos hit, Direction face, Direction looking) {
        int ticks = sp.getServer().computeOnServer(s -> b.ticksFor(player(s)));
        ctx.runOnClient(mc -> net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
                new com.zomboidcraft.building.Builder.Request(b.ordinal(), hit, face.get3DDataValue(), looking.get3DDataValue())));
        ctx.waitTicks(Math.min(20, ticks / 2));
        if (b == Build.CRATE || b == Build.WOOD_DOOR || b == Build.FLOOR && hit.getY() == yard.getY() + 1) frame(ctx, "building-" + b.name().toLowerCase());
        ctx.waitTicks(ticks + 10);
        String what = sp.getServer().computeOnServer(s -> {
            Build.Spot spot = b.spot(s.overworld(), hit, face, looking);
            return "now " + (spot.ok() ? "free" : spot.problem()) + ", at hit+face " + s.overworld().getBlockState(hit.relative(face)).getBlock()
                    + ", hit " + s.overworld().getBlockState(hit).getBlock();
        });
        LOG.info("ZCBASE build {} at {}: {}", b, hit, what);
    }

    /** The Build page scrolled to the wood builds. */
    private static void buildPage(ClientGameTestContext ctx) {
        ctx.getInput().pressKey(InputConstants.KEY_E);
        ctx.waitTicks(10);
        ctx.runOnClient(mc -> {
            if (mc.gui.screen() instanceof KitScreen<?> k) k.open(KitScreen.Page.BUILD);
        });
        frame(ctx, "build-page");
        ctx.runOnClient(mc -> {
            if (mc.gui.screen() instanceof KitScreen<?> k) {
                for (int i = 0; i < 8; i++) k.build.scrolled(0, 0, -1);
                k.build.selected = Build.SHUTTERS;
            }
        });
        frame(ctx, "build-page-scrolled");
        ctx.runOnClient(mc -> mc.gui.setScreen(null));
        ctx.waitTicks(5);
    }

    private static void hold(TestSingleplayerContext sp, ItemStack stack) {
        sp.getServer().runOnServer(s -> player(s).setItemInHand(InteractionHand.MAIN_HAND, stack.copy()));
    }

    private static String chances() {
        StringBuilder b = new StringBuilder();
        for (int l = 0; l <= 10; l++) {
            b.append(String.format(java.util.Locale.ROOT, " %d:%.1f/%.1f", l, 100 * Moving.chance(0.75, l), 100 * Moving.chance(0.25, l)));
        }
        return b.toString();
    }

    private static ServerPlayer player(MinecraftServer s) {
        return s.getPlayerList().getPlayers().get(0);
    }

    private static BlockPos at(int i) {
        return origin.offset(2 + i, 1, 3);
    }

    /** A plain room on a platform in the sky: oak floor, cream walls and a ceiling, outside brick on the south side, and the furniture row. */
    private static void room(MinecraftServer s) {
        ServerPlayer p = player(s);
        p.setGameMode(GameType.SURVIVAL);
        ServerLevel level = s.overworld();
        origin = p.blockPosition().offset(0, 20, 0);
        BlockState floor = CityBlocks.resolve("home_floor_oak"), wall = CityBlocks.resolve("home_wall_cream"),
                brick = CityBlocks.resolve("brick_red");
        for (int x = 0; x <= 14; x++) {
            for (int z = 0; z <= 8; z++) {
                level.setBlockAndUpdate(origin.offset(x, 0, z), floor);
                level.setBlockAndUpdate(origin.offset(x, 5, z), floor);
                for (int y = 1; y <= 4; y++) {
                    boolean edge = x == 0 || x == 14 || z == 0 || z == 8;
                    level.setBlockAndUpdate(origin.offset(x, y, z), edge ? (z == 8 ? brick : wall) : Blocks.AIR.defaultBlockState());
                }
            }
        }
        // a partition across the room's east end, for the wall step
        for (int z = 1; z <= 7; z++) {
            for (int y = 1; y <= 4; y++) level.setBlockAndUpdate(origin.offset(12, y, z), wall);
        }
        for (int i = 0; i < ROW.size(); i++) {
            BlockState piece = CityBlocks.resolve(ROW.get(i) + "[facing=north]");
            if (piece == null) piece = CityBlocks.resolve(ROW.get(i));
            level.setBlockAndUpdate(at(i), piece);
            Moving.Piece m = Moving.piece(piece, level, at(i));
            LOG.info("ZCBASE piece {}: {}", ROW.get(i), m);
        }
        level.setBlockAndUpdate(origin.offset(6, 2, 7), CityBlocks.resolve("lx_switch[facing=north]"));
        p.teleportTo(origin.getX() + 4.5, origin.getY() + 1, origin.getZ() + 1.5);
    }

    /** Stands in the room at (x, z), looking at yaw/pitch. */
    private static void stand(ClientGameTestContext ctx, TestSingleplayerContext sp, double x, double z, float yaw, float pitch) {
        sp.getServer().runCommand(String.format(java.util.Locale.ROOT, "tp @a %.2f %d %.2f %.1f %.1f",
                origin.getX() + x + 0.5, origin.getY() + 1, origin.getZ() + z + 0.5, yaw, pitch));
        ctx.waitTicks(10);
    }

    /** Holds left click on piece i of the row for a while, screenshotting the crack, and logs what came of it. */
    private static void lift(ClientGameTestContext ctx, TestSingleplayerContext sp, String name, int i) {
        BlockPos pos = at(i);
        // two blocks back from it, looking straight at its middle
        stand(ctx, sp, pos.getX() - origin.getX(), pos.getZ() - origin.getZ() - 1.7, 0f, 38f);
        String before = sp.getServer().computeOnServer(s -> {
            ServerPlayer p = player(s);
            BlockState st = s.overworld().getBlockState(pos);
            Moving.Piece m = Moving.piece(st, s.overworld(), pos);
            return st.getBlock() + " " + m + " refusal=" + (m == null ? "-" : Moving.refusal(m, p, s.overworld(), pos))
                    + " risk=" + (m == null ? "-" : Moving.risk(m, p))
                    + " progress=" + st.getDestroyProgress(p, s.overworld(), pos);
        });
        LOG.info("ZCBASE lift {} before: {}", name, before);
        ctx.runOnClient(mc -> {
            KeyMapping.click(mc.options.keyAttack.getDefaultKey());
            mc.options.keyAttack.setDown(true);
        });
        for (int k = 0; k < 4; k++) {
            ctx.waitTicks(12);
            frame(ctx, "lift-" + name + "-" + k);
        }
        ctx.waitTicks(70);
        ctx.runOnClient(mc -> mc.options.keyAttack.setDown(false));
        ctx.waitTicks(5);
        String after = sp.getServer().computeOnServer(s -> {
            ServerPlayer p = player(s);
            ItemStack got = ItemStack.EMPTY;
            for (int j = 0; j < p.getInventory().getContainerSize(); j++) {
                ItemStack st = p.getInventory().getItem(j);
                if (BuiltInRegistries.ITEM.getKey(st.getItem()).getPath().equals(name)) got = st;
            }
            boolean gone = s.overworld().getBlockState(pos).isAir();
            boolean dropped = !s.overworld().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                    p.getBoundingBox().inflate(4), e -> BuiltInRegistries.ITEM.getKey(e.getItem().getItem()).getPath().equals(name)).isEmpty();
            return "block now " + s.overworld().getBlockState(pos).getBlock() + ", in inventory " + got
                    + (got.isEmpty() ? (dropped ? " SET DOWN AT FEET" : gone ? " BROKE" : "") : " weighing " + Weights.format(Weights.of(got)));
        });
        LOG.info("ZCBASE lift {} after: {}", name, after);
        frame(ctx, "lift-" + name + "-done");
    }

    /** What each kind of wall wants: the cream partition, the outside brick, and the steps by level. */
    private static void walls(ClientGameTestContext ctx, TestSingleplayerContext sp) {
        String out = sp.getServer().computeOnServer(s -> {
            ServerPlayer p = player(s);
            ServerLevel level = s.overworld();
            BlockPos partition = origin.offset(12, 2, 4), outside = origin.offset(4, 2, 8);
            p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.SLEDGEHAMMER));
            StringBuilder b = new StringBuilder();
            for (int add : new int[] {150, 600, 1050}) {
                Skills.addXp(p, Skill.MASONRY, add);
                b.append(" masonry ").append(Skills.level(p, Skill.MASONRY)).append(": partition ")
                        .append(DigRules.wall(level.getBlockState(partition), level, partition)).append(' ')
                        .append(level.getBlockState(partition).getDestroyProgress(p, level, partition))
                        .append(", outside ").append(DigRules.wall(level.getBlockState(outside), level, outside)).append(' ')
                        .append(level.getBlockState(outside).getDestroyProgress(p, level, outside)).append(';');
            }
            p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            return b.toString();
        });
        LOG.info("ZCBASE walls:{}", out);
    }

    /** Dirt on the oak floor is refused; the same dirt into a hole in the ground outside goes in. */
    private static void soil(ClientGameTestContext ctx, TestSingleplayerContext sp) {
        sp.getServer().runOnServer(s -> player(s).setItemInHand(InteractionHand.MAIN_HAND,
                new ItemStack(net.minecraft.world.item.Items.DIRT, 4)));
        stand(ctx, sp, 7, 2, 0f, 70f);
        ctx.runOnClient(mc -> KeyMapping.click(mc.options.keyUse.getDefaultKey()));
        ctx.waitTicks(5);
        frame(ctx, "dirt-refused");
        String floor = sp.getServer().computeOnServer(s -> String.valueOf(s.overworld().getBlockState(origin.offset(7, 1, 2)).getBlock()));
        LOG.info("ZCBASE dirt on the floor: block above the floor is now {}", floor);
    }

    private static void pages(ClientGameTestContext ctx) {
        ctx.getInput().pressKey(InputConstants.KEY_E);
        ctx.waitTicks(10);
        for (Skill skill : new Skill[] {Skill.CARPENTRY, Skill.MASONRY, Skill.METALWORKING, Skill.ELECTRICAL, Skill.PLUMBING}) {
            ctx.runOnClient(mc -> {
                if (mc.gui.screen() instanceof KitScreen<?> k) {
                    k.open(KitScreen.Page.SKILLS);
                    k.skills.selected = skill;
                }
            });
            ctx.waitTicks(5);
            frame(ctx, "skills-" + skill.name().toLowerCase());
        }
        ctx.runOnClient(mc -> {
            if (mc.gui.screen() instanceof KitScreen<?> k) k.open(KitScreen.Page.PACK);
        });
        ctx.waitTicks(5);
        frame(ctx, "pack");
    }

    /**
     * The G menu on a fresh piece: Pick up and Scrap greyed with an empty hand, then live with a hammer (weight and
     * break chance on the line), then Pick up chosen: it joins the action bar and the piece comes up.
     */
    private static void gMenu(ClientGameTestContext ctx, TestSingleplayerContext sp) {
        sp.getServer().runOnServer(s -> {
            ServerPlayer p = player(s);
            p.getInventory().clearContent();
            p.setAttached(Skills.TYPE, java.util.Map.of());
            s.overworld().setBlockAndUpdate(at(2), CityBlocks.resolve("home_sofa_blue_m[facing=north]"));
        });
        BlockPos pos = at(2);
        stand(ctx, sp, pos.getX() - origin.getX(), pos.getZ() - origin.getZ() - 1.7, 0f, 38f);
        hold(sp, ItemStack.EMPTY);
        menu(ctx, "gmenu-empty-hand");
        hold(sp, new ItemStack(ModItems.HAMMER));
        menu(ctx, "gmenu-hammer");
        String lines = ctx.computeOnClient(mc -> String.valueOf(com.zomboidcraft.client.interact.InteractMenu.collect(mc, mc.player, mc.hitResult)
                .stream().map(o -> o.label() + (o.can() ? "" : " [" + o.why() + "]")).toList()));
        LOG.info("ZCBASE g menu: {}", lines);
        ctx.runOnClient(mc -> net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
                new com.zomboidcraft.building.MoveNet.Move(pos, false)));
        ctx.waitTicks(4);
        frame(ctx, "gmenu-picking-up");
        ctx.waitTicks(40);
        String after = sp.getServer().computeOnServer(s -> "block now " + s.overworld().getBlockState(pos).getBlock());
        LOG.info("ZCBASE g menu pick up: {}", after);
        frame(ctx, "gmenu-done");
    }

    private static void menu(ClientGameTestContext ctx, String name) {
        ctx.waitTicks(5);
        ctx.runOnClient(com.zomboidcraft.client.interact.InteractMenu::open);
        frame(ctx, name);
        ctx.runOnClient(mc -> mc.gui.setScreen(null));
        ctx.waitTicks(5);
    }

    private static void frame(ClientGameTestContext ctx, String name) {
        // the software renderer on CI draws a few frames a second: give it time to catch up with the game
        ctx.waitTicks(30);
        ctx.takeScreenshot(String.format("%03d-%s", shot++, name));
    }
}
