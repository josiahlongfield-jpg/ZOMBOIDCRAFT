package com.zomboidcraft.client.kit;

import com.zomboidcraft.client.survivor.SurvivorPack;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Previews of a Zomboid survivor before a build goes out: the appearance page (as it opens, randomised, close up),
 * then her in first person with a rifle at the hip and aimed, with an empty hand, and from the front. Needs the
 * survivor packs in the run's config/zomboidcraft/npc, so it runs on a PC that has them, never on CI:
 * ./gradlew runClientGameTest -Puitest with this class as the only fabric-client-gametest entrypoint.
 */
public class SurvivorShotsTest implements FabricClientGameTest {
    private static final Logger LOG = LoggerFactory.getLogger("ZCSURV");

    @Override
    public void runTest(ClientGameTestContext ctx) {
        try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
            sp.getConnection().waitForChunksRender();
            for (String c : new String[] {"gamemode creative @a", "time set noon", "weather clear", "gamerule doDaylightCycle false"}) {
                sp.getServer().runCommand(c);
            }
            ctx.getInput().resizeWindow(1600, 900);
            boolean packs = ctx.computeOnClient(mc -> SurvivorPack.installed());
            LOG.info("ZCSURV packs installed: {}", packs);
            if (!packs) {
                ctx.takeScreenshot("surv-no-packs");
                return;
            }
            // the page, opened as a new survivor sees it, once her skin has been made
            ctx.runOnClient(mc -> mc.gui.setScreen(new AppearanceScreen()));
            ctx.waitTicks(80);
            ctx.runOnClient(SurvivorShotsTest::state);
            ctx.takeScreenshot("surv-appearance");
            for (int i = 1; i <= 2; i++) {
                ctx.runOnClient(mc -> call(mc.gui.screen(), "randomise"));
                ctx.waitTicks(60);
                ctx.takeScreenshot("surv-appearance-random-" + i);
            }
            ctx.runOnClient(mc -> set(mc.gui.screen(), "close", true));
            ctx.waitTicks(20);
            ctx.takeScreenshot("surv-appearance-closeup");
            ctx.runOnClient(mc -> set(mc.gui.screen(), "close", false));
            ctx.runOnClient(mc -> call(mc.gui.screen(), "finish"));
            ctx.waitTicks(40);
            ctx.runOnClient(mc -> mc.gui.setScreen(null));
            ctx.waitTicks(40);

            // first person: an empty hand (nothing of her shows), then the rifle at the hip and aimed
            sp.getServer().runCommand("clear @a");
            sp.getServer().runCommand("tp @a ~ ~ ~ 0 10");
            ctx.waitTicks(30);
            ctx.takeScreenshot("surv-fp-empty");
            sp.getServer().runCommand("give @a zomboidcraft:assault_rifle");
            ctx.waitTicks(20);
            ctx.runOnClient(mc -> mc.player.getInventory().setSelectedSlot(0));
            ctx.waitTicks(30);
            ctx.takeScreenshot("surv-fp-rifle-hip");
            ctx.getInput().holdKey(options -> options.keyUse);
            ctx.waitTicks(30);
            ctx.takeScreenshot("surv-fp-rifle-aim");
            ctx.getInput().releaseKey(options -> options.keyUse);
            ctx.waitTicks(10);
            for (String gun : new String[] {"pistol", "shotgun"}) {
                sp.getServer().runCommand("clear @a");
                sp.getServer().runCommand("give @a zomboidcraft:" + gun);
                ctx.waitTicks(30);
                ctx.takeScreenshot("surv-fp-" + gun + "-hip");
                ctx.getInput().holdKey(options -> options.keyUse);
                ctx.waitTicks(30);
                ctx.takeScreenshot("surv-fp-" + gun + "-aim");
                ctx.getInput().releaseKey(options -> options.keyUse);
                ctx.waitTicks(10);
            }
            // and her from the front, rifle in hand
            sp.getServer().runCommand("clear @a");
            sp.getServer().runCommand("give @a zomboidcraft:assault_rifle");
            ctx.waitTicks(20);
            ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
            ctx.waitTicks(20);
            ctx.takeScreenshot("surv-tp-front-rifle");
            ctx.getInput().holdKey(options -> options.keyUse);
            ctx.waitTicks(30);
            ctx.takeScreenshot("surv-tp-front-rifle-aim");
            ctx.getInput().releaseKey(options -> options.keyUse);
            ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
            ctx.waitTicks(10);
        }
    }

    /** What the survivor code makes of the local player right now, for when a shot shows the wrong thing. */
    private static void state(net.minecraft.client.Minecraft mc) {
        try {
            Class<?> sv = Class.forName("com.zomboidcraft.client.survivor.Survivors");
            Method look = sv.getDeclaredMethod("lookOf", net.minecraft.world.entity.player.Player.class);
            Method outfit = sv.getDeclaredMethod("outfit", net.minecraft.world.entity.player.Player.class);
            look.setAccessible(true);
            outfit.setAccessible(true);
            Field preview = sv.getDeclaredField("preview");
            LOG.info("ZCSURV screen={} preview={} look={} active={} outfit={}", mc.gui.screen(), preview.get(null),
                    look.invoke(null, mc.player), com.zomboidcraft.client.survivor.Survivors.active(mc.player),
                    outfit.invoke(null, mc.player));
        } catch (ReflectiveOperationException | RuntimeException e) {
            LOG.error("ZCSURV state failed", e);
        }
    }

    private static void call(Object screen, String method) {
        try {
            Method m = screen.getClass().getDeclaredMethod(method);
            m.setAccessible(true);
            m.invoke(screen);
        } catch (ReflectiveOperationException e) {
            LOG.error("ZCSURV couldn't call {} on {}", method, screen, e);
        }
    }

    private static void set(Object screen, String field, Object value) {
        try {
            Field f = screen.getClass().getDeclaredField(field);
            f.setAccessible(true);
            f.set(screen, value);
        } catch (ReflectiveOperationException e) {
            LOG.error("ZCSURV couldn't set {} on {}", field, screen, e);
        }
    }
}
