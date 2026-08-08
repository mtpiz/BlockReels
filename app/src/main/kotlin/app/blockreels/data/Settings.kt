package app.blockreels.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.blockreels.detect.DetectorConfig
import app.blockreels.detect.Detectors
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "blockreels")

class Settings(private val context: Context) {

    val enabledPackages: Flow<Set<String>> = context.dataStore.data.map { prefs ->
        prefs[KEY_ENABLED] ?: Detectors.defaultEnabledPackages
    }

    val dumpMode: Flow<Boolean> = context.dataStore.data.map { it[KEY_DUMP_MODE] ?: false }

    /** How many posts into the home feed are allowed before it counts as doomscrolling. */
    val feedPostLimit: Flow<Int> = context.dataStore.data.map {
        it[KEY_FEED_LIMIT] ?: DetectorConfig().feedPostLimit
    }

    suspend fun setFeedPostLimit(limit: Int) {
        context.dataStore.edit { it[KEY_FEED_LIMIT] = limit }
    }

    /** Cheap motivation, and a health check: if this stops rising, a detector has broken. */
    val blockedCount: Flow<Int> = context.dataStore.data.map { it[KEY_BLOCKED_COUNT] ?: 0 }

    suspend fun recordBlock() {
        context.dataStore.edit { it[KEY_BLOCKED_COUNT] = (it[KEY_BLOCKED_COUNT] ?: 0) + 1 }
    }

    suspend fun setPackageEnabled(packageName: String, enabled: Boolean) {
        context.dataStore.edit { prefs ->
            val current = prefs[KEY_ENABLED] ?: Detectors.defaultEnabledPackages
            prefs[KEY_ENABLED] = if (enabled) current + packageName else current - packageName
        }
    }

    suspend fun setDumpMode(enabled: Boolean) {
        context.dataStore.edit { it[KEY_DUMP_MODE] = enabled }
    }

    private companion object {
        val KEY_ENABLED = stringSetPreferencesKey("enabled_packages")
        val KEY_DUMP_MODE = booleanPreferencesKey("dump_mode")
        val KEY_BLOCKED_COUNT = intPreferencesKey("blocked_count")
        val KEY_FEED_LIMIT = intPreferencesKey("feed_post_limit")
    }
}
