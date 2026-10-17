package com.cristianku.nav2pilot

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.cristianku.nav2pilot.ui.MainViewModel
import com.cristianku.nav2pilot.ui.Nav2PilotRoot
import com.cristianku.nav2pilot.ui.theme.Nav2PilotTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels {
        MainViewModel.factory((application as Nav2PilotApplication).container)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // A recreated activity (rotation) already handled its intent; the ViewModel kept the result.
        if (savedInstanceState == null) {
            handleIntent(intent)
        }
        setContent {
            Nav2PilotTheme {
                Nav2PilotRoot(viewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) {
            return
        }
        if (intent.action == Intent.ACTION_SEND) {
            val text = intent.getStringExtra(Intent.EXTRA_TEXT) ?: ""
            val subject = intent.getStringExtra(Intent.EXTRA_SUBJECT) ?: ""
            if (text.isNotBlank() || subject.isNotBlank()) {
                viewModel.handleShare(text, subject)
            }
            return
        }
        if (intent.action == Intent.ACTION_VIEW) {
            val link = intent.dataString
            if (link != null && link.isNotBlank()) {
                viewModel.handleLink(link)
            }
        }
    }
}
