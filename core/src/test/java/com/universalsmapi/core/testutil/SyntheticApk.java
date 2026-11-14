package com.universalsmapi.core.testutil;

import com.universalsmapi.core.apk.ApkSource;
import com.universalsmapi.core.io.ByteArraySource;
import com.universalsmapi.core.io.SeekableSource;
import com.universalsmapi.core.testutil.AxmlBuilder.A;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Builds APKs that look like what the documentation says a .NET-for-Android game contains. These are fixtures made
 * from the published formats; they are NOT samples of the real Stardew Valley APK.
 */
public final class SyntheticApk {
    public enum Layout { BLOB_WITH_MANIFEST, BLOB_NO_MANIFEST, LOOSE, ELF_STORE, NONE }

    private SyntheticApk() {
    }

    public static byte[] xabaHeader(long version) {
        return new Bytes().u32(0x41424158L).u32(version).u32(7).u32(7).u32(0).zeros(40).toByteArray();
    }

    public static byte[] manifest(String pkg, String versionName, long versionCode, String split, boolean utf8) {
        AxmlBuilder b = new AxmlBuilder(utf8);
        A[] manifestAttrs = split == null
                ? new A[] {A.integer("versionCode", versionCode), A.str("versionName", versionName), A.str("package", pkg)}
                : new A[] {A.integer("versionCode", versionCode), A.str("versionName", versionName), A.str("package", pkg),
                        A.str("split", split)};
        b.start("manifest", manifestAttrs);
        b.start("uses-sdk", A.integer("minSdkVersion", 23), A.integer("targetSdkVersion", 34)).end("uses-sdk");
        b.start("uses-permission", A.str("name", "android.permission.INTERNET")).end("uses-permission");
        b.start("uses-permission", A.str("name", "android.permission.VIBRATE")).end("uses-permission");
        b.start("uses-feature", A.str("name", "android.hardware.touchscreen"), A.bool("required", false)).end("uses-feature");
        b.start("application", A.str("name", "crc64f0.MainApplication"), A.bool("extractNativeLibs", false), A.bool("largeHeap", true));
        b.start("activity", A.str("name", "crc64f0.MainActivity"));
        b.start("intent-filter");
        b.start("action", A.str("name", "android.intent.action.MAIN")).end("action");
        b.start("category", A.str("name", "android.intent.category.LAUNCHER")).end("category");
        b.end("intent-filter").end("activity");
        b.start("service", A.str("name", "crc64f0.SomeService")).end("service");
        b.end("application").end("manifest");
        return b.build();
    }

    public static byte[] stardewLike(Layout layout, String versionName, boolean withArm32, boolean signingBlock) {
        MiniZip zip = new MiniZip();
        zip.add("AndroidManifest.xml", manifest("com.chucklefish.stardewvalley", versionName, 321, null, false));
        zip.add("classes.dex", "dex\n035\0".getBytes(StandardCharsets.US_ASCII));
        zip.add("resources.arsc", new byte[] {1, 2, 3});
        zip.add("assets/Content/Data/Objects.xnb", new byte[] {9, 9});
        zip.add("assets/Content/Maps/Farm.tbin", new byte[] {9});
        zip.add("lib/arm64-v8a/libmonodroid.so", new byte[] {1});
        zip.add("lib/arm64-v8a/libmonosgen-2.0.so", new byte[] {1});
        zip.add("lib/arm64-v8a/libxamarin-app.so", new byte[] {1});
        if (withArm32) {
            zip.add("lib/armeabi-v7a/libmonodroid.so", new byte[] {1});
        }
        zip.add("META-INF/CERT.SF", "Signature-Version: 1.0\n");
        zip.add("META-INF/CERT.RSA", new byte[] {5});
        switch (layout) {
            case BLOB_WITH_MANIFEST:
                zip.add("assemblies/assemblies.blob", xabaHeader(2));
                zip.add("assemblies/assemblies.arm64_v8a.blob", xabaHeader(2));
                zip.add("assemblies/assemblies.manifest",
                        "Hash 32     Hash 64             Blob ID  Blob idx  Name\n"
                                + "0xa2e0939b  0x4288cfb749e4c631  000      0000      Xamarin.AndroidX.Activity\n"
                                + "0x11111111  0x2222222222222222  001      0000      Stardew Valley\n"
                                + "0x33333333  0x4444444444444444  001      0001      StardewValley.GameData\n"
                                + "0x55555555  0x6666666666666666  001      0002      MonoGame.Framework\n"
                                + "0x77777777  0x8888888888888888  001      0003      xTile\n"
                                + "0x99999999  0xaaaaaaaaaaaaaaaa  001      0004      Lidgren.Network\n"
                                + "0xbbbbbbbb  0xcccccccccccccccc  001      0005      BmFont\n");
                break;
            case BLOB_NO_MANIFEST:
                zip.add("assemblies/assemblies.blob", xabaHeader(2));
                zip.add("assemblies/assemblies.arm64_v8a.blob", xabaHeader(2));
                break;
            case LOOSE:
                zip.add("assemblies/Stardew Valley.dll", new byte[] {'M', 'Z', 0, 0});
                zip.add("assemblies/xTile.dll", new Bytes().ascii("XALZ").u32(0).u32(100).toByteArray());
                zip.add("assemblies/MonoGame.Framework.dll", new byte[] {'M', 'Z', 0, 0});
                break;
            case ELF_STORE:
                zip.add("lib/arm64-v8a/libassembly-store.so", ElfBuilder.elf64(xabaHeader(3)));
                break;
            default:
                break;
        }
        if (signingBlock) {
            zip.signingBlock(SigningBlocks.build(0x7109871aL, 0xf05368c0L, 0x42726577L));
        }
        return zip.build();
    }

    private static String manifestRow(long blobId, long idx, String name) {
        return String.format("0x%08x  0x%016x  %03d      %04d      %s\n", blobId * 31 + idx, blobId * 977 + idx, blobId, idx, name);
    }

    /**
     * Mirrors the structure reported by a real phone for Stardew Valley 1.6.15.0: one universal APK with four ABIs, a
     * shared store (id 0) plus one store per ABI (id 1) in version-1 layout, and an assemblies.manifest naming them.
     */
    public static byte[] deviceLike(String versionName, boolean breakManifestCount) {
        MiniZip zip = new MiniZip();
        zip.add("AndroidManifest.xml", manifest("com.chucklefish.stardewvalley", versionName, 239, null, true));
        zip.add("classes.dex", "dex\n035\0".getBytes(StandardCharsets.US_ASCII));
        zip.add("resources.arsc", new byte[] {1});
        String[] abis = {"arm64-v8a", "armeabi-v7a", "x86", "x86_64"};
        for (String abi : abis) {
            for (String lib : new String[] {"libmonodroid.so", "libmonosgen-2.0.so", "libSystem.Native.so", "libxamarin-app.so"}) {
                zip.add("lib/" + abi + "/" + lib, new byte[] {1, 2});
            }
        }
        zip.add("assets/Content/Data/Objects.xnb", new byte[] {9});
        zip.add("assets/Content/Maps/Farm.tbin", new byte[] {9});
        zip.add("assemblies/assemblies.blob", StoreBuilder.v1(0, 4, true,
                StoreBuilder.xalz(9_000, 300), StoreBuilder.pe(120), StoreBuilder.pe(60)));
        int size = 40;
        for (String abi : abis) {
            size += 10;
            zip.add("assemblies/assemblies." + abi.replace('-', '_') + ".blob", StoreBuilder.v1(1, 0, false, StoreBuilder.xalz(2_000 + size, size)));
        }
        StringBuilder manifest = new StringBuilder("Hash 32     Hash 64             Blob ID  Blob idx  Name\n");
        manifest.append(manifestRow(0, 0, "Stardew Valley"));
        manifest.append(manifestRow(0, 1, "MonoGame.Framework"));
        manifest.append(manifestRow(0, 2, "xTile"));
        manifest.append(manifestRow(1, 0, "System.Private.CoreLib"));
        if (breakManifestCount) {
            manifest.append(manifestRow(0, 3, "Ghost.Assembly"));
        }
        zip.add("assemblies/assemblies.manifest", manifest.toString());
        zip.add("META-INF/CERT.SF", "Signature-Version: 1.0\n");
        zip.add("META-INF/CERT.RSA", new byte[] {5});
        zip.signingBlock(SigningBlocks.build(0x7109871aL, 0xf05368c0L));
        return zip.build();
    }

    /** ApkSource over bytes. */
    public static ApkSource source(final byte[] data, final String origin) {
        return new ApkSource() {
            @Override
            public String describe() {
                return origin;
            }

            @Override
            public SeekableSource open() {
                return new ByteArraySource(data);
            }
        };
    }

    /** ApkSource that always fails to open, like an OS denying access. */
    public static ApkSource denied(final String origin, final String message) {
        return new ApkSource() {
            @Override
            public String describe() {
                return origin;
            }

            @Override
            public SeekableSource open() throws IOException {
                throw new java.io.FileNotFoundException(message);
            }
        };
    }
}
