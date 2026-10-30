package com.zomboidcraft.client.combat;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import com.zomboidcraft.ZomboidCraft;
import com.zomboidcraft.combat.Attachments;
import com.zomboidcraft.combat.GunSlotData;
import com.zomboidcraft.combat.Guns;
import java.util.List;
import java.util.Map;
import net.minecraft.client.CameraType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
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
            sights(ctx, sp);
        }
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
