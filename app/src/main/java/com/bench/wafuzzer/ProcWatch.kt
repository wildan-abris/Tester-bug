package com.bench.wafuzzer

import android.app.ActivityManager
import android.content.Context
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

enum class ProcMode { RUNNING_APP, PS, ROOT_PS, UNKNOWN }

object ProcWatch {

    private const val WA = "com.whatsapp"

    @Volatile
    var mode: ProcMode = ProcMode.UNKNOWN
        private set

    fun detectMode(ctx: Context): ProcMode {
        if (mode != ProcMode.UNKNOWN) return mode

        // coba root
        if (runShell("su -c 'echo ok'")?.trim() == "ok") {
            mode = ProcMode.ROOT_PS
            return mode
        }

        // coba ps tanpa root (hasil non-empty = ps bekerja)
        val psOut = runShell("ps -A | grep com.whatsapp")
        if (!psOut.isNullOrBlank()) {
            mode = ProcMode.PS
            return mode
        }

        // fallback ActivityManager
        val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val list = am.runningAppProcesses
        if (list != null) {
            mode = ProcMode.RUNNING_APP
            return mode
        }

        mode = ProcMode.RUNNING_APP
        return mode
    }

    fun count(ctx: Context): Int {
        return when (detectMode(ctx)) {
            ProcMode.ROOT_PS -> {
                val out = runShell("su -c 'ps -A | grep $WA'") ?: return 0
                out.lines().count { it.contains(WA) && !it.contains("grep") }
            }
            ProcMode.PS -> {
                val out = runShell("ps -A | grep $WA") ?: return 0
                out.lines().count { it.contains(WA) && !it.contains("grep") }
            }
            ProcMode.RUNNING_APP -> {
                val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                am.runningAppProcesses?.count { it.processName.startsWith(WA) } ?: 0
            }
            ProcMode.UNKNOWN -> 0
        }
    }

    fun hasRoot(): Boolean = runShell("su -c 'echo ok'")?.trim() == "ok"

    private fun runShell(cmd: String): String? {
        return try {
            val p = Runtime.getRuntime().exec(arrayOf("sh", "-c", cmd))
            val ok = p.waitFor(2, TimeUnit.SECONDS)
            if (!ok) { p.destroy(); return null }
            val r = BufferedReader(InputStreamReader(p.inputStream))
            val sb = StringBuilder()
            var line: String?
            while (r.readLine().also { line = it } != null) {
                sb.append(line).append('\n')
            }
            sb.toString()
        } catch (_: Exception) {
            null
        }
    }
}
