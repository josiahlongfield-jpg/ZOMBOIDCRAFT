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
                    "give @a zomboidcraft:fire_axe", "give @a zomboidcraft:crossbow", "give @a zomboidcraft:revolver", "give @a zomboidcraft:smg",
                    "item replace entity @a armor.head with zomboidcraft:beanie", "item replace entity @a armor.chest with zomboidcraft:hoodie",
                    "item replace entity @a armor.legs with zomboidcraft:jeans", "item replace entity @a armor.feet with zomboidcraft:work_boots")) {
                sp.getServer().runCommand(c);
            }
            sp.getServer().runOnServer(s -> {
                ServerPlayer p = s.getPlayerList().getPlayers().get(0);
                var gear = new com.zomboidcraft.carry.GearContainer(p);
                gear.setItem(com.zomboidcraft.carry.GearStore.SHIRT, new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(com.zomboidcraft.ZomboidCraft.id("t_shirt"))));
                gear.setItem(com.zomboidcraft.carry.GearStore.HANDS, new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(com.zomboidcraft.ZomboidCraft.id("work_gloves"))));
            });
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
            int packTab = KitScreen.Page.PACK.ordinal(), benchTab = KitScreen.Page.BENCH.ordinal();
            if (tabs.size() > packTab) {
                click(ctx, tabs.get(packTab)[0], tabs.get(packTab)[1], 0);
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
            if (tabs.size() > benchTab) {
                click(ctx, tabs.get(benchTab)[0], tabs.get(benchTab)[1], 0);
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
            beltHudTest(ctx, sp);
            mapTest(ctx);
            bookTest(sp);
            spawnPickerTest(ctx, sp);
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
     * Weapons and the quick slots: a picked-up rifle goes on the sling (Back), a pistol into the holster and a knife into
     * its sheath, never a pocket; a slot only takes what fits it. Then the slung rifle seen from behind, over the pack.
     */
    private static void slingTest(ClientGameTestContext ctx, TestSingleplayerContext sp) {
        sp.getServer().runCommand("clear @a");
        sp.getServer().runOnServer(s -> com.zomboidcraft.carry.Carry.gear(s.getPlayerList().getPlayers().get(0)).takeAll(x -> { }));
        for (String c : List.of("time set noon", "weather clear", "effect clear @a", "kill @e[type=!player]")) sp.getServer().runCommand(c);
        sp.getServer().runCommand("give @a zomboidcraft:hiking_backpack");
        ctx.waitTicks(5);
        String report = sp.getServer().computeOnServer(s -> {
            ServerPlayer p = s.getPlayerList().getPlayers().get(0);
            var gear = new com.zomboidcraft.carry.GearContainer(p);
            gear.setItem(com.zomboidcraft.carry.GearStore.BELT, new ItemStack(ModItems.LEATHER_BELT));
            gear.setItem(com.zomboidcraft.carry.GearStore.HOLSTER, new ItemStack(ModItems.HIP_HOLSTER));
            gear.setItem(com.zomboidcraft.carry.GearStore.SHEATH, new ItemStack(ModItems.KNIFE_SHEATH));
            for (int i = 0; i < 9; i++) p.getInventory().setItem(i, ItemStack.EMPTY);
            p.getInventory().add(new ItemStack(ModItems.HUNTING_RIFLE));
            p.getInventory().add(new ItemStack(ModItems.PISTOL));
            p.getInventory().add(new ItemStack(ModItems.KITCHEN_KNIFE));
            p.getInventory().add(new ItemStack(ModItems.HAMMER));
            p.getInventory().add(new ItemStack(Items.BREAD, 2));
            StringBuilder out = new StringBuilder("quick slots:");
            for (int i = 0; i < 9; i++) out.append(' ').append(i + 1).append('=').append(p.getInventory().getItem(i));
            for (int i = 9; i < 36; i++) if (!p.getInventory().getItem(i).isEmpty()) out.append(" POCKET ").append(i).append('=').append(p.getInventory().getItem(i));
            out.append(" pocket takes a rifle=").append(p.inventoryMenu.slots.get(9).mayPlace(new ItemStack(ModItems.ASSAULT_RIFLE)))
                    .append(" pocket takes bread=").append(p.inventoryMenu.slots.get(9).mayPlace(new ItemStack(Items.BREAD)))
                    .append(" back takes dirt=").append(p.inventoryMenu.slots.get(36 + 5).mayPlace(new ItemStack(Items.DIRT)))
                    .append(" holster takes a rifle=").append(p.inventoryMenu.slots.get(36 + 3).mayPlace(new ItemStack(ModItems.ASSAULT_RIFLE)))
                    .append(" pouch open (leather belt)=").append(com.zomboidcraft.carry.QuickSlots.open(p, 6));
            boolean ok = p.getInventory().getItem(5).is(ModItems.HUNTING_RIFLE) && p.getInventory().getItem(3).is(ModItems.PISTOL)
                    && p.getInventory().getItem(4).is(ModItems.KITCHEN_KNIFE) && p.getInventory().getItem(1).is(ModItems.HAMMER);
            out.append(ok ? " ROUTED OK" : " ROUTED WRONG");
            p.getInventory().setItem(1, ItemStack.EMPTY);
            p.getInventory().setSelectedSlot(0);
            return out.toString();
        });
        LOG.info("ZCUI sling: {}", report);
        if (report.contains("ROUTED WRONG")) throw new AssertionError("quick slot routing: " + report);
        // taking the belt off: the hip holster and sheath stop working, and what was in them moves off the belt
        String spilled = sp.getServer().computeOnServer(s -> {
            ServerPlayer p = s.getPlayerList().getPlayers().get(0);
            p.getInventory().setItem(2, new ItemStack(ModItems.CROWBAR));
            return "belt off, before: hip=" + p.getInventory().getItem(2);
        });
        sp.getServer().runOnServer(s -> new com.zomboidcraft.carry.GearContainer(s.getPlayerList().getPlayers().get(0))
                .setItem(com.zomboidcraft.carry.GearStore.BELT, ItemStack.EMPTY));
        ctx.waitTicks(25);
        spilled += sp.getServer().computeOnServer(s -> {
            ServerPlayer p = s.getPlayerList().getPlayers().get(0);
            StringBuilder out = new StringBuilder(" after:");
            for (int i = 0; i < 9; i++) out.append(' ').append(i + 1).append('=').append(p.getInventory().getItem(i));
            out.append(" hip open=").append(com.zomboidcraft.carry.QuickSlots.open(p, 1));
            // and the belt back on for the pictures
            new com.zomboidcraft.carry.GearContainer(p).setItem(com.zomboidcraft.carry.GearStore.BELT, new ItemStack(ModItems.LEATHER_BELT));
            for (int i = 0; i < 36; i++) {
                ItemStack st = p.getInventory().getItem(i);
                if (i != 5 && (st.is(ModItems.PISTOL) || st.is(ModItems.KITCHEN_KNIFE) || st.is(ModItems.CROWBAR))) p.getInventory().setItem(i, ItemStack.EMPTY);
            }
            p.getInventory().setItem(3, new ItemStack(ModItems.PISTOL));
            p.getInventory().setItem(4, new ItemStack(ModItems.KITCHEN_KNIFE));
            return out.toString();
        });
        LOG.info("ZCUI quick slots spill: {}", spilled);
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
        // a melee weapon on the sling instead, without the pack
        sp.getServer().runOnServer(s -> {
            var gear = com.zomboidcraft.carry.Carry.gear(s.getPlayerList().getPlayers().get(0));
            gear.takeAll(x -> { });
        });
        sp.getServer().runOnServer(s -> {
            ServerPlayer p = s.getPlayerList().getPlayers().get(0);
            for (int i = 0; i < 9; i++) p.getInventory().setItem(i, ItemStack.EMPTY);
            p.getInventory().add(new ItemStack(ModItems.FIRE_AXE));
        });
        ctx.waitTicks(60);
        String gearNow = sp.getServer().computeOnServer(s -> {
            ServerPlayer p = s.getPlayerList().getPlayers().get(0);
            var g = com.zomboidcraft.carry.Carry.gear(p);
            return g.get(0) + ", " + g.get(1) + ", " + g.get(2) + ", " + g.get(3) + " back slot " + p.getInventory().getItem(5)
                    + " worn " + p.getAttachedOrElse(com.zomboidcraft.carry.Carry.WORN, List.of());
        });
        String wornClient = ctx.computeOnClient(mc -> String.valueOf(mc.player.getAttachedOrElse(com.zomboidcraft.carry.Carry.WORN, List.of())));
        LOG.info("ZCUI sling axe: server gear {} client worn {}", gearNow, wornClient);
        ctx.runOnClient(mc -> mc.gui.setScreen(new BackView(20f)));
        ctx.waitTicks(3);
        ctx.takeScreenshot("sling-figure-axe");
        ctx.runOnClient(mc -> mc.gui.setScreen(null));
    }

    /**
     * Drag and drop on the pack page with room to spare: bread dragged onto an empty pocket lands there on release;
     * a rifle dragged onto an empty pocket goes back to the belt (no guns in pockets).
     */
    /** The spawn picker drawn with the real place list (the test world isn't a county, so it's opened by hand). */
    private static void spawnPickerTest(ClientGameTestContext ctx, TestSingleplayerContext sp) {
        var places = sp.getServer().computeOnServer(s -> com.zomboidcraft.spawn.SpawnPicker.places());
        LOG.info("ZCUI spawn picker: {} places offered", places.size());
        if (places.size() < 20) throw new AssertionError("spawn picker offers only " + places.size() + " places");
        var open = new com.zomboidcraft.spawn.SpawnNet.Open(places,
                List.of(new com.zomboidcraft.spawn.SpawnNet.Friend("00000000-0000-0000-0000-000000000000", "Sam", "Seabright")), false);
        ctx.runOnClient(mc -> mc.gui.setScreen(new SpawnScreen(open)));
        ctx.waitTicks(5);
        ctx.takeScreenshot("spawn-picker");
        // a click on the Riverside row picks it
        int[] row = ctx.computeOnClient(mc -> {
            SpawnScreen s = (SpawnScreen) mc.gui.screen();
            return s.rowFor("riverside");
        });
        if (row != null) {
            click(ctx, row[0] + 5, row[1] + 4, 0);
            ctx.waitTicks(3);
        }
        String sel = ctx.computeOnClient(mc -> mc.gui.screen() instanceof SpawnScreen s ? s.selected() : "closed");
        LOG.info("ZCUI spawn picker: clicked Riverside row {} -> selected {}", row == null ? "missing" : row[0] + "," + row[1], sel);
        ctx.takeScreenshot("spawn-picker-riverside");
        ctx.runOnClient(mc -> mc.gui.setScreen(null));
    }

    /**
     * The HUD's quick slots in one row, empty ones too (peeked with Left Alt): a full duty kit shows all eight, a plain
     * belt only hands, hip, hip and back. Scrolling skips the slots you don't have.
     */
    private static void beltHudTest(ClientGameTestContext ctx, TestSingleplayerContext sp) {
        for (boolean duty : new boolean[]{true, false}) {
            sp.getServer().runOnServer(s -> {
                ServerPlayer p = s.getPlayerList().getPlayers().get(0);
                var gear = new com.zomboidcraft.carry.GearContainer(p);
                gear.setItem(com.zomboidcraft.carry.GearStore.BELT, new ItemStack(duty ? ModItems.DUTY_BELT : ModItems.LEATHER_BELT));
                gear.setItem(com.zomboidcraft.carry.GearStore.HOLSTER, duty ? new ItemStack(ModItems.HIP_HOLSTER) : ItemStack.EMPTY);
                gear.setItem(com.zomboidcraft.carry.GearStore.SHEATH, duty ? new ItemStack(ModItems.MACHETE_SHEATH) : ItemStack.EMPTY);
                for (int i = 0; i < 9; i++) p.getInventory().setItem(i, ItemStack.EMPTY);
                p.getInventory().setItem(0, new ItemStack(ModItems.CROWBAR));
                p.getInventory().setItem(1, new ItemStack(ModItems.HAMMER));
                if (duty) {
                    p.getInventory().setItem(3, new ItemStack(ModItems.PISTOL));
                    p.getInventory().setItem(4, new ItemStack(ModItems.MACHETE));
                    p.getInventory().setItem(6, new ItemStack(ModItems.BANDAGE, 3));
                }
                p.getInventory().setItem(5, new ItemStack(ModItems.HUNTING_RIFLE));
            });
            ctx.waitTicks(5);
            ctx.runOnClient(mc -> {
                mc.gui.setScreen(null);
                mc.player.getInventory().setSelectedSlot(0);
            });
            ctx.getInput().holdKey(InputConstants.KEY_LALT);
            ctx.waitTicks(20);
            ctx.takeScreenshot(duty ? "hud-belt-duty" : "hud-belt-plain");
            ctx.getInput().releaseKey(InputConstants.KEY_LALT);
            // and worn: the belt round the waist, the pistol and machete hanging off it
            for (float turn : new float[]{0f, 150f, 210f}) {
                ctx.runOnClient(mc -> mc.gui.setScreen(new BackView(turn)));
                ctx.waitTicks(3);
                ctx.takeScreenshot("belt-figure-" + (duty ? "duty-" : "plain-") + Math.round(turn));
            }
            ctx.runOnClient(mc -> mc.gui.setScreen(null));
        }
        // with a plain belt, scrolling on from the second hip slot lands on the sling, not the empty holster place
        String scrolled = ctx.computeOnClient(mc -> {
            var inv = mc.player.getInventory();
            inv.setSelectedSlot(2);
            inv.setSelectedSlot(3);
            int forward = inv.getSelectedSlot();
            inv.setSelectedSlot(4);
            int back = inv.getSelectedSlot();
            inv.setSelectedSlot(6);
            int key = inv.getSelectedSlot();
            inv.setSelectedSlot(0);
            return "from 3 forward -> " + (forward + 1) + ", from there back -> " + (back + 1) + ", key 7 -> " + (key + 1);
        });
        LOG.info("ZCUI quick slot scroll: {}", scrolled);
        // the other rigs worn: a shoulder holster with a knife on the belt, then a thigh holster
        for (boolean shoulder : new boolean[]{true, false}) {
            sp.getServer().runOnServer(s -> {
                ServerPlayer p = s.getPlayerList().getPlayers().get(0);
                var gear = new com.zomboidcraft.carry.GearContainer(p);
                gear.setItem(com.zomboidcraft.carry.GearStore.BELT, new ItemStack(ModItems.LEATHER_BELT));
                gear.setItem(com.zomboidcraft.carry.GearStore.HOLSTER,
                        new ItemStack(shoulder ? ModItems.SHOULDER_HOLSTER : ModItems.THIGH_HOLSTER));
                gear.setItem(com.zomboidcraft.carry.GearStore.SHEATH, shoulder ? new ItemStack(ModItems.KNIFE_SHEATH) : ItemStack.EMPTY);
                for (int i = 0; i < 9; i++) p.getInventory().setItem(i, ItemStack.EMPTY);
                p.getInventory().setItem(3, new ItemStack(ModItems.PISTOL));
                if (shoulder) p.getInventory().setItem(4, new ItemStack(ModItems.KITCHEN_KNIFE));
            });
            ctx.waitTicks(5);
            for (float turn : new float[]{0f, 150f, 210f}) {
                ctx.runOnClient(mc -> mc.gui.setScreen(new BackView(turn)));
                ctx.waitTicks(3);
                ctx.takeScreenshot("rig-figure-" + (shoulder ? "shoulder-" : "thigh-") + Math.round(turn));
            }
            ctx.runOnClient(mc -> mc.gui.setScreen(null));
        }
    }

    /**
     * The map page over the planned county (the test world isn't one, so the atlas is opened by hand): the whole
     * county, the island, then streets close in, each once its tiles are drawn.
     */
    private static void mapTest(ClientGameTestContext ctx) {
        ctx.runOnClient(mc -> com.zomboidcraft.client.map.CountyAtlas.open(12345L, true));
        ctx.runOnClient(mc -> com.zomboidcraft.client.map.MapPlayers.pretend(java.util.List.of(
                new com.zomboidcraft.world.map.MapNet.Spot(java.util.UUID.nameUUIDFromBytes(new byte[] {1}), "Rowan", "minecraft:overworld", 3296, -1248, 0),
                new com.zomboidcraft.world.map.MapNet.Spot(java.util.UUID.nameUUIDFromBytes(new byte[] {2}), "Mags", "minecraft:overworld", 3424, -1312, 0))));
        ctx.runOnClient(mc -> mc.gui.setScreen(null));
        ctx.getInput().pressKey(InputConstants.KEY_E);
        ctx.waitTicks(10);
        ctx.runOnClient(mc -> {
            if (mc.gui.screen() instanceof KitScreen<?> k) k.open(KitScreen.Page.MAP);
        });
        float[][] views = {{0, 0, 0.022f, 3}, {3100, -1500, 0.09f, 2}, {3350, -1250, 0.3f, 1}, {3350, -1250, 1.2f, 0}};
        String[] names = {"county", "island", "downtown", "streets"};
        for (int v = 0; v < views.length; v++) {
            float[] view = views[v];
            ctx.runOnClient(mc -> {
                if (mc.gui.screen() instanceof KitScreen<?> k) k.map.look(view[0], view[1], view[2]);
            });
            long start = System.currentTimeMillis();
            boolean ready = false;
            while (System.currentTimeMillis() - start < 150_000) {
                ctx.waitTicks(20);
                ready = ctx.computeOnClient(mc -> com.zomboidcraft.client.map.CountyAtlas.ready((int) view[3], view[0], view[1]));
                if (ready) break;
            }
            ctx.waitTicks(10);
            LOG.info("ZCUI map {}: ready {} after {} s, {} tiles drawn", names[v], ready, (System.currentTimeMillis() - start) / 1000,
                    ctx.computeOnClient(mc -> com.zomboidcraft.client.map.CountyAtlas.made()));
            ctx.takeScreenshot("map-" + names[v]);
        }
        ctx.runOnClient(mc -> com.zomboidcraft.client.map.MapPlayers.pretend(java.util.List.of()));
        ctx.runOnClient(mc -> mc.gui.setScreen(null));
    }

    /** A skill book read for your band multiplies the XP you earn (SkillBooks). */
    private static void bookTest(TestSingleplayerContext sp) {
        sp.getServer().runOnServer(s -> {
            ServerPlayer p = s.getPlayerList().getPlayers().get(0);
            var skill = com.zomboidcraft.skills.Skill.CARPENTRY;
            var item = com.zomboidcraft.loot.items.LootItems.item("skillbook_carpentry");
            int a = com.zomboidcraft.skills.Skills.xp(p, skill);
            com.zomboidcraft.skills.Skills.addXp(p, skill, 10);
            int b = com.zomboidcraft.skills.Skills.xp(p, skill);
            new ItemStack(item).finishUsingItem(p.level(), p);
            com.zomboidcraft.skills.Skills.addXp(p, skill, 10);
            int c = com.zomboidcraft.skills.Skills.xp(p, skill);
            LOG.info("ZCUI skill book: item {} plain +{} read +{} vol1 read {}", item == null ? "missing" : item.getClass().getSimpleName(),
                    b - a, c - b, com.zomboidcraft.skills.SkillBooks.read(p, skill, 1));
        });
    }

    private static void dragTest(ClientGameTestContext ctx, TestSingleplayerContext sp) {
        sp.getServer().runCommand("clear @a");
        sp.getServer().runOnServer(s -> {
            ServerPlayer p = s.getPlayerList().getPlayers().get(0);
            p.getInventory().setItem(0, new ItemStack(Items.BREAD, 3));
            p.getInventory().setItem(5, new ItemStack(ModItems.HUNTING_RIFLE));
        });
        ctx.waitTicks(5);
        ctx.getInput().pressKey(InputConstants.KEY_E);
        ctx.waitTicks(5);
        ctx.runOnClient(mc -> {
            if (mc.gui.screen() instanceof KitScreen<?> k) k.open(KitScreen.Page.PACK);
        });
        ctx.waitTicks(3);
        for (int belt : new int[]{36, 41}) {
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

        // a pause screen stops the integrated server ticking, and with it the worn-gear sync
        @Override
        public boolean isPauseScreen() {
            return false;
        }

        @Override
        public void extractBackground(net.minecraft.client.gui.GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
            g.fill(0, 0, width, height, 0xFF7A8490);
            var player = minecraft.player;
            try {
                net.minecraft.client.renderer.entity.EntityRenderer renderer = minecraft.getEntityRenderDispatcher().getRenderer(player);
                var state = (net.minecraft.client.renderer.entity.state.EntityRenderState) renderer.createRenderState(player, 1f);
                if (state instanceof net.minecraft.client.renderer.entity.state.LivingEntityRenderState living) {
                    // body yaw 180 faces you, as the inventory draws it; 0 shows the back
                    living.bodyRot = turn;
                    living.yRot = 0;
                    living.xRot = 0;
                }
                state.lightCoords = 0xF000F0;
                float scale = (height - 10) / player.getBbHeight() / 1.1f;
                g.entity(state, scale, new org.joml.Vector3f(0, player.getBbHeight() / 2, 0), new org.joml.Quaternionf().rotateZ((float) Math.PI), null,
                        width / 2 - 120, 2, width / 2 + 120, height - 2);
            } catch (Throwable t) {
                if (!logged) LOG.error("ZCUI back view failed", t);
            }
            logged = true;
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
