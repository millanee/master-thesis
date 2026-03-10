package com.millane.thesis.application.goals

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.millane.thesis.application.DailyGoalsPromptActivity
import com.millane.thesis.application.data.dailygoals.DailyGoalsRepository
import com.millane.thesis.application.util.getCurrentDayBoundaryMs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

/**
 * On first phone unlock of the day (after the configured boundary time), if we haven't shown
 * the daily goals prompt for the current day, start [DailyGoalsPromptActivity].
 */
class FirstUnlockReceiver : BroadcastReceiver() {

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_USER_PRESENT) return

        val repo = DailyGoalsRepository(context)
        val currentDay = getCurrentDayBoundaryMs()
        val lastShown = runBlocking { repo.getLastDailyGoalsPromptDayMs() }
        if (lastShown == currentDay) {
            Log.d("FirstUnlock", "Daily goals already shown for day $currentDay, skipping")
            return
        }

        // Clear immediately even if Android blocks background activity launch.
        runBlocking {
            withContext(Dispatchers.IO) {
                repo.replaceAllGoals(emptyList())
            }
        }

        Log.d("FirstUnlock", "First unlock of day (boundary=$currentDay), attempting to show daily goals prompt")
        val activityIntent = Intent(context, DailyGoalsPromptActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(activityIntent) }
            .onFailure { e -> Log.e("FirstUnlock", "Failed to start DailyGoalsPromptActivity (background launch blocked?)", e) }
    }
}
