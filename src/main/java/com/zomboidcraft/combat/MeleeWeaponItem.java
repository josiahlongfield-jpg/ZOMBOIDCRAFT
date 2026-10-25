package com.zomboidcraft.combat;

import com.zomboidcraft.ZcConfig;
import com.zomboidcraft.ZomboidCraft;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.component.Weapon;

/** A Zomboid-style melee weapon: its own damage, swing speed, reach and knockback, and a condition that wears down. */
public class MeleeWeaponItem extends Item {
    public MeleeWeaponItem(Properties properties) {
        super(properties);
    }

    public static Properties properties(WeaponSpecs.WeaponSpec spec) {
        ItemAttributeModifiers attributes = ItemAttributeModifiers.builder()
                .add(Attributes.ATTACK_DAMAGE, new AttributeModifier(Item.BASE_ATTACK_DAMAGE_ID,
                        spec.damage() * ZcConfig.get().combat.meleeDamage - 1, AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.MAINHAND)
                .add(Attributes.ATTACK_SPEED, new AttributeModifier(Item.BASE_ATTACK_SPEED_ID,
                        spec.speed() - 4, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
                .add(Attributes.ENTITY_INTERACTION_RANGE, new AttributeModifier(ZomboidCraft.id("weapon_reach"),
                        spec.reach() - 3, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
                .add(Attributes.ATTACK_KNOCKBACK, new AttributeModifier(ZomboidCraft.id("weapon_knockback"),
                        spec.knockback(), AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
                .build();
        int wear = ZcConfig.get().combat.weaponWear ? 1 : 0;
        return new Properties().stacksTo(1).durability(spec.durability()).attributes(attributes)
                .component(DataComponents.WEAPON, new Weapon(wear, 0f)).enchantable(10);
    }

    /** Wearing down doesn't make the weapon dip out of view as if it had been swapped. */
    @Override
    public boolean allowComponentsUpdateAnimation(Player player, InteractionHand hand, ItemStack oldStack, ItemStack newStack) {
        return false;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        WeaponSpecs.WeaponSpec spec = Weapons.spec(stack);
        if (spec == null) {
            return;
        }
        tooltip.accept(condition(stack));
        if (spec.twoHanded()) {
            tooltip.accept(Component.literal("Two-handed: hits harder with nothing in your off hand")
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    /** "Condition: Worn" in Zomboid's words, coloured by how close it is to breaking. */
    public static Component condition(ItemStack stack) {
        if (!stack.isDamageableItem()) {
            return Component.empty();
        }
        float left = 1f - (float) stack.getDamageValue() / stack.getMaxDamage();
        String word = left > 0.85f ? "Pristine" : left > 0.6f ? "Good" : left > 0.35f ? "Worn"
                : left > 0.15f ? "Battered" : "About to break";
        ChatFormatting colour = left > 0.6f ? ChatFormatting.GREEN : left > 0.35f ? ChatFormatting.YELLOW
                : left > 0.15f ? ChatFormatting.GOLD : ChatFormatting.RED;
        return Component.literal("Condition: ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(word + " (" + Math.round(left * 100) + "%)").withStyle(colour));
    }
}
