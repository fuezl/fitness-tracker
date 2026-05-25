package ru.fuezl.gymdiary.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import ru.fuezl.gymdiary.core.database.BodyWeightDao
import ru.fuezl.gymdiary.core.database.ExerciseDao
import ru.fuezl.gymdiary.core.database.ExerciseEntity
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
        repository.updateSet(WorkoutSetModel(setId, workoutExerciseId, 1, 100.0, 5, 8.0, false, "", 0))
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
        val progressRepository = DefaultProgressRepository(workoutDao, bodyWeightDao)
        val workoutId = workoutRepository.startWorkout("Спина")
        val workoutExerciseId = workoutRepository.addExerciseToWorkout(workoutId, exerciseId)
        val setId = workoutRepository.addSet(workoutExerciseId)
        workoutRepository.updateSet(WorkoutSetModel(setId, workoutExerciseId, 1, 150.0, 3, 9.0, false, "", 0))
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
    fun settingsRepository_exportsAndImportsAllLinkedData() = runTest {
        val exerciseId = exerciseDao.insert(ExerciseEntity(name = "Жим", muscleGroup = MuscleGroup.CHEST, equipment = Equipment.BARBELL))
        val workoutRepository = DefaultWorkoutRepository(workoutDao, templateDao)
        val templateRepository = DefaultWorkoutTemplateRepository(workoutDao, templateDao)
        val progressRepository = DefaultProgressRepository(workoutDao, bodyWeightDao)
        val settingsRepository = DefaultSettingsRepository(database, exerciseDao, workoutDao, templateDao, bodyWeightDao, settingsDataSource)
        val workoutId = workoutRepository.startWorkout("Грудь")
        val workoutExerciseId = workoutRepository.addExerciseToWorkout(workoutId, exerciseId)
        val setId = workoutRepository.addSet(workoutExerciseId)
        workoutRepository.updateSet(WorkoutSetModel(setId, workoutExerciseId, 1, 100.0, 5, null, false, "", 0))
        workoutRepository.completeSet(setId, true)
        workoutRepository.finishWorkout(workoutId)
        templateRepository.createTemplateFromWorkout(workoutId, "Шаблон груди")
        progressRepository.addBodyWeight(80.0, "после тренировки")
        settingsRepository.updateTheme(ThemeMode.DARK)

        val exported = settingsRepository.exportData()
        val tree = Json.parseToJsonElement(exported).jsonObject
        assertEquals(1, tree.getValue("workoutSessions").jsonArray.size)
        assertEquals(1, tree.getValue("workoutExercises").jsonArray.size)
        assertEquals(1, tree.getValue("workoutSets").jsonArray.size)
        assertEquals(1, tree.getValue("templates").jsonArray.size)
        assertEquals(1, tree.getValue("bodyWeightEntries").jsonArray.size)

        database.clearAllTables()
        assertEquals(emptyList<Any>(), workoutDao.getAllSessions())
        settingsRepository.importData(exported)

        assertEquals(1, workoutDao.getAllSessions().size)
        assertEquals(1, workoutDao.getAllWorkoutExercises().size)
        assertEquals(1, workoutDao.getAllSets().size)
        assertEquals(1, templateDao.getAllTemplates().size)
        assertEquals(1, bodyWeightDao.getAll().size)
        assertEquals(ThemeMode.DARK, settingsDataSource.settings.first().themeMode)
        assertNotEquals(null, workoutDao.getWorkoutDetails(workoutId))
    }

    @Test
    fun settingsRepository_invalidImportKeepsExistingData() = runTest {
        val settingsRepository = DefaultSettingsRepository(database, exerciseDao, workoutDao, templateDao, bodyWeightDao, settingsDataSource)
        val exerciseId = exerciseDao.insert(ExerciseEntity(name = "Жим", muscleGroup = MuscleGroup.CHEST, equipment = Equipment.BARBELL))

        runCatching { settingsRepository.importData("{bad json}") }

        assertEquals(exerciseId, exerciseDao.getAll().first().id)
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

    override suspend fun restore(settings: UserSettings) {
        state.value = settings
    }
}
