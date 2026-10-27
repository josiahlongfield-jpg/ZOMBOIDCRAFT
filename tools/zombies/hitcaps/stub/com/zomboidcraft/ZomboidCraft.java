package com.zomboidcraft;

/** Stand-in for the mod class, so the body mesh loader compiles without Minecraft. */
public final class ZomboidCraft {
    public static final Log LOGGER = new Log();

    public static final class Log {
        public void warn(String message, Object... args) {
            System.err.println(message + " " + java.util.Arrays.toString(args));
        }
    }
}
