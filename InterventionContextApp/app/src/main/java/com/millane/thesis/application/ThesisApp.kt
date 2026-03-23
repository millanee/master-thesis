package com.millane.thesis.application

import android.app.Application
import com.google.firebase.FirebaseApp
import com.google.android.libraries.places.api.Places
import com.millane.thesis.application.study.SessionManager

class ThesisApp : Application() {
    val sessionManager: SessionManager by lazy { SessionManager(applicationContext) }

    override fun onCreate() {
        super.onCreate()
        FirebaseApp.initializeApp(this)
        if (BuildConfig.PLACES_API_KEY.isNotBlank() && !Places.isInitialized()) {
            Places.initializeWithNewPlacesApiEnabled(this, BuildConfig.PLACES_API_KEY)
        }
    }
}
