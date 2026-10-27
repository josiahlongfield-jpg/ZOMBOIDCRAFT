package com.zomboidcraft.combat;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.zomboidcraft.ModItems;
import com.zomboidcraft.ZcConfig;
import com.zomboidcraft.ZomboidCraft;
import com.zomboidcraft.zombie.ZombieTypes;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * Crossbow bolts left standing in the zombies they hit, where they went in: each one keeps to the body part it struck
 * (the arm it's in swings with the arm), falls out if that limb is shot off, and drops with the body when the zombie
 * dies, to be walked over and picked up. Drawn by the client's bolt layer from {@link #BOLTS}.
 */
public final class StuckBolts {
    /**
     * One bolt in a zombie: the part it's in (Dismember's numbering), how far along the part (0 at its upper end, 1 at
     * its lower), where round it went in (radians from the part's front, see {@link #front}), and its heading in the
     * part's own frame (along it, to its front, to its side).
     */
    public record Bolt(int part, float along, float round, float du, float df, float dr) {
        public static final Codec<Bolt> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("part").forGetter(Bolt::part),
                Codec.FLOAT.fieldOf("along").forGetter(Bolt::along),
                Codec.FLOAT.fieldOf("round").forGetter(Bolt::round),
                Codec.FLOAT.fieldOf("du").forGetter(Bolt::du),
                Codec.FLOAT.fieldOf("df").forGetter(Bolt::df),
                Codec.FLOAT.fieldOf("dr").forGetter(Bolt::dr)).apply(i, Bolt::new));
        public static final StreamCodec<RegistryFriendlyByteBuf, Bolt> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Bolt::part, ByteBufCodecs.FLOAT, Bolt::along, ByteBufCodecs.FLOAT, Bolt::round,
                ByteBufCodecs.FLOAT, Bolt::du, ByteBufCodecs.FLOAT, Bolt::df, ByteBufCodecs.FLOAT, Bolt::dr, Bolt::new);
    }

    /** The bolts standing in a zombie (saved with it, and sent to everyone who can see it). */
    public static final AttachmentType<List<Bolt>> BOLTS = AttachmentRegistry.create(
            ZomboidCraft.id("stuck_bolts"),
            builder -> builder
                    .persistent(Bolt.CODEC.listOf())
                    .syncWith(Bolt.STREAM_CODEC.apply(ByteBufCodecs.list()), AttachmentSyncPredicate.all()));

    /**
     * Each part's lie in the zombie's frame standing still (its capsule from upper end to lower, as Dismember's
     * standing table has it), and how thick it is (blocks).
     */
    private static final Vec3[] REST = {new Vec3(-0.039, 0.188, 0.069), new Vec3(0, 0.288, 0.062),
            new Vec3(0, 0.288, 0.062), new Vec3(0.182, 0, 0), new Vec3(-0.040, -0.280, -0.063),
            new Vec3(-0.077, -0.406, 0.003), new Vec3(0.001, -0.274, -0.095), new Vec3(0.026, -0.411, -0.021),
            new Vec3(-0.009, -0.408, 0.029), new Vec3(-0.008, -0.413, 0.013), new Vec3(0.017, -0.407, 0.035),
            new Vec3(0.012, -0.412, 0.002)};
    public static final float[] RADIUS = {0.187f, 0.119f, 0.119f, 0.108f, 0.080f, 0.077f, 0.080f, 0.077f, 0.097f,
            0.085f, 0.097f, 0.085f};
    private static final Vec3 FORWARD = new Vec3(0, 0, 1), LEFT = new Vec3(1, 0, 0);

    public static void register() {
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (entity instanceof Zombie zombie) {
                List<Bolt> bolts = zombie.getAttachedOrElse(BOLTS, List.of());
                if (!bolts.isEmpty()) {
                    zombie.removeAttached(BOLTS);
                    drop(zombie, zombie.position().add(0, 0.3, 0), bolts.size());
                }
            }
        });
    }

    /**
     * Leaves a bolt standing in a zombie where the shot went in, or false if it can't (not one of ours, its line
     * missed the body, or it's full of bolts already): the caller drops it at its feet instead.
     */
    public static boolean stick(Zombie zombie, Vec3 eye, Vec3 dir) {
        List<Bolt> bolts = zombie.getAttachedOrElse(BOLTS, List.of());
        if (!ZombieTypes.isWalker(zombie) || bolts.size() >= ZcConfig.get().crossbow.maxStuckBolts) {
            return false;
        }
        Dismember.Strike strike = Dismember.strike(zombie, eye, dir);
        if (strike == null) {
            return false;
        }
        Vec3 u = strike.b().subtract(strike.a());
        double len2 = u.lengthSqr();
        double along = len2 < 1e-9 ? 0 : Mth.clamp(strike.at().subtract(strike.a()).dot(u) / len2, 0, 1);
        Vec3 axis = len2 < 1e-9 ? REST[strike.part()].normalize() : u.normalize();
        Vec3 f = front(strike.part(), axis), r = axis.cross(f);
        Vec3 radial = strike.at().subtract(strike.a().add(u.scale(along)));
        radial = radial.lengthSqr() < 1e-9 ? strike.dir().scale(-1) : radial;
        Vec3 d = strike.dir().normalize();
        List<Bolt> more = new ArrayList<>(bolts);
        more.add(new Bolt(strike.part(), (float) along, (float) Math.atan2(radial.dot(r), radial.dot(f)),
                (float) d.dot(axis), (float) d.dot(f), (float) d.dot(r)));
        zombie.setAttached(BOLTS, List.copyOf(more));
        return true;
    }

    /** A limb's been shot off: the bolts in it fall out where it came away. */
    public static void shed(Zombie zombie, Vec3 at) {
        List<Bolt> bolts = zombie.getAttachedOrElse(BOLTS, List.of());
        List<Bolt> kept = bolts.stream().filter(b -> !Dismember.gone(zombie, b.part())).toList();
        if (kept.size() < bolts.size()) {
            zombie.setAttached(BOLTS, kept);
            drop(zombie, at, bolts.size() - kept.size());
        }
    }

    /**
     * A part's front, the way it lies now (along axis, a unit vector in the zombie's frame): the zombie's forward
     * turned the shortest way from the part's standing lie onto this one, so the side of an arm that faced ahead
     * still counts as its front when the arm's raised. Unit, square to axis. The client works it the same way.
     */
    public static Vec3 front(int part, Vec3 axis) {
        Vec3 rest = REST[part].normalize();
        double c = rest.dot(axis);
        Vec3 f;
        if (c > 0.9999) {
            f = FORWARD;
        } else if (c < -0.9999) {
            f = FORWARD.scale(-1);
        } else {
            Vec3 k = rest.cross(axis);
            f = FORWARD.scale(c).add(k.cross(FORWARD)).add(k.scale(k.dot(FORWARD) / (1 + c)));
        }
        f = f.subtract(axis.scale(f.dot(axis)));
        if (f.lengthSqr() < 1e-6) {
            f = LEFT.subtract(axis.scale(LEFT.dot(axis)));
        }
        return f.normalize();
    }

    private static void drop(Zombie zombie, Vec3 at, int count) {
        if (!(zombie.level() instanceof ServerLevel level)) {
            return;
        }
        for (int i = 0; i < count; i++) {
            ItemEntity bolt = new ItemEntity(level, at.x, at.y, at.z, new ItemStack(ModItems.CROSSBOW_BOLT));
            bolt.setDeltaMovement((zombie.getRandom().nextDouble() - 0.5) * 0.1, 0.05, (zombie.getRandom().nextDouble() - 0.5) * 0.1);
            bolt.setPickUpDelay(10);
            level.addFreshEntity(bolt);
        }
    }

    private StuckBolts() {
    }
}
