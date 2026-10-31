package com.zomboidcraft.clothing;

import com.zomboidcraft.ZomboidCraft;
import com.zomboidcraft.zombie.ZombieTypes;
import com.zomboidcraft.survival.Body.Region;
import com.zomboidcraft.survival.TimedActions;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Dirty, bloody and wet clothes, and washing them, Zomboid style (see {@link ClothDirt}).
 *
 * <ul>
 * <li>A melee blow on a zombie splashes blood on your gloves and the outermost layer over your chest and arms; being
 * hurt bleeds into whatever covers the torso.</li>
 * <li>Crawling and lying on the ground grime everything; walking bare ground in rain dirties boots and trousers.</li>
 * <li>Rain soaks the outer layers (less through rainproof pieces); clothes dry when out of it.</li>
 * <li>Washing (W in the kit, by water: a lake or river, a water cauldron, a sink filled from one) clears the dirt and,
 * with soap, the blood; without soap only half the blood comes out. The piece comes out soaking wet.</li>
 * </ul>
 */
public final class Grime {
    private static final String[] SOAP = {"soap_bar", "dish_soap"};

    private Grime() {
    }

    public static void register() {
        ClothDirt.register();
        ServerTickEvents.END_SERVER_TICK.register(Grime::tick);
        ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damage, blocked) -> {
            if (blocked || damage <= 0) return;
            if (entity instanceof ServerPlayer player && !player.isCreative()) {
                // your own blood: into whatever covers the torso, and the arms a little
                splash(player, Region.UPPER_TORSO, 0, Math.round(3 + damage * 2));
                if (player.getRandom().nextBoolean()) splash(player, Region.LEFT_UPPER_ARM, 0, 3);
                else splash(player, Region.RIGHT_UPPER_ARM, 0, 3);
            }
            if (ZombieTypes.isWalker(entity) && source.getDirectEntity() instanceof ServerPlayer player
                    && source.getEntity() == player && player.distanceToSqr(entity) < 16) {
                RandomSource r = player.getRandom();
                if (r.nextFloat() < 0.6f) {
                    int b = 2 + r.nextInt(4);
                    splashSlot(player, ClothingSlot.HANDS, 0, b + 2);
                    splash(player, Region.UPPER_TORSO, 0, b);
                    splash(player, r.nextBoolean() ? Region.RIGHT_UPPER_ARM : Region.LEFT_UPPER_ARM, 0, b + 1);
                }
            }
        });
    }

    /** Dirt and blood over a body part, 0 to 1, from the worst piece covering it: a wound under it festers more. */
    public static float filth(Player player, Region region) {
        float worst = 0;
        for (ClothingSlot slot : ClothingSlot.ALL) {
            ItemStack stack = slot.worn(player);
            Garment g = Clothes.of(stack);
            if (g == null || !covers(g, region)) continue;
            worst = Math.max(worst, ClothDirt.of(stack).filth());
        }
        return worst;
    }

    /** How wet the outermost piece over this part is, 0 to 1 (for body warmth, when survival has it). */
    public static float wetness(Player player, Region region) {
        for (ClothingSlot slot : OUTER) {
            ItemStack stack = slot.worn(player);
            Garment g = Clothes.of(stack);
            if (g != null && covers(g, region)) return ClothDirt.of(stack).wet() / 100f;
        }
        return 0;
    }

    private static final java.util.List<ClothingSlot> OUTER = java.util.List.of(ClothingSlot.HAT, ClothingSlot.VEST,
            ClothingSlot.JACKET, ClothingSlot.ARM_GUARDS, ClothingSlot.LEG_GUARDS, ClothingSlot.HANDS, ClothingSlot.FEET,
            ClothingSlot.SHIRT, ClothingSlot.LEGS, ClothingSlot.UNDER);

    private static boolean covers(Garment g, Region region) {
        for (Spot s : g.covers()) {
            for (Region r : s.regions()) if (r == region) return true;
        }
        return false;
    }

    /** Dirties the outermost cloth piece over this part. */
    private static void splash(ServerPlayer player, Region region, int dirt, int blood) {
        for (ClothingSlot slot : OUTER) {
            ItemStack stack = slot.worn(player);
            Garment g = Clothes.of(stack);
            if (g != null && covers(g, region)) {
                stack.set(ClothDirt.TYPE, ClothDirt.of(stack).add(dirt, blood, 0));
                return;
            }
        }
    }

    private static void splashSlot(ServerPlayer player, ClothingSlot slot, int dirt, int blood) {
        ItemStack stack = slot.worn(player);
        if (Clothes.of(stack) != null) stack.set(ClothDirt.TYPE, ClothDirt.of(stack).add(dirt, blood, 0));
    }

    private static void tick(MinecraftServer server) {
        if (server.getTickCount() % 200 != 0) return;                                // every ten seconds
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.isCreative() || player.isSpectator()) continue;
            ServerLevel level = player.level();
            BlockPos head = BlockPos.containing(player.getX(), player.getEyeY(), player.getZ());
            boolean rain = level.isRainingAt(head.above());
            boolean ground = player.onGround() && !player.isInWater();
            boolean crawling = ground && (player.getPose() == Pose.SWIMMING || player.getPose() == Pose.SLEEPING);
            boolean moving = player.getDeltaMovement().horizontalDistanceSqr() > 0.001;
            boolean bare = ground && soft(level.getBlockState(player.getOnPos()));
            for (ClothingSlot slot : ClothingSlot.ALL) {
                ItemStack stack = slot.worn(player);
                Garment g = Clothes.of(stack);
                if (g == null || g.covers().isEmpty()) continue;
                ClothDirt d = ClothDirt.of(stack);
                int dirt = 0, wet = 0;
                if (crawling) dirt += 2;
                else if (rain && bare && moving && (slot == ClothingSlot.FEET || slot == ClothingSlot.LEGS
                        || slot == ClothingSlot.LEG_GUARDS)) dirt += 1;
                if (player.isInWater()) wet += 40;
                else if (rain && OUTER.indexOf(slot) < 7) wet += Math.round(8 * (1 - g.water()));
                else if (d.wet() > 0) wet -= 3;                                       // drying, a few minutes out of it
                if (dirt != 0 || wet != 0) stack.set(ClothDirt.TYPE, d.add(dirt, 0, wet));
            }
        }
    }

    private static boolean soft(BlockState s) {
        return s.is(Blocks.DIRT) || s.is(Blocks.GRASS_BLOCK) || s.is(Blocks.COARSE_DIRT) || s.is(Blocks.MUD)
                || s.is(Blocks.PODZOL) || s.is(Blocks.ROOTED_DIRT) || s.is(Blocks.FARMLAND) || s.is(Blocks.DIRT_PATH)
                || s.is(Blocks.GRAVEL) || s.is(Blocks.SAND);
    }

    // ------------------------------------------------------------------ washing

    /** Starts washing the piece in this menu slot (the kit's W, through Tailoring.click). */
    static void startWash(ServerPlayer player, AbstractContainerMenu menu, int slot, int carriedEnd) {
        ItemStack stack = menu.slots.get(slot).getItem();
        Garment g = Clothes.of(stack);
        if (g == null) return;
        if (ClothDirt.of(stack).clean()) {
            tell(player, "It's clean already.");
            return;
        }
        if (!byWater(player)) {
            tell(player, "You need water to wash it in: a river or pond, or a filled cauldron or sink.");
            return;
        }
        boolean soap = soap(menu, carriedEnd) >= 0;
        TimedActions.start(player, "wash:" + slot, "Washing", soap ? 60 : 90, p -> wash(p, slot));
    }

    private static void wash(ServerPlayer player, int slot) {
        AbstractContainerMenu menu = player.inventoryMenu;
        ItemStack stack = menu.slots.get(slot).getItem();
        if (Clothes.of(stack) == null || !byWater(player)) return;
        int soapAt = soap(menu, menu.slots.size());
        ClothDirt d = ClothDirt.of(stack);
        int blood = soapAt >= 0 ? 0 : d.blood() / 2;
        if (soapAt >= 0 && player.getRandom().nextFloat() < 0.2f) menu.slots.get(soapAt).getItem().shrink(1);
        stack.set(ClothDirt.TYPE, new ClothDirt(0, blood, 100));
        menu.broadcastChanges();
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BUCKET_EMPTY,
                SoundSource.PLAYERS, 0.6f, 1.2f);
        tell(player, blood > 0 ? "Washed. Without soap, some of the blood stays in." : "Washed clean. It's soaking wet.");
    }

    /** A river, pond or filled cauldron within reach. */
    private static boolean byWater(ServerPlayer player) {
        ServerLevel level = player.level();
        BlockPos at = player.blockPosition();
        for (BlockPos p : BlockPos.betweenClosed(at.offset(-2, -2, -2), at.offset(2, 1, 2))) {
            BlockState s = level.getBlockState(p);
            if (s.getFluidState().is(FluidTags.WATER)) return true;
            if (s.is(Blocks.WATER_CAULDRON) && s.getValue(LayeredCauldronBlock.LEVEL) > 0) return true;
        }
        return player.isInWater();
    }

    private static int soap(AbstractContainerMenu menu, int end) {
        for (String id : SOAP) {
            Item item = BuiltInRegistries.ITEM.getValue(ZomboidCraft.id(id));
            if (item == Items.AIR) continue;
            for (int i = InventoryMenu.INV_SLOT_START; i < Math.min(end, menu.slots.size()); i++) {
                Slot s = menu.slots.get(i);
                if (s instanceof com.zomboidcraft.carry.CarrySlots.GearSlot) continue;
                if (s instanceof com.zomboidcraft.carry.CarrySlots.BagSlot bag && !bag.open()) continue;
                if (s.getItem().is(item)) return i;
            }
        }
        return -1;
    }

    private static void tell(ServerPlayer player, String s) {
        player.sendOverlayMessage(Component.literal(s).withStyle(ChatFormatting.GRAY));
    }
}
