package com.zomboidcraft.client.survivor;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.zomboidcraft.ZomboidCraft;
import com.zomboidcraft.clothing.Camos;
import com.zomboidcraft.clothing.Clothes;
import com.zomboidcraft.clothing.ClothingLoot;
import com.zomboidcraft.clothing.ClothingSlot;
import com.zomboidcraft.clothing.Garment;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * What a survivor wears, as Zomboid's own clothes: each of our wearables (clothing/Clothes) is drawn as the Zomboid
 * clothingItems clothes_map.json names for it, in her pack's model for her sex. A garment Zomboid paints on the skin
 * (shirts, shoes, gloves) goes into the body's texture; one with a model is drawn as its own mesh, and the skin under it
 * is hidden (its masks) so it never shows through. Which of a garment's textures and tints it wears is fixed by who
 * wears it and what it is, so it looks the same to everyone. Camo pieces wear our camo pattern in the Zomboid
 * texture's light and shade; crusader pieces their order's finish.
 */
public final class SurvivorClothes {
    /** A wearable's Zomboid pieces and how they're finished. */
    record Mapping(List<String> pz, boolean camo, boolean plate, int colour) {
    }

    /** A garment's mesh drawn this frame, with its texture. */
    public record Piece(String piece, Identifier texture) {
    }

    /** All a survivor wears: meshes, body layers (painted-on garments, then the skin hidden), and the hat category. */
    public record Dress(List<Piece> pieces, List<SurvivorTextures.Layer> body, String hat, boolean ready) {
        static final Dress NONE = new Dress(List.of(), List.of(), null, true);
    }

    /** Inner to outer: what's over is painted (and drawn) after what's under. */
    private static final List<ClothingSlot> ORDER = List.of(ClothingSlot.UNDER, ClothingSlot.SHIRT, ClothingSlot.LEGS,
            ClothingSlot.LEG_GUARDS, ClothingSlot.FEET, ClothingSlot.HANDS, ClothingSlot.BELT, ClothingSlot.JACKET,
            ClothingSlot.ARM_GUARDS, ClothingSlot.VEST, ClothingSlot.NECK, ClothingSlot.FACE, ClothingSlot.EYES, ClothingSlot.HAT);

    /** Zomboid's random clothing tints: worn, everyday colours. */
    private static final int[] TINTS = {0x2B3A55, 0x6B2A2A, 0x3E5A3A, 0x7A6A4A, 0x4A4A4E, 0x8C8C88, 0x1E1E22, 0xB8B0A0,
            0x5A3D2A, 0x2F5A6A, 0x7A4A6A, 0xA05A2A, 0x4A5A2A, 0xC8C0B0, 0x6A1E2A, 0x3A3A5A};

    /** A crusader order's plate, as a tint over Zomboid's steel (none keeps it bright steel). */
    private static int finish(String order) {
        return switch (order == null ? "" : order) {
            case "black" -> 0x3A3A42;
            case "crimson" -> 0x8A2A30;
            case "rusted" -> 0xA06A48;
            case "gilded" -> 0xE8D8A8;
            default -> -1;
        };
    }

    private static Map<String, Mapping> map;

    private SurvivorClothes() {
    }

    static Map<String, Mapping> map() {
        if (map != null) return map;
        Map<String, Mapping> out = new HashMap<>();
        try (InputStream in = SurvivorClothes.class.getResourceAsStream("/assets/zomboidcraft/survivor/clothes_map.json")) {
            if (in != null) {
                JsonObject o = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
                for (Map.Entry<String, JsonElement> e : o.entrySet()) {
                    JsonElement v = e.getValue();
                    if (v.isJsonArray()) {
                        out.put(e.getKey(), new Mapping(names(v), false, false, -1));
                    } else if (v.isJsonObject()) {
                        JsonObject m = v.getAsJsonObject();
                        out.put(e.getKey(), new Mapping(names(m.get("pz")), m.has("camo") && m.get("camo").getAsBoolean(),
                                m.has("plate") && m.get("plate").getAsBoolean(),
                                m.has("colour") ? Integer.parseInt(m.get("colour").getAsString().substring(1), 16) : -1));
                    }
                }
            }
        } catch (Exception e) {
            ZomboidCraft.LOGGER.error("Couldn't read the survivor clothes map", e);
        }
        map = out;
        return out;
    }

    private static List<String> names(JsonElement a) {
        List<String> out = new ArrayList<>();
        if (a != null && a.isJsonArray()) for (JsonElement e : a.getAsJsonArray()) out.add(e.getAsString());
        return out;
    }

    /** Whether this wearable is drawn on survivors as Zomboid clothes (so the boxes for it aren't needed). */
    public static boolean covered(String garment) {
        return map().containsKey(garment);
    }

    /** What this player wears, ready to draw (ready is false while a texture is being made). */
    public static Dress dress(SurvivorPack pack, Player player) {
        if (pack.clothes.isEmpty()) return Dress.NONE;
        Map<String, Piece> pieces = new LinkedHashMap<>();
        List<SurvivorTextures.Layer> paint = new ArrayList<>(), cuts = new ArrayList<>();
        String hat = null;
        boolean ready = true;
        long who = player.getUUID().getLeastSignificantBits() ^ player.getUUID().getMostSignificantBits();
        for (ClothingSlot slot : ORDER) {
            ItemStack stack = slot.shown(player);
            Garment g = Clothes.of(stack);
            Mapping m = g == null ? null : map().get(g.id());
            if (m == null) continue;
            String scheme = stack.get(ClothingLoot.CAMO);
            if (scheme == null) {
                List<String> camos = Camos.FOR.get(g.id());
                scheme = camos == null || camos.isEmpty() ? null : camos.get(0);
            }
            String camo = m.camo() && scheme != null && Camos.FOR.containsKey(g.id()) ? scheme : "";
            for (String name : m.pz()) {
                SurvivorPack.Cloth c = pack.clothes.get(name);
                if (c == null) continue;
                int roll = (int) (who * 31 + (g.id() + "/" + name).hashCode() * 0x9E3779B1L >>> 7);
                int tint = m.plate() ? finish(scheme) : m.colour() >= 0 ? m.colour()
                        : c.tint() || c.hue() ? TINTS[Math.floorMod(roll >> 5, TINTS.length)] : -1;
                if (c.piece() != null && !c.textures().isEmpty()) {
                    String key = c.textures().get(Math.floorMod(roll, c.textures().size()));
                    Identifier tex = SurvivorTextures.cloth(pack, key, tint, camo);
                    if (tex == null) ready = false;
                    else pieces.put(c.piece(), new Piece(c.piece(), tex));
                }
                if (!c.base().isEmpty()) {
                    String key = c.base().get(Math.floorMod(roll, c.base().size()));
                    paint.add(new SurvivorTextures.Layer(key, tint));
                    if (!camo.isEmpty()) paint.add(new SurvivorTextures.Layer(camo, -1, SurvivorTextures.CAMO));
                }
                if (c.mask() != null) cuts.add(new SurvivorTextures.Layer(c.mask(), -1, SurvivorTextures.CUT));
                if (c.hat() != null && !c.hat().isEmpty()) hat = c.hat();
            }
        }
        List<SurvivorTextures.Layer> body = new ArrayList<>(paint);
        body.addAll(cuts);
        return new Dress(List.copyOf(pieces.values()), body, hat, ready);
    }
}
