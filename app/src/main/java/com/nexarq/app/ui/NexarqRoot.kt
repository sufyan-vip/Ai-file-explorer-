package com.nexarq.app.ui

import android.os.Environment
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.nexarq.app.data.AppContainer
import com.nexarq.app.ui.screens.AboutScreen
import com.nexarq.app.ui.screens.AiCenterScreen
import com.nexarq.app.ui.screens.AnalyzerScreen
import com.nexarq.app.ui.screens.ApkInspectorScreen
import com.nexarq.app.ui.screens.ArchiveViewerScreen
import com.nexarq.app.ui.screens.BatchRenameScreen
import com.nexarq.app.ui.screens.BrowserScreen
import com.nexarq.app.ui.screens.DuplicatesScreen
import com.nexarq.app.ui.screens.FileCompareScreen
import com.nexarq.app.ui.screens.HashScreen
import com.nexarq.app.ui.screens.HexViewerScreen
import com.nexarq.app.ui.screens.HomeScreen
import com.nexarq.app.ui.screens.ImageViewerScreen
import com.nexarq.app.ui.screens.OperationsScreen
import com.nexarq.app.ui.screens.RootBrowserScreen
import com.nexarq.app.ui.screens.SearchScreen
import com.nexarq.app.ui.screens.SettingsScreen
import com.nexarq.app.ui.screens.TextEditorScreen
import com.nexarq.app.ui.screens.ToolsScreen
import com.nexarq.app.ui.theme.NexarqTheme

fun defaultStoragePath(): String {
    return runCatching { Environment.getExternalStorageDirectory().absolutePath }
        .getOrNull()
        ?: runCatching { java.io.File("/storage/emulated/0").takeIf { it.exists() }?.absolutePath }
            .getOrNull()
        ?: "/"
}

@Composable
fun NexarqRoot(container: AppContainer) {
    val settings by container.settings.settings.collectAsState()
    NexarqTheme(themeOverride = settings.theme, dynamicColors = settings.dynamicColors) {
        CompositionLocalProvider(LocalContainer provides container) {
            AppScaffold(container = container)
        }
    }
}

private data class Tab(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    companion object {
        val HOME = Tab("Home", Icons.Default.Home)
        val FILES = Tab("Files", Icons.Default.Folder)
        val TOOLS = Tab("Tools", Icons.Default.Widgets)
        val AI = Tab("AI", Icons.Default.AutoAwesome)
        val SETTINGS = Tab("Settings", Icons.Default.Settings)
    }
}

@Composable
private fun AppScaffold(container: AppContainer) {
    val navigator = rememberNavigator()
    var selectedTab by rememberSaveable { mutableStateOf(0) }
    val tabs = listOf(Tab.HOME, Tab.FILES, Tab.TOOLS, Tab.AI, Tab.SETTINGS)

    BackHandler(enabled = navigator.canGoBack) { navigator.pop() }

    Scaffold(
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { index, tab ->
                    NavigationBarItem(
                        selected = selectedTab == index,
                        onClick = {
                            selectedTab = index
                            when (tab) {
                                Tab.HOME -> navigator.resetTo(Screen.Home)
                                Tab.FILES -> navigator.resetTo(Screen.Browser(defaultStoragePath()))
                                Tab.TOOLS -> navigator.resetTo(Screen.Tools)
                                Tab.AI -> navigator.resetTo(Screen.AiCenter)
                                Tab.SETTINGS -> navigator.resetTo(Screen.Settings)
                            }
                        },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) },
                    )
                }
            }
        },
    ) { padding ->
        val screen = navigator.current
        androidx.compose.foundation.layout.Box(modifier = Modifier.padding(padding)) {
            when (screen) {
                is Screen.Home -> HomeScreen(navigator)
                is Screen.Browser -> BrowserScreen(path = screen.path, navigator = navigator)
                is Screen.ArchiveViewer -> ArchiveViewerScreen(path = screen.path, navigator = navigator)
                is Screen.TextEditor -> TextEditorScreen(path = screen.path, navigator = navigator)
                is Screen.HexViewer -> HexViewerScreen(path = screen.path, navigator = navigator)
                is Screen.ImagePreview -> ImageViewerScreen(path = screen.path, navigator = navigator)
                is Screen.ApkInspector -> ApkInspectorScreen(path = screen.path, navigator = navigator)
                is Screen.Search -> SearchScreen(navigator)
                is Screen.Tools -> ToolsScreen(navigator)
                is Screen.Analyzer -> AnalyzerScreen(navigator)
                is Screen.Duplicates -> DuplicatesScreen(navigator)
                is Screen.HashTool -> HashScreen(navigator)
                is Screen.BatchRename -> BatchRenameScreen(paths = screen.paths, navigator = navigator)
                is Screen.FileCompare -> FileCompareScreen(left = screen.left, right = screen.right, navigator = navigator)
                is Screen.AiCenter -> AiCenterScreen(navigator)
                is Screen.Settings -> SettingsScreen(navigator)
                is Screen.Operations -> OperationsScreen(navigator)
                is Screen.RootBrowser -> RootBrowserScreen(navigator)
                is Screen.About -> AboutScreen(navigator)
            }
        }
    }
}
