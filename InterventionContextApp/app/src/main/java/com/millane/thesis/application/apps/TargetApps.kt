package com.millane.thesis.application.apps

object TargetApps {
    const val INSTAGRAM = "com.instagram.android"
    const val TIKTOK = "com.zhiliaoapp.musically"

    val all = setOf(INSTAGRAM, TIKTOK)

    fun displayName(packageName: String): String = when (packageName) {
        INSTAGRAM -> "Instagram"
        TIKTOK -> "TikTok"
        else -> packageName
    }
}