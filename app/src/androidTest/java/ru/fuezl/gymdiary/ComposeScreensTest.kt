package ru.fuezl.gymdiary

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test
import ru.fuezl.gymdiary.core.model.Equipment
import ru.fuezl.gymdiary.core.model.Exercise
import ru.fuezl.gymdiary.core.model.ExerciseAnalytics
import ru.fuezl.gymdiary.core.model.ExerciseGoal
import ru.fuezl.gymdiary.core.model.ExerciseHistoryEntry
import ru.fuezl.gymdiary.core.model.ExerciseProgressPoint
import ru.fuezl.gymdiary.core.model.MuscleGroup
import ru.fuezl.gymdiary.core.model.WeeklyStats
import ru.fuezl.gymdiary.core.model.WorkoutDetails
import ru.fuezl.gymdiary.core.model.WorkoutExerciseDetails
import ru.fuezl.gymdiary.core.model.WorkoutSetModel
import ru.fuezl.gymdiary.core.model.WorkoutSummary
import ru.fuezl.gymdiary.feature.dashboard.DashboardScreen
import ru.fuezl.gymdiary.feature.dashboard.DashboardUiState
import ru.fuezl.gymdiary.feature.exercises.ExerciseEditScreen
import ru.fuezl.gymdiary.feature.exercises.ExerciseEditUiState
import ru.fuezl.gymdiary.feature.exercises.ExercisesScreen
import ru.fuezl.gymdiary.feature.exercises.ExercisesUiState
import ru.fuezl.gymdiary.feature.progress.ProgressScreen
import ru.fuezl.gymdiary.feature.progress.ProgressUiState
import ru.fuezl.gymdiary.feature.workout.ActiveWorkoutScreen
import ru.fuezl.gymdiary.feature.workout.ActiveWorkoutUiState

class ComposeScreensTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun dashboard_displaysStartWorkoutButton() {
        composeRule.setContent {
            DashboardScreen(DashboardUiState(), PaddingValues(), {}, {}, {}, {}, {})
        }

        composeRule.onNodeWithText("Начать тренировку").assertIsDisplayed()
    }

    @Test
    fun exercisesScreen_displaysExerciseList() {
        composeRule.setContent {
            ExercisesScreen(
                state = ExercisesUiState(exercises = listOf(sampleExercise())),
                contentPadding = PaddingValues(),
                onAdd = {},
                onEdit = {},
                onQueryChange = {},
                onMuscleGroupChange = {},
                onEquipmentChange = {}
            )
        }

        composeRule.onNodeWithText("Жим штанги лёжа").assertIsDisplayed()
    }

    @Test
    fun exerciseEdit_displaysEmptyNameError() {
        composeRule.setContent {
            ExerciseEditScreen(
                state = ExerciseEditUiState(nameError = "Название не может быть пустым"),
                onBack = {},
                onNameChange = {},
                onMuscleGroupChange = {},
                onEquipmentChange = {},
                onNoteChange = {},
                onSave = {},
                onDelete = {}
            )
        }

        composeRule.onNodeWithText("Название не может быть пустым").assertIsDisplayed()
    }

    @Test
    fun activeWorkout_displaysAddSetButton() {
        composeRule.setContent {
            ActiveWorkoutScreen(
                state = ActiveWorkoutUiState(
                    workout = sampleWorkout(),
                    exerciseHistory = mapOf(1L to listOf(sampleHistoryEntry()))
                ),
                contentPadding = PaddingValues(),
                onAddExercise = {},
                onAddSet = {},
                onUpdateSet = { _, _, _, _, _ -> },
                onCompleteSet = { _, _ -> },
                onDeleteSet = {},
                onUpdateWorkoutNote = { _, _, _, _ -> },
                onUpdateExerciseNote = { _, _ -> },
                onShowFinish = {},
                onHideFinish = {},
                onFinish = {},
                onAddRest = {},
                onSkipRest = {},
                onPauseRest = {},
                onSaveTemplate = {}
            )
        }

        composeRule.onNodeWithText("Добавить подход").assertIsDisplayed()
        composeRule.onNodeWithText("+2.5").assertIsDisplayed()
        composeRule.onNodeWithText("Заметка к упражнению").assertIsDisplayed()
        composeRule.onNodeWithText("Энергия 1-5").assertIsDisplayed()
    }

    @Test
    fun progressScreen_displaysGoalHistoryAndPlateau() {
        composeRule.setContent {
            ProgressScreen(
                state = ProgressUiState(
                    weeklyStats = WeeklyStats(workouts = 2, sets = 6, volume = 1200.0),
                    exercises = listOf(sampleExercise()),
                    selectedExerciseId = 1,
                    selectedExerciseProgress = listOf(ExerciseProgressPoint(1, 100.0, 500.0, 116.6)),
                    selectedExerciseAnalytics = ExerciseAnalytics(
                        history = listOf(sampleHistoryEntry()),
                        plateauMessage = "Похоже на плато",
                        goal = ExerciseGoal(1, 1, 120.0, 5, "цель")
                    )
                ),
                contentPadding = PaddingValues(),
                onSelectExercise = {},
                onAddBodyWeight = { _, _ -> },
                onDeleteBodyWeight = {},
                onSaveGoal = { _, _, _ -> },
                onDeleteGoal = {}
            )
        }

        composeRule.onNodeWithText("Цель упражнения").assertIsDisplayed()
        composeRule.onNodeWithText("История упражнения").assertIsDisplayed()
        composeRule.onNodeWithText("Плато").assertIsDisplayed()
    }

    private fun sampleExercise(): Exercise = Exercise(1, "Жим штанги лёжа", MuscleGroup.CHEST, Equipment.BARBELL, "", false, 0, 0)

    private fun sampleWorkout(): WorkoutDetails = WorkoutDetails(
        summary = WorkoutSummary(1, "Тренировка", 0, 0, 1, 0, 0.0, 0),
        note = "",
        painNote = "",
        exercises = listOf(
            WorkoutExerciseDetails(
                workoutExerciseId = 1,
                exerciseId = 1,
                exerciseName = "Жим штанги лёжа",
                note = "",
                sets = listOf(
                    WorkoutSetModel(1, 1, 1, 100.0, 5, null, false, "", 0)
                )
            )
        )
    )

    private fun sampleHistoryEntry(): ExerciseHistoryEntry = ExerciseHistoryEntry(
        workoutId = 1,
        date = 1,
        sets = listOf(WorkoutSetModel(1, 1, 1, 100.0, 5, 8.0, true, "", 0)),
        volume = 500.0,
        maxWeight = 100.0,
        bestEstimatedOneRm = 116.6
    )
}
