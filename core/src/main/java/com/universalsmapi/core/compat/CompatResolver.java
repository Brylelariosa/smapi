package com.universalsmapi.core.compat;

import com.universalsmapi.core.version.GameVersion;
import com.universalsmapi.core.version.SemVer;

import java.util.ArrayList;
import java.util.List;

/**
 * Chooses what the database can honestly say about a detected game version. It never reports "compatible": the best
 * outcome it can produce without a verified-on-device row is "documented, untested".
 */
public final class CompatResolver {
    public enum Status {
        /** The game version could not be parsed, so nothing can be matched. */
        GAME_VERSION_UNKNOWN,
        /** No Android SMAPI release is catalogued for this game version. */
        NO_ANDROID_BUILD_CATALOGUED,
        /** An Android release is catalogued but only from documentation. */
        ANDROID_RELEASE_DOCUMENTED_UNVERIFIED,
        /** An Android release is catalogued and was verified on a real installation. */
        ANDROID_RELEASE_VERIFIED
    }

    public enum CheckState { MET, NOT_MET, UNKNOWN }

    /** One documented prerequisite compared against what was detected. */
    public static final class Check {
        public final String label;
        public final CheckState state;
        public final String detail;

        Check(String label, CheckState state, String detail) {
            this.label = label;
            this.state = state;
            this.detail = detail;
        }
    }

    public static final class Resolution {
        public Status status = Status.NO_ANDROID_BUILD_CATALOGUED;
        public CompatDatabase.AndroidRelease androidRelease;
        /** Informational only: what SMAPI's own table says for this exact game version (desktop release). */
        public CompatDatabase.ExactRecommendation upstreamExact;
        /** Informational only: newest desktop release whose notes cover this game version. */
        public CompatDatabase.UpstreamRelease upstreamLatestDeclared;
        public final List<Check> checks = new ArrayList<>();
        public final List<String> caveats = new ArrayList<>();

        public String headline() {
            switch (status) {
                case GAME_VERSION_UNKNOWN:
                    return "Game version not understood; no match attempted";
                case NO_ANDROID_BUILD_CATALOGUED:
                    return "No Android SMAPI build catalogued for this version (nothing is claimed as compatible)";
                case ANDROID_RELEASE_DOCUMENTED_UNVERIFIED:
                    return "Android release documented but untested: SMAPI " + androidRelease.smapiVersion;
                default:
                    return "Android release verified on a real installation: " + androidRelease.smapiVersion;
            }
        }
    }

    private final CompatDatabase db;

    public CompatResolver(CompatDatabase db) {
        this.db = db;
    }

    /**
     * @param game              detected game version, or null
     * @param device64Bit       whether the device can run 64-bit code
     * @param installerPackage  package that installed the game (store), or null when unknown
     */
    public Resolution resolve(GameVersion game, boolean device64Bit, String installerPackage) {
        Resolution r = new Resolution();
        if (game == null) {
            r.status = Status.GAME_VERSION_UNKNOWN;
            r.caveats.add("The installed package did not report a version this app can parse.");
            return r;
        }
        SemVer core = game.core();

        for (CompatDatabase.ExactRecommendation x : db.exactRecommendations) {
            if (x.game.compareTo(game.version) == 0 || x.game.compareTo(core) == 0) {
                r.upstreamExact = x;
                break;
            }
        }
        for (CompatDatabase.UpstreamRelease u : db.upstreamReleases) {
            boolean covers = u.gameMin.compareTo(core) <= 0 && (u.gameMax == null || core.compareTo(u.gameMax) <= 0);
            if (covers && (r.upstreamLatestDeclared == null || u.version.compareTo(r.upstreamLatestDeclared.version) > 0)) {
                r.upstreamLatestDeclared = u;
            }
        }

        for (CompatDatabase.AndroidRelease a : db.androidReleases) {
            boolean covers = a.gameMin.compareTo(game.version) <= 0 && (a.gameMax == null || game.version.compareTo(a.gameMax) <= 0);
            if (covers && (r.androidRelease == null || a.smapiVersion.compareTo(r.androidRelease.smapiVersion) > 0)) {
                r.androidRelease = a;
            }
        }
        if (r.androidRelease == null) {
            r.status = Status.NO_ANDROID_BUILD_CATALOGUED;
        } else if (r.androidRelease.verification == CompatDatabase.Verification.VERIFIED_ON_DEVICE) {
            r.status = Status.ANDROID_RELEASE_VERIFIED;
        } else {
            r.status = Status.ANDROID_RELEASE_DOCUMENTED_UNVERIFIED;
        }

        boolean fourParts = game.raw.split("\\.").length >= 4;
        for (CompatDatabase.AndroidRequirement req : db.androidRequirements) {
            String who = req.launcher == null ? "Android launcher" : req.launcher;
            if (!fourParts) {
                r.checks.add(new Check(who + ": game version at least " + req.minGame, CheckState.UNKNOWN,
                        "installed version '" + game.raw + "' has no platform-release number to compare"));
            } else {
                boolean ok = game.version.compareTo(req.minGame) >= 0;
                r.checks.add(new Check(who + ": game version at least " + req.minGame,
                        ok ? CheckState.MET : CheckState.NOT_MET, "installed " + game.display()));
            }
            if (req.requires64BitOs) {
                r.checks.add(new Check(who + ": 64-bit device", device64Bit ? CheckState.MET : CheckState.NOT_MET,
                        device64Bit ? "device reports a 64-bit ABI" : "device reports no 64-bit ABI"));
            }
            if (!req.stores.isEmpty()) {
                String store = storeLabel(installerPackage);
                if (store == null) {
                    r.checks.add(new Check(who + ": installed from " + req.stores, CheckState.UNKNOWN,
                            "install source not reported by the system"));
                } else {
                    boolean listed = req.stores.contains(store);
                    r.checks.add(new Check(who + ": installed from " + req.stores,
                            listed ? CheckState.MET : CheckState.NOT_MET, "installed by " + store));
                }
            }
        }

        if (r.androidRelease != null) {
            r.caveats.add("Android release row '" + r.androidRelease.name + "' comes from: " + r.androidRelease.evidence);
        }
        r.caveats.add("Android SMAPI ports are separate builds from upstream SMAPI; upstream releases cannot run on Android as-is.");
        if (r.upstreamExact != null) {
            r.caveats.add("SMAPI's own table lists desktop release " + r.upstreamExact.smapi + " for game " + r.upstreamExact.game
                    + " (informational, desktop only).");
        } else if (r.upstreamLatestDeclared != null) {
            r.caveats.add("Desktop SMAPI " + r.upstreamLatestDeclared.version + " declares support for '"
                    + r.upstreamLatestDeclared.supportText + "' (informational, desktop only).");
        }
        r.caveats.add("Documented requirements come from third-party pages and have not been tested by this app.");
        return r;
    }

    /** Friendly store name for an installer package, or null if unknown/absent. */
    public static String storeLabel(String installerPackage) {
        if (installerPackage == null) {
            return null;
        }
        switch (installerPackage) {
            case "com.android.vending":
                return "Play Store";
            case "com.sec.android.app.samsungapps":
                return "Galaxy Store";
            case "com.amazon.venezia":
                return "Amazon Appstore";
            case "com.google.android.packageinstaller":
            case "com.android.packageinstaller":
            case "com.samsung.android.packageinstaller":
                return "Package installer (APK file)";
            default:
                return installerPackage;
        }
    }
}
