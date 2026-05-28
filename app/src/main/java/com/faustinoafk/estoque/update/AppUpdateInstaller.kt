package com.faustinoafk.estoque.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

object AppUpdateInstaller {
    private val client = OkHttpClient()

    suspend fun downloadAndOpenInstaller(context: Context, apkUrl: String): Result<Unit> = withContext(Dispatchers.IO) {
        val cleanUrl = apkUrl.trim()
        if (cleanUrl.isBlank() || cleanUrl == "about:blank") {
            return@withContext Result.failure(IllegalArgumentException("Configure UPDATE_APK_URL antes de atualizar."))
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
            openUnknownAppsSettings(context)
            return@withContext Result.failure(IllegalStateException("Permita instalacoes deste app e toque em atualizar novamente."))
        }

        val request = Request.Builder().url(cleanUrl).get().build()
        val apkFile = File(context.cacheDir, "updates/estoque-update.apk")
        apkFile.parentFile?.mkdirs()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                return@withContext Result.failure(IllegalStateException("Download falhou: HTTP ${response.code}"))
            }

            val body = response.body ?: return@withContext Result.failure(IllegalStateException("Download sem conteudo."))
            apkFile.outputStream().use { output ->
                body.byteStream().use { input -> input.copyTo(output) }
            }
        }

        openInstaller(context, apkFile)
        Result.success(Unit)
    }

    private fun openUnknownAppsSettings(context: Context) {
        val intent = Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}")
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    private fun openInstaller(context: Context, apkFile: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
    }
}
