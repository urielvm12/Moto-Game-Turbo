package com.moto.gameturbo

import android.app.ActivityManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Choreographer
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class OverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var shizukuHelper: ShizukuHelper
    private var edgeHandleView: View? = null
    private var hudPanelView: View? = null
    private var isPanelOpen = false

    private var tvFps: TextView? = null
    private var tvRam: TextView? = null

    private var frameCount = 0
    private var lastFpsTime = System.nanoTime()
    private var currentFps = 60

    private val handler = Handler(Looper.getMainLooper())

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            frameCount++
            val elapsed = frameTimeNanos - lastFpsTime
            if (elapsed >= 1_000_000_000L) {
                currentFps = ((frameCount * 1_000_000_000L) / elapsed).toInt()
                frameCount = 0
                lastFpsTime = frameTimeNanos
                updateStatsUI()
            }
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        shizukuHelper = ShizukuHelper(this)
        if (shizukuHelper.isShizukuReady()) {
            shizukuHelper.bindService()
        }

        startForegroundNotification()
        showEdgeHandle()
        Choreographer.getInstance().postFrameCallback(frameCallback)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        Choreographer.getInstance().removeFrameCallback(frameCallback)
        handler.removeCallbacksAndMessages(null)
        edgeHandleView?.let { windowManager.removeView(it) }
        hudPanelView?.let { windowManager.removeView(it) }
        shizukuHelper.unbindService()
    }

    private fun startForegroundNotification() {
        val channelId = "moto_game_turbo_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Moto Game Turbo HUD",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        val notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, channelId)
                .setContentTitle("Moto Game Turbo Activo")
                .setContentText("Desliza o toca la pestaña lateral para abrir el HUD")
                .setSmallIcon(android.R.drawable.ic_media_play)
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setContentTitle("Moto Game Turbo Activo")
                .setSmallIcon(android.R.drawable.ic_media_play)
                .build()
        }

        startForeground(101, notification)
    }

    private fun overlayLayoutType(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
    }

    private fun showEdgeHandle() {
        val handle = TextView(this).apply {
            text = "⚡"
            textSize = 16f
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#00E5FF"))
            setPadding(14, 36, 14, 36)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#CC0D1117"))
                cornerRadii = floatArrayOf(24f, 24f, 0f, 0f, 0f, 0f, 24f, 24f)
                setStroke(2, Color.parseColor("#00E5FF"))
            }
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayLayoutType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            x = 0
            y = -100
        }

        var initialY = 0
        var initialTouchY = 0f
        var isClick = true

        handle.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialY = params.y
                    initialTouchY = event.rawY
                    isClick = true
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaY = (event.rawY - initialTouchY).toInt()
                    if (kotlin.math.abs(deltaY) > 12) isClick = false
                    params.y = initialY + deltaY
                    windowManager.updateViewLayout(handle, params)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (isClick) toggleHudPanel()
                    true
                }
                else -> false
            }
        }

        edgeHandleView = handle
        windowManager.addView(handle, params)
    }

    private fun toggleHudPanel() {
        if (isPanelOpen) {
            closeHudPanel()
        } else {
            openHudPanel()
        }
    }

    private fun openHudPanel() {
        if (hudPanelView != null) return

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(36, 36, 36, 36)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#EE0D1117"))
                cornerRadius = 32f
                setStroke(3, Color.parseColor("#00E5FF"))
            }
        }

        val header = TextView(this).apply {
            text = "MOTO GAME TURBO"
            textSize = 16f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#00E5FF"))
            gravity = Gravity.CENTER
        }

        val statsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, 20, 0, 20)
        }

        tvFps = TextView(this).apply {
            text = "FPS: $currentFps"
            textSize = 15f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#00E676"))
            setPadding(0, 0, 24, 0)
        }

        tvRam = TextView(this).apply {
            text = "RAM: --%"
            textSize = 15f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#FFEA00"))
        }

        statsRow.addView(tvFps)
        statsRow.addView(tvRam)

        val btnBoost = hudButton("🚀 Limpiar RAM y Caché", "#00C853") {
            performRamBoost()
        }

        val btnDnd = hudButton("🔕 Alternar No Molestar", "#1F6FEB") {
            toggleDoNotDisturb()
        }

        val hzRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        val btn60Hz = hudButton("60 Hz", "#30363D") {
            applyRefreshRate(60f)
        }.apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                topMargin = 16
                marginEnd = 8
            }
        }

        val btn90Hz = hudButton("90 Hz Turbo", "#8957E5") {
            applyRefreshRate(90f)
        }.apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                topMargin = 16
                marginStart = 8
            }
        }

        hzRow.addView(btn60Hz)
        hzRow.addView(btn90Hz)

        val btnClose = hudButton("Cerrar Panel", "#DA3633") {
            closeHudPanel()
        }

        container.addView(header)
        container.addView(statsRow)
        container.addView(btnBoost)
        container.addView(btnDnd)
        container.addView(hzRow)
        container.addView(btnClose)

        val params = WindowManager.LayoutParams(
            680,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayLayoutType(),
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
        }

        hudPanelView = container
        windowManager.addView(container, params)
        isPanelOpen = true
        updateStatsUI()
    }

    private fun closeHudPanel() {
        hudPanelView?.let {
            windowManager.removeView(it)
            hudPanelView = null
        }
        isPanelOpen = false
    }

    private fun hudButton(label: String, hexColor: String, action: () -> Unit): Button {
        return Button(this).apply {
            text = label
            textSize = 13f
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
            background = GradientDrawable().apply {
                setColor(Color.parseColor(hexColor))
                cornerRadius = 18f
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = 16
            }
            setPadding(16, 20, 16, 20)
            setOnClickListener { action() }
        }
    }

    private fun updateStatsUI() {
        val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        am.getMemoryInfo(memInfo)
        val usedPercent = (((memInfo.totalMem - memInfo.availMem).toDouble() / memInfo.totalMem) * 100).toInt()

        tvFps?.text = "FPS: $currentFps"
        tvRam?.text = "RAM: $usedPercent%"
    }

    private fun performRamBoost() {
        val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memBefore = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }.availMem

        @Suppress("DEPRECATION")
        am.runningAppProcesses?.forEach { proc ->
            if (proc.processName != packageName) {
                am.killBackgroundProcesses(proc.processName)
            }
        }

        if (shizukuHelper.isShizukuReady()) {
            shizukuHelper.deepCleanMemory()
        }

        handler.postDelayed({
            val memAfter = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }.availMem
            val freedMb = ((memAfter - memBefore) / (1024 * 1024)).coerceAtLeast(0)
            updateStatsUI()
            Toast.makeText(this, "RAM optimizada (+${freedMb} MB libres)", Toast.LENGTH_SHORT).show()
        }, 400)
    }

    private fun toggleDoNotDisturb() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (!nm.isNotificationPolicyAccessGranted) {
            Toast.makeText(this, "Activa el permiso No Molestar en la app", Toast.LENGTH_SHORT).show()
            return
        }
        val nextFilter = if (nm.currentInterruptionFilter == NotificationManager.INTERRUPTION_FILTER_ALL) {
            NotificationManager.INTERRUPTION_FILTER_PRIORITY
        } else {
            NotificationManager.INTERRUPTION_FILTER_ALL
        }
        nm.setInterruptionFilter(nextFilter)
        val state = if (nextFilter == NotificationManager.INTERRUPTION_FILTER_PRIORITY) "ACTIVADO" else "DESACTIVADO"
        Toast.makeText(this, "Modo No Molestar: $state", Toast.LENGTH_SHORT).show()
    }

    private fun applyRefreshRate(hz: Float) {
        if (!shizukuHelper.isShizukuReady()) {
            Toast.makeText(this, "Conecta Shizuku primero para cambiar los Hz", Toast.LENGTH_SHORT).show()
            return
        }
        shizukuHelper.setRefreshRate(hz)
        Toast.makeText(this, "Tasa de refresco fijada a ${hz.toInt()} Hz", Toast.LENGTH_SHORT).show()
    }
}
