package com.universalsmapi.core.detect;

/** Whether an assembly that upstream SMAPI compiles against was seen in the game's APKs. */
public final class AssemblyCheck {
    public enum State {
        PRESENT,
        ABSENT,
        /** The layout does not expose assembly names, so absence cannot be concluded. */
        UNKNOWN
    }

    public final String name;
    public final State state;
    /** The name actually seen when it differs from {@link #name} (the game assembly is 'StardewValley' on Android). */
    public final String foundAs;

    public AssemblyCheck(String name, State state) {
        this(name, state, null);
    }

    public AssemblyCheck(String name, State state, String foundAs) {
        this.name = name;
        this.state = state;
        this.foundAs = foundAs;
    }
}
