package com.zomboidcraft.mixin.client;

import com.zomboidcraft.client.kit.KitScreen;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Swaps Minecraft's survival inventory for the kit screen whenever it opens. 26.3 opens screens through Gui. */
@Mixin(Gui.class)
public abstract class GuiMixin {
    @ModifyVariable(method = "setScreen", at = @At("HEAD"), argsOnly = true, require = 1)
    private Screen zomboidcraft$fieldKit(Screen screen) {
        if (screen != null && screen.getClass() == InventoryScreen.class) {
            return KitScreen.replace((InventoryScreen) screen);
        }
        return screen;
    }
}
