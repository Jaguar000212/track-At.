package com.jaguar.attendancetracker.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DrawerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Header(drawerState: DrawerState, title: @Composable () -> Unit) {
    val scope = rememberCoroutineScope()
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
//        Icon(Icons.Outlined.Settings, "", Modifier.padding(16.dp)) TODO
    })
}