package com.universalsmapi.core.axml;

import java.io.IOException;

/** The bytes are not a binary Android XML document this parser can read. */
public final class AxmlException extends IOException {
    private static final long serialVersionUID = 1L;

    public AxmlException(String message) {
        super(message);
    }

    public AxmlException(String message, Throwable cause) {
        super(message, cause);
    }
}
