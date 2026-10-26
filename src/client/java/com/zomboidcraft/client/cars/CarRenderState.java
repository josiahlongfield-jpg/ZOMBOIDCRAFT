package com.zomboidcraft.client.cars;

import net.minecraft.client.renderer.entity.state.EntityRenderState;

public class CarRenderState extends EntityRenderState {
    public String model = "sedan";
    public String variant = "usable";
    public float yaw;
    /** How far the boot is open, eased, 0 to 1. */
    public float open;
    /** How far the wheels have rolled (metres), and the front wheels' steer (radians, left +). */
    public double rolled;
    public float steer;
    /** The local player is sitting in this car, looking out of it. */
    public boolean inside;
    /** Zombies have smashed the windows in: no glass. */
    public boolean smashed;
    /** How coarse a copy of the mesh to draw, from how far off the car is ({@link CarLod}). */
    public int lod;
    /** The engine's running, so the lamps and the dash are lit; the brake or reversing lights too. */
    public boolean lights, braking, reversing;
    /** How dark it is round the car, 0 (day) to 1 (night), and whether a shader pack draws the glow. */
    public float dark;
    public boolean shaders;
    /** Seconds since the aircraft's cockpit lights came on (someone sat in it), -1 while off; how dark it is round it. */
    public float cockpit = -1;
    public float cockpitDark;
}
