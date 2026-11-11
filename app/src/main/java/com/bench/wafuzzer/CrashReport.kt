package com.bench.wafuzzer

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class CrashReport(
    val timestamp: Long,
    val packageName: String,
    val activity: String,
    val action: String,
    val dataUri: String?,
    val mimeType: String?,
    val extras: Map<String, String>,
    val processBefore: Int,
    val processAfter: Int,
    val signal: String,
    val exception: String? = null,
    val logcatTrace: String? = null,
    val crashedProcess: String? = null
)

object ReportWriter {

    fun write(ctx: Context, reports: List<CrashReport>, surface: List<String>): File {
        val root = JSONObject()
        root.put("generatedAt", System.currentTimeMillis())
        root.put("device", android.os.Build.MODEL)
        root.put("android", android.os.Build.VERSION.RELEASE)
        root.put("sdk", android.os.Build.VERSION.SDK_INT)

        val surfaceArr = JSONArray()
        surface.forEach { surfaceArr.put(it) }
        root.put("exportedSurface", surfaceArr)

        val reportArr = JSONArray()
        reports.forEach { r ->
            val o = JSONObject()
            o.put("ts", r.timestamp)
            o.put("pkg", r.packageName)
            o.put("activity", r.activity)
            o.put("action", r.action)
            o.put("dataUri", r.dataUri ?: JSONObject.NULL)
            o.put("mimeType", r.mimeType ?: JSONObject.NULL)
            val ex = JSONObject()
            r.extras.forEach { (k, v) -> ex.put(k, v) }
            o.put("extras", ex)
            o.put("processBefore", r.processBefore)
            o.put("processAfter", r.processAfter)
            o.put("signal", r.signal)
            o.put("exception", r.exception ?: JSONObject.NULL)
            o.put("crashedProcess", r.crashedProcess ?: JSONObject.NULL)
            o.put("logcatTrace", r.logcatTrace ?: JSONObject.NULL)
            reportArr.put(o)
        }
        root.put("crashes", reportArr)

        val dedup = reports
            .filter { it.signal == "PROCESS_DIED" || it.signal == "EXCEPTION" }
            .distinctBy { "${it.packageName}|${it.activity}|${it.action}|${it.dataUri}|${it.signal}" }

        val dedupArr = JSONArray()
        dedup.forEach { r ->
            dedupArr.put(JSONObject().apply {
                put("pkg", r.packageName)
                put("activity", r.activity)
                put("action", r.action)
                put("dataUri", r.dataUri ?: JSONObject.NULL)
                put("signal", r.signal)
                put("crashedProcess", r.crashedProcess ?: JSONObject.NULL)
                put("exception", r.exception ?: JSONObject.NULL)
            })
        }
        root.put("uniqueCrashes", dedupArr)

        val dir = ctx.getExternalFilesDir(null) ?: ctx.filesDir
        val f = File(dir, "wa_fuzz_${System.currentTimeMillis()}.json")
        f.writeText(root.toString(2))
        return f
    }
}
