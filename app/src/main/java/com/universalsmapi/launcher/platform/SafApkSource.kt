package com.universalsmapi.launcher.platform

import android.content.ContentResolver
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.universalsmapi.core.apk.ApkSource
import com.universalsmapi.core.io.ChannelSource
import com.universalsmapi.core.io.SeekableSource
import java.io.IOException

/**
 * An APK the user picked with the system file picker (Storage Access Framework). This is the fallback that needs no
 * assumptions about reading another app's files: the user grants access to exactly this document.
 */
class SafApkSource(private val resolver: ContentResolver, private val uri: Uri) : ApkSource {
    override fun describe(): String = "saf:$uri"

    override fun open(): SeekableSource {
        val pfd = resolver.openFileDescriptor(uri, "r")
            ?: throw IOException("ContentResolver.openFileDescriptor returned null for $uri")
        // AutoCloseInputStream owns the descriptor, so closing the source releases it exactly once.
        val stream = ParcelFileDescriptor.AutoCloseInputStream(pfd)
        return ChannelSource(stream.channel, stream)
    }
}
