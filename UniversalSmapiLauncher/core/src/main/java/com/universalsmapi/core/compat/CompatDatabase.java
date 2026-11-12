package com.universalsmapi.core.compat;

import com.universalsmapi.core.json.LenientJson;
import com.universalsmapi.core.version.SemVer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Read-only view of {@code compat-db.json}. Upstream rows are mined from the SMAPI source by
 * {@code tools/generate_compat_db.py}; Android rows can only come from third-party documentation or real tests and
 * carry an explicit {@link Verification} level. Nothing here ever upgrades a row on its own.
 */
public final class CompatDatabase {
    public static final String RESOURCE = "/com/universalsmapi/core/compat/compat-db.json";

    public enum Verification {
        /** Stated by the SMAPI project itself (desktop builds). */
        DECLARED_BY_SMAPI_SOURCE,
        /** Stated by third-party documentation; nobody here has tested it. */
        DOCUMENTED_UNVERIFIED,
        /** Confirmed by running it on a real installation. Never set by the generator. */
        VERIFIED_ON_DEVICE;

        static Verification parse(String s) {
            if ("verified-on-device".equals(s)) {
                return VERIFIED_ON_DEVICE;
            }
            if ("declared-by-smapi-source".equals(s)) {
                return DECLARED_BY_SMAPI_SOURCE;
            }
            return DOCUMENTED_UNVERIFIED;
        }
    }

    /** A desktop SMAPI release and the game versions its release notes say it supports. */
    public static final class UpstreamRelease {
        public final SemVer version;
        public final String released;
        public final SemVer gameMin;
        /** Null means "or later" (open-ended). */
        public final SemVer gameMax;
        public final String supportText;
        public final String evidence;

        UpstreamRelease(SemVer version, String released, SemVer gameMin, SemVer gameMax, String supportText, String evidence) {
            this.version = version;
            this.released = released;
            this.gameMin = gameMin;
            this.gameMax = gameMax;
            this.supportText = supportText;
            this.evidence = evidence;
        }
    }

    /** SMAPI's own exact game-version to last-compatible-release table (Constants.GetCompatibleApiVersion). */
    public static final class ExactRecommendation {
        public final SemVer game;
        public final SemVer smapi;
        public final String evidence;

        ExactRecommendation(SemVer game, SemVer smapi, String evidence) {
            this.game = game;
            this.smapi = smapi;
            this.evidence = evidence;
        }
    }

    /** What a third-party Android launcher documents as its prerequisites. */
    public static final class AndroidRequirement {
        public final String launcher;
        public final String launcherPackage;
        public final SemVer minGame;
        public final List<String> stores;
        public final boolean requires64BitOs;
        public final String zipNaming;
        public final String evidence;
        public final Verification verification;

        AndroidRequirement(String launcher, String launcherPackage, SemVer minGame, List<String> stores,
                           boolean requires64BitOs, String zipNaming, String evidence, Verification verification) {
            this.launcher = launcher;
            this.launcherPackage = launcherPackage;
            this.minGame = minGame;
            this.stores = stores;
            this.requires64BitOs = requires64BitOs;
            this.zipNaming = zipNaming;
            this.evidence = evidence;
            this.verification = verification;
        }
    }

    /** An Android SMAPI port release for a range of game versions. The bundled database has none yet. */
    public static final class AndroidRelease {
        public final String name;
        public final SemVer smapiVersion;
        public final SemVer gameMin;
        public final SemVer gameMax;
        public final String evidence;
        public final Verification verification;

        AndroidRelease(String name, SemVer smapiVersion, SemVer gameMin, SemVer gameMax, String evidence, Verification verification) {
            this.name = name;
            this.smapiVersion = smapiVersion;
            this.gameMin = gameMin;
            this.gameMax = gameMax;
            this.evidence = evidence;
            this.verification = verification;
        }
    }

    public final int schemaVersion;
    public final String sourceApiVersion;
    public final String sourceMinimumGame;
    public final String sourceTargetFramework;
    public final String sourcePlatformTarget;
    public final List<String> notes;
    public final List<String> upstreamReferencedAssemblies;
    public final List<String> upstreamGameAssemblyAliases;
    public final List<UpstreamRelease> upstreamReleases;
    public final List<ExactRecommendation> exactRecommendations;
    public final List<AndroidRequirement> androidRequirements;
    public final List<AndroidRelease> androidReleases;
    public final String androidSummary;
    /** Rows that could not be understood (kept so the loader never silently drops data). */
    public final List<String> parseWarnings;

    private CompatDatabase(Map<String, Object> root, List<String> warnings) {
        this.parseWarnings = Collections.unmodifiableList(warnings);
        this.schemaVersion = (int) LenientJson.num(root, "schemaVersion", 0);
        Map<String, Object> source = LenientJson.asObject(root.get("source"));
        this.sourceApiVersion = LenientJson.str(source, "apiVersion");
        this.sourceMinimumGame = LenientJson.str(source, "minimumGameVersion");
        this.sourceTargetFramework = LenientJson.str(source, "targetFramework");
        this.sourcePlatformTarget = LenientJson.str(source, "platformTarget");
        this.notes = Collections.unmodifiableList(LenientJson.strings(root.get("notes")));
        this.upstreamReferencedAssemblies = Collections.unmodifiableList(LenientJson.strings(root.get("upstreamReferencedAssemblies")));
        this.upstreamGameAssemblyAliases = Collections.unmodifiableList(LenientJson.strings(root.get("upstreamGameAssemblyAliases")));

        List<UpstreamRelease> releases = new ArrayList<>();
        for (Object o : LenientJson.asArray(root.get("upstreamReleases"))) {
            Map<String, Object> r = LenientJson.asObject(o);
            SemVer v = SemVer.parse(LenientJson.str(r, "version"), true);
            SemVer min = SemVer.parse(LenientJson.str(r, "gameMin"), true);
            String maxText = LenientJson.str(r, "gameMax");
            SemVer max = maxText == null ? null : SemVer.parse(maxText, true);
            if (v == null || min == null || (maxText != null && max == null)) {
                warnings.add("upstream release row skipped: " + r);
                continue;
            }
            releases.add(new UpstreamRelease(v, LenientJson.str(r, "released"), min, max,
                    LenientJson.str(r, "supportText"), LenientJson.str(r, "evidence")));
        }
        this.upstreamReleases = Collections.unmodifiableList(releases);

        List<ExactRecommendation> exact = new ArrayList<>();
        for (Object o : LenientJson.asArray(root.get("upstreamExactRecommendations"))) {
            Map<String, Object> r = LenientJson.asObject(o);
            SemVer g = SemVer.parse(LenientJson.str(r, "game"), true);
            SemVer s = SemVer.parse(LenientJson.str(r, "smapi"), true);
            if (g == null || s == null) {
                warnings.add("exact row skipped: " + r);
                continue;
            }
            exact.add(new ExactRecommendation(g, s, LenientJson.str(r, "evidence")));
        }
        this.exactRecommendations = Collections.unmodifiableList(exact);

        Map<String, Object> android = LenientJson.asObject(root.get("androidPorts"));
        this.androidSummary = LenientJson.str(android, "summary");
        List<AndroidRequirement> reqs = new ArrayList<>();
        for (Object o : LenientJson.asArray(android.get("documentedRequirements"))) {
            Map<String, Object> r = LenientJson.asObject(o);
            SemVer min = SemVer.parse(LenientJson.str(r, "minGame"), true);
            if (min == null) {
                warnings.add("android requirement skipped: " + r);
                continue;
            }
            reqs.add(new AndroidRequirement(LenientJson.str(r, "launcher"), LenientJson.str(r, "launcherPackage"), min,
                    LenientJson.strings(r.get("stores")), LenientJson.bool(r, "requires64BitOs", false),
                    LenientJson.str(r, "zipNaming"), LenientJson.str(r, "evidence"),
                    Verification.parse(LenientJson.str(r, "verification"))));
        }
        this.androidRequirements = Collections.unmodifiableList(reqs);

        List<AndroidRelease> androidReleases = new ArrayList<>();
        for (Object o : LenientJson.asArray(android.get("releases"))) {
            Map<String, Object> r = LenientJson.asObject(o);
            SemVer v = SemVer.parse(LenientJson.str(r, "smapiVersion"), true);
            SemVer min = SemVer.parse(LenientJson.str(r, "gameMin"), true);
            String maxText = LenientJson.str(r, "gameMax");
            SemVer max = maxText == null ? null : SemVer.parse(maxText, true);
            if (v == null || min == null || (maxText != null && max == null)) {
                warnings.add("android release row skipped: " + r);
                continue;
            }
            androidReleases.add(new AndroidRelease(LenientJson.str(r, "name"), v, min, max, LenientJson.str(r, "evidence"),
                    Verification.parse(LenientJson.str(r, "verification"))));
        }
        this.androidReleases = Collections.unmodifiableList(androidReleases);
    }

    public static CompatDatabase parse(String json) throws LenientJson.JsonException {
        Object root = LenientJson.parse(json);
        return new CompatDatabase(LenientJson.asObject(root), new ArrayList<String>());
    }

    /** Load the database bundled in the core jar. */
    public static CompatDatabase loadBundled() throws IOException {
        try (InputStream in = CompatDatabase.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new IOException("Bundled compatibility database missing: " + RESOURCE);
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
            }
            return parse(new String(out.toByteArray(), StandardCharsets.UTF_8));
        } catch (LenientJson.JsonException e) {
            throw new IOException("Bundled compatibility database is malformed: " + e.getMessage(), e);
        }
    }
}
