package com.ironlog.app

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.ironlog.app.ui.theme.TypographyRuntime
import com.ironlog.app.ui.theme.resourceId

class HealthConnectRationaleActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val typography = TypographyRuntime.store(this).selection.value
        fun TextView.applyAppTypography(weight: Int) {
            typeface = resources.getFont(typography.font.resourceId())
            fontVariationSettings = "'wght' ${typography.weightFor(weight)}"
        }

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
            text = getString(R.string.health_connect_rationale_title)
            setTextColor(Color.WHITE)
            textSize = 28f
            applyAppTypography(700)
        }

        val body = TextView(this).apply {
            text = getString(R.string.health_connect_rationale_body)
            setTextColor(Color.rgb(190, 190, 190))
            textSize = 17f
            applyAppTypography(400)
            setLineSpacing(dp(4).toFloat(), 1f)
            setPadding(0, dp(18), 0, dp(26))
        }

        val done = Button(this).apply {
            text = getString(R.string.action_done)
            applyAppTypography(500)
            setOnClickListener { finish() }
        }

        root.addView(title)
        root.addView(body)
        root.addView(done)
        setContentView(root)
    }
}
