package ru.fuezl.gymdiary.feature.progress

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
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
import kotlinx.coroutines.launch
import ru.fuezl.gymdiary.core.common.formatDate
import ru.fuezl.gymdiary.core.common.formatKg
import ru.fuezl.gymdiary.core.database.BodyWeightEntryEntity
import ru.fuezl.gymdiary.core.model.Exercise
import ru.fuezl.gymdiary.core.model.ExerciseAnalytics
import ru.fuezl.gymdiary.core.model.ExerciseProgressPoint
import ru.fuezl.gymdiary.core.model.PersonalRecord
import ru.fuezl.gymdiary.core.model.WeeklyStats
import ru.fuezl.gymdiary.core.ui.EmptyState
import ru.fuezl.gymdiary.core.ui.GymDiaryTopBar
import ru.fuezl.gymdiary.core.ui.SimpleLineChart
import ru.fuezl.gymdiary.core.ui.StatCard
import ru.fuezl.gymdiary.data.repository.ExerciseRepository
import ru.fuezl.gymdiary.data.repository.ProgressRepository
import javax.inject.Inject

data class ProgressUiState(
    val weeklyStats: WeeklyStats = WeeklyStats(),
    val personalRecords: List<PersonalRecord> = emptyList(),
    val exercises: List<Exercise> = emptyList(),
    val selectedExerciseId: Long? = null,
    val selectedExerciseProgress: List<ExerciseProgressPoint> = emptyList(),
    val selectedExerciseAnalytics: ExerciseAnalytics = ExerciseAnalytics(),
    val bodyWeight: List<BodyWeightEntryEntity> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ProgressViewModel @Inject constructor(
    private val progressRepository: ProgressRepository,
    exerciseRepository: ExerciseRepository,
) : ViewModel() {
    private val selectedExerciseId = MutableStateFlow<Long?>(null)
    private val selectedProgress = selectedExerciseId.flatMapLatest { id ->
        if (id == null) kotlinx.coroutines.flow.flowOf(emptyList()) else progressRepository.observeExerciseProgress(id)
    }
    private val selectedAnalytics = selectedExerciseId.flatMapLatest { id ->
        if (id == null) kotlinx.coroutines.flow.flowOf(ExerciseAnalytics()) else progressRepository.observeExerciseAnalytics(id)
    }
    val uiState = combine(
        listOf(
            progressRepository.observeWeeklyStats(),
            progressRepository.observePersonalRecords(),
            exerciseRepository.observeExercises(),
            selectedExerciseId,
            selectedProgress,
            selectedAnalytics,
            progressRepository.observeBodyWeight(),
        ),
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        ProgressUiState(
            weeklyStats = values[0] as WeeklyStats,
            personalRecords = values[1] as List<PersonalRecord>,
            exercises = values[2] as List<Exercise>,
            selectedExerciseId = values[3] as Long?,
            selectedExerciseProgress = values[4] as List<ExerciseProgressPoint>,
            selectedExerciseAnalytics = values[5] as ExerciseAnalytics,
            bodyWeight = values[6] as List<BodyWeightEntryEntity>,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProgressUiState())

    fun selectExercise(id: Long) {
        selectedExerciseId.value = id
    }

    fun addBodyWeight(weight: String, note: String) {
        val value = weight.replace(',', '.').toDoubleOrNull() ?: return
        if (value <= 0.0) return
        viewModelScope.launch { progressRepository.addBodyWeight(value, note) }
    }

    fun deleteBodyWeight(id: Long) {
        viewModelScope.launch { progressRepository.deleteBodyWeight(id) }
    }

    fun saveGoal(weight: String, reps: String, note: String) {
        val exerciseId = selectedExerciseId.value ?: return
        val weightValue = weight.replace(',', '.').toDoubleOrNull() ?: return
        val repsValue = reps.toIntOrNull() ?: return
        viewModelScope.launch { progressRepository.saveExerciseGoal(exerciseId, weightValue, repsValue, note) }
    }

    fun deleteGoal() {
        val exerciseId = selectedExerciseId.value ?: return
        viewModelScope.launch { progressRepository.deleteExerciseGoal(exerciseId) }
    }
}

@Composable
fun ProgressRoute(
    contentPadding: PaddingValues,
    viewModel: ProgressViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.exercises) {
        if (state.selectedExerciseId == null) state.exercises.firstOrNull()?.let { viewModel.selectExercise(it.id) }
    }
    ProgressScreen(state, contentPadding, viewModel::selectExercise, viewModel::addBodyWeight, viewModel::deleteBodyWeight, viewModel::saveGoal, viewModel::deleteGoal)
}

@Composable
fun ProgressScreen(
    state: ProgressUiState,
    contentPadding: PaddingValues,
    onSelectExercise: (Long) -> Unit,
    onAddBodyWeight: (String, String) -> Unit,
    onDeleteBodyWeight: (Long) -> Unit,
    onSaveGoal: (String, String, String) -> Unit,
    onDeleteGoal: () -> Unit,
) {
    var bodyWeightValue by remember { mutableStateOf("") }
    var bodyWeightNote by remember { mutableStateOf("") }
    LazyColumn(
        Modifier.fillMaxSize().padding(contentPadding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { GymDiaryTopBar("Прогресс") }
        item {
            StatCard(
                "Текущая неделя",
                "${state.weeklyStats.workouts} трен. • ${state.weeklyStats.volume.formatKg()}",
                Modifier.fillMaxWidth(),
            )
        }
        item {
            Text("График максимального веса", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            if (state.exercises.isNotEmpty()) {
                ExerciseSelector(state.exercises, state.selectedExerciseId, onSelectExercise)
            }
            SimpleLineChart(state.selectedExerciseProgress.map { it.maxWeight })
        }
        item {
            Text("График тренировочного объёма", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            SimpleLineChart(state.selectedExerciseProgress.map { it.volume })
        }
        item {
            Text("График расчётного 1ПМ", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            SimpleLineChart(state.selectedExerciseProgress.map { it.bestEstimatedOneRm })
        }
        item {
            ExerciseGoalCard(state, onSaveGoal, onDeleteGoal)
        }
        state.selectedExerciseAnalytics.plateauMessage?.let { message ->
            item {
                Card {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Плато", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(message)
                        Text("Проверьте сон, объём и RPE; иногда помогает разгрузочная неделя.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        item { Text("История упражнения", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
        if (state.selectedExerciseAnalytics.history.isEmpty()) {
            item { EmptyState("Нет выполненных подходов по выбранному упражнению") }
        } else {
            items(state.selectedExerciseAnalytics.history, key = { it.workoutId }) { entry ->
                Card {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(formatDate(entry.date), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text("Макс: ${entry.maxWeight.formatKg()} • объём: ${entry.volume.formatKg()} • 1ПМ: ${entry.bestEstimatedOneRm.formatKg()}")
                        Text(entry.sets.joinToString("  ") { "${it.weightKg.formatKg()}×${it.reps}" }, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        item { Text("Личные рекорды", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
        if (state.personalRecords.isEmpty()) {
            item { EmptyState("Нет данных для прогресса") }
        } else {
            items(state.personalRecords, key = { it.exerciseId }) { record ->
                Card {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(record.exerciseName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text("Максимальный вес: ${record.maxWeight.formatKg()}")
                        Text("Лучший объём за тренировку: ${record.bestWorkoutVolume.formatKg()}")
                        Text("Расчётный 1ПМ: ${record.bestEstimatedOneRm.formatKg()}")
                    }
                }
            }
        }
        item { Text("Масса тела", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
        item {
            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(bodyWeightValue, { bodyWeightValue = it }, label = { Text("кг") }, modifier = Modifier.weight(1f), singleLine = true)
                        OutlinedTextField(bodyWeightNote, { bodyWeightNote = it }, label = { Text("Заметка") }, modifier = Modifier.weight(2f), singleLine = true)
                    }
                    Button(
                        onClick = {
                            onAddBodyWeight(bodyWeightValue, bodyWeightNote)
                            bodyWeightValue = ""
                            bodyWeightNote = ""
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Добавить запись") }
                }
            }
        }
        if (state.bodyWeight.isEmpty()) {
            item { EmptyState("Нет записей массы тела") }
        } else {
            items(state.bodyWeight, key = { it.id }) { entry ->
                Card {
                    Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text(entry.weightKg.formatKg(), fontWeight = FontWeight.SemiBold)
                            Text(formatDate(entry.date), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (entry.note.isNotBlank()) Text(entry.note)
                        }
                        OutlinedButton(onClick = { onDeleteBodyWeight(entry.id) }) { Text("Удалить") }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExerciseGoalCard(
    state: ProgressUiState,
    onSaveGoal: (String, String, String) -> Unit,
    onDeleteGoal: () -> Unit,
) {
    val goal = state.selectedExerciseAnalytics.goal
    var weight by remember(goal?.id) { mutableStateOf(goal?.targetWeightKg?.toString().orEmpty()) }
    var reps by remember(goal?.id) { mutableStateOf(goal?.targetReps?.toString().orEmpty()) }
    var note by remember(goal?.id) { mutableStateOf(goal?.note.orEmpty()) }
    val latest = state.selectedExerciseAnalytics.history.maxByOrNull { it.bestEstimatedOneRm }
    val targetOneRm = (weight.replace(',', '.').toDoubleOrNull() ?: 0.0) * (1 + ((reps.toIntOrNull() ?: 0) / 30.0))
    val progress = if (targetOneRm > 0.0 && latest != null) "${((latest.bestEstimatedOneRm / targetOneRm) * 100).coerceAtMost(100.0).toInt()}%" else "нет данных"
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Цель упражнения", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(weight, { weight = it }, label = { Text("Вес") }, modifier = Modifier.weight(1f), singleLine = true)
                OutlinedTextField(reps, { reps = it }, label = { Text("Повт.") }, modifier = Modifier.weight(1f), singleLine = true)
            }
            OutlinedTextField(note, { note = it }, label = { Text("Заметка") }, modifier = Modifier.fillMaxWidth())
            Text("Прогресс к цели: $progress", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(onClick = { onSaveGoal(weight, reps, note) }, modifier = Modifier.weight(1f)) { Text("Сохранить") }
                OutlinedButton(onClick = onDeleteGoal, modifier = Modifier.weight(1f)) { Text("Удалить") }
            }
        }
    }
}

@Composable
private fun ExerciseSelector(
    exercises: List<Exercise>,
    selectedExerciseId: Long?,
    onSelectExercise: (Long) -> Unit,
) {
    val selected = exercises.firstOrNull { it.id == selectedExerciseId } ?: exercises.first()
    ru.fuezl.gymdiary.feature.exercises.EnumDropdown(
        label = "Упражнение",
        selected = selected,
        values = exercises,
        title = { it.name },
        onSelected = { it?.let { exercise -> onSelectExercise(exercise.id) } },
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
    )
}
