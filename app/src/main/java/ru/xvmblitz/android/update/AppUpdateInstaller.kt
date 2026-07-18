package ru.xvmblitz.android.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.provider.Settings
import java.io.File
import java.io.InputStream
import java.io.RandomAccessFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient

class AppUpdateInstaller(
    private val context: Context,
    private val httpClient: OkHttpClient,
) {
    suspend fun downloadAndInstall(
        downloadUrl: String,
        onProgress: (Float) -> Unit,
    ) = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !context.packageManager.canRequestPackageInstalls()
        ) {
            val settingsIntent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                data = android.net.Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(settingsIntent)
            error("Разрешите установку из этого источника и повторите обновление")
        }

        val apkFile = File(context.cacheDir, "xvm-blitz-update.apk")
        if (apkFile.exists()) {
            apkFile.delete()
        }

        try {
            downloadApk(downloadUrl, apkFile, onProgress)
            validateApkFile(apkFile)
            installApkFile(apkFile)
            onProgress(1f)
        } catch (exception: Exception) {
            apkFile.delete()
            throw exception
        }
    }

    private fun downloadApk(
        downloadUrl: String,
        apkFile: File,
        onProgress: (Float) -> Unit,
    ) {
        GitHubReleaseDownloader.openDownloadResponse(httpClient, downloadUrl).use { response ->
            val body = response.body ?: error("Пустой ответ при скачивании обновления")
            val contentType = body.contentType()?.toString().orEmpty()
            if (contentType.contains("json", ignoreCase = true) ||
                contentType.contains("text/html", ignoreCase = true) ||
                contentType.contains("text/plain", ignoreCase = true)
            ) {
                val preview = body.string().take(180)
                error(
                    "Сервер вернул не APK (${contentType.ifBlank { "unknown" }}). " +
                        preview.ifBlank { "Проверьте download_url релиза." },
                )
            }

            val totalBytes = body.contentLength()
            body.byteStream().use { input ->
                apkFile.outputStream().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var downloaded = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) {
                            break
                        }
                        output.write(buffer, 0, read)
                        downloaded += read
                        if (totalBytes > 0) {
                            onProgress((downloaded.toFloat() / totalBytes.toFloat()).coerceIn(0f, 0.99f))
                        }
                    }
                }
            }
        }
    }

    private fun validateApkFile(apkFile: File) {
        if (!apkFile.exists() || apkFile.length() < MIN_APK_BYTES) {
            error("Скачанный файл слишком маленький (${apkFile.length()} байт) и не является APK")
        }

        RandomAccessFile(apkFile, "r").use { file ->
            val magic = ByteArray(4)
            val read = file.read(magic)
            if (read < 4 || magic[0] != 'P'.code.toByte() || magic[1] != 'K'.code.toByte()) {
                val preview = apkFile.inputStream().use { input ->
                    input.readPreview(180)
                }
                error(
                    "Скачанный файл не является APK (нет ZIP-сигнатуры). " +
                        preview.ifBlank { "Возможно, GitHub вернул JSON/HTML вместо файла." },
                )
            }
        }
    }

    private fun installApkFile(apkFile: File) {
        val packageInstaller = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
        }
        val sessionId = packageInstaller.createSession(params)
        val session = packageInstaller.openSession(sessionId)
        try {
            session.openWrite("xvm-blitz-update.apk", 0, apkFile.length()).use { output ->
                apkFile.inputStream().use { input ->
                    input.copyTo(output)
                    session.fsync(output)
                }
            }
            val callbackIntent = Intent(context, UpdateInstallReceiver::class.java).apply {
                action = UpdateInstallReceiver.ACTION_INSTALL_STATUS
            }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            val pendingIntent = PendingIntent.getBroadcast(context, sessionId, callbackIntent, flags)
            session.commit(pendingIntent.intentSender)
        } catch (exception: Exception) {
            session.abandon()
            throw exception
        } finally {
            session.close()
        }
    }

    private fun InputStream.readPreview(maxChars: Int): String {
        val bytes = readNBytesCompat(maxChars)
        return bytes.toString(Charsets.UTF_8).replace('\n', ' ').trim()
    }

    private fun InputStream.readNBytesCompat(maxBytes: Int): ByteArray {
        val buffer = ByteArray(maxBytes)
        var offset = 0
        while (offset < maxBytes) {
            val read = read(buffer, offset, maxBytes - offset)
            if (read < 0) {
                break
            }
            offset += read
        }
        return buffer.copyOf(offset)
    }

    companion object {
        private const val DEFAULT_BUFFER_SIZE = 8 * 1024
        private const val MIN_APK_BYTES = 64 * 1024L
    }
}
