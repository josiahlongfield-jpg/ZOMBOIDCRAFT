package com.zomboidcraft.client.gear;

import com.google.gson.JsonObject;
import com.zomboidcraft.client.interact.InteractMenu;
import com.zomboidcraft.client.interact.InteractMenu.Option;
import com.zomboidcraft.gear.RadioItem;
import com.zomboidcraft.gear.StrikeRadio;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Inventory;

/**
 * The radio operator's air strike call on the G menu (command net wave 2): with a field radio on you, "Call an air
 * strike" opens gun run, bombs or Apache, each on your laser, the nearest smoke or the grid you're looking at.
 * While Overwatch holds a danger close call it offers "Confirm danger close" and "Cancel the call"; while your
 * strike is on its way, "Wave off the strike". Overwatch answers on the command net ({@link StrikeRadio}).
 */
public final class StrikeMenu {
    public static void register() {
        InteractMenu.provider((client, player, hit, out) -> {
            if (!fieldRadio(player)) {
                return;
            }
            JsonObject strike = HuntBoardClient.board().has("strike") ? HuntBoardClient.board().getAsJsonObject("strike") : new JsonObject();
            if (strike.has("pending")) {
                JsonObject held = strike.getAsJsonObject("pending");
                out.add(Option.of("Confirm danger close", () -> ClientPlayNetworking.send(new StrikeRadio.Call(held.get("type").getAsString(),
                        held.get("mark").getAsString(), true))));
                out.add(Option.of("Cancel the call", () -> ClientPlayNetworking.send(new StrikeRadio.Cancel("cancel"))));
                return;
            }
            if (strike.has("mine") && strike.get("mine").getAsBoolean()) {
                out.add(Option.of("Wave off the strike", () -> ClientPlayNetworking.send(new StrikeRadio.Cancel("abort"))));
                return;
            }
            boolean ready = !strike.has("ready") || strike.get("ready").getAsBoolean()
                    || strike.has("bonus") && strike.get("bonus").getAsInt() > 0;
            if (!ready && client.level != null && strike.has("readyAt")) {
                long left = strike.get("readyAt").getAsLong() - client.level.getGameTime();
                int mins = (int) Math.max(1, Math.round(left / 1200.0));
                out.add(Option.no("Call an air strike", "No air for about " + mins + (mins == 1 ? " minute" : " minutes")));
                return;
            }
            List<Option> types = new ArrayList<>();
            types.add(Option.menu("Gun run", marks("gun_run")));
            types.add(Option.menu("Bombs", marks("bombs")));
            types.add(Option.menu("Apache", marks("apache")));
            out.add(Option.menu("Call an air strike", types));
        });
    }

    private static List<Option> marks(String type) {
        return List.of(
                Option.of("On my laser", () -> ClientPlayNetworking.send(new StrikeRadio.Call(type, "laser", false))),
                Option.of("On the nearest smoke", () -> ClientPlayNetworking.send(new StrikeRadio.Call(type, "smoke", false))),
                Option.of("On the grid I'm looking at", () -> ClientPlayNetworking.send(new StrikeRadio.Call(type, "grid", false))));
    }

    private static boolean fieldRadio(LocalPlayer player) {
        if (player.isCreative()) {
            return true;
        }
        Inventory inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            if (inventory.getItem(i).getItem() instanceof RadioItem radio && radio.type() == RadioItem.Type.FIELD) {
                return true;
            }
        }
        return false;
    }

    private StrikeMenu() {
    }
}
