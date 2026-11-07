package com.zomboidcraft.decor;

import com.zomboidcraft.ZomboidCraft;
import com.zomboidcraft.clothing.Clothes;
import com.zomboidcraft.combat.Stamina;
import com.zomboidcraft.skills.Skill;
import com.zomboidcraft.skills.Skills;
import com.zomboidcraft.survival.Mood;
import com.zomboidcraft.survival.Needs;
import com.zomboidcraft.survival.TimedActions;
import com.zomboidcraft.zombie.Noise;
import com.zomboidcraft.zombie.ZombieTypes;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * What each decor piece does beyond comfort, light, play and noise: the {@code special} column of decor.py, one
 * handler per kind. The G menu's actions come in through {@link DecorNet}; the pieces that act on their own (the
 * clock, the stove, the vending machine, the beacon, the decoys, the black light, the stained glass, the disco ball)
 * tick through {@link DecorBlock#tick}.
 */
public final class DecorUses {
    /** Specials whose block holds things (DecorEntity). */
    static final Set<String> HOLDS = Set.of("drinks_store", "cold_store", "sweets_store", "stove_heat", "dress_decoy");
    /** Specials whose block keeps a tick going whatever its ON says. */
    static final Set<String> TICKS = Set.of("chime", "stove_heat", "cold_store", "beacon", "decoy", "dress_decoy",
            "blacklight", "coloured_light", "spots");

    /** Ticks of fire one piece of fuel gives. */
    private static final int LOG = 2400, PLANK = 800, STICK = 200, COAL = 3200;
    private static final List<String> TOYS = List.of("toy_car", "toy_truck", "toy_radio", "broken_toy");
    private static final List<String> COINS = List.of("silver_coin", "gold_coin", "coin_roll");
    private static final List<String> BROADCASTS = List.of(
            "\"...this is the Emergency Broadcast System. Residents of Knox County are to remain indoors...\"",
            "\"...Louisville Memorial is no longer accepting patients. Repeat, no longer accepting...\"",
            "\"...good evening. Our top story tonight: the governor has asked the National Guard...\"",
            "\"...if a family member has been bitten, do not, I repeat, do not take them to...\"",
            "\"...WKNX, your county voice. Today's high sixty-eight, and the fair's on through Sunday...\"",
            "\"...checkpoints on the interstate will be closed from midnight. Turn back...\"",
            "\"...the exclusion zone now runs from the river to the county line...\"",
            "\"...and that was the Highway 9 Band, live from the Grange hall. Coming up after the news...\"");

    private DecorUses() {
    }

    static boolean is(DecorBlock d, String special) {
        return special.equals(d.spec.special());
    }

    // ------------------------------------------------------------------ the G menu's actions

    /** Sit and rest on a seat, recliner or massage chair: endurance back, a little calm. */
    static void rest(ServerPlayer player, ServerLevel level, BlockPos pos, DecorBlock d) {
        boolean massage = is(d, "massage");
        if (massage && !d.powered(level, pos)) {
            say(player, "It needs power.");
            return;
        }
        boolean fast = is(d, "rest_fast") || massage;
        TimedActions.start(player, "decor_rest:" + pos.asLong(), massage ? "Massage" : "Resting", 200, p -> {
            Stamina.use(p, -(fast ? 60 : 30));
            Mood.add(p, Mood.Stat.STRESS, massage ? -20 : -3);
            if (massage) {
                Mood.add(p, Mood.Stat.UNHAPPINESS, -6);
                level.playSound(null, pos, SoundEvents.BEEHIVE_WORK, SoundSource.BLOCKS, 0.6f, 0.6f);
            }
            say(p, massage ? "Every knot in your back lets go." : fast ? "You put your feet up. That's better." : "You take the weight off your feet.");
        });
    }

    /** Burns a piece of wood or coal in the stove. */
    static void fuel(ServerPlayer player, ServerLevel level, BlockPos pos, BlockState s, DecorBlock d) {
        if (!(level.getBlockEntity(pos) instanceof DecorEntity e)) {
            return;
        }
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            int burn = burn(stack);
            if (burn > 0) {
                stack.shrink(1);
                e.fuel = Math.min(e.fuel + burn, LOG * 6);
                e.setChanged();
                level.setBlock(pos, s.setValue(DecorBlock.ON, true), 3);
                level.scheduleTick(pos, d, 20);
                level.playSound(null, pos, SoundEvents.FIRECHARGE_USE, SoundSource.BLOCKS, 0.6f, 0.8f);
                say(player, "The fire takes. About " + Math.max(1, e.fuel / 1200) + " minutes of it.");
                return;
            }
        }
        say(player, "You need something to burn: firewood, planks or charcoal.");
    }

    private static int burn(ItemStack s) {
        if (s.is(ItemTags.LOGS_THAT_BURN)) return LOG;
        if (s.is(ItemTags.PLANKS)) return PLANK;
        String path = BuiltInRegistries.ITEM.getKey(s.getItem()).getPath();
        if (path.equals("charcoal")) return COAL;
        return path.contains("firewood") || path.contains("log") ? LOG : path.contains("plank") ? PLANK : 0;
    }

    /** Cooks the raw food you carry on a lit stove: the better the cook, the more it stretches. */
    static void cook(ServerPlayer player, ServerLevel level, BlockPos pos) {
        if (!level.getBlockState(pos).getValue(DecorBlock.ON)) {
            say(player, "Light the stove first.");
            return;
        }
        if (!has(player, com.zomboidcraft.loot.items.Cooking::raw)) {
            say(player, "You've nothing raw to cook.");
            return;
        }
        TimedActions.start(player, "decor_cook:" + pos.asLong(), "Cooking", Math.round(200 * com.zomboidcraft.building.base.RoomEffects.cookScale(player)), p -> {
            int made = 0;
            int skill = Skills.level(p, Skill.COOKING);
            for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
                ItemStack s = p.getInventory().getItem(i);
                if (!com.zomboidcraft.loot.items.Cooking.raw(s)) continue;
                int n = s.getCount(), extra = 0;
                for (int k = 0; k < n; k++) if (p.getRandom().nextInt(25) < skill) extra++;
                p.getInventory().setItem(i, com.zomboidcraft.loot.items.Cooking.cook(s, n + extra));
                made += n;
            }
            Skills.addXp(p, Skill.COOKING, Math.round((3 + made) * com.zomboidcraft.building.base.RoomEffects.cookXp(p)));
            level.playSound(null, pos, SoundEvents.CAMPFIRE_CRACKLE, SoundSource.BLOCKS, 1f, 1f);
            say(p, "Hot food.");
        });
    }

    /** Puts the clothing in your hand on the mannequin, or takes back what it's wearing. */
    static void dress(ServerPlayer player, ServerLevel level, BlockPos pos, BlockState s) {
        if (!(level.getBlockEntity(pos) instanceof DecorEntity e)) {
            return;
        }
        if (!e.getItem(0).isEmpty()) {
            ItemStack back = e.removeItemNoUpdate(0);
            if (!player.getInventory().add(back)) net.minecraft.world.level.block.Block.popResource(level, player.blockPosition(), back);
            e.setChanged();
            level.setBlock(pos, s.setValue(DecorBlock.ON, false), 3);
            say(player, "You take the clothes back off it.");
            return;
        }
        ItemStack hand = player.getMainHandItem();
        if (hand.isEmpty() || Clothes.of(hand) == null) {
            say(player, "Hold a piece of clothing to dress it.");
            return;
        }
        e.setItem(0, hand.split(1));
        e.setChanged();
        level.setBlock(pos, s.setValue(DecorBlock.ON, true), 3);
        level.scheduleTick(pos, s.getBlock(), 40);
        say(player, "Dressed, it looks like someone from a distance.");
    }

    /** Rolls the bowling ball off the way you're facing: it rumbles and clatters where it stops. */
    static void roll(ServerPlayer player, ServerLevel level) {
        Vec3 look = player.getLookAngle().multiply(1, 0, 1).normalize();
        Vec3 to = player.position().add(look.scale(14));
        level.playSound(null, player.blockPosition(), SoundEvents.MINECART_RIDING, SoundSource.PLAYERS, 0.8f, 0.6f);
        level.playSound(null, to.x, to.y, to.z, SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.8f, 1.6f);
        Noise.emit(level, to, 20);
        say(player, "It thunders off and crashes somewhere ahead.");
    }

    /** Reads the calendar, the clock, the map or the deck's old tapes. */
    static void read(ServerPlayer player, ServerLevel level, BlockPos pos, BlockState s, DecorBlock d) {
        switch (d.spec.special()) {
            case "days" -> {
                long day = level.getGameTime() / (long) (Needs.hourSeconds() * 20 * 24) + 1;
                say(player, "Day " + day + ". You've been crossing them off.");
                Mood.add(player, Mood.Stat.UNHAPPINESS, -1);
            }
            case "chime", "time" -> say(player, "It's " + clock(level) + ".");
            case "back_road" -> {
                if (s.getValue(DecorBlock.LOOK) >= 2) {
                    if (com.zomboidcraft.world.map.PaperMaps.read(player, com.zomboidcraft.world.map.PaperMaps.Kind.ATLAS)) {
                        player.sendSystemMessage(Component.literal("Someone inked a back road on this that isn't on any new map. It's on yours now (M).")
                                .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
                    } else {
                        say(player, "An old map of the county, the roads all wrong.");
                    }
                } else {
                    say(player, "An old map of the world. Pretty, and no use at all now.");
                }
            }
            default -> {
            }
        }
    }

    /** Turns the marquee to its next letter. */
    static void nextLook(ServerPlayer player, ServerLevel level, BlockPos pos, BlockState s, DecorBlock d) {
        int look = (s.getValue(DecorBlock.LOOK) + 1) % (d.spec.maxLook() + 1);
        level.setBlock(pos, s.setValue(DecorBlock.LOOK, look), 3);
        level.playSound(null, pos, SoundEvents.STONE_BUTTON_CLICK_ON, SoundSource.BLOCKS, 0.6f, 1.4f);
    }

    /**
     * What playing a piece needs before it starts: a record, a tape, a cue, a TV, a coin. Null when it can go ahead;
     * otherwise what to say. {@code take} uses up what it needs (a coin) once the play is done.
     */
    static String needs(ServerPlayer player, ServerLevel level, BlockPos pos, DecorBlock d) {
        String sp = d.spec.special();
        if (sp == null) return null;
        return switch (sp) {
            case "needs_record" -> has(player, id("vinyl_record")) ? null : "You need a record to put on.";
            case "vhs" -> has(player, id("vhs_tape")) ? null : "You need a VHS tape to watch.";
            case "needs_cue" -> has(player, id("pool_cue")) ? null : "You need a pool cue.";
            case "needs_tv" -> !has(player, id("game_cartridge")) ? "You need a game cartridge."
                    : tvNear(level, pos) ? null : "It needs a TV beside it (a big CRT TV).";
            case "payout" -> has(player, s -> COINS.contains(BuiltInRegistries.ITEM.getKey(s.getItem()).getPath()))
                    ? null : "It takes coins.";
            default -> null;
        };
    }

    /** After a play: what the piece gives back beyond the mood (skill, a prize, a payout, a broadcast). */
    static void played(ServerPlayer player, ServerLevel level, BlockPos pos, DecorBlock d) {
        String sp = d.spec.special();
        if (sp == null) return;
        RandomSource r = player.getRandom();
        switch (sp) {
            case "aim_xp" -> {
                Skills.addXp(player, Skill.AIMING, 4);
                say(player, r.nextInt(5) == 0 ? "Bullseye." : "Not bad. Your eye's getting better.");
            }
            case "train" -> {
                Stamina.use(player, 30);
                Skills.addXp(player, Skill.FITNESS, 6);
                Skills.addXp(player, Skill.STRENGTH, 6);
                Mood.add(player, Mood.Stat.STRESS, -6);
                say(player, "You beat the bag until your arms burn.");
            }
            case "prize_toy" -> {
                if (r.nextInt(4) == 0 && give(player, TOYS.get(r.nextInt(TOYS.size())))) say(player, "The claw holds on. You win a toy!");
                else say(player, "The claw drops it. Typical.");
            }
            case "tickets" -> {
                if (r.nextInt(6) == 0 && give(player, TOYS.get(r.nextInt(TOYS.size())))) say(player, "Enough tickets for a prize from the counter.");
                else say(player, "A strip of tickets. Not enough for anything.");
            }
            case "payout" -> {
                take(player, s -> COINS.contains(BuiltInRegistries.ITEM.getKey(s.getItem()).getPath()));
                int roll = r.nextInt(100);
                if (roll < 2) {
                    give(player, "coin_roll");
                    give(player, "gold_coin");
                    level.playSound(null, pos, SoundEvents.PLAYER_LEVELUP, SoundSource.BLOCKS, 1f, 1f);
                    say(player, "Jackpot! Coins pour out.");
                } else if (roll < 18) {
                    for (int i = 0; i < 2; i++) give(player, "silver_coin");
                    say(player, "Three cherries. A couple of coins back.");
                } else {
                    say(player, "Nothing. The house always wins.");
                }
            }
            case "old_broadcast" -> player.sendSystemMessage(Component.literal(BROADCASTS.get(r.nextInt(BROADCASTS.size())))
                    .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
            case "needs_record" -> tune(level, pos, r);
            default -> {
            }
        }
    }

    // ------------------------------------------------------------------ the pieces that act on their own

    /** The ticking specials; returns the ticks to the next look, or 0 to stop. */
    static int tick(BlockState s, ServerLevel level, BlockPos pos, DecorBlock d, RandomSource r) {
        String sp = d.spec.special();
        boolean on = s.getValue(DecorBlock.ON);
        switch (sp) {
            case "chime" -> {
                level.playSound(null, pos, SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 1.2f, 0.7f);
                Noise.emit(level, Vec3.atCenterOf(pos), d.spec.noise());
                return toNextHour(level);
            }
            case "stove_heat" -> {
                if (!(level.getBlockEntity(pos) instanceof DecorEntity e)) return 0;
                if (e.fuel <= 0) {
                    if (on) level.setBlock(pos, s.setValue(DecorBlock.ON, false), 3);
                    return 0;
                }
                e.fuel = Math.max(0, e.fuel - 100);
                e.setChanged();
                level.sendParticles(net.minecraft.core.particles.ParticleTypes.CAMPFIRE_COSY_SMOKE,
                        pos.getX() + 0.5, pos.getY() + 1.6, pos.getZ() + 0.5, 1, 0.05, 0.1, 0.05, 0.01);
                return 100;
            }
            case "cold_store" -> {
                if (level.getBlockEntity(pos) instanceof DecorEntity e) e.chill(level, on && d.powered(level, pos));
                return 200;
            }
            case "beacon" -> {
                if (on && d.powered(level, pos) && !level.getEntitiesOfClass(Zombie.class, new AABB(pos).inflate(24),
                        ZombieTypes::isWalker).isEmpty()) {
                    level.playSound(null, pos, SoundEvents.NOTE_BLOCK_BIT.value(), SoundSource.BLOCKS, 2f, 0.5f);
                    for (ServerPlayer p : level.getPlayers(p -> p.blockPosition().distSqr(pos) < 64 * 64)) {
                        say(p, "The beacon's picked up movement.");
                    }
                    return 200;
                }
                return 60;
            }
            case "decoy", "dress_decoy" -> {
                if (sp.equals("dress_decoy") && !on) return 0;
                lure(level, pos, sp.equals("dress_decoy") ? 12 : 8, r);
                return 60;
            }
            case "blacklight" -> {
                glow(level, pos, on && d.powered(level, pos));
                return on ? 40 : 0;
            }
            case "coloured_light" -> {
                boolean lit = level.getSkyDarken() < 4 && level.getBrightness(LightLayer.SKY, pos) >= 8;
                if (lit != on) level.setBlock(pos, s.setValue(DecorBlock.ON, lit), 3);
                return 200;
            }
            case "spots" -> {
                if (!on || !d.powered(level, pos)) return 0;
                for (int i = 0; i < 6; i++) {
                    int c = net.minecraft.util.Mth.hsvToRgb(r.nextFloat(), 0.9f, 1f) & 0xFFFFFF;
                    level.sendParticles(new DustParticleOptions(c, 1.2f), pos.getX() + 0.5 + r.nextGaussian() * 3,
                            pos.getY() + 0.5 + r.nextGaussian() * 1.5, pos.getZ() + 0.5 + r.nextGaussian() * 3, 1, 0, 0, 0, 0);
                }
                return 10;
            }
            default -> {
                return 0;
            }
        }
    }

    /** The light-up dance floor: something stepping on it while it's lit sounds the alarm. */
    static void stepped(ServerLevel level, BlockPos pos, BlockState s, Entity entity) {
        if (!s.getValue(DecorBlock.ON) || !(entity instanceof Zombie z) || !ZombieTypes.isWalker(z)
                || level.getGameTime() % 40 != 0) {
            return;
        }
        level.playSound(null, pos, SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.BLOCKS, 2f, 1.8f);
        for (ServerPlayer p : level.getPlayers(p -> p.blockPosition().distSqr(pos) < 48 * 48)) {
            say(p, "Something's on the dance floor.");
        }
    }

    /** Plasma balls and aquariums: a calm room near one. */
    public static int calmNear(ServerLevel level, BlockPos at) {
        int calm = 0;
        for (BlockPos p : BlockPos.betweenClosed(at.offset(-4, -2, -4), at.offset(4, 2, 4))) {
            BlockState s = level.getBlockState(p);
            if (s.getBlock() instanceof DecorBlock d && is(d, "calm") && s.getValue(DecorBlock.ON)) {
                calm += 2;
            }
        }
        return Math.min(calm, 6);
    }

    /** A rug underfoot muffles your steps: the footstep noise multiplier (zombie/ZombieEvents). */
    public static double footsteps(Player player) {
        BlockPos feet = player.blockPosition();
        for (BlockPos p : new BlockPos[] {feet, feet.below()}) {
            if (player.level().getBlockState(p).getBlock() instanceof DecorBlock d && is(d, "muffle")) {
                return 0.4;
            }
        }
        return 1.0;
    }

    /** A lit stove, for the companions' cooking (npc/NpcWork). */
    public static boolean litStove(BlockState s) {
        return s.getBlock() instanceof DecorBlock d && is(d, "stove_heat") && s.getValue(DecorBlock.ON);
    }

    /** A barber's chair within reach: a haircut there lifts your spirits (clothing/Grooming). */
    public static boolean barberNear(Player player) {
        for (BlockPos p : BlockPos.betweenClosed(player.blockPosition().offset(-2, -1, -2), player.blockPosition().offset(2, 1, 2))) {
            if (player.level().getBlockState(p).getBlock() instanceof DecorBlock d && is(d, "haircut")) return true;
        }
        return false;
    }

    /** What a bed or hammock is to sleep on: 0 not one, 1 average, 2 good, 3 the best (survival/Sleep). */
    public static int bed(BlockState s) {
        if (!(s.getBlock() instanceof DecorBlock d) || d.spec.special() == null) return 0;
        return switch (d.spec.special()) {
            case "bed" -> 1;
            case "rest_fast" -> 1;
            case "bed_best" -> 3;
            default -> 0;
        };
    }

    // ------------------------------------------------------------------ helpers

    private static void lure(ServerLevel level, BlockPos pos, int reach, RandomSource r) {
        Vec3 at = Vec3.atCenterOf(pos);
        for (Zombie z : level.getEntitiesOfClass(Zombie.class, new AABB(pos).inflate(reach), ZombieTypes::isWalker)) {
            if (z.getTarget() != null && z.getTarget().distanceToSqr(z) < 16) continue;   // one at arm's length stays on it
            if (z.getTarget() != null && r.nextInt(3) != 0) continue;                     // sometimes it goes for the decoy first
            z.setTarget(null);
            if (z.distanceToSqr(at) > 2.5) {
                z.getNavigation().moveTo(at.x, at.y, at.z, 1.0);
            } else {
                z.getLookControl().setLookAt(at);
            }
        }
    }

    /** Lights the band posters round a black light (the glowing looks have ink that shows under it). */
    private static void glow(ServerLevel level, BlockPos pos, boolean lit) {
        for (BlockPos p : BlockPos.betweenClosed(pos.offset(-6, -3, -6), pos.offset(6, 3, 6))) {
            BlockState s = level.getBlockState(p);
            if (s.getBlock() instanceof DecorBlock d && is(d, "glows") && s.getValue(DecorBlock.ON) != lit) {
                level.setBlock(p, s.setValue(DecorBlock.ON, lit), 3);
            }
        }
    }

    private static boolean tvNear(ServerLevel level, BlockPos pos) {
        for (BlockPos p : BlockPos.betweenClosed(pos.offset(-4, -1, -4), pos.offset(4, 1, 4))) {
            BlockState s = level.getBlockState(p);
            if (s.getBlock() instanceof DecorBlock d && "vhs".equals(d.spec.special())
                    || BuiltInRegistries.BLOCK.getKey(s.getBlock()).getPath().contains("tv")) {
                return true;
            }
        }
        return false;
    }

    private static void tune(ServerLevel level, BlockPos pos, RandomSource r) {
        SoundEvent[] notes = {SoundEvents.NOTE_BLOCK_GUITAR.value(), SoundEvents.NOTE_BLOCK_BASS.value(), SoundEvents.NOTE_BLOCK_HARP.value()};
        for (int i = 0; i < 3; i++) {
            level.playSound(null, pos, notes[i], SoundSource.RECORDS, 0.8f, 0.6f + r.nextFloat() * 0.8f);
        }
    }

    /** Ticks to the top of the next Zomboid hour. */
    static int toNextHour(ServerLevel level) {
        long hour = (long) (Needs.hourSeconds() * 20);
        return (int) Math.max(20, hour - level.getGameTime() % hour);
    }

    private static String clock(ServerLevel level) {
        long hour = (long) (Needs.hourSeconds() * 20);
        long t = level.getGameTime() % (hour * 24);
        int h = (int) (t / hour), m = (int) (t % hour * 60 / hour);
        return String.format("%d:%02d %s", h % 12 == 0 ? 12 : h % 12, m, h < 12 ? "in the morning" : "in the afternoon");
    }

    private static Predicate<ItemStack> id(String id) {
        Item item = BuiltInRegistries.ITEM.getValue(ZomboidCraft.id(id));
        return s -> item != Items.AIR && s.is(item);
    }

    private static boolean has(ServerPlayer player, Predicate<ItemStack> what) {
        return player.getInventory().contains(what);
    }

    private static void take(ServerPlayer player, Predicate<ItemStack> what) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack s = player.getInventory().getItem(i);
            if (!s.isEmpty() && what.test(s)) {
                s.shrink(1);
                return;
            }
        }
    }

    private static boolean give(ServerPlayer player, String id) {
        Item item = BuiltInRegistries.ITEM.getValue(ZomboidCraft.id(id));
        if (item == Items.AIR) return false;
        ItemStack s = new ItemStack(item);
        if (!player.getInventory().add(s)) net.minecraft.world.level.block.Block.popResource(player.level(), player.blockPosition(), s);
        return true;
    }

    static void say(ServerPlayer player, String text) {
        player.sendOverlayMessage(Component.literal(text));
    }
}
