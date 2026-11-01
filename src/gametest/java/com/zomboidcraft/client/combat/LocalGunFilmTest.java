package com.zomboidcraft.client.combat;

import com.zomboidcraft.ZomboidCraft;
import com.zomboidcraft.client.cars.CarAssets;
import com.zomboidcraft.combat.GunState;
import com.zomboidcraft.combat.Guns;
import com.zomboidcraft.combat.LocalGuns;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.phys.AABB;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The preview of guns kept on this computer (combat/LocalGuns, Josiah's Brita pack 2026-11-01). Brita's files can't
 * go to CI, so two stand-ins are written into config/zomboidcraft/guns the way the Brita guns will be on his PC, each
 * with a mesh of ours under another name: one riding on our pistol (drawn with the magnum's mesh, its own name, .500
 * calibre and five rounds), one on the block rimfire rifle (drawn with the SMG's mesh). Each is given with
 * /zc localgun, held hip, aimed and from the front, and fired at a zombie; then the files are taken away, as on a
 * friend's computer, and the same gun is our plain one again. ZCLOCAL lines caption the frames.
 */
public class LocalGunFilmTest implements FabricClientGameTest {
    private static final Logger LOG = LoggerFactory.getLogger("ZCLOCAL");
    private static int frame;

    @Override
    public void runTest(ClientGameTestContext ctx) {
        try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
            sp.getConnection().waitForChunksRender();
            for (String c : new String[] {"gamemode creative @a", "time set noon", "weather clear", "gamerule doDaylightCycle false",
                    "gamerule doMobSpawning false", "zc config population.enabled false"}) {
                sp.getServer().runCommand(c);
            }
            ctx.getInput().resizeWindow(1280, 720);
            sp.getServer().runCommand("effect give @a night_vision infinite 0 true");
            sp.getServer().runCommand("tp @a 0 120 0");
            ctx.waitTicks(20);
            sp.getServer().runCommand("fill -20 99 -20 20 99 20 minecraft:stone");
            sp.getServer().runCommand("fill -20 100 -20 20 110 20 minecraft:air");
            sp.getServer().runCommand("kill @e[type=!player]");
            ctx.waitTicks(20);

            ctx.runOnClient(mc -> standIn(mc, "standin_500", "Stand-in .500 revolver (local)", "pistol", "pbr_pistol", "pbr_magnum", ".500 S&W",
                    "{\"damage\": 30.0, \"magazineSize\": 5, \"recoil\": 2.5}", "sw500_ammo"));
            ctx.runOnClient(mc -> standIn(mc, "standin_rimfire", "Stand-in SMG on the rimfire (local)", "rimfire", "block_rimfire", "pbr_smg", ".22 LR",
                    "{\"magazineSize\": 50}", null));
            sp.getServer().runCommand("zc localgun reload");
            ctx.waitTicks(5);

            show(ctx, sp, "standin_500", "pistol");
            show(ctx, sp, "standin_rimfire", "rimfire");

            // a friend's computer: no files, so the very same stacks are our own guns
            for (String id : new String[] {"standin_500", "standin_rimfire"}) {
                try {
                    Files.deleteIfExists(LocalGuns.DIR.resolve(id + ".json"));
                    Files.deleteIfExists(LocalGuns.DIR.resolve(id + ".zmesh"));
                } catch (java.io.IOException e) {
                    throw new RuntimeException(e);
                }
            }
            sp.getServer().runCommand("zc localgun reload");
            ctx.waitTicks(5);
            friend(ctx, sp, "standin_500", "pistol");
            friend(ctx, sp, "standin_rimfire", "rimfire");
            rounds(ctx, sp);
        }
    }

    /** A local gun's two files, as the converter writes Brita's: its spec, and a mesh of ours scrambled under its id. */
    private static void standIn(Minecraft mc, String id, String name, String anchor, String anchorMesh, String mesh, String calibre, String stats,
                               String ammo) {
        try (InputStream in = mc.getResourceManager().open(ZomboidCraft.id("meshes/" + mesh + ".zmesh"))) {
            byte[] data = in.readAllBytes();
            CarAssets.unscramble(data, mesh);                       // the stream is xor: once off with our id,
            CarAssets.unscramble(data, MeshGuns.LOCAL + id);        // once on with the local one
            Files.createDirectories(LocalGuns.DIR);
            Files.write(LocalGuns.DIR.resolve(id + ".zmesh"), data);
            Files.writeString(LocalGuns.DIR.resolve(id + ".json"), "{\"id\": \"" + id + "\", \"name\": \"" + name
                    + "\", \"anchor\": \"" + anchor + "\", \"anchorMesh\": \"" + anchorMesh + "\", \"calibre\": \"" + calibre
                    + "\", " + (ammo == null ? "" : "\"ammo\": \"" + ammo + "\", ") + "\"stats\": " + stats + "}",
                    StandardCharsets.UTF_8);
        } catch (java.io.IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void show(ClientGameTestContext ctx, TestSingleplayerContext sp, String id, String anchor) {
        sp.getServer().runCommand("clear @a");
        sp.getServer().runCommand("kill @e[type=zombie]");
        sp.getServer().runCommand("execute as @a run zc localgun " + id);   // the console is no player
        ctx.runOnClient(mc -> mc.player.getInventory().setSelectedSlot(0));
        sp.getServer().runCommand("tp @a 0 100 -6 0 0");
        ctx.waitTicks(20);
        String[] name = {"?"};
        int[] rounds = {0};
        sp.getServer().runOnServer(server -> {
            ServerPlayer p = server.getPlayerList().getPlayers().getFirst();
            name[0] = p.getMainHandItem().getHoverName().getString();
            GunState st = p.getMainHandItem().get(GunState.TYPE);
            rounds[0] = st == null ? -1 : st.rounds();
        });
        LOG.info("ZCLOCAL gave {} on our {}: named {}, {} rounds loaded", id, anchor, name[0], rounds[0]);
        sp.getServer().runCommand("title @a actionbar {\"text\":\"" + name[0] + "  |  rides on our " + anchor + "\"}");
        shots(ctx, name[0] + " | first person, hip", 10);
        ctx.getInput().holdKey(options -> options.keyUse);
        shots(ctx, name[0] + " | aimed", 14);
        ctx.getInput().releaseKey(options -> options.keyUse);
        ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
        shots(ctx, name[0] + " | third person, front", 10);
        ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));

        // its own numbers: fired at a zombie four blocks off, health after each shot
        sp.getServer().runCommand("summon zombie 0 100 0 {NoAI:1b,Rotation:[180f,0f]}");
        sp.getServer().runCommand("tp @a 0 100 -4 0 12");
        ctx.waitTicks(20);
        for (int i = 1; i <= 3; i++) {
            sp.getServer().runOnServer(server -> Guns.fire(server.getPlayerList().getPlayers().getFirst(), false));
            ctx.waitTicks(12);
            float hp = health(sp);
            LOG.info("ZCLOCAL {} shot {}: zombie health {}", id, i, hp);
            shots(ctx, name[0] + " | shot " + i + " | zombie health " + String.format(java.util.Locale.ROOT, "%.1f", hp), 6);
            if (hp <= 0) {
                break;
            }
        }
        // what /zc localgun handed over beside it: its own magazines and rounds; then a reload from its own magazine
        sp.getServer().runOnServer(server -> {
            ServerPlayer p = server.getPlayerList().getPlayers().getFirst();
            for (int i = 0; i < 9; i++) {
                var st = p.getInventory().getItem(i);
                if (!st.isEmpty()) {
                    LOG.info("ZCLOCAL {} slot {}: {} x{} {}", id, i, st.getHoverName().getString(), st.getCount(),
                            st.get(GunState.MAG_ROUNDS) == null ? "" : "(" + st.get(GunState.MAG_ROUNDS) + " rounds)");
                }
            }
            Guns.reload(p);
        });
        shots(ctx, name[0] + " | reloading from its own magazine", 24);
        sp.getServer().runOnServer(server -> {
            var st = server.getPlayerList().getPlayers().getFirst().getMainHandItem().get(GunState.TYPE);
            LOG.info("ZCLOCAL {} after the reload: {}", id, st);
        });
    }

    /** Every ammo item, one held at a time with the hotbar showing them, then lying on the ground. */
    private static void rounds(ClientGameTestContext ctx, TestSingleplayerContext sp) {
        String[] items = {"pistol_ammo", "pocket_ammo", "revolver_ammo", "magnum_ammo", "acp45_ammo", "fn57_ammo",
                "colt45_ammo", "sw500_ammo", "rimfire_ammo", "rifle_ammo", "hunting_rifle_ammo", "lever_ammo", "heavy_ammo",
                "shotgun_shell", "grenade_round", "pg7v_rocket"};
        sp.getServer().runCommand("kill @e[type=zombie]");
        sp.getServer().runCommand("tp @a 0 100 -3 0 20");
        for (int page = 0; page < items.length; page += 8) {
            sp.getServer().runCommand("clear @a");
            for (int i = page; i < Math.min(items.length, page + 8); i++) {
                sp.getServer().runCommand("give @a zomboidcraft:" + items[i] + " 12");
            }
            for (int i = 0; i < 8 && page + i < items.length; i++) {
                int slot = i;
                ctx.runOnClient(mc -> mc.player.getInventory().setSelectedSlot(slot));
                shots(ctx, "rounds | " + items[page + i] + " in hand", 4);
            }
        }
        sp.getServer().runCommand("clear @a");
        sp.getServer().runCommand("kill @e[type=item]");
        for (int i = 0; i < items.length; i++) {
            sp.getServer().runCommand(String.format(java.util.Locale.ROOT,
                    "summon item %.2f 100.1 %.2f {Item:{id:\"zomboidcraft:%s\",count:1},PickupDelay:32767,Age:-32768}",
                    -1.6 + (i % 4) * 1.05, -0.6 + (i / 4) * 0.9, items[i]));
        }
        ctx.waitTicks(10);
        sp.getServer().runOnServer(server -> {
            var all = server.overworld().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, new AABB(-4, 98, -4, 4, 104, 4));
            LOG.info("ZCLOCAL rounds on the ground: {}", all.size());
            all.forEach(e -> LOG.info("ZCLOCAL  {} at {}", e.getItem().getHoverName().getString(), e.position()));
        });
        sp.getServer().runCommand("kill @e[type=zomboidcraft:corpse]");
        sp.getServer().runCommand("tp @a 0.2 102.0 -1.4 0 62");
        shots(ctx, "rounds | on the ground: 9mm .380 .38 .44 / .45ACP 5.7 .45Colt .500 / .22 5.56 .308 .30-30 / .50 12g 40mm rocket", 12);
    }

    private static void friend(ClientGameTestContext ctx, TestSingleplayerContext sp, String id, String anchor) {
        sp.getServer().runCommand("clear @a");
        sp.getServer().runCommand("kill @e[type=zombie]");
        sp.getServer().runOnServer(server -> {
            ServerPlayer p = server.getPlayerList().getPlayers().getFirst();
            var gun = new net.minecraft.world.item.ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM
                    .getValue(ZomboidCraft.id(anchor)));
            gun.set(LocalGuns.ID, id);
            gun.set(GunState.TYPE, new GunState(true, 5, false));
            p.getInventory().add(gun);
        });
        ctx.runOnClient(mc -> mc.player.getInventory().setSelectedSlot(0));
        sp.getServer().runCommand("tp @a 0 100 -6 0 0");
        ctx.waitTicks(20);
        String[] name = {"?"};
        sp.getServer().runOnServer(server -> name[0] = server.getPlayerList().getPlayers().getFirst().getMainHandItem()
                .getHoverName().getString());
        LOG.info("ZCLOCAL without the files, {} is {}", id, name[0]);
        sp.getServer().runCommand("title @a actionbar {\"text\":\"No files here: " + name[0] + "\"}");
        shots(ctx, "without the files | " + name[0] + " | first person", 10);
        ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
        shots(ctx, "without the files | " + name[0] + " | third person, front", 10);
        ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
    }

    private static float health(TestSingleplayerContext sp) {
        float[] out = {0};
        sp.getServer().runOnServer(server -> {
            for (Zombie z : server.overworld().getEntitiesOfClass(Zombie.class, new AABB(-3, 98, -3, 3, 104, 3), LivingEntity::isAlive)) {
                out[0] = z.getHealth();
            }
        });
        return out[0];
    }

    private static void shots(ClientGameTestContext ctx, String caption, int frames) {
        for (int i = 0; i < frames; i++) {
            ctx.waitTicks(2);
            String name = String.format("local-%04d", frame++);
            ctx.takeScreenshot(name);
            LOG.info("ZCLOCAL cap {} {}", name, caption);
        }
    }
}
