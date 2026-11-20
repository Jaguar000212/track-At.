package com.jaguar.attendancetracker.ui.components

import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.jaguar.attendancetracker.navigation.Destinations

@Composable
fun BottomBar(navController: NavHostController, modifier: Modifier = Modifier) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val haptic = LocalHapticFeedback.current

    NavigationBar(modifier = modifier) {
        Destinations.entries.filter { it.showInDrawer }.forEach { destination ->
            val selected = currentRoute == destination.route
            NavigationBarItem(
                selected = selected,
                onClick = {
                    if (!selected) {
                        navController.navigate(destination.route) {
                            popUpTo(Destinations.TODAY.route) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                        haptic.performHapticFeedback(HapticFeedbackType.ToggleOn)
                    }
                },
                icon = {
                    when (val icon = destination.icon) {
                        is Int -> Icon(painterResource(icon), contentDescription = null)
                        is ImageVector -> Icon(icon, contentDescription = null)
                        else -> Unit
                    }
                },
                label = { Text(stringResource(destination.label)) },
            )
        }
    }
}
