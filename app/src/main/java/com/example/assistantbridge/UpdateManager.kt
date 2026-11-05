package com.example.assistantbridge

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

object UpdateManager {
    private const val RELEASE_API = "https://api.github.com/repos/a025803922-lgtm/app/releases/latest"
    fun check(activity: Activity, status: (String) -> Unit) {
        status("Checking GitHub for updates…")
        thread {
            try {
                val connection = URL(RELEASE_API).openConnection() as HttpURLConnection
                connection.connectTimeout = 15000
                connection.readTimeout = 15000
                connection.setRequestProperty("Accept", "application/vnd.github+json")
                connection.setRequestProperty("User-Agent", "AssistantBridge-Android")
                val release = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
                connection.disconnect()
                val tag = release.optString("tag_name").removePrefix("v")
                val assets = release.getJSONArray("assets")
                var apkUrl: String? = null
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    if (asset.optString("name").endsWith(".apk", true)) { apkUrl = asset.optString("browser_download_url"); break }
                }
                if (apkUrl.isNullOrBlank()) {
                    activity.runOnUiThread { status("No APK is attached to the latest GitHub release.") }
                    return@thread
                }
                if (tag.isBlank() || tag == BuildConfig.VERSION_NAME) {
                    activity.runOnUiThread { status("You have the latest version (${BuildConfig.VERSION_NAME}).") }
                    return@thread
                }
                activity.runOnUiThread { status("Version $tag is available. Downloading…") }
                val download = URL(apkUrl).openConnection() as HttpURLConnection
                download.connectTimeout = 20000
                download.readTimeout = 60000
                val apk = File(activity.cacheDir, "assistant-bridge-update.apk")
                download.inputStream.use { input -> apk.outputStream().use { output -> input.copyTo(output) } }
                download.disconnect()
                activity.runOnUiThread { status("Download complete. Opening Android installer…"); install(activity, apk) }
            } catch (e: Exception) {
                activity.runOnUiThread { status("Update check failed: ${e.message}") }
            }
        }
    }
    private fun install(activity: Activity, apk: File) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !activity.packageManager.canRequestPackageInstalls()) {
            activity.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${activity.packageName}")))
            return
        }
        val uri = FileProvider.getUriForFile(activity, "${activity.packageName}.fileprovider", apk)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        activity.startActivity(intent)
    }
}
