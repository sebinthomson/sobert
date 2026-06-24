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
import com.example.sobert.data.*
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.time.LocalDate
import java.time.temporal.ChronoUnit

import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

val FRAME_INDEX_KEY = intPreferencesKey("frame_index")
val STATUS_FRAME_INDEX_KEY = intPreferencesKey("status_frame_index")
val WIDGET_STREAK_ID_KEY = stringPreferencesKey("widget_streak_id")

class SobrietyWidget : GlanceAppWidget() {

    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val prefs = currentState<Preferences>()
            val streaksJson = prefs[StreakRepository.STREAKS_KEY]
            val checkOffsJson = prefs[StreakRepository.CHECKOFFS_KEY]
            
            val streaks: List<Streak> = streaksJson?.let {
                val type = object : TypeToken<List<Streak>>() {}.type
                Gson().fromJson(it, type)
            } ?: emptyList()

            val checkOffs: List<CheckOff> = checkOffsJson?.let {
                val type = object : TypeToken<List<CheckOff>>() {}.type
                Gson().fromJson(it, type)
            } ?: emptyList()

            val assignedId = prefs[WIDGET_STREAK_ID_KEY]
            val selectedId = assignedId ?: prefs[StreakRepository.SELECTED_STREAK_ID_KEY]
            
            val selectedStreak = streaks.find { it.id == selectedId }
            val selectedCheckOff = if (selectedStreak == null) checkOffs.find { it.id == selectedId } else null
            
            val item = selectedStreak ?: selectedCheckOff ?: streaks.firstOrNull { it.isEnabled } ?: checkOffs.firstOrNull { it.isEnabled }

            if (item != null) {
                when (item) {
                    is Streak -> {
                        if (item.isEnabled) {
                            val count = if (item.updateMode == UpdateMode.DAILY) {
                                ChronoUnit.DAYS.between(LocalDate.ofEpochDay(item.startDateEpochDay), LocalDate.now()).coerceAtLeast(0)
                            } else {
                                item.manualCount.toLong()
                            }
                            val frames = getFrames(context, item.selectedIcon)
                            val frameIndex = (prefs[FRAME_INDEX_KEY] ?: 0) % frames.size
                            
                            StreakWidgetContent(
                                count = count,
                                frameRes = frames[frameIndex],
                                backgroundColor = Color(item.widgetBgColor),
                                textColor = Color(item.widgetTextColor)
                            )
                        } else {
                            EmptyState("Streak disabled")
                        }
                    }
                    is CheckOff -> {
                        if (item.isEnabled) {
                            val frames = getFrames(context, item.selectedIcon)
                            val frameIndex = (prefs[FRAME_INDEX_KEY] ?: 0) % frames.size
                            
                            val statusIcon = if (item.isCompletedToday) item.selectedTick else item.selectedCross
                            val statusFrames = getFrames(context, statusIcon)
                            val statusFrameIndex = (prefs[STATUS_FRAME_INDEX_KEY] ?: 0) % statusFrames.size

                            CheckOffWidgetContent(
                                frameRes = frames[frameIndex],
                                statusRes = statusFrames[statusFrameIndex],
                                backgroundColor = Color(item.widgetBgColor),
                                textColor = Color(item.widgetTextColor)
                            )
                        } else {
                            EmptyState("Check-off disabled")
                        }
                    }
                }
            } else {
                EmptyState("Add a tracker in the app")
            }
        }
    }

    private fun getFrames(context: Context, option: IconOption?): List<Int> {
        return option?.fileNames?.mapNotNull { name ->
            val resId = context.resources.getIdentifier(name, "drawable", context.packageName)
            if (resId != 0) resId else null
        }?.ifEmpty { listOf(R.drawable.appicon) } ?: listOf(R.drawable.appicon)
    }
}

@androidx.compose.runtime.Composable
fun EmptyState(text: String) {
    Column(
        modifier = GlanceModifier.fillMaxSize().background(Color.White),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text)
    }
}

@androidx.compose.runtime.Composable
fun StreakWidgetContent(
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
            modifier = GlanceModifier.size(64.dp)
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

@androidx.compose.runtime.Composable
fun CheckOffWidgetContent(
    frameRes: Int,
    statusRes: Int,
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
            modifier = GlanceModifier.size(48.dp)
        )
        Spacer(modifier = GlanceModifier.height(4.dp))
        Image(
            provider = ImageProvider(statusRes),
            contentDescription = null,
            modifier = GlanceModifier.size(32.dp)
        )
    }
}
