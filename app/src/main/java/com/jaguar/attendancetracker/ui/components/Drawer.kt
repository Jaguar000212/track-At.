package com.jaguar.attendancetracker.ui.components

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DrawerState
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.jaguar.attendancetracker.navigation.Destinations
import com.jaguar.attendancetracker.ui.theme.AppTypography
import kotlinx.coroutines.launch

@Composable
fun Drawer(
    drawerState: DrawerState,
    navController: NavHostController,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val scope = rememberCoroutineScope()

    ModalNavigationDrawer({
        ModalDrawerSheet(drawerState) {
            Spacer(Modifier.padding(8.dp))

            Destinations.entries.filter { it.showInDrawer }.forEach {
                NavigationDrawerItem(
                    label = {
                        Text(
                            it.label,
                            fontStyle = AppTypography.labelMedium.fontStyle,
                            fontSize = AppTypography.labelMedium.fontSize,
                            fontWeight = AppTypography.labelMedium.fontWeight,
                            fontFamily = AppTypography.labelMedium.fontFamily,
                        )
                    }, onClick = {
                        navController.navigate(it.route) {
                            popUpTo(Destinations.DAYVIEW.route) {
                                inclusive = false
                            }
                        }
                        scope.launch {
                            drawerState.close()
                        }
                    }, selected = currentRoute == it.route, icon = {
                        if (it.icon is Int) Icon(painterResource(it.icon), "")
                        else Icon(it.icon as ImageVector, "")
                    }, modifier = Modifier.padding(horizontal = 8.dp)
                )
            }
        }
    }, drawerState = drawerState, modifier = modifier, content = content)
}