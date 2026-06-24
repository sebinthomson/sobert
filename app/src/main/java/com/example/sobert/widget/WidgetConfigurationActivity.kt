package com.example.sobert.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.lifecycle.lifecycleScope
import com.example.sobert.data.StreakRepository
import com.example.sobert.ui.theme.SobertTheme
import kotlinx.coroutines.launch
import androidx.datastore.preferences.core.stringPreferencesKey

class WidgetConfigurationActivity : ComponentActivity() {

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setResult(RESULT_CANCELED)

        val intent = intent
        val extras = intent.extras
        if (extras != null) {
            appWidgetId = extras.getInt(
                AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID
            )
        }

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        val repository = StreakRepository(applicationContext)

        setContent {
            SobertTheme {
                ConfigurationScreen(
                    repository = repository,
                    onStreakSelected = { streakId ->
                        handleStreakSelected(streakId)
                    }
                )
            }
        }
    }

    private fun handleStreakSelected(streakId: String) {
        lifecycleScope.launch {
            val manager = GlanceAppWidgetManager(applicationContext)
            val glanceId = manager.getGlanceIdBy(appWidgetId)
            
            updateAppWidgetState(applicationContext, PreferencesGlanceStateDefinition, glanceId) { prefs ->
                prefs.toMutablePreferences().apply {
                    this[WIDGET_STREAK_ID_KEY] = streakId
                }
            }
            
            SobrietyWidget().update(applicationContext, glanceId)

            val resultValue = Intent()
            resultValue.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            setResult(Activity.RESULT_OK, resultValue)
            finish()
        }
    }
}

@Composable
fun ConfigurationScreen(
    repository: StreakRepository,
    onStreakSelected: (String) -> Unit
) {
    val streaks by repository.streaksFlow.collectAsState(initial = emptyList())
    val enabledStreaks = streaks.filter { it.isEnabled }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Select a streak for this widget",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            if (enabledStreaks.isEmpty()) {
                Text("No enabled streaks found. Please enable a streak in the app first.")
            } else {
                LazyColumn {
                    items(enabledStreaks) { streak ->
                        ListItem(
                            headlineContent = { Text(streak.name) },
                            modifier = Modifier.clickable { onStreakSelected(streak.id) }
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}
