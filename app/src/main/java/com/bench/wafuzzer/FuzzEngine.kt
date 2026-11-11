package com.bench.wafuzzer

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class FuzzEngine(
    private val ctx: Context,
    private val onLog: (String) -> Unit
) {

    private val handler = Handler(Looper.getMainLooper())
    private val reports = mutableListOf<CrashReport>()
    private var surface: List<AttackSurface> = emptyList()

    suspend fun run(iterationsPerSeed: Int = 12) {
        surface = SurfaceEnumerator.enumerate(ctx)
        onLog("Surface: ${surface.size} entry")

        for ((i, s) in surface.withIndex()) {
            onLog("[${i + 1}/${surface.size}] ${s.activity}")
            fireOnSurface(s, iterationsPerSeed)
            delay(300)
        }

        val file = ReportWriter.write(ctx, reports, surface.map { "${it.pkg}/${it.activity}" })
        onLog("Report: ${file.absolutePath}")
    }

    private suspend fun fireOnSurface(s: AttackSurface, iters: Int) {
        if (s.schemes.isNotEmpty()) {
            val seeds = SurfaceEnumerator.seedUris()
            for (seed in seeds) {
                val mutations = Mutator.mutateUri(seed, iters)
                for (uri in mutations) {
                    fireUri(s, uri, iters)
                }
            }
        }

        if (s.mimeTypes.isNotEmpty()) {
            for (mimeSeed in s.mimeTypes) {
                val mimes = Mutator.mutateMime(mimeSeed, iters)
                for (m in mimes) {
                    fireSend(s, m)
                }
            }
        }
    }

    private suspend fun fireUri(s: AttackSurface, uri: String, iters: Int) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setPackage(s.pkg)
            data = Uri.parse(uri)
            addCategory(Intent.CATEGORY_DEFAULT)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }

        val before = waProcessCount()
        val signal = try {
            ctx.startActivity(intent)
            null
        } catch (e: Exception) {
            e.message
        }

        if (signal != null) {
            reports += CrashReport(
                timestamp = System.currentTimeMillis(),
                activity = s.activity,
                action = Intent.ACTION_VIEW,
                dataUri = uri,
                mimeType = null,
                extras = emptyMap(),
                processBefore = before,
                processAfter = -1,
                signal = "NO_HANDLER",
                exception = signal
            )
            return
        }

        delay(800)
        val after = waProcessCount()
        val sig = when {
            before > 0 && after == 0 -> "PROCESS_DIED"
            before > 0 && after < before -> "PROCESS_DIED"
            else -> "ALIVE"
        }

        if (sig == "PROCESS_DIED") {
            reports += CrashReport(
                timestamp = System.currentTimeMillis(),
                activity = s.activity,
                action = Intent.ACTION_VIEW,
                dataUri = uri,
                mimeType = null,
                extras = emptyMap(),
                processBefore = before,
                processAfter = after,
                signal = sig
            )
            onLog("  !! CRASH uri=${uri.take(80)}")
        }
    }

    private suspend fun fireSend(s: AttackSurface, mime: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            setPackage(s.pkg)
            type = mime
            putExtra(Intent.EXTRA_TEXT, "fuzz\u0000" + "x".repeat(64))
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }

        val before = waProcessCount()
        try {
            ctx.startActivity(intent)
        } catch (e: Exception) {
            reports += CrashReport(
                timestamp = System.currentTimeMillis(),
                activity = s.activity,
                action = Intent.ACTION_SEND,
                dataUri = null,
                mimeType = mime,
                extras = emptyMap(),
                processBefore = before,
                processAfter = -1,
                signal = "NO_HANDLER",
                exception = e.message
            )
            return
        }

        delay(800)
        val after = waProcessCount()
        if (before > 0 && after == 0) {
            reports += CrashReport(
                timestamp = System.currentTimeMillis(),
                activity = s.activity,
                action = Intent.ACTION_SEND,
                dataUri = null,
                mimeType = mime,
                extras = emptyMap(),
                processBefore = before,
                processAfter = after,
                signal = "PROCESS_DIED"
            )
            onLog("  !! CRASH mime=$mime")
        }
    }

    private fun waProcessCount(): Int {
        val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        return am.runningAppProcesses?.count {
            it.processName.startsWith("com.whatsapp")
        } ?: 0
    }

    private suspend fun delay(ms: Long) = suspendCoroutine<Unit> { c ->
        handler.postDelayed({ c.resume(Unit) }, ms)
    }
}
