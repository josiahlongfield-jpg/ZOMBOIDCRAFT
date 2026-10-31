package com.zomboidcraft.survival;

import com.zomboidcraft.combat.ModSounds;
import com.zomboidcraft.skills.Skill;
import com.zomboidcraft.skills.Skills;
import com.zomboidcraft.survival.Body.Part;
import com.zomboidcraft.survival.Body.Region;
import com.zomboidcraft.ZomboidCraft;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Seeing to wounds, your own (from the item in your hand, the health page or the quick-use wheel) or a friend's (the
 * interact key on them). Each treatment is
 * a timed action: the bandage or splint is only used up when it's done, and First Aid makes the hands quicker.
 */
public final class Treatment {
    public enum Kind {
        BANDAGE("Bandaging", "bandage"), SPLINT("Splinting", "splint");

        public final String verb;
        public final String noun;

        Kind(String verb, String noun) {
            this.verb = verb;
            this.noun = noun;
        }

        public boolean is(ItemStack stack) {
            return this == BANDAGE ? stack.getItem() instanceof BandageItem : stack.getItem() instanceof SplintItem;
        }
    }

    /** Legs first: a broken leg is the one that gets you killed. */
    private static final Region[] SPLINT_ORDER = {Region.LEFT_SHIN, Region.RIGHT_SHIN, Region.LEFT_THIGH, Region.RIGHT_THIGH,
            Region.LEFT_FOOT, Region.RIGHT_FOOT, Region.LEFT_FOREARM, Region.RIGHT_FOREARM, Region.LEFT_UPPER_ARM,
            Region.RIGHT_UPPER_ARM, Region.LEFT_HAND, Region.RIGHT_HAND, Region.UPPER_TORSO, Region.LOWER_TORSO, Region.GROIN,
            Region.NECK, Region.HEAD};

    /** Whether this part needs this treatment. */
    public static boolean needs(Part p, Kind kind) {
        return kind == Kind.BANDAGE ? p.wounded() && !p.bandaged() : p.fractured() && !p.splinted();
    }

    /** The part a treatment should go to first: bleeding wounds, then the deepest; broken legs, then arms. */
    public static Region target(Body body, Kind kind) {
        if (kind == Kind.SPLINT) {
            for (Region r : SPLINT_ORDER) if (needs(body.part(r), kind)) return r;
            return null;
        }
        Region target = null;
        int best = -1;
        for (Region r : Region.values()) {
            Part p = body.part(r);
            if (!needs(p, kind)) continue;
            int score = (p.bleeding() ? 10 : 0) + p.wound().ordinal();
            if (score > best) {
                best = score;
                target = r;
            }
        }
        return target;
    }

    /** Seconds' worth of ticks the treatment takes at this First Aid level. */
    public static int ticks(Kind kind, int firstAid) {
        return kind == Kind.BANDAGE ? Math.max(30, 80 - 5 * firstAid) : Math.max(60, 140 - 8 * firstAid);
    }

    /** The bandage or splint to use: the one in your hands first, then the first one carried anywhere. */
    public static Slot find(Player player, Kind kind) {
        return find(player, kind::is);
    }

    public static Slot find(Player player, Predicate<ItemStack> wanted) {
        InventoryMenu menu = player.inventoryMenu;
        int selected = InventoryMenu.USE_ROW_SLOT_START + player.getInventory().getSelectedSlot();
        if (selected < menu.slots.size() && wanted.test(menu.slots.get(selected).getItem())) return menu.slots.get(selected);
        if (wanted.test(menu.slots.get(InventoryMenu.SHIELD_SLOT).getItem())) return menu.slots.get(InventoryMenu.SHIELD_SLOT);
        for (int i = InventoryMenu.INV_SLOT_START; i < menu.slots.size(); i++) {
            Slot slot = menu.slots.get(i);
            if (wanted.test(slot.getItem()) && slot.mayPickup(player)) return slot;
        }
        return null;
    }

    /** Starts treating a part (or the one that needs it most, when {@code region} is null). False if there's nothing to do. */
    public static boolean start(ServerPlayer player, Kind kind, Region region) {
        return start(player, player, kind, region);
    }

    /**
     * Treating someone else, Zomboid style: the interact key on a friend sees to what they need most, a bleeding wound
     * first, then a break. The helper's kit and First Aid do the work; the patient's body takes it.
     */
    public static boolean treatOther(ServerPlayer helper, Player patient) {
        if (patient == helper || patient.distanceToSqr(helper) > REACH * REACH) return false;
        Body body = Body.get(patient);
        for (Kind kind : Kind.values()) {
            Region r = target(body, kind);
            if (r != null && find(helper, kind) != null) return start(helper, patient, kind, r);
        }
        boolean hurt = body.injured();
        helper.sendOverlayMessage(Component.literal(!hurt ? patient.getName().getString() + " has nothing to treat."
                : "You have nothing to treat " + patient.getName().getString() + " with.").withStyle(ChatFormatting.GRAY));
        return false;
    }

    /** The interact key on a friend who isn't down: treat them. */
    public record Other(int target) implements CustomPacketPayload {
        public static final Type<Other> TYPE = new Type<>(ZomboidCraft.id("treat_other"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Other> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Other::target, Other::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public static void register() {
        PayloadTypeRegistry.serverboundPlay().register(Other.TYPE, Other.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(Other.TYPE, (payload, context) -> {
            ServerPlayer helper = context.player();
            helper.level().getServer().execute(() -> {
                if (helper.level().getEntity(payload.target()) instanceof Player patient && !Downs.downed(patient) && !Downs.downed(helper)) {
                    treatOther(helper, patient);
                }
            });
        });
    }

    /** How close you have to be to treat someone. */
    private static final double REACH = 3;

    private static boolean start(ServerPlayer player, Player patient, Kind kind, Region region) {
        Body body = Body.get(patient);
        boolean self = patient == player;
        if (region == null) region = target(body, kind);
        if (region == null || !needs(body.part(region), kind)) {
            player.sendOverlayMessage(Component.literal(kind == Kind.BANDAGE ? "Nothing needs a bandage." : "Nothing is broken.")
                    .withStyle(ChatFormatting.GRAY));
            return false;
        }
        if (find(player, kind) == null) {
            player.sendOverlayMessage(Component.literal("You have no " + kind.noun + ".").withStyle(ChatFormatting.GRAY));
            return false;
        }
        Region part = region;
        int firstAid = Skills.level(player, Skill.FIRST_AID);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                ModSounds.BANDAGE, SoundSource.PLAYERS, 0.6f, kind == Kind.BANDAGE ? 1.1f : 0.85f);
        String whose = self ? "" : patient.getName().getString() + "'s ";
        TimedActions.start(player, "treat:" + kind + ":" + part + ":" + patient.getId(), kind.verb + " " + whose + part.label,
                ticks(kind, firstAid), p -> finish(p, patient, kind, part));
        if (!self && patient instanceof ServerPlayer sp) {
            sp.sendOverlayMessage(Component.literal(player.getName().getString() + " is " + kind.verb.toLowerCase(java.util.Locale.ROOT)
                    + " your " + part.label + ".").withStyle(ChatFormatting.GRAY));
        }
        return true;
    }

    private static void finish(ServerPlayer player, Player patient, Kind kind, Region region) {
        if (patient != player && (!patient.isAlive() || patient.distanceToSqr(player) > REACH * REACH)) {
            player.sendOverlayMessage(Component.literal(patient.getName().getString() + " moved away.").withStyle(ChatFormatting.GRAY));
            return;
        }
        Slot slot = find(player, kind);
        if (slot == null) return;
        int firstAid = Skills.level(player, Skill.FIRST_AID);
        if (!apply(patient, kind, region, firstAid)) return;
        Skills.addXp(player, Skill.FIRST_AID, kind == Kind.BANDAGE ? 10 : 15);
        ItemStack stack = slot.getItem();
        stack.shrink(1);
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.BANDAGE, SoundSource.PLAYERS,
                1f, (kind == Kind.BANDAGE ? 0.95f : 0.8f) + player.getRandom().nextFloat() * 0.1f);
        player.sendOverlayMessage(Component.literal(patient == player ? "You " + kind.noun + " your " + region.label + "."
                : "You " + kind.noun + " " + patient.getName().getString() + "'s " + region.label + "."));
    }

    /**
     * Puts a bandage or splint on a part, done by someone with this First Aid level: the end of a treatment, for a
     * player helper above or the companion nurse (who times it herself with {@link #ticks}). False if the part no
     * longer needs it. Uses up nothing; the helper's supply is theirs to take.
     */
    public static boolean apply(Player patient, Kind kind, Region region, int firstAid) {
        Body body = Body.get(patient);
        if (!needs(body.part(region), kind)) return false;
        if (kind == Kind.BANDAGE) {
            // First Aid 5: one bandage stretches to a second wound
            body = bind(body, region);
            if (firstAid >= 5) {
                Region second = target(body, kind);
                if (second != null) body = bind(body, second);
            }
            if (firstAid > 0) patient.heal(0.5f * firstAid);
        } else {
            Part p = body.part(region);
            body = body.with(region, new Part(p.wound(), p.woundLeft(), p.bleeding(), p.bandaged(), p.fractureLeft(), true));
        }
        Body.set(patient, body);
        return true;
    }

    private static Body bind(Body body, Region r) {
        Part p = body.part(r);
        return body.with(r, new Part(p.wound(), p.woundLeft(), false, true, p.fractureLeft(), p.splinted()));
    }

    private Treatment() {
    }
}
