package com.zomboidcraft.sandbox;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.Arrays;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;

/**
 * /zc sandbox: opens the Sandbox page. /zc sandbox list [group] shows the world's settings, /zc sandbox set id value
 * changes one, /zc sandbox preset name puts them all to a preset. Ops only, like the rest of /zc.
 */
final class SandboxCommand {
    static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registry, env) -> dispatcher.register(
                Commands.literal("zc")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.literal("sandbox")
                                .executes(SandboxCommand::open)
                                .then(Commands.literal("list")
                                        .executes(ctx -> list(ctx, null))
                                        .then(Commands.argument("group", StringArgumentType.word())
                                                .suggests((ctx, b) -> SharedSuggestionProvider.suggest(
                                                        Arrays.stream(SandboxGroup.values()).map(g -> g.id), b))
                                                .executes(ctx -> list(ctx, StringArgumentType.getString(ctx, "group")))))
                                .then(Commands.literal("set")
                                        .then(Commands.argument("id", StringArgumentType.word())
                                                .suggests((ctx, b) -> SharedSuggestionProvider.suggest(Sandbox.options().stream()
                                                        .filter(o -> o.kind != SandboxOption.Kind.FIXED)
                                                        .map(o -> o.id), b))
                                                .then(Commands.argument("value", StringArgumentType.greedyString())
                                                        .suggests((ctx, b) -> {
                                                            SandboxOption o = Sandbox.option(StringArgumentType.getString(ctx, "id"));
                                                            if (o != null && o.kind == SandboxOption.Kind.CHOICE) {
                                                                return SharedSuggestionProvider.suggest(o.choices, b);
                                                            }
                                                            if (o != null && o.kind == SandboxOption.Kind.TOGGLE) {
                                                                return SharedSuggestionProvider.suggest(new String[] {"true", "false"}, b);
                                                            }
                                                            return b.buildFuture();
                                                        })
                                                        .executes(ctx -> set(ctx, StringArgumentType.getString(ctx, "id"),
                                                                StringArgumentType.getString(ctx, "value"))))))
                                .then(Commands.literal("preset")
                                        .then(Commands.argument("name", StringArgumentType.word())
                                                .suggests((ctx, b) -> SharedSuggestionProvider.suggest(Sandbox.PRESETS, b))
                                                .executes(ctx -> preset(ctx, StringArgumentType.getString(ctx, "name"))))))));
    }

    private static int open(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        var player = ctx.getSource().getPlayerOrException();
        Sandbox.sync(player);
        ServerPlayNetworking.send(player, new SandboxNet.Open());
        ctx.getSource().sendSuccess(() -> Component.literal("World settings: preset " + Sandbox.preset() + ", "
                + Sandbox.options().size() + " settings in " + SandboxGroup.values().length
                + " groups. /zc sandbox list <group> shows them."), false);
        return 1;
    }

    private static int list(CommandContext<CommandSourceStack> ctx, String groupId) {
        CommandSourceStack src = ctx.getSource();
        if (groupId == null) {
            StringBuilder text = new StringBuilder("World settings (preset " + Sandbox.preset() + "):");
            for (SandboxGroup g : SandboxGroup.values()) {
                text.append(String.format("%n  %s (%s): %d", g.label, g.id, Sandbox.options(g).size()));
            }
            src.sendSuccess(() -> Component.literal(text.toString()), false);
            return 1;
        }
        SandboxGroup group = SandboxGroup.byId(groupId);
        if (group == null) {
            src.sendFailure(Component.literal("No group " + groupId));
            return 0;
        }
        StringBuilder text = new StringBuilder(group.label + ":");
        for (SandboxOption o : Sandbox.options(group)) {
            String note = o.kind == SandboxOption.Kind.FIXED ? " (fixed)" : o.coming() ? " (coming)" : o.client ? " (your screen)" : "";
            text.append(String.format("%n  %s = %s%s  [%s]", o.label, Sandbox.value(o.id), note, o.id));
        }
        src.sendSuccess(() -> Component.literal(text.toString()), false);
        return 1;
    }

    private static int set(CommandContext<CommandSourceStack> ctx, String id, String value) {
        try {
            String v = Sandbox.set(id, value);
            ctx.getSource().sendSuccess(() -> Component.literal(Sandbox.option(id).label + " set to " + v), true);
            return 1;
        } catch (IllegalArgumentException e) {
            ctx.getSource().sendFailure(Component.literal(e.getMessage()));
            return 0;
        }
    }

    private static int preset(CommandContext<CommandSourceStack> ctx, String name) {
        try {
            Sandbox.applyPreset(name);
            ctx.getSource().sendSuccess(() -> Component.literal("World settings put to the " + name + " preset"), true);
            return 1;
        } catch (IllegalArgumentException e) {
            ctx.getSource().sendFailure(Component.literal(e.getMessage()));
            return 0;
        }
    }

    private SandboxCommand() {
    }
}
