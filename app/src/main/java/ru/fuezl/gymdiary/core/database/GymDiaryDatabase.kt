package ru.fuezl.gymdiary.core.database

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        ExerciseEntity::class,
        WorkoutSessionEntity::class,
        WorkoutExerciseEntity::class,
        WorkoutSetEntity::class,
        WorkoutTemplateEntity::class,
        WorkoutTemplateExerciseEntity::class,
        BodyWeightEntryEntity::class,
        ExerciseGoalEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class GymDiaryDatabase : RoomDatabase() {
    abstract fun exerciseDao(): ExerciseDao
    abstract fun workoutDao(): WorkoutDao
    abstract fun workoutTemplateDao(): WorkoutTemplateDao
    abstract fun bodyWeightDao(): BodyWeightDao
    abstract fun exerciseGoalDao(): ExerciseGoalDao
}
