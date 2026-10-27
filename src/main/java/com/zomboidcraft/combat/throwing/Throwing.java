package com.zomboidcraft.combat.throwing;

import com.zomboidcraft.ZcConfig;
import com.zomboidcraft.ZomboidCraft;
import com.zomboidcraft.combat.ModSounds;
import com.zomboidcraft.combat.Stamina;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

/**
 * Throwing things: the thrown object, the contained fire it can start, and the wind-up. Fused throwables (grenades,
 * the pipe bomb) are live from the moment the wind-up starts; we remember when, so holding on too long sets one off
 * in the hand and putting it away mid wind-up drops it at your feet, still burning down.
 */
public final class Throwing {
    private static final ResourceKey<Block> FIRE_KEY = ResourceKey.create(Registries.BLOCK, ZomboidCraft.id("contained_fire"));
    public static final Block FIRE = Registry.register(BuiltInRegistries.BLOCK, FIRE_KEY, new ContainedFireBlock(BlockBehaviour.Properties.of()
            .mapColor(MapColor.FIRE).replaceable().noCollision().instabreak().lightLevel(s -> 15).sound(SoundType.WOOL)
            .randomTicks().noLootTable().setId(FIRE_KEY)));

    private static final ResourceKey<EntityType<?>> THROWN_KEY = ResourceKey.create(Registries.ENTITY_TYPE, ZomboidCraft.id("thrown"));
    public static final EntityType<Thrown> THROWN = Registry.register(BuiltInRegistries.ENTITY_TYPE, THROWN_KEY,
            EntityType.Builder.<Thrown>of(Thrown::new, MobCategory.MISC)
                    .sized(0.25f, 0.25f).clientTrackingRange(8).updateInterval(1).fireImmune().noLootTable()
                    .build(THROWN_KEY));

    /** Who has a live one in hand: what it is and the game time the pin came out. */
    private record Live(ThrowKind kind, long since) {
    }

    private static final Map<UUID, Live> LIVE = new HashMap<>();

    private Throwing() {
    }

    public static void register() {
        ContainedFire.register();
        Smoke.register();
        ServerTickEvents.END_SERVER_TICK.register(Throwing::tick);
    }

    /** The wind-up starts: pin out, fuse or rag lit. */
    static void windUp(ServerPlayer player, ThrowKind kind) {
        SoundEvent sound = switch (kind) {
            case FLASHBANG, FRAG, SMOKE -> ModSounds.PIN_PULL;
            case PIPE_BOMB, FIRECRACKERS -> ModSounds.FUSE;
            case MOLOTOV -> ModSounds.FLARE_STRIKE;
            default -> null;
        };
        if (sound != null) {
            player.level().playSound(null, player.getX(), player.getEyeY(), player.getZ(), sound, SoundSource.PLAYERS, 0.7f, 1f);
        }
        if (kind.fused && !LIVE.containsKey(player.getUUID())) {
            LIVE.put(player.getUUID(), new Live(kind, player.level().getGameTime()));
        }
    }

    /** Ticks left on the live one in this player's hand; a lot if they aren't holding one. */
    static int fuseLeft(ServerPlayer player) {
        Live live = LIVE.get(player.getUUID());
        return live == null ? Integer.MAX_VALUE : left(live, player.level().getGameTime());
    }

    private static int left(Live live, long now) {
        return (int) (live.kind.fuse() - (now - live.since));
    }

    /**
     * Lets go of one from stack: thrown at a wind-up of power (0 to 1), or, inHand, gone off before it left the hand.
     * One is used up (not in creative) and the throw costs endurance.
     */
    static void release(ServerPlayer player, ItemStack stack, float power, boolean inHand) {
        if (!(stack.getItem() instanceof ThrowableItem item) || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        ThrowKind kind = item.kind();
        Live live = LIVE.remove(player.getUUID());
        int fuse = -1;
        if (kind.fused) {
            fuse = live == null ? kind.fuse() : Math.max(1, left(live, level.getGameTime()));
        }
        if (inHand) {
            Thrown.launch(level, player, stack, 0, 1);
        } else {
            Thrown.launch(level, player, stack, ThrowableItem.speed(kind, power), fuse);
            level.playSound(null, player.getX(), player.getEyeY(), player.getZ(), ModSounds.THROW, SoundSource.PLAYERS,
                    0.5f + 0.5f * power, 0.9f + 0.2f * power);
            Stamina.use(player, ZcConfig.get().throwing.throwEndurance * (0.5 + 0.5 * power));
            player.swing(player.getUsedItemHand(), net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
        }
        if (!player.isCreative()) {
            stack.shrink(1);
        }
    }

    /** Thrown straight off the quick-use wheel, at the wheel's own fixed wind-up. */
    public static void quickThrow(ServerPlayer player, ItemStack stack) {
        if (stack.getItem() instanceof ThrowableItem item && ZcConfig.get().throwing.enabled) {
            windUp(player, item.kind());
            release(player, stack, (float) ZcConfig.get().throwing.quickThrowPower, false);
        }
    }

    /** A live one whose wind-up stopped without a throw (item put away, or dropped) falls at the player's feet. */
    private static void tick(MinecraftServer server) {
        Iterator<Map.Entry<UUID, Live>> it = LIVE.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Live> e = it.next();
            ServerPlayer player = server.getPlayerList().getPlayer(e.getKey());
            Live live = e.getValue();
            if (player == null || !player.isAlive()) {
                it.remove();
                continue;
            }
            ItemStack using = player.getUseItem();
            if (player.isUsingItem() && using.getItem() instanceof ThrowableItem t && t.kind() == live.kind) {
                continue;
            }
            it.remove();
            ItemStack held = find(player, live.kind);
            if (!held.isEmpty() && player.level() instanceof ServerLevel level) {
                Thrown.drop(level, player, held, Math.max(1, left(live, level.getGameTime())));
                if (!player.isCreative()) {
                    held.shrink(1);
                }
            }
        }
    }

    private static ItemStack find(ServerPlayer player, ThrowKind kind) {
        var inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.getItem() instanceof ThrowableItem t && t.kind() == kind) {
                return s;
            }
        }
        return ItemStack.EMPTY;
    }
}
