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
    /** How coarse a copy of the mesh to draw, from how far off the car is ({@link CarLod}). */
    public int lod;
}
