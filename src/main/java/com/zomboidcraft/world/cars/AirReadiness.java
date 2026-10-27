package com.zomboidcraft.world.cars;

import com.mojang.serialization.Codec;
import com.zomboidcraft.ModItems;
import com.zomboidcraft.ZcConfig;
import com.zomboidcraft.ZomboidCraft;
import com.zomboidcraft.skills.Skill;
import com.zomboidcraft.skills.Skills;
import com.zomboidcraft.survival.TimedActions;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Earning an aircraft. Two halves, both needed:
 *
 * <ul>
 * <li>The pilot: a flight manual for the kind of aircraft (read once, like Zomboid's skill magazines,
 * and forgotten when you die), without which you can only taxi; and the Piloting skill, which
 * steadies the controls and lets you land harder as it grows.</li>
 * <li>The aircraft: found with near-empty tanks, a flat battery and worn out. Drums of jet fuel, an
 * aircraft battery and aircraft parts, used on it with the interact key, make it airworthy.</li>
 * </ul>
 */
public final class AirReadiness {
    public enum Manual {
        ROTARY("rotary_wing_manual", "Rotary-Wing Flight Manual"),
        JET("jet_flight_manual", "Jet Fighter Flight Manual"),
        WEAPONS("aircraft_weapons_manual", "Aircraft Weapons Manual");

        public final String id, title;

        Manual(String id, String title) {
            this.id = id;
            this.title = title;
        }
    }

    /** The manuals a player has read, by name. Synced to them for the cockpit; not kept through death. */
    public static final AttachmentType<List<String>> READ = AttachmentRegistry.create(
            ZomboidCraft.id("flight_manuals"),
            builder -> builder
                    .persistent(Codec.STRING.listOf())
                    .initializer(List::of)
                    .syncWith(ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), AttachmentSyncPredicate.targetOnly()));

    /** The least condition (percent) an aircraft will start at. */
    public static final int AIRWORTHY = 60;

    public static void register() {
        // loading the class registers the attachment
    }

    public static boolean knows(Player player, Manual manual) {
        return player.isCreative() || player.getAttachedOrCreate(READ).contains(manual.name());
    }

    /** The manual that lets you take this kind of aircraft off the ground. */
    public static Manual manualFor(Flight.Kind kind) {
        return kind == Flight.Kind.HELI ? Manual.ROTARY : Manual.JET;
    }

    /** How steady a pilot is, 0 (never flown) to 1 (Piloting 10). */
    public static float skill(Player player) {
        return player.isCreative() ? 1 : Skills.level(player, Skill.PILOTING) / (float) Skill.MAX_LEVEL;
    }

    /** A flight manual: use it to read it, which takes a while; it stays in your bag afterwards. */
    public static class ManualItem extends Item {
        private final Manual manual;

        public ManualItem(Manual manual, Properties properties) {
            super(properties);
            this.manual = manual;
        }

        @Override
        public InteractionResult use(Level level, Player player, InteractionHand hand) {
            if (player instanceof ServerPlayer sp && !TimedActions.busy(player)) {
                if (knows(player, manual) && !player.isCreative()) {
                    sp.sendOverlayMessage(Component.literal("You've already read the " + manual.title + ".").withStyle(ChatFormatting.GRAY));
                    return InteractionResult.CONSUME;
                }
                TimedActions.start(sp, "read_manual", "Reading", 20 * 15, p -> {
                    List<String> read = new ArrayList<>(p.getAttachedOrCreate(READ));
                    if (!read.contains(manual.name())) {
                        read.add(manual.name());
                    }
                    p.setAttached(READ, read);
                    Skills.addXp(p, Skill.PILOTING, 60);
                    p.sendOverlayMessage(Component.literal(switch (manual) {
                        case ROTARY -> "You know how to fly a helicopter now: collective, cyclic and pedals.";
                        case JET -> "You know how to fly a jet now: take-off speed, rotation and the landing.";
                        case WEAPONS -> "You know how to arm and fire aircraft weapons now.";
                    }));
                });
            }
            return InteractionResult.CONSUME;
        }
    }

    /**
     * The interact key on an aircraft from outside with something in hand: fuel, a battery or parts.
     * Returns whether it was one of those (and so started, or said why not).
     */
    public static boolean service(CarProp car, ServerPlayer player) {
        ItemStack held = player.getMainHandItem();
        Item item = held.getItem();
        if (item != ModItems.JET_FUEL && item != ModItems.AIRCRAFT_BATTERY && item != ModItems.AIRCRAFT_PARTS) {
            return false;
        }
        if (!car.engineRuns() || car.broken()) {
            say(player, "It's a wreck. Nothing will fix it now.");
            return true;
        }
        if (TimedActions.busy(player)) {
            return true;
        }
        if (item == ModItems.JET_FUEL) {
            if (car.fuel() >= 0.995f) {
                say(player, "The tanks are full.");
                return true;
            }
            TimedActions.start(player, "air_fuel", "Refuelling", 20 * 6, p -> {
                if (!p.getMainHandItem().is(ModItems.JET_FUEL)) {
                    return;
                }
                p.getMainHandItem().consume(1, p);
                car.setFuel(car.fuel() + (car.helicopter() ? 0.35f : 0.15f));
                p.level().playSound(null, car.getX(), car.getY(), car.getZ(), SoundEvents.BUCKET_EMPTY, SoundSource.PLAYERS, 0.8f, 0.7f);
                say(p, "Fuel " + Math.round(car.fuel() * 100) + "%.");
            });
        } else if (item == ModItems.AIRCRAFT_BATTERY) {
            if (car.battery()) {
                say(player, "The battery's good.");
                return true;
            }
            TimedActions.start(player, "air_battery", "Fitting the battery", 20 * 8, p -> {
                if (!p.getMainHandItem().is(ModItems.AIRCRAFT_BATTERY)) {
                    return;
                }
                p.getMainHandItem().consume(1, p);
                car.setBattery(true);
                p.level().playSound(null, car.getX(), car.getY(), car.getZ(), SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.PLAYERS, 0.8f, 1.2f);
                say(p, "Battery in. The aircraft has power.");
            });
        } else {
            if (car.condition() >= 100) {
                say(player, "Nothing left to repair.");
                return true;
            }
            int level = Skills.level(player, Skill.MAINTENANCE);
            TimedActions.start(player, "air_repair", "Repairing", 20 * Math.max(5, 12 - level / 2), p -> {
                if (!p.getMainHandItem().is(ModItems.AIRCRAFT_PARTS)) {
                    return;
                }
                p.getMainHandItem().consume(1, p);
                car.setCondition(car.condition() + 15 + level * 2);
                Skills.addXp(p, Skill.MAINTENANCE, 15);
                p.level().playSound(null, car.getX(), car.getY(), car.getZ(), SoundEvents.ANVIL_USE, SoundSource.PLAYERS, 0.4f, 1.4f);
                say(p, "Condition " + car.condition() + "%" + (car.condition() >= AIRWORTHY ? ", airworthy." : ", not airworthy yet ("
                        + AIRWORTHY + "% needed)."));
            });
        }
        return true;
    }

    /** Why this aircraft won't start for player, or null if it will. */
    public static String cantStart(CarProp car, Player player) {
        if (player.isCreative()) {
            return null;
        }
        if (!car.battery()) {
            return "Nothing happens: the battery's flat. Fit an aircraft battery (interact with one in hand).";
        }
        if (car.condition() < AIRWORTHY) {
            return "Warning lights everywhere: it's at " + car.condition() + "%, not airworthy. Repair it with aircraft parts.";
        }
        if (car.fuel() <= 0.001f) {
            return "The tanks are dry. Refuel it with drums of jet fuel.";
        }
        return null;
    }

    /** What a freshly found aircraft is like: a little fuel, a dead battery, worn (unless the setting's off). */
    public static void found(CarProp car) {
        if (!ZcConfig.get().world.aircraftService) {
            car.setFuel(1);
            car.setBattery(true);
            car.setCondition(100);
            return;
        }
        var r = car.getRandom();
        car.setFuel(r.nextFloat() * 0.12f);
        car.setBattery(false);
        car.setCondition("usable".equals(car.variant()) ? 40 + r.nextInt(31) : 20 + r.nextInt(31));
    }

    private static void say(ServerPlayer player, String text) {
        player.sendOverlayMessage(Component.literal(text));
    }

    private AirReadiness() {
    }
}
