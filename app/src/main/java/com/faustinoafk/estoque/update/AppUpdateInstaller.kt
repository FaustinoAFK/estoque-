package com.faustinoafk.estoque.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.faustinoafk.estoque.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File

object AppUpdateInstaller {
    private val client = OkHttpClient()
    private const val LATEST_RELEASE_URL = "https://api.github.com/repos/FaustinoAFK/estoque-/releases/latest"

    suspend fun downloadAndOpenInstaller(context: Context, apkUrl: String): Result<Unit> = withContext(Dispatchers.IO) {
        val cleanUrl = apkUrl.trim()
        if (cleanUrl.isBlank() || cleanUrl == "about:blank") {
            return@withContext Result.failure(IllegalArgumentException("Configure UPDATE_APK_URL antes de atualizar."))
        }

        val latestVersion = fetchLatestVersion()
            ?: return@withContext Result.failure(IllegalStateException("Não foi possível verificar a versão mais recente."))

        if (!isNewerVersion(latestVersion, BuildConfig.VERSION_NAME)) {
            return@withContext Result.failure(IllegalStateException("Você já está na versão mais recente (${BuildConfig.VERSION_NAME})."))
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
            openUnknownAppsSettings(context)
            return@withContext Result.failure(IllegalStateException("Permita instalações deste app e toque em atualizar novamente."))
        }

        val request = Request.Builder().url(cleanUrl).get().build()
        val apkFile = File(context.cacheDir, "updates/estoque-update.apk")
        apkFile.parentFile?.mkdirs()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                return@withContext Result.failure(IllegalStateException("Download falhou: HTTP ${response.code}"))
            }

            val body = response.body ?: return@withContext Result.failure(IllegalStateException("Download sem conteúdo."))
            apkFile.outputStream().use { output ->
                body.byteStream().use { input -> input.copyTo(output) }
            }
        }

        openInstaller(context, apkFile)
        Result.success(Unit)
    }

    private fun fetchLatestVersion(): String? {
        val request = Request.Builder()
            .url(LATEST_RELEASE_URL)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "StockSync-Pro")
            .get()
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body?.string() ?: return null
                JSONObject(body).optString("tag_name").takeIf { it.isNotBlank() }
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun normalizeVersion(version: String): String {
        return version.trim().removePrefix("v").removePrefix("V")
    }

    private fun isNewerVersion(latestVersion: String, currentVersion: String): Boolean {
        val latestParts = normalizeVersion(latestVersion).split(".").map { it.toIntOrNull() ?: 0 }
        val currentParts = normalizeVersion(currentVersion).split(".").map { it.toIntOrNull() ?: 0 }
        val maxSize = maxOf(latestParts.size, currentParts.size)

        for (index in 0 until maxSize) {
            val latest = latestParts.getOrElse(index) { 0 }
            val current = currentParts.getOrElse(index) { 0 }
            if (latest > current) return true
            if (latest < current) return false
        }

        return false
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
