package com.zomboidcraft.client.kit;

import com.mojang.blaze3d.platform.InputConstants;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
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
            for (String c : List.of("gamemode survival @a", "give @a zomboidcraft:assault_rifle", "give @a zomboidcraft:red_dot_sight",
                    "give @a zomboidcraft:foregrip", "give @a zomboidcraft:pistol", "give @a zomboidcraft:bandage 3",
                    "give @a minecraft:bread 5", "zc injure left_arm cut")) {
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
            }

            // the bench, then a drag across the gun
            if (tabs.size() > 3) {
                click(ctx, tabs.get(3)[0], tabs.get(3)[1], 0);
                ctx.takeScreenshot("bench");
                log(ctx, "bench");
            }

            // close with E, then the pause menu and its Options button
            ctx.getInput().pressKey(InputConstants.KEY_E);
            ctx.waitTicks(5);
            log(ctx, "after closing");
            pauseOptions(ctx, "after the kit");
        }
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
            String info = ctx.computeOnClient(mc -> {
                StringBuilder b = new StringBuilder();
                for (var child : mc.gui.screen().children()) {
                    if (child instanceof AbstractWidget w) {
                        b.append("\n     ").append(w.getClass().getName()).append(" '").append(w.getMessage().getString()).append("' active=")
                                .append(w.active).append(" visible=").append(w.visible).append(" at ").append(w.getX()).append(',').append(w.getY())
                                .append(' ').append(w.getWidth()).append('x').append(w.getHeight());
                    }
                }
                return b.toString();
            });
            LOG.info("ZCUI pause widgets:{}", info);
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
