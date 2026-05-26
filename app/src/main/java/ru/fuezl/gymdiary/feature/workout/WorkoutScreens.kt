package ru.fuezl.gymdiary.feature.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.fuezl.gymdiary.core.common.formatDuration
import ru.fuezl.gymdiary.core.common.formatKg
import ru.fuezl.gymdiary.core.common.formatTime
import ru.fuezl.gymdiary.core.model.Exercise
import ru.fuezl.gymdiary.core.model.ExerciseHistoryEntry
import ru.fuezl.gymdiary.core.model.MuscleGroup
import ru.fuezl.gymdiary.core.model.UserSettings
import ru.fuezl.gymdiary.core.model.WorkoutDetails
import ru.fuezl.gymdiary.core.model.WorkoutExerciseDetails
import ru.fuezl.gymdiary.core.model.WorkoutSetModel
import ru.fuezl.gymdiary.core.ui.EmptyState
import ru.fuezl.gymdiary.core.ui.ExerciseCard
import ru.fuezl.gymdiary.core.ui.GymDiaryTopBar
import ru.fuezl.gymdiary.core.ui.MetricChip
import ru.fuezl.gymdiary.core.ui.SectionTitle
import ru.fuezl.gymdiary.core.ui.performGymHaptic
import ru.fuezl.gymdiary.core.ui.vibrateGymCue
import ru.fuezl.gymdiary.data.repository.ExerciseRepository
import ru.fuezl.gymdiary.data.repository.SettingsRepository
import ru.fuezl.gymdiary.data.repository.WorkoutRepository
import ru.fuezl.gymdiary.data.repository.WorkoutTemplateRepository
import ru.fuezl.gymdiary.data.repository.WorkoutTemplateSummary
import ru.fuezl.gymdiary.domain.usecase.SetValidator
import ru.fuezl.gymdiary.domain.usecase.TrainingCalculators

@HiltViewModel
class StartWorkoutViewModel @Inject constructor(private val repository: WorkoutRepository, private val templateRepository: WorkoutTemplateRepository) : ViewModel() {
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

private fun parseBackfillDateTime(date: String, time: String): Long? = try {
    LocalDateTime.of(
        LocalDate.parse(date.trim(), backfillDateFormatter),
        LocalTime.parse(time.trim(), backfillTimeFormatter)
    ).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
} catch (_: DateTimeParseException) {
    null
}

@Composable
fun StartWorkoutRoute(contentPadding: PaddingValues, onActiveWorkout: () -> Unit, onHistory: () -> Unit = onActiveWorkout, viewModel: StartWorkoutViewModel = hiltViewModel()) {
    val templates by viewModel.templates.collectAsStateWithLifecycle()
    var backfillError by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { viewModel.events.collect { isFinished -> if (isFinished) onHistory() else onActiveWorkout() } }
    LazyColumn(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(contentPadding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { GymDiaryTopBar("Старт тренировки") }
        item { Button(onClick = viewModel::startEmpty, modifier = Modifier.fillMaxWidth()) { Text("Начать тренировку") } }
        item {
            BackfillWorkoutCard(
                error = backfillError,
                onStart = { title, date, start, finish ->
                    backfillError = null
                    viewModel.startBackfilled(title, date, start, finish) { backfillError = it }
                }
            )
        }
        item { Text("Шаблоны", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
        if (templates.isEmpty()) {
            item { EmptyState("Сохраните любую тренировку как шаблон") }
        } else {
            items(templates, key = { it.id }, contentType = { "template" }) { template ->
                TemplateCard(
                    template = template,
                    onStart = { viewModel.startTemplate(template.id) },
                    onDelete = { viewModel.deleteTemplate(template.id) }
                )
            }
        }
    }
}

@Composable
private fun TemplateCard(template: WorkoutTemplateSummary, onStart: () -> Unit, onDelete: () -> Unit) {
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

data class RestTimerState(val remainingSeconds: Int = 0, val isPaused: Boolean = false)

data class ActiveWorkoutUiState(
    val workout: WorkoutDetails? = null,
    val exerciseHistory: Map<Long, List<ExerciseHistoryEntry>> = emptyMap(),
    val hapticsEnabled: Boolean = true,
    val durationSeconds: Long = 0,
    val isFinishDialogVisible: Boolean = false,
    val restTimerState: RestTimerState = RestTimerState(),
    val restFinishedSignal: Int = 0,
    val errorMessage: String? = null
)

@HiltViewModel
class ActiveWorkoutViewModel @Inject constructor(private val workoutRepository: WorkoutRepository, private val templateRepository: WorkoutTemplateRepository, settingsRepository: SettingsRepository) :
    ViewModel() {
    private val local = MutableStateFlow(ActiveWorkoutUiState())
    private val settings = settingsRepository.observeSettings().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UserSettings())
    val uiState = combine(workoutRepository.observeActiveWorkout(), workoutRepository.observeExerciseHistoryIndex(), settings, local) {
            workout,
            history,
            settings,
            localState
        ->
        localState.copy(
            workout = workout,
            exerciseHistory = history,
            hapticsEnabled = settings.hapticsEnabled,
            durationSeconds = workout?.let { ((System.currentTimeMillis() - it.summary.startedAt) / 1000) } ?: 0
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ActiveWorkoutUiState())
    private val finishChannel = Channel<Unit>(Channel.BUFFERED)
    val finishEvents = finishChannel.receiveAsFlow()
    private var previousRestSeconds = 0
    private val setUpdateJobs = mutableMapOf<Long, Job>()
    private var workoutNoteJob: Job? = null
    private val exerciseNoteJobs = mutableMapOf<Long, Job>()

    init {
        viewModelScope.launch {
            while (true) {
                delay(1000)
                if (uiState.value.workout == null && local.value.restTimerState.remainingSeconds == 0) continue
                local.update { state ->
                    val timer = state.restTimerState
                    val next = if (timer.remainingSeconds > 0 && !timer.isPaused) timer.copy(remainingSeconds = timer.remainingSeconds - 1) else timer
                    val signal = if (previousRestSeconds > 0 &&
                        next.remainingSeconds == 0 &&
                        settings.value.hapticsEnabled
                    ) {
                        state.restFinishedSignal + 1
                    } else {
                        state.restFinishedSignal
                    }
                    previousRestSeconds = next.remainingSeconds
                    state.copy(
                        durationSeconds = state.durationSeconds + 1,
                        restTimerState = next,
                        restFinishedSignal = signal,
                        errorMessage = if (signal !=
                            state.restFinishedSignal
                        ) {
                            "Отдых завершён"
                        } else {
                            state.errorMessage
                        }
                    )
                }
            }
        }
    }

    fun showFinishDialog() = local.update { it.copy(isFinishDialogVisible = true) }
    fun hideFinishDialog() = local.update { it.copy(isFinishDialogVisible = false) }

    fun addSet(workoutExerciseId: Long) = viewModelScope.launch { workoutRepository.addSet(workoutExerciseId) }

    fun updateSet(set: WorkoutSetModel, weight: String, reps: String, note: String) {
        val weightValue = weight.replace(',', '.').toDoubleOrNull() ?: 0.0
        val repsValue = reps.toIntOrNull() ?: 0
        val error = SetValidator.validate(weightValue, repsValue)
        if (error != null) {
            local.update { it.copy(errorMessage = error) }
            return
        }
        if (set.weightKg == weightValue && set.reps == repsValue && set.note == note) return
        setUpdateJobs[set.id]?.cancel()
        setUpdateJobs[set.id] = viewModelScope.launch {
            delay(INPUT_SAVE_DEBOUNCE_MS)
            workoutRepository.updateSet(set.copy(weightKg = weightValue, reps = repsValue, note = note))
            setUpdateJobs.remove(set.id)
        }
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
    fun updateWorkoutNote(note: String, pain: String) {
        val workout = uiState.value.workout ?: return
        if (workout.note == note.trim() && workout.painNote == pain.trim()) return
        workoutNoteJob?.cancel()
        workoutNoteJob = viewModelScope.launch {
            delay(INPUT_SAVE_DEBOUNCE_MS)
            workoutRepository.updateWorkoutNote(
                workout.summary.id,
                note,
                workout.summary.energyLevel,
                workout.summary.sleepQuality,
                pain
            )
        }
    }
    fun updateExerciseNote(workoutExerciseId: Long, note: String) {
        val currentNote = uiState.value.workout
            ?.exercises
            ?.firstOrNull { it.workoutExerciseId == workoutExerciseId }
            ?.note
            .orEmpty()
        if (currentNote == note.trim()) return
        exerciseNoteJobs[workoutExerciseId]?.cancel()
        exerciseNoteJobs[workoutExerciseId] = viewModelScope.launch {
            delay(INPUT_SAVE_DEBOUNCE_MS)
            workoutRepository.updateWorkoutExerciseNote(workoutExerciseId, note)
            exerciseNoteJobs.remove(workoutExerciseId)
        }
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
        if (workout.exercises.isEmpty()) {
            local.update { it.copy(errorMessage = "Добавьте упражнения перед сохранением шаблона") }
            return
        }
        viewModelScope.launch {
            val templateId = templateRepository.createTemplateFromWorkout(workout.summary.id, workout.summary.title, workout.note)
            local.update {
                it.copy(
                    errorMessage = if (templateId == 0L) {
                        "Добавьте упражнения перед сохранением шаблона"
                    } else {
                        "Шаблон сохранён"
                    }
                )
            }
        }
    }
}

private const val INPUT_SAVE_DEBOUNCE_MS = 300L

private data class ActiveWorkoutActions(
    val onAddExercise: (Long) -> Unit,
    val onShowFinish: () -> Unit,
    val onHideFinish: () -> Unit,
    val onFinish: () -> Unit,
    val onSaveTemplate: () -> Unit,
    val setActions: ActiveSetActions,
    val noteActions: ActiveNoteActions,
    val restActions: ActiveRestActions
)

private data class ActiveSetActions(
    val onAddSet: (Long) -> Unit,
    val onUpdateSet: (WorkoutSetModel, String, String, String) -> Unit,
    val onCompleteSet: (WorkoutSetModel, Boolean) -> Unit,
    val onDeleteSet: (Long) -> Unit
)

private data class ActiveNoteActions(
    val onUpdateWorkoutNote: (String, String) -> Unit,
    val onUpdateExerciseNote: (Long, String) -> Unit
)

private data class ActiveRestActions(
    val onAddRest: () -> Unit,
    val onSkipRest: () -> Unit,
    val onPauseRest: () -> Unit
)

@Composable
fun ActiveWorkoutRoute(
    contentPadding: PaddingValues,
    onAddExercise: (Long) -> Unit,
    onHistory: () -> Unit,
    viewModel: ActiveWorkoutViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.finishEvents.collect { onHistory() } }
    ActiveWorkoutScreen(
        state,
        contentPadding,
        onAddExercise,
        viewModel::addSet,
        viewModel::updateSet,
        viewModel::completeSet,
        viewModel::deleteSet,
        viewModel::updateWorkoutNote,
        viewModel::updateExerciseNote,
        viewModel::showFinishDialog,
        viewModel::hideFinishDialog,
        viewModel::finish,
        viewModel::addRestTime,
        viewModel::skipRest,
        viewModel::pauseRest,
        viewModel::saveAsTemplate
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ActiveWorkoutScreen(
    state: ActiveWorkoutUiState,
    contentPadding: PaddingValues,
    onAddExercise: (Long) -> Unit,
    onAddSet: (Long) -> Unit,
    onUpdateSet: (WorkoutSetModel, String, String, String) -> Unit,
    onCompleteSet: (WorkoutSetModel, Boolean) -> Unit,
    onDeleteSet: (Long) -> Unit,
    onUpdateWorkoutNote: (String, String) -> Unit,
    onUpdateExerciseNote: (Long, String) -> Unit,
    onShowFinish: () -> Unit,
    onHideFinish: () -> Unit,
    onFinish: () -> Unit,
    onAddRest: () -> Unit,
    onSkipRest: () -> Unit,
    onPauseRest: () -> Unit,
    onSaveTemplate: () -> Unit
) {
    val workout = state.workout
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val actions = ActiveWorkoutActions(
        onAddExercise = onAddExercise,
        onShowFinish = onShowFinish,
        onHideFinish = onHideFinish,
        onFinish = onFinish,
        onSaveTemplate = onSaveTemplate,
        setActions = ActiveSetActions(onAddSet, onUpdateSet, onCompleteSet, onDeleteSet),
        noteActions = ActiveNoteActions(onUpdateWorkoutNote, onUpdateExerciseNote),
        restActions = ActiveRestActions(onAddRest, onSkipRest, onPauseRest)
    )
    LaunchedEffect(state.restFinishedSignal) {
        if (state.restFinishedSignal > 0) {
            context.vibrateGymCue(state.hapticsEnabled)
        }
    }
    LazyColumn(
        Modifier
            .fillMaxSize()
            .padding(contentPadding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { GymDiaryTopBar("Активная тренировка") }
        if (workout == null) {
            item { EmptyState("Активной тренировки нет") }
        } else {
            activeWorkoutContent(state, workout, actions, haptics)
        }
    }
    if (state.isFinishDialogVisible) {
        FinishWorkoutDialog(workout, state.hapticsEnabled, actions, haptics)
    }
}

@OptIn(ExperimentalLayoutApi::class)
private fun LazyListScope.activeWorkoutContent(
    state: ActiveWorkoutUiState,
    workout: WorkoutDetails,
    actions: ActiveWorkoutActions,
    haptics: HapticFeedback
) {
    item { ActiveWorkoutHeader(state, workout, actions, haptics) }
    item { AddExerciseButton(workout.summary.id, state.hapticsEnabled, actions.onAddExercise, haptics) }
    if (workout.exercises.isEmpty()) {
        item { EmptyState("Добавьте первое упражнение") }
    } else {
        items(workout.exercises, key = { it.workoutExerciseId }, contentType = { "workout_exercise" }) { exercise ->
            ActiveWorkoutExerciseCard(exercise, state, actions, haptics)
        }
    }
    item { ActiveWorkoutFooter(state.hapticsEnabled, actions, haptics) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ActiveWorkoutHeader(
    state: ActiveWorkoutUiState,
    workout: WorkoutDetails,
    actions: ActiveWorkoutActions,
    haptics: HapticFeedback
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        var workoutNote by remember(workout.summary.id, workout.note) { mutableStateOf(workout.note) }
        var pain by remember(workout.summary.id, workout.painNote) { mutableStateOf(workout.painNote) }
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(workout.summary.title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MetricChip("Время", formatDuration(state.durationSeconds))
                MetricChip("Упр.", workout.exercises.size.toString())
            }
            OutlinedTextField(pain, {
                pain = it
                actions.noteActions.onUpdateWorkoutNote(workoutNote, pain)
            }, label = { Text("Боль/самочувствие") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(workoutNote, {
                workoutNote = it
                actions.noteActions.onUpdateWorkoutNote(workoutNote, pain)
            }, label = { Text("Заметка тренировки") }, modifier = Modifier.fillMaxWidth())
            RestTimerControls(state, actions.restActions, haptics)
            state.errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RestTimerControls(
    state: ActiveWorkoutUiState,
    actions: ActiveRestActions,
    haptics: HapticFeedback
) {
    if (state.restTimerState.remainingSeconds <= 0) return
    SectionTitle("Отдых: ${state.restTimerState.remainingSeconds} сек")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = {
            haptics.performGymHaptic(state.hapticsEnabled)
            actions.onAddRest()
        }) { Text("+30 сек") }
        OutlinedButton(onClick = {
            haptics.performGymHaptic(state.hapticsEnabled)
            actions.onPauseRest()
        }) { Text(if (state.restTimerState.isPaused) "Продолжить" else "Пауза") }
        OutlinedButton(onClick = {
            haptics.performGymHaptic(state.hapticsEnabled, HapticFeedbackType.LongPress)
            actions.onSkipRest()
        }) { Text("Пропустить") }
    }
}

@Composable
private fun AddExerciseButton(
    workoutId: Long,
    hapticsEnabled: Boolean,
    onAddExercise: (Long) -> Unit,
    haptics: HapticFeedback
) {
    Button(onClick = {
        haptics.performGymHaptic(hapticsEnabled)
        onAddExercise(workoutId)
    }, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Default.Add, contentDescription = null)
        Text("Добавить упражнение", Modifier.padding(start = 8.dp))
    }
}

@Composable
private fun ActiveWorkoutExerciseCard(
    exercise: WorkoutExerciseDetails,
    state: ActiveWorkoutUiState,
    actions: ActiveWorkoutActions,
    haptics: HapticFeedback
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        val history = state.exerciseHistory[exercise.exerciseId].orEmpty()
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(exercise.exerciseName, style = MaterialTheme.typography.titleMedium)
            ExerciseTrainingHints(history)
            ExerciseNoteField(exercise, actions.noteActions)
            exercise.sets.forEach { set ->
                SetRow(
                    set,
                    history.firstOrNull()?.sets?.getOrNull(set.setNumber - 1),
                    state.hapticsEnabled,
                    actions.setActions.onUpdateSet,
                    actions.setActions.onCompleteSet,
                    actions.setActions.onDeleteSet
                )
            }
            OutlinedButton(onClick = {
                haptics.performGymHaptic(state.hapticsEnabled)
                actions.setActions.onAddSet(exercise.workoutExerciseId)
            }, modifier = Modifier.testTag("add_set")) {
                Text("Добавить подход")
            }
        }
    }
}

@Composable
private fun ExerciseNoteField(exercise: WorkoutExerciseDetails, actions: ActiveNoteActions) {
    var exerciseNote by remember(exercise.workoutExerciseId, exercise.note) { mutableStateOf(exercise.note) }
    OutlinedTextField(
        exerciseNote,
        {
            exerciseNote = it
            actions.onUpdateExerciseNote(exercise.workoutExerciseId, exerciseNote)
        },
        label = { Text("Заметка к упражнению") },
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun ActiveWorkoutFooter(
    hapticsEnabled: Boolean,
    actions: ActiveWorkoutActions,
    haptics: HapticFeedback
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(onClick = {
            haptics.performGymHaptic(hapticsEnabled)
            actions.onSaveTemplate()
        }, modifier = Modifier.weight(1f)) { Text("В шаблон") }
        Button(onClick = {
            haptics.performGymHaptic(hapticsEnabled, HapticFeedbackType.LongPress)
            actions.onShowFinish()
        }, modifier = Modifier.weight(1f)) { Text("Завершить") }
    }
}

@Composable
private fun FinishWorkoutDialog(
    workout: WorkoutDetails?,
    hapticsEnabled: Boolean,
    actions: ActiveWorkoutActions,
    haptics: HapticFeedback
) {
    AlertDialog(
        onDismissRequest = actions.onHideFinish,
        title = { Text("Завершить тренировку?") },
        text = { Text(finishDialogText(workout)) },
        confirmButton = {
            TextButton(onClick = {
                haptics.performGymHaptic(hapticsEnabled, HapticFeedbackType.LongPress)
                actions.onFinish()
            }) { Text("Завершить") }
        },
        dismissButton = { TextButton(onClick = actions.onHideFinish) { Text("Отмена") } }
    )
}

private fun finishDialogText(workout: WorkoutDetails?): String = when {
    workout?.exercises.isNullOrEmpty() -> "В тренировке нет упражнений. Завершить без сохранения полезных данных?"
    workout.exercises.any { exercise -> exercise.sets.none { it.isCompleted } } -> "Есть упражнения без выполненных подходов. Завершить тренировку?"
    else -> "Тренировка будет сохранена в историю."
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SetRow(
    set: WorkoutSetModel,
    previousSet: WorkoutSetModel?,
    hapticsEnabled: Boolean,
    onUpdateSet: (WorkoutSetModel, String, String, String) -> Unit,
    onCompleteSet: (WorkoutSetModel, Boolean) -> Unit,
    onDeleteSet: (Long) -> Unit
) {
    var weight by remember(set.id, set.weightKg) { mutableStateOf(if (set.weightKg == 0.0) "" else set.weightKg.toString()) }
    var reps by remember(set.id, set.reps) { mutableStateOf(if (set.reps == 0) "" else set.reps.toString()) }
    var note by remember(set.id, set.note) { mutableStateOf(set.note) }
    val haptics = LocalHapticFeedback.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("set_row"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Text("Подход ${set.setNumber}", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Checkbox(checked = set.isCompleted, onCheckedChange = {
                    haptics.performGymHaptic(hapticsEnabled, HapticFeedbackType.LongPress)
                    onCompleteSet(set, it)
                })
                IconButton(onClick = {
                    haptics.performGymHaptic(hapticsEnabled, HapticFeedbackType.LongPress)
                    onDeleteSet(set.id)
                }) { Icon(Icons.Default.Delete, contentDescription = "Удалить подход") }
            }
            previousSet?.let { Text("Прошлый раз: ${it.weightKg.formatKg()} × ${it.reps}", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(weight, {
                    weight = it
                    onUpdateSet(set, weight, reps, note)
                }, label = { Text("Вес") }, modifier = Modifier.weight(1f), singleLine = true)
                OutlinedTextField(reps, {
                    reps = it
                    onUpdateSet(set, weight, reps, note)
                }, label = { Text("Повт.") }, modifier = Modifier.weight(1f), singleLine = true)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = {
                    haptics.performGymHaptic(hapticsEnabled)
                    weight = ((weight.replace(',', '.').toDoubleOrNull() ?: 0.0) + 2.5).toString()
                    onUpdateSet(set, weight, reps, note)
                }) { Text("+2.5") }
                OutlinedButton(onClick = {
                    haptics.performGymHaptic(hapticsEnabled)
                    weight =
                        (((weight.replace(',', '.').toDoubleOrNull() ?: 0.0) - 2.5).coerceAtLeast(0.0)).toString()
                    onUpdateSet(set, weight, reps, note)
                }) { Text("-2.5") }
                OutlinedButton(onClick = {
                    haptics.performGymHaptic(hapticsEnabled)
                    reps = ((reps.toIntOrNull() ?: 0) + 1).toString()
                    onUpdateSet(set, weight, reps, note)
                }) { Text("+1 повт.") }
                previousSet?.let {
                    OutlinedButton(onClick = {
                        haptics.performGymHaptic(hapticsEnabled)
                        weight = it.weightKg.toString()
                        reps = it.reps.toString()
                        onUpdateSet(set, weight, reps, note)
                    }) { Text("Копия") }
                }
            }
            OutlinedTextField(note, {
                note = it
                onUpdateSet(set, weight, reps, note)
            }, label = { Text("Комментарий") }, modifier = Modifier.fillMaxWidth())
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BackfillWorkoutCard(error: String?, onStart: (String, String, String, String) -> Unit) {
    var title by remember { mutableStateOf("Старая тренировка") }
    var date by remember { mutableStateOf(LocalDate.now()) }
    var startTime by remember { mutableStateOf<LocalTime?>(null) }
    var finishTime by remember { mutableStateOf<LocalTime?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }
    var timeDialogTarget by remember { mutableStateOf<BackfillTimeTarget?>(null) }
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Внести старую тренировку", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            OutlinedTextField(title, { title = it }, label = { Text("Название") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedButton(onClick = { showDatePicker = true }, modifier = Modifier.fillMaxWidth()) {
                Text("Дата: ${date.format(backfillDateFormatter)}")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = { timeDialogTarget = BackfillTimeTarget.START }, modifier = Modifier.weight(1f)) {
                    Text("Старт: ${startTime?.format(backfillTimeFormatter) ?: "-"}")
                }
                OutlinedButton(onClick = { timeDialogTarget = BackfillTimeTarget.FINISH }, modifier = Modifier.weight(1f)) {
                    Text("Финиш: ${finishTime?.format(backfillTimeFormatter) ?: "-"}")
                }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(
                onClick = {
                    onStart(
                        title,
                        date.format(backfillDateFormatter),
                        startTime?.format(backfillTimeFormatter).orEmpty(),
                        finishTime?.format(backfillTimeFormatter).orEmpty()
                    )
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Внести старую тренировку")
            }
        }
    }
    if (showDatePicker) {
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let { millis ->
                            date = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                        }
                        showDatePicker = false
                    }
                ) {
                    Text("Выбрать")
                }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Отмена") } }
        ) {
            DatePicker(state = pickerState)
        }
    }
    timeDialogTarget?.let { target ->
        BackfillTimeDialog(
            title = if (target == BackfillTimeTarget.START) "Время старта" else "Время финиша",
            initialTime = if (target == BackfillTimeTarget.START) startTime else finishTime,
            onDismiss = { timeDialogTarget = null },
            onClear = {
                if (target == BackfillTimeTarget.START) startTime = null else finishTime = null
                timeDialogTarget = null
            },
            onConfirm = {
                if (target == BackfillTimeTarget.START) startTime = it else finishTime = it
                timeDialogTarget = null
            }
        )
    }
}

private enum class BackfillTimeTarget { START, FINISH }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BackfillTimeDialog(
    title: String,
    initialTime: LocalTime?,
    onDismiss: () -> Unit,
    onClear: () -> Unit,
    onConfirm: (LocalTime) -> Unit
) {
    val timePickerState = rememberTimePickerState(
        initialHour = initialTime?.hour ?: 12,
        initialMinute = initialTime?.minute ?: 0,
        is24Hour = true
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            TimePicker(state = timePickerState)
        },
        confirmButton = { TextButton(onClick = { onConfirm(LocalTime.of(timePickerState.hour, timePickerState.minute)) }) { Text("Выбрать") } },
        dismissButton = {
            Row {
                TextButton(onClick = onClear) { Text("Не указывать") }
                TextButton(onClick = onDismiss) { Text("Отмена") }
            }
        }
    )
}

@Composable
private fun ExerciseTrainingHints(history: List<ExerciseHistoryEntry>) {
    val last = history.firstOrNull() ?: return
    val bestSet = remember(last.sets) {
        last.sets.maxWithOrNull(compareBy<WorkoutSetModel> { TrainingCalculators.estimatedOneRm(it.weightKg, it.reps) ?: 0.0 }.thenBy { it.weightKg })
    }
    val warmups = remember(bestSet) {
        val nextWeight = bestSet?.nextSuggestedWeight() ?: return@remember emptyList()
        listOf(0.5, 0.7, 0.85).map { ratio -> (nextWeight * ratio).coerceAtLeast(20.0) }
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Последняя тренировка: ${last.maxWeight.formatKg()} • объём ${last.volume.formatKg()}", color = MaterialTheme.colorScheme.onSurfaceVariant)
        bestSet?.let {
            val nextWeight = it.nextSuggestedWeight()
            val nextReps = if (nextWeight > it.weightKg) 6 else (it.reps + 1).coerceAtMost(12)
            Text("План: ${nextWeight.formatKg()} × $nextReps", color = MaterialTheme.colorScheme.primary)
            Text(
                "Разминка: ${
                    warmups.joinToString(" • ") { weight ->
                        "${weight.formatKg()} × ${if (weight < nextWeight * 0.75) 5 else 3}"
                    }
                }",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun WorkoutSetModel.nextSuggestedWeight(): Double = if (reps >= 8) weightKg + 2.5 else weightKg

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class AddExerciseToWorkoutViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    exerciseRepository: ExerciseRepository,
    private val workoutRepository: WorkoutRepository,
    settingsRepository: SettingsRepository
) : ViewModel() {
    private val workoutId: Long = savedStateHandle["workoutId"] ?: 0L
    private val query = MutableStateFlow("")
    private val muscleGroup = MutableStateFlow<MuscleGroup?>(null)
    val exercises = combine(query.debounce(150), muscleGroup) { q, group -> q to group }
        .flatMapLatest { (q, group) -> exerciseRepository.searchExercises(q, group, null) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
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
fun AddExerciseToWorkoutRoute(onBack: () -> Unit, viewModel: AddExerciseToWorkoutViewModel = hiltViewModel()) {
    val exercises by viewModel.exercises.collectAsStateWithLifecycle()
    val settings by viewModel.hapticsEnabled.collectAsStateWithLifecycle()
    val haptics = LocalHapticFeedback.current
    var query by remember { mutableStateOf(TextFieldValue("")) }
    var group by remember { mutableStateOf<MuscleGroup?>(null) }
    LaunchedEffect(Unit) { viewModel.events.collect { onBack() } }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { GymDiaryTopBar("Добавить упражнение", onBack) }
        item {
            OutlinedTextField(
                value = query,
                onValueChange = {
                    query = it.copy(selection = TextRange(it.text.length))
                    viewModel.onQueryChange(it.text)
                },
                label = { Text("Поиск") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
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
                modifier = Modifier.fillMaxWidth()
            )
        }
        items(exercises, key = { it.id }, contentType = { "exercise" }) { exercise ->
            ExerciseCard(exercise, onClick = {
                haptics.performGymHaptic(settings.hapticsEnabled)
                viewModel.add(exercise)
            })
        }
    }
}
