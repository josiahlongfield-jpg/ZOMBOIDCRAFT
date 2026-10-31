package com.zomboidcraft.client.building;

import com.zomboidcraft.building.DigRules;
import com.zomboidcraft.building.MoveNet;
import com.zomboidcraft.building.Moving;
import com.zomboidcraft.carry.Weights;
import com.zomboidcraft.client.interact.InteractMenu;
import com.zomboidcraft.client.interact.InteractMenu.Option;
import java.util.Locale;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * Zomboid's moveable options in the G menu, on any piece of the town's furniture: Pick up, saying what it weighs and
 * the chance it breaks at your level (Zomboid's tooltip), and Scrap, for what it's made of, with the tool for its
 * material. What can't be done stays in the list, greyed, saying the tool or the emptying it needs.
 */
public final class MoveMenu {
    public static void register() {
        InteractMenu.provider((client, player, hit, out) -> {
            if (!(hit instanceof BlockHitResult b) || hit.getType() != HitResult.Type.BLOCK || client.level == null) return;
            if (b.getLocation().distanceTo(player.getEyePosition()) > 4.5 || player.isCreative()) return;
            BlockPos pos = b.getBlockPos();
            BlockState state = client.level.getBlockState(pos);
            Moving.Piece piece = Moving.piece(state, client.level, pos);
            if (piece == null) return;
            String no = Moving.refusal(piece, player, client.level, pos);
            if (no != null) {
                out.add(Option.no("Pick up", no));
            } else {
                double chance = piece.breakChance(player);
                String label = "Pick up (" + Weights.format(piece.kg()) + (chance <= 0 ? ")"
                        : String.format(Locale.ROOT, ", %s%% break chance)", pct(chance)));
                float secs = MoveNet.liftTicks(piece, player, client.level, pos) / 20f;
                out.add(Option.timed(label, secs, () -> ClientPlayNetworking.send(new MoveNet.Move(pos, false))));
            }
            DigRules.Tier tier = DigRules.propTier(state, client.level, pos);
            if (tier != null) {
                out.add(DigRules.fits(tier, player.getMainHandItem())
                        ? Option.timed("Scrap for " + (tier == DigRules.Tier.WOOD ? "planks" : tier == DigRules.Tier.MASONRY ? "rubble" : "scrap metal"),
                                MoveNet.SCRAP_TICKS / 20f, () -> ClientPlayNetworking.send(new MoveNet.Move(pos, true)))
                        : Option.no("Scrap", "Needs " + tier.tools() + " in hand"));
            }
        });
    }

    private static String pct(double chance) {
        String s = String.format(Locale.ROOT, "%.1f", chance * 100);
        return s.endsWith(".0") ? s.substring(0, s.length() - 2) : s;
    }

    private MoveMenu() {
    }
}
