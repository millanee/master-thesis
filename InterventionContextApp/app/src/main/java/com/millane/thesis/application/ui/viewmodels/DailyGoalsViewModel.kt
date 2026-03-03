package com.millane.thesis.application.ui.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.millane.thesis.application.data.dailygoals.DailyGoalsRepository
import com.millane.thesis.application.domain.dailygoals.DailyGoal
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DailyGoalsViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = DailyGoalsRepository(application.applicationContext)

    val goals: StateFlow<List<DailyGoal>> =
        repo.goals.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = listOf(DailyGoal(id = "loading", text = "…"))
        )

    fun addGoal(text: String) {
        viewModelScope.launch { repo.addGoal(text) }
    }

    fun deleteGoal(id: String) {
        viewModelScope.launch { repo.deleteGoal(id) }
    }
}