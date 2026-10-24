package com.zomboidcraft.client.zombie;

import com.zomboidcraft.ZomboidCraft;
import net.minecraft.client.renderer.entity.state.ZombieRenderState;
import net.minecraft.resources.Identifier;

public class WalkerRenderState extends ZombieRenderState {
    public Identifier texture = ZomboidCraft.id("textures/entity/walker/civilian_pale.png");
    /** Stable per-zombie number, so each one lolls and sways a little differently. */
    public int variant;
}
