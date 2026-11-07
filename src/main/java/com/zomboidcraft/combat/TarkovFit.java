package com.zomboidcraft.combat;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * How a Tarkov gun goes together from what's fitted in its bench slots (Josiah 2026-11-07: any part pulled for it fits
 * at the bench, only where Tarkov lets it go). Starting from the gun's base, each fitted part goes into a free slot of
 * what's already on that takes it (Tarkov's filters), through a mount where it needs one (a scope's rings, a grip's rail
 * section, a can's adapter), keeping to where the gun's own kit had that kind of part and the kit's own mounts wherever
 * they still serve. Kit parts that are no bench part of their own (rail covers, the magazine) stay where their parent
 * still is. The same tree drives the drawing (client TarkovRig), the sights and the grip hold, so what's fitted is what's
 * seen, aimed through and held.
 */
public final class TarkovFit {
    private TarkovFit() {
    }

    /**
     * One part in place: its path (slot names from the gun down, "" for the base), the item, its parent's path and the
     * slot it sits in there, whether it is exactly the kit's own (same item, same place, kit all the way up: drawn by the
     * gun's model as packed), and the bench slot it fills (null for a link or a kit extra).
     */
    public record Node(String path, String item, @Nullable String parent, @Nullable String slot, boolean kit,
                       Attachments.@Nullable Slot socket) {
    }

    /** The gun as built: its parts, parents first; complete when every fitted part found a place. */
    public record Assembly(List<Node> nodes, Map<String, Node> byPath, Map<Attachments.Slot, Node> bySocket, boolean complete) {
        public @Nullable Node at(String path) {
            return byPath.get(path);
        }
    }

    /** The order parts go on: what others hang from first. */
    private static final Attachments.Slot[] ORDER = {
            Attachments.Slot.RECEIVER, Attachments.Slot.BARREL, Attachments.Slot.GAS_BLOCK, Attachments.Slot.HANDGUARD,
            Attachments.Slot.PISTOL_GRIP, Attachments.Slot.STOCK, Attachments.Slot.CHARGING, Attachments.Slot.MUZZLE,
            Attachments.Slot.SUPPRESSOR, Attachments.Slot.REAR_SIGHT, Attachments.Slot.FRONT_SIGHT, Attachments.Slot.OPTIC,
            Attachments.Slot.PIGGYBACK, Attachments.Slot.UNDERBARREL, Attachments.Slot.SIDE_RAIL, Attachments.Slot.RIGHT_RAIL,
            Attachments.Slot.BIPOD};

    private static final Map<String, Assembly> CACHE = new HashMap<>();
    private static final Map<String, List<Attachments.Slot>> SOCKETS = new HashMap<>();

    static synchronized void forget() {
        CACHE.clear();
        SOCKETS.clear();
    }

    /** Whether a gun is a Tarkov gun built from parts (its file has its kit tree). */
    public static boolean tarkov(@Nullable LocalGuns.Spec spec) {
        return spec != null && spec.tarkov != null && spec.tarkov.base != null && spec.tarkov.kit != null;
    }

    public static boolean tarkov(ItemStack gun) {
        return tarkov(LocalGuns.of(gun));
    }

    /** The Tarkov part ids fitted to a gun, by bench slot. */
    public static Map<Attachments.Slot, String> fitted(ItemStack gun) {
        Map<Attachments.Slot, String> out = new EnumMap<>(Attachments.Slot.class);
        Attachments.of(gun).mounted().forEach((slot, stack) -> {
            String id = TarkovParts.of(stack);
            if (id != null) out.put(slot, id);
        });
        return out;
    }

    /** The gun as it is built now, or null for any other gun. */
    public static @Nullable Assembly of(ItemStack gun) {
        LocalGuns.Spec spec = LocalGuns.of(gun);
        return tarkov(spec) ? resolve(spec, fitted(gun)) : null;
    }

    /** Whether a kit part can't come off in Tarkov: the slot it sits in on its parent is a required one. */
    public static boolean builtIn(LocalGuns.Spec spec, String item) {
        if (!tarkov(spec)) return false;
        for (LocalGuns.KitPart k : spec.tarkov.kit) {
            if (!k.item.equals(item)) continue;
            int cut = k.path.lastIndexOf('/');
            if (cut < 0) continue;
            String parentPath = k.path.substring(0, cut), slot = k.path.substring(cut + 1);
            for (LocalGuns.KitPart parent : spec.tarkov.kit) {
                if (!parent.path.equals(parentPath)) continue;
                TarkovParts.Part pp = TarkovParts.get(parent.item);
                TarkovParts.Port port = pp == null ? null : pp.slots.get(slot);
                if (port != null && port.required) return true;
            }
        }
        return false;
    }

    /** What a Tarkov gun comes with, by bench slot: its kit's parts (a second sight rides piggyback, a second device on the other rail). */
    public static Map<Attachments.Slot, String> kitSockets(LocalGuns.Spec spec) {
        Map<Attachments.Slot, String> out = new EnumMap<>(Attachments.Slot.class);
        if (!tarkov(spec)) return out;
        List<LocalGuns.KitPart> kit = spec.tarkov.kit;
        for (LocalGuns.KitPart k : kit) {
            TarkovParts.Part p = TarkovParts.get(k.item);
            if (p == null || p.slot() == null || p.slot() == Attachments.Slot.MAGAZINE) continue;
            Attachments.Slot s = p.slot();
            if (s == Attachments.Slot.OPTIC && out.containsKey(s)) s = Attachments.Slot.PIGGYBACK;
            else if (s == Attachments.Slot.SIDE_RAIL && out.containsKey(s)) s = Attachments.Slot.RIGHT_RAIL;
            if (out.containsKey(s)) {
                // an adapter of the same kind with the real part under it (an AK's stock lock and its stock): the one
                // furthest out is the bench part, the other a link
                String had = out.get(s);
                String hadPath = kit.stream().filter(x -> x.item.equals(had)).map(x -> x.path).findFirst().orElse("");
                if (k.path.startsWith(hadPath + "/")) out.put(s, k.item);
                continue;
            }
            out.put(s, k.item);
        }
        return out;
    }

    /** The bench slots a Tarkov gun shows: every one some part reachable from its base can fill. */
    public static synchronized List<Attachments.Slot> sockets(LocalGuns.Spec spec) {
        if (!tarkov(spec)) return List.of();
        return SOCKETS.computeIfAbsent(spec.id, k -> {
            Set<String> seen = new HashSet<>();
            List<String> todo = new ArrayList<>(List.of(spec.tarkov.base));
            Set<Attachments.Slot> got = new HashSet<>();
            int tactical = 0;
            boolean piggy = false;
            while (!todo.isEmpty()) {
                String id = todo.remove(todo.size() - 1);
                if (!seen.add(id)) continue;
                TarkovParts.Part p = TarkovParts.get(id);
                if (p == null) continue;
                if (p.slot() != null && p.slot() != Attachments.Slot.MAGAZINE) got.add(p.slot());
                for (TarkovParts.Port port : p.slots.values()) {
                    boolean anyTactical = false;
                    for (String c : port.filter) {
                        TarkovParts.Part cp = TarkovParts.get(c);
                        if (cp == null) continue;
                        if (cp.slot() == Attachments.Slot.SIDE_RAIL) anyTactical = true;
                        if (p.sight() && cp.sight()) piggy = true;
                        todo.add(c);
                    }
                    if (anyTactical) tactical++;
                }
            }
            if (piggy || got.contains(Attachments.Slot.OPTIC)) got.add(Attachments.Slot.PIGGYBACK);
            if (tactical > 1) got.add(Attachments.Slot.RIGHT_RAIL);
            got.addAll(kitSockets(spec).keySet());
            List<Attachments.Slot> out = new ArrayList<>();
            for (Attachments.Slot s : Attachments.Slot.values()) {
                if (got.contains(s)) out.add(s);
            }
            return out;
        });
    }

    /** Whether a part of this kind goes in this bench slot (a sight can ride piggyback, a device on either rail). */
    public static boolean goesIn(TarkovParts.Part p, Attachments.Slot socket) {
        Attachments.Slot s = p.slot();
        if (s == null) return false;
        return s == socket || s == Attachments.Slot.OPTIC && socket == Attachments.Slot.PIGGYBACK
                || s == Attachments.Slot.SIDE_RAIL && socket == Attachments.Slot.RIGHT_RAIL;
    }

    /** Whether this part can go into this bench slot of the gun now, everything else fitted still finding its place. */
    public static boolean canFit(ItemStack gun, Attachments.Slot socket, String id) {
        LocalGuns.Spec spec = LocalGuns.of(gun);
        TarkovParts.Part p = TarkovParts.get(id);
        if (!tarkov(spec) || p == null || !goesIn(p, socket)) return false;
        Map<Attachments.Slot, String> next = fitted(gun);
        next.put(socket, id);
        Assembly a = resolve(spec, next);
        return a.complete();
    }

    /**
     * Whether the part in this bench slot can come off: not when the gun needs that slot filled (a barrel, a receiver:
     * swap those instead) or when something else fitted hangs on it.
     */
    public static boolean canRemove(ItemStack gun, Attachments.Slot socket) {
        LocalGuns.Spec spec = LocalGuns.of(gun);
        if (!tarkov(spec)) return true;
        Map<Attachments.Slot, String> now = fitted(gun);
        Assembly a = resolve(spec, now);
        Node n = a.bySocket().get(socket);
        if (n == null) return true;
        Node parent = n.parent() == null ? null : a.at(n.parent());
        TarkovParts.Part pp = parent == null ? null : TarkovParts.get(parent.item());
        TarkovParts.Port port = pp == null || n.slot() == null ? null : pp.slots.get(n.slot());
        if (port != null && port.required) return false;
        now.remove(socket);
        return resolve(spec, now).complete();
    }

    /** The gun built from these fitted parts. */
    public static synchronized Assembly resolve(LocalGuns.Spec spec, Map<Attachments.Slot, String> fitted) {
        String key = spec.id + "|" + fitted;
        Assembly got = CACHE.get(key);
        if (got != null) return got;
        if (CACHE.size() > 256) CACHE.clear();
        got = build(spec, fitted);
        CACHE.put(key, got);
        return got;
    }

    private record Link(String path, String item) {
    }

    private static Assembly build(LocalGuns.Spec spec, Map<Attachments.Slot, String> fitted) {
        Map<String, String> kitAt = new HashMap<>();
        for (LocalGuns.KitPart k : spec.tarkov.kit) kitAt.put(k.path, k.item);
        Map<String, Node> nodes = new LinkedHashMap<>();
        Map<Attachments.Slot, Node> bySocket = new EnumMap<>(Attachments.Slot.class);
        nodes.put("", new Node("", spec.tarkov.base, null, null, true, null));
        boolean complete = true;
        // where the kit had each kind of part, so a swap goes where the old one was
        Map<Attachments.Slot, List<String>> kitPaths = new EnumMap<>(Attachments.Slot.class);
        kitSockets(spec).forEach((s, item) -> {
            for (LocalGuns.KitPart k : spec.tarkov.kit) {
                if (k.item.equals(item)) kitPaths.computeIfAbsent(s, x -> new ArrayList<>()).add(k.path);
            }
        });
        Set<String> kitItems = new HashSet<>(kitAt.values());
        // parts whose kit place hangs under another's go after it (the SV-98's chassis before its handguard), the rest
        // in ORDER; a part with no place yet goes round again once the rest are on
        List<Attachments.Slot> todo = new ArrayList<>();
        for (Attachments.Slot s : ORDER) {
            if (TarkovParts.get(fitted.get(s)) != null) todo.add(s);
        }
        java.util.function.ToIntFunction<Attachments.Slot> kitDepth = s -> {
            List<String> ps = kitPaths.get(s);
            return ps == null || ps.isEmpty() ? 99 : depth(ps.get(0));
        };
        todo.sort(java.util.Comparator.comparingInt(kitDepth).thenComparingInt(s -> List.of(ORDER).indexOf(s)));
        while (!todo.isEmpty()) {
            List<Attachments.Slot> left = new ArrayList<>();
            for (Attachments.Slot socket : todo) {
                String id = fitted.get(socket);
                TarkovParts.Part p = TarkovParts.get(id);
                List<Link> best = null;
                String bestPath = null;
                double bestScore = Double.MAX_VALUE;
                int order = 0;
                Node optic = bySocket.get(Attachments.Slot.OPTIC);
                for (Node n : List.copyOf(nodes.values())) {
                    TarkovParts.Part np = TarkovParts.get(n.item());
                    if (np == null) continue;
                    for (Map.Entry<String, TarkovParts.Port> e : np.slots.entrySet()) {
                        String sp = n.path() + "/" + e.getKey();
                        if (nodes.containsKey(sp)) continue;
                        for (List<Link> chain : routes(sp, e.getValue(), id, p, socket, fitted, 0)) {
                            String target = chain.isEmpty() ? sp : chain.get(chain.size() - 1).path;
                            chain = chain.isEmpty() ? chain : chain.subList(0, chain.size() - 1);
                            if (conflicts(id, nodes, kitItems) || chain.stream().anyMatch(l -> conflicts(l.item, nodes, kitItems))) continue;
                            double score = order++ + 100.0 * chain.size();
                            if (id.equals(kitAt.get(target))) score -= 10000;
                            else if (kitPaths.getOrDefault(socket, List.of()).contains(target)) score -= 5000;
                            for (Link l : chain) {
                                if (l.item.equals(kitAt.get(l.path))) score -= 2000;
                            }
                            if (socket == Attachments.Slot.PIGGYBACK && optic != null && target.startsWith(optic.path() + "/")) score -= 3000;
                            if (socket == Attachments.Slot.OPTIC && target.substring(target.lastIndexOf('/') + 1).startsWith("mod_scope")) score -= 500;
                            if (score < bestScore) {
                                bestScore = score;
                                best = chain;
                                bestPath = target;
                            }
                        }
                    }
                }
                if (bestPath == null) {
                    left.add(socket);
                    continue;
                }
                for (Link l : best) add(nodes, kitAt, l.path, l.item, null);
                bySocket.put(socket, add(nodes, kitAt, bestPath, id, socket));
            }
            if (left.size() == todo.size()) {
                complete = false;
                break;
            }
            todo = left;
        }
        // the kit's own parts that are no bench part (rail covers, eyecups, its magazine) stay where their parent is
        Set<String> benched = new HashSet<>(kitSockets(spec).values());
        for (LocalGuns.KitPart k : spec.tarkov.kit) {
            if (k.path.isEmpty() || nodes.containsKey(k.path) || benched.contains(k.item)) continue;
            TarkovParts.Part p = TarkovParts.get(k.item);
            if (p == null) continue;
            String parent = k.path.substring(0, k.path.lastIndexOf('/'));
            Node pn = nodes.get(parent);
            if (pn == null || !pn.kit() || conflicts(k.item, nodes, kitItems)) continue;
            add(nodes, kitAt, k.path, k.item, null);
        }
        Map<String, Node> byPath = new LinkedHashMap<>();
        List<Node> list = new ArrayList<>();
        // parents before children: by depth, then as placed
        nodes.values().stream().sorted(java.util.Comparator.comparingInt(n -> depth(n.path()))).forEach(n -> {
            list.add(n);
            byPath.put(n.path(), n);
        });
        return new Assembly(List.copyOf(list), byPath, bySocket, complete);
    }

    private static int depth(String path) {
        int d = 0;
        for (int i = 0; i < path.length(); i++) {
            if (path.charAt(i) == '/') d++;
        }
        return d;
    }

    private static Node add(Map<String, Node> nodes, Map<String, String> kitAt, String path, String item,
                            Attachments.@Nullable Slot socket) {
        int cut = path.lastIndexOf('/');
        String parent = path.substring(0, cut), slot = path.substring(cut + 1);
        Node pn = nodes.get(parent);
        boolean kit = pn != null && pn.kit() && item.equals(kitAt.get(path));
        Node n = new Node(path, item, parent, slot, kit, socket);
        nodes.put(path, n);
        return n;
    }

    /**
     * The ways a part reaches into a slot: straight in (an empty chain), or through one or two links (mounts, or an
     * adapter of its own kind: a stock's buffer-tube adapter; a can's muzzle adapter when no muzzle device is fitted).
     * Each chain ends with the part's own place.
     */
    private static List<List<Link>> routes(String sp, TarkovParts.Port port, String id, TarkovParts.Part p,
                                           Attachments.Slot socket, Map<Attachments.Slot, String> fitted, int depth) {
        List<List<Link>> out = new ArrayList<>();
        if (port.filter.contains(id)) {
            out.add(new ArrayList<>(List.of(new Link(sp, id))));
            if (depth == 0) out.get(0).clear();
            return out;
        }
        if (depth >= 2) return out;
        for (String m : port.filter) {
            TarkovParts.Part mp = TarkovParts.get(m);
            if (mp == null || !link(mp, p, socket, fitted)) continue;
            for (Map.Entry<String, TarkovParts.Port> e : mp.slots.entrySet()) {
                for (List<Link> rest : routes(sp + "/" + e.getKey(), e.getValue(), id, p, socket, fitted, depth + 1)) {
                    List<Link> chain = new ArrayList<>();
                    chain.add(new Link(sp, m));
                    if (rest.isEmpty()) chain.add(new Link(sp + "/" + e.getKey(), id));
                    else chain.addAll(rest);
                    out.add(chain);
                }
            }
        }
        return out;
    }

    private static boolean link(TarkovParts.Part m, TarkovParts.Part p, Attachments.Slot socket, Map<Attachments.Slot, String> fitted) {
        if (m.is("Mount")) return true;
        if (m.slot() != null && m.slot() == p.slot() && m.slot() != Attachments.Slot.OPTIC) return true;
        return socket == Attachments.Slot.SUPPRESSOR && !fitted.containsKey(Attachments.Slot.MUZZLE)
                && (m.is("MuzzleCombo") || m.is("FlashHider"));
    }

    /** Whether a part clashes with one on the gun (Tarkov's conflicting items); the kit's own parts never clash with each other. */
    private static boolean conflicts(String id, Map<String, Node> nodes, Set<String> kitItems) {
        TarkovParts.Part p = TarkovParts.get(id);
        boolean kitPart = kitItems.contains(id);
        for (Node n : nodes.values()) {
            if (kitPart && kitItems.contains(n.item())) continue;
            if (p != null && p.conflicts.contains(n.item())) return true;
            TarkovParts.Part np = TarkovParts.get(n.item());
            if (np != null && np.conflicts.contains(id)) return true;
        }
        return false;
    }

    /** The fitted part of a kind anywhere on the gun, by Tarkov kind ("Silencer"), or null. */
    public static @Nullable Node anyOf(@Nullable Assembly a, String kind) {
        if (a == null) return null;
        for (Node n : a.nodes()) {
            TarkovParts.Part p = TarkovParts.get(n.item());
            if (p != null && p.is(kind)) return n;
        }
        return null;
    }

    /** Whether something on the gun shines a laser (light: a white light). */
    public static boolean shines(@Nullable Assembly a, boolean light) {
        if (a == null) return false;
        for (Node n : a.nodes()) {
            TarkovParts.Part p = TarkovParts.get(n.item());
            if (p != null && (light ? p.light : p.laser) != null) return true;
        }
        return false;
    }

    /** The first sight's first mode's magnification, or 0 with none fitted (irons). */
    public static float sightZoom(@Nullable Assembly a) {
        Node n = a == null ? null : a.bySocket().get(Attachments.Slot.OPTIC);
        TarkovParts.Part p = n == null ? null : TarkovParts.get(n.item());
        if (p == null || p.aims == null || p.aims.isEmpty() || p.aims.get(0).modes.isEmpty()) return 0;
        return p.aims.get(0).modes.get(0).zoom;
    }

    /** Tarkov parts this gun takes in a bench slot (for the tooltip and loot). */
    public static boolean reaches(LocalGuns.Spec spec, String id) {
        TarkovParts.Part p = TarkovParts.get(id);
        if (!tarkov(spec) || p == null) return false;
        Set<String> seen = new HashSet<>();
        List<String> todo = new ArrayList<>(List.of(spec.tarkov.base));
        while (!todo.isEmpty()) {
            String c = todo.remove(todo.size() - 1);
            if (!seen.add(c)) continue;
            if (Objects.equals(c, id)) return true;
            TarkovParts.Part cp = TarkovParts.get(c);
            if (cp == null) continue;
            for (TarkovParts.Port port : cp.slots.values()) todo.addAll(port.filter);
        }
        return false;
    }
}
