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

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, GymDiaryDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        exerciseDao = database.exerciseDao()
        workoutDao = database.workoutDao()
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
}
