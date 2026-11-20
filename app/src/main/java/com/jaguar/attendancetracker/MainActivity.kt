package com.jaguar.attendancetracker

import android.content.Intent
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
import com.jaguar.attendancetracker.backend.reminders.OPEN_EXAM_REMINDER
import com.jaguar.attendancetracker.navigation.Destinations
import com.jaguar.attendancetracker.navigation.Navigation
import com.jaguar.attendancetracker.ui.components.BottomBar
import com.jaguar.attendancetracker.ui.components.Header
import com.jaguar.attendancetracker.ui.theme.AppTypography
import com.jaguar.attendancetracker.ui.theme.UITheme
import com.jaguar.attendancetracker.ui.widget.CampusBriefRefresh
import com.jaguar.attendancetracker.ui.widget.OPEN_CAMPUS_BRIEF_TODAY
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private var launchShortcut by mutableStateOf<Destinations?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        launchShortcut = intent.shortcutDestination()
        enableEdgeToEdge()
        setContent {
            App()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.shortcutDestination()?.let {
            setIntent(intent)
            launchShortcut = it
        }
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) {
            lifecycleScope.launch { CampusBriefRefresh.afterLeavingApp(applicationContext) }
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
            val subId = if (intent.shortcutDestination() != null) null else intent.extras?.getString("subjectId")
            if (subId != null) {
                navController.navigate("${Destinations.SUBJECT.route}/$subId") {
                    popUpTo(Destinations.TODAY.route) {
                        inclusive = false
                    }
                }
                intent.removeExtra("subjectId")
            }
        }
        LaunchedEffect(launchShortcut) {
            val destination = launchShortcut ?: return@LaunchedEffect
            navController.navigate(destination.route) {
                popUpTo(Destinations.TODAY.route) { inclusive = false }
                launchSingleTop = true
            }
            if (intent.shortcutDestination() == destination) intent.action = null
            launchShortcut = null
        }
    }
}

private fun Intent.shortcutDestination(): Destinations? = when (action) {
    OPEN_CAMPUS_BRIEF_TODAY -> Destinations.TODAY
    OPEN_EXAM_REMINDER -> Destinations.EXAMS
    else -> null
}
