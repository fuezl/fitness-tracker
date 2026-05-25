package ru.fuezl.gymdiary.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import ru.fuezl.gymdiary.core.model.WorkoutSetModel
import ru.fuezl.gymdiary.core.model.WorkoutSummary

class CalculatorsTest {
    @Test
    fun weightedVolume_usesOnlyCompletedWeightedSets() {
        val sets = listOf(
            set(weightKg = 100.0, reps = 5, isCompleted = true),
            set(weightKg = 80.0, reps = 8, isCompleted = true),
            set(weightKg = 60.0, reps = 10, isCompleted = false),
            set(weightKg = 0.0, reps = 12, isCompleted = true),
        )

        assertEquals(1140.0, TrainingCalculators.weightedVolume(sets), 0.001)
    }

    @Test
    fun weightedVolume_returnsZeroForEmptyIncompleteZeroAndNegativeInputs() {
        assertEquals(0.0, TrainingCalculators.weightedVolume(emptyList()), 0.001)
        assertEquals(
            0.0,
            TrainingCalculators.weightedVolume(
                listOf(
                    set(weightKg = 100.0, reps = 5, isCompleted = false),
                    set(weightKg = 0.0, reps = 10, isCompleted = true),
                    set(weightKg = -10.0, reps = 10, isCompleted = true),
                    set(weightKg = 100.0, reps = 0, isCompleted = true),
                    set(weightKg = 100.0, reps = -1, isCompleted = true),
                ),
            ),
            0.001,
        )
    }

    @Test
    fun estimatedOneRm_usesEpleyFormula() {
        assertEquals(120.0, TrainingCalculators.estimatedOneRm(100.0, 6) ?: 0.0, 0.001)
        assertNull(TrainingCalculators.estimatedOneRm(0.0, 6))
        assertNull(TrainingCalculators.estimatedOneRm(-1.0, 6))
        assertNull(TrainingCalculators.estimatedOneRm(100.0, 0))
        assertNull(TrainingCalculators.estimatedOneRm(100.0, -1))
        assertEquals(103.333, TrainingCalculators.estimatedOneRm(100.0, 1) ?: 0.0, 0.001)
    }

    @Test
    fun bodyweightReps_countsCompletedZeroWeightSets() {
        val sets = listOf(
            set(weightKg = 0.0, reps = 10, isCompleted = true),
            set(weightKg = 0.0, reps = 8, isCompleted = false),
            set(weightKg = 50.0, reps = 10, isCompleted = true),
        )

        assertEquals(10, TrainingCalculators.bodyweightReps(sets))
    }

    @Test
    fun bodyweightReps_ignoresWeightedIncompleteAndInvalidReps() {
        val sets = listOf(
            set(weightKg = 0.0, reps = 0, isCompleted = true),
            set(weightKg = 0.0, reps = -2, isCompleted = true),
            set(weightKg = 0.0, reps = 5, isCompleted = false),
            set(weightKg = 20.0, reps = 5, isCompleted = true),
        )

        assertEquals(0, TrainingCalculators.bodyweightReps(sets))
    }

    @Test
    fun workoutHistory_sortsByStartedAtDescending() {
        val sorted = listOf(summary(1, 1000), summary(2, 3000), summary(3, 2000)).sortedByDescending { it.startedAt }

        assertEquals(listOf(2L, 3L, 1L), sorted.map { it.id })
    }

    private fun set(weightKg: Double, reps: Int, isCompleted: Boolean): WorkoutSetModel =
        WorkoutSetModel(
            id = 1,
            workoutExerciseId = 1,
            setNumber = 1,
            weightKg = weightKg,
            reps = reps,
            rpe = null,
            isCompleted = isCompleted,
            note = "",
            createdAt = 0,
        )

    private fun summary(id: Long, startedAt: Long): WorkoutSummary =
        WorkoutSummary(id, "Тренировка", startedAt, 0, 0, 0, 0.0, 0)
}
