package com.bench.wafuzzer

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.textview.MaterialTextView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.view.Gravity

class MainActivity : AppCompatActivity() {

    private lateinit var logView: MaterialTextView
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 48, 32, 32)
        }

        val btn = MaterialButton(this).apply {
            text = "Run Fuzz Cases"
        }

        logView = MaterialTextView(this).apply {
            textSize = 12f
            setTextIsSelectable(true)
        }

        val scroll = ScrollView(this).apply {
            addView(logView)
        }

        root.addView(btn)
        root.addView(scroll, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        ))

        setContentView(root)

        btn.setOnClickListener { runAll() }
    }

    private fun runAll() {
        logView.text = ""
        val cases = FuzzCases.all
        runCase(cases, 0)
    }

    private fun runCase(cases: List<FuzzCase>, idx: Int) {
        if (idx >= cases.size) {
            append("── done ──\n")
            return
        }

        val c = cases[idx]
        append("[${idx + 1}/${cases.size}] ${c.label}\n")

        val intent = try {
            c.build()
        } catch (e: Exception) {
            append("  build err: ${e.message}\n")
            runCase(cases, idx + 1)
            return
        }

        // Deteksi crash dengan polling process WA setelah intent dikirim
        val before = waProcessCount()

        try {
            startActivity(intent)
        } catch (e: Exception) {
            append("  no handler: ${e.message}\n")
            runCase(cases, idx + 1)
            return
        }

        handler.postDelayed({
            val after = waProcessCount()
            if (after == 0 && before > 0) {
                append("  !!! WA PROCESS DIED !!!\n")
            } else if (after < before) {
                append("  ? process count $before -> $after\n")
            } else {
                append("  alive\n")
            }
            // delay kecil antar case supaya logcat sempat flush
            handler.postDelayed({ runCase(cases, idx + 1) }, 250)
        }, 1500)
    }

    private fun waProcessCount(): Int {
        val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        return am.runningAppProcesses?.count {
            it.processName.startsWith("com.whatsapp")
        } ?: 0
    }

    private fun append(s: String) {
        logView.append(s)
    }
}
