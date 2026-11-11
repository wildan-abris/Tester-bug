package com.bench.wafuzzer

import android.os.Bundle
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.textview.MaterialTextView
import kotlinx.coroutines.launch
import java.io.File

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val logView = MaterialTextView(this).apply {
            textSize = 11f
            setTextIsSelectable(true)
        }

        val btnRun = MaterialButton(this).apply { text = "Run Fuzz (v3)" }
        val btnBounty = MaterialButton(this).apply { text = "Generate Bounty Report" }
        val btnLogcat = MaterialButton(this).apply { text = "Dump Logcat Now" }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 48, 32, 32)
            addView(btnRun)
            addView(btnBounty)
            addView(btnLogcat)
            addView(ScrollView(this@MainActivity).apply {
                addView(logView)
            }, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
            ))
        }

        setContentView(root)

        btnRun.setOnClickListener {
            btnRun.isEnabled = false
            logView.text = ""
            val engine = FuzzEngine(this) { line ->
                runOnUiThread { logView.append("$line\n") }
            }
            lifecycleScope.launch {
                engine.run(iterationsPerSeed = 12)
                btnRun.isEnabled = true
            }
        }

        btnBounty.setOnClickListener {
            val dir = getExternalFilesDir(null) ?: filesDir
            val latest = dir.listFiles { f -> f.name.startsWith("wa_fuzz_") && f.name.endsWith(".json") }
                ?.maxByOrNull { it.lastModified() }
            if (latest == null) {
                logView.append("no report json yet\n")
                return@setOnClickListener
            }
            val out = File(dir, "bounty_${System.currentTimeMillis()}.md")
            BountyReporter.generate(latest, out)
            logView.append("bounty: ${out.absolutePath}\n")
        }

        btnLogcat.setOnClickListener {
            val t = LogcatParser.capture("com.whatsapp", System.currentTimeMillis() - 60_000)
            logView.append(if (t.isNullOrBlank()) "logcat: empty\n" else "logcat:\n$t\n")
        }
    }
}
