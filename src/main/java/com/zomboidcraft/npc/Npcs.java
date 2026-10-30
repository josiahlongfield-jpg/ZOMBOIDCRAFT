package com.zomboidcraft.npc;

import com.mojang.brigadier.arguments.FloatArgumentType;
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
 * she has on, and {@code /zc npc name <name>} names her (her chat lines go out under it). A model that comes in whole
 * outfits rather than pieces (its .zsk says which meshes go with which look) doesn't dress herself:
 * {@code /zc npc outfit <look>} puts her in one, and wear/remove take its extras (horns, harness...) on and off. {@code /zc npc mind} shows her
 * mood, traits, needs and latest memories (NpcMind); {@code /zc npc trait <trait> <-1..1>} and
 * {@code /zc npc feel <need> <0..100>} set one, for testing.
 * <p>
 * Her work (NpcWork): {@code /zc npc home} makes where you stand her home; {@code /zc npc job <job>} gives her a job there
 * (guard, repair, farm, cook, scavenge, medic, haul, none); {@code /zc npc shift <day|night>} picks her hours; make her
 * wait (G) and she gets on with it. {@code /zc npc skills} lists what she's good at; {@code /zc npc skill <skill> <0..10>}
 * sets one, for testing.
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
    public static final List<String> PIECES = List.of("bra", "stockings", "heels", "hair", "sweater", "shorts", "skirt",
            "dress", "boots");

    /** Whether this model is a skinned one in pieces she can dress in (a .zsk in the local folder or the jar), so she has a wardrobe. */
    public static boolean skinned(String model) {
        return SKINNED.computeIfAbsent(model, m -> java.nio.file.Files.exists(localFolder().resolve(m + ".zsk"))
                || Npcs.class.getResource("/assets/zomboidcraft/npc/" + m + ".zsk") != null);
    }

    private static final java.util.Map<String, Boolean> SKINNED = new java.util.concurrent.ConcurrentHashMap<>();

    /** A skinned model's pieces and whole outfits (looks), read from its .zsk's mesh list. */
    record Dress(java.util.Set<String> pieces, List<String> looks) {
        static final Dress NONE = new Dress(java.util.Set.of(), List.of());
    }

    private static final java.util.Map<String, Dress> DRESS = new java.util.concurrent.ConcurrentHashMap<>();

    /** What this model can wear: its pieces (besides "body") and its looks, if it comes in whole outfits. */
    static Dress dress(String model) {
        return DRESS.computeIfAbsent(model, m -> {
            try {
                java.nio.file.Path local = localFolder().resolve(m + ".zsk");
                byte[] data;
                if (java.nio.file.Files.isRegularFile(local)) {
                    data = java.nio.file.Files.readAllBytes(local);
                } else {
                    try (var in = Npcs.class.getResourceAsStream("/assets/zomboidcraft/npc/" + m + ".zsk")) {
                        if (in == null) return Dress.NONE;
                        data = in.readAllBytes();
                    }
                }
                unscramble(data, m);
                var in = new java.io.DataInputStream(new java.util.zip.InflaterInputStream(new java.io.ByteArrayInputStream(data)));
                in.readInt();
                in.readInt();
                java.util.Set<String> pieces = new java.util.TreeSet<>();
                List<String> looks = new java.util.ArrayList<>();
                int groups = in.readInt();
                for (int g = 0; g < groups; g++) {
                    in.readUTF();
                    String piece = in.readUTF();
                    in.readUTF();
                    in.readByte();
                    in.readByte();
                    in.skipNBytes(27L * in.readInt());
                    in.skipNBytes(12L * in.readInt());
                    int at = piece.indexOf('@');
                    if (at >= 0) {
                        for (String k : piece.substring(at + 1).split(",")) {
                            String look = k.contains("|") ? k.substring(0, k.indexOf('|')) : k;
                            if (!looks.contains(look)) looks.add(look);
                        }
                        piece = piece.substring(0, at);
                    }
                    if (!piece.equals("body")) pieces.add(piece);
                }
                return new Dress(pieces, looks);
            } catch (java.io.IOException | RuntimeException e) {
                ZomboidCraft.LOGGER.warn("Couldn't read the pieces of NPC model {}: {}", m, e.toString());
                return Dress.NONE;
            }
        });
    }

    /** Whether she dresses herself piece by piece (NpcWardrobe): a skinned model with those clothes in it. */
    public static boolean dresses(String model) {
        return skinned(model) && dress(model).pieces().stream().anyMatch(PIECES::contains);
    }

    /** Undoes pack.py's xorshift32 scramble (seeded from the model id). */
    private static void unscramble(byte[] data, String model) {
        int x = 0x3C6EF372 ^ model.hashCode();
        if (x == 0) {
            x = 1;
        }
        for (int i = 0; i < data.length; i += 4) {
            x ^= x << 13;
            x ^= x >>> 17;
            x ^= x << 5;
            for (int j = 0; j < 4 && i + j < data.length; j++) {
                data[i + j] ^= (byte) (x >>> (24 - 8 * j));
            }
        }
    }

    /** The pieces she can put on and take off: the wardrobe's, or her own model's. */
    private static List<String> pieces(NpcEntity npc) {
        return npc == null || dresses(npc.model()) ? PIECES : List.copyOf(dress(npc.model()).pieces());
    }

    private static List<String> suggestPieces(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer p = ctx.getSource().getPlayer();
        return p == null ? PIECES : pieces(target(p));
    }

    private static List<String> suggestOutfits(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer p = ctx.getSource().getPlayer();
        NpcEntity npc = p == null ? null : target(p);
        return npc != null && !dresses(npc.model()) && !dress(npc.model()).looks().isEmpty() ? dress(npc.model()).looks()
                : names(NpcWardrobe.Outfit.values());
    }

    /** Her current look, for a model in whole outfits ("look=NAME" among her pieces off; else her first). */
    static String look(NpcEntity npc) {
        List<String> looks = dress(npc.model()).looks();
        for (String h : npc.hidden()) {
            if (h.startsWith("look=") && looks.contains(h.substring(5))) return h.substring(5);
        }
        return looks.isEmpty() ? "" : looks.get(0);
    }

    private static final ResourceKey<EntityType<?>> KEY = ResourceKey.create(Registries.ENTITY_TYPE, ZomboidCraft.id("npc"));
    public static final EntityType<NpcEntity> NPC = Registry.register(BuiltInRegistries.ENTITY_TYPE, KEY,
            EntityType.Builder.<NpcEntity>of(NpcEntity::new, MobCategory.MISC)
                    .sized(0.6f, 1.85f).eyeHeight(1.7f).clientTrackingRange(10).noLootTable()
                    .build(KEY));

    public static void register() {
        FabricDefaultAttributeRegistry.register(NPC, NpcEntity.attributes());
        NpcNet.register();
        NpcPanel.register();
        net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (!(entity instanceof net.minecraft.world.entity.monster.zombie.Zombie) || !(source.getEntity() instanceof ServerPlayer killer)) {
                return;
            }
            for (NpcEntity npc : entity.level().getEntitiesOfClass(NpcEntity.class, entity.getBoundingBox().inflate(10))) {
                if (npc.isAlive() && npc.bond.about(killer)
                        && (npc.mind.get(NpcMind.Need.FEAR) > 30 || npc.distanceTo(entity) < 4)) {
                    npc.bond.saved(npc);
                }
            }
        });
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
                Commands.literal("zc")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.literal("npc")
                                .then(Commands.literal("clear").executes(Npcs::clear))
                                .then(Commands.literal("wear").then(Commands.argument("piece", StringArgumentType.word())
                                        .suggests((ctx, b) -> SharedSuggestionProvider.suggest(suggestPieces(ctx), b))
                                        .executes(ctx -> dress(ctx, StringArgumentType.getString(ctx, "piece"), true))))
                                .then(Commands.literal("remove").then(Commands.argument("piece", StringArgumentType.word())
                                        .suggests((ctx, b) -> SharedSuggestionProvider.suggest(suggestPieces(ctx), b))
                                        .executes(ctx -> dress(ctx, StringArgumentType.getString(ctx, "piece"), false))))
                                .then(Commands.literal("outfit").executes(Npcs::outfit)
                                        .then(Commands.argument("outfit", StringArgumentType.word())
                                                .suggests((ctx, b) -> SharedSuggestionProvider.suggest(suggestOutfits(ctx), b))
                                                .executes(ctx -> outfit(ctx, StringArgumentType.getString(ctx, "outfit")))))
                                .then(Commands.literal("mind").executes(Npcs::mind))
                                .then(Commands.literal("home").executes(Npcs::home))
                                .then(Commands.literal("job").then(Commands.argument("job", StringArgumentType.word())
                                        .suggests((ctx, b) -> SharedSuggestionProvider.suggest(names(NpcWork.Job.values()), b))
                                        .executes(ctx -> job(ctx, StringArgumentType.getString(ctx, "job")))))
                                .then(Commands.literal("shift").then(Commands.argument("shift", StringArgumentType.word())
                                        .suggests((ctx, b) -> SharedSuggestionProvider.suggest(List.of("day", "night"), b))
                                        .executes(ctx -> shift(ctx, StringArgumentType.getString(ctx, "shift")))))
                                .then(Commands.literal("skills").executes(Npcs::skills))
                                .then(Commands.literal("bond").executes(Npcs::bond)
                                        .then(Commands.argument("feeling", StringArgumentType.word())
                                                .suggests((ctx, b) -> SharedSuggestionProvider.suggest(List.of("trust", "affection", "attraction", "together", "apart"), b))
                                                .executes(ctx -> bond(ctx, StringArgumentType.getString(ctx, "feeling"), -1))
                                                .then(Commands.argument("value", FloatArgumentType.floatArg(0f, 100f))
                                                        .executes(ctx -> bond(ctx, StringArgumentType.getString(ctx, "feeling"),
                                                                FloatArgumentType.getFloat(ctx, "value"))))))
                                .then(Commands.literal("skill").then(Commands.argument("skill", StringArgumentType.word())
                                        .suggests((ctx, b) -> SharedSuggestionProvider.suggest(names(NpcSkills.Skill.values()), b))
                                        .then(Commands.argument("level", com.mojang.brigadier.arguments.IntegerArgumentType.integer(0, NpcSkills.MAX))
                                                .executes(ctx -> skill(ctx, StringArgumentType.getString(ctx, "skill"),
                                                        com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "level"))))))
                                .then(Commands.literal("trait").then(Commands.argument("trait", StringArgumentType.word())
                                        .suggests((ctx, b) -> SharedSuggestionProvider.suggest(names(NpcMind.Trait.values()), b))
                                        .then(Commands.argument("value", FloatArgumentType.floatArg(-1f, 1f))
                                                .executes(ctx -> trait(ctx, StringArgumentType.getString(ctx, "trait"),
                                                        FloatArgumentType.getFloat(ctx, "value"))))))
                                .then(Commands.literal("feel").then(Commands.argument("need", StringArgumentType.word())
                                        .suggests((ctx, b) -> SharedSuggestionProvider.suggest(names(NpcMind.Need.values()), b))
                                        .then(Commands.argument("value", FloatArgumentType.floatArg(0f, 100f))
                                                .executes(ctx -> feel(ctx, StringArgumentType.getString(ctx, "need"),
                                                        FloatArgumentType.getFloat(ctx, "value"))))))
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
            npc.born(player);
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
        NpcEntity npc = target(ctx.getSource().getPlayerOrException());
        if (npc == null) {
            ctx.getSource().sendFailure(Component.literal("No companion near you"));
            return 0;
        }
        if (!pieces(npc).contains(piece)) {
            ctx.getSource().sendFailure(Component.literal("Pieces: " + String.join(", ", pieces(npc))
                    + (dresses(npc.model()) ? " (her underwear always stays on; she puts her outfit back together after a while)" : "")));
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
        if (!dresses(npc.model()) && !dress(npc.model()).looks().isEmpty()) {
            List<String> on = pieces(npc).stream().filter(p -> !npc.hidden().contains(p)).toList();
            ctx.getSource().sendSuccess(() -> Component.literal("Outfit: " + look(npc) + (on.isEmpty() ? "" : ", with " + String.join(", ", on))
                    + " (outfits: " + String.join(", ", dress(npc.model()).looks()) + ")"), false);
            return 1;
        }
        List<String> on = PIECES.stream().filter(p -> !npc.hidden().contains(p)).toList();
        String pieces = "underwear" + (on.isEmpty() ? "" : ", " + String.join(", ", on));
        ctx.getSource().sendSuccess(() -> Component.literal(dresses(npc.model()) ? npc.wardrobe.line() + ": " + pieces : "Wearing: " + pieces), false);
        return 1;
    }

    /** Puts her in one of her outfits, no questions (for testing; she keeps it a while, then dresses as she likes). */
    private static int outfit(CommandContext<CommandSourceStack> ctx, String name) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        NpcEntity npc = target(player);
        if (npc != null && !dresses(npc.model()) && dress(npc.model()).looks().contains(name)) {
            // a model in whole outfits: she just wears that one
            java.util.Set<String> hidden = new java.util.HashSet<>(npc.hidden());
            hidden.removeIf(h -> h.startsWith("look="));
            hidden.add("look=" + name);
            npc.setHidden(hidden);
            ctx.getSource().sendSuccess(() -> Component.literal("Outfit: " + name), false);
            return 1;
        }
        NpcWardrobe.Outfit o = NpcWardrobe.Outfit.named(name);
        if (npc == null || o == null) {
            ctx.getSource().sendFailure(Component.literal(npc == null ? "No companion near you" : "Outfits: work, skirt, dress, dress_boots, date"));
            return 0;
        }
        npc.wardrobe.force(npc, o);
        ctx.getSource().sendSuccess(() -> Component.literal("She's changing into " + o.label), false);
        return 1;
    }

    private static List<String> names(Enum<?>[] values) {
        return java.util.Arrays.stream(values).map(v -> v.name().toLowerCase(java.util.Locale.ROOT)).toList();
    }

    private static int mind(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        NpcEntity npc = target(ctx.getSource().getPlayerOrException());
        if (npc == null) {
            ctx.getSource().sendFailure(Component.literal("No companion near you"));
            return 0;
        }
        NpcMind m = npc.mind;
        StringBuilder b = new StringBuilder(npc.speakerName() + " is " + npc.mood() + ". " + capital(m.traitWords()) + ".");
        b.append('\n').append(bondLine(npc));
        if (dresses(npc.model())) {
            b.append('\n').append(npc.wardrobe.line()).append('.');
        }
        for (NpcMind.Need n : NpcMind.Need.values()) {
            int v = Math.round(m.get(n));
            b.append("\n ").append(n.label).append(' ').append("|".repeat(v / 5)).append(".".repeat(20 - v / 5)).append(' ').append(v);
        }
        long now = npc.level().getGameTime();
        List<NpcMind.Memory> recent = m.diary.subList(Math.max(0, m.diary.size() - 5), m.diary.size());
        if (!recent.isEmpty()) {
            b.append("\nRemembers:");
            for (int i = recent.size() - 1; i >= 0; i--) {
                NpcMind.Memory mem = recent.get(i);
                long mins = (now - mem.at()) / 1200;
                b.append("\n - ").append(mem.what()).append(" (").append(mins < 1 ? "just now" : mins + " min ago")
                        .append(", near ").append(mem.x()).append(", ").append(mem.z()).append(')');
            }
        }
        b.append("\n").append(skillLines(npc));
        ctx.getSource().sendSuccess(() -> Component.literal(b.toString()), false);
        return 1;
    }

    private static int home(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        NpcEntity npc = target(player);
        if (npc == null) {
            ctx.getSource().sendFailure(Component.literal("No companion near you"));
            return 0;
        }
        npc.work.home = player.blockPosition();
        int stores = npc.work.stores((ServerLevel) player.level()).size();
        npc.mind.remember(npc, "Moved into a new home", 0.3f, 0.6f);
        ctx.getSource().sendSuccess(() -> Component.literal(npc.speakerName() + "'s home is here now (" + NpcWork.HOME_RADIUS
                + " blocks round). Stores she can use: " + stores + (stores == 0 ? ". Put a chest or barrel near here for her" : "")
                + ". Give her a job with /zc npc job, then make her wait (G)."), false);
        return 1;
    }

    private static int job(CommandContext<CommandSourceStack> ctx, String name) throws CommandSyntaxException {
        NpcEntity npc = target(ctx.getSource().getPlayerOrException());
        NpcWork.Job job = NpcWork.Job.of(name);
        if (npc == null || job == null) {
            ctx.getSource().sendFailure(Component.literal(npc == null ? "No companion near you" : "Jobs: " + NpcWork.jobsList()));
            return 0;
        }
        if (npc.work.home == null && job != NpcWork.Job.NONE) {
            ctx.getSource().sendFailure(Component.literal("Give her a home first: stand where it is and use /zc npc home"));
            return 0;
        }
        npc.work.job = job;
        boolean guardNights = job == NpcWork.Job.GUARD;
        ctx.getSource().sendSuccess(() -> Component.literal(job == NpcWork.Job.NONE ? npc.speakerName() + " has no job now"
                : npc.speakerName() + " is the " + job.label + " now" + (npc.following ? ". Make her wait (G) and she'll start" : "")
                + (guardNights && !npc.work.nightShift ? ". Tip: /zc npc shift night for a night watch" : "")), false);
        return 1;
    }

    private static int shift(CommandContext<CommandSourceStack> ctx, String name) throws CommandSyntaxException {
        NpcEntity npc = target(ctx.getSource().getPlayerOrException());
        if (npc == null || !name.equals("day") && !name.equals("night")) {
            ctx.getSource().sendFailure(Component.literal(npc == null ? "No companion near you" : "Shift is day or night"));
            return 0;
        }
        npc.work.nightShift = name.equals("night");
        ctx.getSource().sendSuccess(() -> Component.literal(npc.work.nightShift
                ? "Night shift: she works 19:00 to 05:00, unwinds till 09:00 and sleeps through the day"
                : "Day shift: she works 06:00 to 17:00, unwinds till 21:00 and sleeps through the night"), false);
        return 1;
    }

    private static int bond(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        NpcEntity npc = target(ctx.getSource().getPlayerOrException());
        if (npc == null) {
            ctx.getSource().sendFailure(Component.literal("No companion near you"));
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.literal(bondLine(npc)), false);
        return 1;
    }

    /** Sets how she feels about the player she's with (for trying things out), or puts the two of you together or apart. */
    private static int bond(CommandContext<CommandSourceStack> ctx, String feeling, float value) throws CommandSyntaxException {
        NpcEntity npc = target(ctx.getSource().getPlayerOrException());
        boolean known = List.of("trust", "affection", "attraction").contains(feeling);
        if (npc == null || !known && !feeling.equals("together") && !feeling.equals("apart") || known && value < 0) {
            ctx.getSource().sendFailure(Component.literal(npc == null ? "No companion near you"
                    : "Use trust, affection or attraction with a value 0 to 100, or together, or apart"));
            return 0;
        }
        npc.bond.meet(ctx.getSource().getPlayerOrException());
        if (known) npc.bond.set(feeling, value);
        else npc.bond.together = feeling.equals("together");
        ctx.getSource().sendSuccess(() -> Component.literal(bondLine(npc)), false);
        return 1;
    }

    static String bondLine(NpcEntity npc) {
        NpcBond b = npc.bond;
        if (b.with == null) return "She hasn't got to know anyone yet.";
        return "About " + b.withName + ": trust " + Math.round(b.trust) + ", affection " + Math.round(b.affection) + ", attraction "
                + Math.round(b.attraction) + (b.together ? ", together" : "") + ". "
                + NpcBond.words(b.trust, b.affection, b.attraction, b.together);
    }

    private static int skills(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        NpcEntity npc = target(ctx.getSource().getPlayerOrException());
        if (npc == null) {
            ctx.getSource().sendFailure(Component.literal("No companion near you"));
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.literal(skillLines(npc)), false);
        return 1;
    }

    static String skillLines(NpcEntity npc) {
        StringBuilder b = new StringBuilder(npc.speakerName() + ": " + npc.work.job.label
                + (npc.work.home == null ? ", no home" : ", " + (npc.work.nightShift ? "night" : "day") + " shift"));
        for (NpcSkills.Skill sk : NpcSkills.Skill.values()) {
            int l = npc.skills.level(sk);
            b.append("\n ").append(sk.label).append(' ').append("#".repeat(l)).append("-".repeat(NpcSkills.MAX - l))
                    .append(' ').append(l).append(" (").append(Math.round(npc.skills.progress(sk) * 100)).append("% to next)");
        }
        return b.toString();
    }

    private static int skill(CommandContext<CommandSourceStack> ctx, String name, int level) throws CommandSyntaxException {
        NpcEntity npc = target(ctx.getSource().getPlayerOrException());
        NpcSkills.Skill sk = java.util.Arrays.stream(NpcSkills.Skill.values()).filter(v -> v.name().equalsIgnoreCase(name)).findFirst().orElse(null);
        if (npc == null || sk == null) {
            ctx.getSource().sendFailure(Component.literal(npc == null ? "No companion near you" : "Skills: " + String.join(", ", names(NpcSkills.Skill.values()))));
            return 0;
        }
        npc.skills.set(sk, level);
        ctx.getSource().sendSuccess(() -> Component.literal("Her " + sk.label + " is " + level + " now"), false);
        return 1;
    }

    private static String capital(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static int trait(CommandContext<CommandSourceStack> ctx, String name, float value) throws CommandSyntaxException {
        NpcEntity npc = target(ctx.getSource().getPlayerOrException());
        NpcMind.Trait t = java.util.Arrays.stream(NpcMind.Trait.values()).filter(v -> v.name().equalsIgnoreCase(name)).findFirst().orElse(null);
        if (npc == null || t == null) {
            ctx.getSource().sendFailure(Component.literal(npc == null ? "No companion near you" : "Traits: " + String.join(", ", names(NpcMind.Trait.values()))));
            return 0;
        }
        npc.mind.setTrait(t, value);
        ctx.getSource().sendSuccess(() -> Component.literal("She's " + npc.mind.traitWords() + " now"), false);
        return 1;
    }

    private static int feel(CommandContext<CommandSourceStack> ctx, String name, float value) throws CommandSyntaxException {
        NpcEntity npc = target(ctx.getSource().getPlayerOrException());
        NpcMind.Need n = java.util.Arrays.stream(NpcMind.Need.values()).filter(v -> v.name().equalsIgnoreCase(name)).findFirst().orElse(null);
        if (npc == null || n == null) {
            ctx.getSource().sendFailure(Component.literal(npc == null ? "No companion near you" : "Needs: " + String.join(", ", names(NpcMind.Need.values()))));
            return 0;
        }
        npc.mind.set(n, value);
        ctx.getSource().sendSuccess(() -> Component.literal("Her " + n.label + " is " + Math.round(value) + "; she's " + npc.mood()), false);
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
