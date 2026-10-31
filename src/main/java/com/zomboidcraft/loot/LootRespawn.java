package com.zomboidcraft.loot;

import com.mojang.serialization.Codec;
import com.zomboidcraft.ModBlocks;
import com.zomboidcraft.ZcConfig;
import com.zomboidcraft.ZomboidCraft;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.phys.Vec3;

/**
 * Zomboid's loot respawn: with Hours For Loot Respawn above 0, a container we stocked fills again from its own table
 * that many in-game hours after it was first opened, unless it holds Max Items For Loot Respawn or more, someone has
 * been round it within Seen Hours Prevent Loot Respawn (here: a player within 64 blocks, checked as the hours pass),
 * or, with Construction Prevents Loot Respawn, the place has been barricaded. Each container is remembered (table and
 * when it was opened) from its first roll, on the overworld.
 */
public final class LootRespawn {
    private static final int HOUR = 1000, NEAR = 64;
    /** pos -> "table@gameTime" of the last roll, or of the last time a player was near it. */
    private static final AttachmentType<Map<Long, String>> ROLLED = AttachmentRegistry.create(ZomboidCraft.id("loot_rolled"),
            b -> b.persistent(Codec.unboundedMap(Codec.STRING.xmap(Long::parseLong, String::valueOf), Codec.STRING))
                    .initializer(Map::of));

    private LootRespawn() {
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(LootRespawn::tick);
    }

    /** A container rolled its table (LootSettings, as it's first opened). */
    static void rolled(ServerLevel level, Vec3 at, String table) {
        if (at == null || level.dimension() != net.minecraft.world.level.Level.OVERWORLD) {
            return;
        }
        Map<Long, String> m = new HashMap<>(level.getAttachedOrElse(ROLLED, Map.of()));
        m.put(BlockPos.containing(at).asLong(), table + "@" + level.getGameTime());
        level.setAttached(ROLLED, Map.copyOf(m));
    }

    private static void tick(MinecraftServer server) {
        ServerLevel level = server.overworld();
        ZcConfig.Loot cfg = ZcConfig.get().loot;
        if (cfg.hoursForLootRespawn <= 0 || level.getGameTime() % 1200 != 0) {
            return;
        }
        Map<Long, String> m = level.getAttachedOrElse(ROLLED, Map.of());
        if (m.isEmpty()) {
            return;
        }
        long now = level.getGameTime();
        Map<Long, String> next = new HashMap<>(m);
        boolean changed = false;
        for (Iterator<Map.Entry<Long, String>> it = next.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Long, String> e = it.next();
            BlockPos pos = BlockPos.of(e.getKey());
            int at = e.getValue().lastIndexOf('@');
            String table = e.getValue().substring(0, at);
            long since = Long.parseLong(e.getValue().substring(at + 1));
            if (!level.isLoaded(pos)) {
                continue;
            }
            boolean near = level.getNearestPlayer(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, NEAR, false) != null;
            if (near && cfg.seenHoursPreventLootRespawn > 0) {
                e.setValue(table + "@" + now);  // seen: the clock starts again
                changed = true;
                continue;
            }
            if (now - since < cfg.hoursForLootRespawn * HOUR || near) {
                continue;
            }
            BlockEntity be = level.getBlockEntity(pos);
            if (!(be instanceof RandomizableContainer c) || !(be instanceof Container box)) {
                it.remove();
                changed = true;
                continue;
            }
            if (count(box) >= cfg.maxItemsForLootRespawn || cfg.constructionPreventsLootRespawn && barricaded(level, pos)) {
                continue;
            }
            c.setLootTable(ResourceKey.create(Registries.LOOT_TABLE, Identifier.parse(table)), level.getRandom().nextLong());
            be.setChanged();
            it.remove();
            changed = true;
        }
        if (changed) {
            level.setAttached(ROLLED, Map.copyOf(next));
        }
    }

    private static int count(Container box) {
        int n = 0;
        for (int i = 0; i < box.getContainerSize(); i++) {
            if (!box.getItem(i).isEmpty()) {
                n++;
            }
        }
        return n;
    }

    private static boolean barricaded(ServerLevel level, BlockPos pos) {
        for (BlockPos p : BlockPos.betweenClosed(pos.offset(-8, -3, -8), pos.offset(8, 3, 8))) {
            if (level.getBlockState(p).is(ModBlocks.BARRICADE)) {
                return true;
            }
        }
        return false;
    }
}
