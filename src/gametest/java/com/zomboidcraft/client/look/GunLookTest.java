package com.zomboidcraft.client.look;

import com.zomboidcraft.ModItems;
import com.zomboidcraft.combat.Attachments;
import com.zomboidcraft.combat.GunState;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Screenshots a rifle with its light and laser on at midnight, from the hip, on the sights and looking down into tall
 * grass: first without a shader pack, then with whatever pack config/iris.properties names (Photon on CI).
 */
public class GunLookTest implements FabricClientGameTest {
    private static final Logger LOG = LoggerFactory.getLogger("ZCLOOK");
    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
    /** GLFW's right mouse button (LWJGL isn't on the test's compile path). */
    private static final int RIGHT_BUTTON = 1;

    @Override
    public void runTest(ClientGameTestContext ctx) {
        try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
            sp.getConnection().waitForChunksRender();
            ctx.getInput().resizeWindow(1280, 720);
            BlockPos at = ctx.computeOnClient(mc -> mc.player.blockPosition());
            LOG.info("ZCLOOK standing at {}", at);
            LOG.info("ZCLOOK game folder {}, iris {}, sodium {}", net.fabricmc.loader.api.FabricLoader.getInstance().getGameDir(),
                    net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("iris"),
                    net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("sodium"));
            for (String c : new String[] {"difficulty peaceful", "gamemode survival @a", "time set midnight", "weather clear"}) {
                sp.getServer().runCommand(c);
            }
            sp.getServer().runOnServer(server -> {
                ServerLevel level = server.overworld();
                for (BlockPos p : BlockPos.betweenClosed(at.offset(-12, 0, -4), at.offset(12, 6, 24))) {
                    level.setBlock(p, Blocks.AIR.defaultBlockState(), FLAGS);
                }
                for (BlockPos p : BlockPos.betweenClosed(at.offset(-12, -1, -4), at.offset(12, -1, 24))) {
                    level.setBlock(p, Blocks.GRASS_BLOCK.defaultBlockState(), FLAGS);
                }
                // a patch of tall grass ahead, and a brick wall behind it
                for (BlockPos p : BlockPos.betweenClosed(at.offset(-8, 0, 2), at.offset(8, 0, 9))) {
                    level.setBlock(p, Blocks.TALL_GRASS.defaultBlockState().setValue(DoublePlantBlock.HALF, DoubleBlockHalf.LOWER), FLAGS);
                    level.setBlock(p.above(), Blocks.TALL_GRASS.defaultBlockState().setValue(DoublePlantBlock.HALF, DoubleBlockHalf.UPPER), FLAGS);
                }
                for (BlockPos p : BlockPos.betweenClosed(at.offset(-7, 0, 14), at.offset(7, 4, 14))) {
                    level.setBlock(p, Blocks.STONE_BRICKS.defaultBlockState(), FLAGS);
                }
                ServerPlayer player = server.getPlayerList().getPlayers().get(0);
                ItemStack gun = new ItemStack(ModItems.ASSAULT_RIFLE);
                Attachments.mount(gun, new ItemStack(ModItems.LASER_SIGHT));
                Attachments.mount(gun, new ItemStack(ModItems.WEAPON_LIGHT));
                gun.set(GunState.LASER_ON, true);
                gun.set(GunState.RAIL_ON, true);
                player.getInventory().setItem(player.getInventory().getSelectedSlot(), gun);
            });
            look(sp, at, 0, 4);
            ctx.waitTicks(60);
            shots(ctx, sp, at, "vanilla");

            boolean on = ctx.computeOnClient(mc -> shaders(true));
            LOG.info("ZCLOOK shader pack switched on: {}", on);
            ctx.waitTicks(200);
            shots(ctx, sp, at, "pack");
        }
    }

    private static void shots(ClientGameTestContext ctx, TestSingleplayerContext sp, BlockPos at, String name) {
        laser(sp, true);
        look(sp, at, 0, 4);
        ctx.waitTicks(30);
        ctx.takeScreenshot(name + "-hip");
        ctx.getInput().holdMouse(RIGHT_BUTTON);
        ctx.waitTicks(20);
        ctx.takeScreenshot(name + "-sights");
        ctx.getInput().releaseMouse(RIGHT_BUTTON);
        look(sp, at, 25, 3);
        ctx.waitTicks(20);
        ctx.takeScreenshot(name + "-turned");
        laser(sp, false);
        look(sp, at, 0, 22);
        ctx.waitTicks(30);
        ctx.takeScreenshot(name + "-light-grass");
        look(sp, at, -10, 6);
        ctx.waitTicks(20);
        ctx.takeScreenshot(name + "-light-wall");
    }

    private static void look(TestSingleplayerContext sp, BlockPos at, float yaw, float pitch) {
        sp.getServer().runCommand(String.format("tp @a %d %d %d %.1f %.1f", at.getX(), at.getY(), at.getZ(), yaw, pitch));
    }

    private static void laser(TestSingleplayerContext sp, boolean on) {
        sp.getServer().runOnServer(server -> server.getPlayerList().getPlayers().get(0).getMainHandItem().set(GunState.LASER_ON, on));
    }

    /** Switches the shader pack on or off through Iris's own API (none without Iris). */
    private static boolean shaders(boolean on) {
        try {
            Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            Object iris = api.getMethod("getInstance").invoke(null);
            Object config = api.getMethod("getConfig").invoke(iris);
            Class.forName("net.irisshaders.iris.api.v0.IrisApiConfig").getMethod("setShadersEnabledAndApply", boolean.class)
                    .invoke(config, on);
            return (boolean) api.getMethod("isShaderPackInUse").invoke(iris);
        } catch (ReflectiveOperationException | LinkageError e) {
            LOG.info("ZCLOOK no shader pack: {}", e.toString());
            return false;
        }
    }
}
