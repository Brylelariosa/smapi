package com.universalsmapi.core.apk;

import com.universalsmapi.core.io.SeekableSource;

import java.io.IOException;

/** Something that can hand out a fresh read handle to one APK. Implementations decide *how* access is obtained. */
public interface ApkSource {
    /** Human-readable origin for logs, e.g. "path:/data/app/.../base.apk" or "saf:content://...". */
    String describe();

    /** Open a new handle. May throw: the exact exception text is evidence about OS access rules. */
    SeekableSource open() throws IOException;
}
