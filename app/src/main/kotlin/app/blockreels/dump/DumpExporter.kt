package app.blockreels.dump

import android.content.ContentValues
import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import java.io.File

/**
 * Copies dumps into the phone's Downloads folder.
 *
 * Dumps live in app-private storage, which Android hides from the Files app, so the only
 * way to reach them is a share sheet. That's a poor fit for the actual job — uploading
 * them to GitHub — because the upload form takes a file picker. Landing the files in
 * `Downloads/BlockReels` turns that into a direct selection with no round trip through
 * email or Drive.
 *
 * Uses MediaStore, so no storage permission is involved.
 */
object DumpExporter {

    const val FOLDER = "BlockReels"

    fun saveToDownloads(context: Context, files: List<File>): Int {
        val resolver = context.contentResolver
        val relativePath = "${Environment.DIRECTORY_DOWNLOADS}/$FOLDER"
        var saved = 0

        files.forEach { file ->
            // MediaStore would otherwise deduplicate by appending " (1)" to the name, which
            // breaks the verdict suffix the fixture name depends on. Replace instead.
            deleteExisting(context, file.name, relativePath)

            val pending = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, file.name)
                put(MediaStore.Downloads.MIME_TYPE, "text/plain")
                put(MediaStore.Downloads.RELATIVE_PATH, relativePath)
                put(MediaStore.Downloads.IS_PENDING, 1)
            }

            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, pending)
                ?: return@forEach

            runCatching {
                resolver.openOutputStream(uri)?.use { out ->
                    file.inputStream().use { it.copyTo(out) }
                }
                resolver.update(
                    uri,
                    ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) },
                    null,
                    null,
                )
                saved++
            }.onFailure {
                resolver.delete(uri, null, null)
            }
        }

        return saved
    }

    private fun deleteExisting(context: Context, displayName: String, relativePath: String) {
        runCatching {
            context.contentResolver.delete(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                "${MediaStore.Downloads.DISPLAY_NAME} = ? AND " +
                    "${MediaStore.Downloads.RELATIVE_PATH} LIKE ?",
                arrayOf(displayName, "$relativePath%"),
            )
        }
    }
}
