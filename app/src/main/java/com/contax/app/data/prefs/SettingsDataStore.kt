package com.contax.app.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.contax.app.data.model.BackupFormat
import com.contax.app.data.model.ScheduleMode
import com.contax.app.data.model.SettingsData
import com.contax.app.data.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "contax_settings")

class SettingsDataStore(private val context: Context) {

    private object K {
        val LANGUAGE = stringPreferencesKey("language")
        val THEME = stringPreferencesKey("theme")
        val LOCK_ENABLED = booleanPreferencesKey("lock_enabled")
        val PIN_HASH = stringPreferencesKey("pin_hash")
        val FINGERPRINT = booleanPreferencesKey("fingerprint")
        val UPDATE_CHECK = booleanPreferencesKey("update_check_enabled")
        val SCHEDULE_MODE = stringPreferencesKey("schedule_mode")
        val SCHEDULE_HOUR = intPreferencesKey("schedule_hour")
        val AUTO_FORMAT = stringPreferencesKey("auto_format")
        val LAST_AUTO = longPreferencesKey("last_auto_backup")
        val FIRST_LOCALE_APPLIED = booleanPreferencesKey("first_locale_applied")
    }

    val settings: Flow<SettingsData> = context.dataStore.data.map { p ->
        SettingsData(
            language = p[K.LANGUAGE] ?: "fa",
            theme = p[K.THEME]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.LIGHT,
            lockEnabled = p[K.LOCK_ENABLED] ?: false,
            pinHash = p[K.PIN_HASH],
            fingerprintEnabled = p[K.FINGERPRINT] ?: true,
            updateCheckEnabled = p[K.UPDATE_CHECK] ?: true,
            scheduleMode = p[K.SCHEDULE_MODE]?.let { runCatching { ScheduleMode.valueOf(it) }.getOrNull() } ?: ScheduleMode.OFF,
            scheduleHour = p[K.SCHEDULE_HOUR] ?: 9,
            autoFormat = p[K.AUTO_FORMAT]?.let { runCatching { BackupFormat.valueOf(it) }.getOrNull() } ?: BackupFormat.VCF,
            lastAutoBackupAt = p[K.LAST_AUTO]
        )
    }

    suspend fun current(): SettingsData = settings.first()

    suspend fun isFirstLocaleApplied(): Boolean = context.dataStore.data.first()[K.FIRST_LOCALE_APPLIED] ?: false
    suspend fun markLocaleApplied() = context.dataStore.edit { it[K.FIRST_LOCALE_APPLIED] = true }

    suspend fun setLanguage(v: String) = context.dataStore.edit { it[K.LANGUAGE] = v }
    suspend fun setTheme(v: ThemeMode) = context.dataStore.edit { it[K.THEME] = v.name }
    suspend fun setLockEnabled(v: Boolean) = context.dataStore.edit { it[K.LOCK_ENABLED] = v }
    suspend fun setPinHash(hash: String?) {
        context.dataStore.edit {
            if (hash == null) it.remove(K.PIN_HASH) else it[K.PIN_HASH] = hash
        }
    }
    suspend fun setFingerprint(v: Boolean) = context.dataStore.edit { it[K.FINGERPRINT] = v }

    suspend fun setUpdateCheckEnabled(v: Boolean) = context.dataStore.edit { it[K.UPDATE_CHECK] = v }

    suspend fun setSchedule(mode: ScheduleMode, hour: Int, format: BackupFormat) {
        context.dataStore.edit {
            it[K.SCHEDULE_MODE] = mode.name
            it[K.SCHEDULE_HOUR] = hour
            it[K.AUTO_FORMAT] = format.name
        }
    }
    suspend fun setLastAutoBackupAt(ts: Long) = context.dataStore.edit { it[K.LAST_AUTO] = ts }
}
