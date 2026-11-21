package com.example.service

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Displays an instant WindowManager shield overlay over Incognito / Private browser tabs
 * because browsers suppress page DOM accessibility nodes inside private sessions.
 */
object PrivateTabOverlayManager {

    private const val TAG = "PrivateTabOverlay"
    private var overlayView: View? = null
    private var lastShownAtMs: Long = 0L

    fun show(
        context: Context,
        browserName: String,
        detectedReason: String,
        isFromAccessibilityService: Boolean = true
    ) {
        val now = System.currentTimeMillis()
        if (overlayView != null && (now - lastShownAtMs) < 1500L) {
            return
        }

        val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return
        val canDrawOverlay = Settings.canDrawOverlays(context)
        if (!canDrawOverlay && !isFromAccessibilityService) {
            return
        }

        val windowType = when {
            isFromAccessibilityService -> WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O -> WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else -> @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE
        }

        dismiss(context)

        try {
            val dp = context.resources.displayMetrics.density
            val root = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setBackgroundColor(Color.parseColor("#EE0F172A"))
                setPadding((28 * dp).toInt(), (36 * dp).toInt(), (28 * dp).toInt(), (36 * dp).toInt())
            }

            val card = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#FFFFFF"))
                    cornerRadius = 24f * dp
                }
                setPadding((24 * dp).toInt(), (28 * dp).toInt(), (24 * dp).toInt(), (24 * dp).toInt())
            }

            val badge = TextView(context).apply {
                text = "PRIVATE TAB BLOCKED"
                setTextColor(Color.parseColor("#B91C1C"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setTypeface(null, Typeface.BOLD)
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#FEE2E2"))
                    cornerRadius = 50f * dp
                }
                setPadding((14 * dp).toInt(), (6 * dp).toInt(), (14 * dp).toInt(), (6 * dp).toInt())
            }

            val titleView = TextView(context).apply {
                text = "Incognito & Private Browsing Restricted"
                setTextColor(Color.parseColor("#0F172A"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
                setTypeface(null, Typeface.BOLD)
                gravity = Gravity.CENTER
                setPadding(0, (16 * dp).toInt(), 0, (10 * dp).toInt())
            }

            val descView = TextView(context).apply {
                text = "FocusSense detected private browsing mode in $browserName ($detectedReason). Private tabs bypass family safety protections and are blocked by your Parent's policy."
                setTextColor(Color.parseColor("#475569"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                gravity = Gravity.CENTER
                setPadding(0, 0, 0, (22 * dp).toInt())
            }

            val homeBtn = Button(context).apply {
                text = "Return to Safe Home"
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                setTypeface(null, Typeface.BOLD)
                isAllCaps = false
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#4F46E5"))
                    cornerRadius = 14f * dp
                }
                minimumHeight = (50 * dp).toInt()
                setPadding((20 * dp).toInt(), (12 * dp).toInt(), (20 * dp).toInt(), (12 * dp).toInt())
                setOnClickListener {
                    dismiss(context)
                    try {
                        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                            addCategory(Intent.CATEGORY_HOME)
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        context.startActivity(homeIntent)
                    } catch (_: Exception) {}
                }
            }

            card.addView(badge)
            card.addView(titleView)
            card.addView(descView)
            card.addView(
                homeBtn,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            )

            root.addView(
                card,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            )

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                windowType,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.CENTER
            }

            wm.addView(root, params)
            overlayView = root
            lastShownAtMs = now
        } catch (e: Exception) {
            Log.w(TAG, "Unable to attach private-tab overlay window: ${e.message}")
            overlayView = null
        }
    }

    fun dismiss(context: Context) {
        val view = overlayView ?: return
        try {
            val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
            wm?.removeViewImmediate(view)
        } catch (_: Exception) {
        } finally {
            overlayView = null
        }
    }
}
