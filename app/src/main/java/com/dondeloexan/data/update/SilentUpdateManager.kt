package com.dondeloexan.data.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import io.ktor.client.HttpClient
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.utils.io.jvm.javaio.copyTo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class SilentUpdateManager(private val context: Context) {

    private val client = HttpClient()
    private val updateDir: File
        get() = File(context.cacheDir, "updates").also { it.mkdirs() }

    suspend fun downloadAndInstall(downloadUrl: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val apkFile = File(updateDir, "update.apk")
            if (apkFile.exists()) apkFile.delete()

            client.prepareGet(downloadUrl).execute { response ->
                val channel = response.bodyAsChannel()
                FileOutputStream(apkFile).use { output ->
                    channel.copyTo(output)
                }
            }

            installApk(apkFile)
        }
    }

    private fun installApk(apkFile: File) {
        if (Build.VERSION.SDK_INT >= 31) {
            silentInstall(apkFile)
        } else {
            fallbackInstall(apkFile)
        }
    }

    private fun silentInstall(apkFile: File) {
        val packageInstaller = context.packageManager.packageInstaller
        val sessionParams = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        sessionParams.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)

        val sessionId = packageInstaller.createSession(sessionParams)
        val session = packageInstaller.openSession(sessionId)

        try {
            apkFile.inputStream().use { input ->
                session.openWrite("base.apk", 0, apkFile.length()).use { output ->
                    input.copyTo(output, 8192)
                    session.fsync(output)
                }
            }

            val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
                ?: Intent()
            val pendingIntent = PendingIntent.getActivity(
                context, 0, launchIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            session.commit(pendingIntent.intentSender)
        } catch (e: Exception) {
            session.abandon()
            fallbackInstall(apkFile)
        }
    }

    private fun fallbackInstall(apkFile: File) {
        val apkUri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
    }
}
