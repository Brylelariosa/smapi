package com.universalsmapi.core.detect;

import com.universalsmapi.core.dotnet.AssemblyVerifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Evidence about what a process other than the game could do with the installed game: obtain its assemblies, and load
 * its native libraries. Collected only when the user asks for it (the native-load probes execute library initialisers).
 */
public final class HostProbeResult {
    /** Null when the extraction check could not run (see {@link #extractionError}). */
    public final AssemblyVerifier.Result extraction;
    public final String extractionError;
    public final List<ProbeResult> nativeProbes;

    public HostProbeResult(AssemblyVerifier.Result extraction, String extractionError, List<ProbeResult> nativeProbes) {
        this.extraction = extraction;
        this.extractionError = extractionError;
        this.nativeProbes = new ArrayList<>(nativeProbes);
    }

    /** One-paragraph summary for the UI. */
    public String headline() {
        StringBuilder sb = new StringBuilder();
        if (extraction != null) {
            sb.append("Assemblies: ").append(extraction.ok).append(" of ").append(extraction.items.size())
                    .append(" decompressed and verified in ").append(extraction.millis).append(" ms.");
        } else {
            sb.append("Assemblies: check did not run (").append(extractionError).append(").");
        }
        int loaded = 0;
        int attempted = 0;
        for (ProbeResult p : nativeProbes) {
            if (p.id.startsWith("native-load:")) {
                attempted++;
                if (p.outcome == ProbeResult.Outcome.OK) {
                    loaded++;
                }
            }
        }
        sb.append(" Native libraries loaded from the game's folder: ").append(loaded).append(" of ").append(attempted).append('.');
        return sb.toString();
    }

    public String toText() {
        StringBuilder sb = new StringBuilder("== Host probes (opt-in; read-only on the game) ==\n");
        if (extraction != null) {
            sb.append("Assembly extraction check: ").append(extraction.ok).append(" of ").append(extraction.items.size())
                    .append(" assemblies decompressed in memory and verified as managed PE images in ").append(extraction.millis)
                    .append(" ms (stored ").append(mb(extraction.stored)).append(", expanded ").append(mb(extraction.expanded)).append(")\n");
            for (AssemblyVerifier.Item it : extraction.items) {
                sb.append("  ").append(it.ok ? "ok    " : "FAIL  ").append(pad(it.name, 40)).append(' ')
                        .append(pad(kb(it.expandedSize), 10)).append(' ').append(pad(it.machine, 12)).append(' ')
                        .append(it.managed ? "managed " : "native  ").append(it.sha256);
                if (!it.ok) {
                    sb.append("  ").append(it.reason);
                }
                sb.append('\n');
            }
        } else {
            sb.append("Assembly extraction check did not run: ").append(extractionError).append('\n');
        }
        sb.append("Native library probes:\n");
        for (ProbeResult p : nativeProbes) {
            sb.append("  [").append(p.outcome).append("] ").append(p.id).append(" - ").append(p.detail).append('\n');
        }
        return sb.toString();
    }

    private static String mb(long n) {
        return String.format(Locale.ROOT, "%.1f MB", n / 1048576.0);
    }

    private static String kb(long n) {
        return n >= 1048576 ? mb(n) : String.format(Locale.ROOT, "%.1f KB", n / 1024.0);
    }

    private static String pad(String s, int width) {
        StringBuilder sb = new StringBuilder(s);
        while (sb.length() < width) {
            sb.append(' ');
        }
        return sb.toString();
    }
}
