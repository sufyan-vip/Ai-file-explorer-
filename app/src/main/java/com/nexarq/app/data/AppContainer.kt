package com.nexarq.app.data

import android.content.Context
import com.nexarq.app.ai.AiRepository
import java.io.File

/**
 * Manual dependency container (lightweight service locator). NEXARQ keeps its
 * dependency graph explicit and easy to reason about without a DI framework.
 */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext
    private val store = JsonStore(File(appContext.filesDir, "data"))

    val settings = SettingsRepository(appContext)
    val secureStore = SecureStore()
    val recents = RecentsRepository(store)
    val bookmarks = BookmarkRepository(store)
    val presets = PresetRepository(store)
    val operations = OperationManager(store)
    val ai = AiRepository(appContext, secureStore, store)
}
