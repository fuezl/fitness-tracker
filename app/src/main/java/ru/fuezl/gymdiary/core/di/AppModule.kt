package ru.fuezl.gymdiary.core.di

import android.content.Context
import androidx.room.Room
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import ru.fuezl.gymdiary.core.database.GymDiaryDatabase
import ru.fuezl.gymdiary.core.datastore.SettingsDataStore
import ru.fuezl.gymdiary.core.datastore.SettingsLocalDataSource
import ru.fuezl.gymdiary.data.repository.DefaultExerciseRepository
import ru.fuezl.gymdiary.data.repository.DefaultProgressRepository
import ru.fuezl.gymdiary.data.repository.DefaultSettingsRepository
import ru.fuezl.gymdiary.data.repository.DefaultWorkoutRepository
import ru.fuezl.gymdiary.data.repository.DefaultWorkoutTemplateRepository
import ru.fuezl.gymdiary.data.repository.ExerciseRepository
import ru.fuezl.gymdiary.data.repository.ProgressRepository
import ru.fuezl.gymdiary.data.repository.SettingsRepository
import ru.fuezl.gymdiary.data.repository.WorkoutRepository
import ru.fuezl.gymdiary.data.repository.WorkoutTemplateRepository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): GymDiaryDatabase =
        Room.databaseBuilder(context, GymDiaryDatabase::class.java, "gym_diary.db")
            .addMigrations(GymDiaryDatabase.MIGRATION_1_2)
            .build()

    @Provides fun provideExerciseDao(database: GymDiaryDatabase) = database.exerciseDao()
    @Provides fun provideWorkoutDao(database: GymDiaryDatabase) = database.workoutDao()
    @Provides fun provideWorkoutTemplateDao(database: GymDiaryDatabase) = database.workoutTemplateDao()
    @Provides fun provideBodyWeightDao(database: GymDiaryDatabase) = database.bodyWeightDao()
    @Provides fun provideExerciseGoalDao(database: GymDiaryDatabase) = database.exerciseGoalDao()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds abstract fun bindSettingsLocalDataSource(impl: SettingsDataStore): SettingsLocalDataSource
    @Binds abstract fun bindExerciseRepository(impl: DefaultExerciseRepository): ExerciseRepository
    @Binds abstract fun bindWorkoutRepository(impl: DefaultWorkoutRepository): WorkoutRepository
    @Binds abstract fun bindWorkoutTemplateRepository(impl: DefaultWorkoutTemplateRepository): WorkoutTemplateRepository
    @Binds abstract fun bindProgressRepository(impl: DefaultProgressRepository): ProgressRepository
    @Binds abstract fun bindSettingsRepository(impl: DefaultSettingsRepository): SettingsRepository
}
