package com.moto.gameturbo

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import rikka.shizuku.Shizuku

class MainActivity : AppCompatActivity() {

    private lateinit var shizukuHelper: ShizukuHelper
    private lateinit var statusText: TextView

    private val permissionListener = Shizuku.OnRequestPermissionResultListener { _, grantResult ->
        if (grantResult == PackageManager.PERMISSION_GRANTED) {
            shizukuHelper.bindService()
            updateStatus()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        shizukuHelper = ShizukuHelper(this)
        Shizuku.addRequestPermissionResultListener(permissionListener)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    1002
                )
            }
        }

        buildUI()
    }

    override fun onResume() {
        super.onResume()
        if (shizukuHelper.isShizukuReady()) {
            shizukuHelper.bindService()
        }
        updateStatus()
    }

    override fun onDestroy() {
        super.onDestroy()
        Shizuku.removeRequestPermissionResultListener(permissionListener)
    }

    private fun buildUI() {
        val scrollView = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#0D1117"))
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 64, 48, 64)
        }

        val title = TextView(this).apply {
            text = "MOTO GAME TURBO"
            textSize = 26f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#00E5FF"))
        }

        val subtitle = TextView(this).apply {
            text = "Optimización HUD • Motorola Moto G24"
            textSize = 14f
            setTextColor(Color.parseColor("#8B949E"))
            setPadding(0, 8, 0, 36)
        }

        statusText = TextView(this).apply {
            textSize = 14f
            setTextColor(Color.WHITE)
            setPadding(32, 32, 32, 32)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#161B22"))
                cornerRadius = 24f
                setStroke(2, Color.parseColor("#30363D"))
            }
        }

        val btnOverlay = createStyledButton("1. Permitir Ventana Flotante (HUD)", "#1F6FEB") {
            if (!Settings.canDrawOverlays(this)) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                startActivity(intent)
            } else {
                Toast.makeText(this, "Permiso de ventana flotante ya activo", Toast.LENGTH_SHORT).show()
            }
        }

        val btnDnd = createStyledButton("2. Permitir Control No Molestar", "#1F6FEB") {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (!nm.isNotificationPolicyAccessGranted) {
                startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
            } else {
                Toast.makeText(this, "Permiso No Molestar ya activo", Toast.LENGTH_SHORT).show()
            }
        }

        val btnShizuku = createStyledButton("3. Conectar Shizuku (90Hz / RAM Profunda)", "#8957E5") {
            if (shizukuHelper.isShizukuReady()) {
                shizukuHelper.bindService()
                Toast.makeText(this, "Shizuku conectado", Toast.LENGTH_SHORT).show()
            } else {
                shizukuHelper.requestPermission()
            }
            updateStatus()
        }

        val btnLaunch = createStyledButton("ACTIVAR BARRA GAME TURBO", "#00C853") {
            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "Primero activa el permiso de ventana flotante", Toast.LENGTH_LONG).show()
                return@createStyledButton
            }
            val intent = Intent(this, OverlayService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
            Toast.makeText(this, "Game Turbo activo en el borde lateral", Toast.LENGTH_SHORT).show()
        }

        val btnStop = createStyledButton("DETENER GAME TURBO", "#DA3633") {
            stopService(Intent(this, OverlayService::class.java))
            Toast.makeText(this, "Game Turbo detenido", Toast.LENGTH_SHORT).show()
        }

        root.addView(title)
        root.addView(subtitle)
        root.addView(statusText)
        root.addView(btnOverlay)
        root.addView(btnDnd)
        root.addView(btnShizuku)
        root.addView(btnLaunch)
        root.addView(btnStop)

        scrollView.addView(root)
        setContentView(scrollView)
    }

    private fun createStyledButton(label: String, hexColor: String, onClick: () -> Unit): Button {
        return Button(this).apply {
            text = label
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                setColor(Color.parseColor(hexColor))
                cornerRadius = 20f
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = 28
            }
            setPadding(24, 32, 24, 32)
            setOnClickListener { onClick() }
        }
    }

    private fun updateStatus() {
        val overlayOk = Settings.canDrawOverlays(this)
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val dndOk = nm.isNotificationPolicyAccessGranted
        val shizukuOk = shizukuHelper.isShizukuReady()

        statusText.text = """
            ESTADO DEL SISTEMA:
            • Ventana Flotante (HUD): ${if (overlayOk) "ACTIVO" else "PENDIENTE"}
            • Modo No Molestar: ${if (dndOk) "ACTIVO" else "PENDIENTE"}
            • Shizuku (90Hz / Limpieza): ${if (shizukuOk) "CONECTADO" else "DESCONECTADO"}
        """.trimIndent()
    }
}
