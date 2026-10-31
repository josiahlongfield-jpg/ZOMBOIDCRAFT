package com.zomboidcraft.world.cars;

import com.zomboidcraft.ZomboidCraft;
import com.zomboidcraft.loot.items.LootItems;
import com.zomboidcraft.skills.Skill;
import com.zomboidcraft.skills.Skills;
import com.zomboidcraft.survival.TimedActions;
import com.zomboidcraft.world.cars.CarParts.Area;
import com.zomboidcraft.world.cars.CarParts.Kind;
import com.zomboidcraft.world.cars.CarParts.Part;
import java.util.Locale;
import java.util.Random;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/**
 * Working on a car's parts from the mechanics screen: taking one off, putting one on, topping up the fluids.
 * Each job is a timed action and needs the Mechanics level, the tools and the access its part does: tyres
 * and brakes want the corner up on a jack (or the car on a hoist), the underside and the suspension a hoist.
 * Whether a job can be done is worked out the same on both sides ({@link #why}), so the screen greys out
 * exactly what the server would refuse and says why.
 *
 * <p>A part taken off is an item carrying its condition and class as custom data ({@link #stack}), so it
 * can be carried to another car, stored or traded; LOOT's found parts carry the same keys.
 */
public final class Mechanics {
    public enum Job { REMOVE, INSTALL, TOP_UP }

    /** Do job on slot of car (its entity id), from the mechanics screen. */
    public record Work(int car, int slot, int job) implements CustomPacketPayload {
        public static final Type<Work> TYPE = new Type<>(ZomboidCraft.id("car_work"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Work> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Work::car, ByteBufCodecs.VAR_INT, Work::slot, ByteBufCodecs.VAR_INT, Work::job, Work::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** How close you have to be to the car to work on it. */
    public static final double REACH = 3.5;

    public static void register() {
        net.fabricmc.fabric.api.event.player.UseItemCallback.EVENT.register(Mechanics::read);
        PayloadTypeRegistry.serverboundPlay().register(Work.TYPE, Work.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(Work.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            player.level().getServer().execute(() -> {
                if (player.level().getEntity(payload.car()) instanceof CarProp car && payload.slot() >= 0
                        && payload.slot() < CarParts.SLOTS.size() && payload.job() >= 0 && payload.job() < Job.values().length) {
                    start(player, car, payload.slot(), Job.values()[payload.job()]);
                }
            });
        });
    }

    /** The data keys a car part item carries. */
    public static final String PART_KEY = "car_part", CONDITION_KEY = "condition", KIND_KEY = "kind";

    /** A part item: its id, condition 0-100 and class; or null if the stack isn't one. */
    public record Held(String id, int condition, Kind kind) {
    }

    public static Held held(ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        CompoundTag tag = data == null ? new CompoundTag() : data.copyTag();
        String id = tag.getStringOr(PART_KEY, "");
        if (id.isEmpty()) {
            var key = BuiltInRegistries.ITEM.getKey(stack.getItem());
            if (!ZomboidCraft.MOD_ID.equals(key.getNamespace())) {
                return null;
            }
            id = key.getPath();
        }
        Kind kind;
        try {
            kind = Kind.valueOf(tag.getStringOr(KIND_KEY, "standard").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            kind = Kind.STANDARD;
        }
        // a part found new in its box, without a condition written on it, is in good order
        return new Held(id, Math.clamp(tag.getIntOr(CONDITION_KEY, 80), 0, 100), kind);
    }

    /** A part as an item, the loot item where LOOT has made one, else scrap metal named for it. */
    public static ItemStack stack(Part part, int condition, Kind kind) {
        Item item = LootItems.item(part.item);
        boolean stand = item == null;
        ItemStack stack = new ItemStack(stand ? LootItems.item("scrap_metal") : item);
        CompoundTag tag = new CompoundTag();
        tag.putString(PART_KEY, part.item);
        tag.putInt(CONDITION_KEY, condition);
        tag.putString(KIND_KEY, kind.name().toLowerCase(Locale.ROOT));
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        if (stand) {
            stack.set(DataComponents.CUSTOM_NAME, Component.literal(part.title + " (" + condition + "%)"));
        }
        return stack;
    }

    /**
     * Parts any car takes whatever its class, as in Zomboid: the battery, the bulbs, the radio and the small
     * stuff. Everything else fits only its own class (standard, heavy-duty, sport), so the donor car matters.
     */
    private static boolean universal(Part part) {
        return switch (part) {
            case BATTERY, SPARK_PLUGS, HEADLIGHT, TAIL_LIGHT, HORN, RADIO, FAN_BELT, OIL, BRAKE_FLUID -> true;
            default -> false;
        };
    }

    /**
     * Zomboid's auto manuals ("Laine's Auto Manual"): reading one teaches you to work on that class of car, kept
     * till you die. Without it you can still do what anyone can (tyres, the battery, bulbs, fluids).
     */
    public static final net.fabricmc.fabric.api.attachment.v1.AttachmentType<java.util.List<String>> MANUALS =
            net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry.create(ZomboidCraft.id("auto_manuals"),
                    builder -> builder.persistent(com.mojang.serialization.Codec.STRING.listOf())
                            .initializer(java.util.List::of)
                            .syncWith(ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()),
                                    net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate.targetOnly()));

    public static String manual(Kind kind) {
        return "auto_manual_" + kind.name().toLowerCase(Locale.ROOT);
    }

    public static String manualTitle(Kind kind) {
        return switch (kind) {
            case STANDARD -> "Standard Models";
            case HEAVY -> "Commercial Models";
            case SPORT -> "Performance Models";
        };
    }

    public static boolean knows(Player player, Kind kind) {
        return player.isCreative() || player.getAttachedOrCreate(MANUALS).contains(manual(kind));
    }

    /** The jobs anyone can do without a manual: what you'd do at the roadside. */
    private static boolean basic(Part part) {
        return universal(part) || part == Part.TIRE;
    }

    /** Using an auto manual reads it (a while, like a flight manual); it stays in the bag afterwards. */
    private static net.minecraft.world.InteractionResult read(Player player, net.minecraft.world.level.Level level,
            net.minecraft.world.InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        String id = held.isEmpty() ? "" : BuiltInRegistries.ITEM.getKey(held.getItem()).getPath();
        Kind kind = null;
        for (Kind k : Kind.values()) {
            if (manual(k).equals(id)) {
                kind = k;
            }
        }
        if (kind == null) {
            return net.minecraft.world.InteractionResult.PASS;
        }
        if (player instanceof ServerPlayer sp && !TimedActions.busy(player)) {
            Kind k = kind;
            if (knows(player, k) && !player.isCreative()) {
                sp.sendOverlayMessage(Component.literal("You've already read Laine's Auto Manual: " + manualTitle(k) + "."));
                return net.minecraft.world.InteractionResult.CONSUME;
            }
            TimedActions.start(sp, "read_auto_manual", "Reading", 20 * 15, p -> {
                java.util.List<String> read = new java.util.ArrayList<>(p.getAttachedOrCreate(MANUALS));
                if (!read.contains(manual(k))) {
                    read.add(manual(k));
                }
                p.setAttached(MANUALS, read);
                Skills.addXp(p, Skill.MECHANICS, 40);
                p.sendOverlayMessage(Component.literal("You know your way around " + manualTitle(k).toLowerCase(Locale.ROOT)
                        + " now: their parts are yours to take off and fit."));
            });
        }
        return net.minecraft.world.InteractionResult.CONSUME;
    }

    /** The fluid (or kit) a top-up uses, or null where a part can't be topped up. */
    private static String fluid(Part part) {
        return switch (part) {
            case OIL -> "motor_oil";
            case BRAKE_FLUID -> "brake_fluid";
            case RADIATOR -> "coolant";
            default -> null;
        };
    }

    /** The jobs a slot offers, in the order the screen shows them. */
    public static Job[] jobs(Part part) {
        return switch (part) {
            case OIL -> new Job[]{Job.TOP_UP, Job.INSTALL};     // a top-up, or a full change with a new filter
            case BRAKE_FLUID -> new Job[]{Job.TOP_UP};
            case RADIATOR -> new Job[]{Job.TOP_UP, Job.REMOVE, Job.INSTALL};
            default -> new Job[]{Job.REMOVE, Job.INSTALL};
        };
    }

    public static String label(Part part, Job job) {
        return switch (job) {
            case TOP_UP -> part == Part.RADIATOR ? "Top up coolant" : "Top up";
            case INSTALL -> part == Part.OIL ? "Change oil" : "Install";
            case REMOVE -> "Remove";
        };
    }

    private static boolean has(Player player, String id) {
        return find(player, id) >= 0;
    }

    private static int find(Player player, String id) {
        Item item = LootItems.item(id);
        var inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (!s.isEmpty() && (item != null && s.is(item) || id.equals(partId(s)))) {
                return i;
            }
        }
        return -1;
    }

    private static String partId(ItemStack s) {
        CustomData data = s.get(DataComponents.CUSTOM_DATA);
        return data == null ? "" : data.copyTag().getStringOr(PART_KEY, "");
    }

    /** The best part item for this slot in the player's inventory (highest condition), or -1. */
    private static int bestPart(Player player, Part part) {
        var inv = player.getInventory();
        int best = -1, bestC = -1;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            Held h = held(inv.getItem(i));
            if (h != null && h.id.equals(part.item) && h.condition > bestC) {
                best = i;
                bestC = h.condition;
            }
        }
        return best;
    }

    /** The tool a part's work needs on top of the access, or null for bare hands. */
    private static String tool(Part part) {
        return switch (part) {
            case TIRE -> "tire_iron";
            case HEADLIGHT, TAIL_LIGHT, HORN, RADIO -> "screwdriver";
            case BATTERY, OIL, BRAKE_FLUID -> null;
            default -> "socket_set";
        };
    }

    private static String name(String id) {
        Item item = LootItems.item(id);
        return item != null ? new ItemStack(item).getHoverName().getString().toLowerCase(Locale.ROOT) : id.replace('_', ' ');
    }

    public static int level(Player player) {
        return player.isCreative() ? Skill.MAX_LEVEL : Skills.level(player, Skill.MECHANICS);
    }

    /** Whether the car is up on a hoist (all of it), a trolley jack holds up this wheel's corner, or (tyres) they have a jack. */
    private static String access(Player player, CarProp car, Part part, int corner) {
        if (!part.lifted || car.underReach()) {
            return null;
        }
        boolean corner = part == Part.TIRE || part == Part.RIM || part == Part.BRAKES;
        if (!corner) {
            return car.hoisted() ? "Take the hoist up to chest height to reach it" : "Needs the car up on a hoist";
        }
        if (car.wheelsFree()) {
            return null;
        }
        if (car.jackedFree(corner) || part == Part.TIRE && has(player, "car_jack")) {
            return null;
        }
        return part == Part.TIRE ? "Needs a jack under that corner, or the car on a hoist"
                : "Needs a trolley jack under that corner, or the car on a hoist";
    }

    /**
     * Why this player can't do this job on this slot right now, or null if they can. The same answer on the
     * client (to grey the button) and the server (to refuse it).
     */
    public static String why(Player player, CarProp car, int slot, Job job) {
        CarParts.Slot s = CarParts.SLOTS.get(slot);
        Part part = s.part();
        String parts = car.parts();
        int c = CarParts.get(parts, slot);
        if (c == -2) {
            return "This car doesn't have one";
        }
        int level = level(player);
        int need = job == Job.TOP_UP ? 0 : part.level;
        if (level < need) {
            return "Needs Mechanics " + need;
        }
        if (job == Job.TOP_UP) {
            if (c < 0) {
                return part == Part.RADIATOR ? "No radiator to fill" : "Nothing to fill";
            }
            if (c >= 100) {
                return "Already full";
            }
            if (part == Part.RADIATOR && c < 25) {
                return "It's leaking: coolant won't stay in";
            }
            String f = fluid(part);
            return has(player, f) || part == Part.RADIATOR && has(player, "antifreeze") ? null : "Needs " + name(f);
        }
        Kind carKind = CarParts.kind(CarModels.get(car.model()));
        if (!basic(part) && !knows(player, carKind)) {
            return "Needs Laine's Auto Manual: " + manualTitle(carKind);
        }
        String a = access(player, car, part, s.index());
        if (a != null) {
            return a;
        }
        if ((part == Part.BRAKES || part == Part.SUSPENSION || part == Part.RIM) && CarParts.get(parts, Part.TIRE, s.index()) >= 0) {
            return "Take the " + CarParts.SLOTS.get(CarParts.slot(Part.TIRE, s.index())).title().toLowerCase(Locale.ROOT) + " off first";
        }
        if (part == Part.TIRE && job == Job.INSTALL && CarParts.get(parts, Part.RIM, s.index()) < 0) {
            return "No wheel rim to fit it on";
        }
        if (part == Part.ENGINE && !ShopTool.craneAt(car)) {
            return job == Job.REMOVE ? "Needs an engine crane at the front to lift it out" : "Needs an engine crane at the front to drop it in";
        }
        String t = tool(part);
        if (t != null && !has(player, t)) {
            return "Needs a " + name(t);
        }
        if (job == Job.REMOVE) {
            return c < 0 ? "Nothing fitted" : null;
        }
        if (part == Part.OIL) {
            return has(player, "motor_oil") && has(player, "oil_filter") ? null : "Needs motor oil and an oil filter";
        }
        if (c >= 0) {
            return "Take the old one off first";
        }
        int at = bestPart(player, part);
        if (at < 0) {
            return "You have no " + part.title.toLowerCase(Locale.ROOT);
        }
        Held h = held(player.getInventory().getItem(at));
        if (!universal(part) && h.kind != carKind) {
            return "Yours is a " + manualTitle(h.kind).toLowerCase(Locale.ROOT).replace(" models", "") + " part: it won't fit this car";
        }
        return null;
    }

    /** An engine crane block stands within a few blocks (the shop's own, set up by the hoist work). */
    /** How long a job takes, in ticks: harder parts longer, a hoist and the right power tools quicker, skill too. */
    public static int ticks(Player player, CarProp car, int slot, Job job) {
        Part part = CarParts.SLOTS.get(slot).part();
        if (job == Job.TOP_UP) {
            return 60;
        }
        double t = part == Part.ENGINE ? 900 : 60 + part.level * 30;
        if (job == Job.INSTALL && part == Part.OIL) {
            t = 200;
        }
        if (part.lifted && car.wheelsFree()) {
            t *= 0.6;
        }
        if ((part == Part.TIRE || part == Part.RIM) && has(player, "impact_wrench")) {
            t /= 3;
        }
        t *= 1 - 0.03 * Math.max(0, level(player) - part.level);
        return (int) Math.round(t);
    }

    private static void start(ServerPlayer player, CarProp car, int slot, Job job) {
        if (car.getBoundingBox().inflate(1).distanceToSqr(player.getEyePosition()) > REACH * REACH || player.isPassenger()) {
            return;
        }
        String why = why(player, car, slot, job);
        if (why != null) {
            player.sendOverlayMessage(Component.literal(why + "."));
            return;
        }
        CarParts.Slot s = CarParts.SLOTS.get(slot);
        String what = label(s.part(), job) + ": " + s.title().toLowerCase(Locale.ROOT);
        TimedActions.start(player, "car_" + car.getId() + "_" + slot + "_" + job, what, ticks(player, car, slot, job),
                p -> finish(p, car, slot, job));
        ServerLevel level = (ServerLevel) player.level();
        level.playSound(null, car.getX(), car.getY(), car.getZ(), job == Job.TOP_UP ? SoundEvents.BUCKET_EMPTY : SoundEvents.CHAIN_PLACE,
                SoundSource.PLAYERS, 0.6f, 0.9f);
    }

    private static void finish(ServerPlayer player, CarProp car, int slot, Job job) {
        if (car.isRemoved() || why(player, car, slot, job) != null) {
            return;     // something changed while they worked: the part went, the tool's gone
        }
        CarParts.Slot s = CarParts.SLOTS.get(slot);
        Part part = s.part();
        String parts = car.parts();
        int c = CarParts.get(parts, slot);
        int level = level(player);
        Random rng = new Random(player.getRandom().nextLong());
        Kind kind = CarParts.kind(CarModels.get(car.model()));
        // the chance a job goes wrong: high at the level it's first allowed, falling with each level past it
        double slip = player.isCreative() ? 0 : Math.max(0.03, 0.3 - 0.05 * (level - part.level));
        ServerLevel world = (ServerLevel) player.level();
        String said;
        switch (job) {
            case TOP_UP -> {
                String f = fluid(part);
                int at = find(player, f);
                if (at < 0 && part == Part.RADIATOR) {
                    at = find(player, "antifreeze");
                }
                player.getInventory().getItem(at).shrink(1);
                int add = part == Part.OIL ? 50 : part == Part.BRAKE_FLUID ? 60 : 30;
                car.setParts(CarParts.set(parts, slot, Math.min(100, c + add)));
                said = s.title() + " topped up to " + Math.min(100, c + add) + "%.";
                Skills.addXp(player, Skill.MECHANICS, 4);
                world.playSound(null, car.getX(), car.getY(), car.getZ(), SoundEvents.BUCKET_EMPTY, SoundSource.PLAYERS, 0.6f, 1.2f);
            }
            case REMOVE -> {
                int got = c;
                boolean slipped = rng.nextDouble() < slip;
                if (slipped) {
                    got = Math.max(0, c - 15 - rng.nextInt(26));
                }
                car.setParts(CarParts.set(parts, slot, -1));
                ItemStack out = stack(part, got, kind);
                if (!player.getInventory().add(out)) {
                    player.spawnAtLocation((ServerLevel) player.level(), out);
                }
                said = slipped ? "It fights you and comes off damaged: " + s.title().toLowerCase(Locale.ROOT) + " " + got + "%."
                        : s.title() + " off (" + got + "%).";
                Skills.addXp(player, Skill.MECHANICS, 8 + part.level * 2);
                world.playSound(null, car.getX(), car.getY(), car.getZ(), SoundEvents.CHAIN_BREAK, SoundSource.PLAYERS, 0.7f, 0.9f);
            }
            default -> {
                if (part == Part.OIL) {
                    player.getInventory().getItem(find(player, "motor_oil")).shrink(1);
                    player.getInventory().getItem(find(player, "oil_filter")).shrink(1);
                    car.setParts(CarParts.set(parts, slot, 100));
                    said = "Fresh oil and a new filter.";
                    Skills.addXp(player, Skill.MECHANICS, 14);
                } else {
                    int at = bestPart(player, part);
                    ItemStack item = player.getInventory().getItem(at);
                    Held h = held(item);
                    int fit = h.condition;
                    boolean slipped = rng.nextDouble() < slip;
                    if (slipped) {
                        fit = Math.max(0, fit - 5 - rng.nextInt(16));
                    }
                    item.shrink(1);
                    car.setParts(CarParts.set(parts, slot, fit));
                    said = (slipped ? "A fight to get it seated: " : "Fitted: ") + s.title().toLowerCase(Locale.ROOT) + " " + fit + "%.";
                    Skills.addXp(player, Skill.MECHANICS, 12 + part.level * 3);
                }
                world.playSound(null, car.getX(), car.getY(), car.getZ(), SoundEvents.CHAIN_PLACE, SoundSource.PLAYERS, 0.7f, 1.1f);
            }
        }
        player.sendOverlayMessage(Component.literal(said));
    }

    /** Which part of the car a job is done at, for where the screen's work lamp is aimed. */
    public static Area area(int slot) {
        return CarParts.SLOTS.get(slot).part().area;
    }

    private Mechanics() {
    }
}
