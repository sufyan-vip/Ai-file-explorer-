package com.nexarq.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import com.nexarq.app.data.AppContainer

/** CompositionLocal so any composable can reach the dependency container. */
val LocalContainer = compositionLocalOf<AppContainer?> { null }

sealed class Screen {
    data object Home : Screen()
    data class Browser(val path: String) : Screen()
    data class ArchiveViewer(val path: String) : Screen()
    data class TextEditor(val path: String) : Screen()
    data class HexViewer(val path: String) : Screen()
    data class ImagePreview(val path: String) : Screen()
    data class ApkInspector(val path: String) : Screen()
    data object Search : Screen()
    data object Tools : Screen()
    data object Analyzer : Screen()
    data object Duplicates : Screen()
    data object HashTool : Screen()
    data class BatchRename(val paths: List<String>) : Screen()
    data class FileCompare(val left: String, val right: String) : Screen()
    data object AiCenter : Screen()
    data object Settings : Screen()
    data object Operations : Screen()
    data object RootBrowser : Screen()
    data object About : Screen()
}

/** Simple back-stack navigator. Avoids route-encoding issues with filesystem paths. */
class Navigator {
    private val stack = mutableStateListOf<Screen>(Screen.Home)

    val current: Screen get() = stack.last()
    val canGoBack: Boolean get() = stack.size > 1

    fun push(screen: Screen) = stack.add(screen)
    fun pop() { if (stack.size > 1) stack.removeAt(stack.size - 1) }
    fun replace(screen: Screen) { stack[stack.size - 1] = screen }
    fun resetTo(screen: Screen) { stack.clear(); stack.add(screen) }
}

@Composable
fun rememberNavigator(): Navigator = remember { Navigator() }
