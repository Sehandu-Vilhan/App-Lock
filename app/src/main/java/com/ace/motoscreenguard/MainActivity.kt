package com.ace.motoscreenguard

import android.app.Activity
import android.os.Bundle
import android.content.Intent
import android.provider.Settings
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences(GuardAccessibilityService.PREFS, MODE_PRIVATE)
        val pad = (16 * resources.displayMetrics.density).toInt()
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(pad, pad * 2, pad, pad)
        }
        val title = TextView(this).apply {
            text = "Screen-Off Guard (prototype)"
            textSize = 23f
        }
        val note = TextView(this).apply {
            text = "Choose apps by package name. After the screen turns off while a protected app is open, this prototype asks for a local PIN when that app is seen again."
            textSize = 15f
            setPadding(0, pad, 0, pad)
        }
        val pin = EditText(this).apply {
            hint = "Set a PIN (4–12 digits)"
            inputType = 2
            setText(prefs.getString("pin", ""))
        }
        val packages = EditText(this).apply {
            hint = "Protected package names, comma-separated"
            minLines = 3
            setText(prefs.getString("packages", ""))
        }
        val save = Button(this).apply {
            text = "Save PIN and protected apps"
            setOnClickListener {
                val p = pin.text.toString()
                val names = packages.text.toString().split(",").map { it.trim() }
                    .filter { it.isNotBlank() }.distinct()
                if (p.length !in 4..12 || !p.all { it.isDigit() }) {
                    Toast.makeText(this@MainActivity, "Use a 4–12 digit PIN.", Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }
                if (names.isEmpty()) {
                    Toast.makeText(this@MainActivity, "Enter at least one package name.", Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }
                prefs.edit().putString("pin", p).putString("packages", names.joinToString(","))
                    .apply()
                Toast.makeText(this@MainActivity, "Saved on this phone.", Toast.LENGTH_LONG).show()
            }
        }
        val accessibility = Button(this).apply {
            text = "Open Accessibility settings"
            setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        }
        val help = TextView(this).apply {
            text = "\nEnable “Screen-Off Guard service” in Accessibility settings. Find an app's package name from its app details or a package-name utility. Test with a non-sensitive app first.\n\nLimitations: this is an experimental prototype, not a replacement for Android's secure lock screen. Accessibility services can be stopped, and an overlay-based app lock can have bypasses. Do not rely on it for high-risk secrets."
            textSize = 13f
            setPadding(0, pad, 0, 0)
        }
        layout.addView(title)
        layout.addView(note)
        layout.addView(pin, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        layout.addView(packages, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        layout.addView(save)
        layout.addView(accessibility)
        layout.addView(help)
        setContentView(layout)
    }
}
