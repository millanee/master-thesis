package com.millane.thesis.application

import android.app.Application
import com.google.firebase.FirebaseApp
import com.millane.thesis.application.study.SessionManager

class ThesisApp : Application() {
    val sessionManager: SessionManager by lazy { SessionManager(applicationContext) }

    override fun onCreate() {
        super.onCreate()
        FirebaseApp.initializeApp(this)
    }
}