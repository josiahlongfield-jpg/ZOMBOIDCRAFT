package com.zomboidcraft.client.kit;

import com.mojang.blaze3d.platform.InputConstants;
import com.zomboidcraft.ModItems;
import com.zomboidcraft.building.DigRules;
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
 * left click), refused until the skill and tool are there, and what it then weighs on you; the steps of knocking
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
            for (String c : new String[] {"time set noon", "weather clear", "gamerule doDaylightCycle false", "gamerule doMobSpawning false"}) {
                sp.getServer().runCommand(c);
            }
            ctx.getInput().resizeWindow(1280, 720);
            sp.getServer().runOnServer(BaseMoveTest::room);
            ctx.waitTicks(40);
            sp.getConnection().waitForChunksRender();
            stand(ctx, sp, 4, 1, 60, 30);
            frame(ctx, "room");

            // a chair anyone can lift, by hand
            lift(ctx, sp, "home_chair", 0);
            // a sofa end: too heavy at Carpentry 0 ...
            lift(ctx, sp, "home_sofa_blue_l", 1);
            // ... fine at the medium level
            sp.getServer().runOnServer(s -> Skills.addXp(player(s), Skill.CARPENTRY, 150));
            lift(ctx, sp, "home_sofa_blue_l", 1);
            // a sink: plumbed in, first it wants a wrench and Plumbing, then emptying, then it comes
            lift(ctx, sp, "home_sink_white", 4);
            sp.getServer().runOnServer(s -> {
                ServerPlayer p = player(s);
                p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.PIPE_WRENCH));
                Skills.addXp(p, Skill.PLUMBING, 50);
            });
            lift(ctx, sp, "home_sink_white", 4);
            sp.getServer().runOnServer(s -> {
                if (s.overworld().getBlockEntity(at(4)) instanceof RandomizableContainer c) c.setLootTable(null);
            });
            lift(ctx, sp, "home_sink_white", 4);
            // the fridge: big metal, needs Metalworking 5
            sp.getServer().runOnServer(s -> {
                player(s).setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                if (s.overworld().getBlockEntity(at(5)) instanceof RandomizableContainer c) c.setLootTable(null);
            });
            lift(ctx, sp, "home_fridge", 5);
            sp.getServer().runOnServer(s -> Skills.addXp(player(s), Skill.METALWORKING, 750));
            lift(ctx, sp, "home_fridge", 5);
            String load = sp.getServer().computeOnServer(s -> {
                ServerPlayer p = player(s);
                return String.format("load %.1f of %.1f kg", Carry.load(p), Carry.capacity());
            });
            LOG.info("ZCBASE carrying: {}", load);
            frame(ctx, "carrying");

            walls(ctx, sp);
            soil(ctx, sp);
            pages(ctx);
        }
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
        stand(ctx, sp, pos.getX() - origin.getX(), pos.getZ() - origin.getZ() - 2.2, 0f, 28f);
        String before = sp.getServer().computeOnServer(s -> {
            ServerPlayer p = player(s);
            BlockState st = s.overworld().getBlockState(pos);
            Moving.Piece m = Moving.piece(st, s.overworld(), pos);
            return st.getBlock() + " " + m + " refusal=" + (m == null ? "-" : Moving.refusal(m, p, s.overworld(), pos))
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
        ctx.waitTicks(60);
        ctx.runOnClient(mc -> mc.options.keyAttack.setDown(false));
        ctx.waitTicks(5);
        String after = sp.getServer().computeOnServer(s -> {
            ServerPlayer p = player(s);
            ItemStack got = ItemStack.EMPTY;
            for (int j = 0; j < p.getInventory().getContainerSize(); j++) {
                ItemStack st = p.getInventory().getItem(j);
                if (BuiltInRegistries.ITEM.getKey(st.getItem()).getPath().equals(name)) got = st;
            }
            return "block now " + s.overworld().getBlockState(pos).getBlock() + ", in inventory " + got
                    + (got.isEmpty() ? "" : " weighing " + Weights.format(Weights.of(got)));
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
        String floor = sp.getServer().computeOnServer(s -> String.valueOf(s.overworld().getBlockState(origin.offset(7, 1, 3)).getBlock()));
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

    private static void frame(ClientGameTestContext ctx, String name) {
        ctx.takeScreenshot(String.format("%03d-%s", shot++, name));
    }
}
