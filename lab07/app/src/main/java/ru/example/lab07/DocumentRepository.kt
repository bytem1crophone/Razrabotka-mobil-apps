package ru.example.lab07

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DocumentRepository(context: Context) {
    private val resolver = context.applicationContext.contentResolver
    private val preferences = context.applicationContext.getSharedPreferences(
        "document_uris",
        Context.MODE_PRIVATE
    )

    // В настройках хранятся только URI, содержимое документов читается заново.
    val lastOpenedUri: Uri?
        get() = preferences.getString(KEY_OPENED_URI, null)?.let(Uri::parse)

    val lastExportedUri: Uri?
        get() = preferences.getString(KEY_EXPORTED_URI, null)?.let(Uri::parse)

    // Новая Activity узнает о завершении записи, начатой до поворота.
    fun observeExportedUri(onChanged: (Uri?) -> Unit): () -> Unit {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == KEY_EXPORTED_URI) onChanged(lastExportedUri)
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        onChanged(lastExportedUri)
        return { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    suspend fun readPreview(uri: Uri): String = withContext(Dispatchers.IO) {
        val stream = resolver.openInputStream(uri)
            ?: throw IOException("Не удалось открыть поток чтения документа")

        stream.bufferedReader(Charsets.UTF_8).use { reader ->
            val buffer = CharArray(PREVIEW_LENGTH)
            var count = 0
            // Один read может вернуть меньше символов, чем запрошено.
            while (count < buffer.size) {
                val read = reader.read(buffer, count, buffer.size - count)
                if (read < 0) break
                if (read == 0) {
                    val character = reader.read()
                    if (character < 0) break
                    buffer[count++] = character.toChar()
                } else {
                    count += read
                }
            }
            String(buffer, 0, count)
        }
    }

    suspend fun exportNote(uri: Uri, note: String): Unit = withContext(Dispatchers.IO) {
        val stream = resolver.openOutputStream(uri, "wt")
            ?: throw IOException("Не удалось открыть поток записи документа")

        // use завершает запись и закрывает поток до возврата из функции.
        stream.bufferedWriter(Charsets.UTF_8).use { writer ->
            writer.write(note)
        }
    }

    suspend fun verifyReadable(uri: Uri): Unit = withContext(Dispatchers.IO) {
        val stream = resolver.openInputStream(uri)
            ?: throw IOException("Не удалось открыть поток чтения документа")
        stream.use { }
    }

    fun persistReadPermission(uri: Uri): Boolean = try {
        resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        true
    } catch (_: SecurityException) {
        false
    } catch (_: IllegalArgumentException) {
        false
    }

    // Вызывать после получения постоянного разрешения на чтение.
    fun rememberOpenedUri(uri: Uri) {
        val previous = lastOpenedUri
        preferences.edit().putString(KEY_OPENED_URI, uri.toString()).apply()
        previous?.let(::releaseReadPermissionIfUnused)
    }

    fun rememberExportedUri(uri: Uri) {
        val previous = lastExportedUri
        preferences.edit().putString(KEY_EXPORTED_URI, uri.toString()).apply()
        previous?.let(::releaseReadPermissionIfUnused)
    }

    fun forgetOpenedUri() {
        val previous = lastOpenedUri
        preferences.edit().remove(KEY_OPENED_URI).apply()
        previous?.let(::releaseReadPermissionIfUnused)
    }

    fun forgetExportedUri() {
        val previous = lastExportedUri
        preferences.edit().remove(KEY_EXPORTED_URI).apply()
        previous?.let(::releaseReadPermissionIfUnused)
    }

    fun releaseReadPermissionIfUnused(uri: Uri) {
        // Один документ может одновременно быть открытым и экспортированным.
        if (uri == lastOpenedUri || uri == lastExportedUri) return
        try {
            resolver.releasePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: SecurityException) {
            // Доступ мог быть уже отозван провайдером.
        } catch (_: IllegalArgumentException) {
            // Провайдер больше не поддерживает этот URI.
        }
    }

    private companion object {
        const val PREVIEW_LENGTH = 500
        const val KEY_OPENED_URI = "last_opened_uri"
        const val KEY_EXPORTED_URI = "last_exported_uri"
    }
}
