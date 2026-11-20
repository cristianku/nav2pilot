package com.cristianku.nav2pilot.ui

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cristianku.nav2pilot.BuildConfig
import com.cristianku.nav2pilot.R
import com.cristianku.nav2pilot.net.HttpClient
import com.cristianku.nav2pilot.net.HttpRequest
import com.cristianku.nav2pilot.net.UrlConnectionHttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.time.LocalDate

internal data class FeedbackVisits(val lastDay: Long = -1, val days: Int = 0, val nextDay: Long = 0, val stopped: Boolean = false) {
    fun visit(today: Long): Pair<FeedbackVisits, Boolean> {
        if (stopped || today <= lastDay) return this to false
        val count = minOf(days + 1, 3)
        val ask = count == 3 && today >= nextDay
        return copy(lastDay = today, days = count, nextDay = if (ask) today + 60 else nextDay) to ask
    }
}

internal object TesterInbox {
    const val ENDPOINT = "https://feedback.14bodhi.com/v1/feedback"
    val metadata = "Nav2Pilot feedback\nformat: 1\napp: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})\nplatform: Android"
    fun report(message: String) = "$metadata\n\n[message]\n${message.trim()}\n"
    fun send(http: HttpClient, key: String, report: String): Boolean = key.isNotEmpty() &&
        http.execute(HttpRequest("POST", ENDPOINT, mapOf("Content-Type" to "text/plain; charset=utf-8",
            "X-App" to "nav2pilot", "X-App-Key" to key), body = report, followRedirects = false,
            timeoutMillis = 10_000)).code == 201
}

class TesterFeedback(application: Application) : AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences("tester_feedback", 0)
    enum class Screen { Hidden, Invite, Write, Thanks }
    var screen by mutableStateOf(Screen.Hidden)
        private set
    var message by mutableStateOf("")
    var busy by mutableStateOf(false)
        private set
    var failed by mutableStateOf(false)
        private set
    fun visit() {
        val old = FeedbackVisits(prefs.getLong("day", -1), prefs.getInt("days", 0), prefs.getLong("next", 0), prefs.getBoolean("stopped", false))
        val (new, ask) = old.visit(LocalDate.now().toEpochDay())
        if (old != new) prefs.edit().putLong("day", new.lastDay).putInt("days", new.days).putLong("next", new.nextDay).apply()
        if (ask) screen = Screen.Invite
    }
    fun stopAsking() { prefs.edit().putBoolean("stopped", true).apply(); hide() }
    fun write() { if (!busy) { failed = false; screen = Screen.Write } }
    fun hide() { screen = Screen.Hidden }
    fun send() {
        if (busy || message.isBlank() || screen != Screen.Write) return
        val body = TesterInbox.report(message)
        busy = true
        failed = false
        viewModelScope.launch {
            val accepted = withContext(Dispatchers.IO) {
                try {
                    TesterInbox.send(UrlConnectionHttpClient("Nav2Pilot/${BuildConfig.VERSION_NAME}"), BuildConfig.FEEDBACK_KEY, body)
                } catch (_: IOException) { false }
            }
            busy = false
            if (accepted) {
                prefs.edit().putBoolean("stopped", true).apply()
                message = ""
                if (screen == Screen.Write) screen = Screen.Thanks
            } else failed = true
        }
    }
}

@Composable
internal fun TesterFeedbackDialog(model: TesterFeedback) {
    val context = LocalContext.current
    when (model.screen) {
        TesterFeedback.Screen.Hidden -> Unit
        TesterFeedback.Screen.Invite -> AlertDialog(onDismissRequest = model::hide,
            title = { Text(stringResource(R.string.fb_invite)) },
            text = { Text(stringResource(R.string.fb_question)) },
            confirmButton = { Button(onClick = model::write) { Text(stringResource(R.string.fb_write)) } },
            dismissButton = { Column {
                TextButton(onClick = model::hide) { Text(stringResource(R.string.fb_later)) }
                TextButton(onClick = model::stopAsking) { Text(stringResource(R.string.fb_stop)) }
            } })
        TesterFeedback.Screen.Write -> AlertDialog(onDismissRequest = { if (!model.busy) model.hide() },
            title = { Text(stringResource(R.string.fb_title)) },
            text = { Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(stringResource(R.string.fb_notice), style = MaterialTheme.typography.bodyMedium)
                Text(TesterInbox.metadata, style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(model.message, { model.message = it.take(4000) }, Modifier.fillMaxWidth(),
                    enabled = !model.busy, minLines = 3, maxLines = 6, label = { Text(stringResource(R.string.fb_message)) })
                if (model.failed) {
                    Text(stringResource(R.string.fb_failed))
                    TextButton(onClick = { FeedbackMail.share(context, BuildConfig.VERSION_NAME, TesterInbox.report(model.message)) }) {
                        Text(stringResource(R.string.fb_mail))
                    }
                }
            } },
            confirmButton = { Button(onClick = model::send, enabled = !model.busy && model.message.isNotBlank()) {
                Text(stringResource(if (model.busy) R.string.fb_sending else R.string.fb_send))
            } },
            dismissButton = { TextButton(onClick = model::hide, enabled = !model.busy) { Text(stringResource(android.R.string.cancel)) } })
        TesterFeedback.Screen.Thanks -> AlertDialog(onDismissRequest = model::hide, title = { Text(stringResource(R.string.fb_thanks)) },
            confirmButton = { TextButton(onClick = model::hide) { Text(stringResource(android.R.string.ok)) } })
    }
}
