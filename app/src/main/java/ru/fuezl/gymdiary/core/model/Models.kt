package ru.fuezl.gymdiary.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class MuscleGroup(val title: String) {
    CHEST("Грудь"),
    BACK("Спина"),
    LEGS("Ноги"),
    SHOULDERS("Плечи"),
    BICEPS("Бицепс"),
    TRICEPS("Трицепс"),
    ABS("Пресс"),
    FULL_BODY("Всё тело"),
    CARDIO("Кардио"),
    OTHER("Другое"),
}

@Serializable
enum class Equipment(val title: String) {
    BARBELL("Штанга"),
    DUMBBELL("Гантели"),
    MACHINE("Тренажёр"),
    CABLE("Блок"),
    BODYWEIGHT("Собственный вес"),
    KETTLEBELL("Гиря"),
    CARDIO_MACHINE("Кардио-тренажёр"),
    OTHER("Другое"),
}

@Serializable
enum class ThemeMode(val title: String) {
    SYSTEM("Системная"),
    LIGHT("Светлая"),
    DARK("Тёмная"),
}

@Serializable
enum class WeightUnit(val title: String) {
    KG("кг"),
}

@Serializable
data class Exercise(
    val id: Long,
    val name: String,
    val muscleGroup: MuscleGroup,
    val equipment: Equipment,
    val note: String,
    val isCustom: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
)

@Serializable
data class WorkoutSummary(
    val id: Long,
    val title: String,
    val startedAt: Long,
    val durationSeconds: Long,
    val exerciseCount: Int,
    val setCount: Int,
    val totalVolume: Double,
    val bodyweightReps: Int,
)

@Serializable
data class WorkoutDetails(
    val summary: WorkoutSummary,
    val note: String,
    val exercises: List<WorkoutExerciseDetails>,
)

@Serializable
data class WorkoutExerciseDetails(
    val workoutExerciseId: Long,
    val exerciseId: Long,
    val exerciseName: String,
    val note: String,
    val sets: List<WorkoutSetModel>,
)

@Serializable
data class WorkoutSetModel(
    val id: Long,
    val workoutExerciseId: Long,
    val setNumber: Int,
    val weightKg: Double,
    val reps: Int,
    val rpe: Double?,
    val isCompleted: Boolean,
    val note: String,
    val createdAt: Long,
)

@Serializable
data class UserSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val defaultRestTimerSeconds: Int = 90,
    val restTimerEnabled: Boolean = true,
    val weightUnit: WeightUnit = WeightUnit.KG,
)

data class WeeklyStats(
    val workouts: Int = 0,
    val sets: Int = 0,
    val volume: Double = 0.0,
)

data class PersonalRecord(
    val exerciseId: Long,
    val exerciseName: String,
    val maxWeight: Double,
    val bestRepsAtWeight: Int,
    val bestWorkoutVolume: Double,
    val bestEstimatedOneRm: Double,
)

data class ExerciseProgressPoint(
    val date: Long,
    val maxWeight: Double,
    val volume: Double,
)
