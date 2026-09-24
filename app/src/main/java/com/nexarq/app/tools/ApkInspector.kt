package com.nexarq.app.tools

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class ApkInfo(
    val packageName: String?,
    val label: String?,
    val versionName: String?,
    val versionCode: Long?,
    val minSdk: Int?,
    val targetSdk: Int?,
    val requestedPermissions: List<String>,
    val installLocation: String?,
    val icon: Drawable?,
    val fileSize: Long,
    val supportedAbis: Set<String>,
)

/**
 * APK inspector built on the platform PackageManager (no security bypass — read-only).
 */
object ApkInspector {

    @Suppress("DEPRECATION")
    suspend fun inspect(context: Context, path: String): ApkInfo = withContext(Dispatchers.IO) {
        val file = File(path)
        val pm = context.packageManager
        val flags = PackageManager.GET_META_DATA or
            PackageManager.GET_PERMISSIONS or
            PackageManager.GET_SIGNATURES or
            PackageManager.GET_CONFIGURATIONS
        val info = pm.getPackageArchiveInfo(path, flags)
        if (info == null) {
            ApkInfo(null, null, null, null, null, null, emptyList(), null, null, file.length(), emptySet())
        } else {
            val appInfo = info.applicationInfo
            if (appInfo != null) {
                appInfo.sourceDir = path
                appInfo.publicSourceDir = path
            }
            val label = appInfo?.let { a ->
                runCatching { pm.getApplicationLabel(a).toString() }.getOrNull()
            }
            val icon = appInfo?.let { a ->
                runCatching { pm.getApplicationIcon(a) }.getOrNull()
            }
            val requested = info.requestedPermissions?.toList() ?: emptyList()
            val abis = mutableSetOf<String>()
            appInfo?.let { a ->
                val nativeDir = File(path.substringBeforeLast('/'), "lib")
                nativeDir.listFiles()?.forEach { abis.add(it.name) }
                if (a.splitSourceDirs != null) {
                    // nothing extra needed; ABI detection below via lib folder is best-effort
                }
            }
            ApkInfo(
                packageName = info.packageName,
                label = label,
                versionName = info.versionName,
                versionCode = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong(),
                minSdk = runCatching { appInfo?.minSdkVersion }.getOrNull(),
                targetSdk = runCatching { appInfo?.targetSdkVersion }.getOrNull(),
                requestedPermissions = requested,
                installLocation = installLocationName(info.installLocation),
                icon = icon,
                fileSize = file.length(),
                supportedAbis = abis,
            )
        }
    }

    private fun installLocationName(code: Int?): String? = when (code) {
        0 -> "Auto"
        1 -> "Internal only"
        2 -> "Prefer external"
        else -> null
    }
}
