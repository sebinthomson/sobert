package com.example.sobert.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import com.example.sobert.data.Streak
import com.example.sobert.data.StreakRepository
import com.example.sobert.data.dataStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import androidx.datastore.preferences.core.Preferences

class AdvanceFrameCallback : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val widget = SobrietyWidget()
        
        val mainPrefs = context.dataStore.data.first()
        val streaksJson = mainPrefs[StreakRepository.STREAKS_KEY]
        
        val streaks: List<Streak> = if (streaksJson != null) {
            val type = object : TypeToken<List<Streak>>() {}.type
            Gson().fromJson(streaksJson, type)
        } else {
            emptyList()
        }

        var frames: List<Int> = emptyList()
        
        updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { glancePrefs ->
            val assignedId = glancePrefs[WIDGET_STREAK_ID_KEY]
            val selectedId = assignedId ?: mainPrefs[StreakRepository.SELECTED_STREAK_ID_KEY]
            
            val selectedStreak = streaks.find { it.id == selectedId } ?: streaks.firstOrNull { it.isEnabled }
            
            if (selectedStreak != null) {
                val selectedIcon = selectedStreak.selectedIcon
                frames = selectedIcon?.fileNames?.mapNotNull { name ->
                    val resId = context.resources.getIdentifier(name, "drawable", context.packageName)
                    if (resId != 0) resId else null
                } ?: emptyList()
            }
            glancePrefs
        }

        if (frames.size <= 1) return

        for (i in frames.indices) {
            updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { prefs ->
                prefs.toMutablePreferences().apply {
                    this[FRAME_INDEX_KEY] = i
                }
            }
            widget.update(context, glanceId)
            delay(60)
        }
    }
}
