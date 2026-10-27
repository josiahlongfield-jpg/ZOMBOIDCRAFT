package com.zomboidcraft.world.gen;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.zomboidcraft.ZomboidCraft;
import com.zomboidcraft.world.county.County;
import com.zomboidcraft.world.county.Track;
import com.zomboidcraft.world.trains.Line;
import com.zomboidcraft.world.trains.Trains;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

/**
 * The county's railway and subway handed to the trains: when a Grayhaven County world starts, each
 * line the county laid in the ground is laid for the trains too (the same points, so they run on the
 * track the chunks were built with), with its stops and level crossings. /zc station [name]
 * teleports to a stop's platform.
 */
public final class CountyRails {
    public static void register() {
        // after Trains has loaded the world's saved lines, so an unchanged line keeps its trains
        ServerLifecycleEvents.SERVER_STARTED.register(CountyRails::lay);
        CommandRegistrationCallback.EVENT.register((dispatcher, registry, env) -> dispatcher.register(
                Commands.literal("zc")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.literal("station")
                                .executes(ctx -> station(ctx, null))
                                .then(Commands.argument("name", StringArgumentType.word())
                                        .suggests((ctx, b) -> SharedSuggestionProvider.suggest(names(county(ctx.getSource().getLevel())), b))
                                        .executes(ctx -> station(ctx, StringArgumentType.getString(ctx, "name")))))));
    }

    private static County county(ServerLevel level) {
        return level.getChunkSource().getGenerator() instanceof CountyChunkGenerator
                ? CountyChunkGenerator.gen(level.getSeed()).county() : null;
    }

    private static void lay(MinecraftServer server) {
        ServerLevel level = server.overworld();
        County county = county(level);
        if (county == null || !county.planned()) {
            return;
        }
        long t0 = System.currentTimeMillis();
        for (Track t : county.tracks()) {
            List<Trains.StopSpec> stops = new ArrayList<>();
            for (Track.Stop st : t.stops) {
                double[] p = t.stopPoint(st);
                stops.add(new Trains.StopSpec(st.name(), p[0], p[1]));
            }
            String id = "grayhaven_" + t.id;
            Line.Kind kind = t.kind == Track.Kind.SUBWAY ? Line.Kind.SUBWAY : Line.Kind.RAIL;
            Trains.lineExact(level, id, kind, t.name, t.points, stops);
            Trains.crossings(id, t.crossings);
        }
        ZomboidCraft.LOGGER.info("County lines laid for the trains in {} ms", System.currentTimeMillis() - t0);
    }

    /** A stop's command name: its plan key without the "st-" ("union", "uptown"), subway stops after "subway_". */
    private static String name(Track t, Track.Stop st) {
        String k = st.spot().startsWith("st-") ? st.spot().substring(3) : st.spot();
        return (t.kind == Track.Kind.SUBWAY ? "subway_" : "") + k.toLowerCase(Locale.ROOT).replace('-', '_');
    }

    private static List<String> names(County county) {
        List<String> out = new ArrayList<>();
        if (county != null) {
            for (Track t : county.tracks()) {
                for (Track.Stop st : t.stops) {
                    out.add(name(t, st));
                }
            }
        }
        return out;
    }

    /** Teleports to the platform of the named stop, or the nearest one. */
    private static int station(CommandContext<CommandSourceStack> ctx, String want) {
        CommandSourceStack src = ctx.getSource();
        County county = county(src.getLevel());
        if (county == null || county.tracks().isEmpty()) {
            src.sendFailure(Component.literal("This world has no railway (only a Grayhaven County world has one)."));
            return 0;
        }
        if (src.getEntity() == null) {
            src.sendFailure(Component.literal("Run /zc station as a player: it takes you to the platform."));
            return 0;
        }
        Track best = null;
        Track.Stop stop = null;
        double bestD = Double.MAX_VALUE;
        for (Track t : county.tracks()) {
            for (Track.Stop st : t.stops) {
                double[] p = t.stopPoint(st);
                double d = want != null ? (name(t, st).equals(want) ? 0 : Double.MAX_VALUE)
                        : src.getPosition().distanceToSqr(p[0], src.getPosition().y, p[1]);
                if (d < bestD) {
                    bestD = d;
                    best = t;
                    stop = st;
                }
            }
        }
        if (stop == null) {
            src.sendFailure(Component.literal("No stop called " + want + ". Try one of: " + String.join(", ", names(county))));
            return 0;
        }
        double[] p = best.stopPoint(stop);
        // the platform's top is a block above the bed's
        double y = best.bedTop(stop) + 1.05;
        src.getServer().getCommands().performPrefixedCommand(src, String.format(Locale.ROOT, "tp @s %.1f %.2f %.1f", p[0], y, p[1]));
        String said = stop.name() + (best.kind == Track.Kind.SUBWAY ? " (subway)" : "");
        src.sendSuccess(() -> Component.literal("On the platform at " + said), false);
        return 1;
    }

    private CountyRails() {
    }
}
