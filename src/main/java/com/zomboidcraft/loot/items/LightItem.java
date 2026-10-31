package com.zomboidcraft.loot.items;

import com.mojang.serialization.Codec;
import com.zomboidcraft.ZomboidCraft;
import java.util.Map;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * A light you carry: a torch (flashlight), a lantern, a candle or a glow stick. Use it to switch it on or off; held in
 * either hand it lights the world round you (HandLights, on each computer). It runs down while it's on, a second at a
 * time, whether it's in your hand or left on in your pocket: a battery light then needs a fresh battery (one from your
 * inventory goes in the next time you switch it on), a lantern more kerosene, and a candle or glow stick is gone.
 * A flame (lantern, candle) needs a lighter or matches to light, and goes out when it isn't in your hand.
 */
public class LightItem extends LootItem {
    public enum Kind { BEAM, AROUND }

    /** Switched on. */
    public static final DataComponentType<Boolean> LIT = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
            ZomboidCraft.id("lit"), DataComponentType.<Boolean>builder()
                    .persistent(Codec.BOOL).networkSynchronized(ByteBufCodecs.BOOL).build());
    /** Seconds of light left. */
    public static final DataComponentType<Integer> CHARGE = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
            ZomboidCraft.id("charge"), DataComponentType.<Integer>builder()
                    .persistent(Codec.INT).networkSynchronized(ByteBufCodecs.VAR_INT).build());

    /** id -> kind, brightness (light level 1-15), what refills it (null: used up when it's out), seconds per refill, a flame. */
    record Spec(Kind kind, int brightness, @Nullable String fuel, String fuelName, int seconds, boolean flame) {
    }

    static final Map<String, Spec> SPECS = Map.of(
            "flashlight", new Spec(Kind.BEAM, 13, "aa_battery", "an AA battery", 1200, false),
            "big_flashlight", new Spec(Kind.BEAM, 15, "d_battery", "a D battery", 1800, false),
            "penlight", new Spec(Kind.BEAM, 10, "aa_battery", "an AA battery", 900, false),
            "headlamp", new Spec(Kind.BEAM, 12, "aa_battery", "an AA battery", 1500, false),
            "lantern_battery_light", new Spec(Kind.AROUND, 14, "lantern_battery", "a lantern battery", 3600, false),
            "kerosene_lantern", new Spec(Kind.AROUND, 14, "kerosene", "kerosene", 2400, true),
            "candle", new Spec(Kind.AROUND, 10, null, "", 600, true),
            "church_candle", new Spec(Kind.AROUND, 11, null, "", 1200, true),
            "glow_stick", new Spec(Kind.AROUND, 7, null, "", 480, false),
            "angle_flashlight", new Spec(Kind.BEAM, 12, "aa_battery", "an AA battery", 2400, false));

    private final Spec spec;

    LightItem(Properties properties, Spec spec) {
        super(properties.stacksTo(1).component(LIT, false).component(CHARGE, spec.seconds()));
        this.spec = spec;
    }

    public Kind kind() {
        return spec.kind();
    }

    public int brightness() {
        return spec.brightness();
    }

    public static boolean lit(ItemStack stack) {
        return stack.getItem() instanceof LightItem && stack.getOrDefault(LIT, false);
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(LightItem::tick);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide() || !(player instanceof ServerPlayer sp)) {
            return InteractionResult.SUCCESS;
        }
        if (stack.getOrDefault(LIT, false)) {
            stack.set(LIT, false);
            sp.level().playSound(null, sp.blockPosition(), spec.flame() ? SoundEvents.CANDLE_EXTINGUISH
                    : SoundEvents.STONE_BUTTON_CLICK_OFF, SoundSource.PLAYERS, 0.6f, 1.2f);
            return InteractionResult.SUCCESS;
        }
        if (stack.getOrDefault(CHARGE, 0) <= 0) {
            ItemStack fuel = spec.fuel() == null ? ItemStack.EMPTY : find(sp, spec.fuel());
            if (fuel.isEmpty()) {
                say(sp, spec.fuel() == null ? "It's spent." : "It needs " + spec.fuelName() + ".");
                return InteractionResult.FAIL;
            }
            fuel.shrink(1);
            stack.set(CHARGE, spec.seconds());
            say(sp, "Put in " + spec.fuelName() + ".");
        }
        if (spec.flame() && !FireItem.strike(sp)) {
            say(sp, "You need a lighter or matches to light it.");
            return InteractionResult.FAIL;
        }
        stack.set(LIT, true);
        sp.level().playSound(null, sp.blockPosition(), spec.flame() ? SoundEvents.FLINTANDSTEEL_USE
                : SoundEvents.STONE_BUTTON_CLICK_ON, SoundSource.PLAYERS, 0.6f, 1.2f);
        return InteractionResult.SUCCESS;
    }

    private static ItemStack find(ServerPlayer sp, String id) {
        var item = LootItems.item(id);
        Inventory inv = sp.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (item != null && s.is(item)) {
                return s;
            }
        }
        return ItemStack.EMPTY;
    }

    static void say(ServerPlayer sp, String text) {
        sp.sendOverlayMessage(Component.literal(text).withStyle(ChatFormatting.GRAY));
    }

    /** Once a second: every light left on runs down a second, and flames out of hand go out. */
    private static void tick(MinecraftServer server) {
        if (server.getTickCount() % 20 != 0) {
            return;
        }
        for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
            Inventory inv = sp.getInventory();
            for (int i = 0; i < inv.getContainerSize(); i++) {
                ItemStack s = inv.getItem(i);
                if (!(s.getItem() instanceof LightItem light) || !s.getOrDefault(LIT, false)) {
                    continue;
                }
                boolean held = s == sp.getMainHandItem() || s == sp.getOffhandItem();
                if (light.spec.flame() && !held) {
                    s.set(LIT, false);
                    continue;
                }
                int left = s.getOrDefault(CHARGE, 0) - 1;
                if (left > 0) {
                    s.set(CHARGE, left);
                } else if (light.spec.fuel() == null) {
                    say(sp, "Your " + s.getHoverName().getString().toLowerCase() + " burnt out.");
                    s.shrink(1);
                } else {
                    s.set(CHARGE, 0);
                    s.set(LIT, false);
                    say(sp, "Your " + s.getHoverName().getString().toLowerCase() + " died. It needs " + light.spec.fuelName() + ".");
                }
            }
        }
    }

    @Override
    public boolean allowComponentsUpdateAnimation(Player player, InteractionHand hand, ItemStack oldStack, ItemStack newStack) {
        return false;
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return stack.getOrDefault(CHARGE, spec.seconds()) < spec.seconds();
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.round(13f * stack.getOrDefault(CHARGE, 0) / spec.seconds());
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return 0xE8D070;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        int left = stack.getOrDefault(CHARGE, 0);
        tooltip.accept(Component.literal((stack.getOrDefault(LIT, false) ? "On, " : "Off, ")
                + (left / 60) + " min left").withStyle(ChatFormatting.GRAY));
        super.appendHoverText(stack, context, display, tooltip, flag);
    }
}
