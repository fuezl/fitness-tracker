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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
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
import ru.fuezl.gymdiary.core.common.formatKg
import ru.fuezl.gymdiary.core.common.formatTime
import ru.fuezl.gymdiary.core.model.ExerciseHistoryEntry
import ru.fuezl.gymdiary.core.model.MuscleGroup
import ru.fuezl.gymdiary.core.model.Exercise
import ru.fuezl.gymdiary.core.model.UserSettings
import ru.fuezl.gymdiary.core.model.WorkoutDetails
import ru.fuezl.gymdiary.core.model.WorkoutSetModel
import ru.fuezl.gymdiary.core.ui.EmptyState
import ru.fuezl.gymdiary.core.ui.ExerciseCard
import ru.fuezl.gymdiary.core.ui.GymDiaryTopBar
import ru.fuezl.gymdiary.core.ui.performGymHaptic
import ru.fuezl.gymdiary.core.ui.vibrateGymCue
import ru.fuezl.gymdiary.data.repository.ExerciseRepository
import ru.fuezl.gymdiary.data.repository.SettingsRepository
import ru.fuezl.gymdiary.data.repository.WorkoutRepository
import ru.fuezl.gymdiary.data.repository.WorkoutTemplateRepository
import ru.fuezl.gymdiary.data.repository.WorkoutTemplateSummary
import ru.fuezl.gymdiary.domain.usecase.SetValidator
import ru.fuezl.gymdiary.domain.usecase.TrainingCalculators
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import javax.inject.Inject

@HiltViewModel
class StartWorkoutViewModel @Inject constructor(
    private val repository: WorkoutRepository,
    private val templateRepository: WorkoutTemplateRepository,
) : ViewModel() {
    private val eventsChannel = Channel<Boolean>(Channel.BUFFERED)
    val events = eventsChannel.receiveAsFlow()
    val templates = templateRepository.observeTemplates().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun startEmpty() {
        viewModelScope.launch {
            repository.startWorkout("Тренировка ${formatTime(System.currentTimeMillis())}")
            eventsChannel.send(false)
        }
    }

    fun startTemplate(templateId: Long) {
        viewModelScope.launch {
            templateRepository.startWorkoutFromTemplate(templateId)
            eventsChannel.send(false)
        }
    }

    fun deleteTemplate(templateId: Long) {
        viewModelScope.launch { templateRepository.deleteTemplate(templateId) }
    }

    fun startBackfilled(title: String, date: String, startTime: String, finishTime: String, onError: (String) -> Unit) {
        val startedAt = parseBackfillDateTime(date, startTime.ifBlank { "12:00" })
        if (startedAt == null) {
            onError("Укажите дату в формате ГГГГ-ММ-ДД")
            return
        }
        val finishedAt = finishTime.takeIf { it.isNotBlank() }?.let {
            parseBackfillDateTime(date, it)
        }
        if (finishTime.isNotBlank() && finishedAt == null) {
            onError("Укажите время в формате ЧЧ:ММ")
            return
        }
        if (finishedAt != null && finishedAt < startedAt) {
            onError("Время финиша не может быть раньше старта")
            return
        }
        viewModelScope.launch {
            repository.startBackfilledWorkout(title, startedAt, finishedAt)
            eventsChannel.send(finishedAt != null)
        }
    }
}

private val backfillDateFormatter = DateTimeFormatter.ISO_LOCAL_DATE
private val backfillTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

private fun parseBackfillDateTime(date: String, time: String): Long? =
    try {
        LocalDateTime.of(
            LocalDate.parse(date.trim(), backfillDateFormatter),
            LocalTime.parse(time.trim(), backfillTimeFormatter),
        ).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    } catch (_: DateTimeParseException) {
        null
    }

@Composable
fun StartWorkoutRoute(
    contentPadding: PaddingValues,
    onActiveWorkout: () -> Unit,
    onHistory: () -> Unit = onActiveWorkout,
    viewModel: StartWorkoutViewModel = hiltViewModel(),
) {
    val templates by viewModel.templates.collectAsStateWithLifecycle()
    var backfillError by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { viewModel.events.collect { isFinished -> if (isFinished) onHistory() else onActiveWorkout() } }
    LazyColumn(
        Modifier.fillMaxSize().padding(contentPadding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { GymDiaryTopBar("Старт тренировки") }
        item { Button(onClick = viewModel::startEmpty, modifier = Modifier.fillMaxWidth()) { Text("Пустая тренировка") } }
        item {
            BackfillWorkoutCard(
                error = backfillError,
                onStart = { title, date, start, finish ->
                    backfillError = null
                    viewModel.startBackfilled(title, date, start, finish) { backfillError = it }
                },
            )
        }
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
    val exerciseHistory: Map<Long, List<ExerciseHistoryEntry>> = emptyMap(),
    val hapticsEnabled: Boolean = true,
    val durationSeconds: Long = 0,
    val isFinishDialogVisible: Boolean = false,
    val restTimerState: RestTimerState = RestTimerState(),
    val restFinishedSignal: Int = 0,
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
    val uiState = combine(workoutRepository.observeActiveWorkout(), workoutRepository.observeExerciseHistoryIndex(), settings, local) { workout, history, settings, localState ->
        localState.copy(
            workout = workout,
            exerciseHistory = history,
            hapticsEnabled = settings.hapticsEnabled,
            durationSeconds = workout?.let { ((System.currentTimeMillis() - it.summary.startedAt) / 1000) } ?: 0,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ActiveWorkoutUiState())
    private val finishChannel = Channel<Unit>(Channel.BUFFERED)
    val finishEvents = finishChannel.receiveAsFlow()
    private var previousRestSeconds = 0

    init {
        viewModelScope.launch {
            while (true) {
                delay(1000)
                local.update { state ->
                    val timer = state.restTimerState
                    val next = if (timer.remainingSeconds > 0 && !timer.isPaused) timer.copy(remainingSeconds = timer.remainingSeconds - 1) else timer
                    val signal = if (previousRestSeconds > 0 && next.remainingSeconds == 0 && settings.value.hapticsEnabled) state.restFinishedSignal + 1 else state.restFinishedSignal
                    previousRestSeconds = next.remainingSeconds
                    state.copy(durationSeconds = state.durationSeconds + 1, restTimerState = next, restFinishedSignal = signal, errorMessage = if (signal != state.restFinishedSignal) "Отдых завершён" else state.errorMessage)
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
    fun updateWorkoutNote(note: String, energy: String, sleep: String, pain: String) {
        val workout = uiState.value.workout ?: return
        viewModelScope.launch {
            workoutRepository.updateWorkoutNote(
                workout.summary.id,
                note,
                energy.toIntOrNull()?.coerceIn(1, 5),
                sleep.toIntOrNull()?.coerceIn(1, 5),
                pain,
            )
        }
    }
    fun updateExerciseNote(workoutExerciseId: Long, note: String) {
        viewModelScope.launch { workoutRepository.updateWorkoutExerciseNote(workoutExerciseId, note) }
    }
    fun addRestTime() = local.update { it.copy(restTimerState = it.restTimerState.copy(remainingSeconds = it.restTimerState.remainingSeconds + 30)) }
    fun skipRest() {
        previousRestSeconds = 0
        local.update { it.copy(restTimerState = RestTimerState()) }
    }
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
    ActiveWorkoutScreen(state, contentPadding, onAddExercise, viewModel::addSet, viewModel::updateSet, viewModel::completeSet, viewModel::deleteSet, viewModel::updateWorkoutNote, viewModel::updateExerciseNote, viewModel::showFinishDialog, viewModel::hideFinishDialog, viewModel::finish, viewModel::addRestTime, viewModel::skipRest, viewModel::pauseRest, viewModel::saveAsTemplate)
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
    onUpdateWorkoutNote: (String, String, String, String) -> Unit,
    onUpdateExerciseNote: (Long, String) -> Unit,
    onShowFinish: () -> Unit,
    onHideFinish: () -> Unit,
    onFinish: () -> Unit,
    onAddRest: () -> Unit,
    onSkipRest: () -> Unit,
    onPauseRest: () -> Unit,
    onSaveTemplate: () -> Unit,
) {
    val workout = state.workout
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(state.restFinishedSignal) {
        if (state.restFinishedSignal > 0) {
            context.vibrateGymCue(state.hapticsEnabled)
        }
    }
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
                    var workoutNote by remember(workout.summary.id, workout.note) { mutableStateOf(workout.note) }
                    var energy by remember(workout.summary.id, workout.summary.energyLevel) { mutableStateOf(workout.summary.energyLevel?.toString().orEmpty()) }
                    var sleep by remember(workout.summary.id, workout.summary.sleepQuality) { mutableStateOf(workout.summary.sleepQuality?.toString().orEmpty()) }
                    var pain by remember(workout.summary.id, workout.painNote) { mutableStateOf(workout.painNote) }
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(workout.summary.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        Text("Длительность: ${formatDuration(state.durationSeconds)}")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(energy, { energy = it; onUpdateWorkoutNote(workoutNote, energy, sleep, pain) }, label = { Text("Энергия 1-5") }, modifier = Modifier.weight(1f), singleLine = true)
                            OutlinedTextField(sleep, { sleep = it; onUpdateWorkoutNote(workoutNote, energy, sleep, pain) }, label = { Text("Сон 1-5") }, modifier = Modifier.weight(1f), singleLine = true)
                        }
                        OutlinedTextField(pain, { pain = it; onUpdateWorkoutNote(workoutNote, energy, sleep, pain) }, label = { Text("Боль/самочувствие") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(workoutNote, { workoutNote = it; onUpdateWorkoutNote(workoutNote, energy, sleep, pain) }, label = { Text("Заметка тренировки") }, modifier = Modifier.fillMaxWidth())
                        if (state.restTimerState.remainingSeconds > 0) {
                            Text("Отдых: ${state.restTimerState.remainingSeconds} сек")
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { haptics.performGymHaptic(state.hapticsEnabled); onAddRest() }) { Text("+30 сек") }
                                OutlinedButton(onClick = { haptics.performGymHaptic(state.hapticsEnabled); onPauseRest() }) { Text(if (state.restTimerState.isPaused) "Продолжить" else "Пауза") }
                                OutlinedButton(onClick = { haptics.performGymHaptic(state.hapticsEnabled, HapticFeedbackType.LongPress); onSkipRest() }) { Text("Пропустить") }
                            }
                        }
                        state.errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
                    }
                }
            }
            item {
                Button(onClick = { haptics.performGymHaptic(state.hapticsEnabled); onAddExercise(workout.summary.id) }, modifier = Modifier.fillMaxWidth()) {
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
                            val history = state.exerciseHistory[exercise.exerciseId].orEmpty()
                            ExerciseTrainingHints(history)
                            var exerciseNote by remember(exercise.workoutExerciseId, exercise.note) { mutableStateOf(exercise.note) }
                            OutlinedTextField(
                                exerciseNote,
                                { exerciseNote = it; onUpdateExerciseNote(exercise.workoutExerciseId, exerciseNote) },
                                label = { Text("Заметка к упражнению") },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            exercise.sets.forEach { set ->
                                SetRow(set, history.firstOrNull()?.sets?.getOrNull(set.setNumber - 1), state.hapticsEnabled, onUpdateSet, onCompleteSet, onDeleteSet)
                            }
                            OutlinedButton(onClick = { haptics.performGymHaptic(state.hapticsEnabled); onAddSet(exercise.workoutExerciseId) }, modifier = Modifier.testTag("add_set")) {
                                Text("Добавить подход")
                            }
                        }
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = { haptics.performGymHaptic(state.hapticsEnabled); onSaveTemplate() }, modifier = Modifier.weight(1f)) { Text("В шаблон") }
                    Button(onClick = { haptics.performGymHaptic(state.hapticsEnabled, HapticFeedbackType.LongPress); onShowFinish() }, modifier = Modifier.weight(1f)) { Text("Завершить") }
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
            confirmButton = { TextButton(onClick = { haptics.performGymHaptic(state.hapticsEnabled, HapticFeedbackType.LongPress); onFinish() }) { Text("Завершить") } },
            dismissButton = { TextButton(onClick = onHideFinish) { Text("Отмена") } },
        )
    }
}

@Composable
fun SetRow(
    set: WorkoutSetModel,
    previousSet: WorkoutSetModel?,
    hapticsEnabled: Boolean,
    onUpdateSet: (WorkoutSetModel, String, String, String, String) -> Unit,
    onCompleteSet: (WorkoutSetModel, Boolean) -> Unit,
    onDeleteSet: (Long) -> Unit,
) {
    var weight by remember(set.id, set.weightKg) { mutableStateOf(if (set.weightKg == 0.0) "" else set.weightKg.toString()) }
    var reps by remember(set.id, set.reps) { mutableStateOf(if (set.reps == 0) "" else set.reps.toString()) }
    var rpe by remember(set.id, set.rpe) { mutableStateOf(set.rpe?.toString().orEmpty()) }
    var note by remember(set.id, set.note) { mutableStateOf(set.note) }
    val haptics = LocalHapticFeedback.current
    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.testTag("set_row")) {
        Text("Подход ${set.setNumber}")
        previousSet?.let { Text("Прошлый раз: ${it.weightKg.formatKg()} × ${it.reps}${it.rpe?.let { rpe -> " • RPE $rpe" }.orEmpty()}", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(weight, { weight = it; onUpdateSet(set, weight, reps, rpe, note) }, label = { Text("Вес") }, modifier = Modifier.weight(1f), singleLine = true)
            OutlinedTextField(reps, { reps = it; onUpdateSet(set, weight, reps, rpe, note) }, label = { Text("Повт.") }, modifier = Modifier.weight(1f), singleLine = true)
            OutlinedTextField(rpe, { rpe = it; onUpdateSet(set, weight, reps, rpe, note) }, label = { Text("RPE") }, modifier = Modifier.weight(1f), singleLine = true)
            Checkbox(checked = set.isCompleted, onCheckedChange = { haptics.performGymHaptic(hapticsEnabled, HapticFeedbackType.LongPress); onCompleteSet(set, it) })
            IconButton(onClick = { haptics.performGymHaptic(hapticsEnabled, HapticFeedbackType.LongPress); onDeleteSet(set.id) }) { Text("×") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = { haptics.performGymHaptic(hapticsEnabled); weight = ((weight.replace(',', '.').toDoubleOrNull() ?: 0.0) + 2.5).toString(); onUpdateSet(set, weight, reps, rpe, note) }, modifier = Modifier.weight(1f)) { Text("+2.5") }
            OutlinedButton(onClick = { haptics.performGymHaptic(hapticsEnabled); weight = (((weight.replace(',', '.').toDoubleOrNull() ?: 0.0) - 2.5).coerceAtLeast(0.0)).toString(); onUpdateSet(set, weight, reps, rpe, note) }, modifier = Modifier.weight(1f)) { Text("-2.5") }
            OutlinedButton(onClick = { haptics.performGymHaptic(hapticsEnabled); reps = ((reps.toIntOrNull() ?: 0) + 1).toString(); onUpdateSet(set, weight, reps, rpe, note) }, modifier = Modifier.weight(1f)) { Text("+1 повт.") }
            previousSet?.let {
                OutlinedButton(onClick = {
                    haptics.performGymHaptic(hapticsEnabled)
                    weight = it.weightKg.toString()
                    reps = it.reps.toString()
                    rpe = it.rpe?.toString().orEmpty()
                    onUpdateSet(set, weight, reps, rpe, note)
                }, modifier = Modifier.weight(1f)) { Text("Копия") }
            }
        }
        OutlinedTextField(note, { note = it; onUpdateSet(set, weight, reps, rpe, note) }, label = { Text("Комментарий") }, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun BackfillWorkoutCard(
    error: String?,
    onStart: (String, String, String, String) -> Unit,
) {
    val today = remember { LocalDate.now().format(backfillDateFormatter) }
    var title by remember { mutableStateOf("Старая тренировка") }
    var date by remember { mutableStateOf(today) }
    var startTime by remember { mutableStateOf("") }
    var finishTime by remember { mutableStateOf("") }
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Внести старую тренировку", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            OutlinedTextField(title, { title = it }, label = { Text("Название") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(date, { date = it }, label = { Text("Дата: ГГГГ-ММ-ДД") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(startTime, { startTime = it }, label = { Text("Старт ЧЧ:ММ") }, modifier = Modifier.weight(1f), singleLine = true)
                OutlinedTextField(finishTime, { finishTime = it }, label = { Text("Финиш ЧЧ:ММ") }, modifier = Modifier.weight(1f), singleLine = true)
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(onClick = { onStart(title, date, startTime, finishTime) }, modifier = Modifier.fillMaxWidth()) {
                Text("Создать старую тренировку")
            }
        }
    }
}

@Composable
private fun ExerciseTrainingHints(history: List<ExerciseHistoryEntry>) {
    val last = history.firstOrNull() ?: return
    val bestSet = last.sets.maxWithOrNull(compareBy<WorkoutSetModel> { TrainingCalculators.estimatedOneRm(it.weightKg, it.reps) ?: 0.0 }.thenBy { it.weightKg })
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Последняя тренировка: ${last.maxWeight.formatKg()} • объём ${last.volume.formatKg()}", color = MaterialTheme.colorScheme.onSurfaceVariant)
        bestSet?.let {
            val nextWeight = if ((it.rpe ?: 8.0) <= 8.0 && it.reps >= 8) it.weightKg + 2.5 else it.weightKg
            val nextReps = if (nextWeight > it.weightKg) 6 else (it.reps + 1).coerceAtMost(12)
            Text("План: ${nextWeight.formatKg()} × $nextReps", color = MaterialTheme.colorScheme.primary)
            val warmups = listOf(0.5, 0.7, 0.85).map { ratio -> (nextWeight * ratio).coerceAtLeast(20.0) }
            Text("Разминка: ${warmups.joinToString(" • ") { weight -> "${weight.formatKg()} × ${if (weight < nextWeight * 0.75) 5 else 3}" }}", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@HiltViewModel
class AddExerciseToWorkoutViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    exerciseRepository: ExerciseRepository,
    private val workoutRepository: WorkoutRepository,
    settingsRepository: SettingsRepository,
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
    val hapticsEnabled = settingsRepository.observeSettings()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UserSettings())
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
    val settings by viewModel.hapticsEnabled.collectAsStateWithLifecycle()
    val haptics = LocalHapticFeedback.current
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
            ExerciseCard(exercise, onClick = { haptics.performGymHaptic(settings.hapticsEnabled); viewModel.add(exercise) })
        }
    }
}
