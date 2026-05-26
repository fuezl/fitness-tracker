package ru.fuezl.gymdiary.data.repository

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import ru.fuezl.gymdiary.core.database.BodyWeightDao
import ru.fuezl.gymdiary.core.database.BodyWeightEntryEntity
import ru.fuezl.gymdiary.core.database.ExerciseDao
import ru.fuezl.gymdiary.core.database.ExerciseEntity
import ru.fuezl.gymdiary.core.database.ExerciseGoalDao
import ru.fuezl.gymdiary.core.database.ExerciseGoalEntity
import ru.fuezl.gymdiary.core.database.GymDiaryDatabase
import ru.fuezl.gymdiary.core.database.SeedData
import ru.fuezl.gymdiary.core.database.WorkoutDao
import ru.fuezl.gymdiary.core.database.WorkoutExerciseEntity
import ru.fuezl.gymdiary.core.database.WorkoutSessionEntity
import ru.fuezl.gymdiary.core.database.WorkoutSetEntity
import ru.fuezl.gymdiary.core.database.WorkoutTemplateDao
import ru.fuezl.gymdiary.core.database.WorkoutTemplateEntity
import ru.fuezl.gymdiary.core.database.WorkoutTemplateExerciseEntity
import ru.fuezl.gymdiary.core.database.asDetails
import ru.fuezl.gymdiary.core.database.asModel
import ru.fuezl.gymdiary.core.datastore.SettingsLocalDataSource
import ru.fuezl.gymdiary.core.model.Equipment
import ru.fuezl.gymdiary.core.model.Exercise
import ru.fuezl.gymdiary.core.model.ExerciseAnalytics
import ru.fuezl.gymdiary.core.model.ExerciseGoal
import ru.fuezl.gymdiary.core.model.ExerciseHistoryEntry
import ru.fuezl.gymdiary.core.model.ExerciseProgressPoint
import ru.fuezl.gymdiary.core.model.MuscleGroup
import ru.fuezl.gymdiary.core.model.PersonalRecord
import ru.fuezl.gymdiary.core.model.ThemeMode
import ru.fuezl.gymdiary.core.model.UserSettings
import ru.fuezl.gymdiary.core.model.WeeklyStats
import ru.fuezl.gymdiary.core.model.WorkoutDetails
import ru.fuezl.gymdiary.core.model.WorkoutSetModel
import ru.fuezl.gymdiary.core.model.WorkoutSummary
import ru.fuezl.gymdiary.domain.usecase.TrainingCalculators
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

interface ExerciseRepository {
    fun observeExercises(): Flow<List<Exercise>>
    fun searchExercises(query: String, muscleGroup: MuscleGroup?, equipment: Equipment?): Flow<List<Exercise>>
    suspend fun getExercise(id: Long): Exercise?
    suspend fun createExercise(name: String, muscleGroup: MuscleGroup, equipment: Equipment, note: String): Long
    suspend fun updateExercise(exercise: Exercise)
    suspend fun deleteExercise(id: Long)
    suspend fun seedDefaultsIfEmpty()
}

@Singleton
class DefaultExerciseRepository @Inject constructor(private val dao: ExerciseDao) : ExerciseRepository {
    override fun observeExercises(): Flow<List<Exercise>> = dao.observeExercises()
        .map { it.map(ExerciseEntity::asModel) }
        .distinctUntilChanged()

    override fun searchExercises(query: String, muscleGroup: MuscleGroup?, equipment: Equipment?): Flow<List<Exercise>> =
        dao.searchExercises(query.trim(), muscleGroup?.name, equipment?.name)
            .map { it.map(ExerciseEntity::asModel) }
            .distinctUntilChanged()

    override suspend fun getExercise(id: Long): Exercise? = dao.getExercise(id)?.asModel()

    override suspend fun createExercise(name: String, muscleGroup: MuscleGroup, equipment: Equipment, note: String): Long {
        val now = System.currentTimeMillis()
        return dao.insert(
            ExerciseEntity(
                name = name.trim(),
                muscleGroup = muscleGroup,
                equipment = equipment,
                note = note.trim(),
                isCustom = true,
                createdAt = now,
                updatedAt = now
            )
        )
    }

    override suspend fun updateExercise(exercise: Exercise) {
        dao.update(
            ExerciseEntity(
                id = exercise.id,
                name = exercise.name.trim(),
                muscleGroup = exercise.muscleGroup,
                equipment = exercise.equipment,
                note = exercise.note.trim(),
                isCustom = exercise.isCustom,
                createdAt = exercise.createdAt,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    override suspend fun deleteExercise(id: Long) {
        dao.getExercise(id)?.takeIf { it.isCustom }?.let { dao.delete(it) }
    }

    override suspend fun seedDefaultsIfEmpty() {
        if (dao.count() == 0) dao.insertAll(SeedData.exercises())
    }
}

interface WorkoutRepository {
    fun observeActiveWorkout(): Flow<WorkoutDetails?>
    fun observeWorkoutHistory(): Flow<List<WorkoutSummary>>
    fun observeWorkoutDetails(id: Long): Flow<WorkoutDetails?>
    fun observeExerciseHistoryIndex(): Flow<Map<Long, List<ExerciseHistoryEntry>>>
    suspend fun startWorkout(title: String = "Тренировка"): Long
    suspend fun startBackfilledWorkout(title: String, startedAt: Long, finishedAt: Long?): Long
    suspend fun addExerciseToWorkout(workoutId: Long, exerciseId: Long): Long
    suspend fun addSet(workoutExerciseId: Long): Long
    suspend fun updateSet(set: WorkoutSetModel)
    suspend fun completeSet(setId: Long, completed: Boolean)
    suspend fun deleteSet(setId: Long)
    suspend fun updateWorkoutNote(workoutId: Long, note: String, energyLevel: Int?, sleepQuality: Int?, painNote: String)
    suspend fun updateWorkoutExerciseNote(workoutExerciseId: Long, note: String)
    suspend fun finishWorkout(workoutId: Long)
    suspend fun deleteWorkout(workoutId: Long)
    suspend fun repeatWorkout(workoutId: Long): Long
}

@Singleton
class DefaultWorkoutRepository @Inject constructor(private val dao: WorkoutDao, private val templateDao: WorkoutTemplateDao) : WorkoutRepository {
    override fun observeActiveWorkout(): Flow<WorkoutDetails?> = dao.observeActiveWorkoutDetails()
        .map { it?.asDetails() }
        .distinctUntilChanged()

    override fun observeWorkoutHistory(): Flow<List<WorkoutSummary>> =
        dao.observeFinishedWorkoutDetails()
            .map { workouts -> workouts.map { it.asDetails().summary }.sortedByDescending { it.startedAt } }
            .distinctUntilChanged()

    override fun observeWorkoutDetails(id: Long): Flow<WorkoutDetails?> = dao.observeWorkoutDetails(id)
        .map { it?.asDetails() }
        .distinctUntilChanged()

    override fun observeExerciseHistoryIndex(): Flow<Map<Long, List<ExerciseHistoryEntry>>> = dao.observeFinishedWorkoutDetails()
        .map { workouts ->
            workouts.flatMap { workout ->
                val details = workout.asDetails()
                details.exercises.mapNotNull { exercise ->
                    val sets = exercise.sets.filter { it.isCompleted && it.reps > 0 }
                    if (sets.isEmpty()) {
                        null
                    } else {
                        exercise.exerciseId to ExerciseHistoryEntry(
                            workoutId = details.summary.id,
                            date = details.summary.startedAt,
                            sets = sets,
                            volume = TrainingCalculators.weightedVolume(sets),
                            maxWeight = sets.maxOf { it.weightKg },
                            bestEstimatedOneRm = sets.mapNotNull { TrainingCalculators.estimatedOneRm(it.weightKg, it.reps) }.maxOrNull() ?: 0.0
                        )
                    }
                }
            }.groupBy({ it.first }, { it.second }).mapValues { (_, entries) -> entries.sortedByDescending { it.date } }
        }
        .distinctUntilChanged()

    override suspend fun startWorkout(title: String): Long {
        val now = System.currentTimeMillis()
        return dao.insertSession(WorkoutSessionEntity(title = title, startedAt = now, createdAt = now, updatedAt = now))
    }

    override suspend fun startBackfilledWorkout(title: String, startedAt: Long, finishedAt: Long?): Long {
        val now = System.currentTimeMillis()
        val normalizedFinish = finishedAt?.takeIf { it >= startedAt }
        return dao.insertSession(
            WorkoutSessionEntity(
                title = title.trim().ifBlank { "Старая тренировка" },
                startedAt = startedAt,
                finishedAt = normalizedFinish,
                durationSeconds = normalizedFinish?.let { ((it - startedAt) / 1000).coerceAtLeast(0) } ?: 0,
                createdAt = now,
                updatedAt = now
            )
        )
    }

    override suspend fun addExerciseToWorkout(workoutId: Long, exerciseId: Long): Long {
        val order = dao.getWorkoutExercises(workoutId).size
        return dao.insertWorkoutExercise(WorkoutExerciseEntity(workoutSessionId = workoutId, exerciseId = exerciseId, orderIndex = order))
    }

    override suspend fun addSet(workoutExerciseId: Long): Long {
        val next = (dao.getSets(workoutExerciseId).maxOfOrNull { it.setNumber } ?: 0) + 1
        return dao.insertSet(WorkoutSetEntity(workoutExerciseId = workoutExerciseId, setNumber = next, weightKg = 0.0, reps = 0))
    }

    override suspend fun updateSet(set: WorkoutSetModel) {
        dao.updateSet(
            WorkoutSetEntity(
                id = set.id,
                workoutExerciseId = set.workoutExerciseId,
                setNumber = set.setNumber,
                weightKg = set.weightKg,
                reps = set.reps,
                isCompleted = set.isCompleted,
                note = set.note,
                createdAt = set.createdAt
            )
        )
    }

    override suspend fun completeSet(setId: Long, completed: Boolean) {
        dao.getSet(setId)?.let { dao.updateSet(it.copy(isCompleted = completed)) }
    }

    override suspend fun deleteSet(setId: Long) = dao.deleteSet(setId)

    override suspend fun updateWorkoutNote(workoutId: Long, note: String, energyLevel: Int?, sleepQuality: Int?, painNote: String) {
        dao.getSession(workoutId)?.let {
            dao.updateSession(
                it.copy(
                    note = note.trim(),
                    energyLevel = energyLevel,
                    sleepQuality = sleepQuality,
                    painNote = painNote.trim(),
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
    }

    override suspend fun updateWorkoutExerciseNote(workoutExerciseId: Long, note: String) {
        dao.getWorkoutExercise(workoutExerciseId)?.let { dao.updateWorkoutExercise(it.copy(note = note.trim())) }
    }

    override suspend fun finishWorkout(workoutId: Long) {
        val session = dao.getSession(workoutId) ?: return
        val now = System.currentTimeMillis()
        dao.updateSession(session.copy(finishedAt = now, durationSeconds = ((now - session.startedAt) / 1000).coerceAtLeast(0), updatedAt = now))
    }

    override suspend fun deleteWorkout(workoutId: Long) = dao.deleteSession(workoutId)

    override suspend fun repeatWorkout(workoutId: Long): Long {
        val details = dao.getWorkoutDetails(workoutId)?.asDetails() ?: return startWorkout()
        val newId = startWorkout(details.summary.title)
        details.exercises.forEach { exercise ->
            val workoutExerciseId = addExerciseToWorkout(newId, exercise.exerciseId)
            exercise.sets.forEach { old ->
                val setId = addSet(workoutExerciseId)
                dao.getSet(setId)?.let { dao.updateSet(it.copy(weightKg = old.weightKg, reps = old.reps, note = old.note, isCompleted = false)) }
            }
        }
        return newId
    }
}

data class WorkoutTemplateSummary(val id: Long, val title: String, val note: String, val exerciseCount: Int)

interface WorkoutTemplateRepository {
    fun observeTemplates(): Flow<List<WorkoutTemplateSummary>>
    suspend fun createTemplateFromWorkout(workoutId: Long, title: String, note: String = ""): Long
    suspend fun startWorkoutFromTemplate(templateId: Long): Long
    suspend fun deleteTemplate(templateId: Long)
}

@Singleton
class DefaultWorkoutTemplateRepository @Inject constructor(private val workoutDao: WorkoutDao, private val templateDao: WorkoutTemplateDao) : WorkoutTemplateRepository {
    override fun observeTemplates(): Flow<List<WorkoutTemplateSummary>> = templateDao.observeTemplateSummaries()
        .map { templates ->
            templates.map { template ->
                WorkoutTemplateSummary(
                    id = template.id,
                    title = template.title,
                    note = template.note,
                    exerciseCount = template.exerciseCount
                )
            }
        }
        .distinctUntilChanged()

    override suspend fun createTemplateFromWorkout(workoutId: Long, title: String, note: String): Long {
        val workoutExercises = workoutDao.getWorkoutExercises(workoutId)
        if (workoutExercises.isEmpty()) return 0L
        val now = System.currentTimeMillis()
        val templateId = templateDao.insertTemplate(WorkoutTemplateEntity(title = title.trim(), note = note.trim(), createdAt = now, updatedAt = now))
        workoutExercises.forEachIndexed { index, workoutExercise ->
            templateDao.insertTemplateExercise(
                WorkoutTemplateExerciseEntity(
                    templateId = templateId,
                    exerciseId = workoutExercise.exerciseId,
                    orderIndex = index
                )
            )
        }
        return templateId
    }

    override suspend fun startWorkoutFromTemplate(templateId: Long): Long {
        val template = templateDao.getTemplate(templateId) ?: return 0L
        val now = System.currentTimeMillis()
        val workoutId = workoutDao.insertSession(WorkoutSessionEntity(title = template.title, startedAt = now, createdAt = now, updatedAt = now))
        templateDao.getTemplateExercises(templateId).forEach { templateExercise ->
            workoutDao.insertWorkoutExercise(
                WorkoutExerciseEntity(
                    workoutSessionId = workoutId,
                    exerciseId = templateExercise.exerciseId,
                    orderIndex = templateExercise.orderIndex
                )
            )
        }
        return workoutId
    }

    override suspend fun deleteTemplate(templateId: Long) {
        templateDao.deleteTemplate(templateId)
    }
}

data class ProgressOverview(val weeklyStats: WeeklyStats = WeeklyStats(), val personalRecords: List<PersonalRecord> = emptyList())

data class SelectedExerciseProgress(val points: List<ExerciseProgressPoint> = emptyList(), val analytics: ExerciseAnalytics = ExerciseAnalytics())

interface ProgressRepository {
    fun observeProgressOverview(): Flow<ProgressOverview>
    fun observeWeeklyStats(): Flow<WeeklyStats>
    fun observePersonalRecords(): Flow<List<PersonalRecord>>
    fun observeSelectedExerciseProgress(exerciseId: Long): Flow<SelectedExerciseProgress>
    fun observeExerciseProgress(exerciseId: Long): Flow<List<ExerciseProgressPoint>>
    fun observeExerciseAnalytics(exerciseId: Long): Flow<ExerciseAnalytics>
    fun observeLastWeights(): Flow<List<Pair<String, Double>>>
    fun observeBodyWeight(): Flow<List<BodyWeightEntryEntity>>
    suspend fun addBodyWeight(weightKg: Double, note: String)
    suspend fun deleteBodyWeight(id: Long)
    suspend fun saveExerciseGoal(exerciseId: Long, targetWeightKg: Double, targetReps: Int, note: String)
    suspend fun deleteExerciseGoal(exerciseId: Long)
}

@Singleton
class DefaultProgressRepository @Inject constructor(private val workoutDao: WorkoutDao, private val bodyWeightDao: BodyWeightDao, private val goalDao: ExerciseGoalDao) : ProgressRepository {
    override fun observeProgressOverview(): Flow<ProgressOverview> = workoutDao.observeFinishedWorkoutDetails()
        .map { workouts ->
            val details = workouts.map { it.asDetails() }
            ProgressOverview(
                weeklyStats = calculateWeeklyStats(details),
                personalRecords = calculatePersonalRecords(details)
            )
        }
        .distinctUntilChanged()

    override fun observeWeeklyStats(): Flow<WeeklyStats> = workoutDao.observeFinishedWorkoutDetails()
        .map { workouts ->
            calculateWeeklyStats(workouts.map { it.asDetails() })
        }
        .distinctUntilChanged()

    override fun observePersonalRecords(): Flow<List<PersonalRecord>> = workoutDao.observeFinishedWorkoutDetails()
        .map { workouts ->
            calculatePersonalRecords(workouts.map { it.asDetails() })
        }
        .distinctUntilChanged()

    override fun observeSelectedExerciseProgress(exerciseId: Long): Flow<SelectedExerciseProgress> =
        combine(workoutDao.observeFinishedWorkoutDetails(), goalDao.observeGoal(exerciseId)) { workouts, goal ->
            val details = workouts.map { it.asDetails() }
            val history = calculateExerciseHistory(details, exerciseId)
            SelectedExerciseProgress(
                points = calculateExerciseProgress(details, exerciseId),
                analytics = ExerciseAnalytics(
                    history = history,
                    plateauMessage = detectPlateau(history),
                    goal = goal?.let { ExerciseGoal(it.id, it.exerciseId, it.targetWeightKg, it.targetReps, it.note) }
                )
            )
        }.distinctUntilChanged()

    override fun observeExerciseProgress(exerciseId: Long): Flow<List<ExerciseProgressPoint>> = workoutDao.observeFinishedWorkoutDetails()
        .map { workouts ->
            calculateExerciseProgress(workouts.map { it.asDetails() }, exerciseId)
        }
        .distinctUntilChanged()

    override fun observeExerciseAnalytics(exerciseId: Long): Flow<ExerciseAnalytics> = combine(workoutDao.observeFinishedWorkoutDetails(), goalDao.observeGoal(exerciseId)) { workouts, goal ->
        val history = calculateExerciseHistory(workouts.map { it.asDetails() }, exerciseId)
        ExerciseAnalytics(
            history = history,
            plateauMessage = detectPlateau(history),
            goal = goal?.let { ExerciseGoal(it.id, it.exerciseId, it.targetWeightKg, it.targetReps, it.note) }
        )
    }
        .distinctUntilChanged()

    override fun observeLastWeights(): Flow<List<Pair<String, Double>>> = workoutDao.observeFinishedWorkoutDetails()
        .map { workouts ->
            workouts.flatMap { workout ->
                workout.asDetails().exercises.mapNotNull { exercise ->
                    exercise.sets.filter { it.isCompleted && it.weightKg > 0.0 }.maxByOrNull { it.createdAt }?.let { exercise.exerciseName to it.weightKg }
                }
            }.distinctBy { it.first }.take(5)
        }
        .distinctUntilChanged()

    override fun observeBodyWeight(): Flow<List<BodyWeightEntryEntity>> = bodyWeightDao.observeEntries().distinctUntilChanged()

    override suspend fun addBodyWeight(weightKg: Double, note: String) {
        bodyWeightDao.insert(BodyWeightEntryEntity(date = System.currentTimeMillis(), weightKg = weightKg, note = note.trim()))
    }

    override suspend fun deleteBodyWeight(id: Long) {
        bodyWeightDao.delete(id)
    }

    override suspend fun saveExerciseGoal(exerciseId: Long, targetWeightKg: Double, targetReps: Int, note: String) {
        if (targetWeightKg <= 0.0 || targetReps <= 0) return
        val now = System.currentTimeMillis()
        val existing = goalDao.getAll().firstOrNull { it.exerciseId == exerciseId }
        goalDao.upsert(
            ExerciseGoalEntity(
                id = existing?.id ?: 0,
                exerciseId = exerciseId,
                targetWeightKg = targetWeightKg,
                targetReps = targetReps,
                note = note.trim(),
                createdAt = existing?.createdAt ?: now,
                updatedAt = now
            )
        )
    }

    override suspend fun deleteExerciseGoal(exerciseId: Long) {
        goalDao.deleteForExercise(exerciseId)
    }

    private fun detectPlateau(history: List<ExerciseHistoryEntry>): String? {
        if (history.size < 4) return null
        val recent = history.take(4)
        val bestBefore = history.drop(4).maxOfOrNull { it.bestEstimatedOneRm } ?: return null
        val recentBest = recent.maxOf { it.bestEstimatedOneRm }
        return if (recentBest <= bestBefore * 1.01) "Похоже на плато: последние 4 тренировки без заметного роста 1ПМ." else null
    }

    private fun calculateWeeklyStats(details: List<WorkoutDetails>): WeeklyStats {
        val start = LocalDate.now().minusDays(6).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val recent = details.filter { it.summary.startedAt >= start }
        return WeeklyStats(recent.size, recent.sumOf { it.summary.setCount }, recent.sumOf { it.summary.totalVolume })
    }

    private fun calculatePersonalRecords(details: List<WorkoutDetails>): List<PersonalRecord> =
        details.flatMap { it.exercises }.groupBy { it.exerciseId }.mapNotNull { (_, entries) ->
            val name = entries.firstOrNull()?.exerciseName ?: return@mapNotNull null
            val sets = entries.flatMap { it.sets }.filter { it.isCompleted && it.reps > 0 }
            if (sets.isEmpty()) return@mapNotNull null
            PersonalRecord(
                exerciseId = entries.first().exerciseId,
                exerciseName = name,
                maxWeight = sets.maxOf { it.weightKg },
                bestRepsAtWeight = sets.maxWith(compareBy<WorkoutSetModel> { it.weightKg }.thenBy { it.reps }).reps,
                bestWorkoutVolume = entries.maxOf { TrainingCalculators.weightedVolume(it.sets) },
                bestEstimatedOneRm = sets.mapNotNull { TrainingCalculators.estimatedOneRm(it.weightKg, it.reps) }.maxOrNull() ?: 0.0
            )
        }.sortedBy { it.exerciseName }

    private fun calculateExerciseProgress(details: List<WorkoutDetails>, exerciseId: Long): List<ExerciseProgressPoint> =
        details.mapNotNull { workout ->
            val exerciseSets = workout.exercises.filter { it.exerciseId == exerciseId }.flatMap { it.sets }.filter { it.isCompleted }
            if (exerciseSets.isEmpty()) {
                null
            } else {
                ExerciseProgressPoint(
                    date = workout.summary.startedAt,
                    maxWeight = exerciseSets.maxOf { it.weightKg },
                    volume = TrainingCalculators.weightedVolume(exerciseSets),
                    bestEstimatedOneRm = exerciseSets.mapNotNull { TrainingCalculators.estimatedOneRm(it.weightKg, it.reps) }.maxOrNull() ?: 0.0
                )
            }
        }.sortedBy { it.date }

    private fun calculateExerciseHistory(details: List<WorkoutDetails>, exerciseId: Long): List<ExerciseHistoryEntry> =
        details.mapNotNull { workout ->
            val sets = workout.exercises
                .filter { it.exerciseId == exerciseId }
                .flatMap { it.sets }
                .filter { it.isCompleted && it.reps > 0 }
            if (sets.isEmpty()) {
                null
            } else {
                ExerciseHistoryEntry(
                    workoutId = workout.summary.id,
                    date = workout.summary.startedAt,
                    sets = sets,
                    volume = TrainingCalculators.weightedVolume(sets),
                    maxWeight = sets.maxOf { it.weightKg },
                    bestEstimatedOneRm = sets.mapNotNull { TrainingCalculators.estimatedOneRm(it.weightKg, it.reps) }.maxOrNull() ?: 0.0
                )
            }
        }.sortedByDescending { it.date }
}

interface SettingsRepository {
    fun observeSettings(): Flow<UserSettings>
    suspend fun updateTheme(themeMode: ThemeMode)
    suspend fun updateRestTimer(enabled: Boolean, seconds: Int)
    suspend fun updateHaptics(enabled: Boolean)
    suspend fun exportData(): String
    suspend fun importData(json: String)
    suspend fun clearAllData()
}

@Serializable
data class ExportData(
    val exercises: List<ExerciseEntity>,
    val workoutSessions: List<WorkoutSessionEntity>,
    val workoutExercises: List<WorkoutExerciseEntity>,
    val workoutSets: List<WorkoutSetEntity>,
    val templates: List<WorkoutTemplateEntity>,
    val templateExercises: List<WorkoutTemplateExerciseEntity>,
    val bodyWeightEntries: List<BodyWeightEntryEntity> = emptyList(),
    val exerciseGoals: List<ExerciseGoalEntity> = emptyList(),
    val settings: UserSettings
)

@Singleton
class DefaultSettingsRepository @Inject constructor(
    private val database: GymDiaryDatabase,
    private val exerciseDao: ExerciseDao,
    private val workoutDao: WorkoutDao,
    private val templateDao: WorkoutTemplateDao,
    private val bodyWeightDao: BodyWeightDao,
    private val goalDao: ExerciseGoalDao,
    private val settingsDataStore: SettingsLocalDataSource
) : SettingsRepository {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    override fun observeSettings(): Flow<UserSettings> = settingsDataStore.settings

    override suspend fun updateTheme(themeMode: ThemeMode) = settingsDataStore.updateTheme(themeMode)

    override suspend fun updateRestTimer(enabled: Boolean, seconds: Int) = settingsDataStore.updateRestTimer(enabled, seconds)

    override suspend fun updateHaptics(enabled: Boolean) = settingsDataStore.updateHaptics(enabled)

    override suspend fun exportData(): String = json.encodeToString(
        ExportData(
            exercises = exerciseDao.getAll(),
            workoutSessions = workoutDao.getAllSessions(),
            workoutExercises = workoutDao.getAllWorkoutExercises(),
            workoutSets = workoutDao.getAllSets(),
            templates = templateDao.getAllTemplates(),
            templateExercises = templateDao.getAllTemplateExercises(),
            bodyWeightEntries = bodyWeightDao.getAll(),
            exerciseGoals = goalDao.getAll(),
            settings = settingsDataStore.settings.first()
        )
    )

    override suspend fun importData(json: String) {
        val data = this.json.decodeFromString<ExportData>(json)
        database.withTransaction {
            database.clearAllTables()
            exerciseDao.upsertAll(data.exercises)
            workoutDao.upsertSessions(data.workoutSessions)
            workoutDao.upsertWorkoutExercises(data.workoutExercises)
            workoutDao.upsertSets(data.workoutSets)
            templateDao.upsertTemplates(data.templates)
            templateDao.upsertTemplateExercises(data.templateExercises)
            bodyWeightDao.upsertAll(data.bodyWeightEntries)
            goalDao.upsertAll(data.exerciseGoals)
        }
        settingsDataStore.restore(data.settings)
    }

    override suspend fun clearAllData() {
        database.clearAllTables()
        exerciseDao.insertAll(SeedData.exercises())
    }
}
