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
        println("::warning title=diag-count::${lines.size} lines")
        lines.take(600).chunked(40).forEachIndexed { i, c ->
            val msg = c.joinToString("%0A") { it.replace("%", "%25").replace("\r", "").replace("\n", "%0A") }
            println("::error title=diag-$i::$msg")
        }
    }
}
