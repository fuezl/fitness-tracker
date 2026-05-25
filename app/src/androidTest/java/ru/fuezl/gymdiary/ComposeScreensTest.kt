package ru.fuezl.gymdiary

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test
import ru.fuezl.gymdiary.core.model.Equipment
import ru.fuezl.gymdiary.core.model.Exercise
import ru.fuezl.gymdiary.core.model.MuscleGroup
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
                onEquipmentChange = {},
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
                onDelete = {},
            )
        }

        composeRule.onNodeWithText("Название не может быть пустым").assertIsDisplayed()
    }

    @Test
    fun activeWorkout_displaysAddSetButton() {
        composeRule.setContent {
            ActiveWorkoutScreen(
                state = ActiveWorkoutUiState(workout = sampleWorkout()),
                contentPadding = PaddingValues(),
                onAddExercise = {},
                onAddSet = {},
                onUpdateSet = { _, _, _, _, _ -> },
                onCompleteSet = { _, _ -> },
                onDeleteSet = {},
                onShowFinish = {},
                onHideFinish = {},
                onFinish = {},
                onAddRest = {},
                onSkipRest = {},
                onPauseRest = {},
                onSaveTemplate = {},
            )
        }

        composeRule.onNodeWithText("Добавить подход").assertIsDisplayed()
    }

    private fun sampleExercise(): Exercise =
        Exercise(1, "Жим штанги лёжа", MuscleGroup.CHEST, Equipment.BARBELL, "", false, 0, 0)

    private fun sampleWorkout(): WorkoutDetails =
        WorkoutDetails(
            summary = WorkoutSummary(1, "Тренировка", 0, 0, 1, 0, 0.0, 0),
            note = "",
            exercises = listOf(
                WorkoutExerciseDetails(
                    workoutExerciseId = 1,
                    exerciseId = 1,
                    exerciseName = "Жим штанги лёжа",
                    note = "",
                    sets = listOf(
                        WorkoutSetModel(1, 1, 1, 100.0, 5, null, false, "", 0),
                    ),
                ),
            ),
        )
}
