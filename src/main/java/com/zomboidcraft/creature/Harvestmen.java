package com.zomboidcraft.creature;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.zomboidcraft.ZomboidCraft;
import java.util.Arrays;
import java.util.List;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
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
 * The Harvestman's entity type, and its commands (operators): {@code /zc harvestman} puts one a few blocks in front of
 * you; {@code clear} takes them all away; {@code info} says how many there are, who is hunted and what each is doing;
 * {@code do <plan>} makes the nearest take up a plan; {@code target <player>} sets who is hunted ({@code target clear}
 * ends the hunt).
 */
public final class Harvestmen {
    private static final ResourceKey<EntityType<?>> KEY = ResourceKey.create(Registries.ENTITY_TYPE, ZomboidCraft.id("harvestman"));
    public static final EntityType<HarvestmanEntity> HARVESTMAN = Registry.register(BuiltInRegistries.ENTITY_TYPE, KEY,
            EntityType.Builder.<HarvestmanEntity>of(HarvestmanEntity::new, MobCategory.MISC)
                    .sized(1.2f, 0.8f).eyeHeight(0.4f).clientTrackingRange(10).updateInterval(1).fireImmune().noLootTable()
                    .build(KEY));

    public static void register() {
        FabricDefaultAttributeRegistry.register(HARVESTMAN, HarvestmanEntity.attributes());
        HarvestmanSounds.register();
        HarvestmanDirector.register();
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
                Commands.literal("zc")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.literal("harvestman")
                                .executes(Harvestmen::spawn)
                                .then(Commands.literal("clear").executes(Harvestmen::clear))
                                .then(Commands.literal("info").executes(Harvestmen::info))
                                .then(Commands.literal("target")
                                        .then(Commands.literal("clear").executes(ctx -> target(ctx, null)))
                                        .then(Commands.argument("player", EntityArgument.player())
                                                .executes(ctx -> target(ctx, EntityArgument.getPlayer(ctx, "player")))))
                                .then(Commands.literal("do")
                                        .then(Commands.argument("plan", StringArgumentType.word())
                                                .suggests((ctx, b) -> SharedSuggestionProvider.suggest(
                                                        Arrays.asList(HarvestmanMind.PLANS).subList(1, HarvestmanMind.PLANS.length), b))
                                                .executes(ctx -> force(ctx, StringArgumentType.getString(ctx, "plan"))))))));
    }

    private static int spawn(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ServerLevel level = (ServerLevel) player.level();
        // six blocks ahead (less if a wall is nearer), dropped onto the floor there
        Vec3 eye = player.getEyePosition(), look = player.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0, look.z);
        flat = flat.lengthSqr() < 1e-4 ? new Vec3(0, 0, 1) : flat.normalize();
        var ahead = level.clip(new ClipContext(eye, eye.add(flat.scale(6)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        Vec3 at = ahead.getType() == HitResult.Type.MISS ? eye.add(flat.scale(6)) : ahead.getLocation().subtract(flat.scale(1.5));
        var down = level.clip(new ClipContext(at, at.add(0, -12, 0), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        Vec3 floor = down.getType() == HitResult.Type.MISS ? player.position() : down.getLocation();
        HarvestmanEntity h = HARVESTMAN.create(level, EntitySpawnReason.COMMAND);
        if (h == null) {
            return 0;
        }
        h.snapTo(floor.x, floor.y + HarvestmanEntity.STAND - HarvestmanEntity.MID, floor.z, player.getYRot() + 180f, 0f);
        level.addFreshEntity(h);
        ctx.getSource().sendSuccess(() -> Component.literal("A Harvestman is in front of you; its den is here. "
                + "/zc harvestman do <plan> steers it, target <player> sets who it hunts, clear removes them all"), false);
        return 1;
    }

    private static List<HarvestmanEntity> all(CommandContext<CommandSourceStack> ctx) {
        ServerLevel level = ctx.getSource().getLevel();
        List<HarvestmanEntity> out = new java.util.ArrayList<>();
        for (Entity e : level.getAllEntities()) {
            if (e instanceof HarvestmanEntity h) {
                out.add(h);
            }
        }
        return out;
    }

    private static int clear(CommandContext<CommandSourceStack> ctx) {
        List<HarvestmanEntity> all = all(ctx);
        HarvestmanDirector.forgetAll();
        all.forEach(Entity::discard);
        ctx.getSource().sendSuccess(() -> Component.literal("Removed " + all.size() + " Harvestm" + (all.size() == 1 ? "an" : "en")
                + " and forgot every den and hunt"), false);
        return all.size();
    }

    private static int info(CommandContext<CommandSourceStack> ctx) {
        String report = HarvestmanDirector.report(ctx.getSource().getServer());
        ctx.getSource().sendSuccess(() -> Component.literal(report), false);
        return 1;
    }

    private static int target(CommandContext<CommandSourceStack> ctx, ServerPlayer who) {
        String said = HarvestmanDirector.setTarget(ctx.getSource().getServer(), who);
        ctx.getSource().sendSuccess(() -> Component.literal(said), false);
        return 1;
    }

    private static int force(CommandContext<CommandSourceStack> ctx, String name) {
        int plan = Arrays.asList(HarvestmanMind.PLANS).indexOf(name);
        if (plan < 1) {
            ctx.getSource().sendFailure(Component.literal("Plans: " + String.join(", ", Arrays.asList(HarvestmanMind.PLANS).subList(1, HarvestmanMind.PLANS.length))));
            return 0;
        }
        Vec3 from = ctx.getSource().getPosition();
        HarvestmanEntity near = null;
        for (HarvestmanEntity h : all(ctx)) {
            if (h.isAlive() && (near == null || h.position().distanceToSqr(from) < near.position().distanceToSqr(from))) {
                near = h;
            }
        }
        if (near == null) {
            ctx.getSource().sendFailure(Component.literal("No Harvestman here (/zc harvestman makes one)"));
            return 0;
        }
        String why = near.mind.force(ctx.getSource().getLevel(), plan);
        if (why != null) {
            ctx.getSource().sendFailure(Component.literal("The nearest Harvestman can't " + name + ": " + why));
            return 0;
        }
        String doing = near.mind.doing();
        ctx.getSource().sendSuccess(() -> Component.literal("The nearest Harvestman is " + doing), false);
        return 1;
    }

    private Harvestmen() {
    }
}
