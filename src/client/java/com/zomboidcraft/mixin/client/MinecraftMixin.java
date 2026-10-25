package com.zomboidcraft.mixin.client;

import com.zomboidcraft.client.inventory.FieldKitScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Swaps Minecraft's survival inventory for the field-kit one whenever it opens. */
@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
    @ModifyVariable(method = "setScreenAndShow", at = @At("HEAD"), argsOnly = true)
    private Screen zomboidcraft$fieldKit(Screen screen) {
        if (screen != null && screen.getClass() == InventoryScreen.class) {
            return FieldKitScreen.replace((InventoryScreen) screen);
        }
        return screen;
    }
}
