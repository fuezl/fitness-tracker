package ru.fuezl.gymdiary.core.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ExerciseDao {
    @Query("SELECT * FROM exercises ORDER BY isCustom DESC, name COLLATE NOCASE ASC")
    fun observeExercises(): Flow<List<ExerciseEntity>>

    @Query("SELECT * FROM exercises ORDER BY name COLLATE NOCASE ASC")
    suspend fun getAll(): List<ExerciseEntity>

    @Query(
        """
        SELECT * FROM exercises
        WHERE (:query = '' OR name LIKE '%' || :query || '%')
        AND (:muscleGroup IS NULL OR muscleGroup = :muscleGroup)
        AND (:equipment IS NULL OR equipment = :equipment)
        ORDER BY name COLLATE NOCASE ASC
        """,
    )
    fun searchExercises(query: String, muscleGroup: String?, equipment: String?): Flow<List<ExerciseEntity>>

    @Query("SELECT * FROM exercises WHERE id = :id")
    suspend fun getExercise(id: Long): ExerciseEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(exercise: ExerciseEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(exercises: List<ExerciseEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(exercises: List<ExerciseEntity>)

    @Update
    suspend fun update(exercise: ExerciseEntity)

    @Delete
    suspend fun delete(exercise: ExerciseEntity)

    @Query("SELECT COUNT(*) FROM exercises")
    suspend fun count(): Int
}

@Dao
interface WorkoutDao {
    @Query("SELECT * FROM workout_sessions WHERE finishedAt IS NULL ORDER BY startedAt DESC LIMIT 1")
    fun observeActiveWorkout(): Flow<WorkoutSessionEntity?>

    @Query("SELECT * FROM workout_sessions WHERE finishedAt IS NOT NULL ORDER BY startedAt DESC")
    fun observeWorkoutHistory(): Flow<List<WorkoutSessionEntity>>

    @Query("SELECT * FROM workout_sessions WHERE id = :id")
    suspend fun getSession(id: Long): WorkoutSessionEntity?

    @Query("SELECT * FROM workout_sessions")
    suspend fun getAllSessions(): List<WorkoutSessionEntity>

    @Query("SELECT * FROM workout_exercises")
    suspend fun getAllWorkoutExercises(): List<WorkoutExerciseEntity>

    @Query("SELECT * FROM workout_sets")
    suspend fun getAllSets(): List<WorkoutSetEntity>

    @Transaction
    @Query("SELECT * FROM workout_sessions WHERE id = :id")
    fun observeWorkoutDetails(id: Long): Flow<WorkoutSessionWithDetails?>

    @Transaction
    @Query("SELECT * FROM workout_sessions WHERE id = :id")
    suspend fun getWorkoutDetails(id: Long): WorkoutSessionWithDetails?

    @Transaction
    @Query("SELECT * FROM workout_sessions WHERE finishedAt IS NOT NULL ORDER BY startedAt DESC")
    fun observeFinishedWorkoutDetails(): Flow<List<WorkoutSessionWithDetails>>

    @Insert
    suspend fun insertSession(session: WorkoutSessionEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSessions(sessions: List<WorkoutSessionEntity>)

    @Update
    suspend fun updateSession(session: WorkoutSessionEntity)

    @Query("DELETE FROM workout_sessions WHERE id = :id")
    suspend fun deleteSession(id: Long)

    @Query("SELECT * FROM workout_exercises WHERE workoutSessionId = :sessionId ORDER BY orderIndex ASC")
    suspend fun getWorkoutExercises(sessionId: Long): List<WorkoutExerciseEntity>

    @Insert
    suspend fun insertWorkoutExercise(entity: WorkoutExerciseEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertWorkoutExercises(entities: List<WorkoutExerciseEntity>)

    @Query("SELECT * FROM workout_sets WHERE workoutExerciseId = :workoutExerciseId ORDER BY setNumber ASC")
    suspend fun getSets(workoutExerciseId: Long): List<WorkoutSetEntity>

    @Query("SELECT * FROM workout_sets WHERE id = :id")
    suspend fun getSet(id: Long): WorkoutSetEntity?

    @Insert
    suspend fun insertSet(set: WorkoutSetEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSets(sets: List<WorkoutSetEntity>)

    @Update
    suspend fun updateSet(set: WorkoutSetEntity)

    @Query("DELETE FROM workout_sets WHERE id = :id")
    suspend fun deleteSet(id: Long)
}

@Dao
interface WorkoutTemplateDao {
    @Query("SELECT * FROM workout_templates ORDER BY title ASC")
    fun observeTemplates(): Flow<List<WorkoutTemplateEntity>>

    @Query("SELECT * FROM workout_templates WHERE id = :id")
    suspend fun getTemplate(id: Long): WorkoutTemplateEntity?

    @Query("SELECT * FROM workout_template_exercises WHERE templateId = :templateId ORDER BY orderIndex ASC")
    suspend fun getTemplateExercises(templateId: Long): List<WorkoutTemplateExerciseEntity>

    @Insert
    suspend fun insertTemplate(template: WorkoutTemplateEntity): Long

    @Insert
    suspend fun insertTemplateExercise(exercise: WorkoutTemplateExerciseEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTemplates(templates: List<WorkoutTemplateEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTemplateExercises(exercises: List<WorkoutTemplateExerciseEntity>)

    @Query("SELECT * FROM workout_templates")
    suspend fun getAllTemplates(): List<WorkoutTemplateEntity>

    @Query("SELECT * FROM workout_template_exercises")
    suspend fun getAllTemplateExercises(): List<WorkoutTemplateExerciseEntity>

    @Query("DELETE FROM workout_templates WHERE id = :id")
    suspend fun deleteTemplate(id: Long)
}

@Dao
interface BodyWeightDao {
    @Query("SELECT * FROM body_weight_entries ORDER BY date DESC")
    fun observeEntries(): Flow<List<BodyWeightEntryEntity>>

    @Insert
    suspend fun insert(entry: BodyWeightEntryEntity): Long

    @Query("SELECT * FROM body_weight_entries")
    suspend fun getAll(): List<BodyWeightEntryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entries: List<BodyWeightEntryEntity>)

    @Query("DELETE FROM body_weight_entries WHERE id = :id")
    suspend fun delete(id: Long)
}
