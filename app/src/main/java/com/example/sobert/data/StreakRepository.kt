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

data class CheckOff(
    val id: String,
    val name: String,
    val lastCompletedDateEpochDay: Long? = null,
    val isEnabled: Boolean = true,
    val iconOptions: List<IconOption> = emptyList(),
    val tickIconOptions: List<IconOption> = emptyList(),
    val crossIconOptions: List<IconOption> = emptyList(),
    val selectedIconIndex: Int = 0,
    val selectedTickIndex: Int = 0,
    val selectedCrossIndex: Int = 0,
    val widgetBgColor: Int = 0xFFFFFFFF.toInt(),
    val widgetTextColor: Int = 0xFF4CAF50.toInt()
) {
    val isCompletedToday: Boolean
        get() = lastCompletedDateEpochDay == LocalDate.now().toEpochDay()
    
    val selectedIcon: IconOption? get() = iconOptions.getOrNull(selectedIconIndex)
    val selectedTick: IconOption? get() = tickIconOptions.getOrNull(selectedTickIndex)
    val selectedCross: IconOption? get() = crossIconOptions.getOrNull(selectedCrossIndex)
}

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class StreakRepository(private val context: Context) {
    private val gson = Gson()

    companion object {
        val STREAKS_KEY = stringPreferencesKey("streaks")
        val CHECKOFFS_KEY = stringPreferencesKey("checkoffs")
        val SELECTED_STREAK_ID_KEY = stringPreferencesKey("selected_streak_id")
    }

    val streaksFlow: Flow<List<Streak>> = context.dataStore.data
        .map { preferences ->
            val json = preferences[STREAKS_KEY] ?: return@map emptyList<Streak>()
            val type = object : TypeToken<List<Streak>>() {}.type
            gson.fromJson(json, type)
        }

    val checkOffsFlow: Flow<List<CheckOff>> = context.dataStore.data
        .map { preferences ->
            val json = preferences[CHECKOFFS_KEY] ?: return@map emptyList<CheckOff>()
            val type = object : TypeToken<List<CheckOff>>() {}.type
            gson.fromJson(json, type)
        }

    val selectedStreakIdFlow: Flow<String?> = context.dataStore.data
        .map { it[SELECTED_STREAK_ID_KEY] }

    suspend fun initializePredefinedData() {
        initializePredefinedStreaks()
        initializePredefinedCheckOffs()
    }

    private suspend fun initializePredefinedStreaks() {
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
                IconOption(fileNames = files, isAnimated = files.size > 1)
            }
            
            if (existing != null) {
                existing.copy(id = id, name = ps.title, updateMode = UpdateMode.valueOf(ps.updateMode), iconOptions = iconOptions)
            } else {
                Streak(id = id, name = ps.title, updateMode = UpdateMode.valueOf(ps.updateMode), iconOptions = iconOptions, isEnabled = true)
            }
        }

        context.dataStore.edit { preferences ->
            preferences[STREAKS_KEY] = gson.toJson(updatedStreaks)
            if (preferences[SELECTED_STREAK_ID_KEY] == null) {
                updatedStreaks.firstOrNull { it.isEnabled }?.id?.let { preferences[SELECTED_STREAK_ID_KEY] = it }
            }
        }
    }

    private suspend fun initializePredefinedCheckOffs() {
        val currentJson = context.dataStore.data.first()[CHECKOFFS_KEY]
        val type = object : TypeToken<List<CheckOff>>() {}.type
        val existingCheckOffs: List<CheckOff> = if (currentJson != null) gson.fromJson(currentJson, type) else emptyList()

        val jsonString = context.assets.open("predefined_check_offs.json").bufferedReader().use { it.readText() }
        val predefinedType = object : TypeToken<List<PredefinedCheckOff>>() {}.type
        val predefined: List<PredefinedCheckOff> = gson.fromJson(jsonString, predefinedType)

        val updatedCheckOffs = predefined.map { pc ->
            val id = "checkoff_" + pc.title.lowercase().replace(" ", "_")
            val existing = existingCheckOffs.find { it.id == id || it.name == pc.title }
            
            val iconOptions = pc.iconFiles.map { files -> IconOption(fileNames = files, isAnimated = files.size > 1) }
            val tickOptions = pc.tickIconFiles.map { files -> IconOption(fileNames = files, isAnimated = files.size > 1) }
            val crossOptions = pc.crossIconFiles.map { files -> IconOption(fileNames = files, isAnimated = files.size > 1) }
            
            if (existing != null) {
                existing.copy(id = id, name = pc.title, iconOptions = iconOptions, tickIconOptions = tickOptions, crossIconOptions = crossOptions)
            } else {
                CheckOff(id = id, name = pc.title, iconOptions = iconOptions, tickIconOptions = tickOptions, crossIconOptions = crossOptions, isEnabled = true)
            }
        }

        context.dataStore.edit { it[CHECKOFFS_KEY] = gson.toJson(updatedCheckOffs) }
    }

    suspend fun saveStreak(streak: Streak) {
        context.dataStore.edit { preferences ->
            val currentJson = preferences[STREAKS_KEY]
            val type = object : TypeToken<MutableList<Streak>>() {}.type
            val streaks: MutableList<Streak> = if (currentJson != null) gson.fromJson(currentJson, type) else mutableListOf()
            val index = streaks.indexOfFirst { it.id == streak.id }
            if (index != -1) streaks[index] = streak else streaks.add(streak)
            preferences[STREAKS_KEY] = gson.toJson(streaks)
        }
    }

    suspend fun saveCheckOff(checkOff: CheckOff) {
        context.dataStore.edit { preferences ->
            val currentJson = preferences[CHECKOFFS_KEY]
            val type = object : TypeToken<MutableList<CheckOff>>() {}.type
            val list: MutableList<CheckOff> = if (currentJson != null) gson.fromJson(currentJson, type) else mutableListOf()
            val index = list.indexOfFirst { it.id == checkOff.id }
            if (index != -1) list[index] = checkOff else list.add(checkOff)
            preferences[CHECKOFFS_KEY] = gson.toJson(list)
        }
    }

    suspend fun setSelectedStreak(streakId: String) {
        context.dataStore.edit { it[SELECTED_STREAK_ID_KEY] = streakId }
    }
}

data class PredefinedStreak(val title: String, val updateMode: String, val iconFiles: List<List<String>>)
data class PredefinedCheckOff(val title: String, val iconFiles: List<List<String>>, val tickIconFiles: List<List<String>>, val crossIconFiles: List<List<String>>)
