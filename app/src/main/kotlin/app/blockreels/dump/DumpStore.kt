package app.blockreels.dump

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Dumps live in app storage and are shared out via FileProvider when you want to diff them. */
class DumpStore(context: Context) {

    private val dir = File(context.filesDir, "dumps").apply { mkdirs() }

    fun write(packageName: String, content: String): File {
        val stamp = SimpleDateFormat("MMdd-HHmmss", Locale.US).format(Date())
        val shortPkg = packageName.substringAfterLast('.')
        return File(dir, "$stamp-$shortPkg.txt").apply { writeText(content) }
    }

    fun list(): List<File> =
        dir.listFiles()?.sortedByDescending { it.lastModified() }.orEmpty()

    /**
     * Renames a capture to a fixture name. If that name is already taken — easy to do when
     * re-capturing a surface — the existing one is replaced, since the newer capture is the
     * one that reflects the current app version.
     */
    fun rename(file: File, fileName: String): File {
        val target = File(dir, fileName)
        if (target.exists() && target != file) target.delete()
        return if (file.renameTo(target)) target else file
    }

    fun clear() {
        dir.listFiles()?.forEach { it.delete() }
    }
}
