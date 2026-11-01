package com.zomboidcraft.client.npc;

import com.zomboidcraft.ZcConfig;
import com.zomboidcraft.client.kit.CompanionScreen;
import com.zomboidcraft.npc.NpcEntity;
import com.zomboidcraft.npc.NpcPanel;
import com.zomboidcraft.survival.character.CharacterData;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Her occupation (NpcSkills.occupy), filmed on her page: the one she came with at random, the occupation list opened
 * from her past, Police Officer picked (Aiming 3, Reloading 2, Nimble 1 plus a hobby spread), and the Tailoring she'd
 * learned before kept through the change. Frames go to the shots/pd5a26 branch (.github/workflows/companionshots.yml).
 */
public class CompanionPastTest implements FabricClientGameTest {
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
            sp.getServer().runOnServer(s -> ZcConfig.get().population.enabled = false);
            sp.getServer().runCommand("execute as @p at @p run zc npc dark_elf raw");
            ctx.waitTicks(20);
            ctx.runOnClient(mc -> {
                NpcEntity npc = clientNpc(mc);
                ClientPlayNetworking.send(new NpcPanel.Bond(npc.getId(), true));
            });
            ctx.waitTicks(10);
            log(sp, "new companion");
            // she's picked up some sewing on the road
            sp.getServer().runCommand("execute as @p at @p run zc npc skill TAILORING 4");
            log(sp, "after learning tailoring");
            ctx.runOnClient(mc -> mc.gui.setScreen(new CompanionScreen(clientNpc(mc), CompanionScreen.Tab.HER)));
            ctx.waitTicks(30);
            shot(ctx, "page");
            ctx.runOnClient(mc -> {
                if (mc.gui.screen() instanceof CompanionScreen page) page.pickOccupation();
            });
            ctx.waitTicks(10);
            shot(ctx, "list");
            int police = new ArrayList<>(CharacterData.occupations().keySet()).indexOf("police_officer");
            ctx.runOnClient(mc -> ClientPlayNetworking.send(new NpcPanel.Order(clientNpc(mc).getId(), NpcPanel.OCCUPATION + police)));
            ctx.waitTicks(40);
            shot(ctx, "picked");
            log(sp, "police officer");
            int nurse = new ArrayList<>(CharacterData.occupations().keySet()).indexOf("nurse");
            ctx.runOnClient(mc -> ClientPlayNetworking.send(new NpcPanel.Order(clientNpc(mc).getId(), NpcPanel.OCCUPATION + nurse)));
            ctx.waitTicks(40);
            shot(ctx, "picked");
            log(sp, "nurse");
            ctx.runOnClient(mc -> mc.gui.setScreen(null));
        }
    }

    private void shot(ClientGameTestContext ctx, String what) {
        for (int i = 0; i < 4; i++) ctx.takeScreenshot(String.format("%03d-%s", frame++, what));
    }

    private static NpcEntity npc(MinecraftServer s) {
        ServerPlayer p = s.getPlayerList().getPlayers().get(0);
        return s.overworld().getEntitiesOfClass(NpcEntity.class, new AABB(p.blockPosition()).inflate(40)).get(0);
    }

    private static NpcEntity clientNpc(net.minecraft.client.Minecraft mc) {
        return mc.level.getEntitiesOfClass(NpcEntity.class, mc.player.getBoundingBox().inflate(40)).get(0);
    }

    private static void log(TestSingleplayerContext sp, String when) {
        sp.getServer().runOnServer(s -> {
            NpcEntity npc = npc(s);
            LOG.info("ZCNPC {}: {}", when, npc.skills.past());
            LOG.info("ZCNPC   {}", com.zomboidcraft.npc.Npcs.class.getSimpleName() + " " + String.join("; ", skillList(npc)));
        });
    }

    private static List<String> skillList(NpcEntity npc) {
        List<String> out = new ArrayList<>();
        for (com.zomboidcraft.skills.Skill k : com.zomboidcraft.skills.Skill.values()) {
            int l = npc.skills.level(k.name());
            if (l > 0) out.add(k.displayName() + " " + l);
        }
        return out;
    }
}
