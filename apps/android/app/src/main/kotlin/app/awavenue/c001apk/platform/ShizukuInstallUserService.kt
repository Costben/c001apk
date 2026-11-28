package app.awavenue.c001apk.platform

import android.content.Context
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.annotation.Keep
import kotlin.system.exitProcess

/**
 * Privileged Shizuku UserService that streams an APK into `cmd package install` / `pm install`
 * inside Shizuku's UID (shell/root) without requiring hidden Android framework AIDL stubs.
 */
@Keep
class ShizukuInstallUserService() : IShizukuInstallService.Stub() {
    @Suppress("unused")
    constructor(@Suppress("UNUSED_PARAMETER") context: Context) : this()

    override fun destroy() {
        exitProcess(0)
    }

    override fun installApk(pfd: ParcelFileDescriptor, size: Long): String {
        val commands = buildInstallCommand(size)
        return try {
            val process = ProcessBuilder(commands)
                .redirectErrorStream(true)
                .start()
            ParcelFileDescriptor.AutoCloseInputStream(pfd).use { input ->
                process.outputStream.use { output ->
                    val buffer = ByteArray(16 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                    }
                    output.flush()
                }
            }
            val outputText = process.inputStream.bufferedReader().use { it.readText() }.trim()
            val exitCode = process.waitFor()
            if (exitCode == 0 && outputText.contains("Success", ignoreCase = true)) {
                "SUCCESS"
            } else {
                outputText.ifEmpty { "pm install exited with code $exitCode" }
            }
        } catch (t: Throwable) {
            runCatching { pfd.close() }
            t.message ?: t.javaClass.simpleName
        }
    }

    private fun buildInstallCommand(size: Long): List<String> {
        val base = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            mutableListOf("cmd", "package", "install", "-r", "-d")
        } else {
            mutableListOf("pm", "install", "-r", "-d")
        }
        if (size > 0L) {
            base += listOf("-S", size.toString())
        }
        return base
    }
}
