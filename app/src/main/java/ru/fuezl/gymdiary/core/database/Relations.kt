package ru.fuezl.gymdiary.core.database

import androidx.room.Embedded
import androidx.room.Relation

data class WorkoutExerciseWithSets(
    @Embedded val workoutExercise: WorkoutExerciseEntity,
    @Relation(parentColumn = "exerciseId", entityColumn = "id")
    val exercise: ExerciseEntity,
    @Relation(parentColumn = "id", entityColumn = "workoutExerciseId")
    val sets: List<WorkoutSetEntity>,
)

data class WorkoutSessionWithDetails(
    @Embedded val session: WorkoutSessionEntity,
    @Relation(
        entity = WorkoutExerciseEntity::class,
        parentColumn = "id",
        entityColumn = "workoutSessionId",
    )
    val exercises: List<WorkoutExerciseWithSets>,
)
