package ru.fuezl.gymdiary.feature.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.fuezl.gymdiary.core.model.ThemeMode
import ru.fuezl.gymdiary.core.model.UserSettings
import ru.fuezl.gymdiary.core.ui.GymDiaryTopBar

@Composable
fun SettingsRoute(contentPadding: PaddingValues, viewModel: SettingsViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    var pendingExport by remember { mutableStateOf<String?>(null) }
    var importConfirm by remember { mutableStateOf<Uri?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    val createDocument = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val data = pendingExport
        if (uri != null && data != null) {
            context.contentResolver.openOutputStream(uri)?.use { it.write(data.toByteArray()) }
            message = "Данные успешно экспортированы"
        }
        pendingExport = null
    }
    val openDocument = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> importConfirm = uri }
    LaunchedEffect(Unit) {
        viewModel.exportEvents.collect {
            pendingExport = it
            createDocument.launch("gym_diary_export.json")
        }
    }
    LaunchedEffect(Unit) { viewModel.messages.collect { message = it } }
    SettingsScreen(
        settings = settings,
        contentPadding = contentPadding,
        message = message,
        onTheme = viewModel::updateTheme,
        onRestTimer = viewModel::updateRestTimer,
        onHaptics = viewModel::updateHaptics,
        onExport = viewModel::exportData,
        onImport = { openDocument.launch(arrayOf("application/json", "text/*")) },
        onClear = viewModel::clearAllData
    )
    if (importConfirm != null) {
        AlertDialog(
            onDismissRequest = { importConfirm = null },
            title = { Text("Импортировать данные?") },
            text = { Text("Импорт может изменить текущие данные. Продолжить?") },
            confirmButton = {
                TextButton(onClick = {
                    val uri = importConfirm
                    importConfirm = null
                    if (uri != null) {
                        val json = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }.orEmpty()
                        viewModel.importData(json)
                    }
                }) { Text("Продолжить") }
            },
            dismissButton = { TextButton(onClick = { importConfirm = null }) { Text("Отмена") } }
        )
    }
}

@Composable
fun SettingsScreen(
    settings: UserSettings,
    contentPadding: PaddingValues,
    message: String?,
    onTheme: (ThemeMode) -> Unit,
    onRestTimer: (Boolean, Int) -> Unit,
    onHaptics: (Boolean) -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onClear: () -> Unit
) {
    var clearConfirm by remember { mutableStateOf(false) }
    var restSeconds by remember(settings.defaultRestTimerSeconds) { mutableStateOf(settings.defaultRestTimerSeconds.toString()) }
    LazyColumn(
        Modifier
            .fillMaxSize()
            .padding(contentPadding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { GymDiaryTopBar("Настройки") }
        item { Text("Внешний вид", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
        item { ThemeDropdown(settings.themeMode, onTheme) }
        item { Text("Таймер отдыха", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
        item {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text("Включить таймер")
                Switch(checked = settings.restTimerEnabled, onCheckedChange = { onRestTimer(it, restSeconds.toIntOrNull() ?: 90) })
            }
        }
        item {
            OutlinedTextField(
                value = restSeconds,
                onValueChange = {
                    restSeconds = it
                    onRestTimer(settings.restTimerEnabled, it.toIntOrNull() ?: 90)
                },
                label = { Text("Длительность по умолчанию, сек") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        }
        item { Text("Отклик", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
        item {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text("Вибрация")
                Switch(checked = settings.hapticsEnabled, onCheckedChange = onHaptics)
            }
        }
        item { Text("Данные", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onExport, modifier = Modifier.fillMaxWidth()) { Text("Экспорт данных в JSON") }
                OutlinedButton(onClick = onImport, modifier = Modifier.fillMaxWidth()) { Text("Импорт данных из JSON") }
                OutlinedButton(onClick = { clearConfirm = true }, modifier = Modifier.fillMaxWidth()) { Text("Очистить все данные") }
            }
        }
        if (message != null) item { Text(message, color = MaterialTheme.colorScheme.primary) }
    }
    if (clearConfirm) {
        AlertDialog(
            onDismissRequest = { clearConfirm = false },
            title = { Text("Очистить все данные?") },
            text = { Text("Все тренировки и пользовательские упражнения будут удалены.") },
            confirmButton = {
                TextButton(onClick = {
                    clearConfirm = false
                    onClear()
                }) { Text("Очистить") }
            },
            dismissButton = { TextButton(onClick = { clearConfirm = false }) { Text("Отмена") } }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThemeDropdown(selected: ThemeMode, onSelected: (ThemeMode) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selected.title,
            onValueChange = {},
            readOnly = true,
            label = { Text("Тема") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, true)
                .fillMaxWidth()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            ThemeMode.entries.forEach { mode ->
                DropdownMenuItem(text = { Text(mode.title) }, onClick = {
                    onSelected(mode)
                    expanded = false
                })
            }
        }
    }
}
