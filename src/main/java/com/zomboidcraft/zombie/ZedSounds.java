package com.zomboidcraft.zombie;

import com.zomboidcraft.ZomboidCraft;
import com.zomboidcraft.combat.Surface;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/**
 * Every sound a zombie makes, as Project Zomboid makes it (Josiah 2026-11-01: Zomboid's zombie audio replaces ours
 * completely).
 *
 * <p>Each zombie has one of Zomboid's own voices for life: female or male to match the body ZedBodies gives it (the same
 * roll on its UUID), and one of Zomboid's three shambler voices, or one of its two sprinter voices if it runs. Each voice
 * has Zomboid's states: idle moans, the aggro cry when it spots you, the snarls of the chase, the swing, the bite, hurt,
 * pushed, eating and reanimating. Like Zomboid's, a shambler dies without a sound: you hear the body hit the ground.
 * Its feet sound on what it walks on, and its blows on doors, windows, fences, barricades and cars are Zomboid's thumps.
 *
 * <p>The events live in the jar's sounds.json pointing at our old sounds, so the game works as before without Zomboid's
 * files; the local "Zomboid zombie sounds" resource pack on Josiah's PC (tools/pz/zombie_sounds_pack.py, never in the
 * repo or the jar) replaces every one of them with Zomboid's own.
 */
public final class ZedSounds {
    public enum State { IDLE, AGGRO, CHASE, SWING, BITE, HURT, PUSHED, EATING, REANIMATE, DEATH }

    /** Zomboid's voices: three shambler voices and two sprinter voices for each sex. */
    private static final String[] VOICES = {"fa", "fb", "fc", "ma", "mb", "mc", "sfa", "sfb", "sma", "smb"};
    private static final Map<String, EnumMap<State, SoundEvent>> VOICE = new HashMap<>();
    private static final Map<String, SoundEvent> OTHER = new HashMap<>();

    static {
        for (String v : VOICES) {
            EnumMap<State, SoundEvent> states = new EnumMap<>(State.class);
            for (State s : State.values()) {
                // the cries carry: Zomboid's aggro and chase are heard a street away
                float range = s == State.AGGRO ? 32f : s == State.CHASE ? 24f : 16f;
                states.put(s, register("pz.zombie." + v + "." + s.name().toLowerCase(), range));
            }
            VOICE.put(v, states);
        }
        for (String k : new String[] {"step.grass", "step.dirt", "step.gravel", "step.snow", "step.mud", "step.leaves",
                "step.concrete", "step.ceramic", "step.wood", "step.metal", "step.carpet", "step.glass", "step.water",
                "thump.wood", "thump.wood_heavy", "thump.metal", "thump.metal_heavy", "thump.window", "thump.fence",
                "thump.chainlink", "thump.garage", "thump.car", "thump.car_window", "break.door", "break.barricade",
                "break.barricade_metal", "break.window", "break.chainlink", "claw.wood", "claw.metal", "climb.window",
                "bite.neck", "scratch", "bodyfall.concrete", "bodyfall.grass", "bodyfall.metal", "bodyfall.wood"}) {
            OTHER.put(k, register("pz.zombie." + k, k.startsWith("step.") ? 12f : 24f));
        }
    }

    private static SoundEvent register(String name, float range) {
        Identifier id = ZomboidCraft.id(name);
        return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createFixedRangeEvent(id, range));
    }

    public static void register() {
        // loading the class registers the sounds
    }

    /** The same roll ZedBodies makes on a zombie's UUID, so its voice and its body agree on its sex. */
    private static float roll(int variant, long salt) {
        long x = variant * 0x9E3779B97F4A7C15L + salt;
        x = (x ^ (x >>> 30)) * 0xBF58476D1CE4E5B9L;
        x = (x ^ (x >>> 27)) * 0x94D049BB133111EBL;
        x = x ^ (x >>> 31);
        return (x >>> 40) / (float) (1L << 24);
    }

    /** Which of Zomboid's voices this zombie has, for life. */
    public static String voice(Entity zombie) {
        int variant = (int) zombie.getUUID().getLeastSignificantBits();
        String sex = roll(variant, 1) < 0.5f ? "f" : "m";
        boolean sprinter = ZombieTypes.isSprinter(zombie);
        int n = sprinter ? 2 : 3;
        int pick = Math.min(n - 1, (int) (roll(variant, 61) * n));
        return (sprinter ? "s" : "") + sex + (char) ('a' + pick);
    }

    public static SoundEvent of(Entity zombie, State state) {
        return VOICE.get(voice(zombie)).get(state);
    }

    public static SoundEvent get(String key) {
        return OTHER.get(key);
    }

    /** The zombie says something in its own voice. */
    public static void say(Entity zombie, State state, float volume) {
        if (zombie.isSilent()) {
            return;
        }
        zombie.level().playSound(null, zombie.getX(), zombie.getEyeY(), zombie.getZ(), of(zombie, state), SoundSource.HOSTILE,
                volume, 0.95f + zombie.level().getRandom().nextFloat() * 0.1f);
    }

    public static void play(Level level, double x, double y, double z, String key, float volume) {
        level.playSound(null, x, y, z, OTHER.get(key), SoundSource.HOSTILE, volume, 0.92f + level.getRandom().nextFloat() * 0.16f);
    }

    public static void play(Level level, BlockPos pos, String key, float volume) {
        play(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, key, volume);
    }

    /** A footstep on what it's standing on. */
    public static String step(Entity zombie, BlockState under) {
        if (zombie != null && zombie.isInWater()) {
            return "step.water";
        }
        String path = BuiltInRegistries.BLOCK.getKey(under.getBlock()).getPath();
        SoundType sound = under.getSoundType();
        if (path.contains("leaves") || sound == SoundType.AZALEA_LEAVES) {
            return "step.leaves";
        }
        if (path.contains("carpet") || path.contains("rug") || sound == SoundType.WOOL || sound == SoundType.MOSS_CARPET) {
            return "step.carpet";
        }
        if (path.contains("tile") || path.contains("ceramic") || path.contains("linoleum")) {
            return "step.ceramic";
        }
        if (sound == SoundType.GRASS || path.contains("grass") || sound == SoundType.MOSS) {
            return "step.grass";
        }
        if (sound == SoundType.GRAVEL || path.contains("gravel")) {
            return "step.gravel";
        }
        if (sound == SoundType.SNOW || sound == SoundType.POWDER_SNOW) {
            return "step.snow";
        }
        if (sound == SoundType.MUD || path.contains("mud")) {
            return "step.mud";
        }
        if (sound == SoundType.SAND || sound == SoundType.ROOTED_DIRT || path.contains("dirt") || path.contains("farmland")) {
            return "step.dirt";
        }
        return switch (Surface.of(under)) {
            case GROUND -> "step.dirt";
            case WOOD -> "step.wood";
            case METAL, CAR -> "step.metal";
            case GLASS -> "step.glass";
            case SOFT -> "step.carpet";
            case CONCRETE, BRICK -> "step.concrete";
        };
    }

    /** A zombie's blow on a block: Zomboid's thump for what it's made of. */
    public static String thump(BlockState state, boolean heavy) {
        String path = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
        if (path.contains("garage") || path.contains("roller") || path.contains("shutter")) {
            return "thump.garage";
        }
        if (path.contains("chain") || path.contains("wire_fence") || path.contains("mesh_fence")) {
            return "thump.chainlink";
        }
        if (path.contains("window") || path.contains("glass") || path.contains("pane")) {
            return "thump.window";
        }
        Surface surface = Surface.of(state);
        if (surface == Surface.METAL || surface == Surface.CAR) {
            return heavy ? "thump.metal_heavy" : "thump.metal";
        }
        if (path.contains("fence") || path.contains("gate")) {
            return "thump.fence";
        }
        return heavy ? "thump.wood_heavy" : "thump.wood";
    }

    /** The body hitting the ground. */
    public static String bodyfall(BlockState under) {
        return switch (step(null, under)) {
            case "step.grass", "step.dirt", "step.gravel", "step.snow", "step.mud", "step.leaves", "step.carpet" -> "bodyfall.grass";
            case "step.wood" -> "bodyfall.wood";
            case "step.metal" -> "bodyfall.metal";
            default -> "bodyfall.concrete";
        };
    }

    /**
     * Swaps a vanilla zombie sound for Zomboid's: its moans, hurt, death and steps (EntitySoundMixin). True if it
     * played Zomboid's instead.
     */
    public static boolean swap(Entity entity, SoundEvent sound, float volume) {
        if (!(entity instanceof Zombie zombie) || !ZombieTypes.isWalker(zombie) || zombie.isSilent()
                || !(entity.level() instanceof ServerLevel level)) {
            return false;
        }
        String path = BuiltInRegistries.SOUND_EVENT.getKey(sound) == null ? "" : BuiltInRegistries.SOUND_EVENT.getKey(sound).getPath();
        if (!path.startsWith("entity.zombie") && !path.startsWith("entity.husk")) {
            return false;
        }
        if (path.endsWith(".ambient")) {
            // hunting, it snarls on its own clock (ZombieVoice); idle, it moans
            if (zombie.getTarget() == null) {
                say(zombie, State.IDLE, 1f);
            }
            return true;
        }
        if (path.endsWith(".hurt")) {
            say(zombie, State.HURT, 1f);
            return true;
        }
        if (path.endsWith(".death")) {
            say(zombie, State.DEATH, 1f);
            BlockState under = level.getBlockState(zombie.getOnPos());
            play(level, zombie.getX(), zombie.getY(), zombie.getZ(), bodyfall(under), 1f);
            return true;
        }
        if (path.endsWith(".step")) {
            BlockState under = level.getBlockState(zombie.getOnPos());
            play(level, zombie.getX(), zombie.getY(), zombie.getZ(), step(zombie, under), Math.max(0.35f, volume * 2.5f));
            return true;
        }
        return false;
    }

    private ZedSounds() {
    }
}
