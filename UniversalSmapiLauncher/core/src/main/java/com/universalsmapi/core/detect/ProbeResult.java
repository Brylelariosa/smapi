package com.universalsmapi.core.detect;

/** Outcome of one thing the app tried, recorded verbatim so access rules are observed rather than assumed. */
public final class ProbeResult {
    public enum Outcome { OK, FAILED, INFO, SKIPPED }

    public final String id;
    public final Outcome outcome;
    public final String detail;

    public ProbeResult(String id, Outcome outcome, String detail) {
        this.id = id;
        this.outcome = outcome;
        this.detail = detail;
    }

    public static ProbeResult ok(String id, String detail) {
        return new ProbeResult(id, Outcome.OK, detail);
    }

    public static ProbeResult failed(String id, String detail) {
        return new ProbeResult(id, Outcome.FAILED, detail);
    }

    public static ProbeResult info(String id, String detail) {
        return new ProbeResult(id, Outcome.INFO, detail);
    }

    public static ProbeResult skipped(String id, String detail) {
        return new ProbeResult(id, Outcome.SKIPPED, detail);
    }
}
