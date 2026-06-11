package com.jaguar.attendancetracker.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.jaguar.attendancetracker.R
import com.jaguar.attendancetracker.navigation.Destinations

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Header(navController: NavController, title: @Composable () -> Unit) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val haptic = LocalHapticFeedback.current

    val importExportTooltipState = rememberTooltipState()
    LaunchedEffect(importExportTooltipState.isVisible) {
        if (importExportTooltipState.isVisible) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    val helpTooltipState = rememberTooltipState()
    LaunchedEffect(helpTooltipState.isVisible) {
        if (helpTooltipState.isVisible) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    CenterAlignedTopAppBar(
        title = title, actions = {
            if (navBackStackEntry?.destination?.route == Destinations.DASHBOARD.route) TooltipBox(
                positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                tooltip = {
                    PlainTooltip { Text("Import/Export data.") }
                },
                state = importExportTooltipState
            ) {
                IconButton({
                    navController.navigate(Destinations.IM_EXPORT.route) {
                        popUpTo(Destinations.DAYVIEW.route) {
                            inclusive = false
                        }
                    }
                    haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                }) {
                    Icon(painterResource(R.drawable.import_export), "")
                }
            }
            if (navBackStackEntry?.destination?.route != Destinations.HELP.route) {
                TooltipBox(
                    positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                    tooltip = {
                        PlainTooltip { Text("Get to know the app.") }
                    },
                    state = helpTooltipState
                ) {
                    IconButton({
                        navController.navigate(Destinations.HELP.route) {
                            popUpTo(Destinations.DAYVIEW.route) {
                                inclusive = false
                            }
                        }
                        haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                    }) {
                        Icon(Icons.Outlined.Info, "")
                    }
                }
            }
        })
}