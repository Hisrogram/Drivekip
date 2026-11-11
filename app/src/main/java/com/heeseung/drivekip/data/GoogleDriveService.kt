package com.heeseung.drivekip.data

import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.Locale
import java.util.concurrent.TimeUnit

data class StorageQuotaInfo(
    val limitBytes: Long,
    val usageBytes: Long
) {
    val freeBytes: Long
        get() = (limitBytes - usageBytes).coerceAtLeast(0L)

    val formattedFree: String
        get() = formatBytes(freeBytes)

    val formattedTotal: String
        get() = formatBytes(limitBytes)

    val formattedUsage: String
        get() = formatBytes(usageBytes)

    val usagePercent: Int
        get() = if (limitBytes > 0) ((usageBytes.toDouble() / limitBytes.toDouble()) * 100).toInt() else 0

    private fun formatBytes(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val gb = bytes / (1024.0 * 1024.0 * 1024.0)
        return if (gb >= 1.0) {
            String.format(Locale.KOREA, "%.1f GB", gb)
        } else {
            val mb = bytes / (1024.0 * 1024.0)
            String.format(Locale.KOREA, "%.1f MB", mb)
        }
    }
}

class GoogleDriveService(
    private var tokenProvider: () -> String?,
    private var tokenRefresher: (suspend () -> String?)? = null
) {

    private val gson = Gson()
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()

    private var cachedWorkFolderId: String? = null

    private fun getAuthToken(): String {
        return tokenProvider() ?: throw IllegalStateException("Google 계정 인증 토큰이 필요합니다.")
    }

    private fun newRequestBuilder(url: String, token: String = getAuthToken()): Request.Builder {
        return Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $token")
    }

    /**
     * Executes request with automatic 401 token refresh retry
     */
    private suspend fun executeWithRetry(
        url: String,
        buildRequest: (token: String) -> Request
    ): Response = withContext(Dispatchers.IO) {
        val initialToken = getAuthToken()
        val firstResponse = client.newCall(buildRequest(initialToken)).execute()

        if (firstResponse.code == 401 && tokenRefresher != null) {
            firstResponse.close()
            val freshToken = tokenRefresher?.invoke()
            if (!freshToken.isNullOrBlank()) {
                return@withContext client.newCall(buildRequest(freshToken)).execute()
            }
        }
        firstResponse
    }

    suspend fun getStorageQuota(): StorageQuotaInfo = withContext(Dispatchers.IO) {
        val url = "https://www.googleapis.com/drive/v3/about?fields=storageQuota"
        val response = executeWithRetry(url) { token ->
            newRequestBuilder(url, token).get().build()
        }

        response.use { res ->
            if (!res.isSuccessful) throw IOException("드라이브 용량 조회 실패: ${res.code}")
            val body = res.body?.string() ?: ""
            val json = gson.fromJson(body, JsonObject::class.java)
            val quota = json.getAsJsonObject("storageQuota")
            val limit = quota?.get("limit")?.asLong ?: (15L * 1024 * 1024 * 1024)
            val usage = quota?.get("usage")?.asLong ?: 0L
            StorageQuotaInfo(limitBytes = limit, usageBytes = usage)
        }
    }

    suspend fun getOrCreateWorkFolder(): String = withContext(Dispatchers.IO) {
        cachedWorkFolderId?.let { return@withContext it }

        val query = "name = 'work' and mimeType = 'application/vnd.google-apps.folder' and trashed = false and 'root' in parents"
        val existingId = findFolderId(query)
        if (existingId != null) {
            cachedWorkFolderId = existingId
            return@withContext existingId
        }

        val createdId = createFolder("work", parentId = null)
        cachedWorkFolderId = createdId
        createdId
    }

    suspend fun getOrCreateProjectFolder(projectName: String): String = withContext(Dispatchers.IO) {
        val workId = getOrCreateWorkFolder()
        val query = "name = '${projectName.replace("'", "\\'")}' and mimeType = 'application/vnd.google-apps.folder' and trashed = false and '${workId}' in parents"
        val existingId = findFolderId(query)
        if (existingId != null) {
            return@withContext existingId
        }

        createFolder(projectName, parentId = workId)
    }

    suspend fun listProjects(): List<DriveItem> = withContext(Dispatchers.IO) {
        val workId = getOrCreateWorkFolder()
        val query = "mimeType = 'application/vnd.google-apps.folder' and trashed = false and '${workId}' in parents"
        val url = "https://www.googleapis.com/drive/v3/files?q=${query.urlEncode()}&fields=files(id,name,mimeType,createdTime,size,thumbnailLink,webViewLink)&orderBy=name"

        val response = executeWithRetry(url) { token ->
            newRequestBuilder(url, token).get().build()
        }
        response.use { res ->
            if (!res.isSuccessful) throw IOException("프로젝트 목록 조회 실패: ${res.code}")
            val body = res.body?.string() ?: ""
            parseDriveItems(body)
        }
    }

    suspend fun renameProjectFolder(folderId: String, newName: String) = withContext(Dispatchers.IO) {
        val url = "https://www.googleapis.com/drive/v3/files/${folderId}"
        val patchBody = JsonObject().apply {
            addProperty("name", newName)
        }.toString().toRequestBody("application/json; charset=UTF-8".toMediaType())

        val response = executeWithRetry(url) { token ->
            newRequestBuilder(url, token).patch(patchBody).build()
        }
        response.use { res ->
            if (!res.isSuccessful) throw IOException("프로젝트 이름 변경 실패: ${res.code}")
        }
    }

    suspend fun listMediaFiles(projectFolderId: String): List<DriveItem> = withContext(Dispatchers.IO) {
        if (projectFolderId.isBlank()) return@withContext emptyList()
        val query = "'${projectFolderId}' in parents and trashed = false and mimeType != 'application/vnd.google-apps.folder'"
        val url = "https://www.googleapis.com/drive/v3/files?q=${query.urlEncode()}&fields=files(id,name,mimeType,createdTime,size,thumbnailLink,webViewLink)&orderBy=createdTime desc&pageSize=200"

        val response = executeWithRetry(url) { token ->
            newRequestBuilder(url, token).get().build()
        }
        response.use { res ->
            if (!res.isSuccessful) throw IOException("미디어 목록 조회 실패: ${res.code}")
            val body = res.body?.string() ?: ""
            parseDriveItems(body)
        }
    }

    suspend fun listAllWorkMediaFiles(): List<Pair<DriveItem, String>> = withContext(Dispatchers.IO) {
        val projects = listProjects()
        val allList = mutableListOf<Pair<DriveItem, String>>()
        for (proj in projects) {
            try {
                val files = listMediaFiles(proj.id)
                for (f in files) {
                    allList.add(Pair(f, proj.name))
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        allList.sortedByDescending { it.first.createdTime }
    }

    suspend fun uploadMediaFile(
        projectFolderId: String,
        file: File,
        mimeType: String
    ): DriveItem = withContext(Dispatchers.IO) {
        val metadataJson = JsonObject().apply {
            addProperty("name", file.name)
            add("parents", gson.toJsonTree(listOf(projectFolderId)))
        }.toString()

        val multipartBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "metadata",
                null,
                metadataJson.toRequestBody("application/json; charset=UTF-8".toMediaType())
            )
            .addFormDataPart(
                "file",
                file.name,
                file.asRequestBody(mimeType.toMediaType())
            )
            .build()

        val url = "https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart&fields=id,name,mimeType,createdTime,size,thumbnailLink,webViewLink"
        val response = executeWithRetry(url) { token ->
            newRequestBuilder(url, token).post(multipartBody).build()
        }

        response.use { res ->
            if (!res.isSuccessful) {
                val errorBody = res.body?.string()
                throw IOException("업로드 실패: ${res.code} $errorBody")
            }
            val body = res.body?.string() ?: ""
            gson.fromJson(body, DriveItem::class.java)
        }
    }

    suspend fun trashFile(fileId: String) = withContext(Dispatchers.IO) {
        val url = "https://www.googleapis.com/drive/v3/files/${fileId}"
        val patchBody = JsonObject().apply {
            addProperty("trashed", true)
        }.toString().toRequestBody("application/json; charset=UTF-8".toMediaType())

        val response = executeWithRetry(url) { token ->
            newRequestBuilder(url, token).patch(patchBody).build()
        }
        response.use { res ->
            if (!res.isSuccessful) throw IOException("삭제(휴지통 이동) 실패: ${res.code}")
        }
    }

    suspend fun downloadFileTo(fileId: String, destinationFile: File) = withContext(Dispatchers.IO) {
        val url = "https://www.googleapis.com/drive/v3/files/${fileId}?alt=media"
        val response = executeWithRetry(url) { token ->
            newRequestBuilder(url, token).get().build()
        }
        response.use { res ->
            if (!res.isSuccessful) throw IOException("파일 다운로드 실패: ${res.code}")
            val inputStream = res.body?.byteStream() ?: throw IOException("응답 바디 없음")
            FileOutputStream(destinationFile).use { outputStream ->
                inputStream.copyTo(outputStream)
            }
        }
    }

    suspend fun getOrCreateAppUpdateFolder(): String = withContext(Dispatchers.IO) {
        val workId = getOrCreateWorkFolder()
        val query = "name = '_AppUpdate' and mimeType = 'application/vnd.google-apps.folder' and trashed = false and '${workId}' in parents"
        val existingId = findFolderId(query)
        if (existingId != null) return@withContext existingId
        createFolder("_AppUpdate", workId)
    }

    suspend fun listAppUpdateFiles(): List<DriveItem> = withContext(Dispatchers.IO) {
        val updateFolderId = getOrCreateAppUpdateFolder()
        val query = "trashed = false and '${updateFolderId}' in parents"
        val url = "https://www.googleapis.com/drive/v3/files?q=${query.urlEncode()}&fields=files(id,name,mimeType,createdTime,size,webViewLink)&orderBy=createdTime desc"
        val response = executeWithRetry(url) { token ->
            newRequestBuilder(url, token).get().build()
        }
        response.use { res ->
            if (!res.isSuccessful) return@withContext emptyList()
            val body = res.body?.string() ?: return@withContext emptyList()
            val json = gson.fromJson(body, JsonObject::class.java)
            val filesArray = json.getAsJsonArray("files") ?: return@withContext emptyList()
            filesArray.map { gson.fromJson(it, DriveItem::class.java) }
        }
    }

    suspend fun readFileContent(fileId: String): String? = withContext(Dispatchers.IO) {
        val url = "https://www.googleapis.com/drive/v3/files/${fileId}?alt=media"
        val response = executeWithRetry(url) { token ->
            newRequestBuilder(url, token).get().build()
        }
        response.use { res ->
            if (!res.isSuccessful) return@withContext null
            res.body?.string()
        }
    }

    suspend fun downloadFileWithProgress(
        fileId: String,
        destinationFile: File,
        onProgress: (Float) -> Unit
    ) = withContext(Dispatchers.IO) {
        val url = "https://www.googleapis.com/drive/v3/files/${fileId}?alt=media"
        val response = executeWithRetry(url) { token ->
            newRequestBuilder(url, token).get().build()
        }
        response.use { res ->
            if (!res.isSuccessful) throw IOException("파일 다운로드 실패: ${res.code}")
            val body = res.body ?: throw IOException("응답 바디 없음")
            val totalBytes = body.contentLength()
            val inputStream = body.byteStream()
            FileOutputStream(destinationFile).use { outputStream ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                var readSoFar = 0L
                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                    readSoFar += bytesRead
                    if (totalBytes > 0) {
                        onProgress((readSoFar.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f))
                    }
                }
                outputStream.flush()
            }
        }
    }

    private suspend fun findFolderId(query: String): String? = withContext(Dispatchers.IO) {
        val url = "https://www.googleapis.com/drive/v3/files?q=${query.urlEncode()}&fields=files(id)&pageSize=1"
        val response = executeWithRetry(url) { token ->
            newRequestBuilder(url, token).get().build()
        }
        response.use { res ->
            if (!res.isSuccessful) return@withContext null
            val body = res.body?.string() ?: return@withContext null
            val json = gson.fromJson(body, JsonObject::class.java)
            val files = json.getAsJsonArray("files")
            if (files != null && files.size() > 0) {
                return@withContext files.get(0).asJsonObject.get("id").asString
            }
        }
        null
    }

    private suspend fun createFolder(name: String, parentId: String?): String = withContext(Dispatchers.IO) {
        val metadata = JsonObject().apply {
            addProperty("name", name)
            addProperty("mimeType", "application/vnd.google-apps.folder")
            if (parentId != null) {
                add("parents", gson.toJsonTree(listOf(parentId)))
            }
        }.toString()

        val url = "https://www.googleapis.com/drive/v3/files?fields=id"
        val response = executeWithRetry(url) { token ->
            newRequestBuilder(url, token)
                .post(metadata.toRequestBody("application/json; charset=UTF-8".toMediaType()))
                .build()
        }

        response.use { res ->
            if (!res.isSuccessful) {
                val err = res.body?.string()
                throw IOException("폴더 생성 실패 ($name): ${res.code} $err")
            }
            val body = res.body?.string() ?: throw IOException("응답 바디 없음")
            val json = gson.fromJson(body, JsonObject::class.java)
            json.get("id").asString
        }
    }

    private fun parseDriveItems(jsonStr: String): List<DriveItem> {
        val json = gson.fromJson(jsonStr, JsonObject::class.java)
        val filesArray = json.getAsJsonArray("files") ?: return emptyList()
        val list = mutableListOf<DriveItem>()
        for (element in filesArray) {
            val item = gson.fromJson(element, DriveItem::class.java)
            list.add(item)
        }
        return list
    }

    private fun String.urlEncode(): String {
        return java.net.URLEncoder.encode(this, "UTF-8")
    }
}
