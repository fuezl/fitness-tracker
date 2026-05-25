package ru.fuezl.gymdiary

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import ru.fuezl.gymdiary.feature.dashboard.DashboardRoute
import ru.fuezl.gymdiary.feature.exercises.ExerciseEditRoute
import ru.fuezl.gymdiary.feature.exercises.ExercisesRoute
import ru.fuezl.gymdiary.feature.history.WorkoutDetailsRoute
import ru.fuezl.gymdiary.feature.history.WorkoutHistoryRoute
import ru.fuezl.gymdiary.feature.progress.ProgressRoute
import ru.fuezl.gymdiary.feature.settings.SettingsRoute
import ru.fuezl.gymdiary.feature.workout.ActiveWorkoutRoute
import ru.fuezl.gymdiary.feature.workout.AddExerciseToWorkoutRoute
import ru.fuezl.gymdiary.feature.workout.StartWorkoutRoute

object Routes {
    const val DASHBOARD = "dashboard"
    const val EXERCISES = "exercises"
    const val EXERCISE_EDIT = "exerciseEdit/{exerciseId}"
    const val START_WORKOUT = "startWorkout"
    const val ACTIVE_WORKOUT = "activeWorkout"
    const val ADD_EXERCISE = "addExercise/{workoutId}"
    const val HISTORY = "history"
    const val DETAILS = "details/{workoutId}"
    const val PROGRESS = "progress"
    const val SETTINGS = "settings"
    const val MORE = "more"
}

private data class BottomItem(val route: String, val label: String, val icon: ImageVector)

private val bottomItems = listOf(
    BottomItem(Routes.DASHBOARD, "Главная", Icons.Default.Home),
    BottomItem(Routes.EXERCISES, "Упражнения", Icons.Default.FitnessCenter),
    BottomItem(Routes.START_WORKOUT, "Тренировка", Icons.Default.FitnessCenter),
    BottomItem(Routes.HISTORY, "История", Icons.Default.History),
    BottomItem(Routes.MORE, "Ещё", Icons.Default.MoreHoriz)
)

@Composable
fun GymDiaryApp(navController: NavHostController = rememberNavController()) {
    val backStack by navController.currentBackStackEntryAsState()
    val current = backStack?.destination
    val currentRoute = current?.route
    val showBottom = bottomItems.any { item -> current?.hierarchy?.any { it.route == item.route } == true } ||
        currentRoute in listOf(Routes.PROGRESS, Routes.SETTINGS)
    Scaffold(
        bottomBar = {
            if (showBottom) {
                NavigationBar {
                    bottomItems.forEach { item ->
                        NavigationBarItem(
                            selected = current?.hierarchy?.any { it.route == item.route } == true ||
                                (item.route == Routes.MORE && currentRoute in listOf(Routes.PROGRESS, Routes.SETTINGS)),
                            onClick = {
                                navController.navigate(item.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(item.icon, contentDescription = item.label) },
                            label = { Text(item.label) }
                        )
                    }
                }
            }
        }
    ) { padding ->
        NavHost(navController = navController, startDestination = Routes.DASHBOARD) {
            composable(Routes.DASHBOARD) {
                DashboardRoute(
                    contentPadding = padding,
                    onStartWorkout = { navController.navigate(Routes.START_WORKOUT) },
                    onExercises = { navController.navigate(Routes.EXERCISES) },
                    onHistory = { navController.navigate(Routes.HISTORY) },
                    onProgress = { navController.navigate(Routes.PROGRESS) },
                    onWorkoutDetails = { navController.navigate("details/$it") }
                )
            }
            composable(Routes.EXERCISES) {
                ExercisesRoute(
                    contentPadding = padding,
                    onAdd = { navController.navigate("exerciseEdit/0") },
                    onEdit = { navController.navigate("exerciseEdit/$it") }
                )
            }
            composable(
                Routes.EXERCISE_EDIT,
                arguments = listOf(navArgument("exerciseId") { type = NavType.LongType })
            ) {
                ExerciseEditRoute(onBack = { navController.popBackStack() })
            }
            composable(Routes.START_WORKOUT) {
                StartWorkoutRoute(
                    contentPadding = padding,
                    onActiveWorkout = { navController.navigate(Routes.ACTIVE_WORKOUT) },
                    onHistory = { navController.navigate(Routes.HISTORY) }
                )
            }
            composable(Routes.ACTIVE_WORKOUT) {
                ActiveWorkoutRoute(
                    contentPadding = padding,
                    onAddExercise = { navController.navigate("addExercise/$it") },
                    onHistory = {
                        navController.navigate(Routes.HISTORY) {
                            popUpTo(Routes.START_WORKOUT) { inclusive = true }
                        }
                    }
                )
            }
            composable(
                Routes.ADD_EXERCISE,
                arguments = listOf(navArgument("workoutId") { type = NavType.LongType })
            ) {
                AddExerciseToWorkoutRoute(onBack = { navController.popBackStack() })
            }
            composable(Routes.HISTORY) {
                WorkoutHistoryRoute(contentPadding = padding, onOpen = { navController.navigate("details/$it") })
            }
            composable(
                Routes.DETAILS,
                arguments = listOf(navArgument("workoutId") { type = NavType.LongType })
            ) {
                WorkoutDetailsRoute(
                    onBack = { navController.popBackStack() },
                    onRepeat = {
                        navController.navigate(Routes.ACTIVE_WORKOUT) {
                            popUpTo(Routes.HISTORY)
                        }
                    }
                )
            }
            composable(Routes.PROGRESS) { ProgressRoute(contentPadding = padding) }
            composable(Routes.SETTINGS) { SettingsRoute(contentPadding = padding) }
            composable(Routes.MORE) {
                MoreRoute(
                    contentPadding = padding,
                    onProgress = { navController.navigate(Routes.PROGRESS) },
                    onSettings = { navController.navigate(Routes.SETTINGS) }
                )
            }
        }
    }
}

@Composable
private fun MoreRoute(contentPadding: PaddingValues, onProgress: () -> Unit, onSettings: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ru.fuezl.gymdiary.core.ui.GymDiaryTopBar("Ещё")
        Button(onClick = onProgress, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.BarChart, contentDescription = null)
            Text("Прогресс")
        }
        Button(onClick = onSettings, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.MoreHoriz, contentDescription = null)
            Text("Настройки")
        }
    }
}
