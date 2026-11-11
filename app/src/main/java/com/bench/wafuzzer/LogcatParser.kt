package com.bench.wafuzzer

import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

object LogcatParser {

    /**
     * Ambil trace crash untuk paket tertentu setelah [sinceMs].
     * Coba buffer crash dulu, fallback ke buffer main dengan grep.
     */
    fun capture(pkg: String, sinceMs: Long): String? {
        val candidates = listOf(
            "logcat -b crash -d -t 500",
            "logcat -d -t 2000",
            "logcat -d -v brief -t 4000"
        )

        for (cmd in candidates) {
            val raw = runShell(cmd) ?: continue
            val filtered = filter(raw, pkg, sinceMs)
            if (filtered.isNotBlank()) return filtered
        }
        return null
    }

    private fun filter(raw: String, pkg: String, sinceMs: Long): String {
        val lines = raw.lines()
        val out = StringBuilder()
        var capturing = false
        var depth = 0

        for (line in lines) {
            val isCrashHeader = line.contains("FATAL EXCEPTION") ||
                    line.contains("AndroidRuntime")
            val mentionsPkg = line.contains(pkg)

            if (isCrashHeader || (mentionsPkg && !capturing)) {
                capturing = true
            }

            if (capturing) {
                // heuristik: trace berakhir ketika ketemu baris kosong beruntun
                if (line.isBlank()) {
                    depth++
                    if (depth >= 2 && out.isNotBlank()) {
                        capturing = false
                        depth = 0
                        out.append("\n---\n")
                    }
                } else {
                    depth = 0
                    out.append(line).append('\n')
                }
            }
        }

        // filter window waktu jika trace menyertakan timestamp (logcat default -v threadtime)
        if (out.isBlank()) return ""
        return out.toString()
    }

    fun crashedProcessOf(trace: String?): String? {
        if (trace == null) return null
        val m = Regex("Process: ([\\w.]+), PID: \\d+").find(trace)
        return m?.groupValues?.getOrNull(1)
    }

    fun exceptionOf(trace: String?): String? {
        if (trace == null) return null
        val m = Regex("([\\w.$]+Exception[^\\n]*)").find(trace)
        return m?.groupValues?.getOrNull(1)?.take(200)
    }

    private fun runShell(cmd: String): String? {
        return try {
            val p = Runtime.getRuntime().exec(arrayOf("sh", "-c", cmd))
            val ok = p.waitFor(3, TimeUnit.SECONDS)
            if (!ok) { p.destroy(); return null }
            val r = BufferedReader(InputStreamReader(p.inputStream))
            val sb = StringBuilder()
            var line: String?
            while (r.readLine().also { line = it } != null) sb.append(line).append('\n')
            sb.toString()
        } catch (_: Exception) {
            null
        }
    }
}
