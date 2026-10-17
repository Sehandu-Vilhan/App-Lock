package com.ace.motoscreenguard

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.os.Build
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class GuardAccessibilityService : AccessibilityService() {
    companion object {
        const val PREFS = "guard_prefs"
    }

    private var screenWasTurnedOff = false
    private var currentPackage = ""
    private var dialog: AlertDialog? = null

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    val protected = protectedPackages()
                    screenWasTurnedOff = currentPackage in protected
                }
                Intent.ACTION_SCREEN_ON, Intent.ACTION_USER_PRESENT -> {
                    if (screenWasTurnedOff) {
                        screenWasTurnedOff = false
                        // Window events can arrive just after SCREEN_ON; retry on the next event too.
                        if (currentPackage in protectedPackages()) showPinGate()
                    }
                }
            }
        }
    }

    override fun onServiceConnected() {
        serviceInfo = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                AccessibilityEvent.TYPE_WINDOWS_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            notificationTimeout = 50
            flags = AccessibilityServiceInfo.DEFAULT
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(screenReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(screenReceiver, filter)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val pkg = event?.packageName?.toString() ?: return
        currentPackage = pkg
        if (screenWasTurnedOff && pkg in protectedPackages()) {
            screenWasTurnedOff = false
            showPinGate()
        }
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        try { unregisterReceiver(screenReceiver) } catch (_: Exception) {}
        dialog?.dismiss()
        super.onDestroy()
    }

    private fun protectedPackages(): Set<String> =
        getSharedPreferences(PREFS, MODE_PRIVATE).getString("packages", "")
            .orEmpty().split(",").map { it.trim() }.filter { it.isNotBlank() }.toSet()

    private fun showPinGate() {
        if (dialog?.isShowing == true) return
        val expectedPin = getSharedPreferences(PREFS, MODE_PRIVATE).getString("pin", "").orEmpty()
        if (expectedPin.isBlank()) return

        val density = resources.displayMetrics.density
        val pad = (24 * density).toInt()
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
            setBackgroundColor(Color.WHITE)
        }
        val title = TextView(this).apply {
            text = "Private app locked"
            textSize = 22f
            setTextColor(Color.BLACK)
        }
        val message = TextView(this).apply {
            text = "Enter your Screen-Off Guard PIN to continue."
            textSize = 15f
            setTextColor(Color.DKGRAY)
        }
        val input = EditText(this).apply {
            hint = "PIN"
            inputType = 2
        }
        box.addView(title)
        box.addView(message)
        box.addView(input)

        val builder = AlertDialog.Builder(this)
            .setView(box)
            .setCancelable(false)
            .setPositiveButton("Unlock", null)
        try {
            dialog = builder.create().apply {
                window?.setType(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY)
                setOnShowListener {
                    getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        if (input.text.toString() == expectedPin) {
                            dismiss()
                            dialog = null
                        } else {
                            input.setText("")
                            input.error = "Incorrect PIN"
                        }
                    }
                }
                show()
                window?.setType(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY)
            }
        } catch (_: Exception) {
            Toast.makeText(this@GuardAccessibilityService,
                "Could not show PIN gate. This prototype may not work on this Android build.",
                Toast.LENGTH_LONG).show()
        }
    }
}
