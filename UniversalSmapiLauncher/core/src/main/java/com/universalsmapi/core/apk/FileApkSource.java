package com.universalsmapi.core.apk;

import com.universalsmapi.core.io.ChannelSource;
import com.universalsmapi.core.io.SeekableSource;

import java.io.File;
import java.io.IOException;

/** APK reached through a plain filesystem path (for installed apps: ApplicationInfo.sourceDir). */
public final class FileApkSource implements ApkSource {
    private final File file;

    public FileApkSource(File file) {
        this.file = file;
    }

    @Override
    public String describe() {
        return "path:" + file.getPath();
    }

    @Override
    public SeekableSource open() throws IOException {
        return ChannelSource.open(file);
    }
}
