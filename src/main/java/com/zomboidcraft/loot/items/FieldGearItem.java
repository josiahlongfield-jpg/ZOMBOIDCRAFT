package com.zomboidcraft.loot.items;

import com.zomboidcraft.skills.Skill;
import com.zomboidcraft.skills.Skills;
import com.zomboidcraft.survival.Mood;
import com.zomboidcraft.survival.Mood.Stat;
import com.zomboidcraft.survival.TimedActions;
import com.zomboidcraft.zombie.Noise;
import java.util.Locale;
import java.util.Map;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Wave 4 military kit that works from your hands: the laser rangefinder (the range to whatever you aim at, while it's
 * in your main hand), the lensatic compass (an exact bearing in degrees and mils, and the back bearing), the
 * regimental drum (beat it to bring the dead on purpose), the ration heater (a hot field meal with no fire), the
 * parachute (cut into rags and cord) and the survival field manual (read it to learn trapping and foraging).
 */
public class FieldGearItem extends LootItem {
    public enum Kind { RANGEFINDER, LENSATIC, DRUM, HEATER, PARACHUTE, MANUAL }

    static final Map<String, Kind> KINDS = Map.of(
            "laser_rangefinder", Kind.RANGEFINDER, "lensatic_compass", Kind.LENSATIC, "regimental_drum", Kind.DRUM,
            "ration_heater", Kind.HEATER, "parachute", Kind.PARACHUTE, "survival_field_manual", Kind.MANUAL);

    private static final String[] POINTS = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};

    private final Kind kind;

    FieldGearItem(Properties properties, Kind kind) {
        super(properties);
        this.kind = kind;
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(FieldGearItem::tick);
    }

    /** The readouts: shown while the rangefinder or the lensatic compass is in your main hand. */
    private static void tick(MinecraftServer server) {
        if (server.getTickCount() % 5 != 0) return;
        for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
            if (!(sp.getMainHandItem().getItem() instanceof FieldGearItem gear)) continue;
            String text = switch (gear.kind) {
                case RANGEFINDER -> range(sp);
                case LENSATIC -> bearing(sp);
                default -> null;
            };
            if (text != null) sp.sendOverlayMessage(Component.literal(text).withStyle(ChatFormatting.GRAY));
        }
    }

    private static String range(ServerPlayer sp) {
        Vec3 eye = sp.getEyePosition(), to = eye.add(sp.getViewVector(1f).scale(400));
        BlockHitResult hit = sp.level().clip(new ClipContext(eye, to, ClipContext.Block.VISUAL, ClipContext.Fluid.ANY, sp));
        if (hit.getType() == HitResult.Type.MISS) return "Range: ---";
        return String.format(Locale.ROOT, "Range: %d m", Math.round(hit.getLocation().distanceTo(eye)));
    }

    private static String bearing(ServerPlayer sp) {
        int deg = Math.floorMod(Math.round(sp.getYRot()) + 180, 360);
        int mils = Math.round(deg * 6400f / 360f);
        return String.format(Locale.ROOT, "Bearing %s %d° (%d mils)   back bearing %d°",
                POINTS[Math.floorMod(Math.round(deg / 45f), 8)], deg, mils, (deg + 180) % 360);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!(player instanceof ServerPlayer sp) || TimedActions.busy(player)) return InteractionResult.CONSUME;
        ItemStack stack = player.getItemInHand(hand);
        switch (kind) {
            case DRUM -> {
                if (player.getCooldowns().isOnCooldown(stack)) return InteractionResult.FAIL;
                player.getCooldowns().addCooldown(stack, 30);
                level.playSound(null, player.blockPosition(), SoundEvents.NOTE_BLOCK_BASEDRUM.value(), SoundSource.PLAYERS, 3f, 0.7f);
                Noise.emit((ServerLevel) level, player.position(), 45);
                Mood.add(sp, Stat.BOREDOM, -3);
                return InteractionResult.SUCCESS;
            }
            case HEATER -> {
                ItemStack meal = find(sp, "mre");
                if (meal.isEmpty()) {
                    say(sp, "You need a ration meal to heat.");
                    return InteractionResult.FAIL;
                }
                TimedActions.start(sp, "heat_mre", "Heating a ration", 200, p -> {
                    ItemStack m = find(p, "mre"), h = p.getItemInHand(hand);
                    if (m.isEmpty() || !(h.getItem() instanceof FieldGearItem)) return;
                    h.shrink(1);
                    m.shrink(1);
                    ItemStack hot = new ItemStack(m.getItem());
                    CustomData.update(DataComponents.CUSTOM_DATA, hot, tag -> tag.putBoolean("hot", true));
                    hot.set(DataComponents.CUSTOM_NAME, Component.literal("Hot Ration Meal").withStyle(s -> s.withItalic(false)));
                    if (!p.getInventory().add(hot)) p.spawnAtLocation((ServerLevel) p.level(), hot);
                    say(p, "The heater hisses. A hot meal, and no smoke.");
                });
                return InteractionResult.CONSUME;
            }
            case PARACHUTE -> {
                if (!(has(sp, "medical_scissors") || has(sp, "kitchen_knife") || has(sp, "pocket_knife") || has(sp, "machete"))) {
                    say(sp, "You need scissors or a knife to cut it up.");
                    return InteractionResult.FAIL;
                }
                TimedActions.start(sp, "cut_chute", "Cutting up the parachute", 300, p -> {
                    ItemStack h = p.getItemInHand(hand);
                    if (!(h.getItem() instanceof FieldGearItem)) return;
                    h.shrink(1);
                    give(p, "rag", 8);
                    give(p, "rope", 2);
                    give(p, "tarp", 1);
                    say(p, "Eight rags, two lengths of cord and a canopy panel.");
                });
                return InteractionResult.CONSUME;
            }
            case MANUAL -> {
                TimedActions.start(sp, "read_survival_manual", "Reading the field manual", 600, p -> {
                    ItemStack h = p.getItemInHand(hand);
                    if (!(h.getItem() instanceof FieldGearItem)) return;
                    boolean again = CustomData.EMPTY != h.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY)
                            && h.get(DataComponents.CUSTOM_DATA).copyTag().getBooleanOr("read", false);
                    int xp = again ? 15 : 75;
                    Skills.addXp(p, Skill.TRAPPING, xp);
                    Skills.addXp(p, Skill.FORAGING, xp);
                    CustomData.update(DataComponents.CUSTOM_DATA, h, tag -> tag.putBoolean("read", true));
                    say(p, again ? "You skim it again. A little more sinks in." : "Snares, deadfalls, shelters. You take notes.");
                });
                return InteractionResult.CONSUME;
            }
            default -> {
                return InteractionResult.PASS;
            }
        }
    }

    private static ItemStack find(ServerPlayer sp, String id) {
        var item = LootItems.item(id);
        if (item == null) return ItemStack.EMPTY;
        for (int i = 0; i < sp.getInventory().getContainerSize(); i++) {
            ItemStack s = sp.getInventory().getItem(i);
            if (s.is(item)) return s;
        }
        return ItemStack.EMPTY;
    }

    private static boolean has(ServerPlayer sp, String id) {
        return !find(sp, id).isEmpty();
    }

    private static void give(ServerPlayer sp, String id, int count) {
        var item = LootItems.item(id);
        if (item == null) return;
        ItemStack s = new ItemStack(item, count);
        if (!sp.getInventory().add(s)) sp.spawnAtLocation((ServerLevel) sp.level(), s);
    }

    private static void say(ServerPlayer sp, String text) {
        sp.sendOverlayMessage(Component.literal(text).withStyle(ChatFormatting.GRAY));
    }
}
