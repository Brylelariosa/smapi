package com.universalsmapi.core.zip;

import java.io.IOException;

/** The data is not a well-formed ZIP/APK (or uses a feature this reader deliberately does not support). */
public final class ZipFormatException extends IOException {
    private static final long serialVersionUID = 1L;

    public ZipFormatException(String message) {
        super(message);
    }
}
