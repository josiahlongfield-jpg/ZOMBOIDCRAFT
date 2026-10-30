package com.zomboidcraft.client.zombie;

import com.zomboidcraft.ZomboidCraft;
import com.zomboidcraft.client.zombie.body.ZBodyMesh;
import com.zomboidcraft.client.zombie.body.ZMove;
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
    /** Limbs shot off (Dismember's cut bits). */
    public int cuts;
    /** Crawls for good: lies face down. A zombie knocked over without this lies on its back. */
    public boolean crawler;
    /** Lost both legs (and lying down, as it always is then): drawn with stumps. */
    public boolean legless;
    /** The new body for this outfit, or null to draw the old model. */
    public ZBodyMesh zbody;
    public Identifier zskin;
    /** The outfit's baked far-off body (clothes painted on, only the big pieces 3D) and its atlas, or null. */
    public ZBodyMesh zlow;
    public Identifier zlowSkin;
    public Identifier zkit;
    /** The new body's eye mask, or null when look.glowingEyes is off. */
    public Identifier zeyes;
    public final ZMove.Pose zpose = new ZMove.Pose();
    /** Held by the Harvestman: how far it's drawn tipped from its grip, and laid on its back to be fed on (0..1). */
    public float heldTilt, heldLie;
    /** Crossbow bolts standing in it. */
    public java.util.List<com.zomboidcraft.combat.StuckBolts.Bolt> bolts = java.util.List.of();
}
