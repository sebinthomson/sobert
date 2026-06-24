package com.example.sobert.ui

import android.app.Application
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.appwidget.updateAll
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.sobert.data.CheckOff
import com.example.sobert.data.Streak
import com.example.sobert.data.StreakRepository
import com.example.sobert.widget.SobrietyWidget
import com.google.gson.Gson
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import com.example.sobert.widget.SobrietyWidgetReceiver
import com.example.sobert.widget.WidgetPinReceiver

data class StreakUiState(
    val streaks: List<Streak> = emptyList(),
    val checkOffs: List<CheckOff> = emptyList(),
    val selectedStreakId: String? = null
)

class StreakViewModel(
    application: Application,
    private val repository: StreakRepository
) : AndroidViewModel(application) {

    val uiState: StateFlow<StreakUiState> = combine(
        repository.streaksFlow,
        repository.checkOffsFlow,
        repository.selectedStreakIdFlow
    ) { streaks, checkOffs, selectedId ->
        StreakUiState(streaks, checkOffs, selectedId)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = StreakUiState()
    )

    init {
        viewModelScope.launch {
            repository.initializePredefinedData()
            syncWidget()
        }
    }

    fun updateStreak(streak: Streak) {
        viewModelScope.launch {
            repository.saveStreak(streak)
            syncWidget()
        }
    }

    fun toggleStreak(streak: Streak, enabled: Boolean) {
        viewModelScope.launch {
            repository.saveStreak(streak.copy(isEnabled = enabled))
            syncWidget()
        }
    }

    fun checkIn(streak: Streak) {
        viewModelScope.launch {
            val updated = streak.copy(manualCount = streak.manualCount + 1)
            repository.saveStreak(updated)
            syncWidget()
        }
    }

    fun resetStreak(streak: Streak) {
        viewModelScope.launch {
            val updated = streak.copy(
                startDateEpochDay = LocalDate.now().toEpochDay(),
                manualCount = 0
            )
            repository.saveStreak(updated)
            syncWidget()
        }
    }

    fun updateCheckOff(checkOff: CheckOff) {
        viewModelScope.launch {
            repository.saveCheckOff(checkOff)
            syncWidget()
        }
    }

    fun toggleCheckOffCompletion(checkOff: CheckOff) {
        viewModelScope.launch {
            val today = LocalDate.now().toEpochDay()
            val newDate = if (checkOff.lastCompletedDateEpochDay == today) null else today
            repository.saveCheckOff(checkOff.copy(lastCompletedDateEpochDay = newDate))
            syncWidget()
        }
    }

    fun toggleCheckOffEnabled(checkOff: CheckOff, enabled: Boolean) {
        viewModelScope.launch {
            repository.saveCheckOff(checkOff.copy(isEnabled = enabled))
            syncWidget()
        }
    }

    fun selectStreakForWidget(streakId: String) {
        viewModelScope.launch {
            repository.setSelectedStreak(streakId)
            syncWidget()
        }
    }

    fun pinStreakWidget(streakId: String) {
        val context = getApplication<Application>()
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val myProvider = ComponentName(context, SobrietyWidgetReceiver::class.java)

        if (appWidgetManager.isRequestPinAppWidgetSupported) {
            val successCallback = Intent(context, WidgetPinReceiver::class.java).apply {
                putExtra("streak_id", streakId)
            }

            val pendingIntent = PendingIntent.getBroadcast(
                context,
                streakId.hashCode(),
                successCallback,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )

            appWidgetManager.requestPinAppWidget(myProvider, null, pendingIntent)
        }
    }

    private suspend fun syncWidget() {
        val context = getApplication<Application>()
        val streaks = repository.streaksFlow.first()
        val checkOffs = repository.checkOffsFlow.first()
        val selectedId = repository.selectedStreakIdFlow.first()
        
        val streaksJson = Gson().toJson(streaks)
        val checkOffsJson = Gson().toJson(checkOffs)
        
        val glanceIds = GlanceAppWidgetManager(context).getGlanceIds(SobrietyWidget::class.java)
        glanceIds.forEach { glanceId ->
            updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { prefs ->
                prefs.toMutablePreferences().apply {
                    this[StreakRepository.STREAKS_KEY] = streaksJson
                    this[StreakRepository.CHECKOFFS_KEY] = checkOffsJson
                    if (selectedId != null) {
                        this[StreakRepository.SELECTED_STREAK_ID_KEY] = selectedId
                    } else {
                        remove(StreakRepository.SELECTED_STREAK_ID_KEY)
                    }
                }
            }
        }
        SobrietyWidget().updateAll(context)
    }
}
