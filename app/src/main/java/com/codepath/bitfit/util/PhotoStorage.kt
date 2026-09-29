package com.codepath.bitfit.util

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

/**
 * Daily photos live in the app's private files dir. Only the file PATH is saved in Room
 * (images are too large to keep inside the database).
 */
object PhotoStorage {

    private fun dir(context: Context) = File(context.filesDir, "photos").apply { mkdirs() }

    fun newPhotoFile(context: Context): File = File(dir(context), "IMG_${System.currentTimeMillis()}.jpg")

    fun uriFor(context: Context, file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

    /** Copies a picked gallery image into private storage so it still exists later. */
    fun copyFromUri(context: Context, source: Uri): File? = runCatching {
        val file = newPhotoFile(context)
        context.contentResolver.openInputStream(source)?.use { input ->
            file.outputStream().use { output -> input.copyTo(output) }
        } ?: return null
        file
    }.getOrNull()

    fun delete(path: String?) {
        if (path != null) runCatching { File(path).delete() }
    }

    fun deleteAll(context: Context) {
        dir(context).listFiles()?.forEach { it.delete() }
    }
}
