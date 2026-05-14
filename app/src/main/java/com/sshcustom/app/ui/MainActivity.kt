package com.sshcustom.app.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.sshcustom.app.R
import com.sshcustom.app.ui.screens.AppRoot
import com.sshcustom.app.ui.theme.SshCustomTheme

/**
 * Single-activity entry point. The whole app is one Compose root with a
 * bottom navigation bar driving four screens; activity lifecycle is the
 * unit of session for the SSE connection too (see [MainViewModel]).
 *
 * The splash theme set in the manifest holds the launcher icon for the
 * fraction of a second between app launch and the Compose first frame;
 * after that we reset to the regular theme so the Compose surface
 * background takes over.
 */
class MainActivity : ComponentActivity() {

    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Reset from the splash theme (set in the manifest) to the regular
        // theme so the Compose color scheme drives edge-to-edge correctly.
        // The splash theme background was just a flat color so there's
        // nothing to tear down beyond this style swap.
        setTheme(R.style.Theme_SshCustom)
        enableEdgeToEdge()

        setContent {
            SshCustomTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppRoot(viewModel = vm)
                }
            }
        }
    }
}
