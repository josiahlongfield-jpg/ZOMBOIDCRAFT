package com.zomboidcraft.sandbox;

import com.zomboidcraft.ZcConfig;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * One setting on the Sandbox page. Its id is Zomboid's own SandboxVars name where Zomboid has it
 * ("ZombieConfig.PopulationMultiplier"), or "zc." and a name for ours. A setting that drives the game names the
 * ZcConfig key it sets; one whose system isn't built yet has no key ("coming") and only keeps its value in the world.
 */
public final class SandboxOption {
    public enum Kind {
        /** On or off: "true" / "false". */
        TOGGLE,
        /** A number between min and max. */
        NUMBER,
        /** One of the choices. */
        CHOICE,
        /** Free text, such as an item list. */
        TEXT,
        /** A rule Josiah set: shown with its value and reason, never changed. */
        FIXED
    }

    public final String id;
    public final SandboxGroup group;
    public final String label;
    public final String tooltip;
    public final Kind kind;
    /** The ZcConfig "group.field" it sets, or null while its system is still coming. */
    public final String key;
    public final double min;
    public final double max;
    public final List<String> choices;
    /** Zomboid B42's Apocalypse value, or null for a setting Zomboid doesn't have. */
    public final String zomboidDefault;
    /** The thread that builds what this setting drives (for tooltips). */
    public final String owner;
    /** Changes only what you see on your own screen: kept per player, not per world. */
    public final boolean client;
    private final String fallback;

    private SandboxOption(Builder b) {
        id = b.id;
        group = b.group;
        label = b.label;
        tooltip = b.tooltip;
        kind = b.kind;
        key = b.key;
        min = b.min;
        max = b.max;
        choices = List.copyOf(b.choices);
        zomboidDefault = b.zomboid;
        owner = b.owner;
        client = b.client;
        fallback = b.fallback;
    }

    public boolean coming() {
        return key == null;
    }

    /** Kept with the world (and sent to everyone in it), rather than on each player's own PC. */
    public boolean perWorld() {
        return !client && kind != Kind.FIXED;
    }

    /** Ours out of the box: Zomboid's Apocalypse value unless Josiah changed it or our system works differently. */
    public String defaultValue() {
        return key != null ? ZcConfig.defaultValue(key) : fallback;
    }

    /** The value the game runs on right now, read from the config. */
    String configValue() {
        return key != null ? ZcConfig.getValue(key) : null;
    }

    /** The value tidied into its stored form, or an IllegalArgumentException saying what's wrong. */
    public String check(String raw) {
        String v = raw.trim();
        switch (kind) {
            case FIXED -> throw new IllegalArgumentException(label + " is fixed: " + tooltip);
            case TEXT -> {
                return v;
            }
            case TOGGLE -> {
                if (!v.equalsIgnoreCase("true") && !v.equalsIgnoreCase("false")) {
                    throw new IllegalArgumentException(label + " is true or false");
                }
                return v.toLowerCase(Locale.ROOT);
            }
            case NUMBER -> {
                double d;
                try {
                    d = Double.parseDouble(v);
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("Not a number: " + raw);
                }
                if (d < min || d > max) {
                    throw new IllegalArgumentException(label + " goes from " + num(min) + " to " + num(max));
                }
                // whole-number settings stay whole, so the config takes them
                return key != null && !ZcConfig.defaultValue(key).contains(".") ? String.valueOf((long) d) : v;
            }
            default -> {
                String c = v.toLowerCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
                if (!choices.contains(c)) {
                    throw new IllegalArgumentException(label + ": " + String.join(", ", choices));
                }
                return c;
            }
        }
    }

    private static String num(double d) {
        return d == Math.rint(d) ? String.valueOf((long) d) : String.valueOf(d);
    }

    static Builder of(SandboxGroup group, String id, String label) {
        return new Builder(group, id, label);
    }

    static final class Builder {
        final SandboxGroup group;
        final String id;
        final String label;
        String tooltip = "";
        Kind kind;
        String key;
        double min;
        double max;
        List<String> choices = List.of();
        String zomboid;
        String owner = "COMMANDS";
        boolean client;
        String fallback;

        Builder(SandboxGroup group, String id, String label) {
            this.group = group;
            this.id = id;
            this.label = label;
        }

        Builder tip(String text) {
            tooltip = text;
            return this;
        }

        Builder toggle(String key) {
            kind = Kind.TOGGLE;
            this.key = key;
            return this;
        }

        Builder number(String key, double min, double max) {
            kind = Kind.NUMBER;
            this.key = key;
            this.min = min;
            this.max = max;
            return this;
        }

        Builder choice(String key, String... choices) {
            kind = Kind.CHOICE;
            this.key = key;
            this.choices = List.of(choices);
            return this;
        }

        Builder kind(Kind kind) {
            this.kind = kind;
            return this;
        }

        Builder range(double min, double max) {
            this.min = min;
            this.max = max;
            return this;
        }

        Builder choices(List<String> choices) {
            this.choices = List.copyOf(choices);
            return this;
        }

        /** Not built yet: its value is kept in the world for when it is. */
        Builder coming(String value) {
            key = null;
            fallback = value;
            return this;
        }

        /** One of Josiah's rules: shown, never changed. */
        Builder fixed(String value, String reason) {
            kind = Kind.FIXED;
            key = null;
            fallback = value;
            tooltip = reason;
            return this;
        }

        Builder zomboid(String value) {
            zomboid = value;
            return this;
        }

        Builder owner(String thread) {
            owner = thread;
            return this;
        }

        Builder client() {
            client = true;
            return this;
        }

        SandboxOption build() {
            if (kind == null) {
                throw new IllegalStateException("No kind for " + id);
            }
            return new SandboxOption(this);
        }
    }
}
