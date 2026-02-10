package com.jaguar.attendancetracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.compose.rememberNavController
import com.jaguar.attendancetracker.navigation.Navigation
import com.jaguar.attendancetracker.ui.components.Drawer
import com.jaguar.attendancetracker.ui.components.Header
import com.jaguar.attendancetracker.ui.theme.UITheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            App()
        }
    }

    @Composable
    fun App() {
        val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
        val navController = rememberNavController()

        UITheme {
            Drawer(drawerState, navController) {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    topBar = { Header(drawerState = drawerState) { Text(stringResource(R.string.app_name)) } }) { innerPadding ->
                    Navigation(navController, Modifier.padding(innerPadding))
                }
            }
        }
    }
}