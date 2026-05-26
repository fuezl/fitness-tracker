package ru.fuezl.gymdiary.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import ru.fuezl.gymdiary.core.model.Equipment
import ru.fuezl.gymdiary.core.model.MuscleGroup

@RunWith(AndroidJUnit4::class)
class GymDiaryDatabaseTest {
    private lateinit var database: GymDiaryDatabase
    private lateinit var exerciseDao: ExerciseDao
    private lateinit var workoutDao: WorkoutDao
    private lateinit var templateDao: WorkoutTemplateDao
    private lateinit var goalDao: ExerciseGoalDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, GymDiaryDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        exerciseDao = database.exerciseDao()
        workoutDao = database.workoutDao()
        templateDao = database.workoutTemplateDao()
        goalDao = database.exerciseGoalDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun insertExercise_observeExercisesReturnsIt() = runTest {
        exerciseDao.insert(ExerciseEntity(name = "Жим", muscleGroup = MuscleGroup.CHEST, equipment = Equipment.BARBELL))

        val exercises = exerciseDao.observeExercises().first()

        assertEquals(1, exercises.size)
        assertEquals("Жим", exercises.first().name)
    }

    @Test
    fun searchExercises_filtersByQueryMuscleEquipmentAndSortsCaseInsensitive() = runTest {
        exerciseDao.insert(ExerciseEntity(name = "жим гантелей", muscleGroup = MuscleGroup.CHEST, equipment = Equipment.DUMBBELL))
        exerciseDao.insert(ExerciseEntity(name = "Жим штанги", muscleGroup = MuscleGroup.CHEST, equipment = Equipment.BARBELL))
        exerciseDao.insert(ExerciseEntity(name = "Тяга блока", muscleGroup = MuscleGroup.BACK, equipment = Equipment.MACHINE))

        val chestPresses = exerciseDao.searchExercises("ЖИМ", MuscleGroup.CHEST.name, null).first()
        val barbellPresses = exerciseDao.searchExercises("жим", MuscleGroup.CHEST.name, Equipment.BARBELL.name).first()
        val empty = exerciseDao.searchExercises("жим", MuscleGroup.BACK.name, null).first()

        assertEquals(setOf("жим гантелей", "Жим штанги"), chestPresses.map { it.name }.toSet())
        assertEquals(listOf("Жим штанги"), barbellPresses.map { it.name })
        assertEquals(emptyList<ExerciseEntity>(), empty)
    }

    @Test
    fun insertWorkoutWithExerciseAndSet_returnsDetails() = runTest {
        val exerciseId = exerciseDao.insert(ExerciseEntity(name = "Присед", muscleGroup = MuscleGroup.LEGS, equipment = Equipment.BARBELL))
        val sessionId = workoutDao.insertSession(WorkoutSessionEntity(title = "Ноги", startedAt = 1, finishedAt = 2, durationSeconds = 1))
        val workoutExerciseId = workoutDao.insertWorkoutExercise(WorkoutExerciseEntity(workoutSessionId = sessionId, exerciseId = exerciseId, orderIndex = 0))
        workoutDao.insertSet(WorkoutSetEntity(workoutExerciseId = workoutExerciseId, setNumber = 1, weightKg = 100.0, reps = 5, isCompleted = true))

        val details = workoutDao.getWorkoutDetails(sessionId)

        assertNotNull(details)
        assertEquals("Ноги", details?.session?.title)
        assertEquals(1, details?.exercises?.size)
        assertEquals(1, details?.exercises?.first()?.sets?.size)
    }

    @Test
    fun getSets_returnsBySetNumberEvenWhenInsertedOutOfOrder() = runTest {
        val exerciseId = exerciseDao.insert(ExerciseEntity(name = "Присед", muscleGroup = MuscleGroup.LEGS, equipment = Equipment.BARBELL))
        val sessionId = workoutDao.insertSession(WorkoutSessionEntity(title = "Ноги", startedAt = 1))
        val workoutExerciseId = workoutDao.insertWorkoutExercise(WorkoutExerciseEntity(workoutSessionId = sessionId, exerciseId = exerciseId, orderIndex = 0))
        workoutDao.insertSet(WorkoutSetEntity(workoutExerciseId = workoutExerciseId, setNumber = 2, weightKg = 90.0, reps = 5))
        workoutDao.insertSet(WorkoutSetEntity(workoutExerciseId = workoutExerciseId, setNumber = 1, weightKg = 80.0, reps = 8))

        val sets = workoutDao.getSets(workoutExerciseId)

        assertEquals(listOf(1, 2), sets.map { it.setNumber })
    }

    @Test
    fun aggregateWorkoutQueries_returnCountsAndMaxSetNumberWithoutLoadingRows() = runTest {
        val exerciseId = exerciseDao.insert(ExerciseEntity(name = "Присед", muscleGroup = MuscleGroup.LEGS, equipment = Equipment.BARBELL))
        val sessionId = workoutDao.insertSession(WorkoutSessionEntity(title = "Ноги", startedAt = 1))
        val workoutExerciseId = workoutDao.insertWorkoutExercise(WorkoutExerciseEntity(workoutSessionId = sessionId, exerciseId = exerciseId, orderIndex = 0))

        assertEquals(1, workoutDao.getWorkoutExerciseCount(sessionId))
        assertEquals(0, workoutDao.getMaxSetNumber(workoutExerciseId))

        val firstSetId = workoutDao.insertSet(WorkoutSetEntity(workoutExerciseId = workoutExerciseId, setNumber = 1, weightKg = 80.0, reps = 8))
        workoutDao.insertSet(WorkoutSetEntity(workoutExerciseId = workoutExerciseId, setNumber = 3, weightKg = 100.0, reps = 5))
        workoutDao.deleteSet(firstSetId)

        assertEquals(3, workoutDao.getMaxSetNumber(workoutExerciseId))
    }

    @Test
    fun deleteWorkout_cascadesWorkoutExercisesAndSets() = runTest {
        val exerciseId = exerciseDao.insert(ExerciseEntity(name = "Тяга", muscleGroup = MuscleGroup.BACK, equipment = Equipment.BARBELL))
        val sessionId = workoutDao.insertSession(WorkoutSessionEntity(title = "Спина", startedAt = 1, finishedAt = 2, durationSeconds = 1))
        val workoutExerciseId = workoutDao.insertWorkoutExercise(WorkoutExerciseEntity(workoutSessionId = sessionId, exerciseId = exerciseId, orderIndex = 0))
        workoutDao.insertSet(WorkoutSetEntity(workoutExerciseId = workoutExerciseId, setNumber = 1, weightKg = 120.0, reps = 3, isCompleted = true))

        workoutDao.deleteSession(sessionId)

        assertEquals(null, workoutDao.getWorkoutDetails(sessionId))
        assertEquals(emptyList<WorkoutExerciseEntity>(), workoutDao.getAllWorkoutExercises())
        assertEquals(emptyList<WorkoutSetEntity>(), workoutDao.getAllSets())
    }

    @Test
    fun deleteExercise_restrictsWhenExerciseIsUsedByWorkout() = runTest {
        val exerciseId = exerciseDao.insert(ExerciseEntity(name = "Жим", muscleGroup = MuscleGroup.CHEST, equipment = Equipment.BARBELL))
        val sessionId = workoutDao.insertSession(WorkoutSessionEntity(title = "Грудь", startedAt = 1))
        workoutDao.insertWorkoutExercise(WorkoutExerciseEntity(workoutSessionId = sessionId, exerciseId = exerciseId, orderIndex = 0))

        val result = runCatching { exerciseDao.delete(exerciseDao.getExercise(exerciseId) ?: error("exercise not found")) }

        assertTrue(result.isFailure)
        assertNotNull(exerciseDao.getExercise(exerciseId))
    }

    @Test
    fun workoutSession_persistsWellnessFields() = runTest {
        val sessionId = workoutDao.insertSession(
            WorkoutSessionEntity(
                title = "Проверка",
                startedAt = 1,
                finishedAt = 2,
                durationSeconds = 1,
                note = "note",
                energyLevel = 4,
                sleepQuality = 3,
                painNote = "нет"
            )
        )

        val session = workoutDao.getSession(sessionId)

        assertEquals(4, session?.energyLevel)
        assertEquals(3, session?.sleepQuality)
        assertEquals("нет", session?.painNote)
    }

    @Test
    fun templateSummaries_returnCountsWithoutPerTemplateQueries() = runTest {
        val exerciseId = exerciseDao.insert(ExerciseEntity(name = "Жим", muscleGroup = MuscleGroup.CHEST, equipment = Equipment.BARBELL))
        val secondExerciseId = exerciseDao.insert(ExerciseEntity(name = "Тяга", muscleGroup = MuscleGroup.BACK, equipment = Equipment.BARBELL))
        val secondTemplateId = templateDao.insertTemplate(WorkoutTemplateEntity(title = "Б шаблон", note = "2"))
        val firstTemplateId = templateDao.insertTemplate(WorkoutTemplateEntity(title = "А шаблон", note = "1"))
        templateDao.insertTemplateExercise(WorkoutTemplateExerciseEntity(templateId = secondTemplateId, exerciseId = exerciseId, orderIndex = 0))
        templateDao.insertTemplateExercise(WorkoutTemplateExerciseEntity(templateId = secondTemplateId, exerciseId = secondExerciseId, orderIndex = 1))

        val summaries = templateDao.observeTemplateSummaries().first()

        assertEquals(listOf("А шаблон", "Б шаблон"), summaries.map { it.title })
        assertEquals(0, summaries.first { it.id == firstTemplateId }.exerciseCount)
        assertEquals(2, summaries.first { it.id == secondTemplateId }.exerciseCount)
    }

    @Test
    fun exerciseGoal_upsertsObservesDeletesAndCascadesWithExercise() = runTest {
        val exerciseId = exerciseDao.insert(ExerciseEntity(name = "Жим", muscleGroup = MuscleGroup.CHEST, equipment = Equipment.BARBELL))
        goalDao.upsert(ExerciseGoalEntity(exerciseId = exerciseId, targetWeightKg = 100.0, targetReps = 5, note = "цель"))
        goalDao.upsert(ExerciseGoalEntity(id = goalDao.getAll().first().id, exerciseId = exerciseId, targetWeightKg = 105.0, targetReps = 3, note = "новая"))

        val goal = goalDao.observeGoal(exerciseId).first()

        assertEquals(1, goalDao.getAll().size)
        assertEquals(105.0, goal?.targetWeightKg ?: 0.0, 0.001)
        assertEquals(3, goal?.targetReps)

        goalDao.deleteForExercise(exerciseId)
        assertNull(goalDao.observeGoal(exerciseId).first())

        goalDao.upsert(ExerciseGoalEntity(exerciseId = exerciseId, targetWeightKg = 110.0, targetReps = 1))
        exerciseDao.delete(exerciseDao.getExercise(exerciseId) ?: error("exercise not found"))
        assertNull(goalDao.observeGoal(exerciseId).first())
    }
}
