package com.moto.gameturbo

import java.io.BufferedReader
import java.io.InputStreamReader
import kotlin.system.exitProcess

class ShizukuShellService : IShizukuShellService.Stub() {

    override fun runCommand(command: String): String {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val output = StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                output.append(line).append("\n")
            }
            process.waitFor()
            output.toString().trim()
        } catch (e: Exception) {
            "ERROR: ${e.message}"
        }
    }

    override fun destroy() {
        exitProcess(0)
    }
}
