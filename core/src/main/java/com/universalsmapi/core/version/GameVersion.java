/*
 * Derived from SMAPI (https://github.com/Pathoschild/SMAPI), (c) Pathoschild and contributors,
 * licensed under the GNU Lesser General Public License v3.0. This file is a Java port of
 * src/SMAPI.Toolkit/SemanticVersion.cs, src/SMAPI.Toolkit/Framework/SemanticVersionReader.cs and
 * src/SMAPI/Framework/GameVersion.cs from SMAPI 4.5.2, and remains under the same license.
 */
package com.universalsmapi.core.version;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Port of SMAPI's {@code GameVersion} (src/SMAPI/Framework/GameVersion.cs): Stardew Valley's very old
 * non-semantic versions are mapped to semantic ones, and a fourth number is allowed (platform release,
 * as used by the Android build, e.g. 1.6.15.1).
 */
public final class GameVersion {
    private static final Map<String, String> VERSION_MAP;

    static {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("1.0", "1.0.0");
        m.put("1.01", "1.0.1");
        m.put("1.02", "1.0.2");
        m.put("1.03", "1.0.3");
        m.put("1.04", "1.0.4");
        m.put("1.05", "1.0.5");
        m.put("1.051", "1.0.5.1");
        m.put("1.051b", "1.0.5.2");
        m.put("1.06", "1.0.6");
        m.put("1.07", "1.0.7");
        m.put("1.07a", "1.0.7.1");
        m.put("1.08", "1.0.8");
        m.put("1.1", "1.1.0");
        m.put("1.2", "1.2.0");
        m.put("1.11", "1.1.1");
        VERSION_MAP = Collections.unmodifiableMap(m);
    }

    /** The text as reported by the game or package. */
    public final String raw;
    public final SemVer version;

    private GameVersion(String raw, SemVer version) {
        this.raw = raw;
        this.version = version;
    }

    /** Returns null when the text cannot be understood as a game version. */
    public static GameVersion parse(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        String mapped = VERSION_MAP.get(trimmed.toLowerCase(Locale.ROOT));
        SemVer v = SemVer.parse(mapped != null ? mapped : trimmed, true);
        return v == null ? null : new GameVersion(trimmed, v);
    }

    /** Major.minor.patch only, for matching against SMAPI's declared game ranges. */
    public SemVer core() {
        return version.core();
    }

    public int platformRelease() {
        return version.platformRelease;
    }

    @Override
    public String toString() {
        return version.toString();
    }
}
