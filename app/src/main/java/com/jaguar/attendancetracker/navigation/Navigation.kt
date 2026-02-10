package com.jaguar.attendancetracker.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.jaguar.attendancetracker.ui.dashboard.Dashboard
import com.jaguar.attendancetracker.ui.dayView.DayView
import com.jaguar.attendancetracker.ui.scheduleView.ScheduleView
import com.jaguar.attendancetracker.ui.subject.Subject

@Composable
fun Navigation(navController: NavHostController, modifier: Modifier) {
    NavHost(
        navController = navController,
        startDestination = Destinations.DAYVIEW.route,
        modifier = modifier
    ) {
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
    }
}
