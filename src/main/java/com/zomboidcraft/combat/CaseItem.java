package com.zomboidcraft.combat;

import com.mojang.serialization.Codec;
import com.zomboidcraft.ModItems;
import com.zomboidcraft.ZcConfig;
import com.zomboidcraft.ZomboidCraft;
import com.zomboidcraft.combat.rolls.Rolls;
import com.zomboidcraft.carry.BagItem;
import com.zomboidcraft.carry.BagSpec;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.component.TooltipDisplay;

/**
 * A gun case or an ammo can: carried in the off hand like the duffel, it opens there, and takes only gun gear (an ammo
 * can only rounds and magazines). One found in the world comes packed, the way Zomboid's do: a case with its gun, a
 * magazine or two and some rounds; a can with boxes of rounds. It's filled the first time it's in someone's inventory.
 */
public class CaseItem extends BagItem {
    /** Still as it was found: what's in it is rolled the first time a player has it. */
    public static final DataComponentType<Boolean> PACKED = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
            ZomboidCraft.id("packed"), DataComponentType.<Boolean>builder()
                    .persistent(Codec.BOOL).networkSynchronized(ByteBufCodecs.BOOL).build());

    public enum Kind { RIFLE, PISTOL, AMMO }

    private final Kind kind;

    public CaseItem(Kind kind, BagSpec spec, Properties properties) {
        super(spec, properties.component(PACKED, true));
        this.kind = kind;
    }

    /** Fills a case that's still packed. Returns true if it did. */
    public static boolean unpack(ItemStack stack, RandomSource random) {
        if (!(stack.getItem() instanceof CaseItem item) || !stack.getOrDefault(PACKED, false)) {
            return false;
        }
        ZcConfig.Cases cfg = ZcConfig.get().cases;
        if (cfg.packed) {
            List<ItemStack> load = switch (item.kind) {
                case RIFLE -> gunCase(random, cfg, pick(random, new GunType[] {GunType.ASSAULT_RIFLE,
                        GunType.HUNTING_RIFLE, GunType.SHOTGUN, GunType.DOUBLE_BARREL, GunType.RIMFIRE, GunType.SMG,
                        GunType.SAWN_OFF, GunType.LEVER_ACTION}, new int[] {cfg.assaultRifleWeight, cfg.huntingRifleWeight,
                        cfg.shotgunWeight, cfg.doubleBarrelWeight, cfg.rimfireWeight, cfg.smgWeight, cfg.sawnOffWeight,
                        cfg.leverActionWeight}));
                case PISTOL -> gunCase(random, cfg, pick(random, new GunType[] {GunType.PISTOL, GunType.REVOLVER,
                        GunType.MAGNUM}, new int[] {cfg.pistolWeight, cfg.revolverWeight, cfg.magnumWeight}));
                case AMMO -> ammoCan(random, cfg);
            };
            stack.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(load));
        }
        stack.set(PACKED, false);
        return true;
    }

    /** A gun put away unloaded and looked after, with what feeds it. */
    private static List<ItemStack> gunCase(RandomSource random, ZcConfig.Cases cfg, GunType type) {
        List<ItemStack> load = new ArrayList<>();
        ItemStack gun = new ItemStack(type.item());
        gun.set(GunState.TYPE, new GunState(type.tube, 0, false));         // stored with its magazine out
        if (gun.isDamageableItem()) {
            gun.setDamageValue(random.nextInt(Math.max(1, (int) (gun.getMaxDamage() * cfg.maxWear) + 1)));
        }
        // now and then someone left a part on it
        Item part = switch (type) {
            case HUNTING_RIFLE -> random.nextDouble() < cfg.scopeChance ? ModItems.SCOPE_4X : null;
            case ASSAULT_RIFLE -> random.nextDouble() < cfg.redDotChance ? ModItems.RED_DOT_SIGHT : null;
            case PISTOL -> random.nextDouble() < cfg.lightChance ? ModItems.WEAPON_LIGHT : null;
            default -> null;
        };
        if (part != null) {
            Attachments.mount(gun, new ItemStack(part));
        }
        Rolls.roll(gun, Rolls.CASE, random);     // its tier sets its condition over the wear above
        GunFinish.roll(gun, random);
        Attachments.refreshModel(gun);      // drawn without its magazine
        load.add(gun);
        if (type.magazine() != null) {
            int mags = type == GunType.PISTOL ? 1 : 1 + random.nextInt(Math.max(1, cfg.maxMagazines));
            for (int i = 0; i < mags; i++) {
                ItemStack mag = new ItemStack(type.magazine());
                mag.set(GunState.MAG_ROUNDS, random.nextDouble() < cfg.fullMagazineChance ? type.capacity() : random.nextInt(type.capacity() + 1));
                load.add(mag);
            }
        }
        int loose = type.tube ? 6 + random.nextInt(13) : type == GunType.HUNTING_RIFLE ? 5 + random.nextInt(11) : 10 + random.nextInt(21);
        load.add(new ItemStack(type.ammo(), Math.min(loose, new ItemStack(type.ammo()).getMaxStackSize())));
        return load;
    }

    /** Boxes of one or two kinds of rounds, three or four stacks of them. */
    private static List<ItemStack> ammoCan(RandomSource random, ZcConfig.Cases cfg) {
        GunType[] kinds = {GunType.ASSAULT_RIFLE, GunType.PISTOL, GunType.SHOTGUN, GunType.HUNTING_RIFLE, GunType.REVOLVER,
                GunType.RIMFIRE, GunType.MAGNUM, GunType.LEVER_ACTION};
        int[] weights = {4, 4, 3, 3, 2, 4, 1, 3};
        GunType first = pick(random, kinds, weights), second = random.nextDouble() < cfg.secondCalibreChance ? pick(random, kinds, weights) : first;
        List<ItemStack> load = new ArrayList<>();
        int stacks = 3 + random.nextInt(2);
        for (int i = 0; i < stacks; i++) {
            Item ammo = (i % 2 == 0 ? first : second).ammo();
            int max = new ItemStack(ammo).getMaxStackSize();
            load.add(new ItemStack(ammo, max / 2 + random.nextInt(max / 2 + 1)));
        }
        return load;
    }

    private static GunType pick(RandomSource random, GunType[] types, int[] weights) {
        int total = 0;
        for (int w : weights) {
            total += Math.max(0, w);
        }
        if (total <= 0) {
            return types[0];
        }
        int r = random.nextInt(total);
        for (int i = 0; i < types.length; i++) {
            r -= Math.max(0, weights[i]);
            if (r < 0) {
                return types[i];
            }
        }
        return types[0];
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        if (stack.getOrDefault(PACKED, false)) {
            tooltip.accept(Component.literal("Shut, as it was found").withStyle(ChatFormatting.GRAY));
        }
        tooltip.accept(Component.literal(kind == Kind.AMMO ? "Holds rounds and magazines" : "Holds guns, parts, magazines and rounds")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
