package com.moto.gameturbo

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import rikka.shizuku.Shizuku

class ShizukuHelper(private val context: Context) {

    private var shellService: IShizukuShellService? = null
    private var isBound = false

    private val userServiceArgs = Shizuku.UserServiceArgs(
        ComponentName(context.packageName, ShizukuShellService::class.java.name)
    )
        .daemon(false)
        .processNameSuffix("turbo_shell")
        .debuggable(false)
        .version(1)

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            shellService = IShizukuShellService.Stub.asInterface(service)
            isBound = true
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            shellService = null
            isBound = false
        }
    }

    fun isShizukuReady(): Boolean {
        return try {
            Shizuku.pingBinder() &&
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (_: Exception) {
            false
        }
    }

    fun requestPermission(requestCode: Int = 1001) {
        try {
            if (Shizuku.pingBinder() && Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                Shizuku.requestPermission(requestCode)
            }
        } catch (_: Exception) {
        }
    }

    fun bindService() {
        if (isShizukuReady() && !isBound) {
            try {
                Shizuku.bindUserService(userServiceArgs, connection)
            } catch (_: Exception) {
            }
        }
    }

    fun unbindService() {
        if (isBound) {
            try {
                Shizuku.unbindUserService(userServiceArgs, connection, true)
                isBound = false
            } catch (_: Exception) {
            }
        }
    }

    fun executeCommand(cmd: String): String {
        if (!isBound) bindService()
        return try {
            shellService?.runCommand(cmd) ?: "Shizuku no vinculado"
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }

    fun setRefreshRate(hz: Float) {
        executeCommand("settings put system peak_refresh_rate $hz")
        executeCommand("settings put system min_refresh_rate $hz")
    }

    fun deepCleanMemory() {
        executeCommand("am kill-all")
        executeCommand("pm trim-caches 999G")
    }
}
