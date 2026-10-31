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
            p.getInventory().add(new ItemStack(net.minecraft.world.item.Items.OAK_PLANKS, 64));
            p.getInventory().add(new ItemStack(net.minecraft.world.item.Items.OAK_PLANKS, 64));
            for (int i = 0; i < 3; i++) {
                p.getInventory().add(new ItemStack(ModItems.NAILS, 64));
                p.getInventory().add(new ItemStack(net.minecraft.world.item.Items.OAK_PLANKS, 64));
            }
            p.getInventory().add(new ItemStack(BuiltInRegistries.ITEM.getValue(com.zomboidcraft.ZomboidCraft.id("hinges")), 2));
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
        standYard(ctx, sp, 5.5, 6.5, -90f, 15f);
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
