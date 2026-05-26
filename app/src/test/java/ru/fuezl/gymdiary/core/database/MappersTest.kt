package ru.fuezl.gymdiary.core.database

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.fuezl.gymdiary.core.model.Equipment
import ru.fuezl.gymdiary.core.model.MuscleGroup

class MappersTest {
    @Test
    fun exerciseEntityAsModel_mapsAllFields() {
        val entity = ExerciseEntity(
            id = 7,
            name = "Жим лёжа",
            muscleGroup = MuscleGroup.CHEST,
            equipment = Equipment.BARBELL,
            note = "Средний хват",
            isCustom = false,
            createdAt = 100,
            updatedAt = 200
        )

        val model = entity.asModel()

        assertEquals(7L, model.id)
        assertEquals("Жим лёжа", model.name)
        assertEquals(MuscleGroup.CHEST, model.muscleGroup)
        assertEquals(Equipment.BARBELL, model.equipment)
        assertEquals("Средний хват", model.note)
        assertFalse(model.isCustom)
        assertEquals(100L, model.createdAt)
        assertEquals(200L, model.updatedAt)
    }

    @Test
    fun workoutSetEntityAsModel_mapsPlannedAndActualValues() {
        val entity = WorkoutSetEntity(
            id = 11,
            workoutExerciseId = 22,
            setNumber = 3,
            plannedWeightKg = 105.0,
            plannedReps = 5,
            weightKg = 107.5,
            reps = 4,
            isCompleted = true,
            note = "Тяжело",
            createdAt = 300
        )

        val model = entity.asModel()

        assertEquals(11L, model.id)
        assertEquals(22L, model.workoutExerciseId)
        assertEquals(3, model.setNumber)
        assertEquals(105.0, model.plannedWeightKg ?: 0.0, 0.001)
        assertEquals(5, model.plannedReps)
        assertEquals(107.5, model.weightKg, 0.001)
        assertEquals(4, model.reps)
        assertTrue(model.isCompleted)
        assertEquals("Тяжело", model.note)
        assertEquals(300L, model.createdAt)
    }

    @Test
    fun workoutSetEntityAsModel_keepsMissingPlanAsNull() {
        val model = WorkoutSetEntity(
            id = 1,
            workoutExerciseId = 2,
            setNumber = 1,
            plannedWeightKg = null,
            plannedReps = null,
            weightKg = 0.0,
            reps = 0
        ).asModel()

        assertNull(model.plannedWeightKg)
        assertNull(model.plannedReps)
        assertFalse(model.isCompleted)
    }

    @Test
    fun workoutSessionWithDetailsAsDetails_sortsExercisesAndSetsAndBuildsSummary() {
        val details = WorkoutSessionWithDetails(
            session = WorkoutSessionEntity(
                id = 1,
                title = "Ноги",
                startedAt = 1_000,
                durationSeconds = 3_600,
                note = "Нормально",
                painNote = "Нет"
            ),
            exercises = listOf(
                exerciseWithSets(
                    workoutExerciseId = 20,
                    orderIndex = 2,
                    exerciseId = 200,
                    exerciseName = "Подтягивания",
                    restSeconds = null,
                    sets = listOf(
                        set(id = 4, workoutExerciseId = 20, setNumber = 2, weightKg = 0.0, reps = 8, isCompleted = true),
                        set(id = 3, workoutExerciseId = 20, setNumber = 1, weightKg = 0.0, reps = 10, isCompleted = true)
                    )
                ),
                exerciseWithSets(
                    workoutExerciseId = 10,
                    orderIndex = 1,
                    exerciseId = 100,
                    exerciseName = "Присед",
                    restSeconds = 180,
                    sets = listOf(
                        set(id = 2, workoutExerciseId = 10, setNumber = 2, weightKg = 120.0, reps = 5, isCompleted = false),
                        set(id = 1, workoutExerciseId = 10, setNumber = 1, weightKg = 100.0, reps = 5, isCompleted = true)
                    )
                )
            )
        ).asDetails()

        assertEquals("Ноги", details.summary.title)
        assertEquals(2, details.summary.exerciseCount)
        assertEquals(3, details.summary.setCount)
        assertEquals(500.0, details.summary.totalVolume, 0.001)
        assertEquals(18, details.summary.bodyweightReps)
        assertEquals("Нормально", details.note)
        assertEquals("Нет", details.painNote)
        assertEquals(listOf("Присед", "Подтягивания"), details.exercises.map { it.exerciseName })
        assertEquals(listOf(1L, 2L), details.exercises.first().sets.map { it.id })
        assertEquals(180, details.exercises.first().restSeconds)
        assertNull(details.exercises.last().restSeconds)
    }

    @Test
    fun workoutSessionWithDetailsAsDetails_handlesEmptyExercises() {
        val details = WorkoutSessionWithDetails(
            session = WorkoutSessionEntity(id = 1, title = "Пустая", startedAt = 500),
            exercises = emptyList()
        ).asDetails()

        assertEquals(0, details.summary.exerciseCount)
        assertEquals(0, details.summary.setCount)
        assertEquals(0.0, details.summary.totalVolume, 0.001)
        assertEquals(0, details.summary.bodyweightReps)
        assertTrue(details.exercises.isEmpty())
    }

    private fun exerciseWithSets(
        workoutExerciseId: Long,
        orderIndex: Int,
        exerciseId: Long,
        exerciseName: String,
        restSeconds: Int?,
        sets: List<WorkoutSetEntity>
    ): WorkoutExerciseWithSets = WorkoutExerciseWithSets(
        workoutExercise = WorkoutExerciseEntity(
            id = workoutExerciseId,
            workoutSessionId = 1,
            exerciseId = exerciseId,
            orderIndex = orderIndex,
            note = "Заметка $workoutExerciseId",
            restSeconds = restSeconds
        ),
        exercise = ExerciseEntity(
            id = exerciseId,
            name = exerciseName,
            muscleGroup = MuscleGroup.LEGS,
            equipment = Equipment.BARBELL
        ),
        sets = sets
    )

    private fun set(
        id: Long,
        workoutExerciseId: Long,
        setNumber: Int,
        weightKg: Double,
        reps: Int,
        isCompleted: Boolean
    ): WorkoutSetEntity = WorkoutSetEntity(
        id = id,
        workoutExerciseId = workoutExerciseId,
        setNumber = setNumber,
        weightKg = weightKg,
        reps = reps,
        isCompleted = isCompleted
    )
}
