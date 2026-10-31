package com.zomboidcraft.world.files;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.zomboidcraft.ZomboidCraft;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * The files that tell how it started: memos, test logs, army orders, notes, each one a block
 * (rl_doc_ID) lying where someone dropped it, at Ridgeview Labs first. The interact key (or a
 * click) on one reads it; the kit's Files page keeps every one you've read, grouped by set.
 * The texts are data/zomboidcraft/files/files.json, read on both sides from the jar; what each
 * player has found is an attachment that persists and syncs to them.
 */
public final class LabFiles {
    public record File(String id, String set, String title, String from, String date, String body) {
    }

    private static final Codec<List<String>> CODEC = Codec.STRING.listOf();
    private static final StreamCodec<io.netty.buffer.ByteBuf, List<String>> STREAM_CODEC =
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list());

    /** The ids of the files read, in the order they were found. */
    public static final AttachmentType<List<String>> FOUND = AttachmentRegistry.create(
            ZomboidCraft.id("files_found"),
            builder -> builder
                    .persistent(CODEC)
                    .initializer(List::of)
                    .syncWith(STREAM_CODEC, AttachmentSyncPredicate.targetOnly()));

    /** The interact key on a file. */
    public record Read(BlockPos pos) implements CustomPacketPayload {
        public static final Type<Read> TYPE = new Type<>(ZomboidCraft.id("file_read"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Read> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, Read::pos, Read::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    private static final String PREFIX = "rl_doc_";
    private static @Nullable Map<String, File> files;

    public static void register() {
        PayloadTypeRegistry.serverboundPlay().register(Read.TYPE, Read.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(Read.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            player.level().getServer().execute(() -> {
                if (!player.isSpectator() && player.blockPosition().distSqr(payload.pos()) < 6 * 6) {
                    String id = id(player.level().getBlockState(payload.pos()));
                    if (id != null) {
                        found(player, id);
                    }
                }
            });
        });
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            String id = id(level.getBlockState(hit.getBlockPos()));
            if (id == null) {
                return InteractionResult.PASS;
            }
            if (!level.isClientSide() && player instanceof ServerPlayer sp) {
                found(sp, id);
            }
            return InteractionResult.SUCCESS;
        });
    }

    /** The file a block is, or null. */
    public static @Nullable String id(BlockState state) {
        String path = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
        return path.startsWith(PREFIX) ? path.substring(PREFIX.length()) : null;
    }

    private static void found(ServerPlayer player, String id) {
        File f = all().get(id);
        if (f == null) {
            return;
        }
        List<String> had = player.getAttachedOrCreate(FOUND);
        if (!had.contains(id)) {
            List<String> now = new ArrayList<>(had);
            now.add(id);
            player.setAttached(FOUND, now);
            player.sendOverlayMessage(Component.literal("File found: " + f.title() + "  (kit, Files)").withStyle(ChatFormatting.GRAY));
        }
    }

    public static List<String> found(Player player) {
        return player.getAttachedOrCreate(FOUND);
    }

    /** Every file, by id, in the order of the json. */
    public static Map<String, File> all() {
        if (files == null) {
            Map<String, File> m = new LinkedHashMap<>();
            try (InputStream in = LabFiles.class.getResourceAsStream("/data/zomboidcraft/files/files.json")) {
                if (in != null) {
                    JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
                    for (JsonElement e : root.getAsJsonArray("files")) {
                        JsonObject o = e.getAsJsonObject();
                        File f = new File(o.get("id").getAsString(), o.get("set").getAsString(), o.get("title").getAsString(),
                                o.get("from").getAsString(), o.get("date").getAsString(), o.get("body").getAsString());
                        m.put(f.id(), f);
                    }
                }
            } catch (Exception e) {
                ZomboidCraft.LOGGER.error("Couldn't read the files", e);
            }
            files = Collections.unmodifiableMap(m);
        }
        return files;
    }

    /** True if the block at pos is a file (client or server). */
    public static boolean isFile(Level level, BlockPos pos) {
        return id(level.getBlockState(pos)) != null;
    }

    private LabFiles() {
    }
}
