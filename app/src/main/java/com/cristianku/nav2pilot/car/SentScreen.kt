package com.cristianku.nav2pilot.car

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.Template
import com.cristianku.nav2pilot.transport.SendOutcome

/** The answer of the car: sent, or why not, with a retry. Done goes back to the favorites. */
class SentScreen(
    carContext: CarContext,
    private val outcome: SendOutcome,
    private val retry: () -> Unit,
) : Screen(carContext) {
    override fun onGetTemplate(): Template {
        var title = "Sent"
        if (!outcome.success) {
            title = "Not sent"
        }
        val message = MessageTemplate.Builder(outcome.message)
            .setTitle(title)
            .setHeaderAction(Action.BACK)
        if (!outcome.success) {
            message.addAction(
                Action.Builder()
                    .setTitle("Try again")
                    .setOnClickListener {
                        screenManager.pop()
                        retry()
                    }
                    .build(),
            )
        }
        message.addAction(
            Action.Builder()
                .setTitle("Done")
                .setFlags(Action.FLAG_PRIMARY)
                .setOnClickListener { screenManager.popToRoot() }
                .build(),
        )
        return message.build()
    }
}
