package com.universalsmapi.core.detect;

import com.universalsmapi.core.apk.ApkFacts;

/** Result of inspecting one APK file (base, split, or user-picked). Exactly one of {@link #facts} / {@link #error} is set. */
public final class ApkReport {
    public final String role;
    public final String origin;
    public long size = -1;
    public ApkFacts facts;
    public String error;

    public ApkReport(String role, String origin) {
        this.role = role;
        this.origin = origin;
    }

    public boolean ok() {
        return facts != null;
    }
}
