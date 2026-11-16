package com.universalsmapi.launcher.platform

import android.content.Context
import com.universalsmapi.core.detect.PackageFacts
import com.universalsmapi.core.detect.ProbeResult
import dalvik.system.DexClassLoader
import java.lang.reflect.Modifier

/**
 * Opt-in probes of the PUBLIC Android APIs a plugin-style host would rely on to run the game's own code and read its
 * assets from another app's process: createPackageContext, DexClassLoader and PackageManager.getResourcesForApplication.
 * Classes are loaded without being initialised; nothing is started and nothing is written. No hidden APIs are used.
 */
class PluginProbes(private val context: Context) {

    class Output(val probes: List<ProbeResult>, val bootstrapApi: List<String>)

    fun run(pkg: PackageFacts): Output {
        val probes = mutableListOf<ProbeResult>()
        val api = mutableListOf<String>()
        val loaders = linkedMapOf<String, ClassLoader>()

        try {
            val ctx = context.createPackageContext(
                pkg.packageName,
                Context.CONTEXT_INCLUDE_CODE or Context.CONTEXT_IGNORE_SECURITY,
            )
            val loader = ctx.classLoader
            loaders["package-context"] = loader
            probes += ProbeResult.ok("plugin-context", "createPackageContext succeeded; classLoader=${loader.javaClass.name}")
        } catch (e: Throwable) {
            probes += ProbeResult.failed("plugin-context", "${e.javaClass.name}: ${e.message}")
        }

        val apk = pkg.sourceDir
        if (apk == null) {
            probes += ProbeResult.skipped("plugin-dexloader", "no base APK path was reported")
        } else {
            try {
                val dex = DexClassLoader(apk, null, pkg.nativeLibraryDir, context.classLoader)
                loaders["dex-class-loader"] = dex
                probes += ProbeResult.ok("plugin-dexloader", "DexClassLoader created over $apk")
            } catch (e: Throwable) {
                probes += ProbeResult.failed("plugin-dexloader", "${e.javaClass.name}: ${e.message}")
            }
        }

        for ((label, loader) in loaders) {
            for (name in CLASSES) {
                try {
                    val c = Class.forName(name, false, loader)
                    probes += ProbeResult.ok("plugin-class:$label:$name", "loaded without initialising; superclass=${c.superclass?.name}")
                } catch (e: Throwable) {
                    probes += ProbeResult.failed("plugin-class:$label:$name", "${e.javaClass.simpleName}: ${e.message}")
                }
            }
        }

        val dumpLoader = loaders.values.firstOrNull { l ->
            try {
                Class.forName(CLASSES[0], false, l)
                true
            } catch (e: Throwable) {
                false
            }
        }
        if (dumpLoader == null) {
            api += "(no class loader could load ${CLASSES[0]}, so there is nothing to describe)"
        } else {
            for (name in CLASSES) {
                try {
                    describe(Class.forName(name, false, dumpLoader), api)
                } catch (e: Throwable) {
                    api += "class $name: ${e.javaClass.simpleName}: ${e.message}"
                }
            }
        }

        assetProbes(pkg, probes)
        return Output(probes, api)
    }

    private fun assetProbes(pkg: PackageFacts, probes: MutableList<ProbeResult>) {
        try {
            val assets = context.packageManager.getResourcesForApplication(pkg.packageName).assets
            val root = assets.list("") ?: emptyArray()
            probes += ProbeResult.ok(
                "plugin-assets-root",
                "getResourcesForApplication worked; ${root.size} top-level entries: ${root.take(10)}",
            )
            val content = assets.list("Content") ?: emptyArray()
            probes += ProbeResult.info("plugin-assets-content", "assets.list(Content) returned ${content.size} entries: ${content.take(10)}")
            var opened = false
            for (name in content.take(40)) {
                try {
                    assets.open("Content/$name").use { stream ->
                        val buf = ByteArray(16)
                        val n = stream.read(buf)
                        probes += ProbeResult.ok("plugin-assets-open", "Content/$name: read $n bytes through the game's own AssetManager")
                    }
                    opened = true
                    break
                } catch (e: Exception) {
                    // a directory or unreadable entry: try the next one
                }
            }
            if (!opened) {
                probes += ProbeResult.failed("plugin-assets-open", "no file directly under Content could be opened (first 40 entries tried)")
            }
        } catch (e: Throwable) {
            probes += ProbeResult.failed("plugin-assets", "${e.javaClass.name}: ${e.message}")
        }
    }

    private fun describe(c: Class<*>, out: MutableList<String>) {
        val chain = mutableListOf<String>()
        var s: Class<*>? = c.superclass
        while (s != null && chain.size < 12) {
            chain += s.name
            s = s.superclass
        }
        out += "class ${c.name}  extends ${chain.joinToString(" > ")}"
        try {
            for (m in c.declaredMethods.sortedBy { it.name }.take(80)) {
                out += "  ${Modifier.toString(m.modifiers)} ${m.returnType.simpleName} ${m.name}(${m.parameterTypes.joinToString(", ") { it.simpleName }})"
            }
        } catch (e: Throwable) {
            out += "  (methods not readable: ${e.javaClass.simpleName}: ${e.message})"
        }
        try {
            for (f in c.declaredFields.take(40)) {
                out += "  field ${Modifier.toString(f.modifiers)} ${f.type.simpleName} ${f.name}"
            }
        } catch (e: Throwable) {
            out += "  (fields not readable: ${e.javaClass.simpleName}: ${e.message})"
        }
    }

    private companion object {
        /** The Java side of the .NET-for-Android bootstrap and the game's launcher activity. */
        val CLASSES = listOf(
            "mono.MonoPackageManager",
            "mono.android.Runtime",
            "mono.MonoRuntimeProvider",
            "com.chucklefish.stardewvalley.MainActivity",
        )
    }
}
