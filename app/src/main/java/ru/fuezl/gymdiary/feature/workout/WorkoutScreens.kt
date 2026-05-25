package ru.fuezl.gymdiary.feature.workout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.fuezl.gymdiary.core.common.formatDuration
import ru.fuezl.gymdiary.core.common.formatTime
import ru.fuezl.gymdiary.core.model.MuscleGroup
import ru.fuezl.gymdiary.core.model.Exercise
import ru.fuezl.gymdiary.core.model.UserSettings
import ru.fuezl.gymdiary.core.model.WorkoutDetails
import ru.fuezl.gymdiary.core.model.WorkoutSetModel
import ru.fuezl.gymdiary.core.ui.EmptyState
import ru.fuezl.gymdiary.core.ui.ExerciseCard
import ru.fuezl.gymdiary.core.ui.GymDiaryTopBar
import ru.fuezl.gymdiary.data.repository.ExerciseRepository
import ru.fuezl.gymdiary.data.repository.SettingsRepository
import ru.fuezl.gymdiary.data.repository.WorkoutRepository
import ru.fuezl.gymdiary.data.repository.WorkoutTemplateRepository
import ru.fuezl.gymdiary.data.repository.WorkoutTemplateSummary
import ru.fuezl.gymdiary.domain.usecase.SetValidator
import javax.inject.Inject

@HiltViewModel
class StartWorkoutViewModel @Inject constructor(
    private val repository: WorkoutRepository,
    private val templateRepository: WorkoutTemplateRepository,
) : ViewModel() {
    private val eventsChannel = Channel<Unit>(Channel.BUFFERED)
    val events = eventsChannel.receiveAsFlow()
    val templates = templateRepository.observeTemplates().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun startEmpty() {
        viewModelScope.launch {
            repository.startWorkout("Тренировка ${formatTime(System.currentTimeMillis())}")
            eventsChannel.send(Unit)
        }
    }

    fun startTemplate(templateId: Long) {
        viewModelScope.launch {
            templateRepository.startWorkoutFromTemplate(templateId)
            eventsChannel.send(Unit)
        }
    }

    fun deleteTemplate(templateId: Long) {
        viewModelScope.launch { templateRepository.deleteTemplate(templateId) }
    }
}

@Composable
fun StartWorkoutRoute(
    contentPadding: PaddingValues,
    onActiveWorkout: () -> Unit,
    viewModel: StartWorkoutViewModel = hiltViewModel(),
) {
    val templates by viewModel.templates.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.events.collect { onActiveWorkout() } }
    LazyColumn(
        Modifier.fillMaxSize().padding(contentPadding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { GymDiaryTopBar("Старт тренировки") }
        item { Button(onClick = viewModel::startEmpty, modifier = Modifier.fillMaxWidth()) { Text("Пустая тренировка") } }
        item { Text("Шаблоны", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
        if (templates.isEmpty()) {
            item { EmptyState("Сохраните любую тренировку как шаблон") }
        } else {
            items(templates, key = { it.id }) { template ->
                TemplateCard(
                    template = template,
                    onStart = { viewModel.startTemplate(template.id) },
                    onDelete = { viewModel.deleteTemplate(template.id) },
                )
            }
        }
    }
}

@Composable
private fun TemplateCard(
    template: WorkoutTemplateSummary,
    onStart: () -> Unit,
    onDelete: () -> Unit,
) {
    var confirmDelete by remember { mutableStateOf(false) }
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(template.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text("Упражнений: ${template.exerciseCount}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (template.note.isNotBlank()) Text(template.note)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onStart, modifier = Modifier.weight(1f)) { Text("Начать") }
                OutlinedButton(onClick = { confirmDelete = true }, modifier = Modifier.weight(1f)) { Text("Удалить") }
            }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Удалить шаблон?") },
            text = { Text("Шаблон будет удалён, история тренировок не изменится.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }) { Text("Удалить") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Отмена") } },
        )
    }
}

data class RestTimerState(
    val remainingSeconds: Int = 0,
    val isPaused: Boolean = false,
)

data class ActiveWorkoutUiState(
    val workout: WorkoutDetails? = null,
    val durationSeconds: Long = 0,
    val isFinishDialogVisible: Boolean = false,
    val restTimerState: RestTimerState = RestTimerState(),
    val errorMessage: String? = null,
)

@HiltViewModel
class ActiveWorkoutViewModel @Inject constructor(
    private val workoutRepository: WorkoutRepository,
    private val templateRepository: WorkoutTemplateRepository,
    settingsRepository: SettingsRepository,
) : ViewModel() {
    private val local = MutableStateFlow(ActiveWorkoutUiState())
    private val settings = settingsRepository.observeSettings().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UserSettings())
    val uiState = combine(workoutRepository.observeActiveWorkout(), local) { workout, localState ->
        localState.copy(workout = workout, durationSeconds = workout?.let { ((System.currentTimeMillis() - it.summary.startedAt) / 1000) } ?: 0)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ActiveWorkoutUiState())
    private val finishChannel = Channel<Unit>(Channel.BUFFERED)
    val finishEvents = finishChannel.receiveAsFlow()

    init {
        viewModelScope.launch {
            while (true) {
                delay(1000)
                local.update { state ->
                    val timer = state.restTimerState
                    val next = if (timer.remainingSeconds > 0 && !timer.isPaused) timer.copy(remainingSeconds = timer.remainingSeconds - 1) else timer
                    state.copy(durationSeconds = state.durationSeconds + 1, restTimerState = next)
                }
            }
        }
    }

    fun showFinishDialog() = local.update { it.copy(isFinishDialogVisible = true) }
    fun hideFinishDialog() = local.update { it.copy(isFinishDialogVisible = false) }

    fun addSet(workoutExerciseId: Long) = viewModelScope.launch { workoutRepository.addSet(workoutExerciseId) }

    fun updateSet(set: WorkoutSetModel, weight: String, reps: String, rpe: String, note: String) {
        val weightValue = weight.replace(',', '.').toDoubleOrNull() ?: 0.0
        val repsValue = reps.toIntOrNull() ?: 0
        val rpeValue = rpe.replace(',', '.').toDoubleOrNull()
        val error = SetValidator.validate(weightValue, repsValue, rpeValue)
        if (error != null) {
            local.update { it.copy(errorMessage = error) }
            return
        }
        viewModelScope.launch { workoutRepository.updateSet(set.copy(weightKg = weightValue, reps = repsValue, rpe = rpeValue, note = note)) }
    }

    fun completeSet(set: WorkoutSetModel, completed: Boolean) {
        viewModelScope.launch {
            workoutRepository.completeSet(set.id, completed)
            if (completed && settings.value.restTimerEnabled) {
                local.update { it.copy(restTimerState = RestTimerState(settings.value.defaultRestTimerSeconds)) }
            }
        }
    }

    fun deleteSet(setId: Long) = viewModelScope.launch { workoutRepository.deleteSet(setId) }
    fun addRestTime() = local.update { it.copy(restTimerState = it.restTimerState.copy(remainingSeconds = it.restTimerState.remainingSeconds + 30)) }
    fun skipRest() = local.update { it.copy(restTimerState = RestTimerState()) }
    fun pauseRest() = local.update { it.copy(restTimerState = it.restTimerState.copy(isPaused = !it.restTimerState.isPaused)) }

    fun finish() {
        val workout = uiState.value.workout ?: return
        viewModelScope.launch {
            workoutRepository.finishWorkout(workout.summary.id)
            local.update { it.copy(isFinishDialogVisible = false) }
            finishChannel.send(Unit)
        }
    }

    fun saveAsTemplate() {
        val workout = uiState.value.workout ?: return
        viewModelScope.launch {
            templateRepository.createTemplateFromWorkout(workout.summary.id, workout.summary.title, workout.note)
            local.update { it.copy(errorMessage = "Шаблон сохранён") }
        }
    }
}

@Composable
fun ActiveWorkoutRoute(
    contentPadding: PaddingValues,
    onAddExercise: (Long) -> Unit,
    onHistory: () -> Unit,
    viewModel: ActiveWorkoutViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.finishEvents.collect { onHistory() } }
    ActiveWorkoutScreen(state, contentPadding, onAddExercise, viewModel::addSet, viewModel::updateSet, viewModel::completeSet, viewModel::deleteSet, viewModel::showFinishDialog, viewModel::hideFinishDialog, viewModel::finish, viewModel::addRestTime, viewModel::skipRest, viewModel::pauseRest, viewModel::saveAsTemplate)
}

@Composable
fun ActiveWorkoutScreen(
    state: ActiveWorkoutUiState,
    contentPadding: PaddingValues,
    onAddExercise: (Long) -> Unit,
    onAddSet: (Long) -> Unit,
    onUpdateSet: (WorkoutSetModel, String, String, String, String) -> Unit,
    onCompleteSet: (WorkoutSetModel, Boolean) -> Unit,
    onDeleteSet: (Long) -> Unit,
    onShowFinish: () -> Unit,
    onHideFinish: () -> Unit,
    onFinish: () -> Unit,
    onAddRest: () -> Unit,
    onSkipRest: () -> Unit,
    onPauseRest: () -> Unit,
    onSaveTemplate: () -> Unit,
) {
    val workout = state.workout
    LazyColumn(
        Modifier.fillMaxSize().padding(contentPadding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { GymDiaryTopBar("Активная тренировка") }
        if (workout == null) {
            item { EmptyState("Активной тренировки нет") }
        } else {
            item {
                Card {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(workout.summary.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        Text("Длительность: ${formatDuration(state.durationSeconds)}")
                        if (state.restTimerState.remainingSeconds > 0) {
                            Text("Отдых: ${state.restTimerState.remainingSeconds} сек")
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = onAddRest) { Text("+30 сек") }
                                OutlinedButton(onClick = onPauseRest) { Text(if (state.restTimerState.isPaused) "Продолжить" else "Пауза") }
                                OutlinedButton(onClick = onSkipRest) { Text("Пропустить") }
                            }
                        }
                        state.errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
                    }
                }
            }
            item {
                Button(onClick = { onAddExercise(workout.summary.id) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Text("Добавить упражнение", Modifier.padding(start = 8.dp))
                }
            }
            if (workout.exercises.isEmpty()) {
                item { EmptyState("Добавьте первое упражнение") }
            } else {
                items(workout.exercises, key = { it.workoutExerciseId }) { exercise ->
                    Card {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(exercise.exerciseName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            exercise.sets.forEach { set ->
                                SetRow(set, onUpdateSet, onCompleteSet, onDeleteSet)
                            }
                            OutlinedButton(onClick = { onAddSet(exercise.workoutExerciseId) }, modifier = Modifier.testTag("add_set")) {
                                Text("Добавить подход")
                            }
                        }
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = onSaveTemplate, modifier = Modifier.weight(1f)) { Text("В шаблон") }
                    Button(onClick = onShowFinish, modifier = Modifier.weight(1f)) { Text("Завершить") }
                }
            }
        }
    }
    if (state.isFinishDialogVisible) {
        AlertDialog(
            onDismissRequest = onHideFinish,
            title = { Text("Завершить тренировку?") },
            text = {
                Text(
                    when {
                        workout?.exercises.isNullOrEmpty() -> "В тренировке нет упражнений. Завершить без сохранения полезных данных?"
                        workout.exercises.any { exercise -> exercise.sets.none { it.isCompleted } } -> "Есть упражнения без выполненных подходов. Завершить тренировку?"
                        else -> "Тренировка будет сохранена в историю."
                    },
                )
            },
            confirmButton = { TextButton(onClick = onFinish) { Text("Завершить") } },
            dismissButton = { TextButton(onClick = onHideFinish) { Text("Отмена") } },
        )
    }
}

@Composable
fun SetRow(
    set: WorkoutSetModel,
    onUpdateSet: (WorkoutSetModel, String, String, String, String) -> Unit,
    onCompleteSet: (WorkoutSetModel, Boolean) -> Unit,
    onDeleteSet: (Long) -> Unit,
) {
    var weight by remember(set.id, set.weightKg) { mutableStateOf(if (set.weightKg == 0.0) "" else set.weightKg.toString()) }
    var reps by remember(set.id, set.reps) { mutableStateOf(if (set.reps == 0) "" else set.reps.toString()) }
    var rpe by remember(set.id, set.rpe) { mutableStateOf(set.rpe?.toString().orEmpty()) }
    var note by remember(set.id, set.note) { mutableStateOf(set.note) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.testTag("set_row")) {
        Text("Подход ${set.setNumber}")
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(weight, { weight = it; onUpdateSet(set, weight, reps, rpe, note) }, label = { Text("Вес") }, modifier = Modifier.weight(1f), singleLine = true)
            OutlinedTextField(reps, { reps = it; onUpdateSet(set, weight, reps, rpe, note) }, label = { Text("Повт.") }, modifier = Modifier.weight(1f), singleLine = true)
            OutlinedTextField(rpe, { rpe = it; onUpdateSet(set, weight, reps, rpe, note) }, label = { Text("RPE") }, modifier = Modifier.weight(1f), singleLine = true)
            Checkbox(checked = set.isCompleted, onCheckedChange = { onCompleteSet(set, it) })
            IconButton(onClick = { onDeleteSet(set.id) }) { Text("×") }
        }
        OutlinedTextField(note, { note = it; onUpdateSet(set, weight, reps, rpe, note) }, label = { Text("Комментарий") }, modifier = Modifier.fillMaxWidth())
    }
}

@HiltViewModel
class AddExerciseToWorkoutViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    exerciseRepository: ExerciseRepository,
    private val workoutRepository: WorkoutRepository,
) : ViewModel() {
    private val workoutId: Long = savedStateHandle["workoutId"] ?: 0L
    private val query = MutableStateFlow("")
    private val muscleGroup = MutableStateFlow<MuscleGroup?>(null)
    val exercises = combine(exerciseRepository.observeExercises(), query, muscleGroup) { exercises, q, group ->
        exercises.filter { exercise ->
            (q.isBlank() || exercise.name.contains(q, ignoreCase = true)) &&
                (group == null || exercise.muscleGroup == group)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private val eventsChannel = Channel<Unit>(Channel.BUFFERED)
    val events = eventsChannel.receiveAsFlow()

    fun onQueryChange(value: String) {
        query.value = value
    }

    fun onMuscleGroupChange(value: MuscleGroup?) {
        muscleGroup.value = value
    }

    fun add(exercise: Exercise) {
        viewModelScope.launch {
            workoutRepository.addExerciseToWorkout(workoutId, exercise.id)
            eventsChannel.send(Unit)
        }
    }
}

@Composable
fun AddExerciseToWorkoutRoute(
    onBack: () -> Unit,
    viewModel: AddExerciseToWorkoutViewModel = hiltViewModel(),
) {
    val exercises by viewModel.exercises.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    var group by remember { mutableStateOf<MuscleGroup?>(null) }
    LaunchedEffect(Unit) { viewModel.events.collect { onBack() } }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { GymDiaryTopBar("Добавить упражнение", onBack) }
        item {
            OutlinedTextField(
                value = query,
                onValueChange = {
                    query = it
                    viewModel.onQueryChange(it)
                },
                label = { Text("Поиск") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
        }
        item {
            ru.fuezl.gymdiary.feature.exercises.EnumDropdown(
                label = "Группа мышц",
                selected = group,
                values = MuscleGroup.entries,
                title = { it.title },
                onSelected = {
                    group = it
                    viewModel.onMuscleGroupChange(it)
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        items(exercises, key = { it.id }) { exercise ->
            ExerciseCard(exercise, onClick = { viewModel.add(exercise) })
        }
    }
}
