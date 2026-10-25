package com.zomboidcraft.mixin.client;

import com.zomboidcraft.client.kit.WeaponTip;
import com.zomboidcraft.combat.rolls.WeaponCard;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner;
import net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A weapon's item tooltip becomes the kit card alone: its text lines are left out (the card says all of it) and the
 * dark-glass style replaces whatever frame the screen asked for.
 */
@Mixin(GuiGraphicsExtractor.class)
public abstract class GuiGraphicsExtractorMixin {
    @Shadow
    private void setTooltipForNextFrameInternal(Font font, List<ClientTooltipComponent> lines, int x, int y,
                                                ClientTooltipPositioner positioner, @Nullable Identifier style,
                                                boolean centred, boolean focused) {
        throw new AssertionError();
    }

    @Inject(method = "setTooltipForNextFrame(Lnet/minecraft/client/gui/Font;Ljava/util/List;Ljava/util/Optional;IILnet/minecraft/resources/Identifier;Z)V",
            at = @At("HEAD"), cancellable = true, require = 1)
    private void zomboidcraft$weaponCard(Font font, List<Component> lines, Optional<TooltipComponent> image, int x, int y,
                                         @Nullable Identifier style, boolean focused, CallbackInfo ci) {
        if (image.isPresent() && image.get() instanceof WeaponCard card) {
            List<ClientTooltipComponent> parts = new ArrayList<>();
            parts.add(new WeaponTip(card.stack()));
            setTooltipForNextFrameInternal(font, parts, x, y, DefaultTooltipPositioner.INSTANCE, WeaponTip.STYLE, false, focused);
            ci.cancel();
        }
    }
}
