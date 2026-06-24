package com.example.sobert.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import com.example.sobert.data.*
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.delay
import androidx.datastore.preferences.core.Preferences

class AdvanceFrameCallback : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val widget = SobrietyWidget()
        var frames: List<Int> = emptyList()
        var statusFrames: List<Int> = emptyList()
        
        updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { glancePrefs ->
            val streaksJson = glancePrefs[StreakRepository.STREAKS_KEY]
            val checkOffsJson = glancePrefs[StreakRepository.CHECKOFFS_KEY]
            
            val streaks: List<Streak> = streaksJson?.let {
                val type = object : TypeToken<List<Streak>>() {}.type
                Gson().fromJson(it, type)
            } ?: emptyList()

            val checkOffs: List<CheckOff> = checkOffsJson?.let {
                val type = object : TypeToken<List<CheckOff>>() {}.type
                Gson().fromJson(it, type)
            } ?: emptyList()

            val assignedId = glancePrefs[WIDGET_STREAK_ID_KEY]
            val selectedId = assignedId ?: glancePrefs[StreakRepository.SELECTED_STREAK_ID_KEY]
            
            val selectedStreak = streaks.find { it.id == selectedId }
            val selectedCheckOff = if (selectedStreak == null) checkOffs.find { it.id == selectedId } else null
            
            val item = selectedStreak ?: selectedCheckOff ?: streaks.firstOrNull { it.isEnabled } ?: checkOffs.firstOrNull { it.isEnabled }
            
            if (item != null) {
                when (item) {
                    is Streak -> {
                        frames = getFrames(context, item.selectedIcon)
                    }
                    is CheckOff -> {
                        frames = getFrames(context, item.selectedIcon)
                        val statusIcon = if (item.isCompletedToday) item.selectedTick else item.selectedCross
                        statusFrames = getFrames(context, statusIcon)
                    }
                }
            }
            glancePrefs
        }

        val maxFrames = maxOf(frames.size, statusFrames.size)
        if (maxFrames <= 1) return

        for (i in 0 until maxFrames) {
            updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { prefs ->
                prefs.toMutablePreferences().apply {
                    if (frames.isNotEmpty()) this[FRAME_INDEX_KEY] = i % frames.size
                    if (statusFrames.isNotEmpty()) this[STATUS_FRAME_INDEX_KEY] = i % statusFrames.size
                }
            }
            widget.update(context, glanceId)
            delay(60)
        }
    }

    private fun getFrames(context: Context, option: IconOption?): List<Int> {
        return option?.fileNames?.mapNotNull { name ->
            val resId = context.resources.getIdentifier(name, "drawable", context.packageName)
            if (resId != 0) resId else null
        } ?: emptyList()
    }
}
