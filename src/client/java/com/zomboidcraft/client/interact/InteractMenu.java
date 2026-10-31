package com.zomboidcraft.client.interact;

import com.mojang.blaze3d.platform.InputConstants;
import com.zomboidcraft.client.hud.Hud;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * Zomboid's right-click menu, on G (PZ review section 16, Josiah 2026-10-31: "G stays, but it offers every option
 * Zomboid's right-click menu would"). A tap of G still does the obvious thing at once; holding it opens this list
 * beside the crosshair with everything you can do to what you're looking at, in sub-menus where Zomboid has them.
 * What you can't do yet stays in the list, greyed, saying what's missing (the skill, the tool, the item).
 * <p>
 * Every system adds its own options with {@link #provider}: when a thread builds something you can do to a thing,
 * it registers a provider here, and the menu grows with the game. Timed options join Zomboid's action queue.
 */
public final class InteractMenu {
    /**
     * One line: what it says, why it can't be done (null if it can), and what picking it does; or a sub-menu.
     * seconds is shown beside it when known (0 for instant or unknown).
     */
    public record Option(String label, String why, float seconds, Runnable run, List<Option> sub) {
        public static Option of(String label, Runnable run) {
            return new Option(label, null, 0, run, List.of());
        }

        public static Option timed(String label, float seconds, Runnable run) {
            return new Option(label, null, seconds, run, List.of());
        }

        /** Shown greyed with the reason. */
        public static Option no(String label, String why) {
            return new Option(label, why, 0, null, List.of());
        }

        public static Option menu(String label, List<Option> sub) {
            return new Option(label, null, 0, null, List.copyOf(sub));
        }

        public boolean can() {
            return why == null;
        }
    }

    /** Adds what can be done to what the player is looking at (or sitting in) right now. */
    public interface Provider {
        void options(Minecraft client, LocalPlayer player, HitResult hit, List<Option> out);
    }

    private static final List<Provider> PROVIDERS = new ArrayList<>();

    public static void provider(Provider provider) {
        PROVIDERS.add(provider);
    }

    static {
        provider(InteractMenu::systems);
        provider(InteractMenu::blocks);
    }

    /** The mod's own buttons and bells: each does what a tap of G already does there. */
    private static void systems(Minecraft client, LocalPlayer player, HitResult hit, List<Option> out) {
        if (hit instanceof net.minecraft.world.phys.EntityHitResult e && e.getEntity() instanceof net.minecraft.world.entity.player.Player p
                && p != player && com.zomboidcraft.survival.Downs.downed(p)) {
            out.add(Option.timed("Help them up", 0, () -> com.zomboidcraft.client.survival.DownedClient.interact(client, hit)));
        }
        if (!(hit instanceof BlockHitResult b) || hit.getType() != HitResult.Type.BLOCK) return;
        if (b.getLocation().distanceTo(player.getEyePosition()) > 4.5) return;
        BlockState state = client.level.getBlockState(b.getBlockPos());
        if (state.is(com.zomboidcraft.world.lifts.Lifts.BUTTON) || state.is(com.zomboidcraft.world.lifts.Lifts.DOOR)) {
            out.add(Option.of("Call the lift", () -> com.zomboidcraft.client.lifts.LiftClient.interact(client, hit)));
        } else if (state.is(com.zomboidcraft.world.trains.Trains.CALL_BUTTON)) {
            out.add(Option.of("Call the train", () -> com.zomboidcraft.client.trains.TrainClient.interact(client, hit)));
        } else if (state.is(com.zomboidcraft.world.ferry.Ferries.BELL)) {
            out.add(Option.of("Ring for the ferry", () -> com.zomboidcraft.client.ferry.FerryClient.interact(client, hit)));
        } else if (state.getBlock() instanceof com.zomboidcraft.world.blocks.LightSwitch.Switch) {
            out.add(Option.of("Flip the light switch", () -> com.zomboidcraft.client.lights.LightClient.interact(client, hit)));
        } else if (com.zomboidcraft.world.files.LabFiles.id(state) != null) {
            out.add(Option.of("Read", () -> com.zomboidcraft.client.kit.FilesClient.interact(client, hit)));
        }
    }

    /** Everything on offer here, in the order providers were registered. */
    public static List<Option> collect(Minecraft client, LocalPlayer player, HitResult hit) {
        List<Option> out = new ArrayList<>();
        for (Provider p : PROVIDERS) p.options(client, player, hit, out);
        return out;
    }

    /** Opens the menu on what's under the crosshair (held G). */
    public static void open(Minecraft client) {
        LocalPlayer player = client.player;
        if (player == null || client.level == null) return;
        List<Option> opts = collect(client, player, client.hitResult);
        String title = title(client, client.hitResult);
        client.gui.setScreen(new MenuScreen(title, opts));
    }

    private static String title(Minecraft client, HitResult hit) {
        if (client.player != null && client.player.getVehicle() != null) return client.player.getVehicle().getName().getString();
        if (hit instanceof net.minecraft.world.phys.EntityHitResult e) return e.getEntity().getName().getString();
        if (hit instanceof BlockHitResult b && hit.getType() == HitResult.Type.BLOCK) {
            return client.level.getBlockState(b.getBlockPos()).getBlock().getName().getString();
        }
        return "Here";
    }

    /** The game's own usable blocks: doors, hatches, gates, levers and buttons, beds, anything with a container. */
    private static void blocks(Minecraft client, LocalPlayer player, HitResult hit, List<Option> out) {
        if (!(hit instanceof BlockHitResult b) || hit.getType() != HitResult.Type.BLOCK) return;
        if (b.getLocation().distanceTo(player.getEyePosition()) > 4.5) return;
        BlockState state = client.level.getBlockState(b.getBlockPos());
        Runnable use = () -> {
            if (client.gameMode != null) client.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, b);
        };
        boolean open = state.hasProperty(BlockStateProperties.OPEN) && state.getValue(BlockStateProperties.OPEN);
        if (state.getBlock() instanceof DoorBlock) {
            out.add(Option.of(open ? "Close door" : "Open door", use));
        } else if (state.getBlock() instanceof TrapDoorBlock) {
            out.add(Option.of(open ? "Close hatch" : "Open hatch", use));
        } else if (state.getBlock() instanceof FenceGateBlock) {
            out.add(Option.of(open ? "Close gate" : "Open gate", use));
        } else if (state.getBlock() instanceof LeverBlock) {
            out.add(Option.of("Pull lever", use));
        } else if (state.getBlock() instanceof ButtonBlock) {
            out.add(Option.of("Press button", use));
        } else if (state.getBlock() instanceof BedBlock) {
            out.add(Option.of("Sleep", use));
        } else if (client.level.getBlockEntity(b.getBlockPos()) instanceof Container) {
            out.add(Option.of("Loot", use));
        }
    }

    /** The list itself: Zomboid's context menu in the kit's look, with sub-menus to the side. */
    static final class MenuScreen extends Screen {
        private static final int W = 170, ROW = 13;
        private final String title;
        private final List<Option> options;
        /** Which line is lit at each depth (mouse or keys), and which sub-menu is open. */
        private int sel, subSel = -1;
        private boolean inSub;
        private final List<int[]> rows = new ArrayList<>();

        /** When G last came in: the key repeating while it's still held mustn't close the menu it just opened. */
        private long lastG = net.minecraft.util.Util.getMillis();

        MenuScreen(String title, List<Option> options) {
            super(Component.literal(title));
            this.title = title;
            this.options = options;
        }

        @Override
        public boolean isPauseScreen() {
            return false;
        }

        @Override
        public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float partialTick) {
            rows.clear();
            int x = width / 2 + 24, y = Math.max(8, height / 2 - 40);
            int h = 16 + Math.max(1, options.size()) * ROW + 4;
            if (y + h > height - 8) y = Math.max(8, height - 8 - h);
            panel(g, x, y, W, h);
            caps(g, title, x + 8, y + 5, 0xFFF2EFE8, 0.8f);
            capsRight(g, "G", x + W - 8, y + 5, 0x4DE8E4DA, 0.8f);
            g.fill(x + 1, y + 15, x + W - 1, y + 16, 0x29E8E4DA);
            if (options.isEmpty()) {
                text(g, "Nothing to do here", x + 8, y + 19, 0x4DE8E4DA, 0.85f);
                return;
            }
            int ry = y + 17;
            for (int i = 0; i < options.size(); i++) {
                Option o = options.get(i);
                boolean over = mx >= x && mx < x + W && my >= ry && my < ry + ROW;
                if (over && !inSubArea(mx, my)) {
                    sel = i;
                    inSub = false;
                }
                line(g, o, x, ry, i == sel && !inSub || i == sel && !o.sub().isEmpty());
                rows.add(new int[]{0, i, x, ry, W, ROW});
                if (i == sel && !o.sub().isEmpty()) {
                    int sx = x + W - 2, sy = ry - 2, sh = o.sub().size() * ROW + 4;
                    if (sx + W > width - 4) sx = x - W + 2;
                    panel(g, sx, sy, W, sh);
                    for (int k = 0; k < o.sub().size(); k++) {
                        int ky = sy + 2 + k * ROW;
                        boolean ov = mx >= sx && mx < sx + W && my >= ky && my < ky + ROW;
                        if (ov) {
                            subSel = k;
                            inSub = true;
                        }
                        line(g, o.sub().get(k), sx, ky, inSub && subSel == k);
                        rows.add(new int[]{1, k, sx, ky, W, ROW});
                    }
                }
                ry += ROW;
            }
            // the reason under a greyed line, as Zomboid's tooltip gives it
            Option lit = lit();
            if (lit != null && !lit.can()) {
                int ty = y + h + 3;
                int tw = Math.round(width(lit.why(), 0.8f)) + 10;
                panel(g, x, ty, Math.max(tw, 60), 13);
                text(g, lit.why(), x + 5, ty + 3, 0xFFD0675E, 0.8f);
            }
        }

        private boolean inSubArea(int mx, int my) {
            for (int[] r : rows) {
                if (r[0] == 1 && mx >= r[2] && mx < r[2] + r[4] && my >= r[3] && my < r[3] + r[5]) return true;
            }
            return false;
        }

        private Option lit() {
            if (sel < 0 || sel >= options.size()) return null;
            Option o = options.get(sel);
            if (inSub && subSel >= 0 && subSel < o.sub().size()) return o.sub().get(subSel);
            return o;
        }

        private void line(GuiGraphicsExtractor g, Option o, int x, int y, boolean on) {
            if (on) g.fill(x + 1, y, x + W - 1, y + ROW, 0x1FE8E4DA);
            int colour = o.can() ? 0xFFE8E4DA : 0x4DE8E4DA;
            text(g, o.label(), x + 8, y + 3, colour, 0.85f);
            if (!o.sub().isEmpty()) {
                textRight(g, ">", x + W - 7, y + 3, 0xA8E8E4DA, 0.85f);
            } else if (o.seconds() > 0) {
                textRight(g, Math.round(o.seconds()) + "s", x + W - 7, y + 3, 0x4DE8E4DA, 0.75f);
            }
        }

        private void pick(Option o) {
            if (o == null || !o.can()) return;
            if (!o.sub().isEmpty()) {
                inSub = true;
                subSel = 0;
                return;
            }
            onClose();
            com.zomboidcraft.client.sound.UiSounds.play(com.zomboidcraft.client.sound.UiSounds.CLICK);
            if (o.run() != null) o.run().run();
        }

        @Override
        public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
            for (int[] r : rows) {
                if (event.x() >= r[2] && event.x() < r[2] + r[4] && event.y() >= r[3] && event.y() < r[3] + r[5]) {
                    if (r[0] == 0) {
                        sel = r[1];
                        inSub = false;
                        pick(options.get(sel));
                    } else {
                        subSel = r[1];
                        pick(options.get(sel).sub().get(subSel));
                    }
                    return true;
                }
            }
            onClose();
            return true;
        }

        @Override
        public boolean keyPressed(KeyEvent event) {
            int k = event.key();
            int n = inSub ? options.get(sel).sub().size() : options.size();
            if (n > 0 && (k == InputConstants.KEY_DOWN || k == InputConstants.KEY_S)) {
                if (inSub) subSel = (subSel + 1) % n;
                else sel = (sel + 1) % n;
                return true;
            }
            if (n > 0 && (k == InputConstants.KEY_UP || k == InputConstants.KEY_W)) {
                if (inSub) subSel = Math.floorMod(subSel - 1, n);
                else sel = Math.floorMod(sel - 1, n);
                return true;
            }
            if (k == InputConstants.KEY_RIGHT || k == InputConstants.KEY_D) {
                if (!inSub && !options.isEmpty() && !options.get(sel).sub().isEmpty()) {
                    inSub = true;
                    subSel = 0;
                }
                return true;
            }
            if (k == InputConstants.KEY_LEFT || k == InputConstants.KEY_A) {
                inSub = false;
                return true;
            }
            if (k == InputConstants.KEY_RETURN || k == InputConstants.KEY_NUMPADENTER || k == InputConstants.KEY_SPACE) {
                pick(lit());
                return true;
            }
            if (com.zomboidcraft.client.cars.CarClient.INTERACT.matches(event)) {
                long now = net.minecraft.util.Util.getMillis();
                if (now - lastG > 600) onClose();
                lastG = now;
                return true;
            }
            return super.keyPressed(event);
        }

        /** Picks the lit line (the UI test calls it). */
        void pickLit() {
            pick(lit());
        }

        void select(int main, int sub) {
            sel = main;
            subSel = sub;
            inSub = sub >= 0;
        }

        // ---- the kit's look, drawn with the HUD font ----

        private static void panel(GuiGraphicsExtractor g, int x, int y, int w, int h) {
            g.fill(x, y, x + w, y + h, 0xF50C0D0F);
            g.outline(x, y, w, h, 0x2EE8E4DA);
        }

        private void text(GuiGraphicsExtractor g, String s, float x, float y, int colour, float scale) {
            var pose = g.pose();
            pose.pushMatrix();
            pose.translate(x, y);
            pose.scale(scale, scale);
            g.text(font, Hud.text(s), 0, 0, colour, false);
            pose.popMatrix();
        }

        private float width(String s, float scale) {
            return font.width(Hud.text(s)) * scale;
        }

        private void textRight(GuiGraphicsExtractor g, String s, float right, float y, int colour, float scale) {
            text(g, s, right - width(s, scale), y, colour, scale);
        }

        private void caps(GuiGraphicsExtractor g, String s, float x, float y, int colour, float scale) {
            Hud.caps(g, font, s, x, y, colour, 1.2f, scale);
        }

        private void capsRight(GuiGraphicsExtractor g, String s, float right, float y, int colour, float scale) {
            Hud.caps(g, font, s, right - Hud.capsWidth(font, s, 1.2f, scale), y, colour, 1.2f, scale);
        }
    }

    private InteractMenu() {
    }
}
