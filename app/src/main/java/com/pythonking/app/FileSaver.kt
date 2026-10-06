package com.pythonking.app

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

/** Writes files into the shared Downloads/PythonKing folder. */
object FileSaver {

    const val FOLDER = "PythonKing"

    fun save(
        context: Context,
        displayName: String,
        mimeType: String,
        write: (OutputStream) -> Unit
    ): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                    put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                    put(
                        MediaStore.MediaColumns.RELATIVE_PATH,
                        Environment.DIRECTORY_DOWNLOADS + "/" + FOLDER
                    )
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
                val uri = context.contentResolver
                    .insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return false
                context.contentResolver.openOutputStream(uri)?.use { write(it) } ?: return false
                val done = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
                context.contentResolver.update(uri, done, null, null)
                true
            } else {
                val dir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    FOLDER
                )
                if (!dir.exists()) dir.mkdirs()
                FileOutputStream(File(dir, displayName)).use { write(it) }
                true
            }
        } catch (_: Exception) {
            false
        }
    }
}
