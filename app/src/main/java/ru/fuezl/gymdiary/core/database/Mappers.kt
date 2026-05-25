package ru.fuezl.gymdiary.core.database

import ru.fuezl.gymdiary.core.model.Exercise
import ru.fuezl.gymdiary.core.model.WorkoutDetails
import ru.fuezl.gymdiary.core.model.WorkoutExerciseDetails
import ru.fuezl.gymdiary.core.model.WorkoutSetModel
import ru.fuezl.gymdiary.core.model.WorkoutSummary
import ru.fuezl.gymdiary.domain.usecase.TrainingCalculators

fun ExerciseEntity.asModel(): Exercise = Exercise(id, name, muscleGroup, equipment, note, isCustom, createdAt, updatedAt)

fun WorkoutSetEntity.asModel(): WorkoutSetModel = WorkoutSetModel(id, workoutExerciseId, setNumber, weightKg, reps, rpe, isCompleted, note, createdAt)

fun WorkoutSessionWithDetails.asDetails(): WorkoutDetails {
    val sortedExercises = exercises.sortedBy { it.workoutExercise.orderIndex }
    val sets = sortedExercises.flatMap { it.sets }.map { it.asModel() }
    val summary = WorkoutSummary(
        id = session.id,
        title = session.title,
        startedAt = session.startedAt,
        durationSeconds = session.durationSeconds,
        exerciseCount = sortedExercises.size,
        setCount = sets.count { it.isCompleted },
        totalVolume = TrainingCalculators.weightedVolume(sets),
        bodyweightReps = TrainingCalculators.bodyweightReps(sets),
        energyLevel = session.energyLevel,
        sleepQuality = session.sleepQuality
    )
    return WorkoutDetails(
        summary = summary,
        note = session.note,
        painNote = session.painNote,
        exercises = sortedExercises.map {
            WorkoutExerciseDetails(
                workoutExerciseId = it.workoutExercise.id,
                exerciseId = it.exercise.id,
                exerciseName = it.exercise.name,
                note = it.workoutExercise.note,
                sets = it.sets.sortedBy { set -> set.setNumber }.map { set -> set.asModel() }
            )
        }
    )
}
