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
import net.minecraft.client.gui.screens.Screen;
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
        try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
            sp.getConnection().waitForChunksRender();
            for (String c : List.of("gamemode survival @a", "give @a zomboidcraft:assault_rifle", "give @a zomboidcraft:red_dot_sight",
                    "give @a zomboidcraft:foregrip", "give @a zomboidcraft:pistol", "give @a zomboidcraft:bandage 3",
                    "give @a minecraft:bread 5", "zc injure left_arm cut")) {
                sp.getServer().runCommand(c);
            }
            ctx.waitTicks(20);
            ctx.runOnClient(mc -> {
                mc.options.guiScale().set(4);
                mc.resizeGui();
            });
            ctx.waitTicks(5);
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
            ctx.getInput().pressKey(InputConstants.KEY_ESCAPE);
            ctx.waitTicks(5);
            log(ctx, "after Esc");
            ctx.takeScreenshot("pause");
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
            if (options != null) {
                click(ctx, options[0], options[1], 0);
                log(ctx, "after clicking Options");
                ctx.takeScreenshot("options");
            } else {
                LOG.info("ZCUI no Options button found");
            }
        }
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
