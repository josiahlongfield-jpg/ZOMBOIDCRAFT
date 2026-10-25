package com.zomboidcraft.client.cars;

import net.minecraft.client.renderer.entity.state.EntityRenderState;

public class CarRenderState extends EntityRenderState {
    public String model = "sedan";
    public String variant = "usable";
    public float yaw;
    /** How far the boot is open, eased, 0 to 1. */
    public float open;
}
