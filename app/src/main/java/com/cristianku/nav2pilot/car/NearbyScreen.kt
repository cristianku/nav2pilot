package com.cristianku.nav2pilot.car

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.constraints.ConstraintManager
import androidx.car.app.model.Action
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import com.cristianku.nav2pilot.places.PlaceCategory

/**
 * The kinds of places the car can look for around itself, the same as the phone's places menu. The ones that
 * matter most while driving come first, so a car with short lists still shows them.
 */
class NearbyScreen(carContext: CarContext, private val model: CarModel) : Screen(carContext) {
    override fun onGetTemplate(): Template {
        val constraints = carContext.getCarService(ConstraintManager::class.java)
        val limit = constraints.getContentLimit(ConstraintManager.CONTENT_LIMIT_TYPE_LIST)
        val items = ItemList.Builder()
        for (category in PlaceCategory.entries.take(limit)) {
            items.addItem(
                Row.Builder()
                    .setTitle(category.label)
                    .setBrowsable(true)
                    .setOnClickListener { screenManager.push(PlacesScreen(carContext, model, category)) }
                    .build(),
            )
        }
        return ListTemplate.Builder()
            .setTitle("Nearby")
            .setHeaderAction(Action.BACK)
            .setSingleList(items.build())
            .build()
    }
}
