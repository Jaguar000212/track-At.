package com.jaguar.attendancetracker.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navArgument
import com.jaguar.attendancetracker.ui.board.BoardScreen
import com.jaguar.attendancetracker.ui.dashboard.Dashboard
import com.jaguar.attendancetracker.ui.dayView.DayView
import com.jaguar.attendancetracker.ui.exams.ExamsScreen
import com.jaguar.attendancetracker.ui.helpPage.HelpPage
import com.jaguar.attendancetracker.ui.imExport.ImExport
import com.jaguar.attendancetracker.ui.imExport.PlannerBackupScreen
import com.jaguar.attendancetracker.ui.leave.LeaveScreen
import com.jaguar.attendancetracker.ui.insights.InsightsScreen
import com.jaguar.attendancetracker.ui.scheduleView.ScheduleView
import com.jaguar.attendancetracker.ui.rollover.RolloverScreen
import com.jaguar.attendancetracker.ui.subject.Subject
import com.jaguar.attendancetracker.ui.today.Today

@Composable
fun Navigation(
    navController: NavHostController,
    modifier: Modifier,
    quickCaptureEntry: NavBackStackEntry? = null,
    onQuickCaptureHandled: (NavBackStackEntry) -> Unit = {},
) {
    // NavHost remembers the graph builder. Its destination must observe updated request
    // state rather than closing over the request value from graph construction.
    val latestCapture = rememberUpdatedState(quickCaptureEntry)
    val latestHandled = rememberUpdatedState(onQuickCaptureHandled)
    NavHost(
        navController = navController,
        startDestination = Destinations.TODAY.route,
        modifier = modifier,
        enterTransition = {
            slideInVertically(
                initialOffsetY = { fullHeight -> fullHeight }, animationSpec = tween(300)
            ) + fadeIn(animationSpec = tween(300))
        },
        exitTransition = {
            slideOutVertically(
                targetOffsetY = { fullHeight -> -fullHeight }, animationSpec = tween(300)
            ) + fadeOut(animationSpec = tween(300))
        },
        popEnterTransition = {
            slideInHorizontally(
                initialOffsetX = { fullWidth -> -fullWidth }, animationSpec = tween(300)
            ) + fadeIn(animationSpec = tween(300))
        },
        popExitTransition = {
            slideOutHorizontally(
                targetOffsetX = { fullWidth -> fullWidth }, animationSpec = tween(300)
            ) + fadeOut(animationSpec = tween(300))
        }) {
        composable(Destinations.TODAY.route) {
            Today(
                openDayView = {
                    // The Today shortcut opens today's date rather than restoring a historical tab.
                    navController.navigate(Destinations.DAYVIEW.route) { launchSingleTop = true }
                },
                openInsights = {
                    navController.navigate(Destinations.INSIGHTS.route) { launchSingleTop = true }
                },
                openLeave = {
                    navController.navigate(Destinations.LEAVE.route) { launchSingleTop = true }
                },
                openExams = {
                    navController.navigate(Destinations.EXAMS.route) { launchSingleTop = true }
                },
                openDashboard = {
                    navController.navigate(Destinations.DASHBOARD.route) { launchSingleTop = true }
                },
            )
        }
        composable(Destinations.INSIGHTS.route) { InsightsScreen() }
        composable(Destinations.LEAVE.route) { LeaveScreen() }
        composable(Destinations.EXAMS.route) { ExamsScreen() }
        composable(Destinations.BOARD.route) { entry ->
            val current = navController.currentBackStackEntryAsState().value
            BoardScreen(
                quickCapture = latestCapture.value == entry && current == entry,
                onQuickCaptureHandled = { latestHandled.value(entry) },
            )
        }
        composable(Destinations.DASHBOARD.route) {
            Dashboard(navController = navController)
        }
        composable(
            "${Destinations.TERM_ROLLOVER.route}/{termId}",
            arguments = listOf(navArgument("termId") { type = NavType.StringType }),
        ) { RolloverScreen(onDone = { navController.popBackStack() }) }
        composable(Destinations.DAYVIEW.route) {
            DayView()
        }
        composable(Destinations.SCHEDULE.route) {
            ScheduleView()
        }
        composable(
            "${Destinations.SUBJECT.route}/{subjectId}", arguments = listOf(
                navArgument("subjectId") { type = NavType.StringType })
        ) {
            Subject()
        }
        composable(Destinations.IM_EXPORT.route) {
            ImExport(openPlannerBackup = { navController.navigate(Destinations.PLANNER_BACKUP.route) {
                launchSingleTop = true
            } })
        }
        composable(Destinations.PLANNER_BACKUP.route) {
            PlannerBackupScreen()
        }
        composable(Destinations.HELP.route) {
            HelpPage()
        }
    }
}
