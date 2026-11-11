package com.heeseung.drivekip.data

import android.content.Context
import android.content.SharedPreferences

enum class CameraPreference(val key: String, val label: String, val description: String) {
    ASK_EVERY_TIME("ask", "매번 선택 (바텀시트)", "촬영을 누를 때마다 어떤 카메라로 촬영할지 선택"),
    NATIVE_APP("native", "갤럭시 기본 카메라 앱", "삼성 100배 스페이스 줌, 200MP, 전문가 모드 풀 활용"),
    CAMERAX("camerax", "앱 내 카메라 (CameraX)", "앱 내 실시간 뷰파인더 및 빠른 촬영")
}

class AppSettings(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("drivekip_settings", Context.MODE_PRIVATE)

    var cameraPreference: CameraPreference
        get() {
            val key = prefs.getString("camera_pref", CameraPreference.ASK_EVERY_TIME.key)
            return CameraPreference.values().find { it.key == key } ?: CameraPreference.ASK_EVERY_TIME
        }
        set(value) {
            prefs.edit().putString("camera_pref", value.key).apply()
        }

    var updateCheckUrl: String
        get() = prefs.getString("update_check_url", DEFAULT_GITHUB_RELEASE_URL) ?: DEFAULT_GITHUB_RELEASE_URL
        set(value) {
            prefs.edit().putString("update_check_url", value).apply()
        }

    companion object {
        const val DEFAULT_GITHUB_RELEASE_URL = "https://api.github.com/repos/Hisrogram/Drivekip/releases/latest"
    }

    var isWifiOnly: Boolean
        get() = prefs.getBoolean("wifi_only", false)
        set(value) = prefs.edit().putBoolean("wifi_only", value).apply()

    var isDefaultWatermark: Boolean
        get() = prefs.getBoolean("default_watermark", true)
        set(value) = prefs.edit().putBoolean("default_watermark", value).apply()
}
