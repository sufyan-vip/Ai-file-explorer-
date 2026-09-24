package com.nexarq.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "nexarq_prefs")

/**
 * App settings backed by Jetpack DataStore. Every setting that appears in the UI is
 * read from here and actually applied by the relevant feature code.
 */
class SettingsRepository(private val context: Context) {

    private object Keys {
        val theme = stringPreferencesKey("theme")
        val dynamicColors = booleanPreferencesKey("dynamic_colors")
        val showHidden = booleanPreferencesKey("show_hidden")
        val showExtensions = booleanPreferencesKey("show_extensions")
        val folderFirst = booleanPreferencesKey("folder_first")
        val confirmDelete = booleanPreferencesKey("confirm_delete")
        val confirmOverwrite = booleanPreferencesKey("confirm_overwrite")
        val defaultFormat = stringPreferencesKey("default_format")
        val compressionLevel = intPreferencesKey("compression_level")
        val extractBehavior = stringPreferencesKey("extract_behavior")
        val rootEnabled = booleanPreferencesKey("root_enabled")
        val shellTimeout = intPreferencesKey("shell_timeout")
        val recentEnabled = booleanPreferencesKey("recent_enabled")
        val aiEnabled = booleanPreferencesKey("ai_enabled")
        val aiFallback = booleanPreferencesKey("ai_fallback")
        val aiMetadataOnly = booleanPreferencesKey("ai_metadata_only")
        val viewMode = stringPreferencesKey("view_mode")
        val sortMode = stringPreferencesKey("sort_mode")
        val useTrash = booleanPreferencesKey("use_trash")
        val trashRetentionDays = intPreferencesKey("trash_retention_days")
    }

    data class AppSettings(
        val theme: String = "system",
        val dynamicColors: Boolean = true,
        val showHidden: Boolean = false,
        val showExtensions: Boolean = true,
        val folderFirst: Boolean = true,
        val confirmDelete: Boolean = true,
        val confirmOverwrite: Boolean = true,
        val defaultFormat: String = "zip",
        val compressionLevel: Int = 6,
        val extractBehavior: String = "ask",
        val rootEnabled: Boolean = false,
        val shellTimeout: Int = 15,
        val recentEnabled: Boolean = true,
        val aiEnabled: Boolean = false,
        val aiFallback: Boolean = false,
        val aiMetadataOnly: Boolean = true,
        val viewMode: String = "list",
        val sortMode: String = "name",
        val useTrash: Boolean = true,
        val trashRetentionDays: Int = 30,
    )

    val settings: Flow<AppSettings> = context.dataStore.data.map { p ->
        AppSettings(
            theme = p[Keys.theme] ?: "system",
            dynamicColors = p[Keys.dynamicColors] ?: true,
            showHidden = p[Keys.showHidden] ?: false,
            showExtensions = p[Keys.showExtensions] ?: true,
            folderFirst = p[Keys.folderFirst] ?: true,
            confirmDelete = p[Keys.confirmDelete] ?: true,
            confirmOverwrite = p[Keys.confirmOverwrite] ?: true,
            defaultFormat = p[Keys.defaultFormat] ?: "zip",
            compressionLevel = p[Keys.compressionLevel] ?: 6,
            extractBehavior = p[Keys.extractBehavior] ?: "ask",
            rootEnabled = p[Keys.rootEnabled] ?: false,
            shellTimeout = p[Keys.shellTimeout] ?: 15,
            recentEnabled = p[Keys.recentEnabled] ?: true,
            aiEnabled = p[Keys.aiEnabled] ?: false,
            aiFallback = p[Keys.aiFallback] ?: false,
            aiMetadataOnly = p[Keys.aiMetadataOnly] ?: true,
            viewMode = p[Keys.viewMode] ?: "list",
            sortMode = p[Keys.sortMode] ?: "name",
            useTrash = p[Keys.useTrash] ?: true,
            trashRetentionDays = p[Keys.trashRetentionDays] ?: 30,
        )
    }

    suspend fun current(): AppSettings = settings.first()

    suspend fun setTheme(value: String) = edit { it[Keys.theme] = value }
    suspend fun setDynamicColors(value: Boolean) = edit { it[Keys.dynamicColors] = value }
    suspend fun setShowHidden(value: Boolean) = edit { it[Keys.showHidden] = value }
    suspend fun setShowExtensions(value: Boolean) = edit { it[Keys.showExtensions] = value }
    suspend fun setFolderFirst(value: Boolean) = edit { it[Keys.folderFirst] = value }
    suspend fun setConfirmDelete(value: Boolean) = edit { it[Keys.confirmDelete] = value }
    suspend fun setConfirmOverwrite(value: Boolean) = edit { it[Keys.confirmOverwrite] = value }
    suspend fun setDefaultFormat(value: String) = edit { it[Keys.defaultFormat] = value }
    suspend fun setCompressionLevel(value: Int) = edit { it[Keys.compressionLevel] = value }
    suspend fun setExtractBehavior(value: String) = edit { it[Keys.extractBehavior] = value }
    suspend fun setRootEnabled(value: Boolean) = edit { it[Keys.rootEnabled] = value }
    suspend fun setShellTimeout(value: Int) = edit { it[Keys.shellTimeout] = value }
    suspend fun setRecentEnabled(value: Boolean) = edit { it[Keys.recentEnabled] = value }
    suspend fun setAiEnabled(value: Boolean) = edit { it[Keys.aiEnabled] = value }
    suspend fun setAiFallback(value: Boolean) = edit { it[Keys.aiFallback] = value }
    suspend fun setAiMetadataOnly(value: Boolean) = edit { it[Keys.aiMetadataOnly] = value }
    suspend fun setViewMode(value: String) = edit { it[Keys.viewMode] = value }
    suspend fun setSortMode(value: String) = edit { it[Keys.sortMode] = value }
    suspend fun setUseTrash(value: Boolean) = edit { it[Keys.useTrash] = value }
    suspend fun setTrashRetentionDays(value: Int) = edit { it[Keys.trashRetentionDays] = value }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.dataStore.edit(block)
    }
}
