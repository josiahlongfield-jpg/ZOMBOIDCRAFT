package com.zomboidcraft.client.camera;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

/**
 * One place for the mod's camera rigs (the aircraft's cockpit and chase views, a third person on
 * foot): after Minecraft lines the camera up with what it follows, the first rig in priority order
 * that wants it says where the camera is and which way it's turned, roll and all.
 * {@code CameraMixin} applies the answer.
 */
public final class CameraRig {
    /** What a rig decides: the camera's turn in Minecraft's camera frame (looking down -z), and where it is (null keeps vanilla's). */
    public static final class Out {
        public final Quaternionf rotation = new Quaternionf();
        public Vec3 position;
    }

    public interface Provider {
        /** Fills out and returns true to take the camera this frame; false leaves it to the next rig or vanilla. */
        boolean apply(Camera camera, Entity entity, float partialTick, Out out);
    }

    private record Entry(int priority, Provider provider) {
    }

    private static final List<Entry> RIGS = new ArrayList<>();
    private static final Out OUT = new Out();

    /** Adds a rig; lower priority numbers are asked first. */
    public static void register(int priority, Provider provider) {
        RIGS.add(new Entry(priority, provider));
        RIGS.sort(Comparator.comparingInt(Entry::priority));
    }

    /** The answer of the first rig that takes the camera this frame, or null. */
    public static Out apply(Camera camera, Entity entity, float partialTick) {
        for (Entry e : RIGS) {
            OUT.position = null;
            if (e.provider().apply(camera, entity, partialTick, OUT)) {
                return OUT;
            }
        }
        return null;
    }

    /** Minecraft's camera frame for something whose own frame has its nose toward +z and its left toward +x. */
    public static Quaternionf fromBody(Quaternionf body, Quaternionf dest) {
        return dest.set(body).rotateY((float) Math.PI);
    }

    private CameraRig() {
    }
}
