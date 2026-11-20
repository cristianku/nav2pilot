package com.cristianku.nav2pilot.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import java.net.URLEncoder

/**
 * "Send feedback" in the settings: an e-mail that the user reads, completes and sends with their own mail app.
 * The app itself sends nothing.
 */
object FeedbackMail {
    /** The contact address of the privacy page. */
    const val ADDRESS = "admin@14bodhi.com"

    fun subject(version: String): String {
        if (version.isEmpty()) {
            return "Nav2Pilot feedback"
        }
        return "Nav2Pilot feedback – $version (Android)"
    }

    /** Room to write, then what tells one phone's problem from another's: app version, Android version, phone. */
    fun body(version: String, androidRelease: String, phone: String): String {
        val parts = mutableListOf<String>()
        if (version.isNotEmpty()) {
            parts.add("Nav2Pilot $version")
        }
        if (androidRelease.isNotEmpty()) {
            parts.add("Android $androidRelease")
        }
        if (phone.isNotEmpty()) {
            parts.add(phone)
        }
        return "\n\n\n--\n" + parts.joinToString(", ")
    }

    /** "Google Pixel 8"; the maker is left out when the model already starts with it. */
    fun phoneName(manufacturer: String, model: String): String {
        val maker = manufacturer.trim()
        val name = model.trim()
        if (maker.isEmpty() || name.startsWith(maker, ignoreCase = true)) {
            return name
        }
        return maker.replaceFirstChar { first -> first.uppercase() } + " " + name
    }

    fun mailto(subject: String, body: String): String {
        return "mailto:" + ADDRESS + "?subject=" + encode(subject) + "&body=" + encode(body)
    }

    /** Offer the exact same report via mail, or the share sheet when there is no mail handler. */
    fun share(context: Context, version: String, body: String) {
        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse(mailto(subject(version), body)))
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (noMailApp: ActivityNotFoundException) {
            val share = Intent(Intent.ACTION_SEND).setType("text/plain")
                .putExtra(Intent.EXTRA_SUBJECT, subject(version)).putExtra(Intent.EXTRA_TEXT, body)
            context.startActivity(Intent.createChooser(share, null))
        }
    }

    // a mailto link takes %20 for a space, not the "+" of a form
    private fun encode(text: String): String {
        return URLEncoder.encode(text, "UTF-8").replace("+", "%20")
    }
}
