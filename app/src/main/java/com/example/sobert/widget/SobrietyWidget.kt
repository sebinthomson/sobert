package com.example.sobert.widget

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.size
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.color.ColorProvider
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.currentState
import com.example.sobert.R
import com.example.sobert.data.Streak
import com.example.sobert.data.StreakRepository
import com.example.sobert.data.UpdateMode
import com.example.sobert.data.dataStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.temporal.ChronoUnit

import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

val FRAME_INDEX_KEY = intPreferencesKey("frame_index")
val WIDGET_STREAK_ID_KEY = stringPreferencesKey("widget_streak_id")

class SobrietyWidget : GlanceAppWidget() {

    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val mainPrefs = context.dataStore.data.first()
        val streaksJson = mainPrefs[StreakRepository.STREAKS_KEY]
        
        val streaks: List<Streak> = if (streaksJson != null) {
            val type = object : TypeToken<List<Streak>>() {}.type
            Gson().fromJson(streaksJson, type)
        } else {
            emptyList()
        }

        provideContent {
            val glancePrefs = currentState<Preferences>()
            val assignedId = glancePrefs[WIDGET_STREAK_ID_KEY]
            val selectedId = assignedId ?: mainPrefs[StreakRepository.SELECTED_STREAK_ID_KEY]
            
            // Priority for finding the streak:
            // 1. Assigned ID (for specific widget configuration)
            // 2. Globally selected ID
            // 3. Fallback to assigned name (if ID mismatch due to UUID changes)
            // 4. First enabled streak
            val selectedStreak = streaks.find { it.id == selectedId } 
                ?: streaks.firstOrNull { it.isEnabled }
            
            if (selectedStreak != null && selectedStreak.isEnabled) {
                val count = if (selectedStreak.updateMode == UpdateMode.DAILY) {
                    ChronoUnit.DAYS.between(
                        LocalDate.ofEpochDay(selectedStreak.startDateEpochDay),
                        LocalDate.now()
                    ).coerceAtLeast(0)
                } else {
                    selectedStreak.manualCount.toLong()
                }

                val selectedIcon = selectedStreak.selectedIcon
                val frames: List<Int> = selectedIcon?.fileNames?.mapNotNull { name ->
                    val resId = context.resources.getIdentifier(name, "drawable", context.packageName)
                    if (resId != 0) resId else null
                } ?: listOf(R.drawable.appicon)
                
                // If mapNotNull returned empty list, fallback to appicon
                val finalFrames = if (frames.isEmpty()) listOf(R.drawable.appicon) else frames
                
                val frameIndex = (glancePrefs[FRAME_INDEX_KEY] ?: 0) % finalFrames.size
                val frameRes = finalFrames[frameIndex]

                SobertWidgetContent(
                    count = count,
                    frameRes = frameRes,
                    backgroundColor = Color(selectedStreak.widgetBgColor),
                    textColor = Color(selectedStreak.widgetTextColor)
                )
            } else {
                Column(
                    modifier = GlanceModifier.fillMaxSize().background(Color.White),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Add a streak in the app")
                }
            }
        }
    }
}

@androidx.compose.runtime.Composable
fun SobertWidgetContent(
    count: Long,
    frameRes: Int,
    backgroundColor: Color,
    textColor: Color
) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(backgroundColor)
            .clickable(actionRunCallback<AdvanceFrameCallback>()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            provider = ImageProvider(frameRes),
            contentDescription = null,
            modifier = GlanceModifier
                .size(64.dp)
        )

        Spacer(modifier = GlanceModifier.height(4.dp))

        Text(
            text = count.toString(),
            style = TextStyle(
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = ColorProvider(day = textColor, night = textColor)
            )
        )
    }
}
