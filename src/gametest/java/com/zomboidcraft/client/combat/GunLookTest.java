package com.zomboidcraft.client.combat;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Screenshots a held gun in first and third person and lying on the ground, so a new gun model (a bought mesh in
 * place of a block gun) can be checked without a player. Run on CI by .github/workflows/gunshots.yml.
 */
public class GunLookTest implements FabricClientGameTest {
    private static final Logger LOG = LoggerFactory.getLogger("ZCGUN");
    private static final String GUN = System.getProperty("zc.gun", "assault_rifle");

    @Override
    public void runTest(ClientGameTestContext ctx) {
        try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
            sp.getConnection().waitForChunksRender();
            for (String c : new String[] {"gamemode creative @a", "time set noon", "weather clear", "gamerule doDaylightCycle false",
                    "clear @a", "give @a zomboidcraft:" + GUN, "tp @a ~ ~ ~ 0 10"}) {
                sp.getServer().runCommand(c);
            }
            ctx.waitTicks(40);
            ctx.getInput().resizeWindow(1280, 720);
            ctx.runOnClient(mc -> mc.player.getInventory().setSelectedSlot(0));
            ctx.waitTicks(30);
            String held = ctx.computeOnClient(mc -> mc.player.getMainHandItem().toString());
            LOG.info("ZCGUN holding {}", held);
            ctx.takeScreenshot("gun-fp-hip");
            ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
            ctx.waitTicks(10);
            ctx.takeScreenshot("gun-tp-back");
            ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
            ctx.waitTicks(10);
            ctx.takeScreenshot("gun-tp-front");
            ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
            // one on the ground just ahead, looked down at
            sp.getServer().runCommand("summon item ^ ^ ^1.5 {Item:{id:\"zomboidcraft:" + GUN + "\",count:1},PickupDelay:32767}");
            sp.getServer().runCommand("tp @a ~ ~ ~ 0 60");
            ctx.waitTicks(30);
            ctx.takeScreenshot("gun-ground");
        }
    }
}
