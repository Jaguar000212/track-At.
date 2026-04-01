package com.jaguar.attendancetracker.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DrawerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.res.painterResource
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.jaguar.attendancetracker.R
import com.jaguar.attendancetracker.navigation.Destinations
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Header(drawerState: DrawerState, navController: NavController, title: @Composable () -> Unit) {
    val scope = rememberCoroutineScope()
    val navBackStackEntry by navController.currentBackStackEntryAsState()

    CenterAlignedTopAppBar(title = title, navigationIcon = {
        IconButton({
            scope.launch {
                if (drawerState.isOpen) drawerState.close()
                else drawerState.open()
            }
        }) {
            Icon(Icons.Outlined.Menu, "")
        }
    }, actions = {
        if (navBackStackEntry?.destination?.route == Destinations.DASHBOARD.route)
            TooltipBox(
                positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                tooltip = { PlainTooltip { Text("Import/Export data.") } },
                state = rememberTooltipState()
            ) {
                IconButton({
                    navController.navigate(Destinations.IMEXPORT.route) {
                        launchSingleTop = true
                    }
                }) {
                    Icon(painterResource(R.drawable.import_export), "")
                }
            }
        if (navBackStackEntry?.destination?.route != Destinations.HELP.route) {
            TooltipBox(
                positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                tooltip = { PlainTooltip { Text("Get to know the app.") } },
                state = rememberTooltipState()
            ) {
                IconButton({
                    navController.navigate(Destinations.HELP.route) {
                        popUpTo(Destinations.DAYVIEW.route) {
                            inclusive = false
                        }
                    }
                }) {
                    Icon(Icons.Outlined.Info, "")
                }
            }
        }
    })
}