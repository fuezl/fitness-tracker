package ru.fuezl.gymdiary.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import ru.fuezl.gymdiary.core.database.BodyWeightDao
import ru.fuezl.gymdiary.core.database.ExerciseDao
import ru.fuezl.gymdiary.core.database.ExerciseEntity
import ru.fuezl.gymdiary.core.database.ExerciseGoalDao
import ru.fuezl.gymdiary.core.database.GymDiaryDatabase
import ru.fuezl.gymdiary.core.database.WorkoutDao
import ru.fuezl.gymdiary.core.database.WorkoutTemplateDao
import ru.fuezl.gymdiary.core.datastore.SettingsLocalDataSource
import ru.fuezl.gymdiary.core.model.Equipment
import ru.fuezl.gymdiary.core.model.MuscleGroup
import ru.fuezl.gymdiary.core.model.ThemeMode
import ru.fuezl.gymdiary.core.model.UserSettings
import ru.fuezl.gymdiary.core.model.WorkoutSetModel

@RunWith(AndroidJUnit4::class)
class RepositoryIntegrationTest {
    private lateinit var database: GymDiaryDatabase
    private lateinit var exerciseDao: ExerciseDao
    private lateinit var workoutDao: WorkoutDao
    private lateinit var templateDao: WorkoutTemplateDao
    private lateinit var bodyWeightDao: BodyWeightDao
    private lateinit var goalDao: ExerciseGoalDao
    private lateinit var settingsDataSource: FakeSettingsLocalDataSource

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, GymDiaryDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        exerciseDao = database.exerciseDao()
        workoutDao = database.workoutDao()
        templateDao = database.workoutTemplateDao()
        bodyWeightDao = database.bodyWeightDao()
        goalDao = database.exerciseGoalDao()
        settingsDataSource = FakeSettingsLocalDataSource()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun workoutRepository_finishesRepeatsAndDeletesWorkout() = runTest {
        val exerciseId = exerciseDao.insert(ExerciseEntity(name = "Жим", muscleGroup = MuscleGroup.CHEST, equipment = Equipment.BARBELL))
        val repository = DefaultWorkoutRepository(workoutDao, templateDao)

        val workoutId = repository.startWorkout("Грудь")
        val workoutExerciseId = repository.addExerciseToWorkout(workoutId, exerciseId)
        val setId = repository.addSet(workoutExerciseId)
        repository.updateSet(WorkoutSetModel(setId, workoutExerciseId, 1, 100.0, 5, false, "", 0))
        repository.completeSet(setId, true)
        repository.finishWorkout(workoutId)

        val history = repository.observeWorkoutHistory().first()
        assertEquals(1, history.size)
        assertEquals(500.0, history.first().totalVolume, 0.001)

        val repeatedId = repository.repeatWorkout(workoutId)
        val repeated = repository.observeWorkoutDetails(repeatedId).first()
        assertEquals(1, repeated?.exercises?.size)
        assertEquals(false, repeated?.exercises?.first()?.sets?.first()?.isCompleted)

        repository.deleteWorkout(workoutId)
        assertEquals(null, repository.observeWorkoutDetails(workoutId).first())
    }

    @Test
    fun templateRepository_createsStartsAndDeletesTemplate() = runTest {
        val exerciseId = exerciseDao.insert(ExerciseEntity(name = "Присед", muscleGroup = MuscleGroup.LEGS, equipment = Equipment.BARBELL))
        val workoutRepository = DefaultWorkoutRepository(workoutDao, templateDao)
        val templateRepository = DefaultWorkoutTemplateRepository(workoutDao, templateDao)
        val workoutId = workoutRepository.startWorkout("Ноги")
        workoutRepository.addExerciseToWorkout(workoutId, exerciseId)

        val templateId = templateRepository.createTemplateFromWorkout(workoutId, "День ног")
        val templates = templateRepository.observeTemplates().first()
        assertEquals(1, templates.size)
        assertEquals(1, templates.first().exerciseCount)

        val startedId = templateRepository.startWorkoutFromTemplate(templateId)
        val started = workoutRepository.observeWorkoutDetails(startedId).first()
        assertEquals("День ног", started?.summary?.title)
        assertEquals(1, started?.exercises?.size)

        templateRepository.deleteTemplate(templateId)
        assertEquals(emptyList<WorkoutTemplateSummary>(), templateRepository.observeTemplates().first())
    }

    @Test
    fun progressRepository_calculatesRecordsWeeklyStatsAndBodyWeight() = runTest {
        val exerciseId = exerciseDao.insert(ExerciseEntity(name = "Тяга", muscleGroup = MuscleGroup.BACK, equipment = Equipment.BARBELL))
        val workoutRepository = DefaultWorkoutRepository(workoutDao, templateDao)
        val progressRepository = DefaultProgressRepository(workoutDao, bodyWeightDao, goalDao)
        val workoutId = workoutRepository.startWorkout("Спина")
        val workoutExerciseId = workoutRepository.addExerciseToWorkout(workoutId, exerciseId)
        val setId = workoutRepository.addSet(workoutExerciseId)
        workoutRepository.updateSet(WorkoutSetModel(setId, workoutExerciseId, 1, 150.0, 3, false, "", 0))
        workoutRepository.completeSet(setId, true)
        workoutRepository.finishWorkout(workoutId)

        val records = progressRepository.observePersonalRecords().first()
        assertEquals(1, records.size)
        assertEquals(150.0, records.first().maxWeight, 0.001)
        assertEquals(165.0, records.first().bestEstimatedOneRm, 0.001)
        assertEquals(1, progressRepository.observeWeeklyStats().first().workouts)
        assertEquals(1, progressRepository.observeExerciseProgress(exerciseId).first().size)

        progressRepository.addBodyWeight(82.5, "утро")
        val bodyWeight = progressRepository.observeBodyWeight().first()
        assertEquals(82.5, bodyWeight.first().weightKg, 0.001)
        progressRepository.deleteBodyWeight(bodyWeight.first().id)
        assertEquals(emptyList<Any>(), progressRepository.observeBodyWeight().first())
    }

    @Test
    fun workoutRepository_updatesWorkoutNotesExerciseNotesAndHistoryIndex() = runTest {
        val exerciseId = exerciseDao.insert(ExerciseEntity(name = "Жим", muscleGroup = MuscleGroup.CHEST, equipment = Equipment.BARBELL))
        val repository = DefaultWorkoutRepository(workoutDao, templateDao)
        val workoutId = repository.startWorkout("Грудь")
        val workoutExerciseId = repository.addExerciseToWorkout(workoutId, exerciseId)
        val setId = repository.addSet(workoutExerciseId)

        repository.updateSet(WorkoutSetModel(setId, workoutExerciseId, 1, 100.0, 5, false, "рабочий", 0))
        repository.completeSet(setId, true)
        repository.updateWorkoutNote(workoutId, "хорошая тренировка", energyLevel = 5, sleepQuality = 4, painNote = "локоть ок")
        repository.updateWorkoutExerciseNote(workoutExerciseId, "скамья 3")
        repository.finishWorkout(workoutId)

        val details = repository.observeWorkoutDetails(workoutId).first() ?: error("details not found")
        assertEquals("хорошая тренировка", details.note)
        assertEquals("локоть ок", details.painNote)
        assertEquals(5, details.summary.energyLevel)
        assertEquals(4, details.summary.sleepQuality)
        assertEquals("скамья 3", details.exercises.first().note)

        val history = repository.observeExerciseHistoryIndex().first()
        assertEquals(1, history.getValue(exerciseId).size)
        assertEquals(500.0, history.getValue(exerciseId).first().volume, 0.001)
    }

    @Test
    fun workoutRepository_activeWorkoutEmitsWhenExerciseIsAdded() = runTest {
        val exerciseId = exerciseDao.insert(ExerciseEntity(name = "Жим", muscleGroup = MuscleGroup.CHEST, equipment = Equipment.BARBELL))
        val repository = DefaultWorkoutRepository(workoutDao, templateDao)
        val workoutId = repository.startWorkout("Грудь")

        repository.addExerciseToWorkout(workoutId, exerciseId)

        val active = repository.observeActiveWorkout().first() ?: error("active workout not found")
        assertEquals(workoutId, active.summary.id)
        assertEquals(1, active.exercises.size)
        assertEquals("Жим", active.exercises.first().exerciseName)
    }

    @Test
    fun workoutRepository_addSetUsesNextMaxSetNumberAfterDelete() = runTest {
        val exerciseId = exerciseDao.insert(ExerciseEntity(name = "Жим", muscleGroup = MuscleGroup.CHEST, equipment = Equipment.BARBELL))
        val repository = DefaultWorkoutRepository(workoutDao, templateDao)
        val workoutId = repository.startWorkout("Грудь")
        val workoutExerciseId = repository.addExerciseToWorkout(workoutId, exerciseId)
        val firstSetId = repository.addSet(workoutExerciseId)
        repository.addSet(workoutExerciseId)
        repository.deleteSet(firstSetId)

        repository.addSet(workoutExerciseId)

        assertEquals(listOf(2, 3), workoutDao.getSets(workoutExerciseId).map { it.setNumber })
    }

    @Test
    fun workoutRepository_dashboardSnapshotCalculatesHomeScreenDataFromSingleHistoryFlow() = runTest {
        val recentExerciseId = exerciseDao.insert(ExerciseEntity(name = "Жим", muscleGroup = MuscleGroup.CHEST, equipment = Equipment.BARBELL))
        val oldExerciseId = exerciseDao.insert(ExerciseEntity(name = "Тяга", muscleGroup = MuscleGroup.BACK, equipment = Equipment.BARBELL))
        val repository = DefaultWorkoutRepository(workoutDao, templateDao)
        val recentStartedAt = LocalDateTime.now().minusDays(1).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val oldStartedAt = LocalDateTime.now().minusDays(40).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        insertFinishedWorkout(exerciseId = oldExerciseId, startedAt = oldStartedAt, weight = 140.0, reps = 3)
        insertFinishedWorkout(exerciseId = recentExerciseId, startedAt = recentStartedAt, weight = 100.0, reps = 5)

        val snapshot = repository.observeDashboardSnapshot().first()

        assertEquals("Тест $recentStartedAt", snapshot.lastWorkout?.title)
        assertEquals(1, snapshot.weeklyStats.workouts)
        assertEquals(1, snapshot.monthlyWorkoutCount)
        assertEquals(listOf("Жим" to 100.0, "Тяга" to 140.0), snapshot.lastWeights)
    }

    @Test
    fun workoutRepository_ignoresUpdatesForMissingWorkoutAndSetIds() = runTest {
        val repository = DefaultWorkoutRepository(workoutDao, templateDao)

        repository.completeSet(999, true)
        repository.deleteSet(999)
        repository.updateWorkoutNote(999, "note", 1, 1, "pain")
        repository.updateWorkoutExerciseNote(999, "note")
        repository.finishWorkout(999)
        repository.deleteWorkout(999)

        assertEquals(emptyList<Any>(), workoutDao.getAllSessions())
        assertEquals(emptyList<Any>(), workoutDao.getAllSets())
    }

    @Test
    fun workoutRepository_createsBackfilledWorkoutWithManualStartAndOptionalFinish() = runTest {
        val repository = DefaultWorkoutRepository(workoutDao, templateDao)
        val startedAt = LocalDateTime.of(2024, 1, 10, 18, 30).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val finishedAt = LocalDateTime.of(2024, 1, 10, 19, 45).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

        val finishedWorkoutId = repository.startBackfilledWorkout("Старая грудь", startedAt, finishedAt)
        val activeWorkoutId = repository.startBackfilledWorkout("Старая спина", startedAt, null)
        val invalidFinishId = repository.startBackfilledWorkout("Неверный финиш", finishedAt, startedAt)

        val finished = repository.observeWorkoutDetails(finishedWorkoutId).first() ?: error("finished details not found")
        val active = repository.observeWorkoutDetails(activeWorkoutId).first() ?: error("active details not found")
        val normalized = repository.observeWorkoutDetails(invalidFinishId).first() ?: error("normalized details not found")

        assertEquals("Старая грудь", finished.summary.title)
        assertEquals(startedAt, finished.summary.startedAt)
        assertEquals(75 * 60L, finished.summary.durationSeconds)
        assertEquals(1, repository.observeWorkoutHistory().first().size)
        assertEquals(0, active.summary.durationSeconds)
        assertEquals(0, normalized.summary.durationSeconds)
        assertEquals(activeWorkoutId, workoutDao.observeActiveWorkout().first()?.id)
    }

    @Test
    fun progressRepository_tracksGoalHistoryOneRmAndPlateau() = runTest {
        val exerciseId = exerciseDao.insert(ExerciseEntity(name = "Присед", muscleGroup = MuscleGroup.LEGS, equipment = Equipment.BARBELL))
        val progressRepository = DefaultProgressRepository(workoutDao, bodyWeightDao, goalDao)
        repeat(5) { index ->
            insertFinishedWorkout(exerciseId = exerciseId, startedAt = 1_000L + index, weight = 100.0, reps = 5)
        }

        progressRepository.saveExerciseGoal(exerciseId, targetWeightKg = 120.0, targetReps = 5, note = "рабочая цель")
        val progress = progressRepository.observeExerciseProgress(exerciseId).first()
        val analytics = progressRepository.observeExerciseAnalytics(exerciseId).first()

        assertEquals(5, progress.size)
        assertEquals(100.0, progress.first().maxWeight, 0.001)
        assertEquals(116.666, progress.first().bestEstimatedOneRm, 0.001)
        assertEquals(5, analytics.history.size)
        assertEquals("рабочая цель", analytics.goal?.note)
        assertEquals("Похоже на плато: последние 4 тренировки без заметного роста 1ПМ.", analytics.plateauMessage)
    }

    @Test
    fun progressRepository_aggregatedOverviewMatchesSeparateWeeklyStatsAndRecords() = runTest {
        val recentExerciseId = exerciseDao.insert(ExerciseEntity(name = "Жим", muscleGroup = MuscleGroup.CHEST, equipment = Equipment.BARBELL))
        val oldExerciseId = exerciseDao.insert(ExerciseEntity(name = "Старая тяга", muscleGroup = MuscleGroup.BACK, equipment = Equipment.BARBELL))
        val progressRepository = DefaultProgressRepository(workoutDao, bodyWeightDao, goalDao)
        val recentStartedAt = LocalDateTime.now().minusDays(1).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val oldStartedAt = LocalDateTime.now().minusDays(20).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        insertFinishedWorkout(exerciseId = recentExerciseId, startedAt = recentStartedAt, weight = 100.0, reps = 5)
        insertFinishedWorkout(exerciseId = oldExerciseId, startedAt = oldStartedAt, weight = 180.0, reps = 2)

        val overview = progressRepository.observeProgressOverview().first()

        assertEquals(progressRepository.observeWeeklyStats().first(), overview.weeklyStats)
        assertEquals(progressRepository.observePersonalRecords().first(), overview.personalRecords)
        assertEquals(1, overview.weeklyStats.workouts)
        assertEquals(2, overview.personalRecords.size)
        assertEquals(listOf("Жим", "Старая тяга"), overview.personalRecords.map { it.exerciseName })
    }

    @Test
    fun progressRepository_selectedExerciseProgressCombinesChartPointsHistoryAndGoal() = runTest {
        val selectedExerciseId = exerciseDao.insert(ExerciseEntity(name = "Жим", muscleGroup = MuscleGroup.CHEST, equipment = Equipment.BARBELL))
        val otherExerciseId = exerciseDao.insert(ExerciseEntity(name = "Присед", muscleGroup = MuscleGroup.LEGS, equipment = Equipment.BARBELL))
        val progressRepository = DefaultProgressRepository(workoutDao, bodyWeightDao, goalDao)
        insertFinishedWorkout(exerciseId = selectedExerciseId, startedAt = 3_000L, weight = 100.0, reps = 5)
        insertFinishedWorkout(exerciseId = otherExerciseId, startedAt = 2_000L, weight = 150.0, reps = 3)
        insertFinishedWorkout(exerciseId = selectedExerciseId, startedAt = 1_000L, weight = 90.0, reps = 8)
        progressRepository.saveExerciseGoal(selectedExerciseId, targetWeightKg = 110.0, targetReps = 5, note = "план")

        val selected = progressRepository.observeSelectedExerciseProgress(selectedExerciseId).first()

        assertEquals(listOf(1_000L, 3_000L), selected.points.map { it.date })
        assertEquals(listOf(3_000L, 1_000L), selected.analytics.history.map { it.date })
        assertEquals(2, selected.points.size)
        assertEquals(2, selected.analytics.history.size)
        assertEquals(110.0, selected.analytics.goal?.targetWeightKg ?: 0.0, 0.001)
        assertEquals("план", selected.analytics.goal?.note)
    }

    @Test
    fun progressRepository_selectedExerciseProgressReturnsEmptyForMissingExercise() = runTest {
        val exerciseId = exerciseDao.insert(ExerciseEntity(name = "Жим", muscleGroup = MuscleGroup.CHEST, equipment = Equipment.BARBELL))
        val progressRepository = DefaultProgressRepository(workoutDao, bodyWeightDao, goalDao)
        insertFinishedWorkout(exerciseId = exerciseId, startedAt = 1_000L, weight = 100.0, reps = 5)

        val selected = progressRepository.observeSelectedExerciseProgress(exerciseId + 100).first()

        assertEquals(emptyList<Any>(), selected.points)
        assertEquals(emptyList<Any>(), selected.analytics.history)
        assertNull(selected.analytics.goal)
        assertNull(selected.analytics.plateauMessage)
    }

    @Test
    fun progressRepository_goalValidationDeleteAndEmptyAnalyticsCornerCases() = runTest {
        val exerciseId = exerciseDao.insert(ExerciseEntity(name = "Тяга", muscleGroup = MuscleGroup.BACK, equipment = Equipment.BARBELL))
        val progressRepository = DefaultProgressRepository(workoutDao, bodyWeightDao, goalDao)

        progressRepository.saveExerciseGoal(exerciseId, targetWeightKg = 0.0, targetReps = 5, note = "bad")
        progressRepository.saveExerciseGoal(exerciseId, targetWeightKg = 100.0, targetReps = 0, note = "bad")
        assertNull(progressRepository.observeExerciseAnalytics(exerciseId).first().goal)

        progressRepository.saveExerciseGoal(exerciseId, targetWeightKg = 180.0, targetReps = 3, note = "valid")
        assertEquals(180.0, progressRepository.observeExerciseAnalytics(exerciseId).first().goal?.targetWeightKg ?: 0.0, 0.001)

        progressRepository.deleteExerciseGoal(exerciseId)
        val analytics = progressRepository.observeExerciseAnalytics(exerciseId).first()
        assertNull(analytics.goal)
        assertEquals(emptyList<Any>(), analytics.history)
        assertNull(analytics.plateauMessage)
    }

    @Test
    fun progressRepository_weeklyStatsIgnoresOldFinishedWorkoutsAndInvalidSets() = runTest {
        val exerciseId = exerciseDao.insert(ExerciseEntity(name = "Тяга", muscleGroup = MuscleGroup.BACK, equipment = Equipment.BARBELL))
        val progressRepository = DefaultProgressRepository(workoutDao, bodyWeightDao, goalDao)
        val oldStartedAt = LocalDateTime.now().minusDays(10).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val recentStartedAt = LocalDateTime.now().minusDays(1).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        insertFinishedWorkout(exerciseId = exerciseId, startedAt = oldStartedAt, weight = 100.0, reps = 5)
        insertFinishedWorkout(exerciseId = exerciseId, startedAt = recentStartedAt, weight = 0.0, reps = 12)

        val stats = progressRepository.observeWeeklyStats().first()

        assertEquals(1, stats.workouts)
        assertEquals(1, stats.sets)
        assertEquals(0.0, stats.volume, 0.001)
    }

    @Test
    fun progressRepository_plateauRequiresFourRecentAndHistoricalBest() = runTest {
        val exerciseId = exerciseDao.insert(ExerciseEntity(name = "Присед", muscleGroup = MuscleGroup.LEGS, equipment = Equipment.BARBELL))
        val progressRepository = DefaultProgressRepository(workoutDao, bodyWeightDao, goalDao)
        repeat(4) { index ->
            insertFinishedWorkout(exerciseId = exerciseId, startedAt = 2_000L + index, weight = 100.0 + index, reps = 5)
        }

        assertNull(progressRepository.observeExerciseAnalytics(exerciseId).first().plateauMessage)

        insertFinishedWorkout(exerciseId = exerciseId, startedAt = 1_000L, weight = 80.0, reps = 5)

        assertNull(progressRepository.observeExerciseAnalytics(exerciseId).first().plateauMessage)
    }

    @Test
    fun settingsRepository_exportsAndImportsAllLinkedData() = runTest {
        val exerciseId = exerciseDao.insert(ExerciseEntity(name = "Жим", muscleGroup = MuscleGroup.CHEST, equipment = Equipment.BARBELL))
        val workoutRepository = DefaultWorkoutRepository(workoutDao, templateDao)
        val templateRepository = DefaultWorkoutTemplateRepository(workoutDao, templateDao)
        val progressRepository = DefaultProgressRepository(workoutDao, bodyWeightDao, goalDao)
        val settingsRepository = DefaultSettingsRepository(database, exerciseDao, workoutDao, templateDao, bodyWeightDao, goalDao, settingsDataSource)
        val workoutId = workoutRepository.startWorkout("Грудь")
        val workoutExerciseId = workoutRepository.addExerciseToWorkout(workoutId, exerciseId)
        val setId = workoutRepository.addSet(workoutExerciseId)
        workoutRepository.updateSet(WorkoutSetModel(setId, workoutExerciseId, 1, 100.0, 5, false, "", 0))
        workoutRepository.completeSet(setId, true)
        workoutRepository.finishWorkout(workoutId)
        templateRepository.createTemplateFromWorkout(workoutId, "Шаблон груди")
        progressRepository.addBodyWeight(80.0, "после тренировки")
        progressRepository.saveExerciseGoal(exerciseId, 110.0, 5, "цель")
        workoutRepository.updateWorkoutNote(workoutId, "note", energyLevel = 4, sleepQuality = 3, painNote = "нет")
        settingsRepository.updateTheme(ThemeMode.DARK)
        settingsRepository.updateHaptics(false)

        val exported = settingsRepository.exportData()
        val tree = Json.parseToJsonElement(exported).jsonObject
        assertEquals(1, tree.getValue("workoutSessions").jsonArray.size)
        assertEquals(1, tree.getValue("workoutExercises").jsonArray.size)
        assertEquals(1, tree.getValue("workoutSets").jsonArray.size)
        assertEquals(1, tree.getValue("templates").jsonArray.size)
        assertEquals(1, tree.getValue("bodyWeightEntries").jsonArray.size)
        assertEquals(1, tree.getValue("exerciseGoals").jsonArray.size)

        database.clearAllTables()
        assertEquals(emptyList<Any>(), workoutDao.getAllSessions())
        settingsRepository.importData(exported)

        assertEquals(1, workoutDao.getAllSessions().size)
        assertEquals(1, workoutDao.getAllWorkoutExercises().size)
        assertEquals(1, workoutDao.getAllSets().size)
        assertEquals(1, templateDao.getAllTemplates().size)
        assertEquals(1, bodyWeightDao.getAll().size)
        assertEquals(1, goalDao.getAll().size)
        assertEquals(ThemeMode.DARK, settingsDataSource.settings.first().themeMode)
        assertEquals(false, settingsDataSource.settings.first().hapticsEnabled)
        val restored = workoutDao.getWorkoutDetails(workoutId)
        assertNotEquals(null, restored)
        assertEquals(4, restored?.session?.energyLevel)
        assertEquals(3, restored?.session?.sleepQuality)
        assertEquals("нет", restored?.session?.painNote)
    }

    @Test
    fun settingsRepository_importAcceptsLegacyUnknownRpeField() = runTest {
        val exerciseId = exerciseDao.insert(ExerciseEntity(name = "Жим", muscleGroup = MuscleGroup.CHEST, equipment = Equipment.BARBELL))
        val workoutRepository = DefaultWorkoutRepository(workoutDao, templateDao)
        val settingsRepository = DefaultSettingsRepository(database, exerciseDao, workoutDao, templateDao, bodyWeightDao, goalDao, settingsDataSource)
        val workoutId = workoutRepository.startWorkout("Грудь")
        val workoutExerciseId = workoutRepository.addExerciseToWorkout(workoutId, exerciseId)
        val setId = workoutRepository.addSet(workoutExerciseId)
        workoutRepository.updateSet(WorkoutSetModel(setId, workoutExerciseId, 1, 100.0, 5, false, "", 0))
        val legacyJson = settingsRepository.exportData().replace("\"isCompleted\"", "\"rpe\": 8.5,\n      \"isCompleted\"")

        database.clearAllTables()
        settingsRepository.importData(legacyJson)

        val restored = workoutDao.getAllSets().single()
        assertEquals(100.0, restored.weightKg, 0.001)
        assertEquals(5, restored.reps)
    }

    @Test
    fun settingsRepository_structurallyInvalidImportKeepsExistingData() = runTest {
        val exerciseId = exerciseDao.insert(ExerciseEntity(name = "Жим", muscleGroup = MuscleGroup.CHEST, equipment = Equipment.BARBELL))
        val workoutRepository = DefaultWorkoutRepository(workoutDao, templateDao)
        val settingsRepository = DefaultSettingsRepository(database, exerciseDao, workoutDao, templateDao, bodyWeightDao, goalDao, settingsDataSource)
        val workoutId = workoutRepository.startWorkout("Грудь")
        val workoutExerciseId = workoutRepository.addExerciseToWorkout(workoutId, exerciseId)
        val setId = workoutRepository.addSet(workoutExerciseId)
        workoutRepository.updateSet(WorkoutSetModel(setId, workoutExerciseId, 1, 100.0, 5, false, "", 0))
        val invalidLinkedJson = settingsRepository.exportData().replace("\"exerciseId\": $exerciseId", "\"exerciseId\": 999")

        runCatching { settingsRepository.importData(invalidLinkedJson) }

        assertEquals(1, exerciseDao.getAll().size)
        assertEquals(1, workoutDao.getAllSessions().size)
        assertEquals(1, workoutDao.getAllSets().size)
    }

    @Test
    fun settingsRepository_clearAllDataReseedsDefaultsAndClearsUserData() = runTest {
        val exerciseId = exerciseDao.insert(ExerciseEntity(name = "Мой жим", muscleGroup = MuscleGroup.CHEST, equipment = Equipment.BARBELL))
        val workoutRepository = DefaultWorkoutRepository(workoutDao, templateDao)
        val settingsRepository = DefaultSettingsRepository(database, exerciseDao, workoutDao, templateDao, bodyWeightDao, goalDao, settingsDataSource)
        val workoutId = workoutRepository.startWorkout("Грудь")
        workoutRepository.addExerciseToWorkout(workoutId, exerciseId)

        settingsRepository.clearAllData()

        assertEquals(emptyList<Any>(), workoutDao.getAllSessions())
        assertTrue(exerciseDao.getAll().size >= 18)
        assertEquals(null, exerciseDao.getAll().firstOrNull { it.name == "Мой жим" })
    }

    @Test
    fun settingsRepository_invalidImportKeepsExistingData() = runTest {
        val settingsRepository = DefaultSettingsRepository(database, exerciseDao, workoutDao, templateDao, bodyWeightDao, goalDao, settingsDataSource)
        val exerciseId = exerciseDao.insert(ExerciseEntity(name = "Жим", muscleGroup = MuscleGroup.CHEST, equipment = Equipment.BARBELL))

        runCatching { settingsRepository.importData("{bad json}") }

        assertEquals(exerciseId, exerciseDao.getAll().first().id)
    }

    @Test
    fun settingsRepository_importWithMissingRequiredFieldsKeepsExistingData() = runTest {
        val settingsRepository = DefaultSettingsRepository(database, exerciseDao, workoutDao, templateDao, bodyWeightDao, goalDao, settingsDataSource)
        val exerciseId = exerciseDao.insert(ExerciseEntity(name = "Жим", muscleGroup = MuscleGroup.CHEST, equipment = Equipment.BARBELL))
        val invalidJson = """
            {
              "version": 1,
              "exercises": [],
              "workoutSessions": []
            }
        """.trimIndent()

        runCatching { settingsRepository.importData(invalidJson) }

        assertEquals(listOf(exerciseId), exerciseDao.getAll().map { it.id })
        assertEquals(emptyList<Any>(), workoutDao.getAllSessions())
    }

    @Test
    fun exerciseRepository_seedsSearchesUpdatesAndBlocksUsedDelete() = runTest {
        val exerciseRepository = DefaultExerciseRepository(exerciseDao)
        val workoutRepository = DefaultWorkoutRepository(workoutDao, templateDao)
        exerciseRepository.seedDefaultsIfEmpty()
        assertTrue(exerciseRepository.observeExercises().first().size >= 18)

        val customId = exerciseRepository.createExercise("Мой жим", MuscleGroup.CHEST, Equipment.BARBELL, "note")
        assertEquals(1, exerciseRepository.searchExercises("мой", MuscleGroup.CHEST, Equipment.BARBELL).first().size)
        val custom = exerciseRepository.getExercise(customId) ?: error("exercise not found")
        exerciseRepository.updateExercise(custom.copy(name = "Мой жим 2"))
        assertEquals("Мой жим 2", exerciseRepository.getExercise(customId)?.name)

        val workoutId = workoutRepository.startWorkout("test")
        workoutRepository.addExerciseToWorkout(workoutId, customId)
        runCatching { exerciseRepository.deleteExercise(customId) }
        assertEquals("Мой жим 2", exerciseRepository.getExercise(customId)?.name)
    }

    @Test
    fun exerciseRepository_trimsCreateUpdateAndDeletesUnusedCustomOnly() = runTest {
        val exerciseRepository = DefaultExerciseRepository(exerciseDao)
        val customId = exerciseRepository.createExercise("  Мой жим  ", MuscleGroup.CHEST, Equipment.BARBELL, "  note  ")
        exerciseDao.insert(ExerciseEntity(name = "Базовый жим", muscleGroup = MuscleGroup.CHEST, equipment = Equipment.BARBELL, isCustom = false))

        val custom = exerciseRepository.getExercise(customId) ?: error("custom exercise not found")
        assertEquals("Мой жим", custom.name)
        assertEquals("note", custom.note)

        exerciseRepository.updateExercise(custom.copy(name = "  Новый жим  ", note = "  новая заметка  "))
        assertEquals("Новый жим", exerciseRepository.getExercise(customId)?.name)
        assertEquals("новая заметка", exerciseRepository.getExercise(customId)?.note)

        val builtInId = exerciseDao.getAll().first { !it.isCustom }.id
        exerciseRepository.deleteExercise(builtInId)
        exerciseRepository.deleteExercise(customId)

        assertNotEquals(null, exerciseRepository.getExercise(builtInId))
        assertEquals(null, exerciseRepository.getExercise(customId))
    }

    @Test
    fun templateRepository_missingAndEmptyWorkoutCornerCases() = runTest {
        val templateRepository = DefaultWorkoutTemplateRepository(workoutDao, templateDao)
        val workoutRepository = DefaultWorkoutRepository(workoutDao, templateDao)
        val emptyWorkoutId = workoutRepository.startWorkout("Пустая")

        val emptyTemplateId = templateRepository.createTemplateFromWorkout(emptyWorkoutId, "  Пустой шаблон  ", "  note  ")
        val startedMissingId = templateRepository.startWorkoutFromTemplate(999)

        val templates = templateRepository.observeTemplates().first()
        assertEquals(0L, emptyTemplateId)
        assertEquals(emptyList<WorkoutTemplateSummary>(), templates)
        assertEquals(0L, startedMissingId)
    }

    private suspend fun insertFinishedWorkout(exerciseId: Long, startedAt: Long, weight: Double, reps: Int) {
        val sessionId = workoutDao.insertSession(
            ru.fuezl.gymdiary.core.database.WorkoutSessionEntity(
                title = "Тест $startedAt",
                startedAt = startedAt,
                finishedAt = startedAt + 1,
                durationSeconds = 1
            )
        )
        val workoutExerciseId = workoutDao.insertWorkoutExercise(
            ru.fuezl.gymdiary.core.database.WorkoutExerciseEntity(
                workoutSessionId = sessionId,
                exerciseId = exerciseId,
                orderIndex = 0
            )
        )
        workoutDao.insertSet(
            ru.fuezl.gymdiary.core.database.WorkoutSetEntity(
                workoutExerciseId = workoutExerciseId,
                setNumber = 1,
                weightKg = weight,
                reps = reps,
                isCompleted = true
            )
        )
    }
}

private class FakeSettingsLocalDataSource : SettingsLocalDataSource {
    private val state = MutableStateFlow(UserSettings())
    override val settings = state

    override suspend fun updateTheme(themeMode: ThemeMode) {
        state.value = state.value.copy(themeMode = themeMode)
    }

    override suspend fun updateRestTimer(enabled: Boolean, seconds: Int) {
        state.value = state.value.copy(restTimerEnabled = enabled, defaultRestTimerSeconds = seconds)
    }

    override suspend fun updateHaptics(enabled: Boolean) {
        state.value = state.value.copy(hapticsEnabled = enabled)
    }

    override suspend fun restore(settings: UserSettings) {
        state.value = settings
    }
}
