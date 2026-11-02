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

    /**
     * Fills a case that's still packed. Returns true if it did. Its gun comes from Arsenal's gun cases (combat/GunLoot):
     * a Brita gun kept on this computer or the Heavy Sniper; with none to pick, it was put away with only its magazines'
     * worth of rounds (our old guns are gone, Josiah 2026-11-02).
     */
    public static boolean unpack(ItemStack stack, RandomSource random) {
        if (!(stack.getItem() instanceof CaseItem item) || !stack.getOrDefault(PACKED, false)) {
            return false;
        }
        ZcConfig.Cases cfg = ZcConfig.get().cases;
        if (cfg.packed) {
            List<ItemStack> load;
            if (item.kind == Kind.AMMO) {
                load = ammoCan(random, cfg);
            } else {
                ItemStack gun = GunLoot.caseGun(item.kind == Kind.PISTOL, random);
                load = gun != null && GunType.of(gun) != null ? gunCase(random, cfg, gun, GunType.of(gun)) : rounds(random, item.kind == Kind.PISTOL);
            }
            stack.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(load));
        }
        stack.set(PACKED, false);
        return true;
    }

    /** A gun put away unloaded and looked after, with what feeds it. */
    private static List<ItemStack> gunCase(RandomSource random, ZcConfig.Cases cfg, ItemStack gun, GunType type) {
        List<ItemStack> load = new ArrayList<>();
        gun.set(GunState.TYPE, new GunState(type.tube, 0, false));         // stored with its magazine out
        if (gun.isDamageableItem()) {
            gun.setDamageValue(random.nextInt(Math.max(1, (int) (gun.getMaxDamage() * cfg.maxWear) + 1)));
        }
        // now and then someone left its scope on the Heavy Sniper; a Brita gun only ever takes what Brita gives it
        if (type == GunType.HEAVY_SNIPER && gun.get(LocalGuns.ID) == null && random.nextDouble() < cfg.scopeChance) {
            Attachments.mount(gun, new ItemStack(ModItems.LONG_RANGE_SCOPE));
        }
        Rolls.roll(gun, Rolls.CASE, random);     // its tier sets its condition over the wear above
        GunFinish.roll(gun, random);
        Attachments.paint(gun, random);
        Attachments.refreshModel(gun);      // drawn without its magazine
        load.add(gun);
        if (type.magazine() != null) {
            int mags = type.handgun() ? 1 : 1 + random.nextInt(Math.max(1, cfg.maxMagazines));
            int cap = type.capacity(gun);
            for (int i = 0; i < mags; i++) {
                ItemStack mag = LocalGuns.magazineFor(gun, type);
                mag.set(GunState.MAG_ROUNDS, random.nextDouble() < cfg.fullMagazineChance ? cap : random.nextInt(cap + 1));
                load.add(mag);
            }
        }
        Item ammo = LocalGuns.ammo(gun, type);
        int loose = type == GunType.GRENADE_LAUNCHER ? 1 + random.nextInt(3) : type.tube ? 6 + random.nextInt(13)
                : type == GunType.HEAVY_SNIPER ? 3 + random.nextInt(6) : 10 + random.nextInt(21);
        load.add(new ItemStack(ammo, Math.min(loose, new ItemStack(ammo).getMaxStackSize())));
        return load;
    }

    /** A case put away without its gun: a box or two of rounds. */
    private static List<ItemStack> rounds(RandomSource random, boolean pistol) {
        Item[] kinds = pistol ? new Item[] {ModItems.PISTOL_AMMO, ModItems.REVOLVER_AMMO, ModItems.POCKET_AMMO, ModItems.MAGNUM_AMMO}
                : new Item[] {ModItems.RIFLE_AMMO, ModItems.HUNTING_RIFLE_AMMO, ModItems.SHOTGUN_SHELL, ModItems.RIMFIRE_AMMO};
        Item ammo = kinds[random.nextInt(kinds.length)];
        List<ItemStack> load = new ArrayList<>();
        int boxes = 1 + random.nextInt(2);
        for (int i = 0; i < boxes; i++) {
            load.add(new ItemStack(ammo, 8 + random.nextInt(13)));
        }
        return load;
    }

    /** Boxes of one or two kinds of rounds, three or four stacks of them: ours, and those of the guns kept here. */
    private static List<ItemStack> ammoCan(RandomSource random, ZcConfig.Cases cfg) {
        List<Item> kinds = new ArrayList<>(List.of(ModItems.RIFLE_AMMO, ModItems.PISTOL_AMMO, ModItems.SHOTGUN_SHELL,
                ModItems.HUNTING_RIFLE_AMMO, ModItems.REVOLVER_AMMO, ModItems.RIMFIRE_AMMO, ModItems.MAGNUM_AMMO,
                ModItems.POCKET_AMMO, ModItems.HEAVY_AMMO));
        List<Integer> weights = new ArrayList<>(List.of(4, 4, 3, 3, 2, 4, 1, 2, 1));
        for (Item local : GunLoot.localRounds()) {
            if (!kinds.contains(local)) {
                kinds.add(local);
                weights.add(2);
            }
        }
        Item first = pick(random, kinds, weights), second = random.nextDouble() < cfg.secondCalibreChance ? pick(random, kinds, weights) : first;
        List<ItemStack> load = new ArrayList<>();
        int stacks = 3 + random.nextInt(2);
        for (int i = 0; i < stacks; i++) {
            Item ammo = i % 2 == 0 ? first : second;
            int max = new ItemStack(ammo).getMaxStackSize();
            load.add(new ItemStack(ammo, max / 2 + random.nextInt(max / 2 + 1)));
        }
        return load;
    }

    private static Item pick(RandomSource random, List<Item> items, List<Integer> weights) {
        int total = 0;
        for (int w : weights) {
            total += Math.max(0, w);
        }
        int r = random.nextInt(Math.max(1, total));
        for (int i = 0; i < items.size(); i++) {
            r -= Math.max(0, weights.get(i));
            if (r < 0) {
                return items.get(i);
            }
        }
        return items.get(0);
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
