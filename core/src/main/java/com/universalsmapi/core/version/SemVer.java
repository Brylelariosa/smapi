/*
 * Derived from SMAPI (https://github.com/Pathoschild/SMAPI), (c) Pathoschild and contributors,
 * licensed under the GNU Lesser General Public License v3.0. This file is a Java port of
 * src/SMAPI.Toolkit/SemanticVersion.cs, src/SMAPI.Toolkit/Framework/SemanticVersionReader.cs and
 * src/SMAPI/Framework/GameVersion.cs from SMAPI 4.5.2, and remains under the same license.
 */
package com.universalsmapi.core.version;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Port of SMAPI's {@code StardewModdingAPI.Toolkit.SemanticVersion} (src/SMAPI.Toolkit/SemanticVersion.cs and
 * Framework/SemanticVersionReader.cs): semantic versioning 2.0 plus SMAPI's non-standard fourth number,
 * the "platform release" that Stardew Valley's ports (including Android) append, e.g. 1.6.15.1.
 *
 * <p>Parsing and ordering rules intentionally match the C# code, including: no leading zeros, "unofficial"
 * prerelease parts always rank lowest, numeric prerelease parts compare as numbers.
 */
public final class SemVer implements Comparable<SemVer> {
    /** SMAPI's TagPattern (SemanticVersion.cs): validates prerelease tags and build metadata. */
    private static final Pattern TAG_PATTERN = Pattern.compile("^(?>[a-z0-9]+[\\-\\.]?)+$", Pattern.CASE_INSENSITIVE);

    public final int major;
    public final int minor;
    public final int patch;
    public final int platformRelease;
    /** Prerelease tag without the leading '-', or null. */
    public final String prerelease;
    /** Build metadata without the leading '+', or null. Ignored for ordering. */
    public final String build;

    public SemVer(int major, int minor, int patch, int platformRelease, String prerelease, String build) {
        this.major = major;
        this.minor = minor;
        this.patch = patch;
        this.platformRelease = platformRelease;
        this.prerelease = prerelease;
        this.build = build;
    }

    /** Parse, returning null when the text is not a valid version. */
    public static SemVer parse(String text, boolean allowNonStandard) {
        if (text == null) {
            return null;
        }
        String s = text.trim();
        if (s.isEmpty()) {
            return null;
        }
        int[] pos = {0};
        int[] part = new int[1];
        if (!readPart(s, pos, part)) {
            return null;
        }
        int major = part[0];
        if (!readLiteral(s, pos, '.') || !readPart(s, pos, part)) {
            return null;
        }
        int minor = part[0];
        int patch = 0;
        if (readLiteral(s, pos, '.')) {
            if (!readPart(s, pos, part)) {
                return null;
            }
            patch = part[0];
        }
        int platform = 0;
        if (allowNonStandard && readLiteral(s, pos, '.')) {
            if (!readPart(s, pos, part)) {
                return null;
            }
            platform = part[0];
        }
        String[] tag = new String[1];
        String pre = null;
        if (readLiteral(s, pos, '-')) {
            if (!readTag(s, pos, tag)) {
                return null;
            }
            pre = tag[0];
        }
        String build = null;
        if (readLiteral(s, pos, '+')) {
            if (!readTag(s, pos, tag)) {
                return null;
            }
            build = tag[0];
        }
        if (pos[0] != s.length()) {
            return null;
        }
        // SMAPI's AssertValid(): at least one of major/minor/patch must be non-zero; tags must match TagPattern.
        if (major == 0 && minor == 0 && patch == 0) {
            return null;
        }
        if (pre != null && !TAG_PATTERN.matcher(pre).matches()) {
            return null;
        }
        if (build != null && !TAG_PATTERN.matcher(build).matches()) {
            return null;
        }
        return new SemVer(major, minor, patch, platform, pre, build);
    }

    /** Same as {@link #parse(String, boolean)} but throws for invalid input. */
    public static SemVer require(String text, boolean allowNonStandard) {
        SemVer v = parse(text, allowNonStandard);
        if (v == null) {
            throw new IllegalArgumentException("Not a valid version: '" + text + "'");
        }
        return v;
    }

    /** Version with the platform release, prerelease and build removed (e.g. 1.6.15.1 becomes 1.6.15). */
    public SemVer core() {
        return new SemVer(major, minor, patch, 0, null, null);
    }

    public boolean isPrerelease() {
        return prerelease != null && !prerelease.trim().isEmpty();
    }

    public boolean isNonStandard() {
        return platformRelease != 0;
    }

    public boolean isBetween(SemVer min, SemVer max) {
        return (min == null || compareTo(min) >= 0) && (max == null || compareTo(max) <= 0);
    }

    public boolean isNewerThan(SemVer other) {
        return compareTo(other) > 0;
    }

    public boolean isOlderThan(SemVer other) {
        return compareTo(other) < 0;
    }

    @Override
    public int compareTo(SemVer other) {
        if (other == null) {
            return 1;
        }
        if (major != other.major) {
            return Integer.compare(major, other.major);
        }
        if (minor != other.minor) {
            return Integer.compare(minor, other.minor);
        }
        if (patch != other.patch) {
            return Integer.compare(patch, other.patch);
        }
        if (platformRelease != other.platformRelease) {
            return Integer.compare(platformRelease, other.platformRelease);
        }
        return comparePrerelease(prerelease, other.prerelease);
    }

    private static int comparePrerelease(String cur, String other) {
        if (equalsNullable(cur, other)) {
            return 0;
        }
        boolean curStable = cur == null || cur.trim().isEmpty();
        boolean otherStable = other == null || other.trim().isEmpty();
        if (curStable) {
            return 1; // stable supersedes prerelease
        }
        if (otherStable) {
            return -1;
        }
        String[] a = cur.split("[.\\-]", -1);
        String[] b = other.split("[.\\-]", -1);
        int length = Math.max(a.length, b.length);
        for (int i = 0; i < length; i++) {
            if (a.length <= i) {
                return -1; // longer tag supersedes if otherwise equal
            }
            if (b.length <= i) {
                return 1;
            }
            if (a[i].equals(b[i])) {
                if (i == length - 1) {
                    return 0;
                }
                continue;
            }
            if (b[i].equalsIgnoreCase("unofficial")) {
                return 1; // unofficial is always lower precedence
            }
            if (a[i].equalsIgnoreCase("unofficial")) {
                return -1;
            }
            Integer na = tryInt(a[i]);
            Integer nb = tryInt(b[i]);
            if (na != null && nb != null) {
                return Integer.compare(na, nb);
            }
            int lex = a[i].compareToIgnoreCase(b[i]);
            return lex < 0 ? -1 : (lex > 0 ? 1 : 0);
        }
        return 0;
    }

    private static Integer tryInt(String s) {
        if (s.isEmpty() || s.length() > 9) {
            return null;
        }
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) < '0' || s.charAt(i) > '9') {
                return null;
            }
        }
        return Integer.valueOf(s);
    }

    private static boolean equalsNullable(String a, String b) {
        return a == null ? b == null : a.equals(b);
    }

    private static boolean readPart(String s, int[] pos, int[] out) {
        int start = pos[0];
        int i = start;
        while (i < s.length() && s.charAt(i) >= '0' && s.charAt(i) <= '9') {
            i++;
        }
        int len = i - start;
        if (len == 0 || len > 9) {
            return false;
        }
        if (len > 1 && s.charAt(start) == '0') {
            return false; // leading zeros are not allowed
        }
        out[0] = Integer.parseInt(s.substring(start, i));
        pos[0] = i;
        return true;
    }

    private static boolean readLiteral(String s, int[] pos, char ch) {
        if (pos[0] >= s.length() || s.charAt(pos[0]) != ch) {
            return false;
        }
        pos[0]++;
        return true;
    }

    private static boolean readTag(String s, int[] pos, String[] out) {
        int start = pos[0];
        int i = start;
        while (i < s.length() && (Character.isLetterOrDigit(s.charAt(i)) || s.charAt(i) == '-' || s.charAt(i) == '.')) {
            i++;
        }
        if (i == start) {
            return false;
        }
        out[0] = s.substring(start, i);
        pos[0] = i;
        return true;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof SemVer && compareTo((SemVer) o) == 0;
    }

    @Override
    public int hashCode() {
        String tag = prerelease == null ? "" : prerelease.toLowerCase(Locale.ROOT);
        return (((major * 31 + minor) * 31 + patch) * 31 + platformRelease) * 31 + tag.hashCode();
    }

    /** Same shape as SMAPI's ToString: always major.minor.patch, then optional parts. */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(major).append('.').append(minor).append('.').append(patch);
        if (platformRelease != 0) {
            sb.append('.').append(platformRelease);
        }
        if (prerelease != null) {
            sb.append('-').append(prerelease);
        }
        if (build != null) {
            sb.append('+').append(build);
        }
        return sb.toString();
    }
}
