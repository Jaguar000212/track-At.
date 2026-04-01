package com.jaguar.attendancetracker.ui.helpPage

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.jaguar.attendancetracker.ui.theme.AppTypography
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Brands
import compose.icons.fontawesomeicons.brands.Github

@Composable
fun HelpHome(changePage: (Int) -> Unit) {
    val uriHandler = LocalUriHandler.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(text = "Save Our Souls!", style = AppTypography.headlineMedium)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Hello fellow student! If you're reading this, you might be feeling a bit lost in the world of attendance tracking. Don't worry, we've got your back! Let's dive in and explore how to use this app to its fullest potential!",
            style = AppTypography.bodyMedium
        )
        Spacer(modifier = Modifier.height(8.dp))

        for (topic in HelpTopics.entries) {
            TextButton({ changePage(topic.pageNo) }) {
                if (topic.icon is Int) Icon(painterResource(topic.icon), "")
                else Icon(topic.icon as ImageVector, "")

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = topic.title,
                    style = AppTypography.bodyLarge,
                    textDecoration = TextDecoration.Underline
                )
            }
            Text(
                text = topic.description,
                style = AppTypography.bodyMedium,
                modifier = Modifier.padding(start = 32.dp)
            )
        }

        Spacer(modifier = Modifier.height(48.dp))

        Icon(
            FontAwesomeIcons.Brands.Github,
            "GitHub",
            Modifier
                .align(Alignment.CenterHorizontally)
                .size(64.dp),
            Color.Gray
        )
        Text(
            text = "Made with ❤ by",
            style = AppTypography.bodyMedium,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(top = 8.dp)
        )
        Text(
            text = "@Jaguar000212",
            style = AppTypography.bodyMedium,
            textDecoration = TextDecoration.Underline,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .clickable { uriHandler.openUri("https://github.com/Jaguar000212/track-at.") })
    }
}
