package ru.fuezl.gymdiary.feature.exercises

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.fuezl.gymdiary.core.model.Equipment
import ru.fuezl.gymdiary.core.model.MuscleGroup
import ru.fuezl.gymdiary.core.ui.GymDiaryTopBar
import ru.fuezl.gymdiary.data.repository.ExerciseRepository
import ru.fuezl.gymdiary.domain.usecase.ExerciseValidator
import javax.inject.Inject

data class ExerciseEditUiState(
    val id: Long = 0,
    val name: String = "",
    val muscleGroup: MuscleGroup = MuscleGroup.CHEST,
    val equipment: Equipment = Equipment.BARBELL,
    val note: String = "",
    val nameError: String? = null,
    val deleteError: String? = null,
    val isCustom: Boolean = true
)

@HiltViewModel
class ExerciseEditViewModel @Inject constructor(savedStateHandle: SavedStateHandle, private val repository: ExerciseRepository) : ViewModel() {
    private val exerciseId: Long = savedStateHandle["exerciseId"] ?: 0L
    val uiState = MutableStateFlow(ExerciseEditUiState(id = exerciseId))
    private val eventsChannel = Channel<Unit>(Channel.BUFFERED)
    val events = eventsChannel.receiveAsFlow()

    init {
        if (exerciseId > 0) {
            viewModelScope.launch {
                repository.getExercise(exerciseId)?.let { exercise ->
                    uiState.value =
                        ExerciseEditUiState(exercise.id, exercise.name, exercise.muscleGroup, exercise.equipment, exercise.note, isCustom = exercise.isCustom)
                }
            }
        }
    }

    fun onNameChange(value: String) = uiState.update { it.copy(name = value, nameError = null) }
    fun onMuscleGroupChange(value: MuscleGroup?) = uiState.update { it.copy(muscleGroup = value ?: it.muscleGroup) }
    fun onEquipmentChange(value: Equipment?) = uiState.update { it.copy(equipment = value ?: it.equipment) }
    fun onNoteChange(value: String) = uiState.update { it.copy(note = value) }

    fun save() {
        val state = uiState.value
        val error = ExerciseValidator.validateName(state.name)
        if (error != null) {
            uiState.update { it.copy(nameError = error) }
            return
        }
        viewModelScope.launch {
            if (state.id == 0L) {
                repository.createExercise(state.name, state.muscleGroup, state.equipment, state.note)
            } else {
                val original = repository.getExercise(state.id) ?: return@launch
                repository.updateExercise(original.copy(name = state.name, muscleGroup = state.muscleGroup, equipment = state.equipment, note = state.note))
            }
            eventsChannel.send(Unit)
        }
    }

    fun delete() {
        val id = uiState.value.id
        if (id == 0L) return
        viewModelScope.launch {
            runCatching { repository.deleteExercise(id) }
                .onSuccess { eventsChannel.send(Unit) }
                .onFailure { uiState.update { it.copy(deleteError = "Нельзя удалить упражнение, которое используется в тренировках") } }
        }
    }
}

@Composable
fun ExerciseEditRoute(onBack: () -> Unit, viewModel: ExerciseEditViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.events.collect { onBack() } }
    ExerciseEditScreen(
        state,
        onBack,
        viewModel::onNameChange,
        viewModel::onMuscleGroupChange,
        viewModel::onEquipmentChange,
        viewModel::onNoteChange,
        viewModel::save,
        viewModel::delete
    )
}

@Composable
fun ExerciseEditScreen(
    state: ExerciseEditUiState,
    onBack: () -> Unit,
    onNameChange: (String) -> Unit,
    onMuscleGroupChange: (MuscleGroup?) -> Unit,
    onEquipmentChange: (Equipment?) -> Unit,
    onNoteChange: (String) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit
) {
    var confirmDelete by remember { mutableStateOf(false) }
    Scaffold(topBar = { GymDiaryTopBar(if (state.id == 0L) "Новое упражнение" else "Редактирование", onBack) }) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = state.name,
                onValueChange = onNameChange,
                label = { Text("Название") },
                isError = state.nameError != null,
                supportingText = { state.nameError?.let { Text(it, Modifier.testTag("exercise_name_error")) } },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("exercise_name"),
                singleLine = true
            )
            EnumDropdown("Группа мышц", state.muscleGroup, MuscleGroup.entries, { it.title }, onMuscleGroupChange, Modifier.fillMaxWidth())
            EnumDropdown("Оборудование", state.equipment, Equipment.entries, { it.title }, onEquipmentChange, Modifier.fillMaxWidth())
            OutlinedTextField(value = state.note, onValueChange = onNoteChange, label = { Text("Комментарий") }, modifier = Modifier.fillMaxWidth())
            Button(onClick = onSave, modifier = Modifier
                .fillMaxWidth()
                .testTag("save_exercise")) { Text("Сохранить") }
            if (state.id > 0 && state.isCustom) {
                OutlinedButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth()) { Text("Удалить упражнение") }
            }
            state.deleteError?.let { Text(it) }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Удалить упражнение?") },
            text = { Text("Удалить можно только пользовательские упражнения, которые не используются в тренировках.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDelete()
                }) { Text("Удалить") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Отмена") } }
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ExerciseEditPreview() {
    ExerciseEditScreen(ExerciseEditUiState(), {}, {}, {}, {}, {}, {}, {})
}
