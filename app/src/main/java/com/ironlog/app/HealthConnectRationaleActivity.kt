package com.ironlog.app

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class HealthConnectRationaleActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val density = resources.displayMetrics.density
        fun dp(value: Int): Int = (value * density).toInt()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(28), dp(28), dp(28), dp(28))
            setBackgroundColor(Color.rgb(14, 14, 14))
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
        }

        val title = TextView(this).apply {
            text = "Ironlog + Health Connect"
            setTextColor(Color.WHITE)
            textSize = 28f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }

        val body = TextView(this).apply {
            text = "Ironlog uses Health Connect only to improve recovery, readiness, body-weight sync, and workout history sync. Your data stays under Android Health Connect permission controls, and you can revoke access at any time."
            setTextColor(Color.rgb(190, 190, 190))
            textSize = 17f
            setLineSpacing(dp(4).toFloat(), 1f)
            setPadding(0, dp(18), 0, dp(26))
        }

        val privacy = Button(this).apply {
            text = "Open Privacy Policy"
            setOnClickListener {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://ironlogpro.app/privacy")))
            }
        }

        val done = Button(this).apply {
            text = "Done"
            setOnClickListener { finish() }
        }

        root.addView(title)
        root.addView(body)
        root.addView(privacy)
        root.addView(done)
        setContentView(root)
    }
}
