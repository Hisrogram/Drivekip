package com.heeseung.drivekip.data

data class DriveItem(
    val id: String,
    val name: String,
    val mimeType: String,
    val createdTime: String? = null,
    val size: Long? = null,
    val thumbnailLink: String? = null,
    val webViewLink: String? = null,
    val isFolder: Boolean = mimeType == "application/vnd.google-apps.folder"
) {
    val isVideo: Boolean
        get() = mimeType.startsWith("video/")

    val formattedDate: String
        get() {
            if (createdTime.isNullOrBlank()) return "날짜 정보 없음"
            return try {
                // ISO 8601: 2026-11-11T09:15:00.000Z
                createdTime.substring(0, 10)
            } catch (e: Exception) {
                createdTime
            }
        }
}
