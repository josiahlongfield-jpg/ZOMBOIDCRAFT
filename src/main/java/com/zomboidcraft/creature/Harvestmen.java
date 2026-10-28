package com.zomboidcraft.creature;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.zomboidcraft.ZomboidCraft;
import java.util.Arrays;
import java.util.List;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
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
 * The Harvestman's entity type, and its test command: {@code /zc harvestman} puts one a few blocks in front of you,
 * {@code /zc harvestman clear} takes them all away, {@code /zc harvestman do <plan>} makes the nearest take up a plan
 * (wander, perch, ceiling, hide, stalk) and {@code /zc harvestman info} says what each one is doing.
 */
public final class Harvestmen {
    private static final ResourceKey<EntityType<?>> KEY = ResourceKey.create(Registries.ENTITY_TYPE, ZomboidCraft.id("harvestman"));
    public static final EntityType<HarvestmanEntity> HARVESTMAN = Registry.register(BuiltInRegistries.ENTITY_TYPE, KEY,
            EntityType.Builder.<HarvestmanEntity>of(HarvestmanEntity::new, MobCategory.MISC)
                    .sized(1.2f, 0.8f).clientTrackingRange(10).updateInterval(1).fireImmune().noLootTable()
                    .build(KEY));

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
                Commands.literal("zc")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.literal("harvestman")
                                .executes(Harvestmen::spawn)
                                .then(Commands.literal("clear").executes(Harvestmen::clear))
                                .then(Commands.literal("info").executes(Harvestmen::info))
                                .then(Commands.literal("do")
                                        .then(Commands.argument("plan", StringArgumentType.word())
                                                .suggests((ctx, b) -> SharedSuggestionProvider.suggest(
                                                        Arrays.asList(HarvestmanEntity.PLANS).subList(1, HarvestmanEntity.PLANS.length), b))
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
        h.snapTo(floor.x, floor.y + 1.2, floor.z, player.getYRot() + 180f, 0f);
        level.addFreshEntity(h);
        ctx.getSource().sendSuccess(() -> Component.literal("A Harvestman is in front of you. /zc harvestman do <plan> to steer it, /zc harvestman clear to remove it"), false);
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
        all.forEach(Entity::discard);
        ctx.getSource().sendSuccess(() -> Component.literal("Removed " + all.size() + " Harvestm" + (all.size() == 1 ? "an" : "en")), false);
        return all.size();
    }

    private static int info(CommandContext<CommandSourceStack> ctx) {
        List<HarvestmanEntity> all = all(ctx);
        Vec3 from = ctx.getSource().getPosition();
        StringBuilder b = new StringBuilder(all.size() + " in this world");
        for (HarvestmanEntity h : all) {
            b.append(String.format("\n%.0f blocks away at %d %d %d: %s", h.position().distanceTo(from),
                    h.getBlockX(), h.getBlockY(), h.getBlockZ(), h.planName()));
        }
        ctx.getSource().sendSuccess(() -> Component.literal(b.toString()), false);
        return all.size();
    }

    private static int force(CommandContext<CommandSourceStack> ctx, String name) {
        int plan = Arrays.asList(HarvestmanEntity.PLANS).indexOf(name);
        if (plan < 1) {
            ctx.getSource().sendFailure(Component.literal("Plans: wander, perch, ceiling, hide, stalk"));
            return 0;
        }
        Vec3 from = ctx.getSource().getPosition();
        HarvestmanEntity near = null;
        for (HarvestmanEntity h : all(ctx)) {
            if (near == null || h.position().distanceToSqr(from) < near.position().distanceToSqr(from)) {
                near = h;
            }
        }
        if (near == null) {
            ctx.getSource().sendFailure(Component.literal("No Harvestman here (/zc harvestman makes one)"));
            return 0;
        }
        near.force(plan);
        ctx.getSource().sendSuccess(() -> Component.literal("The nearest Harvestman will " + name + " next"), false);
        return 1;
    }

    private Harvestmen() {
    }
}
