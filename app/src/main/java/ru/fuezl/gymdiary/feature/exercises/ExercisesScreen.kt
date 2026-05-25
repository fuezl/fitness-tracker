package ru.fuezl.gymdiary.feature.exercises

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import ru.fuezl.gymdiary.core.model.Equipment
import ru.fuezl.gymdiary.core.model.Exercise
import ru.fuezl.gymdiary.core.model.MuscleGroup
import ru.fuezl.gymdiary.core.ui.EmptyState
import ru.fuezl.gymdiary.core.ui.ExerciseCard
import ru.fuezl.gymdiary.core.ui.GymDiaryTopBar
import ru.fuezl.gymdiary.data.repository.ExerciseRepository
import javax.inject.Inject

data class ExercisesUiState(val query: String = "", val muscleGroup: MuscleGroup? = null, val equipment: Equipment? = null, val exercises: List<Exercise> = emptyList())

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ExercisesViewModel @Inject constructor(private val repository: ExerciseRepository) : ViewModel() {
    private val query = MutableStateFlow("")
    private val muscleGroup = MutableStateFlow<MuscleGroup?>(null)
    private val equipment = MutableStateFlow<Equipment?>(null)

    val uiState = combine(query, muscleGroup, equipment) { q, m, e -> Triple(q, m, e) }
        .flatMapLatest { (q, m, e) ->
            repository.searchExercises(q, m, e).combine(query) { list, currentQuery ->
                ExercisesUiState(currentQuery, m, e, list)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ExercisesUiState())

    fun onQueryChange(value: String) {
        query.value = value
    }

    fun onMuscleGroupChange(value: MuscleGroup?) {
        muscleGroup.value = value
    }

    fun onEquipmentChange(value: Equipment?) {
        equipment.value = value
    }
}

@Composable
fun ExercisesRoute(contentPadding: PaddingValues, onAdd: () -> Unit, onEdit: (Long) -> Unit, viewModel: ExercisesViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ExercisesScreen(state, contentPadding, onAdd, onEdit, viewModel::onQueryChange, viewModel::onMuscleGroupChange, viewModel::onEquipmentChange)
}

@Composable
fun ExercisesScreen(
    state: ExercisesUiState,
    contentPadding: PaddingValues,
    onAdd: () -> Unit,
    onEdit: (Long) -> Unit,
    onQueryChange: (String) -> Unit,
    onMuscleGroupChange: (MuscleGroup?) -> Unit,
    onEquipmentChange: (Equipment?) -> Unit
) {
    Scaffold(
        modifier = Modifier.padding(contentPadding),
        floatingActionButton = {
            FloatingActionButton(onClick = onAdd) {
                Icon(Icons.Default.Add, contentDescription = "Добавить")
            }
        }
    ) { inner ->
        LazyColumn(
            Modifier
                .fillMaxSize()
                .padding(inner),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { GymDiaryTopBar("Упражнения") }
            item {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = onQueryChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Поиск") },
                    singleLine = true
                )
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    EnumDropdown("Группа", state.muscleGroup, MuscleGroup.entries, { it.title }, onMuscleGroupChange, Modifier.weight(1f))
                    EnumDropdown("Оборудование", state.equipment, Equipment.entries, { it.title }, onEquipmentChange, Modifier.weight(1f))
                }
            }
            if (state.exercises.isEmpty()) {
                item { EmptyState("Нет упражнений") }
            } else {
                items(state.exercises, key = { it.id }) { exercise ->
                    ExerciseCard(exercise, onClick = { onEdit(exercise.id) })
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> EnumDropdown(label: String, selected: T?, values: List<T>, title: (T) -> String, onSelected: (T?) -> Unit, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = modifier) {
        OutlinedTextField(
            value = selected?.let(title) ?: "Все",
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, true)
                .fillMaxWidth()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text("Все") }, onClick = {
                onSelected(null)
                expanded = false
            })
            values.forEach { value ->
                DropdownMenuItem(text = { Text(title(value)) }, onClick = {
                    onSelected(value)
                    expanded = false
                })
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ExercisesPreview() {
    ExercisesScreen(ExercisesUiState(), PaddingValues(), {}, {}, {}, {}, {})
}
