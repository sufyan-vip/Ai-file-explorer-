pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "NEXARQ"
include(":app")

// ---- TEMP CI DIAGNOSTICS: surface errors as GitHub annotations ----
if (System.getenv("GITHUB_ACTIONS") == "true") {
    val ciErrors = java.util.Collections.synchronizedList(mutableListOf<String>())
    gradle.beforeProject {
        tasks.configureEach {
            val t = this
            t.logging.addStandardErrorListener(StandardOutputListener { chunk ->
                chunk.toString().lines().filter { it.isNotBlank() && !it.startsWith("::") }
                    .forEach { ciErrors.add("[${t.name}] $it") }
            })
        }
    }
    @Suppress("DEPRECATION")
    gradle.buildFinished {
        val lines = ciErrors.toMutableList()
        var f: Throwable? = failure
        var depth = 0
        while (f != null && depth < 15) {
            lines.add("FAILURE: ${f.javaClass.name}: ${f.message}")
            f = f.cause; depth++
        }
        val short = lines.map {
            it.replace("file:///home/runner/work/Ai-file-explorer-/Ai-file-explorer-/app/src/main/java/com/nexarq/app/", "")
              .replace("[compileDebugKotlin] e: ", "")
        }
        val chunks = short.take(900).chunked(22)
        chunks.forEachIndexed { i, c ->
            val msg = c.joinToString("%0A") { it.take(300).replace("%", "%25").replace("\r", "").replace("\n", "%0A") }
            val level = when (i / 10) { 0 -> "error"; 1 -> "warning"; else -> "notice" }
            println("::$level title=diag-$i::$msg")
        }
    }
}
