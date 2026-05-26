package ru.fuezl.gymdiary.core.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable
import ru.fuezl.gymdiary.core.model.Equipment
import ru.fuezl.gymdiary.core.model.MuscleGroup

@Serializable
@Entity(tableName = "exercises")
data class ExerciseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val muscleGroup: MuscleGroup,
    val equipment: Equipment,
    val note: String = "",
    val isCustom: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Serializable
@Entity(tableName = "workout_sessions")
data class WorkoutSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val startedAt: Long,
    val finishedAt: Long? = null,
    val durationSeconds: Long = 0,
    val note: String = "",
    val energyLevel: Int? = null,
    val sleepQuality: Int? = null,
    val painNote: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Serializable
@Entity(
    tableName = "workout_exercises",
    foreignKeys = [
        ForeignKey(
            entity = WorkoutSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["workoutSessionId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = ExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["exerciseId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [Index("workoutSessionId"), Index("exerciseId")]
)
data class WorkoutExerciseEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, val workoutSessionId: Long, val exerciseId: Long, val orderIndex: Int, val note: String = "")

@Serializable
@Entity(
    tableName = "workout_sets",
    foreignKeys = [
        ForeignKey(
            entity = WorkoutExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["workoutExerciseId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("workoutExerciseId")]
)
data class WorkoutSetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val workoutExerciseId: Long,
    val setNumber: Int,
    val weightKg: Double,
    val reps: Int,
    val isCompleted: Boolean = false,
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Serializable
@Entity(tableName = "workout_templates")
data class WorkoutTemplateEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Serializable
@Entity(
    tableName = "workout_template_exercises",
    foreignKeys = [
        ForeignKey(
            entity = WorkoutTemplateEntity::class,
            parentColumns = ["id"],
            childColumns = ["templateId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = ExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["exerciseId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [Index("templateId"), Index("exerciseId")]
)
data class WorkoutTemplateExerciseEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, val templateId: Long, val exerciseId: Long, val orderIndex: Int)

@Serializable
@Entity(tableName = "body_weight_entries")
data class BodyWeightEntryEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, val date: Long, val weightKg: Double, val note: String = "")

@Serializable
@Entity(
    tableName = "exercise_goals",
    foreignKeys = [
        ForeignKey(
            entity = ExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["exerciseId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["exerciseId"], unique = true)]
)
data class ExerciseGoalEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val exerciseId: Long,
    val targetWeightKg: Double,
    val targetReps: Int,
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
