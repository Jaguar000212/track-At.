package com.jaguar.attendancetracker.ui.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ExperimentalGlanceApi
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.components.Scaffold
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.material3.ColorProviders
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.jaguar.attendancetracker.CampusBriefWidgetReceiver
import com.jaguar.attendancetracker.MainActivity
import com.jaguar.attendancetracker.R
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.dropWhile
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

internal const val OPEN_CAMPUS_BRIEF_TODAY = "com.jaguar.attendancetracker.action.CAMPUS_BRIEF_TODAY"

internal fun campusBriefTodayIntent(context: Context): Intent = Intent(context, MainActivity::class.java)
    .setAction(OPEN_CAMPUS_BRIEF_TODAY)
    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)

private val compact = DpSize(148.dp, 116.dp)
private val expanded = DpSize(220.dp, 154.dp)

class CampusBriefWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(setOf(compact, expanded))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val app = context.applicationContext
        val generation = CampusBriefRefresh.generations.value
        val first = readBrief(app)
        // updateAll does not restart an active Glance session. Its short-lived composition
        // observes only this in-memory refresh signal (no recurring job or database observer).
        val revisions = CampusBriefRefresh.generations.dropWhile { it == generation }.map { readBrief(app) }
        val colors = ColorProviders(light = lightColorScheme(), dark = darkColorScheme())
        provideContent {
            val counts by revisions.collectAsState(first)
            GlanceTheme(colors) { BriefContent(counts) }
        }
    }

    private suspend fun readBrief(app: Context): CampusBriefCounts? = try {
        val entry = EntryPointAccessors.fromApplication(app, WidgetEntryPoint::class.java)
        campusBriefCounts(entry.database(), LocalDate.now())
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }
}

@OptIn(ExperimentalGlanceApi::class)
@Composable
private fun BriefContent(counts: CampusBriefCounts?) {
    val context = LocalContext.current
    val showUpcoming = LocalSize.current.width >= expanded.width && LocalSize.current.height >= expanded.height
    val normal = TextStyle(color = GlanceTheme.colors.onBackground, fontSize = 13.sp)
    Scaffold(backgroundColor = GlanceTheme.colors.background) {
        Column(GlanceModifier.fillMaxSize().clickable(actionStartActivity(campusBriefTodayIntent(context))).padding(8.dp)) {
            Text(context.getString(R.string.campus_brief_title),
                style = TextStyle(color = GlanceTheme.colors.onBackground, fontSize = 15.sp, fontWeight = FontWeight.Bold))
            if (counts == null) {
                Text(context.getString(R.string.campus_brief_unavailable), style = normal)
            } else {
                val date = counts.asOf.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
                Text(context.getString(R.string.campus_brief_as_of, date), style = normal)
                Text(context.getString(R.string.campus_brief_saved, counts.savedMarked, counts.savedUnmarked), style = normal)
                if (showUpcoming) {
                    Text(context.getString(R.string.campus_brief_upcoming, counts.dueTasks, counts.datedExams), style = normal)
                }
            }
            Text(context.getString(R.string.campus_brief_open_today),
                style = TextStyle(color = GlanceTheme.colors.primary, fontSize = 13.sp, fontWeight = FontWeight.Medium))
        }
    }
}

internal object CampusBriefRefresh {
    val generations = MutableStateFlow(0L)
    private val refreshLock = Mutex()

    suspend fun afterLeavingApp(context: Context) {
        if (!refreshLock.tryLock()) return
        try {
            val app = context.applicationContext
            val installed = withContext(Dispatchers.IO) {
                AppWidgetManager.getInstance(app).getAppWidgetIds(ComponentName(app, CampusBriefWidgetReceiver::class.java))
                    .isNotEmpty()
            }
            if (!installed) return
            generations.update { it + 1 }
            CampusBriefWidget().updateAll(app)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // A best-effort launcher update must not affect the app or log data.
        } finally {
            refreshLock.unlock()
        }
    }
}
