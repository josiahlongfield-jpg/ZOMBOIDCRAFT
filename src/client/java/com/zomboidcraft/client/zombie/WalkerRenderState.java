package com.zomboidcraft.client.zombie;

import com.zomboidcraft.ZomboidCraft;
import net.minecraft.client.renderer.entity.state.ZombieRenderState;
import net.minecraft.resources.Identifier;

public class WalkerRenderState extends ZombieRenderState {
    public Identifier texture = ZomboidCraft.id("textures/entity/walker/civilian_pale.png");
    /** Stable per-zombie number, so each one lolls and sways a little differently. */
    public int variant;
    /** Atlas for the outfit's 3D pieces, or null when they are turned off. */
    public Identifier addonTexture;
    /** Glowing eye mask matching the skin, or null when look.glowingEyes is off. */
    public Identifier eyesTexture;
    public String outfit = "civilian";
    /** 0 none, 1 right forearm, 2 left forearm, 3 right arm, 4 left arm. */
    public int missingLimb;
    /** Crawls for good: lies face down. A zombie knocked over without this lies on its back. */
    public boolean crawler;
    /** Swing of its attack, 0 to 1: the hands close on the grab at the middle of it. */
    public float attack;
}
