package com.example.sobert.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sobert.R
import com.example.sobert.data.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StreakScreen(
    viewModel: StreakViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    var streakToEdit by remember { mutableStateOf<Streak?>(null) }
    var checkOffToEdit by remember { mutableStateOf<CheckOff?>(null) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(title = { Text("My Trackers") })
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (uiState.streaks.isNotEmpty()) {
                item {
                    Text("Streaks", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }
                items(uiState.streaks) { streak ->
                    StreakItem(
                        streak = streak,
                        isSelected = streak.id == uiState.selectedStreakId,
                        onSelect = { if (streak.isEnabled) viewModel.selectStreakForWidget(streak.id) },
                        onReset = { viewModel.resetStreak(streak) },
                        onToggle = { enabled -> viewModel.toggleStreak(streak, enabled) },
                        onCheckIn = { viewModel.checkIn(streak) },
                        onEdit = { streakToEdit = streak },
                        onPin = { viewModel.pinStreakWidget(streak.id) }
                    )
                }
            }

            if (uiState.checkOffs.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Daily Check-Offs", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }
                items(uiState.checkOffs) { checkOff ->
                    CheckOffItem(
                        checkOff = checkOff,
                        isSelected = checkOff.id == uiState.selectedStreakId, // Sharing selection logic for widget
                        onSelect = { if (checkOff.isEnabled) viewModel.selectStreakForWidget(checkOff.id) },
                        onToggleCompletion = { viewModel.toggleCheckOffCompletion(checkOff) },
                        onToggleEnabled = { enabled -> viewModel.toggleCheckOffEnabled(checkOff, enabled) },
                        onEdit = { checkOffToEdit = checkOff },
                        onPin = { viewModel.pinStreakWidget(checkOff.id) }
                    )
                }
            }
        }
    }

    streakToEdit?.let { streak ->
        StreakEditDialog(
            streak = streak,
            onDismiss = { streakToEdit = null },
            onConfirm = { name, desc, date, mode, bgColor, txtColor, iconIndex ->
                viewModel.updateStreak(streak.copy(
                    name = name,
                    description = desc,
                    startDateEpochDay = date.toEpochDay(),
                    updateMode = mode,
                    widgetBgColor = bgColor,
                    widgetTextColor = txtColor,
                    selectedIconIndex = iconIndex,
                    widgetTitle = name
                ))
                streakToEdit = null
            }
        )
    }

    checkOffToEdit?.let { checkOff ->
        CheckOffEditDialog(
            checkOff = checkOff,
            onDismiss = { checkOffToEdit = null },
            onConfirm = { name, bgColor, txtColor, iconIdx, tickIdx, crossIdx ->
                viewModel.updateCheckOff(checkOff.copy(
                    name = name,
                    widgetBgColor = bgColor,
                    widgetTextColor = txtColor,
                    selectedIconIndex = iconIdx,
                    selectedTickIndex = tickIdx,
                    selectedCrossIndex = crossIdx
                ))
                checkOffToEdit = null
            }
        )
    }
}

@Composable
fun StreakItem(
    streak: Streak,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onReset: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onCheckIn: () -> Unit,
    onEdit: () -> Unit,
    onPin: () -> Unit
) {
    val count = if (streak.updateMode == UpdateMode.DAILY) {
        ChronoUnit.DAYS.between(
            LocalDate.ofEpochDay(streak.startDateEpochDay),
            LocalDate.now()
        ).coerceAtLeast(0)
    } else {
        streak.manualCount.toLong()
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (streak.isEnabled) 1f else 0.6f)
            .clickable(enabled = streak.isEnabled) { onSelect() },
        shape = RoundedCornerShape(16.dp),
        border = if (isSelected && streak.isEnabled) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        colors = CardDefaults.cardColors(
            containerColor = Color(streak.widgetBgColor).copy(alpha = 0.1f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = streak.name,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color(streak.widgetTextColor)
                    )
                    Text(
                        text = if (streak.updateMode == UpdateMode.DAILY) "Daily Streak" else "Manual Counter",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
                
                Switch(
                    checked = streak.isEnabled,
                    onCheckedChange = onToggle,
                    modifier = Modifier.scale(0.8f)
                )
            }

            if (streak.isEnabled) {
                if (streak.description.isNotBlank()) {
                    Text(
                        text = streak.description,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = count.toString(),
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.Black,
                        color = Color(streak.widgetTextColor)
                    )
                    
                    if (streak.updateMode == UpdateMode.MANUAL) {
                        Button(
                            onClick = onCheckIn,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                        ) {
                            Text("Check In")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isSelected) {
                        Text(
                            "Selected for Widget",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    IconButton(onClick = onPin) {
                        Icon(Icons.Default.PushPin, contentDescription = "Pin to Home")
                    }
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit")
                    }
                    IconButton(onClick = onReset) {
                        Icon(Icons.Default.Refresh, contentDescription = "Reset")
                    }
                }
            }
        }
    }
}

@Composable
fun CheckOffItem(
    checkOff: CheckOff,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onToggleCompletion: () -> Unit,
    onToggleEnabled: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onPin: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (checkOff.isEnabled) 1f else 0.6f)
            .clickable(enabled = checkOff.isEnabled) { onSelect() },
        shape = RoundedCornerShape(16.dp),
        border = if (isSelected && checkOff.isEnabled) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        colors = CardDefaults.cardColors(
            containerColor = Color(checkOff.widgetBgColor).copy(alpha = 0.1f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = checkOff.name,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color(checkOff.widgetTextColor)
                    )
                    Text(
                        text = "Daily Check-Off",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
                
                Switch(
                    checked = checkOff.isEnabled,
                    onCheckedChange = onToggleEnabled,
                    modifier = Modifier.scale(0.8f)
                )
            }

            if (checkOff.isEnabled) {
                Spacer(modifier = Modifier.height(16.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val statusText = if (checkOff.isCompletedToday) "Completed Today" else "Not Completed"
                    val statusColor = if (checkOff.isCompletedToday) Color(0xFF4CAF50) else Color(0xFFF44336)
                    
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (checkOff.isCompletedToday) Icons.Default.Check else Icons.Default.Close,
                            contentDescription = null,
                            tint = statusColor,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = statusText, style = MaterialTheme.typography.titleMedium, color = statusColor)
                    }

                    Button(
                        onClick = onToggleCompletion,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (checkOff.isCompletedToday) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text(if (checkOff.isCompletedToday) "Undo" else "Check Off")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isSelected) {
                        Text(
                            "Selected for Widget",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    IconButton(onClick = onPin) {
                        Icon(Icons.Default.PushPin, contentDescription = "Pin to Home")
                    }
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit")
                    }
                }
            }
        }
    }
}

@Composable
fun Modifier.scale(scale: Float) = this.then(
    Modifier.layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)
        layout((placeable.width * scale).toInt(), (placeable.height * scale).toInt()) {
            placeable.placeRelative(0, 0)
        }
    }
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StreakEditDialog(
    streak: Streak,
    onDismiss: () -> Unit,
    onConfirm: (String, String, LocalDate, UpdateMode, Int, Int, Int) -> Unit
) {
    var name by remember { mutableStateOf(streak.name) }
    var description by remember { mutableStateOf(streak.description) }
    var selectedDate by remember { mutableStateOf(LocalDate.ofEpochDay(streak.startDateEpochDay)) }
    var updateMode by remember { mutableStateOf(streak.updateMode) }
    var bgColor by remember { mutableStateOf(streak.widgetBgColor) }
    var txtColor by remember { mutableStateOf(streak.widgetTextColor) }
    var selectedIconIndex by remember { mutableStateOf(streak.selectedIconIndex) }
    var showDatePicker by remember { mutableStateOf(false) }

    val colors = listOf(
        0xFFFFFFFF.toInt(), 0xFF000000.toInt(), 0xFFF44336.toInt(),
        0xFF4CAF50.toInt(), 0xFF2196F3.toInt(), 0xFFFFEB3B.toInt(),
        0xFFFF9800.toInt(), 0xFF9C27B0.toInt()
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit ${streak.name}") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Display Name") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                item {
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("Description (Optional)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                
                item {
                    Text("Update Mode", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = updateMode == UpdateMode.DAILY,
                            onClick = { updateMode = UpdateMode.DAILY },
                            label = { Text("Daily") }
                        )
                        FilterChip(
                            selected = updateMode == UpdateMode.MANUAL,
                            onClick = { updateMode = UpdateMode.MANUAL },
                            label = { Text("Manual") }
                        )
                    }
                }

                if (updateMode == UpdateMode.DAILY) {
                    item {
                        Button(
                            onClick = { showDatePicker = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Start Date: ${selectedDate}")
                        }
                    }
                }

                if (streak.iconOptions.isNotEmpty()) {
                    item {
                        Text("Choose Icon Set", style = MaterialTheme.typography.labelLarge)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            streak.iconOptions.forEachIndexed { index, option ->
                                IconSetOption(
                                    option = option,
                                    isSelected = selectedIconIndex == index,
                                    bgColor = bgColor,
                                    onClick = { selectedIconIndex = index }
                                )
                            }
                        }
                    }
                }

                item {
                    Text("Background Color", style = MaterialTheme.typography.labelLarge)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        colors.forEach { color ->
                            ColorCircle(color, bgColor == color) { bgColor = color }
                        }
                    }
                }

                item {
                    Text("Text Color", style = MaterialTheme.typography.labelLarge)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        colors.forEach { color ->
                            ColorCircle(color, txtColor == color) { txtColor = color }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (name.isNotBlank()) onConfirm(name, description, selectedDate, updateMode, bgColor, txtColor, selectedIconIndex) },
                enabled = name.isNotBlank()
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = selectedDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        selectedDate = Instant.ofEpochMilli(millis)
                            .atZone(ZoneId.systemDefault())
                            .toLocalDate()
                    }
                    showDatePicker = false
                }) {
                    Text("OK")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CheckOffEditDialog(
    checkOff: CheckOff,
    onDismiss: () -> Unit,
    onConfirm: (String, Int, Int, Int, Int, Int) -> Unit
) {
    var name by remember { mutableStateOf(checkOff.name) }
    var bgColor by remember { mutableStateOf(checkOff.widgetBgColor) }
    var txtColor by remember { mutableStateOf(checkOff.widgetTextColor) }
    var iconIdx by remember { mutableStateOf(checkOff.selectedIconIndex) }
    var tickIdx by remember { mutableStateOf(checkOff.selectedTickIndex) }
    var crossIdx by remember { mutableStateOf(checkOff.selectedCrossIndex) }

    val colors = listOf(
        0xFFFFFFFF.toInt(), 0xFF000000.toInt(), 0xFFF44336.toInt(),
        0xFF4CAF50.toInt(), 0xFF2196F3.toInt(), 0xFFFFEB3B.toInt(),
        0xFFFF9800.toInt(), 0xFF9C27B0.toInt()
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit ${checkOff.name}") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Display Name") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                item {
                    Text("Base Icon", style = MaterialTheme.typography.labelLarge)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        checkOff.iconOptions.forEachIndexed { index, option ->
                            IconSetOption(option, iconIdx == index, bgColor) { iconIdx = index }
                        }
                    }
                }

                item {
                    Text("Tick Icon (Completed)", style = MaterialTheme.typography.labelLarge)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        checkOff.tickIconOptions.forEachIndexed { index, option ->
                            IconSetOption(option, tickIdx == index, bgColor) { tickIdx = index }
                        }
                    }
                }

                item {
                    Text("Cross Icon (Pending)", style = MaterialTheme.typography.labelLarge)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        checkOff.crossIconOptions.forEachIndexed { index, option ->
                            IconSetOption(option, crossIdx == index, bgColor) { crossIdx = index }
                        }
                    }
                }

                item {
                    Text("Background Color", style = MaterialTheme.typography.labelLarge)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        colors.forEach { color ->
                            ColorCircle(color, bgColor == color) { bgColor = color }
                        }
                    }
                }

                item {
                    Text("Text Color", style = MaterialTheme.typography.labelLarge)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        colors.forEach { color ->
                            ColorCircle(color, txtColor == color) { txtColor = color }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { if (name.isNotBlank()) onConfirm(name, bgColor, txtColor, iconIdx, tickIdx, crossIdx) }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun IconSetOption(
    option: IconOption,
    isSelected: Boolean,
    bgColor: Int,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(bgColor))
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.LightGray,
                shape = RoundedCornerShape(8.dp)
            )
            .clickable { onClick() }
            .padding(4.dp)
    ) {
        IconSetPreview(
            fileNames = option.fileNames,
            isAnimated = option.isAnimated && isSelected
        )
        
        if (option.isAnimated) {
            Text(
                "GIF", 
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(2.dp))
                    .padding(horizontal = 2.dp),
                color = Color.White,
                fontSize = 8.sp
            )
        }
    }
}

@Composable
fun ColorCircle(color: Int, isSelected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(24.dp)
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.LightGray,
                shape = CircleShape
            )
            .padding(2.dp)
            .clip(CircleShape)
            .background(Color(color))
            .clickable { onClick() }
    )
}

@Composable
fun IconSetPreview(
    fileNames: List<String>,
    isAnimated: Boolean
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var currentFrameIndex by remember { mutableStateOf(0) }

    if (isAnimated && fileNames.size > 1) {
        LaunchedEffect(fileNames) {
            while (true) {
                kotlinx.coroutines.delay(60)
                currentFrameIndex = (currentFrameIndex + 1) % fileNames.size
            }
        }
    } else {
        currentFrameIndex = 0
    }

    val currentFileName = fileNames.getOrNull(currentFrameIndex) ?: ""
    val resId = context.resources.getIdentifier(currentFileName, "drawable", context.packageName)
    
    if (resId != 0) {
        androidx.compose.foundation.Image(
            painter = androidx.compose.ui.res.painterResource(resId),
            contentDescription = null,
            modifier = Modifier.fillMaxSize()
        )
    }
}
