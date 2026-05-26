package ru.fuezl.gymdiary

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
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
    fun exercisesScreen_displaysEmptyStateWhenListIsEmpty() {
        composeRule.setContent {
            ExercisesScreen(
                state = ExercisesUiState(exercises = emptyList()),
                contentPadding = PaddingValues(),
                onAdd = {},
                onEdit = {},
                onQueryChange = {},
                onMuscleGroupChange = {},
                onEquipmentChange = {}
            )
        }

        composeRule.onNodeWithText("Нет упражнений").assertIsDisplayed()
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
                onUpdateSet = { _, _, _, _ -> },
                onCompleteSet = { _, _ -> },
                onDeleteSet = {},
                onUpdateWorkoutNote = { _, _ -> },
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
        composeRule.onNodeWithText("Время").assertIsDisplayed()
        composeRule.onNodeWithText("Вес").assertIsDisplayed()
        composeRule.onNodeWithText("Повт.").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Удалить подход").assertIsDisplayed()
        composeRule.onNodeWithText("Заметка к упражнению").assertIsDisplayed()
    }

    @Test
    fun activeWorkout_displaysEmptyStateWhenNoWorkout() {
        composeRule.setContent {
            ActiveWorkoutScreen(
                state = ActiveWorkoutUiState(workout = null),
                contentPadding = PaddingValues(),
                onAddExercise = {},
                onAddSet = {},
                onUpdateSet = { _, _, _, _ -> },
                onCompleteSet = { _, _ -> },
                onDeleteSet = {},
                onUpdateWorkoutNote = { _, _ -> },
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

        composeRule.onNodeWithText("Активной тренировки нет").assertIsDisplayed()
        composeRule.onAllNodesWithText("Добавить упражнение").assertCountEquals(0)
    }

    @Test
    fun activeWorkout_displaysFinishConfirmationForEmptyWorkout() {
        composeRule.setContent {
            ActiveWorkoutScreen(
                state = ActiveWorkoutUiState(workout = sampleWorkout(exercises = emptyList()), isFinishDialogVisible = true),
                contentPadding = PaddingValues(),
                onAddExercise = {},
                onAddSet = {},
                onUpdateSet = { _, _, _, _ -> },
                onCompleteSet = { _, _ -> },
                onDeleteSet = {},
                onUpdateWorkoutNote = { _, _ -> },
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

        composeRule.onNodeWithText("Завершить тренировку?").assertIsDisplayed()
        composeRule.onNodeWithText("В тренировке нет упражнений. Завершить без сохранения полезных данных?").assertIsDisplayed()
    }

    @Test
    fun activeWorkout_displaysTemplateErrorMessage() {
        composeRule.setContent {
            ActiveWorkoutScreen(
                state = ActiveWorkoutUiState(
                    workout = sampleWorkout(exercises = emptyList()),
                    errorMessage = "Добавьте упражнения перед сохранением шаблона"
                ),
                contentPadding = PaddingValues(),
                onAddExercise = {},
                onAddSet = {},
                onUpdateSet = { _, _, _, _ -> },
                onCompleteSet = { _, _ -> },
                onDeleteSet = {},
                onUpdateWorkoutNote = { _, _ -> },
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

        composeRule.onNodeWithText("Добавьте упражнения перед сохранением шаблона").assertIsDisplayed()
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

    @Test
    fun progressScreen_displaysEmptyStatesWhenNoExerciseProgressOrRecords() {
        composeRule.setContent {
            ProgressScreen(
                state = ProgressUiState(
                    exercises = listOf(sampleExercise()),
                    selectedExerciseId = 1,
                    selectedExerciseProgress = emptyList(),
                    selectedExerciseAnalytics = ExerciseAnalytics(history = emptyList()),
                    personalRecords = emptyList(),
                    bodyWeight = emptyList()
                ),
                contentPadding = PaddingValues(),
                onSelectExercise = {},
                onAddBodyWeight = { _, _ -> },
                onDeleteBodyWeight = {},
                onSaveGoal = { _, _, _ -> },
                onDeleteGoal = {}
            )
        }

        composeRule.onAllNodesWithText("Нет данных для графика").assertCountEquals(3)
        composeRule.onNodeWithText("Нет выполненных подходов по выбранному упражнению").assertIsDisplayed()
        composeRule.onNodeWithText("Нет данных для прогресса").assertIsDisplayed()
        composeRule.onNodeWithText("Нет записей массы тела").assertIsDisplayed()
    }

    @Test
    fun progressScreen_doesNotShowRpeText() {
        composeRule.setContent {
            ProgressScreen(
                state = ProgressUiState(
                    exercises = listOf(sampleExercise()),
                    selectedExerciseId = 1,
                    selectedExerciseProgress = listOf(ExerciseProgressPoint(1, 100.0, 500.0, 116.6)),
                    selectedExerciseAnalytics = ExerciseAnalytics(
                        history = listOf(sampleHistoryEntry()),
                        plateauMessage = "Похоже на плато"
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

        composeRule.onAllNodesWithText("RPE", substring = true).assertCountEquals(0)
    }

    private fun sampleExercise(): Exercise = Exercise(1, "Жим штанги лёжа", MuscleGroup.CHEST, Equipment.BARBELL, "", false, 0, 0)

    private fun sampleWorkout(exercises: List<WorkoutExerciseDetails> = listOf(sampleWorkoutExercise())): WorkoutDetails = WorkoutDetails(
        summary = WorkoutSummary(1, "Тренировка", 0, 0, 1, 0, 0.0, 0),
        note = "",
        painNote = "",
        exercises = exercises
    )

    private fun sampleWorkoutExercise(): WorkoutExerciseDetails = WorkoutExerciseDetails(
        workoutExerciseId = 1,
        exerciseId = 1,
        exerciseName = "Жим штанги лёжа",
        note = "",
        sets = listOf(
            WorkoutSetModel(1, 1, 1, 100.0, 5, false, "", 0)
        )
    )

    private fun sampleHistoryEntry(): ExerciseHistoryEntry = ExerciseHistoryEntry(
        workoutId = 1,
        date = 1,
        sets = listOf(WorkoutSetModel(1, 1, 1, 100.0, 5, true, "", 0)),
        volume = 500.0,
        maxWeight = 100.0,
        bestEstimatedOneRm = 116.6
    )
}
