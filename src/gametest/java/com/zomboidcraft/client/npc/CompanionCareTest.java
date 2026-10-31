package com.zomboidcraft.client.npc;

import com.zomboidcraft.ZcConfig;
import com.zomboidcraft.combat.GunState;
import com.zomboidcraft.npc.NpcCare;
import com.zomboidcraft.npc.NpcEntity;
import com.zomboidcraft.npc.NpcNet;
import com.zomboidcraft.npc.NpcSkills;
import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A companion looking after herself (NpcCare), filmed: hurt, with an empty pistol, near a few things on the ground and a
 * cupboard nobody's opened. She gathers what she needs and asks about what you might want, you let her keep it on the
 * talk page, she puts on the backpack, patches herself up, reads her book under the lights and fights a zombie with the
 * real rounds she found. Frames go to the shots/pd5a26 branch (.github/workflows/companionshots.yml).
 */
public class CompanionCareTest implements FabricClientGameTest {
    private static final Logger LOG = LoggerFactory.getLogger("ZCNPC");
    private int frame;

    @Override
    public void runTest(ClientGameTestContext ctx) {
        try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
            sp.getConnection().waitForChunksRender();
            ctx.getInput().resizeWindow(1280, 720);
            for (String c : List.of("gamemode creative @a", "time set noon", "weather clear", "gamerule doDaylightCycle false",
                    "gamerule doMobSpawning false", "kill @e[type=!player]")) {
                sp.getServer().runCommand(c);
            }
            BlockPos c = sp.getServer().computeOnServer(CompanionCareTest::stage);
            ctx.waitTicks(40);
            sp.getServer().runCommand("zc npc dark_elf raw");
            ctx.waitTicks(10);
            sp.getServer().runOnServer(s -> setUp(s, c));
            ctx.waitTicks(20);
            shot(ctx, "start");
            log(sp, "start");
            for (int i = 0; i < 18; i++) {
                ctx.waitTicks(12);
                shot(ctx, "gather");
            }
            log(sp, "after gathering");
            // walk over and answer her on the talk page
            sp.getServer().runOnServer(s -> {
                NpcEntity npc = npc(s);
                s.getCommands().performPrefixedCommand(s.createCommandSourceStack(),
                        String.format(java.util.Locale.ROOT, "tp @a %.2f %.2f %.2f 0 15", npc.getX(), npc.getY(), npc.getZ() - 2.6));
            });
            ctx.waitTicks(15);
            ctx.runOnClient(mc -> {
                NpcEntity npc = clientNpc(mc);
                mc.player.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, npc.getEyePosition());
                mc.setScreen(new TalkScreen(npc));
            });
            ctx.waitTicks(10);
            shot(ctx, "talk");
            for (int i = 0; i < 4; i++) {
                ctx.runOnClient(mc -> {
                    NpcEntity npc = clientNpc(mc);
                    if (!npc.offer().isEmpty()) ClientPlayNetworking.send(new NpcNet.Talk(npc.getId(), NpcNet.OFFER_KEEP));
                });
                ctx.waitTicks(25);
                shot(ctx, "talk");
            }
            ctx.runOnClient(mc -> ClientPlayNetworking.send(new NpcNet.Talk(clientNpc(mc).getId(), NpcNet.CARRYING)));
            ctx.waitTicks(10);
            ctx.runOnClient(mc -> mc.setScreen(null));
            ctx.waitTicks(10);
            shot(ctx, "carrying");
            log(sp, "after answering");
            // back up on the stand to watch her sort herself out
            sp.getServer().runCommand(stand(c));
            for (int i = 0; i < 26; i++) {
                ctx.waitTicks(16);
                shot(ctx, "care");
                if (i % 6 == 5) log(sp, "caring " + i);
            }
            log(sp, "after caring");
            sp.getServer().runCommand("summon minecraft:zombie " + c.getX() + " " + c.getY() + " " + (c.getZ() + 11));
            for (int i = 0; i < 20; i++) {
                ctx.waitTicks(6);
                shot(ctx, "fight");
            }
            log(sp, "after the fight");
        }
    }

    private void shot(ClientGameTestContext ctx, String what) {
        ctx.takeScreenshot(String.format("%03d-%s", frame++, what));
    }

    private static NpcEntity npc(MinecraftServer s) {
        ServerLevel level = s.overworld();
        ServerPlayer p = s.getPlayerList().getPlayers().get(0);
        return level.getEntitiesOfClass(NpcEntity.class, new AABB(p.blockPosition()).inflate(40)).get(0);
    }

    private static NpcEntity clientNpc(net.minecraft.client.Minecraft mc) {
        return mc.level.getEntitiesOfClass(NpcEntity.class, mc.player.getBoundingBox().inflate(40)).get(0);
    }

    private static void log(TestSingleplayerContext sp, String when) {
        sp.getServer().runOnServer(s -> {
            NpcEntity npc = npc(s);
            LOG.info("ZCNPC {}: health {}/{}, first aid {}, asking about '{}'", when, Math.round(npc.getHealth()), Math.round(npc.getMaxHealth()),
                    npc.skills.level(NpcSkills.Skill.FIRST_AID), npc.offer());
            for (String l : NpcCare.carrying(npc, s.getPlayerList().getPlayers().get(0).getUUID())) LOG.info("ZCNPC   {}", l);
        });
    }

    /** A lit stone floor cleared of everything, and a stand for the camera; returns the middle of the floor. */
    private static BlockPos stage(MinecraftServer s) {
        ZcConfig.get().population.enabled = false;
        ServerLevel level = s.overworld();
        ServerPlayer p = s.getPlayerList().getPlayers().get(0);
        BlockPos c = p.blockPosition();
        String at = " " + c.getX() + " " + c.getY() + " " + c.getZ();
        for (String cmd : List.of(
                "fill ~-14 ~-1 ~-14 ~14 ~-1 ~14 minecraft:smooth_stone", "fill ~-14 ~ ~-14 ~14 ~10 ~14 minecraft:air",
                "fill ~-14 ~-2 ~-14 ~14 ~-2 ~14 minecraft:stone",
                "fill ~ ~ ~-7 ~ ~3 ~-7 minecraft:stone_bricks")) {
            s.getCommands().performPrefixedCommand(s.createCommandSourceStack().withPosition(net.minecraft.world.phys.Vec3.atBottomCenterOf(c)), cmd);
        }
        for (int x = -12; x <= 12; x += 4) {
            for (int z = -12; z <= 12; z += 4) {
                level.setBlockAndUpdate(c.offset(x, -1, z), net.minecraft.world.level.block.Blocks.SEA_LANTERN.defaultBlockState());
            }
        }
        LOG.info("ZCNPC stage at{}", at);
        s.getCommands().performPrefixedCommand(s.createCommandSourceStack(), stand(c));
        return c;
    }

    /** Up on the stand, looking over the floor. */
    private static String stand(BlockPos c) {
        return "tp @a " + (c.getX() + 0.5) + " " + (c.getY() + 4) + " " + (c.getZ() - 6.5) + " 0 28";
    }

    private static ItemStack item(String id, int n) {
        return new ItemStack(BuiltInRegistries.ITEM.getValue(com.zomboidcraft.ZomboidCraft.id(id)), n);
    }

    /** Her at the middle, hurt, holding an empty pistol; things lying about; a medicine cupboard nobody has opened. */
    private static void setUp(MinecraftServer s, BlockPos c) {
        ServerLevel level = s.overworld();
        NpcEntity npc = npc(s);
        npc.teleportTo(c.getX() + 0.5, c.getY(), c.getZ() + 0.5);
        ItemStack pistol = item("pistol", 1);
        pistol.set(GunState.TYPE, GunState.EMPTY_MAG);
        npc.setItemSlot(EquipmentSlot.MAINHAND, pistol);
        npc.setHealth(npc.getMaxHealth() * 0.4f);
        Object[][] ground = {{"pistol_ammo", 30, 3, 2}, {"hiking_backpack", 1, -3, 3}, {"skillbook_first_aid", 1, 2, 5},
                {"assault_rifle", 1, -2, 6}, {"bandage", 2, 0, 4}};
        for (Object[] g : ground) {
            var e = new net.minecraft.world.entity.item.ItemEntity(level, c.getX() + 0.5 + (int) g[2], c.getY() + 0.2, c.getZ() + 0.5 + (int) g[3],
                    item((String) g[0], (int) g[1]));
            e.setDeltaMovement(0, 0, 0);
            e.setPickUpDelay(32767);
            level.addFreshEntity(e);
        }
        BlockPos chest = c.offset(5, 0, 4);
        level.setBlockAndUpdate(chest, net.minecraft.world.level.block.Blocks.CHEST.defaultBlockState());
        if (level.getBlockEntity(chest) instanceof net.minecraft.world.level.block.entity.ChestBlockEntity box) {
            box.setLootTable(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.LOOT_TABLE,
                    com.zomboidcraft.ZomboidCraft.id("shop/home_medicine")), 7L);
        }
    }
}
