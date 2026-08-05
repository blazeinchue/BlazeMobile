package com.example.engine

data class InstalledAppInfo(
    val appName: String,
    val packageName: String,
    val category: String,
    val iconResName: String,
    val isSystemApp: Boolean = true
)

class AppLauncherEngine {

    fun getInstalledApps(): List<InstalledAppInfo> {
        return listOf(
            InstalledAppInfo("Blaze Bridge Hub", "com.aistudio.blazeagentbridge.qvmp", "AI Tools", "ic_launcher", true),
            InstalledAppInfo("Google Calendar", "com.google.android.calendar", "Productivity", "ic_calendar", true),
            InstalledAppInfo("Google Photos", "com.google.android.apps.photos", "Media", "ic_photos", true),
            InstalledAppInfo("Files by Google", "com.google.android.apps.nbu.files", "Tools", "ic_files", true),
            InstalledAppInfo("Android Settings", "com.android.settings", "System", "ic_settings", true),
            InstalledAppInfo("Camera", "com.android.camera2", "Media", "ic_camera", true),
            InstalledAppInfo("Chrome Browser", "com.android.chrome", "Network", "ic_chrome", false),
            InstalledAppInfo("Gmail", "com.google.android.gm", "Communication", "ic_gmail", false),
            InstalledAppInfo("Slack", "com.Slack", "Business", "ic_slack", false)
        )
    }

    fun launchApp(packageNameOrName: String): String {
        val app = getInstalledApps().find {
            it.packageName.contains(packageNameOrName, ignoreCase = true) ||
            it.appName.contains(packageNameOrName, ignoreCase = true)
        }
        return if (app != null) {
            "Launched application '${app.appName}' (${app.packageName}) successfully."
        } else {
            "App containing '$packageNameOrName' is not installed on this Android device."
        }
    }
}
