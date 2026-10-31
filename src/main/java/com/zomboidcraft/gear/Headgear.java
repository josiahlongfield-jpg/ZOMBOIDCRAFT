package com.zomboidcraft.gear;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.zomboidcraft.ZcConfig;
import com.zomboidcraft.ZomboidCraft;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomModelData;
import org.jetbrains.annotations.Nullable;

/**
 * Night vision on headgear. The goggles clip onto a helmet or a head mount (the {@link #GOGGLES} component on the
 * headgear holds them), flip down over the eyes with the N key and up out of the way again, and run on an AA battery
 * that's good for about half an hour ({@link #CHARGE} on the goggles, in seconds). Flipped down with charge left, the
 * wearer has night vision; the tube look itself is drawn on the client.
 *
 * A combat helmet's side rails also take a comms headset ({@link #HEADSET}), for the radios ({@link Radios}).
 *
 * The headgear's item model shows what's on it from its custom model data strings: the goggles' key (or "none"), "up"
 * or "down", then the headset's key (or "none"). Change a headgear stack through the helpers here so that stays right.
 */
public final class Headgear {
    /** Goggles clipped onto headgear, and whether they're flipped up. Absent when nothing's mounted. */
    public record Mounted(ItemStack goggles, boolean up) {
        public static final Codec<Mounted> CODEC = RecordCodecBuilder.create(i -> i.group(
                ItemStack.CODEC.fieldOf("goggles").forGetter(Mounted::goggles),
                Codec.BOOL.optionalFieldOf("up", false).forGetter(Mounted::up)).apply(i, Mounted::new));
        public static final StreamCodec<RegistryFriendlyByteBuf, Mounted> STREAM_CODEC = StreamCodec.composite(
                ItemStack.STREAM_CODEC, Mounted::goggles, ByteBufCodecs.BOOL, Mounted::up, Mounted::new);

        public Mounted {
            goggles = goggles.copyWithCount(1);
        }

        // ItemStack has no equals of its own, so compare the way vanilla's item containers do
        @Override
        public boolean equals(Object o) {
            return o instanceof Mounted other && up == other.up && ItemStack.matches(goggles, other.goggles);
        }

        @Override
        public int hashCode() {
            return ItemStack.hashItemAndComponents(goggles) * 31 + (up ? 1 : 0);
        }
    }

    /** A headset clipped onto a helmet's rails. Absent when there's none. */
    public record Rail(ItemStack headset) {
        public static final Codec<Rail> CODEC = ItemStack.CODEC.xmap(Rail::new, Rail::headset);
        public static final StreamCodec<RegistryFriendlyByteBuf, Rail> STREAM_CODEC = ItemStack.STREAM_CODEC.map(Rail::new, Rail::headset);

        public Rail {
            headset = headset.copyWithCount(1);
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Rail other && ItemStack.matches(headset, other.headset);
        }

        @Override
        public int hashCode() {
            return ItemStack.hashItemAndComponents(headset);
        }
    }

    public static final DataComponentType<Rail> HEADSET = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
            ZomboidCraft.id("headset"), DataComponentType.<Rail>builder()
                    .persistent(Rail.CODEC).networkSynchronized(Rail.STREAM_CODEC).cacheEncoding().build());

    public static final DataComponentType<Mounted> GOGGLES = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
            ZomboidCraft.id("goggles"), DataComponentType.<Mounted>builder()
                    .persistent(Mounted.CODEC).networkSynchronized(Mounted.STREAM_CODEC).cacheEncoding().build());

    /** Seconds of battery the goggles have left. Absent on goggles nobody has picked up yet: found ones are part used. */
    public static final DataComponentType<Integer> CHARGE = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
            ZomboidCraft.id("battery"), DataComponentType.<Integer>builder()
                    .persistent(Codec.INT).networkSynchronized(ByteBufCodecs.VAR_INT).build());

    /** Battery seconds used but not yet written to the worn goggles, per player (written every few seconds). */
    private static final Map<UUID, Integer> PENDING = new HashMap<>();
    /** Who had powered goggles down last tick, to play the tube's whine as it comes on and say when it dies. */
    private static final Map<UUID, Boolean> WAS_ON = new HashMap<>();
    private static int ticks;

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(Headgear::tick);
    }

    // ---- reading and changing stacks ----

    public static @Nullable Mounted mounted(ItemStack headgear) {
        return headgear.getItem() instanceof HeadGearItem ? headgear.get(GOGGLES) : null;
    }

    /** Clips goggles onto headgear, flipped up. Returns what was on it before, or empty. */
    public static ItemStack mount(ItemStack headgear, ItemStack goggles) {
        Mounted old = headgear.get(GOGGLES);
        headgear.set(GOGGLES, new Mounted(goggles, true));
        refreshModel(headgear);
        return old == null ? ItemStack.EMPTY : old.goggles();
    }

    /** Takes the goggles off headgear. Returns them, or empty. */
    public static ItemStack unmount(ItemStack headgear) {
        Mounted old = headgear.remove(GOGGLES);
        refreshModel(headgear);
        return old == null ? ItemStack.EMPTY : old.goggles();
    }

    public static void setUp(ItemStack headgear, boolean up) {
        Mounted m = headgear.get(GOGGLES);
        if (m != null && m.up() != up) {
            headgear.set(GOGGLES, new Mounted(m.goggles(), up));
            refreshModel(headgear);
        }
    }

    /** Changes the mounted goggles (their battery) without touching how they sit. */
    private static void setGoggles(ItemStack headgear, ItemStack goggles) {
        Mounted m = headgear.get(GOGGLES);
        if (m != null) {
            headgear.set(GOGGLES, new Mounted(goggles, m.up()));
        }
    }

    /** Whether this headgear has side rails a headset clips onto. */
    public static boolean hasRails(ItemStack headgear) {
        return headgear.getItem() instanceof HeadGearItem item && item.rails();
    }

    /** The headset on headgear's rails, or null. */
    public static @Nullable ItemStack headset(ItemStack headgear) {
        Rail rail = headgear.getItem() instanceof HeadGearItem ? headgear.get(HEADSET) : null;
        return rail == null ? null : rail.headset();
    }

    /** Clips a headset onto headgear's rails. Returns the one that was on them, or empty. */
    public static ItemStack mountHeadset(ItemStack headgear, ItemStack headset) {
        Rail old = headgear.get(HEADSET);
        headgear.set(HEADSET, new Rail(headset));
        refreshModel(headgear);
        return old == null ? ItemStack.EMPTY : old.headset();
    }

    public static ItemStack unmountHeadset(ItemStack headgear) {
        Rail old = headgear.remove(HEADSET);
        refreshModel(headgear);
        return old == null ? ItemStack.EMPTY : old.headset();
    }

    /** What the item model draws: the goggles' key and how they sit, and the headset's key, or nothing. */
    public static void refreshModel(ItemStack headgear) {
        Mounted m = headgear.get(GOGGLES);
        Rail rail = headgear.get(HEADSET);
        String goggles = m != null && m.goggles().getItem() instanceof NightVisionItem nv ? nv.generation().key : "none";
        String headset = rail == null ? "none" : BuiltInRegistries.ITEM.getKey(rail.headset().getItem()).getPath();
        if (goggles.equals("none") && headset.equals("none")) {
            headgear.remove(DataComponents.CUSTOM_MODEL_DATA);
            return;
        }
        headgear.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(List.of(), List.of(),
                List.of(goggles, m == null || m.up() ? "up" : "down", headset), List.of()));
    }

    // ---- the battery ----

    /** A fresh AA's life in seconds. */
    public static int fullCharge() {
        return Math.max(1, (int) Math.round(ZcConfig.get().nightVision.batteryMinutes * 60));
    }

    /** Seconds left in the goggles; a stack nobody has had yet counts as full. */
    public static int charge(ItemStack goggles) {
        return goggles.getOrDefault(CHARGE, fullCharge());
    }

    public static float chargeFraction(ItemStack goggles) {
        return Mth.clamp(charge(goggles) / (float) fullCharge(), 0f, 1f);
    }

    public static boolean hasPower(ItemStack goggles) {
        return !ZcConfig.get().nightVision.batteries || charge(goggles) > 0;
    }

    /** Puts a fresh battery in loose goggles. The old one is thrown away. */
    public static void freshBattery(ItemStack goggles) {
        goggles.set(CHARGE, fullCharge());
    }

    /**
     * Swaps the battery in the goggles on a stack: loose goggles, or headgear with goggles on it. Uses one battery
     * from {@code battery}. Returns false if there are no goggles to put it in.
     */
    public static boolean swapBattery(ItemStack target, ItemStack battery, Player player) {
        if (!(battery.getItem() instanceof BatteryItem)) {
            return false;
        }
        if (target.getItem() instanceof NightVisionItem) {
            target.set(CHARGE, fullCharge() * BatteryItem.lasts(battery));
        } else if (mounted(target) != null) {
            if (player instanceof ServerPlayer sp) {
                PENDING.remove(sp.getUUID());
            }
            ItemStack goggles = mounted(target).goggles().copy();
            goggles.set(CHARGE, fullCharge() * BatteryItem.lasts(battery));
            setGoggles(target, goggles);
        } else {
            return false;
        }
        battery.consume(1, player);
        player.playSound(SoundEvents.ITEM_FRAME_REMOVE_ITEM, 0.5f, 1.6f);
        return true;
    }

    // ---- on the head ----

    /** The N key: flips the goggles on your headgear down over your eyes or up out of the way. */
    public static void flip(ServerPlayer player) {
        ItemStack head = player.getItemBySlot(EquipmentSlot.HEAD);
        Mounted m = mounted(head);
        if (m == null) {
            player.sendOverlayMessage(Component.literal(head.getItem() instanceof HeadGearItem
                    ? "No night vision on your " + head.getHoverName().getString().toLowerCase(java.util.Locale.ROOT) + "."
                    : "You're not wearing any night vision.").withStyle(ChatFormatting.GRAY));
            return;
        }
        flush(player, head);
        m = mounted(head);
        boolean down = m.up();
        setUp(head, !down);
        player.level().playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.STONE_BUTTON_CLICK_OFF,
                SoundSource.PLAYERS, 0.35f, down ? 1.5f : 1.8f);
        if (down && !hasPower(m.goggles())) {
            player.sendOverlayMessage(Component.literal("The battery's dead.").withStyle(ChatFormatting.GRAY));
        }
    }

    private static void tick(MinecraftServer server) {
        ticks++;
        ZcConfig.NightVision cfg = ZcConfig.get().nightVision;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            UUID id = player.getUUID();
            ItemStack head = player.getItemBySlot(EquipmentSlot.HEAD);
            Mounted m = mounted(head);
            boolean down = m != null && !m.up();
            boolean on = down && hasPower(m.goggles()) && !player.isSpectator();
            if (on) {
                MobEffectInstance current = player.getEffect(MobEffects.NIGHT_VISION);
                if (current == null || current.isAmbient() && current.getDuration() < 220) {
                    player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 400, 0, true, false, false));
                }
                if (!WAS_ON.getOrDefault(id, false)) {
                    // the tube's faint whine as it warms up
                    player.level().playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.BEACON_ACTIVATE,
                            SoundSource.PLAYERS, 0.12f, 2.0f);
                }
                if (cfg.batteries && !player.isCreative() && ticks % 20 == 0) {
                    int used = PENDING.merge(id, 1, Integer::sum);
                    if (charge(m.goggles()) - used <= 0 || used >= Math.max(1, cfg.saveEverySeconds)) {
                        flush(player, head);
                        if (!hasPower(mounted(head).goggles())) {
                            player.level().playSound(null, player.getX(), player.getEyeY(), player.getZ(),
                                    SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.15f, 2.0f);
                            player.sendOverlayMessage(Component.literal("The night vision fades out. The battery's dead.")
                                    .withStyle(ChatFormatting.GRAY));
                        }
                    }
                }
            } else {
                if (PENDING.containsKey(id)) {
                    flush(player, head);
                }
                MobEffectInstance current = player.getEffect(MobEffects.NIGHT_VISION);
                // only ours: a potion's is shown and isn't ambient
                if (current != null && current.isAmbient() && !current.isVisible() && current.getDuration() <= 400) {
                    player.removeEffect(MobEffects.NIGHT_VISION);
                }
            }
            WAS_ON.put(id, on);
            if (ticks % 20 == 0) {
                settleFound(player);
            }
        }
        if (ticks % 1200 == 0) {
            WAS_ON.keySet().retainAll(server.getPlayerList().getPlayers().stream().map(Player::getUUID).toList());
        }
    }

    /** Writes the battery used since the last write to the goggles on this player's head. */
    private static void flush(ServerPlayer player, ItemStack head) {
        Integer used = PENDING.remove(player.getUUID());
        Mounted m = mounted(head);
        if (used == null || m == null) {
            return;
        }
        ItemStack goggles = m.goggles().copy();
        goggles.set(CHARGE, Math.max(0, charge(goggles) - used));
        setGoggles(head, goggles);
    }

    /** Goggles found in the world have been sitting with a battery in: somewhere between part used and nearly full. */
    private static void settleFound(ServerPlayer player) {
        Inventory inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.getItem() instanceof NightVisionItem && !stack.has(CHARGE)) {
                stack.set(CHARGE, foundCharge(player));
                inventory.setChanged();
            } else if (mounted(stack) != null && !mounted(stack).goggles().has(CHARGE)) {
                ItemStack goggles = mounted(stack).goggles().copy();
                goggles.set(CHARGE, foundCharge(player));
                setGoggles(stack, goggles);
                inventory.setChanged();
            }
        }
    }

    private static int foundCharge(ServerPlayer player) {
        if (player.isCreative()) {
            return fullCharge();
        }
        ZcConfig.NightVision cfg = ZcConfig.get().nightVision;
        double lo = Mth.clamp(Math.min(cfg.foundChargeMin, cfg.foundChargeMax), 0, 1);
        double hi = Mth.clamp(Math.max(cfg.foundChargeMin, cfg.foundChargeMax), 0, 1);
        return (int) Math.round(fullCharge() * (lo + player.getRandom().nextDouble() * (hi - lo)));
    }

    /** "24 min left", "40 s left", "Dead". */
    public static String chargeText(ItemStack goggles) {
        int left = charge(goggles);
        if (left <= 0) {
            return "Battery dead";
        }
        return "Battery " + Math.round(chargeFraction(goggles) * 100) + "%, "
                + (left >= 90 ? Math.round(left / 60f) + " min left" : left + " s left");
    }

    private Headgear() {
    }
}
