package com.zomboidcraft.mixin.client;

import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Lets the field-kit inventory lay the player's slots out its own way. Positions only matter on the client. */
@Mixin(Slot.class)
public interface SlotAccessor {
    @Mutable
    @Accessor("x")
    void zomboidcraft$setX(int x);

    @Mutable
    @Accessor("y")
    void zomboidcraft$setY(int y);
}
