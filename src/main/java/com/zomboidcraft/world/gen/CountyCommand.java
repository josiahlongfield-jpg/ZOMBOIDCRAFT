package com.zomboidcraft.world.gen;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.zomboidcraft.world.county.Bunkers;
import com.zomboidcraft.world.county.County;
import com.zomboidcraft.world.county.CountyGen;
import com.zomboidcraft.world.county.Place;
import com.zomboidcraft.world.county.PlaceType;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

/**
 * /zc places [type]: the nearest towns, farms, ports and bases with their variants and where
 * they are. /zc goto type: teleports to the nearest one (for trying the world out). /zc bunker
 * [tower|ship|shelter|forest] [door]: teleports to the way into the nearest bunker (of that kind),
 * or with "door" to its blast door. /zc bunkermap: gives you a marked map.
 */
public final class CountyCommand {
    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registry, env) -> dispatcher.register(
                Commands.literal("zc")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.literal("places")
                                .executes(ctx -> places(ctx, null))
                                .then(Commands.argument("type", StringArgumentType.word())
                                        .suggests((ctx, b) -> SharedSuggestionProvider.suggest(types(), b))
                                        .executes(ctx -> places(ctx, StringArgumentType.getString(ctx, "type")))))
                        .then(Commands.literal("goto")
                                .then(Commands.argument("type", StringArgumentType.word())
                                        .suggests((ctx, b) -> SharedSuggestionProvider.suggest(gotoNames(ctx.getSource()), b))
                                        .executes(ctx -> go(ctx, StringArgumentType.getString(ctx, "type")))))
                        .then(Commands.literal("bunker")
                                .executes(ctx -> bunker(ctx, null, false))
                                .then(Commands.literal("door").executes(ctx -> bunker(ctx, null, true)))
                                .then(Commands.argument("kind", StringArgumentType.word())
                                        .suggests((ctx, b) -> SharedSuggestionProvider.suggest(Bunkers.KINDS, b))
                                        .executes(ctx -> bunker(ctx, StringArgumentType.getString(ctx, "kind"), false))
                                        .then(Commands.literal("door")
                                                .executes(ctx -> bunker(ctx, StringArgumentType.getString(ctx, "kind"), true)))))
                        .then(Commands.literal("bunkermap").executes(CountyCommand::bunkerMap))));
    }

    private static List<String> types() {
        return Arrays.stream(PlaceType.values()).map(t -> t.name().toLowerCase()).toList();
    }

    /** What /zc goto takes: the place kinds, then every named spot of the planned county (stadium, mercy, airport, lab, ...). */
    private static List<String> gotoNames(CommandSourceStack src) {
        List<String> out = new java.util.ArrayList<>(types());
        if (src.getLevel().getChunkSource().getGenerator() instanceof CountyChunkGenerator) {
            out.addAll(CountyChunkGenerator.gen(src.getLevel().getSeed()).county().names());
        }
        return out;
    }

    /** A place's variant without the "@x,z" the tiled sites (airport, shipyard, Ravenrock, ...) carry. */
    private static String bare(String variant) {
        int at = variant.indexOf('@');
        return at < 0 ? variant : variant.substring(0, at);
    }

    private static County county(CommandSourceStack src) {
        ServerLevel level = src.getLevel();
        if (level.getChunkSource().getGenerator() instanceof CountyChunkGenerator) {
            return CountyChunkGenerator.gen(level.getSeed()).county();
        }
        src.sendFailure(Component.literal("This world isn't a ZomboidCraft County world (pick that world type when creating it)."));
        return null;
    }

    private static List<Place> nearest(County county, Vec3 at, String type, int radius) {
        int x = (int) at.x, z = (int) at.z;
        List<Place> all = new java.util.ArrayList<>(county.placesNear(x - radius, z - radius, x + radius, z + radius, 0));
        all.addAll(county.hideoutsNear(x - radius, z - radius, x + radius, z + radius, 0));
        return all.stream()
                .filter(p -> type == null || p.type.name().equalsIgnoreCase(type) || bare(p.variant).equalsIgnoreCase(type))
                .sorted(Comparator.comparingDouble(p -> Math.hypot(p.centreX() - at.x, p.centreZ() - at.z)))
                .toList();
    }

    private static int places(CommandContext<CommandSourceStack> ctx, String type) {
        CommandSourceStack src = ctx.getSource();
        County county = county(src);
        if (county == null) {
            return 0;
        }
        Vec3 at = src.getPosition();
        List<Place> list = nearest(county, at, type, 3000);
        if (list.isEmpty()) {
            src.sendSuccess(() -> Component.literal("Nothing like that within 3000 blocks."), false);
            return 0;
        }
        for (Place p : list.subList(0, Math.min(12, list.size()))) {
            double dx = p.centreX() - at.x, dz = p.centreZ() - at.z;
            String dir = compass(dx, dz);
            src.sendSuccess(() -> Component.literal(String.format("%s (%s): %d blocks %s, at %d %d %d",
                    p.type.name().toLowerCase(), p.variant.replace('_', ' '), (int) Math.hypot(dx, dz), dir,
                    p.centreX(), p.level + 1, p.centreZ())), false);
        }
        return list.size();
    }

    /** How far /zc goto looks, widening in turn (the widest takes a few seconds). */
    private static final int[] GO_REACH = {6000, 24000, 64000};

    private static int go(CommandContext<CommandSourceStack> ctx, String type) {
        CommandSourceStack src = ctx.getSource();
        County county = county(src);
        if (county == null || src.getEntity() == null) {
            return 0;
        }
        // a named spot of the plan (stadium, mercy, airport, lab, kingsbridge, ...) goes straight there
        Place spot = county.named(type.toLowerCase(java.util.Locale.ROOT));
        List<Place> list = spot != null ? List.of(spot) : List.of();
        // rare kinds (a naval wharf, one port in eight) can be far off: look wider until one turns up
        for (int r : GO_REACH) {
            if (!list.isEmpty()) {
                break;
            }
            list = nearest(county, src.getPosition(), type, r);
        }
        if (list.isEmpty()) {
            List<String> names = county.names();
            src.sendFailure(Component.literal("No " + type + " within " + GO_REACH[GO_REACH.length - 1] + " blocks."
                    + (names.isEmpty() ? "" : " Named places: " + String.join(", ", names))));
            return 0;
        }
        Place p = list.get(0);
        if (p.type == PlaceType.HIDEOUT) {
            // nothing is levelled round a hideout: stand by the hatch, the only ground it knows
            src.getServer().getCommands().performPrefixedCommand(src, "tp @s " + p.centreX() + ".5 " + (p.level + 2) + " " + (p.centreZ() + 3) + ".5");
            src.sendSuccess(() -> Component.literal("By the hatch of " + p), false);
            return 1;
        }
        int[] gate = p.gate(2);
        src.getServer().getCommands().performPrefixedCommand(src, "tp @s " + gate[0] + " " + (p.level + 3) + " " + (gate[1] + 6));
        src.sendSuccess(() -> Component.literal("At the south gate of " + p), false);
        return 1;
    }

    /** Teleports to the way into the nearest bunker of a kind (null: any), or to its blast door. */
    private static int bunker(CommandContext<CommandSourceStack> ctx, String kind, boolean door) {
        CommandSourceStack src = ctx.getSource();
        County county = county(src);
        if (county == null || src.getEntity() == null) {
            return 0;
        }
        if (kind != null && !Bunkers.KINDS.contains(kind)) {
            src.sendFailure(Component.literal("Bunker kinds: " + String.join(", ", Bunkers.KINDS)));
            return 0;
        }
        Vec3 from = src.getPosition();
        CountyGen gen = CountyChunkGenerator.gen(src.getLevel().getSeed());
        for (Place p : Bunkers.candidates(county, (int) from.x, (int) from.z, kind, 8000)) {
            for (Bunkers.Found b : Bunkers.in(gen, p, kind)) {
                int x = door ? b.doorX() : b.x(), y = door ? b.doorY() : b.y(), z = door ? b.doorZ() : b.z();
                src.getServer().getCommands().performPrefixedCommand(src, "tp @s " + x + ".5 " + y + " " + z + ".5");
                String where = door ? "at its blast door" : switch (b.kind()) {
                    case "ship" -> "at the false plate in the engine room's forward bulkhead";
                    case "shelter" -> "at the top of the stairs down to the boiler room";
                    case "forest" -> "on the hatch under the leaves";
                    default -> "on the hatch in the electrical room";
                };
                String in = switch (b.kind()) {
                    case "ship" -> "in the ship at ";
                    case "forest" -> "in the woods at ";
                    default -> "under ";
                };
                src.sendSuccess(() -> Component.literal("The " + b.kind() + " bunker " + in + p + " (" + b.state() + "): " + where), false);
                return 1;
            }
        }
        src.sendFailure(Component.literal(kind == null ? "No bunker near here." : "No " + kind + " bunker near here."));
        return 0;
    }

    /** A marked map for trying them out: it picks its bunker when it's first read. */
    private static int bunkerMap(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        if (!(src.getEntity() instanceof net.minecraft.server.level.ServerPlayer player)) {
            return 0;
        }
        player.getInventory().add(new net.minecraft.world.item.ItemStack(com.zomboidcraft.ModItems.BUNKER_MAP));
        src.sendSuccess(() -> Component.literal("A marked map. Read it to see where it leads."), false);
        return 1;
    }

    private static String compass(double dx, double dz) {
        String[] names = {"east", "south-east", "south", "south-west", "west", "north-west", "north", "north-east"};
        int i = (int) Math.round(Math.atan2(dz, dx) / (Math.PI / 4));
        return names[Math.floorMod(i, 8)];
    }

    private CountyCommand() {
    }
}
