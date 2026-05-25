package ru.fuezl.gymdiary.domain.usecase

import ru.fuezl.gymdiary.core.model.WorkoutSetModel

object TrainingCalculators {
    fun estimatedOneRm(weightKg: Double, reps: Int): Double? {
        if (weightKg <= 0.0 || reps <= 0) return null
        return weightKg * (1 + reps / 30.0)
    }

    fun weightedVolume(sets: List<WorkoutSetModel>): Double = sets.filter { it.isCompleted && it.weightKg > 0.0 && it.reps > 0 }
        .sumOf { it.weightKg * it.reps }

    fun bodyweightReps(sets: List<WorkoutSetModel>): Int = sets.filter { it.isCompleted && it.weightKg == 0.0 && it.reps > 0 }.sumOf { it.reps }
}

object ExerciseValidator {
    fun validateName(name: String): String? = when {
        name.isBlank() -> "Название не может быть пустым"
        name.trim().length < 2 -> "Название должно быть не короче 2 символов"
        else -> null
    }
}

object SetValidator {
    fun validate(weightKg: Double, reps: Int, rpe: Double?): String? = when {
        weightKg < 0.0 -> "Вес не может быть отрицательным"
        reps < 0 -> "Количество повторений не может быть отрицательным"
        rpe != null && (rpe < 1.0 || rpe > 10.0) -> "RPE должен быть от 1 до 10"
        else -> null
    }
}
