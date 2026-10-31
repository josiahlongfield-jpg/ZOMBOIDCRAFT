package com.zomboidcraft.client.combat;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import com.zomboidcraft.ZomboidCraft;
import com.zomboidcraft.combat.Attachments;
import com.zomboidcraft.combat.GunSlotData;
import com.zomboidcraft.combat.GunState;
import com.zomboidcraft.combat.Guns;
import java.util.List;
import java.util.Map;
import net.minecraft.client.CameraType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Screenshots a held gun in first and third person and lying on the ground, so a new gun model (a bought mesh in
 * place of a block gun) can be checked without a player. Run on CI by .github/workflows/gunshots.yml.
 */
public class GunLookTest implements FabricClientGameTest {
    private static final Logger LOG = LoggerFactory.getLogger("ZCGUN");
    private static final String[] GUNS = System.getProperty("zc.gun", "assault_rifle,battle_rifle,suppressed_rifle,hunting_rifle,precision_rifle,heavy_sniper,crossbow,grenade_launcher,magnum,pistol,machine_pistol,pocket_pistol,smg,shotgun").split(",");

    @Override
    public void runTest(ClientGameTestContext ctx) {
        try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
            sp.getConnection().waitForChunksRender();
            for (String c : new String[] {"gamemode creative @a", "time set noon", "weather clear", "gamerule doDaylightCycle false"}) {
                sp.getServer().runCommand(c);
            }
            ctx.getInput().resizeWindow(1280, 720);
            int n = 0;
            for (String gun : GUNS) {
                for (String c : new String[] {"kill @e[type=item]", "clear @a", "give @a zomboidcraft:" + gun, "tp @a ~ ~ ~ 0 10"}) {
                    sp.getServer().runCommand(c);
                }
                ctx.waitTicks(30);
                ctx.runOnClient(mc -> mc.player.getInventory().setSelectedSlot(0));
                ctx.waitTicks(20);
                String held = ctx.computeOnClient(mc -> mc.player.getMainHandItem().toString());
                LOG.info("ZCGUN holding {}", held);
                ctx.takeScreenshot(gun + "-fp-hip");
                ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
                ctx.waitTicks(10);
                ctx.takeScreenshot(gun + "-tp-front");
                ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
                // one on the ground just ahead, looked down at
                sp.getServer().runCommand("summon item ^ ^ ^1.5 {Item:{id:\"zomboidcraft:" + gun + "\",count:1},PickupDelay:32767}");
                sp.getServer().runCommand("tp @a ~ ~ ~ 0 60");
                ctx.waitTicks(30);
                ctx.takeScreenshot(gun + "-ground");
                // and again with other parts fitted, one per slot, to check where they sit
                int pick = n++;
                sp.getServer().runOnServer(server -> {
                    for (var player : server.getPlayerList().getPlayers()) {
                        ItemStack gunStack = player.getMainHandItem();
                        String id = Attachments.gunId(gunStack);
                        if (id == null) {
                            continue;
                        }
                        int k = 0;
                        for (Map.Entry<Attachments.Slot, List<String>> e : GunSlotData.FITS.get(id).entrySet()) {
                            String comes = GunSlotData.DEFAULTS.get(id).get(e.getKey());
                            List<String> others = e.getValue().stream().filter(x -> !x.equals(comes)).toList();
                            if (e.getKey() != Attachments.Slot.MAGAZINE && !others.isEmpty()) {
                                Attachments.mount(gunStack, new ItemStack(BuiltInRegistries.ITEM.getValue(ZomboidCraft.id(others.get((pick + k++) % others.size())))));
                            }
                        }
                        LOG.info("ZCGUN kitted {} {}", id, Attachments.of(gunStack).mounted());
                    }
                });
                sp.getServer().runCommand("tp @a ~ ~ ~ 0 10");
                ctx.waitTicks(20);
                ctx.takeScreenshot(gun + "-kit-fp-hip");
                ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
                ctx.waitTicks(10);
                ctx.takeScreenshot(gun + "-kit-tp-front");
                ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
            }
            colours(ctx, sp);
            irons(ctx, sp);
            primitives(ctx, sp);
            sights(ctx, sp);
            reloading(ctx, sp);
        }
    }

    /** The other colours the parts came in: a rifle fitted with every tan part, the loose parts plain and tan side by
     * side on the ground, and the flip-up irons in each of their colours. */
    private static void colours(ClientGameTestContext ctx, TestSingleplayerContext sp) {
        if (!List.of(GUNS).contains("assault_rifle")) {
            return;
        }
        List<String> tan = List.of("holo_sight", "magnifier", "stubby_grip", "weapon_light");
        for (String c : new String[] {"kill @e[type=item]", "clear @a", "give @a zomboidcraft:assault_rifle", "tp @a ~ ~ ~ 0 10"}) {
            sp.getServer().runCommand(c);
        }
        ctx.waitTicks(20);
        sp.getServer().runOnServer(server -> {
            for (var player : server.getPlayerList().getPlayers()) {
                ItemStack gunStack = player.getMainHandItem();
                for (String part : tan) {
                    ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.getValue(ZomboidCraft.id(part)));
                    stack.set(GunState.COLOUR, "tan");
                    Attachments.mount(gunStack, stack);
                }
                LOG.info("ZCGUN colours {}", Attachments.of(gunStack).mounted());
            }
        });
        ctx.waitTicks(20);
        ctx.takeScreenshot("colour-assault_rifle-tan-fp-hip");
        ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
        ctx.waitTicks(10);
        ctx.takeScreenshot("colour-assault_rifle-tan-tp-front");
        ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
        // the same rifle on the ground, and each part plain (near row) and tan (far row)
        sp.getServer().runOnServer(server -> {
            for (var player : server.getPlayerList().getPlayers()) {
                Vec3 at = player.position();
                drop(player.level(), at.add(0, 0, 2.4), player.getMainHandItem().copy());
                for (int i = 0; i < tan.size(); i++) {
                    ItemStack plain = new ItemStack(BuiltInRegistries.ITEM.getValue(ZomboidCraft.id(tan.get(i))));
                    ItemStack coloured = plain.copy();
                    coloured.set(GunState.COLOUR, "tan");
                    double x = 0.75 - i * 0.5;
                    drop(player.level(), at.add(x, 0, 1.0), plain);
                    drop(player.level(), at.add(x, 0, 1.5), coloured);
                }
            }
        });
        sp.getServer().runCommand("clear @a");
        sp.getServer().runCommand("tp @a ~ ~ ~ 0 55");
        ctx.waitTicks(30);
        ctx.takeScreenshot("colour-parts-ground");
        for (String irons : new String[] {"grey", "dark", "camo", "green"}) {
            for (String c : new String[] {"kill @e[type=item]", "clear @a", "give @a zomboidcraft:assault_rifle", "tp @a ~ ~ ~ 0 10"}) {
                sp.getServer().runCommand(c);
            }
            ctx.waitTicks(20);
            sp.getServer().runOnServer(server -> {
                for (var player : server.getPlayerList().getPlayers()) {
                    ItemStack gunStack = player.getMainHandItem();
                    Attachments.unmount(gunStack, Attachments.Slot.OPTIC);      // the irons stand up
                    if (!irons.equals("grey")) {
                        gunStack.set(GunState.COLOUR, irons);
                    }
                    Attachments.refreshModel(gunStack);
                }
            });
            ctx.waitTicks(20);
            ctx.takeScreenshot("colour-irons-" + irons + "-fp-hip");
            ctx.getInput().holdKey(options -> options.keyUse);
            ctx.waitTicks(30);
            ctx.takeScreenshot("colour-irons-" + irons + "-ads");
            ctx.getInput().releaseKey(options -> options.keyUse);
            ctx.waitTicks(10);
        }
    }

    /** Each gun with flip-up irons, its optic off so they stand up: at the hip and looking down them. */
    private static void irons(ClientGameTestContext ctx, TestSingleplayerContext sp) {
        for (String gun : new String[] {"assault_rifle", "precision_rifle", "hunting_rifle", "heavy_sniper"}) {
            if (!List.of(GUNS).contains(gun)) {
                continue;
            }
            for (String c : new String[] {"kill @e[type=item]", "clear @a", "give @a zomboidcraft:" + gun, "tp @a ~ ~ ~ 0 10"}) {
                sp.getServer().runCommand(c);
            }
            ctx.waitTicks(20);
            sp.getServer().runOnServer(server -> {
                for (var player : server.getPlayerList().getPlayers()) {
                    ItemStack gunStack = player.getMainHandItem();
                    Attachments.unmount(gunStack, Attachments.Slot.OPTIC);
                    Attachments.refreshModel(gunStack);
                }
            });
            ctx.waitTicks(20);
            ctx.takeScreenshot("irons-" + gun + "-fp-hip");
            ctx.getInput().holdKey(options -> options.keyUse);
            ctx.waitTicks(30);
            ctx.takeScreenshot("irons-" + gun + "-ads");
            ctx.getInput().releaseKey(options -> options.keyUse);
            ctx.waitTicks(10);
        }
    }

    /** B42's primitive weapons and what they're made from: each in hand, from the front, and on the ground. */
    private static void primitives(ClientGameTestContext ctx, TestSingleplayerContext sp) {
        if (!List.of(GUNS).contains("assault_rifle")) {
            return;
        }
        for (String w : new String[] {"wooden_spear", "fire_hardened_spear", "spear_knife", "stone_axe", "stone_hammer",
                "stone_knife", "plank_nails", "cudgel_nails"}) {
            for (String c : new String[] {"kill @e[type=item]", "clear @a", "give @a zomboidcraft:" + w, "tp @a ~ ~ ~ 0 10"}) {
                sp.getServer().runCommand(c);
            }
            ctx.waitTicks(20);
            ctx.takeScreenshot("prim-" + w + "-fp-hip");
            ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
            ctx.waitTicks(10);
            ctx.takeScreenshot("prim-" + w + "-tp-front");
            ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
        }
        sp.getServer().runOnServer(server -> {
            for (var player : server.getPlayerList().getPlayers()) {
                Vec3 at = player.position();
                String[] all = {"wooden_spear", "fire_hardened_spear", "spear_knife", "stone_axe", "stone_hammer",
                        "stone_knife", "plank_nails", "cudgel_nails", "tree_branch", "stone", "sharp_stone"};
                for (int i = 0; i < all.length; i++) {
                    double x = 1.5 - (i % 6) * 0.6;
                    drop(player.level(), at.add(x, 0, i < 6 ? 1.2 : 2.0),
                            new ItemStack(BuiltInRegistries.ITEM.getValue(ZomboidCraft.id(all[i]))));
                }
            }
        });
        sp.getServer().runCommand("clear @a");
        sp.getServer().runCommand("tp @a ~ ~ ~ 0 55");
        ctx.waitTicks(30);
        ctx.takeScreenshot("prim-ground");
        sp.getServer().runCommand("tp @a ~ ~ ~ 0 10");
    }

    /** The reloading bench: placed and looked at, its G menu and sub-menu, a batch of 9mm loaded (frames through the
     * timed action), brass thrown from a pistol, a revolver's six and a shotgun hull, and every making on the ground. */
    private static void reloading(ClientGameTestContext ctx, TestSingleplayerContext sp) {
        if (!List.of(GUNS).contains("assault_rifle")) {
            return;
        }
        final int[] b = new int[3];
        sp.getServer().runCommand("kill @e[type=item]");
        sp.getServer().runCommand("clear @a");
        sp.getServer().runOnServer(server -> {
            var p = server.getPlayerList().getPlayers().get(0).blockPosition();
            b[0] = p.getX();
            b[1] = p.getY();
            b[2] = p.getZ();
        });
        int x = b[0], y = b[1], z = b[2] + 2;
        sp.getServer().runCommand("setblock " + x + " " + y + " " + z + " zomboidcraft:reloading_bench[facing=north]");
        String at = (x + 0.5) + " " + (y + 0.7) + " " + (z + 0.5);
        String[][] views = {
                {"reload-bench-front", (x + 0.5) + " " + y + " " + (z - 2.3)},
                {"reload-bench-angle", (x + 2.6) + " " + y + " " + (z - 1.6)},
                {"reload-bench-left", (x - 2.2) + " " + y + " " + (z - 1.2)},
                {"reload-bench-close", (x + 0.9) + " " + (y + 0.4) + " " + (z - 1.0)}};
        for (String[] v : views) {
            sp.getServer().runCommand("tp @a " + v[1] + " facing " + at);
            ctx.waitTicks(20);
            ctx.takeScreenshot(v[0]);
        }
        // the G menu, with makings for 9mm and shells but no .38 heads
        for (String c : new String[] {"give @a zomboidcraft:spent_9mm 10", "give @a zomboidcraft:primers 20",
                "give @a zomboidcraft:bullets_9mm 20", "give @a zomboidcraft:smokeless_powder 1",
                "give @a zomboidcraft:spent_hulls 6", "give @a zomboidcraft:lead_shot 1", "give @a zomboidcraft:spent_38 4"}) {
            sp.getServer().runCommand(c);
        }
        sp.getServer().runCommand("tp @a " + (x + 0.5) + " " + y + " " + (z - 1.3) + " facing " + (x + 0.5) + " " + (y + 0.6) + " " + (z + 0.5));
        ctx.waitTicks(20);
        boolean opened = ctx.computeOnClient(mc -> ReloadingClient.interact(mc, mc.hitResult));
        LOG.info("ZCGUN reload menu opened {}", opened);
        ctx.waitTicks(5);
        ctx.takeScreenshot("reload-menu");
        ctx.getInput().pressKey(com.mojang.blaze3d.platform.InputConstants.KEY_RIGHT);
        ctx.waitTicks(5);
        ctx.takeScreenshot("reload-menu-sub");
        ctx.getInput().pressKey(com.mojang.blaze3d.platform.InputConstants.KEY_RETURN);
        for (int i = 0; i <= 11; i++) {
            ctx.waitTicks(i == 0 ? 10 : 36);
            ctx.takeScreenshot(String.format("reload-load-%02d", i));
        }
        String inv = ctx.computeOnClient(mc -> {
            StringBuilder sb = new StringBuilder();
            var in = mc.player.getInventory();
            for (int i = 0; i < in.getContainerSize(); i++) {
                if (!in.getItem(i).isEmpty()) sb.append(in.getItem(i)).append(' ');
            }
            return sb.toString();
        });
        LOG.info("ZCGUN after loading: {}", inv);
        ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
        ctx.waitTicks(10);
        ctx.takeScreenshot("reload-after-tp");
        ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
        // brass: a pistol's casings thrown to the right, frame by frame from behind
        sp.getServer().runCommand("kill @e[type=item]");
        sp.getServer().runCommand("clear @a");
        sp.getServer().runCommand("give @a zomboidcraft:pistol");
        sp.getServer().runCommand("tp @a " + (x - 4.5) + " " + y + " " + (z - 3.5) + " 0 15");
        ctx.waitTicks(20);
        ctx.runOnClient(mc -> mc.player.getInventory().setSelectedSlot(0));
        ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
        ctx.waitTicks(10);
        for (int f = 0; f < 36; f++) {
            if (f % 6 == 0 && f < 30) {
                sp.getServer().runOnServer(server -> {
                    for (var player : server.getPlayerList().getPlayers()) {
                        com.zomboidcraft.combat.Reloading.eject(player, com.zomboidcraft.combat.GunType.PISTOL, 1);
                    }
                });
            }
            ctx.waitTicks(1);
            ctx.takeScreenshot(String.format("reload-brass-%02d", f));
        }
        // the real trigger too: does a fired pistol throw its own?
        ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
        sp.getServer().runCommand("kill @e[type=item]");
        ctx.getInput().holdKey(options -> options.keyAttack);
        ctx.waitTicks(30);
        ctx.getInput().releaseKey(options -> options.keyAttack);
        ctx.waitTicks(10);
        int thrown = ctx.computeOnClient(mc -> mc.level.getEntitiesOfClass(ItemEntity.class, mc.player.getBoundingBox().inflate(6)).size());
        LOG.info("ZCGUN pistol fired: {} casings on the ground", thrown);
        ctx.takeScreenshot("reload-brass-fired");
        // a revolver's six on reload, and a shotgun hull
        sp.getServer().runCommand("kill @e[type=item]");
        sp.getServer().runCommand("tp @a " + (x - 4.5) + " " + y + " " + (z - 3.5) + " 0 50");
        sp.getServer().runOnServer(server -> {
            for (var player : server.getPlayerList().getPlayers()) {
                com.zomboidcraft.combat.Reloading.eject(player, com.zomboidcraft.combat.GunType.MAGNUM, 6);
                com.zomboidcraft.combat.Reloading.eject(player, com.zomboidcraft.combat.GunType.SHOTGUN, 2);
            }
        });
        ctx.waitTicks(40);
        ctx.takeScreenshot("reload-brass-revolver");
        // every making on the ground
        sp.getServer().runCommand("kill @e[type=item]");
        sp.getServer().runCommand("clear @a");
        String[] all = {"spent_9mm", "spent_380", "spent_38", "spent_44", "spent_556", "spent_308", "spent_3030",
                "spent_50", "spent_hulls", "smokeless_powder", "primers", "lead_shot", "gunsmith_magazine",
                "bullets_9mm", "bullets_38", "bullets_44", "bullets_556", "bullets_30", "bullets_50"};
        sp.getServer().runOnServer(server -> {
            for (var player : server.getPlayerList().getPlayers()) {
                Vec3 p = player.position();
                for (int i = 0; i < all.length; i++) {
                    int row = i < 7 ? 0 : i < 13 ? 1 : 2;
                    int col = i < 7 ? i : i < 13 ? i - 7 : i - 13;
                    drop(player.level(), p.add(1.4 - col * 0.47, 0, 1.0 + row * 0.6),
                            new ItemStack(BuiltInRegistries.ITEM.getValue(ZomboidCraft.id(all[i]))));
                }
            }
        });
        sp.getServer().runCommand("tp @a ~ ~ ~ 0 58");
        ctx.waitTicks(30);
        ctx.takeScreenshot("reload-ground");
        sp.getServer().runCommand("tp @a ~ ~ ~ 0 10");
    }

    private static void drop(net.minecraft.world.level.Level level, Vec3 at, ItemStack stack) {
        ItemEntity item = new ItemEntity(level, at.x, at.y, at.z, stack);
        item.setPickUpDelay(32767);
        item.setDeltaMovement(Vec3.ZERO);
        level.addFreshEntity(item);
    }

    /** On the sights through each kind of optic: what each one shows (its own reticle or eyepiece picture), and the
     * magnifier swung in and aside behind the holo. */
    private static void sights(ClientGameTestContext ctx, TestSingleplayerContext sp) {
        String[][] looks = {{"assault_rifle", "holo_sight", "magnifier"}, {"assault_rifle", "holo_sight"},
                {"assault_rifle", "red_dot_sight"}, {"assault_rifle", "scope_4x"}, {"assault_rifle", "combat_scope"},
                {"hunting_rifle", "hunting_scope"}, {"precision_rifle", "precision_scope"}, {"heavy_sniper", "long_range_scope"},
                {"crossbow", "crossbow_scope"}, {"magnum", "red_dot"}};
        for (String[] look : looks) {
            if (!List.of(GUNS).contains(look[0])) {
                continue;
            }
            for (String c : new String[] {"kill @e[type=item]", "clear @a", "give @a zomboidcraft:" + look[0], "tp @a ~ ~ ~ 0 0"}) {
                sp.getServer().runCommand(c);
            }
            ctx.waitTicks(20);
            sp.getServer().runOnServer(server -> {
                for (var player : server.getPlayerList().getPlayers()) {
                    ItemStack gunStack = player.getMainHandItem();
                    for (int i = 1; i < look.length; i++) {
                        Attachments.mount(gunStack, new ItemStack(BuiltInRegistries.ITEM.getValue(ZomboidCraft.id(look[i]))));
                    }
                    LOG.info("ZCGUN sights {} {}", look[0], Attachments.of(gunStack).mounted());
                }
            });
            String name = "sight-" + look[0] + "-" + String.join("-", List.of(look).subList(1, look.length));
            ctx.waitTicks(10);
            ctx.getInput().holdKey(options -> options.keyUse);      // a mouse click would only grab the mouse
            ctx.waitTicks(30);
            ctx.takeScreenshot(name);
            if (look.length > 2) {
                sp.getServer().runOnServer(server -> {
                    for (var player : server.getPlayerList().getPlayers()) {
                        Guns.flipMagnifier(player);
                    }
                });
                ctx.waitTicks(20);
                ctx.takeScreenshot(name + "-aside");
            }
            ctx.getInput().releaseKey(options -> options.keyUse);
            ctx.waitTicks(10);
        }
    }
}
