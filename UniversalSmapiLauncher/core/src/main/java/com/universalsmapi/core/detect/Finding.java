package com.universalsmapi.core.detect;

/** A conclusion the user should see, with a stable code for tests and support. */
public final class Finding {
    public enum Severity { INFO, WARN, ERROR }

    public final Severity severity;
    public final String code;
    public final String message;

    public Finding(Severity severity, String code, String message) {
        this.severity = severity;
        this.code = code;
        this.message = message;
    }
}
