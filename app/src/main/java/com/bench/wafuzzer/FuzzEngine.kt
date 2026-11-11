package com.bench.wafuzzer

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
    private var sinceMs: Long = 0

    suspend fun run(iterationsPerSeed: Int = 12) {
        val mode = ProcWatch.detectMode(ctx)
        onLog("ProcWatch: $mode (root=${ProcWatch.hasRoot()})")

        surface = SurfaceEnumerator.enumerate(ctx)
        onLog("Surface: ${surface.size} entry")

        sinceMs = System.currentTimeMillis()

        for ((i, s) in surface.withIndex()) {
            onLog("[${i + 1}/${surface.size}] ${s.activity}")
            fireOnSurface(s, iterationsPerSeed)
            delay(250)
        }

        val file = ReportWriter.write(ctx, reports, surface.map { "${it.pkg}/${it.activity}" })
        onLog("Report: ${file.absolutePath}")
    }

    private suspend fun fireOnSurface(s: AttackSurface, iters: Int) {
        if (s.schemes.isNotEmpty()) {
            for (seed in SurfaceEnumerator.seedUris()) {
                for (uri in Mutator.mutateUri(seed, iters)) fireUri(s, uri)
            }
        }
        if (s.mimeTypes.isNotEmpty()) {
            for (mimeSeed in s.mimeTypes) {
                for (m in Mutator.mutateMime(mimeSeed, iters)) fireSend(s, m)
            }
        }
    }

    private suspend fun fireUri(s: AttackSurface, uri: String) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setPackage(s.pkg)
            data = Uri.parse(uri)
            addCategory(Intent.CATEGORY_DEFAULT)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }

        val before = ProcWatch.count(ctx)
        try {
            ctx.startActivity(intent)
        } catch (e: Exception) {
            reports += crash(s, Intent.ACTION_VIEW, uri, null, before, -1, "NO_HANDLER", e.message, null, null)
            return
        }

        delay(800)
        val after = ProcWatch.count(ctx)
        if (before > 0 && after < before) {
            val trace = LogcatParser.capture(s.pkg, sinceMs)
            reports += crash(
                s, Intent.ACTION_VIEW, uri, null, before, after, "PROCESS_DIED",
                LogcatParser.exceptionOf(trace), trace, LogcatParser.crashedProcessOf(trace)
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

        val before = ProcWatch.count(ctx)
        try {
            ctx.startActivity(intent)
        } catch (e: Exception) {
            reports += crash(s, Intent.ACTION_SEND, null, mime, before, -1, "NO_HANDLER", e.message, null, null)
            return
        }

        delay(800)
        val after = ProcWatch.count(ctx)
        if (before > 0 && after < before) {
            val trace = LogcatParser.capture(s.pkg, sinceMs)
            reports += crash(
                s, Intent.ACTION_SEND, null, mime, before, after, "PROCESS_DIED",
                LogcatParser.exceptionOf(trace), trace, LogcatParser.crashedProcessOf(trace)
            )
            onLog("  !! CRASH mime=$mime")
        }
    }

    private fun crash(
        s: AttackSurface, action: String, uri: String?, mime: String?,
        before: Int, after: Int, signal: String,
        exception: String?, trace: String?, crashedProcess: String?
    ) = CrashReport(
        timestamp = System.currentTimeMillis(),
        packageName = s.pkg,
        activity = s.activity,
        action = action,
        dataUri = uri,
        mimeType = mime,
        extras = emptyMap(),
        processBefore = before,
        processAfter = after,
        signal = signal,
        exception = exception,
        logcatTrace = trace,
        crashedProcess = crashedProcess
    )

    private suspend fun delay(ms: Long) = suspendCoroutine<Unit> { c ->
        handler.postDelayed({ c.resume(Unit) }, ms)
    }
}
