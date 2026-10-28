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
    /** Every window's shattered: no glass. */
    public boolean smashed;
    /** The windows one by one ({@link CarGlassDraw}): which look's windows, how each is, how far each is wound down. */
    public int look = -1;
    public com.zomboidcraft.world.cars.CarGlass.Panes panes;
    public float[] wound = new float[0];
    /** How coarse a copy of the mesh to draw, from how far off the car is ({@link CarLod}). */
    public int lod;
    /** The headlights (and tail lights) are switched on; the engine's running, so the dash is lit; the brake or reversing lights. */
    public boolean lights, dash, braking, reversing;
    /** How dark it is round the car, 0 (day) to 1 (night), and whether a shader pack draws the glow. */
    public float dark;
    public boolean shaders;
    /** Seconds since the aircraft's cockpit lights came on (someone sat in it), -1 while off; how dark it is round it. */
    public float cockpit = -1;
    public float cockpitDark;
    /** The aircraft the local player sits in, whose screens show the flight live ({@link CockpitDisplays}); else null. */
    public com.zomboidcraft.world.cars.CarProp liveCockpit;
    /** The aircraft itself, for its control surfaces; null for a car. */
    public com.zomboidcraft.world.cars.CarProp aircraft;
    /** An aircraft: turned by its attitude about a point pivot metres up, rotors at rotor radians. */
    public boolean air;
    public final org.joml.Quaternionf attitude = new org.joml.Quaternionf();
    public float pivot;
    public double rotor;
    /** Torn apart in a crash: only the shell is drawn. */
    public boolean broken;
    /** What's lit green for the local player's interact key (a door handle, a seat, the glovebox), in model space, and its size; or null. */
    public float[] highlight;
    public float highlightSize;
    /** The steering wheel's turn (radians, anticlockwise as the driver sees it). */
    public float wheelTurn;
    /** The local player drives this car in first person: which of their hands to draw on the wheel ({@link CarHands}), and their seat. */
    public int arms;
    public float[] driverEye;
}
