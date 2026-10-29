package com.zomboidcraft.npc;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.zomboidcraft.ZomboidCraft;
import java.util.List;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Characters from bought models, companions with a brain of their own (NpcBrain), spawned by command:
 * {@code /zc npc <model> [raw|toon|both]} puts one (or one of each look, side by side) a few blocks ahead, facing you;
 * {@code /zc npc clear} removes them all. A model from a player's local folder ({@link #localFolder}) can be spawned by name
 * on a server too; players without the file just don't see it.
 * <p>
 * For the companion you're looking at (or the nearest): {@code /zc npc wear <piece>} and {@code /zc npc remove <piece>} put
 * her clothes on and take them off ({@link #PIECES}; her underwear always stays on), {@code /zc npc outfit} says what
 * she has on, and {@code /zc npc name <name>} names her (her chat lines go out under it).
 */
public final class Npcs {
    /** Every model with a file in assets/zomboidcraft/npc/ (tools/npc/pack.py). */
    public static final List<String> MODELS = List.of("dark_elf");

    /**
     * Models kept off the repo and out of the jar (characters someone else owns) live on the player's own PC in this
     * folder; only players who have the file there see them.
     */
    public static java.nio.file.Path localFolder() {
        return net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("zomboidcraft").resolve("npc");
    }

    /** The shipped models, then any in this PC's local folder. */
    static List<String> known() {
        List<String> out = new java.util.ArrayList<>(MODELS);
        try (var files = java.nio.file.Files.list(localFolder())) {
            files.map(f -> f.getFileName().toString()).filter(n -> n.endsWith(".znpc") || n.endsWith(".zsk"))
                    .map(n -> n.substring(0, n.lastIndexOf('.'))).filter(n -> !out.contains(n)).distinct().sorted().forEach(out::add);
        } catch (java.io.IOException | RuntimeException e) {
            // no local folder: just the shipped models
        }
        return out;
    }
    public static final List<String> LOOKS = List.of("raw", "toon", "both");
    /** Clothes a skinned companion can put on and take off (the pieces in her .zsk). */
    public static final List<String> PIECES = List.of("bra", "stockings", "heels", "hair");

    private static final ResourceKey<EntityType<?>> KEY = ResourceKey.create(Registries.ENTITY_TYPE, ZomboidCraft.id("npc"));
    public static final EntityType<NpcEntity> NPC = Registry.register(BuiltInRegistries.ENTITY_TYPE, KEY,
            EntityType.Builder.<NpcEntity>of(NpcEntity::new, MobCategory.MISC)
                    .sized(0.6f, 1.85f).eyeHeight(1.7f).clientTrackingRange(10).noLootTable()
                    .build(KEY));

    public static void register() {
        FabricDefaultAttributeRegistry.register(NPC, NpcEntity.attributes());
        NpcNet.register();
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
                Commands.literal("zc")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.literal("npc")
                                .then(Commands.literal("clear").executes(Npcs::clear))
                                .then(Commands.literal("wear").then(Commands.argument("piece", StringArgumentType.word())
                                        .suggests((ctx, b) -> SharedSuggestionProvider.suggest(PIECES, b))
                                        .executes(ctx -> dress(ctx, StringArgumentType.getString(ctx, "piece"), true))))
                                .then(Commands.literal("remove").then(Commands.argument("piece", StringArgumentType.word())
                                        .suggests((ctx, b) -> SharedSuggestionProvider.suggest(PIECES, b))
                                        .executes(ctx -> dress(ctx, StringArgumentType.getString(ctx, "piece"), false))))
                                .then(Commands.literal("outfit").executes(Npcs::outfit))
                                .then(Commands.literal("name").then(Commands.argument("name", StringArgumentType.greedyString())
                                        .executes(ctx -> name(ctx, StringArgumentType.getString(ctx, "name")))))
                                .then(Commands.argument("model", StringArgumentType.word())
                                        .suggests((ctx, b) -> SharedSuggestionProvider.suggest(known(), b))
                                        .executes(ctx -> spawn(ctx, StringArgumentType.getString(ctx, "model"), "both"))
                                        .then(Commands.argument("look", StringArgumentType.word())
                                                .suggests((ctx, b) -> SharedSuggestionProvider.suggest(LOOKS, b))
                                                .executes(ctx -> spawn(ctx, StringArgumentType.getString(ctx, "model"),
                                                        StringArgumentType.getString(ctx, "look"))))))));
    }

    private static int spawn(CommandContext<CommandSourceStack> ctx, String model, String look) throws CommandSyntaxException {
        if (!model.matches("[a-z0-9_]+")) {
            ctx.getSource().sendFailure(Component.literal("No NPC model called " + model + ". Models: " + String.join(", ", known())));
            return 0;
        }
        if (!LOOKS.contains(look)) {
            ctx.getSource().sendFailure(Component.literal("Look must be raw, toon or both"));
            return 0;
        }
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ServerLevel level = (ServerLevel) player.level();
        Vec3 at = ahead(player);
        Vec3 side = new Vec3(Math.cos(Math.toRadians(player.getYRot())), 0, Math.sin(Math.toRadians(player.getYRot())));
        // a skinned model (.zsk) has one look, as bought
        boolean skinned = java.nio.file.Files.isRegularFile(localFolder().resolve(model + ".zsk"));
        List<String> looks = skinned ? List.of("raw") : look.equals("both") ? List.of("raw", "toon") : List.of(look);
        int made = 0;
        for (int i = 0; i < looks.size(); i++) {
            NpcEntity npc = NPC.create(level, EntitySpawnReason.COMMAND);
            if (npc == null) {
                continue;
            }
            // with both, the raw one stands on your left and the toon one on your right
            double off = looks.size() == 1 ? 0 : (i == 0 ? -0.9 : 0.9);
            Vec3 p = at.add(side.scale(-off));
            float face = player.getYRot() + 180f;
            npc.setModel(model, looks.get(i));
            npc.snapTo(p.x, p.y, p.z, face, 0f);
            npc.setYHeadRot(face);
            npc.setYBodyRot(face);
            npc.toggleFollow(player);
            level.addFreshEntity(npc);
            made++;
        }
        int count = made;
        ctx.getSource().sendSuccess(() -> Component.literal(count == 2
                ? "Spawned " + model + ": as bought on your left, toon-painted on your right. They follow you; interact with one "
                + "to make her wait. /zc npc clear removes them"
                : "Spawned " + model + " (" + look + "). She follows you; interact with her to make her wait. /zc npc clear removes them"), false);
        return count;
    }

    private static int clear(CommandContext<CommandSourceStack> ctx) {
        int n = 0;
        for (Entity e : ctx.getSource().getLevel().getAllEntities()) {
            if (e instanceof NpcEntity) {
                e.discard();
                n++;
            }
        }
        int count = n;
        ctx.getSource().sendSuccess(() -> Component.literal("Removed " + count + " NPC" + (count == 1 ? "" : "s")), false);
        return count;
    }

    private static int dress(CommandContext<CommandSourceStack> ctx, String piece, boolean on) throws CommandSyntaxException {
        if (!PIECES.contains(piece)) {
            ctx.getSource().sendFailure(Component.literal("Pieces: " + String.join(", ", PIECES) + " (her underwear always stays on)"));
            return 0;
        }
        NpcEntity npc = target(ctx.getSource().getPlayerOrException());
        if (npc == null) {
            ctx.getSource().sendFailure(Component.literal("No companion near you"));
            return 0;
        }
        java.util.Set<String> hidden = new java.util.HashSet<>(npc.hidden());
        if (on ? !hidden.remove(piece) : !hidden.add(piece)) {
            ctx.getSource().sendSuccess(() -> Component.literal(on ? "She already has her " + piece + " on" : "Her " + piece + " is already off"), false);
            return 0;
        }
        npc.setHidden(hidden);
        ctx.getSource().sendSuccess(() -> Component.literal((on ? "Put on: " : "Took off: ") + piece), false);
        return 1;
    }

    private static int outfit(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        NpcEntity npc = target(ctx.getSource().getPlayerOrException());
        if (npc == null) {
            ctx.getSource().sendFailure(Component.literal("No companion near you"));
            return 0;
        }
        List<String> on = PIECES.stream().filter(p -> !npc.hidden().contains(p)).toList();
        List<String> off = PIECES.stream().filter(p -> npc.hidden().contains(p)).toList();
        ctx.getSource().sendSuccess(() -> Component.literal("Wearing: underwear" + (on.isEmpty() ? "" : ", " + String.join(", ", on))
                + (off.isEmpty() ? "" : ". Off: " + String.join(", ", off))), false);
        return 1;
    }

    private static int name(CommandContext<CommandSourceStack> ctx, String name) throws CommandSyntaxException {
        NpcEntity npc = target(ctx.getSource().getPlayerOrException());
        if (npc == null) {
            ctx.getSource().sendFailure(Component.literal("No companion near you"));
            return 0;
        }
        npc.setCustomName(Component.literal(name.strip()));
        npc.setCustomNameVisible(false);
        ctx.getSource().sendSuccess(() -> Component.literal("She's " + name.strip() + " now"), false);
        return 1;
    }

    /** The companion the player is looking at within eight blocks, else the nearest within sixteen. */
    private static NpcEntity target(ServerPlayer player) {
        Vec3 eye = player.getEyePosition(), look = player.getLookAngle();
        NpcEntity best = null, near = null;
        double bestDot = 0.97, nearD = 16 * 16;
        for (NpcEntity npc : player.level().getEntitiesOfClass(NpcEntity.class, player.getBoundingBox().inflate(16))) {
            if (!npc.isAlive()) {
                continue;
            }
            Vec3 to = npc.position().add(0, npc.getBbHeight() * 0.6, 0).subtract(eye);
            double d = to.length(), dot = d < 1e-3 ? 1 : to.scale(1 / d).dot(look);
            if (d < 8 && dot > bestDot) {
                bestDot = dot;
                best = npc;
            }
            if (npc.distanceToSqr(player) < nearD) {
                nearD = npc.distanceToSqr(player);
                near = npc;
            }
        }
        return best != null ? best : near;
    }

    /** The floor four blocks ahead (less if a wall is nearer). */
    private static Vec3 ahead(ServerPlayer player) {
        ServerLevel level = (ServerLevel) player.level();
        Vec3 eye = player.getEyePosition(), look = player.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0, look.z);
        flat = flat.lengthSqr() < 1e-4 ? new Vec3(0, 0, 1) : flat.normalize();
        var hit = level.clip(new ClipContext(eye, eye.add(flat.scale(4)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        Vec3 at = hit.getType() == HitResult.Type.MISS ? eye.add(flat.scale(4)) : hit.getLocation().subtract(flat.scale(1));
        var down = level.clip(new ClipContext(at, at.add(0, -12, 0), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        return down.getType() == HitResult.Type.MISS ? player.position() : down.getLocation();
    }

    private Npcs() {
    }
}
