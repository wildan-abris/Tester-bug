package com.bench.wafuzzer

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri

data class AttackSurface(
    val pkg: String,
    val activity: String,
    val actions: List<String>,
    val schemes: List<String>,
    val mimeTypes: List<String>
)

object SurfaceEnumerator {

    private const val WA = "com.whatsapp"
    private const val W4B = "com.whatsapp.w4b"

    private val ACTIONS = listOf(
        Intent.ACTION_VIEW,
        Intent.ACTION_SEND,
        Intent.ACTION_SENDTO,
        Intent.ACTION_MAIN,
        Intent.ACTION_ATTACH_DATA,
        Intent.ACTION_EDIT
    )

    private val SCHEMES = listOf(
        "https", "http", "whatsapp", "whatsapp-xyz", "smsto", "tel"
    )

    private val MIMES = listOf(
        "text/plain", "text/x-vcard", "image/*", "video/*",
        "application/pdf", "application/octet-stream", "*/*"
    )

    private val PREFIXES = listOf(
        "https://wa.me/",
        "https://api.whatsapp.com/send?phone=",
        "https://chat.whatsapp.com/",
        "whatsapp://send?phone=",
        "whatsapp://call?phone=",
        "whatsapp://"
    )

    private val SUFFIXES = listOf(
        "", "0", "6281234567890", "0".repeat(2048),
        "%00", "%ZZ%ZZ", "?", "?text=", "?text=%00",
        "?text=" + "A".repeat(4096), "#", "//", "../../../etc/passwd"
    )

    fun enumerate(ctx: Context): List<AttackSurface> {
        val pm = ctx.packageManager
        val out = mutableListOf<AttackSurface>()

        for (pkg in listOf(WA, W4B)) {
            try {
                pm.getPackageInfo(pkg, 0)
            } catch (_: PackageManager.NameNotFoundException) {
                continue
            }

            for (action in ACTIONS) {
                for (scheme in SCHEMES) {
                    val intent = Intent(action).apply {
                        setPackage(pkg)
                        data = Uri.parse("$scheme://")
                        addCategory(Intent.CATEGORY_DEFAULT)
                    }
                    val matches = pm.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
                    for (m in matches) {
                        out += AttackSurface(
                            pkg = pkg,
                            activity = m.activityInfo.name,
                            actions = listOf(action),
                            schemes = listOf(scheme),
                            mimeTypes = emptyList()
                        )
                    }
                }
                for (mime in MIMES) {
                    val intent = Intent(action).apply {
                        setPackage(pkg)
                        type = mime
                        addCategory(Intent.CATEGORY_DEFAULT)
                    }
                    val matches = pm.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
                    for (m in matches) {
                        out += AttackSurface(
                            pkg = pkg,
                            activity = m.activityInfo.name,
                            actions = listOf(action),
                            schemes = emptyList(),
                            mimeTypes = listOf(mime)
                        )
                    }
                }
            }
        }

        return out.distinctBy {
            "${it.pkg}|${it.activity}|${it.actions.joinToString(",")}|${it.schemes.joinToString(",")}|${it.mimeTypes.joinToString(",")}"
        }
    }

    fun seedUris(): List<String> {
        val seeds = mutableListOf<String>()
        for (p in PREFIXES) {
            for (s in SUFFIXES) {
                seeds += "$p$s"
            }
        }
        return seeds
    }
}
