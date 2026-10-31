package com.zomboidcraft.npc;

import com.zomboidcraft.ZcConfig;
import com.zomboidcraft.carry.BagItem;
import com.zomboidcraft.carry.BagSpec;
import com.zomboidcraft.carry.Wear;
import com.zomboidcraft.clothing.Clothes;
import com.zomboidcraft.combat.AttachmentItem;
import com.zomboidcraft.combat.Attachments;
import com.zomboidcraft.combat.GunState;
import com.zomboidcraft.combat.GunType;
import com.zomboidcraft.combat.MagazineItem;
import com.zomboidcraft.combat.rolls.Rarity;
import com.zomboidcraft.loot.items.SkillBookItem;
import com.zomboidcraft.skills.SkillBooks;
import com.zomboidcraft.skills.Skills;
import com.zomboidcraft.survival.BandageItem;
import com.zomboidcraft.survival.Drinks;
import com.zomboidcraft.survival.SplintItem;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.phys.Vec3;

/**
 * A companion looking after herself, the way you would: she eats and drinks from her pack when she's hungry, patches
 * herself up, reads the skill books she carries, loads her gun with real rounds, and swaps in a bigger backpack, a better
 * gun or an attachment that fits it. Walking with you she picks up what she needs from the ground and searches the
 * containers nobody has opened yet, out of the same world loot you'd find there; what she leaves is still there for you.
 * <p>
 * Anything you might want (any gun, an attachment, a backpack, a rare weapon, ammo for a gun you carry, medicine while
 * you're hurt, a skill book you haven't read) she holds for you and asks first. You answer on the talk page; leave it a
 * day and she keeps it. Clothes she never takes: what she wears is set by her model.
 */
public final class NpcCare {
    /** The stack tags that mark something she's holding for someone, and since when (game time). */
    private static final String OFFER = "zcOfferFor", OFFER_AT = "zcOfferAt";
    /** How long she holds a find for you before she keeps it: a day. */
    private static final long HOLD = 24000;
    /** No zombie this close and she'll see to herself; how far she reaches from you to gather. */
    private static final double SAFE = 12, GATHER = 8, FROM_LEADER = 14;
    /** Her pack without a backpack; how many rounds she'd like to carry, in magazines' worth. */
    static final int BASE_PACK = 9;
    private static final int SPARE_MAGS = 4;

    // ---------------------------------------------------------------- what she's holding for you

    /** Who she's holding this for, or null. */
    public static UUID offeredTo(ItemStack s) {
        CustomData data = s.get(DataComponents.CUSTOM_DATA);
        if (data == null) return null;
        String id = data.copyTag().getStringOr(OFFER, "");
        try {
            return id.isEmpty() ? null : UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static long offeredAt(ItemStack s) {
        CustomData data = s.get(DataComponents.CUSTOM_DATA);
        return data == null ? 0 : data.copyTag().getLongOr(OFFER_AT, 0);
    }

    private static void mark(ItemStack s, UUID who, long now) {
        CompoundTag tag = s.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        tag.putString(OFFER, who.toString());
        tag.putLong(OFFER_AT, now);
        s.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    /** Takes the held-for-you mark off (it's yours now, or hers). */
    public static void clear(ItemStack s) {
        CustomData data = s.get(DataComponents.CUSTOM_DATA);
        if (data == null) return;
        CompoundTag tag = data.copyTag();
        if (!tag.contains(OFFER)) return;
        tag.remove(OFFER);
        tag.remove(OFFER_AT);
        if (tag.isEmpty()) s.remove(DataComponents.CUSTOM_DATA);
        else s.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    /** Who she'd ask: the player she's following, else the one her feelings are about, else anyone she's bonded with nearby. */
    static Player asker(NpcEntity npc) {
        if (!(npc.level() instanceof ServerLevel level)) return null;
        if (npc.following) {
            Player p = npc.leader();
            if (p != null) return p;
        }
        for (ServerPlayer p : level.players()) {
            if (npc.bond.about(p) || npc.bonds.contains(p.getUUID())) return p;
        }
        return null;
    }

    /** Whether this player might want it before she takes it for herself. */
    static boolean important(ItemStack s, Player p) {
        if (GunType.of(s) != null || s.getItem() instanceof AttachmentItem) return true;
        BagSpec bag = BagItem.spec(s);
        if (bag != null && bag.wear() == Wear.BACK) return true;
        Rarity r = Rarity.of(s);
        if (r != null && r.ordinal() >= Rarity.RARE.ordinal()) return true;
        if ((s.getItem() instanceof BandageItem || s.getItem() instanceof SplintItem) && p.getHealth() < p.getMaxHealth() * 0.75f) return true;
        if (s.getItem() instanceof SkillBookItem book) {
            int l = Skills.level(p, book.skill());
            return l < com.zomboidcraft.skills.Skill.MAX_LEVEL && !SkillBooks.read(p, book.skill(), SkillBooks.volume(l));
        }
        GunType mag = GunType.ofMagazine(s);
        var inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            GunType t = GunType.of(inv.getItem(i));
            if (t != null && (t == mag || s.is(t.ammo()))) return true;
        }
        return false;
    }

    /** Into her pack: anything someone might want she marks as held for them and asks. Returns what didn't fit. */
    static ItemStack stow(NpcEntity npc, ItemStack s) {
        Player p = asker(npc);
        boolean ask = p != null && important(s, p);
        if (ask) mark(s, p.getUUID(), npc.level().getGameTime());
        int before = s.getCount();
        ItemStack left = npc.work.pack.addItem(s);
        if (ask && left.getCount() < before) {
            npc.lines.sayLine(npc, NpcLines.Kind.REPLY, NpcWork.pick(npc, "Found " + a(s) + ". Do you want it?",
                    "Hey, " + a(s) + ". Yours if you want it.", "Look what I found: " + a(s) + ". Want it?"));
        }
        if (!left.isEmpty()) clear(left);
        return left;
    }

    private static String a(ItemStack s) {
        String name = s.getHoverName().getString();
        if (s.getCount() > 1) return s.getCount() + " " + name;
        return ("aeiouAEIOU".indexOf(name.charAt(0)) >= 0 ? "an " : "a ") + name;
    }

    /** The slot of the first thing she's holding for this player (anyone, when null), or -1. */
    static int pending(NpcEntity npc, UUID who) {
        for (int i = 0; i < npc.work.pack.getContainerSize(); i++) {
            UUID to = offeredTo(npc.work.pack.getItem(i));
            if (to != null && (who == null || to.equals(who))) return i;
        }
        return -1;
    }

    /** What she's asking about, for the talk page ("" when nothing). */
    static String pendingName(NpcEntity npc) {
        int i = pending(npc, null);
        if (i < 0) return "";
        ItemStack s = npc.work.pack.getItem(i);
        return (s.getCount() > 1 ? s.getCount() + " x " : "") + s.getHoverName().getString();
    }

    /** Your answer about the thing she's holding for you: yes, hand it over; no, she keeps it. */
    static void answer(NpcEntity npc, ServerPlayer p, boolean take) {
        int slot = pending(npc, p.getUUID());
        if (slot < 0) {
            npc.lines.sayLine(npc, NpcLines.Kind.REPLY, "I'm not holding anything for you right now.");
            return;
        }
        ItemStack s = npc.work.pack.getItem(slot);
        if (take) {
            ItemStack give = s.copy();
            clear(give);
            npc.work.pack.setItem(slot, ItemStack.EMPTY);
            if (!p.getInventory().add(give)) p.spawnAtLocation((ServerLevel) p.level(), give);
            npc.lines.sayLine(npc, NpcLines.Kind.REPLY, NpcWork.pick(npc, "Here. It's yours.", "Take it. You'll use it better than me.",
                    "All yours."));
        } else {
            clear(s);
            npc.work.pack.setItem(slot, s);
            npc.lines.sayLine(npc, NpcLines.Kind.REPLY, NpcWork.pick(npc, "Thanks. I'll put it to good use.", "Really? Thank you.",
                    "I'll look after it."));
            npc.mind.add(NpcMind.Need.MORALE, 2f);
        }
    }

    /** Finds she's held a day with no answer become hers. */
    private static void expire(NpcEntity npc, long now) {
        for (int i = 0; i < npc.work.pack.getContainerSize(); i++) {
            ItemStack s = npc.work.pack.getItem(i);
            if (offeredTo(s) != null && now - offeredAt(s) > HOLD) {
                clear(s);
                npc.work.pack.setItem(i, s);
                npc.lines.sayLine(npc, NpcLines.Kind.REPLY, "Nobody's asked for the " + s.getHoverName().getString() + ", so I'm keeping it.");
            }
        }
    }

    /** What she has on her, told to you in chat. */
    static void tellCarrying(NpcEntity npc, ServerPlayer p) {
        npc.lines.sayLine(npc, NpcLines.Kind.REPLY, NpcWork.pick(npc, "Here's what I've got.", "Have a look.", "Not much, but here."));
        for (String l : carrying(npc, p.getUUID())) p.sendSystemMessage(Component.literal(l).withStyle(ChatFormatting.GRAY));
    }

    /** What she has on her, a line each for her hands, her back and her pack (finds held for `you` marked). */
    public static List<String> carrying(NpcEntity npc, UUID you) {
        List<String> lines = new ArrayList<>();
        ItemStack gun = npc.getMainHandItem();
        GunType t = GunType.of(gun);
        if (!gun.isEmpty()) {
            lines.add("In her hands: " + gun.getHoverName().getString()
                    + (t != null ? " (" + loaded(gun) + " loaded, " + reserve(npc, t) + " spare)" : ""));
        }
        if (!npc.work.bag.isEmpty()) {
            lines.add("On her back: " + npc.work.bag.getHoverName().getString() + " (" + npc.work.pack.getContainerSize() + " slots in all)");
        }
        StringBuilder pack = new StringBuilder();
        for (int i = 0; i < npc.work.pack.getContainerSize(); i++) {
            ItemStack s = npc.work.pack.getItem(i);
            if (s.isEmpty()) continue;
            if (!pack.isEmpty()) pack.append(", ");
            pack.append(s.getCount() > 1 ? s.getCount() + " " : "").append(s.getHoverName().getString());
            UUID to = offeredTo(s);
            if (to != null) pack.append(to.equals(you) ? " (held for you)" : " (held for someone)");
        }
        lines.add(pack.isEmpty() ? "Her pack is empty." : "Pack: " + pack);
        return lines;
    }

    // ---------------------------------------------------------------- what's hers to keep, and what she wants

    /** What she keeps on her when she empties her pack into the stores: finds held for you, her ammo, bandages, unread books. */
    static boolean keeps(NpcEntity npc, ItemStack s) {
        if (offeredTo(s) != null || s.getItem() instanceof BandageItem) return true;
        GunType t = NpcBrain.gun(npc);
        if (t != null && ammoFor(t, s)) return true;
        if (s.getItem() instanceof SkillBookItem book) return unread(npc, book.skill().name());
        return s.getItem() instanceof AttachmentItem && t != null && fitsFree(npc.getMainHandItem(), s);
    }

    private static boolean ammoFor(GunType t, ItemStack s) {
        return s.is(t.ammo()) || GunType.ofMagazine(s) == t;
    }

    private static boolean fitsFree(ItemStack gun, ItemStack part) {
        Attachments.Slot slot = Attachments.slotOf(part);
        return slot != null && slot != Attachments.Slot.MAGAZINE && Attachments.canMount(gun, part) && Attachments.get(gun, slot).isEmpty();
    }

    private static boolean unread(NpcEntity npc, String key) {
        int l = npc.skills.level(key);
        return l < NpcSkills.MAX && !npc.skills.hasRead(key, SkillBooks.volume(l));
    }

    /** Whether she'd pick this up for herself. */
    static boolean wants(NpcEntity npc, ItemStack s) {
        if (s.isEmpty() || Clothes.of(s) != null) return false;
        NpcWork w = npc.work;
        if (NpcWork.FOOD.test(s) || NpcWork.DRINK.test(s)) return NpcWork.count(w.pack, NpcWork.FOOD.or(NpcWork.DRINK)) < 4;
        if (s.getItem() instanceof BandageItem || s.getItem() instanceof SplintItem) return NpcWork.count(w.pack, NpcWork.BANDAGE) < 3;
        GunType mine = NpcBrain.gun(npc);
        GunType found = GunType.of(s);
        if (found != null) return mine == null || Rarity.of(found).ordinal() > Rarity.of(mine).ordinal();
        if (mine != null && ammoFor(mine, s)) return !ZcConfig.get().companions.endlessAmmo && reserve(npc, mine) < mine.capacity() * SPARE_MAGS;
        if (s.getItem() instanceof AttachmentItem) return mine != null && fitsFree(npc.getMainHandItem(), s);
        BagSpec bag = BagItem.spec(s);
        if (bag != null) return bag.wear() == Wear.BACK && bag.slots() > bagSlots(w.bag);
        if (s.getItem() instanceof SkillBookItem book) return unread(npc, book.skill().name());
        return false;
    }

    static int bagSlots(ItemStack bag) {
        BagSpec spec = BagItem.spec(bag);
        return spec == null ? 0 : spec.slots();
    }

    // ---------------------------------------------------------------- her gun and real rounds

    static int loaded(ItemStack gun) {
        return gun.getOrDefault(GunState.TYPE, GunState.EMPTY_MAG).rounds();
    }

    static void setLoaded(ItemStack gun, int rounds) {
        GunState g = gun.getOrDefault(GunState.TYPE, GunState.EMPTY_MAG);
        gun.set(GunState.TYPE, new GunState(true, Math.max(0, rounds), g.jammed()));
    }

    /** Rounds she has for this gun besides what's in it: loose ones and those in its magazines (not finds held for you). */
    static int reserve(NpcEntity npc, GunType t) {
        int n = 0;
        for (int i = 0; i < npc.work.pack.getContainerSize(); i++) {
            ItemStack s = npc.work.pack.getItem(i);
            if (offeredTo(s) != null) continue;
            if (s.is(t.ammo())) n += s.getCount();
            else if (GunType.ofMagazine(s) == t) n += MagazineItem.rounds(s) * s.getCount();
        }
        return n;
    }

    /** Takes up to `want` rounds for this gun out of her pack, loose ones first; returns how many. */
    static int draw(NpcEntity npc, GunType t, int want) {
        int got = 0;
        var pack = npc.work.pack;
        for (int i = 0; i < pack.getContainerSize() && got < want; i++) {
            ItemStack s = pack.getItem(i);
            if (offeredTo(s) == null && s.is(t.ammo())) {
                int n = Math.min(want - got, s.getCount());
                s.shrink(n);
                got += n;
            }
        }
        for (int i = 0; i < pack.getContainerSize() && got < want; i++) {
            ItemStack s = pack.getItem(i);
            if (offeredTo(s) == null && GunType.ofMagazine(s) == t && s.getCount() == 1) {
                int n = Math.min(want - got, MagazineItem.rounds(s));
                s.set(GunState.MAG_ROUNDS, MagazineItem.rounds(s) - n);
                got += n;
            }
        }
        pack.setChanged();
        return got;
    }

    /** Whether she can fight with her gun: it has rounds in it or she has more, or ammo's endless. */
    static boolean armed(NpcEntity npc) {
        GunType t = NpcBrain.gun(npc);
        return t != null && (ZcConfig.get().companions.endlessAmmo || loaded(npc.getMainHandItem()) > 0 || reserve(npc, t) > 0);
    }

    // ---------------------------------------------------------------- her kit

    /** Puts on a bigger backpack, picks the best gun she has rounds for, and fits attachments with a free slot. */
    private static void kit(NpcEntity npc) {
        NpcWork w = npc.work;
        net.minecraft.world.SimpleContainer pack = w.pack;
        for (int i = 0; i < pack.getContainerSize(); i++) {
            ItemStack s = pack.getItem(i);
            BagSpec spec = BagItem.spec(s);
            if (offeredTo(s) == null && spec != null && spec.wear() == Wear.BACK && spec.slots() > bagSlots(w.bag)) {
                ItemStack old = w.bag;
                w.bag = pack.removeItemNoUpdate(i);
                w.resize(npc, BASE_PACK + spec.slots());
                pack = w.pack;
                if (!old.isEmpty()) {
                    ItemStack left = pack.addItem(old);
                    if (!left.isEmpty()) npc.spawnAtLocation((ServerLevel) npc.level(), left);
                }
                npc.lines.sayLine(npc, NpcLines.Kind.REPLY, NpcWork.pick(npc, "Much better. Room for more now.", "This bag's a lot bigger."));
                break;
            }
        }
        ItemStack held = npc.getMainHandItem();
        GunType mine = GunType.of(held);
        boolean dry = mine != null && !ZcConfig.get().companions.endlessAmmo && loaded(held) == 0 && reserve(npc, mine) == 0;
        if (mine == null && !held.isEmpty()) return;    // a gift she's holding that isn't a gun: hers to hold
        int best = -1;
        int bestScore = mine == null || dry ? -1 : Rarity.of(mine).ordinal() * 2 + 1;
        for (int i = 0; i < pack.getContainerSize(); i++) {
            ItemStack s = pack.getItem(i);
            GunType t = GunType.of(s);
            if (t == null || offeredTo(s) != null) continue;
            boolean rounds = ZcConfig.get().companions.endlessAmmo || loaded(s) > 0 || reserve(npc, t) > 0;
            int score = Rarity.of(t).ordinal() * 2 + (rounds ? 1 : 0) - (rounds ? 0 : 10);
            if (score > bestScore) {
                bestScore = score;
                best = i;
            }
        }
        if (best >= 0) {
            ItemStack next = pack.removeItemNoUpdate(best);
            npc.setItemSlot(EquipmentSlot.MAINHAND, next);
            npc.rounds = -1;
            npc.reloadLeft = 0;
            if (!held.isEmpty()) {
                ItemStack left = pack.addItem(held);
                if (!left.isEmpty()) npc.spawnAtLocation((ServerLevel) npc.level(), left);
            }
            npc.say(NpcLines.Kind.GUN);
            held = next;
        }
        if (GunType.of(held) == null) return;
        for (int i = 0; i < pack.getContainerSize(); i++) {
            ItemStack s = pack.getItem(i);
            if (offeredTo(s) == null && s.getItem() instanceof AttachmentItem && fitsFree(held, s)) {
                ItemStack off = Attachments.mount(held, s);
                if (off != null) {
                    s.shrink(1);
                    if (!off.isEmpty()) pack.addItem(off);
                    npc.lines.sayLine(npc, NpcLines.Kind.REPLY, "That goes on nicely.");
                }
            }
        }
        pack.setChanged();
    }

    /** Eats or drinks from her own pack when she needs to, wherever she is. */
    private static void eat(NpcEntity npc) {
        NpcMind m = npc.mind;
        boolean hungry = m.get(NpcMind.Need.HUNGER) > 30, thirsty = m.get(NpcMind.Need.THIRST) > 30;
        if (!hungry && !thirsty) return;
        ItemStack mine = npc.work.takeFromPack(s -> offeredTo(s) == null
                && (hungry && NpcWork.FOOD.test(s) || thirsty && NpcWork.DRINK.test(s)));
        if (mine.isEmpty()) return;
        var food = mine.get(DataComponents.FOOD);
        m.ate(npc, mine.getHoverName().getString(), food == null ? 0 : food.nutrition() * 6f, Drinks.thirstValue(mine));
        npc.playSound(food != null ? SoundEvents.GENERIC_EAT.value() : SoundEvents.GENERIC_DRINK.value(), 0.8f, 1f);
    }

    private static boolean safe(NpcEntity npc, double range) {
        return NpcBrain.nearestZombie(npc, range, false) == null;
    }

    // ---------------------------------------------------------------- goals

    /** Seeing to herself when it's quiet: eating, her kit, patching herself up, and reading her books. */
    static final class LookAfter extends Goal {
        private static final int NONE = 0, HEAL = 1, READ = 2;
        /** Reading one volume takes what it takes you (SkillBookItem: 15 seconds); bandaging herself three. */
        private static final int READ_TICKS = 15 * 20, HEAL_TICKS = 60;
        private final NpcEntity npc;
        private int doing, ticks;
        private String key;
        private long lastDark = -1_000_000, next;

        LookAfter(NpcEntity npc) {
            this.npc = npc;
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            // once a second (goals are only asked every other tick, so a tickCount % 20 test can miss for good)
            if (!(npc.level() instanceof ServerLevel level) || level.getGameTime() < next || npc.work.asleep) return false;
            next = level.getGameTime() + 20;
            expire(npc, level.getGameTime());
            if (!safe(npc, SAFE)) return false;
            eat(npc);
            kit(npc);
            doing = NONE;
            if (npc.getHealth() < npc.getMaxHealth() * 0.6f && npc.work.packHas(s -> s.getItem() instanceof BandageItem && offeredTo(s) == null)) {
                doing = HEAL;
                ticks = HEAL_TICKS;
                return true;
            }
            Player leader = npc.following ? npc.leader() : null;
            boolean still = leader == null || leader.getDeltaMovement().horizontalDistanceSqr() < 0.002 && leader.distanceToSqr(npc) < 10 * 10;
            if (!still || npc.mind.get(NpcMind.Need.FEAR) > 25) return false;
            key = book();
            if (key == null) return false;
            if (level.getMaxLocalRawBrightness(npc.blockPosition().above()) < 7) {
                if (level.getGameTime() - lastDark > 20 * 300) {
                    lastDark = level.getGameTime();
                    npc.lines.sayLine(npc, NpcLines.Kind.WORK, "I'd read if I could see the page. Is there a light anywhere?");
                }
                return false;
            }
            doing = READ;
            ticks = READ_TICKS;
            return true;
        }

        /** The skill of a book she carries and hasn't read the volume for her level of, or null. */
        private String book() {
            for (int i = 0; i < npc.work.pack.getContainerSize(); i++) {
                ItemStack s = npc.work.pack.getItem(i);
                if (s.getItem() instanceof SkillBookItem b && offeredTo(s) == null && unread(npc, b.skill().name())) return b.skill().name();
            }
            return null;
        }

        @Override
        public boolean canContinueToUse() {
            if (ticks <= 0 || npc.work.asleep || !safe(npc, 8)) return false;
            Player leader = npc.following ? npc.leader() : null;
            return doing != READ || leader == null || leader.distanceToSqr(npc) < 10 * 10;
        }

        @Override
        public void start() {
            npc.getNavigation().stop();
            npc.work.action = doing == READ ? NpcWork.SIT : NpcWork.KNEEL;
        }

        @Override
        public void stop() {
            if (npc.work.action == NpcWork.SIT || npc.work.action == NpcWork.KNEEL) npc.work.action = NpcWork.NONE;
            doing = NONE;
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            if (doing == NONE || ticks <= 0) return;
            ServerLevel level = (ServerLevel) npc.level();
            if (doing == READ && ticks % 60 == 0) {
                level.playSound(null, npc.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.NEUTRAL, 0.6f,
                        0.9f + npc.getRandom().nextFloat() * 0.2f);
            }
            if (--ticks > 0) return;
            if (doing == HEAL) {
                if (npc.work.takeFromPack(s -> s.getItem() instanceof BandageItem && offeredTo(s) == null).isEmpty()) return;
                npc.heal(6f + npc.skills.level(NpcSkills.Skill.FIRST_AID) * 0.6f);
                level.playSound(null, npc.blockPosition(), SoundEvents.ARMOR_EQUIP_LEATHER.value(), SoundSource.NEUTRAL, 0.8f, 1.2f);
                npc.work.practise(npc, NpcSkills.Skill.FIRST_AID, 6);
                npc.lines.sayLine(npc, NpcLines.Kind.WORK_DONE, NpcWork.pick(npc, "There. That'll hold.", "Better. Sort of."));
            } else if (doing == READ && key != null) {
                int volume = SkillBooks.volume(npc.skills.level(key));
                npc.skills.markRead(key, volume);
                String name = com.zomboidcraft.skills.Skill.valueOf(key).displayName();
                npc.lines.sayLine(npc, NpcLines.Kind.WORK_DONE, "Finished " + name + " Vol " + volume + ". I think I've learned something.");
                npc.mind.remember(npc, "Read " + name + " Vol " + volume, 0.2f, 0.3f);
            }
        }
    }

    /**
     * Walking with you, when it's quiet: she picks up what she needs from the ground nearby and searches the containers
     * nobody has opened yet (their loot is rolled as she opens them, as it would be for you), taking only what she needs.
     */
    static final class Gather extends Goal {
        private final NpcEntity npc;
        private ItemEntity item;
        private BlockPos box;
        private int searching, repath;
        private long next;

        Gather(NpcEntity npc) {
            this.npc = npc;
            setFlags(EnumSet.of(Flag.MOVE));
        }

        private Player leader() {
            return npc.following ? npc.leader() : null;
        }

        @Override
        public boolean canUse() {
            if (!(npc.level() instanceof ServerLevel level) || level.getGameTime() < next || npc.work.asleep || npc.aiming()) return false;
            next = level.getGameTime() + 20;
            Player leader = leader();
            if (leader == null || leader.distanceToSqr(npc) > FROM_LEADER * FROM_LEADER || !safe(npc, SAFE)) return false;
            item = null;
            box = null;
            double best = Double.MAX_VALUE;
            for (ItemEntity e : level.getEntitiesOfClass(ItemEntity.class, npc.getBoundingBox().inflate(GATHER),
                    e -> e.isAlive() && e.distanceToSqr(leader) < FROM_LEADER * FROM_LEADER && wants(npc, e.getItem()))) {
                double d = e.distanceToSqr(npc);
                if (d < best) {
                    best = d;
                    item = e;
                }
            }
            if (item != null) return true;
            List<BlockPos> fresh = new ArrayList<>();
            NpcWork.forEachContainer(level, npc.blockPosition(), (int) GATHER, (pos, c) -> {
                if (c instanceof RandomizableContainer r && r.getLootTable() != null && !npc.work.searched.contains(pos.asLong())
                        && pos.distToCenterSqr(leader.position()) < FROM_LEADER * FROM_LEADER) {
                    fresh.add(pos);
                }
            });
            for (BlockPos p : fresh) {
                double d = p.distToCenterSqr(npc.position());
                if (d < best) {
                    best = d;
                    box = p;
                }
            }
            return box != null;
        }

        @Override
        public boolean canContinueToUse() {
            Player leader = leader();
            if (leader == null || leader.distanceToSqr(npc) > 18 * 18 || !safe(npc, 8)) return false;
            return item != null ? item.isAlive() : box != null;
        }

        @Override
        public void start() {
            searching = 0;
            repath = 0;
        }

        @Override
        public void stop() {
            item = null;
            box = null;
            if (npc.work.action == NpcWork.SEARCH) npc.work.action = NpcWork.NONE;
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            // done with this one: the selector only lets the goal go on its next full pass
            if (item == null && box == null || item != null && !item.isAlive()) return;
            ServerLevel level = (ServerLevel) npc.level();
            Vec3 at = item != null ? item.position() : Vec3.atBottomCenterOf(box);
            double reach = item != null ? 1.8 : 2.2;
            if (npc.position().distanceToSqr(at) > reach * reach) {
                if (--repath <= 0) {
                    repath = 10;
                    npc.getNavigation().moveTo(at.x, at.y, at.z, 1.0 * npc.mind.pace());
                }
                return;
            }
            npc.getNavigation().stop();
            if (item != null) {
                ItemStack left = stow(npc, item.getItem().copy());
                if (left.isEmpty()) item.discard();
                else item.setItem(left);
                item = null;
                return;
            }
            npc.work.action = NpcWork.SEARCH;
            npc.getLookControl().setLookAt(at.x, at.y + 0.5, at.z);
            if (++searching < Math.max(30, 80 - npc.skills.level(NpcSkills.Skill.SCAVENGING) * 5)) return;
            BlockPos pos = box;
            box = null;
            npc.work.action = NpcWork.NONE;
            npc.work.searched.add(pos.asLong());
            while (npc.work.searched.size() > 300) npc.work.searched.remove(npc.work.searched.iterator().next());
            Container c = NpcWork.container(level, pos);
            if (c == null) return;
            if (c instanceof RandomizableContainer loot) loot.unpackLootTable(null);
            int took = 0;
            for (int i = 0; i < c.getContainerSize() && took < 3; i++) {
                ItemStack s = c.getItem(i);
                if (!wants(npc, s)) continue;
                ItemStack left = stow(npc, c.removeItemNoUpdate(i));
                c.setItem(i, left);
                took++;
            }
            c.setChanged();
            npc.work.practise(npc, NpcSkills.Skill.SCAVENGING, 2);
        }
    }

    private NpcCare() {
    }
}
