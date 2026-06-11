package com.jaguar.attendancetracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.compose.rememberNavController
import com.jaguar.attendancetracker.navigation.Destinations
import com.jaguar.attendancetracker.navigation.Navigation
import com.jaguar.attendancetracker.ui.components.BottomBar
import com.jaguar.attendancetracker.ui.components.Header
import com.jaguar.attendancetracker.ui.theme.AppTypography
import com.jaguar.attendancetracker.ui.theme.UITheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            App()
        }
    }

    @Composable
    fun App() {
        val navController = rememberNavController()

        UITheme {
            Scaffold(modifier = Modifier.fillMaxSize(), topBar = {
                Header(navController = navController) {
                    Text(
                        stringResource(R.string.app_name), style = AppTypography.headlineMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }, bottomBar = {
                BottomBar(navController)
            }) { innerPadding ->
                Navigation(navController, Modifier.padding(innerPadding))
            }
        }
        LaunchedEffect(Unit) {
            val subId = intent.extras?.getString("subjectId")
            if (subId != null) {
                navController.navigate("${Destinations.SUBJECT.route}/$subId") {
                    popUpTo(Destinations.DAYVIEW.route) {
                        inclusive = false
                    }
                }
                intent.removeExtra("subjectId")
            }
        }
    }
}