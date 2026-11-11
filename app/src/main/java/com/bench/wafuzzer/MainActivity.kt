package com.bench.wafuzzer

import android.os.Bundle
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.textview.MaterialTextView
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val logView = MaterialTextView(this).apply {
            textSize = 11f
            setTextIsSelectable(true)
        }

        val btn = MaterialButton(this).apply {
            text = "Run Fuzz (v2)"
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 48, 32, 32)
            addView(btn)
            addView(ScrollView(this@MainActivity).apply {
                addView(logView)
            }, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
            ))
        }

        setContentView(root)

        btn.setOnClickListener {
            btn.isEnabled = false
            logView.text = ""
            val engine = FuzzEngine(this) { line ->
                runOnUiThread {
                    logView.append("$line\n")
                }
            }
            lifecycleScope.launch {
                engine.run(iterationsPerSeed = 12)
                btn.isEnabled = true
            }
        }
    }
}
