package com.heeseung.drivekip.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

data class AppUpdateInfo(
    val hasUpdate: Boolean,
    val latestVersionName: String = "",
    val latestVersionCode: Int = 0,
    val releaseNotes: String = "",
    val driveFileId: String? = null,
    val downloadUrl: String? = null,
    val fileSize: Long = 0L
)

class AppUpdateManager {
    private val gson = Gson()
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun checkForUpdate(
        driveService: GoogleDriveService,
        appSettings: AppSettings,
        currentVersionCode: Int,
        currentVersionName: String
    ): AppUpdateInfo = withContext(Dispatchers.IO) {
        // 1. Check custom Update URL if configured (e.g. GitHub Releases)
        val customUrl = appSettings.updateCheckUrl.trim()
        if (customUrl.isNotBlank()) {
            try {
                val info = checkFromUrl(customUrl, currentVersionCode, currentVersionName)
                if (info.hasUpdate) return@withContext info
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // 2. Check Google Drive work/_AppUpdate/
        try {
            val files = driveService.listAppUpdateFiles()
            if (files.isNotEmpty()) {
                // Check if update.json exists
                val jsonFile = files.find { it.name.equals("update.json", ignoreCase = true) }
                if (jsonFile != null) {
                    val content = driveService.readFileContent(jsonFile.id)
                    if (!content.isNullOrBlank()) {
                        val obj = gson.fromJson(content, JsonObject::class.java)
                        val vCode = if (obj.has("versionCode")) obj.get("versionCode").asInt else 0
                        val vName = if (obj.has("versionName")) obj.get("versionName").asString else ""
                        val notes = if (obj.has("releaseNotes")) obj.get("releaseNotes").asString else "최신 기능 및 버그 수정"
                        val apkName = if (obj.has("apkFileName")) obj.get("apkFileName").asString else ""
                        val directUrl = if (obj.has("downloadUrl")) obj.get("downloadUrl").asString else null

                        val apkDriveFile = files.find { it.name.equals(apkName, ignoreCase = true) || it.name.endsWith(".apk", ignoreCase = true) }

                        val isNewer = (vCode > currentVersionCode) || isSemanticVersionNewer(vName, currentVersionName)
                        if (isNewer) {
                            return@withContext AppUpdateInfo(
                                hasUpdate = true,
                                latestVersionName = vName,
                                latestVersionCode = vCode,
                                releaseNotes = notes,
                                driveFileId = apkDriveFile?.id,
                                downloadUrl = directUrl,
                                fileSize = apkDriveFile?.size ?: 0L
                            )
                        }
                    }
                }

                // If no update.json, find latest DriveKip-v*.apk in _AppUpdate
                val apkFiles = files.filter { it.name.endsWith(".apk", ignoreCase = true) }
                for (apk in apkFiles) {
                    val extractedVer = extractVersionFromApkName(apk.name)
                    if (extractedVer != null && isSemanticVersionNewer(extractedVer, currentVersionName)) {
                        return@withContext AppUpdateInfo(
                            hasUpdate = true,
                            latestVersionName = extractedVer,
                            latestVersionCode = currentVersionCode + 1,
                            releaseNotes = "Google Drive _AppUpdate 폴더에서 감지된 최신 릴리즈 (${apk.name})",
                            driveFileId = apk.id,
                            fileSize = apk.size ?: 0L
                        )
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        AppUpdateInfo(hasUpdate = false, latestVersionName = currentVersionName, latestVersionCode = currentVersionCode)
    }

    private fun checkFromUrl(url: String, currentVersionCode: Int, currentVersionName: String): AppUpdateInfo {
        val request = Request.Builder()
            .url(url)
            .addHeader("User-Agent", "DriveKip-Android-App")
            .addHeader("Accept", "application/vnd.github+json")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return AppUpdateInfo(hasUpdate = false)
            val body = response.body?.string() ?: return AppUpdateInfo(hasUpdate = false)
            val json = gson.fromJson(body, JsonObject::class.java)

            // Support GitHub Releases API or generic update JSON
            val tag = if (json.has("tag_name")) json.get("tag_name").asString else if (json.has("versionName")) json.get("versionName").asString else ""
            val cleanVer = tag.removePrefix("v").trim()
            val notes = if (json.has("body")) json.get("body").asString else if (json.has("releaseNotes")) json.get("releaseNotes").asString else "업데이트 내용이 포함되어 있습니다."

            var apkUrl: String? = null
            if (json.has("assets") && json.get("assets").isJsonArray) {
                val assets = json.getAsJsonArray("assets")
                for (elem in assets) {
                    val aObj = elem.asJsonObject
                    val aName = aObj.get("name").asString
                    if (aName.endsWith(".apk", ignoreCase = true)) {
                        apkUrl = aObj.get("browser_download_url").asString
                        break
                    }
                }
            } else if (json.has("downloadUrl")) {
                apkUrl = json.get("downloadUrl").asString
            }

            if (isSemanticVersionNewer(cleanVer, currentVersionName) && !apkUrl.isNullOrBlank()) {
                return AppUpdateInfo(
                    hasUpdate = true,
                    latestVersionName = cleanVer,
                    latestVersionCode = currentVersionCode + 1,
                    releaseNotes = notes,
                    downloadUrl = apkUrl
                )
            }
        }
        return AppUpdateInfo(hasUpdate = false)
    }

    suspend fun downloadUpdate(
        context: Context,
        updateInfo: AppUpdateInfo,
        driveService: GoogleDriveService,
        onProgress: (Float) -> Unit
    ): File = withContext(Dispatchers.IO) {
        val destFile = File(context.cacheDir, "DriveKip-update.apk")
        if (destFile.exists()) destFile.delete()

        if (updateInfo.driveFileId != null) {
            driveService.downloadFileWithProgress(updateInfo.driveFileId, destFile, onProgress)
        } else if (!updateInfo.downloadUrl.isNullOrBlank()) {
            val request = Request.Builder()
                .url(updateInfo.downloadUrl)
                .addHeader("User-Agent", "DriveKip-Android-App")
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw Exception("다운로드 실패: HTTP ${response.code}")
                val body = response.body ?: throw Exception("다운로드 내용 없음")
                val total = body.contentLength()
                val inputStream = body.byteStream()
                FileOutputStream(destFile).use { out ->
                    val buffer = ByteArray(8192)
                    var read: Int
                    var readTotal = 0L
                    while (inputStream.read(buffer).also { read = it } != -1) {
                        out.write(buffer, 0, read)
                        readTotal += read
                        if (total > 0) {
                            onProgress((readTotal.toFloat() / total.toFloat()).coerceIn(0f, 1f))
                        }
                    }
                    out.flush()
                }
            }
        } else {
            throw Exception("다운로드 가능한 APK 경로가 없습니다.")
        }

        destFile
    }

    fun canInstallPackages(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    fun requestInstallPermission(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                data = Uri.parse("package:${context.packageName}")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        }
    }

    fun triggerApkInstallation(context: Context, apkFile: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "com.heeseung.drivekip.fileprovider",
            apkFile
        )
        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
        context.startActivity(installIntent)
    }

    private fun extractVersionFromApkName(name: String): String? {
        val pattern = Pattern.compile("(?i)drivekip[_-]v?([0-9]+(?:\\.[0-9]+)+)")
        val matcher = pattern.matcher(name)
        if (matcher.find()) {
            return matcher.group(1)
        }
        return null
    }

    private fun isSemanticVersionNewer(remote: String, current: String): Boolean {
        if (remote.isBlank() || current.isBlank()) return false
        val rParts = remote.split(".").mapNotNull { it.toIntOrNull() }
        val cParts = current.split(".").mapNotNull { it.toIntOrNull() }
        val maxLen = maxOf(rParts.size, cParts.size)
        for (i in 0 until maxLen) {
            val r = rParts.getOrElse(i) { 0 }
            val c = cParts.getOrElse(i) { 0 }
            if (r > c) return true
            if (r < c) return false
        }
        return false
    }
}
