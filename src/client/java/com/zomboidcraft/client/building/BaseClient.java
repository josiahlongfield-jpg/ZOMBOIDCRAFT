package com.zomboidcraft.client.building;

import com.zomboidcraft.building.base.Claim;
import com.zomboidcraft.building.base.Claims;
import com.zomboidcraft.building.base.Rooms;
import com.zomboidcraft.client.hud.RoomTape;
import com.zomboidcraft.client.interact.InteractMenu;
import com.zomboidcraft.client.interact.InteractMenu.Option;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.EntityHitResult;

/**
 * The client side of base claims and rooms: keeps the claims the server sends, shows the room readout, and puts the
 * claim and squad lines in the G menu (claim this building, let it go, add someone to the squad, leave a squad).
 */
public final class BaseClient {
    /** The base page's last list of rooms, and when it came. */
    public static volatile List<Rooms.Line> report = List.of();
    public static volatile long reportAt;

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(Claims.Sync.TYPE, (payload, context) -> Claims.clientView(payload.claims()));
        ClientPlayNetworking.registerGlobalReceiver(Rooms.Status.TYPE, (payload, context) ->
                context.client().execute(() -> RoomTape.show(payload.type(), payload.grade(), payload.next())));
        ClientPlayNetworking.registerGlobalReceiver(Rooms.Report.TYPE, (payload, context) -> {
            report = List.copyOf(payload.lines());
            reportAt = System.currentTimeMillis();
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            Claims.clientView(List.of());
            report = List.of();
        });

        InteractMenu.provider((client, player, hit, out) -> {
            if (client.level == null || player.isSpectator()) return;
            UUID me = player.getUUID();
            Claim mine = Claims.baseOf(client.level, me);
            if (hit instanceof EntityHitResult e && e.getEntity() instanceof Player other && other != player) {
                if (mine == null) {
                    out.add(Option.no("Add " + other.getName().getString() + " to your squad", "Claim a base first"));
                } else if (mine.squad().contains(other.getUUID())) {
                    out.add(Option.of("Take " + other.getName().getString() + " off your squad", () -> send(Claims.REMOVE, other.getUUID())));
                } else {
                    out.add(Option.of("Add " + other.getName().getString() + " to your squad", () -> send(Claims.ADD, other.getUUID())));
                }
                return;
            }
            if (client.level.canSeeSky(player.blockPosition())) return;
            Claim here = Claims.at(client.level, player.blockPosition());
            if (here == null) {
                out.add(Option.timed(mine == null ? "Claim this building as your base" : "Move your base to this building", 0,
                        () -> send(Claims.CLAIM, me)));
            } else if (here.owner().equals(me)) {
                out.add(Option.menu("Your base", List.of(Option.of("Let this base go", () -> send(Claims.RELEASE, me)))));
            } else if (here.squad().contains(me)) {
                out.add(Option.menu(here.ownerName() + "'s base", List.of(Option.of("Leave the squad", () -> send(Claims.LEAVE, here.owner())))));
            } else {
                out.add(Option.no("Claim this building", "It's " + here.ownerName() + "'s base"));
            }
        });
    }

    /** Asks for the base page's list (at most every couple of seconds while it's open). */
    public static void survey() {
        if (System.currentTimeMillis() - reportAt < 2000) return;
        reportAt = System.currentTimeMillis();
        ClientPlayNetworking.send(new Rooms.Survey());
    }

    private static void send(int action, UUID target) {
        ClientPlayNetworking.send(new Claims.Act(action, target));
    }

    private BaseClient() {
    }
}
