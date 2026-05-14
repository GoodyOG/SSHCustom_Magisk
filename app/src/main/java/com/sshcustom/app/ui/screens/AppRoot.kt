package com.sshcustom.app.ui.screens

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.sshcustom.app.R
import com.sshcustom.app.ui.MainViewModel

private enum class Tab(val labelRes: Int) {
    Home(R.string.tab_home),
    Profiles(R.string.tab_profiles),
    Runtime(R.string.tab_runtime),
    Settings(R.string.tab_settings),
}

/**
 * Top-level scaffold. The bottom nav drives a Compose-only state switch
 * (no navigation-compose backstack) because:
 *
 * - The app is shallow — four flat tabs, no nested routes — and a
 *   backstack would only confuse users who press Back expecting to exit.
 * - rememberSaveable on the selected tab survives configuration changes
 *   without needing a NavController, so this is the simplest thing that
 *   works for the v2.0.0 surface area.
 *
 * If a future PR adds deep navigation (e.g. profile detail screens that
 * push), this is the right place to introduce navigation-compose.
 */
@Composable
fun AppRoot(viewModel: MainViewModel) {
    var selected: Tab by rememberSaveable { mutableStateOf(Tab.Home) }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.toast.collect { snackbar.showSnackbar(it) }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(hostState = snackbar) },
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = selected == tab,
                        onClick = { selected = tab },
                        icon = {
                            Icon(
                                imageVector = when (tab) {
                                    Tab.Home -> Icons.Outlined.Home
                                    Tab.Profiles -> Icons.Outlined.Person
                                    Tab.Runtime -> Icons.Outlined.Build
                                    Tab.Settings -> Icons.Outlined.Settings
                                },
                                contentDescription = stringResource(tab.labelRes),
                            )
                        },
                        label = { Text(stringResource(tab.labelRes)) },
                    )
                }
            }
        },
    ) { padding: PaddingValues ->
        when (selected) {
            Tab.Home -> HomeScreen(viewModel = viewModel, contentPadding = padding)
            Tab.Profiles -> ProfilesScreen(viewModel = viewModel, contentPadding = padding)
            Tab.Runtime -> RuntimeScreen(viewModel = viewModel, contentPadding = padding)
            Tab.Settings -> SettingsScreen(viewModel = viewModel, contentPadding = padding)
        }
    }
}
