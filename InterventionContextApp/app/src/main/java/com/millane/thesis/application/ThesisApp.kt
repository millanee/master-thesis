package com.millane.thesis.application

import android.app.Application
import com.google.firebase.FirebaseApp

class ThesisApp : Application() {
    override fun onCreate() {
        super.onCreate()
        FirebaseApp.initializeApp(this)
    }
}