package com.zomboidcraft.client.kit;

import com.mojang.blaze3d.platform.InputConstants;
import com.zomboidcraft.skills.Skill;
import com.zomboidcraft.survival.Nutrition;
import com.zomboidcraft.survival.Sleep;
import com.zomboidcraft.survival.SurvivalStats;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Frames for the survival wave 1 preview: the moodle strip as hunger, thirst and tiredness climb through Zomboid's
 * four stages, eating to Full and Stuffed, the Skills page scrolled through the B42 list, and lying down on a bed with
 * the screen going dark and the needs clock fast-forwarding. Run on CI by .github/workflows/survivalshots.yml.
 */
public class SurvivalLookTest implements FabricClientGameTest {
    private static final Logger LOG = LoggerFactory.getLogger("ZCSURV");
    private int frame;

    @Override
    public void runTest(ClientGameTestContext ctx) {
        try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
            sp.getConnection().waitForChunksRender();
            for (String c : new String[] {"gamemode survival @a", "time set midnight", "weather clear", "gamerule doDaylightCycle false",
                    "give @a minecraft:bread 4", "give @a zomboidcraft:canned_ham 2"}) {
                sp.getServer().runCommand(c);
            }
            ctx.getInput().resizeWindow(1280, 720);
            ctx.runOnClient(mc -> {
                mc.options.guiScale().set(3);
                mc.resizeGui();
            });
            ctx.waitTicks(20);
            shot(ctx, "start");

            // hunger, thirst and tiredness climbing together, stage by stage
            float[][] steps = {{0.16f, 87, 61}, {0.27f, 74, 71}, {0.47f, 29, 81}, {0.72f, 15, 91}};
            for (float[] s : steps) {
                sp.getServer().runOnServer(server -> {
                    ServerPlayer p = server.getPlayerList().getPlayers().get(0);
                    Nutrition.set(p, Nutrition.get(p).withHunger(s[0]));
                    SurvivalStats.set(p, SurvivalStats.get(p).withThirst(s[1]).withFatigue(s[2]));
                });
                ctx.waitTicks(30);
                shot(ctx, "needs");
                ctx.waitTicks(40);
                shot(ctx, "needs");
            }
            log(sp, "starving");

            // eating: the ham takes hunger right down, then bread past full
            sp.getServer().runOnServer(server -> {
                ServerPlayer p = server.getPlayerList().getPlayers().get(0);
                SurvivalStats.set(p, SurvivalStats.get(p).withThirst(100).withFatigue(65));
                com.zomboidcraft.survival.Drinks.onConsumed(p, new net.minecraft.world.item.ItemStack(
                        com.zomboidcraft.loot.items.LootItems.item("canned_ham")));
                LOG.info("ZCSURV ham is worth {}", com.zomboidcraft.survival.Foods.of(new net.minecraft.world.item.ItemStack(
                        com.zomboidcraft.loot.items.LootItems.item("canned_ham"))));
            });
            ctx.waitTicks(30);
            shot(ctx, "ate");
            log(sp, "after the ham");
            sp.getServer().runOnServer(server -> {
                ServerPlayer p = server.getPlayerList().getPlayers().get(0);
                for (int i = 0; i < 3; i++) {
                    com.zomboidcraft.survival.Drinks.onConsumed(p, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BREAD));
                }
            });
            ctx.waitTicks(30);
            log(sp, "after three loaves");
            ctx.waitTicks(30);
            shot(ctx, "stuffed");

            // the rest of the set: a toothache and a sting for pain, a cold, a turned stomach, a few drinks
            sp.getServer().runOnServer(server -> {
                ServerPlayer p = server.getPlayerList().getPlayers().get(0);
                var m = com.zomboidcraft.survival.Ailments.edit(p);
                m.put("tooth", 999);
                m.put("sting", 30);
                m.put("cold", 2000);
                m.put("nausea", 60);
                m.put("drunk", 70);
                com.zomboidcraft.survival.Ailments.save(p, m);
            });
            ctx.waitTicks(50);
            shot(ctx, "ailments");
            ctx.waitTicks(40);
            shot(ctx, "ailments");

            // the Skills page, scrolled through the whole Zomboid list
            sp.getServer().runOnServer(server -> {
                ServerPlayer p = server.getPlayerList().getPlayers().get(0);
                com.zomboidcraft.skills.Skills.addXp(p, Skill.FITNESS, 300);
                com.zomboidcraft.skills.Skills.addXp(p, Skill.AIMING, 400);
            });
            ctx.getInput().pressKey(InputConstants.KEY_E);
            ctx.waitTicks(10);
            ctx.runOnClient(mc -> {
                if (mc.gui.screen() instanceof KitScreen<?> k) k.open(KitScreen.Page.SKILLS);
            });
            ctx.waitTicks(5);
            shot(ctx, "skills");
            for (int i = 0; i < 4; i++) {
                ctx.runOnClient(mc -> {
                    if (mc.gui.screen() instanceof KitScreen<?> k) {
                        for (int j = 0; j < 3; j++) k.skills.scrolled(0, 0, -1);
                    }
                });
                ctx.waitTicks(5);
                shot(ctx, "skills");
            }
            ctx.getInput().pressKey(InputConstants.KEY_ESCAPE);
            ctx.waitTicks(10);

            // a bed in front of you: lie down, the screen goes dark, rest runs fast, you wake rested
            sp.getServer().runOnServer(server -> {
                ServerPlayer p = server.getPlayerList().getPlayers().get(0);
                net.minecraft.core.BlockPos bed = p.blockPosition().relative(p.getDirection(), 2);
                server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),
                        "setblock " + bed.getX() + " " + bed.getY() + " " + bed.getZ() + " zomboidcraft:home_bed_quilt_head");
                SurvivalStats.set(p, SurvivalStats.get(p).withFatigue(85));
                Nutrition.set(p, Nutrition.get(p).withHunger(0.05f));
            });
            ctx.waitTicks(20);
            shot(ctx, "bed");
            sp.getServer().runOnServer(server -> {
                ServerPlayer p = server.getPlayerList().getPlayers().get(0);
                net.minecraft.core.BlockPos bed = p.blockPosition().relative(p.getDirection(), 2);
                LOG.info("ZCSURV bed block {} -> {}", p.level().getBlockState(bed), Sleep.bedOf(p.level().getBlockState(bed)));
                try {
                    var m = Sleep.class.getDeclaredMethod("lieDown", ServerPlayer.class, net.minecraft.core.BlockPos.class);
                    m.setAccessible(true);
                    m.invoke(null, p, bed);
                } catch (ReflectiveOperationException e) {
                    LOG.error("ZCSURV couldn't lie down", e);
                }
            });
            for (int i = 0; i < 12; i++) {
                ctx.waitTicks(20);
                shot(ctx, "asleep");
                log(sp, "asleep " + i);
            }
            log(sp, "end");
        }
    }

    private void shot(ClientGameTestContext ctx, String what) {
        ctx.takeScreenshot(String.format("surv-%03d-%s", frame++, what));
    }

    private static void log(TestSingleplayerContext sp, String when) {
        String line = sp.getServer().computeOnServer(server -> {
            ServerPlayer p = server.getPlayerList().getPlayers().get(0);
            Nutrition n = Nutrition.get(p);
            SurvivalStats s = SurvivalStats.get(p);
            return String.format("hunger %.2f kcal %.0f weight %.2f thirst %.0f fatigue %.1f asleep %s hp %.1f food %d tickrate sprinting %s",
                    n.hunger(), n.calories(), n.weight(), s.thirst(), s.fatigue(), Sleep.asleep(p), p.getHealth(),
                    p.getFoodData().getFoodLevel(), server.tickRateManager().isSprinting());
        });
        LOG.info("ZCSURV {}: {}", when, line);
    }
}
