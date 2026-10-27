package com.zomboidcraft.client.kit;

import com.mojang.blaze3d.platform.InputConstants;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import com.zomboidcraft.ModItems;
import com.zomboidcraft.building.BuildBlocks;
import com.zomboidcraft.building.DigRules;
import com.zomboidcraft.combat.Stamina;
import com.zomboidcraft.skills.Skill;
import com.zomboidcraft.skills.Skills;
import com.zomboidcraft.world.blocks.CityBlocks;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.client.gui.components.AbstractWidget;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Opens the kit screen in a real client and clicks it the way a player does (through the window's mouse), logging
 * what each click did and taking a screenshot of every page. Run on CI with xvfb: ./gradlew runClientGameTest -Puitest
 */
public class KitUiTest implements FabricClientGameTest {
    private static final Logger LOG = LoggerFactory.getLogger("ZCUI");

    @Override
    public void runTest(ClientGameTestContext ctx) {
        ctx.runOnClient(mc -> ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
            LOG.info("ZCUI   screen opened: {}", screen.getClass().getSimpleName());
            ScreenEvents.remove(screen).register(closed -> {
                StringBuilder why = new StringBuilder();
                for (StackTraceElement e : new Throwable().getStackTrace()) {
                    String c = e.getClassName();
                    if (c.startsWith("net.fabricmc.fabric.impl") || c.startsWith("java.")) continue;
                    why.append("\n      at ").append(c).append('.').append(e.getMethodName()).append(':').append(e.getLineNumber());
                    if (why.length() > 1500) break;
                }
                LOG.info("ZCUI   screen closed: {}{}", closed.getClass().getSimpleName(), why);
            });
        }));
        try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
            sp.getConnection().waitForChunksRender();
            LOG.info("ZCUI dig rules: {}", sp.getServer().computeOnServer(KitUiTest::digProbe));
            for (String c : List.of("gamemode survival @a", "give @a zomboidcraft:assault_rifle", "give @a zomboidcraft:red_dot_sight",
                    "give @a zomboidcraft:foregrip", "give @a zomboidcraft:pistol", "give @a zomboidcraft:bandage 3",
                    "give @a minecraft:bread 5", "zc injure left_arm cut",
                    "give @a zomboidcraft:propane_torch", "give @a zomboidcraft:rubble_chunks 6", "give @a zomboidcraft:scrap_metal 4",
                    "give @a zomboidcraft:shovel", "give @a zomboidcraft:empty_sandbag 8", "give @a minecraft:dirt 8",
                    "give @a zomboidcraft:hammer", "give @a minecraft:oak_planks 4", "give @a zomboidcraft:nails 8",
                    "give @a zomboidcraft:fire_axe", "give @a zomboidcraft:crossbow", "give @a zomboidcraft:revolver", "give @a zomboidcraft:smg")) {
                sp.getServer().runCommand(c);
            }
            ctx.waitTicks(20);
            ctx.getInput().resizeWindow(1999, 1089);
            ctx.runOnClient(mc -> {
                mc.options.guiScale().set(0);
                mc.resizeGui();
            });
            ctx.waitTicks(5);
            pauseOptions(ctx, "before the kit");
            log(ctx, "before E");
            ctx.getInput().pressKey(InputConstants.KEY_E);
            ctx.waitTicks(10);
            log(ctx, "after E");
            ctx.takeScreenshot("kit-open");

            // every page tab along the top, by left click
            List<int[]> tabs = ctx.computeOnClient(mc -> {
                List<int[]> out = new ArrayList<>();
                if (mc.gui.screen() instanceof KitScreen<?> k) {
                    for (KitScreen.Hot h : hots(k)) if (h.h() == 14 && h.w() > 12) out.add(new int[]{h.x() + h.w() / 2, h.y() + h.h() / 2});
                }
                return out;
            });
            LOG.info("ZCUI tabs found: {}", tabs.size());
            for (int i = 0; i < tabs.size(); i++) {
                int[] t = tabs.get(i);
                click(ctx, t[0], t[1], 0);
                String page = ctx.computeOnClient(mc -> mc.gui.screen() instanceof KitScreen<?> k ? k.page.name() : String.valueOf(mc.gui.screen()));
                LOG.info("ZCUI left click on tab {} -> page {}", i, page);
                ctx.takeScreenshot("page-" + page.toLowerCase());
            }

            // right click on the first tab should not switch
            if (!tabs.isEmpty()) {
                click(ctx, tabs.get(0)[0], tabs.get(0)[1], 1);
                String after = ctx.computeOnClient(mc -> mc.gui.screen() instanceof KitScreen<?> k ? k.page.name() : "?");
                LOG.info("ZCUI right click on tab 0 -> page {}", after);
            }

            // the pack page: left click a tile with something in it picks it up, again puts it down
            if (tabs.size() > 1) {
                click(ctx, tabs.get(1)[0], tabs.get(1)[1], 0);
                int[] tile = ctx.computeOnClient(mc -> {
                    if (mc.gui.screen() instanceof KitScreen<?> k) {
                        for (KitScreen.Hot h : hots(k)) {
                            if (h.slot() >= 0 && !h.card().isEmpty()) return new int[]{h.x() + h.w() / 2, h.y() + h.h() / 2, h.slot()};
                        }
                    }
                    return null;
                });
                if (tile != null) {
                    click(ctx, tile[0], tile[1], 0);
                    LOG.info("ZCUI left click on tile slot {} -> carried {}", tile[2], carried(ctx));
                    ctx.takeScreenshot("pack-carrying");
                    click(ctx, tile[0], tile[1], 0);
                    LOG.info("ZCUI left click again -> carried {}", carried(ctx));
                    click(ctx, tile[0], tile[1], 1);
                    LOG.info("ZCUI right click on tile -> carried {}", carried(ctx));
                    click(ctx, tile[0], tile[1], 0);
                } else {
                    LOG.info("ZCUI no filled tile on the pack page");
                }
                ctx.waitTicks(3);
                // drag and drop: a filled tile dragged onto an empty one lands there on release; dragged off into
                // nothing it goes back
                String drag = ctx.computeOnClient(mc -> {
                    if (!(mc.gui.screen() instanceof KitScreen<?> k)) return "no kit";
                    KitScreen.Hot full = null, empty = null;
                    for (KitScreen.Hot h : hots(k)) {
                        if (h.slot() < 0 || h.slot() >= k.menu().slots.size() || !k.carried().contains(h.slot())) continue;
                        if (full == null && !h.card().isEmpty() && !KitItems.weapon(h.card())) full = h;
                        if (empty == null && h.card().isEmpty() && k.menu().slots.get(h.slot()).getClass() == net.minecraft.world.inventory.Slot.class) empty = h;
                    }
                    if (full == null || empty == null) return "no tiles: full " + full + " empty " + empty;
                    String what = k.menu().slots.get(full.slot()).getItem().toString();
                    mouse(k, full, empty);
                    String first = "dragged " + what + " from " + full.slot() + " to " + empty.slot() + ": now there "
                            + k.menu().slots.get(empty.slot()).getItem() + ", left behind " + k.menu().slots.get(full.slot()).getItem()
                            + ", cursor " + k.menu().getCarried();
                    return first + "|" + full.slot() + "|" + empty.slot();
                });
                LOG.info("ZCUI drag onto a slot: {}", drag);
                ctx.waitTicks(3);
                String back = ctx.computeOnClient(mc -> {
                    if (!(mc.gui.screen() instanceof KitScreen<?> k)) return "no kit";
                    for (KitScreen.Hot h : hots(k)) {
                        if (h.slot() >= 0 && !h.card().isEmpty() && k.carried().contains(h.slot())) {
                            String what = h.card().toString();
                            k.mouseClicked(new MouseButtonEvent(h.x() + 2, h.y() + 2, new MouseButtonInfo(0, 0)), false);
                            k.mouseDragged(new MouseButtonEvent(3, 3, new MouseButtonInfo(0, 0)), -h.x(), -h.y());
                            String mid = k.menu().getCarried().toString();
                            k.mouseReleased(new MouseButtonEvent(3, 3, new MouseButtonInfo(0, 0)));
                            return "dragged " + what + " from " + h.slot() + " into nothing: cursor mid-drag " + mid + ", after "
                                    + k.menu().getCarried() + ", slot now " + k.menu().slots.get(h.slot()).getItem();
                        }
                    }
                    return "nothing to drag";
                });
                LOG.info("ZCUI drag into nothing: {}", back);
                ctx.waitTicks(3);
            }

            // the bench, then a drag across the gun
            if (tabs.size() > 3) {
                click(ctx, tabs.get(3)[0], tabs.get(3)[1], 0);
                ctx.takeScreenshot("bench");
                log(ctx, "bench");
                // every weapon carried goes on the 3D stage, guns without sockets and melee too
                List<Integer> weapons = ctx.computeOnClient(mc -> {
                    List<Integer> out = new ArrayList<>();
                    if (mc.gui.screen() instanceof KitScreen<?> k) {
                        for (int i : k.carried()) if (KitItems.weapon(k.menu().slots.get(i).getItem())) out.add(i);
                    }
                    return out;
                });
                String noGhost = ctx.computeOnClient(mc -> {
                    List<String> out = new ArrayList<>();
                    for (String gun : com.zomboidcraft.combat.GunSlotData.FITS.keySet()) {
                        for (GunStage.Mount m : GunStage.mounts(gun)) if (m.ghost() == null) out.add(gun + "/" + m.slot());
                    }
                    return out.toString();
                });
                LOG.info("ZCUI bench slots without a ghost model: {}", noGhost);
                for (int i : weapons) {
                    String name = ctx.computeOnClient(mc -> {
                        if (!(mc.gui.screen() instanceof KitScreen<?> k)) return "?";
                        k.bench.show(i);
                        return net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(k.menu().slots.get(i).getItem().getItem()).getPath();
                    });
                    ctx.waitTicks(2);
                    LOG.info("ZCUI bench stage: {}", name);
                    ctx.takeScreenshot("bench-" + name);
                }
            }

            // close with E, then the pause menu and its Options button
            ctx.getInput().pressKey(InputConstants.KEY_E);
            ctx.waitTicks(5);
            log(ctx, "after closing");
            // closed on the bench, it must open on Character again
            ctx.getInput().pressKey(InputConstants.KEY_E);
            ctx.waitTicks(10);
            String reopened = ctx.computeOnClient(mc -> mc.gui.screen() instanceof KitScreen<?> k ? k.page.name() : String.valueOf(mc.gui.screen()));
            LOG.info("ZCUI reopened after closing on the bench -> page {}", reopened);
            if (!"CHARACTER".equals(reopened)) throw new AssertionError("kit reopened on " + reopened + ", not CHARACTER");
            ctx.getInput().pressKey(InputConstants.KEY_E);
            ctx.waitTicks(5);
            buildTest(ctx, sp);
            wave2Test(ctx, sp);
            safe(sp);
            dragTest(ctx, sp);
            swapTest(ctx, sp, false);
            swapTest(ctx, sp, true);
            swapSweep(ctx, sp);
            slingTest(ctx, sp);
            pauseOptions(ctx, "after the kit");
        }
    }

    /** Places a sandbag wall through the placer the way a player does: look down in front, left click, wait out the bar. */
    private static void buildTest(ClientGameTestContext ctx, TestSingleplayerContext sp) {
        ctx.runOnClient(mc -> {
            mc.player.setXRot(55f);
            mc.player.setYRot(0f);
            com.zomboidcraft.client.building.Placer.start(com.zomboidcraft.building.Build.SANDBAGS);
        });
        ctx.waitTicks(10);
        String aim = ctx.computeOnClient(mc -> String.valueOf(mc.hitResult instanceof net.minecraft.world.phys.BlockHitResult b ? b.getBlockPos() + " " + b.getDirection() : mc.hitResult));
        String building = ctx.computeOnClient(mc -> String.valueOf(com.zomboidcraft.client.building.Placer.building()));
        LOG.info("ZCUI placer: building {} looking at {}", building, aim);
        ctx.takeScreenshot("build-ghost");
        boolean grabbed = ctx.computeOnClient(mc -> mc.mouseHandler.isMouseGrabbed());
        ctx.getInput().pressMouse(0);
        ctx.waitTicks(3);
        int asked = ctx.computeOnClient(mc -> com.zomboidcraft.client.building.Placer.asked);
        if (asked == 0) {
            // the test's mouse doesn't reach key bindings in game: click the attack binding the way the mouse handler does
            ctx.runOnClient(mc -> net.minecraft.client.KeyMapping.click(mc.options.keyAttack.getDefaultKey()));
            ctx.waitTicks(3);
        }
        String after = ctx.computeOnClient(mc -> com.zomboidcraft.client.building.Placer.asked + " " + mc.mouseHandler.isMouseGrabbed());
        LOG.info("ZCUI placer: grabbed before {}, asked after first press {}, now {}", grabbed, asked, after);
        ctx.waitTicks(7);
        String action = ctx.computeOnClient(mc -> {
            Object v = mc.player.getAttached(com.zomboidcraft.survival.TimedActions.TYPE);
            return String.valueOf(v);
        });
        LOG.info("ZCUI placer: timed action {}", action);
        ctx.waitTicks(140);
        String placed = sp.getServer().computeOnServer(server -> {
            ServerPlayer p = server.getPlayerList().getPlayers().get(0);
            BlockPos feet = p.blockPosition();
            StringBuilder out = new StringBuilder();
            for (BlockPos pos : BlockPos.betweenClosed(feet.offset(-3, -1, -3), feet.offset(3, 1, 3))) {
                if (p.level().getBlockState(pos).is(com.zomboidcraft.building.BuildBlocks.SANDBAGS)) out.append(pos.immutable()).append(' ');
            }
            return "sandbags at [" + out + "] masonry xp=" + Skills.xp(p, Skill.MASONRY) + " sandbags left="
                    + com.zomboidcraft.building.Materials.count(p, s -> s.is(ModItems.EMPTY_SANDBAG));
        });
        LOG.info("ZCUI placer: {}", placed);
        ctx.takeScreenshot("build-placed");
        ctx.runOnClient(mc -> com.zomboidcraft.client.building.Placer.stop());
    }

    /** Tries the digging rules on the server: a brick wall by hand, with a sledgehammer before and after Masonry 2, and stone under the floor. */
    /**
     * Wave 2 on the server: an alarm clock wound for a second starts ringing, a zombie stuck against a spiked
     * wall gets hurt, and a planter takes seed, water, grows and gives up its crop. Then a screenshot of all three.
     */
    private static void wave2Test(ClientGameTestContext ctx, TestSingleplayerContext sp) {
        BlockPos[] at = sp.getServer().computeOnServer(s -> {
            ServerPlayer p = s.getPlayerList().getPlayers().get(0);
            ServerLevel level = (ServerLevel) p.level();
            BlockPos base = p.blockPosition().offset(3, 0, 0);
            BlockPos wall = base.offset(0, 0, -2), planter = base, clock = base.offset(0, 0, 2);
            level.setBlockAndUpdate(wall, BuildBlocks.SPIKED_WALL.defaultBlockState());
            level.setBlockAndUpdate(planter, BuildBlocks.PLANTER.defaultBlockState());
            level.setBlockAndUpdate(clock, BuildBlocks.ALARM_CLOCK.defaultBlockState().setValue(BuildBlocks.AlarmClock.FACING, net.minecraft.core.Direction.WEST));
            BuildBlocks.AlarmClock.wind(level, clock, 20);
            s.getCommands().performPrefixedCommand(s.createCommandSourceStack(), String.format(java.util.Locale.ROOT,
                    "summon minecraft:zombie %.2f %d %.2f {NoAI:1b,Tags:[\"spiketest\"]}", wall.getX() + 0.5, wall.getY(), wall.getZ() + 0.12));
            return new BlockPos[]{wall, planter, clock};
        });
        ctx.waitTicks(30);
        String early = sp.getServer().computeOnServer(s -> String.valueOf(s.overworld().getBlockState(at[2])));
        ctx.waitTicks(220);
        String report = sp.getServer().computeOnServer(s -> {
            StringBuilder out = new StringBuilder();
            try {
                ServerPlayer p = s.getPlayerList().getPlayers().get(0);
                ServerLevel level = (ServerLevel) p.level();
                out.append("clock after 1.5 s=").append(early).append(" after 12.5 s=").append(level.getBlockState(at[2]));
                var zombies = level.getEntitiesOfClass(net.minecraft.world.entity.monster.zombie.Zombie.class, new AABB(at[0]).inflate(2));
                out.append(" | zombies by the wall=").append(zombies.stream().map(z -> z.getHealth() + "/" + z.getMaxHealth()).toList());
                zombies.forEach(z -> z.discard());
                // the planter: seed, water, grow, pick
                net.minecraft.world.phys.BlockHitResult hit = new net.minecraft.world.phys.BlockHitResult(
                        net.minecraft.world.phys.Vec3.atCenterOf(at[1]).add(0, 0.5, 0), net.minecraft.core.Direction.UP, at[1], false);
                p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.CARROT_SEEDS, 2));
                out.append(" | plant=").append(level.getBlockState(at[1]).useItemOn(p.getMainHandItem(), level, p, InteractionHand.MAIN_HAND, hit));
                p.setItemInHand(InteractionHand.MAIN_HAND, net.minecraft.world.item.alchemy.PotionContents.createItemStack(Items.POTION,
                        net.minecraft.world.item.alchemy.Potions.WATER));
                out.append(" water=").append(level.getBlockState(at[1]).useItemOn(p.getMainHandItem(), level, p, InteractionHand.MAIN_HAND, hit))
                        .append(" hand=").append(p.getMainHandItem().getItem());
                out.append(" state=").append(level.getBlockState(at[1]));
                int ticks = 0;
                while (level.getBlockState(at[1]).getValue(BuildBlocks.Planter.AGE) < 7 && ticks < 2000) {
                    BlockState st = level.getBlockState(at[1]);
                    if (!st.getValue(BuildBlocks.Planter.WET)) level.setBlockAndUpdate(at[1], st.setValue(BuildBlocks.Planter.WET, true));
                    level.getBlockState(at[1]).randomTick(level, at[1], level.getRandom());
                    ticks++;
                }
                out.append(" ripe after ").append(ticks).append(" random ticks (").append(String.format(java.util.Locale.ROOT, "%.1f", ticks / (24000 * 3 / 4096.0)))
                        .append(" days)");
                p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                out.append(" pick=").append(level.getBlockState(at[1]).useWithoutItem(level, p, hit));
                out.append(" dropped=").append(level.getEntitiesOfClass(ItemEntity.class, new AABB(at[1]).inflate(2)).stream()
                        .map(e -> e.getItem().toString()).toList());
                level.getEntitiesOfClass(ItemEntity.class, new AABB(at[1]).inflate(2)).forEach(e -> e.discard());
                // grown again for the picture
                level.setBlockAndUpdate(at[1], level.getBlockState(at[1]).setValue(BuildBlocks.Planter.CROP, BuildBlocks.Crop.CARROT)
                        .setValue(BuildBlocks.Planter.AGE, 7).setValue(BuildBlocks.Planter.WET, true));
            } catch (Throwable t) {
                out.append(" FAILED ").append(t);
                LOG.error("ZCUI wave 2 failed", t);
            }
            return out.toString();
        });
        LOG.info("ZCUI wave2: {}", report);
        ctx.runOnClient(mc -> {
            mc.player.setYRot(-90f);
            mc.player.setXRot(25f);
        });
        ctx.waitTicks(10);
        ctx.takeScreenshot("wave2-builds");
    }

    /**
     * Fits a red dot from the kit's bench and takes it off again, then says where it went on both sides: it should
     * land in a carried slot and show in the spares list. With {@code full}, the pockets and belt are full first.
     */
    private static void swapTest(ClientGameTestContext ctx, TestSingleplayerContext sp, boolean full) {
        String tag = full ? "full" : "room";
        sp.getServer().runCommand("clear @a");
        for (String c : List.of("give @a zomboidcraft:assault_rifle", "give @a zomboidcraft:red_dot_sight")) sp.getServer().runCommand(c);
        if (full) sp.getServer().runCommand("give @a minecraft:dirt 2000");
        ctx.waitTicks(10);
        sp.getServer().runOnServer(s -> s.overworld().getEntitiesOfClass(ItemEntity.class, s.getPlayerList().getPlayers().get(0).getBoundingBox().inflate(6))
                .forEach(e -> e.discard()));
        ctx.getInput().pressKey(InputConstants.KEY_E);
        ctx.waitTicks(10);
        String fit = ctx.computeOnClient(mc -> {
            if (!(mc.gui.screen() instanceof KitScreen<?> k)) return "no kit: " + mc.gui.screen();
            int gun = -1, dot = -1;
            for (int i : k.carried()) {
                ItemStack s = k.menu().slots.get(i).getItem();
                if (s.is(ModItems.ASSAULT_RIFLE)) gun = i;
                if (s.is(ModItems.RED_DOT_SIGHT)) dot = i;
            }
            if (gun < 0 || dot < 0) return "missing gun " + gun + " dot " + dot;
            k.button(com.zomboidcraft.combat.Bench.fitButton(dot, gun, com.zomboidcraft.combat.Attachments.Slot.OPTIC));
            return "gun " + gun + " dot " + dot;
        });
        ctx.waitTicks(10);
        String fitted = sp.getServer().computeOnServer(s -> where(s.getPlayerList().getPlayers().get(0).inventoryMenu));
        String off = ctx.computeOnClient(mc -> {
            if (!(mc.gui.screen() instanceof KitScreen<?> k)) return "no kit";
            for (int i : k.carried()) {
                if (k.menu().slots.get(i).getItem().is(ModItems.ASSAULT_RIFLE)) {
                    k.button(com.zomboidcraft.combat.Bench.takeOffButton(i, com.zomboidcraft.combat.Attachments.Slot.OPTIC));
                    return "took off from " + i + "; client now " + where(k.menu());
                }
            }
            return "no gun";
        });
        ctx.waitTicks(10);
        String server = sp.getServer().computeOnServer(s -> {
            ServerPlayer p = s.getPlayerList().getPlayers().get(0);
            return where(p.inventoryMenu) + " dropped=" + p.level().getEntitiesOfClass(ItemEntity.class, p.getBoundingBox().inflate(6)).stream()
                    .filter(e -> !e.getItem().is(Items.DIRT)).map(e -> e.getItem().toString()).toList();
        });
        String client = ctx.computeOnClient(mc -> {
            if (!(mc.gui.screen() instanceof KitScreen<?> k)) return "no kit";
            List<Integer> spares = new ArrayList<>();
            for (int i : k.carried()) if (k.menu().slots.get(i).getItem().is(ModItems.RED_DOT_SIGHT)) spares.add(i);
            return where(k.menu()) + " spares listed from " + spares;
        });
        LOG.info("ZCUI swap {}: fit {} -> server {} | take off {} | server {} | client {}", tag, fit, fitted, off, server, client);
        ctx.getInput().pressKey(InputConstants.KEY_E);
        ctx.waitTicks(5);
    }

    /**
     * Every gun, wearing a backpack: fit one of each part it takes from the bench, take it off again, and check the
     * part is back among the carried slots (so the spares list shows it) on both sides.
     */
    private static void swapSweep(ClientGameTestContext ctx, TestSingleplayerContext sp) {
        List<String> bad = new ArrayList<>();
        int checked = 0;
        for (var gunEntry : com.zomboidcraft.combat.GunSlotData.FITS.entrySet()) {
            String gun = gunEntry.getKey();
            sp.getServer().runCommand("clear @a");
            sp.getServer().runCommand("give @a zomboidcraft:hiking_backpack");
            sp.getServer().runCommand("give @a zomboidcraft:" + gun);
            for (var parts : gunEntry.getValue().values()) if (!parts.isEmpty()) sp.getServer().runCommand("give @a zomboidcraft:" + parts.get(0));
            ctx.waitTicks(5);
            ctx.getInput().pressKey(InputConstants.KEY_E);
            ctx.waitTicks(5);
            for (var socketEntry : gunEntry.getValue().entrySet()) {
                if (socketEntry.getValue().isEmpty()) continue;
                com.zomboidcraft.combat.Attachments.Slot socket = socketEntry.getKey();
                String part = socketEntry.getValue().get(0);
                String fit = ctx.computeOnClient(mc -> {
                    if (!(mc.gui.screen() instanceof KitScreen<?> k)) return "no kit";
                    int g = find(k, gun), dot = find(k, part);
                    if (g < 0) return "no gun";
                    if (!com.zomboidcraft.combat.Attachments.get(k.menu().slots.get(g).getItem(), socket).isEmpty()) return "";
                    if (dot < 0) return "part not carried before fitting";
                    k.button(com.zomboidcraft.combat.Bench.fitButton(dot, g, socket));
                    return "";
                });
                ctx.waitTicks(4);
                String off = ctx.computeOnClient(mc -> {
                    if (!(mc.gui.screen() instanceof KitScreen<?> k)) return "no kit";
                    int g = find(k, gun);
                    if (g < 0) return "no gun";
                    if (com.zomboidcraft.combat.Attachments.get(k.menu().slots.get(g).getItem(), socket).isEmpty()) return "nothing fitted";
                    k.button(com.zomboidcraft.combat.Bench.takeOffButton(g, socket));
                    return "";
                });
                ctx.waitTicks(6);
                String client = ctx.computeOnClient(mc -> mc.gui.screen() instanceof KitScreen<?> k
                        ? (find(k, part) >= 0 ? "" : "client lost it " + where(k.menu())) : "no kit");
                String server = sp.getServer().computeOnServer(sv -> {
                    var menu = sv.getPlayerList().getPlayers().get(0).inventoryMenu;
                    for (int i = 0; i < menu.slots.size(); i++) {
                        if (net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(menu.slots.get(i).getItem().getItem()).getPath().equals(part)) {
                            return "";
                        }
                    }
                    return "server lost it";
                });
                checked++;
                String all = (fit + " " + off + " " + client + " " + server).trim();
                if (!all.isEmpty()) bad.add(gun + "/" + socket + "/" + part + ": " + all);
            }
            ctx.getInput().pressKey(InputConstants.KEY_E);
            ctx.waitTicks(3);
        }
        LOG.info("ZCUI swap sweep: {} parts checked, problems {}", checked, bad);
    }

    private static int find(KitScreen<?> k, String id) {
        for (int i : k.carried()) {
            if (net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(k.menu().slots.get(i).getItem().getItem()).getPath().equals(id)) return i;
        }
        return -1;
    }

    /**
     * Weapons and pockets: with the belt full, a picked-up rifle goes on the sling and a pistol into the pack, never a
     * pocket; then the slung rifle seen from behind, over the pack.
     */
    private static void slingTest(ClientGameTestContext ctx, TestSingleplayerContext sp) {
        sp.getServer().runCommand("clear @a");
        sp.getServer().runOnServer(s -> com.zomboidcraft.carry.Carry.gear(s.getPlayerList().getPlayers().get(0)).takeAll(x -> { }));
        for (String c : List.of("time set noon", "weather clear", "effect clear @a", "kill @e[type=!player]")) sp.getServer().runCommand(c);
        sp.getServer().runCommand("give @a zomboidcraft:hiking_backpack");
        ctx.waitTicks(5);
        String report = sp.getServer().computeOnServer(s -> {
            ServerPlayer p = s.getPlayerList().getPlayers().get(0);
            for (int i = 0; i < 9; i++) p.getInventory().setItem(i, new ItemStack(Items.DIRT));
            ItemStack rifle = new ItemStack(ModItems.HUNTING_RIFLE), pistol = new ItemStack(ModItems.PISTOL);
            boolean r = p.getInventory().add(rifle), q = p.getInventory().add(pistol);
            StringBuilder out = new StringBuilder("rifle taken=" + r + " pistol taken=" + q + " sling="
                    + com.zomboidcraft.carry.Carry.gear(p).get(com.zomboidcraft.carry.GearStore.SLING));
            for (int i = 9; i < 36; i++) if (!p.getInventory().getItem(i).isEmpty()) out.append(" POCKET ").append(i).append('=').append(p.getInventory().getItem(i));
            for (int i = 0; i < p.inventoryMenu.slots.size(); i++) {
                if (p.inventoryMenu.slots.get(i).getItem().is(ModItems.PISTOL)) out.append(" pistol in slot ").append(i).append(' ')
                        .append(p.inventoryMenu.slots.get(i).getClass().getSimpleName());
            }
            out.append(" pocket takes a rifle=").append(p.inventoryMenu.slots.get(9).mayPlace(new ItemStack(ModItems.ASSAULT_RIFLE)))
                    .append(" pocket takes bread=").append(p.inventoryMenu.slots.get(9).mayPlace(new ItemStack(Items.BREAD)));
            for (int i = 0; i < 9; i++) p.getInventory().setItem(i, ItemStack.EMPTY);
            return out.toString();
        });
        LOG.info("ZCUI sling: {}", report);
        ctx.waitTicks(10);
        // a camera on a stand behind you, looking at your back (your own third-person views sit too close)
        sp.getServer().runCommand("effect give @a minecraft:night_vision 60 0 true");
        for (int[] view : new int[][]{{0, 26}, {40, 22}, {-150, 10}}) {
            sp.getServer().runOnServer(s -> {
                ServerPlayer p = s.getPlayerList().getPlayers().get(0);
                p.setYRot(0f);
                p.setYHeadRot(0f);
                p.setYBodyRot(0f);
                s.getCommands().performPrefixedCommand(s.createCommandSourceStack(), "kill @e[type=!player]");
                float yaw = view[0];
                double r = 0.8;
                double cx = p.getX() + Math.sin(Math.toRadians(yaw)) * r, cz = p.getZ() - Math.cos(Math.toRadians(yaw)) * r;
                var stand = new net.minecraft.world.entity.decoration.ArmorStand(p.level(), cx, p.getY() - 0.3, cz);
                stand.setYRot(yaw);
                stand.setYHeadRot(yaw);
                stand.setXRot(view[1]);
                stand.setInvisible(true);
                stand.setNoGravity(true);
                p.level().addFreshEntity(stand);
            });
            ctx.waitTicks(5);
            ctx.runOnClient(mc -> {
                mc.player.setYRot(0f);
                mc.player.yBodyRot = 0f;
                mc.player.yHeadRot = 0f;
                int found = 0;
                for (var e : mc.level.entitiesForRendering()) {
                    if (e instanceof net.minecraft.world.entity.decoration.ArmorStand) {
                        mc.setCameraEntity(e);
                        found++;
                    }
                }
                // your own model is hidden in first person, so the view sits back from the stand
                mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK);
                LOG.info("ZCUI sling camera stands found: {}", found);
            });
            ctx.waitTicks(3);
            ctx.takeScreenshot("sling-view-" + (view[0] < 0 ? "front" : view[0]));
        }
        ctx.runOnClient(mc -> {
            mc.setCameraEntity(mc.player);
            mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
        });
        sp.getServer().runCommand("kill @e[type=minecraft:armor_stand]");
        // and drawn flat on a plain screen, turned to show the back
        for (float turn : new float[]{0f, 35f, 180f}) {
            ctx.runOnClient(mc -> mc.gui.setScreen(new BackView(turn)));
            ctx.waitTicks(3);
            ctx.takeScreenshot("sling-figure-" + Math.round(turn));
        }
        ctx.runOnClient(mc -> mc.gui.setScreen(null));
    }

    /**
     * Drag and drop on the pack page with room to spare: bread dragged onto an empty pocket lands there on release;
     * a rifle dragged onto an empty pocket goes back to the belt (no guns in pockets).
     */
    private static void dragTest(ClientGameTestContext ctx, TestSingleplayerContext sp) {
        sp.getServer().runCommand("clear @a");
        sp.getServer().runOnServer(s -> {
            ServerPlayer p = s.getPlayerList().getPlayers().get(0);
            p.getInventory().setItem(0, new ItemStack(Items.BREAD, 3));
            p.getInventory().setItem(1, new ItemStack(ModItems.HUNTING_RIFLE));
        });
        ctx.waitTicks(5);
        ctx.getInput().pressKey(InputConstants.KEY_E);
        ctx.waitTicks(5);
        ctx.runOnClient(mc -> {
            if (mc.gui.screen() instanceof KitScreen<?> k) k.open(KitScreen.Page.PACK);
        });
        ctx.waitTicks(3);
        for (int belt : new int[]{36, 37}) {
            String out = ctx.computeOnClient(mc -> {
                if (!(mc.gui.screen() instanceof KitScreen<?> k)) return "no kit";
                KitScreen.Hot from = null, pocket = null;
                for (KitScreen.Hot h : hots(k)) {
                    if (h.slot() == belt) from = h;
                    if (pocket == null && h.slot() >= 9 && h.slot() < 36 && h.card().isEmpty()) pocket = h;
                }
                if (from == null || pocket == null) return "no tiles: from " + from + " pocket " + pocket;
                String what = from.card().toString();
                mouse(k, from, pocket);
                return what + " from " + belt + " onto pocket " + pocket.slot() + ": pocket now " + k.menu().slots.get(pocket.slot()).getItem()
                        + ", belt now " + k.menu().slots.get(belt).getItem() + ", cursor " + k.menu().getCarried();
            });
            ctx.waitTicks(5);
            LOG.info("ZCUI drag test: {}", out);
        }
        ctx.takeScreenshot("pack-after-drag");
        // shift + right click on the bread drops it on the floor
        ctx.getInput().holdKey(InputConstants.KEY_LSHIFT);
        ctx.waitTicks(1);
        String dropped = ctx.computeOnClient(mc -> {
            if (!(mc.gui.screen() instanceof KitScreen<?> k)) return "no kit";
            for (KitScreen.Hot h : hots(k)) {
                if (h.slot() >= 0 && h.card().is(Items.BREAD)) {
                    k.mouseClicked(new MouseButtonEvent(h.x() + 2, h.y() + 2, new MouseButtonInfo(1, 1)), false);
                    k.mouseReleased(new MouseButtonEvent(h.x() + 2, h.y() + 2, new MouseButtonInfo(1, 1)));
                    return "slot " + h.slot() + " now " + k.menu().slots.get(h.slot()).getItem();
                }
            }
            return "no bread";
        });
        ctx.getInput().releaseKey(InputConstants.KEY_LSHIFT);
        ctx.waitTicks(10);
        String floor = sp.getServer().computeOnServer(s -> {
            ServerPlayer p = s.getPlayerList().getPlayers().get(0);
            return p.level().getEntitiesOfClass(ItemEntity.class, p.getBoundingBox().inflate(4)).stream().map(e -> e.getItem().toString()).toList().toString();
        });
        LOG.info("ZCUI shift right click: {} floor {}", dropped, floor);
        ctx.getInput().pressKey(InputConstants.KEY_E);
        ctx.waitTicks(3);
    }

    /** A plain screen with the player drawn big, body turned {@code turn} degrees from facing away. */
    static final class BackView extends Screen {
        private final float turn;
        private boolean logged;

        BackView(float turn) {
            super(net.minecraft.network.chat.Component.literal("back"));
            this.turn = turn;
        }

        @Override
        public void extractBackground(net.minecraft.client.gui.GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
            g.fill(0, 0, width, height, 0xFF7A8490);
            var player = minecraft.player;
            float body = player.yBodyRot, bodyO = player.yBodyRotO, head = player.yHeadRot, headO = player.yHeadRotO, yaw = player.getYRot(),
                    yawO = player.yRotO, pitch = player.getXRot(), pitchO = player.xRotO;
            player.yBodyRot = player.yBodyRotO = turn;
            player.yHeadRot = player.yHeadRotO = turn;
            player.setYRot(turn);
            player.yRotO = turn;
            player.setXRot(0);
            player.xRotO = 0;
            try {
                for (var m : net.minecraft.client.gui.screens.inventory.InventoryScreen.class.getDeclaredMethods()) {
                    if (!m.getName().startsWith("extractEntityInInventory") || m.getName().contains("Follows")) continue;
                    if (!logged) LOG.info("ZCUI back view method {}", m);
                    Object[] args = new Object[m.getParameterCount()];
                    int ints = 0;
                    int[] box = {width / 2 - 90, 4, width / 2 + 90, height - 4};
                    boolean quat = false, ok = true;
                    for (int i = 0; i < args.length; i++) {
                        Class<?> t = m.getParameterTypes()[i];
                        if (t == int.class) args[i] = box[Math.min(3, ints++)];
                        else if (t == float.class) args[i] = (height - 8) / player.getBbHeight() / 1.15f;
                        else if (t.isInstance(g)) args[i] = g;
                        else if (t == org.joml.Vector3f.class) args[i] = new org.joml.Vector3f(0, player.getBbHeight() / 2 + 0.0625f, 0);
                        else if (t == org.joml.Quaternionf.class) {
                            args[i] = quat ? null : new org.joml.Quaternionf().rotateZ((float) Math.PI);
                            quat = true;
                        } else if (t.isInstance(player)) args[i] = player;
                        else ok = false;
                    }
                    if (!ok) {
                        if (!logged) LOG.info("ZCUI back view: can't fill {}", m);
                        continue;
                    }
                    m.setAccessible(true);
                    m.invoke(null, args);
                    break;
                }
            } catch (Throwable t) {
                if (!logged) LOG.error("ZCUI back view failed", t);
            } finally {
                logged = true;
                player.yBodyRot = body;
                player.yBodyRotO = bodyO;
                player.yHeadRot = head;
                player.yHeadRotO = headO;
                player.setYRot(yaw);
                player.yRotO = yawO;
                player.setXRot(pitch);
                player.xRotO = pitchO;
            }
        }
    }

    /** Keeps the test survivor alive from here on: no zombie bites, no bleeding out, healed. */
    private static void safe(TestSingleplayerContext sp) {
        sp.getServer().runOnServer(s -> {
            ServerPlayer p = s.getPlayerList().getPlayers().get(0);
            p.getAbilities().invulnerable = true;
            p.onUpdateAbilities();
            p.setHealth(p.getMaxHealth());
            s.getCommands().performPrefixedCommand(s.createCommandSourceStack(), "kill @e[type=!player]");
        });
    }

    /** Where the rifle and red dots are in a menu, and whether the rifle has its optic. */
    private static String where(net.minecraft.world.inventory.AbstractContainerMenu menu) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < menu.slots.size(); i++) {
            ItemStack s = menu.slots.get(i).getItem();
            if (s.is(ModItems.RED_DOT_SIGHT)) out.append("dot@").append(i).append('(').append(menu.slots.get(i).getClass().getSimpleName()).append(") ");
            if (s.is(ModItems.ASSAULT_RIFLE)) out.append("rifle@").append(i).append(" optic=")
                    .append(com.zomboidcraft.combat.Attachments.get(s, com.zomboidcraft.combat.Attachments.Slot.OPTIC)).append(' ');
        }
        return out.toString();
    }

    private static String digProbe(MinecraftServer server) {
        StringBuilder out = new StringBuilder();
        try {
            ServerPlayer p = server.getPlayerList().getPlayers().get(0);
            p.setGameMode(GameType.SURVIVAL);
            ServerLevel level = (ServerLevel) p.level();
            BlockPos at = p.blockPosition().offset(3, 1, 0);
            BlockState brick = CityBlocks.resolve("brick_red_moss");
            level.setBlockAndUpdate(at, brick);
            out.append("brick=").append(brick).append(" tier=").append(DigRules.wallTier(brick, level, at));
            out.append(" hand=").append(level.getBlockState(at).getDestroyProgress(p, level, at));
            p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.SLEDGEHAMMER));
            out.append(" sledge@0=").append(level.getBlockState(at).getDestroyProgress(p, level, at));
            Skills.addXp(p, Skill.MASONRY, 150);
            out.append(" masonry=").append(Skills.level(p, Skill.MASONRY));
            out.append(" sledge@2=").append(level.getBlockState(at).getDestroyProgress(p, level, at));
            float stamina = Stamina.get(p);
            boolean broke = p.gameMode.destroyBlock(at);
            out.append(" knocked=").append(broke).append(" now=").append(level.getBlockState(at).getBlock())
                    .append(" endurance ").append(stamina).append("->").append(Stamina.get(p))
                    .append(" masonry xp=").append(Skills.xp(p, Skill.MASONRY))
                    .append(" drops=").append(level.getEntitiesOfClass(ItemEntity.class, new AABB(at).inflate(2)).stream()
                            .map(e -> e.getItem().toString()).toList());
            p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_PICKAXE));
            int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at.getX() + 2, at.getZ()) - 1;
            BlockPos shallow = new BlockPos(at.getX() + 2, top - 3, at.getZ()), deep = new BlockPos(at.getX() + 2, top - 12, at.getZ());
            level.setBlockAndUpdate(shallow, Blocks.STONE.defaultBlockState());
            level.setBlockAndUpdate(deep, Blocks.STONE.defaultBlockState());
            out.append(" | ground top=").append(top).append(" stone 3 down=").append(Blocks.STONE.defaultBlockState().getDestroyProgress(p, level, shallow))
                    .append(" stone 12 down=").append(Blocks.STONE.defaultBlockState().getDestroyProgress(p, level, deep));
            p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        } catch (Throwable t) {
            out.append(" FAILED ").append(t);
            LOG.error("ZCUI dig probe failed", t);
        }
        return out.toString();
    }

    /** Esc, then a real left click on the pause menu's Options button: does a vanilla button answer? */
    private static void pauseOptions(ClientGameTestContext ctx, String when) {
        ctx.getInput().pressKey(InputConstants.KEY_ESCAPE);
        ctx.waitTicks(10);
        log(ctx, "pause " + when);
        int[] options = ctx.computeOnClient(mc -> {
            Screen s = mc.gui.screen();
            if (s == null) return null;
            for (var child : s.children()) {
                if (child instanceof AbstractWidget w && w.getMessage().getString().startsWith("Options")) {
                    return new int[]{w.getX() + w.getWidth() / 2, w.getY() + w.getHeight() / 2};
                }
            }
            return null;
        });
        if (options == null) {
            LOG.info("ZCUI no Options button found");
            return;
        }
        ctx.runOnClient(mc -> {
            Screen s = mc.gui.screen();
            ScreenMouseEvents.allowMouseClick(s).register((screen, event) -> {
                LOG.info("ZCUI   pause screen got a click: button {} at {},{}", event.button(), event.x(), event.y());
                return true;
            });
            ScreenMouseEvents.allowMouseRelease(s).register((screen, event) -> {
                LOG.info("ZCUI   pause screen got a release: button {}", event.button());
                return true;
            });
        });
        click(ctx, options[0], options[1], 0);
        ctx.waitTicks(10);
        log(ctx, "after clicking Options " + when);
        if (ctx.computeOnClient(mc -> mc.gui.screen() instanceof PauseScreen)) {
            boolean took = ctx.computeOnClient(mc -> mc.gui.screen().mouseClicked(
                    new MouseButtonEvent(options[0] + 0.5, options[1] + 0.5, new MouseButtonInfo(0, 0)), false));
            ctx.waitTicks(5);
            log(ctx, "after calling mouseClicked directly (" + took + ") " + when);
            ctx.runOnClient(mc -> {
                Screen scr = mc.gui.screen();
                double x = options[0] + 0.5, y = options[1] + 0.5;
                LOG.info("ZCUI child at the click: {} focused={} dragging={}", scr.getChildAt(x, y).orElse(null), scr.getFocused(), scr.isDragging());
                for (var child : scr.children()) {
                    String what = child instanceof AbstractWidget w ? w.getClass().getName() + " '" + w.getMessage().getString() + "' active=" + w.active
                            + " visible=" + w.visible + " " + w.getX() + "," + w.getY() + " " + w.getWidth() + "x" + w.getHeight() : child.getClass().getName();
                    LOG.info("ZCUI   child {} over={}", what, child.isMouseOver(x, y));
                }
            });
            boolean helper = ctx.computeOnClient(mc -> {
                for (var child : mc.gui.screen().children()) {
                    if (child instanceof net.minecraft.client.gui.components.AbstractButton w && w.getMessage().getString().startsWith("Options")) {
                        w.onPress(new MouseButtonInfo(0, 0));
                        return true;
                    }
                }
                return false;
            });
            ctx.waitTicks(5);
            log(ctx, "after onPress directly (" + helper + ") " + when);
        }
        ctx.takeScreenshot("options-" + when.replace(' ', '-'));
        // back out to the game
        for (int i = 0; i < 3; i++) {
            if (ctx.computeOnClient(mc -> mc.gui.screen() == null)) break;
            ctx.getInput().pressKey(InputConstants.KEY_ESCAPE);
            ctx.waitTicks(5);
        }
        log(ctx, "back in game " + when);
    }

    @SuppressWarnings("unchecked")
    /** Presses on one hot spot, drags to another and lets go there, the way a hand on the mouse does. */
    private static void mouse(KitScreen<?> k, KitScreen.Hot from, KitScreen.Hot to) {
        double fx = from.x() + from.w() / 2.0, fy = from.y() + from.h() / 2.0, tx = to.x() + to.w() / 2.0, ty = to.y() + to.h() / 2.0;
        k.mouseClicked(new MouseButtonEvent(fx, fy, new MouseButtonInfo(0, 0)), false);
        k.mouseDragged(new MouseButtonEvent((fx + tx) / 2, (fy + ty) / 2, new MouseButtonInfo(0, 0)), (tx - fx) / 2, (ty - fy) / 2);
        k.mouseDragged(new MouseButtonEvent(tx, ty, new MouseButtonInfo(0, 0)), (tx - fx) / 2, (ty - fy) / 2);
        k.mouseReleased(new MouseButtonEvent(tx, ty, new MouseButtonInfo(0, 0)));
    }

    static List<KitScreen.Hot> hots(KitScreen<?> k) {
        try {
            Field f = KitScreen.class.getDeclaredField("hots");
            f.setAccessible(true);
            return new ArrayList<>((List<KitScreen.Hot>) f.get(k));
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private static String carried(ClientGameTestContext ctx) {
        return ctx.computeOnClient(mc -> mc.player.containerMenu.getCarried().toString());
    }

    /** Moves the real cursor to a GUI point and presses a mouse button there. */
    private static void click(ClientGameTestContext ctx, int gx, int gy, int button) {
        double[] px = ctx.computeOnClient(mc -> {
            var w = mc.getWindow();
            double f = (double) w.getScreenWidth() / w.getGuiScaledWidth();
            return new double[]{(gx + 0.5) * f, (gy + 0.5) * f};
        });
        ctx.getInput().setCursorPos(px[0], px[1]);
        ctx.waitTick();
        String at = ctx.computeOnClient(mc -> String.format("%.1f,%.1f gui=%d", mc.mouseHandler.xpos(), mc.mouseHandler.ypos(), mc.getWindow().getGuiScale()));
        ctx.getInput().pressMouse(button);
        ctx.waitTicks(3);
        LOG.info("ZCUI click button {} at gui {},{} -> window {} ", button, gx, gy, at);
    }

    private static void log(ClientGameTestContext ctx, String when) {
        String s = ctx.computeOnClient((Minecraft mc) -> {
            var w = mc.getWindow();
            return String.valueOf(mc.gui.screen()) + " guiScale=" + w.getGuiScale() + " scaled=" + w.getGuiScaledWidth() + "x" + w.getGuiScaledHeight()
                    + " screen=" + w.getScreenWidth() + "x" + w.getScreenHeight() + " fb=" + w.getWidth() + "x" + w.getHeight();
        });
        LOG.info("ZCUI {}: {}", when, s);
    }
}
