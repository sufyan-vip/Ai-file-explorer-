package com.nexarq.app.tools

/**
 * Local (offline) log analyzer. Detects common error patterns, groups similar
 * messages and estimates crash/error counts. No data leaves the device.
 */
object LogAnalyzer {

    data class Finding(
        val level: Level,
        val category: String,
        val description: String,
        val occurrences: Int,
        val sampleLine: String,
    ) {
        enum class Level { ERROR, WARNING, INFO }
    }

    data class LogSummary(
        val totalLines: Int,
        val errorLines: Int,
        val warningLines: Int,
        val crashLines: Int,
        val exceptionTypes: Map<String, Int>,
        val findings: List<Finding>,
        val firstTimestamp: String?,
        val lastTimestamp: String?,
    )

    private val exceptionPattern = Regex("(?:Exception|Error|Throwable|FATAL)")
    private val crashPattern = Regex("FATAL EXCEPTION|ANR in|Process .* has died|SIGSEGV|SIGABRT|tombstone")
    private val tsPattern = Regex("\\b(\\d{4}-\\d{2}-\\d{2}[ T]\\d{2}:\\d{2}:\\d{2}(?:\\.\\d+)?)")

    fun analyze(text: String): LogSummary {
        val lines = text.lines().filter { it.isNotBlank() }
        var errorLines = 0
        var warningLines = 0
        var crashLines = 0
        val exceptions = mutableMapOf<String, Int>()
        val grouped = mutableMapOf<String, MutableList<String>>()

        val timestamps = mutableListOf<String>()

        for (line in lines) {
            val lower = line.lowercase()
            val ts = tsPattern.find(line)?.groupValues?.get(1)
            if (ts != null) timestamps.add(ts)

            when {
                crashPattern.containsMatchIn(line) -> { crashLines++; errorLines++; bump(exceptions, "crash"); group(grouped, "crash", line) }
                lower.contains("exception") || lower.contains("error") || lower.contains("fatal") ->
                    { errorLines++; bump(exceptions, extractException(line)); group(grouped, "error", line) }
                lower.contains("warn") -> { warningLines++; group(grouped, "warning", line) }
            }
        }

        val findings = grouped.map { (category, samples) ->
            val sample = samples.first()
            val level = when (category) {
                "crash" -> Finding.Level.ERROR
                "error" -> Finding.Level.ERROR
                else -> Finding.Level.WARNING
            }
            Finding(level, category, describe(sample, category), samples.size, sample.take(300))
        }.sortedByDescending { it.occurrences }

        return LogSummary(
            totalLines = lines.size,
            errorLines = errorLines,
            warningLines = warningLines,
            crashLines = crashLines,
            exceptionTypes = exceptions.toList().sortedByDescending { it.second }.toMap(),
            findings = findings,
            firstTimestamp = timestamps.firstOrNull(),
            lastTimestamp = timestamps.lastOrNull(),
        )
    }

    private fun bump(map: MutableMap<String, Int>, key: String) {
        map[key] = (map[key] ?: 0) + 1
    }

    private fun group(map: MutableMap<String, MutableList<String>>, category: String, line: String) {
        map.getOrPut(category) { mutableListOf() }.add(line)
    }

    private fun extractException(line: String): String {
        val m = Regex("([A-Za-z0-9_.$]+(?:Exception|Error))").find(line)
        return m?.groupValues?.get(1) ?: "error"
    }

    private fun describe(sample: String, category: String): String = when (category) {
        "crash" -> "Possible crash detected"
        "error" -> "Repeated error: ${extractException(sample)}"
        "warning" -> "Repeated warning"
        else -> "Notable log pattern"
    }

    /** Build a concise text digest suitable for pasting into the AI assistant. */
    fun digest(summary: LogSummary): String = buildString {
        appendLine("Log analysis (local):")
        appendLine("- ${summary.totalLines} lines, ${summary.errorLines} error lines, ${summary.crashLines} crash signals")
        if (summary.exceptionTypes.isNotEmpty()) {
            appendLine("- Top exceptions:")
            summary.exceptionTypes.entries.take(8).forEach { (k, v) -> appendLine("  · $k × $v") }
        }
        if (summary.firstTimestamp != null) appendLine("- Window: ${summary.firstTimestamp} → ${summary.lastTimestamp}")
    }
}
