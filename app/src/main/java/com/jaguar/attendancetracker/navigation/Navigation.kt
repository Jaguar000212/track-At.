package com.jaguar.attendancetracker.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.jaguar.attendancetracker.ui.dashboard.Dashboard
import com.jaguar.attendancetracker.ui.dayView.DayView
import com.jaguar.attendancetracker.ui.imexport.ImExport
import com.jaguar.attendancetracker.ui.scheduleView.ScheduleView
import com.jaguar.attendancetracker.ui.subject.Subject

@Composable
fun Navigation(navController: NavHostController, modifier: Modifier) {
    NavHost(
        navController = navController,
        startDestination = Destinations.DAYVIEW.route,
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
        composable(Destinations.DASHBOARD.route) {
            Dashboard(navController = navController)
        }
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
        composable(Destinations.IMEXPORT.route) {
            ImExport()
        }
    }
}
