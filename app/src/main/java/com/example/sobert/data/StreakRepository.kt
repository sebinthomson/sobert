package com.example.sobert.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.example.sobert.R
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.util.UUID

enum class UpdateMode { DAILY, MANUAL }

data class IconOption(
    val fileNames: List<String>,
    val isAnimated: Boolean
)

data class Streak(
    val id: String,
    val name: String,
    val updateMode: UpdateMode = UpdateMode.DAILY,
    val startDateEpochDay: Long = LocalDate.now().toEpochDay(),
    val manualCount: Int = 0,
    val isEnabled: Boolean = true,
    val description: String = "",
    val iconOptions: List<IconOption> = emptyList(),
    val selectedIconIndex: Int = 0,
    val widgetBgColor: Int = 0xFFFFFFFF.toInt(),
    val widgetTextColor: Int = 0xFF4CAF50.toInt(),
    val widgetTitle: String = ""
) {
    val selectedIcon: IconOption? get() = iconOptions.getOrNull(selectedIconIndex)
}

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class StreakRepository(private val context: Context) {
    private val gson = Gson()

    companion object {
        val STREAKS_KEY = stringPreferencesKey("streaks")
        val SELECTED_STREAK_ID_KEY = stringPreferencesKey("selected_streak_id")
    }

    val streaksFlow: Flow<List<Streak>> = context.dataStore.data
        .map { preferences ->
            val json = preferences[STREAKS_KEY] ?: return@map emptyList<Streak>()
            val type = object : TypeToken<List<Streak>>() {}.type
            gson.fromJson(json, type)
        }

    val selectedStreakIdFlow: Flow<String?> = context.dataStore.data
        .map { it[SELECTED_STREAK_ID_KEY] }

    suspend fun initializePredefinedStreaks() {
        val currentJson = context.dataStore.data.first()[STREAKS_KEY]
        val type = object : TypeToken<List<Streak>>() {}.type
        val existingStreaks: List<Streak> = if (currentJson != null) gson.fromJson(currentJson, type) else emptyList()

        val jsonString = context.assets.open("predefined_streaks.json").bufferedReader().use { it.readText() }
        val predefinedType = object : TypeToken<List<PredefinedStreak>>() {}.type
        val predefined: List<PredefinedStreak> = gson.fromJson(jsonString, predefinedType)

        val updatedStreaks = predefined.map { ps ->
            val id = ps.title.lowercase().replace(" ", "_")
            val existing = existingStreaks.find { it.id == id || it.name == ps.title }
            
            val iconOptions = ps.iconFiles.map { files ->
                IconOption(
                    fileNames = files,
                    isAnimated = files.size > 1
                )
            }
            
            if (existing != null) {
                existing.copy(
                    id = id,
                    updateMode = UpdateMode.valueOf(ps.updateMode),
                    iconOptions = iconOptions
                )
            } else {
                Streak(
                    id = id,
                    name = ps.title,
                    updateMode = UpdateMode.valueOf(ps.updateMode),
                    iconOptions = iconOptions,
                    isEnabled = true
                )
            }
        }

        context.dataStore.edit { preferences ->
            preferences[STREAKS_KEY] = gson.toJson(updatedStreaks)
            
            val selectedId = preferences[SELECTED_STREAK_ID_KEY]
            if (selectedId == null || updatedStreaks.none { it.id == selectedId }) {
                updatedStreaks.firstOrNull { it.isEnabled }?.id?.let { newId ->
                    preferences[SELECTED_STREAK_ID_KEY] = newId
                }
            }
        }
    }

    suspend fun saveStreak(streak: Streak) {
        context.dataStore.edit { preferences ->
            val currentJson = preferences[STREAKS_KEY]
            val type = object : TypeToken<MutableList<Streak>>() {}.type
            val streaks: MutableList<Streak> = if (currentJson != null) {
                gson.fromJson(currentJson, type)
            } else {
                mutableListOf()
            }
            
            val index = streaks.indexOfFirst { it.id == streak.id }
            if (index != -1) {
                streaks[index] = streak
            } else {
                streaks.add(streak)
            }
            
            preferences[STREAKS_KEY] = gson.toJson(streaks)
            if (preferences[SELECTED_STREAK_ID_KEY] == null) {
                val firstId = streaks.firstOrNull()?.id
                if (firstId != null) {
                    preferences[SELECTED_STREAK_ID_KEY] = firstId
                }
            }
        }
    }

    suspend fun deleteStreak(streakId: String) {
        context.dataStore.edit { preferences ->
            val currentJson = preferences[STREAKS_KEY] ?: return@edit
            val type = object : TypeToken<MutableList<Streak>>() {}.type
            val streaks: MutableList<Streak> = gson.fromJson(currentJson, type)
            
            streaks.removeAll { it.id == streakId }
            preferences[STREAKS_KEY] = gson.toJson(streaks)
            
            if (preferences[SELECTED_STREAK_ID_KEY] == streakId) {
                val nextId = streaks.firstOrNull()?.id
                if (nextId != null) {
                    preferences[SELECTED_STREAK_ID_KEY] = nextId
                } else {
                    preferences.remove(SELECTED_STREAK_ID_KEY)
                }
            }
        }
    }

    suspend fun setSelectedStreak(streakId: String) {
        context.dataStore.edit { it[SELECTED_STREAK_ID_KEY] = streakId }
    }
}

data class PredefinedStreak(
    val title: String,
    val updateMode: String,
    val iconFiles: List<List<String>>
)
