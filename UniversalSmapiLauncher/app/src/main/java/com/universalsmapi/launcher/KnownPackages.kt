package com.universalsmapi.launcher

/**
 * Package ids this build looks for. They MUST match the `<queries>` block in AndroidManifest.xml: on Android 11+ a
 * package that is not declared there is invisible to this app, and the lookup will look exactly like "not installed".
 */
object KnownPackages {
    /** Stardew Valley as distributed by Play Store, Galaxy Store and Amazon (documented package id; verify per store). */
    val stardewValley: List<String> = listOf("com.chucklefish.stardewvalley")

    /** Third-party SMAPI launchers we only report on (never touch), to explain conflicts in diagnostics. */
    val existingLaunchers: List<String> = listOf("abc.smapi.gameloader")
}
