package ru.fuezl.gymdiary

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
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
import ru.fuezl.gymdiary.feature.workout.WorkoutExerciseEditorScreen

class ComposeScreensTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun dashboard_displaysStartWorkoutButton() {
        composeRule.setContent {
            DashboardScreen(DashboardUiState(), PaddingValues(), {}, {}, {}, {}, {})
        }

        composeRule.onAllNodesWithText("Начать тренировку").assertCountEquals(2)
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
    fun exercisesScreen_searchAllowsTypingAndDeletingSingleLetter() {
        var latestQuery = ""
        composeRule.setContent {
            var query by remember { mutableStateOf("") }
            ExercisesScreen(
                state = ExercisesUiState(query = query, exercises = listOf(sampleExercise())),
                contentPadding = PaddingValues(),
                onAdd = {},
                onEdit = {},
                onQueryChange = {
                    query = it
                    latestQuery = it
                },
                onMuscleGroupChange = {},
                onEquipmentChange = {}
            )
        }

        composeRule.onNodeWithTag("exercise_search").performTextInput("Ж")
        composeRule.onNodeWithTag("exercise_search").assertTextContains("Ж")
        composeRule.onNodeWithTag("exercise_search").performTextClearance()

        composeRule.onNodeWithTag("exercise_search").assertTextContains("")
        assert(latestQuery.isEmpty())
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
    fun activeWorkout_displaysCompactExerciseCards() {
        composeRule.setContent {
            ActiveWorkoutScreen(
                state = ActiveWorkoutUiState(
                    workout = sampleWorkout(),
                    exerciseHistory = mapOf(1L to listOf(sampleHistoryEntry()))
                ),
                contentPadding = PaddingValues(),
                onAddExercise = {},
                onUpdateWorkoutNote = { _, _ -> },
                onOpenExercise = {},
                onShowFinish = {},
                onHideFinish = {},
                onFinish = {},
                onAddRest = {},
                onSkipRest = {},
                onPauseRest = {},
                onSaveTemplate = {}
            )
        }

        composeRule.onNodeWithText("Время").assertIsDisplayed()
        composeRule.onNodeWithText("Жим штанги лёжа").assertIsDisplayed()
        composeRule.onNodeWithText("Открыть подходы и план").assertIsDisplayed()
        composeRule.onAllNodesWithText("План вес").assertCountEquals(0)
    }

    @Test
    fun activeWorkout_invokesMainActionsAndDoesNotShowEnergyOrSleepInputs() {
        var addExerciseWorkoutId = 0L
        var openedExerciseId = 0L
        var saveTemplateClicks = 0
        var finishDialogClicks = 0
        composeRule.setContent {
            ActiveWorkoutScreen(
                state = ActiveWorkoutUiState(workout = sampleWorkout()),
                contentPadding = PaddingValues(),
                onAddExercise = { addExerciseWorkoutId = it },
                onUpdateWorkoutNote = { _, _ -> },
                onOpenExercise = { openedExerciseId = it },
                onShowFinish = { finishDialogClicks++ },
                onHideFinish = {},
                onFinish = {},
                onAddRest = {},
                onSkipRest = {},
                onPauseRest = {},
                onSaveTemplate = { saveTemplateClicks++ }
            )
        }

        composeRule.onAllNodesWithText("Энергия", substring = true).assertCountEquals(0)
        composeRule.onAllNodesWithText("Сон", substring = true).assertCountEquals(0)
        composeRule.onNodeWithTag("add_exercise").performClick()
        composeRule.onNodeWithText("Открыть подходы и план").performClick()
        composeRule.onNodeWithTag("active_workout_list").performScrollToIndex(3)
        composeRule.onNodeWithTag("save_template").performClick()
        composeRule.onNodeWithTag("show_finish").performClick()

        assert(addExerciseWorkoutId == 1L)
        assert(openedExerciseId == 1L)
        assert(saveTemplateClicks == 1)
        assert(finishDialogClicks == 1)
    }

    @Test
    fun activeWorkout_restTimerControlsInvokeCallbacks() {
        var addRestClicks = 0
        var pauseRestClicks = 0
        var skipRestClicks = 0
        composeRule.setContent {
            ActiveWorkoutScreen(
                state = ActiveWorkoutUiState(
                    workout = sampleWorkout(),
                    restTimerState = ru.fuezl.gymdiary.feature.workout.RestTimerState(remainingSeconds = 30)
                ),
                contentPadding = PaddingValues(),
                onAddExercise = {},
                onUpdateWorkoutNote = { _, _ -> },
                onOpenExercise = {},
                onShowFinish = {},
                onHideFinish = {},
                onFinish = {},
                onAddRest = { addRestClicks++ },
                onSkipRest = { skipRestClicks++ },
                onPauseRest = { pauseRestClicks++ },
                onSaveTemplate = {}
            )
        }

        composeRule.onNodeWithText("+30 сек").performClick()
        composeRule.onNodeWithText("Пауза").performClick()
        composeRule.onNodeWithText("Пропустить").performClick()

        assert(addRestClicks == 1)
        assert(pauseRestClicks == 1)
        assert(skipRestClicks == 1)
    }

    @Test
    fun workoutExerciseEditor_displaysPlanningAndRestControls() {
        composeRule.setContent {
            WorkoutExerciseEditorScreen(
                state = ActiveWorkoutUiState(
                    workout = sampleWorkout(),
                    exerciseHistory = mapOf(1L to listOf(sampleHistoryEntry()))
                ),
                workoutExerciseId = 1,
                onBack = {},
                onAddSet = {},
                onUpdateSet = { _, _, _, _, _, _ -> },
                onCompleteSet = { _, _ -> },
                onDeleteSet = {},
                onUpdateExerciseNote = { _, _ -> },
                onUpdateExerciseRest = { _, _ -> }
            )
        }

        composeRule.onNodeWithText("Добавить подход").assertIsDisplayed()
        composeRule.onNodeWithText("+2.5").assertIsDisplayed()
        composeRule.onNodeWithText("План вес").assertIsDisplayed()
        composeRule.onNodeWithText("Факт вес").assertIsDisplayed()
        composeRule.onNodeWithText("План повт.").assertIsDisplayed()
        composeRule.onNodeWithText("Факт повт.").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Удалить подход").assertIsDisplayed()
        composeRule.onNodeWithText("Заметка к упражнению").assertIsDisplayed()
        composeRule.onNodeWithText("Отдых между подходами, сек").assertIsDisplayed()
    }

    @Test
    fun workoutExerciseEditor_updatesPlanFactNotesRestAndSetActions() {
        var addSetWorkoutExerciseId = 0L
        var completedSet: WorkoutSetModel? = null
        var deletedSetId = 0L
        var updatedRest = ""
        var updatedExerciseNote = ""
        var latestSetUpdate: List<String> = emptyList()
        composeRule.setContent {
            WorkoutExerciseEditorScreen(
                state = ActiveWorkoutUiState(workout = sampleWorkout()),
                workoutExerciseId = 1,
                onBack = {},
                onAddSet = { addSetWorkoutExerciseId = it },
                onUpdateSet = { _, plannedWeight, plannedReps, actualWeight, actualReps, note ->
                    latestSetUpdate = listOf(plannedWeight, plannedReps, actualWeight, actualReps, note)
                },
                onCompleteSet = { set, _ -> completedSet = set },
                onDeleteSet = { deletedSetId = it },
                onUpdateExerciseNote = { _, note -> updatedExerciseNote = note },
                onUpdateExerciseRest = { _, rest -> updatedRest = rest }
            )
        }

        composeRule.onNodeWithTag("exercise_rest").performTextInput("180")
        composeRule.onNodeWithTag("exercise_note").performTextInput("скамья 3")
        composeRule.onNodeWithTag("planned_weight").performTextClearance()
        composeRule.onNodeWithTag("planned_weight").performTextInput("100")
        composeRule.onNodeWithTag("planned_reps").performTextInput("5")
        composeRule.onNodeWithTag("actual_weight").performTextClearance()
        composeRule.onNodeWithTag("actual_weight").performTextInput("97.5")
        composeRule.onNodeWithTag("actual_reps").performTextClearance()
        composeRule.onNodeWithTag("actual_reps").performTextInput("4")
        composeRule.onNodeWithTag("set_note").performTextInput("рабочий")
        composeRule.onNodeWithText("Добавить подход").performClick()
        composeRule.onNodeWithContentDescription("Удалить подход").performClick()

        assert(updatedRest == "180")
        assert(updatedExerciseNote == "скамья 3")
        assert(latestSetUpdate == listOf("100", "5", "97.5", "4", "рабочий"))
        assert(addSetWorkoutExerciseId == 1L)
        assert(deletedSetId == 1L)
        assert(completedSet == null)
    }

    @Test
    fun workoutExerciseEditor_displaysMissingExerciseState() {
        composeRule.setContent {
            WorkoutExerciseEditorScreen(
                state = ActiveWorkoutUiState(workout = sampleWorkout()),
                workoutExerciseId = 999,
                onBack = {},
                onAddSet = {},
                onUpdateSet = { _, _, _, _, _, _ -> },
                onCompleteSet = { _, _ -> },
                onDeleteSet = {},
                onUpdateExerciseNote = { _, _ -> },
                onUpdateExerciseRest = { _, _ -> }
            )
        }

        composeRule.onNodeWithText("Упражнение не найдено").assertIsDisplayed()
    }

    @Test
    fun activeWorkout_displaysEmptyStateWhenNoWorkout() {
        composeRule.setContent {
            ActiveWorkoutScreen(
                state = ActiveWorkoutUiState(workout = null),
                contentPadding = PaddingValues(),
                onAddExercise = {},
                onUpdateWorkoutNote = { _, _ -> },
                onOpenExercise = {},
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
                onUpdateWorkoutNote = { _, _ -> },
                onOpenExercise = {},
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
    fun activeWorkout_finishDialogWarnsAboutIncompleteExercise() {
        composeRule.setContent {
            ActiveWorkoutScreen(
                state = ActiveWorkoutUiState(workout = sampleWorkout(), isFinishDialogVisible = true),
                contentPadding = PaddingValues(),
                onAddExercise = {},
                onUpdateWorkoutNote = { _, _ -> },
                onOpenExercise = {},
                onShowFinish = {},
                onHideFinish = {},
                onFinish = {},
                onAddRest = {},
                onSkipRest = {},
                onPauseRest = {},
                onSaveTemplate = {}
            )
        }

        composeRule.onNodeWithText("Есть упражнения без выполненных подходов. Завершить тренировку?").assertIsDisplayed()
    }

    @Test
    fun activeWorkout_finishDialogConfirmsCompleteWorkout() {
        composeRule.setContent {
            ActiveWorkoutScreen(
                state = ActiveWorkoutUiState(workout = sampleWorkout(exercises = listOf(sampleWorkoutExercise(completed = true))), isFinishDialogVisible = true),
                contentPadding = PaddingValues(),
                onAddExercise = {},
                onUpdateWorkoutNote = { _, _ -> },
                onOpenExercise = {},
                onShowFinish = {},
                onHideFinish = {},
                onFinish = {},
                onAddRest = {},
                onSkipRest = {},
                onPauseRest = {},
                onSaveTemplate = {}
            )
        }

        composeRule.onNodeWithText("Тренировка будет сохранена в историю.").assertIsDisplayed()
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
                onUpdateWorkoutNote = { _, _ -> },
                onOpenExercise = {},
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
        composeRule.onNodeWithTag("progress_list").performScrollToIndex(7)
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
        composeRule.onNodeWithTag("progress_list").performScrollToIndex(7)
        composeRule.onNodeWithText("Нет выполненных подходов по выбранному упражнению").assertIsDisplayed()
        composeRule.onNodeWithTag("progress_list").performScrollToIndex(9)
        composeRule.onNodeWithText("Нет данных для прогресса").assertIsDisplayed()
        composeRule.onNodeWithTag("progress_list").performScrollToIndex(12)
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

    private fun sampleWorkoutExercise(completed: Boolean = false): WorkoutExerciseDetails = WorkoutExerciseDetails(
        workoutExerciseId = 1,
        exerciseId = 1,
        exerciseName = "Жим штанги лёжа",
        note = "",
        sets = listOf(
            WorkoutSetModel(1, 1, 1, 100.0, 5, completed, "", 0)
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
