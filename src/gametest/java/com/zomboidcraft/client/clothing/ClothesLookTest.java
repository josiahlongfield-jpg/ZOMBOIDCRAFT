package com.zomboidcraft.client.clothing;

import com.zomboidcraft.client.combat.MeshGuns;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Screenshots the player in a few outfits from the front and back, and a garment lying on the ground, so worn
 * clothing drawn by the mesh renderer can be checked without a player. Run on CI by .github/workflows/clothesshots.yml.
 */
public class ClothesLookTest implements FabricClientGameTest {
    private static final Logger LOG = LoggerFactory.getLogger("ZCCLOTHES");
    /** head, chest, legs, feet: the clothing slots that are vanilla armour slots, so a command can fill them. */
    private static final String[][] OUTFITS = {
            {"baseball_cap", "parka", "jeans", "work_boots"},
            {"fire_helmet", "fire_coat", "fire_pants", "rubber_boots"},
            {"cowboy_hat", "leather_jacket", "leather_chaps", "cowboy_boots"},
            {"police_cap", "police_jacket", "cargo_pants", "combat_boots"},
    };
    private static final String[] SLOTS = {"armor.head", "armor.chest", "armor.legs", "armor.feet"};

    @Override
    public void runTest(ClientGameTestContext ctx) {
        try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
            sp.getConnection().waitForChunksRender();
            for (String c : new String[] {"gamemode creative @a", "time set noon", "weather clear", "gamerule doDaylightCycle false"}) {
                sp.getServer().runCommand(c);
            }
            ctx.getInput().resizeWindow(1280, 720);
            for (String[] outfit : OUTFITS) {
                sp.getServer().runCommand("clear @a");
                for (int i = 0; i < SLOTS.length; i++) {
                    sp.getServer().runCommand("item replace entity @a " + SLOTS[i] + " with zomboidcraft:" + outfit[i]);
                }
                sp.getServer().runCommand("tp @a ~ ~ ~ 0 10");
                ctx.waitTicks(30);
                LOG.info("ZCCLOTHES wearing {}", String.join(",", outfit));
                ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
                ctx.waitTicks(10);
                ctx.takeScreenshot("clothes-" + outfit[1] + "-front");
                ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
                ctx.waitTicks(10);
                ctx.takeScreenshot("clothes-" + outfit[1] + "-back");
                ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
            }
            // the last outfit again without the outline, then a garment on the ground
            ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
            MeshGuns.inkOn = false;
            ctx.waitTicks(10);
            ctx.takeScreenshot("clothes-no-ink");
            MeshGuns.inkOn = true;
            ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
            sp.getServer().runCommand("summon item ^ ^ ^1.5 {Item:{id:\"zomboidcraft:hoodie\",count:1},PickupDelay:32767}");
            sp.getServer().runCommand("tp @a ~ ~ ~ 0 60");
            ctx.waitTicks(30);
            ctx.takeScreenshot("clothes-ground");
        }
    }
}
