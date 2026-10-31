package com.zomboidcraft.loot.items;

import com.zomboidcraft.survival.Mood;
import com.zomboidcraft.survival.Mood.Stat;
import com.zomboidcraft.survival.TimedActions;
import com.zomboidcraft.zombie.Noise;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;

/**
 * Something to take your mind off things (Mood): reading, a game, music, a keepsake, a smoke. Using it is a timed
 * action; when it's done it eases boredom, unhappiness and stress. Papers you've read are marked and only help a
 * little the second time (Zomboid's rule). Instruments are loud. Smokes need a light and go a cigarette at a time.
 */
public class LeisureItem extends LootItem {
    /** seconds: how long it takes; the three moods eased, in points; noise: blocks zombies hear it; how: see Kind. */
    record Fun(int seconds, int boredom, int unhappiness, int stress, int noise, Kind how) {
    }

    enum Kind { READ, PLAY, KEEPSAKE, SMOKE, CROSSWORD, TOGETHER }

    static final Map<String, Fun> FUN = new HashMap<>();

    private static void f(String id, int seconds, int boredom, int unhappiness, int stress, int noise, Kind how) {
        FUN.put(id, new Fun(seconds, boredom, unhappiness, stress, noise, how));
    }

    static {
        f("paperback", 40, 40, 15, 5, 0, Kind.READ);
        f("hardback", 60, 50, 20, 5, 0, Kind.READ);
        f("bible", 30, 10, 25, 10, 0, Kind.READ);
        f("hymn_book", 20, 10, 20, 5, 0, Kind.READ);
        f("magazine", 15, 25, 5, 0, 0, Kind.READ);
        f("comic_book", 15, 30, 10, 0, 0, Kind.READ);
        f("newspaper", 15, 20, 0, 0, 0, Kind.READ);
        f("childrens_book", 10, 10, 15, 0, 0, Kind.READ);
        f("textbook", 40, 15, 0, 0, 0, Kind.READ);
        f("encyclopedia", 40, 25, 5, 0, 0, Kind.READ);
        f("crossword_book", 30, 40, 5, 5, 0, Kind.CROSSWORD);
        f("playing_cards", 30, 30, 10, 5, 0, Kind.TOGETHER);
        f("dice", 15, 15, 5, 0, 0, Kind.TOGETHER);
        f("board_game", 60, 50, 20, 5, 0, Kind.TOGETHER);
        f("jigsaw_puzzle", 60, 45, 10, 10, 0, Kind.PLAY);
        f("rubiks_cube", 20, 25, 0, 0, 0, Kind.PLAY);
        f("yo_yo", 10, 15, 5, 0, 0, Kind.PLAY);
        f("model_kit", 60, 50, 15, 5, 0, Kind.PLAY);
        f("harmonica", 20, 25, 20, 10, 10, Kind.PLAY);
        f("acoustic_guitar", 40, 40, 30, 15, 18, Kind.PLAY);
        f("trumpet", 20, 25, 20, 5, 30, Kind.PLAY);
        f("basketball", 20, 30, 10, 10, 6, Kind.PLAY);
        f("football", 20, 25, 10, 10, 4, Kind.PLAY);
        f("baseball", 15, 20, 5, 5, 0, Kind.PLAY);
        f("frisbee", 15, 20, 10, 5, 0, Kind.PLAY);
        f("skateboard", 20, 30, 10, 5, 8, Kind.PLAY);
        f("rc_car", 30, 40, 15, 0, 6, Kind.PLAY);
        f("photograph", 5, 0, 15, 5, 0, Kind.KEEPSAKE);
        f("polaroid_photo", 5, 0, 10, 5, 0, Kind.KEEPSAKE);
        f("teddy_bear", 5, 0, 15, 10, 0, Kind.KEEPSAKE);
        f("rosary", 10, 0, 15, 15, 0, Kind.KEEPSAKE);
        f("snow_globe", 5, 5, 10, 0, 0, Kind.KEEPSAKE);
        f("cigarettes", 8, 5, 5, 25, 0, Kind.SMOKE);
        f("cigars", 15, 10, 10, 35, 0, Kind.SMOKE);
        f("pipe_tobacco", 15, 10, 10, 30, 0, Kind.SMOKE);
    }

    private final Fun fun;

    LeisureItem(Properties properties, String id) {
        super(properties);
        this.fun = FUN.get(id);
    }

    private static boolean read(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data != null && data.copyTag().getBooleanOr("read", false);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!(player instanceof ServerPlayer sp) || TimedActions.busy(player)) return InteractionResult.CONSUME;
        ItemStack stack = player.getItemInHand(hand);
        if (fun.how() == Kind.CROSSWORD && !(has(sp, "pen") || has(sp, "pencil"))) {
            say(sp, "You need a pen or a pencil.");
            return InteractionResult.FAIL;
        }
        if (fun.how() == Kind.SMOKE && !has(sp, "lighter") && !has(sp, "brass_lighter") && !has(sp, "matches")
                && !has(sp, "firestarter")) {
            say(sp, "You need a light.");
            return InteractionResult.FAIL;
        }
        String verb = switch (fun.how()) {
            case READ, CROSSWORD -> "Reading";
            case SMOKE -> "Smoking";
            case KEEPSAKE -> "Looking at";
            default -> "Playing with";
        };
        ItemStack what = stack.copyWithCount(1);
        TimedActions.start(sp, "fun:" + stack.getItem(), verb + " the " + stack.getHoverName().getString().toLowerCase(java.util.Locale.ROOT),
                fun.seconds() * 20, p -> finish(p, hand, what));
        return InteractionResult.CONSUME;
    }

    private void finish(ServerPlayer player, InteractionHand hand, ItemStack what) {
        ItemStack stack = player.getItemInHand(hand);
        if (!ItemStack.isSameItem(stack, what)) return;
        double k = 1;
        boolean paper = fun.how() == Kind.READ || fun.how() == Kind.CROSSWORD;
        if (paper && read(stack)) k = 0.25;
        if (fun.how() == Kind.TOGETHER && !player.level().getEntitiesOfClass(Player.class,
                player.getBoundingBox().inflate(4), o -> o != player).isEmpty()) {
            k = 1.6;     // a game with someone else is worth far more
        }
        if (fun.how() == Kind.SMOKE && !FireItem.strike(player)) {
            say(player, "You need a light.");
            return;
        }
        Mood.add(player, Stat.BOREDOM, (int) -Math.round(fun.boredom() * k));
        Mood.add(player, Stat.UNHAPPINESS, (int) -Math.round(fun.unhappiness() * k));
        Mood.add(player, Stat.STRESS, (int) -Math.round(fun.stress() * k));
        if (fun.noise() > 0) Noise.emit((ServerLevel) player.level(), player.position(), fun.noise());
        if (paper && !read(stack)) {
            if (stack.getCount() > 1) {
                ItemStack done = stack.split(1);
                CustomData.update(DataComponents.CUSTOM_DATA, done, tag -> tag.putBoolean("read", true));
                if (!player.getInventory().add(done)) player.spawnAtLocation((ServerLevel) player.level(), done);
            } else {
                CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putBoolean("read", true));
            }
        }
        if (fun.how() == Kind.SMOKE) {
            stack.shrink(1);
            com.zomboidcraft.survival.Ailments.set(player, "smoke", Math.min(100, com.zomboidcraft.survival.Ailments.get(player, "smoke") + 4));
        }
        if (fun.how() == Kind.CROSSWORD && player.getRandom().nextInt(10) == 0) {
            say(player, "You finish the last puzzle.");
            stack.shrink(1);
        }
        say(player, k < 1 ? "You've read it before. It helps a little." : "That helped.");
    }

    private static boolean has(ServerPlayer player, String id) {
        net.minecraft.world.item.Item item = LootItems.item(id);
        return item != null && player.getInventory().contains(new ItemStack(item));
    }

    private static void say(ServerPlayer player, String text) {
        player.sendOverlayMessage(Component.literal(text).withStyle(ChatFormatting.GRAY));
    }
}
