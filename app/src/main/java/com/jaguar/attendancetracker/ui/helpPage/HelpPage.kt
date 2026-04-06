package com.jaguar.attendancetracker.ui.helpPage

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpPage() {
    var pageNo by remember { mutableIntStateOf(0) }
    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        when (pageNo) {
            0 -> HelpHome { pageNo = it }
            1 -> HelpDashboard()
            2 -> HelpSchedule()
            3 -> HelpDay()
            4 -> HelpSubject()
            5 -> HelpImExport()
        }

        if (pageNo != 0) SmallFloatingActionButton(
            { pageNo-- },
            shape = CircleShape,
            modifier = Modifier
                .padding(8.dp)
                .align(Alignment.BottomStart)
        ) {
            TooltipBox(
                positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                tooltip = { PlainTooltip { Text("Previous Page") } },
                state = rememberTooltipState()
            ) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, "")
            }
        }

        if (pageNo != 5) SmallFloatingActionButton(
            { pageNo++ },
            shape = CircleShape,
            modifier = Modifier
                .padding(8.dp)
                .align(Alignment.BottomEnd)
        ) {
            TooltipBox(
                positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                tooltip = { PlainTooltip { Text("Next Page") } },
                state = rememberTooltipState()
            ) {
                Icon(Icons.AutoMirrored.Outlined.ArrowForward, "")
            }
        }
    }
}