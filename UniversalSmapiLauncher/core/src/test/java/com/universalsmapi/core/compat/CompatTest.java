package com.universalsmapi.core.compat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.universalsmapi.core.compat.CompatResolver.Check;
import com.universalsmapi.core.compat.CompatResolver.CheckState;
import com.universalsmapi.core.compat.CompatResolver.Resolution;
import com.universalsmapi.core.compat.CompatResolver.Status;
import com.universalsmapi.core.version.GameVersion;

import org.junit.Test;

public class CompatTest {
    private static CompatDatabase db() throws Exception {
        return CompatDatabase.loadBundled();
    }

    private static CheckState state(Resolution r, String labelPart) {
        for (Check c : r.checks) {
            if (c.label.contains(labelPart)) {
                return c.state;
            }
        }
        throw new AssertionError("no check containing '" + labelPart + "' in " + r.checks.size() + " checks");
    }

    @Test
    public void bundledDatabaseIsMinedFromTheUploadedSmapiSource() throws Exception {
        CompatDatabase d = db();
        assertEquals(1, d.schemaVersion);
        assertEquals("4.5.2", d.sourceApiVersion);
        assertEquals("1.6.14", d.sourceMinimumGame);
        assertEquals("net6.0", d.sourceTargetFramework);
        assertEquals("x64", d.sourcePlatformTarget);
        assertTrue(d.upstreamReleases.size() >= 140);
        assertEquals(47, d.exactRecommendations.size());
        assertTrue(d.parseWarnings.isEmpty());
        assertTrue(d.upstreamReferencedAssemblies.contains("Stardew Valley"));
        assertTrue(d.upstreamReferencedAssemblies.contains("MonoGame.Framework"));
        assertTrue(d.upstreamGameAssemblyAliases.contains("StardewValley"));
        assertTrue(d.androidReleases.isEmpty());
        assertEquals(1, d.androidRequirements.size());
        assertEquals(CompatDatabase.Verification.DOCUMENTED_UNVERIFIED, d.androidRequirements.get(0).verification);
    }

    @Test
    public void noBundledRowIsMarkedVerifiedOnDevice() throws Exception {
        CompatDatabase d = db();
        for (CompatDatabase.AndroidRelease r : d.androidReleases) {
            assertFalse(r.verification == CompatDatabase.Verification.VERIFIED_ON_DEVICE);
        }
        for (CompatDatabase.AndroidRequirement r : d.androidRequirements) {
            assertFalse(r.verification == CompatDatabase.Verification.VERIFIED_ON_DEVICE);
        }
    }

    @Test
    public void exactTableMirrorsSmapisOwnMapping() throws Exception {
        CompatResolver resolver = new CompatResolver(db());
        assertEquals("4.0.8", resolver.resolve(GameVersion.parse("1.6.8"), true, null).upstreamExact.smapi.toString());
        assertEquals("3.18.6", resolver.resolve(GameVersion.parse("1.5.6"), true, null).upstreamExact.smapi.toString());
        assertEquals("3.7.6", resolver.resolve(GameVersion.parse("1.4.5.151"), true, null).upstreamExact.smapi.toString());
    }

    @Test
    public void androidGameGetsNewestDesktopDeclarationButNoAndroidBuild() throws Exception {
        Resolution r = new CompatResolver(db()).resolve(GameVersion.parse("1.6.15.1"), true, "com.android.vending");
        assertEquals(Status.NO_ANDROID_BUILD_CATALOGUED, r.status);
        assertNull(r.androidRelease);
        assertNull(r.upstreamExact);
        assertNotNull(r.upstreamLatestDeclared);
        assertEquals("4.5.2", r.upstreamLatestDeclared.version.toString());
        assertEquals(CheckState.MET, state(r, "at least 1.6.15.1"));
        assertEquals(CheckState.MET, state(r, "64-bit"));
        assertEquals(CheckState.MET, state(r, "installed from"));
        assertTrue(r.headline().contains("nothing is claimed"));
        assertFalse(r.caveats.isEmpty());
    }

    @Test
    public void documentedMinimumUsesThePlatformReleaseNumber() throws Exception {
        CompatResolver resolver = new CompatResolver(db());
        assertEquals(CheckState.NOT_MET, state(resolver.resolve(GameVersion.parse("1.6.15.0"), true, "com.android.vending"), "at least"));
        assertEquals(CheckState.MET, state(resolver.resolve(GameVersion.parse("1.6.16.2"), true, "com.android.vending"), "at least"));
        assertEquals(CheckState.UNKNOWN, state(resolver.resolve(GameVersion.parse("1.6.15"), true, "com.android.vending"), "at least"));
    }

    @Test
    public void deviceAndStoreRequirementsAreChecked() throws Exception {
        CompatResolver resolver = new CompatResolver(db());
        assertEquals(CheckState.NOT_MET, state(resolver.resolve(GameVersion.parse("1.6.15.1"), false, "com.android.vending"), "64-bit"));
        assertEquals(CheckState.NOT_MET, state(resolver.resolve(GameVersion.parse("1.6.15.1"), true, "com.amazon.venezia"), "installed from"));
        assertEquals(CheckState.MET, state(resolver.resolve(GameVersion.parse("1.6.15.1"), true, "com.sec.android.app.samsungapps"), "installed from"));
        assertEquals(CheckState.UNKNOWN, state(resolver.resolve(GameVersion.parse("1.6.15.1"), true, null), "installed from"));
    }

    @Test
    public void unknownGameVersionMatchesNothing() throws Exception {
        Resolution r = new CompatResolver(db()).resolve(null, true, null);
        assertEquals(Status.GAME_VERSION_UNKNOWN, r.status);
        assertTrue(r.checks.isEmpty());
    }

    @Test
    public void androidRowsAreHonouredAndKeepTheirVerificationLevel() throws Exception {
        String json = "{\"schemaVersion\":1,\"androidPorts\":{\"releases\":["
                + "{\"name\":\"doc\",\"smapiVersion\":\"4.1.10.3\",\"gameMin\":\"1.6.15.0\",\"gameMax\":\"1.6.15.9\",\"verification\":\"documented-unverified\"},"
                + "{\"name\":\"tested\",\"smapiVersion\":\"4.1.10.4\",\"gameMin\":\"1.6.15.1\",\"gameMax\":null,\"verification\":\"verified-on-device\"}]}}";
        CompatResolver resolver = new CompatResolver(CompatDatabase.parse(json));
        Resolution documented = resolver.resolve(GameVersion.parse("1.6.15.0"), true, null);
        assertEquals(Status.ANDROID_RELEASE_DOCUMENTED_UNVERIFIED, documented.status);
        assertEquals("4.1.10.3", documented.androidRelease.smapiVersion.toString());
        Resolution verified = resolver.resolve(GameVersion.parse("1.6.15.2"), true, null);
        assertEquals(Status.ANDROID_RELEASE_VERIFIED, verified.status);
        assertEquals("4.1.10.4", verified.androidRelease.smapiVersion.toString());
        assertEquals(Status.NO_ANDROID_BUILD_CATALOGUED, resolver.resolve(GameVersion.parse("1.5.6.52"), true, null).status);
    }

    @Test
    public void malformedRowsAreReportedNotSilentlyDropped() throws Exception {
        CompatDatabase d = CompatDatabase.parse("{\"upstreamReleases\":[{\"version\":\"nope\",\"gameMin\":\"1.6.14\"}],"
                + "\"upstreamExactRecommendations\":[{\"game\":\"1.6.8\"}]}");
        assertEquals(0, d.upstreamReleases.size());
        assertEquals(0, d.exactRecommendations.size());
        assertEquals(2, d.parseWarnings.size());
    }

    @Test
    public void storeLabelsAreFriendly() {
        assertEquals("Play Store", CompatResolver.storeLabel("com.android.vending"));
        assertEquals("Galaxy Store", CompatResolver.storeLabel("com.sec.android.app.samsungapps"));
        assertEquals("Amazon Appstore", CompatResolver.storeLabel("com.amazon.venezia"));
        assertEquals("org.fdroid.fdroid", CompatResolver.storeLabel("org.fdroid.fdroid"));
        assertNull(CompatResolver.storeLabel(null));
    }
}
