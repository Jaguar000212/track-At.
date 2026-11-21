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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavBackStackEntry
import androidx.navigation.compose.currentBackStackEntryAsState
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
        launchShortcut = if (savedInstanceState == null) intent.shortcutDestination()
        else savedInstanceState.getString(PENDING_SHORTCUT)?.let { name ->
            Destinations.entries.find { it.name == name && it != Destinations.BOARD }
        }
        enableEdgeToEdge()
        setContent {
            App()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.shortcutDestination()?.let { launchShortcut = it }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        // Quick Pin is a live tap, not a request to persist or replay after recreation.
        outState.putString(PENDING_SHORTCUT, launchShortcut?.takeUnless { it == Destinations.BOARD }?.name)
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
        // Ephemeral, addressed to one live Board entry; never survives as an unconsumed
        // composer request in saved Nav state (which could replay after cancel).
        var captureEntry by remember { mutableStateOf<NavBackStackEntry?>(null) }
        val currentEntry by navController.currentBackStackEntryAsState()
        LaunchedEffect(currentEntry, captureEntry) {
            // Inspect the live controller in case navigation and its Compose observation are
            // briefly one frame apart. A left Board entry must not re-open on return.
            if (captureEntry != null && captureEntry != navController.currentBackStackEntry) captureEntry = null
        }

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
                Navigation(
                    navController, Modifier.padding(innerPadding), quickCaptureEntry = captureEntry,
                    onQuickCaptureHandled = { entry -> if (captureEntry == entry) captureEntry = null },
                )
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
            val quickCapture = destination == Destinations.BOARD
            captureEntry = null
            if (!quickCapture || navController.currentDestination?.route != destination.route) {
                navController.navigate(destination.route) {
                    popUpTo(Destinations.TODAY.route) { inclusive = false }
                    launchSingleTop = true
                }
            }
            if (quickCapture) {
                captureEntry = navController.currentBackStackEntry?.takeIf { it.destination.route == destination.route }
            }
            launchShortcut = null
        }
    }
}

private const val PENDING_SHORTCUT = "pending_launch_shortcut"

private fun Intent.shortcutDestination(): Destinations? = when (action) {
    OPEN_CAMPUS_BRIEF_TODAY -> Destinations.TODAY
    OPEN_EXAM_REMINDER -> Destinations.EXAMS
    OPEN_QUICK_PIN -> Destinations.BOARD
    else -> null
}
