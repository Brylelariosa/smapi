package com.universalsmapi.core.axml;

import java.util.ArrayList;
import java.util.List;

/** Facts read from a compiled AndroidManifest.xml. Fields are null/-1 when the manifest did not say. */
public final class ManifestFacts {
    public String packageName;
    public String versionName;
    /** Unsigned 32-bit versionCode, or -1 if absent. */
    public long versionCode = -1;
    public String compileSdkVersion;
    public String minSdk;
    public String targetSdk;
    public String splitName;
    public String configForSplit;
    public boolean isFeatureSplit;
    public Boolean extractNativeLibs;
    public Boolean hasCode;
    public Boolean requestLegacyExternalStorage;
    public Boolean largeHeap;
    public String applicationClass;
    public String appComponentFactory;
    public String launcherActivity;
    public int activities;
    public int services;
    public int receivers;
    public int providers;
    /** Component class names (first 60 of each kind), useful for judging how the runtime is bootstrapped. */
    public final List<String> activityNames = new ArrayList<>();
    public final List<String> serviceNames = new ArrayList<>();
    public final List<String> receiverNames = new ArrayList<>();
    public final List<String> providerNames = new ArrayList<>();
    public final List<String> permissions = new ArrayList<>();
    public final List<String> features = new ArrayList<>();
    public final List<String> nativeLibraries = new ArrayList<>();
    public final List<String> libraries = new ArrayList<>();

    public static ManifestFacts parse(byte[] axml) throws AxmlException {
        XmlNode root = AxmlParser.parse(axml);
        if (!root.name.equals("manifest")) {
            throw new AxmlException("Root element is <" + root.name + ">, expected <manifest>");
        }
        return from(root);
    }

    public static ManifestFacts from(XmlNode manifest) {
        ManifestFacts f = new ManifestFacts();
        f.packageName = manifest.attr("package");
        f.versionName = manifest.attr("versionName");
        f.versionCode = parseUnsigned(manifest.attr("versionCode"));
        f.compileSdkVersion = manifest.attr("compileSdkVersion");
        f.splitName = manifest.attr("split");
        f.configForSplit = manifest.attr("configForSplit");
        f.isFeatureSplit = "true".equals(manifest.attr("isFeatureSplit"));

        XmlNode sdk = manifest.firstChild("uses-sdk");
        if (sdk != null) {
            f.minSdk = sdk.attr("minSdkVersion");
            f.targetSdk = sdk.attr("targetSdkVersion");
        }
        for (XmlNode n : manifest.children) {
            if (n.name.equals("uses-permission") || n.name.equals("uses-permission-sdk-23")) {
                addIfPresent(f.permissions, n.attr("name"));
            } else if (n.name.equals("uses-feature")) {
                String name = n.attr("name");
                if (name != null) {
                    f.features.add("false".equals(n.attr("required")) ? name + " (optional)" : name);
                }
            }
        }
        XmlNode app = manifest.firstChild("application");
        if (app != null) {
            f.applicationClass = app.attr("name");
            f.appComponentFactory = app.attr("appComponentFactory");
            f.extractNativeLibs = bool(app.attr("extractNativeLibs"));
            f.hasCode = bool(app.attr("hasCode"));
            f.requestLegacyExternalStorage = bool(app.attr("requestLegacyExternalStorage"));
            f.largeHeap = bool(app.attr("largeHeap"));
            for (XmlNode c : app.children) {
                switch (c.name) {
                    case "activity":
                    case "activity-alias":
                        f.activities++;
                        addCapped(f.activityNames, c.attr("name"));
                        if (f.launcherActivity == null && isLauncher(c)) {
                            f.launcherActivity = c.attr("name");
                        }
                        break;
                    case "service":
                        f.services++;
                        addCapped(f.serviceNames, c.attr("name"));
                        break;
                    case "receiver":
                        f.receivers++;
                        addCapped(f.receiverNames, c.attr("name"));
                        break;
                    case "provider":
                        f.providers++;
                        addCapped(f.providerNames, c.attr("name"));
                        break;
                    case "uses-native-library":
                        addIfPresent(f.nativeLibraries, c.attr("name"));
                        break;
                    case "uses-library":
                        addIfPresent(f.libraries, c.attr("name"));
                        break;
                    default:
                        break;
                }
            }
        }
        return f;
    }

    private static boolean isLauncher(XmlNode activity) {
        for (XmlNode filter : activity.childrenNamed("intent-filter")) {
            boolean main = false;
            boolean launcher = false;
            for (XmlNode c : filter.children) {
                if (c.name.equals("action") && "android.intent.action.MAIN".equals(c.attr("name"))) {
                    main = true;
                }
                if (c.name.equals("category") && "android.intent.category.LAUNCHER".equals(c.attr("name"))) {
                    launcher = true;
                }
            }
            if (main && launcher) {
                return true;
            }
        }
        return false;
    }

    private static void addCapped(List<String> list, String v) {
        if (v != null && !v.isEmpty() && list.size() < 60) {
            list.add(v);
        }
    }

    private static void addIfPresent(List<String> list, String v) {
        if (v != null && !v.isEmpty()) {
            list.add(v);
        }
    }

    private static Boolean bool(String v) {
        return v == null ? null : Boolean.valueOf("true".equals(v));
    }

    private static long parseUnsigned(String v) {
        if (v == null) {
            return -1;
        }
        try {
            long n = v.startsWith("0x") ? Long.parseLong(v.substring(2), 16) : Long.parseLong(v);
            return n < 0 ? n & 0xFFFFFFFFL : n;
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
